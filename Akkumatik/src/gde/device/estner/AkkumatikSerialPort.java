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
    
    Copyright (c) 2008,2009,2010,2011,2012,2013,2014,2015,2016,2017,2018,2019,2020,2021,2022,2023 Winfried Bruegmann
****************************************************************************************/
package gde.device.estner;

import java.io.IOException;
import java.util.logging.Logger;

import gde.comm.DeviceCommPort;
import gde.device.IDevice;
import gde.device.InputTypes;
import gde.exception.TimeOutException;
import gde.ui.DataExplorer;
import gde.utils.StringHelper;

/**
 * PowerLab8 serial port implementation
 * @author Winfried Brügmann
 */
public class AkkumatikSerialPort extends DeviceCommPort {
	final static String	$CLASS_NAME		= AkkumatikSerialPort.class.getName();
	final static Logger	log						= Logger.getLogger(AkkumatikSerialPort.$CLASS_NAME);

	final int						timeout;
	final int						stableIndex;
	final int						maxRetryCount	= 25;
	int									retryCount;

	/**
	 * constructor of default implementation
	 * @param currentDeviceConfig - required by super class to initialize the serial communication port
	 * @param currentApplication - may be used to reflect serial receive,transmit on/off status or overall status by progress bar 
	 */
	public AkkumatikSerialPort(IDevice currentDevice, DataExplorer currentApplication) {
		super(currentDevice, currentApplication);
		this.timeout = this.device.getDeviceConfiguration().getReadTimeOut();
		this.stableIndex = this.device.getDeviceConfiguration().getReadStableIndex();
		this.retryCount = 0;
	}

	/**
	 * method to gather data from device, implementation is individual for device
	 * Checking Charger Status:
	 * 1. Send Ram0 to request a status packet
	 * 2. Verify the CRC checksum to confirm the received packet is valid.
	 * 3. Gather the following important information from the packet (done in gatherer thread)
	 * 	- Cell Voltages
	 * 	- Mode
	 * 	- Preset Number
	 * 	- Charge/Discharge Complete (from Status Flags)
	 * @return byte array containing gathered data - this can individual specified per device
	 * @throws IOException
	 */
	public synchronized byte[] getData() throws Exception {
		final String $METHOD_NAME = "getData";
		byte[] data = new byte[Math.abs(this.device.getDataBlockSize(InputTypes.SERIAL_IO))];

		try {
			data = this.read(data, this.timeout, this.stableIndex);

			if (AkkumatikSerialPort.log.isLoggable(java.util.logging.Level.FINE)) {
				AkkumatikSerialPort.log.logp(java.util.logging.Level.FINER, AkkumatikSerialPort.$CLASS_NAME, $METHOD_NAME, "0123456789|123456789|123456789|123456789|123456789|123456789|123456789|123456789");
				AkkumatikSerialPort.log.logp(java.util.logging.Level.FINE, AkkumatikSerialPort.$CLASS_NAME, $METHOD_NAME, new String(data));
			}
			if ((data.length < 69 || data.length > 129) || data[4] != 58 || data[7] != 58
					|| !(data[0] == 49 || data[0] == 50) || data[1] != -1 || data[data.length - 1] != 0x0A || data[data.length - 2] != 0x0D) {
				AkkumatikSerialPort.log.logp(java.util.logging.Level.WARNING, AkkumatikSerialPort.$CLASS_NAME, $METHOD_NAME, "Serial comm error, data = " + new String(data));
				++this.retryCount;
				if (this.retryCount > this.maxRetryCount) {
					final String msg = "Errors during serial communication, maximum of retries exceeded!";
					this.retryCount = 0;
					AkkumatikSerialPort.log.logp(java.util.logging.Level.WARNING, AkkumatikSerialPort.$CLASS_NAME, $METHOD_NAME, msg);
				}
				
				this.cleanInputStream();
				data = getData();
			}
		}
		catch (Exception e) {
			if (!(e instanceof TimeOutException)) {
				AkkumatikSerialPort.log.logp(java.util.logging.Level.SEVERE, AkkumatikSerialPort.$CLASS_NAME, $METHOD_NAME, e.getMessage(), e);
			}
			throw e;
		}
		return data;
	}

	/**
	 * converts the data byte buffer in a String array with mostly readable content
	 * @param buffer
	 * @return
	 */
	public String[] getDataArray(byte[] buffer) {
		if (log.isLoggable(java.util.logging.Level.FINE)) {
			log.log(java.util.logging.Level.FINE, "  Read : " + StringHelper.byte2Hex2CharString(buffer, buffer.length));
			log.log(java.util.logging.Level.FINE, "0123456789|123456789|123456789|123456789|123456789|123456789|123456789|123456789");
			log.log(java.util.logging.Level.FINE, "splitter = " + new String(new byte[] {(byte) 0xFF}) + " - " + new String(buffer));
		}
		String[] dataArray = new String(buffer).split(new String(new byte[] {(byte) 0xFF}));
		if (dataArray.length < 15) {
			log.log(java.util.logging.Level.SEVERE, "array size = " + dataArray.length + " splitter = " + new String(new byte[] {(byte) 0xFF}) + " - " + new String(buffer));
			log.log(java.util.logging.Level.SEVERE, "splitter = " + new String(new byte[] {(byte) 0xFF}) + " - " + new String(buffer));
		}
		if (dataArray.length < 15) {
			for (int i = 0; i<buffer.length; ++i) {
				if (buffer[i] == 0xFF) buffer[i] = 0x21;
			}
			log.log(java.util.logging.Level.WARNING, "array size = " + dataArray.length + " splitter = " + new String(new byte[] {(byte) 0x21}) + " - " + new String(buffer));
			dataArray = new String(buffer).split(new String(new byte[] {(byte) 0x21}));
		}
		return dataArray;
	}
}
