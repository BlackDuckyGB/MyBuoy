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
 * chart datum derived from the model itself.
 *
 * French charts are referenced to the "zero hydrographique", which is the
 * lowest astronomical tide: the lowest level the harmonic prediction reaches
 * over a full 18.6 year nodal cycle. It needs no extra data, only time.
 */
package com.gb.mybuoy.tides;

import java.util.ArrayList;
import java.util.List;

/** Lowest and highest astronomical tide at one position. */
public final class TideDatum
{
    /** The two extreme levels, in metres above mean sea level. */
    public static final class Range
    {
        public final double lowest;
        public final double highest;

        Range(double lowest, double highest)
        {
            this.lowest = lowest;
            this.highest = highest;
        }
    }

    /**
     * Coarse walk of the cycle. Half an hour is far too wide to land on an extremum, which
     * is why every candidate close to the running best is refined afterwards rather than
     * only the best one: two low waters nineteen years apart can differ by a centimetre.
     */
    private static final long SCAN_STEP_SECONDS = 1800L;

    /** A coarse sample within this of the best is worth refining. */
    private static final double CANDIDATE_MARGIN_METRES = 0.05;

    private static final long[] REFINE_STEPS = { 60L, 5L };

    private static final long NODAL_CYCLE_SECONDS = 19L * 365L * 86400L;

    private TideDatum()
    {
    }

    /**
     * Scans a full nodal cycle for the extreme levels, refining every plausible candidate.
     * Costs hundreds of thousands of evaluations: never call it from the drawing thread.
     */
    public static Range compute(TidePredictor predictor, long startUnixSeconds)
    {
        long end = startUnixSeconds + NODAL_CYCLE_SECONDS;

        TidePredictor.Candidates candidates =
                predictor.scanCoarse(startUnixSeconds, end, SCAN_STEP_SECONDS, CANDIDATE_MARGIN_METRES);

        double lowest = _best(predictor, candidates.lowest, true);
        double highest = _best(predictor, candidates.highest, false);

        return new Range(lowest, highest);
    }

    /** Narrows every candidate down, step by step, and keeps the extreme of them all. */
    private static double _best(TidePredictor predictor, List<Long> candidates, boolean seekingLowest)
    {
        double best = seekingLowest ? Double.MAX_VALUE : -Double.MAX_VALUE;

        for (long candidate : candidates)
        {
            long centre = candidate;
            long span = SCAN_STEP_SECONDS;

            for (long step : REFINE_STEPS)
            {
                double localBest = predictor.heightAt(centre);
                long localTime = centre;

                for (long offset = -span; offset <= span; offset += step)
                {
                    double height = predictor.heightAt(centre + offset);

                    if (seekingLowest ? height < localBest : height > localBest)
                    {
                        localBest = height;
                        localTime = centre + offset;
                    }
                }

                centre = localTime;
                span = step;
            }

            double height = predictor.heightAt(centre);

            if (seekingLowest ? height < best : height > best)
            {
                best = height;
            }
        }

        return best;
    }
}
