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

    Copyright (c) 2011,2012,2013,2014,2015,2016,2017,2018,2019 Winfried Bruegmann
****************************************************************************************/
package gde.device.graupner;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Vector;
import java.util.function.Supplier;
import java.util.logging.Logger;

import javax.xml.bind.JAXBException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import gde.Analyzer;
import gde.GDE;
import gde.comm.DeviceCommPort;
import gde.data.Channel;
import gde.data.Record;
import gde.data.RecordSet;
import gde.device.ChannelPropertyTypes;
import gde.device.DeviceConfiguration;
import gde.device.IDevice;
import gde.device.MeasurementPropertyTypes;
import gde.device.graupner.hott.MessageIds;
import gde.exception.DataInconsitsentException;
import gde.exception.DataTypeException;
import gde.histo.cache.VaultCollector;
import gde.histo.device.IHistoDevice;
import gde.histo.device.UniversalSampler;
import gde.histo.utils.PathUtils;
import gde.io.DataParser;
import gde.io.FileHandler;
import gde.log.Level;
import gde.messages.Messages;
import gde.utils.FileUtils;
import gde.utils.GPSHelper;
import gde.utils.ObjectKeyCompliance;
import gde.utils.StringHelper;
import gde.utils.WaitTimer;

/**
 * Sample device class, used as template for new device implementations
 * @author Winfried Brügmann
 */
public class HoTTAdapter2 extends HoTTAdapter implements IDevice, IHistoDevice {
	final static Logger			log											= Logger.getLogger(HoTTAdapter2.class.getName());

	public static final int	CHANNELS_CHANNEL_NUMBER	= 4;

	/**
	 * constructor using properties file
	 * @throws JAXBException
	 * @throws FileNotFoundException
	 */
	public HoTTAdapter2(String deviceProperties) throws FileNotFoundException, JAXBException {
		super(deviceProperties);
		if (this.application.getMenuToolBar() != null) {
			String toolTipText = HoTTAdapter.getImportToolTip();
			this.configureSerialPortMenu(DeviceCommPort.ICON_SET_IMPORT_CLOSE, toolTipText, toolTipText);
			updateFileExportMenu(this.application.getMenuBar().getExportMenu());
			updateFileImportMenu(this.application.getMenuBar().getImportMenu());
		}

		setPickerParameters();
	}

	/**
	 * constructor using existing device configuration
	 * @param deviceConfig device configuration
	 */
	public HoTTAdapter2(DeviceConfiguration deviceConfig) {
		super(deviceConfig);
		if (this.application.getMenuToolBar() != null) {
			String toolTipText = HoTTAdapter.getImportToolTip();
			this.configureSerialPortMenu(DeviceCommPort.ICON_SET_IMPORT_CLOSE, toolTipText, toolTipText);
			updateFileExportMenu(this.application.getMenuBar().getExportMenu());
			updateFileImportMenu(this.application.getMenuBar().getImportMenu());
		}

		setPickerParameters();
	}

	private void setPickerParameters() {
		this.pickerParameters.isChannelsChannelEnabled = this.getChannelProperty(ChannelPropertyTypes.ENABLE_CHANNEL) != null && this.getChannelProperty(ChannelPropertyTypes.ENABLE_CHANNEL).getValue() != ""
				? Boolean.parseBoolean(this.getChannelProperty(ChannelPropertyTypes.ENABLE_CHANNEL).getValue()) : false;
		this.pickerParameters.isFilterEnabled = this.getChannelProperty(ChannelPropertyTypes.ENABLE_FILTER) != null && this.getChannelProperty(ChannelPropertyTypes.ENABLE_FILTER).getValue() != ""
				? Boolean.parseBoolean(this.getChannelProperty(ChannelPropertyTypes.ENABLE_FILTER).getValue()) : true;
		this.pickerParameters.isFilterTextModus = this.getChannelProperty(ChannelPropertyTypes.TEXT_MODE) != null && this.getChannelProperty(ChannelPropertyTypes.TEXT_MODE).getValue() != ""
				? Boolean.parseBoolean(this.getChannelProperty(ChannelPropertyTypes.TEXT_MODE).getValue()) : false;
		this.pickerParameters.isTolerateSignChangeLatitude = this.getMeasruementProperty(application.getActiveChannelNumber(), 12, MeasurementPropertyTypes.TOLERATE_SIGN_CHANGE.value()) != null
				? Boolean.parseBoolean(this.getMeasruementProperty(application.getActiveChannelNumber(), 12, MeasurementPropertyTypes.TOLERATE_SIGN_CHANGE.value()).getValue()) : false;
		this.pickerParameters.isTolerateSignChangeLongitude = this.getMeasruementProperty(application.getActiveChannelNumber(), 13, MeasurementPropertyTypes.TOLERATE_SIGN_CHANGE.value()) != null
				? Boolean.parseBoolean(this.getMeasruementProperty(application.getActiveChannelNumber(), 13, MeasurementPropertyTypes.TOLERATE_SIGN_CHANGE.value()).getValue()) : false;
		this.pickerParameters.latitudeToleranceFactor = this.getMeasurementPropertyValue(application.getActiveChannelNumber(), 12, MeasurementPropertyTypes.FILTER_FACTOR.value()).toString().length() > 0
				? Double.parseDouble(this.getMeasurementPropertyValue(application.getActiveChannelNumber(), 12, MeasurementPropertyTypes.FILTER_FACTOR.value()).toString()) : 90.0;
		this.pickerParameters.longitudeToleranceFactor = this.getMeasurementPropertyValue(application.getActiveChannelNumber(), 13, MeasurementPropertyTypes.FILTER_FACTOR.value()).toString().length() > 0
				? Double.parseDouble(this.getMeasurementPropertyValue(application.getActiveChannelNumber(), 13, MeasurementPropertyTypes.FILTER_FACTOR.value()).toString()) : 25.0;
	}

