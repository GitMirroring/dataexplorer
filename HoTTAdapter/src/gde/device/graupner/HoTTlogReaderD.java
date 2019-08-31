/**************************************************************************************
  	This file is part of GNU DataExplorer.

    GNU DataExplorer is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    DataExplorer is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with GNU DataExplorer.  If not, see <https://www.gnu.org/licenses/>.

    Copyright (c) 2011,2012,2013,2014,2015,2016,2017,2018,2019 Winfried Bruegmann
****************************************************************************************/
package gde.device.graupner;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.logging.Logger;

import gde.GDE;
import gde.data.Channel;
import gde.data.RecordSet;
import gde.device.graupner.HoTTAdapter.PickerParameters;
import gde.device.graupner.HoTTAdapter.Sensor;
import gde.exception.DataInconsitsentException;
import gde.io.DataParser;
import gde.log.Level;
import gde.messages.MessageIds;
import gde.messages.Messages;
import gde.ui.menu.MenuToolBar;
import gde.utils.StringHelper;

/**
 * Class to read Graupner HoTT binary data as saved on SD-Cards
 * @author Winfried Brügmann
 */
public class HoTTlogReaderD extends HoTTlogReader2 {
	final static Logger	log					= Logger.getLogger(HoTTlogReaderD.class.getName());
	static int[]				points;
	static RecordSet		recordSet;
	static boolean			isJustMigrated	= false;

