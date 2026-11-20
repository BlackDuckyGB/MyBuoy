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
 * harmonic prediction.
 *
 * Prediction convention ported from pyTMD (MIT License,
 * Copyright (c) 2017 Tyler C Sutterley):
 *
 *     h(t) = sum over constituents of
 *            f * ( Re(hc) * cos(theta) - Im(hc) * sin(theta) )
 *
 * with theta = radians(G) + u, and hc = amplitude * exp(-i * phase) as
 * stored by the model.
 */
package com.gb.mybuoy.tides;

import java.util.ArrayList;
import java.util.List;

/** Predicts the ocean tide height at one fixed position. */
public final class TidePredictor
{
    /** Modified Julian Date of the Unix epoch, 1970-01-01T00:00:00Z. */
    public static final double MJD_UNIX_EPOCH = 40587.0;

    private static final double SECONDS_PER_DAY = 86400.0;

    private final List<String> _names = new ArrayList<>();
    private final List<double[]> _constants = new ArrayList<>();

    /**
     * Extracts the harmonic constants at the given position. Constituents
     * that are fully masked around the point are simply dropped.
     */
    public TidePredictor(TideGrid grid, double latitude, double longitude)
    {
        List<String> gridNames = grid.names();

        for (int index = 0; index < gridNames.size(); index++)
        {
            double[] sampled = grid.sample(index, latitude, longitude);

            if (sampled == null)
            {
                continue;
            }

            _names.add(gridNames.get(index));
            _constants.add(sampled);
        }
    }

    /** Constituents actually resolved at this position. */
    public List<String> resolvedConstituents()
    {
        return _names;
    }

    /** Tide height above mean sea level, in metres, at a Unix time. */
    public double heightAt(long unixSeconds)
    {
        double mjd = MJD_UNIX_EPOCH + unixSeconds / SECONDS_PER_DAY;

        return _heightAtMjd(mjd);
    }

    /**
     * Amplitude of a single constituent at this position, in centimetres.
     * Handy to report what the model actually holds here.
     */
    public double amplitudeOf(String name)
    {
        int index = _names.indexOf(name);

        if (index < 0)
        {
            return Double.NaN;
        }

        double[] constants = _constants.get(index);

        return Math.hypot(constants[0], constants[1]);
    }

    // The nodal factors depend on the lunar perigee and node, which drift by a tenth of a
    // degree a day. Recomputing them at every sample cost more than the harmonic sum itself,
    // and the chart datum walks a million samples: they are held for the day instead.
    private long _cachedDay = Long.MIN_VALUE;
    private double[] _cachedSchureman = null;
    private double[][] _cachedModulations = null;

    /** Coarse sweep result: the moments worth looking at more closely. */
    public static final class Candidates
    {
        public final List<Long> lowest = new ArrayList<>();
        public final List<Long> highest = new ArrayList<>();
    }

    /**
     * Walks a long stretch quickly, returning the moments near its lowest and highest
     * levels rather than the levels themselves.
     *
     * Straight evaluation costs two transcendental calls per constituent per sample, and
     * the chart datum needs hundreds of thousands of samples. Here each constituent's angle
     * advances by a constant amount per step, so its sine and cosine are carried forward by
     * rotation -- four multiplications instead of a sine and a cosine. The angles are seeded
     * exactly again at every day boundary, where the nodal factors are refreshed anyway, so
     * the recurrence never runs long enough to drift; and since only candidate *times* come
     * out of here, the caller still measures them with the exact method.
     */
    public Candidates scanCoarse(long start, long end, long step, double marginMetres)
    {
        Candidates candidates = new Candidates();

        int count = _names.size();

        double[] cosine = new double[count];
        double[] sine = new double[count];
        double[] cosStep = new double[count];
        double[] sinStep = new double[count];

        double lowestSeen = Double.MAX_VALUE;
        double highestSeen = -Double.MAX_VALUE;

        long day = Long.MIN_VALUE;

        for (long moment = start; moment < end; moment += step)
        {
            double mjd = MJD_UNIX_EPOCH + moment / SECONDS_PER_DAY;
            long thisDay = (long) Math.floor(mjd);

            if (thisDay != day)
            {
                _seed(mjd, step, cosine, sine, cosStep, sinStep);
                day = thisDay;
            }

            double centimetres = 0.0;

            for (int index = 0; index < count; index++)
            {
                double[] constants = _constants.get(index);
                double factor = _cachedModulations[index][0];

                centimetres += factor * (constants[0] * cosine[index] - constants[1] * sine[index]);
            }

            double height = centimetres / 100.0;

            if (height < lowestSeen - marginMetres)
            {
                candidates.lowest.clear();
            }

            if (height < lowestSeen)
            {
                lowestSeen = height;
            }

            if (height <= lowestSeen + marginMetres)
            {
                candidates.lowest.add(moment);
            }

            if (height > highestSeen + marginMetres)
            {
                candidates.highest.clear();
            }

            if (height > highestSeen)
            {
                highestSeen = height;
            }

            if (height >= highestSeen - marginMetres)
            {
                candidates.highest.add(moment);
            }

            for (int index = 0; index < count; index++)
            {
                double nextCosine = cosine[index] * cosStep[index] - sine[index] * sinStep[index];
                double nextSine = sine[index] * cosStep[index] + cosine[index] * sinStep[index];

                cosine[index] = nextCosine;
                sine[index] = nextSine;
            }
        }

        return candidates;
    }

