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
 * The two roads a body is on: the one across the sky, and the one under it.
 *
 * Each is drawn as its own arc, from one horizon to the other. Which pair is
 * the live one is settled by the INSTANT asked for, not by where the body
 * stands: an arc holds while the instant falls between its own two horizon
 * crossings, and the arc beside it holds while it still meets that one at a
 * crossing. A test on the bearing alone cannot do this. A body a fortnight
 * later stands in very nearly the same place in the sky, so such a test keeps
 * answering yes and the drawing is left showing the road of a passage long
 * past, with the body itself nowhere near it.
 *
 * Because each arc runs from horizon to horizon rather than once round the
 * whole circuit, neither has to meet its own tail. The path is a spiral, not a
 * loop: a full turn does not come back to where it started, and drawing one
 * leaves a gap in the middle of the hidden half. Two half-roads joined at the
 * rim have no such seam.
 *
 * Below the horizon a body keeps the BEARING the observer would point at
 * through the ground, and only its height is turned over. It is therefore
 * always on the map, and on the hidden road it travels from where it set
 * towards where it will rise.
 */
package com.gb.mybuoy.astro;

/** The live arc of the Sun and of the Moon, above the horizon and below it. */
public final class SkyTrack
{
    /** Radius of the Sun, in kilometres. */
    private static final double SUN_RADIUS_KM = 696000.0;

    /** Radius of the Moon, in kilometres. */
    private static final double MOON_RADIUS_KM = 1737.4;

    /** How far apart two arcs may place a shared crossing and still count as joined. */
    private static final long JOIN_SLACK_SECONDS = 300L;

    /** Step of the walk that brackets a horizon crossing. */
    private static final long STEP_SECONDS = 600L;

    /** How far the walk may go before giving up: beyond this a body is circumpolar. */
    private static final long REACH_SECONDS = 2L * 86400L;

    /** Points drawn along one arc. */
    private static final int SAMPLES = 110;

    /**
     * One arc: a road between two bounds, and what those bounds are.
     *
     * Ordinarily a bound is a horizon crossing, a rise or a set. Where a body neither
     * rises nor sets it is a meridian passage instead, which always exists, and the flags
     * say which: an end that is not a crossing is no time to print and no point to hang a
     * gauge on.
     */
    private static final class Arc
    {
        private final float[] _points;
        private final double _riseAzimuth;
        private final double _setAzimuth;
        private final long _riseSeconds;
        private final long _setSeconds;
        private final boolean _riseIsCrossing;
        private final boolean _setIsCrossing;

        /** The meridian the body passes in the middle of this arc, over it or under it. */
        private final long _middleSeconds;

        private Arc(float[] points, double riseAzimuth, double setAzimuth,
                    long riseSeconds, long setSeconds,
                    boolean riseIsCrossing, boolean setIsCrossing, long middleSeconds)
        {
            _middleSeconds = middleSeconds;
            _points = points;
            _riseAzimuth = riseAzimuth;
            _setAzimuth = setAzimuth;
            _riseSeconds = riseSeconds;
            _setSeconds = setSeconds;
            _riseIsCrossing = riseIsCrossing;
            _setIsCrossing = setIsCrossing;
        }
    }

    private final double _latitudeRadians;
    private final double _longitudeRadians;

    private final RiseSet.Altitude _sun;
    private final RiseSet.Altitude _moon;

    private Arc _sunAbove = null;
    private Arc _sunBelow = null;
    private Arc _moonAbove = null;
    private Arc _moonBelow = null;

    /* Which of the two arcs each body is travelling at the instant last asked for. */
    private boolean _sunLiveAbove = false;
    private boolean _moonLiveAbove = false;

    /**
     * @param latitudeDegrees  observer's latitude, positive north
     * @param longitudeDegrees observer's longitude, positive east
     */
    public SkyTrack(double latitudeDegrees, double longitudeDegrees)
    {
        _latitudeRadians = Math.toRadians(latitudeDegrees);
        _longitudeRadians = Math.toRadians(longitudeDegrees);

        _sun = RiseSet.sun(_longitudeRadians, _latitudeRadians);
        _moon = RiseSet.moon(_longitudeRadians, _latitudeRadians);
    }