	/**
	 * convert the device bytes into raw values, no calculation will take place here, see translateValue reverseTranslateValue
	 * inactive or to be calculated data point are filled with 0 and needs to be handles after words
	 * @param points pointer to integer array to be filled with converted data
	 * @param dataBuffer byte array with the data to be converted
	 */
	@Override
	public int[] convertDataBytes(int[] points, byte[] dataBuffer) {
		int maxVotage = Integer.MIN_VALUE;
		int minVotage = Integer.MAX_VALUE;
		int tmpHeight, tmpClimb3, tmpClimb10, tmpCapacity, tmpVoltage, tmpCurrent, tmpRevolution, tmpCellVoltage, tmpVoltage1, tmpVoltage2, tmpLatitudeGrad, tmpLongitudeGrad, tmpPackageLoss, tmpVoltageRx,
				tmpTemperatureRx;

		switch (this.serialPort.protocolType) {
		case TYPE_19200_V3:
			switch (dataBuffer[1]) {
			case HoTTAdapter2.SENSOR_TYPE_RECEIVER_19200:
				if (dataBuffer.length == 17) {
					//0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
					points[0] = 0; // Rx->Tx PLoss
					points[1] = (dataBuffer[9] & 0xFF) * 1000;
					points[2] = (dataBuffer[5] & 0xFF) * 1000;
					points[3] = DataParser.parse2Short(dataBuffer, 11) * 1000;
					points[4] = (dataBuffer[13] & 0xFF) * -1000;
					points[5] = (dataBuffer[9] & 0xFF) * -1000;
					points[6] = (dataBuffer[6] & 0xFF) * 1000;
					points[7] = ((dataBuffer[7] & 0xFF) - 20) * 1000;
				}
				break;

			case HoTTAdapter2.SENSOR_TYPE_VARIO_19200:
				if (dataBuffer.length == 31) {
					//0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
					//10=Height, 11=Climb 1, 12=Climb 3, 13=Climb 10 14=EventVario
					points[10] = (DataParser.parse2Short(dataBuffer, 16) - 500) * 1000;
					points[11] = (DataParser.parse2Short(dataBuffer, 22) - 30000) * 10;
					points[12] = (DataParser.parse2Short(dataBuffer, 24) - 30000) * 10;
					points[13] = (DataParser.parse2Short(dataBuffer, 26) - 30000) * 10;
				}
				break;

			case HoTTAdapter2.SENSOR_TYPE_GPS_19200:
				if (dataBuffer.length == 40) {
					//0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
					//10=Height, 11=Climb 1, 12=Climb 3, 13=Climb 10 14=EventVario
					//15=Latitude, 16=Longitude, 17=Velocity, 18=DistanceStart, 19=DirectionStart, 20=TripDistance 21=NumSatellites 22=GPS-Fix 23=EventGPS
					points[15] = DataParser.parse2Short(dataBuffer, 20) * 10000 + DataParser.parse2Short(dataBuffer, 22);
					points[15] = dataBuffer[19] == 1 ? -1 * points[15] : points[15];
					points[16] = DataParser.parse2Short(dataBuffer, 25) * 10000 + DataParser.parse2Short(dataBuffer, 27);
					points[16] = dataBuffer[24] == 1 ? -1 * points[16] : points[16];
					points[10] = (DataParser.parse2Short(dataBuffer, 31) - 500) * 1000;
					points[11] = (DataParser.parse2Short(dataBuffer, 33) - 30000) * 10;
					points[12] = ((dataBuffer[35] & 0xFF) - 120) * 1000;
					points[17] = DataParser.parse2Short(dataBuffer, 17) * 1000;
					points[18] = DataParser.parse2Short(dataBuffer, 29) * 1000;
					points[19] = (dataBuffer[16] & 0xFF) * 1000;
					points[20] = 0;
				}
				break;

			case HoTTAdapter2.SENSOR_TYPE_GENERAL_19200:
				if (dataBuffer.length == 48) {
					//0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
					//10=Height, 11=Climb 1, 12=Climb 3, 13=Climb 10 14=EventVario
					//15=Latitude, 16=Longitude, 17=Velocity, 18=DistanceStart, 19=DirectionStart, 20=TripDistance 21=NumSatellites 22=GPS-Fix 23=EventGPS
					//24=Voltage G, 25=Current G, 26=Capacity G, 27=Power G, 28=Balance G, 29=CellVoltage G1, 30=CellVoltage G2 .... 34=CellVoltage G6, 35=Revolution G, 36=FuelLevel, 37=Voltage G1, 38=Voltage G2, 39=Temperature G1, 40=Temperature G2 41=Speed G, 42=LowestCellVoltage, 43=LowestCellNumber, 44=Pressure, 45=Event G
					points[24] = DataParser.parse2Short(dataBuffer, 40) * 1000;
					points[25] = DataParser.parse2Short(dataBuffer, 38) * 1000;
					points[26] = DataParser.parse2Short(dataBuffer, 42) * 1000;
					points[27] = Double.valueOf(points[1] / 1000.0 * points[2]).intValue(); // power U*I [W];
					for (int j = 0; j < 6; j++) {
						points[j + 29] = (dataBuffer[23 + j] & 0xFF) * 1000;
						if (points[j + 29] > 0) {
							maxVotage = points[j + 29] > maxVotage ? points[j + 29] : maxVotage;
							minVotage = points[j + 29] < minVotage ? points[j + 29] : minVotage;
						}
					}
					//calculate balance on the fly
					points[28] = (maxVotage != Integer.MIN_VALUE && minVotage != Integer.MAX_VALUE ? maxVotage - minVotage : 0);
					points[35] = DataParser.parse2Short(dataBuffer, 31) * 1000;
					points[10] = (DataParser.parse2Short(dataBuffer, 33) - 500) * 1000;
					points[11] = (DataParser.parse2Short(dataBuffer, 35) - 30000) * 10;
					points[12] = ((dataBuffer[37] & 0xFF) - 120) * 1000;
					points[36] = DataParser.parse2Short(dataBuffer, 29) * 1000;
					points[37] = DataParser.parse2Short(dataBuffer, 22) * 1000;
					points[38] = DataParser.parse2Short(dataBuffer, 24) * 1000;
					points[39] = ((dataBuffer[26] & 0xFF) - 20) * 1000;
					points[40] = ((dataBuffer[27] & 0xFF) - 20) * 1000;
				}
				break;

			case HoTTAdapter2.SENSOR_TYPE_ELECTRIC_19200:
				if (dataBuffer.length == 51) {
					//0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
					//10=Height, 11=Climb 1, 12=Climb 3, 13=Climb 10 14=EventVario
					//15=Latitude, 16=Longitude, 17=Velocity, 18=DistanceStart, 19=DirectionStart, 20=TripDistance 21=NumSatellites 22=GPS-Fix 23=EventGPS
					//24=Voltage G, 25=Current G, 26=Capacity G, 27=Power G, 28=Balance G, 29=CellVoltage G1, 30=CellVoltage G2 .... 34=CellVoltage G6, 35=Revolution G, 36=FuelLevel, 37=Voltage G1, 38=Voltage G2, 39=Temperature G1, 40=Temperature G2 41=Speed G, 42=LowestCellVoltage, 43=LowestCellNumber, 44=Pressure, 45=Event G
					//46=Voltage E, 47=Current E, 48=Capacity E, 49=Power E, 50=Balance E, 51=CellVoltage E1, 52=CellVoltage E2 .... 64=CellVoltage E14, 65=Voltage E1, 66=Voltage E2, 67=Temperature E1, 68=Temperature E2 69=Revolution E 70=MotorTime 71=Speed 72=Event E
					points[46] = DataParser.parse2Short(dataBuffer, 40) * 1000;
					points[47] = DataParser.parse2Short(dataBuffer, 38) * 1000;
					points[48] = DataParser.parse2Short(dataBuffer, 42) * 1000;
					points[49] = Double.valueOf(points[1] / 1000.0 * points[2]).intValue(); // power U*I [W];
					for (int j = 0; j < 14; j++) {
						points[j + 51] = (dataBuffer[40 + j] & 0xFF) * 1000;
						if (points[j + 51] > 0) {
							maxVotage = points[j + 51] > maxVotage ? points[j + 51] : maxVotage;
							minVotage = points[j + 51] < minVotage ? points[j + 51] : minVotage;
						}
					}
					//calculate balance on the fly
					points[50] = (maxVotage != Integer.MIN_VALUE && minVotage != Integer.MAX_VALUE ? maxVotage - minVotage : 0);
					points[10] = (DataParser.parse2Short(dataBuffer, 36) - 500) * 1000;
					points[11] = (DataParser.parse2Short(dataBuffer, 44) - 30000) * 10;
					points[12] = ((dataBuffer[46] & 0xFF) - 120) * 1000;
					points[65] = DataParser.parse2Short(dataBuffer, 30) * 1000;
					points[66] = DataParser.parse2Short(dataBuffer, 32) * 1000;
					points[67] = ((dataBuffer[34] & 0xFF) - 20) * 1000;
					points[68] = ((dataBuffer[35] & 0xFF) - 20) * 1000;
				}
				break;
			}
			break;

		case TYPE_19200_V4:
			//0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
			tmpPackageLoss = DataParser.parse2Short(dataBuffer, 11);
			tmpVoltageRx = (dataBuffer[6] & 0xFF);
			tmpTemperatureRx = (dataBuffer[7] & 0xFF) - 20;
			if (!this.pickerParameters.isFilterEnabled || tmpPackageLoss > -1 && tmpVoltageRx > -1 && tmpVoltageRx < 100 && tmpTemperatureRx < 120) {
				points[0] = 0; //Rx->Tx PLoss
				points[1] = (dataBuffer[9] & 0xFF) * 1000;
				points[2] = (dataBuffer[5] & 0xFF) * 1000;
				points[3] = tmpPackageLoss * 1000;
				points[4] = (dataBuffer[13] & 0xFF) * 1000;
				points[5] = (dataBuffer[8] & 0xFF) * 1000;
				points[6] = tmpVoltageRx * 1000;
				points[7] = tmpTemperatureRx * 1000;
				points[8] = (dataBuffer[10] & 0xFF) * 1000;
			}
			switch (dataBuffer[1]) {

			case HoTTAdapter2.SENSOR_TYPE_VARIO_19200:
				if (dataBuffer.length == 57) {
					//0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
					//10=Height, 11=Climb 1, 12=Climb 3, 13=Climb 10 14=EventVario
					tmpHeight = DataParser.parse2Short(dataBuffer, 16) - 500;
					if (tmpHeight > -490 && tmpHeight < 5000) {
						points[10] = tmpHeight * 1000;
						points[11] = (DataParser.parse2Short(dataBuffer, 22) - 30000) * 10;
					}
					tmpClimb3 = DataParser.parse2Short(dataBuffer, 24) - 30000;
					tmpClimb10 = DataParser.parse2Short(dataBuffer, 26) - 30000;
					if (tmpClimb3 > -10000 && tmpClimb10 > -10000 && tmpClimb3 < 10000 && tmpClimb10 < 10000) {
						points[12] = tmpClimb3 * 10;
						points[13] = tmpClimb10 * 10;
					}
				}
				break;

			case HoTTAdapter2.SENSOR_TYPE_GPS_19200:
				if (dataBuffer.length == 57) {
					//0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
					//10=Height, 11=Climb 1, 12=Climb 3, 13=Climb 10 14=EventVario
					//15=Latitude, 16=Longitude, 17=Velocity, 18=DistanceStart, 19=DirectionStart, 20=TripDistance 21=NumSatellites 22=GPS-Fix 23=EventGPS
					tmpLatitudeGrad = DataParser.parse2Short(dataBuffer, 20);
					tmpLongitudeGrad = DataParser.parse2Short(dataBuffer, 25);
					tmpHeight = DataParser.parse2Short(dataBuffer, 31) - 500;
					tmpClimb3 = (dataBuffer[35] & 0xFF) - 120;
					if ((tmpLatitudeGrad == tmpLongitudeGrad || tmpLatitudeGrad > 0) && tmpHeight > -490 && tmpHeight < 5000 && tmpClimb3 > -50) {
						points[15] = tmpLatitudeGrad * 10000 + DataParser.parse2Short(dataBuffer, 22);
						points[15] = dataBuffer[20] == 1 ? -1 * points[15] : points[15];
						points[16] = tmpLongitudeGrad * 10000 + DataParser.parse2Short(dataBuffer, 27);
						points[16] = dataBuffer[25] == 1 ? -1 * points[16] : points[16];
						points[10] = tmpHeight * 1000;
						points[11] = (DataParser.parse2Short(dataBuffer, 33) - 30000) * 10;
						points[12] = tmpClimb3 * 1000;
						points[17] = DataParser.parse2Short(dataBuffer, 17) * 1000;
						points[18] = DataParser.parse2Short(dataBuffer, 29) * 1000;
						points[19] = (dataBuffer[38] & 0xFF) * 1000;
						points[20] = 0;
					}
				}
				break;

			case HoTTAdapter2.SENSOR_TYPE_GENERAL_19200:
				if (dataBuffer.length == 57) {
					//0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
					//10=Height, 11=Climb 1, 12=Climb 3, 13=Climb 10 14=EventVario
					//15=Latitude, 16=Longitude, 17=Velocity, 18=DistanceStart, 19=DirectionStart, 20=TripDistance 21=NumSatellites 22=GPS-Fix 23=EventGPS
					//24=Voltage G, 25=Current G, 26=Capacity G, 27=Power G, 28=Balance G, 29=CellVoltage G1, 30=CellVoltage G2 .... 34=CellVoltage G6, 35=Revolution G, 36=FuelLevel, 37=Voltage G1, 38=Voltage G2, 39=Temperature G1, 40=Temperature G2 41=Speed G, 42=LowestCellVoltage, 43=LowestCellNumber, 44=Pressure, 45=Event G
					tmpVoltage = DataParser.parse2Short(dataBuffer, 40);
					tmpCapacity = DataParser.parse2Short(dataBuffer, 42);
					tmpHeight = DataParser.parse2Short(dataBuffer, 33) - 500;
					tmpClimb3 = (dataBuffer[37] & 0xFF) - 120;
					tmpVoltage1 = DataParser.parse2Short(dataBuffer, 22);
					tmpVoltage2 = DataParser.parse2Short(dataBuffer, 24);
					if (tmpClimb3 > -50 && tmpHeight > -490 && tmpHeight < 5000 && Math.abs(tmpVoltage1) < 600 && Math.abs(tmpVoltage2) < 600 && tmpCapacity >= points[20] / 1000) {
						points[24] = tmpVoltage * 1000;
						points[25] = DataParser.parse2Short(dataBuffer, 38) * 1000;
						points[26] = tmpCapacity * 1000;
						points[27] = Double.valueOf(points[24] / 1000.0 * points[25]).intValue(); // power U*I [W];
						if (tmpVoltage > 0) {
							for (int j = 0; j < 6; j++) {
								tmpCellVoltage = (dataBuffer[16 + j] & 0xFF);
								points[j + 29] = tmpCellVoltage > 0 ? tmpCellVoltage * 1000 : points[j + 29];
								if (points[j + 29] > 0) {
									maxVotage = points[j + 29] > maxVotage ? points[j + 29] : maxVotage;
									minVotage = points[j + 29] < minVotage ? points[j + 29] : minVotage;
								}
							}
							//calculate balance on the fly
							points[28] = (maxVotage != Integer.MIN_VALUE && minVotage != Integer.MAX_VALUE ? maxVotage - minVotage : 0) * 10;
						}
						points[35] = DataParser.parse2Short(dataBuffer, 31) * 1000;
						points[10] = tmpHeight * 1000;
						points[11] = (DataParser.parse2Short(dataBuffer, 35) - 30000) * 10;
						points[12] = tmpClimb3 * 1000;
						points[36] = DataParser.parse2Short(dataBuffer, 29) * 1000;
						points[37] = tmpVoltage1 * 100;
						points[38] = tmpVoltage2 * 100;
						points[39] = ((dataBuffer[26] & 0xFF) - 20) * 1000;
						points[40] = ((dataBuffer[27] & 0xFF) - 20) * 1000;
					}
				}
				break;

			case HoTTAdapter2.SENSOR_TYPE_ELECTRIC_19200:
				if (dataBuffer.length == 57) {
					//0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
					//10=Height, 11=Climb 1, 12=Climb 3, 13=Climb 10 14=EventVario
					//15=Latitude, 16=Longitude, 17=Velocity, 18=DistanceStart, 19=DirectionStart, 20=TripDistance 21=NumSatellites 22=GPS-Fix 23=EventGPS
					//24=Voltage G, 25=Current G, 26=Capacity G, 27=Power G, 28=Balance G, 29=CellVoltage G1, 30=CellVoltage G2 .... 34=CellVoltage G6, 35=Revolution G, 36=FuelLevel, 37=Voltage G1, 38=Voltage G2, 39=Temperature G1, 40=Temperature G2 41=Speed G, 42=LowestCellVoltage, 43=LowestCellNumber, 44=Pressure, 45=Event G
					//46=Voltage E, 47=Current E, 48=Capacity E, 49=Power E, 50=Balance E, 51=CellVoltage E1, 52=CellVoltage E2 .... 64=CellVoltage E14, 65=Voltage E1, 66=Voltage E2, 67=Temperature E1, 68=Temperature E2 69=Revolution E 70=MotorTime 71=Speed 72=Event E
					tmpVoltage = DataParser.parse2Short(dataBuffer, 40);
					tmpCapacity = DataParser.parse2Short(dataBuffer, 42);
					tmpHeight = DataParser.parse2Short(dataBuffer, 36) - 500;
					tmpClimb3 = (dataBuffer[46] & 0xFF) - 120;
					tmpVoltage1 = DataParser.parse2Short(dataBuffer, 30);
					tmpVoltage2 = DataParser.parse2Short(dataBuffer, 32);
					if (tmpClimb3 > -50 && tmpHeight > -490 && tmpHeight < 5000 && Math.abs(tmpVoltage1) < 600 && Math.abs(tmpVoltage2) < 600 && tmpCapacity >= points[37] / 1000) {
						points[46] = tmpVoltage * 1000;
						points[47] = DataParser.parse2Short(dataBuffer, 38) * 1000;
						points[48] = tmpCapacity * 1000;
						points[49] = Double.valueOf(points[46] / 1000.0 * points[47]).intValue(); // power U*I [W];
						if (tmpVoltage > 0) {
							for (int j = 0; j < 14; j++) {
								tmpCellVoltage = (dataBuffer[16 + j] & 0xFF);
								points[j + 51] = tmpCellVoltage > 0 ? tmpCellVoltage * 1000 : points[j + 51];
								if (points[j + 51] > 0) {
									maxVotage = points[j + 51] > maxVotage ? points[j + 51] : maxVotage;
									minVotage = points[j + 51] < minVotage ? points[j + 51] : minVotage;
								}
							}
							//calculate balance on the fly
							points[50] = (maxVotage != Integer.MIN_VALUE && minVotage != Integer.MAX_VALUE ? maxVotage - minVotage : 0) * 10;
						}
						points[10] = tmpHeight * 1000;
						points[11] = (DataParser.parse2Short(dataBuffer, 44) - 30000) * 10;
						points[12] = tmpClimb3 * 1000;
						points[65] = tmpVoltage1 * 100;
						points[66] = tmpVoltage2 * 100;
						points[67] = ((dataBuffer[34] & 0xFF) - 20) * 1000;
						points[68] = ((dataBuffer[35] & 0xFF) - 20) * 1000;
						points[69] = DataParser.parse2Short(dataBuffer, 47) * 1000;
					}
				}
				break;

			case HoTTAdapter.SENSOR_TYPE_SPEED_CONTROL_19200:
				//0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
				//10=Height, 11=Climb 1, 12=Climb 3, 13=Climb 10 14=EventVario
				//15=Latitude, 16=Longitude, 17=Velocity, 18=DistanceStart, 19=DirectionStart, 20=TripDistance 21=NumSatellites 22=GPS-Fix 23=EventGPS
				//24=Voltage G, 25=Current G, 26=Capacity G, 27=Power G, 28=Balance G, 29=CellVoltage G1, 30=CellVoltage G2 .... 34=CellVoltage G6, 35=Revolution G, 36=FuelLevel, 37=Voltage G1, 38=Voltage G2, 39=Temperature G1, 40=Temperature G2 41=Speed G, 42=LowestCellVoltage, 43=LowestCellNumber, 44=Pressure, 45=Event G
				//46=Voltage E, 47=Current E, 48=Capacity E, 49=Power E, 50=Balance E, 51=CellVoltage E1, 52=CellVoltage E2 .... 64=CellVoltage E14, 65=Voltage E1, 66=Voltage E2, 67=Temperature E1, 68=Temperature E2 69=Revolution E 70=MotorTime 71=Speed 72=Event E
				//73=VoltageM, 74=CurrentM, 75=CapacityM, 76=PowerM, 77=RevolutionM, 78=TemperatureM 1, 79=TemperatureM 2 80=Voltage_min, 81=Current_max, 82=Revolution_max, 83=Temperature1_max, 84=Temperature2_max 85=Event M

				//73=Ch 1, 74=Ch 2, 75=Ch 3 .. 88=Ch 16, 89=PowerOff, 90=BatterieLow, 91=Reset, 92=reserve
				//93=VoltageM, 94=CurrentM, 95=CapacityM, 96=PowerM, 97=RevolutionM, 98=TemperatureM 1, 99=TemperatureM 2 100=Voltage_min, 101=Current_max, 102=Revolution_max, 103=Temperature1_max, 104=Temperature2_max 105=Event M
				if (dataBuffer.length == 57) {
					tmpVoltage = DataParser.parse2Short(dataBuffer, 17);
					tmpCurrent = DataParser.parse2Short(dataBuffer, 21);
					tmpRevolution = DataParser.parse2Short(dataBuffer, 25);
					if (this.application.getActiveChannelNumber() == 4) {
						if (!this.pickerParameters.isFilterEnabled || tmpVoltage > -1 && tmpVoltage < 1000 && tmpCurrent < 2550 && tmpRevolution > -1 && tmpRevolution < 2000) {
							//93=VoltageM, 94=CurrentM, 95=CapacityM, 96=PowerM, 97=RevolutionM, 98=TemperatureM 1, 99=TemperatureM 2 100=Voltage_min, 101=Current_max, 102=Revolution_max, 103=Temperature1_max, 104=Temperature2_max 105=Event M
							points[93] = tmpVoltage * 1000;
							points[94] = tmpCurrent * 1000;
							points[95] = DataParser.parse2Short(dataBuffer, 29) * 1000;
							points[96] = Double.valueOf(points[93] / 1000.0 * points[94]).intValue(); // power U*I [W];
							points[97] = tmpRevolution * 1000;
							points[98] = DataParser.parse2Short(dataBuffer, 33) * 1000;
						}
					}
					else {
						//73=VoltageM, 74=CurrentM, 75=CapacityM, 76=PowerM, 77=RevolutionM, 78=TemperatureM 1, 79=TemperatureM 2 80=Voltage_min, 81=Current_max, 82=Revolution_max, 83=Temperature1_max, 84=Temperature2_max 85=Event M
						if (!this.pickerParameters.isFilterEnabled || tmpVoltage > -1 && tmpVoltage < 1000 && tmpCurrent < 2550 && tmpRevolution > -1 && tmpRevolution < 2000) {
							points[73] = tmpVoltage * 1000;
							points[74] = tmpCurrent * 1000;
							points[75] = DataParser.parse2Short(dataBuffer, 29) * 1000;
							points[76] = Double.valueOf(points[73] / 1000.0 * points[74]).intValue(); // power U*I [W];
							points[77] = tmpRevolution * 1000;
							points[78] = DataParser.parse2Short(dataBuffer, 33) * 1000;
						}
					}
				}
				break;
			}
			break;

		case TYPE_115200:
			switch (dataBuffer[0]) {
			case HoTTAdapter2.SENSOR_TYPE_RECEIVER_115200:
				if (dataBuffer.length >= 21) {
					//0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
					tmpPackageLoss = DataParser.parse2Short(dataBuffer, 12);
					tmpVoltageRx = dataBuffer[15] & 0xFF;
					tmpTemperatureRx = DataParser.parse2Short(dataBuffer, 10);
					if (!this.pickerParameters.isFilterEnabled || tmpPackageLoss > -1 && tmpVoltageRx > -1 && tmpVoltageRx < 100 && tmpTemperatureRx < 100) {
						this.pickerParameters.reverseChannelPackageLossCounter.add((dataBuffer[5] & 0xFF) == 0 && (dataBuffer[4] & 0xFF) == 0 ? 0 : 1);
						points[0] = this.pickerParameters.reverseChannelPackageLossCounter.getPercentage() * 1000;
						points[1] = (dataBuffer[17] & 0xFF) * 1000;
						points[2] = (dataBuffer[14] & 0xFF) * 1000;
						points[3] = tmpPackageLoss * 1000;
						points[4] = (dataBuffer[5] & 0xFF) * 1000;
						points[5] = (dataBuffer[4] & 0xFF) * 1000;
						points[6] = tmpVoltageRx * 1000;
						points[7] = tmpTemperatureRx * 1000;
						points[8] = (dataBuffer[18] & 0xFF) * 1000;
					}
				}
				break;

			case HoTTAdapter2.SENSOR_TYPE_VARIO_115200:
				if (dataBuffer.length >= 25) {
					//0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
					//10=Height, 11=Climb 1, 12=Climb 3, 13=Climb 10 14=EventVario
					tmpHeight = DataParser.parse2Short(dataBuffer, 10);
					if (tmpHeight > -490 && tmpHeight < 5000) {
						points[10] = tmpHeight * 1000;
						points[11] = DataParser.parse2Short(dataBuffer, 16) * 10;
					}
					tmpClimb3 = DataParser.parse2Short(dataBuffer, 18);
					tmpClimb10 = DataParser.parse2Short(dataBuffer, 20);
					if (tmpClimb3 > -10000 && tmpClimb10 > -10000 && tmpClimb3 < 10000 && tmpClimb10 < 10000) {
						points[12] = tmpClimb3 * 10;
						points[13] = tmpClimb10 * 10;
					}
				}
				break;

			case HoTTAdapter2.SENSOR_TYPE_GPS_115200:
				if (dataBuffer.length >= 34) {
					//0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
					//10=Height, 11=Climb 1, 12=Climb 3, 13=Climb 10 14=EventVario
					//15=Latitude, 16=Longitude, 17=Velocity, 18=DistanceStart, 19=DirectionStart, 20=TripDistance 21=NumSatellites 22=GPS-Fix 23=EventGPS
					tmpLatitudeGrad = DataParser.parse2Short(dataBuffer, 16);
					tmpLongitudeGrad = DataParser.parse2Short(dataBuffer, 20);
					tmpHeight = DataParser.parse2Short(dataBuffer, 14);
					tmpClimb3 = dataBuffer[30];
					if ((tmpLatitudeGrad == tmpLongitudeGrad || tmpLatitudeGrad > 0) && tmpHeight > -490 && tmpHeight < 5000 && tmpClimb3 > -50) {
						points[15] = tmpLatitudeGrad * 10000 + DataParser.parse2Short(dataBuffer, 18);
						points[15] = dataBuffer[27] == 1 ? -1 * points[15] : points[15];
						points[16] = tmpLongitudeGrad * 10000 + DataParser.parse2Short(dataBuffer, 22);
						points[16] = dataBuffer[28] == 1 ? -1 * points[16] : points[16];
						points[10] = tmpHeight * 1000;
						points[11] = DataParser.parse2Short(dataBuffer, 28) * 10;
						points[12] = tmpClimb3 * 1000;
						points[17] = DataParser.parse2Short(dataBuffer, 10) * 1000;
						points[18] = DataParser.parse2Short(dataBuffer, 12) * 1000;
						points[19] = DataParser.parse2Short(dataBuffer, 24) * 500;
						points[20] = 0;
					}
				}
				break;

			case HoTTAdapter2.SENSOR_TYPE_GENERAL_115200:
				if (dataBuffer.length >= 49) {
					//0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
					//10=Height, 11=Climb 1, 12=Climb 3, 13=Climb 10 14=EventVario
					//15=Latitude, 16=Longitude, 17=Velocity, 18=DistanceStart, 19=DirectionStart, 20=TripDistance 21=NumSatellites 22=GPS-Fix 23=EventGPS
					//24=Voltage G, 25=Current G, 26=Capacity G, 27=Power G, 28=Balance G, 29=CellVoltage G1, 30=CellVoltage G2 .... 34=CellVoltage G6, 35=Revolution G, 36=FuelLevel, 37=Voltage G1, 38=Voltage G2, 39=Temperature G1, 40=Temperature G2 41=Speed G, 42=LowestCellVoltage, 43=LowestCellNumber, 44=Pressure, 45=Event G
					tmpVoltage = DataParser.parse2Short(dataBuffer, 36);
					tmpCapacity = DataParser.parse2Short(dataBuffer, 38);
					tmpHeight = DataParser.parse2Short(dataBuffer, 32);
					tmpClimb3 = dataBuffer[44];
					tmpVoltage1 = DataParser.parse2Short(dataBuffer, 22);
					tmpVoltage2 = DataParser.parse2Short(dataBuffer, 24);
					if (tmpClimb3 > -50 && tmpHeight > -490 && tmpHeight < 5000 && Math.abs(tmpVoltage1) < 600 && Math.abs(tmpVoltage2) < 600 && tmpCapacity >= points[20] / 1000) {
						points[24] = tmpVoltage * 1000;
						points[25] = DataParser.parse2Short(dataBuffer, 34) * 1000;
						points[26] = tmpCapacity * 1000;
						points[27] = Double.valueOf(points[24] / 1000.0 * points[25]).intValue(); // power U*I [W];
						if (tmpVoltage > 0) {
							for (int i = 0, j = 0; i < 6; i++, j += 2) {
								tmpCellVoltage = DataParser.parse2Short(dataBuffer, j + 10);
								points[i + 29] = tmpCellVoltage > 0 ? tmpCellVoltage * 500 : points[i + 29];
								if (points[i + 29] > 0) {
									maxVotage = points[i + 29] > maxVotage ? points[i + 29] : maxVotage;
									minVotage = points[i + 29] < minVotage ? points[i + 29] : minVotage;
								}
							}
							//calculate balance on the fly
							points[28] = (maxVotage != Integer.MIN_VALUE && minVotage != Integer.MAX_VALUE ? maxVotage - minVotage : 0) * 10;
						}
						points[35] = DataParser.parse2Short(dataBuffer, 30) * 1000;
						points[10] = tmpHeight * 1000;
						points[11] = DataParser.parse2Short(dataBuffer, 42) * 10;
						points[12] = tmpClimb3 * 1000;
						points[36] = DataParser.parse2Short(dataBuffer, 40) * 1000;
						points[37] = tmpVoltage1 * 100;
						points[38] = tmpVoltage2 * 100;
						points[39] = DataParser.parse2Short(dataBuffer, 26) * 1000;
						points[40] = DataParser.parse2Short(dataBuffer, 28) * 1000;
					}
				}
				break;

			case HoTTAdapter2.SENSOR_TYPE_ELECTRIC_115200:
				if (dataBuffer.length >= 60) {
					//0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
					//10=Height, 11=Climb 1, 12=Climb 3, 13=Climb 10 14=EventVario
					//15=Latitude, 16=Longitude, 17=Velocity, 18=DistanceStart, 19=DirectionStart, 20=TripDistance 21=NumSatellites 22=GPS-Fix 23=EventGPS
					//24=Voltage G, 25=Current G, 26=Capacity G, 27=Power G, 28=Balance G, 29=CellVoltage G1, 30=CellVoltage G2 .... 34=CellVoltage G6, 35=Revolution G, 36=FuelLevel, 37=Voltage G1, 38=Voltage G2, 39=Temperature G1, 40=Temperature G2 41=Speed G, 42=LowestCellVoltage, 43=LowestCellNumber, 44=Pressure, 45=Event G
					//46=Voltage E, 47=Current E, 48=Capacity E, 49=Power E, 50=Balance E, 51=CellVoltage E1, 52=CellVoltage E2 .... 64=CellVoltage E14, 65=Voltage E1, 66=Voltage E2, 67=Temperature E1, 68=Temperature E2 69=Revolution E 70=MotorTime 71=Speed 72=Event E
					tmpVoltage = DataParser.parse2Short(dataBuffer, 50);
					tmpCapacity = DataParser.parse2Short(dataBuffer, 52);
					tmpHeight = DataParser.parse2Short(dataBuffer, 46);
					tmpClimb3 = dataBuffer[56];
					tmpVoltage1 = DataParser.parse2Short(dataBuffer, 38);
					tmpVoltage2 = DataParser.parse2Short(dataBuffer, 40);
					if (tmpClimb3 > -50 && tmpHeight > -490 && tmpHeight < 5000 && Math.abs(tmpVoltage1) < 600 && Math.abs(tmpVoltage2) < 600 && tmpCapacity >= points[37] / 1000) {
						points[46] = DataParser.parse2Short(dataBuffer, 50) * 1000;
						points[47] = DataParser.parse2Short(dataBuffer, 48) * 1000;
						points[48] = tmpCapacity * 1000;
						points[49] = Double.valueOf(points[46] / 1000.0 * points[47]).intValue(); // power U*I [W];
						if (tmpVoltage > 0) {
							for (int i = 0, j = 0; i < 14; i++, j += 2) {
								tmpCellVoltage = DataParser.parse2Short(dataBuffer, j + 10);
								points[i + 51] = tmpCellVoltage > 0 ? tmpCellVoltage * 500 : points[i + 51];
								if (points[i + 51] > 0) {
									maxVotage = points[i + 51] > maxVotage ? points[i + 51] : maxVotage;
									minVotage = points[i + 51] < minVotage ? points[i + 51] : minVotage;
								}
							}
							//calculate balance on the fly
							points[50] = (maxVotage != Integer.MIN_VALUE && minVotage != Integer.MAX_VALUE ? maxVotage - minVotage : 0) * 10;
						}
						points[10] = tmpHeight * 1000;
						points[11] = DataParser.parse2Short(dataBuffer, 54) * 10;
						points[12] = dataBuffer[46] * 1000;
						points[65] = tmpVoltage1 * 100;
						points[66] = tmpVoltage2 * 100;
						points[67] = DataParser.parse2Short(dataBuffer, 42) * 1000;
						points[68] = DataParser.parse2Short(dataBuffer, 44) * 1000;
						points[69] = DataParser.parse2Short(dataBuffer, 58) * 1000;
					}
				}
				break;

			case HoTTAdapter.SENSOR_TYPE_SPEED_CONTROL_115200:
				//0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
				//10=Height, 11=Climb 1, 12=Climb 3, 13=Climb 10 14=EventVario
				//15=Latitude, 16=Longitude, 17=Velocity, 18=DistanceStart, 19=DirectionStart, 20=TripDistance 21=NumSatellites 22=GPS-Fix 23=EventGPS
				//24=Voltage G, 25=Current G, 26=Capacity G, 27=Power G, 28=Balance G, 29=CellVoltage G1, 30=CellVoltage G2 .... 34=CellVoltage G6, 35=Revolution G, 36=FuelLevel, 37=Voltage G1, 38=Voltage G2, 39=Temperature G1, 40=Temperature G2 41=Speed G, 42=LowestCellVoltage, 43=LowestCellNumber, 44=Pressure, 45=Event G
				//46=Voltage E, 47=Current E, 48=Capacity E, 49=Power E, 50=Balance E, 51=CellVoltage E1, 52=CellVoltage E2 .... 64=CellVoltage E14, 65=Voltage E1, 66=Voltage E2, 67=Temperature E1, 68=Temperature E2 69=Revolution E 70=MotorTime 71=Speed 72=Event E
				//73=VoltageM, 74=CurrentM, 75=CapacityM, 76=PowerM, 77=RevolutionM, 78=TemperatureM 1, 79=TemperatureM 2 80=Voltage_min, 81=Current_max, 82=Revolution_max, 83=Temperature1_max, 84=Temperature2_max 85=Event M

				//73=Ch 1, 74=Ch 2, 75=Ch 3 .. 88=Ch 16, 89=PowerOff, 90=BatterieLow, 91=Reset, 92=reserve
				//93=VoltageM, 94=CurrentM, 95=CapacityM, 96=PowerM, 97=RevolutionM, 98=TemperatureM 1, 99=TemperatureM 2 100=Voltage_min, 101=Current_max, 102=Revolution_max, 103=Temperature1_max, 104=Temperature2_max 105=Event M
				if (dataBuffer.length >= 34) {
					tmpVoltage = DataParser.parse2Short(dataBuffer, 10);
					tmpCurrent = DataParser.parse2Short(dataBuffer, 14);
					tmpRevolution = DataParser.parse2Short(dataBuffer, 18);
					if (this.application.getActiveChannelNumber() == 4) {
						//93=VoltageM, 94=CurrentM, 95=CapacityM, 96=PowerM, 97=RevolutionM, 98=TemperatureM 1, 99=TemperatureM 2 100=Voltage_min, 101=Current_max, 102=Revolution_max, 103=Temperature1_max, 104=Temperature2_max 105=Event M
						if (!this.pickerParameters.isFilterEnabled || tmpVoltage > -1 && tmpVoltage < 1000 && tmpCurrent < 2550 && tmpRevolution > -1 && tmpRevolution < 2000) {
							points[93] = tmpVoltage * 1000;
							points[94] = tmpCurrent * 1000;
							points[95] = DataParser.parse2Short(dataBuffer, 22) * 1000;
							points[96] = Double.valueOf(points[93] / 1000.0 * points[94]).intValue(); // power U*I [W];
							points[97] = tmpRevolution * 1000;
							points[98] = DataParser.parse2Short(dataBuffer, 24) * 1000;
						}
					}
					else {
						//73=VoltageM, 74=CurrentM, 75=CapacityM, 76=PowerM, 77=RevolutionM, 78=TemperatureM 1, 79=TemperatureM 2 80=Voltage_min, 81=Current_max, 82=Revolution_max, 83=Temperature1_max, 84=Temperature2_max 85=Event M
						if (!this.pickerParameters.isFilterEnabled || tmpVoltage > -1 && tmpVoltage < 1000 && tmpCurrent < 2550 && tmpRevolution > -1 && tmpRevolution < 2000) {
							points[73] = tmpVoltage * 1000;
							points[74] = tmpCurrent * 1000;
							points[75] = DataParser.parse2Short(dataBuffer, 22) * 1000;
							points[76] = Double.valueOf(points[73] / 1000.0 * points[74]).intValue(); // power U*I [W];
							points[77] = tmpRevolution * 1000;
							points[78] = DataParser.parse2Short(dataBuffer, 24) * 1000;
						}
					}
				}
				break;
			}
			break;
		}
		return points;
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
		int[] points = new int[recordSet.getNoneCalculationRecordNames().length];
					String sThreadId = String.format("%06d", Thread.currentThread().getId()); //$NON-NLS-1$
		int progressCycle = 1;
		if (doUpdateProgressBar) this.application.setProgress(progressCycle, sThreadId);

		int timeStampBufferSize = GDE.SIZE_BYTES_INTEGER * recordDataSize;
		int index = 0;
		for (int i = 0; i < recordDataSize; i++) {
			index = i * dataBufferSize + timeStampBufferSize;
			if (log.isLoggable(Level.FINER)) log.log(Level.FINER, i + " i*dataBufferSize+timeStampBufferSize = " + index); //$NON-NLS-1$

			for (int j = 0; j < points.length; j++) {
				points[j] = (((dataBuffer[0 + (j * 4) + index] & 0xff) << 24) + ((dataBuffer[1 + (j * 4) + index] & 0xff) << 16) + ((dataBuffer[2 + (j * 4) + index] & 0xff) << 8)
						+ ((dataBuffer[3 + (j * 4) + index] & 0xff) << 0));
			}

			recordSet.addNoneCalculationRecordsPoints(points,
					(((dataBuffer[0 + (i * 4)] & 0xff) << 24) + ((dataBuffer[1 + (i * 4)] & 0xff) << 16) + ((dataBuffer[2 + (i * 4)] & 0xff) << 8) + ((dataBuffer[3 + (i * 4)] & 0xff) << 0)) / 10.0);

			if (doUpdateProgressBar && i % 50 == 0) this.application.setProgress(((++progressCycle * 5000) / recordDataSize), sThreadId);
		}
		if (doUpdateProgressBar) this.application.setProgress(100, sThreadId);
		recordSet.syncScaleOfSyncableRecords();
	}

