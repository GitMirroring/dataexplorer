/**
 *
 */
package gde.device.graupner;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
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
 * @author brueg
 */
public class HoTTlogReader extends HoTTbinReader {
	final static Logger	log					= Logger.getLogger(HoTTlogReader2.class.getName());
	static int[]				points;


	/**
	 * read complete file data and display the first found record set
	 *
	 * @param filePath
	 * @throws Exception
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
		HoTTAdapter device = (HoTTAdapter) HoTTbinReader.application.getActiveDevice();
		int recordSetNumber = HoTTbinReader.channels.get(1).maxSize() + 1;
		String recordSetName = GDE.STRING_EMPTY;
		String recordSetNameExtend = getRecordSetExtend(file);
		Channel channel = null;
		HoTTbinReader.recordSetReceiver = null; // 0=RF_RXSQ, 1=RXSQ, 2=Strength, 3=PackageLoss, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=UminRx
		HoTTbinReader.recordSetChannel = null; // 0=FreCh, 1=Tx, 2=Rx, 3=Ch 1, 4=Ch 2 .. 18=Ch 16 19=PowerOff 20=BattLow 21=Reset 22=Warning
		HoTTbinReader.recordSetGAM = null; // 0=RXSQ, 1=Voltage, 2=Current, 3=Capacity, 4=Power, 5=Balance, 6=CellVoltage 1, 7=CellVoltage 2 .... 11=CellVoltage 6, 12=Revolution, 13=Altitude, 14=Climb, 15=Climb3, 16=FuelLevel, 17=Voltage 1, 18=Voltage 2, 19=Temperature 1, 20=Temperature 2
		HoTTbinReader.recordSetEAM = null; // 0=RXSQ, 1=Voltage, 2=Current, 3=Capacity, 4=Power, 5=Balance, 6=CellVoltage 1, 7=CellVoltage 2 .... 19=CellVoltage 14, 20=Height, 21=Climb 1, 22=Climb 3, 23=Voltage 1, 24=Voltage 2, 25=Temperature 1, 26=Temperature 2, 27=Revolution
		HoTTbinReader.recordSetVario = null; // 0=RXSQ, 1=Height, 2=Climb 1, 3=Climb 3, 4=Climb 10, 5=VoltageRx, 6=TemperatureRx
		HoTTbinReader.recordSetGPS = null; // 0=RXSQ, 1=Latitude, 2=Longitude, 3=Height, 4=Climb 1, 5=Climb 3, 6=Velocity, 7=DistanceStart, 8=DirectionStart, 9=TripDistance, 10=VoltageRx, 11=TemperatureRx
		HoTTbinReader.recordSetESC = null; // 0=RF_RXSQ, 1=Voltage, 2=Current, 3=Capacity, 4=Power, 5=Revolution, 6=Temperaure
		HoTTbinReader.pointsReceiver = new int[10];
		HoTTbinReader.pointsVario = new int[8];
		HoTTbinReader.pointsVario[2] = 100000;
		HoTTbinReader.pointsGPS = new int[15];
		HoTTbinReader.pointsGAM = new int[26];
		HoTTbinReader.pointsEAM = new int[31];
		HoTTbinReader.pointsChannel = new int[23];
		HoTTbinReader.pointsESC = new int[14];
		HoTTbinReader.timeStep_ms = 0;
		int numberLogChannels = Integer.valueOf(fileInfoHeader.get("LOG NOB CHANNEL"));
		HoTTbinReader.dataBlockSize = 66 + numberLogChannels * 2;
		HoTTbinReader.buf = new byte[HoTTbinReader.dataBlockSize];
		int logTimeStep = 1000/Integer.valueOf(fileInfoHeader.get("COUNTER").split("/")[1].split(GDE.STRING_BLANK)[0]);
		PackageLossDeque reverseChannelPackageLossCounter = new PackageLossDeque(logTimeStep);
		HoTTbinReader.isJustParsed = false;
		HoTTbinReader.isTextModusSignaled = false;
		int countPackageLoss = 0;
		int logDataOffset = Integer.valueOf(fileInfoHeader.get("LOG DATA OFFSET"));
		long numberDatablocks = (fileSize - logDataOffset) / HoTTbinReader.dataBlockSize;
		long startTimeStamp_ms = HoTTbinReader.getStartTimeStamp(fileInfoHeader.get("LOG START TIME"), HoTTbinReader.getStartTimeStamp(file.getName(), file.lastModified(), numberDatablocks));
		String date = StringHelper.getDate();
		String dateTime = new SimpleDateFormat("yyyy-MM-dd, HH:mm:ss").format(startTimeStamp_ms); //$NON-NLS-1$
		RecordSet tmpRecordSet;
		MenuToolBar menuToolBar = HoTTbinReader.application.getMenuToolBar();
		int progressIndicator = (int) (numberDatablocks / 30);
		GDE.getUiNotification().setProgress(0);

		try {
			HoTTbinReader.recordSets.clear();
			// receiver data are always contained
			// check if recordSetReceiver initialized, transmitter and receiver
			// data always present, but not in the same data rate and signals
			channel = HoTTbinReader.channels.get(1);
			channel.setFileDescription(HoTTbinReader.application.isObjectoriented()
					? date + GDE.STRING_BLANK + HoTTbinReader.application.getObjectKey() : date);
			recordSetName = recordSetNumber + GDE.STRING_RIGHT_PARENTHESIS_BLANK + HoTTAdapter.Sensor.RECEIVER.value() + recordSetNameExtend;
			HoTTbinReader.recordSetReceiver = RecordSet.createRecordSet(recordSetName, device, 1, true, true, true);
			channel.put(recordSetName, HoTTbinReader.recordSetReceiver);
			HoTTbinReader.recordSets.put(HoTTAdapter.Sensor.RECEIVER.value(), HoTTbinReader.recordSetReceiver);
			tmpRecordSet = channel.get(recordSetName);
			tmpRecordSet.setRecordSetDescription(device.getName() + GDE.STRING_MESSAGE_CONCAT + Messages.getString(MessageIds.GDE_MSGT0129) + dateTime);
			tmpRecordSet.setStartTimeStamp(startTimeStamp_ms);
			if (HoTTbinReader.application.getMenuToolBar() != null) {
				channel.applyTemplate(recordSetName, false);
			}
			// recordSetReceiver initialized and ready to add data
			// channel data are always contained
			if (pickerParameters.isChannelsChannelEnabled) {
				// check if recordSetChannel initialized, transmitter and
				// receiver data always present, but not in the same data rate
				// and signals
				channel = HoTTbinReader.channels.get(6);
				channel.setFileDescription(HoTTbinReader.application.isObjectoriented()
						? date + GDE.STRING_BLANK + HoTTbinReader.application.getObjectKey() : date);
				recordSetName = recordSetNumber + GDE.STRING_RIGHT_PARENTHESIS_BLANK + HoTTAdapter.Sensor.CHANNEL.value() + recordSetNameExtend;
				HoTTbinReader.recordSetChannel = RecordSet.createRecordSet(recordSetName, device, 6, true, true, true);
				channel.put(recordSetName, HoTTbinReader.recordSetChannel);
				HoTTbinReader.recordSets.put(HoTTAdapter.Sensor.CHANNEL.value(), HoTTbinReader.recordSetChannel);
				tmpRecordSet = channel.get(recordSetName);
				tmpRecordSet.setRecordSetDescription(device.getName() + GDE.STRING_MESSAGE_CONCAT + Messages.getString(MessageIds.GDE_MSGT0129) + dateTime);
				tmpRecordSet.setStartTimeStamp(startTimeStamp_ms);
				if (HoTTbinReader.application.getMenuToolBar() != null) {
					channel.applyTemplate(recordSetName, false);
				}
				// recordSetChannel initialized and ready to add data
			}

			// read all the data blocks from the file and parse
			data_in.skip(logDataOffset);
			for (int i = 0; i < numberDatablocks; i++) {
				data_in.read(HoTTbinReader.buf);
				if (log.isLoggable(Level.FINE)) {
					log.logp(Level.FINE, HoTTbinReader.$CLASS_NAME, $METHOD_NAME, StringHelper.byte2Hex4CharString(HoTTbinReader.buf, HoTTbinReader.buf.length));
				}

				//if (!pickerParameters.isFilterTextModus || (HoTTbinReader.buf[6] & 0x01) == 0) { // switch into text modus
				if (HoTTbinReader.buf[8] != 0 && HoTTbinReader.buf[9] != 0) { //buf 8, 9, tx,rx, rx sensitivity data
					if (HoTTbinReader.buf[24] != 0x1F) {//rx sensitivity data
						if (log.isLoggable(Level.FINE)) {
							log.log(Level.FINE, String.format("Sensor %02X", HoTTbinReader.buf[26]));
						}
					}

						reverseChannelPackageLossCounter.add(1);
						HoTTbinReader.pointsReceiver[0] = reverseChannelPackageLossCounter.getPercentage() * 1000;
						// create and fill sensor specific data record sets
						if (log.isLoggable(Level.FINEST)) {
							log.logp(Level.FINEST, HoTTbinReader.$CLASS_NAME, $METHOD_NAME,
									StringHelper.byte2Hex2CharString(new byte[] { HoTTbinReader.buf[7] }, 1) + GDE.STRING_MESSAGE_CONCAT + StringHelper.printBinary(HoTTbinReader.buf[7], false));
						}

						// fill receiver data
						if (HoTTbinReader.buf[24] != 0x1F) { //receiver sensitive data
							parseReceiver(HoTTbinReader.buf, HoTTbinReader.pointsReceiver);
							HoTTbinReader.recordSetReceiver.addPoints(HoTTbinReader.pointsReceiver, HoTTbinReader.timeStep_ms);
						}
						if (pickerParameters.isChannelsChannelEnabled) {
							// 0=FreCh, 1=Tx, 2=Rx, 3=Ch 1, 4=Ch 2 .. 18=Ch 16 19=PowerOff 20=BattLow 21=Reset 22=Warning
							parseChannel(HoTTbinReader.buf, HoTTbinReader.pointsChannel, numberLogChannels);
							HoTTbinReader.recordSetChannel.addPoints(HoTTbinReader.pointsChannel, HoTTbinReader.timeStep_ms);
						}

						switch ((byte) (HoTTbinReader.buf[26] & 0xFF)) { //actual sensor
						case HoTTAdapter.ANSWER_SENSOR_VARIO_19200:
								// check if recordSetVario initialized, transmitter and receiver data always
								// present, but not in the same data rate as signals
								if (HoTTbinReader.recordSetVario == null) {
									channel = HoTTbinReader.channels.get(2);
									channel.setFileDescription(HoTTbinReader.application.isObjectoriented()
											? date + GDE.STRING_BLANK + HoTTbinReader.application.getObjectKey() : date);
									recordSetName = recordSetNumber + GDE.STRING_RIGHT_PARENTHESIS_BLANK + HoTTAdapter.Sensor.VARIO.value() + recordSetNameExtend;
									HoTTbinReader.recordSetVario = RecordSet.createRecordSet(recordSetName, device, 2, true, true, true);
									channel.put(recordSetName, HoTTbinReader.recordSetVario);
									HoTTbinReader.recordSets.put(HoTTAdapter.Sensor.VARIO.value(), HoTTbinReader.recordSetVario);
									tmpRecordSet = channel.get(recordSetName);
									tmpRecordSet.setRecordSetDescription(device.getName() + GDE.STRING_MESSAGE_CONCAT + Messages.getString(MessageIds.GDE_MSGT0129) + dateTime);
									tmpRecordSet.setStartTimeStamp(startTimeStamp_ms);
									if (HoTTbinReader.application.getMenuToolBar() != null) {
										channel.applyTemplate(recordSetName, false);
									}
								}
								// recordSetVario initialized and ready to add data
								parseVario(HoTTbinReader.buf, HoTTbinReader.pointsVario, false);
								HoTTbinReader.recordSetVario.addPoints(HoTTbinReader.pointsVario, HoTTbinReader.timeStep_ms);
								break;

						case HoTTAdapter.ANSWER_SENSOR_GPS_19200:
								// check if recordSetReceiver initialized, transmitter and receiver
								// data always present, but not in the same data rate as signals
								if (HoTTbinReader.recordSetGPS == null) {
									channel = HoTTbinReader.channels.get(3);
									channel.setFileDescription(HoTTbinReader.application.isObjectoriented()
											? date + GDE.STRING_BLANK + HoTTbinReader.application.getObjectKey() : date);
									recordSetName = recordSetNumber + GDE.STRING_RIGHT_PARENTHESIS_BLANK + HoTTAdapter.Sensor.GPS.value() + recordSetNameExtend;
									HoTTbinReader.recordSetGPS = RecordSet.createRecordSet(recordSetName, device, 3, true, true, true);
									channel.put(recordSetName, HoTTbinReader.recordSetGPS);
									HoTTbinReader.recordSets.put(HoTTAdapter.Sensor.GPS.value(), HoTTbinReader.recordSetGPS);
									tmpRecordSet = channel.get(recordSetName);
									tmpRecordSet.setRecordSetDescription(device.getName() + GDE.STRING_MESSAGE_CONCAT + Messages.getString(MessageIds.GDE_MSGT0129) + dateTime);
									tmpRecordSet.setStartTimeStamp(startTimeStamp_ms);
									if (HoTTbinReader.application.getMenuToolBar() != null) {
										channel.applyTemplate(recordSetName, false);
									}
								}
								// recordSetGPS initialized and ready to add data
								parseGPS(HoTTbinReader.buf, HoTTbinReader.pointsGPS, false);
								HoTTbinReader.recordSetGPS.addPoints(HoTTbinReader.pointsGPS, HoTTbinReader.timeStep_ms);
								break;

						case HoTTAdapter.ANSWER_SENSOR_GENERAL_19200:
								// check if recordSetGeneral initialized, transmitter and receiver
								// data always present, but not in the same data rate as signals
								if (HoTTbinReader.recordSetGAM == null) {
									channel = HoTTbinReader.channels.get(4);
									channel.setFileDescription(HoTTbinReader.application.isObjectoriented()
											? date + GDE.STRING_BLANK + HoTTbinReader.application.getObjectKey() : date);
									recordSetName = recordSetNumber + GDE.STRING_RIGHT_PARENTHESIS_BLANK + HoTTAdapter.Sensor.GAM.value() + recordSetNameExtend;
									HoTTbinReader.recordSetGAM = RecordSet.createRecordSet(recordSetName, device, 4, true, true, true);
									channel.put(recordSetName, HoTTbinReader.recordSetGAM);
									HoTTbinReader.recordSets.put(HoTTAdapter.Sensor.GAM.value(), HoTTbinReader.recordSetGAM);
									tmpRecordSet = channel.get(recordSetName);
									tmpRecordSet.setRecordSetDescription(device.getName() + GDE.STRING_MESSAGE_CONCAT + Messages.getString(MessageIds.GDE_MSGT0129) + dateTime);
									tmpRecordSet.setStartTimeStamp(startTimeStamp_ms);
									if (HoTTbinReader.application.getMenuToolBar() != null) {
										channel.applyTemplate(recordSetName, false);
									}
								}
								// recordSetGeneral initialized and ready to add data
								parseGAM(HoTTbinReader.buf, HoTTbinReader.pointsGAM, HoTTbinReader.recordSetGAM, false);
								HoTTbinReader.recordSetGAM.addPoints(HoTTbinReader.pointsGAM, HoTTbinReader.timeStep_ms);
								break;

						case HoTTAdapter.ANSWER_SENSOR_ELECTRIC_19200:
								// check if recordSetGeneral initialized, transmitter and receiver
								// data always present, but not in the same data rate as signals
								if (HoTTbinReader.recordSetEAM == null) {
									channel = HoTTbinReader.channels.get(5);
									channel.setFileDescription(HoTTbinReader.application.isObjectoriented()
											? date + GDE.STRING_BLANK + HoTTbinReader.application.getObjectKey() : date);
									recordSetName = recordSetNumber + GDE.STRING_RIGHT_PARENTHESIS_BLANK + HoTTAdapter.Sensor.EAM.value() + recordSetNameExtend;
									HoTTbinReader.recordSetEAM = RecordSet.createRecordSet(recordSetName, device, 5, true, true, true);
									channel.put(recordSetName, HoTTbinReader.recordSetEAM);
									HoTTbinReader.recordSets.put(HoTTAdapter.Sensor.EAM.value(), HoTTbinReader.recordSetEAM);
									tmpRecordSet = channel.get(recordSetName);
									tmpRecordSet.setRecordSetDescription(device.getName() + GDE.STRING_MESSAGE_CONCAT + Messages.getString(MessageIds.GDE_MSGT0129) + dateTime);
									tmpRecordSet.setStartTimeStamp(startTimeStamp_ms);
									if (HoTTbinReader.application.getMenuToolBar() != null) {
										channel.applyTemplate(recordSetName, false);
									}
								}
								// recordSetElectric initialized and ready to add data
								parseEAM(HoTTbinReader.buf, HoTTbinReader.pointsEAM, HoTTbinReader.recordSetEAM, false);
								HoTTbinReader.recordSetEAM.addPoints(HoTTbinReader.pointsEAM, HoTTbinReader.timeStep_ms);
								break;

						case HoTTAdapter.ANSWER_SENSOR_MOTOR_DRIVER_19200:
								// check if recordSetGeneral initialized, transmitter and receiver
								// data always present, but not in the same data rate as signals
								if (HoTTbinReader.recordSetESC == null) {
									channel = HoTTbinReader.channels.get(7);
									channel.setFileDescription(HoTTbinReader.application.isObjectoriented()
											? date + GDE.STRING_BLANK + HoTTbinReader.application.getObjectKey() : date);
									recordSetName = recordSetNumber + GDE.STRING_RIGHT_PARENTHESIS_BLANK + HoTTAdapter.Sensor.ESC.value() + recordSetNameExtend;
									HoTTbinReader.recordSetESC = RecordSet.createRecordSet(recordSetName, device, 7, true, true, true);
									channel.put(recordSetName, HoTTbinReader.recordSetESC);
									HoTTbinReader.recordSets.put(HoTTAdapter.Sensor.ESC.value(), HoTTbinReader.recordSetESC);
									tmpRecordSet = channel.get(recordSetName);
									tmpRecordSet.setRecordSetDescription(device.getName() + GDE.STRING_MESSAGE_CONCAT + Messages.getString(MessageIds.GDE_MSGT0129) + dateTime);
									tmpRecordSet.setStartTimeStamp(startTimeStamp_ms);
									if (HoTTbinReader.application.getMenuToolBar() != null) {
										channel.applyTemplate(recordSetName, false);
									}
								}
								// recordSetElectric initialized and ready to add data
								parseESC(HoTTbinReader.buf, HoTTbinReader.pointsESC, HoTTbinReader.recordSetESC);
								HoTTbinReader.recordSetESC.addPoints(HoTTbinReader.pointsESC, HoTTbinReader.timeStep_ms);
								break;
						}

						HoTTbinReader.timeStep_ms += logTimeStep;// add time step from log record given in info header

						if (i % progressIndicator == 0)
							GDE.getUiNotification().setProgress((int) (i * 100 / numberDatablocks));

						if (HoTTbinReader.isJustParsed && countPackageLoss > 0) {
							HoTTbinReader.lostPackages.add(countPackageLoss);
							countPackageLoss = 0;
							HoTTbinReader.isJustParsed = false;
						}
					}
					else { // tx,rx == 0
						if (HoTTlogReader.log.isLoggable(Level.FINE)) HoTTlogReader.log.log(Level.FINE, "-->> Found tx=rx=0 dBm");

						reverseChannelPackageLossCounter.add(0);
						HoTTbinReader.pointsReceiver[0] = reverseChannelPackageLossCounter.getPercentage() * 1000;
						++countPackageLoss; // add up lost packages in telemetrie data

						if (pickerParameters.isChannelsChannelEnabled) {
							parseChannel(HoTTbinReader.buf, HoTTbinReader.pointsChannel, numberLogChannels);
							HoTTbinReader.recordSetChannel.addPoints(HoTTbinReader.pointsChannel, HoTTbinReader.timeStep_ms);
						}

						HoTTbinReader.timeStep_ms += logTimeStep;
					}
				//} //isTextModus
				//else if (!HoTTbinReader.isTextModusSignaled) {
				//	HoTTbinReader.isTextModusSignaled = true;
				//	HoTTbinReader.application.openMessageDialogAsync(Messages.getString(gde.device.graupner.hott.MessageIds.GDE_MSGW2404));
				//}
			}
			// if (HoTTbinReader.oldProtocolCount > 2) {
			// application.openMessageDialogAsync(Messages.getString(gde.device.graupner.hott.MessageIds.GDE_MSGW2405,
			// new Object[] { HoTTbinReader.oldProtocolCount }));
			// }
			String packageLossPercentage = HoTTbinReader.recordSetReceiver.getRecordDataSize(true) > 0
					? String.format("%.1f", (countPackageLoss / HoTTbinReader.recordSetReceiver.getTime_ms(HoTTbinReader.recordSetReceiver.getRecordDataSize(true) - 1) * 1000)) : "100";
			HoTTbinReader.recordSetReceiver.setRecordSetDescription(tmpRecordSet.getRecordSetDescription()
					+ Messages.getString(gde.device.graupner.hott.MessageIds.GDE_MSGI2404, new Object[] { countPackageLoss, packageLossPercentage, HoTTbinReader.lostPackages.getStatistics() })
					+ Sensor.getSetAsSignature(HoTTbinReader.detectedSensors));
			HoTTbinReader.log.logp(Level.WARNING, HoTTbinReader.$CLASS_NAME, $METHOD_NAME, "skipped number receiver data due to package loss = " + countPackageLoss); //$NON-NLS-1$
			HoTTbinReader.log.logp(Level.TIME, HoTTbinReader.$CLASS_NAME, $METHOD_NAME, "read time = " //$NON-NLS-1$
					+ StringHelper.getFormatedTime("mm:ss:SSS", (System.nanoTime() / 1000000 - startTime))); //$NON-NLS-1$

			if (GDE.isWithUi()) {
				for (RecordSet recordSet : HoTTbinReader.recordSets.values()) {
					device.makeInActiveDisplayable(recordSet);
					device.updateVisibilityStatus(recordSet, true);

					// write filename after import to record description
					recordSet.descriptionAppendFilename(file.getName());
				}

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
	 * print values evaluated by parsing sensor bytes
	 * @param buffer
	 * @param values
	 * @param endIndex
	 */
	protected static void printSensorValues(byte[] buffer, int[] values, int endIndex) {
		log.log(Level.FINER, StringHelper.byte2Hex2CharString(buffer, 26, 38));
		StringBuilder sb = new StringBuilder().append(String.format("Sensor = 0x%X", buffer[26]));
		sb.append(String.format(" %d", values[endIndex-1] / 1000));
		for (int i = 0; i < endIndex; i++) {
			sb.append(String.format(" %6.3f", values[i] / 1000.0));
		}
		log.log(Level.FINER, sb.toString());
	}

