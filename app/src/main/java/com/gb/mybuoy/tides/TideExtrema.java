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
 * high and low water detection.
 */
package com.gb.mybuoy.tides;

import java.util.ArrayList;
import java.util.List;

/** Finds the turning points of a predicted tide curve. */
public final class TideExtrema
{
    /** One high or low water. */
    public static final class Event
    {
        public final long unixSeconds;
        public final double heightMetres;
        public final boolean high;

        Event(long unixSeconds, double heightMetres, boolean high)
        {
            this.unixSeconds = unixSeconds;
            this.heightMetres = heightMetres;
            this.high = high;
        }
    }

    private TideExtrema()
    {
    }

    /**
     * Scans the window at the given step and returns every turning point,
     * refined by fitting a parabola through the three samples around it.
     */
    public static List<Event> find(TidePredictor predictor,
                                   long startUnixSeconds,
                                   long endUnixSeconds,
                                   int stepSeconds)
    {
        int sampleCount = (int) ((endUnixSeconds - startUnixSeconds) / stepSeconds) + 1;
        double[] heights = new double[sampleCount];

        for (int index = 0; index < sampleCount; index++)
        {
            heights[index] = predictor.heightAt(startUnixSeconds + (long) index * stepSeconds);
        }

        List<Event> events = new ArrayList<>();

        for (int index = 1; index < sampleCount - 1; index++)
        {
            double previous = heights[index - 1];
            double current = heights[index];
            double next = heights[index + 1];

            boolean isMaximum = current > previous && current >= next;
            boolean isMinimum = current < previous && current <= next;

            if (!isMaximum && !isMinimum)
            {
                continue;
            }

            // Vertex of the parabola through the three samples, in units of
            // the sampling step away from the middle sample.
            double denominator = previous - 2.0 * current + next;
            double offset = 0.0;

            if (denominator != 0.0)
            {
                offset = 0.5 * (previous - next) / denominator;
            }

            double refinedHeight = current
                    - 0.25 * (previous - next) * offset;

            long refinedTime = startUnixSeconds
                    + Math.round((index + offset) * stepSeconds);

            events.add(new Event(refinedTime, refinedHeight, isMaximum));
        }

        return events;
    }
}