	/**
	 * Add record data points from file stream to each measurement.
	 * It is possible to add only none calculation records if makeInActiveDisplayable calculates the rest.
	 * Do not forget to call makeInActiveDisplayable afterwards to calculate the missing data
	 * Reduces memory and cpu load by taking measurement samples every x ms based on device setting |histoSamplingTime| .
	 * @param recordSet is the target object holding the records (curves) which include measurement curves and calculated curves
	 * @param dataBuffer holds rows for each time step (i = recordDataSize) with measurement data (j = recordNamesLength equals the number of measurements)
	 * @param recordDataSize is the number of time steps
	 */
	@Override
	public void addDataBufferAsRawDataPoints(RecordSet recordSet, byte[] dataBuffer, int recordDataSize, int[] maxPoints, int[] minPoints, Analyzer analyzer) throws DataInconsitsentException {
		if (maxPoints.length != minPoints.length || maxPoints.length == 0) throw new DataInconsitsentException("number of max/min points differs: " + maxPoints.length + "/" + minPoints.length); //$NON-NLS-1$

		int recordTimespan_ms = 10;
		UniversalSampler histoRandomSample = UniversalSampler.createSampler(recordSet.getChannelConfigNumber(), maxPoints, minPoints, recordTimespan_ms, analyzer);
		int[] points = histoRandomSample.getPoints();
		IntBuffer intBuffer = ByteBuffer.wrap(dataBuffer).asIntBuffer(); // no performance penalty compared to familiar bit shifting solution
		for (int i = 0, pointsLength = points.length; i < recordDataSize; i++) {
			for (int j = 0, iOffset = i * pointsLength + recordDataSize; j < pointsLength; j++) {
				points[j] = intBuffer.get(j + iOffset);
			}
			int timeStep_ms = intBuffer.get(i) / 10;
			if (histoRandomSample.capturePoints(timeStep_ms)) recordSet.addNoneCalculationRecordsPoints(points, timeStep_ms);
		}
		recordSet.syncScaleOfSyncableRecords();
		if (log.isLoggable(Level.FINE)) log.log(Level.INFO, String.format("%s processed: %,9d", recordSet.getChannelConfigName(), recordDataSize)); //$NON-NLS-1$
	}