	/**
	 * parse the buffered data to receiver values
	 * @param _buf
	 */
	protected static boolean parseReceiver(byte[] _buf, int[] values) {
		//log.log(Level.OFF, StringHelper.byte2Hex2CharString(_buf, 12, 10));
		//0=RX-TX-VPacks, 1=RXSQ, 2=Strength, 3=VPacks, 4=Tx, 5=Rx, 6=VoltageRx, 7=TemperatureRx 8=VoltageRxMin 9=EventRx
		//data bytes: 12=RX-S-STR 13=voltageRx 14=TemperatureRx 15=Rx-DBM 16=RX-S-QUA 17=voltageRx_min 18=VPack_low_byte 19=VPack_high_byte 20=Tx-DBM 21=?
		HoTTbinReader.tmpVoltageRx = (_buf[13] & 0xFF);
		HoTTbinReader.tmpTemperatureRx = (_buf[2] & 0xFF);
		values[1] = (_buf[16] & 0xFF) * 1000;
		values[3] = DataParser.parse2Short(_buf, 18) * 1000;
		values[2] = (convertRxDbm2Strength(_buf[9] & 0xFF)) * 1000;
		values[4] = _buf[8] * -1000;
		values[5] = _buf[9] * -1000;
		values[6] = (_buf[13] & 0xFF) * 1000;
		values[7] = ((_buf[14] & 0xFF) - 20) * 1000;
		values[8] = (_buf[17] & 0xFF) * 1000;
		if ((_buf[25] & 0x40) > 0 || (_buf[25] & 0x20) > 0 && HoTTbinReader.tmpTemperatureRx >= 70) //T = 70 - 20 = 50 lowest temperature warning
			values[9] = (_buf[25] & 0x60) * 1000; //warning V,T only
		else
			values[9] = 0;

		if (log.isLoggable(Level.FINER)) {
			//data bytes: 8=TXdBm(-D), 9=RXdBm(-D)
			StringBuilder sb = new StringBuilder().append(String.format("Tx-dbm = -%d Rx-dbm = -%d", _buf[8], _buf[9]));
			for (int i = 0; i < 10; i++) {
				sb.append(String.format(" %6.3f", values[i] / 1000.0));
			}
			log.log(Level.FINER, sb.toString());
		}
		return true;
	}

