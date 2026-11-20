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
 * ===========================================================================
 * THIS FILE IS A JAVA PORT OF ERFA's moon98.c (the eraMoon98 routine).
 *
 * The copyright, conditions and disclaimer reproduced below belong to that
 * material and travel with it. The Java adaptation is Golden-Block's and is
 * distributed under the terms at the top of this file; the conditions below
 * continue to apply to everything derived from ERFA, under either of those
 * terms.
 *
 * Source: https://github.com/liberfa/erfa (src/moon98.c)
 *
 * This is a library derived from SOFA, not SOFA itself.
 *
 *
 *
 *  Copyright (C) 2013-2023, NumFOCUS Foundation.
 *  All rights reserved.
 *
 *  This library is derived, with permission, from the International
 *  Astronomical Union's "Standards of Fundamental Astronomy" library,
 *  available from http://www.iausofa.org.
 *
 *  The ERFA version is intended to retain identical functionality to
 *  the SOFA library, but made distinct through different function and
 *  file names, as set out in the SOFA license conditions.  The SOFA
 *  original has a role as a reference standard for the IAU and IERS,
 *  and consequently redistribution is permitted only in its unaltered
 *  state.  The ERFA version is not subject to this restriction and
 *  therefore can be included in distributions which do not support the
 *  concept of "read only" software.
 *
 *  Although the intent is to replicate the SOFA API (other than
 *  replacement of prefix names) and results (with the exception of
 *  bugs;  any that are discovered will be fixed), SOFA is not
 *  responsible for any errors found in this version of the library.
 *
 *  If you wish to acknowledge the SOFA heritage, please acknowledge
 *  that you are using a library derived from SOFA, rather than SOFA
 *  itself.
 *
 *
 *  TERMS AND CONDITIONS
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *  1 Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 *  2 Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 *  3 Neither the name of the Standards Of Fundamental Astronomy Board,
 *    the International Astronomical Union nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 *  FOR A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE
 *  COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 *  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 *  CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 *  LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 *  ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *  POSSIBILITY OF SUCH DAMAGE.
 *
 * ===========================================================================
 */

/*
 * Two deliberate departures from the original, both narrowing it rather than
 * changing it.
 *
 * The original also returns the Moon's velocity. Nothing here needs it, and
 * dropping it removes half the arithmetic, so only the position is computed.
 *
 * The original ends by rotating the result into the GCRS. A rise time and a
 * tidal potential are both worked out from the ecliptic longitude, latitude
 * and distance of date, which is what the series yields just before that
 * rotation, so the rotation and the five routines it needs are left out. What
 * comes back is therefore referred to the mean equinox and ecliptic OF DATE.
 */
package com.gb.mybuoy.astro;

/** Geocentric position of the Moon, from the Meeus series as published by ERFA. */
public final class MoonErfa
{
    private static final double D2R = Math.PI / 180.0;

    /** Days in a Julian century. */
    private static final double CENTURY = 36525.0;

    /** Julian date of J2000.0. */
    private static final double J2000 = 2451545.0;

    /** Constant term of the distance series, in metres. */
    private static final double R0_METRES = 385000560.0;