	/**
	 * function to prepare a data table row of record set while translating available measurement values
	 * @return pointer to filled data table row with formated values
	 */
	@Override
	public String[] prepareDataTableRow(RecordSet recordSet, String[] dataTableRow, int rowIndex) {
		try {
			int index = 0;
			for (final Record record : recordSet.getVisibleAndDisplayableRecordsForTable()) {
				int ordinal = record.getOrdinal();
				//0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
				//10=Height, 11=Climb 1, 12=Climb 3, 13=Climb 10 14=EventVario
				//15=Latitude, 16=Longitude, 17=Velocity, 18=DistanceStart, 19=DirectionStart, 20=TripDistance 21=NumSatellites 22=GPS-Fix 23=EventGPS
				//24=Voltage G, 25=Current G, 26=Capacity G, 27=Power G, 28=Balance G, 29=CellVoltage G1, 30=CellVoltage G2 .... 34=CellVoltage G6, 35=Revolution G, 36=FuelLevel, 37=Voltage G1, 38=Voltage G2, 39=Temperature G1, 40=Temperature G2 41=Speed G, 42=LowestCellVoltage, 43=LowestCellNumber, 44=Pressure, 45=Event G
				//46=Voltage E, 47=Current E, 48=Capacity E, 49=Power E, 50=Balance E, 51=CellVoltage E1, 52=CellVoltage E2 .... 64=CellVoltage E14, 65=Voltage E1, 66=Voltage E2, 67=Temperature E1, 68=Temperature E2 69=Revolution E 70=MotorTime 71=Speed 72=Event E
				//73=VoltageM, 74=CurrentM, 75=CapacityM, 76=PowerM, 77=RevolutionM, 78=TemperatureM 1, 79=TemperatureM 2 80=Voltage_min, 81=Current_max, 82=Revolution_max, 83=Temperature1_max, 84=Temperature2_max 85=Event M

				//73=Ch 1, 74=Ch 2, 75=Ch 3 .. 88=Ch 16, 89=PowerOff, 90=BatterieLow, 91=Reset, 92=reserve
				//93=VoltageM, 94=CurrentM, 95=CapacityM, 96=PowerM, 97=RevolutionM, 98=TemperatureM 1, 99=TemperatureM 2 100=Voltage_min, 101=Current_max, 102=Revolution_max, 103=Temperature1_max, 104=Temperature2_max 105=Event M
				if (ordinal >= 0 && ordinal <= 5) {
					dataTableRow[index + 1] = String.format("%.0f", (record.realGet(rowIndex) / 1000.0)); //$NON-NLS-1$
				}
				else if (ordinal == 92) { //Warning
					dataTableRow[index + 1] = record.realGet(rowIndex) == 0
							? GDE.STRING_EMPTY
									: String.format("'%c'", ((record.realGet(rowIndex) / 1000)+64));
				}
				else {
					dataTableRow[index + 1] = record.getFormattedTableValue(rowIndex);
				}
				++index;
			}
		}
		catch (RuntimeException e) {
			log.log(java.util.logging.Level.SEVERE, e.getMessage(), e);
		}
		return dataTableRow;
	}

	/**
	 * query if the given record is longitude or latitude of GPS data, such data needs translation for display as graph
	 * @param record
	 * @return
	 */
	@Override
	public boolean isGPSCoordinates(Record record) {
		//15=Latitude, 16=Longitude, 17=Velocity, 18=DistanceStart, 19=DirectionStart, 20=TripDistance 21=NumSatellites 22=GPS-Fix 23=EventGPS
		final int latOrdinal = 15, lonOrdinal = 16;
		return record.getOrdinal() == latOrdinal || record.getOrdinal() == lonOrdinal;
	}

	/**
	 * function to translate measured values from a device to values represented
	 * this function should be over written by device and measurement specific algorithm
	 * @return double of device dependent value
	 */
	@Override
	public double translateValue(Record record, double value) {
		double factor = record.getFactor(); // != 1 if a unit translation is required
		double offset = record.getOffset(); // != 0 if a unit translation is required
		double reduction = record.getReduction(); // != 0 if a unit translation is required
		double newValue = 0;
		//0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
		//10=Height, 11=Climb 1, 12=Climb 3, 13=Climb 10 14=EventVario
		//15=Latitude, 16=Longitude, 17=Velocity, 18=DistanceStart, 19=DirectionStart, 20=TripDistance 21=NumSatellites 22=GPS-Fix 23=EventGPS
		//24=Voltage G, 25=Current G, 26=Capacity G, 27=Power G, 28=Balance G, 29=CellVoltage G1, 30=CellVoltage G2 .... 34=CellVoltage G6, 35=Revolution G, 36=FuelLevel, 37=Voltage G1, 38=Voltage G2, 39=Temperature G1, 40=Temperature G2 41=Speed G, 42=LowestCellVoltage, 43=LowestCellNumber, 44=Pressure, 45=Event G
		//46=Voltage E, 47=Current E, 48=Capacity E, 49=Power E, 50=Balance E, 51=CellVoltage E1, 52=CellVoltage E2 .... 64=CellVoltage E14, 65=Voltage E1, 66=Voltage E2, 67=Temperature E1, 68=Temperature E2 69=Revolution E 70=MotorTime 71=Speed 72=Event E
		//73=VoltageM, 74=CurrentM, 75=CapacityM, 76=PowerM, 77=RevolutionM, 78=TemperatureM 1, 79=TemperatureM 2 80=Voltage_min, 81=Current_max, 82=Revolution_max, 83=Temperature1_max, 84=Temperature2_max 85=Event M

		//73=Ch 1, 74=Ch 2, 75=Ch 3 .. 88=Ch 16, 89=PowerOff, 90=BatterieLow, 91=Reset, 92=reserve
		//93=VoltageM, 94=CurrentM, 95=CapacityM, 96=PowerM, 97=RevolutionM, 98=TemperatureM 1, 99=TemperatureM 2 100=Voltage_min, 101=Current_max, 102=Revolution_max, 103=Temperature1_max, 104=Temperature2_max 105=Event M
		final int latOrdinal = 15, lonOrdinal = 16;
		if (record.getOrdinal() == latOrdinal || record.getOrdinal() == lonOrdinal) { //15=Latitude, 16=Longitude
			int grad = ((int) (value / 1000));
			double minuten = (value - (grad * 1000.0)) / 10.0;
			newValue = grad + minuten / 60.0;
		}
		else if (record.getAbstractParent().getChannelConfigNumber() == 4 && (record.getOrdinal() >= 73 && record.getOrdinal() <= 88)) {
			if (this.pickerParameters.isChannelPercentEnabled) {
				if (!record.getUnit().equals("%")) record.setUnit("%");
				factor = 0.250;
				reduction = 1500.0;
				newValue = (value - reduction) * factor;
			}
			else {
				if (!record.getUnit().equals("µsec")) record.setUnit("µsec");
				newValue = (value - reduction) * factor;
			}
		}
		else {
			newValue = (value - reduction) * factor + offset;
		}

		if (log.isLoggable(Level.FINE)) log.log(Level.FINE, "for " + record.getName() + " in value = " + value + " out value = " + newValue); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		return newValue;
	}

	/**
	 * function to reverse translate measured values from a device to values represented
	 * this function should be over written by device and measurement specific algorithm
	 * @return double of device dependent value
	 */
	@Override
	public double reverseTranslateValue(Record record, double value) {
		double factor = record.getFactor(); // != 1 if a unit translation is required
		double offset = record.getOffset(); // != 0 if a unit translation is required
		double reduction = record.getReduction(); // != 0 if a unit translation is required
		double newValue = 0;
		//0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
		//10=Height, 11=Climb 1, 12=Climb 3, 13=Climb 10 14=EventVario
		//15=Latitude, 16=Longitude, 17=Velocity, 18=DistanceStart, 19=DirectionStart, 20=TripDistance 21=NumSatellites 22=GPS-Fix 23=EventGPS
		//24=Voltage G, 25=Current G, 26=Capacity G, 27=Power G, 28=Balance G, 29=CellVoltage G1, 30=CellVoltage G2 .... 34=CellVoltage G6, 35=Revolution G, 36=FuelLevel, 37=Voltage G1, 38=Voltage G2, 39=Temperature G1, 40=Temperature G2 41=Speed G, 42=LowestCellVoltage, 43=LowestCellNumber, 44=Pressure, 45=Event G
		//46=Voltage E, 47=Current E, 48=Capacity E, 49=Power E, 50=Balance E, 51=CellVoltage E1, 52=CellVoltage E2 .... 64=CellVoltage E14, 65=Voltage E1, 66=Voltage E2, 67=Temperature E1, 68=Temperature E2 69=Revolution E 70=MotorTime 71=Speed 72=Event E
		//73=VoltageM, 74=CurrentM, 75=CapacityM, 76=PowerM, 77=RevolutionM, 78=TemperatureM 1, 79=TemperatureM 2 80=Voltage_min, 81=Current_max, 82=Revolution_max, 83=Temperature1_max, 84=Temperature2_max 85=Event M

		//73=Ch 1, 74=Ch 2, 75=Ch 3 .. 88=Ch 16, 89=PowerOff, 90=BatterieLow, 91=Reset, 92=reserve
		//93=VoltageM, 94=CurrentM, 95=CapacityM, 96=PowerM, 97=RevolutionM, 98=TemperatureM 1, 99=TemperatureM 2 100=Voltage_min, 101=Current_max, 102=Revolution_max, 103=Temperature1_max, 104=Temperature2_max 105=Event M
		final int latOrdinal = 15, lonOrdinal = 16;
		if (record.getOrdinal() == latOrdinal || record.getOrdinal() == lonOrdinal) { // 15=Latitude, 16=Longitude
			int grad = (int) value;
			double minuten = (value - grad * 1.0) * 60.0;
			newValue = (grad + minuten / 100.0) * 1000.0;
		}
		else if (record.getAbstractParent().getChannelConfigNumber() == 4 && (record.getOrdinal() >= 73 && record.getOrdinal() <= 88)) {
			if (this.pickerParameters.isChannelPercentEnabled) {
				if (!record.getUnit().equals("%")) record.setUnit("%");
				factor = 0.250;
				reduction = 1500.0;
				newValue = value / factor + reduction;
			}
			else {
				if (!record.getUnit().equals("µsec")) record.setUnit("µsec");
				newValue = (value - reduction) * factor;
			}
		}
		else {
			newValue = (value - offset) / factor + reduction;
		}

		if (log.isLoggable(Level.FINE)) log.log(Level.FINE, "for " + record.getName() + " in value = " + value + " out value = " + newValue); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		return newValue;
	}