	/**
	* read log data according to version 0
	* @param filePath
	* @param newPickerParameters
	* @throws IOException
	* @throws DataInconsitsentException
	*/
	public static synchronized void read(String filePath, PickerParameters newPickerParameters) throws Exception {
		final String $METHOD_NAME = "read";
		HoTTlogReaderD.pickerParameters = newPickerParameters;
		HashMap<String, String> fileInfoHeader = getFileInfo(new File(filePath), newPickerParameters);
		HoTTlogReader.detectedSensors = Sensor.getSetFromDetected(fileInfoHeader.get(HoTTAdapter.DETECTED_SENSOR));

		final File file = new File(fileInfoHeader.get(HoTTAdapter.FILE_PATH));
		long startTime = System.nanoTime() / 1000000;
		FileInputStream file_input = new FileInputStream(file);
		DataInputStream data_in = new DataInputStream(file_input);
		long fileSize = file.length();
		HoTTAdapterD device = (HoTTAdapterD) HoTTbinReader.application.getActiveDevice();
		int recordSetNumber = HoTTbinReader.channels.get(1).maxSize() + 1;
		String recordSetName = GDE.STRING_EMPTY;
		String recordSetNameExtend = getRecordSetExtend(file);
		Channel channel = null;
		boolean isReceiverData = false;
		boolean isVarioData = false;
		boolean isGPSData = false;
		boolean isGeneralData = false;
		boolean isElectricData = false;
		boolean isMotorDriverData = false;
		HoTTlogReaderD.recordSet = null;
		HoTTlogReaderD.isJustMigrated = false;
		//0=RF_RXSQ, 1=RXSQ, 2=Strength, 3=PackageLoss, 4=Tx_dbm, 5=Rx_dbm, 6=VoltageRx, 7=TemperatureRx
		//8=Height, 9=Climb 1, 10=Climb 3, 11=Climb 10
		//12=Latitude, 13=Longitude, 14=Velocity, 15=DistanceStart, 16=DirectionStart, 17=TripDistance
		//18=GPS.Satelites, 19=GPS.Fix, 20=HomeDirection, 21=NorthVelocity, 22=SpeedAccuracy, 23=GPS.Time, 24=EastVelocity, 25=HorizontalAccuracy, 26=Altitude, 27=GPS.Fix2, 28=Version
		//29=VoltageG, 30=CurrentG, 31=CapacityG, 32=PowerG, 33=BalanceG, 34=CellVoltageG 1, 35=CellVoltageG 2 .... 39=CellVoltageG 6, 40=Revolution, 41=FuelLevel, 42=VoltageG 1, 43=VoltageG 2, 44=TemperatureG 1, 45=TemperatureG 2
		//46=VoltageE, 47=CurrentE, 48=CapacityE, 49=PowerE, 50=BalanceE, 51=CellVoltageE 1, 52=CellVoltageE 2 .... 64=CellVoltageE 14, 65=Revolution, 66=VoltageE 1, 67=VoltageE 2, 68=TemperatureE 1, 69=TemperatureE 2
		//70=VoltageM, 71=CurrentM, 72=CapacityM, 73=PowerM, 74=RevolutionM, 75=TemperatureM
		//76=Ch 1, 77=Ch 2 , 78=Ch 3 .. 91=Ch 16 92=PowerOff, 93=BattLow, 94=Reset, 95=reserved
		//96=Test 00 97=Test 01.. 108=Test 12
		//109=SmoothedRx_dbm, 110=DiffRx_dbm, 111=LapsRx_dbm, 112=DiffDistance, 113=LapsDistance
		//114=VoltageRx_min 115=Speed G, 116=CellVoltage_min G 117=CellNumber_min G 118=Pressure 119=MotorRuntime E 120=Speed E
		//121=Temperature M2 122=Voltage Mmin 123=Current Mmax 124=RPM Mmax 125=Temperatire M1max 126=Temperature M2max
		//127=EventRx 128=EventVario 129=EventGPS 130=EventGAM 131=EventEAM 132=EventESC
		HoTTlogReaderD.points = new int[device.getNumberOfMeasurements(1)];
		HoTTbinReader.timeStep_ms = 0;
		int numberLogChannels = Integer.valueOf(fileInfoHeader.get("LOG NOB CHANNEL"));
		HoTTbinReader.dataBlockSize = 66 + numberLogChannels * 2;
		HoTTbinReader.buf = new byte[HoTTbinReader.dataBlockSize];
		int[] valuesChannel = new int[23];
		int[] valuesVario = new int[21];
		valuesVario[2] = 100000;
		int[] valuesGPS = new int[24];
		int[] valuesGAM = new int[26];
		int[] valuesEAM = new int[31];
		int[] valuesESC = new int[14];
		int logTimeStep = 1000/Integer.valueOf(fileInfoHeader.get("COUNTER").split("/")[1].split(GDE.STRING_BLANK)[0]);
		PackageLossDeque reverseChannelPackageLossCounter = new PackageLossDeque(logTimeStep);
		HoTTbinReader.isTextModusSignaled = false;
		int countPackageLoss = 0;
		int logDataOffset = Integer.valueOf(fileInfoHeader.get("LOG DATA OFFSET"));
		long numberDatablocks = (fileSize - logDataOffset) / HoTTbinReader.dataBlockSize;
		long startTimeStamp_ms = getStartTimeStamp(fileInfoHeader.get("LOG START TIME"), getStartTimeStamp(file.getName(), file.lastModified(), numberDatablocks));
		String date = StringHelper.getDate();
		String dateTime = new SimpleDateFormat("yyyy-MM-dd, HH:mm:ss").format(startTimeStamp_ms); //$NON-NLS-1$
		RecordSet tmpRecordSet;
		MenuToolBar menuToolBar = HoTTbinReader.application.getMenuToolBar();
		int progressIndicator = (int) (numberDatablocks / 30);
		GDE.getUiNotification().setProgress(0);

		try {
			//receiver data are always contained
			channel = HoTTbinReader.channels.get(1);
			channel.setFileDescription(HoTTbinReader.application.isObjectoriented() ? date + GDE.STRING_BLANK + HoTTbinReader.application.getObjectKey() : date);
			recordSetName = recordSetNumber + device.getRecordSetStemNameReplacement() + recordSetNameExtend;
			HoTTlogReaderD.recordSet = RecordSet.createRecordSet(recordSetName, device, 1, true, true, true);
			channel.put(recordSetName, HoTTlogReaderD.recordSet);
			tmpRecordSet = channel.get(recordSetName);
			tmpRecordSet.setRecordSetDescription(device.getName() + GDE.STRING_MESSAGE_CONCAT + Messages.getString(MessageIds.GDE_MSGT0129) + dateTime);
			tmpRecordSet.setStartTimeStamp(startTimeStamp_ms);
			//recordSet initialized and ready to add data

			//read all the data blocks from the file and parse
			data_in.skip(logDataOffset);
			for (int i = 0; i < numberDatablocks; i++) {
				data_in.read(HoTTbinReader.buf);
				if (log.isLoggable(Level.FINE)) {
					log.logp(Level.FINE, HoTTbinReader.$CLASS_NAME, $METHOD_NAME, StringHelper.byte2Hex4CharString(HoTTbinReader.buf, HoTTbinReader.buf.length));
				}

				//Ph(D)[4], Evt1(H)[5], Evt2(D)[6], Fch(D)[7], TXdBm(-D)[8], RXdBm(-D)[9], RfRcvRatio(D)[10], TrnRcvRatio(D)[11]
				//STATUS : Ph(D)[4], Evt1(H)[5], Evt2(D)[6], Fch(D)[7], TXdBm(-D)[8], RXdBm(-D)[9], RfRcvRatio(D)[10], TrnRcvRatio(D)[11]
				//S.INFOR : DEV(D)[22], CH(D)[23], SID(H)[24], WARN(H)[25]
				//if (!HoTTAdapter.isFilterTextModus || (HoTTbinReader.buf[6] & 0x01) == 0) { //switch into text modus
				if (HoTTbinReader.buf[8] != 0 && HoTTbinReader.buf[9] != 0) { //buf 8, 9, tx,rx, rx sensitivity data
					if (HoTTbinReader.buf[24] != 0x1F) {//rx sensitivity data
						if (log.isLoggable(Level.FINE)) {
							log.log(Level.FINE, String.format("Sensor %02X", HoTTbinReader.buf[26]));
						}
					}
					reverseChannelPackageLossCounter.add(1);
					HoTTlogReaderD.points[0] = reverseChannelPackageLossCounter.getPercentage() * 1000;
					//create and fill sensor specific data record sets
					if (log.isLoggable(Level.FINEST)) {
						log.logp(Level.FINEST, HoTTbinReader.$CLASS_NAME, $METHOD_NAME,
								StringHelper.byte2Hex2CharString(new byte[] { HoTTbinReader.buf[7] }, 1) + GDE.STRING_MESSAGE_CONCAT + StringHelper.printBinary(HoTTbinReader.buf[7], false));
					}

					//fill receiver data
					if (HoTTbinReader.buf[24] != 0x1F) { //receiver sensitive data
						//in 0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
						isReceiverData = parseReceiver(HoTTbinReader.buf, HoTTlogReaderD.points);
						HoTTlogReaderD.points[114] = HoTTlogReaderD.points[8]; //114=VoltageRx_min
						HoTTlogReaderD.points[8] = 0;
						HoTTlogReaderD.points[127] = HoTTlogReaderD.points[9]; //127=EventRx
						HoTTlogReaderD.points[9] = 0;
					}

					parseChannel(HoTTbinReader.buf, valuesChannel, numberLogChannels);
					//in 0=FreCh, 1=Tx, 2=Rx, 3=Ch 1, 4=Ch 2 .. 18=Ch 16 19=PowerOff 20=BattLow 21=Reset 22=Warning
					//out 76=Ch 1, 77=Ch 2 , 78=Ch 3 .. 91=Ch 16 92=PowerOff, 93=BattLow, 94=Reset, 95=warning
					System.arraycopy(valuesChannel, 3, HoTTlogReaderD.points, 76, 20); //copy channel data and events, warning

					switch ((byte) (HoTTbinReader.buf[26] & 0xFF)) { //actual sensor
					case HoTTAdapter.ANSWER_SENSOR_VARIO_19200:
						isVarioData = parseVario(HoTTbinReader.buf, valuesVario, true);
						if (isVarioData && isReceiverData) {
							migrateAddPoints(isVarioData, isGPSData, isGeneralData, isElectricData, isMotorDriverData, 1, valuesVario, valuesGPS, valuesGAM, valuesEAM, valuesESC);
							isReceiverData = false;
						}
						break;
					case HoTTAdapter.ANSWER_SENSOR_GPS_19200:
						isGPSData = parseGPS(HoTTbinReader.buf, valuesGPS, true);
						if (isGPSData && isReceiverData) {
							migrateAddPoints(isVarioData, isGPSData, isGeneralData, isElectricData, isMotorDriverData, 1, valuesVario, valuesGPS, valuesGAM, valuesEAM, valuesESC);
							isReceiverData = false;
						}
						break;
					case HoTTAdapter.ANSWER_SENSOR_GENERAL_19200:
						isGeneralData = parseGAM(HoTTbinReader.buf, valuesGAM, HoTTlogReaderD.recordSet, true);
						if (isGeneralData && isReceiverData) {
							migrateAddPoints(isVarioData, isGPSData, isGeneralData, isElectricData, isMotorDriverData, 1, valuesVario, valuesGPS, valuesGAM, valuesEAM, valuesESC);
							isReceiverData = false;
						}
						break;
					case HoTTAdapter.ANSWER_SENSOR_ELECTRIC_19200:
						isElectricData = parseEAM(HoTTbinReader.buf, valuesEAM, HoTTlogReaderD.recordSet, true);
						if (isElectricData && isReceiverData) {
							migrateAddPoints(isVarioData, isGPSData, isGeneralData, isElectricData, isMotorDriverData, 1, valuesVario, valuesGPS, valuesGAM, valuesEAM, valuesESC);
							isReceiverData = false;
						}
						break;
					case HoTTAdapter.ANSWER_SENSOR_MOTOR_DRIVER_19200:
						isMotorDriverData = parseESC(HoTTbinReader.buf, valuesESC, HoTTlogReaderD.recordSet);
						if (isMotorDriverData && isReceiverData) {
							migrateAddPoints(isVarioData, isGPSData, isGeneralData, isElectricData, isMotorDriverData, 1, valuesVario, valuesGPS, valuesGAM, valuesEAM, valuesESC);
							isReceiverData = false;
						}
						break;
					case 0x1F: //receiver sensitive data
					default:
						break;
					}

					if (HoTTlogReaderD.isJustMigrated && countPackageLoss > 0) {
						HoTTbinReader.lostPackages.add(countPackageLoss * 10);
						countPackageLoss = 0;
					}

					if (isReceiverData) { //this will only be true if no other sensor is connected
						HoTTlogReaderD.recordSet.addPoints(HoTTlogReaderD.points, HoTTbinReader.timeStep_ms);
						isReceiverData = false;
					}
					else if (!HoTTlogReaderD.isJustMigrated) { //this will only be true if no other sensor is connected and channel 4
						HoTTlogReaderD.recordSet.addPoints(HoTTlogReaderD.points, HoTTbinReader.timeStep_ms);
					}
					HoTTlogReaderD.isJustMigrated = false;

					HoTTbinReader.timeStep_ms += logTimeStep;// add default time step from log record of 100 msec

					if (i % progressIndicator == 0) GDE.getUiNotification().setProgress((int) (i * 100 / numberDatablocks));
				}
					else { //skip empty block, but add time step
						if (log.isLoggable(Level.FINE)) log.log(Level.FINE, "-->> Found tx=rx=0 dBm");

						reverseChannelPackageLossCounter.add(0);
						HoTTlogReaderD.points[0] = reverseChannelPackageLossCounter.getPercentage() * 1000;
						countPackageLoss+=1; // add up lost packages in telemetry data

						parseChannel(HoTTbinReader.buf, valuesChannel, numberLogChannels);
						//in 0=FreCh, 1=Tx, 2=Rx, 3=Ch 1, 4=Ch 2 .. 18=Ch 16 19=PowerOff 20=BattLow 21=Reset 22=Warning
						//out 76=Ch 1, 77=Ch 2 , 78=Ch 3 .. 91=Ch 16 92=PowerOff, 93=BattLow, 94=Reset, 95=warning
						System.arraycopy(valuesChannel, 3, HoTTlogReaderD.points, 76, 20); //copy channel data and events, warning
						HoTTlogReaderD.recordSet.addPoints(HoTTlogReaderD.points, HoTTbinReader.timeStep_ms);

						HoTTbinReader.timeStep_ms += logTimeStep;
						//reset buffer to avoid mixing data >> 20 Jul 14, not any longer required due to protocol change requesting next sensor data block
						//logCountVario = logCountGPS = logCountGeneral = logCountElectric = logCountMotorDriver = 0;
					}
//				} //switch into text modus - telemetry menu
//				else if (!HoTTbinReader.isTextModusSignaled) {
//					HoTTbinReader.isTextModusSignaled = true;
//					HoTTbinReader.application.openMessageDialogAsync(Messages.getString(gde.device.graupner.hott.MessageIds.GDE_MSGW2404));
//				}
			}
			String packageLossPercentage = tmpRecordSet.getRecordDataSize(true) > 0 ? String.format("%.1f", (countPackageLoss / tmpRecordSet.getTime_ms(tmpRecordSet.getRecordDataSize(true) - 1) * 1000)) : "100";
			tmpRecordSet.setRecordSetDescription(tmpRecordSet.getRecordSetDescription()
					+ Messages.getString(gde.device.graupner.hott.MessageIds.GDE_MSGI2404, new Object[] { countPackageLoss, packageLossPercentage, HoTTbinReader.lostPackages.getStatistics() })
					+ Sensor.getSetAsSignature(HoTTbinReader.detectedSensors));
			log.logp(Level.WARNING, HoTTbinReader.$CLASS_NAME, $METHOD_NAME, "skipped number receiver data due to package loss = " + countPackageLoss); //$NON-NLS-1$
			log.logp(Level.TIME, HoTTbinReader.$CLASS_NAME, $METHOD_NAME, "read time = " + StringHelper.getFormatedTime("mm:ss:SSS", (System.nanoTime() / 1000000 - startTime))); //$NON-NLS-1$ //$NON-NLS-2$

			if (GDE.isWithUi()) {
				GDE.getUiNotification().setProgress(99);
				device.makeInActiveDisplayable(HoTTlogReaderD.recordSet);
				device.updateVisibilityStatus(HoTTlogReaderD.recordSet, true);
				channel.applyTemplate(recordSetName, false);

				//write filename after import to record description
				HoTTlogReaderD.recordSet.descriptionAppendFilename(file.getName());

				menuToolBar.updateChannelSelector();
				menuToolBar.updateRecordSetSelectCombo();
				GDE.getUiNotification().setProgress(100);
			}
		}
		finally {
			data_in.close();
			data_in = null;
		}
	}

