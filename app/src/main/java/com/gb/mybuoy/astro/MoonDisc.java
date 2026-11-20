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
 * The shape of a Moon at a given phase, and the turn that puts it in the sky.
 *
 * Two views draw the Moon: the big disc beside the figures, and the small one on
 * the sky map. They are the same Moon, so the geometry lives here once. They had
 * been two copies, and the copies drifted.
 *
 * THE SHAPE. The lit part is bounded by two curves: the limb, which is half of a
 * circle, and the terminator, which is the day-night line on a sphere seen edge
 * on and therefore projects to half of an ellipse. The ellipse has the same
 * height as the disc and a width of |2k - 1| of it, k being the lit fraction:
 * zero at the quarters, where the terminator is a straight line, and the full
 * width at new and full Moon, where it merges with the limb.
 *
 * Which side is lit, and which way the ellipse bulges, are the only things that
 * change between a crescent and a gibbous Moon.
 *
 * THE TURN. The shape is built upright, lit edge a quarter turn from the top, and
 * the caller turns the whole disc. The turn is negative, and that quarter turn has
 * to be taken back: the tilt is a position angle, running east of north, and east
 * is to the LEFT on a sky one looks up at, so it turns anticlockwise while a
 * canvas turns the other way. Getting either of those wrong has the horns turning
 * backwards through the night, which is the one thing a drawn Moon must not do.
 *
 * Nothing is computed here beyond the drawing: the fraction and the tilt arrive
 * already worked out.
 */
package com.gb.mybuoy.astro;

import android.graphics.Path;
import android.graphics.RectF;

/** The lit shape of the Moon, shared by every view that draws one. */
public final class MoonDisc
{
    private MoonDisc()
    {
    }

    /**
     * How far to turn a disc built by {@link #litPath}, in degrees for a canvas.
     *
     * @param tiltRadians the bright limb's angle from straight up, turning east
     * @param waxing      true while the lit part is growing
     */
    public static float rotationDegrees(double tiltRadians, boolean waxing)
    {
        return (float) -Math.toDegrees(tiltRadians) - (waxing ? 90.0f : 270.0f);
    }

    /**
     * Half the limb, then half the terminator back: the lit part of the disc,
     * upright, with the lit side on the right while waxing.
     *
     * @param path                reset and filled with the outline
     * @param bounds              scratch space, so nothing is allocated while drawing
     * @param illuminatedFraction zero at new Moon, one at full
     */
    public static void litPath(Path path, RectF bounds, float centreX, float centreY,
                               float radius, double illuminatedFraction, boolean waxing)
    {
        float terminatorWidth = (float) Math.abs(2.0 * illuminatedFraction - 1.0) * radius;

        boolean gibbous = illuminatedFraction > 0.5;

        path.reset();

        // The limb: a half circle down the lit side.
        bounds.set(centreX - radius, centreY - radius, centreX + radius, centreY + radius);

        path.moveTo(centreX, centreY - radius);
        path.arcTo(bounds, -90.0f, waxing ? 180.0f : -180.0f);

        // The terminator: a half ellipse back up to the top. A gibbous Moon has it
        // bulging the same way as the limb, a crescent has it bulging the other way,
        // which is the whole difference between the two shapes.
        bounds.set(centreX - terminatorWidth, centreY - radius,
                centreX + terminatorWidth, centreY + radius);

        boolean bulgeSameWay = gibbous == waxing;

        path.arcTo(bounds, 90.0f, bulgeSameWay ? 180.0f : -180.0f);

        path.close();
    }
}