    /** Recomputes the exact angles, and the rotation one step advances them by. */
    private void _seed(double mjd, long step,
                       double[] cosine, double[] sine, double[] cosStep, double[] sinStep)
    {
        double[] here = _anglesAt(mjd);
        double[] next = _anglesAt(mjd + step / SECONDS_PER_DAY);

        for (int index = 0; index < here.length; index++)
        {
            cosine[index] = Math.cos(here[index]);
            sine[index] = Math.sin(here[index]);

            double advance = next[index] - here[index];

            cosStep[index] = Math.cos(advance);
            sinStep[index] = Math.sin(advance);
        }
    }

    /** Angle of every constituent at a moment, nodal correction included. */
    private double[] _anglesAt(double mjd)
    {
        double[] longitudes = TideAstro.meanLongitudesAstro5(mjd);

        double s = longitudes[0];
        double h = longitudes[1];
        double p = longitudes[2];
        double n = longitudes[3];
        double pp = longitudes[4];

        double hour = 24.0 * (mjd - Math.floor(mjd));
        double tau = 15.0 * hour - s + h;

        double[] schureman = TideAstro.schuremanArguments(
                Math.toRadians(p), Math.toRadians(n));

        _cachedSchureman = schureman;
        _cachedModulations = new double[_names.size()][];

        double[] angles = new double[_names.size()];

        for (int index = 0; index < _names.size(); index++)
        {
            String name = _names.get(index);

            _cachedModulations[index] = TideConstituents.nodalModulation(name, schureman);

            double argument = TideConstituents.equilibriumArgument(name, tau, s, h, p, n, pp);

            angles[index] = Math.toRadians(argument) + _cachedModulations[index][1];
        }

        _cachedDay = (long) Math.floor(mjd);

        return angles;
    }

    private double _heightAtMjd(double mjd)
    {
        double[] longitudes = TideAstro.meanLongitudesAstro5(mjd);

        double s = longitudes[0];
        double h = longitudes[1];
        double p = longitudes[2];
        double n = longitudes[3];
        double pp = longitudes[4];

        double dayFraction = mjd - Math.floor(mjd);
        double hour = 24.0 * dayFraction;

        // Mean lunar time in degrees.
        double tau = 15.0 * hour - s + h;

        long day = (long) Math.floor(mjd);

        if (day != _cachedDay || _cachedModulations == null)
        {
            _cachedSchureman = TideAstro.schuremanArguments(
                    Math.toRadians(p), Math.toRadians(n));

            _cachedModulations = new double[_names.size()][];

            for (int index = 0; index < _names.size(); index++)
            {
                _cachedModulations[index] =
                        TideConstituents.nodalModulation(_names.get(index), _cachedSchureman);
            }

            _cachedDay = day;
        }

        double heightCentimetres = 0.0;

        for (int index = 0; index < _names.size(); index++)
        {
            String name = _names.get(index);
            double[] constants = _constants.get(index);

            double argument = TideConstituents.equilibriumArgument(
                    name, tau, s, h, p, n, pp);

            double[] modulation = _cachedModulations[index];

            double theta = Math.toRadians(argument) + modulation[1];

            heightCentimetres += modulation[0]
                    * (constants[0] * Math.cos(theta) - constants[1] * Math.sin(theta));
        }

        return heightCentimetres / 100.0;
    }
}
