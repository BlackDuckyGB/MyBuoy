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
 * When a body crosses a given height, and when it is highest.
 *
 * The method is deliberately the blunt one: walk the day in short steps, watch
 * for the altitude changing sign about the target, and close in on each
 * crossing by halving. The clever alternative, interpolating from three
 * positions a day apart, is faster and is what the almanacs did when computing
 * was dear; it also quietly assumes there is exactly one rise and one set, and
 * that assumption fails precisely where this has to be right. The Moon rises
 * roughly fifty minutes later each day, so about once a month a civil day holds
 * no moonrise at all. Near the poles the Sun can do the same for months. The
 * walk finds what is there and reports nothing when there is nothing, which is
 * the honest answer.
 *
 * The cost is a few hundred positions per day, each a trigonometric series of a
 * few hundred terms. That is milliseconds, and it happens on a worker thread,
 * never in a draw.
 */
package com.gb.mybuoy.astro;

import java.util.ArrayList;
import java.util.List;

/** Rises, sets and meridian passages for a body over a window of time. */
public final class RiseSet
{
    /** Standard refraction at the horizon: 34 arcminutes, the usual convention. */
    public static final double HORIZON_REFRACTION_RADIANS = Math.toRadians(-34.0 / 60.0);

    /** Civil twilight: the Sun's centre six degrees below the horizon. */
    public static final double CIVIL_RADIANS = Math.toRadians(-6.0);

    /** Nautical twilight, where the horizon stops being usable for a sextant. */
    public static final double NAUTICAL_RADIANS = Math.toRadians(-12.0);

    /** Astronomical twilight, beyond which the sky is as dark as it will get. */
    public static final double ASTRONOMICAL_RADIANS = Math.toRadians(-18.0);

    /** Step of the walk. Neither body moves more than a degree in two minutes. */
    private static final long STEP_SECONDS = 120L;

    /** Halvings applied to each crossing: two minutes down to well under a second. */
    private static final int REFINEMENTS = 10;

    /** Where a body stands at a moment, whatever that body is. */
    public interface Altitude
    {
        /** @return height above the horizon in radians, refraction excluded */
        double radiansAt(long unixSeconds);

        /**
         * @return the local hour angle in radians, folded to plus or minus pi: zero
         *         when the body is on the meridian, negative before, positive after
         */
        double hourAngleAt(long unixSeconds);
    }

    /** What happened to a body between two moments. */
    public static final class Window
    {
        private final List<Long> _rises;
        private final List<Long> _sets;
        private final long _transitSeconds;
        private final double _transitAltitudeRadians;
        private final boolean _upThroughout;

        private Window(List<Long> rises, List<Long> sets, long transitSeconds,
                       double transitAltitudeRadians, boolean upThroughout)
        {
            _rises = rises;
            _sets = sets;
            _transitSeconds = transitSeconds;
            _transitAltitudeRadians = transitAltitudeRadians;
            _upThroughout = upThroughout;
        }

        /** Every crossing upwards, in order. Usually one, sometimes none, rarely two. */
        public List<Long> rises()
        {
            return _rises;
        }

        /** Every crossing downwards, in order. */
        public List<Long> sets()
        {
            return _sets;
        }

        /** When the body was highest. Always meaningful, even with no rise or set. */
        public long transitSeconds()
        {
            return _transitSeconds;
        }

        public double transitAltitudeRadians()
        {
            return _transitAltitudeRadians;
        }

        /** True when the body never crossed: either up the whole time, or down the whole time. */
        public boolean neverCrossed()
        {
            return _rises.isEmpty() && _sets.isEmpty();
        }

        /** Meaningful only when nothing crossed: whether it stayed up or stayed down. */
        public boolean upThroughout()
        {
            return _upThroughout;
        }
    }

    private RiseSet()
    {
    }

