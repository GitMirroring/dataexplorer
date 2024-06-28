/**************************************************************************************
  	This file is part of GNU DataExplorer.

    GNU DataExplorer is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    GNU DataExplorer is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with GNU DataExplorer.  If not, see <https://www.gnu.org/licenses/>.
    
    Copyright (c) 2012,2013,2014,2015,2016,2017,2018,2019,2020,2021,2022,2023,2024 Winfried Bruegmann
****************************************************************************************/
package gde.device.wb;

import gde.GDE;
import gde.data.Channel;
import gde.data.Channels;
import gde.device.DeviceConfiguration;
import gde.exception.ApplicationConfigurationException;
import gde.exception.SerialPortException;
import gde.log.Level;
import gde.messages.Messages;

import java.io.FileNotFoundException;

import javax.xml.bind.JAXBException;

/**
 * @author brueg
 *
 */
public class AV4ms_FV_762 extends CSV2SerialAdapter {
	protected final AV4msSerialPort					serialPort;
	protected GathererThread_AV4ms				gathererThread;


	/**
	 * @param deviceProperties
	 * @throws FileNotFoundException
	 * @throws JAXBException
	 */
	public AV4ms_FV_762(String deviceProperties) throws FileNotFoundException, JAXBException {
		super(deviceProperties);
		this.serialPort = new AV4msSerialPort(this, this.application);
	}

	/**
	 * @param deviceConfig
	 */
	public AV4ms_FV_762(DeviceConfiguration deviceConfig) {
		super(deviceConfig);
		this.serialPort = new AV4msSerialPort(this, this.application);
	}

	/**
	 * query if the record set numbering should follow channel configuration numbering
	 * @return true where devices does not distinguish between channels (for example Av4ms_FV_762)
	 */
	@Override
	public boolean recordSetNumberFollowChannel() {
		return true;
	}

	/**
	 * query if the channel in use has dependency with each other to initiate file description synchronization 
	 * @return true for devices with one source of data distributed over record sets (HoTTAdapter, Av4ms_FV_762)
	 */
	@Override
	public boolean useChannelWithSyncedDescription() { return true; }
	
	/**
	 * method toggle open close serial port or start/stop gathering data from device
	 * if the device does not use serial port communication this place could be used for other device related actions which makes sense here
	 * as example a file selection dialog could be opened to import serialized ASCII data 
	 */
	public void open_closeCommPort() {
		if (this.isSerialIO) {
			if (this.serialPort != null) {
				if (!this.serialPort.isConnected()) {
					try {
						Channel activChannel = Channels.getInstance().getActiveChannel();
						if (activChannel != null) {
							this.gathererThread = new GathererThread_AV4ms(this.application, this, this.serialPort, activChannel.getNumber());
							try {
								if (this.serialPort.isConnected()) {
									this.gathererThread.start();
								}
							}
							catch (RuntimeException e) {
								log.log(Level.SEVERE, e.getMessage(), e);
							}
							catch (Throwable e) {
								log.log(Level.SEVERE, e.getMessage(), e);
							}
						}
					}
					catch (SerialPortException e) {
						log.log(Level.SEVERE, e.getMessage(), e);
						this.application.openMessageDialog(Messages.getString(gde.messages.MessageIds.GDE_MSGE0015, new Object[] { e.getClass().getSimpleName() + GDE.STRING_BLANK_COLON_BLANK + e.getMessage() }));
					}
					catch (ApplicationConfigurationException e) {
						log.log(Level.SEVERE, e.getMessage(), e);
						this.application.openMessageDialog(Messages.getString(gde.messages.MessageIds.GDE_MSGE0010));
						this.application.getDeviceSelectionDialog().open();
					}
					catch (Throwable e) {
						log.log(Level.SEVERE, e.getMessage(), e);
					}
				}
				else {
					if (this.gathererThread != null) {
						this.gathererThread.stopDataGatheringThread(false, null);
					}
					this.serialPort.close();
				}
			}
		}
		else { //InputTypes.FILE_IO
			importCsvFiles();
		}
	}
}
