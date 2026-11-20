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
 * The pull of the Sun and the Moon on the ground underfoot.
 *
 * This is the earth tide, and it is not the sea tide. The sea tide is water
 * sloshing in a basin, which takes hours to respond and depends on the shape of
 * the coast; the earth tide is the rock itself stretching, which follows the sky
 * almost instantly and is very nearly the same everywhere at a given latitude.
 * Twice a day the ground where you stand rises and falls by some tens of
 * centimetres, and gravity changes with it. You cannot feel it, and it does not
 * change your depth under the keel, because the seabed and the sea surface rise
 * together. It is measurable with an ordinary gravimeter, and it is the reason
 * one exists.
 *
 * What is computed is the tide-generating potential of each body, which is the
 * difference between its pull here and its pull at the centre of the Earth. It
 * comes out of a single expansion in Legendre polynomials of the angle between
 * the body and the vertical: degree two for both bodies, and degree three for
 * the Moon as well, which is close enough for its finite distance to matter at
 * the percent level.
 *
 * The Earth is not rigid, so the potential is not the whole story. It yields
 * under the strain, and the yielding is described by the Love numbers: h for how
 * far the surface moves, k for the extra potential that the displaced mass
 * creates in its turn. The nominal elastic values of the IERS Conventions are
 * used; a real site departs from them by a few percent, which is the business of
 * a gravimetric observatory and not of a navigation application.
 */
package com.gb.mybuoy.astro;

/** The earth tide: how much the ground moves, and how much gravity changes. */
public final class TidePotential
{
    /** Gravitational parameter of the Moon, in cubic metres per second squared. */
    private static final double GM_MOON = 4.9028e12;

    /** Gravitational parameter of the Sun. */
    private static final double GM_SUN = 1.32712440018e20;

    /** Equatorial radius of the Earth, in metres (WGS84). */
    private static final double EARTH_RADIUS_M = 6378137.0;

    /** Flattening of the WGS84 ellipsoid. */
    private static final double FLATTENING = 1.0 / 298.257223563;

    /** Standard gravity, in metres per second squared. */
    private static final double GRAVITY = 9.80665;

    /**
     * Love numbers, nominal elastic values of the IERS Conventions (2010).
     *
     * h is the vertical displacement of the surface per unit of tidal potential
     * divided by gravity; k is the potential raised by the mass that moved.
     */
    private static final double LOVE_H2 = 0.6078;

    private static final double LOVE_K2 = 0.3022;

    /**
     * The gravimetric factor, 1 + h - 3k/2.
     *
     * A gravimeter on a yielding Earth does not read the direct attraction alone.
     * It rises with the ground, which takes it further from the centre and lowers
     * what it reads, and it also feels the pull of the mass that moved. The three
     * effects together come to about sixteen percent more than the rigid value.
     */
    private static final double GRAVIMETRIC_FACTOR = 1.0 + LOVE_H2 - (1.5 * LOVE_K2);

    /** One microgal, in metres per second squared. */
    private static final double MICROGAL = 1.0e-8;

    /** What the sky is doing to the ground at one place and one moment. */
    public static final class Effect
    {
        private final double _gravityMicroGal;
        private final double _upliftMetres;
        private final double _moonShareMicroGal;

        private Effect(double gravityMicroGal, double upliftMetres, double moonShareMicroGal)
        {
            _gravityMicroGal = gravityMicroGal;
            _upliftMetres = upliftMetres;
            _moonShareMicroGal = moonShareMicroGal;
        }

        /**
         * Change in gravity, in microgals. Negative when the bodies are overhead or
         * underfoot and gravity is at its weakest, positive when they are on the
         * horizon.
         */
        public double gravityMicroGal()
        {
            return _gravityMicroGal;
        }

        /** How far the ground has risen, in metres. Negative when it has sunk. */
        public double upliftMetres()
        {
            return _upliftMetres;
        }

        /** The Moon's part of the gravity change, the Sun's being the remainder. */
        public double moonShareMicroGal()
        {
            return _moonShareMicroGal;
        }
    }

    /** A run of the earth tide over time, ready to be drawn. */
    public static final class Series
    {
        private final long _fromUnixSeconds;
        private final int _stepSeconds;
        private final float[] _gravityMicroGal;
        private final double _centimetresPerMicroGal;
        private final float _peakMicroGal;

        private Series(long fromUnixSeconds, int stepSeconds, float[] gravityMicroGal,
                       double centimetresPerMicroGal)
        {
            _fromUnixSeconds = fromUnixSeconds;
            _stepSeconds = stepSeconds;
            _gravityMicroGal = gravityMicroGal;
            _centimetresPerMicroGal = centimetresPerMicroGal;

            float peak = 1.0f;

            for (float value : gravityMicroGal)
            {
                peak = Math.max(peak, Math.abs(value));
            }

            _peakMicroGal = peak;
        }

