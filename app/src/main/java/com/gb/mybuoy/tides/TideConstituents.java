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
 * tidal constituent table and FES-type nodal corrections.
 *
 * Coefficients and nodal formulations ported from pyTMD
 * (https://github.com/tsutterley/pyTMD), MIT License,
 * Copyright (c) 2017 Tyler C Sutterley.
 *
 * EOT20 is declared as a "FES-netcdf" model, so the FES branch of the
 * nodal corrections is the one that applies.
 */
package com.gb.mybuoy.tides;

import java.util.LinkedHashMap;
import java.util.Map;

/** The 17 tidal constituents distributed with EOT20. */
public final class TideConstituents
{
    /** Constituent names, in the order used by the packed grid file. */
    public static final String[] NAMES = {
            "2n2", "j1", "k1", "k2", "m2", "m4", "mf", "mm", "n2",
            "o1", "p1", "q1", "s1", "s2", "sa", "ssa", "t2"
    };

    /**
     * Doodson coefficients over the seven argument variables
     * { tau, s, h, p, n, pp, k } where tau is mean lunar time, s/h/p/n/pp
     * are the mean longitudes and k is a multiple of 90 degrees.
     */
    private static final Map<String, double[]> DOODSON = buildDoodsonTable();

    private TideConstituents()
    {
    }

    /**
     * Equilibrium argument of a constituent, in degrees, for the given
     * mean lunar time and mean longitudes (all in degrees).
     */
    public static double equilibriumArgument(String name,
                                             double tau,
                                             double s,
                                             double h,
                                             double p,
                                             double n,
                                             double pp)
    {
        double[] coefficients = DOODSON.get(name);

        if (coefficients == null)
        {
            throw new IllegalArgumentException("Unsupported constituent: " + name);
        }

        double argument = coefficients[0] * tau
                + coefficients[1] * s
                + coefficients[2] * h
                + coefficients[3] * p
                + coefficients[4] * n
                + coefficients[5] * pp
                + coefficients[6] * 90.0;

        return argument;
    }

    /**
     * Nodal modulation of a constituent: returns { f, u } with the
     * amplitude factor f (dimensionless) and the angle u in radians.
     * The Schureman angles are those returned by
     * {@link TideAstro#schuremanArguments(double, double)}.
     */
    public static double[] nodalModulation(String name, double[] schureman)
    {
        double inclination = schureman[0];
        double xi = schureman[1];
        double nu = schureman[2];
        double nuPrime = schureman[7];
        double nuSecond = schureman[8];

        double sinI = Math.sin(inclination);
        double cosHalfI = Math.cos(inclination / 2.0);
        double sin2I = Math.sin(2.0 * inclination);

        switch (name)
        {
            case "m2":
            case "2n2":
            case "n2":
            {
                double f = Math.pow(cosHalfI, 4.0) / 0.9154;
                double u = 2.0 * xi - 2.0 * nu;
                return new double[] { f, u };
            }

            case "m4":
            {
                // Compound tide: built by recursion on its M2 parent.
                double[] parent = nodalModulation("m2", schureman);
                return new double[] { parent[0] * parent[0], 2.0 * parent[1] };
            }

            case "o1":
            case "q1":
            {
                double f = sinI * cosHalfI * cosHalfI / 0.38;
                double u = 2.0 * xi - nu;
                return new double[] { f, u };
            }

            case "j1":
            {
                double f = sin2I / 0.7214;
                double u = -nu;
                return new double[] { f, u };
            }

            case "k1":
            {
                double first = 0.8965 * sin2I * sin2I;
                double second = 0.6001 * sin2I * Math.cos(nu);
                double f = Math.sqrt(first + second + 0.1006);
                double u = -nuPrime;
                return new double[] { f, u };
            }

            case "k2":
            {
                double first = 19.0444 * Math.pow(sinI, 4.0);
                double second = 2.7702 * sinI * sinI * Math.cos(2.0 * nu);
                double f = Math.sqrt(first + second + 0.0981);
                double u = -2.0 * nuSecond;
                return new double[] { f, u };
            }

            case "mm":
            {
                double f = (2.0 / 3.0 - sinI * sinI) / 0.5021;
                return new double[] { f, 0.0 };
            }

            case "mf":
            {
                double f = sinI * sinI / 0.1578;
                double u = -2.0 * xi;
                return new double[] { f, u };
            }

            case "p1":
            case "s1":
            case "s2":
            case "sa":
            case "ssa":
            case "t2":
                // No nodal modulation for the solar and long-period terms.
                return new double[] { 1.0, 0.0 };

            default:
                throw new IllegalArgumentException("Unsupported constituent: " + name);
        }
    }

    private static Map<String, double[]> buildDoodsonTable()
    {
        Map<String, double[]> table = new LinkedHashMap<>();

        //                          tau     s     h     p     n    pp     k
        table.put("2n2", new double[] { 2.0, -2.0,  0.0,  2.0,  0.0,  0.0,  0.0 });
        table.put("j1",  new double[] { 1.0,  2.0,  0.0, -1.0,  0.0,  0.0,  1.0 });
        table.put("k1",  new double[] { 1.0,  1.0,  0.0,  0.0,  0.0,  0.0,  1.0 });
        table.put("k2",  new double[] { 2.0,  2.0,  0.0,  0.0,  0.0,  0.0,  0.0 });
        table.put("m2",  new double[] { 2.0,  0.0,  0.0,  0.0,  0.0,  0.0,  0.0 });
        table.put("m4",  new double[] { 4.0,  0.0,  0.0,  0.0,  0.0,  0.0,  0.0 });
        table.put("mf",  new double[] { 0.0,  2.0,  0.0,  0.0,  0.0,  0.0,  0.0 });
        table.put("mm",  new double[] { 0.0,  1.0,  0.0, -1.0,  0.0,  0.0,  0.0 });
        table.put("n2",  new double[] { 2.0, -1.0,  0.0,  1.0,  0.0,  0.0,  0.0 });
        table.put("o1",  new double[] { 1.0, -1.0,  0.0,  0.0,  0.0,  0.0, -1.0 });
        table.put("p1",  new double[] { 1.0,  1.0, -2.0,  0.0,  0.0,  0.0, -1.0 });
        table.put("q1",  new double[] { 1.0, -2.0,  0.0,  1.0,  0.0,  0.0, -1.0 });
        table.put("s1",  new double[] { 1.0,  1.0, -1.0,  0.0,  0.0,  0.0,  2.0 });
        table.put("s2",  new double[] { 2.0,  2.0, -2.0,  0.0,  0.0,  0.0,  0.0 });
        table.put("sa",  new double[] { 0.0,  0.0,  1.0,  0.0,  0.0, -1.0,  0.0 });
        table.put("ssa", new double[] { 0.0,  0.0,  2.0,  0.0,  0.0,  0.0,  0.0 });
        table.put("t2",  new double[] { 2.0,  2.0, -3.0,  0.0,  0.0,  1.0,  0.0 });

        return table;
    }
}
