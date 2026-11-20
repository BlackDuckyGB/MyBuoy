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

/*
 * Everything the tide page needs, prepared once, off the drawing thread.
 *
 * Building one costs seconds: the chart datum is the lowest level the prediction reaches
 * over a full 18.6 year nodal cycle, which is a million evaluations. Nothing here may be
 * called from a draw().
 */
package com.gb.mybuoy.tides;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Tide prediction at one position, ready to be displayed. */
public final class TideModel
{
    /** Days of curve prepared before now: enough to look back at last night, no more. */
    public static final int DAYS_BEFORE = 1;

    /** Days of curve prepared ahead. */
    public static final int DAYS_AFTER = 30;

    /** Sampling step of the prepared curve, in seconds. */
    public static final int CURVE_STEP_SECONDS = 300;

    /** Fishing window: how long before a high water it opens. */
    public static final long FISHING_BEFORE_SECONDS = 2L * 3600L;

    /** Fishing window: how long after a high water it closes. */
    public static final long FISHING_AFTER_SECONDS = 3600L;

    /** One high or low water. */
    public static final class Tide
    {
        public final long unixSeconds;
        public final double heightMetres;
        public final boolean high;

        Tide(long unixSeconds, double heightMetres, boolean high)
        {
            this.unixSeconds = unixSeconds;
            this.heightMetres = heightMetres;
            this.high = high;
        }
    }

    /** A stretch of time worth fishing, around a high water. */
    public static final class Window
    {
        public final long fromUnixSeconds;
        public final long toUnixSeconds;

        Window(long fromUnixSeconds, long toUnixSeconds)
        {
            this.fromUnixSeconds = fromUnixSeconds;
            this.toUnixSeconds = toUnixSeconds;
        }
    }

    /** Spacing of the grid, and the length of a degree, for turning cells into kilometres. */
    private static final double CELL_DEGREES = 0.125;

    private static final double KM_PER_DEGREE = 111.32;

    private final double _awayKm;
    private final double _latitude;
    private final double _longitude;
    private final double _datumMetres;
    private final long _fromUnixSeconds;
    private final long _toUnixSeconds;
    private final float[] _heights;
    private final List<Tide> _tides;
    private final List<Window> _windows;
    private final int _constituentCount;

    /**
     * Reads the grid, extracts the constants at the position, works out the chart datum and
     * prepares the whole curve. Seconds of work: call it from a background thread.
     */
    public TideModel(String gridPath, double latitude, double longitude, long nowUnixSeconds)
            throws IOException
    {
        this(gridPath, latitude, longitude, nowUnixSeconds, Double.NaN, 0.0);
    }

    public TideModel(String gridPath, double latitude, double longitude, long nowUnixSeconds,
                     double knownDatumMetres) throws IOException
    {
        this(gridPath, latitude, longitude, nowUnixSeconds, knownDatumMetres, 0.0);
    }

    /**
     * Same, with the chart datum already known.
     *
     * Working it out is the only slow part here -- it walks a whole nodal cycle -- and it
     * never changes for a place. Handing it back saves the wait on every later visit.
     */
    /**
     * Same, and allowed to look elsewhere when the model holds nothing here.
     *
     * EOT20 is built from satellite altimetry, which cannot measure close to land, so its
     * coastline is drawn well offshore: a bay can sit twenty kilometres from the nearest
     * point it covers. Rather than refusing to answer, the tide is read at the nearest point
     * that does hold data, and how far that was is reported so the page can say it.
     *
     * @param reachKm how far it may look, in kilometres; zero forbids looking elsewhere
     */
    public TideModel(String gridPath, double latitude, double longitude, long nowUnixSeconds,
                     double knownDatumMetres, double reachKm) throws IOException
    {
        TideGrid grid = new TideGrid(gridPath);

        double usedLatitude = latitude;
        double usedLongitude = longitude;
        double away = 0.0;

        TidePredictor predictor = new TidePredictor(grid, latitude, longitude);

        if (predictor.resolvedConstituents().isEmpty() && reachKm > 0.0)
        {
            double cellKm = CELL_DEGREES * KM_PER_DEGREE
                    * Math.max(0.2, Math.cos(Math.toRadians(latitude)));

            int rings = (int) Math.ceil(reachKm / cellKm);

            double[] nearest = grid.nearestDataCell(latitude, longitude, rings);

            if (nearest != null)
            {
                double foundLongitude = nearest[1] > 180.0 ? nearest[1] - 360.0 : nearest[1];
                double distance = _distanceKm(latitude, longitude, nearest[0], foundLongitude);

                if (distance <= reachKm)
                {
                    usedLatitude = nearest[0];
                    usedLongitude = foundLongitude;
                    away = distance;

                    predictor = new TidePredictor(grid, usedLatitude, usedLongitude);
                }
            }
        }

        _latitude = usedLatitude;
        _longitude = usedLongitude;
        _awayKm = away;

        _constituentCount = predictor.resolvedConstituents().size();

        if (_constituentCount == 0)
        {
            throw new IOException("no tidal data at this position");
        }

        _fromUnixSeconds = nowUnixSeconds - (long) DAYS_BEFORE * 86400L;
        _toUnixSeconds = nowUnixSeconds + (long) DAYS_AFTER * 86400L;

        _datumMetres = Double.isNaN(knownDatumMetres)
                ? TideDatum.compute(predictor, nowUnixSeconds).lowest
                : knownDatumMetres;

        int count = (int) ((_toUnixSeconds - _fromUnixSeconds) / CURVE_STEP_SECONDS) + 1;
        _heights = new float[count];

        for (int index = 0; index < count; index++)
        {
            long moment = _fromUnixSeconds + (long) index * CURVE_STEP_SECONDS;
            _heights[index] = (float) (predictor.heightAt(moment) - _datumMetres);
        }

        _tides = new ArrayList<>();

        for (TideExtrema.Event event : TideExtrema.find(predictor, _fromUnixSeconds, _toUnixSeconds, 60))
        {
            _tides.add(new Tide(event.unixSeconds,
                    event.heightMetres - _datumMetres, event.high));
        }

        _windows = new ArrayList<>();

        for (Tide tide : _tides)
        {
            if (!tide.high)
            {
                continue;
            }

            _windows.add(new Window(tide.unixSeconds - FISHING_BEFORE_SECONDS,
                    tide.unixSeconds + FISHING_AFTER_SECONDS));
        }
    }

