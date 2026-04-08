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
    
    Copyright (c) 2026 Winfried Bruegmann
****************************************************************************************/
package gde.device.unitrend;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.logging.Logger;

import javax.xml.bind.JAXBException;

import org.eclipse.swt.SWT;

import gde.GDE;
import gde.data.Channel;
import gde.data.Channels;
import gde.device.DeviceConfiguration;
import gde.exception.ApplicationConfigurationException;
import gde.exception.SerialPortException;
import gde.log.Level;
import gde.messages.Messages;
import gde.utils.StringHelper;

public class UT71D extends UniTrend {
	final static Logger					log1						= Logger.getLogger(UT71D.class.getName());

	public UT71D(String deviceProperties) throws FileNotFoundException, JAXBException {
		super(deviceProperties);
	}

	public UT71D(DeviceConfiguration deviceConfig) {
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
		
		points[0] = Integer.valueOf(String.format("%c%c%c%c%c", dataBuffer[0], dataBuffer[1], dataBuffer[2], dataBuffer[3], dataBuffer[4])).intValue();
		points[0] = dataBuffer[0] == 0x3B ? points[0] * -1 : points[0];
		
		if (log1.isLoggable(Level.FINE)) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < 5; i++) {
				sb.append(String.format("%02x", dataBuffer[i])).append(" "); //$NON-NLS-1$ //$NON-NLS-2$
			}
			log1.log(Level.FINE, sb.toString());
			log1.log(Level.FINE, "Bereich (Byte[5]): " + dataBuffer[5]);
			log1.log(Level.FINE, "Value   (Wert)   : " + points[0]);
		}
		
		switch (dataBuffer[6]) { //Messart
		default:
		case 48: //	'0'	mV~
		case 51: //	'3'	mV=
			switch (dataBuffer[5]) { //Bereich
			case 48:
				points[0] *= 10;
				break;
			default:
				break;
			}
			break;
		case 49: //	'1'	V=
		case 50: //	'2'	V~
			switch (dataBuffer[5]) { //Bereich
			case 49:
				points[0] /= 10;
				break;
			case 50:
			default:
				break;
			case 51:
				points[0] *= 10;
				break;
			case 52:
				points[0] *= 100;
				break;
			}
			break;
		case 52: //	'4'	Ω
			switch (dataBuffer[5]) { //Bereich
			case 49:
				points[0] *= 10;
				break;
			case 50:
				points[0] /= 10;
				break;
			case 51:
				points[0] = points[0];
				break;
			case 52:
				points[0] *= 10;
				break;
			case 53:
				points[0] /= 10;
				break;
			case 54:
				points[0] = points[0];
				break;
			case 55:
				points[0] *= 10;
				break;
			default:
				break;
			}
			break;
		case 53: //	'5'	F
			switch (dataBuffer[5]) { //Bereich
			case 48:
			case 49:
			case 52:
			case 55:
			default:
				break;
			case 50:
			case 53:
				points[0] *= 10;
				break;
			case 51:
			case 54:
				points[0] /= 10;
				break;
			}
			break;
		case 54: //	'6'	°C
		case 61: //	0x3D	°F
			switch (dataBuffer[5]) { //Bereich
			default:
			case 48:
				points[0] *= 100;
				break;
			}
			break;
		case 55: //	'7'	µA
			switch (dataBuffer[5]) { //Bereich
			case 48:
				points[0] *= 10;
				break;
			case 49:
				points[0] *= 100;
				break;
			default:
				break;
			}
			break;
		case 56: //	'8'	mA
			switch (dataBuffer[5]) { //Bereich
			case 49:
				points[0] *= 10;
				break;
			default:
				break;
			}
			break;
		case 57: //	'9'	A
			switch (dataBuffer[5]) { //Bereich
			default:
				break;
			}
			break;
		case 58: //	0x3A	Ω (Pieps, Durchgangsprüfung)
			switch (dataBuffer[5]) { //Bereich
			case 48:
				points[0] *= 10;
				break;
			default:
				break;
			}
			break;
		case 59: //	0x3B	V (Diodenmessung)
			switch (dataBuffer[5]) { //Bereich
			case 48:
				points[0] /= 10;
				break;
			default:
				break;
			}
			break;
		case 60: //	0x3C	Hz (oder Tastverhältnis bei gesetztem NEG-Flag)
			switch (dataBuffer[5]) { //Bereich
			case 48:
			case 51:
			case 54:
			default:
				break;
			case 50:
			case 53:
				points[0] /= 10;
				break;
			case 52:
			case 55:
				points[0] *= 10;
				break;
			}
			break;
		case 62: //	0x3E	W (keine Übertragung von Scheinleistung, Spannung, Strom, Frequenz und cosφ) - nur UT71E
			switch (dataBuffer[5]) { //Bereich
			default:
				break;
			}
			break;
		case 63: //	0x3F	% (4-20-mA-Tester)
			switch (dataBuffer[5]) { //Bereich
			case 48:
				points[0] *= 10;
				break;
			default:
				break;
			}
			break;
		}

		if ((dataBuffer[8] & 0x04) > 0) points[0] *= -1;

		return points;
	}

	/**
	 * get measurement info (type, symbol, unit)
	 * @param buffer
	 * @return measurement unit as string
	 */
	@Override
	public HashMap<String, String> getMeasurementInfo(byte[] buffer, HashMap<String, String> measurementInfo) {
		if (log1.isLoggable(Level.FINE)) {
			log1.log(Level.FINE, "buffer : " + StringHelper.byte2Hex4CharString(buffer, buffer.length));
			log1.log(Level.FINE, "Bereich (Byte[5]): " + buffer[5]);
			log1.log(Level.FINE, "Messart (Byte[6]): " + buffer[6]);
			log1.log(Level.FINE, "Kopplung(Byte[7]): " + buffer[7]);
			log1.log(Level.FINE, "Info    (Byte[8]): " + buffer[8]);
			log1.log(Level.FINE, "Low (Byte[0] == 0x3C): " + (buffer[0] == 0x3C));
			log1.log(Level.FINE, "High(Byte[0] == 0x3F): " + (buffer[0] == 0x3F));
		}
		if (buffer[0] == 0x3C || buffer[0] == 0x3F) {// 0x3A = ' ', 0x3B = '-', 0x3C = 'L', 0x3F = 'H'
			application.setStatusMessage(Messages.getString(MessageIds.GDE_MSGW1500), SWT.COLOR_RED);
			return measurementInfo;
		}
		application.setStatusMessage("");
		
		String unit = ""; //$NON-NLS-1$
		switch (buffer[6]) {
		default:
		case 48: //	'0'	mV~
		unit = "mV~";
			break;
		case 49: //	'1'	V=
		unit = "V=";
			break;
		case 50: //	'2'	V~
		unit = "V~";
			break;
		case 51: //	'3'	mV=
		unit = "mV=";
			break;
		case 53: //	'5'	F
			switch  (buffer[5]) {
			case 49:
			case 50:
				unit = "nF";
				break;
			case 51:
			case 52:
			case 53:
			default:
				unit = "µF";
				break;
			case 54:
			case 55:
				unit = "mF";
				break;
			}
			break;
		case 54: //	'6'	°C
		unit = "°C";
			break;
		case 55: //	'7'	µA
		unit = "µA";
			break;
		case 56: //	'8'	mA
		unit = "mA";
			break;
		case 57: //	'9'	A
		unit = "A";
			break;
		case 52: //	'4'	Ω
		case 58: //	0x3A	Ω (Pieps, Durchgangsprüfung)
			switch (buffer[5]) {
			default:
			case 48:
			case 49:
				unit = "Ω";
				break;
			case 50:
			case 51:
			case 52:
				unit = "kΩ";
				break;
			case 53:
			case 54:
			case 55:
				unit = "MΩ";
				break;
			}
			break;
		case 59: //	0x3B	V (Diodenmessung)
		unit = "V";
			break;
		case 60: //	0x3C	Hz (oder Tastverhältnis bei gesetztem NEG-Flag)
		switch (buffer[5]) {
		case 50:
		case 51:
			unit = "kHz";
			break;
		case 53:
		case 54:
		case 55:
			unit = "MHz";
			break;
		case 48:
		case 49:
		case 52:
		default:
			unit = "Hz";
			break;
		}
			break;
		case 61: //	0x3D	°F
		unit = "°F";
			break;
		case 62: //	0x3E	W (keine Übertragung von Scheinleistung, Spannung, Strom, Frequenz und cosφ) - nur UT71E
		unit = "W";
			break;
		case 63: //	0x3F	% (4-20-mA-Tester)
		unit = "%";
			break;
		}

		measurementInfo.put(UniTrend.INPUT_UNIT, unit);

		String typeSymbol = Messages.getString(MessageIds.GDE_MSGT1500);	//unknown
		if (unit.contains("V")) //$NON-NLS-1$
			typeSymbol = Messages.getString(MessageIds.GDE_MSGT1501);	//Spannung U
		else if (unit.endsWith("A")) //$NON-NLS-1$
			typeSymbol = Messages.getString(MessageIds.GDE_MSGT1503);	//Strom I
		else if (unit.endsWith("Ω")) //$NON-NLS-1$
			typeSymbol = Messages.getString(MessageIds.GDE_MSGT1504);	//Widerstand R
		else if (unit.endsWith("F")) //$NON-NLS-1$
			typeSymbol = Messages.getString(MessageIds.GDE_MSGT1505);	//Kapazität C
		else if (unit.endsWith("Hz")) //$NON-NLS-1$
			if (buffer[0] == 0x3B) // negative
				typeSymbol = Messages.getString(MessageIds.GDE_MSGT1537);	//Tastverhältnis v
			else
				typeSymbol = Messages.getString(MessageIds.GDE_MSGT1506);	//Frequenz F
		else if (unit.endsWith("°C")) //$NON-NLS-1$
			typeSymbol = Messages.getString(MessageIds.GDE_MSGT1507);	//Temperatur T
		else if (unit.endsWith("°F")) //$NON-NLS-1$
			typeSymbol = Messages.getString(MessageIds.GDE_MSGT1507);	//Temperatur T
		else if (unit.endsWith("W")) //$NON-NLS-1$
			typeSymbol = Messages.getString(MessageIds.GDE_MSGT1538);	//Leistung P
		else if (unit.endsWith("%")) //$NON-NLS-1$
			typeSymbol = Messages.getString(MessageIds.GDE_MSGT1537); //Tastverhältnis v 
		
		if (log1.isLoggable(Level.FINE)) {
			log1.log(Level.FINE, "unit  : " + unit);
			log1.log(Level.FINE, "type  : " + typeSymbol.split(" ")[0]);
			log1.log(Level.FINE, "symbol: " + typeSymbol.split(" ")[1]);
		}

		try {
			measurementInfo.put(UniTrend.INPUT_TYPE, typeSymbol.split(" ")[0]); //$NON-NLS-1$
			measurementInfo.put(UniTrend.INPUT_SYMBOL, typeSymbol.split(" ")[1]); //$NON-NLS-1$
		}
		catch (Exception e) {
			log1.log(Level.WARNING, e.getMessage());
		}

		return measurementInfo;
	}

	/**
	 * query battery voltage level
	 * @param buffer
	 * @return true if battery voltage level detected as low
	 */
	@Override
	public boolean isBatteryLevelLow(byte[] buffer) {
		return false;
	}

	/**
	 * get the measurement mode
	 * @param buffer
	 * @return the measurement mode key
	 */
	@Override
	public String getMode(byte[] buffer) {
		String mode;
		if ((buffer[8] & 0x01) > 0)
			mode = Messages.getString(MessageIds.GDE_MSGT1511);	//AUTO	
		else
			mode = Messages.getString(MessageIds.GDE_MSGT1510);	//Manual

		if ((buffer[7] & 0x01) > 0)
			mode += Messages.getString(MessageIds.GDE_MSGT1512); //AC
		else if ((buffer[7] & 0x02) > 0) mode += Messages.getString(MessageIds.GDE_MSGT1513); // DC

		return mode;
	}

	/**
	 * method toggle open close serial port or start/stop gathering data from device
	 */
	@Override
	public void open_closeCommPort() {
		if (this.serialPort != null) {
			if (!this.serialPort.isConnected()) {
				try {
					Channel activChannel = Channels.getInstance().getActiveChannel();
					if (activChannel != null) {
						this.getDialog().dataGatherThread = new GathererThread(this.application, this, this.serialPort, activChannel.getNumber(), this.getDialog());
						try {
							if (this.serialPort.isConnected()) {
								this.getDialog().dataGatherThread.start();
							}
						}
						catch (RuntimeException e) {
							log1.log(Level.WARNING, e.getMessage(), e);
						}
						if (this.getDialog().boundsComposite != null && !this.getDialog().isDisposed()) this.getDialog().boundsComposite.redraw();
					}
				}
				catch (SerialPortException e) {
					log1.log(Level.SEVERE, e.getMessage(), e);
					this.application.openMessageDialog(this.dialog.getDialogShell(),
							Messages.getString(gde.messages.MessageIds.GDE_MSGE0015, new Object[] { e.getClass().getSimpleName() + GDE.STRING_BLANK_COLON_BLANK + e.getMessage() }));
				}
				catch (ApplicationConfigurationException e) {
					log1.log(Level.SEVERE, e.getMessage(), e);
					this.application.openMessageDialog(this.dialog.getDialogShell(), Messages.getString(gde.messages.MessageIds.GDE_MSGE0010));
					this.application.getDeviceSelectionDialog().open();
				}
			}
			else {
				if (this.getDialog().dataGatherThread != null) {
					this.getDialog().dataGatherThread.stopDataGatheringThread(false);
				}
				if (this.getDialog().boundsComposite != null && !this.getDialog().isDisposed()) this.getDialog().boundsComposite.redraw();
				this.serialPort.close();
			}
		}
	}
}
