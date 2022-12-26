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

    Copyright (c) 2011,2012,2013,2014,2015,2016,2017,2018,2019,2020,2021,2022 Winfried Bruegmann
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
import gde.log.Level;
import gde.messages.MessageIds;
import gde.messages.Messages;
import gde.ui.menu.MenuToolBar;
import gde.utils.StringHelper;

/**
 * Class to read Graupner HoTT binary data as saved on SD-Cards
 * @author Winfried Brügmann
 */
public class HoTTlogReader2 extends HoTTlogReader {
	final static Logger	log					= Logger.getLogger(HoTTlogReader2.class.getName());
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
		HoTTlogReader.pickerParameters = newPickerParameters;
		HashMap<String, String> fileInfoHeader = getFileInfo(new File(filePath), newPickerParameters);
		HoTTlogReader.detectedSensors = Sensor.getSetFromDetected(fileInfoHeader.get(HoTTAdapter.DETECTED_SENSOR));

		final File file = new File(fileInfoHeader.get(HoTTAdapter.FILE_PATH));
		long startTime = System.nanoTime() / 1000000;
		FileInputStream file_input = new FileInputStream(file);
		DataInputStream data_in = new DataInputStream(file_input);
		long fileSize = file.length();
		HoTTAdapter2 device = (HoTTAdapter2) HoTTbinReader.application.getActiveDevice();
		int recordSetNumber = HoTTbinReader.channels.get(1).maxSize() + 1;
		String recordSetName = GDE.STRING_EMPTY;
		String recordSetNameExtend = getRecordSetExtend(file);
		Channel channel = null;
		int channelNumber = HoTTlogReader.pickerParameters.analyzer.getActiveChannel().getNumber();
		boolean isReceiverData = false;
		boolean isVarioData = false;
		boolean isGPSData = false;
		boolean isGeneralData = false;
		boolean isElectricData = false;
		boolean isMotorDriverData = false;
		boolean isVarioDetected = false;
		boolean isGPSdetected = false;
		boolean isESCdetected = false;
		HoTTlogReader2.recordSet = null;
		HoTTlogReader2.isJustMigrated = false;
		// 0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
		// 10=Altitude, 11=Climb 1, 12=Climb 3, 13=Climb 10 14=EventVario 15=misc Vario_1 16=misc Vario_2 17=misc Vario_3 18=misc Vario_4 19=misc Vario_5
		// 20=Latitude, 21=Longitude, 22=Velocity, 23=Distance, 24=Direction, 25=TripDistance 26=NumSatellites 27=GPS-Fix 28=EventGPS
		// 29=HomeDirection 30=Roll 31=Pitch 32=Yaw 33=GyroX 34=GyroY 35=GyroZ 36=Vibration 37=Version	
		// 38=Voltage G, 39=Current G, 40=Capacity G, 41=Power G, 42=Balance G, 43=CellVoltage G1, 44=CellVoltage G2 .... 48=CellVoltage G6,
		// 49=Revolution G, 50=FuelLevel, 51=Voltage G1, 52=Voltage G2, 53=Temperature G1, 54=Temperature G2 55=Speed G, 56=LowestCellVoltage,
		// 57=LowestCellNumber, 58=Pressure, 59=Event G
		// 60=Voltage E, 61=Current E, 62=Capacity E, 63=Power E, 64=Balance E, 65=CellVoltage E1, 66=CellVoltage E2 .... 78=CellVoltage E14,
		// 79=Voltage E1, 80=Voltage E2, 81=Temperature E1, 82=Temperature E2 83=Revolution E 84=MotorTime 85=Speed 86=Event E
		// 87=VoltageM, 88=CurrentM, 89=CapacityM, 90=PowerM, 91=RevolutionM, 92=TemperatureM 1, 93=TemperatureM 2 94=Voltage_min, 95=Current_max,
		// 96=Revolution_max, 97=Temperature1_max, 98=Temperature2_max 99=Event M
		// 100=Speed 101=Speed_max 102=PWM 103=Throttle 104=VoltageBEC 105=VoltageBEC_min 106=CurrentBEC 107=TemperatureBEC 108=TemperatureCap 
		// 109=Timing(empty) 110=Temperature_aux 111=Gear 112=YGEGenExt 113=MotStatEscNr 114=misc ESC_15 115=VersionESC