        /**
         * The strongest the tide gets over the whole run. Worked out once here: it is
         * what every scale on the page is measured against, and walking several thousand
         * samples at each touch of a finger would be waste.
         */
        public float peakMicroGal()
        {
            return _peakMicroGal;
        }

        public long fromUnixSeconds()
        {
            return _fromUnixSeconds;
        }

        public long toUnixSeconds()
        {
            return _fromUnixSeconds + ((long) (_gravityMicroGal.length - 1) * _stepSeconds);
        }

        public int stepSeconds()
        {
            return _stepSeconds;
        }

        public float[] gravityMicroGal()
        {
            return _gravityMicroGal;
        }

        /**
         * What one microgal is worth in centimetres of ground, here. NEGATIVE, and that
         * is the physics, not a slip: the two readings move in opposite directions.
         * With the bodies overhead the ground is pulled up and gravity is at its
         * weakest; with them on the horizon the ground sinks and gravity rises.
         *
         * They are otherwise proportional. Gravity is the vertical gradient of the
         * potential and the uplift is the potential itself, so they share their whole
         * dependence on the angle and differ only by a constant. One curve therefore
         * carries both, which is why the chart has a scale on each side rather than
         * two lines.
         */
        public double centimetresPerMicroGal()
        {
            return _centimetresPerMicroGal;
        }

        /** The value at an instant, interpolated between samples. */
        public float gravityAt(long unixSeconds)
        {
            if (_gravityMicroGal.length == 0) {
                return 0.0f;
            }

            double position = (double) (unixSeconds - _fromUnixSeconds) / _stepSeconds;

            int index = (int) Math.floor(position);

            if (index < 0) {
                return _gravityMicroGal[0];
            }

            if (index >= (_gravityMicroGal.length - 1)) {
                return _gravityMicroGal[_gravityMicroGal.length - 1];
            }

            float fraction = (float) (position - index);

            return _gravityMicroGal[index]
                    + (fraction * (_gravityMicroGal[index + 1] - _gravityMicroGal[index]));
        }
    }

    /**
     * Runs the earth tide over a stretch of time.
     *
     * Ten minutes between samples: the shortest thing in the signal is the twice-daily
     * term at twelve hours and twenty-five minutes, so each cycle still gets
     * seventy-odd points and the curve is smooth without being expensive.
     */
    public static Series series(long fromUnixSeconds, long toUnixSeconds,
                                double latitudeRadians, double longitudeRadians)
    {
        final int step = 600;

        int count = (int) ((toUnixSeconds - fromUnixSeconds) / step) + 1;

        float[] gravity = new float[count];

        for (int n = 0; n < count; n++) {

            long when = fromUnixSeconds + ((long) n * step);

            gravity[n] = (float) at(AstroTime.julianDateTt(when), when,
                    latitudeRadians, longitudeRadians).gravityMicroGal();
        }

        return new Series(fromUnixSeconds, step, gravity,
                _centimetresPerMicroGal(latitudeRadians));
    }

    /*
     * The constant tying the two readings together, from the ratio of the potential to
     * its gradient at this radius: h r / 2 for the degree-two term, in centimetres per
     * microgal once the units are sorted out.
     */
    private static double _centimetresPerMicroGal(double latitudeRadians)
    {
        double radius = _geocentricRadius(latitudeRadians);

        double upliftPerPotential = LOVE_H2 / GRAVITY;

        // Potential and gradient differ by a factor of 2/r, and the gravity reading
        // carries the gravimetric factor as well.
        double potentialPerGradient = radius / 2.0;

        // Negative: the gravity reading was itself defined as minus the gradient.
        return -upliftPerPotential * potentialPerGradient * MICROGAL
                / GRAVIMETRIC_FACTOR * 100.0;
    }

    private TidePotential()
    {
    }

