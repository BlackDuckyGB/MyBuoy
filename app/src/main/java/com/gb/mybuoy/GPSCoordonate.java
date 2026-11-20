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

import java.util.Locale;

// Coordonnee GPS. Verite = latitude / longitude en DEGRES DECIMAUX (+ altitude).
// Altitude in metres, null when unknown (0 is a real altitude). Above mean sea level on Android >= 14,
// above the WGS-84 ellipsoid below that (see AppManager.altitudeCorrectionAt).
// On maintient en parallele les decompositions DMS (deg/min/sec + hemisphere) et DM (deg/min-decimales),
// synchronisees dans TOUS les setters, quel que soit le mode d'edition (DD / DM / DMS) :
//   - setLatitude(dd)      : maj latitude, puis re-decompose DMS + DM.
//   - setLatDeg/Min/Sec... : recalcule latitude depuis le DMS, puis maj DM (_latMinDec).
//   - setLatMinDec         : recalcule latitude depuis le DM, puis maj DMS (_latMin / _latSec).
public class GPSCoordonate {

    protected double latitude;
    protected double longitude;
    protected Double altitude;

    // Decomposition latitude (derivee, tenue a jour).
    private int _latDeg;
    private int _latMin;
    private double _latSec;
    private double _latMinDec;   // minutes decimales (= _latMin + _latSec/60)
    private boolean _latNorth;
    // Decomposition longitude.
    private int _lonDeg;
    private int _lonMin;
    private double _lonSec;
    private double _lonMinDec;
    private boolean _lonEast;

    public GPSCoordonate() {
        this(0.0, 0.0, null);
    }

    public GPSCoordonate(double longitude, double latitude) {
        this(longitude, latitude, null);
    }

    public GPSCoordonate(double longitude, double latitude, Double altitude) {
        this.longitude = longitude;
        this.latitude = latitude;
        this.altitude = altitude;
        syncLatFromDecimal();
        syncLonFromDecimal();
    }

    public GPSCoordonate(GPSCoordonate other) {
        this(other.longitude, other.latitude, other.altitude);
    }

