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
    
    Copyright (c) 2016,2017,2018,2019,2020,2021,2022,2023,2024,2025,2026 Winfried Bruegmann
****************************************************************************************/
package gde.device.schulze;

import java.util.logging.Level;
import java.util.logging.Logger;

import gde.device.CheckSumTypes;
import gde.exception.DevicePropertiesInconsistenceException;
import gde.io.DataParser;

/**
 * @author brueg
 *
 */
public class DataParserNext extends DataParser {
	static Logger			log										= Logger.getLogger(DataParserNext.class.getName());
	
	double capacity[] = {0., 0.};
	double energy[] = {0., 0.};
	final NextGen8 nextDevice;
	int counter = 0;
	int newState = 0;
	int stateCh[] = {0, 0};

	protected final int offset;
	/**
	 * @param useTimeFactor
	 * @param useLeaderChar
	 * @param useSeparator
	 * @param useCheckSumType
	 * @param useDataSize
	 */
	public DataParserNext(NextGen8 useDevice, int useTimeFactor, String useLeaderChar, String useSeparator, CheckSumTypes useCheckSumType, int useDataSize, int offset) {
		super(useTimeFactor, useLeaderChar, useSeparator, useCheckSumType, useDataSize);
		this.nextDevice = useDevice;
		this.offset = offset;
	}

	/**
	 * default parse method for 1:   46:11947:  986-R 23; 3943; 3979; 4041;    0;    0;    0;    0;    0;
	 * A:sssss:uuuuu:iiiiiVSttt;uuuZ1;uuuZ2;uuuZ3;uuuZ4;uuuZ5;uuuZ6;uuuZ7;uuuZ8;
	 * V[:,-] Vorzeichen für Strom
	 * S[l,L,E,P,v...] Lade-/Entlade- Programmstatus
	 * @param inputLine
	 * @param strValues
	 * @throws DevicePropertiesInconsistenceException
	 */
	@Override
	public void parse(String inputLine, int line) throws DevicePropertiesInconsistenceException {
		int startIndex = 0; //line == 0 ? 0 : inputLine.length() - 1 - Math.min(73,  inputLine.length() - 1);
		int[] refPositions = new int[] {startIndex, inputLine.length() - 1}; //startIndex - endIndex
		this.nextDevice.setDataLineStartAndLength(inputLine.getBytes(), refPositions);
		byte[] tmpData = new byte[refPositions[1]];
		System.arraycopy(inputLine.getBytes(), refPositions[0], tmpData, 0, tmpData.length);
		inputLine = new String(tmpData);
		log.log(Level.OFF, "parsing " + inputLine);

		String[] mainValues = inputLine.indexOf(';') == -1 ? inputLine.split(":") : inputLine.substring(0, inputLine.indexOf(';')).split(":");
		String[] cellValues = inputLine.indexOf(';') == -1 ? new String[] {} : inputLine.substring(inputLine.indexOf(';')+1).split(";");

		this.channelConfigNumber = Integer.parseInt(mainValues[0].trim()); //+1 to simulate channel 2
		int indexChannel = this.channelConfigNumber - 1; //to address channel related capacity and energy
		
		this.values = new int[this.nextDevice.getNumberOfMeasurements(1)];		
		
		if (this.start_time_ms == Integer.MIN_VALUE) {
			this.start_time_ms = (int) (Double.parseDouble(mainValues[1].trim()) * this.timeFactor); // Seconds * 1000 = msec
		}
		else {
			this.time_ms = (int) (Double.parseDouble(mainValues[1].trim()) * this.timeFactor) - this.start_time_ms; // Seconds * 1000 = msec	
			//log.log(Level.OFF, "time_s = " + this.time_ms/1000 + " counter = " + counter++);
		}


		this.values[0] = Integer.parseInt(mainValues[2].trim()); //voltage
		if (mainValues.length == 4) {
			this.values[1] = Integer.parseInt(mainValues[3].substring(5, 6).trim() + mainValues[3].substring(0, 5).trim()); //current
			this.values[5] = Integer.parseInt(mainValues[3].substring(7, 10).trim()); //temperature
			this.newState = this.nextDevice.getProcessingState(mainValues[3].substring(6, 7).charAt(0)); //must be one of discharge states
		}
		else {
			this.values[1] = Integer.parseInt(mainValues[3].trim()); //current
			this.values[5] = Integer.parseInt(mainValues[4].substring(1, 4).trim()); //temperature
			this.newState = this.nextDevice.getProcessingState(mainValues[4].substring(0, 1).charAt(0)); //must be one of available states
		}
		
		if (this.newState != this.stateCh[indexChannel]) {
			log.log(Level.OFF, "channel = " + this.channelConfigNumber + "; new State = " + this.newState + "; channel state = " + this.stateCh[indexChannel]);
			this.state = this.newState;
			this.stateCh[indexChannel] = this.newState;
			this.capacity[indexChannel] = 0.;
			this.energy[indexChannel] = 0.;
		}
		this.state = this.stateCh[indexChannel];
				
		this.capacity[indexChannel] += this.values[1]/1000. * this.nextDevice.getTimeStep_ms() / 3600.;
		this.values[2] = (int) (this.capacity[indexChannel] * 1000); //capacity
		
		this.values[3] = (int) (this.values[0]/1000. * this.values[1]/1000. * 1000.); //power

		this.energy[indexChannel] += Math.abs((values[0] / 1000.0 * values[1] / 1000.0)) / 3600.; //energy		
		this.values[4] = (int) (this.energy[indexChannel] * 1000.);
		

		if (cellValues.length > 0 && this.values.length > 6) {
			int minCellValue = Integer.MAX_VALUE, maxCellValue = Integer.MIN_VALUE;
			for (int i = 0; i < cellValues.length && i < this.nextDevice.getNumberOfLithiumCells(); ++i) {
				this.values[7 + i] = Integer.parseInt(cellValues[i].trim());
				if (this.values[7 + i] > 0) {
					maxCellValue = this.values[7 + i] > maxCellValue ? this.values[7 + i] : maxCellValue;
					minCellValue = this.values[7 + i] < minCellValue ? this.values[7 + i] : minCellValue;
				}
			}
			this.values[6] = maxCellValue != Integer.MIN_VALUE && minCellValue != Integer.MAX_VALUE ? maxCellValue - minCellValue : 0;
		}
	}

}
