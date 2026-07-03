/**
 * 
 */
package gde.device.easttester;

import java.io.IOException;
import java.util.StringTokenizer;

import gde.GDE;
import gde.comm.DeviceCommPort;
import gde.comm.IDeviceCommPort;
import gde.device.IDevice;
import gde.device.InputTypes;
import gde.exception.TimeOutException;
import gde.log.Level;
import gde.log.Logger;
import gde.ui.DataExplorer;
import gde.utils.StringHelper;
import gde.utils.WaitTimer;

/**
 * 
 */
public class ET5410SerialPort extends DeviceCommPort implements IDeviceCommPort {
	final static String	$CLASS_NAME			= ET5410SerialPort.class.getName();
	final static Logger	log							= Logger.getLogger(ET5410SerialPort.$CLASS_NAME);
	
	final byte					startByte;
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
	
	final boolean				isSerialRequest;
	final byte[]				serialRequest;

	final static byte[] IDN = {'*', 'I', 'D', 'N', '?', 0x0A};  //*IDN? Return the load's model and serial number (example "ET5410 09411830014 V1.02.1806.028 V1.10.1806.012" for mine)
	final static byte[] STAT = {'C', 'H', ':', 'S', 'W', '?', 0x0A};  //CH:SW?  Return ON or OFF to indicate whether the load is active or not. It must be set to ON to take measurements, of course...
	final static byte[] MODE = {'C', 'H', ':', 'M', 'O', 'D', 'E', '?', 0x0A};  //CH:MODE?  Returns "BATT" to indicate that the load is in battery test mode. This is the only mode we are interested in.



	public ET5410SerialPort(IDevice currentDevice, DataExplorer currentApplication) {
		super(currentDevice, currentApplication);
		this.startByte = (byte) this.device.getDataBlockLeader().charAt(0);
		this.endByte = this.device.getDataBlockEnding()[this.device.getDataBlockEnding().length - 1];
		this.endByte_1 = this.device.getDataBlockEnding().length == 2 ? this.device.getDataBlockEnding()[0] : 0x00;
		this.tmpDataLength = Math.abs(this.device.getDataBlockSize(InputTypes.SERIAL_IO));
		this.timeout = this.device.getDeviceConfiguration().getReadTimeOut();
		this.stableIndex = this.device.getDeviceConfiguration().getReadStableIndex();
		this.isDataReceived = false;
		this.index = 0;
		this.tmpData = new byte[0];
		this.isSerialRequest = this.device.getDeviceConfiguration().isSerialPortRequest();
		this.serialRequest = this.device.getDeviceConfiguration().getSerialPortRequest();
		if (log.isLoggable(Level.FINE))
			log.log(Level.FINE, "EOL " + StringHelper.byte2Hex2CharString(this.device.getDataBlockEnding(), this.device.getDataBlockEnding().length));
	}
	
	/**
	 * get the device identifier as array
	 * @return string array containing device name, serial number, FW, HW
	 * @throws Exception
	 */
	public String[] getIDN() throws Exception {
		final String $METHOD_NAME = "getIDN";
		int startIndex;
		String[] result = new String[] {GDE.STRING_QUESTION_MARK, GDE.STRING_QUESTION_MARK, GDE.STRING_QUESTION_MARK, GDE.STRING_QUESTION_MARK}; //deviceName, S/N, FW, HW
		try {
			log.log(Level.OFF, "query data " + StringHelper.arrayToStringNoBlank(ET5410SerialPort.IDN));
			this.write(ET5410SerialPort.IDN); 
			WaitTimer.delay(100);
			
			//receive data while needed
			readNewData();

			//find start index
			startIndex = findStartIndex(0);

			//find end index
			findDataEnd(startIndex);
			
			byte[] stripData = new byte[this.data.length-2];
			System.arraycopy(this.data, 2, stripData, 0, stripData.length);

			StringTokenizer tokenizer = new StringTokenizer((String) StringHelper.arrayToStringNoBlank(stripData), GDE.STRING_BLANK);
			int idx = 0;
			while (tokenizer.hasMoreTokens()) {
				result[idx++] = tokenizer.nextToken();
			}		
		}
		catch (Exception e) {
			if (!(e instanceof TimeOutException)) {
				log.logp(Level.SEVERE, ET5410SerialPort.$CLASS_NAME, $METHOD_NAME, e.getMessage(), e);
			}
			throw e;
		}
		log.log(Level.OFF, "return received data '" + StringHelper.arrayToString(result) + "'");
		this.isDataReceived = false;
		return result;
	}
	