	/**
	 * parse the buffered data to vario values
	 * @param _buf
	 */
	protected static boolean parseVario(byte[] _buf, int[] values, boolean isHoTTAdapter2) {
		//0=RXSQ, 1=Height, 2=Climb 1, 3=Climb 3, 4=Climb 10, 5=VoltageRx, 6=TemperatureRx 7=EventVario
		//10=Height, 11=Climb 1, 12=Climb 3, 13=Climb 10 14=EventVario
		//sensor byte: 26=sensor byte
		//27=inverseBits 28,29=altitude 30,31=altitude_max 32,33=altitude_min 34,35=climb1 36,37=climb3 38,39=climb10
		HoTTlogReader.parseVario(_buf, values, isHoTTAdapter2);
//		values[0] = (_buf[16] & 0xFF) * 1000;
//		HoTTbinReader.tmpHeight = isHoTTAdapter2 ? DataParser.parse2Short(_buf, 28) - 500 : DataParser.parse2Short(_buf, 28);
//		values[1] = HoTTbinReader.tmpHeight * 1000;
//		//pointsVarioMax = DataParser.parse2Short(buf1, 30) * 1000;
//		//pointsVarioMin = DataParser.parse2Short(buf1, 32) * 1000;
//		values[2] = (DataParser.parse2UnsignedShort(_buf, 34) - 30000) * 10;
//		HoTTbinReader.tmpClimb10 = isHoTTAdapter2 ? (DataParser.parse2UnsignedShort(_buf , 38) - 30000) * 10 : DataParser.parse2UnsignedShort(_buf , 38) + 1000;
//		values[3] = isHoTTAdapter2 ? (DataParser.parse2UnsignedShort(_buf, 36) - 30000) * 10 : DataParser.parse2UnsignedShort(_buf, 36) * 1000;
//		values[4] = HoTTbinReader.tmpClimb10;
//		values[5] = (_buf[13] & 0xFF) * 1000;				//voltageRx
//		values[6] = ((_buf[14] & 0xFF) - 20) * 1000;//temperaturRx
//		values[7] = (_buf[27] & 0x3F) * 1000; 			//inverse event

		//if ((_buf[40] & 0xFF) == 0xFF) { // gyro receiver
			//96=Test 00, 97=Test 01, 98=Test 02, ... , 108=Test 12
			for (int i = 0, j = 0; i < 13; i++, j += 2) {
				values[i + 8] = DataParser.parse2Short(_buf, 40 + j) * 1000;
			}
		//}
		if (log.isLoggable(Level.FINER)) {
			printSensorValues(_buf, values, 21);
		}
		return true;
	}