    /**
     * Walks the window and reports every crossing of the target height.
     *
     * @param targetRadians the height counted as the horizon for this purpose: the
     *                      refracted horizon for a rise, or one of the twilight
     *                      depths
     */
    public static Window scan(Altitude body, long fromUnixSeconds, long toUnixSeconds,
                              double targetRadians)
    {
        List<Long> rises = new ArrayList<Long>();
        List<Long> sets = new ArrayList<Long>();

        long highestAt = fromUnixSeconds;
        double highest = body.radiansAt(fromUnixSeconds);

        double previous = highest - targetRadians;
        long previousAt = fromUnixSeconds;

        boolean startedUp = previous > 0.0;

        for (long t = fromUnixSeconds + STEP_SECONDS; t <= toUnixSeconds; t += STEP_SECONDS) {

            double altitude = body.radiansAt(t);

            if (altitude > highest) {
                highest = altitude;
                highestAt = t;
            }

            double current = altitude - targetRadians;

            if ((previous < 0.0) && (current >= 0.0)) {
                rises.add(_crossing(body, previousAt, t, targetRadians));
            } else if ((previous > 0.0) && (current <= 0.0)) {
                sets.add(_crossing(body, previousAt, t, targetRadians));
            }

            previous = current;
            previousAt = t;
        }

        // The meridian passage, which is where the hour angle is zero. NOT the moment
        // the body is highest: for the Sun the two coincide, but the Moon moves in
        // declination fast enough to part them, by a quarter of an hour at high
        // latitudes where the curve of altitude is nearly flat. The almanacs publish
        // the meridian passage, so that is what is computed here.
        long refinedTransit = _transit(body, fromUnixSeconds, toUnixSeconds, highestAt);

        return new Window(rises, sets, refinedTransit, body.radiansAt(refinedTransit), startedUp);
    }

    /** The Sun, as a height above the observer's horizon. */
    public static Altitude sun(final double longitudeRadians, final double latitudeRadians)
    {
        return new Altitude() {
            @Override
            public double radiansAt(long unixSeconds)
            {
                double jdTt = AstroTime.julianDateTt(unixSeconds);

                SunErfa.Position position = SunErfa.at(jdTt);

                return _altitude(position.longitudeRadians(), position.latitudeRadians(),
                        0.0, unixSeconds, jdTt, longitudeRadians, latitudeRadians);
            }

            @Override
            public double hourAngleAt(long unixSeconds)
            {
                double jdTt = AstroTime.julianDateTt(unixSeconds);

                SunErfa.Position position = SunErfa.at(jdTt);

                return _hourAngle(position.longitudeRadians(), position.latitudeRadians(),
                        unixSeconds, jdTt, longitudeRadians);
            }
        };
    }

    /** The Moon, seen from the observer's own place rather than the Earth's centre. */
    public static Altitude moon(final double longitudeRadians, final double latitudeRadians)
    {
        return new Altitude() {
            @Override
            public double radiansAt(long unixSeconds)
            {
                double jdTt = AstroTime.julianDateTt(unixSeconds);

                MoonErfa.Position position = MoonErfa.at(jdTt);

                return _altitude(position.longitudeRadians(), position.latitudeRadians(),
                        position.distanceKm(), unixSeconds, jdTt,
                        longitudeRadians, latitudeRadians);
            }

            @Override
            public double hourAngleAt(long unixSeconds)
            {
                double jdTt = AstroTime.julianDateTt(unixSeconds);

                MoonErfa.Position position = MoonErfa.at(jdTt);

                return _hourAngle(position.longitudeRadians(), position.latitudeRadians(),
                        unixSeconds, jdTt, longitudeRadians);
            }
        };
    }

    /**
     * The height at which a body's upper edge sits on the refracted horizon.
     *
     * Rising is reckoned from the first glint of the upper limb, not from the
     * centre, so the centre is one semidiameter lower; refraction lifts the whole
     * thing by another thirty-four arcminutes. For the Sun this is the familiar
     * minus fifty arcminutes. For the Moon it comes out close to the same, but the
     * semidiameter swings by a tenth of a degree between perigee and apogee, which
     * is worth a minute of time.
     *
     * @param radiusKm   radius of the body
     * @param distanceKm its distance at the moment considered
     */
    public static double upperLimbTarget(double radiusKm, double distanceKm)
    {
        if (distanceKm <= 0.0) {
            return HORIZON_REFRACTION_RADIANS;
        }

        return HORIZON_REFRACTION_RADIANS - Math.asin(radiusKm / distanceKm);
    }

