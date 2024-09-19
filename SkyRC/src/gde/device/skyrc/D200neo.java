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

    Copyright (c) 2024 Winfried Bruegmann
****************************************************************************************/
package gde.device.skyrc;

import java.io.FileNotFoundException;

import javax.usb.UsbClaimException;
import javax.usb.UsbException;
import javax.xml.bind.JAXBException;

import gde.GDE;
import gde.data.Channel;
import gde.data.Channels;
import gde.device.DeviceConfiguration;
import gde.exception.ApplicationConfigurationException;
import gde.io.DataParser;
import gde.log.Level;
import gde.messages.Messages;
import gde.utils.StringHelper;
import gde.utils.WaitTimer;

public class D200neo extends Q200 {
	X200neoGathererThread	dataGatherThread;

	/**
	 * Class to implement SKYRC D200neo, Q200neo device
	 * @author Winfried Bruegmann
	 */
	public D200neo(String xmlFileName) throws FileNotFoundException, JAXBException {
		super(xmlFileName);
	}

	public D200neo(DeviceConfiguration deviceConfig) {
		super(deviceConfig);
	}


	/**
	 * convert the device bytes into raw values, no calculation will take place here, see translateValue reverseTranslateValue
	 * inactive or to be calculated data point are filled with 0 and needs to be handles after words
	 * @param points pointer to integer array to be filled with converted data
	 * @param dataBuffer byte array with the data to be converted
	 */
	@Override
	public int[] convertDataBytes(int[] points, byte[] dataBuffer) {
		int maxVoltage = Integer.MIN_VALUE;
		int minVoltage = Integer.MAX_VALUE;
		log.log(Level.OFF, StringHelper.byte2Hex2CharString(dataBuffer, dataBuffer.length));
		//0=Voltage 1=Current 2=Capacity 3=Power 4=Energy 5=Temperature Int 6=Resistance
		points[0] = DataParser.parse2Short(dataBuffer[10], dataBuffer[9]);
		points[1] = DataParser.parse2Short(dataBuffer[12], dataBuffer[11]);
		points[2] = DataParser.parse2Short(dataBuffer[6], dataBuffer[5]) * 1000;
		points[3] = Double.valueOf(points[0] / 1000.0 * points[1]).intValue(); // power U*I [W]
		//databuffer[0] injected battery type
		//databuffer[1] injected energy handling flag
		//databuffer[3] channel ID as bit field
		switch (dataBuffer[1]) {
		case 0: //add up energy
			switch (dataBuffer[3]) { //channel ID
			case 0x01:
				energy[0] += points[0] / 1000.0 * points[1] / 3600.0;
				points[4] = Double.valueOf(energy[0]).intValue();
				break;
			case 0x02:
				energy[1] += points[0] / 1000.0 * points[1] / 3600.0;				
				points[4] = Double.valueOf(energy[1]).intValue();
				break;
			case 0x04:
				energy[2] += points[0] / 1000.0 * points[1] / 3600.0;				
				points[4] = Double.valueOf(energy[2]).intValue();
				break;
			case 0x08:
				energy[3] += points[0] / 1000.0 * points[1] / 3600.0;
				points[4] = Double.valueOf(energy[3]).intValue();
				break;
			default:
				break;
			}
			if (log.isLoggable(java.util.logging.Level.FINE)) log.log(java.util.logging.Level.FINE, "add up Energy");
			break;
		case 1: // reset energy
			switch (dataBuffer[3]) { //channel ID
			case 0x01:
				energy[0] = 0.0;
				points[4] = 0;
				break;
			case 0x02:
				energy[1] = 0.0;
				points[4] = 0;
				break;
			case 0x04:
				energy[2] = 0.0;
				points[4] = 0;
				break;
			case 0x08:
				energy[3] = 0.0;
				points[4] = 0;
				break;
			default:
				break;
			}
			points[4] = 0;
			if (log.isLoggable(java.util.logging.Level.FINE)) log.log(java.util.logging.Level.FINE, "reset Energy");
			break;
		default: // keep energy untouched
		case -1: // keep energy untouched
			points[4] = points[4];
			if (log.isLoggable(java.util.logging.Level.FINE)) log.log(java.util.logging.Level.FINE, "untouche Energy");
			break;
		}
		//5==Temperature Int 6=Resistance
		points[5] = dataBuffer[14] * 1000;
		points[6] = DataParser.parse2Short(dataBuffer[16], dataBuffer[15]) * 100;
		
		if (dataBuffer[0] <= 3) { // exclude Ni PB batteries
			//8=CellVoltage1....13=CellVoltage6
			int j = 0;
			for (int i = 8; i < points.length; i++, j += 2) {
				if (dataBuffer[j + 17] != 0x00) { // filter none used cell 
					points[i] = DataParser.parse2Short(dataBuffer[j + 18], dataBuffer[j + 17]);
					maxVoltage = points[i] > maxVoltage ? points[i] : maxVoltage;
					minVoltage = points[i] < minVoltage ? points[i] : minVoltage;
				}
				else
					points[i] = 0;
			}
			//7=Balance
			points[7] = 1000 * (maxVoltage != Integer.MIN_VALUE && minVoltage != Integer.MAX_VALUE ? maxVoltage - minVoltage : 0);
		}
		return points;
	}