    /** Brings the four arcs up to date, recomputing only those that no longer hold. */
    public void update(long unixSeconds)
    {
        _sunLiveAbove = sunUp(unixSeconds);
        _moonLiveAbove = moonUp(unixSeconds);

        Arc[] sun = _bring(_sunAbove, _sunBelow, _sun, SUN_RADIUS_KM, false, unixSeconds,
                _sunLiveAbove);

        _sunAbove = sun[0];
        _sunBelow = sun[1];

        Arc[] moon = _bring(_moonAbove, _moonBelow, _moon, MOON_RADIUS_KM, true, unixSeconds,
                _moonLiveAbove);

        _moonAbove = moon[0];
        _moonBelow = moon[1];
    }

    /*
     * The pair of arcs a body is on at one instant: kept when they are still the right
     * ones, rebuilt when they are not.
     *
     * The one the body is on at that instant is checked against its own two crossings,
     * which is exact. The other is the one just left behind or about to be taken, and is
     * right as long as it still joins the first at a horizon.
     *
     * Only one of the two is ever rebuilt in the ordinary case, and neither while nothing
     * has been crossed: a rebuild walks up to a day of sky for each end of the arc, and
     * this runs under the finger while the curve below is dragged.
     */
    private Arc[] _bring(Arc above, Arc below, RiseSet.Altitude body, double radiusKm,
                         boolean moon, long unixSeconds, boolean up)
    {
        Arc live = up ? above : below;
        Arc other = up ? below : above;

        if (!_holds(live, unixSeconds))
        {
            live = _arcFor(body, radiusKm, moon, unixSeconds, up);
        }

        if (!_adjoins(other, live))
        {
            // A road bounded by two turns ends on no horizon, so there is no arc on the
            // far side of one to go and find. Looking for it would walk two days of sky
            // at every update, under the finger, for nothing.
            other = _boundedByTurns(live) ? null
                    : _arcFor(body, radiusKm, moon, unixSeconds, !up);
        }

        return up ? new Arc[] { live, other } : new Arc[] { other, live };
    }

    /* Neither end a horizon crossing: the body neither rises nor sets on this road. */
    private boolean _boundedByTurns(Arc arc)
    {
        return (arc != null) && !arc._riseIsCrossing && !arc._setIsCrossing;
    }

    /* The road a body is on holds for exactly as long as the body is on it. */
    private boolean _holds(Arc arc, long unixSeconds)
    {
        if (arc == null)
        {
            return false;
        }

        return (unixSeconds >= Math.min(arc._riseSeconds, arc._setSeconds))
                && (unixSeconds <= Math.max(arc._riseSeconds, arc._setSeconds));
    }

    /*
     * Whether two arcs meet at a horizon crossing. The shared end is the same event
     * worked out twice, from two instants a few hours apart, and the body having moved
     * between them the two answers differ by a few seconds. Anything further apart than
     * the slack is another passage altogether.
     */
    private boolean _adjoins(Arc arc, Arc live)
    {
        if ((arc == null) || (live == null))
        {
            return false;
        }

        long from = Math.min(arc._riseSeconds, arc._setSeconds);
        long to = Math.max(arc._riseSeconds, arc._setSeconds);

        long liveFrom = Math.min(live._riseSeconds, live._setSeconds);
        long liveTo = Math.max(live._riseSeconds, live._setSeconds);

        return (Math.abs(to - liveFrom) < JOIN_SLACK_SECONDS)
                || (Math.abs(from - liveTo) < JOIN_SLACK_SECONDS);
    }

    /**
     * Whether the Sun stands above the horizon, by the rule that decides its rise and
     * its set: the upper limb on the refracted horizon, which puts the centre fifty
     * arcminutes lower.
     *
     * Anyone drawing the body has to ask THIS, not whether the centre is above zero.
     * The two answers differ by a band of less than a degree, three minutes of an
     * ordinary day; but in late autumn at seventy-eight degrees north the Sun spends
     * its whole short day inside that band, and a map reading the centre would paint
     * it set from one end of the day to the other.
     */
    public boolean sunUp(long unixSeconds)
    {
        return _sun.radiansAt(unixSeconds) > _targetFor(SUN_RADIUS_KM, false, unixSeconds);
    }