		// 87=Ch 1, 88=Ch 2, 89=Ch 3 .. 102=Ch 16, 103=PowerOff, 104=BatterieLow, 105=Reset, 106=reserve
		// 107=VoltageM, 108=CurrentM, 109=CapacityM, 110=PowerM, 111=RevolutionM, 112=TemperatureM 1, 113=TemperatureM 2 114=Voltage_min, 115=Current_max,
		// 116=Revolution_max, 117=Temperature1_max, 118=Temperature2_max 119=Event M
		// 120=Speed 121=Speed_max 122=PWM 123=Throttle 124=VoltageBEC 125=VoltageBEC_min 125=CurrentBEC 127=TemperatureBEC 128=TemperatureCap 
		// 129=Timing(empty) 130=Temperature_aux 131=Gear 132=YGEGenExt 133=MotStatEscNr 134=misc ESC_15 135=VersionESC
		HoTTlogReader2.points = new int[device.getNumberOfMeasurements(channelNumber)];
		HoTTbinReader.timeStep_ms = 0;
		int numberLogChannels = Integer.valueOf(fileInfoHeader.get("LOG NOB CHANNEL"));
		HoTTbinReader.dataBlockSize = 66 + numberLogChannels * 2;
		HoTTbinReader.buf = new byte[HoTTbinReader.dataBlockSize];
		int[] valuesChannel = new int[23];
		int[] valuesVario = new int[13];
		valuesVario[2] = 100000;
		int[] valuesGPS = new int[24];
		int[] valuesGAM = new int[26];
		int[] valuesEAM = new int[31];
		int[] valuesESC = new int[30];
		int logTimeStep = 1000/Integer.valueOf(fileInfoHeader.get("COUNTER").split("/")[1].split(GDE.STRING_BLANK)[0]);
		PackageLossDeque reverseChannelPackageLossCounter = new PackageLossDeque(logTimeStep);
		HoTTbinReader.isTextModusSignaled = false;
		int countPackageLoss = 0;
		int logDataOffset = Integer.valueOf(fileInfoHeader.get("LOG DATA OFFSET"));
		long numberDatablocks = (fileSize - logDataOffset) / HoTTbinReader.dataBlockSize;
		long startTimeStamp_ms = HoTTbinReader.getStartTimeStamp(fileInfoHeader.get("LOG START TIME"), HoTTbinReader.getStartTimeStamp(file.getName(), file.lastModified(), numberDatablocks));
		String date = new SimpleDateFormat("yyyy-MM-dd").format(startTimeStamp_ms); //$NON-NLS-1$
		String dateTime = new SimpleDateFormat("yyyy-MM-dd, HH:mm:ss").format(startTimeStamp_ms); //$NON-NLS-1$
		RecordSet tmpRecordSet;
		MenuToolBar menuToolBar = HoTTbinReader.application.getMenuToolBar();
		int progressIndicator = (int) (numberDatablocks / 30);
		GDE.getUiNotification().setProgress(0);

