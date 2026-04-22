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

    Copyright (c) 2026 Winfried Bruegmann
****************************************************************************************/
package gde.data;

import java.util.logging.Logger;

import org.eclipse.swt.graphics.Rectangle;

import gde.log.Level;

/**
 * zoom scale values are individual for each record
 */
public class ZoomScaleValues {

	final Logger									log												= Logger.getLogger(ZoomScaleValues.class.getName());
	String												name;
	double												tmpMinZoomScaleValue			= 0;
	double												tmpMaxZoomScaleValue			= 0;
	double												minZoomScaleValue					= 0;
	double												maxZoomScaleValue					= 0;
	
	/**
	 * @param record
	 * @param zoomBounds
	 */
	public ZoomScaleValues(Record record, Rectangle zoomBounds) {
		this.name = record.name;
		this.tmpMinZoomScaleValue = record.getVerticalDisplayPointScaleValue(zoomBounds.y, record.parent.drawAreaBounds);
		this.tmpMaxZoomScaleValue = record.getVerticalDisplayPointScaleValue(zoomBounds.height + zoomBounds.y, record.parent.drawAreaBounds);
		this.minZoomScaleValue = tmpMinZoomScaleValue < record.minScaleValue ? record.minScaleValue : tmpMinZoomScaleValue;
		this.maxZoomScaleValue = tmpMaxZoomScaleValue > record.maxScaleValue ? record.maxScaleValue : tmpMaxZoomScaleValue;
		if (log.isLoggable(Level.OFF))
			log.log(Level.OFF, this.name + " - minZoomScaleValue = " + this.minZoomScaleValue + "  maxZoomScaleValue = " + this.maxZoomScaleValue); //$NON-NLS-1$ //$NON-NLS-2$		
	}
	
	/**
	 * @return the tmpMinZoomScaleValue
	 */
	public double getTmpMinZoomScaleValue() {
		return tmpMinZoomScaleValue;
	}

	/**
	 * @return the tmpMaxZoomScaleValue
	 */
	public double getTmpMaxZoomScaleValue() {
		return tmpMaxZoomScaleValue;
	}

	/**
	 * @return the minZoomScaleValue
	 */
	public double getMinZoomScaleValue() {
		return minZoomScaleValue;
	}

	/**
	 * @return the maxZoomScaleValue
	 */
	public double getMaxZoomScaleValue() {
		return maxZoomScaleValue;
	}
}
