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
import gde.utils.TimeLine;

/**
 * zoom offset values and width are values identical for all record
 */
public class ZoomOffsetAndWidth {
	final Logger									log												= Logger.getLogger(ZoomOffsetAndWidth.class.getName());
	int														zoomOffset								= 0;																																																																		// number of measurements point until zoom area begins approximation only
	double												zoomTimeOffset						= 0;																		// time where the zoom area begins
	double												drawTimeWidth							= 0;																		// all or zoomed area time width
	
	/**
	 * @param record
	 * @param zoomBounds
	 */
	public ZoomOffsetAndWidth(Record record, Rectangle zoomBounds) {
		this.zoomTimeOffset = record.getHorizontalDisplayPointTime_ms(zoomBounds.x) + record.getDrawTimeOffset_ms();
		if (this.zoomTimeOffset < 0) this.zoomTimeOffset = 0;
		this.zoomOffset = record.findBestIndex(this.zoomTimeOffset);
		this.drawTimeWidth = record.getHorizontalDisplayPointTime_ms(zoomBounds.width - 1);
		if (this.drawTimeWidth > record.getMaxTime_ms()) this.drawTimeWidth = record.getMaxTime_ms();
		if (log.isLoggable(Level.OFF))
			log.log(Level.OFF, record.name + " zoomOffset " + this.zoomOffset + " zoomTimeOffset " + TimeLine.getFomatedTimeWithUnit(this.zoomTimeOffset) + " drawTimeWidth " + TimeLine.getFomatedTimeWithUnit(this.drawTimeWidth)); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * @return the zoomOffset
	 */
	public int getZoomOffset() {
		return zoomOffset;
	}

	/**
	 * @return the zoomTimeOffset
	 */
	public double getZoomTimeOffset() {
		return zoomTimeOffset;
	}

	/**
	 * @return the drawTimeWidth
	 */
	public double getDrawTimeWidth() {
		return drawTimeWidth;
	}	
}