	/**
	 * get the configured running mode, must "BATT" for usable data
	 * @return "BATT" if in battery mode
	 * @throws Exception
	 */
	public String getMode() throws Exception {
		final String $METHOD_NAME = "getMode";
		int startIndex;
		String result;
		try {
			log.log(Level.OFF, "query data " + StringHelper.arrayToStringNoBlank(ET5410SerialPort.MODE));
			this.write(ET5410SerialPort.MODE); 
			WaitTimer.delay(100);
			
			//receive data while needed
			readNewData();

			//find start index
			startIndex = findStartIndex(0);

			//find end index
			findDataEnd(startIndex);
			
			byte[] stripData = new byte[this.data.length-2];
			System.arraycopy(this.data, 2, stripData, 0, stripData.length);

			result = (String) StringHelper.arrayToStringNoBlank(stripData);
		}
		catch (Exception e) {
			if (!(e instanceof TimeOutException)) {
				log.logp(Level.SEVERE, ET5410SerialPort.$CLASS_NAME, $METHOD_NAME, e.getMessage(), e);
			}
			throw e;
		}
		log.log(Level.OFF, "return received data '" + result +"'");
		this.isDataReceived = false;
		return result;
	}
	
	/**
	 * get the configured running mode, must "BATT" for usable data
	 * @return "BATT" if in battery mode
	 * @throws Exception
	 */
	public boolean isProcessing() throws Exception {
		final String $METHOD_NAME = "isProcessing";
		int startIndex;
		boolean result = false;
		try {
			log.log(Level.OFF, "query data " + StringHelper.arrayToStringNoBlank(ET5410SerialPort.STAT));
			this.write(ET5410SerialPort.STAT); 
			WaitTimer.delay(100);
			
			//receive data while needed
			readNewData();

			//find start index
			startIndex = findStartIndex(0);

			//find end index
			findDataEnd(startIndex);
			
			byte[] stripData = new byte[this.data.length-2];
			System.arraycopy(this.data, 2, stripData, 0, stripData.length);

			result = StringHelper.arrayToStringNoBlank(stripData).equals("ON");
		}
		catch (Exception e) {
			if (!(e instanceof TimeOutException)) {
				log.logp(Level.SEVERE, ET5410SerialPort.$CLASS_NAME, $METHOD_NAME, e.getMessage(), e);
			}
			throw e;
		}
		log.log(Level.OFF, "return received data " + result);
		this.isDataReceived = false;
		return result;
	}

	/**
	 * method to gather data from device, implementation is individual for device
	 * @return byte array containing gathered data - this can individual specified per device
	 * @throws IOException
	 */
	public synchronized byte[] getData() throws Exception {
		final String $METHOD_NAME = "getData";
		int startIndex;
		//log.log(Level.OFF, StringHelper.byte2Hex2CharString("MEAS:ALL?".getBytes()));
		try {
			log.log(Level.OFF, "query data " + StringHelper.arrayToStringNoBlank(this.serialRequest));
			if (this.isSerialRequest)
				this.write(this.serialRequest); 
			
			//receive data while needed
			readNewData();

			//find start index
			startIndex = findStartIndex(0);

			//find end index
			findDataEnd(startIndex);
			/*
			byte[] stripData = new byte[this.data.length-2];
			System.arraycopy(this.data, 2, stripData, 0, stripData.length);
			this.data = new byte[this.data.length-2];
			System.arraycopy(stripData, 0, this.data, 0, this.data.length);
			*/
		}
		catch (Exception e) {
			if (!(e instanceof TimeOutException)) {
				log.logp(Level.SEVERE, ET5410SerialPort.$CLASS_NAME, $METHOD_NAME, e.getMessage(), e);
			}
			throw e;
		}
		log.log(Level.OFF, "return received data " + StringHelper.arrayToStringNoBlank(this.data));
		this.isDataReceived = false;
		return this.data;
	}

