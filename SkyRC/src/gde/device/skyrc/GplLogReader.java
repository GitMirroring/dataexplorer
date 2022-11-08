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

    Copyright (c) 2022 Winfried Bruegmann
****************************************************************************************/
package gde.device.skyrc;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.logging.Logger;

import gde.GDE;
import gde.data.Channel;
import gde.data.Channels;
import gde.data.RecordSet;
import gde.device.IDevice;
import gde.log.Level;
import gde.messages.MessageIds;
import gde.messages.Messages;
import gde.ui.DataExplorer;
import gde.ui.menu.MenuToolBar;
import gde.utils.StringHelper;

public class GplLogReader {

	static Logger							log					= Logger.getLogger(GplLogReader.class.getName());

	static String							lineSep			= GDE.LINE_SEPARATOR;
	static DecimalFormat			df3					= new DecimalFormat("0.000");														//$NON-NLS-1$
	static StringBuffer				sb;

	final static DataExplorer	application	= DataExplorer.getInstance();
	final static Channels			channels		= Channels.getInstance();
	
	final static byte					beginMarker = (byte) 0xee;
	final static byte					endMarker = (byte) 0xdd;

	/**
	 * read GPS exchange format track point and extension data
	 * @param filePath
	 * @param device
	 * @param recordNameExtend
	 * @param channelConfigNumber
	 * @return
	 */
	public static RecordSet read(String filePath, IDevice device, String recordNameExtend, Integer channelConfigNumber) {
		Channel activeChannel = null;
		int lineNumber = 0;
		String recordSetNameExtend = device.getRecordSetStemNameReplacement();
		RecordSet recordSet = null;
		DataInputStream data_in = null;
		int[] points = new int[device.getNoneCalculationMeasurementNames(1, device.getMeasurementNames(1)).length];
		//String dateTime = new SimpleDateFormat("yyyy-MM-dd, HH:mm:ss").format(new File(filePath).lastModified()); //$NON-NLS-1$

		MenuToolBar menuToolBar = GplLogReader.application.getMenuToolBar();
		GDE.getUiNotification().setProgress(0);

		try {
			if (channelConfigNumber == null)
				activeChannel = GplLogReader.channels.getActiveChannel();
			else
				activeChannel = GplLogReader.channels.get(channelConfigNumber);
			channelConfigNumber = GplLogReader.channels.getActiveChannelNumber();

			if (activeChannel != null) {
				if (GplLogReader.log.isLoggable(Level.FINE))
					GplLogReader.log.log(Level.FINE, device.getChannelCount() + " - data for channel = " + channelConfigNumber); //$NON-NLS-1$

				String recordSetName = (activeChannel.size() + 1) + recordSetNameExtend;
				recordSetName = recordNameExtend.length() > 2 ? recordSetName + GDE.STRING_BLANK_LEFT_BRACKET + recordNameExtend + GDE.STRING_RIGHT_BRACKET : recordSetName;

				long startTime = System.nanoTime() / 1000000;
				File file = new File(filePath);
				FileInputStream file_input = new FileInputStream(file);
				data_in = new DataInputStream(file_input);
				byte[] buffer = new byte[16];
				
				boolean isLogData = false;
				long timeStep_ms = 0;
				long timeStamp_ms = 0;
				while (data_in.read(buffer) != -1) {
					if (!isLogData && buffer[0] == beginMarker && buffer[1] == beginMarker && buffer[2] == beginMarker) {
						isLogData = true;
						recordSetName = (activeChannel.size() + 1) + recordSetNameExtend;
						recordSetName = recordNameExtend.length() > 2 ? recordSetName + GDE.STRING_BLANK_LEFT_BRACKET + recordNameExtend + GDE.STRING_RIGHT_BRACKET : recordSetName;
						recordSet = RecordSet.createRecordSet(recordSetName, device, channelConfigNumber, true, true, true);
						activeChannel.put(recordSetName, recordSet);
						recordSet = activeChannel.get(recordSetName);
						recordSet.setRecordSetDescription(recordSet.getRecordSetDescription());
						timeStep_ms = 100 * buffer[3];
						log.log(Level.OFF, String.format("timeStep_ms = %d", timeStep_ms));
						int year = 2000 + buffer[6];
						int month = buffer[7];
						int day = buffer[8];
						int hour = buffer[9] + device.getUTCdelta();
						int minute = buffer[10];
						int seconds = buffer[11];
						log.log(Level.OFF, String.format("startTimeStamp = %d-%02d-%02d %02d:%02d:%02d", year, month, day, hour, minute, seconds));						
						long startTimeStamp = new GregorianCalendar(year, month-1, day, hour, minute, seconds).getTimeInMillis();
						recordSet.setRecordSetDescription(device.getName() + GDE.STRING_MESSAGE_CONCAT	+ Messages.getString(MessageIds.GDE_MSGT0129) + new SimpleDateFormat("yyyy-MM-dd, HH:mm:ss").format(startTimeStamp));
						recordSet.setStartTimeStamp(startTimeStamp);
						String dateTime = String.format("%d-%02d-%02d", year, month, day);	
						activeChannel.setFileDescription(application.isObjectoriented() ? dateTime + GDE.STRING_BLANK + application.getObjectKey() : dateTime);
						timeStamp_ms = 0;
						continue;
					}
					else if (isLogData && buffer[0] == endMarker && buffer[1] == endMarker && buffer[2] == endMarker) {
						isLogData = false;
						//write filename after import to record description
						recordSet.descriptionAppendFilename(filePath.substring(filePath.lastIndexOf(GDE.CHAR_FILE_SEPARATOR_UNIX)+1));

						if (GDE.isWithUi()) {
							activeChannel.applyTemplate(recordSetName, false);
						}
						GDE.getUiNotification().setProgress(100);

						if (GDE.isWithUi()) {
							Channels.getInstance().switchChannel(activeChannel.getName());
							activeChannel.switchRecordSet(recordSetName);
							device.updateVisibilityStatus(recordSet, true);

							menuToolBar.updateChannelSelector();
							menuToolBar.updateRecordSetSelectCombo();
							continue;
						}
					}
					
					// evaluate log data
					if (isLogData && recordSet != null) {
						recordSet.addNoneCalculationRecordsPoints(device.convertDataBytes(points, buffer), timeStamp_ms);
						timeStamp_ms += timeStep_ms;
					}					
				}
				
				if (isLogData) { //log ending without endMarker - overflow?
					if (GDE.isWithUi()) {
						activeChannel.applyTemplate(recordSetName, false);
					}
					GDE.getUiNotification().setProgress(100);

					if (GDE.isWithUi()) {
						Channels.getInstance().switchChannel(activeChannel.getName());
						activeChannel.switchRecordSet(recordSetName);
						device.updateVisibilityStatus(recordSet, true);

						menuToolBar.updateChannelSelector();
						menuToolBar.updateRecordSetSelectCombo();
					}
				}
				
				data_in.close();
				data_in = null;

				GDE.getUiNotification().setProgress(100);

				if (GDE.isWithUi() && recordSet != null) {
					Channels.getInstance().switchChannel(activeChannel.getName());
					activeChannel.switchRecordSet(recordSetName);
					device.updateVisibilityStatus(recordSet, true);

					menuToolBar.updateChannelSelector();
					menuToolBar.updateRecordSetSelectCombo();
					if (isLogData && recordSet != null) { //overflow, no endMarker detected
					//write filename after import to record description
					recordSet.descriptionAppendFilename(filePath.substring(filePath.lastIndexOf(GDE.CHAR_FILE_SEPARATOR_UNIX)+1));
					}
				}

				log.log(Level.TIME, "read time = " + StringHelper.getFormatedTime("mm:ss:SSS", (System.nanoTime() / 1000000 - startTime))); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		catch (FileNotFoundException e) {
			GplLogReader.log.log(Level.WARNING, e.getMessage(), e);
			GplLogReader.application.openMessageDialog(e.getMessage());
		}
		catch (IOException e) {
			GplLogReader.log.log(Level.WARNING, e.getMessage(), e);
			GplLogReader.application.openMessageDialog(e.getMessage());
		}
		catch (Exception e) {
			GplLogReader.log.log(Level.WARNING, e.getMessage(), e);
			// check if previous records are available and needs to be displayed
			if (activeChannel != null && activeChannel.size() > 0) {
				String recordSetName = activeChannel.getFirstRecordSetName();
				activeChannel.setActiveRecordSet(recordSetName);
				device.updateVisibilityStatus(activeChannel.get(recordSetName), true);
				activeChannel.get(recordSetName).checkAllDisplayable(); // raw import needs calculation of passive records
				if (GDE.isWithUi()) activeChannel.switchRecordSet(recordSetName);
			}
			// now display the error message
			String msg = filePath + GDE.STRING_MESSAGE_CONCAT + Messages.getString(MessageIds.GDE_MSGE0045, new Object[] { e.getMessage(), lineNumber });
			GplLogReader.log.log(Level.WARNING, msg, e);
			GplLogReader.application.openMessageDialog(msg);
		}
		finally {
			if (data_in != null) {
				try {
					data_in.close();
				}
				catch (IOException e) {
					log.log(Level.WARNING, e.getMessage());
				}
				data_in = null;
			}
			GDE.getUiNotification().setStatusMessage(GDE.STRING_EMPTY);
		}

		return recordSet;
	}

}
