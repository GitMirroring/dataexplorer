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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Vector;
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
public class HoTTbinReaderD extends HoTTbinReader {
	final static Logger							logger						= Logger.getLogger(HoTTbinReaderD.class.getName());

	static int[]										points;
	static RecordSet								recordSet;
	static boolean									isJustMigrated		= false;
	static boolean									isGpsStartTimeSet	= false;
	static int											gpsStartTime			= 0;
	static int											lastEscFetTemp		= 0;

	protected static StringBuilder	sensorSignature;
	protected static final boolean	isSensorType[]		= { false, false, false, false, false, false };

	/**
	 * read complete file data and display the first found record set
	 * @param filePath
	 * @throws Exception
	 */
	public static synchronized void read(String filePath, PickerParameters newPickerParameters) throws Exception {
		HashMap<String, String> header = HoTTbinReader.getFileInfo(new File(filePath), newPickerParameters);
		EnumSet<Sensor> sensors = Sensor.getSetFromDetected(header.get(HoTTAdapter.DETECTED_SENSOR));
		HoTTbinReaderD.sensorSignature = Sensor.getSetAsSignature(sensors);
		for (int i = 1; i < isSensorType.length; i++) { // exclude receiver
			HoTTbinReaderD.isSensorType[i] = sensors.contains(Sensor.fromOrdinal(i));
		}

		if (sensors.size() <= 2) {
			HoTTbinReader.isReceiverOnly = sensors.size() == 1;
			readSingle(new File(header.get(HoTTAdapter.FILE_PATH)), header);
		}
		else
			readMultiple(new File(header.get(HoTTAdapter.FILE_PATH)), header);
	}