	/**
	 * method toggle open close serial port or start/stop gathering data from device
	 * if the device does not use serial port communication this place could be used for other device related actions which makes sense here
	 * as example a file selection dialog could be opened to import serialized ASCII data 
	 */
	@Override
	public void open_closeCommPort() {
		if (this.usbPort != null) {
			if (!this.usbPort.isConnected()) {
				try {
					Channel activChannel = Channels.getInstance().getActiveChannel();
					if (activChannel != null) {
						this.dataGatherThread = new X200neoGathererThread(this.application, this, this.usbPort, activChannel.getNumber(), this.getDialog());
						try {
							if (this.dataGatherThread != null && this.usbPort.isConnected()) {
							//this.systemInfo = new Q200.SystemInfo(this.usbPort.getSystemInfo(this.dataGatherThread.getUsbInterface(), Q200UsbPort.QuerySystemInfo.SLOT_0.value()));
								for (int i = 0; i < systemInfo.length; i++) {
									switch (i) {
									case 0:
									default:
										this.systemInfo[i] = new Q200.SystemInfo(this.usbPort.getSystemInfo(this.dataGatherThread.getUsbInterface(), Q200UsbPort.QuerySystemInfo.CHANNEL_A.value()));
										this.systemSetting[i] = new Q200.SystemSetting(this.usbPort.getSystemSetting(this.dataGatherThread.getUsbInterface(), Q200UsbPort.QuerySystemSetting.CHANNEL_A.value()));
										break;
									case 1:
										this.systemInfo[i] = new Q200.SystemInfo(this.usbPort.getSystemInfo(this.dataGatherThread.getUsbInterface(), Q200UsbPort.QuerySystemInfo.CHANNEL_B.value()));
										this.systemSetting[i] = new Q200.SystemSetting(this.usbPort.getSystemSetting(this.dataGatherThread.getUsbInterface(), Q200UsbPort.QuerySystemSetting.CHANNEL_B.value()));
										break;
									case 2:
										this.systemInfo[i] = new Q200.SystemInfo(this.usbPort.getSystemInfo(this.dataGatherThread.getUsbInterface(), Q200UsbPort.QuerySystemInfo.CHANNEL_C.value()));
										this.systemSetting[i] = new Q200.SystemSetting(this.usbPort.getSystemSetting(this.dataGatherThread.getUsbInterface(), Q200UsbPort.QuerySystemSetting.CHANNEL_C.value()));
										break;
									case 3:
										this.systemInfo[i] = new Q200.SystemInfo(this.usbPort.getSystemInfo(this.dataGatherThread.getUsbInterface(), Q200UsbPort.QuerySystemInfo.CHANNEL_D.value()));
										this.systemSetting[i] = new Q200.SystemSetting(this.usbPort.getSystemSetting(this.dataGatherThread.getUsbInterface(), Q200UsbPort.QuerySystemSetting.CHANNEL_D.value()));
										break;
									}
								}
								WaitTimer.delay(100);
								this.dataGatherThread.start();
							}
						}
						catch (Throwable e) {
							log.log(java.util.logging.Level.SEVERE, e.getMessage(), e);
						}
					}
				}
				catch (UsbClaimException e) {
					log.log(java.util.logging.Level.SEVERE, e.getMessage(), e);
					this.application.openMessageDialog(this.dialog.getDialogShell(),
							Messages.getString(gde.messages.MessageIds.GDE_MSGE0051, new Object[] { e.getClass().getSimpleName() + GDE.STRING_BLANK_COLON_BLANK + e.getMessage() }));
					try {
						if (this.usbPort != null && this.usbPort.isConnected()) this.usbPort.closeUsbPort(null);
					}
					catch (UsbException ex) {
						log.log(java.util.logging.Level.SEVERE, ex.getMessage(), ex);
					}
				}
				catch (UsbException e) {
					log.log(java.util.logging.Level.SEVERE, e.getMessage(), e);
					this.application.openMessageDialog(this.dialog.getDialogShell(), Messages.getString(gde.messages.MessageIds.GDE_MSGE0050));
					try {
						if (this.usbPort != null && this.usbPort.isConnected()) this.usbPort.closeUsbPort(null);
					}
					catch (UsbException ex) {
						log.log(java.util.logging.Level.SEVERE, ex.getMessage(), ex);
					}
				}
				catch (ApplicationConfigurationException e) {
					log.log(java.util.logging.Level.SEVERE, e.getMessage(), e);
					this.application.openMessageDialog(this.dialog.getDialogShell(), Messages.getString(gde.messages.MessageIds.GDE_MSGE0010));
					this.application.getDeviceSelectionDialog().open();
				}
				catch (Throwable e) {
					log.log(java.util.logging.Level.SEVERE, e.getMessage(), e);
				}
			}
			else {
				if (this.dataGatherThread != null) {
					this.dataGatherThread.stopDataGatheringThread(false, null);
				}
				//if (this.boundsComposite != null && !this.isDisposed()) this.boundsComposite.redraw();
				try {
					WaitTimer.delay(1000);
					if (this.usbPort != null && this.usbPort.isConnected()) this.usbPort.closeUsbPort(null);
				}
				catch (UsbException e) {
					log.log(java.util.logging.Level.SEVERE, e.getMessage(), e);
				}
			}
		}
	}

}
