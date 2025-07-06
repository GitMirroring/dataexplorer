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
    
    Copyright (c) 2024,2025 Winfried Bruegmann
****************************************************************************************/
package gde.device.graupner;

/**
 * classify models, give MDL start character
 */
public enum ModelType {
	HELI("h"), ACRO("a"), CAR("c"), QUAD("q"), BOAT("b"), TANK("t"), CRAWLWER("l"), UNKNOWN("u");

	private final String	value;
	
	private ModelType(String v) {
		this.value = v;
	}

	public String value() {
		return this.value;
	}
	
  public static ModelType fromValue(String v) {
    for (ModelType c: ModelType.values()) {
        if (c.value.equals(v)) {
            return c;
        }
    }
    throw new IllegalArgumentException("unknown transmitter radio " + v);
  }
}
