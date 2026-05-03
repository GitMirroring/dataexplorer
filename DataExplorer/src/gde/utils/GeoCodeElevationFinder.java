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
package gde.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.util.Locale;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import gde.Analyzer;
import gde.DataAccess;
import gde.DataAccess.LocalAccess;
import gde.histo.utils.GpsCoordinate;

/**
 * 
 */
public class GeoCodeElevationFinder {

	/**
	 * testing several web-services available to find an elevation to given coordinates
	 */
	public static void main(String[] args) {

		try {
			Analyzer analyzer = Analyzer.getInstance();
			DataAccess dataAccess = analyzer.getDataAccess();
			GpsCoordinate gpsCoordinate = new GpsCoordinate(46.7088183, 14.0043750);

			try {
				//https://api.opentopodata.org/v1/test-dataset?locations=48.62890252073733,8.988615870996933
				String url = String.format(Locale.US, "https://api.opentopodata.org/v1/test-dataset?locations=%f,%f", gpsCoordinate.getLatitude(), gpsCoordinate.getLongitude());
				System.out.println("Request URL " + url);
				URL requestUrl = new URI(url).toURL();
				InputStream inputStream = ((LocalAccess) dataAccess).getHttpsInputStream(requestUrl);
				BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
				StringBuilder sb = new StringBuilder();

				String line = null;
				try {
					while ((line = reader.readLine()) != null) {
						sb.append(line + "\n");
					}
				}
				catch (IOException e) {
					e.printStackTrace();
				}
				finally {
					try {
						inputStream.close();
					}
					catch (IOException e) {
						e.printStackTrace();
					}
				}
				System.out.println(sb.toString());

				JsonParser jsonParser = new JsonParser();

				JsonObject jsonObject = (JsonObject) jsonParser.parse(sb.toString());

				System.out.println("status = " + jsonObject.get("status"));
				System.out.println("elevation = " + jsonObject.get("results").getAsJsonArray().get(0).getAsJsonObject().get("elevation"));
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}

	}
}