    /**
     * @param julianDateTT     Julian date in Terrestrial Time
     * @param unixSeconds      the same moment, for the sidereal time
     * @param latitudeRadians  observer's latitude, positive north
     * @param longitudeRadians observer's longitude, positive east
     */
    public static Effect at(double julianDateTT, long unixSeconds,
                            double latitudeRadians, double longitudeRadians)
    {
        double radius = _geocentricRadius(latitudeRadians);

        double obliquity = AstroTime.meanObliquity(julianDateTT);

        double sidereal = AstroTime.greenwichMeanSiderealTime(
                AstroTime.julianDateUtc(unixSeconds), julianDateTT);

        MoonErfa.Position moon = MoonErfa.at(julianDateTT);
        SunErfa.Position sun = SunErfa.at(julianDateTT);

        // The zenith angle is what the expansion is written in, and its cosine is the
        // sine of the height above the horizon. Geocentric, deliberately: the parallax
        // belongs to where a body is SEEN, not to the field it raises here.
        double moonCosZenith = _cosZenith(moon.longitudeRadians(), moon.latitudeRadians(),
                obliquity, sidereal, latitudeRadians, longitudeRadians);

        double sunCosZenith = _cosZenith(sun.longitudeRadians(), sun.latitudeRadians(),
                obliquity, sidereal, latitudeRadians, longitudeRadians);

        double moonDistance = moon.distanceKm() * 1000.0;
        double sunDistance = sun.distanceKm() * 1000.0;

        // Potential, in metres squared per second squared.
        double moonPotential = _potential(GM_MOON, radius, moonDistance, moonCosZenith, true);
        double sunPotential = _potential(GM_SUN, radius, sunDistance, sunCosZenith, false);

        // Its vertical gradient, which is what a gravimeter answers to.
        double moonGradient = _gradient(GM_MOON, radius, moonDistance, moonCosZenith, true);
        double sunGradient = _gradient(GM_SUN, radius, sunDistance, sunCosZenith, false);

        double moonGravity = -GRAVIMETRIC_FACTOR * moonGradient / MICROGAL;
        double sunGravity = -GRAVIMETRIC_FACTOR * sunGradient / MICROGAL;

        double uplift = LOVE_H2 * (moonPotential + sunPotential) / GRAVITY;

        return new Effect(moonGravity + sunGravity, uplift, moonGravity);
    }

    /**
     * The strongest a single body can pull at this place: the Moon, straight overhead or
     * straight underfoot, where the angular factor is one.
     *
     * It is the right yardstick for comparing the two bodies on a drawing. Measuring
     * each against the peak of their SUM, as a first attempt did, left neither able to
     * fill its mark: the Moon alone never reaches what the two together do, so both
     * looked weak whatever they were doing. Against this one, the Moon can reach full
     * and the Sun tops out at about forty percent of it, which is the truth of the
     * matter.
     *
     * @param moonDistanceKm the Moon's distance at the moment considered
     */
    public static double strongestSingleMicroGal(double latitudeRadians, double moonDistanceKm)
    {
        double radius = _geocentricRadius(latitudeRadians);

        double gradient = _gradient(GM_MOON, radius, moonDistanceKm * 1000.0, 1.0, true);

        return Math.abs(GRAVIMETRIC_FACTOR * gradient / MICROGAL);
    }

    /*
     * The tide-generating potential: degree two for both bodies, and degree three as
     * well for the Moon, which is near enough for the next term to reach a percent of
     * the first. The Sun's degree-three term is smaller by the ratio of the distances
     * again, which is to say nothing at all.
     */
    private static double _potential(double gm, double radius, double distance,
                                     double cosZenith, boolean thirdDegree)
    {
        double ratio = radius / distance;

        double legendre2 = ((3.0 * cosZenith * cosZenith) - 1.0) / 2.0;

        double potential = (gm / distance) * ratio * ratio * legendre2;

        if (thirdDegree) {

            double legendre3 = ((5.0 * cosZenith * cosZenith * cosZenith)
                    - (3.0 * cosZenith)) / 2.0;

            potential += (gm / distance) * ratio * ratio * ratio * legendre3;
        }

        return potential;
    }

    /* The same expansion differentiated along the vertical. */
    private static double _gradient(double gm, double radius, double distance,
                                    double cosZenith, boolean thirdDegree)
    {
        double ratio = radius / distance;

        double legendre2 = ((3.0 * cosZenith * cosZenith) - 1.0) / 2.0;

        double gradient = 2.0 * (gm / (distance * distance)) * ratio * legendre2;

        if (thirdDegree) {

            double legendre3 = ((5.0 * cosZenith * cosZenith * cosZenith)
                    - (3.0 * cosZenith)) / 2.0;

            gradient += 3.0 * (gm / (distance * distance)) * ratio * ratio * legendre3;
        }

        return gradient;
    }

    /* Cosine of the angle between the body and straight up. */
    private static double _cosZenith(double eclipticLongitude, double eclipticLatitude,
                                     double obliquity, double sidereal,
                                     double latitudeRadians, double longitudeRadians)
    {
        Topocentric.Equatorial equatorial = Topocentric.equatorialFromEcliptic(
                eclipticLongitude, eclipticLatitude, obliquity);

        double hourAngle = sidereal + longitudeRadians - equatorial.rightAscensionRadians();

        double declination = equatorial.declinationRadians();

        return (Math.sin(latitudeRadians) * Math.sin(declination))
                + (Math.cos(latitudeRadians) * Math.cos(declination) * Math.cos(hourAngle));
    }

    /* Distance from the centre of the Earth to the surface at a given latitude. */
    private static double _geocentricRadius(double latitudeRadians)
    {
        double sine = Math.sin(latitudeRadians);

        return EARTH_RADIUS_M * (1.0 - (FLATTENING * sine * sine));
    }
}
