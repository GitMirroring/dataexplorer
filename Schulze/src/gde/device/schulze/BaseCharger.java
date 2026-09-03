package gde.device.schulze;

import java.io.FileNotFoundException;
import java.util.logging.Logger;

import javax.xml.bind.JAXBException;

import gde.GDE;
import gde.comm.DeviceCommPort;
import gde.config.Settings;
import gde.device.DeviceConfiguration;
import gde.device.IDevice;
import gde.log.Level;
import gde.messages.Messages;
import gde.ui.DataExplorer;

public abstract class BaseCharger extends DeviceConfiguration implements IDevice {
	final static Logger								log					= Logger.getLogger(BaseCharger.class.getName());
	
	final DataExplorer								application;
	final Settings										settings;
	public SchulzeSerialPort				serialPort;
	public GathererThread					dataGatherThread;

	public BaseCharger(String deviceProperties) throws FileNotFoundException, JAXBException {
		super(deviceProperties);
		this.application	= DataExplorer.getInstance();
		this.settings		= Settings.getInstance();
		// initializing the resource bundle for this device
		Messages.setDeviceResourceBundle("gde.device.schulze.messages", this.settings.getLocale(), this.getClass().getClassLoader()); //$NON-NLS-1$
		this.serialPort = new SchulzeSerialPort(this, this.application);
		if (this.application.getMenuToolBar() != null) this.configureSerialPortMenu(DeviceCommPort.ICON_SET_START_STOP, GDE.STRING_EMPTY, GDE.STRING_EMPTY);
	}

	public BaseCharger(DeviceConfiguration deviceConfig) {
		super(deviceConfig);
		this.application	= DataExplorer.getInstance();
		this.settings		= Settings.getInstance();
		// initializing the resource bundle for this device
		Messages.setDeviceResourceBundle("gde.device.schulze.messages", this.settings.getLocale(), this.getClass().getClassLoader()); //$NON-NLS-1$
		this.serialPort = new SchulzeSerialPort(this, this.application);
		if (this.application.getMenuToolBar() != null) this.configureSerialPortMenu(DeviceCommPort.ICON_SET_START_STOP, GDE.STRING_EMPTY, GDE.STRING_EMPTY);
	}

	/**
	 *    <property name="Datenaufnahme" value="0" type="Integer"/>
        <property name="Laden" value="1" type="Integer"/>
        <property name="Laden" value="2" type="Integer"/>
        <property name="Laden CC-&gt;CV" value="3" type="Integer"/>
        <property name="Laden CC-&gt;CV" value="4" type="Integer"/>
        <property name="Entladen" value="5" type="Integer"/>
        <property name="Entladen" value="6" type="Integer"/>
        <property name="Entladen-Rückspeisen" value="7" type="Integer"/>
        <property name="Entladen-Rückspeisen" value="8" type="Integer"/>

	 * @param c
	 * @return
	 */
	protected int getProcessingState(char c) {
		int state;
		switch (c) {
		case '0':
		case '1':
		case 'l':
		case 'L':
		case 'o':
		case 'O':
		case 'p':
		case 'P':
			state = 1; //charge
			break;

		case 'e':
		case 'E':
		case 'r':
		case 'R':
			state = 3; //discharge
			break;

		default:
			state = 0; //inactive
			break;
		}
		return state;
	}

	/**
	 * set data line end points - this method will be called within getConvertedLovDataBytes only and requires to set startPos and crlfPos to zero before first call
	 * - data line start is defined with '$ ;'
	 * - end position is defined with '0d0a' (CRLF)
	 * @param dataBuffer
	 * @param startPos
	 * @param crlfPos
	 */
	protected void setDataLineStartAndLength(byte[] dataBuffer, int[] refStartLength) {
		final byte					startByte1 = '1';
		final byte					startByte2 = '2';
		final byte					startByteTrailer = ':';
		log.log(Level.OFF, new String(dataBuffer));

		int startPos = refStartLength[0];
		byte[] lineSep = this.getDataBlockEnding();
		
		//find start index 1: 2:
		while (startPos < dataBuffer.length-1 
				&& !((dataBuffer[startPos] == startByte1 && dataBuffer[startPos+1] == startByteTrailer) 
				||  (dataBuffer[startPos] == startByte2 && dataBuffer[startPos+1] == startByteTrailer)))
			++startPos;

		int crlfPos = refStartLength[0] = startPos;

		for (; crlfPos < dataBuffer.length - 1; ++crlfPos) {
			if (dataBuffer[crlfPos] == lineSep[0] || dataBuffer[crlfPos + 1] == lineSep[1]) break; //0d0a (CRLF)
		}
		refStartLength[1] = crlfPos - startPos;
	}

}