	/**
	 * parse the buffered data to channel values
	 * @param _buf
	 */
	protected static void parseChannel(byte[] _buf,final int[] values, int numberUsedChannels) {
		//0=FreCh, 1=Tx, 2=Rx, 3=Ch 1, 4=Ch 2 .. 18=Ch 16 19=PowerOff 20=BattLow 21=Reset 22=Warning
		values[0] = _buf[7] * -1000;
		values[1] = _buf[8] * -1000;
		values[2] = _buf[9] * -1000;

		for (int i = 0,j = 0; i < numberUsedChannels && i < 16; i++,j+=2) {
			values[i + 3] = (DataParser.parse2UnsignedShort(_buf, (66 + j)) / 2) * 1000;
		}
		//Ph(D)[4], Evt1(H)[5], Evt2(D)[6], Fch(D)[7], TXdBm(-D)[8], RXdBm(-D)[9], RfRcvRatio(D)[10], TrnRcvRatio(D)[11]
		//STATUS : Ph(D)[4], Evt1(H)[5], Evt2(D)[6], Fch(D)[7], TXdBm(-D)[8], RXdBm(-D)[9], RfRcvRatio(D)[10], TrnRcvRatio(D)[11]
		//S.INFOR : DEV(D)[22], CH(D)[23], SID(H)[24], WARN(H)[25]
		//remove evaluation of transmitter event and warning to avoid end user confusion
		//values[19] = (_buf[5] & 0x01) * 100000; 	//power off
		//values[20] = (_buf[1] & 0x01) * 50000;			//batt low
		//values[21] = (_buf[5] & 0x04) * 25000;		//reset
		//if (_buf[25] > 0) {
		//	values[22] = (_buf[25] & 0x7F) * 1000;		//warning
		//}
		//else
		//	values[22] = 0;
		
//		StringBuilder sb = new StringBuilder();
//		sb.append(StringHelper.getFormatedTime("HH:mm:ss:SSS", HoTTlogReader.timeStep_ms - GDE.ONE_HOUR_MS)).append(GDE.STRING_BLANK);			
//		for (int i = 0; i < _buf.length; i++) {
//			sb.append(StringHelper.printBinary(_buf[i], false)).append(GDE.STRING_BLANK);			
//		}
//		log.log(Level.OFF, sb.toString());			
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
		values[0] = (_buf[16] & 0xFF) * 1000;
		HoTTbinReader.tmpHeight = isHoTTAdapter2 ? DataParser.parse2Short(_buf, 28) - 500 : DataParser.parse2Short(_buf, 28);
		values[1] = HoTTbinReader.tmpHeight * 1000;
		//pointsVarioMax = DataParser.parse2Short(buf1, 30) * 1000;
		//pointsVarioMin = DataParser.parse2Short(buf1, 32) * 1000;
		values[2] = (DataParser.parse2UnsignedShort(_buf, 34) - 30000) * 10;
		HoTTbinReader.tmpClimb10 = isHoTTAdapter2 ? (DataParser.parse2UnsignedShort(_buf , 38) - 30000) * 10 : DataParser.parse2UnsignedShort(_buf , 38) * 1000;
		values[3] = isHoTTAdapter2 ? (DataParser.parse2UnsignedShort(_buf, 36) - 30000) * 10 : DataParser.parse2UnsignedShort(_buf, 36) * 1000;
		values[4] = HoTTbinReader.tmpClimb10;
		values[5] = (_buf[13] & 0xFF) * 1000;				//voltageRx
		values[6] = ((_buf[14] & 0xFF) - 20) * 1000;//temperaturRx
		values[7] = (_buf[27] & 0x3F) * 1000; 			//inverse event

		if (log.isLoggable(Level.FINER)) {
			printSensorValues(_buf, values, 8);
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
		values[0] = (_buf[16] & 0xFF) * 1000;
		HoTTbinReader.tmpHeight = isHoTTAdapter2 ? DataParser.parse2Short(_buf, 44) - 500 : DataParser.parse2Short(_buf, 44);
		HoTTbinReader.tmpClimb1 = isHoTTAdapter2 ? (DataParser.parse2UnsignedShort(_buf, 46) - 30000) : DataParser.parse2UnsignedShort(_buf, 46);
		HoTTbinReader.tmpClimb3 = isHoTTAdapter2 ? (_buf[48] & 0xFF) - 120 : (_buf[48] & 0xFF);
		HoTTbinReader.tmpVelocity = DataParser.parse2Short(_buf, 30) * 1000;
		values[6] = HoTTbinReader.tmpVelocity;
		HoTTbinReader.tmpLatitude = DataParser.parse2Short(_buf, 33) * 10000 + DataParser.parse2Short(_buf, 35);
		values[1] = HoTTbinReader.tmpLatitude;
		HoTTbinReader.tmpLongitude = DataParser.parse2Short(_buf, 38) * 10000 + DataParser.parse2Short(_buf, 40);
		values[2] = HoTTbinReader.tmpLongitude;
		values[3] = HoTTbinReader.tmpHeight * 1000;		//altitude
		values[4] = HoTTbinReader.tmpClimb1 * 10;			//climb1
		values[5] = HoTTbinReader.tmpClimb3 * 1000;		//climb3
		values[7] = DataParser.parse2Short(_buf, 42) * 1000;
		values[8] = (_buf[51] & 0xFF) * 1000;
		values[9] = 0; 																//trip length
		values[10] = (_buf[13] & 0xFF) * 1000;				//voltageRx
		values[11] = ((_buf[14] & 0xFF) - 20) * 1000;	//temperaturRx
		values[12] = (_buf[49] & 0xFF) * 1000;
		try {
			values[13] = Integer.valueOf(String.format("%c", _buf[50])) * 1000;
		}
		catch (NumberFormatException e1) {
			//ignore;
		}
		values[14] = (_buf[27] & 0x0F) * 1000; //inverse event

		if (log.isLoggable(Level.FINER)) {
			printSensorValues(_buf, values, 14);
		}
		return true;
	}

	/**
	 * parse the buffered data GAM values
	 * @param _buf
	 */
	protected static boolean parseGAM(byte[] _buf, int[] values, RecordSet recordSetGAM, boolean isHoTTAdapter2) {
		// 0=RF_RXSQ, 1=Voltage, 2=Current, 3=Capacity, 4=Power, 5=Balance,
		// 6=CellVoltage 1, 7=CellVoltage 2 .... 11=CellVoltage 6,
		// 12=Revolution, 13=Height, 14=Climb, 15=Climb3, 16=FuelLevel,
		// 17=Voltage 1, 18=Voltage 2, 19=Temperature 1, 20=Temperature 2
		// 21=Speed, 22=LowestCellVoltage, 23=LowestCellNumber, 24=Pressure, 24=Event
		values[0] = (_buf[16] & 0xFF) * 1000;
		HoTTbinReader.tmpHeight = isHoTTAdapter2 ? DataParser.parse2Short(_buf, 46) - 500 : DataParser.parse2Short(_buf, 46);
		HoTTbinReader.tmpClimb3 = isHoTTAdapter2 ? (_buf[50] & 0xFF) - 120 : (_buf[50] & 0xFF);
		HoTTbinReader.tmpVoltage1 = DataParser.parse2Short(_buf, 35);
		HoTTbinReader.tmpVoltage2 = DataParser.parse2Short(_buf, 37);
		HoTTbinReader.tmpCapacity = DataParser.parse2Short(_buf, 55);
		//sensor byte: 26=sensor byte
		//27,28=InverseBits 29=cell1, 30=cell2 31=cell3 32=cell4 33=cell5 34=cell6 35,36=voltage1 37,38=voltage2 39=temperature1 40=temperature2
		//41=? 42,43=fuel 44,45=rpm 46,47=altitude 48,49=climb1 50=climb3 51,52=current 53,54=voltage 55,56=capacity 57,58=speed
		//59=cellVoltage_min 60=#cellVoltage_min 61,62=rpm2 63=#error 64=pressure 65=version
		int maxVotage = Integer.MIN_VALUE;
		int minVotage = Integer.MAX_VALUE;
		values[1] = DataParser.parse2Short(_buf, 53) * 1000;
		values[2] = DataParser.parse2Short(_buf, 51) * 1000;
		values[3] = HoTTbinReader.tmpCapacity * 1000;
		values[4] = Double.valueOf(values[1] / 1000.0 * values[2]).intValue();
		for (int j = 0; j < 6; j++) { //cell voltages
			values[j + 6] = (_buf[j + 29] & 0xFF) * 1000;
			if (values[j + 5] > 0) {
				maxVotage = values[j + 6] > maxVotage ? values[j + 6] : maxVotage;
				minVotage = values[j + 6] < minVotage ? values[j + 6] : minVotage;
			}
		}
		values[5] = maxVotage != Integer.MIN_VALUE && minVotage != Integer.MAX_VALUE ? (maxVotage - minVotage) * 10 : 0; //balance
		values[12] = DataParser.parse2Short(_buf, 44) * 1000;
		values[13] = HoTTbinReader.tmpHeight * 1000;
		values[14] = isHoTTAdapter2 ? (DataParser.parse2UnsignedShort(_buf, 48) - 30000) * 10 : (DataParser.parse2UnsignedShort(_buf, 48) * 1000);
		values[15] = HoTTbinReader.tmpClimb3 * 1000;
		values[16] = DataParser.parse2Short(_buf, 42) * 1000;
		values[17] = HoTTbinReader.tmpVoltage1 * 100;
		values[18] = HoTTbinReader.tmpVoltage2 * 100;
		values[19] = ((_buf[39] & 0xFF) - 20) * 1000;
		values[20] = ((_buf[40] & 0xFF) - 20) * 1000;
		values[21] = DataParser.parse2Short(_buf, 57) * 1000; //Speed [km/h
		values[22] = (_buf[59] & 0xFF) * 1000; //lowest cell voltage 124 = 2.48 V
		values[23] = (_buf[60] & 0xFF) * 1000; //cell number lowest cell voltage
		values[24] = (_buf[64] & 0xFF) * 1000; //Pressure
		values[25] = ((_buf[27] & 0xFF) + ((_buf[28] & 0x7F) << 8)) * 1000; //inverse event

		if (log.isLoggable(Level.FINER)) {
			printSensorValues(_buf, values, 26);
		}
		return true;
	}

	/**
	 * parse the buffered data to EAM values
	 * @param _buf
	 * @throws DataInconsitsentException
	 */
	protected static boolean parseEAM(byte[] _buf, int[] values, RecordSet recordSetEAM, boolean isHoTTAdapter2) throws DataInconsitsentException {
		//0=RXSQ, 1=Voltage, 2=Current, 3=Capacity, 4=Power, 5=Balance, 6=CellVoltage 1, 7=CellVoltage 2 .... 19=CellVoltage 14,
		//20=Height, 21=Climb 1, 22=Climb 3, 23=Voltage 1, 24=Voltage 2, 25=Temperature 1, 26=Temperature 2 27=RPM 28=MotorTime 29=Speed 30=Event
		//sensor byte: 26=sensor byte
		//27,28=InverseBits 29=cell1, 30=cell2 31=cell3 32=cell4 33=cell5 34=cell6 35=cell7 36=cell8 37=cell9 38=cell10 39=cell11 40=cell12 41=cell13 42=cell14
		//43,44=voltage1 45,46=voltage2 47=temperature1 48=temperature2 49,50=altitude 51,52=current 53,54=voltage 55,56=capacity 57,58=climb1 59=climb3
		//60,61=rpm 62,63=runtime>3A 64,65=speed
		values[0] = (_buf[16] & 0xFF) * 1000;
		HoTTbinReader.tmpHeight = isHoTTAdapter2 ? DataParser.parse2Short(_buf, 49) - 500 : DataParser.parse2Short(_buf, 49);
		HoTTbinReader.tmpClimb3 = isHoTTAdapter2 ? (_buf[59] & 0xFF) - 120 : (_buf[59] & 0xFF);
		HoTTbinReader.tmpVoltage1 = DataParser.parse2Short(_buf, 43);
		HoTTbinReader.tmpVoltage2 = DataParser.parse2Short(_buf, 45);
		HoTTbinReader.tmpCapacity = DataParser.parse2Short(_buf, 55);
		int maxVotage = Integer.MIN_VALUE;
		int minVotage = Integer.MAX_VALUE;
		values[1] = DataParser.parse2Short(_buf, 53) * 1000;
		values[2] = DataParser.parse2Short(_buf, 51) * 1000;
		values[3] = HoTTbinReader.tmpCapacity * 1000;
		values[4] = Double.valueOf(values[1] / 1000.0 * values[2]).intValue(); // power U*I [W];
		for (int j = 0; j < 14; j++) { //cell voltages
			values[j + 6] = (_buf[j + 29] & 0xFF) * 1000;
			if (values[j + 6] > 0) {
				maxVotage = values[j + 6] > maxVotage ? values[j + 6] : maxVotage;
				minVotage = values[j + 6] < minVotage ? values[j + 6] : minVotage;
			}
		}
		values[5] = maxVotage != Integer.MIN_VALUE && minVotage != Integer.MAX_VALUE ? (maxVotage - minVotage) * 10 : 0; //balance
		values[20] = HoTTbinReader.tmpHeight * 1000;
		values[21] = isHoTTAdapter2 ? (DataParser.parse2UnsignedShort(_buf, 57) - 30000) * 10 : DataParser.parse2UnsignedShort(_buf, 57) * 1000;
		values[22] = HoTTbinReader.tmpClimb3 * 1000;
		values[23] = HoTTbinReader.tmpVoltage1 * 100;
		values[24] = HoTTbinReader.tmpVoltage2 * 100;
		values[25] = ((_buf[47] & 0xFF) - 20) * 1000;
		values[26] = ((_buf[48] & 0xFF) - 20) * 1000;
		values[27] = DataParser.parse2Short(_buf, 60) * 1000;
		values[28] = ((_buf[62] & 0xFF) * 60 + (_buf[63] & 0xFF)) * 1000; // motor time
		values[29] = DataParser.parse2Short(_buf, 64) * 1000; // speed
		values[30] = ((_buf[27] & 0xFF) + ((_buf[28] & 0x7F) << 8)) * 1000; //inverse event

		if (log.isLoggable(Level.FINER)) {
			printSensorValues(_buf, values, 31);
		}
		return true;
	}

	/**
	 * parse the buffered data to ESC values
	 * @param _buf
	 * @param values point array
	 * @throws DataInconsitsentException
	 */
	protected static boolean parseESC(byte[] _buf, int[] values, RecordSet recordSetESC) throws DataInconsitsentException {
		//0=RF_RXSQ, 1=Voltage, 2=Current, 3=Capacity, 4=Power, 5=Revolution, 6=Temperature1, 7=Temperature2
		//8=Voltage_min, 9=Current_max, 10=Revolution_max, 11=Temperature1_max, 12=Temperature2_max 13=Event
		//sensor byte: 26=sensor byte
		//27,28=InverseBits 29,30=voltageIn 31,32=voltageIn_min 33,34=capacity 35=temperature1 36=temperature1_max 37,38=current 39,40=current_max
		//41,42=rpm 43,44=rpm_max 45=temperature2 46=temperature2_max
		values[0] = (_buf[16] & 0xFF) * 1000;
		HoTTbinReader.tmpVoltage = DataParser.parse2Short(_buf, 29);
		HoTTbinReader.tmpCurrent = DataParser.parse2Short(_buf, 37);
		HoTTbinReader.tmpCapacity = DataParser.parse2Short(_buf, 33);
		HoTTbinReader.tmpRevolution = DataParser.parse2Short(_buf, 41);
		HoTTbinReader.tmpTemperatureFet = _buf[35] - 20;
		//73=VoltageM, 74=CurrentM, 75=CapacityM, 76=PowerM, 77=RevolutionM, 78=TemperatureM 1, 79=TemperatureM 2 80=Voltage_min, 81=Current_max, 82=Revolution_max, 83=Temperature1_max, 84=Temperature2_max 85=Event M
		if (!pickerParameters.isFilterEnabled || HoTTbinReader.tmpVoltage > 0 && HoTTbinReader.tmpVoltage < 1000 && HoTTbinReader.tmpCurrent < 4000 && HoTTbinReader.tmpCurrent > -10
				&& HoTTbinReader.tmpRevolution > -1 && HoTTbinReader.tmpRevolution < 20000
				&& !(values[6] != 0 && values[6] / 1000 - HoTTbinReader.tmpTemperatureFet > 20)) {
			values[1] = HoTTbinReader.tmpVoltage * 1000;
			values[2] = HoTTbinReader.tmpCurrent * 1000;
			if (!pickerParameters.isFilterEnabled || recordSetESC.getRecordDataSize(true) <= 20
					|| (HoTTbinReader.tmpCapacity != 0 && Math.abs(HoTTbinReader.tmpCapacity) <= (values[3] / 1000 + HoTTbinReader.tmpVoltage * HoTTbinReader.tmpCurrent / 2500 + 2))) {
				values[3] = HoTTbinReader.tmpCapacity * 1000;
			}
			else {
				if (HoTTbinReader.tmpCapacity != 0)
					HoTTlogReader2.log.log(Level.WARNING, StringHelper.getFormatedTime("mm:ss.SSS", HoTTbinReader.timeStep_ms) + " - " + HoTTbinReader.tmpCapacity + " - "
						+ (values[3] / 1000) + " + " + (HoTTbinReader.tmpVoltage * HoTTbinReader.tmpCurrent / 2500 + 2));
			}
			values[4] = Double.valueOf(values[1] / 1000.0 * values[2]).intValue();
			values[5] = HoTTbinReader.tmpRevolution * 1000;
			values[6] = HoTTbinReader.tmpTemperatureFet * 1000;
			values[7] = (_buf[45] - 20) * 1000;
			values[8] = DataParser.parse2Short(_buf, 31) * 1000;
			values[9] = DataParser.parse2Short(_buf, 39) * 1000;
			values[10] = DataParser.parse2Short(_buf, 43) * 1000;
			values[11] = (_buf[36] - 20) * 1000;
			values[12] = (_buf[46] - 20) * 1000;
		}
		values[13] = ((_buf[27] & 0xFF) + ((_buf[28] & 0x7F) << 8)) * 1000; //inverse event

		if (log.isLoggable(Level.FINER)) {
			printSensorValues(_buf, values, 14);
		}
		return true;
	}
}
