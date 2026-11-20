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
 * One day of sky at one place, worked out once and then read from.
 *
 * This is to the Sun and Moon page what TideModel is to the tide page: the
 * whole of the arithmetic happens in the constructor, on a worker thread, and
 * what the page does afterwards is read fields. Nothing here may be called from
 * a draw.
 *
 * The day is handed in as a window rather than taken from a calendar, because
 * the page decides what "today" means: it is the user's local day, and this
 * class has no business knowing about time zones.
 *
 * Where the sky never crosses the horizon, the rises and sets are simply
 * absent. That is the honest answer in polar summer and polar night, and it is
 * also the ordinary answer about once a month for the Moon, which rises some
 * fifty minutes later each day and therefore skips a civil day from time to
 * time.
 */
package com.gb.mybuoy.astro;

/** Rises, sets, twilights and phase for one day at one position. */
public final class AstroDay
{
    /** Radius of the Sun, in kilometres. */
    private static final double SUN_RADIUS_KM = 696000.0;

    /** Radius of the Moon, in kilometres. */
    private static final double MOON_RADIUS_KM = 1737.4;

    private final double _latitudeRadians;
    private final double _longitudeRadians;

    private final long _fromUnixSeconds;
    private final long _toUnixSeconds;

    private final RiseSet.Window _sun;
    private final RiseSet.Window _moon;
    private final RiseSet.Window _civil;
    private final RiseSet.Window _nautical;
    private final RiseSet.Window _astronomical;

    private final MoonPhase.Phase _phase;

    private final double _moonDistanceKm;

    /**
     * Works the whole day out. Seconds of arithmetic are not involved, but a few
     * thousand trigonometric series are, so this belongs on a worker thread.
     *
     * @param latitudeDegrees  observer's latitude, positive north
     * @param longitudeDegrees observer's longitude, positive east
     * @param fromUnixSeconds  start of the local day
     * @param toUnixSeconds    end of the local day
     */
    public AstroDay(double latitudeDegrees, double longitudeDegrees,
                    long fromUnixSeconds, long toUnixSeconds)
    {
        _latitudeRadians = Math.toRadians(latitudeDegrees);
        _longitudeRadians = Math.toRadians(longitudeDegrees);

        _fromUnixSeconds = fromUnixSeconds;
        _toUnixSeconds = toUnixSeconds;

        long middle = (fromUnixSeconds + toUnixSeconds) / 2L;

        double middleTt = AstroTime.julianDateTt(middle);

        // The semidiameters are taken at the middle of the day. Both bodies change
        // size slowly enough that the error is under a second of time, and taking
        // them afresh at each step would mean solving for the target while chasing it.
        double sunDistanceKm = SunErfa.at(middleTt).distanceKm();

        _moonDistanceKm = MoonErfa.at(middleTt).distanceKm();

        RiseSet.Altitude sun = RiseSet.sun(_longitudeRadians, _latitudeRadians);
        RiseSet.Altitude moon = RiseSet.moon(_longitudeRadians, _latitudeRadians);

        _sun = RiseSet.scan(sun, fromUnixSeconds, toUnixSeconds,
                RiseSet.upperLimbTarget(SUN_RADIUS_KM, sunDistanceKm));

        _moon = RiseSet.scan(moon, fromUnixSeconds, toUnixSeconds,
                RiseSet.upperLimbTarget(MOON_RADIUS_KM, _moonDistanceKm));

        _civil = RiseSet.scan(sun, fromUnixSeconds, toUnixSeconds, RiseSet.CIVIL_RADIANS);
        _nautical = RiseSet.scan(sun, fromUnixSeconds, toUnixSeconds, RiseSet.NAUTICAL_RADIANS);
        _astronomical = RiseSet.scan(sun, fromUnixSeconds, toUnixSeconds,
                RiseSet.ASTRONOMICAL_RADIANS);

        _phase = MoonPhase.at(middleTt);

    }

    public long fromUnixSeconds()
    {
        return _fromUnixSeconds;
    }

    public long toUnixSeconds()
    {
        return _toUnixSeconds;
    }

    public RiseSet.Window sun()
    {
        return _sun;
    }

    public RiseSet.Window moon()
    {
        return _moon;
    }

    public RiseSet.Window civilTwilight()
    {
        return _civil;
    }

