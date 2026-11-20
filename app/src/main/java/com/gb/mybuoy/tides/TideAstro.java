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
 * astronomical arguments.
 *
 * Algorithms ported from pyTMD (https://github.com/tsutterley/pyTMD),
 * MIT License, Copyright (c) 2017 Tyler C Sutterley.
 * Underlying formulations: Doodson (1921/1941), Schureman (1958).
 */
package com.gb.mybuoy.tides;

/** Astronomical mean longitudes and Schureman auxiliary angles. */
public final class TideAstro
{
    /** Modified Julian Date of the J2000.0 epoch. */
    public static final double MJD_J2000 = 51544.5;

    /** Number of days in a Julian century. */
    public static final double CENTURY = 36525.0;

    private TideAstro()
    {
    }

    /**
     * Mean longitudes S (Moon), H (Sun), P (lunar perigee), N (ascending
     * node) and Ps (solar perigee), in degrees, using the ASTRO5 series.
     * EOT20 is a FES-type model, which uses this variant.
     */
    public static double[] meanLongitudesAstro5(double mjd)
    {
        double t = (mjd - MJD_J2000) / CENTURY;

        double lunarLongitude = polynomial(t,
                218.3164477, 481267.88123421, -1.5786e-3, 1.855835e-6, -1.53388e-8);

        double lunarElongation = polynomial(t,
                297.8501921, 445267.1114034, -1.8819e-3, 1.83195e-6, -8.8445e-9);

        double s = lunarLongitude;
        double h = lunarLongitude - lunarElongation;

        double p = polynomial(t,
                83.3532465, 4069.0137287, -1.032e-2, -1.249172e-5);

        double n = polynomial(t,
                125.04452, -1934.136261, 2.0708e-3, 2.22222e-6);

        double ps = 282.94 + 1.7192 * t;

        return new double[] {
                normalizeDegrees(s),
                normalizeDegrees(h),
                normalizeDegrees(p),
                normalizeDegrees(n),
                normalizeDegrees(ps)
        };
    }

    /**
     * Schureman auxiliary angles derived from P and N (both in radians).
     * Returns { I, xi, nu, Qa, Qu, Ra, Ru, nuPrime, nuSecond } in radians.
     */
    public static double[] schuremanArguments(double pRad, double nRad)
    {
        double inclination = Math.acos(0.913694997 - 0.035692561 * Math.cos(nRad));

        double at1 = Math.atan(1.01883 * Math.tan(nRad / 2.0));
        double at2 = Math.atan(0.64412 * Math.tan(nRad / 2.0));

        double xiRaw = -at1 - at2 + nRad;
        double xi = Math.atan2(Math.sin(xiRaw), Math.cos(xiRaw));
        double nu = at1 - at2;

        double p = pRad - xi;

        double cosI = Math.cos(inclination);
        double q = Math.atan((5.0 * cosI - 1.0) * Math.tan(p) / (7.0 * cosI + 1.0));
        double qa = Math.pow(2.31 + 1.435 * Math.cos(2.0 * p), -0.5);
        double qu = p - q;

        double tanHalfI = Math.tan(inclination / 2.0);
        double rNumerator = Math.sin(2.0 * p);
        double rDenominator = Math.pow(tanHalfI, -2.0) / 6.0 - Math.cos(2.0 * p);
        double ru = Math.atan(rNumerator / rDenominator);

        double term1 = 12.0 * Math.pow(tanHalfI, 2.0) * Math.cos(2.0 * p);
        double term2 = 36.0 * Math.pow(tanHalfI, 4.0);
        double ra = Math.pow(1.0 - term1 + term2, -0.5);

        double primeNumerator = Math.sin(2.0 * inclination) * Math.sin(nu);
        double primeDenominator = Math.sin(2.0 * inclination) * Math.cos(nu) + 0.3347;
        double nuPrime = Math.atan(primeNumerator / primeDenominator);

        double sinI2 = Math.sin(inclination) * Math.sin(inclination);
        double secondNumerator = sinI2 * Math.sin(2.0 * nu);
        double secondDenominator = sinI2 * Math.cos(2.0 * nu) + 0.0727;
        double nuSecond = 0.5 * Math.atan(secondNumerator / secondDenominator);

        return new double[] {
                inclination, xi, nu, qa, qu, ra, ru, nuPrime, nuSecond
        };
    }

    /** Wraps an angle in degrees into [0, 360). */
    public static double normalizeDegrees(double degrees)
    {
        double wrapped = degrees % 360.0;

        if (wrapped < 0.0)
        {
            wrapped += 360.0;
        }

        return wrapped;
    }

    private static double polynomial(double t, double... coefficients)
    {
        double sum = 0.0;
        double power = 1.0;

        for (double coefficient : coefficients)
        {
            sum += coefficient * power;
            power *= t;
        }

        return sum;
    }
}