    /** The same rule for the Moon, whose semidiameter swings far more than the Sun's. */
    public boolean moonUp(long unixSeconds)
    {
        return _moon.radiansAt(unixSeconds) > _targetFor(MOON_RADIUS_KM, true, unixSeconds);
    }

    public float[] sunAbove()
    {
        return (_sunAbove == null) ? null : _sunAbove._points;
    }

    public float[] sunBelow()
    {
        return (_sunBelow == null) ? null : _sunBelow._points;
    }

    public float[] moonAbove()
    {
        return (_moonAbove == null) ? null : _moonAbove._points;
    }

    public float[] moonBelow()
    {
        return (_moonBelow == null) ? null : _moonBelow._points;
    }

    /*
     * The bearings of the arc ASKED FOR, not always those of the one above the horizon.
     * The line joining a rise to a set belongs to its own arc and must change with it, or
     * the drawing shows the road of one passage closed off by the horizon of another.
     */
    public double sunRiseAzimuth(boolean above)
    {
        return _riseAzimuth(above ? _sunAbove : _sunBelow);
    }

    public double sunSetAzimuth(boolean above)
    {
        return _setAzimuth(above ? _sunAbove : _sunBelow);
    }

    public double moonRiseAzimuth(boolean above)
    {
        return _riseAzimuth(above ? _moonAbove : _moonBelow);
    }

    public double moonSetAzimuth(boolean above)
    {
        return _setAzimuth(above ? _moonAbove : _moonBelow);
    }

    /* An end that is a meridian passage is not on the horizon, so it has no bearing to
     * give: the horizon line of that arc does not exist and must not be drawn. */
    private double _riseAzimuth(Arc arc)
    {
        return ((arc == null) || !arc._riseIsCrossing) ? -1.0 : arc._riseAzimuth;
    }

    private double _setAzimuth(Arc arc)
    {
        return ((arc == null) || !arc._setIsCrossing) ? -1.0 : arc._setAzimuth;
    }

    /** The Moon's stretch, in the same three parts as the Sun's. */
    public long[] moonSpan(boolean above)
    {
        Arc arc = above ? _moonAbove : _moonBelow;

        if (arc == null)
        {
            return null;
        }

        return new long[] {
                above ? arc._riseSeconds : arc._setSeconds,
                arc._middleSeconds,
                above ? arc._setSeconds : arc._riseSeconds,
        };
    }

    /*
     * The times of the arc on show, not of some separately computed day. Taking them from
     * anywhere else would put the figures and the drawing at odds exactly when the body
     * has just crossed a horizon, which is when they are read most closely.
     */
    public long sunRiseSeconds()
    {
        return _riseSeconds(_sunLiveAbove ? _sunAbove : _sunBelow);
    }

    public long sunSetSeconds()
    {
        return _setSeconds(_sunLiveAbove ? _sunAbove : _sunBelow);
    }

    public long moonRiseSeconds()
    {
        return _riseSeconds(_moonLiveAbove ? _moonAbove : _moonBelow);
    }

    public long moonSetSeconds()
    {
        return _setSeconds(_moonLiveAbove ? _moonAbove : _moonBelow);
    }

    /*
     * The two ends of the arc under the body's feet, hence one behind and one ahead: by
     * day its rise and its set, by night the set it has just made and the rise to come.
     * Each is given only if it is a real crossing.
     */
    private long _riseSeconds(Arc arc)
    {
        return ((arc == null) || !arc._riseIsCrossing) ? Long.MIN_VALUE : arc._riseSeconds;
    }

    private long _setSeconds(Arc arc)
    {
        return ((arc == null) || !arc._setIsCrossing) ? Long.MIN_VALUE : arc._setSeconds;
    }

