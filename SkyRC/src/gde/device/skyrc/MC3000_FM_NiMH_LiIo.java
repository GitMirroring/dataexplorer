/**************************************************************************************
  	This file is part of DataExplorer.

    GNU DataExplorer is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    GNU DataExplorer is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with DataExplorer.  If not, see <https://www.gnu.org/licenses/>.

    Copyright (c) 2024,2025 Winfried Bruegmann
****************************************************************************************/
package gde.device.skyrc;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;

import javax.usb.UsbClaimException;
import javax.usb.UsbException;
import javax.xml.bind.JAXBException;

import gde.GDE;
import gde.config.GraphicsTemplate;
import gde.config.Settings;
import gde.data.Channel;
import gde.data.Channels;
import gde.data.RecordSet;
import gde.device.DeviceConfiguration;
import gde.exception.ApplicationConfigurationException;
import gde.exception.DataInconsitsentException;
import gde.log.Level;
import gde.messages.Messages;
import gde.utils.FileUtils;
import gde.utils.WaitTimer;

/**
 * device to fit FM special requests 
 */
public class MC3000_FM_NiMH_LiIo extends MC3000 {
	final static Logger		log									= Logger.getLogger(MC3000_FM_NiMH_LiIo.class.getName());
	MC3000FmGathererThread				dataGatherThread;


	/**
	 * @param deviceProperties
	 * @throws FileNotFoundException
	 * @throws JAXBException
	 */
	public MC3000_FM_NiMH_LiIo(String deviceProperties) throws FileNotFoundException, JAXBException {
		super(deviceProperties);
		try {
			extractDeviceBatteryTypeTemplates(settings.getDeviceServices().get(this.getName()).getJarFile(), this.getName(), Settings.getGraphicsTemplatePath());
		}
		catch (IOException e) {
			log.log(Level.WARNING, "graphics templates doesn't exist and failed to extract from jar");
		}
	}

	/**
	 * @param deviceConfig
	 */
	public MC3000_FM_NiMH_LiIo(DeviceConfiguration deviceConfig) {
		super(deviceConfig);
		try {
			extractDeviceBatteryTypeTemplates(settings.getDeviceServices().get(this.getName()).getJarFile(), this.getName(), Settings.getGraphicsTemplatePath());
		}
		catch (IOException e) {
			log.log(Level.WARNING, "graphics templates doesn't exist and failed to extract from jar");
		}
	}

	/**
	 * Extract device default templates if not exist at target path
	 * @param jarFile
	 * @param templateDirectoryTargetPath
	 * @param serviceName_
	 */
	protected void extractDeviceBatteryTypeTemplates(JarFile jarFile, final String serviceName, String templateDirectoryTargetPath) {
		String serviceName_ = serviceName.substring(0, serviceName.indexOf(GDE.STRING_UNDER_BAR, 9) + 1); //MC3000_FM_
		Enumeration<JarEntry> e = jarFile.entries();
		while (e.hasMoreElements()) {
			String entryName = e.nextElement().getName();
			if (entryName.startsWith(Settings.PATH_RESOURCE_TEMPLATE) && entryName.endsWith(GDE.FILE_ENDING_DOT_XML) 
					&& (entryName.contains(serviceName_ + "NiMH") || entryName.contains(serviceName_ + "LiIo"))) {
				String defaultTemplateName = entryName.substring(Settings.PATH_RESOURCE_TEMPLATE.length());
				if (!FileUtils.checkFileExist(templateDirectoryTargetPath + GDE.FILE_SEPARATOR + defaultTemplateName)) {
					if (log.isLoggable(Level.INFO)) log.log(Level.INFO, String.format("jarFile = %s ; defaultTemplateName = %s", jarFile.getName(), entryName)); //$NON-NLS-1$
					FileUtils.extract(jarFile, defaultTemplateName, Settings.PATH_RESOURCE_TEMPLATE, templateDirectoryTargetPath, Settings.PERMISSION_555);
				}
			}
		}
	}