    public RiseSet.Window nauticalTwilight()
    {
        return _nautical;
    }

    public RiseSet.Window astronomicalTwilight()
    {
        return _astronomical;
    }

    public MoonPhase.Phase moonPhase()
    {
        return _phase;
    }

    /** Distance to the Moon at the middle of the day, which tells perigee from apogee. */
    public double moonDistanceKm()
    {
        return _moonDistanceKm;
    }

    /**
     * How long the Sun is up, in seconds, or a negative number when it never rises
     * or never sets and the question has no answer.
     */
    public long daylightSeconds()
    {
        if (_sun.neverCrossed()) {
            return _sun.upThroughout() ? (_toUnixSeconds - _fromUnixSeconds) : 0L;
        }

        if (_sun.rises().isEmpty() || _sun.sets().isEmpty()) {
            return -1L;
        }

        return _sun.sets().get(0) - _sun.rises().get(0);
    }

    /** Where the Sun stands right now, as a bearing and a height. */
    public Topocentric.Horizontal sunAt(long unixSeconds)
    {
        double jdTt = AstroTime.julianDateTt(unixSeconds);

        SunErfa.Position position = SunErfa.at(jdTt);

        return _horizontal(position.longitudeRadians(), position.latitudeRadians(),
                0.0, unixSeconds, jdTt);
    }

    /** Where the Moon stands right now, seen from the ground rather than the centre. */
    public Topocentric.Horizontal moonAt(long unixSeconds)
    {
        double jdTt = AstroTime.julianDateTt(unixSeconds);

        MoonErfa.Position position = MoonErfa.at(jdTt);

        return _horizontal(position.longitudeRadians(), position.latitudeRadians(),
                position.distanceKm(), unixSeconds, jdTt);
    }

    /**
     * How far the Moon's lit edge is turned from straight up on the screen.
     *
     * The phase gives where the Sun is relative to the Moon on the sky, reckoned from
     * celestial north; the parallactic angle gives where celestial north is relative
     * to the zenith. The difference is what the user actually sees, and it is what a
     * drawing of the phase must be rotated by.
     */
    public double moonTiltRadians(long unixSeconds)
    {
        double jdTt = AstroTime.julianDateTt(unixSeconds);

        MoonErfa.Position moon = MoonErfa.at(jdTt);

        Topocentric.Equatorial equatorial = Topocentric.equatorialFromEcliptic(
                moon.longitudeRadians(), moon.latitudeRadians(),
                AstroTime.meanObliquity(jdTt));

        double sidereal = AstroTime.greenwichMeanSiderealTime(
                AstroTime.julianDateUtc(unixSeconds), jdTt);

        double hourAngle = sidereal + _longitudeRadians - equatorial.rightAscensionRadians();

        double parallactic = Topocentric.parallacticAngle(hourAngle,
                equatorial.declinationRadians(), _latitudeRadians);

        return MoonPhase.at(jdTt).brightLimbAngleRadians() - parallactic;
    }

    /** The Sun's local hour angle, zero on the meridian: what a sundial reads. */
    public double sunHourAngleRadians(long unixSeconds)
    {
        double jdTt = AstroTime.julianDateTt(unixSeconds);

        SunErfa.Position sun = SunErfa.at(jdTt);

        Topocentric.Equatorial equatorial = Topocentric.equatorialFromEcliptic(
                sun.longitudeRadians(), sun.latitudeRadians(), AstroTime.meanObliquity(jdTt));

        double sidereal = AstroTime.greenwichMeanSiderealTime(
                AstroTime.julianDateUtc(unixSeconds), jdTt);

        return sidereal + _longitudeRadians - equatorial.rightAscensionRadians();
    }

    private Topocentric.Horizontal _horizontal(double eclipticLongitude, double eclipticLatitude,
                                               double distanceKm, long unixSeconds, double jdTt)
    {
        Topocentric.Equatorial equatorial = Topocentric.equatorialFromEcliptic(
                eclipticLongitude, eclipticLatitude, AstroTime.meanObliquity(jdTt));

        double sidereal = AstroTime.greenwichMeanSiderealTime(
                AstroTime.julianDateUtc(unixSeconds), jdTt);

        return Topocentric.horizontalFromEquatorial(equatorial, sidereal,
                _longitudeRadians, _latitudeRadians, distanceKm);
    }
}