    /** The Sun's stretch: when it starts, when it passes the meridian, when it ends. */
    public long[] sunSpan(boolean above)
    {
        Arc arc = above ? _sunAbove : _sunBelow;

        if (arc == null)
        {
            return null;
        }

        return new long[] {
                above ? arc._riseSeconds : arc._setSeconds,
                arc._middleSeconds,
                above ? arc._setSeconds : arc._riseSeconds,
        };
    }

    /*
     * Builds one arc: the passage of the body over the road asked for, that it is on at
     * this instant or that lies nearest on either side of it.
     *
     * The search runs both ways from the instant, so dragging backwards through time is
     * answered exactly as dragging forwards is.
     */
    /**
     * @param above true for the road across the sky, false for the one beneath it
     */
    private Arc _arcFor(RiseSet.Altitude body, double radiusKm, boolean moon,
                        long unixSeconds, boolean above)
    {
        double target = _targetFor(radiusKm, moon, unixSeconds);

        boolean up = body.radiansAt(unixSeconds) > target;

        long from;
        long to;

        boolean fromCrossed = true;
        boolean toCrossed = true;

        if (up == above)
        {
            // The body is on the road being asked for: it is the one under its feet.
            //
            // A road that rises AND sets is bounded by its two crossings, as it always
            // has been, capped by the far meridian so that it can never wind round the map
            // twice when the crossing it reaches for is two days off.
            //
            // A road missing one of them belongs to a body that hardly meets the horizon,
            // and is cut at the turns instead: each end is then whichever comes first, the
            // crossing or the meridian passage of either kind. The arc is the half the
            // body is on, from where it turned last to where it turns next, climbing
            // throughout or falling throughout, and a set it has really made still bounds
            // it when it is the nearer of the two.
            long crossedBack = _walk(body, target, unixSeconds, false, above);
            long crossedAhead = _walk(body, target, unixSeconds, true, !above);

            boolean halves = (crossedBack == Long.MIN_VALUE) || (crossedAhead == Long.MIN_VALUE);

            if (halves)
            {
                from = _nearer(crossedBack, _turn(body, unixSeconds, false), false);
                to = _nearer(crossedAhead, _turn(body, unixSeconds, true), true);
            }
            else
            {
                from = _nearer(crossedBack, _transit(body, unixSeconds, false, !above), false);
                to = _nearer(crossedAhead, _transit(body, unixSeconds, true, !above), true);
            }

            fromCrossed = (from == crossedBack) && (crossedBack != Long.MIN_VALUE);
            toCrossed = (to == crossedAhead) && (crossedAhead != Long.MIN_VALUE);
        }
        else
        {
            // It is on the other road, so the one wanted is whichever end is nearer: the
            // arc just left behind, or the one about to begin.
            long behind = _walk(body, target, unixSeconds, false, !above);
            long ahead = _walk(body, target, unixSeconds, true, above);

            boolean takeBehind = (behind != Long.MIN_VALUE)
                    && ((ahead == Long.MIN_VALUE)
                        || ((unixSeconds - behind) < (ahead - unixSeconds)));

            if (takeBehind)
            {
                to = behind;
                from = _walk(body, target, to - STEP_SECONDS, false, above);
            }
            else
            {
                from = ahead;
                to = _walk(body, target, from + STEP_SECONDS, true, !above);
            }
        }

        if ((from == Long.MIN_VALUE) || (to == Long.MIN_VALUE) || (to <= from))
        {
            return null;
        }

        double startAzimuth = _horizontalAt(body, from).azimuthRadians();
        double endAzimuth = _horizontalAt(body, to).azimuthRadians();

        // An arc above the horizon runs rise to set; one below runs set to rise.
        return new Arc(_sample(body, from, to),
                above ? startAzimuth : endAzimuth,
                above ? endAzimuth : startAzimuth,
                above ? from : to,
                above ? to : from,
                above ? fromCrossed : toCrossed,
                above ? toCrossed : fromCrossed,
                _meridian(body, from, to, above));
    }