    // ---- Decimal (canonique) ----
    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
        syncLatFromDecimal();
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
        syncLonFromDecimal();
    }

    public Double getAltitude() {
        return altitude;
    }

    public void setAltitude(Double altitude) {
        this.altitude = altitude;
    }

    // ---- Sync : decimal -> DMS + DM ----
    private void syncLatFromDecimal() {
        double abs = Math.abs(latitude);
        _latNorth = (latitude >= 0.0);
        _latDeg = (int) abs;
        double minFull = (abs - _latDeg) * 60.0;
        _latMin = (int) minFull;
        _latSec = (minFull - _latMin) * 60.0;
        _latMinDec = minFull;
    }

    private void syncLonFromDecimal() {
        double abs = Math.abs(longitude);
        _lonEast = (longitude >= 0.0);
        _lonDeg = (int) abs;
        double minFull = (abs - _lonDeg) * 60.0;
        _lonMin = (int) minFull;
        _lonSec = (minFull - _lonMin) * 60.0;
        _lonMinDec = minFull;
    }

    // ---- Recompute : DMS -> decimal (+ DM) ----
    private void recomputeLatFromDms() {
        double v = _latDeg + _latMin / 60.0 + _latSec / 3600.0;
        latitude = _latNorth ? v : -v;
        _latMinDec = _latMin + _latSec / 60.0;
    }

    private void recomputeLonFromDms() {
        double v = _lonDeg + _lonMin / 60.0 + _lonSec / 3600.0;
        longitude = _lonEast ? v : -v;
        _lonMinDec = _lonMin + _lonSec / 60.0;
    }

    // ---- Recompute : DM -> decimal (+ DMS) ----
    private void recomputeLatFromDm() {
        double v = _latDeg + _latMinDec / 60.0;
        latitude = _latNorth ? v : -v;
        _latMin = (int) _latMinDec;
        _latSec = (_latMinDec - _latMin) * 60.0;
    }

    private void recomputeLonFromDm() {
        double v = _lonDeg + _lonMinDec / 60.0;
        longitude = _lonEast ? v : -v;
        _lonMin = (int) _lonMinDec;
        _lonSec = (_lonMinDec - _lonMin) * 60.0;
    }

    // ---- Composantes latitude (DMS / DM / hemisphere) ----
    public int getLatDeg() {
        return _latDeg;
    }

    public void setLatDeg(int d) {
        _latDeg = d;
        recomputeLatFromDms();
    }

    public int getLatMin() {
        return _latMin;
    }

    public void setLatMin(int m) {
        _latMin = m;
        recomputeLatFromDms();
    }

    public double getLatSec() {
        return _latSec;
    }

    public void setLatSec(double s) {
        _latSec = s;
        recomputeLatFromDms();
    }

    public double getLatMinDec() {
        return _latMinDec;
    }

    public void setLatMinDec(double md) {
        _latMinDec = md;
        recomputeLatFromDm();
    }

    public boolean isLatNorth() {
        return _latNorth;
    }

    public void setLatNorth(boolean north) {
        _latNorth = north;
        recomputeLatFromDms();
    }

    public void toggleLatHemi() {
        _latNorth = !_latNorth;
        recomputeLatFromDms();
    }

    // ---- Composantes longitude (DMS / DM / hemisphere) ----
    public int getLonDeg() {
        return _lonDeg;
    }

    public void setLonDeg(int d) {
        _lonDeg = d;
        recomputeLonFromDms();
    }

    public int getLonMin() {
        return _lonMin;
    }

    public void setLonMin(int m) {
        _lonMin = m;
        recomputeLonFromDms();
    }

    public double getLonSec() {
        return _lonSec;
    }

    public void setLonSec(double s) {
        _lonSec = s;
        recomputeLonFromDms();
    }

    public double getLonMinDec() {
        return _lonMinDec;
    }

    public void setLonMinDec(double md) {
        _lonMinDec = md;
        recomputeLonFromDm();
    }

    public boolean isLonEast() {
        return _lonEast;
    }

    public void setLonEast(boolean east) {
        _lonEast = east;
        recomputeLonFromDms();
    }

    public void toggleLonHemi() {
        _lonEast = !_lonEast;
        recomputeLonFromDms();
    }

    // Cap strict pole / antimeridien : borne le decimal au globe (et re-synchronise DMS/DM).
    public void clampToGlobe() {
        setLatitude(Math.max(-90.0, Math.min(90.0, latitude)));
        setLongitude(Math.max(-180.0, Math.min(180.0, longitude)));
    }

    // ---- Formateurs d'affichage (Locale.US, point decimal) ----
    // DD : decimal signe (ex. "47.806239°" / "-5.503417°").
    // DM : hemisphere + degres + minutes decimales (ex. "N 47°48.3743'").
    // DMS : hemisphere + degres + minutes + secondes (ex. "N 47°48'22.46''").

    public String latDD() {
        return String.format(Locale.US, "%.6f°", latitude);
    }

    public String lonDD() {
        return String.format(Locale.US, "%.6f°", longitude);
    }

    public String latDM() {
        return String.format(Locale.US, "%s %d°%07.4f'",
                _latNorth ? "N" : "S", _latDeg, _latMinDec);
    }

    public String lonDM() {
        return String.format(Locale.US, "%s %d°%07.4f'",
                _lonEast ? "E" : "W", _lonDeg, _lonMinDec);
    }

    public String latDMS() {
        return String.format(Locale.US, "%s %d°%02d'%05.2f''",
                _latNorth ? "N" : "S", _latDeg, _latMin, _latSec);
    }

    public String lonDMS() {
        return String.format(Locale.US, "%s %d°%02d'%05.2f''",
                _lonEast ? "E" : "W", _lonDeg, _lonMin, _lonSec);
    }
}