    /*
     * Longitude and distance series, sixty terms of six values: the multiples of
     * D, M, M' and F, then the sine coefficient of the longitude in degrees and
     * the cosine coefficient of the distance in metres.
     */
    private static final double[] TLR = {
        0, 0, 1, 0, 6.288774, -20905355.0,
        2, 0, -1, 0, 1.274027, -3699111.0,
        2, 0, 0, 0, 0.658314, -2955968.0,
        0, 0, 2, 0, 0.213618, -569925.0,
        0, 1, 0, 0, -0.185116, 48888.0,
        0, 0, 0, 2, -0.114332, -3149.0,
        2, 0, -2, 0, 0.058793, 246158.0,
        2, -1, -1, 0, 0.057066, -152138.0,
        2, 0, 1, 0, 0.053322, -170733.0,
        2, -1, 0, 0, 0.045758, -204586.0,
        0, 1, -1, 0, -0.040923, -129620.0,
        1, 0, 0, 0, -0.034720, 108743.0,
        0, 1, 1, 0, -0.030383, 104755.0,
        2, 0, 0, -2, 0.015327, 10321.0,
        0, 0, 1, 2, -0.012528, 0.0,
        0, 0, 1, -2, 0.010980, 79661.0,
        4, 0, -1, 0, 0.010675, -34782.0,
        0, 0, 3, 0, 0.010034, -23210.0,
        4, 0, -2, 0, 0.008548, -21636.0,
        2, 1, -1, 0, -0.007888, 24208.0,
        2, 1, 0, 0, -0.006766, 30824.0,
        1, 0, -1, 0, -0.005163, -8379.0,
        1, 1, 0, 0, 0.004987, -16675.0,
        2, -1, 1, 0, 0.004036, -12831.0,
        2, 0, 2, 0, 0.003994, -10445.0,
        4, 0, 0, 0, 0.003861, -11650.0,
        2, 0, -3, 0, 0.003665, 14403.0,
        0, 1, -2, 0, -0.002689, -7003.0,
        2, 0, -1, 2, -0.002602, 0.0,
        2, -1, -2, 0, 0.002390, 10056.0,
        1, 0, 1, 0, -0.002348, 6322.0,
        2, -2, 0, 0, 0.002236, -9884.0,
        0, 1, 2, 0, -0.002120, 5751.0,
        0, 2, 0, 0, -0.002069, 0.0,
        2, -2, -1, 0, 0.002048, -4950.0,
        2, 0, 1, -2, -0.001773, 4130.0,
        2, 0, 0, 2, -0.001595, 0.0,
        4, -1, -1, 0, 0.001215, -3958.0,
        0, 0, 2, 2, -0.001110, 0.0,
        3, 0, -1, 0, -0.000892, 3258.0,
        2, 1, 1, 0, -0.000810, 2616.0,
        4, -1, -2, 0, 0.000759, -1897.0,
        0, 2, -1, 0, -0.000713, -2117.0,
        2, 2, -1, 0, -0.000700, 2354.0,
        2, 1, -2, 0, 0.000691, 0.0,
        2, -1, 0, -2, 0.000596, 0.0,
        4, 0, 1, 0, 0.000549, -1423.0,
        0, 0, 4, 0, 0.000537, -1117.0,
        4, -1, 0, 0, 0.000520, -1571.0,
        1, 0, -2, 0, -0.000487, -1739.0,
        2, 1, 0, -2, -0.000399, 0.0,
        0, 0, 2, -2, -0.000381, -4421.0,
        1, 1, 1, 0, 0.000351, 0.0,
        3, 0, -2, 0, -0.000340, 0.0,
        4, 0, -3, 0, 0.000330, 0.0,
        2, -1, 2, 0, 0.000327, 0.0,
        0, 2, 1, 0, -0.000323, 1165.0,
        1, 1, -1, 0, 0.000299, 0.0,
        2, 0, 3, 0, 0.000294, 0.0,
        2, 0, -1, -2, 0.000000, 8752.0
    };

    /* Latitude series, sixty terms of five: the same four multiples, then the
     * sine coefficient of the latitude in degrees. */
    private static final double[] TB = {
        0, 0, 0, 1, 5.128122,
        0, 0, 1, 1, 0.280602,
        0, 0, 1, -1, 0.277693,
        2, 0, 0, -1, 0.173237,
        2, 0, -1, 1, 0.055413,
        2, 0, -1, -1, 0.046271,
        2, 0, 0, 1, 0.032573,
        0, 0, 2, 1, 0.017198,
        2, 0, 1, -1, 0.009266,
        0, 0, 2, -1, 0.008822,
        2, -1, 0, -1, 0.008216,
        2, 0, -2, -1, 0.004324,
        2, 0, 1, 1, 0.004200,
        2, 1, 0, -1, -0.003359,
        2, -1, -1, 1, 0.002463,
        2, -1, 0, 1, 0.002211,
        2, -1, -1, -1, 0.002065,
        0, 1, -1, -1, -0.001870,
        4, 0, -1, -1, 0.001828,
        0, 1, 0, 1, -0.001794,
        0, 0, 0, 3, -0.001749,
        0, 1, -1, 1, -0.001565,
        1, 0, 0, 1, -0.001491,
        0, 1, 1, 1, -0.001475,
        0, 1, 1, -1, -0.001410,
        0, 1, 0, -1, -0.001344,
        1, 0, 0, -1, -0.001335,
        0, 0, 3, 1, 0.001107,
        4, 0, 0, -1, 0.001021,
        4, 0, -1, 1, 0.000833,
        0, 0, 1, -3, 0.000777,
        4, 0, -2, 1, 0.000671,
        2, 0, 0, -3, 0.000607,
        2, 0, 2, -1, 0.000596,
        2, -1, 1, -1, 0.000491,
        2, 0, -2, 1, -0.000451,
        0, 0, 3, -1, 0.000439,
        2, 0, 2, 1, 0.000422,
        2, 0, -3, -1, 0.000421,
        2, 1, -1, 1, -0.000366,
        2, 1, 0, 1, -0.000351,
        4, 0, 0, 1, 0.000331,
        2, -1, 1, 1, 0.000315,
        2, -2, 0, -1, 0.000302,
        0, 0, 1, 3, -0.000283,
        2, 1, 1, -1, -0.000229,
        1, 1, 0, -1, 0.000223,
        1, 1, 0, 1, 0.000223,
        0, 1, -2, -1, -0.000220,
        2, 1, -1, -1, -0.000220,
        1, 0, 1, 1, -0.000185,
        2, -1, -2, -1, 0.000181,
        0, 1, 2, 1, -0.000177,
        4, 0, -2, -1, 0.000176,
        4, -1, -1, -1, 0.000166,
        1, 0, 1, -1, -0.000164,
        4, 0, 1, -1, 0.000132,
        1, 0, -1, -1, -0.000119,
        4, -1, 0, -1, 0.000115,
        2, -2, 0, 1, 0.000107
    };