	/**
	 * recursive find start character index
	 * @param searchIndex
	 * @return startIndex
	 * @throws IOException
	 * @throws TimeOutException
	 */
	private int findStartIndex(int searchIndex) throws IOException, TimeOutException {
		this.index = searchIndex;
		while (this.index < this.answer.length && this.answer[this.index] != this.startByte)
			++this.index;
		
		if (this.index < this.answer.length) {
			searchIndex = this.index;
			++this.index;
			this.tmpData = new byte[0];
		}
		else { //startIndex not found, read new data
			log.log(Level.WARNING, "startIndex not found, check leading character defined");
			this.isDataReceived = false;
			//this.index = 0;
			readNewData();
			return findStartIndex(0);
		}
		return searchIndex;
	}
	
	/**
	 * recursive find the end of data, normal exit is not at the end of the method
	 * @param startIndex
	 * @throws IOException
	 * @throws TimeOutException
	 */
	protected byte[] findDataEnd(int startIndex) throws IOException, TimeOutException {
		final String $METHOD_NAME = "findDataEnd";
		int endIndex;
		this.index = this.answer.length - 1;
		if (this.endByte_1 != 0x00) {//two char line ending CR/LF
			while (this.index >= 0 && !((this.endByte_1 != 0x00 || this.answer[this.index - 1] == this.endByte_1) && this.answer[this.index] == this.endByte))
				--this.index;
		}
		else {//this.endByte_1 == 0x00 -> single line end character
			while (this.index >= 0 && !(this.answer[this.index] == this.endByte))
				--this.index;
		}
		
		if (this.index >= 0 && (this.tmpData.length + this.index - startIndex) > 4) {
			endIndex = this.index > 1 && this.endByte_1 != 0x00 && this.answer[this.index] == this.endByte ? this.index-=1 : this.index;
			this.data = new byte[this.tmpData.length + endIndex - startIndex];
			if (log.isLoggable(Level.FINER)) log.log(Level.FINER, this.tmpData.length + " + " + endIndex + " - " + startIndex);
			System.arraycopy(this.tmpData, 0, this.data, 0, this.tmpData.length);
			System.arraycopy(this.answer, startIndex, this.data, this.tmpData.length, endIndex - startIndex);
			if (log.isLoggable(Level.OFF)) {
				log.logp(Level.OFF, ET5410SerialPort.$CLASS_NAME, $METHOD_NAME, new String(this.data));
				String[] results = new String(this.data).split("\r\n");
				for (String result : results) {
					if (result.startsWith(this.device.getDataBlockLeader()))
						log.logp(Level.FINER, ET5410SerialPort.$CLASS_NAME, $METHOD_NAME, result);
				}
			}
			return this.data;
		}
		//endIndex not found, save temporary data, read new data
		log.log(Level.INFO,"endIndex not found, save temporary data, read new data " );
		this.data = new byte[this.tmpData.length];
		System.arraycopy(this.tmpData, 0, this.data, 0, this.data.length);

		this.tmpData = new byte[this.answer.length - startIndex + this.data.length];
		System.arraycopy(this.data, 0, this.tmpData, 0, this.data.length);
		System.arraycopy(this.answer, startIndex, this.tmpData, this.data.length, this.answer.length - startIndex);

		this.isDataReceived = false;
		readNewData();
		findDataEnd(this.index = 0);
		
		return this.data;
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
			if (log.isLoggable(Level.FINE))
					log.log(Level.FINE, StringHelper.byte2Hex2CharString(this.answer, this.answer.length));
		}
	}

}