	/**
	 * @return true if setting is configured to continuous record set data gathering
	 */
	@Override
	protected boolean isContinuousRecordSet() {
		return true;
	}

	/**
	 * set the measurement ordinal of the values displayed in cell voltage window underneath the cell voltage bars
	 * set value of -1 to suppress this measurement
	 */
	@Override
	public int[] getCellVoltageOrdinals() {
		// Combi
		//0=Voltage 1=Voltage 2=Voltage 3=Voltage 4=Current 5=Current 6=Current 7=Current 8=Capacity 9=Capacity 10=Capacity 11=Capacity
		//12=Temperature 13=Temperature 14=Temperature 15=Temperature 16=Resistance 17=Resistance 18=Resistance 19=Resistance
		return new int[] { -1, -1 };
	}

	/**
	 * add record data size points from file stream to each measurement
	 * it is possible to add only none calculation records if makeInActiveDisplayable calculates the rest
	 * do not forget to call makeInActiveDisplayable afterwards to calculate the missing data
	 * since this is a long term operation the progress bar should be updated to signal business to user 
	 * @param recordSet
	 * @param dataBuffer
	 * @param recordDataSize
	 * @param doUpdateProgressBar
	 * @throws DataInconsitsentException 
	 */
	@Override
	public void addDataBufferAsRawDataPoints(RecordSet recordSet, byte[] dataBuffer, int recordDataSize, boolean doUpdateProgressBar) throws DataInconsitsentException {
		int dataBufferSize = GDE.SIZE_BYTES_INTEGER * recordSet.getNoneCalculationRecordNames().length;
		byte[] convertBuffer = new byte[dataBufferSize];
		int[] points = new int[recordSet.size()];
		String sThreadId = String.format("%06d", Thread.currentThread().threadId()); //$NON-NLS-1$
		int progressCycle = 0;
		if (doUpdateProgressBar) this.application.setProgress(progressCycle, sThreadId);

		for (int i = 0; i < recordDataSize; i++) {
			MC3000.log.log(java.util.logging.Level.FINER, i + " i*dataBufferSize+timeStampBufferSize = " + i * dataBufferSize); //$NON-NLS-1$
			System.arraycopy(dataBuffer, i * dataBufferSize, convertBuffer, 0, dataBufferSize);

			for (int j = 0, k = 0; j < points.length; j++, k += 4) {
				//0=Voltage 1=Current 2=Capacity 3=Power 4=Energy 5=Teperature 6=Resistance 7=SysTemperature
				points[j] = (((convertBuffer[k] & 0xff) << 24) + ((convertBuffer[k + 1] & 0xff) << 16) + ((convertBuffer[k + 2] & 0xff) << 8) + ((convertBuffer[k + 3] & 0xff) << 0));
			}
			recordSet.addPoints(points);

			if (doUpdateProgressBar && i % 50 == 0) this.application.setProgress(((++progressCycle * 2500) / recordDataSize), sThreadId);
		}
		recordSet.syncScaleOfSyncableRecords();
		String templateFileName = this.getName().substring(0, this.getName().indexOf(GDE.STRING_UNDER_BAR, 9) + 1);

		if (recordSet.getDescription().endsWith("LiIo")) {
			templateFileName += "LiIo";
		}
		else {
			templateFileName += "NiMH";
		}
		GraphicsTemplate graphicsTemplate = new GraphicsTemplate(templateFileName);
		graphicsTemplate.load();
		if (graphicsTemplate.isAvailable()) 
			application.getActiveChannel().setTemplate(templateFileName);
		
		if (doUpdateProgressBar) this.application.setProgress(100, sThreadId);
	}

