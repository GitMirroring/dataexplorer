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
    
    Copyright (c) 2008-2026 Winfried Bruegmann
****************************************************************************************/
package gde.exception;

/**
 * Application exception class
 * @author Winfried Brügmann
 */
public class TimeOutException extends Exception {
	static final long serialVersionUID = 26031957;

	public TimeOutException(String message) {
		super(message);
	}
}
