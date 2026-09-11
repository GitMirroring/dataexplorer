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
    
    Copyright (c) 2008-2026 Winfried Bruegmann
****************************************************************************************/
package gde.device.schulze;

import java.io.IOException;
import java.util.logging.Logger;

import gde.comm.DeviceCommPort;
import gde.comm.IDeviceCommPort;
import gde.device.IDevice;
import gde.device.InputTypes;
import gde.exception.TimeOutException;
import gde.log.Level;
import gde.ui.DataExplorer;
import gde.utils.StringHelper;

/**
 * Schulze charger serial port implementation
 * @author Winfried Brügmann
 */
public class SchulzeSerialPort extends DeviceCommPort implements IDeviceCommPort {
	final static String	$CLASS_NAME			= SchulzeSerialPort.class.getName();
	final static Logger	log							= Logger.getLogger(SchulzeSerialPort.$CLASS_NAME);

	final byte					startByte1 = '1';
	final byte					startByte2 = '2';
	final byte					startByteTrailer = ':';
	final byte					separatorByte;
	final byte					endByte;
	final byte					endByte_1;
	final byte[]				tmpByte					= new byte[1];
	final int						timeout;
	final int						stableIndex;
	final int						tmpDataLength;

	byte[]							answer;
	byte[]							tmpData;
	byte[]							data						= new byte[] { 0x00 };
	long								time						= 0;
	boolean							isDataReceived	= false;
	int									index						= 0;
	int									retryCounter		= 0;

	/**
	 * constructor of default implementation
	 * @param currentDeviceConfig - required by super class to initialize the serial communication port
	 * @param currentApplication - may be used to reflect serial receive,transmit on/off status or overall status by progress bar 
	 */
	public SchulzeSerialPort(IDevice currentDevice, DataExplorer currentApplication) {
		super(currentDevice, currentApplication);
		this.endByte = this.device.getDataBlockEnding()[this.device.getDataBlockEnding().length - 1];
		this.endByte_1 = this.device.getDataBlockEnding().length == 2 ? this.device.getDataBlockEnding()[0] : 0x00;
		this.tmpDataLength = Math.abs(this.device.getDataBlockSize(InputTypes.SERIAL_IO));
		this.separatorByte = (byte) this.device.getDataBlockSeparator().value().charAt(0);
		this.timeout = this.device.getDeviceConfiguration().getReadTimeOut();
		this.stableIndex = this.device.getDeviceConfiguration().getReadStableIndex();
		this.isDataReceived = false;
		this.index = 0;
		this.tmpData = new byte[0];
	}
	
	public void resetTmpData() {this.tmpData = new byte[0];}

	/**
	 * method to gather data from device, implementation is individual for device
	 * @return byte array containing gathered data - this can individual specified per device
	 * @throws IOException
	 */
	public synchronized byte[] getData() throws Exception {
		final String $METHOD_NAME = "getData";
		int endIndex = 0;

		try {
			//receive data while needed
			this.isDataReceived = false;
			readNewData();
			if (log.isLoggable(Level.FINE)) log.log(Level.FINE, "'" + new String(answer) + "'"); 
			
			if (answer.length > 3 && (endIndex = getIndexCRLF(answer)) > 1) { //check for CR/LF
				if (this.tmpData.length == 0) {
					this.data = new byte[this.answer.length];
					System.arraycopy(this.answer, 0, this.data, 0, endIndex+1);
				}
				else {
					this.data = new byte[this.answer.length + this.tmpData.length];
					System.arraycopy(this.tmpData, 0, this.data, 0, this.tmpData.length);
					System.arraycopy(this.answer, 0, this.data, this.tmpData.length, this.answer.length);
				}
			}
			else { //do up to 5 retries 
				if (this.retryCounter++ < 5) {
					if (this.tmpData.length == 0) { //first retry append data
						this.tmpData = new byte[this.answer.length];
						System.arraycopy(this.answer, 0, this.tmpData, 0, this.answer.length);
					}
					else { //next retry append data and set tmpData
						this.data = new byte[this.answer.length + this.tmpData.length];
						System.arraycopy(this.tmpData, 0, this.data, 0, this.tmpData.length);
						System.arraycopy(this.answer, 0, this.data, this.tmpData.length, this.answer.length);
						this.tmpData = new byte[this.data.length];
						System.arraycopy(this.data, 0, this.tmpData, 0, this.tmpData.length);
					}
					this.getData();
				}
				else 
					throw new IOException();
			}
		}
		catch (Exception e) {
			if (!(e instanceof TimeOutException)) {
				SchulzeSerialPort.log.logp(Level.SEVERE, SchulzeSerialPort.$CLASS_NAME, $METHOD_NAME, e.getMessage(), e);
			}
			throw e;
		}
		
		if (this.retryCounter != 0)
			log.log(Level.WARNING, String.format("processed %d retries", this.retryCounter));
		
		this.retryCounter = 0;
		this.tmpData = new byte[0];
		if (log.isLoggable(Level.FINE)) log.log(Level.FINE, StringHelper.byte2Hex2CharString(this.data, this.data.length));
		return this.data;
	}

	/**
	 * find end of line CR/LF begin search from end
	 * @param tmpAnswer
	 * @return
	 */
	private int getIndexCRLF(byte[] tmpAnswer) {
		//find end of line CR/LF
		int endIndex = tmpAnswer.length - 1;			
		//1: 3488:13614: 2007:L 41 CR/LF		
		while (endIndex > 0 && (tmpAnswer[endIndex] & 0xFF) != this.endByte && (tmpAnswer[endIndex - 1] & 0xFF) != this.endByte_1)
			--endIndex;
		return endIndex;
	}

	/**
	 * receive data only if needed, a receive buffer may hold more than one getData() result
	 * @throws IOException
	 * @throws TimeOutException
	 */
	protected void readNewData() throws IOException, TimeOutException {
		if (!this.isDataReceived) {
			this.answer = new byte[this.tmpDataLength];
			this.answer = this.read(this.answer, this.timeout, this.stableIndex);
			this.isDataReceived = true;
		}
	}
}