	/**
	 * parse the buffered data to GPS values
	 * @param _buf
	 */
	protected static boolean parseGPS(byte[] _buf, int[] values, boolean isHoTTAdapter2) {
		//0=RXSQ, 1=Latitude, 2=Longitude, 3=Height, 4=Climb 1, 5=Climb 3, 6=Velocity, 7=DistanceStart, 8=DirectionStart, 9=TripLength, 10=VoltageRx, 11=TemperatureRx 12=satellites 13=GPS-fix 14=EventGPS
		//10=Height, 11=Climb 1, 12=Climb 3
		//15=Latitude, 16=Longitude, 17=Velocity, 18=DistanceStart, 19=DirectionStart, 20=TripDistance 21=NumSatellites 22=GPS-Fix 23=EventGPS
		//sensor byte: 26=sensor byte
		//27,28=InverseBits 29=moveDirection 30,31=speed 32,33,34,35,36=latitude 37,38,39,40,41=longitude 42,43=distanceStart 44,45=altitude
		//46,47=climb1 48=climb3 49=#satellites 50=GPS-Fix 51=homeDirection 52,53=northVelocity 54=hAcc 55-58=GPStime 59,60=eastVelocity
		//61=hAcc 62-65=freeChars 66=version
		HoTTlogReader.parseGPS(_buf, values, isHoTTAdapter2);
//		values[0] = (_buf[16] & 0xFF) * 1000;
//		HoTTbinReader.tmpHeight = isHoTTAdapter2 ? DataParser.parse2Short(_buf, 44) - 500 : DataParser.parse2Short(_buf, 44);
//		HoTTbinReader.tmpClimb1 = isHoTTAdapter2 ? (DataParser.parse2UnsignedShort(_buf, 46) - 30000) : DataParser.parse2UnsignedShort(_buf, 46);
//		HoTTbinReader.tmpClimb3 = isHoTTAdapter2 ? (_buf[48] & 0xFF) - 120 : (_buf[48] & 0xFF);
//		HoTTbinReader.tmpVelocity = DataParser.parse2Short(_buf, 30) * 1000;
//		values[6] = HoTTbinReader.tmpVelocity;
//		HoTTbinReader.tmpLatitude = DataParser.parse2Short(_buf, 33) * 10000 + DataParser.parse2Short(_buf, 35);
//		values[1] = HoTTbinReader.tmpLatitude;
//		HoTTbinReader.tmpLongitude = DataParser.parse2Short(_buf, 38) * 10000 + DataParser.parse2Short(_buf, 40);
//		values[2] = HoTTbinReader.tmpLongitude;
//		values[3] = HoTTbinReader.tmpHeight * 1000;		//altitude
//		values[4] = HoTTbinReader.tmpClimb1 * 10;			//climb1
//		values[5] = HoTTbinReader.tmpClimb3 * 1000;		//climb3
//		values[7] = DataParser.parse2Short(_buf, 42) * 1000;
//		values[8] = (_buf[51] & 0xFF) * 1000;
//		values[9] = 0; 																//trip length
//		values[10] = (_buf[13] & 0xFF) * 1000;				//voltageRx
//		values[11] = ((_buf[14] & 0xFF) - 20) * 1000;	//temperaturRx
//		values[12] = (_buf[49] & 0xFF) * 1000;
//		try {
//			values[13] = Integer.valueOf(String.format("%c", _buf[50])) * 1000;
//		}
//		catch (NumberFormatException e1) {
//			//ignore;
//		}
//		values[14] = (_buf[27] & 0x0F) * 1000; //inverse event

		//sensor byte: 26=sensor byte
		//27,28=InverseBits 29=moveDirection 30,31=speed 32,33,34,35,36=latitude 37,38,39,40,41=longitude 42,43=distanceStart 44,45=altitude
		//46,47=climb1 48=climb3 49=#satellites 50=GPS-Fix 51=homeDirection 52,53=northVelocity 54=hAcc 55-58=GPStime 59,60=eastVelocity
		//61=hAcc 62-65=freeChars 66=version

		//15=HomeDirection 16=NorthVelocity 17=SpeedAccuracy 18=Time 19=EastVelocity 20=HorizontalAccuracy 21=Altitude 22=Fix2 23=Version
		values[15] = (_buf[51] & 0xFF) * 1000;
		values[16] = DataParser.parse2Short(_buf, 52) * 1000;
		values[17] = (_buf[54] & 0xFF) * 1000;
		if (!HoTTbinReaderD.isGpsStartTimeSet) {
			HoTTbinReaderD.gpsStartTime = getStartTime(_buf[55]&0xFF, _buf[56]&0xFF, _buf[57]&0xFF, _buf[58]&0xFF);
			HoTTbinReader.log.log(Level.FINE, String.format("%02d:%02d:%02d.%03d", (_buf[55] & 0xFF), (_buf[56] & 0xFF), (_buf[57] & 0xFF), (_buf[58] & 0xFF)));
			HoTTbinReaderD.isGpsStartTimeSet = true;
			final RecordSet activeRecordSet = HoTTbinReader.application.getActiveRecordSet();
			if (activeRecordSet != null) {
				activeRecordSet.setRecordSetDescription(activeRecordSet.getRecordSetDescription() + GDE.STRING_MESSAGE_CONCAT + "GPS start time = "
						+ String.format("%02d:%02d:%02d.%03d ", (_buf[55] & 0xFF), (_buf[56] & 0xFF), (_buf[57] & 0xFF), (_buf[58] & 0xFF)));
			}
		}
		values[18] = getStartTime(_buf[55]&0xFF, _buf[56]&0xFF, _buf[57]&0xFF, _buf[58]&0xFF) - HoTTbinReaderD.gpsStartTime;
		values[19] = DataParser.parse2Short(_buf, 59) * 1000;
		values[20] = (_buf[61] & 0xFF) * 1000;
		values[21] = DataParser.parse2Short(_buf, 62) * 1000;
		try {
			values[22] = Integer.valueOf(String.format("%c", _buf[64])) * 1000;
		}
		catch (NumberFormatException e) {
			// ignore
		}
		values[23] = (_buf[65] & 0xFF) * 1000;

		if (log.isLoggable(Level.FINER)) {
			printSensorValues(_buf, values, 24);
		}
		return true;
	}

