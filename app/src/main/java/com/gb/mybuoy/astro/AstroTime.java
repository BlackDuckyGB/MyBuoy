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
 * PARTS OF THIS FILE ARE A JAVA PORT OF ERFA: the routines eraEra00 (era00.c),
 * eraGmst06 (gmst06.c), eraObl06 (obl06.c) and eraAnp (anp.c).
 *
 * The copyright, conditions and disclaimer reproduced below belong to that
 * material and travel with it. The Java adaptation is Golden-Block's and is
 * distributed under the terms at the top of this file; the conditions below
 * continue to apply to everything derived from ERFA, under either of those
 * terms.
 *
 * Source: https://github.com/liberfa/erfa
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
 * Time scales, and the rotation of the Earth under the sky.
 *
 * Three scales meet here. The phone keeps UTC. The ephemerides want TT, which
 * runs ahead of UTC by the leap seconds plus a fixed 32.184 s. The rotation of
 * the Earth is counted in UT1, which follows the actual, slightly irregular
 * spin and never strays more than 0.9 s from UTC.
 *
 * UT1 is taken to be UTC here. The difference is bounded by 0.9 s by the very
 * definition of UTC, which is 0.0002 degrees of rotation: a hundred times
 * smaller than anything this package goes on to display.
 */
package com.gb.mybuoy.astro;

/** Julian dates, time scales and Greenwich sidereal time. */
public final class AstroTime
{
    private static final double AS2R = Math.PI / (180.0 * 3600.0);

    private static final double TWO_PI = 2.0 * Math.PI;

    /** Julian date of J2000.0. */
    public static final double J2000 = 2451545.0;

    /** Days in a Julian century. */
    public static final double CENTURY = 36525.0;

    /** Julian date of the Unix epoch, 1970-01-01T00:00:00Z. */
    private static final double UNIX_EPOCH_JD = 2440587.5;

    private static final double SECONDS_PER_DAY = 86400.0;

    /**
     * How far Terrestrial Time runs ahead of UTC, in seconds.
     *
     * It is the count of leap seconds since 1972 plus the fixed 32.184 s that
     * separates TT from atomic time: 37 + 32.184 as this is written. The last leap
     * second was inserted in December 2016, and in 2022 the General Conference on
     * Weights and Measures resolved to stop inserting them by 2035, so this number
     * is unlikely to move again. If it ever does, getting it wrong costs one second
     * of sky rotation, which is 15 arcseconds: visible in a planetarium, invisible
     * in a sunrise time.
     */
    private static final double TT_MINUS_UTC_SECONDS = 69.184;

    private AstroTime()
    {
    }

    /** Julian date in UTC, which is also used here as UT1. */
    public static double julianDateUtc(long unixSeconds)
    {
        return UNIX_EPOCH_JD + (unixSeconds / SECONDS_PER_DAY);
    }

    /** Julian date in Terrestrial Time, the scale the ephemerides are built on. */
    public static double julianDateTt(long unixSeconds)
    {
        return UNIX_EPOCH_JD
                + ((unixSeconds + TT_MINUS_UTC_SECONDS) / SECONDS_PER_DAY);
    }

    /**
     * Earth rotation angle, IAU 2000: where the Greenwich meridian points, measured
     * from the celestial intermediate origin.
     */
    public static double earthRotationAngle(double julianDateUt1)
    {
        // The fractional part of the Julian date carries the whole turns of the day;
        // the constant below is only the sidereal EXCESS over one turn. Taking the
        // fraction from midnight rather than from noon, as the Julian day runs,
        // would put the result half a turn out.
        double fractionOfDay = julianDateUt1 % 1.0;

        double sinceEpoch = julianDateUt1 - J2000;

        return normaliseAngle(TWO_PI * (fractionOfDay + 0.7790572732640
                + 0.00273781191135448 * sinceEpoch));
    }

    /** Greenwich mean sidereal time, IAU 2006, in radians. */
    public static double greenwichMeanSiderealTime(double julianDateUt1, double julianDateTt)
    {
        double t = (julianDateTt - J2000) / CENTURY;

        return normaliseAngle(earthRotationAngle(julianDateUt1)
                + (0.014506
                + (4612.156534
                + (1.3915817
                + (-0.00000044
                + (-0.000029956
                + (-0.0000000368)
                * t) * t) * t) * t) * t) * AS2R);
    }

    /** Mean obliquity of the ecliptic, IAU 2006, in radians. */
    public static double meanObliquity(double julianDateTt)
    {
        double t = (julianDateTt - J2000) / CENTURY;

        return (84381.406
                + (-46.836769
                + (-0.0001831
                + (0.00200340
                + (-0.000000576
                + (-0.0000000434)
                * t) * t) * t) * t) * t) * AS2R;
    }

    /** Folds an angle into zero to two pi. */
    public static double normaliseAngle(double radians)
    {
        double folded = radians % TWO_PI;

        return (folded < 0.0) ? (folded + TWO_PI) : folded;
    }
}