    /** Chart datum used, in metres below mean sea level, so it can be kept for next time. */
    public double chartDatumMetres()
    {
        return _datumMetres;
    }

    /** How far from the asked position the tide was actually read, in km; zero when exact. */
    public double awayKm()
    {
        return _awayKm;
    }

    private static double _distanceKm(double fromLatitude, double fromLongitude,
                                      double toLatitude, double toLongitude)
    {
        double middle = Math.toRadians((fromLatitude + toLatitude) / 2.0);

        double north = (toLatitude - fromLatitude) * KM_PER_DEGREE;
        double east = (toLongitude - fromLongitude) * KM_PER_DEGREE * Math.cos(middle);

        return Math.hypot(north, east);
    }

    public double latitude()
    {
        return _latitude;
    }

    public double longitude()
    {
        return _longitude;
    }

    /** Number of constituents resolved here, out of the seventeen the grid holds. */
    public int constituentCount()
    {
        return _constituentCount;
    }

    /** First and last moment the prepared curve covers. */
    public long fromUnixSeconds()
    {
        return _fromUnixSeconds;
    }

    public long toUnixSeconds()
    {
        return _toUnixSeconds;
    }

    /** Prepared heights above chart datum, one every CURVE_STEP_SECONDS. */
    public float[] heights()
    {
        return _heights;
    }

    /** Every high and low water of the prepared window, in time order. */
    public List<Tide> tides()
    {
        return _tides;
    }

    /** The stretches worth fishing, in time order. */
    public List<Window> windows()
    {
        return _windows;
    }

    /** Height above chart datum at a moment, interpolated from the prepared curve. */
    public double heightAt(long unixSeconds)
    {
        return _heightAt(unixSeconds);
    }

    /**
     * The tides still to come after a moment, at most two of them. Fewer when the moment
     * sits at the very end of the prepared window.
     */
    public List<Tide> nextTides(long unixSeconds, int howMany)
    {
        List<Tide> next = new ArrayList<>();

        for (Tide tide : _tides)
        {
            if (tide.unixSeconds <= unixSeconds)
            {
                continue;
            }

            next.add(tide);

            if (next.size() >= howMany)
            {
                break;
            }
        }

        return next;
    }

    /** The last tide before a moment, or null when the window starts after it. */
    public Tide previousTide(long unixSeconds)
    {
        Tide found = null;

        for (Tide tide : _tides)
        {
            if (tide.unixSeconds >= unixSeconds)
            {
                break;
            }

            found = tide;
        }

        return found;
    }

    /** True when the water is rising at that moment. */
    public boolean isRising(long unixSeconds)
    {
        return _heightAt(unixSeconds + 600L) > _heightAt(unixSeconds);
    }

    /** True when the moment falls inside a fishing window. */
    public boolean isFishingTime(long unixSeconds)
    {
        for (Window window : _windows)
        {
            if (unixSeconds >= window.fromUnixSeconds && unixSeconds <= window.toUnixSeconds)
            {
                return true;
            }
        }

        return false;
    }

    private double _heightAt(long unixSeconds)
    {
        double position = (double) (unixSeconds - _fromUnixSeconds) / CURVE_STEP_SECONDS;

        if (position <= 0.0)
        {
            return _heights[0];
        }

        if (position >= _heights.length - 1)
        {
            return _heights[_heights.length - 1];
        }

        int index = (int) position;
        double fraction = position - index;

        return _heights[index] * (1.0 - fraction) + _heights[index + 1] * fraction;
    }
}