		try {
			//receiver data are always contained
			channel = HoTTbinReader.channels.get(channelNumber);
			String newFileDescription = HoTTbinReader.application.isObjectoriented() ? date + GDE.STRING_BLANK + HoTTbinReader.application.getObjectKey()	: date;
			if (channel.getFileDescription().length() <= newFileDescription.length() || (HoTTbinReader.application.isObjectoriented() && !channel.getFileDescription().contains(HoTTbinReader.application.getObjectKey())))
				channel.setFileDescription(newFileDescription);
			recordSetName = recordSetNumber + device.getRecordSetStemNameReplacement() + recordSetNameExtend;
			HoTTlogReader2.recordSet = RecordSet.createRecordSet(recordSetName, device, channelNumber, true, true, true);
			channel.put(recordSetName, HoTTlogReader2.recordSet);
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
						if (log.isLoggable(Level.INFO)) {
							log.log(Level.INFO, String.format("Sensor %02X", HoTTbinReader.buf[26]));
						}
					}
					reverseChannelPackageLossCounter.add(1);
					HoTTlogReader2.points[0] = reverseChannelPackageLossCounter.getPercentage() * 1000;
					//create and fill sensor specific data record sets
					if (log.isLoggable(Level.FINEST)) {
						log.logp(Level.FINEST, HoTTbinReader.$CLASS_NAME, $METHOD_NAME,
								StringHelper.byte2Hex2CharString(new byte[] { HoTTbinReader.buf[7] }, 1) + GDE.STRING_MESSAGE_CONCAT + StringHelper.printBinary(HoTTbinReader.buf[7], false));
					}

					//fill receiver data
					if (HoTTbinReader.buf[24] != 0x1F) { //receiver sensitive data
						isReceiverData = parseReceiver(HoTTbinReader.buf, HoTTlogReader2.points);
					}

					if (channelNumber == 4) {
						parseChannel(HoTTbinReader.buf, valuesChannel, numberLogChannels);
						//in 0=FreCh, 1=Tx, 2=Rx, 3=Ch 1, 4=Ch 2 .. 18=Ch 16 19=PowerOff 20=BattLow 21=Reset 22=Warning
						//out 87=Ch 1, 88=Ch 2, 89=Ch 3 .. 102=Ch 16, 103=PowerOff, 104=BatterieLow, 105=Reset, 106=reserve
						System.arraycopy(valuesChannel, 3, HoTTlogReader2.points, 87, 20); //copy channel data and events, warning
					}

					switch ((byte) (HoTTbinReader.buf[26] & 0xFF)) { //actual sensor
					case HoTTAdapter.ANSWER_SENSOR_VARIO_19200:
						if (!detectedSensors.contains(Sensor.VARIO)) 
							detectedSensors.add(Sensor.VARIO);
						isVarioData = parseVario(HoTTbinReader.buf, valuesVario, true);
						if (isVarioData && isReceiverData) {
							migrateAddPoints(isVarioData, isGPSData, isGeneralData, isElectricData, isMotorDriverData, channelNumber, valuesVario, valuesGPS, valuesGAM, valuesEAM, valuesESC);
							isReceiverData = false;
							
							if (!isVarioDetected) {
								HoTTAdapter2.updateVarioTypeDependent((HoTTbinReader.buf[65] & 0xFF), device, HoTTlogReader2.recordSet);
								isVarioDetected = true;								
							}
						}
						break;
					case HoTTAdapter.ANSWER_SENSOR_GPS_19200:
						if (!detectedSensors.contains(Sensor.GPS)) 
							detectedSensors.add(Sensor.GPS);
						isGPSData = parseGPS(HoTTbinReader.buf, valuesGPS, true);
						if (isGPSData && isReceiverData) {
							migrateAddPoints(isVarioData, isGPSData, isGeneralData, isElectricData, isMotorDriverData, channelNumber, valuesVario, valuesGPS, valuesGAM, valuesEAM, valuesESC);
							isReceiverData = false;

							if (!isGPSdetected) {
								HoTTAdapter2.updateGpsTypeDependent((HoTTbinReader.buf[65] & 0xFF), device, HoTTlogReader2.recordSet, 0);
								isGPSdetected = true;					
							}
						}
						break;
					case HoTTAdapter.ANSWER_SENSOR_GENERAL_19200:
						if (!detectedSensors.contains(Sensor.GAM)) 
							detectedSensors.add(Sensor.GAM);
						isGeneralData = parseGAM(HoTTbinReader.buf, valuesGAM, HoTTlogReader2.recordSet, true);
						if (isGeneralData && isReceiverData) {
							migrateAddPoints(isVarioData, isGPSData, isGeneralData, isElectricData, isMotorDriverData, channelNumber, valuesVario, valuesGPS, valuesGAM, valuesEAM, valuesESC);
							isReceiverData = false;
						}
						break;
					case HoTTAdapter.ANSWER_SENSOR_ELECTRIC_19200:
						if (!detectedSensors.contains(Sensor.EAM)) 
							detectedSensors.add(Sensor.EAM);
						isElectricData = parseEAM(HoTTbinReader.buf, valuesEAM, HoTTlogReader2.recordSet, true);
						if (isElectricData && isReceiverData) {
							migrateAddPoints(isVarioData, isGPSData, isGeneralData, isElectricData, isMotorDriverData, channelNumber, valuesVario, valuesGPS, valuesGAM, valuesEAM, valuesESC);
							isReceiverData = false;
						}
						break;
					case HoTTAdapter.ANSWER_SENSOR_MOTOR_DRIVER_19200:
						if (!detectedSensors.contains(Sensor.ESC)) 
							detectedSensors.add(Sensor.ESC);
						isMotorDriverData = parseESC(HoTTbinReader.buf, valuesESC, HoTTlogReader2.recordSet);
						if (isMotorDriverData && isReceiverData) {
							migrateAddPoints(isVarioData, isGPSData, isGeneralData, isElectricData, isMotorDriverData, channelNumber, valuesVario, valuesGPS, valuesGAM, valuesEAM, valuesESC);
							isReceiverData = false;
							
							if (!isESCdetected) {
								HoTTAdapter2.updateEscTypeDependent((HoTTbinReader.buf[65] & 0xFF), device, HoTTlogReader2.recordSet);
								isESCdetected = true;								
							}
						}
						break;
					case 0x1F: //receiver sensitive data
					default:
						break;
					}

