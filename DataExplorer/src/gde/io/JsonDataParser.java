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
    
    Copyright (c) 2024 Winfried Bruegmann
****************************************************************************************/
package gde.io;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import com.google.gson.Gson;

import gde.device.CheckSumTypes;
import gde.device.FormatTypes;
import gde.exception.DevicePropertiesInconsistenceException;

public class JsonDataParser extends DataParser implements IDataParser {
	
	public static class Data {

		//{"dsn":1,"st":1,"tm":1000,"data":[],"crc":0
		int dsn; 			//dataSetNumber
		int st; 			//state;
		long tm; 			//time;
		List<Integer> data; //measurements;
		byte crc; 		//checksum;
		
		Data(int dsn, int st, long t, ArrayList<Integer> l, byte q) {
			setDataSetNumber(dsn);
			setState(st);
			setTime(t);
			setMeasurements(l);
			setChecksum(q);
		}

		@Override
		public String toString() {
			return "Data [dsn=" + dsn + ", st=" + st + ", tm=" + tm + ", data=" + data + ", crc=" + crc + "]";
		}

		public String[] toStringArray() {
			ArrayList<String> strArray = new ArrayList<>();
			strArray.add("$" + dsn);
			strArray.add(""+st);
			strArray.add(""+tm);
			for (int i : data) 
				strArray.add(""+i);
			strArray.add(""+crc);		
			return strArray.toArray(new String[0]);
		}

		/**
		 * @return the dataSetNumber
		 */
		public int getDataSetNumber() {
			return dsn;
		}
		/**
		 * @param dataSetNumber the dataSetNumber to set
		 */
		public void setDataSetNumber(int dataSetNumber) {
			this.dsn = dataSetNumber;
		}
		/**
		 * @return the state
		 */
		public int getState() {
			return st;
		}
		/**
		 * @param state the state to set
		 */
		public void setState(int state) {
			this.st = state;
		}
		/**
		 * @return the time
		 */
		public long getTime() {
			return tm;
		}
		/**
		 * @param time the time to set
		 */
		public void setTime(long time) {
			this.tm = time;
		}
		/**
		 * @return the measurements
		 */
		public List<Integer> getMeasurements() {
			return data;
		}
		/**
		 * @param measurements the measurements to set
		 */
		public void setMeasurements(List<Integer> measurements) {
			this.data = measurements;
		}

		/**
		 * @return the checksum
		 */
		public byte getChecksum() {
			return crc;
		}
		
		/**
		 * @param checksum the checksum to set
		 */
		public void setChecksum(byte checksum) {
			this.crc = checksum;
		}
		
	}
	
	public static void main(String[] args) {
		
		String jsonLine = "{\"dsn\":1,\"st\":2,\"tm\":1000,\"data\":[1000,1001,1002,1003,1004,1005,1006,1007],\"crc\":0}";
		ArrayList<Integer> measurements = new ArrayList<Integer>(8);
		int val = 1000;
		for (int i=0; i<8; i++)
			measurements.add(val++);
			
		Data data = new Data(1, 2, 1000l, measurements, (byte)0x00);
		
		System.out.println(new Gson().toJson(data, Data.class));
		
		Data jsonData = new Gson().fromJson(jsonLine, Data.class);		
		
		System.out.println(jsonData);
	}


	/**
	 * constructor to initialize required configuration parameter
	 * assuming checkSumFormatType == FormatTypes.TEXT to checksum is build using contained values
	 * dataFormatType == FormatTypes.TEXT where dataBlockSize specifies the number of contained values (file input)
	 * @param useTimeFactor
	 * @param useLeaderChar
	 * @param useSeparator
	 * @param useCheckSumType if null, no checksum calculation will occur
	 * @param useDataSize
	 */
	public JsonDataParser(int useTimeFactor, String useLeaderChar, String useSeparator, CheckSumTypes useCheckSumType, int useDataSize) {
		super(useTimeFactor, useLeaderChar, useSeparator, useCheckSumType, useDataSize);
		log.log(Level.FINE, useTimeFactor + ", " + useLeaderChar + ", " + useSeparator + ", " + useCheckSumType + ", " + useDataSize + ", " + isMultiply1000);
	}


	@Override
	public void parse(String jsonLine, int lineNum) throws DevicePropertiesInconsistenceException, Exception {
		Data jsonData = new Gson().fromJson(jsonLine, Data.class);		
		String[] strValues = jsonData.toStringArray();
		this.valueSize = this.dataFormatType != null && this.dataFormatType == FormatTypes.BINARY 
				? strValues.length - 4 
				: this.dataFormatType != null && this.dataFormatType == FormatTypes.VALUE	&& this.dataBlockSize != 0 
					? Math.abs(this.dataBlockSize) > this.device.getNumberOfMeasurements(this.channelConfigNumber) 
							? this.device.getNumberOfMeasurements(this.channelConfigNumber)
							: Math.abs(this.dataBlockSize)
					: strValues.length - 4;

		parse(jsonLine, strValues);
	}
}
