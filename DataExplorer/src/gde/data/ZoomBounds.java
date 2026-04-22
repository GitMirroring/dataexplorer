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

import java.util.LinkedHashMap;

/**
 * class ZoomBounds represents a single zoom step and is a collection of zoom offsets and width and zoom scale values of records
 * zooming creates absolute bounds from relative mouse selected rectangle within graphics window
 */
public class ZoomBounds {

	private ZoomOffsetAndWidth  zoomOffsetAndWidth;
	private LinkedHashMap<String, ZoomScaleValues> zoomScaleValues;
	
	public ZoomBounds(ZoomOffsetAndWidth  zoomOffsetAndWidth) {
		this.zoomOffsetAndWidth = zoomOffsetAndWidth;
		this.zoomScaleValues = new LinkedHashMap<String, ZoomScaleValues>();
	}
	
	public void addZoomScaleValues(ZoomScaleValues addZoomScaleValues1) {
		this.zoomScaleValues.put(addZoomScaleValues1.name, addZoomScaleValues1);
	}	
	
	public ZoomScaleValues getZoomScaleValues(String name) {
		return this.zoomScaleValues.get(name);
	}
	
	/**
	 * @return the zoomOffsetAndWidth
	 */
	public ZoomOffsetAndWidth getZoomOffsetAndWidth() {
		return zoomOffsetAndWidth;
	}
	
}