    /*
     * The meridian passage inside an arc. Over the horizon the hour angle runs from
     * negative at the rise to positive at the set and the crossing is where it turns;
     * under it, the angle folds at the far meridian instead, which is the same event
     * seen from the other side of the Earth.
     *
     * It is NOT the midpoint between the two ends, and must not be taken for it: the
     * body's declination drifts from one day to the next, which moves the meridian
     * passage off centre by a good part of an hour.
     */
    private long _meridian(RiseSet.Altitude body, long from, long to, boolean above)
    {
        for (long t = from; t < to; t += STEP_SECONDS)
        {
            long next = Math.min(to, t + STEP_SECONDS);

            double here = body.hourAngleAt(t);
            double there = body.hourAngleAt(next);

            boolean crossed = above ? ((here < 0.0) && (there >= 0.0))
                                    : ((here > 0.0) && (there < 0.0));

            if (crossed)
            {
                return _refineMeridian(body, t, next, above);
            }
        }

        return (from + to) / 2L;
    }

    /* Whichever of two bounds the body meets first, either side being possibly missing. */
    private long _nearer(long crossing, long turn, boolean forward)
    {
        if (crossing == Long.MIN_VALUE)
        {
            return turn;
        }

        if (turn == Long.MIN_VALUE)
        {
            return crossing;
        }

        return forward ? Math.min(crossing, turn) : Math.max(crossing, turn);
    }

    /*
     * The meridian passage of the kind asked for: upper, when the body stands at its
     * highest, or lower, when it is under the pole at its lowest. This is the one an
     * ordinary arc holds in its middle, and it caps a crossing that lies too far off.
     */
    private long _transit(RiseSet.Altitude body, long from, boolean forward, boolean upper)
    {
        long step = forward ? STEP_SECONDS : -STEP_SECONDS;

        for (long n = 0; n < (REACH_SECONDS / STEP_SECONDS); n++)
        {
            long here = from + (n * step);
            long next = here + step;

            long earlier = forward ? here : next;
            long later = forward ? next : here;

            double before = body.hourAngleAt(earlier);
            double after = body.hourAngleAt(later);

            boolean turned = upper ? ((before < 0.0) && (after >= 0.0))
                                   : ((before > 0.0) && (after < 0.0));

            if (turned)
            {
                return _refineMeridian(body, earlier, later, upper);
            }
        }

        return Long.MIN_VALUE;
    }

    /*
     * The next turn of the body, searched the way a horizon crossing is and with the same
     * reach: its passage of the local meridian, upper when it stands at its highest, lower
     * when it is under the pole at its lowest. Either counts, the first one met wins.
     *
     * This is the fallback bound of an arc. A body that neither rises nor sets still turns
     * twice a day, so a road always has two ends, and between two turns its height only
     * climbs or only falls.
     */
    private long _turn(RiseSet.Altitude body, long from, boolean forward)
    {
        long step = forward ? STEP_SECONDS : -STEP_SECONDS;

        for (long n = 0; n < (REACH_SECONDS / STEP_SECONDS); n++)
        {
            long here = from + (n * step);
            long next = here + step;

            long earlier = forward ? here : next;
            long later = forward ? next : here;

            double before = body.hourAngleAt(earlier);
            double after = body.hourAngleAt(later);

            if ((before < 0.0) && (after >= 0.0))
            {
                return _refineMeridian(body, earlier, later, true);
            }

            if ((before > 0.0) && (after < 0.0))
            {
                return _refineMeridian(body, earlier, later, false);
            }
        }

        return Long.MIN_VALUE;
    }

    /*
     * Halved down to the second, as the horizon crossings are. Returning the middle of a
     * ten minute bracket put the mark a good five minutes off, which is visible on a
     * gauge whose whole point is that its figures are exact.
     */
    private long _refineMeridian(RiseSet.Altitude body, long before, long after, boolean above)
    {
        for (int step = 0; step < 12; step++)
        {
            long middle = (before + after) / 2L;

            if ((middle == before) || (middle == after))
            {
                break;
            }

            double angle = body.hourAngleAt(middle);

            boolean stillEarly = above ? (angle < 0.0) : (angle > 0.0);

            if (stillEarly)
            {
                before = middle;
            }
            else
            {
                after = middle;
            }
        }

        return (before + after) / 2L;
    }

