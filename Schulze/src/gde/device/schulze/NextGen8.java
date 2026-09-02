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
package gde.device.schulze;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.logging.Logger;

import javax.xml.bind.JAXBException;

import gde.GDE;
import gde.data.Channel;
import gde.data.Channels;
import gde.data.Record;
import gde.data.RecordSet;
import gde.device.DeviceConfiguration;
import gde.device.IDevice;
import gde.device.MeasurementPropertyTypes;
import gde.device.MeasurementType;
import gde.device.PropertyType;
import gde.exception.ApplicationConfigurationException;
import gde.exception.DataInconsitsentException;
import gde.exception.SerialPortException;
import gde.log.Level;
import gde.messages.Messages;
import gde.utils.StringHelper;

public class NextGen8 extends BaseCharger {
	final static Logger							log					= Logger.getLogger(NextGen8.class.getName());
	
	protected static double 				capacity = 0.0; 
	protected DataParserNext				convertData;


	public NextGen8(String xmlFileName) throws FileNotFoundException, JAXBException {
		super(xmlFileName);
	}

	public NextGen8(DeviceConfiguration deviceConfig) {
		super(deviceConfig);
	}

	/**
	 * query number of Lithium cells of this charger device
	 * @return
	 */
	public int getNumberOfLithiumCells() {
		return 8;
	}

	/**
	 * load the mapping exist between lov file configuration keys and GDE keys
	 * @param lov2osdMap reference to the map where the key mapping has to be put
	 * @return lov2osdMap same reference as input parameter
	 */
	@Override
	public HashMap<String, String> getLovKeyMappings(HashMap<String, String> lov2osdMap) {
		return lov2osdMap;
	}

	/**
	 * convert record LogView config data to GDE config keys into records section
	 * @param header reference to header data, contain all key value pairs
	 * @param lov2osdMap reference to the map where the key mapping
	 * @param channelNumber 
	 * @return converted configuration data
	 */
	@Override
	public String getConvertedRecordConfigurations(HashMap<String, String> header, HashMap<String, String> lov2osdMap, int channelNumber) {
		return "";
	}

	/**
	 * get LogView data bytes size, as far as known modulo 16 and depends on the bytes received from device 
	 */
	@Override
	public int getLovDataByteSize() {
		return 85;
	}