					if (HoTTlogReader2.isJustMigrated && countPackageLoss > 0) {
						HoTTbinReader.lostPackages.add(countPackageLoss * 10);
						countPackageLoss = 0;
					}

					if (isReceiverData) { //this will only be true if no other sensor is connected
						HoTTlogReader2.recordSet.addPoints(HoTTlogReader2.points, HoTTbinReader.timeStep_ms);
						isReceiverData = false;
					}
					else if (channelNumber == 4 && !HoTTlogReader2.isJustMigrated) { //this will only be true if no other sensor is connected and channel 4
						HoTTlogReader2.recordSet.addPoints(HoTTlogReader2.points, HoTTbinReader.timeStep_ms);
					}
					HoTTlogReader2.isJustMigrated = false;

					HoTTbinReader.timeStep_ms += logTimeStep;// add default time step from log record of 100 msec

					if (i % progressIndicator == 0) GDE.getUiNotification().setProgress((int) (i * 100 / numberDatablocks));
				}
					else { //skip empty block, but add time step
						if (log.isLoggable(Level.FINE)) log.log(Level.FINE, "-->> Found tx=rx=0 dBm");

						reverseChannelPackageLossCounter.add(0);
						HoTTlogReader2.points[0] = reverseChannelPackageLossCounter.getPercentage() * 1000;
						countPackageLoss+=1; // add up lost packages in telemetry data

						if (channelNumber == 4) {
							parseChannel(HoTTbinReader.buf, valuesChannel, numberLogChannels);
							//in 0=FreCh, 1=Tx, 2=Rx, 3=Ch 1, 4=Ch 2 .. 18=Ch 16 19=PowerOff 20=BattLow 21=Reset 22=Warning
							//out 87=Ch 1, 88=Ch 2, 89=Ch 3 .. 102=Ch 16, 103=PowerOff, 104=BatterieLow, 105=Reset, 106=reserve
							System.arraycopy(valuesChannel, 3, HoTTlogReader2.points, 87, 20); //copy channel data and events, warning
							HoTTlogReader2.recordSet.addPoints(HoTTlogReader2.points, HoTTbinReader.timeStep_ms);
						}
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
			Sensor altitudeClimbSensorSelection = pickerParameters.altitudeClimbSensorSelection == 0 ? null : Sensor.fromOrdinal(pickerParameters.altitudeClimbSensorSelection);
			if (pickerParameters.altitudeClimbSensorSelection == 0 || !detectedSensors.contains(Sensor.fromOrdinal(pickerParameters.altitudeClimbSensorSelection))) { //auto
				if (isElectricData && !isVarioData && !isGPSData && !isGeneralData) 
					altitudeClimbSensorSelection = Sensor.EAM;
				else if (isGeneralData && !isVarioData && !isGPSData)
					altitudeClimbSensorSelection = Sensor.GAM;
				else if (isGPSData && !isVarioData)
					altitudeClimbSensorSelection = Sensor.GPS;
				else if (isVarioData)
					altitudeClimbSensorSelection = Sensor.VARIO;
			}
			String packageLossPercentage = tmpRecordSet.getRecordDataSize(true) > 0 ? String.format("%.1f", (countPackageLoss / tmpRecordSet.getTime_ms(tmpRecordSet.getRecordDataSize(true) - 1) * 1000)) : "100";
			if (channelNumber == 4)
				HoTTbinReader.detectedSensors.add(Sensor.CHANNEL);
			tmpRecordSet.setRecordSetDescription(tmpRecordSet.getRecordSetDescription()
					+ Messages.getString(gde.device.graupner.hott.MessageIds.GDE_MSGI2404, new Object[] { countPackageLoss, packageLossPercentage, HoTTbinReader.lostPackages.getStatistics() })
					+ String.format(" - Sensor: %s", HoTTlogReader.detectedSensors.toString())
					+ (altitudeClimbSensorSelection != null && (detectedSensors.contains(Sensor.fromOrdinal(pickerParameters.altitudeClimbSensorSelection)) || detectedSensors.contains(altitudeClimbSensorSelection))
							? String.format(" - %s = %s", Messages.getString(gde.device.graupner.hott.MessageIds.GDE_MSGT2419), altitudeClimbSensorSelection)
									: ""));
			log.logp(Level.WARNING, HoTTbinReader.$CLASS_NAME, $METHOD_NAME, "skipped number receiver data due to package loss = " + countPackageLoss); //$NON-NLS-1$
			log.logp(Level.TIME, HoTTbinReader.$CLASS_NAME, $METHOD_NAME, "read time = " + StringHelper.getFormatedTime("mm:ss:SSS", (System.nanoTime() / 1000000 - startTime))); //$NON-NLS-1$ //$NON-NLS-2$

			if (menuToolBar != null) {
				GDE.getUiNotification().setProgress(99);
				device.makeInActiveDisplayable(HoTTlogReader2.recordSet);
				device.updateVisibilityStatus(HoTTlogReader2.recordSet, true);
				channel.applyTemplate(recordSetName, false);

				//write filename after import to record description
				HoTTlogReader2.recordSet.descriptionAppendFilename(file.getName());

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
		// 0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
		// 10=Altitude, 11=Climb 1, 12=Climb 3, 13=Climb 10 14=EventVario 15=misc Vario_1 16=misc Vario_2 17=misc Vario_3 18=misc Vario_4 19=misc Vario_5
		// 20=Latitude, 21=Longitude, 22=Velocity, 23=Distance, 24=Direction, 25=TripDistance 26=NumSatellites 27=GPS-Fix 28=EventGPS
		// 29=HomeDirection 30=Roll 31=Pitch 32=Yaw 33=GyroX 34=GyroY 35=GyroZ 36=Vibration 37=Version	
		// 38=Voltage G, 39=Current G, 40=Capacity G, 41=Power G, 42=Balance G, 43=CellVoltage G1, 44=CellVoltage G2 .... 48=CellVoltage G6, 49=Revolution G, 
		// 50=FuelLevel, 51=Voltage G1, 52=Voltage G2, 53=Temperature G1, 54=Temperature G2 55=Speed G, 56=LowestCellVoltage,
		// 57=LowestCellNumber, 58=Pressure, 59=Event G
		// 60=Voltage E, 61=Current E, 62=Capacity E, 63=Power E, 64=Balance E, 65=CellVoltage E1, 66=CellVoltage E2 .... 78=CellVoltage E14,
		// 79=Voltage E1, 80=Voltage E2, 81=Temperature E1, 82=Temperature E2 83=Revolution E 84=MotorTime 85=Speed 86=Event E
		// 87=VoltageM, 88=CurrentM, 89=CapacityM, 90=PowerM, 91=RevolutionM, 92=TemperatureM 1, 93=TemperatureM 2 94=Voltage_min, 95=Current_max,
		// 96=Revolution_max, 97=Temperature1_max, 98=Temperature2_max 99=Event M
		// 100=Speed 101=Speed_max 102=PWM 103=Throttle 104=VoltageBEC 105=VoltageBEC_min 106=CurrentBEC 107=TemperatureBEC 108=TemperatureCap 
		// 109=Timing(empty) 110=Temperature_aux 111=Gear 112=YGEGenExt 113=MotStatEscNr 114=misc ESC_15 115=VersionESC

		// 87=Ch 1, 88=Ch 2, 89=Ch 3 .. 102=Ch 16, 103=PowerOff, 104=BatterieLow, 105=Reset, 106=reserve
		// 107=VoltageM, 108=CurrentM, 109=CapacityM, 110=PowerM, 111=RevolutionM, 112=TemperatureM 1, 113=TemperatureM 2 114=Voltage_min, 115=Current_max,
		// 116=Revolution_max, 117=Temperature1_max, 118=Temperature2_max 119=Event M
		// 120=Speed 121=Speed_max 122=PWM 123=Throttle 124=VoltageBEC 125=VoltageBEC_min 125=CurrentBEC 127=TemperatureBEC 128=TemperatureCap 
		// 129=Timing(empty) 130=Temperature_aux 131=Gear 132=YGEGenExt 133=MotStatEscNr 134=misc ESC_15 135=VersionESC

		//in 0=RXSQ, 1=Voltage, 2=Current, 3=Capacity, 4=Power, 5=Balance, 6=CellVoltage 1, 7=CellVoltage 2 .... 19=CellVoltage 14,
		//in 20=Altitude, 21=Climb 1, 22=Climb 3, 23=Voltage 1, 24=Voltage 2, 25=Temperature 1, 26=Temperature 2 27=RPM 28=MotorTime 29=Speed 30=Event
		if (isElectricData) {
			//out 10=Altitude, 11=Climb 1, 12=Climb 3
			for (int j = 0; !isVarioData && !isGPSData && !isGeneralData && j < 3; j++) { //0=altitude 1=climb1 2=climb3
				HoTTlogReader2.points[j+10] = valuesEAM[j+20];
			}
      //out 60=Voltage E, 61=Current E, 62=Capacity E, 63=Power E, 64=Balance E, 65=CellVoltage E1, 66=CellVoltage E2 .... 78=CellVoltage E14,
      System.arraycopy(valuesEAM, 1, HoTTlogReader2.points, 60, 19);
      //out 79=Voltage E1, 80=Voltage E2, 81=Temperature E1, 82=Temperature E2 83=Revolution E 84=MotorTime 85=Speed 86=Event E
      System.arraycopy(valuesEAM, 23, HoTTlogReader2.points, 79, 8);
		}
		//in 0=RF_RXSQ, 1=Voltage, 2=Current, 3=Capacity, 4=Power, 5=Balance, 6=CellVoltage 1, 7=CellVoltage 2 .... 11=CellVoltage 6,
		//in 12=Revolution, 13=Altitude, 14=Climb, 15=Climb3, 16=FuelLevel, 17=Voltage 1, 18=Voltage 2, 19=Temperature 1, 20=Temperature 2
		//in 21=Speed, 22=LowestCellVoltage, 23=LowestCellNumber, 24=Pressure, 24=Event
		if (isGeneralData) {
			//out 10=Altitude, 11=Climb 1, 12=Climb 3
			for (int k = 0; !isVarioData && !isGPSData && k < 3; k++) {
				HoTTlogReader2.points[k+10] = valuesGAM[k+13];
			}
			//out 38=Voltage G, 39=Current G, 40=Capacity G, 41=Power G, 42=Balance G, 43=CellVoltage G1, 44=CellVoltage G2 .... 48=CellVoltage G6, 49=Revolution G, 
			for (int j = 0; j < 12; j++) {
				HoTTlogReader2.points[j+38] = valuesGAM[j+1];
			}
			//out 50=FuelLevel, 51=Voltage G1, 52=Voltage G2, 53=Temperature G1, 54=Temperature G2 55=Speed G, 56=LowestCellVoltage,
			for (int j = 0; j < 10; j++) {
				HoTTlogReader2.points[j+50] = valuesGAM[j+16];
			}
		}
		//in 0=RXSQ, 1=Latitude, 2=Longitude, 3=Altitude, 4=Climb 1, 5=Climb 3, 6=Velocity, 7=Distance, 8=Direction, 9=TripLength, 10=VoltageRx, 11=TemperatureRx 12=satellites 13=GPS-fix 14=EventGPS
		if (isGPSData) {
			//out 10=Altitude, 11=Climb 1, 12=Climb 3
			for (int j = 0; !isVarioData && j < 3; j++) { //0=altitude 1=climb1 2=climb3
				HoTTlogReader2.points[j+10] = valuesGPS[j+3];
			}
			//out 20=Latitude, 21=Longitude, 22=Velocity, 23=Distance, 24=Direction, 25=TripDistance 
			HoTTlogReader2.points[20] = valuesGPS[1];
			HoTTlogReader2.points[21] = valuesGPS[2];
			for (int k = 0; k < 4; k++) {
				HoTTlogReader2.points[k+22] = valuesGPS[k+6];
			}
			//out 26=NumSatellites 27=GPS-Fix 28=EventGPS
			for (int k = 0; k < 3; k++) {
				HoTTlogReader2.points[k+26] = valuesGPS[k+12];
			}
			//out 29=HomeDirection 30=Roll 31=Pitch 32=Yaw 33=GyroX 34=GyroY 35=GyroZ 36=Vibration 37=Version	
			for (int k = 0; k < 9; k++) {
				HoTTlogReader2.points[k+29] = valuesGPS[k+15];
			}
		}
		//in 0=RXSQ, 1=Altitude, 2=Climb 1, 3=Climb 3, 4=Climb 10, 5=VoltageRx, 6=TemperatureRx 7=Event 
		//in 8=accX 9=accY 10=accZ 11=reserved 12=version	if (isVarioData) {
		if (isVarioData) {
			//out 10=Altitude, 11=Climb 1, 12=Climb 3, 13=Climb 10 14=EventVario
			for (int j = 0; j < 4; j++) {
				HoTTlogReader2.points[j+10] = valuesVario[j+1];
			}
			HoTTlogReader2.points[14] = valuesVario[7];
			
			//out 15=misc Vario_1 16=misc Vario_2 17=misc Vario_3 18=misc Vario_4 19=misc Vario_5
			for (int j = 0; j < 5; j++) {
				HoTTlogReader2.points[j+15] = valuesVario[j+8];
			}
		}
		//in 0=RF_RXSQ, 1=Voltage, 2=Current, 3=Capacity, 4=Power, 5=Revolution, 6=Temperature1, 7=Temperature2
		//in 8=Voltage_min, 9=Current_max, 10=Revolution_max, 11=Temperature1_max, 12=Temperature2_max 13=Event
		//in 14=Speed 15=Speed_max 16=PWM 17=Throttle 18=VoltageBEC 19=VoltageBEC_min 20=CurrentBEC 21=TemperatureBEC 22=TemperatureCap 
		//in 23=Timing(empty) 24=Temperature_aux 25=Gear 26=YGEGenExt 27=MotStatEscNr 28=misc ESC_15 29=VersionESC
		if (isMotorDriverData) {
			if (channelNumber == 4)
				//out 107=VoltageM, 108=CurrentM, 109=CapacityM, 110=PowerM, 111=RevolutionM, 112=TemperatureM 1, 113=TemperatureM 2 114=Voltage_min, 115=Current_max, 116=Revolution_max, 117=Temperature1_max, 118=Temperature2_max 119=Event M
				//out 120=Speed 121=Speed_max 122=PWM 123=Throttle 124=VoltageBEC 125=VoltageBEC_min 125=CurrentBEC 127=TemperatureBEC 128=TemperatureCap 
				//out 129=Timing(empty) 130=Temperature_aux 131=Gear 132=YGEGenExt 133=MotStatEscNr 134=misc ESC_15 135=VersionESC
				for (int j = 0; j < 29; j++) {
					HoTTlogReader2.points[j+107] = valuesESC[j+1];
				}
			else
				//out 87=VoltageM, 88=CurrentM, 89=CapacityM, 90=PowerM, 91=RevolutionM, 92=TemperatureM 1, 93=TemperatureM 2 94=Voltage_min, 95=Current_max, 96=Revolution_max, 97=Temperature1_max, 98=Temperature2_max 99=Event M
				//out 100=Speed 101=Speed_max 102=PWM 103=Throttle 104=VoltageBEC 105=VoltageBEC_min 106=CurrentBEC 107=TemperatureBEC 108=TemperatureCap 
				//out 109=Timing(empty) 110=Temperature_aux 111=Gear 112=YGEGenExt 113=MotStatEscNr 114=misc ESC_15 115=VersionESC
				for (int j = 0; j < 29; j++) {
					HoTTlogReader2.points[j+87] = valuesESC[j+1];
				}
		}
		
		//add altitude and climb values from selected sensor
		switch (Sensor.VALUES[HoTTbinReader.pickerParameters.altitudeClimbSensorSelection]) {
		case VARIO:
			//8=Altitude, 9=Climb 1, 10=Climb 3, 11=Climb 10
			if (isVarioData)
				for (int j = 0; j < 4; j++) {
					HoTTlogReader2.points[j+10] = valuesVario[j+1];
				}
			break;
		case GPS:
			//8=Altitude, 9=Climb 1, 10=Climb 3
			if (isGPSData)
				for (int j = 0; j < 3; j++) { //0=altitude 1=climb1 2=climb3
					HoTTlogReader2.points[j+10] = valuesGPS[j+3];
				}
			HoTTlogReader2.points[11] = 0;
			break;
		case GAM:
			//8=Altitude, 9=Climb 1, 10=Climb 3
			if (isGeneralData)
				for (int j = 0; j < 3; j++) {
					HoTTlogReader2.points[j+10] = valuesGAM[j+13];
				}
			HoTTlogReader2.points[11] = 0;
			break;
		case EAM:
			//8=Altitude, 9=Climb 1, 10=Climb 3
			if (isElectricData)
				for (int j = 0; j < 3; j++) { //0=altitude 1=climb1 2=climb3
					HoTTlogReader2.points[j+10] = valuesEAM[j+20];
				}
			HoTTlogReader2.points[11] = 0;
			break;
		default:
			break;
		}
		
		HoTTlogReader2.recordSet.addPoints(HoTTlogReader2.points, HoTTbinReader.timeStep_ms);
		HoTTlogReader2.isJustMigrated = true;
	}
}