    /** Where the Moon is, in the ecliptic frame of the date asked for. */
    public static final class Position
    {
        private final double _longitudeRadians;
        private final double _latitudeRadians;
        private final double _distanceKm;

        private Position(double longitudeRadians, double latitudeRadians, double distanceKm)
        {
            _longitudeRadians = longitudeRadians;
            _latitudeRadians = latitudeRadians;
            _distanceKm = distanceKm;
        }

        public double longitudeRadians()
        {
            return _longitudeRadians;
        }

        public double latitudeRadians()
        {
            return _latitudeRadians;
        }

        public double distanceKm()
        {
            return _distanceKm;
        }
    }

    private MoonErfa()
    {
    }

    /**
     * @param julianDateTT Julian date in Terrestrial Time
     * @return the Moon referred to the mean equinox and ecliptic of that date
     */
    public static Position at(double julianDateTT)
    {
        double t = (julianDateTT - J2000) / CENTURY;

        // Fundamental arguments, in radians. The constants are Simon et al. (1994).

        // Moon's mean longitude.
        double elp = D2R * _wrap(((((-1.0 / 65194000.0) * t + 1.0 / 538841.0) * t
                - 0.0015786) * t + 481267.88123421) * t + 218.31665436);

        // Moon's mean elongation.
        double d = D2R * _wrap(((((1.0 / 113065000.0) * t + 1.0 / 545868.0) * t
                - 0.0018819) * t + 445267.1114034) * t + 297.8501921);

        // Sun's mean anomaly.
        double em = D2R * _wrap(((((1.0 / 24490000.0) * t + 0.0) * t
                - 0.0001536) * t + 35999.0502909) * t + 357.5291092);

        // Moon's mean anomaly.
        double emp = D2R * _wrap(((((-1.0 / 14712000.0) * t + 1.0 / 69699.0) * t
                + 0.0087414) * t + 477198.8675055) * t + 134.9633964);

        // Mean distance of the Moon from its ascending node.
        double f = D2R * _wrap(((((1.0 / 863310000.0) * t + 1.0 / 3526000.0) * t
                - 0.0036539) * t + 483202.0175233) * t + 93.2720950);

        // Meeus A1 (Venus), A2 (Jupiter) and A3 (the Moon's sidereal motion).
        double a1 = D2R * (119.75 + 131.849 * t);
        double a2 = D2R * (53.09 + 479264.290 * t);
        double a3 = D2R * (313.45 + 481266.484 * t);

        // Eccentricity factor of the Earth's orbit, and its square.
        double e = 1.0 + (-0.002516 + -0.0000074 * t) * t;
        double esq = e * e;

        // Meeus's additive terms start the summations off.
        double elpmf = elp - f;

        double vel = 0.003958 * Math.sin(a1)
                + 0.001962 * Math.sin(elpmf)
                + 0.000318 * Math.sin(a2);

        double vr = 0.0;

        double vb = -0.002235 * Math.sin(elp)
                + 0.000382 * Math.sin(a3)
                + 0.000175 * Math.sin(a1 - f)
                + 0.000175 * Math.sin(a1 + f)
                + 0.000127 * Math.sin(elp - emp)
                - 0.000115 * Math.sin(elp + emp);

        // Summed from the smallest term upwards, as the original does: the series
        // spans eight orders of magnitude and the order of addition is not free.
        for (int n = TLR.length - 6; n >= 0; n -= 6) {

            double argument = TLR[n] * d + TLR[n + 1] * em + TLR[n + 2] * emp + TLR[n + 3] * f;
            double factor = _eccentricityFactor(TLR[n + 1], e, esq);

            vel += TLR[n + 4] * Math.sin(argument) * factor;
            vr += TLR[n + 5] * Math.cos(argument) * factor;
        }

        for (int n = TB.length - 5; n >= 0; n -= 5) {

            double argument = TB[n] * d + TB[n + 1] * em + TB[n + 2] * emp + TB[n + 3] * f;
            double factor = _eccentricityFactor(TB[n + 1], e, esq);

            vb += TB[n + 4] * Math.sin(argument) * factor;
        }

        return new Position(elp + D2R * vel, D2R * vb, (vr + R0_METRES) / 1000.0);
    }

    /*
     * Terms carrying the Sun's mean anomaly are scaled by the eccentricity of the
     * Earth's orbit, once or twice according to how many times the anomaly appears
     * in the argument.
     */
    private static double _eccentricityFactor(double sunAnomalyMultiple, double e, double esq)
    {
        double multiple = Math.abs(sunAnomalyMultiple);

        if (multiple == 1.0) {
            return e;
        }

        if (multiple == 2.0) {
            return esq;
        }

        return 1.0;
    }

    /** Folds degrees into one turn, the way the original's fmod does. */
    private static double _wrap(double degrees)
    {
        return degrees % 360.0;
    }
}
