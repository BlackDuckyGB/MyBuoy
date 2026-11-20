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
 * How much of the Moon is lit, and which way round.
 *
 * All of it follows from one triangle: the Sun, the Earth and the Moon. The
 * angle at the Moon between the other two is the phase angle, and the lit
 * fraction is a straight function of it. Nothing here is ported; the relations
 * are the ordinary ones.
 *
 * Two things are worth saying about the result.
 *
 * The lit fraction is NOT the fraction of the disc that looks bright to the
 * eye: it is the fraction of the diameter spanned by the lit part, which is
 * what the familiar crescent shape measures. At first quarter it is one half,
 * and the Moon then gives about a tenth of the light of the full Moon, not a
 * half.
 *
 * The age is reckoned from the elongation rather than from the true instant of
 * the last new Moon. The two part company by a few hours around the extremes,
 * because the Moon does not travel at a constant rate, and nothing displayed
 * here is finer than that.
 */
package com.gb.mybuoy.astro;

/** The lit fraction of the Moon, its age, and the tilt of its bright limb. */
public final class MoonPhase
{
    /** Mean interval from one new Moon to the next, in days. */
    public static final double SYNODIC_MONTH_DAYS = 29.530588853;

    private static final double TWO_PI = 2.0 * Math.PI;

    /** What the Moon looks like at a given moment. */
    public static final class Phase
    {
        private final double _illuminatedFraction;
        private final double _elongationRadians;
        private final double _phaseAngleRadians;
        private final double _ageDays;
        private final double _brightLimbAngleRadians;

        private Phase(double illuminatedFraction, double elongationRadians,
                      double phaseAngleRadians, double ageDays, double brightLimbAngleRadians)
        {
            _illuminatedFraction = illuminatedFraction;
            _elongationRadians = elongationRadians;
            _phaseAngleRadians = phaseAngleRadians;
            _ageDays = ageDays;
            _brightLimbAngleRadians = brightLimbAngleRadians;
        }

        /** Zero at new Moon, one at full. */
        public double illuminatedFraction()
        {
            return _illuminatedFraction;
        }

        /** Angle between the Moon and the Sun as seen from here, zero to pi. */
        public double elongationRadians()
        {
            return _elongationRadians;
        }

        /** Angle at the Moon between the Sun and the Earth. */
        public double phaseAngleRadians()
        {
            return _phaseAngleRadians;
        }

        /** Days since the last new Moon, from the elongation. */
        public double ageDays()
        {
            return _ageDays;
        }

        /**
         * Where the lit edge points, as an angle from celestial north turning east.
         * A drawing that ignores this puts the crescent on the wrong side, which is
         * glaring to anyone who has looked up.
         */
        public double brightLimbAngleRadians()
        {
            return _brightLimbAngleRadians;
        }

        /** True while the lit part is growing, from new Moon to full. */
        public boolean waxing()
        {
            return _ageDays < (SYNODIC_MONTH_DAYS / 2.0);
        }

        /**
         * The usual eight names, in English.
         *
         * New, first quarter, full and last quarter are INSTANTS, not stretches. They
         * are given a window of six degrees of elongation either side, which the Moon
         * crosses in about a day, so the name lasts roughly as long as people use it.
         * Splitting the month into eight equal parts instead would have the Moon
         * "full" for the best part of four days.
         */
        public String name()
        {
            double degrees = (_ageDays / SYNODIC_MONTH_DAYS) * 360.0;

            double window = 6.0;

            if (degrees < window || degrees > (360.0 - window)) {
                return "New moon";
            }

            if (Math.abs(degrees - 90.0) < window) {
                return "First quarter";
            }

            if (Math.abs(degrees - 180.0) < window) {
                return "Full moon";
            }

            if (Math.abs(degrees - 270.0) < window) {
                return "Last quarter";
            }

            if (degrees < 90.0) {
                return "Waxing crescent";
            }

            if (degrees < 180.0) {
                return "Waxing gibbous";
            }

            if (degrees < 270.0) {
                return "Waning gibbous";
            }

            return "Waning crescent";
        }
    }

    private MoonPhase()
    {
    }

    /** @param julianDateTT Julian date in Terrestrial Time */
    public static Phase at(double julianDateTT)
    {
        SunErfa.Position sun = SunErfa.at(julianDateTT);
        MoonErfa.Position moon = MoonErfa.at(julianDateTT);

        double obliquity = AstroTime.meanObliquity(julianDateTT);

        double difference = moon.longitudeRadians() - sun.longitudeRadians();

        // Elongation: the Moon's latitude matters here, small though it is, because
        // it is what keeps the fraction from reaching exactly zero or one except at
        // an eclipse.
        double elongation = Math.acos(
                Math.cos(moon.latitudeRadians()) * Math.cos(difference));

        // The angle at the Moon, from the triangle Sun to Earth to Moon.
        double phaseAngle = Math.atan2(
                sun.distanceKm() * Math.sin(elongation),
                moon.distanceKm() - sun.distanceKm() * Math.cos(elongation));

        double illuminated = (1.0 + Math.cos(phaseAngle)) / 2.0;

        double age = (AstroTime.normaliseAngle(difference) / TWO_PI) * SYNODIC_MONTH_DAYS;

        Topocentric.Equatorial sunEquatorial = Topocentric.equatorialFromEcliptic(
                sun.longitudeRadians(), sun.latitudeRadians(), obliquity);

        Topocentric.Equatorial moonEquatorial = Topocentric.equatorialFromEcliptic(
                moon.longitudeRadians(), moon.latitudeRadians(), obliquity);

        return new Phase(illuminated, elongation, phaseAngle, age,
                _brightLimbAngle(sunEquatorial, moonEquatorial));
    }

    /*
     * The direction of the midpoint of the lit limb, which is simply the direction
     * of the Sun as seen from the Moon's place on the sky, measured from north.
     */
    private static double _brightLimbAngle(Topocentric.Equatorial sun,
                                           Topocentric.Equatorial moon)
    {
        double rightAscensionDifference =
                sun.rightAscensionRadians() - moon.rightAscensionRadians();

        double sunDeclination = sun.declinationRadians();
        double moonDeclination = moon.declinationRadians();

        return AstroTime.normaliseAngle(Math.atan2(
                Math.cos(sunDeclination) * Math.sin(rightAscensionDifference),
                Math.sin(sunDeclination) * Math.cos(moonDeclination)
                        - Math.cos(sunDeclination) * Math.sin(moonDeclination)
                        * Math.cos(rightAscensionDifference)));
    }
}