    /* The height at which the upper edge sits on the refracted horizon. */
    private double _targetFor(double radiusKm, boolean moon, long unixSeconds)
    {
        double jdTt = AstroTime.julianDateTt(unixSeconds);

        double distance = moon ? MoonErfa.at(jdTt).distanceKm() : SunErfa.at(jdTt).distanceKm();

        return RiseSet.upperLimbTarget(radiusKm, distance);
    }

    /**
     * Walks to the nearest crossing of the horizon.
     *
     * @param forward which way in time to go
     * @param rising  whether the wanted crossing is upwards, read forwards in time
     */
    private long _walk(RiseSet.Altitude body, double target, long from,
                       boolean forward, boolean rising)
    {
        long step = forward ? STEP_SECONDS : -STEP_SECONDS;

        for (long n = 0; n < (REACH_SECONDS / STEP_SECONDS); n++)
        {
            long here = from + (n * step);
            long next = here + step;

            long earlier = forward ? here : next;
            long later = forward ? next : here;

            boolean crossed = rising
                    ? ((body.radiansAt(earlier) <= target) && (body.radiansAt(later) > target))
                    : ((body.radiansAt(earlier) > target) && (body.radiansAt(later) <= target));

            if (crossed)
            {
                return _refine(body, target, earlier, later);
            }
        }

        return Long.MIN_VALUE;
    }

    /* Halves the bracket until the crossing is pinned to a few seconds. */
    private long _refine(RiseSet.Altitude body, double target, long before, long after)
    {
        boolean belowAtStart = body.radiansAt(before) <= target;

        for (int step = 0; step < 10; step++)
        {
            long middle = (before + after) / 2L;

            if ((middle == before) || (middle == after))
            {
                break;
            }

            if ((body.radiansAt(middle) <= target) == belowAtStart)
            {
                before = middle;
            }
            else
            {
                after = middle;
            }
        }

        return (before + after) / 2L;
    }

    /*
     * Bearing and height, two numbers per point. The bearing is always the observer's
     * own, the one he would point at even through the ground; only the height is turned
     * over, which is what keeps a body that has set on the map and on its own road.
     */
    private float[] _sample(RiseSet.Altitude body, long from, long to)
    {
        float[] points = new float[SAMPLES * 2];

        for (int n = 0; n < SAMPLES; n++)
        {
            long when = from + (((to - from) * n) / (SAMPLES - 1));

            Topocentric.Horizontal where = _horizontalAt(body, when);

            points[n * 2] = (float) where.azimuthRadians();
            points[(n * 2) + 1] = (float) Math.abs(where.altitudeRadians());
        }

        return points;
    }

    /*
     * The interface the scan uses gives a height and nothing else, so the bearing is
     * worked out here from the same ephemerides.
     */
    private Topocentric.Horizontal _horizontalAt(RiseSet.Altitude body, long unixSeconds)
    {
        boolean moon = (body == _moon);

        double jdTt = AstroTime.julianDateTt(unixSeconds);

        double longitude;
        double latitude;
        double distance;

        if (moon)
        {
            MoonErfa.Position position = MoonErfa.at(jdTt);

            longitude = position.longitudeRadians();
            latitude = position.latitudeRadians();
            distance = position.distanceKm();
        }
        else
        {
            SunErfa.Position position = SunErfa.at(jdTt);

            longitude = position.longitudeRadians();
            latitude = position.latitudeRadians();
            distance = 0.0;
        }

        Topocentric.Equatorial equatorial = Topocentric.equatorialFromEcliptic(
                longitude, latitude, AstroTime.meanObliquity(jdTt));

        double sidereal = AstroTime.greenwichMeanSiderealTime(
                AstroTime.julianDateUtc(unixSeconds), jdTt);

        return Topocentric.horizontalFromEquatorial(equatorial, sidereal,
                _longitudeRadians, _latitudeRadians, distance);
    }
}