	/**
	 * convert the device bytes into raw values, no calculation will take place here, see translateValue reverseTranslateValue
	 * inactive or to be calculated data point are filled with 0 and needs to be handles after words
	 * @param points pointer to integer array to be filled with converted data
	 * @param dataBuffer byte array with the data to be converted
	 */
	@Override
	public int[] convertDataBytes(int[] points, byte[] dataBuffer) {		
		int[] startLength = new int[] {0,0};
		byte[] lineBuffer = null;
		if (this.convertData == null)
			this.convertData = new DataParserNext(this, this.getDataBlockTimeUnitFactor(), this.getDataBlockLeader(), this.getDataBlockSeparator().value(), this.getDataBlockCheckSumType(), 14, 0);
				
		try {
			setDataLineStartAndLength(dataBuffer, startLength);
			lineBuffer = new byte[startLength[1]];
			System.arraycopy(dataBuffer, startLength[0], lineBuffer, 0, startLength[1]);
			this.convertData.parse(new String(lineBuffer), 1);
			//0=Voltage 1=Current 2=Capacity 3=Power 4=Temperature 5=balance
			//6=cellVoltage1 7=cellVoltage2 8=cellVoltage3 9=cellVoltage4 10=cellVoltage5 11=cellVoltage6 12=cellVoltage7 13=cellVoltage8
		}
		catch (Exception e) {
			log.log(Level.WARNING, e.getMessage());
		}
		return convertData.getValues();
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
		byte[] convertBuffer = new byte[dataBufferSize];
		int[] points = new int[recordSet.size()];
		String sThreadId = String.format("%06d", Thread.currentThread().threadId()); //$NON-NLS-1$
		double timeStep_h = 1.0 / 3600.0;
		int progressCycle = 0;
		if (doUpdateProgressBar) this.application.setProgress(progressCycle, sThreadId);
		
		for (int i = 0; i < recordDataSize; i++) {
			log.log(Level.FINER, i + " i*dataBufferSize+timeStampBufferSize = " + i*dataBufferSize); //$NON-NLS-1$
			System.arraycopy(dataBuffer, i*dataBufferSize, convertBuffer, 0, dataBufferSize);
			
			//0=Voltage 1=Current 2=Capacity 3=Power 4=Temperature
			points[0] = (((convertBuffer[0]&0xff) << 24) + ((convertBuffer[1]&0xff) << 16) + ((convertBuffer[2]&0xff) << 8) + ((convertBuffer[3]&0xff) << 0));
			points[1] = (((convertBuffer[4]&0xff) << 24) + ((convertBuffer[5]&0xff) << 16) + ((convertBuffer[6]&0xff) << 8) + ((convertBuffer[7]&0xff) << 0));
			if (points[1] != 0) {
				capacity += points[1] * timeStep_h; //capacity = capacity + (timeDelta * current)
				points[2] = Double.valueOf(capacity).intValue();
			} else {
				points[2] = 0;
				capacity = 0.0;
			}
			points[3] = Double.valueOf(points[1] * points[2] / 100.0).intValue(); 							// power U*I [W]
			points[4] = (((convertBuffer[8]&0xff) << 24) + ((convertBuffer[9]&0xff) << 16) + ((convertBuffer[10]&0xff) << 8) + ((convertBuffer[11]&0xff) << 0));

			if (recordSet.getChannelConfigNumber() == 1) {
				int maxVotage = Integer.MIN_VALUE;
				int minVotage = Integer.MAX_VALUE;
				//6=cellVoltage1 7=cellVoltage2 8=cellVoltage3 9=cellVoltage4 10=cellVoltage5 11=cellVoltage6 12=cellVoltage7 13=cellVoltage8
				for (int j=0, k=0; j<this.getNumberOfLithiumCells(); ++j, k+=GDE.SIZE_BYTES_INTEGER) {
					points[j + 6] = (((convertBuffer[k+12]&0xff) << 24) + ((convertBuffer[k+13]&0xff) << 16) + ((convertBuffer[k+14]&0xff) << 8) + ((convertBuffer[k+15]&0xff) << 0));
					if (points[j + 6] > 0) {
						maxVotage = points[j + 6] > maxVotage ? points[j + 6] : maxVotage;
						minVotage = points[j + 6] < minVotage ? points[j + 6] : minVotage;
					}
				}
				//5=balance calculate balance on the fly
				points[5] = maxVotage != Integer.MIN_VALUE && minVotage != Integer.MAX_VALUE ? maxVotage - minVotage : 0;
			}

			recordSet.addPoints(points);
			
			if (doUpdateProgressBar && i % 50 == 0) this.application.setProgress(((++progressCycle*2500)/recordDataSize), sThreadId);
		}
		if (doUpdateProgressBar) this.application.setProgress(100, sThreadId);
		recordSet.syncScaleOfSyncableRecords();
	}