	/**
	 * migrate sensor measurement values and add to record set, receiver data are always updated
	 * @param isVarioData
	 * @param isGPSData
	 * @param isGeneralData
	 * @param isElectricData
	 * @param isMotorDriverData
	 * @param channelNumber
	 * @throws DataInconsitsentException
	 */
	public static void migrateAddPoints(boolean isVarioData, boolean isGPSData, boolean isGeneralData, boolean isElectricData, boolean isMotorDriverData, int channelNumber,
			int[] valuesVario, int[] valuesGPS, int[] valuesGAM, int[] valuesEAM, int[] valuesESC)
			throws DataInconsitsentException {
		//receiver data gets integrated each cycle
		//0=RF_RXSQ, 1=RXSQ, 2=Strength, 3=PackageLoss, 4=Tx_dbm, 5=Rx_dbm, 6=VoltageRx, 7=TemperatureRx
		//8=Height, 9=Climb 1, 10=Climb 3, 11=Climb 10
		//12=Latitude, 13=Longitude, 14=Velocity, 15=DistanceStart, 16=DirectionStart, 17=TripDistance
		//18=GPS.Satelites, 19=GPS.Fix, 20=HomeDirection, 21=NorthVelocity, 22=SpeedAccuracy, 23=GPS.Time, 24=EastVelocity, 25=HorizontalAccuracy, 26=Altitude, 27=GPS.Fix2, 28=Version
		//29=VoltageG, 30=CurrentG, 31=CapacityG, 32=PowerG, 33=BalanceG, 34=CellVoltageG 1, 35=CellVoltageG 2 .... 39=CellVoltageG 6, 40=Revolution, 41=FuelLevel, 42=VoltageG 1, 43=VoltageG 2, 44=TemperatureG 1, 45=TemperatureG 2
		//46=VoltageE, 47=CurrentE, 48=CapacityE, 49=PowerE, 50=BalanceE, 51=CellVoltageE 1, 52=CellVoltageE 2 .... 64=CellVoltageE 14, 65=Revolution, 66=VoltageE 1, 67=VoltageE 2, 68=TemperatureE 1, 69=TemperatureE 2
		//70=VoltageM, 71=CurrentM, 72=CapacityM, 73=PowerM, 74=RevolutionM, 75=TemperatureM
		//76=Ch 1, 77=Ch 2 , 78=Ch 3 .. 91=Ch 16
		//92=PowerOff, 93=BattLow, 94=Reset, 95=Warning
		//96=Test 00 97=Test 01.. 108=Test 12
		//109=SmoothedRx_dbm, 110=DiffRx_dbm, 111=LapsRx_dbm, 112=DiffDistance, 113=LapsDistance
		//114=VoltageRx_min 115=Speed G, 116=CellVoltage_min G 117=CellNumber_min G 118=Pressure 119=MotorRuntime E 120=Speed E
		//121=Temperature M2 122=Voltage Mmin 123=Current Mmax 124=RPM Mmax 125=Temperatire M1max 126=Temperature M2max
		//127=EventRx 128=EventVario 129=EventGPS 130=EventGAM 131=EventEAM 132=EventESC

		//in 0=RXSQ, 1=Voltage, 2=Current, 3=Capacity, 4=Power, 5=Balance, 6=CellVoltage 1, 7=CellVoltage 2 .... 19=CellVoltage 14,
		//in 20=Height, 21=Climb 1, 22=Climb 3, 23=Voltage 1, 24=Voltage 2, 25=Temperature 1, 26=Temperature 2 27=RPM 28=MotorTime 29=Speed 30=Event
		if (isElectricData) {
			//out 10=Height, 11=Climb 1, 12=Climb 3
			for (int j = 0; !isVarioData && !isGPSData && !isGeneralData && j < 3; j++) { //0=altitude 1=climb1 2=climb3
				HoTTlogReaderD.points[j+8] = valuesEAM[j+20];
			}
			//out 46=VoltageE, 47=CurrentE, 48=CapacityE, 49=PowerE, 50=BalanceE, 51=CellVoltageE 1, 52=CellVoltageE 2 .... 64=CellVoltageE 14,
			for (int k = 0; k < 19; k++) {
				HoTTlogReaderD.points[k+46] = valuesEAM[k+1];
			}
			//out 65=Revolution,
			HoTTlogReaderD.points[65] = valuesEAM[27];
			//out 66=VoltageE 1, 67=VoltageE 2, 68=TemperatureE 1, 69=TemperatureE 2
			for (int k = 0; k < 4; k++) {
				HoTTlogReaderD.points[k+66] = valuesEAM[k+23];
			}
			//out 119=MotorRuntime E 120=Speed E 131=EventEAM
			HoTTlogReaderD.points[119] = valuesEAM[28];
			HoTTlogReaderD.points[120] = valuesEAM[29];
			HoTTlogReaderD.points[131] = valuesEAM[30];
		}
		//in 0=RF_RXSQ, 1=Voltage, 2=Current, 3=Capacity, 4=Power, 5=Balance, 6=CellVoltage 1, 7=CellVoltage 2 .... 11=CellVoltage 6, 12=Revolution,
		//in 13=Height, 14=Climb, 15=Climb3, 16=FuelLevel, 17=Voltage 1, 18=Voltage 2, 19=Temperature 1, 20=Temperature 2
		//in 21=Speed, 22=LowestCellVoltage, 23=LowestCellNumber, 24=Pressure, 24=Event
		if (isGeneralData) {
			//out 8=Height, 9=Climb 1, 10=Climb 3
			for (int k = 0; !isVarioData && !isGPSData && k < 3; k++) {
				HoTTlogReaderD.points[k+8] = valuesGAM[k+13];
			}
			//out 29=VoltageG, 30=CurrentG, 31=CapacityG, 32=PowerG, 33=BalanceG, 34=CellVoltageG 1, 35=CellVoltageG 2 .... 39=CellVoltageG 6, 40=Revolution,
			for (int j = 0; j < 12; j++) {
				HoTTlogReaderD.points[j+29] = valuesGAM[j+1];
			}
			//out 41=FuelLevel, 42=VoltageG 1, 43=VoltageG 2, 44=TemperatureG 1, 45=TemperatureG 2
			for (int j = 0; j < 5; j++) {
				HoTTlogReaderD.points[j+41] = valuesGAM[j+16];
			}
			//out 115=Speed G, 116=CellVoltage_min G 117=CellNumber_min G 118=Pressure
			for (int j = 0; j < 4; j++) {
				HoTTlogReaderD.points[j+115] = valuesGAM[j+21];
			}
			HoTTlogReaderD.points[130] = valuesGAM[24]; //130=EventGAM
		}
		//in 0=RXSQ, 1=Latitude, 2=Longitude, 3=Height, 4=Climb 1, 5=Climb 3, 6=Velocity, 7=DistanceStart, 8=DirectionStart, 9=TripLength, 10=VoltageRx, 11=TemperatureRx
		//in 12=satellites 13=GPS-fix 14=EventGPS
		//in 15=HomeDirection 16=NorthVelocity 17=SpeedAccuracy 18=Time 19=EastVelocity 20=HorizontalAccuracy 21=Altitude 22=Fix2 23=Version
		if (isGPSData) {
			//out 8=Height, 9=Climb 1, 10=Climb 3
			for (int j = 0; !isVarioData && j < 3; j++) {
				HoTTlogReaderD.points[j+8] = valuesGPS[j+3];
			}
			//out 12=Latitude, 13=Longitude,
			HoTTlogReaderD.points[12] = valuesGPS[1];
			HoTTlogReaderD.points[13] = valuesGPS[2];
			//out 14=Velocity, 15=DistanceStart, 16=DirectionStart, 17=TripDistance
			for (int k = 0; k < 4; k++) {
				HoTTlogReaderD.points[k+14] = valuesGPS[k+6];
			}
			//18=GPS.Satelites, 19=GPS.Fix,
			HoTTlogReaderD.points[18] = valuesGPS[12];
			HoTTlogReaderD.points[19] = valuesGPS[13];
			//20=HomeDirection, 21=NorthVelocity, 22=SpeedAccuracy, 23=GPS.Time, 24=EastVelocity, 25=HorizontalAccuracy, 26=Altitude, 27=GPS.Fix2, 28=Version
			for (int k = 0; k < 9; k++) {
				HoTTlogReaderD.points[k+20] = valuesGPS[k+15];
			}
			HoTTlogReaderD.points[129] = valuesGPS[14]; //129=EventGPS
		}
		//in 0=RXSQ, 1=Height, 2=Climb 1, 3=Climb 3, 4=Climb 10, 5=VoltageRx, 6=TemperatureRx	7=EventVario
		if (isVarioData) {
			//out 8=Height, 9=Climb 1, 10=Climb 3, 11=Climb 10
			for (int j = 0; j < 4; j++) {
				HoTTlogReaderD.points[j+8] = valuesVario[j+1];
			}
			HoTTlogReaderD.points[128] = valuesVario[7]; //128=EventVario

			//special test data of FBL receivers
			//96=Test 00, 97=Test 01, 98=Test 02, ... , 108=Test 12
			for (int j = 0; j < 13; j++) {
				HoTTlogReaderD.points[j + 96] = valuesVario[j + 8];
			}
		}
		//in 0=RF_RXSQ, 1=Voltage, 2=Current, 3=Capacity, 4=Power, 5=Revolution, 6=Temperature1,
		//in 7=Temperature2, 8=Voltage_min, 9=Current_max, 10=Revolution_max, 11=Temperature1_max, 12=Temperature2_max 13=Event
		if (isMotorDriverData) {
			//out 70=VoltageM, 71=CurrentM, 72=CapacityM, 73=PowerM, 74=RevolutionM, 75=TemperatureM
			for (int j = 0; j < 6; j++) {
				HoTTlogReaderD.points[j+70] = valuesESC[j+1];
			}
			//121=Temperature M2 122=Voltage Mmin 123=Current Mmax 124=RPM Mmax 125=Temperatire M1max 126=Temperature M2max
			for (int j = 0; j < 6; j++) {
				HoTTlogReaderD.points[j+121] = valuesESC[j+7];
			}
			HoTTlogReaderD.points[132] = valuesESC[13]; //132=EventESC
		}
				
		//add altitude and climb values from selected sensor
		//log.log(Level.OFF, String.format("pickerParameters.altitudeClimbSensorSelection = %s", pickerParameters.altitudeClimbSensorSelection));
		switch (Sensor.VALUES[HoTTbinReader.pickerParameters.altitudeClimbSensorSelection]) {
		case VARIO:
			//8=Height, 9=Climb 1, 10=Climb 3, 11=Climb 10
			if (isVarioData)
				for (int j = 0; j < 4; j++) {
					HoTTlogReaderD.points[j+8] = valuesVario[j+1];
				}
			break;
		case GPS:
			//8=Height, 9=Climb 1, 10=Climb 3
			if (isGPSData)
				for (int j = 0; j < 3; j++) {
					HoTTlogReaderD.points[j+8] = valuesGPS[j+3];
				}
			HoTTlogReaderD.points[11] = 0;
			break;
		case GAM:
			//8=Height, 9=Climb 1, 10=Climb 3
			if (isGeneralData)
				for (int j = 0; j < 3; j++) {
					HoTTlogReaderD.points[j+8] = valuesGAM[j+13];
				}
			HoTTlogReaderD.points[11] = 0;
			break;
		case EAM:
			//8=Height, 9=Climb 1, 10=Climb 3
			if (isElectricData)
				for (int j = 0; j < 3; j++) { //0=altitude 1=climb1 2=climb3
					HoTTlogReaderD.points[j+8] = valuesEAM[j+20];
				}
			HoTTlogReaderD.points[11] = 0;
			break;
		default:
			break;
		}

		HoTTlogReaderD.recordSet.addPoints(HoTTlogReaderD.points, HoTTbinReader.timeStep_ms);
		HoTTlogReaderD.isJustMigrated = true;
	}

	private static int getStartTime(int HH, int mm, int ss, int SSS) {
		try {
			return Integer.valueOf(String.format("%02d%02.0f%02.0f%03d", HH, 100.0 / 60 * mm, 100.0 / 60 * ss, SSS));
		}
		catch (NumberFormatException e) {
			return HoTTlogReaderD.pointsGPS[23] + HoTTbinReaderD.gpsStartTime; //keep time unchanged
		}
	}
}