	/**
	* read log data according to version 0
	* @param file
	* @param data_in
	* @throws IOException
	* @throws DataInconsitsentException
	*/
	static void readSingle(File file, HashMap<String, String> header) throws IOException, DataInconsitsentException {
		final String $METHOD_NAME = "readSingle";
		long startTime = System.nanoTime() / 1000000;
		FileInputStream file_input = new FileInputStream(file);
		DataInputStream data_in = new DataInputStream(file_input);
		long fileSize = file.length();
		HoTTAdapterD device = (HoTTAdapterD) HoTTbinReader.application.getActiveDevice();
		int recordSetNumber = HoTTbinReader.channels.get(1).maxSize() + 1;
		String recordSetName = GDE.STRING_EMPTY;
		String recordSetNameExtend = getRecordSetExtend(file);
		Channel channel = null;
		int channelNumber = HoTTbinReaderD.pickerParameters.analyzer.getActiveChannel().getNumber();
		device.getMeasurementFactor(channelNumber, 12);
		boolean isReceiverData = false;
		boolean isSensorData = false;
		HoTTbinReaderD.isGpsStartTimeSet = false;
		HoTTbinReaderD.recordSet = null;
		//0=RF_RXSQ, 1=RXSQ, 2=Strength, 3=PackageLoss, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx
		//8=Altitude, 9=Climb 1, 10=Climb 3, 11=Climb 10
		//12=Latitude, 13=Longitude, 14=Velocity, 15=Distance, 16=Direction, 17=TripDistance
		//18=GPS.Satelites, 19=GPS.Fix, 20=HomeDirection, 21=NorthVelocity, 22=SpeedAccuracy, 23=GPS.Time, 24=EastVelocity, 25=HorizontalAccuracy, 26=Altitude, 27=GPS.Fix2, 28=Version
		//18=Satellites 19=Fix 20=HomeDirection 21=Roll 22=Pitch 23=Yaw 24=GyroX 25=GyroY 26=GyroZ 27=GPSAltitude 28=Vibration 29=Version
		//29=VoltageG, 30=CurrentG, 31=CapacityG, 32=PowerG, 33=BalanceG, 34=CellVoltageG 1, 35=CellVoltageG 2 .... 39=CellVoltageG 6, 40=Revolution, 41=FuelLevel, 42=VoltageG 1, 43=VoltageG 2, 44=TemperatureG 1, 45=TemperatureG 2
		//46=VoltageE, 47=CurrentE, 48=CapacityE, 49=PowerE, 50=BalanceE, 51=CellVoltageE 1, 52=CellVoltageE 2 .... 64=CellVoltageE 14, 65=Revolution, 66=VoltageE 1, 67=VoltageE 2, 68=TemperatureE 1, 69=TemperatureE 2
		//70=VoltageM, 71=CurrentM, 72=CapacityM, 73=PowerM, 74=RevolutionM, 75=TemperatureM
		//76=Ch 1, 77=Ch 2 , 78=Ch 3 .. 91=Ch 16
		//92=PowerOff, 93=BattLow, 94=Reset, 95=reserved
		//96=Test 00 97=Test 01.. 108=Test 12
		//109=SmoothedRx_dbm, 110=DiffRx_dbm, 111=LapsRx_dbm, 112=DiffDistance, 113=LapsDistance
		//114=VoltageRx_min 115=Speed G, 116=CellVoltage_min G 117=CellNumber_min G 118=Pressure 119=MotorRuntime E 120=Speed E
		//121=Temperature M2 122=Voltage Mmin 123=Current Mmax 124=RPM Mmax 125=Temperatire M1max 126=Temperature M2max
		//127=EventRx 128=EventVario 129=EventGPS 130=EventGAM 131=EventEAM 132=EventESC
		HoTTbinReaderD.points = new int[device.getNumberOfMeasurements(channelNumber)];
		HoTTbinReaderD.points[2] = 0;
		HoTTbinReader.pointsGAM = HoTTbinReader.pointsEAM = HoTTbinReader.pointsESC = HoTTbinReader.pointsVario = HoTTbinReader.pointsGPS = HoTTbinReaderD.points;
		HoTTbinReader.timeStep_ms = 0;
		HoTTbinReader.dataBlockSize = 64;
		HoTTbinReader.buf = new byte[HoTTbinReader.dataBlockSize];
		HoTTbinReader.buf0 = new byte[30];
		HoTTbinReader.buf1 = new byte[30];
		HoTTbinReader.buf2 = new byte[30];
		HoTTbinReader.buf3 = new byte[30];
		HoTTbinReader.buf4 = new byte[30];
		BufCopier bufCopier = new BufCopier(buf, buf0, buf1, buf2, buf3, buf4);
		pickerParameters.reverseChannelPackageLossCounter.clear();
		HoTTbinReader.lostPackages.clear();
		HoTTbinReader.countLostPackages = 0;
		HoTTbinReader.isTextModusSignaled = false;
		HoTTbinReader.oldProtocolCount = 0;
		HoTTbinReader.blockSequenceCheck = new Vector<Byte>();
		int countPackageLoss = 0;
		boolean isSdLogFormat = Boolean.parseBoolean(header.get(HoTTAdapter.SD_FORMAT));
		long numberDatablocks = isSdLogFormat ? fileSize - HoTTbinReaderX.headerSize - HoTTbinReaderX.footerSize : fileSize / HoTTbinReader.dataBlockSize;
		long startTimeStamp_ms = HoTTbinReader.getStartTimeStamp(file.getName(), file.lastModified(), numberDatablocks);
		String date = new SimpleDateFormat("yyyy-MM-dd").format(startTimeStamp_ms); //$NON-NLS-1$
		String dateTime = new SimpleDateFormat("yyyy-MM-dd, HH:mm:ss").format(startTimeStamp_ms); //$NON-NLS-1$
		RecordSet tmpRecordSet;
		MenuToolBar menuToolBar = HoTTbinReader.application.getMenuToolBar();
		int progressIndicator = (int) (numberDatablocks / 30);
		GDE.getUiNotification().setProgress(0);
		if (isSdLogFormat) data_in.skip(HoTTbinReaderX.headerSize);

		try {
			//check if recordSet initialized, transmitter and receiver data always present, but not in the same data rate and signals
			channel = HoTTbinReader.channels.get(channelNumber);
			channel.setFileDescription(HoTTbinReader.application.isObjectoriented() ? date + GDE.STRING_BLANK + HoTTbinReader.application.getObjectKey() : date);
			recordSetName = recordSetNumber + device.getRecordSetStemNameReplacement() + recordSetNameExtend;
			HoTTbinReaderD.recordSet = RecordSet.createRecordSet(recordSetName, device, channelNumber, true, true, true);
			channel.put(recordSetName, HoTTbinReaderD.recordSet);
			tmpRecordSet = channel.get(recordSetName);
			tmpRecordSet.setRecordSetDescription(device.getName() + GDE.STRING_MESSAGE_CONCAT + Messages.getString(MessageIds.GDE_MSGT0129) + dateTime);
			tmpRecordSet.setStartTimeStamp(startTimeStamp_ms);
			//recordSet initialized and ready to add data

			//read all the data blocks from the file and parse
			for (int i = 0; i < numberDatablocks; i++) {
				data_in.read(HoTTbinReader.buf);
				if (HoTTbinReaderD.logger.isLoggable(Level.FINE) && i % 10 == 0) {
					HoTTbinReaderD.logger.logp(Level.FINE, HoTTbinReader.$CLASS_NAME, $METHOD_NAME, StringHelper.fourDigitsRunningNumber(HoTTbinReader.buf.length));
					HoTTbinReaderD.logger.logp(Level.FINE, HoTTbinReader.$CLASS_NAME, $METHOD_NAME, StringHelper.byte2Hex4CharString(HoTTbinReader.buf, HoTTbinReader.buf.length));
				}

				if (!pickerParameters.isFilterTextModus || (HoTTbinReader.buf[6] & 0x01) == 0) { //switch into text modus
					if (HoTTbinReader.buf[33] >= 0 && HoTTbinReader.buf[33] <= 4 && HoTTbinReader.buf[3] != 0 && HoTTbinReader.buf[4] != 0) { //buf 3, 4, tx,rx
						if (HoTTbinReaderD.logger.isLoggable(Level.FINER))
							HoTTbinReaderD.logger.log(Level.FINER, String.format("Sensor %x Blocknummer : %d", HoTTbinReader.buf[7], HoTTbinReader.buf[33]));

						pickerParameters.reverseChannelPackageLossCounter.add(1);
						HoTTbinReaderD.points[0] = pickerParameters.reverseChannelPackageLossCounter.getPercentage() * 1000;

						if (HoTTbinReaderD.logger.isLoggable(Level.FINER))
							HoTTbinReaderD.logger.logp(Level.FINER, HoTTbinReader.$CLASS_NAME, $METHOD_NAME, StringHelper.byte2Hex2CharString(new byte[] { HoTTbinReader.buf[7] }, 1)
									+ GDE.STRING_MESSAGE_CONCAT + StringHelper.printBinary(HoTTbinReader.buf[7], false));

						//fill receiver data
						if (HoTTbinReader.buf[33] == 0 && (HoTTbinReader.buf[38] & 0x80) != 128 && DataParser.parse2Short(HoTTbinReader.buf, 40) >= 0) {
							parseReceiver(HoTTbinReader.buf);
							isReceiverData = true;
						}
						parseChannel(HoTTbinReader.buf); //Channels

						//fill data block 0 receiver voltage an temperature
						if (buf[33] == 0) {
							bufCopier.copyToBuffer();
						}

						//create and fill sensor specific data record sets
						switch ((byte) (HoTTbinReader.buf[7] & 0xFF)) {
						case HoTTAdapter.SENSOR_TYPE_VARIO_115200:
						case HoTTAdapter.SENSOR_TYPE_VARIO_19200:
							if (HoTTbinReaderD.isSensorType[HoTTAdapter.Sensor.VARIO.ordinal()]) {
								bufCopier.copyToVarioBuffer();
								if (bufCopier.is4BuffersFull()) {
									parseVario(HoTTbinReader.buf0, HoTTbinReader.buf1, HoTTbinReader.buf2, HoTTbinReader.buf3, HoTTbinReader.buf4);
									bufCopier.clearBuffers();
									isSensorData = true;
								}
							}
							break;

						case HoTTAdapter.SENSOR_TYPE_GPS_115200:
						case HoTTAdapter.SENSOR_TYPE_GPS_19200:
							if (HoTTbinReaderD.isSensorType[HoTTAdapter.Sensor.GPS.ordinal()]) {
								bufCopier.copyToFreeBuffer();
								if (bufCopier.is4BuffersFull()) {
									parseGPS(HoTTbinReader.buf0, HoTTbinReader.buf1, HoTTbinReader.buf2, HoTTbinReader.buf3, HoTTbinReader.buf4);
									bufCopier.clearBuffers();
									isSensorData = true;
								}
							}
							break;

						case HoTTAdapter.SENSOR_TYPE_GENERAL_115200:
						case HoTTAdapter.SENSOR_TYPE_GENERAL_19200:
							if (HoTTbinReaderD.isSensorType[HoTTAdapter.Sensor.GAM.ordinal()]) {
								bufCopier.copyToFreeBuffer();
								if (bufCopier.is4BuffersFull()) {
									parseGAM(HoTTbinReader.buf0, HoTTbinReader.buf1, HoTTbinReader.buf2, HoTTbinReader.buf3, HoTTbinReader.buf4);
									bufCopier.clearBuffers();
									isSensorData = true;
								}
							}
							break;

						case HoTTAdapter.SENSOR_TYPE_ELECTRIC_115200:
						case HoTTAdapter.SENSOR_TYPE_ELECTRIC_19200:
							if (HoTTbinReaderD.isSensorType[HoTTAdapter.Sensor.EAM.ordinal()]) {
								bufCopier.copyToFreeBuffer();
								if (bufCopier.is4BuffersFull()) {
									parseEAM(HoTTbinReader.buf0, HoTTbinReader.buf1, HoTTbinReader.buf2, HoTTbinReader.buf3, HoTTbinReader.buf4);
									bufCopier.clearBuffers();
									isSensorData = true;
								}
							}
							break;

						case HoTTAdapter.SENSOR_TYPE_SPEED_CONTROL_115200:
						case HoTTAdapter.SENSOR_TYPE_SPEED_CONTROL_19200:
							if (HoTTbinReaderD.isSensorType[HoTTAdapter.Sensor.ESC.ordinal()]) {
								bufCopier.copyToFreeBuffer();
								if (bufCopier.is4BuffersFull()) {
									parseESC(HoTTbinReader.buf0, HoTTbinReader.buf1, HoTTbinReader.buf2, HoTTbinReader.buf3, HoTTbinReader.buf4, channelNumber);
									bufCopier.clearBuffers();
									isSensorData = true;
								}
							}
							break;
						}

						if (isSensorData && HoTTbinReader.countLostPackages > 0) {
							HoTTbinReader.lostPackages.add(HoTTbinReader.countLostPackages);
							HoTTbinReader.countLostPackages = 0;
						}

						if (isSensorData || isReceiverData) {
							HoTTbinReaderD.recordSet.addPoints(HoTTbinReaderD.points, HoTTbinReader.timeStep_ms);
							isSensorData = isReceiverData = false;
						}
						HoTTbinReaderD.recordSet.addPoints(HoTTbinReaderD.points, HoTTbinReader.timeStep_ms);

						if (HoTTbinReader.blockSequenceCheck.size() > 1) {
							if (HoTTbinReader.blockSequenceCheck.get(1) != 0 && HoTTbinReader.blockSequenceCheck.get(0) - HoTTbinReader.blockSequenceCheck.get(1) > 1
									&& HoTTbinReader.blockSequenceCheck.get(0) - HoTTbinReader.blockSequenceCheck.get(1) < 4 && pickerParameters.reverseChannelPackageLossCounter.getPercentage() < 15)
								++HoTTbinReader.oldProtocolCount;
							HoTTbinReader.blockSequenceCheck.remove(0);
						}
						HoTTbinReader.blockSequenceCheck.add(HoTTbinReader.buf[33]);

						HoTTbinReader.timeStep_ms += 10; // add default time step from device of 10 msec

						if (i % progressIndicator == 0) GDE.getUiNotification().setProgress((int) (i * 100 / numberDatablocks));
					}
					else { //skip empty block, but add time step
						if (HoTTbinReaderD.logger.isLoggable(Level.FINE)) HoTTbinReaderD.logger.log(Level.FINE, "-->> Found tx=rx=0 dBm");

						pickerParameters.reverseChannelPackageLossCounter.add(0);
						HoTTbinReaderD.points[0] = pickerParameters.reverseChannelPackageLossCounter.getPercentage() * 1000;

						++countPackageLoss; // add up lost packages in telemetry data
						++HoTTbinReader.countLostPackages;
						//HoTTbinReaderD.points[0] = (int) (countPackageLoss*100.0 / ((HoTTbinReaderD.timeStep_ms+10) / 10.0)*1000.0);

						parseChannel(HoTTbinReader.buf); //Channels
						HoTTbinReaderD.recordSet.addPoints(HoTTbinReaderD.points, HoTTbinReader.timeStep_ms);
						HoTTbinReader.timeStep_ms += 10;
						//reset buffer to avoid mixing data >> 20 Jul 14, not any longer required due to protocol change requesting next sensor data block
						//HoTTbinReader.buf1 = HoTTbinReader.buf2 = HoTTbinReader.buf3 = HoTTbinReader.buf4 = null;
					}
				}
				else if (!HoTTbinReader.isTextModusSignaled) {
					HoTTbinReader.isTextModusSignaled = true;
					HoTTbinReader.application.openMessageDialogAsync(Messages.getString(gde.device.graupner.hott.MessageIds.GDE_MSGW2404));
				}
			}
			if (HoTTbinReader.oldProtocolCount > 2) {
				HoTTbinReader.application.openMessageDialogAsync(Messages.getString(gde.device.graupner.hott.MessageIds.GDE_MSGW2405, new Object[] { HoTTbinReader.oldProtocolCount }));
			}
			String packageLossPercentage = tmpRecordSet.getRecordDataSize(true) > 0 ? String.format("%.1f", (countPackageLoss / tmpRecordSet.getTime_ms(tmpRecordSet.getRecordDataSize(true) - 1) * 1000))
					: "100";
			tmpRecordSet.setRecordSetDescription(tmpRecordSet.getRecordSetDescription()
					+ Messages.getString(gde.device.graupner.hott.MessageIds.GDE_MSGI2404, new Object[] { countPackageLoss, packageLossPercentage, HoTTbinReader.lostPackages.getStatistics() })
					+ HoTTbinReaderD.sensorSignature);
			HoTTbinReaderD.logger.logp(Level.WARNING, HoTTbinReader.$CLASS_NAME, $METHOD_NAME, "skipped number receiver data due to package loss = " + countPackageLoss); //$NON-NLS-1$
			HoTTbinReaderD.logger.logp(Level.TIME, HoTTbinReader.$CLASS_NAME, $METHOD_NAME, "read time = " + StringHelper.getFormatedTime("mm:ss:SSS", (System.nanoTime() / 1000000 - startTime))); //$NON-NLS-1$ //$NON-NLS-2$

			if (menuToolBar != null) {
				GDE.getUiNotification().setProgress(99);
				device.makeInActiveDisplayable(HoTTbinReaderD.recordSet);
				device.updateVisibilityStatus(HoTTbinReaderD.recordSet, true);
				channel.applyTemplate(recordSetName, false);

				//write filename after import to record description
				HoTTbinReaderD.recordSet.descriptionAppendFilename(file.getName());

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
	* read log data according to version 0
	* @param file
	* @param data_in
	* @throws IOException
	* @throws DataInconsitsentException
	*/
	static void readMultiple(File file, HashMap<String, String> header) throws IOException, DataInconsitsentException {
		final String $METHOD_NAME = "readMultiple";
		long startTime = System.nanoTime() / 1000000;
		FileInputStream file_input = new FileInputStream(file);
		DataInputStream data_in = new DataInputStream(file_input);
		long fileSize = file.length();
		HoTTAdapterD device = (HoTTAdapterD) HoTTbinReader.application.getActiveDevice();
		int recordSetNumber = HoTTbinReader.channels.get(1).maxSize() + 1;
		String recordSetName = GDE.STRING_EMPTY;
		String recordSetNameExtend = getRecordSetExtend(file);
		Channel channel = null;
		int channelNumber = HoTTbinReaderD.pickerParameters.analyzer.getActiveChannel().getNumber();;
		boolean isReceiverData = false;
		boolean isVarioData = false;
		boolean isGPSData = false;
		boolean isGeneralData = false;
		boolean isElectricData = false;
		boolean isMotorDriverData = false;
		boolean isInitialSwitched = false;
		HoTTbinReaderD.isGpsStartTimeSet = false;
		HoTTbinReaderD.recordSet = null;
		HoTTbinReaderD.isJustMigrated = false;
		//0=RF_RXSQ, 1=RXSQ, 2=Strength, 3=PackageLoss, 4=Tx_dbm, 5=Rx_dbm, 6=VoltageRx, 7=TemperatureRx
		//8=Altitude, 9=Climb 1, 10=Climb 3, 11=Climb 10
		//12=Latitude, 13=Longitude, 14=Velocity, 15=Distance, 16=Direction, 17=TripDistance
		//18=GPS.Satelites, 19=GPS.Fix, 20=HomeDirection, 21=NorthVelocity, 22=SpeedAccuracy, 23=GPS.Time, 24=EastVelocity, 25=HorizontalAccuracy, 26=Altitude, 27=GPS.Fix2, 28=Version
		//29=VoltageG, 30=CurrentG, 31=CapacityG, 32=PowerG, 33=BalanceG, 34=CellVoltageG 1, 35=CellVoltageG 2 .... 39=CellVoltageG 6, 40=Revolution, 41=FuelLevel, 42=VoltageG 1, 43=VoltageG 2, 44=TemperatureG 1, 45=TemperatureG 2
		//46=VoltageE, 47=CurrentE, 48=CapacityE, 49=PowerE, 50=BalanceE, 51=CellVoltageE 1, 52=CellVoltageE 2 .... 64=CellVoltageE 14, 65=Revolution, 66=VoltageE 1, 67=VoltageE 2, 68=TemperatureE 1, 69=TemperatureE 2
		//70=VoltageM, 71=CurrentM, 72=CapacityM, 73=PowerM, 74=RevolutionM, 75=TemperatureM
		//76=Ch 1, 77=Ch 2 , 78=Ch 3 .. 91=Ch 16
		//92=PowerOff, 93=BattLow, 94=Reset, 95=reserved
		//96=Test 00 97=Test 01.. 108=Test 12
		//109=SmoothedRx_dbm, 110=DiffRx_dbm, 111=LapsRx_dbm, 112=DiffDistance, 113=LapsDistance
		//114=VoltageRx_min 115=Speed G, 116=CellVoltage_min G 117=CellNumber_min G 118=Pressure 119=MotorRuntime E 120=Speed E
		//121=Temperature M2 122=Voltage Mmin 123=Current Mmax 124=RPM Mmax 125=Temperatire M1max 126=Temperature M2max
		//127=EventRx 128=EventVario 129=EventGPS 130=EventGAM 131=EventEAM 132=EventESC
		HoTTbinReaderD.points = new int[device.getNumberOfMeasurements(channelNumber)];
		HoTTbinReader.pointsGAM = new int[HoTTbinReaderD.points.length];
		HoTTbinReader.pointsEAM = new int[HoTTbinReaderD.points.length];
		HoTTbinReader.pointsESC = new int[HoTTbinReaderD.points.length];
		HoTTbinReader.pointsVario = new int[HoTTbinReaderD.points.length];
		HoTTbinReader.pointsVario[2] = 100000;
		HoTTbinReader.pointsGPS = new int[HoTTbinReaderD.points.length];
		HoTTbinReader.pointsChannel = new int[HoTTbinReaderD.points.length];
		HoTTbinReader.timeStep_ms = 0;
		HoTTbinReader.dataBlockSize = 64;
		HoTTbinReader.buf = new byte[HoTTbinReader.dataBlockSize];
		HoTTbinReader.buf0 = new byte[30];
		HoTTbinReader.buf1 = new byte[30];
		HoTTbinReader.buf2 = new byte[30];
		HoTTbinReader.buf3 = new byte[30];
		HoTTbinReader.buf4 = new byte[30];
		BufCopier bufCopier = new BufCopier(buf, buf0, buf1, buf2, buf3, buf4);
		byte actualSensor = -1, lastSensor = -1;
		int logCountVario = 0, logCountGPS = 0, logCountGAM = 0, logCountEAM = 0, logCountESC = 0;
		boolean isBufferZero = false;
		pickerParameters.reverseChannelPackageLossCounter.clear();
		HoTTbinReader.lostPackages.clear();
		HoTTbinReader.countLostPackages = 0;
		HoTTbinReader.isTextModusSignaled = false;
		HoTTbinReader.oldProtocolCount = 0;
		HoTTbinReader.blockSequenceCheck = new Vector<Byte>();
		int countPackageLoss = 0;
		boolean isSdLogFormat = Boolean.parseBoolean(header.get(HoTTAdapter.SD_FORMAT));
		long numberDatablocks = isSdLogFormat ? fileSize - HoTTbinReaderX.headerSize - HoTTbinReaderX.footerSize : fileSize / HoTTbinReader.dataBlockSize;
		long startTimeStamp_ms = HoTTbinReader.getStartTimeStamp(file.getName(), file.lastModified(), numberDatablocks);
		String date = new SimpleDateFormat("yyyy-MM-dd").format(startTimeStamp_ms); //$NON-NLS-1$
		String dateTime = new SimpleDateFormat("yyyy-MM-dd, HH:mm:ss").format(startTimeStamp_ms); //$NON-NLS-1$
		RecordSet tmpRecordSet;
		MenuToolBar menuToolBar = HoTTbinReader.application.getMenuToolBar();
		int progressIndicator = (int) (numberDatablocks / 30);
		GDE.getUiNotification().setProgress(0);
		if (isSdLogFormat) data_in.skip(HoTTbinReaderX.headerSize);

		try {
			//receiver data are always contained
			channel = HoTTbinReader.channels.get(channelNumber);
			channel.setFileDescription(HoTTbinReader.application.isObjectoriented() ? date + GDE.STRING_BLANK + HoTTbinReader.application.getObjectKey() : date);
			recordSetName = recordSetNumber + device.getRecordSetStemNameReplacement() + recordSetNameExtend;
			HoTTbinReaderD.recordSet = RecordSet.createRecordSet(recordSetName, device, channelNumber, true, true, true);
			channel.put(recordSetName, HoTTbinReaderD.recordSet);
			tmpRecordSet = channel.get(recordSetName);
			tmpRecordSet.setRecordSetDescription(device.getName() + GDE.STRING_MESSAGE_CONCAT + Messages.getString(MessageIds.GDE_MSGT0129) + dateTime);
			tmpRecordSet.setStartTimeStamp(startTimeStamp_ms);
			//recordSet initialized and ready to add data

			//read all the data blocks from the file and parse
			for (int i = 0; i < numberDatablocks; i++) {
				data_in.read(HoTTbinReader.buf);
				if (HoTTbinReaderD.logger.isLoggable(Level.FINEST) && i % 10 == 0) {
					HoTTbinReaderD.logger.logp(Level.FINEST, HoTTbinReader.$CLASS_NAME, $METHOD_NAME, StringHelper.fourDigitsRunningNumber(HoTTbinReader.buf.length));
					HoTTbinReaderD.logger.logp(Level.FINEST, HoTTbinReader.$CLASS_NAME, $METHOD_NAME, StringHelper.byte2Hex4CharString(HoTTbinReader.buf, HoTTbinReader.buf.length));
				}

				if (!pickerParameters.isFilterTextModus || (HoTTbinReader.buf[6] & 0x01) == 0) { //switch into text modus
					if (HoTTbinReader.buf[33] >= 0 && HoTTbinReader.buf[33] <= 4 && HoTTbinReader.buf[3] != 0 && HoTTbinReader.buf[4] != 0) { //buf 3, 4, tx,rx
						if (HoTTbinReaderD.logger.isLoggable(Level.FINE))
							HoTTbinReaderD.logger.log(Level.FINE, String.format("Sensor %x Blocknummer : %d", HoTTbinReader.buf[7], HoTTbinReader.buf[33]));

						pickerParameters.reverseChannelPackageLossCounter.add(1);
						HoTTbinReaderD.points[0] = pickerParameters.reverseChannelPackageLossCounter.getPercentage() * 1000;
						//create and fill sensor specific data record sets
						if (HoTTbinReaderD.logger.isLoggable(Level.FINEST))
							HoTTbinReaderD.logger.logp(Level.FINEST, HoTTbinReader.$CLASS_NAME, $METHOD_NAME, StringHelper.byte2Hex2CharString(new byte[] { HoTTbinReader.buf[7] }, 1)
									+ GDE.STRING_MESSAGE_CONCAT + StringHelper.printBinary(HoTTbinReader.buf[7], false));

						//fill receiver data
						if (HoTTbinReader.buf[33] == 0 && (HoTTbinReader.buf[38] & 0x80) != 128 && DataParser.parse2Short(HoTTbinReader.buf, 40) >= 0) {
							parseReceiver(HoTTbinReader.buf);
							isReceiverData = true;
						}
						parseChannel(HoTTbinReader.buf);

						if (actualSensor == -1)
							lastSensor = actualSensor = (byte) (HoTTbinReader.buf[7] & 0xFF);
						else
							actualSensor = (byte) (HoTTbinReader.buf[7] & 0xFF);

						//											switch (actualSensor) {
						//											case HoTTAdapter.SENSOR_TYPE_VARIO_115200:
						//											case HoTTAdapter.SENSOR_TYPE_VARIO_19200:
						//												System.out.print("VARIO, ");
						//												break;
						//											case HoTTAdapter.SENSOR_TYPE_GPS_115200:
						//											case HoTTAdapter.SENSOR_TYPE_GPS_19200:
						//												System.out.print("GPS, ");
						//												break;
						//											case HoTTAdapter.SENSOR_TYPE_GENERAL_115200:
						//											case HoTTAdapter.SENSOR_TYPE_GENERAL_19200:
						//												System.out.print("GAM, ");
						//												break;
						//											case HoTTAdapter.SENSOR_TYPE_ELECTRIC_115200:
						//											case HoTTAdapter.SENSOR_TYPE_ELECTRIC_19200:
						//												System.out.print("EAM, ");
						//												break;
						//											case HoTTAdapter.SENSOR_TYPE_SPEED_CONTROL_115200:
						//											case HoTTAdapter.SENSOR_TYPE_SPEED_CONTROL_19200:
						//												System.out.print("ESC, ");
						//												break;
						//											}

						if (isBufferZero) { // write data just after sensor switch
							if (logCountVario >= 5 || logCountGPS >= 5 || logCountGAM >= 5 || logCountEAM >= 5 || logCountESC >= 5) {
								switch (lastSensor) {
								case HoTTAdapter.SENSOR_TYPE_VARIO_115200:
								case HoTTAdapter.SENSOR_TYPE_VARIO_19200:
									if (HoTTbinReaderD.isSensorType[HoTTAdapter.Sensor.VARIO.ordinal()]) {
										if (isVarioData && isReceiverData) {
											migrateAddPoints(isVarioData, isGPSData, isGeneralData, isElectricData, isMotorDriverData, channelNumber);
											//System.out.println("isVarioData i = " + i);
											isReceiverData = isVarioData = isGPSData = isGeneralData = isElectricData = isMotorDriverData = false;
										}
										parseVario(HoTTbinReader.buf0, HoTTbinReader.buf1, HoTTbinReader.buf2, HoTTbinReader.buf3, HoTTbinReader.buf4);
										isVarioData = true;
									}
									break;

								case HoTTAdapter.SENSOR_TYPE_GPS_115200:
								case HoTTAdapter.SENSOR_TYPE_GPS_19200:
									if (HoTTbinReaderD.isSensorType[HoTTAdapter.Sensor.GPS.ordinal()]) {
										if (isGPSData && isReceiverData) {
											migrateAddPoints(isVarioData, isGPSData, isGeneralData, isElectricData, isMotorDriverData, channelNumber);
											//System.out.println("isGPSData i = " + i);
											isReceiverData = isVarioData = isGPSData = isGeneralData = isElectricData = isMotorDriverData = false;
										}
										parseGPS(HoTTbinReader.buf0, HoTTbinReader.buf1, HoTTbinReader.buf2, HoTTbinReader.buf3, HoTTbinReader.buf4);
										isGPSData = true;
									}
									break;

								case HoTTAdapter.SENSOR_TYPE_GENERAL_115200:
								case HoTTAdapter.SENSOR_TYPE_GENERAL_19200:
									if (HoTTbinReaderD.isSensorType[HoTTAdapter.Sensor.GAM.ordinal()]) {
										if (isGeneralData && isReceiverData) {
											migrateAddPoints(isVarioData, isGPSData, isGeneralData, isElectricData, isMotorDriverData, channelNumber);
											//System.out.println("isGeneralData i = " + i);
											isReceiverData = isVarioData = isGPSData = isGeneralData = isElectricData = false;
										}
										parseGAM(HoTTbinReader.buf0, HoTTbinReader.buf1, HoTTbinReader.buf2, HoTTbinReader.buf3, HoTTbinReader.buf4);
										isGeneralData = true;
									}
									break;

								case HoTTAdapter.SENSOR_TYPE_ELECTRIC_115200:
								case HoTTAdapter.SENSOR_TYPE_ELECTRIC_19200:
									if (HoTTbinReaderD.isSensorType[HoTTAdapter.Sensor.EAM.ordinal()]) {
										if (isElectricData && isReceiverData) {
											migrateAddPoints(isVarioData, isGPSData, isGeneralData, isElectricData, isMotorDriverData, channelNumber);
											//System.out.println("isElectricData i = " + i);
											isReceiverData = isVarioData = isGPSData = isGeneralData = isElectricData = isMotorDriverData = false;
										}
										parseEAM(HoTTbinReader.buf0, HoTTbinReader.buf1, HoTTbinReader.buf2, HoTTbinReader.buf3, HoTTbinReader.buf4);
										isElectricData = true;
									}
									break;

								case HoTTAdapter.SENSOR_TYPE_SPEED_CONTROL_115200:
								case HoTTAdapter.SENSOR_TYPE_SPEED_CONTROL_19200:
									if (HoTTbinReaderD.isSensorType[HoTTAdapter.Sensor.ESC.ordinal()]) {
										if (isMotorDriverData && isReceiverData) {
											migrateAddPoints(isVarioData, isGPSData, isGeneralData, isElectricData, isMotorDriverData, channelNumber);
											//System.out.println("isElectricData i = " + i);
											isReceiverData = isVarioData = isGPSData = isGeneralData = isElectricData = isMotorDriverData = false;
										}
										parseESC(HoTTbinReader.buf0, HoTTbinReader.buf1, HoTTbinReader.buf2, HoTTbinReader.buf3, HoTTbinReader.buf4, channelNumber);
										isMotorDriverData = true;
									}
									break;
								}

								if (HoTTbinReaderD.logger.isLoggable(Level.FINE))
									HoTTbinReaderD.logger.log(Level.FINE, "isReceiverData " + isReceiverData + " isVarioData " + isVarioData + " isGPSData " + isGPSData + " isGeneralData "
											+ isGeneralData + " isElectricData " + isElectricData);

							}

							if (HoTTbinReaderD.logger.isLoggable(Level.FINE))
								HoTTbinReaderD.logger.logp(Level.FINE, HoTTbinReader.$CLASS_NAME, $METHOD_NAME, "logCountVario = " + logCountVario + " logCountGPS = " + logCountGPS
										+ " logCountGeneral = " + logCountGAM + " logCountElectric = " + logCountEAM);
							lastSensor = actualSensor;
							logCountVario = logCountGPS = logCountGAM = logCountEAM = logCountESC = 0;
						}

						switch (lastSensor) {
						case HoTTAdapter.SENSOR_TYPE_VARIO_115200:
						case HoTTAdapter.SENSOR_TYPE_VARIO_19200:
							++logCountVario;
							break;
						case HoTTAdapter.SENSOR_TYPE_GPS_115200:
						case HoTTAdapter.SENSOR_TYPE_GPS_19200:
							++logCountGPS;
							break;
						case HoTTAdapter.SENSOR_TYPE_GENERAL_115200:
						case HoTTAdapter.SENSOR_TYPE_GENERAL_19200:
							++logCountGAM;
							break;
						case HoTTAdapter.SENSOR_TYPE_ELECTRIC_115200:
						case HoTTAdapter.SENSOR_TYPE_ELECTRIC_19200:
							++logCountEAM;
							break;
						case HoTTAdapter.SENSOR_TYPE_SPEED_CONTROL_115200:
						case HoTTAdapter.SENSOR_TYPE_SPEED_CONTROL_19200:
							++logCountESC;
							break;
						}

						if (HoTTbinReaderD.isJustMigrated && HoTTbinReader.countLostPackages > 0) {
							HoTTbinReader.lostPackages.add(HoTTbinReader.countLostPackages);
							HoTTbinReader.countLostPackages = 0;
						}

						if (isReceiverData && (logCountVario > 0 || logCountGPS > 0 || logCountGAM > 0 || logCountEAM > 0 || logCountESC > 0)) {
							HoTTbinReaderD.recordSet.addPoints(HoTTbinReaderD.points, HoTTbinReader.timeStep_ms);
							//System.out.println("isReceiverData i = " + i);
							isReceiverData = false;
						}
						else if (!HoTTbinReaderD.isJustMigrated) {
							HoTTbinReaderD.recordSet.addPoints(HoTTbinReaderD.points, HoTTbinReader.timeStep_ms);
						}
						HoTTbinReaderD.isJustMigrated = false;

						bufCopier.copyToBuffer();
						if (HoTTbinReader.blockSequenceCheck.size() > 1) {
							if (HoTTbinReader.blockSequenceCheck.get(1) != 0 && HoTTbinReader.blockSequenceCheck.get(0) - HoTTbinReader.blockSequenceCheck.get(1) > 1
									&& HoTTbinReader.blockSequenceCheck.get(0) - HoTTbinReader.blockSequenceCheck.get(1) < 4 && pickerParameters.reverseChannelPackageLossCounter.getPercentage() < 15)
								++HoTTbinReader.oldProtocolCount;
							HoTTbinReader.blockSequenceCheck.remove(0);
						}
						HoTTbinReader.blockSequenceCheck.add(HoTTbinReader.buf[33]);

						HoTTbinReader.timeStep_ms += 10;// add default time step from log record of 10 msec

						if (i % progressIndicator == 0) GDE.getUiNotification().setProgress((int) (i * 100 / numberDatablocks));
					}
					else { //skip empty block, but add time step
						if (HoTTbinReaderD.logger.isLoggable(Level.FINE)) HoTTbinReaderD.logger.log(Level.FINE, "-->> Found tx=rx=0 dBm");

						pickerParameters.reverseChannelPackageLossCounter.add(0);
						HoTTbinReaderD.points[0] = pickerParameters.reverseChannelPackageLossCounter.getPercentage() * 1000;

						++countPackageLoss; // add up lost packages in telemetry data
						++HoTTbinReader.countLostPackages;
						//HoTTbinReaderD.points[0] = (int) (countPackageLoss*100.0 / ((HoTTbinReaderD.timeStep_ms+10) / 10.0)*1000.0);

						parseChannel(HoTTbinReader.buf); //Channels
						HoTTbinReaderD.recordSet.addPoints(HoTTbinReaderD.points, HoTTbinReader.timeStep_ms);
						HoTTbinReader.timeStep_ms += 10;
						//reset buffer to avoid mixing data >> 20 Jul 14, not any longer required due to protocol change requesting next sensor data block
						//logCountVario = logCountGPS = logCountGeneral = logCountElectric = logCountMotorDriver = 0;
					}
				}
				else if (!HoTTbinReader.isTextModusSignaled) {
					HoTTbinReader.isTextModusSignaled = true;
					HoTTbinReader.application.openMessageDialogAsync(Messages.getString(gde.device.graupner.hott.MessageIds.GDE_MSGW2404));
				}
				isBufferZero = buf[33] == 4; //after switch to buffer 4 enable data take over while buffer is 0
			}
			if (HoTTbinReader.oldProtocolCount > 2) {
				HoTTbinReader.application.openMessageDialogAsync(Messages.getString(gde.device.graupner.hott.MessageIds.GDE_MSGW2405, new Object[] { HoTTbinReader.oldProtocolCount }));
			}
			String packageLossPercentage = tmpRecordSet.getRecordDataSize(true) > 0 ? String.format("%.1f", (countPackageLoss / tmpRecordSet.getTime_ms(tmpRecordSet.getRecordDataSize(true) - 1) * 1000))
					: "100";
			tmpRecordSet.setRecordSetDescription(tmpRecordSet.getRecordSetDescription()
					+ Messages.getString(gde.device.graupner.hott.MessageIds.GDE_MSGI2404, new Object[] { countPackageLoss, packageLossPercentage, HoTTbinReader.lostPackages.getStatistics() })
					+ HoTTbinReaderD.sensorSignature);
			HoTTbinReaderD.logger.logp(Level.WARNING, HoTTbinReader.$CLASS_NAME, $METHOD_NAME, "skipped number receiver data due to package loss = " + countPackageLoss); //$NON-NLS-1$
			HoTTbinReaderD.logger.logp(Level.TIME, HoTTbinReader.$CLASS_NAME, $METHOD_NAME, "read time = " + StringHelper.getFormatedTime("mm:ss:SSS", (System.nanoTime() / 1000000 - startTime))); //$NON-NLS-1$ //$NON-NLS-2$

			if (GDE.isWithUi()) {
				GDE.getUiNotification().setProgress(99);
				if (!isInitialSwitched) {
					HoTTbinReader.channels.switchChannel(channel.getName());
					channel.switchRecordSet(recordSetName);
					isInitialSwitched = true;
				}
				else {
					device.makeInActiveDisplayable(HoTTbinReaderD.recordSet);
				}
				device.updateVisibilityStatus(HoTTbinReaderD.recordSet, true);
				channel.applyTemplate(recordSetName, false);

				//write filename after import to record description
				HoTTbinReaderD.recordSet.descriptionAppendFilename(file.getName());

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
	public static void migrateAddPoints(boolean isVarioData, boolean isGPSData, boolean isGeneralData, boolean isElectricData, boolean isMotorDriverData, int channelNumber)
			throws DataInconsitsentException {
		//receiver data gets integrated each cycle 0=RXSQ, 1=Altitude, 2=Climb, 3=Climb 3, 4=Climb 10, 5=VoltageRx, 6=TemperatureRx
		//8=Altitude, 9=Climb 1, 10=Climb 3
		//46=VoltageE, 47=CurrentE, 48=CapacityE, 49=PowerE, 50=BalanceE, 51=CellVoltageE 1, 52=CellVoltageE 2 .... 64=CellVoltageE 14, 65=Revolution, 66=VoltageE 1, 67=VoltageE 2, 68=TemperatureE 1, 69=TemperatureE 2
		if (isElectricData) {
			if ((HoTTbinReaderD.points[8] != 0 && HoTTbinReader.pointsEAM[8] != 0) || HoTTbinReader.pointsEAM[8] != 0) HoTTbinReaderD.points[8] = HoTTbinReader.pointsEAM[8];
			for (int j = 9; j < 11; j++) {
				HoTTbinReaderD.points[j] = HoTTbinReader.pointsEAM[j];
			}
			for (int k = 46; k < 46 + 24; k++) {
				HoTTbinReaderD.points[k] = HoTTbinReader.pointsEAM[k];
			}
			//114=VoltageRx_min 115=Speed G, 116=CellVoltage_min G 117=CellNumber_min G 118=Pressure 119=MotorRuntime E 120=Speed E
			//121=Temperature M2 122=Voltage Mmin 123=Current Mmax 124=RPM Mmax 125=Temperatire M1max 126=Temperature M2max
			//127=EventRx 128=EventVario 129=EventGPS 130=EventGAM 131=EventEAM 132=EventESC
			HoTTbinReaderD.points[119] = HoTTbinReader.pointsEAM[119];
			HoTTbinReaderD.points[120] = HoTTbinReader.pointsEAM[120];
			HoTTbinReaderD.points[131] = HoTTbinReader.pointsEAM[131];
		}
		//8=Altitude, 9=Climb 1, 10=Climb 3
		//29=VoltageG, 30=CurrentG, 31=CapacityG, 32=PowerG, 33=BalanceG, 34=CellVoltageG 1, 35=CellVoltageG 2 .... 39=CellVoltageG 6, 40=Revolution, 41=FuelLevel, 42=VoltageG 1, 43=VoltageG 2, 44=TemperatureG 1, 45=TemperatureG 2
		if (isGeneralData) {
			if ((HoTTbinReaderD.points[8] != 0 && HoTTbinReader.pointsGAM[8] != 0) || HoTTbinReader.pointsGAM[8] != 0) HoTTbinReaderD.points[8] = HoTTbinReader.pointsGAM[8];
			for (int j = 9; j < 11; j++) {
				HoTTbinReaderD.points[j] = HoTTbinReader.pointsGAM[j];
			}
			for (int k = 29; k < 29 + 17; k++) {
				HoTTbinReaderD.points[k] = HoTTbinReader.pointsGAM[k];
			}
			//114=VoltageRx_min 115=Speed G, 116=CellVoltage_min G 117=CellNumber_min G 118=Pressure 119=MotorRuntime E 120=Speed E
			//121=Temperature M2 122=Voltage Mmin 123=Current Mmax 124=RPM Mmax 125=Temperatire M1max 126=Temperature M2max
			//127=EventRx 128=EventVario 129=EventGPS 130=EventGAM 131=EventEAM 132=EventESC
			for (int k = 115; k < 115 + 4; k++) {
				HoTTbinReaderD.points[k] = HoTTbinReader.pointsGAM[k];
			}
			HoTTbinReaderD.points[130] = HoTTbinReader.pointsGAM[130];
		}
		//8=Altitude, 9=Climb 1, 10=Climb 3
		//12=Latitude, 13=Longitude, 14=Velocity, 15=Distance, 16=Direction, 17=TripDistance
		//18=Satellites 19=Fix 20=HomeDirection 21=Roll 22=Pitch 23=Yaw 24=GyroX 25=GyroY 26=GyroZ 27=Vibration 28=Version
		if (isGPSData) {
			for (int j = 8; j < 11; j++) {
				HoTTbinReaderD.points[j] = HoTTbinReader.pointsGPS[j];
			}
			for (int k = 12; k < 12 + 17; k++) {
				HoTTbinReaderD.points[k] = HoTTbinReader.pointsGPS[k];
			}
			//114=VoltageRx_min 115=Speed G, 116=CellVoltage_min G 117=CellNumber_min G 118=Pressure 119=MotorRuntime E 120=Speed E
			//121=Temperature M2 122=Voltage Mmin 123=Current Mmax 124=RPM Mmax 125=Temperatire M1max 126=Temperature M2max
			//127=EventRx 128=EventVario 129=EventGPS 130=EventGAM 131=EventEAM 132=EventESC
			HoTTbinReaderD.points[129] = HoTTbinReader.pointsGAM[129];
		}
		//8=Altitude, 9=Climb 1, 10=Climb 3, 11=Climb 10
		if (isVarioData && (HoTTbinReader.pointsVario[8] != 0 || HoTTbinReader.pointsVario[9] != 0 || HoTTbinReader.pointsVario[10] != 0 || HoTTbinReader.pointsVario[11] != 0)) {
			for (int j = 8; j < 12; j++) {
				HoTTbinReaderD.points[j] = HoTTbinReader.pointsVario[j];
			}
		}
		//114=VoltageRx_min 115=Speed G, 116=CellVoltage_min G 117=CellNumber_min G 118=Pressure 119=MotorRuntime E 120=Speed E
		//121=Temperature M2 122=Voltage Mmin 123=Current Mmax 124=RPM Mmax 125=Temperatire M1max 126=Temperature M2max
		//127=EventRx 128=EventVario 129=EventGPS 130=EventGAM 131=EventEAM 132=EventESC
		HoTTbinReaderD.points[128] = HoTTbinReader.pointsVario[128];

		//special test data of FBL receivers
		for (int j = 96; j < 96 + 13; j++) {
			HoTTbinReaderD.points[j] = HoTTbinReader.pointsVario[j];
		}

		//70=VoltageM, 71=CurrentM, 72=CapacityM, 73=PowerM, 74=RevolutionM, 75=TemperatureM
		if (isMotorDriverData) {
			for (int j = 70; j < 70 + 6 && j < HoTTbinReaderD.points.length; j++) {
				HoTTbinReaderD.points[j] = HoTTbinReader.pointsESC[j];
			}
			//114=VoltageRx_min 115=Speed G, 116=CellVoltage_min G 117=CellNumber_min G 118=Pressure 119=MotorRuntime E 120=Speed E
			//121=Temperature M2 122=Voltage Mmin 123=Current Mmax 124=RPM Mmax 125=Temperatire M1max 126=Temperature M2max
			//127=EventRx 128=EventVario 129=EventGPS 130=EventGAM 131=EventEAM 132=EventESC
			for (int j = 121; j < 121 + 6 && j < HoTTbinReaderD.points.length; j++) {
				HoTTbinReaderD.points[j] = HoTTbinReader.pointsESC[j];
			}
			HoTTbinReaderD.points[132] = HoTTbinReader.pointsESC[132];
		}
		
		//add altitude and climb values from selected sensor
		switch (Sensor.VALUES[HoTTbinReader.pickerParameters.altitudeClimbSensorSelection]) {
		case VARIO:
			//8=Altitude, 9=Climb 1, 10=Climb 3, 11=Climb 10
			if (isVarioData)
				for (int j = 8; j < 12; j++) {
					HoTTbinReaderD.points[j] = HoTTbinReader.pointsVario[j];
				}
			break;
		case GPS:
			//8=Altitude, 9=Climb 1, 10=Climb 3
			if (isGPSData)
				for (int j = 8; j < 11; j++) {
					HoTTbinReaderD.points[j] = HoTTbinReader.pointsGPS[j];
				}
			HoTTbinReaderD.points[11] = 0;
			break;
		case GAM:
			//8=Altitude, 9=Climb 1, 10=Climb 3
			if (isGeneralData)
				for (int j = 8; j < 11; j++) {
					HoTTbinReaderD.points[j] = HoTTbinReader.pointsGAM[j];
				}
			HoTTbinReaderD.points[11] = 0;
			break;
		case EAM:
			//8=Altitude, 9=Climb 1, 10=Climb 3
			if (isElectricData)
				for (int j = 8; j < 11; j++) {
					HoTTbinReaderD.points[j] = HoTTbinReader.pointsEAM[j];
				}
			HoTTbinReaderD.points[11] = 0;
			break;
		default:
			break;
		}
		//log.log(Level.INFO, String.format("pickerParameters.altitudeClimbSensorSelection = %s", pickerParameters.altitudeClimbSensorSelection)));
		
		HoTTbinReaderD.recordSet.addPoints(HoTTbinReaderD.points, HoTTbinReader.timeStep_ms);
		HoTTbinReaderD.isJustMigrated = true;
	}

	/**
	 * parse the buffered data from buffer and add points to record set
	 * @param _buf
	 */
	private static void parseReceiver(byte[] _buf) {
		//0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx
		HoTTbinReader.tmpVoltageRx = (_buf[35] & 0xFF);
		HoTTbinReader.tmpTemperatureRx = (_buf[36] & 0xFF);
		HoTTbinReaderD.points[1] = (_buf[38] & 0xFF) * 1000;
		HoTTbinReaderD.points[3] = DataParser.parse2Short(_buf, 40) * 1000;
		if (!pickerParameters.isFilterEnabled || HoTTbinReader.tmpVoltageRx > -1 && HoTTbinReader.tmpVoltageRx < 100 && HoTTbinReader.tmpTemperatureRx < 100) {
			HoTTbinReaderD.points[2] = (convertRxDbm2Strength(_buf[4] & 0xFF)) * 1000;
			HoTTbinReaderD.points[4] = (_buf[3] & 0xFF) * -1000;
			HoTTbinReaderD.points[5] = (_buf[4] & 0xFF) * -1000;
			HoTTbinReaderD.points[6] = (_buf[35] & 0xFF) * 1000;
			HoTTbinReaderD.points[7] = ((_buf[36] & 0xFF) - 20) * 1000;
			HoTTbinReaderD.points[114] = (_buf[39] & 0xFF) * 1000;
		}
		//114=VoltageRx_min 115=Speed G, 116=CellVoltage_min G 117=CellNumber_min G 118=Pressure 119=MotorRuntime E 120=Speed E
		//121=Temperature M2 122=Voltage Mmin 123=Current Mmax 124=RPM Mmax 125=Temperatire M1max 126=Temperature M2max
		//127=EventRx 128=EventVario 129=EventGPS 130=EventGAM 131=EventEAM 132=EventESC
		if ((_buf[32] & 0x40) > 0 || (_buf[32] & 0x20) > 0 && HoTTbinReader.tmpTemperatureRx >= 70) //T = 70 - 20 = 50 lowest temperature warning
			HoTTbinReaderD.points[127] = (_buf[32] & 0x60) * 1000; //inverse event V,T only
		else
			HoTTbinReaderD.points[127] = 0;
	}

	/**
	 * parse the buffered data from buffer 0 to 2 and add points to record set
	 * @param sdLogVersion
	 * @param _buf0
	 * @param _buf1
	 * @param _buf2
	 */
	private static void parseVario(byte[] _buf0, byte[] _buf1, byte[] _buf2, byte[] _buf3, byte[] _buf4) {
		//0=RXSQ, 1=Altitude, 2=Climb, 3=Climb 3, 4=Climb 10, 5=VoltageRx, 6=TemperatureRx
		//8=Altitude, 9=Climb 1, 10=Climb 3, 11=Climb 10
		HoTTbinReader.tmpHeight = DataParser.parse2Short(_buf1, 2) - 500;
		HoTTbinReader.tmpClimb10 = DataParser.parse2UnsignedShort(_buf2, 2) - 30000;
		if (!HoTTbinReader.pickerParameters.isFilterEnabled || (HoTTbinReader.tmpHeight >= -490 && HoTTbinReader.tmpHeight < 5000 && HoTTbinReader.tmpClimb10 > -10000 && HoTTbinReader.tmpClimb10 < 10000)) {
			HoTTbinReader.pointsVario[8] = HoTTbinReader.tmpHeight * 1000;
			//pointsVarioMax = DataParser.parse2Short(buf1, 4) * 1000;
			//pointsVarioMin = DataParser.parse2Short(buf1, 6) * 1000;
			HoTTbinReader.pointsVario[9] = (DataParser.parse2UnsignedShort(_buf1, 8) - 30000) * 10;
			HoTTbinReader.pointsVario[10] = (DataParser.parse2UnsignedShort(_buf2, 0) - 30000) * 10;
			HoTTbinReader.pointsVario[11] = HoTTbinReader.tmpClimb10 * 10;
		}
		//96=Test 00, 97=Test 01, 98=Test 02, ... , 108=Test 12
		for (int i = 0, j = 0; i < 3; i++, j += 2) {
			HoTTbinReader.pointsVario[i + 96] = DataParser.parse2Short(_buf2, 4 + j) * 1000;
		}
		for (int i = 0, j = 0; i < 5; i++, j += 2) {
			HoTTbinReader.pointsVario[i + 99] = DataParser.parse2Short(_buf3, 0 + j) * 1000;
		}
		for (int i = 0, j = 0; i < 5; i++, j += 2) {
			HoTTbinReader.pointsVario[i + 104] = DataParser.parse2Short(_buf4, 0 + j) * 1000;
		}
		//114=VoltageRx_min 115=Speed G, 116=CellVoltage_min G 117=CellNumber_min G 118=Pressure 119=MotorRuntime E 120=Speed E
		//121=Temperature M2 122=Voltage Mmin 123=Current Mmax 124=RPM Mmax 125=Temperatire M1max 126=Temperature M2max
		//127=EventRx 128=EventVario 129=EventGPS 130=EventGAM 131=EventEAM 132=EventESC
		HoTTbinReader.pointsVario[128] = (_buf1[1] & 0x3F) * 1000; //inverse event
	}

	/**
	 * parse the buffered data from buffer 0 to 3 and add points to record set
	 * @param _buf0
	 * @param _buf1
	 * @param _buf2
	 * @param _buf3
	 */
	private static void parseGPS(byte[] _buf0, byte[] _buf1, byte[] _buf2, byte[] _buf3, byte[] _buf4) {
		HoTTbinReader.tmpHeight = DataParser.parse2Short(_buf2, 8) - 500;
		HoTTbinReader.tmpClimb1 = (DataParser.parse2UnsignedShort(_buf3, 0) - 30000);
		HoTTbinReader.tmpClimb3 = (_buf3[2] & 0xFF) - 120;
		HoTTbinReader.tmpVelocity = DataParser.parse2UnsignedShort(_buf1, 4) * 1000;
		if (!pickerParameters.isFilterEnabled || (HoTTbinReader.tmpClimb1 > -20000 && HoTTbinReader.tmpClimb3 > -90 && HoTTbinReader.tmpHeight >= -490 && HoTTbinReader.tmpHeight < 4500)) {
			//0=RXSQ, 1=Latitude, 2=Longitude, 3=Altitude, 4=Climb1, 5=Climb3, 6=Velocity, 7=Distance, 8=Direction, 9=TripLength, 10=VoltageRx, 11=TemperatureRx
			//8=Altitude, 9=Climb1, 10=Climb3
			//12=Latitude, 13=Longitude, 14=Velocity, 15=Distance, 16=Direction, 17=TripDistance
			HoTTbinReader.pointsGPS[14] = pickerParameters.isFilterEnabled && HoTTbinReader.tmpVelocity > 500000 ? HoTTbinReader.pointsGPS[14] : HoTTbinReader.tmpVelocity;

			HoTTbinReader.tmpLatitude = DataParser.parse2Short(_buf1, 7) * 10000 + DataParser.parse2Short(_buf1[9], _buf2[0]);
			HoTTbinReader.tmpLatitude = _buf1[6] == 1 ? -1 * HoTTbinReader.tmpLatitude : HoTTbinReader.tmpLatitude;
			HoTTbinReader.tmpLatitudeDelta = Math.abs(HoTTbinReader.tmpLatitude - HoTTbinReader.pointsGPS[12]);
			HoTTbinReader.tmpLatitudeDelta = HoTTbinReader.tmpLatitudeDelta > 400000 ? HoTTbinReader.tmpLatitudeDelta - 400000 : HoTTbinReader.tmpLatitudeDelta;
			HoTTbinReader.latitudeTolerance = (HoTTbinReader.pointsGPS[14] / 1000.0) * (HoTTbinReader.timeStep_ms - HoTTbinReader.lastLatitudeTimeStep) / pickerParameters.latitudeToleranceFactor;
			HoTTbinReader.latitudeTolerance = HoTTbinReader.latitudeTolerance > 0 ? HoTTbinReader.latitudeTolerance : 5;

			if (!pickerParameters.isFilterEnabled || HoTTbinReader.pointsGPS[12] == 0 || HoTTbinReader.tmpLatitudeDelta <= HoTTbinReader.latitudeTolerance) {
				HoTTbinReader.lastLatitudeTimeStep = HoTTbinReader.timeStep_ms;
				HoTTbinReader.pointsGPS[12] = HoTTbinReader.tmpLatitude;
			}
			else {
				if (HoTTbinReader.log.isLoggable(Level.FINE))
					HoTTbinReader.log.log(Level.FINE, StringHelper.getFormatedTime("HH:mm:ss:SSS", HoTTbinReader.timeStep_ms - GDE.ONE_HOUR_MS) + " Lat " + HoTTbinReader.tmpLatitude + " - "
							+ HoTTbinReader.tmpLatitudeDelta);
			}

			HoTTbinReader.tmpLongitude = DataParser.parse2Short(_buf2, 2) * 10000 + DataParser.parse2Short(_buf2, 4);
			HoTTbinReader.tmpLongitude = _buf2[1] == 1 ? -1 * HoTTbinReader.tmpLongitude : HoTTbinReader.tmpLongitude;
			HoTTbinReader.tmpLongitudeDelta = Math.abs(HoTTbinReader.tmpLongitude - HoTTbinReader.pointsGPS[13]);
			HoTTbinReader.tmpLongitudeDelta = HoTTbinReader.tmpLongitudeDelta > 400000 ? HoTTbinReader.tmpLongitudeDelta - 400000 : HoTTbinReader.tmpLongitudeDelta;
			HoTTbinReader.longitudeTolerance = (HoTTbinReader.pointsGPS[14] / 1000.0) * (HoTTbinReader.timeStep_ms - HoTTbinReader.lastLongitudeTimeStep) / pickerParameters.longitudeToleranceFactor;
			HoTTbinReader.longitudeTolerance = HoTTbinReader.longitudeTolerance > 0 ? HoTTbinReader.longitudeTolerance : 5;

			if (!pickerParameters.isFilterEnabled || HoTTbinReader.pointsGPS[13] == 0 || HoTTbinReader.tmpLongitudeDelta <= HoTTbinReader.longitudeTolerance) {
				HoTTbinReader.lastLongitudeTimeStep = HoTTbinReader.timeStep_ms;
				HoTTbinReader.pointsGPS[13] = HoTTbinReader.tmpLongitude;
			}
			else {
				if (HoTTbinReader.log.isLoggable(Level.FINE))
					HoTTbinReader.log.log(Level.FINE, StringHelper.getFormatedTime("HH:mm:ss:SSS", HoTTbinReader.timeStep_ms - GDE.ONE_HOUR_MS) + " Long " + HoTTbinReader.tmpLongitude + " - "
							+ HoTTbinReader.tmpLongitudeDelta);
			}

			HoTTbinReader.pointsGPS[8] = HoTTbinReader.tmpHeight * 1000;
			HoTTbinReader.pointsGPS[9] = HoTTbinReader.tmpClimb1 * 10;
			HoTTbinReader.pointsGPS[10] = HoTTbinReader.tmpClimb3 * 1000;
			HoTTbinReader.pointsGPS[15] = DataParser.parse2UnsignedShort(_buf2, 6) * 1000;
			HoTTbinReader.pointsGPS[16] = (_buf1[3] & 0xFF) * 1000;
			HoTTbinReader.pointsGPS[17] = 0;
			//18=Satellites 19=Fix 
			HoTTbinReader.pointsGPS[18] = (_buf3[3] & 0xFF) * 1000;
			switch (_buf3[4]) { //sat-fix
			case '-':
				HoTTbinReader.pointsGPS[19] = 0;
				break;
			case '2':
				HoTTbinReader.pointsGPS[19] = 2000;
				break;
			case '3':
				HoTTbinReader.pointsGPS[19] = 3000;
				break;
			case 'D':
				HoTTbinReader.pointsGPS[19] = 4000;
				break;
			default:
				try {
					HoTTbinReader.pointsGPS[19] = Integer.valueOf(String.format("%c", _buf3[4])) * 1000;
				}
				catch (NumberFormatException e1) {
					HoTTbinReader.pointsGPS[19] = 1000;
				}
				break;
			}
			//log.log(Level.INFO, StringHelper.byte2Hex2CharString(_buf3, _buf3.length));
			HoTTbinReader.pointsGPS[20] = (_buf3[5] & 0xFF) * 1000; //20=HomeDirection
			//21=Roll 22=Pitch 23=Yaw
			HoTTbinReader.pointsGPS[21] = _buf3[6] * 1000;
			HoTTbinReader.pointsGPS[22] = _buf3[7] * 1000;
			HoTTbinReader.pointsGPS[23] = _buf3[8] * 1000; 
			//24=GyroX 25=GyroY 26=GyroZ 			
			HoTTbinReader.pointsGPS[24] = DataParser.parse2Short(_buf3[9], _buf4[0]) * 1000;
			HoTTbinReader.pointsGPS[25] = DataParser.parse2Short(_buf4, 1) * 1000;
			HoTTbinReader.pointsGPS[26] = DataParser.parse2Short(_buf4, 3) * 1000;
			//27=Vibration 28=Version			
			HoTTbinReader.pointsGPS[27] = (_buf4[5] & 0xFF) * 1000;
			HoTTbinReader.pointsGPS[28] = _buf4[9] * 1000;
		}
		//114=VoltageRx_min 115=Speed G, 116=CellVoltage_min G 117=CellNumber_min G 118=Pressure 119=MotorRuntime E 120=Speed E
		//121=Temperature M2 122=Voltage Mmin 123=Current Mmax 124=RPM Mmax 125=Temperatire M1max 126=Temperature M2max
		//127=EventRx 128=EventVario 129=EventGPS 130=EventGAM 131=EventEAM 132=EventESC
		HoTTbinReader.pointsGPS[129] = (_buf1[1] & 0x0F) * 1000; //inverse event
	}

	public static int getStartTime(int HH, int mm, int ss, int SSS) {
		try {
			return Integer.valueOf(String.format("%02d%02.0f%02.0f%03d", HH, 100.0 / 60 * mm, 100.0 / 60 * ss, SSS));
		}
		catch (NumberFormatException e) {
			return HoTTbinReader.pointsGPS[23] + HoTTbinReaderD.gpsStartTime; //keep time unchanged
		}
	}

	/**
	 * parse the buffered data from buffer 0 to 4 and add points to record set
	 * @param _buf0
	 * @param _buf1
	 * @param _buf2
	 * @param _buf3
	 * @param _buf4
	 */
	private static void parseGAM(byte[] _buf0, byte[] _buf1, byte[] _buf2, byte[] _buf3, byte[] _buf4) {
		HoTTbinReader.tmpHeight = DataParser.parse2Short(_buf3, 0) - 500;
		HoTTbinReader.tmpClimb3 = (_buf3[4] & 0xFF) - 120;
		HoTTbinReader.tmpVoltage1 = DataParser.parse2Short(_buf1[9], _buf2[0]);
		HoTTbinReader.tmpVoltage2 = DataParser.parse2Short(_buf2, 1);
		HoTTbinReader.tmpCapacity = DataParser.parse2Short(_buf3[9], _buf4[0]);
		//0=RF_RXSQ, 1=Voltage, 2=Current, 3=Capacity, 4=Power, 5=Balance, 6=CellVoltage 1, 7=CellVoltage 2 .... 11=CellVoltage 6, 12=Revolution, 13=Altitude, 14=Climb, 15=Climb3, 16=FuelLevel, 17=Voltage 1, 18=Voltage 2, 19=Temperature 1, 20=Temperature 2
		//8=Altitude, 9=Climb 1, 10=Climb 3
		//29=VoltageG, 30=CurrentG, 31=CapacityG, 32=PowerG, 33=BalanceG, 34=CellVoltageG 1, 35=CellVoltageG 2 .... 39=CellVoltageG 6, 40=Revolution, 41=FuelLevel, 42=VoltageG 1, 43=VoltageG 2, 44=TemperatureG 1, 45=TemperatureG 2
		if (!pickerParameters.isFilterEnabled
				|| (HoTTbinReader.tmpClimb3 > -90 && HoTTbinReader.tmpHeight >= -490 && HoTTbinReader.tmpHeight < 5000 && Math.abs(HoTTbinReader.tmpVoltage1) < 600 && Math.abs(HoTTbinReader.tmpVoltage2) < 600)) {
			int maxVotage = Integer.MIN_VALUE;
			int minVotage = Integer.MAX_VALUE;
			HoTTbinReader.pointsGAM[29] = DataParser.parse2Short(_buf3, 7) * 1000;
			HoTTbinReader.pointsGAM[30] = DataParser.parse2Short(_buf3, 5) * 1000;
			if (!pickerParameters.isFilterEnabled || HoTTbinReaderD.recordSet.getRecordDataSize(true) <= 1
					|| (HoTTbinReader.tmpCapacity != 0 && Math.abs(HoTTbinReader.tmpCapacity) <= (HoTTbinReader.pointsGAM[31] / 1000 + HoTTbinReader.pointsGAM[29] / 1000 * HoTTbinReader.pointsGAM[30] / 1000 / 2500 + 2))) {
				HoTTbinReader.pointsGAM[31] = HoTTbinReader.tmpCapacity * 1000;
			}
			else {
				if (HoTTbinReader.log.isLoggable(Level.FINE))
					HoTTbinReader.log.log(Level.WARNING, StringHelper.getFormatedTime("mm:ss.SSS", HoTTbinReader.timeStep_ms) + " - " + HoTTbinReader.tmpCapacity + " - "
							+ (HoTTbinReader.pointsGAM[31] / 1000) + " + " + (HoTTbinReader.pointsGAM[29] / 1000 * HoTTbinReader.pointsGAM[30] / 1000 / 2500 + 2));
			}
			HoTTbinReader.pointsGAM[32] = Double.valueOf(HoTTbinReader.pointsGAM[29] / 1000.0 * HoTTbinReader.pointsGAM[30]).intValue();
			for (int j = 0; j < 6; j++) {
				HoTTbinReader.pointsGAM[j + 34] = (_buf1[3 + j] & 0xFF) * 1000;
				if (HoTTbinReader.pointsGAM[j + 34] > 0) {
					maxVotage = HoTTbinReader.pointsGAM[j + 34] > maxVotage ? HoTTbinReader.pointsGAM[j + 34] : maxVotage;
					minVotage = HoTTbinReader.pointsGAM[j + 34] < minVotage ? HoTTbinReader.pointsGAM[j + 34] : minVotage;
				}
			}
			HoTTbinReader.pointsGAM[33] = maxVotage != Integer.MIN_VALUE && minVotage != Integer.MAX_VALUE ? (maxVotage - minVotage) * 10 : 0;
			HoTTbinReader.pointsGAM[40] = DataParser.parse2Short(_buf2, 8) * 1000;
			HoTTbinReader.pointsGAM[41] = DataParser.parse2Short(_buf2, 6) * 1000;
			HoTTbinReader.pointsGAM[8] = HoTTbinReader.tmpHeight * 1000;
			HoTTbinReader.pointsGAM[9] = (DataParser.parse2UnsignedShort(_buf3, 2) - 30000) * 10;
			HoTTbinReader.pointsGAM[10] = HoTTbinReader.tmpClimb3 * 1000;
			HoTTbinReader.pointsGAM[42] = HoTTbinReader.tmpVoltage1 * 100;
			HoTTbinReader.pointsGAM[43] = HoTTbinReader.tmpVoltage2 * 100;
			HoTTbinReader.pointsGAM[44] = ((_buf2[3] & 0xFF) - 20) * 1000;
			HoTTbinReader.pointsGAM[45] = ((_buf2[4] & 0xFF) - 20) * 1000;
			//114=VoltageRx_min 115=Speed G, 116=CellVoltage_min G 117=CellNumber_min G 118=Pressure 119=MotorRuntime E 120=Speed E
			//121=Temperature M2 122=Voltage Mmin 123=Current Mmax 124=RPM Mmax 125=Temperatire M1max 126=Temperature M2max
			//127=EventRx 128=EventVario 129=EventGPS 130=EventGAM 131=EventEAM 132=EventESC
			HoTTbinReader.pointsGAM[115] = DataParser.parse2Short(_buf4, 1) * 1000; //Speed [km/h
			HoTTbinReader.pointsGAM[116] = (_buf4[3] & 0xFF) * 1000; //lowest cell voltage 124 = 2.48 V
			HoTTbinReader.pointsGAM[117] = (_buf4[4] & 0xFF) * 1000; //cell number lowest cell voltage
			HoTTbinReader.pointsGAM[118] = (_buf4[8] & 0xFF) * 1000; //Pressure
		}
		HoTTbinReader.pointsGAM[130] = ((_buf1[1] & 0xFF) + ((_buf1[2] & 0x7F) << 8)) * 1000; //inverse event
	}

	/**
		 * parse the buffered data from buffer 0 to 4 and add points to record set
	 * @param _buf0
	 * @param _buf1
	 * @param _buf2
	 * @param _buf3
	 * @param _buf4
	 * @throws DataInconsitsentException
	 */
	private static void parseEAM(byte[] _buf0, byte[] _buf1, byte[] _buf2, byte[] _buf3, byte[] _buf4) throws DataInconsitsentException {
		HoTTbinReader.tmpHeight = DataParser.parse2Short(_buf3, 3) - 500;
		HoTTbinReader.tmpClimb3 = (_buf4[3] & 0xFF) - 120;
		HoTTbinReader.tmpVoltage1 = DataParser.parse2Short(_buf2, 7);
		HoTTbinReader.tmpVoltage2 = DataParser.parse2Short(_buf2[9], _buf3[0]);
		HoTTbinReader.tmpCapacity = DataParser.parse2Short(_buf3[9], _buf4[0]);
		//0=RXSQ, 1=Voltage, 2=Current, 3=Capacity, 4=Power, 5=Balance, 6=CellVoltage 1, 7=CellVoltage 2 .... 19=CellVoltage 14, 20=Altitude, 21=Climb 1, 22=Climb 3, 23=Voltage 1, 24=Voltage 2, 25=Temperature 1, 26=Temperature 2
		//8=Altitude, 9=Climb 1, 10=Climb 3
		//46=VoltageE, 47=CurrentE, 48=CapacityE, 49=PowerE, 50=BalanceE, 51=CellVoltageE 1, 52=CellVoltageE 2 .... 64=CellVoltageE 14, 65=Revolution, 66=VoltageE 1, 67=VoltageE 2, 68=TemperatureE 1, 69=TemperatureE 2 70=revolution
		if (!pickerParameters.isFilterEnabled
				|| (HoTTbinReader.tmpClimb3 > -90 && HoTTbinReader.tmpHeight >= -490 && HoTTbinReader.tmpHeight < 5000 && Math.abs(HoTTbinReader.tmpVoltage1) < 600 && Math.abs(HoTTbinReader.tmpVoltage2) < 600)) {
			int maxVotage = Integer.MIN_VALUE;
			int minVotage = Integer.MAX_VALUE;
			HoTTbinReader.pointsEAM[46] = DataParser.parse2Short(_buf3, 7) * 1000;
			HoTTbinReader.pointsEAM[47] = DataParser.parse2Short(_buf3, 5) * 1000;
			if (!pickerParameters.isFilterEnabled || HoTTbinReaderD.recordSet.getRecordDataSize(true) <= 1
					|| Math.abs(HoTTbinReader.tmpCapacity) <= (HoTTbinReader.pointsEAM[48] / 1000 + HoTTbinReader.pointsEAM[46] / 1000 * HoTTbinReader.pointsEAM[47] / 1000 / 2500 + 2)) {
				HoTTbinReader.pointsEAM[48] = HoTTbinReader.tmpCapacity * 1000;
			}
			else {
				if (HoTTbinReader.log.isLoggable(Level.FINE))
					HoTTbinReader.log.log(Level.WARNING, StringHelper.getFormatedTime("mm:ss.SSS", HoTTbinReader.timeStep_ms) + " - " + HoTTbinReader.tmpCapacity + " - "
							+ (HoTTbinReader.pointsEAM[48] / 1000) + " + " + (HoTTbinReader.pointsEAM[46] / 1000 * HoTTbinReader.pointsEAM[47] / 1000 / 2500 + 2));
			}
			HoTTbinReader.pointsEAM[49] = Double.valueOf(HoTTbinReader.pointsEAM[46] / 1000.0 * HoTTbinReader.pointsEAM[47]).intValue(); // power U*I [W];
			for (int j = 0; j < 7; j++) {
				HoTTbinReader.pointsEAM[j + 51] = (_buf1[3 + j] & 0xFF) * 1000;
				if (HoTTbinReader.pointsEAM[j + 51] > 0) {
					maxVotage = HoTTbinReader.pointsEAM[j + 51] > maxVotage ? HoTTbinReader.pointsEAM[j + 51] : maxVotage;
					minVotage = HoTTbinReader.pointsEAM[j + 51] < minVotage ? HoTTbinReader.pointsEAM[j + 51] : minVotage;
				}
			}
			for (int j = 0; j < 7; j++) {
				HoTTbinReader.pointsEAM[j + 58] = (_buf2[j] & 0xFF) * 1000;
				if (HoTTbinReader.pointsEAM[j + 58] > 0) {
					maxVotage = HoTTbinReader.pointsEAM[j + 58] > maxVotage ? HoTTbinReader.pointsEAM[j + 58] : maxVotage;
					minVotage = HoTTbinReader.pointsEAM[j + 58] < minVotage ? HoTTbinReader.pointsEAM[j + 58] : minVotage;
				}
			}
			//calculate balance on the fly
			HoTTbinReader.pointsEAM[50] = maxVotage != Integer.MIN_VALUE && minVotage != Integer.MAX_VALUE ? (maxVotage - minVotage) * 10 : 0;
			HoTTbinReader.pointsEAM[8] = HoTTbinReader.tmpHeight * 1000;
			HoTTbinReader.pointsEAM[9] = (DataParser.parse2UnsignedShort(_buf4, 1) - 30000) * 10;
			HoTTbinReader.pointsEAM[10] = HoTTbinReader.tmpClimb3 * 1000;
			HoTTbinReader.pointsEAM[65] = DataParser.parse2Short(_buf4, 4) * 1000;
			HoTTbinReader.pointsEAM[66] = HoTTbinReader.tmpVoltage1 * 100;
			HoTTbinReader.pointsEAM[67] = HoTTbinReader.tmpVoltage2 * 100;
			HoTTbinReader.pointsEAM[68] = ((_buf3[1] & 0xFF) - 20) * 1000;
			HoTTbinReader.pointsEAM[69] = ((_buf3[2] & 0xFF) - 20) * 1000;
			HoTTbinReader.pointsEAM[119] = ((_buf4[6] & 0xFF) * 60 + (_buf4[7] & 0xFF)) * 1000; // motor time
			HoTTbinReader.pointsEAM[120] = DataParser.parse2Short(_buf4, 8) * 1000; // speed
		}
		//114=VoltageRx_min 115=Speed G, 116=CellVoltage_min G 117=CellNumber_min G 118=Pressure 119=MotorRuntime E 120=Speed E
		//121=Temperature M2 122=Voltage Mmin 123=Current Mmax 124=RPM Mmax 125=Temperatire M1max 126=Temperature M2max
		//127=EventRx 128=EventVario 129=EventGPS 130=EventGAM 131=EventEAM 132=EventESC
		HoTTbinReader.pointsEAM[131] = ((_buf1[1] & 0xFF) + ((_buf1[2] & 0x7F) << 8)) * 1000; //inverse event
	}

	/**
	 * parse the buffered data from buffer and add points to record set
	 * @param _buf
	 */
	private static void parseChannel(byte[] _buf) {
		//0=RF_RXSQ, 1=RXSQ, 2=Strength, 3=PackageLoss, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx
		HoTTbinReaderD.points[4] = (_buf[3] & 0xFF) * -1000;
		HoTTbinReaderD.points[5] = (_buf[4] & 0xFF) * -1000;
		//76=Ch 1, 77=Ch 2 , 78=Ch 3 .. 91=Ch 16
		HoTTbinReaderD.points[76] = (DataParser.parse2UnsignedShort(_buf, 8) / 2) * 1000; //1197
		HoTTbinReaderD.points[77] = (DataParser.parse2UnsignedShort(_buf, 10) / 2) * 1000; //
		HoTTbinReaderD.points[78] = (DataParser.parse2UnsignedShort(_buf, 12) / 2) * 1000;
		HoTTbinReaderD.points[79] = (DataParser.parse2UnsignedShort(_buf, 14) / 2) * 1000;
		HoTTbinReaderD.points[80] = (DataParser.parse2UnsignedShort(_buf, 16) / 2) * 1000;
		HoTTbinReaderD.points[81] = (DataParser.parse2UnsignedShort(_buf, 18) / 2) * 1000;
		HoTTbinReaderD.points[82] = (DataParser.parse2UnsignedShort(_buf, 20) / 2) * 1000;
		HoTTbinReaderD.points[83] = (DataParser.parse2UnsignedShort(_buf, 22) / 2) * 1000;

		if (_buf[5] == 0x00) { //channel 9-12
			HoTTbinReaderD.points[84] = (DataParser.parse2UnsignedShort(_buf, 24) / 2) * 1000;
			HoTTbinReaderD.points[85] = (DataParser.parse2UnsignedShort(_buf, 26) / 2) * 1000;
			HoTTbinReaderD.points[86] = (DataParser.parse2UnsignedShort(_buf, 28) / 2) * 1000;
			HoTTbinReaderD.points[87] = (DataParser.parse2UnsignedShort(_buf, 30) / 2) * 1000;
			if (HoTTbinReaderD.points[88] == 0) {
				HoTTbinReaderD.points[88] = 1500 * 1000;
				HoTTbinReaderD.points[89] = 1500 * 1000;
				HoTTbinReaderD.points[90] = 1500 * 1000;
				HoTTbinReaderD.points[91] = 1500 * 1000;
			}
		}
		else { //channel 13-16
			HoTTbinReaderD.points[88] = (DataParser.parse2UnsignedShort(_buf, 24) / 2) * 1000;
			HoTTbinReaderD.points[89] = (DataParser.parse2UnsignedShort(_buf, 26) / 2) * 1000;
			HoTTbinReaderD.points[90] = (DataParser.parse2UnsignedShort(_buf, 28) / 2) * 1000;
			HoTTbinReaderD.points[91] = (DataParser.parse2UnsignedShort(_buf, 30) / 2) * 1000;
			if (HoTTbinReaderD.points[84] == 0) {
				HoTTbinReaderD.points[84] = 1500 * 1000;
				HoTTbinReaderD.points[85] = 1500 * 1000;
				HoTTbinReaderD.points[86] = 1500 * 1000;
				HoTTbinReaderD.points[67] = 1500 * 1000;
			}
		}
		//92=PowerOff, 93=BattLow, 94=Reset, 95=Warning
		HoTTbinReaderD.points[92] = (_buf[50] & 0x01) * 100000;
		HoTTbinReaderD.points[93] = (_buf[50] & 0x02) * 50000;
		HoTTbinReaderD.points[94] = (_buf[50] & 0x04) * 25000;
		if (_buf[32] > 0 && _buf[32] < 27)
			HoTTbinReaderD.points[95] = _buf[32] * 1000; //warning
		else
			HoTTbinReaderD.points[95] = 0;
	}

	/**
	 * parse the buffered data from buffer 0 to 4 and add points to record set
	 * @param _buf0
	 * @param _buf1
	 * @param _buf2
	 * @param channelNumber
	 * @throws DataInconsitsentException
	 */
	private static void parseESC(byte[] _buf0, byte[] _buf1, byte[] _buf2, byte[] _buf3, byte[] _buf4, int channelNumber) throws DataInconsitsentException {
		HoTTbinReader.tmpVoltage = DataParser.parse2Short(_buf1, 3);
		HoTTbinReader.tmpCurrent = DataParser.parse2Short(_buf2, 1);
		HoTTbinReader.tmpCapacity = DataParser.parse2Short(_buf1, 7);
		HoTTbinReader.tmpRevolution = DataParser.parse2Short(_buf2, 5);
		HoTTbinReader.tmpTemperatureFet = (_buf1[9] & 0xFF) - 20;
		//70=VoltageM, 71=CurrentM, 72=CapacityM, 73=PowerM, 74=RevolutionM, 75=TemperatureM
		if (!HoTTbinReader.pickerParameters.isFilterEnabled
				|| HoTTbinReader.tmpVoltage > 0 && HoTTbinReader.tmpVoltage < 1000 && HoTTbinReader.tmpCurrent < 4000 && HoTTbinReader.tmpCurrent > -10 && HoTTbinReader.tmpRevolution > -1
				&& HoTTbinReader.tmpRevolution < 20000 && !(HoTTbinReaderD.lastEscFetTemp != 0 && Math.abs(HoTTbinReaderD.lastEscFetTemp / 1000 - HoTTbinReader.tmpTemperatureFet) > 20)) {
			HoTTbinReader.pointsESC[70] = HoTTbinReader.tmpVoltage * 1000;
			HoTTbinReader.pointsESC[71] = HoTTbinReader.tmpCurrent * 1000;
			if (!pickerParameters.isFilterEnabled || HoTTbinReaderD.recordSet.getRecordDataSize(true) <= 1
					|| Math.abs(HoTTbinReader.tmpCapacity) <= (HoTTbinReader.pointsESC[72] / 1000 + HoTTbinReader.tmpVoltage * HoTTbinReader.tmpCurrent / 2500 + 2)) {
				HoTTbinReader.pointsESC[72] = HoTTbinReader.tmpCapacity * 1000;
			}
			else {
				if (HoTTbinReader.log.isLoggable(Level.FINE))
					HoTTbinReader.log.log(Level.WARNING, StringHelper.getFormatedTime("mm:ss.SSS", HoTTbinReader.timeStep_ms) + " - " + HoTTbinReader.tmpCapacity + " - "
							+ (HoTTbinReader.pointsESC[72] / 1000) + " + " + (HoTTbinReader.tmpVoltage * HoTTbinReader.tmpCurrent / 2500 + 2));
			}
			HoTTbinReader.pointsESC[73] = Double.valueOf(HoTTbinReader.pointsESC[70] / 1000.0 * HoTTbinReader.pointsESC[71]).intValue();
			HoTTbinReader.pointsESC[74] = HoTTbinReader.tmpRevolution * 1000;
			HoTTbinReader.pointsESC[75] = HoTTbinReaderD.lastEscFetTemp = HoTTbinReader.tmpTemperatureFet * 1000;

			HoTTbinReader.pointsESC[121] = ((_buf2[9] & 0xFF) - 20) * 1000;
			HoTTbinReader.pointsESC[122] = DataParser.parse2Short(_buf1, 5) * 1000;
			HoTTbinReader.pointsESC[123] = DataParser.parse2Short(_buf2, 3) * 1000;
			HoTTbinReader.pointsESC[124] = DataParser.parse2Short(_buf2, 7) * 1000;
			HoTTbinReader.pointsESC[125] = ((_buf2[0] & 0xFF) - 20) * 1000;
			HoTTbinReader.pointsESC[126] = ((_buf3[0] & 0xFF) - 20) * 1000;
		}
		//114=VoltageRx_min 115=Speed G, 116=CellVoltage_min G 117=CellNumber_min G 118=Pressure 119=MotorRuntime E 120=Speed E
		//121=Temperature M2 122=Voltage Mmin 123=Current Mmax 124=RPM Mmax 125=Temperatire M1max 126=Temperature M2max
		//127=EventRx 128=EventVario 129=EventGPS 130=EventGAM 131=EventEAM 132=EventESC
		HoTTbinReader.pointsESC[132] = (_buf1[1] & 0xFF) * 1000; //inverse event
	}
}