	/**
	 * function to calculate values for inactive records, data not readable from device
	 * if calculation is done during data gathering this can be a loop switching all records to displayable
	 * for calculation which requires more effort or is time consuming it can call a background thread,
	 * target is to make sure all data point not coming from device directly are available and can be displayed
	 */
	@Override
	public void makeInActiveDisplayable(RecordSet recordSet) {
		if (recordSet != null) {
			calculateInactiveRecords(recordSet);
			recordSet.syncScaleOfSyncableRecords();
			this.updateVisibilityStatus(recordSet, true);
			this.application.updateStatisticsData();
		}
	}

	/**
	 * function to calculate values for inactive records, data not readable from device
	 */
	@Override
	public void calculateInactiveRecords(RecordSet recordSet) {
		// 0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
		// 10=Height, 11=Climb 1, 12=Climb 3, 13=Climb 10 14=EventVario
		// 15=Latitude, 16=Longitude, 17=Velocity, 18=DistanceStart, 19=DirectionStart, 20=TripDistance 21=NumSatellites 22=GPS-Fix 23=EventGPS
		// 24=Voltage G, 25=Current G, 26=Capacity G, 27=Power G, 28=Balance G, 29=CellVoltage G1, 30=CellVoltage G2 .... 34=CellVoltage G6,
		// 35=Revolution G, 36=FuelLevel, 37=Voltage G1, 38=Voltage G2, 39=Temperature G1, 40=Temperature G2 41=Speed G, 42=LowestCellVoltage,
		// 43=LowestCellNumber, 44=Pressure, 45=Event G
		// 46=Voltage E, 47=Current E, 48=Capacity E, 49=Power E, 50=Balance E, 51=CellVoltage E1, 52=CellVoltage E2 .... 64=CellVoltage E14,
		// 65=Voltage E1, 66=Voltage E2, 67=Temperature E1, 68=Temperature E2 69=Revolution E 70=MotorTime 71=Speed 72=Event E
		// 73=VoltageM, 74=CurrentM, 75=CapacityM, 76=PowerM, 77=RevolutionM, 78=TemperatureM 1, 79=TemperatureM 2 80=Voltage_min, 81=Current_max,
		// 82=Revolution_max, 83=Temperature1_max, 84=Temperature2_max 85=Event M

		// 73=Ch 1, 74=Ch 2, 75=Ch 3 .. 88=Ch 16, 89=PowerOff, 90=BatterieLow, 91=Reset, 92=reserve
		// 93=VoltageM, 94=CurrentM, 95=CapacityM, 96=PowerM, 97=RevolutionM, 98=TemperatureM 1, 99=TemperatureM 2 100=Voltage_min, 101=Current_max,
		// 102=Revolution_max, 103=Temperature1_max, 104=Temperature2_max 105=Event M
		final int latOrdinal = 15, lonOrdinal = 16, altOrdinal = 10, distOrdinal = 18, tripOrdinal = 20;
		Record recordLatitude = recordSet.get(latOrdinal);
		Record recordLongitude = recordSet.get(lonOrdinal);
		Record recordAlitude = recordSet.get(altOrdinal);
		if (recordLatitude.hasReasonableData() && recordLongitude.hasReasonableData() && recordAlitude.hasReasonableData()) { // 13=Latitude,
																																																													// 14=Longitude 9=Height
			int recordSize = recordLatitude.realSize();
			int startAltitude = recordAlitude.get(8); // using this as start point might be sense less if the GPS data has no 3D-fix
			// check GPS latitude and longitude
			int indexGPS = 0;
			int i = 0;
			for (; i < recordSize; ++i) {
				if (recordLatitude.get(i) != 0 && recordLongitude.get(i) != 0) {
					indexGPS = i;
					++i;
					break;
				}
			}
			startAltitude = recordAlitude.get(indexGPS); // set initial altitude to enable absolute altitude calculation

			GPSHelper.calculateTripLength(this, recordSet, latOrdinal, lonOrdinal, altOrdinal, startAltitude, distOrdinal, tripOrdinal);
			// GPSHelper.calculateLabs(this, recordSet, latOrdinal, lonOrdinal, distOrdinal, tripOrdinal, 15);
		}

		if (recordSet.getChannelConfigNumber() == 6) { // do lab calculation with configuration Lab-Time only
			// 5=Rx_dbm, 18=DistanceStart, 86=SmoothedRx_dbm, 87=DiffRx_dbm, 88=LapsRx_dbm 89=DiffDistance, 90=LapsDistance
			// 5=Rx_dbm, 86=SmoothedRx_dbm, 87=DiffRx_dbm, 88=LapsRx_dbm
			// 18=DistanceStart, 89=DiffDistance, 90=LapsDistance
			runLabsCalculation(recordSet, 6, 5, 86, 87, 88, 18, 89, 90);
		}
	}

	/**
	 * import device specific *.bin data files
	 */
	@Override
	protected void importDeviceData() {
		final FileDialog fd = FileUtils.getImportDirectoryFileDialog(this, Messages.getString(MessageIds.GDE_MSGT2400), "LogData"); //$NON-NLS-1$

		Thread reader = new Thread("reader") { //$NON-NLS-1$
			@Override
			public void run() {
				try {
					boolean isInitialSwitched = false;
					HoTTAdapter2.this.application.setPortConnected(true);
					for (String tmpFileName : fd.getFileNames()) {
						String selectedImportFile = fd.getFilterPath() + GDE.STRING_FILE_SEPARATOR_UNIX + tmpFileName;
						if (!selectedImportFile.toLowerCase().endsWith(GDE.FILE_ENDING_DOT_BIN) && !selectedImportFile.toLowerCase().endsWith(GDE.FILE_ENDING_DOT_LOG)) {
							log.log(Level.WARNING, String.format("skip selectedImportFile %s since it has not a supported file ending", selectedImportFile));
						}
						log.log(java.util.logging.Level.FINE, "selectedImportFile = " + selectedImportFile); //$NON-NLS-1$

						if (fd.getFileName().length() > MIN_FILENAME_LENGTH) {
							//String recordNameExtend = selectedImportFile.substring(selectedImportFile.lastIndexOf(GDE.CHAR_DOT) - 4, selectedImportFile.lastIndexOf(GDE.CHAR_DOT));

							String directoryName = ObjectKeyCompliance.getUpcomingObjectKey(Paths.get(selectedImportFile));
							if (!directoryName.isEmpty()) ObjectKeyCompliance.createObjectKey(directoryName);

							try {
								// use a copy of the picker parameters to avoid changes by the reader
								if (selectedImportFile.toLowerCase().endsWith(GDE.FILE_ENDING_DOT_BIN)) {
									HoTTbinReader2.read(selectedImportFile, new PickerParameters(HoTTAdapter2.this.pickerParameters));
								}
								else if (selectedImportFile.toLowerCase().endsWith(GDE.FILE_ENDING_DOT_LOG)) {
									HoTTlogReader2.read(selectedImportFile, new PickerParameters(HoTTAdapter2.this.pickerParameters));
								}
								if (!isInitialSwitched) {
									Channel activeChannel = HoTTAdapter2.this.application.getActiveChannel();
									HoTTbinReader2.channels.switchChannel(activeChannel.getName());
									if (selectedImportFile.toLowerCase().endsWith(GDE.FILE_ENDING_DOT_BIN)) {
										activeChannel.switchRecordSet(HoTTbinReader2.recordSet.getName());
									}
									else if (selectedImportFile.toLowerCase().endsWith(GDE.FILE_ENDING_DOT_LOG)) {
										activeChannel.switchRecordSet(HoTTlogReader2.recordSet.getName());
									}
									isInitialSwitched = true;
								}
								else {
									HoTTAdapter2.this.makeInActiveDisplayable(HoTTbinReader2.recordSet);
								}
								WaitTimer.delay(500);
							}
							catch (Exception e) {
								log.log(java.util.logging.Level.WARNING, e.getMessage(), e);
							}
						}
					}
				}
				finally {
					HoTTAdapter2.this.application.setPortConnected(false);
				}
			}
		};
		reader.start();
	}

