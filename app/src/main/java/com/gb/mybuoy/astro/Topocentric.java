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
 * From where a body is, to where the user should look.
 *
 * Nothing here is ported: these are the standard spherical relations between
 * the ecliptic, the equator and the horizon, and they are the same in every
 * textbook.
 *
 * The one step worth naming is the parallax. The ephemerides give positions as
 * seen from the CENTRE of the Earth, and the user stands on its surface, up to
 * a little over six thousand kilometres off that centre. For the Sun, at a
 * hundred and fifty million kilometres, that shifts the direction by at most
 * nine arcseconds and may be ignored. For the Moon it is a different matter:
 * the shift reaches a full degree, twice the Moon's own width, so it is
 * applied.
 */
package com.gb.mybuoy.astro;

/** Converts positions between the ecliptic, the equator and the observer's horizon. */
public final class Topocentric
{
    /** Flattening of the WGS84 ellipsoid, as one minus the axis ratio. */
    private static final double FLATTENING = 1.0 / 298.257223563;

    /** Equatorial radius of the Earth, in kilometres (WGS84). */
    private static final double EARTH_RADIUS_KM = 6378.137;

    /** A direction on the celestial sphere, in the equatorial frame. */
    public static final class Equatorial
    {
        private final double _rightAscensionRadians;
        private final double _declinationRadians;

        private Equatorial(double rightAscensionRadians, double declinationRadians)
        {
            _rightAscensionRadians = rightAscensionRadians;
            _declinationRadians = declinationRadians;
        }

        public double rightAscensionRadians()
        {
            return _rightAscensionRadians;
        }

        public double declinationRadians()
        {
            return _declinationRadians;
        }
    }

    /** A direction as the user sees it: a bearing and a height above the horizon. */
    public static final class Horizontal
    {
        private final double _azimuthRadians;
        private final double _altitudeRadians;

        private Horizontal(double azimuthRadians, double altitudeRadians)
        {
            _azimuthRadians = azimuthRadians;
            _altitudeRadians = altitudeRadians;
        }

        /** Measured from north, turning towards east, as a compass bearing is. */
        public double azimuthRadians()
        {
            return _azimuthRadians;
        }

        /** Positive above the horizon, negative below it. */
        public double altitudeRadians()
        {
            return _altitudeRadians;
        }
    }

    private Topocentric()
    {
    }

    /**
     * Ecliptic longitude and latitude of date to right ascension and declination.
     *
     * @param obliquityRadians the obliquity of the ecliptic for the same date
     */
    public static Equatorial equatorialFromEcliptic(double longitudeRadians,
                                                    double latitudeRadians,
                                                    double obliquityRadians)
    {
        double sinLongitude = Math.sin(longitudeRadians);
        double cosLongitude = Math.cos(longitudeRadians);
        double sinLatitude = Math.sin(latitudeRadians);
        double cosLatitude = Math.cos(latitudeRadians);
        double sinObliquity = Math.sin(obliquityRadians);
        double cosObliquity = Math.cos(obliquityRadians);

        double rightAscension = Math.atan2(
                sinLongitude * cosObliquity - (sinLatitude / cosLatitude) * sinObliquity,
                cosLongitude);

        double declination = Math.asin(
                sinLatitude * cosObliquity + cosLatitude * sinObliquity * sinLongitude);

        return new Equatorial(AstroTime.normaliseAngle(rightAscension), declination);
    }

    /**
     * Where to look for a body, from a point on the ground.
     *
     * @param distanceKm        distance to the body, used for the parallax; pass zero
     *                          or less to skip the correction and treat the position
     *                          as seen from the centre of the Earth
     * @param longitudeRadians  observer's longitude, positive east
     */
    public static Horizontal horizontalFromEquatorial(Equatorial equatorial,
                                                      double greenwichSiderealTime,
                                                      double longitudeRadians,
                                                      double latitudeRadians,
                                                      double distanceKm)
    {
        double rightAscension = equatorial.rightAscensionRadians();
        double declination = equatorial.declinationRadians();

        // Local hour angle: how far the body has turned past the local meridian.
        double hourAngle = greenwichSiderealTime + longitudeRadians - rightAscension;

        if (distanceKm > 0.0) {

            double[] shifted = _parallax(hourAngle, declination, latitudeRadians, distanceKm);

            hourAngle = shifted[0];
            declination = shifted[1];
        }

        double sinLatitude = Math.sin(latitudeRadians);
        double cosLatitude = Math.cos(latitudeRadians);
        double sinDeclination = Math.sin(declination);
        double cosDeclination = Math.cos(declination);
        double sinHourAngle = Math.sin(hourAngle);
        double cosHourAngle = Math.cos(hourAngle);

        double altitude = Math.asin(
                sinLatitude * sinDeclination + cosLatitude * cosDeclination * cosHourAngle);

        // Reckoned from south and turning west, which is the classical convention;
        // half a turn then puts it on north, which is what a compass shows.
        double fromSouth = Math.atan2(sinHourAngle,
                cosHourAngle * sinLatitude - (sinDeclination / cosDeclination) * cosLatitude);

        return new Horizontal(AstroTime.normaliseAngle(fromSouth + Math.PI), altitude);
    }

    /**
     * The parallactic angle: how far celestial north is tilted from straight up, as
     * seen by someone looking at the body.
     *
     * It is what turns the Moon's crescent as the night goes on. The horns point one
     * way when it rises and quite another when it sets, and a drawing that ignores
     * this looks wrong to anyone who has actually looked up.
     *
     * @param hourAngleRadians the body's local hour angle
     */
    public static double parallacticAngle(double hourAngleRadians, double declinationRadians,
                                          double latitudeRadians)
    {
        return Math.atan2(Math.sin(hourAngleRadians),
                Math.tan(latitudeRadians) * Math.cos(declinationRadians)
                        - Math.sin(declinationRadians) * Math.cos(hourAngleRadians));
    }

    /*
     * Moves a geocentric direction to the observer's own position on the surface.
     *
     * The observer's distance from the axis and from the equatorial plane are the
     * two quantities that matter, and they are not simply the radius times the
     * cosine and sine of the latitude: the Earth is flattened, so the geocentric
     * latitude differs from the geographic one by up to eleven arcminutes.
     */
    private static double[] _parallax(double hourAngle, double declination,
                                      double latitudeRadians, double distanceKm)
    {
        double ratio = 1.0 - FLATTENING;

        double reducedLatitude = Math.atan(ratio * Math.tan(latitudeRadians));

        double distanceFromAxis = Math.cos(reducedLatitude);
        double distanceFromEquator = ratio * Math.sin(reducedLatitude);

        double sineParallax = EARTH_RADIUS_KM / distanceKm;

        double cosDeclination = Math.cos(declination);

        double deltaHourAngle = Math.atan2(
                -distanceFromAxis * sineParallax * Math.sin(hourAngle),
                cosDeclination - distanceFromAxis * sineParallax * Math.cos(hourAngle));

        double shiftedDeclination = Math.atan2(
                (Math.sin(declination) - distanceFromEquator * sineParallax)
                        * Math.cos(deltaHourAngle),
                cosDeclination - distanceFromAxis * sineParallax * Math.cos(hourAngle));

        // The hour angle is measured from the meridian to the body, H = theta - alpha,
        // so an increase in right ascension is a DECREASE in hour angle.
        return new double[] { hourAngle - deltaHourAngle, shiftedDeclination };
    }
}