    /*
     * The hour angle, folded to plus or minus pi so that the meridian is a change of
     * sign rather than a jump from one turn to the next. Geocentric: the parallax
     * moves a body along the hour circle by far less than the resolution wanted here,
     * and leaving it out keeps the fold clean.
     */
    private static double _hourAngle(double eclipticLongitude, double eclipticLatitude,
                                     long unixSeconds, double jdTt, double observerLongitude)
    {
        Topocentric.Equatorial equatorial = Topocentric.equatorialFromEcliptic(
                eclipticLongitude, eclipticLatitude, AstroTime.meanObliquity(jdTt));

        double sidereal = AstroTime.greenwichMeanSiderealTime(
                AstroTime.julianDateUtc(unixSeconds), jdTt);

        double angle = sidereal + observerLongitude - equatorial.rightAscensionRadians();

        angle = AstroTime.normaliseAngle(angle);

        return (angle > Math.PI) ? (angle - 2.0 * Math.PI) : angle;
    }

    private static double _altitude(double eclipticLongitude, double eclipticLatitude,
                                    double distanceKm, long unixSeconds, double jdTt,
                                    double observerLongitude, double observerLatitude)
    {
        Topocentric.Equatorial equatorial = Topocentric.equatorialFromEcliptic(
                eclipticLongitude, eclipticLatitude, AstroTime.meanObliquity(jdTt));

        double sidereal = AstroTime.greenwichMeanSiderealTime(
                AstroTime.julianDateUtc(unixSeconds), jdTt);

        Topocentric.Horizontal horizontal = Topocentric.horizontalFromEquatorial(
                equatorial, sidereal, observerLongitude, observerLatitude, distanceKm);

        return horizontal.altitudeRadians();
    }

    /* Halves the bracket until the crossing is pinned to well under a second. */
    private static long _crossing(Altitude body, long before, long after, double targetRadians)
    {
        double beforeValue = body.radiansAt(before) - targetRadians;

        for (int step = 0; step < REFINEMENTS; step++) {

            long middle = (before + after) / 2L;

            if (middle == before || middle == after) {
                break;
            }

            double middleValue = body.radiansAt(middle) - targetRadians;

            if ((beforeValue < 0.0) == (middleValue < 0.0)) {
                before = middle;
                beforeValue = middleValue;
            } else {
                after = middle;
            }
        }

        return (before + after) / 2L;
    }

    /*
     * The moment the hour angle changes sign from negative to positive, which is the
     * body crossing the meridian. The scan already knows roughly when the body was
     * highest, and the crossing is within a step or two of it.
     */
    private static long _transit(Altitude body, long from, long to, long nearSeconds)
    {
        // The whole window, not a bracket around the highest point: at high latitudes
        // the Moon can be highest a quarter of an hour away from its meridian passage,
        // and a narrow bracket simply misses the crossing.
        for (long t = from; t < to; t += STEP_SECONDS) {

            long next = Math.min(to, t + STEP_SECONDS);

            if ((body.hourAngleAt(t) < 0.0) && (body.hourAngleAt(next) >= 0.0)) {
                return _hourAngleZero(body, t, next);
            }
        }

        return nearSeconds;
    }

    /* Halves the bracket until the meridian crossing is pinned to the second. */
    private static long _hourAngleZero(Altitude body, long before, long after)
    {
        for (int step = 0; step < REFINEMENTS + 4; step++) {

            long middle = (before + after) / 2L;

            if (middle == before || middle == after) {
                break;
            }

            if (body.hourAngleAt(middle) < 0.0) {
                before = middle;
            } else {
                after = middle;
            }
        }

        return (before + after) / 2L;
    }
}