	/**
	 * update the file export menu by adding two new entries to export KML/GPX files
	 * @param exportMenue
	 */
	@Override
	public void updateFileExportMenu(Menu exportMenue) {
		MenuItem convertKMZ3DRelativeItem;
		MenuItem convertKMZDAbsoluteItem;
		//		MenuItem convertGPXItem;
		//		MenuItem convertGPXGarminItem;

		if (exportMenue.getItem(exportMenue.getItemCount() - 1).getText().equals(Messages.getString(gde.messages.MessageIds.GDE_MSGT0732))) {
			new MenuItem(exportMenue, SWT.SEPARATOR);

			convertKMZ3DRelativeItem = new MenuItem(exportMenue, SWT.PUSH);
			convertKMZ3DRelativeItem.setText(Messages.getString(MessageIds.GDE_MSGT2405));
			convertKMZ3DRelativeItem.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event e) {
					log.log(java.util.logging.Level.FINEST, "convertKMZ3DRelativeItem action performed! " + e); //$NON-NLS-1$
					export2KMZ3D(DeviceConfiguration.HEIGHT_RELATIVE);
				}
			});

			convertKMZDAbsoluteItem = new MenuItem(exportMenue, SWT.PUSH);
			convertKMZDAbsoluteItem.setText(Messages.getString(MessageIds.GDE_MSGT2406));
			convertKMZDAbsoluteItem.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event e) {
					log.log(java.util.logging.Level.FINEST, "convertKMZDAbsoluteItem action performed! " + e); //$NON-NLS-1$
					export2KMZ3D(DeviceConfiguration.HEIGHT_ABSOLUTE);
				}
			});

			convertKMZDAbsoluteItem = new MenuItem(exportMenue, SWT.PUSH);
			convertKMZDAbsoluteItem.setText(Messages.getString(MessageIds.GDE_MSGT2407));
			convertKMZDAbsoluteItem.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event e) {
					log.log(java.util.logging.Level.FINEST, "convertKMZDAbsoluteItem action performed! " + e); //$NON-NLS-1$
					export2KMZ3D(DeviceConfiguration.HEIGHT_CLAMPTOGROUND);
				}
			});

			//			convertGPXItem = new MenuItem(exportMenue, SWT.PUSH);
			//			convertGPXItem.setText(Messages.getString(gde.messages.MessageIds.GDE_MSGT0728));
			//			convertGPXItem.addListener(SWT.Selection, new Listener() {
			//				public void handleEvent(Event e) {
			//					log.log(java.util.logging.Level.FINEST, "convertGPXItem action performed! " + e); //$NON-NLS-1$
			//					export2GPX(false);
			//				}
			//			});
			//
			//			convertGPXGarminItem = new MenuItem(exportMenue, SWT.PUSH);
			//			convertGPXGarminItem.setText(Messages.getString(gde.messages.MessageIds.GDE_MSGT0729));
			//			convertGPXGarminItem.addListener(SWT.Selection, new Listener() {
			//				public void handleEvent(Event e) {
			//					log.log(java.util.logging.Level.FINEST, "convertGPXGarminItem action performed! " + e); //$NON-NLS-1$
			//					export2GPX(true);
			//				}
			//			});
		}
	}

	/**
	 * update the file import menu by adding new entry to import device specific files
	 * @param importMenue
	 */
	@Override
	public void updateFileImportMenu(Menu importMenue) {
		MenuItem importDeviceLogItem;

		if (importMenue.getItem(importMenue.getItemCount() - 1).getText().equals(Messages.getString(gde.messages.MessageIds.GDE_MSGT0018))) {
			new MenuItem(importMenue, SWT.SEPARATOR);

			importDeviceLogItem = new MenuItem(importMenue, SWT.PUSH);
			String[] messageParams = new String[GDE.MOD1.length + 1];
			System.arraycopy(GDE.MOD1, 0, messageParams, 1, GDE.MOD1.length);
			messageParams[0] = this.getDeviceConfiguration().getDataBlockPreferredFileExtention();
			importDeviceLogItem.setText(Messages.getString(MessageIds.GDE_MSGT2416, messageParams));
			importDeviceLogItem.setAccelerator(SWT.MOD1 + Messages.getAcceleratorChar(MessageIds.GDE_MSGT2416));
			importDeviceLogItem.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event e) {
					log.log(java.util.logging.Level.FINEST, "importDeviceLogItem action performed! " + e); //$NON-NLS-1$
					importDeviceData();
				}
			});
		}
	}

	/**
	 * exports the actual displayed data set to KML file format
	 * @param type DeviceConfiguration.HEIGHT_RELATIVE | DeviceConfiguration.HEIGHT_ABSOLUTE
	 */
	@Override
	public void export2KMZ3D(int type) {
		//0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
		//10=Height, 11=Climb 1, 12=Climb 3, 13=Climb 10 14=EventVario
		//15=Latitude, 16=Longitude, 17=Velocity, 18=DistanceStart, 19=DirectionStart, 20=TripDistance 21=NumSatellites 22=GPS-Fix 23=EventGPS
		//24=Voltage G, 25=Current G, 26=Capacity G, 27=Power G, 28=Balance G, 29=CellVoltage G1, 30=CellVoltage G2 .... 34=CellVoltage G6, 35=Revolution G, 36=FuelLevel, 37=Voltage G1, 38=Voltage G2, 39=Temperature G1, 40=Temperature G2 41=Speed G, 42=LowestCellVoltage, 43=LowestCellNumber, 44=Pressure, 45=Event G
		//46=Voltage E, 47=Current E, 48=Capacity E, 49=Power E, 50=Balance E, 51=CellVoltage E1, 52=CellVoltage E2 .... 64=CellVoltage E14, 65=Voltage E1, 66=Voltage E2, 67=Temperature E1, 68=Temperature E2 69=Revolution E 70=MotorTime 71=Speed 72=Event E
		//73=VoltageM, 74=CurrentM, 75=CapacityM, 76=PowerM, 77=RevolutionM, 78=TemperatureM 1, 79=TemperatureM 2 80=Voltage_min, 81=Current_max, 82=Revolution_max, 83=Temperature1_max, 84=Temperature2_max 85=Event M

		//73=Ch 1, 74=Ch 2, 75=Ch 3 .. 88=Ch 16, 89=PowerOff, 90=BatterieLow, 91=Reset, 92=reserve
		//93=VoltageM, 94=CurrentM, 95=CapacityM, 96=PowerM, 97=RevolutionM, 98=TemperatureM 1, 99=TemperatureM 2 100=Voltage_min, 101=Current_max, 102=Revolution_max, 103=Temperature1_max, 104=Temperature2_max 105=Event M
		final int latOrdinal = 15, lonOrdinal = 16, altOrdinal = 10, climbOrdinal = 11, speedOrdinal = 17, tripOrdinal = 20;
		new FileHandler().exportFileKMZ(Messages.getString(MessageIds.GDE_MSGT2403), lonOrdinal, latOrdinal, altOrdinal, speedOrdinal, climbOrdinal, tripOrdinal, -1,
				type == DeviceConfiguration.HEIGHT_RELATIVE, type == DeviceConfiguration.HEIGHT_CLAMPTOGROUND);
	}

	/**
	 * exports the actual displayed data set to KML file format
	 * @param type DeviceConfiguration.HEIGHT_RELATIVE | DeviceConfiguration.HEIGHT_ABSOLUTE | DeviceConfiguration.HEIGHT_CLAMPTOGROUND
	 */
	@Override
	public void export2GPX(final boolean isGarminExtension) {
		//0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
		//10=Height, 11=Climb 1, 12=Climb 3, 13=Climb 10 14=EventVario
		//15=Latitude, 16=Longitude, 17=Velocity, 18=DistanceStart, 19=DirectionStart, 20=TripDistance 21=NumSatellites 22=GPS-Fix 23=EventGPS
		//24=Voltage G, 25=Current G, 26=Capacity G, 27=Power G, 28=Balance G, 29=CellVoltage G1, 30=CellVoltage G2 .... 34=CellVoltage G6, 35=Revolution G, 36=FuelLevel, 37=Voltage G1, 38=Voltage G2, 39=Temperature G1, 40=Temperature G2 41=Speed G, 42=LowestCellVoltage, 43=LowestCellNumber, 44=Pressure, 45=Event G
		//46=Voltage E, 47=Current E, 48=Capacity E, 49=Power E, 50=Balance E, 51=CellVoltage E1, 52=CellVoltage E2 .... 64=CellVoltage E14, 65=Voltage E1, 66=Voltage E2, 67=Temperature E1, 68=Temperature E2 69=Revolution E 70=MotorTime 71=Speed 72=Event E
		//73=VoltageM, 74=CurrentM, 75=CapacityM, 76=PowerM, 77=RevolutionM, 78=TemperatureM 1, 79=TemperatureM 2 80=Voltage_min, 81=Current_max, 82=Revolution_max, 83=Temperature1_max, 84=Temperature2_max 85=Event M

		//73=Ch 1, 74=Ch 2, 75=Ch 3 .. 88=Ch 16, 89=PowerOff, 90=BatterieLow, 91=Reset, 92=reserve
		//93=VoltageM, 94=CurrentM, 95=CapacityM, 96=PowerM, 97=RevolutionM, 98=TemperatureM 1, 99=TemperatureM 2 100=Voltage_min, 101=Current_max, 102=Revolution_max, 103=Temperature1_max, 104=Temperature2_max 105=Event M
		if (isGarminExtension)
			new FileHandler().exportFileGPX(Messages.getString(gde.messages.MessageIds.GDE_MSGT0730), 15, 16, 10, 17, -1, -1, -1, -1, new int[] { -1, -1, -1 });
		else
			new FileHandler().exportFileGPX(Messages.getString(gde.messages.MessageIds.GDE_MSGT0730), 15, 16, 10, 17, -1, -1, -1, -1, new int[0]);
	}

	/**
	 * @return the translated latitude and longitude to IGC latitude {DDMMmmmN/S, DDDMMmmmE/W} for GPS devices only
	 */
	@Override
	public String translateGPS2IGC(RecordSet recordSet, int index, char fixValidity, int startAltitude, int offsetAltitude) {
		//0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
		//10=Height, 11=Climb 1, 12=Climb 3, 13=Climb 10 14=EventVario
		//15=Latitude, 16=Longitude, 17=Velocity, 18=DistanceStart, 19=DirectionStart, 20=TripDistance 21=NumSatellites 22=GPS-Fix 23=EventGPS
		//24=Voltage G, 25=Current G, 26=Capacity G, 27=Power G, 28=Balance G, 29=CellVoltage G1, 30=CellVoltage G2 .... 34=CellVoltage G6, 35=Revolution G, 36=FuelLevel, 37=Voltage G1, 38=Voltage G2, 39=Temperature G1, 40=Temperature G2 41=Speed G, 42=LowestCellVoltage, 43=LowestCellNumber, 44=Pressure, 45=Event G
		//46=Voltage E, 47=Current E, 48=Capacity E, 49=Power E, 50=Balance E, 51=CellVoltage E1, 52=CellVoltage E2 .... 64=CellVoltage E14, 65=Voltage E1, 66=Voltage E2, 67=Temperature E1, 68=Temperature E2 69=Revolution E 70=MotorTime 71=Speed 72=Event E
		//73=VoltageM, 74=CurrentM, 75=CapacityM, 76=PowerM, 77=RevolutionM, 78=TemperatureM 1, 79=TemperatureM 2 80=Voltage_min, 81=Current_max, 82=Revolution_max, 83=Temperature1_max, 84=Temperature2_max 85=Event M

		//73=Ch 1, 74=Ch 2, 75=Ch 3 .. 88=Ch 16, 89=PowerOff, 90=BatterieLow, 91=Reset, 92=reserve
		//93=VoltageM, 94=CurrentM, 95=CapacityM, 96=PowerM, 97=RevolutionM, 98=TemperatureM 1, 99=TemperatureM 2 100=Voltage_min, 101=Current_max, 102=Revolution_max, 103=Temperature1_max, 104=Temperature2_max 105=Event M
		final int latOrdinal = 15, lonOrdinal = 16, altOrdinal = 10;
		Record recordLatitude = recordSet.get(latOrdinal);
		Record recordLongitude = recordSet.get(lonOrdinal);
		Record gpsAlitude = recordSet.get(altOrdinal);

		return String.format("%02d%05d%s%03d%05d%s%c%05.0f%05.0f", //$NON-NLS-1$
				recordLatitude.get(index) / 1000000, Double.valueOf(recordLatitude.get(index) % 1000000 / 10.0 + 0.5).intValue(), recordLatitude.get(index) > 0 ? "N" : "S", //$NON-NLS-1$
				recordLongitude.get(index) / 1000000, Double.valueOf(recordLongitude.get(index) % 1000000 / 10.0 + 0.5).intValue(), recordLongitude.get(index) > 0 ? "E" : "W", //$NON-NLS-1$
				fixValidity, (this.translateValue(gpsAlitude, gpsAlitude.get(index) / 1000.0) + offsetAltitude), (this.translateValue(gpsAlitude, gpsAlitude.get(index) / 1000.0) + offsetAltitude));
	}

	/**
	 * query if the actual record set of this device contains GPS data to enable KML export to enable google earth visualization
	 * set value of -1 to suppress this measurement
	 */
	@Override
	public boolean isActualRecordSetWithGpsData() {
		boolean containsGPSdata = false;
		Channel activeChannel = this.channels.getActiveChannel();
		if (activeChannel != null) {
			RecordSet activeRecordSet = activeChannel.getActiveRecordSet();
			if (activeRecordSet != null) {
				//0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
				//10=Height, 11=Climb 1, 12=Climb 3, 13=Climb 10 14=EventVario
				//15=Latitude, 16=Longitude, 17=Velocity, 18=DistanceStart, 19=DirectionStart, 20=TripDistance 21=NumSatellites 22=GPS-Fix 23=EventGPS
				//24=Voltage G, 25=Current G, 26=Capacity G, 27=Power G, 28=Balance G, 29=CellVoltage G1, 30=CellVoltage G2 .... 34=CellVoltage G6, 35=Revolution G, 36=FuelLevel, 37=Voltage G1, 38=Voltage G2, 39=Temperature G1, 40=Temperature G2 41=Speed G, 42=LowestCellVoltage, 43=LowestCellNumber, 44=Pressure, 45=Event G
				//46=Voltage E, 47=Current E, 48=Capacity E, 49=Power E, 50=Balance E, 51=CellVoltage E1, 52=CellVoltage E2 .... 64=CellVoltage E14, 65=Voltage E1, 66=Voltage E2, 67=Temperature E1, 68=Temperature E2 69=Revolution E 70=MotorTime 71=Speed 72=Event E
				//73=VoltageM, 74=CurrentM, 75=CapacityM, 76=PowerM, 77=RevolutionM, 78=TemperatureM 1, 79=TemperatureM 2 80=Voltage_min, 81=Current_max, 82=Revolution_max, 83=Temperature1_max, 84=Temperature2_max 85=Event M

				//73=Ch 1, 74=Ch 2, 75=Ch 3 .. 88=Ch 16, 89=PowerOff, 90=BatterieLow, 91=Reset, 92=reserve
				//93=VoltageM, 94=CurrentM, 95=CapacityM, 96=PowerM, 97=RevolutionM, 98=TemperatureM 1, 99=TemperatureM 2 100=Voltage_min, 101=Current_max, 102=Revolution_max, 103=Temperature1_max, 104=Temperature2_max 105=Event M
				final int latOrdinal = 15, lonOrdinal = 16;
				containsGPSdata = activeRecordSet.get(latOrdinal).hasReasonableData() && activeRecordSet.get(lonOrdinal).hasReasonableData();
			}
		}
		return containsGPSdata;
	}

	/**
	 * export a file of the actual channel/record set
	 * @return full qualified file path depending of the file ending type
	 */
	@Override
	public String exportFile(String fileEndingType, boolean isExport2TmpDir) {
		String exportFileName = GDE.STRING_EMPTY;
		Channel activeChannel = this.channels.getActiveChannel();
		if (activeChannel != null) {
			RecordSet activeRecordSet = activeChannel.getActiveRecordSet();
			if (activeRecordSet != null && fileEndingType.contains(GDE.FILE_ENDING_KMZ)) {
				//0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
				//10=Height, 11=Climb 1, 12=Climb 3, 13=Climb 10 14=EventVario
				//15=Latitude, 16=Longitude, 17=Velocity, 18=DistanceStart, 19=DirectionStart, 20=TripDistance 21=NumSatellites 22=GPS-Fix 23=EventGPS
				//24=Voltage G, 25=Current G, 26=Capacity G, 27=Power G, 28=Balance G, 29=CellVoltage G1, 30=CellVoltage G2 .... 34=CellVoltage G6, 35=Revolution G, 36=FuelLevel, 37=Voltage G1, 38=Voltage G2, 39=Temperature G1, 40=Temperature G2 41=Speed G, 42=LowestCellVoltage, 43=LowestCellNumber, 44=Pressure, 45=Event G
				//46=Voltage E, 47=Current E, 48=Capacity E, 49=Power E, 50=Balance E, 51=CellVoltage E1, 52=CellVoltage E2 .... 64=CellVoltage E14, 65=Voltage E1, 66=Voltage E2, 67=Temperature E1, 68=Temperature E2 69=Revolution E 70=MotorTime 71=Speed 72=Event E
				//73=VoltageM, 74=CurrentM, 75=CapacityM, 76=PowerM, 77=RevolutionM, 78=TemperatureM 1, 79=TemperatureM 2 80=Voltage_min, 81=Current_max, 82=Revolution_max, 83=Temperature1_max, 84=Temperature2_max 85=Event M

				//73=Ch 1, 74=Ch 2, 75=Ch 3 .. 88=Ch 16, 89=PowerOff, 90=BatterieLow, 91=Reset, 92=reserve
				//93=VoltageM, 94=CurrentM, 95=CapacityM, 96=PowerM, 97=RevolutionM, 98=TemperatureM 1, 99=TemperatureM 2 100=Voltage_min, 101=Current_max, 102=Revolution_max, 103=Temperature1_max, 104=Temperature2_max 105=Event M
				final int latOrdinal = 15, lonOrdinal = 16, altOrdinal = 10, climbOrdinal = 11, tripOrdinal = 20;
				final int additionalMeasurementOrdinal = this.getGPS2KMZMeasurementOrdinal();
				exportFileName = new FileHandler().exportFileKMZ(lonOrdinal, latOrdinal, altOrdinal, additionalMeasurementOrdinal, climbOrdinal, tripOrdinal, -1, true, isExport2TmpDir);
			}
		}
		return exportFileName;
	}

	/**
	 * @return the measurement ordinal where velocity limits as well as the colors are specified (GPS-velocity)
	 */
	@Override
	public Integer getGPS2KMZMeasurementOrdinal() {
		//0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
		//10=Height, 11=Climb 1, 12=Climb 3, 13=Climb 10 14=EventVario
		//15=Latitude, 16=Longitude, 17=Velocity, 18=DistanceStart, 19=DirectionStart, 20=TripDistance 21=NumSatellites 22=GPS-Fix 23=EventGPS
		//24=Voltage G, 25=Current G, 26=Capacity G, 27=Power G, 28=Balance G, 29=CellVoltage G1, 30=CellVoltage G2 .... 34=CellVoltage G6, 35=Revolution G, 36=FuelLevel, 37=Voltage G1, 38=Voltage G2, 39=Temperature G1, 40=Temperature G2 41=Speed G, 42=LowestCellVoltage, 43=LowestCellNumber, 44=Pressure, 45=Event G
		//46=Voltage E, 47=Current E, 48=Capacity E, 49=Power E, 50=Balance E, 51=CellVoltage E1, 52=CellVoltage E2 .... 64=CellVoltage E14, 65=Voltage E1, 66=Voltage E2, 67=Temperature E1, 68=Temperature E2 69=Revolution E 70=MotorTime 71=Speed 72=Event E
		//73=VoltageM, 74=CurrentM, 75=CapacityM, 76=PowerM, 77=RevolutionM, 78=TemperatureM 1, 79=TemperatureM 2 80=Voltage_min, 81=Current_max, 82=Revolution_max, 83=Temperature1_max, 84=Temperature2_max 85=Event M

		//73=Ch 1, 74=Ch 2, 75=Ch 3 .. 88=Ch 16, 89=PowerOff, 90=BatterieLow, 91=Reset, 92=reserve
		//93=VoltageM, 94=CurrentM, 95=CapacityM, 96=PowerM, 97=RevolutionM, 98=TemperatureM 1, 99=TemperatureM 2 100=Voltage_min, 101=Current_max, 102=Revolution_max, 103=Temperature1_max, 104=Temperature2_max 105=Event M
		if (kmzMeasurementOrdinal == null) // keep usage as initial supposed and use speed measurement ordinal
			return 17;

		return kmzMeasurementOrdinal;
	}

	/**
	 * check and adapt stored measurement properties against actual record set records which gets created by device properties XML
	 * - calculated measurements could be later on added to the device properties XML
	 * - devices with battery cell voltage does not need to all the cell curves which does not contain measurement values
	 * @param fileRecordsProperties - all the record describing properties stored in the file
	 * @param recordSet - the record sets with its measurements build up with its measurements from device properties XML
	 * @return string array of measurement names which match the ordinal of the record set requirements to restore file record properties
	 */
	@Override
	public String[] crossCheckMeasurements(String[] fileRecordsProperties, RecordSet recordSet) {
		//check for HoTTAdapter2 file contained record properties which are not contained in actual configuration

		//3.2.7 extend this measurements: 66/86=TemperatureM 2 67/87=Voltage_min, 68/88=Current_max, 69/89=Revolution_max, 70/90=Temperature1_max, 71/91=Temperature2_max
		//3.3.1 extend this measurements: 9=EventRx, 14=EventVario, 21=NumSatellites 22=GPS-Fix 23=EventGPS, 41=Speed G, 42=LowestCellVoltage, 43=LowestCellNumber, 44=Pressure, 45=Event G, 70=MotorTime 71=Speed 72=Event E, 85/105=Event M

		//0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
		//10=Height, 11=Climb 1, 12=Climb 3, 13=Climb 10 14=EventVario
		//15=Latitude, 16=Longitude, 17=Velocity, 18=DistanceStart, 19=DirectionStart, 20=TripDistance 21=NumSatellites 22=GPS-Fix 23=EventGPS
		//24=Voltage G, 25=Current G, 26=Capacity G, 27=Power G, 28=Balance G, 29=CellVoltage G1, 30=CellVoltage G2 .... 34=CellVoltage G6, 35=Revolution G, 36=FuelLevel, 37=Voltage G1, 38=Voltage G2, 39=Temperature G1, 40=Temperature G2 41=Speed G, 42=LowestCellVoltage, 43=LowestCellNumber, 44=Pressure, 45=Event G
		//46=Voltage E, 47=Current E, 48=Capacity E, 49=Power E, 50=Balance E, 51=CellVoltage E1, 52=CellVoltage E2 .... 64=CellVoltage E14, 65=Voltage E1, 66=Voltage E2, 67=Temperature E1, 68=Temperature E2 69=Revolution E 70=MotorTime 71=Speed 72=Event E
		//73=VoltageM, 74=CurrentM, 75=CapacityM, 76=PowerM, 77=RevolutionM, 78=TemperatureM 1, 79=TemperatureM 2 80=Voltage_min, 81=Current_max, 82=Revolution_max, 83=Temperature1_max, 84=Temperature2_max 85=Event M
		//with channel configuration 4
		//73=Ch 1, 74=Ch 2, 75=Ch 3 .. 88=Ch 16, 89=PowerOff, 90=BatterieLow, 91=Reset, 92=reserve
		//93=VoltageM, 94=CurrentM, 95=CapacityM, 96=PowerM, 97=RevolutionM, 98=TemperatureM 1, 99=TemperatureM 2 100=Voltage_min, 101=Current_max, 102=Revolution_max, 103=Temperature1_max, 104=Temperature2_max 105=Event M
		//with channel configuration 6
		//5=Rx_dbm, 18=DistanceStart, 86=SmoothedRx_dbm, 87=DiffRx_dbm, 88=LapsRx_dbm 89=DiffDistance, 90=LapsDistance

		StringBuilder sb = new StringBuilder().append(GDE.LINE_SEPARATOR);

		String[] recordKeys = recordSet.getRecordNames();
		Vector<String> cleanedRecordNames = new Vector<String>();
		//incoming filePropertiesRecordNames may mismatch recordKeyNames, but addNoneCalculation will use original name
		Vector<String> noneCalculationRecordNames = new Vector<String>();
		Vector<String> fileRecordsPropertiesVector = new Vector<String>();
		fileRecordsPropertiesVector.addAll(Arrays.asList(fileRecordsProperties));


		try {
			switch (fileRecordsProperties.length) {
			case 44: //Android HoTTAdapter3 - special case
				for (int i = 0, j = 0; i < recordKeys.length; i++) {
					switch (i) {
					case 8:  //8=VoltageRx_min
					case 9:  //EventRx
					case 14: //EventVario
					case 21: //NumSatellites
					case 22: //GPS-Fix
					case 23: //EventGPS
					case 28: //Balance G,
					case 29: //CellVoltage G1
					case 30: //CellVoltage G2
					case 31: //CellVoltage G3
					case 32: //CellVoltage G4
					case 33: //CellVoltage G5
					case 34: //CellVoltage G6
					case 37: //Voltage G1,
					case 38: //Voltage G2,
					case 39: //Temperature G1,
					case 40: //Temperature G2
					case 41: //Speed G
					case 42: //LowestCellVoltage
					case 43: //LowestCellNumber
					case 44: //Pressure
					case 45: //Event G
					case 46: //Voltage E,
					case 47: //Current E,
					case 48: //Capacity E,
					case 49: //Power E,
					case 69: //Revolution E
					case 70: //MotorTime E
					case 71: //Speed E
					case 72: //Event E
					case 73: //Voltage M,
					case 74: //Current M,
					case 75: //Capacity M,
					case 76: //Power M,
					case 77: //Revolution M
					case 79: //TemperatureM 2
					case 80: //Voltage_min
					case 81: //Current_max
					case 82: //Revolution_max
					case 83: //Temperature1_max
					case 84: //Temperature2_max
					case 85: //Event M
						sb.append(String.format("added measurement set to isCalculation=true -> %s\n", recordKeys[i]));
						recordSet.get(i).setActive(null);
						break;
					default:
						HashMap<String, String> recordProps = StringHelper.splitString(fileRecordsProperties[j], Record.DELIMITER, Record.propertyKeys);
						sb.append(String.format("%19s match %19s isAvtive = %s\n", recordKeys[i], recordProps.get(Record.NAME), recordProps.get(Record.IS_ACTIVE)));
						cleanedRecordNames.add(recordKeys[i]);
						noneCalculationRecordNames.add(recordProps.get(Record.NAME));
						if (fileRecordsProperties[j].contains("_isActive=false"))
							recordSet.get(i).setActive(false);
						++j;
						break;
					}
				}
				break;

			case 58: //General, GAM, EAM (no ESC) prior to 3.0.4 added PowerOff, BatterieLow, Reset, reserve
				for (int i = 0, j = 0; i < recordKeys.length; i++) {
					switch (i) { //list of added measurements
					case 8:  //8=VoltageRx_min
					case 9:  //EventRx
					case 14: //EventVario
					case 21: //NumSatellites
					case 22: //GPS-Fix
					case 23: //EventGPS
					case 41: //Speed G
					case 42: //LowestCellVoltage
					case 43: //LowestCellNumber
					case 44: //Pressure
					case 45: //Event G
					case 69: //Revolution E
					case 70: //MotorTime E
					case 71: //Speed E
					case 72: //Event E
					case 73: //Voltage M,
					case 74: //Current M,
					case 75: //Capacity M,
					case 76: //Power M,
					case 77: //Revolution M
					case 78: //TemperatureM 1
					case 79: //TemperatureM 2
					case 80: //Voltage_min
					case 81: //Current_max
					case 82: //Revolution_max
					case 83: //Temperature1_max
					case 84: //Temperature2_max
					case 85: //Event M
						sb.append(String.format("added measurement set to isCalculation=true -> %s\n", recordKeys[i]));
						recordSet.get(i).setActive(null);
						break;
					default:
						HashMap<String, String> recordProps = StringHelper.splitString(fileRecordsProperties[j], Record.DELIMITER, Record.propertyKeys);
						sb.append(String.format("%19s match %19s isAvtive = %s\n", recordKeys[i], recordProps.get(Record.NAME), recordProps.get(Record.IS_ACTIVE)));
						cleanedRecordNames.add(recordKeys[i]);
						noneCalculationRecordNames.add(recordProps.get(Record.NAME));
						if (fileRecordsProperties[j].contains("_isActive=false"))
							recordSet.get(i).setActive(false);
						++j;
						break;
					}
				}
				break;
			case 74: //Channels (no ESC) prior to 3.0.4 added PowerOff, BatterieLow, Reset, reserve
				for (int i = 0, j = 0; i < recordKeys.length; i++) {
					switch (i) { //list of added measurements
					case 8:  //8=VoltageRx_min
					case 9:  //EventRx
					case 14: //EventVario
					case 21: //NumSatellites
					case 22: //GPS-Fix
					case 23: //EventGPS
					case 41: //Speed G
					case 42: //LowestCellVoltage
					case 43: //LowestCellNumber
					case 44: //Pressure
					case 45: //Event G
					case 69: //Revolution E
					case 70: //MotorTime E
					case 71: //Speed E
					case 72: //Event E
					case 89: //PowerOff
					case 90: //BatterieLow
					case 91: //Reset
					case 92: //reserve
					case 93: //Voltage M,
					case 94: //Current M,
					case 95: //Capacity M,
					case 96: //Power M,
					case 97: //Revolution M
					case 98: //TemperatureM 1
					case 99: //TemperatureM 2
					case 100: //Voltage_min
					case 101: //Current_max
					case 102: //Revolution_max
					case 103: //Temperature1_max
					case 104: //Temperature2_max
					case 105: //Event M
						sb.append(String.format("added measurement set to isCalculation=true -> %s\n", recordKeys[i]));
						recordSet.get(i).setActive(null);
						break;
					default:
						HashMap<String, String> recordProps = StringHelper.splitString(fileRecordsProperties[j], Record.DELIMITER, Record.propertyKeys);
						sb.append(String.format("%19s match %19s isAvtive = %s\n", recordKeys[i], recordProps.get(Record.NAME), recordProps.get(Record.IS_ACTIVE)));
						cleanedRecordNames.add(recordKeys[i]);
						noneCalculationRecordNames.add(recordProps.get(Record.NAME));
						if (fileRecordsProperties[j].contains("_isActive=false"))
							recordSet.get(i).setActive(false);
						++j;
						break;
					}
				}
				break;

			case 64: //General, GAM, EAM, ESC 3.0.4 added VoltageM, CurrentM, CapacityM, PowerM, RevolutionM, TemperatureM 1
				for (int i = 0, j = 0; i < recordKeys.length; i++) {
					switch (i) { //list of added measurements
					case 8:  //8=VoltageRx_min
					case 9:  //EventRx
					case 14: //EventVario
					case 21: //NumSatellites
					case 22: //GPS-Fix
					case 23: //EventGPS
					case 41: //Speed G
					case 42: //LowestCellVoltage
					case 43: //LowestCellNumber
					case 44: //Pressure
					case 45: //Event G
					case 69: //Revolution E
					case 70: //MotorTime E
					case 71: //Speed E
					case 72: //Event E
					case 79: //TemperatureM 2
					case 80: //Voltage_min
					case 81: //Current_max
					case 82: //Revolution_max
					case 83: //Temperature1_max
					case 84: //Temperature2_max
					case 85: //Event M
						sb.append(String.format("added measurement set to isCalculation=true -> %s\n", recordKeys[i]));
						recordSet.get(i).setActive(null);
						break;
					default:
						HashMap<String, String> recordProps = StringHelper.splitString(fileRecordsProperties[j], Record.DELIMITER, Record.propertyKeys);
						sb.append(String.format("%19s match %19s isAvtive = %s\n", recordKeys[i], recordProps.get(Record.NAME), recordProps.get(Record.IS_ACTIVE)));
						cleanedRecordNames.add(recordKeys[i]);
						noneCalculationRecordNames.add(recordProps.get(Record.NAME));
						if (fileRecordsProperties[j].contains("_isActive=false"))
							recordSet.get(i).setActive(false);
						++j;
						break;
					}
				}
				break;
			case 84: //Channels 3.0.4 added VoltageM, CurrentM, CapacityM, PowerM, RevolutionM, TemperatureM 1
				for (int i = 0, j = 0; i < recordKeys.length; i++) {
					switch (i) { //list of added measurements
					case   8: //8=VoltageRx_min
					case   9: //EventRx
					case  14: //EventVario
					case  21: //NumSatellites
					case  22: //GPS-Fix
					case  23: //EventGPS
					case  41: //Speed G
					case  42: //LowestCellVoltage
					case  43: //LowestCellNumber
					case  44: //Pressure
					case  45: //Event G
					case  69: //Revolution E
					case  70: //MotorTime E
					case  71: //Speed E
					case  72: //Event E
					case  99: //TemperatureM 2
					case 100: //Voltage_min
					case 101: //Current_max
					case 102: //Revolution_max
					case 103: //Temperature1_max
					case 104: //Temperature2_max
					case 105: //Event M
						sb.append(String.format("added measurement set to isCalculation=true -> %s\n", recordKeys[i]));
						recordSet.get(i).setActive(null);
						break;
					default:
						HashMap<String, String> recordProps = StringHelper.splitString(fileRecordsProperties[j], Record.DELIMITER, Record.propertyKeys);
						sb.append(String.format("%19s match %19s isAvtive = %s\n", recordKeys[i], recordProps.get(Record.NAME), recordProps.get(Record.IS_ACTIVE)));
						cleanedRecordNames.add(recordKeys[i]);
						noneCalculationRecordNames.add(recordProps.get(Record.NAME));
						if (fileRecordsProperties[j].contains("_isActive=false"))
							recordSet.get(i).setActive(false);
						++j;
						break;
					}
				}
				break;


			case 66: //General, GAM, EAM, ESC 3.1.9 added VoltageRx_min, Revolution EAM
				for (int i = 0, j = 0; i < recordKeys.length; i++) {
					switch (i) { //list of added measurements
					case 9:  //EventRx
					case 14: //EventVario
					case 21: //NumSatellites
					case 22: //GPS-Fix
					case 23: //EventGPS
					case 41: //Speed G
					case 42: //LowestCellVoltage
					case 43: //LowestCellNumber
					case 44: //Pressure
					case 45: //Event G
					case 70: //MotorTime E
					case 71: //Speed E
					case 72: //Event E
					case 79: //TemperatureM 2
					case 80: //Voltage_min
					case 81: //Current_max
					case 82: //Revolution_max
					case 83: //Temperature1_max
					case 84: //Temperature2_max
					case 85: //Event M
						sb.append(String.format("added measurement set to isCalculation=true -> %s\n", recordKeys[i]));
						recordSet.get(i).setActive(null);
						break;
					default:
						HashMap<String, String> recordProps = StringHelper.splitString(fileRecordsProperties[j], Record.DELIMITER, Record.propertyKeys);
						sb.append(String.format("%19s match %19s isAvtive = %s\n", recordKeys[i], recordProps.get(Record.NAME), recordProps.get(Record.IS_ACTIVE)));
						cleanedRecordNames.add(recordKeys[i]);
						noneCalculationRecordNames.add(recordProps.get(Record.NAME));
						if (fileRecordsProperties[j].contains("_isActive=false")) {
							switch (i) { //OSD saved initially after 3.0.4 and after 3.1.9
							case   8: //8=VoltageRx_min
							case  69: //Revolution E
								sb.append(String.format("previous added measurement set to isCalculation=true -> %s\n", recordKeys[i]));
								cleanedRecordNames.remove(recordKeys[i]);
								noneCalculationRecordNames.remove(recordProps.get(Record.NAME));
								fileRecordsPropertiesVector.remove(fileRecordsProperties[j]);
								recordSet.get(i).setActive(null);
								break;
							default:
								recordSet.get(i).setActive(false);
								break;
							}
						}
						++j;
						break;
					}
				}
				break;
			case 86:
				if (recordSet.getChannelConfigNumber() == 4) { //Channels 3.1.9 added VoltageRx_min, Revolution EAM
					for (int i = 0, j = 0; i < recordKeys.length; i++) {
						switch (i) { //list of added measurements
						case 9: //EventRx
						case 14: //EventVario
						case 21: //NumSatellites
						case 22: //GPS-Fix
						case 23: //EventGPS
						case 41: //Speed G
						case 42: //LowestCellVoltage
						case 43: //LowestCellNumber
						case 44: //Pressure
						case 45: //Event G
						case 70: //MotorTime E
						case 71: //Speed E
						case 72: //Event E
						case 99: //TemperatureM 2
						case 100: //Voltage_min
						case 101: //Current_max
						case 102: //Revolution_max
						case 103: //Temperature1_max
						case 104: //Temperature2_max
						case 105: //Event M
							sb.append(String.format("added measurement set to isCalculation=true -> %s\n", recordKeys[i]));
							recordSet.get(i).setActive(null);
							break;
						default:
							HashMap<String, String> recordProps = StringHelper.splitString(fileRecordsProperties[j], Record.DELIMITER, Record.propertyKeys);
							sb.append(String.format("%19s match %19s isAvtive = %s\n", recordKeys[i], recordProps.get(Record.NAME), recordProps.get(Record.IS_ACTIVE)));
							cleanedRecordNames.add(recordKeys[i]);
							noneCalculationRecordNames.add(recordProps.get(Record.NAME));
							if (fileRecordsProperties[j].contains("_isActive=false")) {
								switch (i) { //OSD saved initially after 3.0.4 and after 3.1.9
								case   8: //8=VoltageRx_min
								case  69: //Revolution E
									sb.append(String.format("previous added measurement set to isCalculation=true -> %s\n", recordKeys[i]));
									cleanedRecordNames.remove(recordKeys[i]);
									noneCalculationRecordNames.remove(recordProps.get(Record.NAME));
									fileRecordsPropertiesVector.remove(fileRecordsProperties[j]);
									recordSet.get(i).setActive(null);
									break;
								default:
									recordSet.get(i).setActive(false);
									break;
								}
							}
							++j;
							break;
						}
					}
				}
				else { //3.3.1 General, GAM, EAM, ESC
					//3.3.1 extend this measurements: 9=EventRx, 14=EventVario, 21=NumSatellites 22=GPS-Fix 23=EventGPS, 41=Speed G, 42=LowestCellVoltage, 43=LowestCellNumber, 44=Pressure, 45=Event G, 70=MotorTime 71=Speed 72=Event E, 85/105=Event M
					cleanedRecordNames.addAll(Arrays.asList(recordKeys));
					for (int i = 0; i < fileRecordsProperties.length; i++) {
						HashMap<String, String> recordProps = StringHelper.splitString(fileRecordsProperties[i], Record.DELIMITER, Record.propertyKeys);
						if (fileRecordsProperties[i].contains("_isActive=true")) {
							noneCalculationRecordNames.add(recordProps.get(Record.NAME));
						}
						else if (fileRecordsProperties[i].contains("_isActive=false")) {
							recordSet.get(i).setActive(false);
							noneCalculationRecordNames.add(recordProps.get(Record.NAME));
						}
						else {
							recordSet.get(i).setActive(null);
						}
					}
				}

				break;

			case 72:	//3.2.7 General, GAM, EAM, ESC
			case 77:	//3.2.7 Lab-Time
				for (int i = 0, j = 0; i < recordKeys.length; i++) {
					//3.3.1 extend this measurements: 9=EventRx, 14=EventVario, 21=NumSatellites 22=GPS-Fix 23=EventGPS, 41=Speed G, 42=LowestCellVoltage, 43=LowestCellNumber, 44=Pressure, 45=Event G, 70=MotorTime 71=Speed 72=Event E, 85/105=Event M
					switch (i) {
					case 9:  //EventRx
					case 14: //EventVario
					case 21: //NumSatellites
					case 22: //GPS-Fix
					case 23: //EventGPS
					case 41: //Speed G
					case 42: //LowestCellVoltage
					case 43: //LowestCellNumber
					case 44: //Pressure
					case 45: //Event G
					case 70: //MotorTime E
					case 71: //Speed E
					case 72: //Event E
					case 85: //Event M
						sb.append(String.format("added measurement set to isCalculation=true -> %s\n", recordKeys[i]));
						recordSet.get(i).setActive(null);
						break;
					default:
						HashMap<String, String> recordProps = StringHelper.splitString(fileRecordsProperties[j], Record.DELIMITER, Record.propertyKeys);
						sb.append(String.format("%19s match %19s isAvtive = %s\n", recordKeys[i], recordProps.get(Record.NAME), recordProps.get(Record.IS_ACTIVE)));
						cleanedRecordNames.add(recordKeys[i]);
						noneCalculationRecordNames.add(recordProps.get(Record.NAME));
						if (fileRecordsProperties[j].contains("_isActive=false"))
							recordSet.get(i).setActive(false);
						++j;
						break;
					}
				}
				break;
			case 92:	//3.2.7 Channels
				for (int i = 0, j = 0; i < recordKeys.length; i++) {
					//3.3.1 extend this measurements: 9=EventRx, 14=EventVario, 21=NumSatellites 22=GPS-Fix 23=EventGPS, 41=Speed G, 42=LowestCellVoltage, 43=LowestCellNumber, 44=Pressure, 45=Event G, 70=MotorTime 71=Speed 72=Event E, 85/105=Event M
					switch (i) {
					case 9:   //EventRx
					case 14:  //EventVario
					case 21:  //NumSatellites
					case 22:  //GPS-Fix
					case 23:  //EventGPS
					case 41:  //Speed G
					case 42:  //LowestCellVoltage
					case 43:  //LowestCellNumber
					case 44:  //Pressure
					case 45:  //Event G
					case 70:  //MotorTime E
					case 71:  //Speed E
					case 72:  //Event E
					case 105: //Event M
						sb.append(String.format("added measurement set to isCalculation=true -> %s\n", recordKeys[i]));
						recordSet.get(i).setActive(null);
						break;
					default:
						HashMap<String, String> recordProps = StringHelper.splitString(fileRecordsProperties[j], Record.DELIMITER, Record.propertyKeys);
						sb.append(String.format("%19s match %19s isAvtive = %s\n", recordKeys[i], recordProps.get(Record.NAME), recordProps.get(Record.IS_ACTIVE)));
						cleanedRecordNames.add(recordKeys[i]);
						noneCalculationRecordNames.add(recordProps.get(Record.NAME));
						if (fileRecordsProperties[j].contains("_isActive=false"))
							recordSet.get(i).setActive(false);
						++j;
						break;
					}
				}
				break;

			case 860:	//3.3.1 General, GAM, EAM, ESC
			case 106:	//3.3.1 Channels
			case 91:	//3.3.1 Lab-Time
			default:
				cleanedRecordNames.addAll(Arrays.asList(recordKeys));
				for (int i = 0; i < fileRecordsProperties.length; i++) {
					HashMap<String, String> recordProps = StringHelper.splitString(fileRecordsProperties[i], Record.DELIMITER, Record.propertyKeys);
					if (fileRecordsProperties[i].contains("_isActive=true")) {
						noneCalculationRecordNames.add(recordProps.get(Record.NAME));
					}
					else if (fileRecordsProperties[i].contains("_isActive=false")) {
						recordSet.get(i).setActive(false);
						noneCalculationRecordNames.add(recordProps.get(Record.NAME));
					}
					else {
						recordSet.get(i).setActive(null);
					}
				}
				break;
			}
		}
		catch (Exception e) {
			log.log(Level.SEVERE, String.format("recordKey to fileRecordsProperties mismatch, check:\n %s \nfileRecordsProperties.length = %d recordKeys.length = %d %s", e.getMessage(), fileRecordsProperties.length, recordKeys.length, sb.toString()));
		}

		recordKeys = cleanedRecordNames.toArray(new String[1]);
		//incoming filePropertiesRecordNames may mismatch recordKeyNames, but addNoneCalculation will use original incoming name
		recordSet.setNoneCalculationRecordNames(noneCalculationRecordNames.toArray(new String[1]));

		if (fileRecordsProperties.length != fileRecordsPropertiesVector.size()) { //if stored again with newer version and isActive handled null as false
			for (int i = 0; i < fileRecordsPropertiesVector.size(); i++) {
				fileRecordsProperties[i] = fileRecordsPropertiesVector.get(i);
			}
			//fileRecordsProperties = fileRecordsPropertiesVector.toArray(new String[1]); //can't be used since it will no be propagated
		}

		if ((recordKeys.length < 86 || (recordKeys.length == 86 && recordSet.getChannelConfigNumber() == 4)) && noneCalculationRecordNames.size() < fileRecordsPropertiesVector.size()) {
			sb.append(String.format("recordKeys.length = %d\n", recordKeys.length));
			sb.append(String.format("noneCalculationRecords.length = %d\n", noneCalculationRecordNames.size()));
			sb.append(String.format("fileRecordsProperties.length = %d\n", fileRecordsProperties.length));
			sb.append(String.format("recordSet.getNoneCalculationMeasurementNames.length = %d\n", this.getNoneCalculationMeasurementNames(recordSet.getChannelConfigNumber(), recordSet.getRecordNames()).length));
			sb.append(String.format("recordSet.getRecordNames.length = %d\n", recordSet.getRecordNames().length));
			log.log(Level.SEVERE, sb.toString());
			if (GDE.isWithUi()) this.application.openMessageDialogAsync(Messages.getString(MessageIds.GDE_MSGE2402));
		}
		return recordKeys;
	}

	//IHistoDevice functions

	/**
	 * @return true if the device supports a native file import for histo purposes
	 */
	@Override
	public boolean isHistoImportSupported() {
		return this.getClass().equals(HoTTAdapter2.class) && !this.getClass().equals(HoTTAdapter2M.class);
	}

	/**
	 * create recordSet and add record data size points from binary file to each measurement.
	 * it is possible to add only none calculation records if makeInActiveDisplayable calculates the rest.
	 * do not forget to call makeInActiveDisplayable afterwards to calculate the missing data.
	 * reduces memory and cpu load by taking measurement samples every x ms based on device setting |histoSamplingTime| .
	 * @param inputStream for loading the log data
	 * @param truss references the requested vault for feeding with the results (vault might be without measurements, settlements and scores)
	 */
	@Override
	public void getRecordSetFromImportFile(Supplier<InputStream> inputStream, VaultCollector truss, Analyzer analyzer) throws DataInconsitsentException,
			IOException, DataTypeException {
		String fileEnding = PathUtils.getFileExtention(truss.getVault().getLoadFilePath());
		if (GDE.FILE_ENDING_DOT_BIN.equals(fileEnding)) {
			new HoTTbinHistoReader2(new PickerParameters(analyzer)).read(inputStream, truss);
		} else if (GDE.FILE_ENDING_DOT_LOG.equals(fileEnding)) {
			// todo implement HoTTlogHistoReader
		} else {
			throw new UnsupportedOperationException(truss.getVault().getLoadFilePath());
		}
	}
	
	/**
	 * get the measurement ordinal of altitude, speed and trip length
	 * @return empty integer array if device does not fulfill complete requirement
	 */
	@Override
	public int[] getAtlitudeTripSpeedOrdinals() { 
		//0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
		//10=Height, 11=Climb 1, 12=Climb 3, 13=Climb 10 14=EventVario
		//15=Latitude, 16=Longitude, 17=Velocity, 18=DistanceStart, 19=DirectionStart, 20=TripDistance 21=NumSatellites 22=GPS-Fix 23=EventGPS
		RecordSet activeRecordSet = this.application.getActiveChannel().getActiveRecordSet();
		if (activeRecordSet.get(10).hasReasonableData() && activeRecordSet.get(20).hasReasonableData() && activeRecordSet.get(17).hasReasonableData())
			return new int[] { 10, 20, 17 };
		else
			return new int[0];
	}  
}