	@Override
	public void addConvertedLovDataBufferAsRawDataPoints(RecordSet recordSet, byte[] dataBuffer, int recordDataSize, boolean doUpdateProgressBar) throws DataInconsitsentException {
		String sThreadId = String.format("%06d", Thread.currentThread().threadId()); //$NON-NLS-1$
		int deviceDataBufferSize = this.getLovDataByteSize();
		int[] points = new int[this.getNumberOfMeasurements(1)];
		int offset = 0;
		int progressCycle = 0;
		int lovDataSize = this.getLovDataByteSize();

		byte[] convertBuffer = new byte[deviceDataBufferSize];

		if (doUpdateProgressBar) this.application.setProgress(progressCycle, sThreadId);

		for (int i = 0; i < recordDataSize; i++) {
			lovDataSize = deviceDataBufferSize/3;
			//prepare convert buffer for conversion
			System.arraycopy(dataBuffer, offset, convertBuffer, 0, deviceDataBufferSize/3);
			for (int j = deviceDataBufferSize/3; j < deviceDataBufferSize; j++) { //start at minimum length of data buffer 
				convertBuffer[j] = dataBuffer[offset+j];
				++lovDataSize;
				if (dataBuffer[offset+j] == 0x0A && dataBuffer[offset+j-1] == 0x0D)
					break;
			}

			recordSet.addPoints(convertDataBytes(points, convertBuffer));
			offset += lovDataSize+8;

			if (doUpdateProgressBar && i % 50 == 0) this.application.setProgress(((++progressCycle * 5000) / recordDataSize), sThreadId);
		}

		if (doUpdateProgressBar) this.application.setProgress(100, sThreadId);
		recordSet.syncScaleOfSyncableRecords();
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
				switch (record.getDataType()) {
				case DATE_TIME:
					dataTableRow[index + 1] = StringHelper.getFormatedTime(record.getUnit(), record.realGet(rowIndex));
					dataTableRow[index + 1] = dataTableRow[index + 1].substring(0, dataTableRow[index + 1].indexOf(GDE.CHAR_COMMA) + 2);
					break;

				default:
					dataTableRow[index + 1] = record.getFormattedTableValue(rowIndex);
					break;
				}
				++index;
			}
		}
		catch (RuntimeException e) {
			log.log(Level.SEVERE, e.getMessage(), e);
		}
		return dataTableRow;
	}

	/**
	 * function to translate measured values from a device to values represented
	 * this function should be over written by device and measurement specific algorithm
	 * @return double of device dependent value
	 */
	public double translateValue(Record record, double value) {
		double factor = record.getFactor(); // != 1 if a unit translation is required
		double offset = record.getOffset(); // != 0 if a unit translation is required
		double reduction = record.getReduction(); // != 0 if a unit translation is required

		PropertyType property = record.getProperty(MeasurementPropertyTypes.DO_SUBTRACT_FIRST.value());
		boolean subtractFirst = property != null ? Boolean.valueOf(property.getValue()).booleanValue() : false;
		property = record.getProperty(MeasurementPropertyTypes.DO_SUBTRACT_LAST.value());
		boolean subtractLast = property != null ? Boolean.valueOf(property.getValue()).booleanValue() : false;

		try {
			if (subtractFirst) {
				reduction = record.getFirst() / 1000.0;
			}
			else if (subtractLast) {
				reduction = record.getLast() / 1000.0;
			}
		}
		catch (Throwable e) {
			reduction = 0;
		}

		double newValue = 0;
		switch (record.getDataType()) {
		case GPS_LATITUDE:
		case GPS_LONGITUDE:
			if (record.getUnit().contains("°") && record.getUnit().contains("'")) {
				int grad = ((int) (value / 1000));
				double minuten = (value - (grad * 1000.0)) / 10.0;
				newValue = grad + minuten / 60.0;
			}
			else { // assume degree only
				newValue = value / 1000.0;
			}
			break;

		case DATE_TIME:
			newValue = 0;
			break;

		default:
			newValue = (value - reduction) * factor + offset;
			break;
		}

		log.log(Level.FINE, "for " + record.getName() + " in value = " + value + " out value = " + newValue); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		return newValue;
	}

	/**
	 * function to reverse translate measured values from a device to values represented
	 * this function should be over written by device and measurement specific algorithm
	 * @return double of device dependent value
	 */
	public double reverseTranslateValue(Record record, double value) {
		double factor = record.getFactor(); // != 1 if a unit translation is required
		double offset = record.getOffset(); // != 0 if a unit translation is required
		double reduction = record.getReduction(); // != 0 if a unit translation is required

		PropertyType property = record.getProperty(MeasurementPropertyTypes.DO_SUBTRACT_FIRST.value());
		boolean subtractFirst = property != null ? Boolean.valueOf(property.getValue()).booleanValue() : false;
		property = record.getProperty(MeasurementPropertyTypes.DO_SUBTRACT_LAST.value());
		boolean subtractLast = property != null ? Boolean.valueOf(property.getValue()).booleanValue() : false;

		try {
			if (subtractFirst) {
				reduction = record.getFirst() / 1000.0;
			}
			else if (subtractLast) {
				reduction = record.getLast() / 1000.0;
			}
		}
		catch (Throwable e) {
			reduction = 0;
		}

		double newValue = 0;
		switch (record.getDataType()) {
		case GPS_LATITUDE:
		case GPS_LONGITUDE:
			if (record.getUnit().contains("°") && record.getUnit().contains("'")) {
				int grad = (int) value;
				double minuten = (value - grad * 1.0) * 60.0;
				newValue = (grad + minuten / 100.0) * 1000.0;
			}
			else { // assume degree only
				newValue = value * 1000.0;
			}
			break;

		case DATE_TIME:
			newValue = 0;
			break;

		default:
			newValue = (value - offset) / factor + reduction;
			break;
		}

		log.log(Level.FINER, "for " + record.getName() + " in value = " + value + " out value = " + newValue); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
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
		//add implementation where data point are calculated
		//do not forget to make record displayable -> record.setDisplayable(true);

		//set record data types if required and/or given with the device properties
		String[] recordNames = recordSet.getRecordNames();
		for (int i = 0; i < recordNames.length; ++i) {
			MeasurementType measurement = this.getMeasurement(recordSet.getChannelConfigNumber(), i);
			PropertyType dataTypeProperty = measurement.getProperty(MeasurementPropertyTypes.DATA_TYPE.value());
			if (dataTypeProperty != null) {
				switch (Record.DataType.fromValue(dataTypeProperty.getValue())) {
				case GPS_ALTITUDE:
				case GPS_LATITUDE:
				case GPS_LONGITUDE:
				case DATE_TIME:
					recordSet.get(recordNames[i]).setDataType(Record.DataType.fromValue(dataTypeProperty.getValue()));
					break;

				default:
					break;
				}
			}
		}
		this.application.updateStatisticsData();
	}

	/**
	 * check and update visibility status of all records according the available device configuration
	 * this function must have only implementation code if the device implementation supports different configurations
	 * where some curves are hided for better overview 
	 * example: if device supports voltage, current and height and no sensors are connected to voltage and current
	 * it makes less sense to display voltage and current curves, if only height has measurement data
	 * at least an update of the graphics window should be included at the end of this method
	 */
	@Override
	public void updateVisibilityStatus(RecordSet recordSet, boolean includeReasonableDataCheck) {

		//0=Voltage 1=Current 2=Capacity 3=Power 4=Temperature 5=balance
		//6=cellVoltage1 7=cellVoltage2 8=cellVoltage3 9=cellVoltage4 10=cellVoltage5 11=cellVoltage6 12=cellVoltage7 13=cellVoltage8
		recordSet.setAllDisplayable();
		for (int i=0; i<recordSet.size(); ++i) {
				Record record = recordSet.get(i);
				record.setDisplayable(record.hasReasonableData());
				if (log.isLoggable(Level.FINER))
					log.log(Level.FINER, record.getName() + " setDisplayable=" + record.hasReasonableData()); //$NON-NLS-1$
		}

		if (log.isLoggable(Level.FINE)) {
			for (int i = 0; i < recordSet.size(); i++) {
				Record record = recordSet.get(i);
				log.log(Level.FINE, record.getName() + " isActive=" + record.isActive() + " isVisible=" + record.isVisible() + " isDisplayable=" + record.isDisplayable()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
		}
	}

	/**
	 * query for all the property keys this device has in use
	 * - the property keys are used to filter serialized properties form OSD data file
	 * @return [offset, factor, reduction, number_cells, prop_n100W, ...]
	 */
	public String[] getUsedPropertyKeys() {
		return new String[] { IDevice.OFFSET, IDevice.FACTOR, IDevice.REDUCTION };
	}

	/**
	 * method toggle open close serial port or start/stop gathering data from device
	 * if the device does not use serial port communication this place could be used for other device related actions which makes sense here
	 * as example a file selection dialog could be opened to import serialized ASCII data 
	 */
	public void open_closeCommPort() {
			if (this.serialPort != null) {
				if (!this.serialPort.isConnected()) {
					try {
						Channel activChannel = Channels.getInstance().getActiveChannel();
						if (activChannel != null) {
							if (this.convertData == null)
								this.convertData = new DataParserNext(this, this.getDataBlockTimeUnitFactor(), this.getDataBlockLeader(), this.getDataBlockSeparator().value(), this.getDataBlockCheckSumType(), 14, 0);

							this.dataGatherThread = new GathererThread(this.application, this, this.serialPort, this.convertData);
							try {
								if (this.serialPort.isConnected()) {
									this.dataGatherThread.start();
								}
							}
							catch (RuntimeException e) {
								log.log(Level.SEVERE, e.getMessage(), e);
							}
							catch (Throwable e) {
								log.log(Level.SEVERE, e.getMessage(), e);
							}
						}
					}
					catch (SerialPortException e) {
						log.log(Level.SEVERE, e.getMessage(), e);
						this.application.openMessageDialog(Messages.getString(gde.messages.MessageIds.GDE_MSGE0015, new Object[] { e.getClass().getSimpleName() + GDE.STRING_BLANK_COLON_BLANK + e.getMessage() }));
					}
					catch (ApplicationConfigurationException e) {
						log.log(Level.SEVERE, e.getMessage(), e);
						this.application.openMessageDialog(Messages.getString(gde.messages.MessageIds.GDE_MSGE0010));
						this.application.getDeviceSelectionDialog().open();
					}
					catch (Throwable e) {
						log.log(Level.SEVERE, e.getMessage(), e);
					}
				}
				else {
					if (this.dataGatherThread != null) {
						this.dataGatherThread.stopDataGatheringThread(false, null);
					}
					this.serialPort.close();
				}
			}
	}
}
