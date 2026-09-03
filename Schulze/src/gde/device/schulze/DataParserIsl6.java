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
public class DataParserIsl6 extends DataParser {
	static Logger			log										= Logger.getLogger(DataParserIsl6.class.getName());
	
	double capacity[] = {0., 0.};
	double energy[] = {0., 0.};
	final Isl6_330d islDevice;
	int counter = 0;
	int newState = 0;

	protected final int offset;
	/**
	 * @param useTimeFactor
	 * @param useLeaderChar
	 * @param useSeparator
	 * @param useCheckSumType
	 * @param useDataSize
	 */
	public DataParserIsl6(Isl6_330d useDevice, int useTimeFactor, String useLeaderChar, String useSeparator, CheckSumTypes useCheckSumType, int useDataSize, int offset) {
		super(useTimeFactor, useLeaderChar, useSeparator, useCheckSumType, useDataSize);
		this.islDevice = useDevice;
		this.offset = offset;
	}

	/**
	 * default parse method for 1:    1: 4047:  400-e..... or 2:    1: 4068:  200:0.....
	 * Datensatz: A:sssss:uuuuu:iiiiiVSttt## (ASCII) 
	 * Legende:
   * A = Akkunummer (Geräteausgang)
   * : = Trennzeichen
   * sssss = Zeit in Sekunden
   * uuuuu = Akkuspannung in Millivolt
   * iiiii = Strom in Milliampere
   * V[:,-] = Vorzeichen für Strom
   * S[l,L,E,P,v...] = Lade-/Entladestatus
   * ttt[...] = Akkutemperatur
   * ##[..] = Gerätenummer Hinweis: Die Akkutemperatur und die Gerätenummer werden bei diesem Gerät fest als "....." (Punkte) übertragen.
	 * @param inputLine
	 * @param strValues
	 * @throws DevicePropertiesInconsistenceException
	 */
	@Override
	public void parse(String inputLine, int line) throws DevicePropertiesInconsistenceException {
		if (inputLine.contains("(A1") || inputLine.contains("(A2")) { // (A1) Akku_ab, Akku_an
			int lineIndex = inputLine.indexOf("(A1") == -1 ? inputLine.indexOf("(A2") : inputLine.indexOf("(A1");
			this.channelConfigNumber = Integer.parseInt(inputLine.substring(lineIndex + 2, lineIndex + 3).trim()); //channel A1/A2 used to address channel in gatherer thread

			this.values[0] = 0; //voltage
			this.values[1] = 0; //current
			this.values[2] = 0; //capacity
			this.values[3] = 0; //power
			this.values[4] = 0; //energy
			this.newState = 0; //unknown to signal end processing
			this.capacity[this.channelConfigNumber - 1] = 0.;
			this.energy[this.channelConfigNumber - 1] = 0.;
		}
		else if (inputLine.contains("laden")  || inputLine.contains("schulze")  || inputLine.contains("elektronik")  || inputLine.contains("isl")  || inputLine.contains("rdy")) { // ge/ent-laden:  1990mAh
			//assuming actual channel/output since it can not be detected
			this.values[0] = 0; //voltage
			this.values[1] = 0; //current
			this.values[2] = 0; //capacity
			this.values[3] = 0; //power
			this.values[4] = 0; //energy
			this.newState = 0; //unknown to signal end processing			
			this.capacity[this.channelConfigNumber - 1] = 0.;
			this.energy[this.channelConfigNumber - 1] = 0.;
		}
		else if (inputLine.contains("Geraetenummer")) { // Geraetenummer=5164
			//assuming actual channel/output since it can not be detected
			this.values[0] = 0; //voltage
			this.values[1] = 0; //current
			this.values[2] = 0; //capacity
			this.values[3] = 0; //power
			this.values[4] = 0; //energy
			this.newState = 0; //unknown to signal end processing			
			this.capacity[this.channelConfigNumber - 1] = 0.;
			this.energy[this.channelConfigNumber - 1] = 0.;
		}
		else {
			int startIndex = line == 0 ? 0 : inputLine.length() - 1 - Math.min(27,  inputLine.length() - 1);
			int[] refPositions = new int[] {startIndex, inputLine.length() - 1}; //startIndex - endIndex
			this.islDevice.setDataLineStartAndLength(inputLine.getBytes(), refPositions);
			byte[] tmpData = new byte[refPositions[1]];
			System.arraycopy(inputLine.getBytes(), refPositions[0], tmpData, 0, tmpData.length);
			inputLine = new String(tmpData);
			log.log(Level.OFF, "parsing " + inputLine);
			
			String[] mainValues = inputLine.indexOf(';') == -1 ? inputLine.split(":") : inputLine.substring(0, inputLine.indexOf(';')).split(":");
			this.channelConfigNumber = Integer.parseInt(mainValues[0].trim()); //channel A1/A2 used to address channel in gatherer thread
			int indexChannel = this.channelConfigNumber - 1; //to address channel related capacity and energy

			try {
				if (mainValues[1].trim().equals("1") || mainValues[1].trim().equals("0")) {
					this.start_time_ms = (int) (Double.parseDouble(mainValues[1].trim()) * this.timeFactor); // Seconds * 1000 = msec
					this.capacity[indexChannel] = 0.;
					this.energy[indexChannel] = 0.;
				}
				else {
					this.time_ms = (int) (Double.parseDouble(mainValues[1].trim()) * this.timeFactor) - this.start_time_ms; // Seconds * 1000 = msec	
					//log.log(Level.OFF, "time_s = " + this.time_ms/1000 + " counter = " + counter++);
				}

				this.values[0] = Integer.parseInt(mainValues[2].trim()); //voltage
				if (mainValues.length == 4) { // 1:    1: 3719:   12-e.....
					this.values[1] = Integer.parseInt(mainValues[3].substring(0, 5).trim()) * (mainValues[3].charAt(5) == '-' ? -1 : 1); //current
					this.newState = this.islDevice.getProcessingState(mainValues[3].substring(6, 7).charAt(0));
				}
				else { //mainValues.length == 5, 1:    1: 3719:   12:p.....
					this.values[1] = Integer.parseInt(mainValues[3].trim()); //current
					this.newState = this.islDevice.getProcessingState(mainValues[4].substring(0, 1).charAt(0));
				}

				if (this.newState <= 8)//keep previous state for o,O,v,V and E,R e,E
					this.state = this.newState;

				this.capacity[indexChannel] += this.values[1] / 1000. * this.islDevice.getTimeStep_ms() / 3600.;
				this.values[2] = (int) (this.capacity[indexChannel] * 1000); //capacity

				this.values[3] = (int) Math.abs(this.values[0] / 1000. * this.values[1] / 1000. * 1000.); //power

				this.energy[indexChannel] += Math.abs((values[0] / 1000.0 * values[1] / 1000.0)) / 3600.; //energy		
				this.values[4] = (int) (this.energy[indexChannel] * 1000.);
			}
			catch (NumberFormatException e) {
				log.log(Level.WARNING, "bad char in line: " + inputLine);
			}
		}
	}

}