	/**
	 * method toggle open close serial port or start/stop gathering data from device
	 */
	@Override
	public void open_closeCommPort() {
		if (this.usbPort != null) {
			if (!this.usbPort.isConnected()) {
				try {
					Channel activChannel = Channels.getInstance().getActiveChannel();
					if (activChannel != null) {
						this.dataGatherThread = new MC3000FmGathererThread(this.application, this, this.usbPort, activChannel.getNumber(), (MC3000Dialog) this.getDialog());
						try {
							if (this.dataGatherThread != null && this.usbPort.isConnected()) {
								this.systemSettings = new MC3000.SystemSettings(this.usbPort.getSystemSettings());
								//WaitTimer.delay(100);
								//this.usbPort.startProcessing(this.getDialog().dataGatherThread.getUsbInterface());
								WaitTimer.delay(100);
								this.dataGatherThread.start();
							}
							else {
								this.application.openMessageDialog(this.dialog.getDialogShell(), Messages.getString(gde.messages.MessageIds.GDE_MSGE0010));
							}
						}
						catch (Throwable e) {
							MC3000.log.log(java.util.logging.Level.SEVERE, e.getMessage(), e);
							//if (this.getDialog().dataGatherThread != null) this.usbPort.stopProcessing(this.getDialog().dataGatherThread.getUsbInterface());
						}
					}
				}
				catch (UsbClaimException e) {
					MC3000.log.log(java.util.logging.Level.SEVERE, e.getMessage(), e);
					this.application.openMessageDialog(this.dialog.getDialogShell(),
							Messages.getString(gde.messages.MessageIds.GDE_MSGE0051, new Object[] { e.getClass().getSimpleName() + GDE.STRING_BLANK_COLON_BLANK + e.getMessage() }));
					try {
						if (this.usbPort != null && this.usbPort.isConnected()) this.usbPort.closeUsbPort(null);
					}
					catch (UsbException ex) {
						MC3000.log.log(java.util.logging.Level.SEVERE, ex.getMessage(), ex);
					}
				}
				catch (UsbException e) {
					MC3000.log.log(java.util.logging.Level.SEVERE, e.getMessage(), e);
					//if (this.getDialog().dataGatherThread != null) this.usbPort.stopProcessing(this.getDialog().dataGatherThread.getUsbInterface());
					this.application.openMessageDialog(this.dialog.getDialogShell(), Messages.getString(gde.messages.MessageIds.GDE_MSGE0050));
					try {
						if (this.usbPort != null && this.usbPort.isConnected()) this.usbPort.closeUsbPort(null);
					}
					catch (UsbException ex) {
						MC3000.log.log(java.util.logging.Level.SEVERE, ex.getMessage(), ex);
					}
				}
				catch (ApplicationConfigurationException e) {
					MC3000.log.log(java.util.logging.Level.SEVERE, e.getMessage(), e);
					//if (this.getDialog().dataGatherThread != null) this.usbPort.stopProcessing(this.getDialog().dataGatherThread.getUsbInterface());
					this.application.openMessageDialog(this.dialog.getDialogShell(), Messages.getString(gde.messages.MessageIds.GDE_MSGE0010));
					this.application.getDeviceSelectionDialog().open();
				}
				catch (Throwable e) {
					MC3000.log.log(java.util.logging.Level.SEVERE, e.getMessage(), e);
					//if (this.getDialog().dataGatherThread != null) this.usbPort.stopProcessing(this.getDialog().dataGatherThread.getUsbInterface());
				}
			}
			else {
				if (this.dataGatherThread != null) {
					//this.usbPort.stopProcessing(this.getDialog().dataGatherThread.getUsbInterface());
					this.dataGatherThread.stopDataGatheringThread(false, null);
				}
				//if (this.getDialog().boundsComposite != null && !this.getDialog().isDisposed()) this.getDialog().boundsComposite.redraw();
				try {
					WaitTimer.delay(1000);
					if (this.usbPort != null && this.usbPort.isConnected()) this.usbPort.closeUsbPort(null);
				}
				catch (UsbException e) {
					MC3000.log.log(java.util.logging.Level.SEVERE, e.getMessage(), e);
				}
			}
		}
	}

}
