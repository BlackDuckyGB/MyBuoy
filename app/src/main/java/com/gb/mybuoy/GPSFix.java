/*
 * MyBuoy — outdoor navigation & mark-tracking for boating, hiking, foraging and discovery walks.
 * Copyright (C) 2026 Golden-Block
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * For commercial licensing (use without AGPL obligations), contact:
 * licence@golden-block.fr
 */
package com.gb.mybuoy;

// Un point GPS mesure = une coordonnee (lat/lon/alt, herite de GPSCoordonate) + les metadonnees du fix
// (precision horizontale, horodatage). Destine a remplacer les tableaux paralleles _latitudes /
// _longitudes / _altitudes / _accuracies / _positionTimes de l'historique GPS.
public class GPSFix extends GPSCoordonate {

    private double accuracy;  // precision horizontale (m), telle que fournie par le fix
    private long timeMs;      // horodatage du fix (branche SDK : nanos elapsed-realtime ou millis)
    private Double verticalAccuracy;  // m, null when the fix gives none (Android < 8 or not reported)

    public GPSFix() {
        super();
    }

    public GPSFix(double longitude, double latitude) {
        super(longitude, latitude);
    }

    public GPSFix(double longitude, double latitude, Double altitude, double accuracy, long timeMs) {
        super(longitude, latitude, altitude);
        this.accuracy = accuracy;
        this.timeMs = timeMs;
    }

    public double getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(double accuracy) {
        this.accuracy = accuracy;
    }

    public Double getVerticalAccuracy() {
        return verticalAccuracy;
    }

    public void setVerticalAccuracy(Double verticalAccuracy) {
        this.verticalAccuracy = verticalAccuracy;
    }

    public long getTimeMs() {
        return timeMs;
    }

    public void setTimeMs(long timeMs) {
        this.timeMs = timeMs;
    }
}
