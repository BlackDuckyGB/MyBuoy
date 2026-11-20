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
 * The whole sky on one disc, seen from where the user stands looking up.
 *
 * The rim is the horizon and the centre is straight overhead, so how far in a
 * body sits IS its height: hugging the edge means rasant, near the middle means
 * high. North is at the top and east on the RIGHT, which is the way round a map
 * is drawn, not the way a compass rose would look if you held it over your head.
 * It is the convention every planisphere uses and the one that matches the
 * bearings the rest of the application shows.
 *
 * A body below the horizon is not dropped: it is placed just outside the rim, in
 * grey, at its bearing. Knowing the Sun is down and roughly where it will come
 * back up is worth more than an empty disc.
 *
 * Nothing is computed here. Positions arrive already worked out, and no object
 * is allocated while drawing.
 */
package com.gb.mybuoy.astro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** Shows where the Sun and the Moon are in the sky right now. */
public final class SkyView extends View
{
    private static final int COLOUR_SKY = 0xFFE8F1FA;
    private static final int COLOUR_RIM = 0xFF90A4AE;
    private static final int COLOUR_GRID = 0xFFC3D2DC;
    private static final int COLOUR_HEIGHT = 0xFF78909C;
    /*
     * Black, not red. Red belongs to the present on the curve below; this triangle marks
     * the instant the reader is pointing at, which is the curve's black cursor.
     */
    private static final int COLOUR_NOW_MARK = 0xFF212121;

    /** How close to the start of a half a graduation may fall before it is dropped. */
    private static final long CROWDING_SECONDS = 50L * 60L;

    /** How faint the gauge of whichever body is not the next to cross its horizon. */
    private static final int SECOND_ALPHA = 95;
    private static final int COLOUR_CARDINAL = 0xFF455A64;
    private static final int COLOUR_SUN = 0xFFFFA000;
    private static final int COLOUR_SUN_DOWN = 0xFFBCAA92;
    private static final int COLOUR_MOON_LIT = 0xFFF5F3E7;
    private static final int COLOUR_MOON_DARK = 0xFF55607A;
    private static final int COLOUR_MOON_EDGE = 0xFF37474F;
    private static final int COLOUR_MOON_LIT_DOWN = 0xFFD8D6CC;
    private static final int COLOUR_MOON_DARK_DOWN = 0xFF9AA3B4;
    private static final int COLOUR_MOON_EDGE_DOWN = 0xFF8C9AA3;
    private static final int COLOUR_SUN_HALO = 0x66FFA000;
    private static final int COLOUR_MOON_HALO = 0x666A7BA8;
    private static final int COLOUR_SUN_TRACK = 0xFFFFC107;
    private static final int COLOUR_SUN_LINE = 0xFFE0A030;
    private static final int COLOUR_MOON_TRACK = 0xFF7E8DB5;
    private static final int COLOUR_MOON_LINE = 0xFF9AA7C4;

    /** The compass needle: the red of the heading on the main map, laid on translucent. */
    private static final int COLOUR_NEEDLE = 0x99FF0000;

    /** Radius of a body's disc, as a share of the sky's radius. */
    private static final float BODY_SIZE = 0.11f;

    /** How far the Sun's rays reach past its own edge, in the same share. */
    private static final float RAY_REACH = 0.11f;

    /** How far a halo may reach past a body's own edge, in the same share. */
    private static final float HALO_REACH = 0.20f;

    /**
     * How far the compass needle reaches past the rim, as a share of it: far enough to
     * read the bearing off the horizon, not far enough to reach the cardinal letters.
     */
    private static final float NEEDLE_REACH = 0.08f;

    private final Paint _skyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _hiddenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _dayLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _heightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _heightHalo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _gaugePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _gaugeTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _gaugeLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _cursorFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path _cursorTriangle = new Path();
    private final Paint _needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _needleFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path _needleHead = new Path();

    private final Path _lit = new Path();
    private final Path _track = new Path();
    private final Path _hidden = new Path();
    private final RectF _bounds = new RectF();

    private boolean _known = false;

    private double _needleAzimuth = 0.0;
    private boolean _hasNeedle = false;

    private double _sunAzimuth = 0.0;
    private double _sunAltitude = 0.0;
    private boolean _sunVisible = false;
    private boolean _moonVisible = false;

    private double _moonAzimuth = 0.0;
    private double _moonAltitude = 0.0;
    private double _moonFraction = 0.5;
    private double _moonTilt = 0.0;
    private boolean _moonWaxing = true;

    /*
     * What each body is doing to the ground right now, in microgals, and the largest
     * either of them reaches over the month. The halo drawn around a body carries the
     * first against the second, which is the one thing a map of directions cannot say
     * on its own: a body overhead and a body underfoot pull the same, and one on the
     * horizon barely pulls at all.
     */
    private double _sunPull = 0.0;
    private double _moonPull = 0.0;
    private double _peakPull = 0.0;

    /* A stretch per body, for the gauges drawn along their horizon lines. */
    private long[] _sunSpan = null;
    private long[] _moonSpan = null;
    private long _spanNow = 0L;

    private float[] _sunAbove = null;
    private float[] _sunBelow = null;
    private double _sunRiseAzimuth = -1.0;
    private double _sunSetAzimuth = -1.0;

    private float[] _moonAbove = null;
    private float[] _moonBelow = null;
    private double _moonRiseAzimuth = -1.0;
    private double _moonSetAzimuth = -1.0;

    public SkyView(Context context)
    {
        super(context);
        _setUpPaints();
    }

    public SkyView(Context context, AttributeSet attributes)
    {
        super(context, attributes);
        _setUpPaints();
    }

    /**
     * All angles in radians. Altitudes are the ones to PLOT and are never negative: a
     * body that has set arrives with its position as seen from the antipode, and its
     * flag false. The map then always has it somewhere, on the far sky rather than the
     * near one.
     */
    public void setSky(double sunAzimuth, double sunAltitude, boolean sunVisible,
                       double moonAzimuth, double moonAltitude, boolean moonVisible,
                       double moonFraction, boolean moonWaxing, double moonTilt)
    {
        _sunVisible = sunVisible;
        _moonVisible = moonVisible;

        _sunAzimuth = sunAzimuth;
        _sunAltitude = sunAltitude;
        _moonAzimuth = moonAzimuth;
        _moonAltitude = moonAltitude;
        _moonFraction = moonFraction;
        _moonWaxing = moonWaxing;
        _moonTilt = moonTilt;
        _known = true;

        invalidate();
    }

    /**
     * The two roads of each body: the one across the sky and the one beneath it.
     *
     * Each arrives as pairs of bearing and height, the height always positive. The road
     * below is drawn dotted at the same scale as the one above, so a body as far under
     * the ground as another is over it lands the same distance from the middle. That
     * matters here, because the pull on the ground is the same in both cases.
     *
     * The straight line between a rise and a set is the one thing a disc like this
     * cannot show on its own: away from the equator the Sun does not rise due east nor
     * set due west, and in winter at these latitudes it does both well round to the
     * south.
     *
     * A bearing below zero means the body does not rise, or does not set, within reach.
     */
    public void setTracks(float[] sunAbove, float[] sunBelow,
                          double sunRiseAzimuth, double sunSetAzimuth,
                          float[] moonAbove, float[] moonBelow,
                          double moonRiseAzimuth, double moonSetAzimuth)
    {
        _sunAbove = sunAbove;
        _sunBelow = sunBelow;
        _sunRiseAzimuth = sunRiseAzimuth;
        _sunSetAzimuth = sunSetAzimuth;

        _moonAbove = moonAbove;
        _moonBelow = moonBelow;
        _moonRiseAzimuth = moonRiseAzimuth;
        _moonSetAzimuth = moonSetAzimuth;

        invalidate();
    }

    /**
     * @param sunMicroGal  the Sun's share of the change in gravity at this moment
     * @param moonMicroGal the Moon's share
     * @param peakMicroGal the largest either reaches over the prepared month, which sets
     *                     the scale so that a halo means the same thing from day to day
     */
    public void setPull(double sunMicroGal, double moonMicroGal, double peakMicroGal)
    {
        _sunPull = sunMicroGal;
        _moonPull = moonMicroGal;
        _peakPull = peakMicroGal;

        invalidate();
    }

    /**
     * The stretch the Sun is in, laid along the line joining its rise to its set.
     *
     * That line is the horizon of this particular passage, and its two ends ARE the two
     * events, so the hours left fall naturally on it. Read from the setting end towards
     * the rising one, it runs the way the Sun does: by day from the east on the right to
     * the west on the left, by night the other way round.
     */
    public void setSunSpan(long from, long middle, long to, long now)
    {
        _sunSpan = _span(from, middle, to);
        _spanNow = now;

        invalidate();
    }

    /** The Moon's stretch, in the same three parts. */
    public void setMoonSpan(long from, long middle, long to)
    {
        _moonSpan = _span(from, middle, to);

        invalidate();
    }

    /**
     * Where the phone itself points, in true degrees, or NaN when the compass has nothing
     * to say. Turning the phone then matches the map to the sky overhead.
     *
     * This is the phone's heading and not the course over ground: it still means something
     * standing still, which is when a map like this one gets read.
     */
    public void setHeading(double trueDegrees)
    {
        boolean has = !Double.isNaN(trueDegrees);
        double azimuth = has ? Math.toRadians(trueDegrees) : 0.0;

        // The sensor freezes its output while the phone is still, so most arrivals say
        // nothing new and must not cost a redraw.
        if ((has == _hasNeedle) && (Math.abs(azimuth - _needleAzimuth) < 0.0005)) {
            return;
        }

        _hasNeedle = has;
        _needleAzimuth = azimuth;

        invalidate();
    }

    private long[] _span(long from, long middle, long to)
    {
        // The highest point may sit ON an end rather than between them: an arc cut at the
        // turn starts there. The gauge then carries a single count down, over its whole
        // length, instead of one for each half.
        boolean sound = (to > from) && (middle >= from) && (middle <= to);

        return sound ? new long[] { from, middle, to } : null;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec)
    {
        int width = MeasureSpec.getSize(widthSpec);
        int height = MeasureSpec.getSize(heightSpec);

        int side = Math.min(width, (height > 0) ? height : width);

        setMeasuredDimension(side, side);
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);

        float side = Math.min(getWidth(), getHeight());

        if (side <= 0.0f) {
            return;
        }

        float centreX = getWidth() / 2.0f;
        float centreY = getHeight() / 2.0f;

        // Room is left all round for two different things, and the tighter of the two
        // wins: the cardinal letters just outside the rim, and a body parked beyond it
        // because it has set, which reaches further still once its own width and its
        // rays are counted. Taking only the letters into account clipped the Sun.
        float available = side / 2.0f;

        float forBodies = available / (1.0f + BODY_SIZE + HALO_REACH + RAY_REACH);
        float forLetters = available - (_textPaint.getTextSize() * 1.3f);

        float radius = Math.min(forBodies, forLetters);

        canvas.drawCircle(centreX, centreY, radius, _skyPaint);

        // Thirty and sixty degrees up, so the height can be read and not just guessed.
        canvas.drawCircle(centreX, centreY, radius * (2.0f / 3.0f), _gridPaint);
        canvas.drawCircle(centreX, centreY, radius / 3.0f, _gridPaint);

        // Named, so that "two thirds of the way out" needs no working out. Each is drawn
        // twice, once fat in the colour of the sky and once on top in its own, which cuts
        // a clean gap in whatever track happens to pass underneath.
        _heightHalo.setTextAlign(Paint.Align.LEFT);
        _heightPaint.setTextAlign(Paint.Align.LEFT);

        float x = centreX + (_heightPaint.getTextSize() * 0.3f);

        float outer = centreY - (radius * (2.0f / 3.0f)) + (_heightPaint.getTextSize() * 1.15f);
        float inner = centreY - (radius / 3.0f) + (_heightPaint.getTextSize() * 1.15f);

        canvas.drawText("30\u00b0", x, outer, _heightHalo);
        canvas.drawText("30\u00b0", x, outer, _heightPaint);

        canvas.drawText("60\u00b0", x, inner, _heightHalo);
        canvas.drawText("60\u00b0", x, inner, _heightPaint);

        canvas.drawLine(centreX - radius, centreY, centreX + radius, centreY, _gridPaint);
        canvas.drawLine(centreX, centreY - radius, centreX, centreY + radius, _gridPaint);

        canvas.drawCircle(centreX, centreY, radius, _rimPaint);

        _drawNeedle(canvas, centreX, centreY, radius);

        _drawCardinals(canvas, centreX, centreY, radius);

        _drawTracks(canvas, centreX, centreY, radius);

        if (!_known) {
            return;
        }

        // Whichever body belongs to the sky one is actually looking at goes in front:
        // the Sun while it is up, the Moon once it has gone down. Mixed cases fall out
        // of the same test, the risen body being the one in front.
        if (_sunVisible) {
            _drawMoon(canvas, centreX, centreY, radius);
            _drawSun(canvas, centreX, centreY, radius);
        } else {
            _drawSun(canvas, centreX, centreY, radius);
            _drawMoon(canvas, centreX, centreY, radius);
        }
    }

    private void _drawCardinals(Canvas canvas, float centreX, float centreY, float radius)
    {
        float out = radius + _textPaint.getTextSize() * 0.95f;
        float lift = _textPaint.getTextSize() * 0.36f;

        canvas.drawText("N", centreX, centreY - radius - _textPaint.getTextSize() * 0.3f, _textPaint);
        canvas.drawText("S", centreX, centreY + out + lift, _textPaint);
        canvas.drawText("E", centreX + out, centreY + lift, _textPaint);
        canvas.drawText("W", centreX - out, centreY + lift, _textPaint);
    }

    /*
     * The compass needle: where the phone is pointing, hinged on the observer at the
     * centre of the disc, which is where he stands.
     *
     * It goes under everything that carries a reading -- the roads, their gauges, the two
     * bodies, and the cardinal letters as well -- so that pointing the phone at the sky
     * can never cost the page a figure. Translucent red, the colour the main map uses for
     * the same heading.
     */
    private void _drawNeedle(Canvas canvas, float centreX, float centreY, float radius)
    {
        if (!_hasNeedle) {
            return;
        }

        float tip = radius * (1.0f + NEEDLE_REACH);
        float base = radius * (1.0f - (NEEDLE_REACH / 2.0f));

        // Along the bearing and across it, in screen terms: x grows east, y grows south.
        float alongX = (float) Math.sin(_needleAzimuth);
        float alongY = (float) -Math.cos(_needleAzimuth);
        float acrossX = (float) Math.cos(_needleAzimuth);
        float acrossY = (float) Math.sin(_needleAzimuth);

        float baseX = centreX + (alongX * base);
        float baseY = centreY + (alongY * base);
        float wing = radius * 0.05f;

        canvas.drawLine(centreX, centreY, baseX, baseY, _needlePaint);

        // The pivot, so it reads as a needle and not as a stray line across the disc.
        canvas.drawCircle(centreX, centreY, radius * 0.035f, _needleFill);

        // The head, astride the rim: its point lands just outside the horizon.
        _needleHead.reset();
        _needleHead.moveTo(centreX + (alongX * tip), centreY + (alongY * tip));
        _needleHead.lineTo(baseX + (acrossX * wing), baseY + (acrossY * wing));
        _needleHead.lineTo(baseX - (acrossX * wing), baseY - (acrossY * wing));
        _needleHead.close();

        canvas.drawPath(_needleHead, _needleFill);
    }

    /*
     * Where a bearing and a height land on the disc. The rim is the horizon and the
     * centre the zenith, so the distance from the middle is the complement of the
     * height. Below the horizon everything is parked at one radius just outside, since
     * how far below is not worth a scale of its own.
     */
    /*
     * Rim to centre is horizon to zenith, and nothing is ever drawn outside. A body that
     * has set is handed over already converted: what arrives is its height seen from the
     * antipode, which is positive, and a flag saying the sky it belongs to is the far
     * one. The map therefore always shows a body somewhere on it, and the two roads
     * share the same scale.
     */
    private float _radiusFor(double altitudeRadians, float radius)
    {
        double fraction = 1.0 - (Math.abs(altitudeRadians) / (Math.PI / 2.0));

        return (float) (radius * Math.max(0.0, fraction));
    }

    private float _xFor(float centreX, double azimuthRadians, float r)
    {
        return centreX + (float) (r * Math.sin(azimuthRadians));
    }

    private float _yFor(float centreY, double azimuthRadians, float r)
    {
        return centreY - (float) (r * Math.cos(azimuthRadians));
    }

    /*
     * The roads and their gauges, drawn under everything else so the bodies stay legible
     * on them.
     *
     * Which of the two gauges carries its figures is decided by the clock, not by taste:
     * whichever body reaches its next horizon first is the one anybody is waiting for,
     * so it is drawn whole. The other is faded, and if the two lines run close enough
     * for their figures to tangle it keeps only its stroke.
     */
    private void _drawTracks(Canvas canvas, float centreX, float centreY, float radius)
    {
        boolean sunLine = _hasLine(_sunRiseAzimuth, _sunSetAzimuth, _sunSpan);
        boolean moonLine = _hasLine(_moonRiseAzimuth, _moonSetAzimuth, _moonSpan);

        // Fading one of the two is how they are told apart. With only one line on the map
        // there is nothing to tell it from, so it is drawn whole whatever the clock says.
        boolean sunLeads = (sunLine != moonLine) ? sunLine
                : _soonest(_sunSpan, _moonSpan);

        boolean crowded = _linesCrowd(centreX, centreY, radius);

        // Order and emphasis answer two different questions. Which road lies under the
        // other follows the sky, exactly as the bodies do; which one keeps its figures
        // and its full colour follows the clock.
        if (_sunVisible) {
            _drawMoonTrack(canvas, centreX, centreY, radius, !sunLeads, crowded);
            _drawSunTrack(canvas, centreX, centreY, radius, sunLeads, crowded);
        } else {
            _drawSunTrack(canvas, centreX, centreY, radius, sunLeads, crowded);
            _drawMoonTrack(canvas, centreX, centreY, radius, !sunLeads, crowded);
        }
    }

    /**
     * @param leading whether this is the body anybody is waiting for, which is the one
     *                drawn whole and with its figures
     * @param crowded whether the two lines run close enough for their figures to tangle
     */
    private void _drawSunTrack(Canvas canvas, float centreX, float centreY, float radius,
                               boolean leading, boolean crowded)
    {
        _drawOneBody(canvas, centreX, centreY, radius, _sunVisible, _sunAbove, _sunBelow,
                _sunRiseAzimuth, _sunSetAzimuth, COLOUR_SUN_TRACK, COLOUR_SUN_LINE,
                true, _sunSpan, leading || !crowded, leading ? 255 : SECOND_ALPHA);
    }

    private void _drawMoonTrack(Canvas canvas, float centreX, float centreY, float radius,
                                boolean leading, boolean crowded)
    {
        _drawOneBody(canvas, centreX, centreY, radius, _moonVisible, _moonAbove, _moonBelow,
                _moonRiseAzimuth, _moonSetAzimuth, COLOUR_MOON_TRACK, COLOUR_MOON_LINE,
                true, _moonSpan, leading || !crowded, leading ? 255 : SECOND_ALPHA);
    }

    /* Whether this body has a horizon line to draw at all: one real crossing is enough. */
    private boolean _hasLine(double riseAzimuth, double setAzimuth, long[] stretch)
    {
        return (stretch != null) && ((riseAzimuth >= 0.0) || (setAzimuth >= 0.0));
    }

    /* Which stretch ends first, counted from the instant on show. */
    private boolean _soonest(long[] first, long[] second)
    {
        if (second == null) {
            return true;
        }

        if (first == null) {
            return false;
        }

        return (first[2] - _spanNow) <= (second[2] - _spanNow);
    }

    /*
     * Whether the two horizon lines pass close enough for their figures to run into one
     * another. Measured between their middles, against the room a figure needs.
     */
    private boolean _linesCrowd(float centreX, float centreY, float radius)
    {
        if ((_sunSpan == null) || (_moonSpan == null)
                || (_sunRiseAzimuth < 0.0) || (_moonRiseAzimuth < 0.0)) {
            return false;
        }

        float sunX = (_xFor(centreX, _sunRiseAzimuth, radius)
                + _xFor(centreX, _sunSetAzimuth, radius)) / 2.0f;
        float sunY = (_yFor(centreY, _sunRiseAzimuth, radius)
                + _yFor(centreY, _sunSetAzimuth, radius)) / 2.0f;

        float moonX = (_xFor(centreX, _moonRiseAzimuth, radius)
                + _xFor(centreX, _moonSetAzimuth, radius)) / 2.0f;
        float moonY = (_yFor(centreY, _moonRiseAzimuth, radius)
                + _yFor(centreY, _moonSetAzimuth, radius)) / 2.0f;

        return Math.hypot(sunX - moonX, sunY - moonY) < (_gaugePaint.getTextSize() * 3.2f);
    }

    private void _drawOneBody(Canvas canvas, float centreX, float centreY, float radius,
                              boolean visible, float[] above, float[] below,
                              double riseAzimuth, double setAzimuth,
                              int trackColour, int lineColour, boolean gauge,
                              long[] stretch, boolean figures, int alpha)
    {
        float[] road = visible ? above : below;

        boolean riseOnRim = riseAzimuth >= 0.0;
        boolean setOnRim = setAzimuth >= 0.0;

        // At least one real crossing, or there is no line to draw: a road bounded by two
        // meridian passages belongs to a body that neither rises nor sets, and nothing is
        // being counted down to.
        if (riseOnRim || setOnRim) {

            // A crossing sits on the rim, which is the horizon. A meridian bound does not:
            // it is taken from the end of the road itself, where the body stands at its
            // passage. The points run in time, so the road above the horizon starts at its
            // rise and the one below it at its set.
            float riseX = riseOnRim ? _xFor(centreX, riseAzimuth, radius)
                    : _roadEndX(road, !visible, centreX, radius);
            float riseY = riseOnRim ? _yFor(centreY, riseAzimuth, radius)
                    : _roadEndY(road, !visible, centreY, radius);

            float setX = setOnRim ? _xFor(centreX, setAzimuth, radius)
                    : _roadEndX(road, visible, centreX, radius);
            float setY = setOnRim ? _yFor(centreY, setAzimuth, radius)
                    : _roadEndY(road, visible, centreY, radius);

            // A line shorter than its own figures would carry nothing legible.
            boolean room = !Float.isNaN(riseX) && !Float.isNaN(setX)
                    && (Math.hypot(riseX - setX, riseY - setY) > (_gaugePaint.getTextSize() * 2.5f));

            if (room) {
                if (gauge && (stretch != null)) {
                    _drawGauge(canvas, setX, setY, riseX, riseY, lineColour, stretch, visible,
                            figures, alpha);
                } else {
                    _dayLinePaint.setColor(lineColour);

                    canvas.drawLine(riseX, riseY, setX, setY, _dayLinePaint);
                }
            }
        }

        if (visible) {

            _trackPaint.setColor(trackColour);

            _buildRoad(_track, above, centreX, centreY, radius);

            canvas.drawPath(_track, _trackPaint);
        } else {

            _hiddenPaint.setColor(trackColour);

            _buildRoad(_hidden, below, centreX, centreY, radius);

            canvas.drawPath(_hidden, _hiddenPaint);
        }
    }

    /*
     * Where one end of a road lands on the map. Its points run in time, so the first is
     * the end the body came from and the last the one it is heading for. Used for a bound
     * that is a meridian passage and therefore not on the rim.
     */
    private float _roadEndX(float[] road, boolean last, float centreX, float radius)
    {
        if ((road == null) || (road.length < 2)) {
            return Float.NaN;
        }

        int at = last ? (road.length - 2) : 0;

        return _xFor(centreX, road[at], _radiusFor(road[at + 1], radius));
    }

    private float _roadEndY(float[] road, boolean last, float centreY, float radius)
    {
        if ((road == null) || (road.length < 2)) {
            return Float.NaN;
        }

        int at = last ? (road.length - 2) : 0;

        return _yFor(centreY, road[at], _radiusFor(road[at + 1], radius));
    }

    /*
     * The horizon line of this passage, graduated.
     *
     * One end is the set and the other the rise, so the line already carries the two
     * events; what is added is the meridian in between and the whole hours left before
     * each of them. A mark showing 1 has an hour to run before the end of its own half,
     * and the remainder is left as a wider gap at the far side rather than smeared over
     * every interval, which would make every figure a lie.
     *
     * Everything stands on ONE side of the line and nothing crosses it: the ticks and
     * their figures hang below, the cursor sits above. A line cut by its own graduations
     * reads as a row of crosses rather than as a scale.
     */
    private void _drawGauge(Canvas canvas, float setX, float setY, float riseX, float riseY,
                            int colour, long[] stretch, boolean visible, boolean figures,
                            int alpha)
    {
        _gaugeLinePaint.setColor(colour);
        _gaugeLinePaint.setAlpha(alpha);

        canvas.drawLine(setX, setY, riseX, riseY, _gaugeLinePaint);

        if (!figures) {
            return;
        }

        // What a line can carry shrinks with it, and it loses it in order: the figures
        // first, when the graduations would stand closer than a figure is wide, then the
        // graduations, then the marks. Below that it stays a bare line, which still says
        // where the body rose and where it will set.
        float length = (float) Math.hypot(riseX - setX, riseY - setY);
        float text = _gaugePaint.getTextSize();

        long hours = Math.max(1L, (stretch[2] - stretch[0]) / 3600L);

        boolean marks = length > (text * 2.5f);
        boolean ticks = length > (text * 5.0f);
        boolean numbers = ticks && ((length / hours) > (text * 1.3f));

        if (!marks) {
            return;
        }

        // Nothing to mark where the highest point is an end of the line.
        if ((stretch[1] > stretch[0]) && (stretch[1] < stretch[2])) {
            _gaugeMark(canvas, setX, setY, riseX, riseY, stretch[1], stretch, visible, colour,
                    alpha, true, null);
        }

        if (ticks) {
            _countDown(canvas, setX, setY, riseX, riseY, stretch, visible, stretch[0],
                    stretch[1], colour, alpha, numbers);
            _countDown(canvas, setX, setY, riseX, riseY, stretch, visible, stretch[1],
                    stretch[2], colour, alpha, numbers);
        }

        if ((_spanNow > stretch[0]) && (_spanNow < stretch[2])) {
            _gaugeCursor(canvas, setX, setY, riseX, riseY, stretch, visible, alpha);
        }
    }

    /*
     * Where a moment falls along the line, ported unchanged from the gauge this replaces.
     *
     * Nought is the SET end and one the RISE end in both cases, because the Sun sets in
     * the west and rises in the east whatever the hour. What differs is the way time
     * runs along it: by day from the rise towards the set, so right to left; by night
     * the other way, from where it went down round to where it will come up.
     */
    private float _along(long when, long[] stretch, boolean visible)
    {
        float fraction = (float) (when - stretch[0]) / (stretch[2] - stretch[0]);

        // Nought is the SET end of the chord. By day the span opens at the rise, which is
        // the far end, so the fraction has to be turned over; by night it opens at the
        // set, which is this end, so it does not.
        return visible ? (1.0f - fraction) : fraction;
    }

    private void _countDown(Canvas canvas, float setX, float setY, float riseX, float riseY,
                            long[] stretch, boolean visible, long from, long to,
                            int colour, int alpha, boolean numbers)
    {
        final long hour = 3600L;

        for (int hours = 1; ; hours++) {

            long when = to - (hours * hour);

            if ((when - from) < CROWDING_SECONDS) {
                return;
            }

            _gaugeMark(canvas, setX, setY, riseX, riseY, when, stretch, visible, colour,
                    alpha, false, numbers ? String.valueOf(hours) : null);
        }
    }

    /* A tick hanging below the line, and its figure under it. */
    private void _gaugeMark(Canvas canvas, float setX, float setY, float riseX, float riseY,
                            long when, long[] stretch, boolean visible, int colour, int alpha,
                            boolean tall, String label)
    {
        float along = _along(when, stretch, visible);

        float x = setX + ((riseX - setX) * along);
        float y = setY + ((riseY - setY) * along);

        float[] down = _belowLine(setX, setY, riseX, riseY);

        if (down == null) {
            return;
        }

        float reach = _gaugePaint.getTextSize() * (tall ? 0.7f : 0.42f);

        _gaugeTickPaint.setColor(colour);
        _gaugeTickPaint.setAlpha(alpha);

        canvas.drawLine(x, y, x + (down[0] * reach), y + (down[1] * reach), _gaugeTickPaint);

        if (label == null) {
            return;
        }

        _gaugePaint.setColor(colour);
        _gaugePaint.setAlpha(alpha);

        canvas.drawText(label,
                x + (down[0] * _gaugePaint.getTextSize() * 1.5f),
                y + (down[1] * _gaugePaint.getTextSize() * 1.5f)
                        + (_gaugePaint.getTextSize() * 0.35f), _gaugePaint);
    }

    /* The triangle, above the line and leaning with it, pointing down at the moment. */
    private void _gaugeCursor(Canvas canvas, float setX, float setY, float riseX, float riseY,
                              long[] stretch, boolean visible, int alpha)
    {
        float along = _along(_spanNow, stretch, visible);

        float x = setX + ((riseX - setX) * along);
        float y = setY + ((riseY - setY) * along);

        float[] down = _belowLine(setX, setY, riseX, riseY);

        if (down == null) {
            return;
        }

        float length = (float) Math.hypot(riseX - setX, riseY - setY);

        float ax = (riseX - setX) / length;
        float ay = (riseY - setY) / length;

        float size = _gaugePaint.getTextSize() * 0.45f;

        float baseX = x - (down[0] * size * 1.9f);
        float baseY = y - (down[1] * size * 1.9f);

        _cursorTriangle.reset();
        _cursorTriangle.moveTo(x - (down[0] * size * 0.3f), y - (down[1] * size * 0.3f));
        _cursorTriangle.lineTo(baseX - (ax * size), baseY - (ay * size));
        _cursorTriangle.lineTo(baseX + (ax * size), baseY + (ay * size));
        _cursorTriangle.close();

        _cursorFill.setColor(COLOUR_NOW_MARK);
        _cursorFill.setAlpha(alpha);

        canvas.drawPath(_cursorTriangle, _cursorFill);
    }

    /*
     * The unit vector at right angles to the line, pointing AWAY from the middle of the
     * disc, which on a chord low in the south is downwards and on one high in the north
     * is upwards. The graduations hang on that side, where there is nothing else.
     */
    private float[] _belowLine(float setX, float setY, float riseX, float riseY)
    {
        float dx = riseX - setX;
        float dy = riseY - setY;

        float length = (float) Math.hypot(dx, dy);

        if (length <= 0.0f) {
            return null;
        }

        float nx = -dy / length;
        float ny = dx / length;

        // Away from the centre of the view, whichever of the two normals that is.
        float midX = (setX + riseX) / 2.0f;
        float midY = (setY + riseY) / 2.0f;

        float toMiddleX = (getWidth() / 2.0f) - midX;
        float toMiddleY = (getHeight() / 2.0f) - midY;

        if (((nx * toMiddleX) + (ny * toMiddleY)) > 0.0f) {
            nx = -nx;
            ny = -ny;
        }

        return new float[] { nx, ny };
    }

    private void _buildRoad(Path road, float[] points, float centreX, float centreY,
                            float radius)
    {
        road.reset();

        if ((points == null) || (points.length < 4)) {
            return;
        }

        for (int n = 0; n < points.length; n += 2) {

            float r = _radiusFor(points[n + 1], radius);

            float x = _xFor(centreX, points[n], r);
            float y = _yFor(centreY, points[n], r);

            if (n == 0) {
                road.moveTo(x, y);
            } else {
                road.lineTo(x, y);
            }
        }
    }

    private void _drawSun(Canvas canvas, float centreX, float centreY, float radius)
    {
        float r = _radiusFor(_sunAltitude, radius);

        float x = _xFor(centreX, _sunAzimuth, r);
        float y = _yFor(centreY, _sunAzimuth, r);

        boolean up = _sunVisible;

        float size = radius * BODY_SIZE;

        _drawHalo(canvas, x, y, size, radius, _sunPull, COLOUR_SUN_HALO);

        _bodyPaint.setColor(up ? COLOUR_SUN : COLOUR_SUN_DOWN);
        _bodyPaint.setStyle(Paint.Style.FILL);

        canvas.drawCircle(x, y, size, _bodyPaint);

        // Rays only while it is up: a Sun below the horizon is not shining on anyone.
        if (!up) {
            return;
        }

        _edgePaint.setColor(COLOUR_SUN);

        for (int ray = 0; ray < 8; ray++) {

            double angle = (Math.PI / 4.0) * ray;

            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);

            canvas.drawLine(x + cos * size * 1.45f, y + sin * size * 1.45f,
                    x + cos * size * 1.95f, y + sin * size * 1.95f, _edgePaint);
        }
    }

    private void _drawMoon(Canvas canvas, float centreX, float centreY, float radius)
    {
        float r = _radiusFor(_moonAltitude, radius);

        float x = _xFor(centreX, _moonAzimuth, r);
        float y = _yFor(centreY, _moonAzimuth, r);

        float size = radius * 0.095f;

        _drawHalo(canvas, x, y, size, radius, _moonPull, COLOUR_MOON_HALO);

        int saved = canvas.save();

        canvas.rotate(MoonDisc.rotationDegrees(_moonTilt, _moonWaxing), x, y);

        boolean up = _moonVisible;

        _bodyPaint.setColor(up ? COLOUR_MOON_DARK : COLOUR_MOON_DARK_DOWN);
        _bodyPaint.setStyle(Paint.Style.FILL);

        canvas.drawCircle(x, y, size, _bodyPaint);

        // The same shape and the same turn as the big disc, from the same place.
        MoonDisc.litPath(_lit, _bounds, x, y, size, _moonFraction, _moonWaxing);

        // Muted below the horizon, as the Sun is: an unlit sky should not be carrying
        // a bright Moon.
        _bodyPaint.setColor(up ? COLOUR_MOON_LIT : COLOUR_MOON_LIT_DOWN);

        canvas.drawPath(_lit, _bodyPaint);

        canvas.restoreToCount(saved);

        _edgePaint.setColor(up ? COLOUR_MOON_EDGE : COLOUR_MOON_EDGE_DOWN);

        canvas.drawCircle(x, y, size, _edgePaint);
    }

    /*
     * A halo whose width is what this body is pulling, measured against the strongest
     * pull of the month. It swells when the body is overhead OR underfoot and shrinks
     * to nothing near the horizon, which is the shape of the law.
     */
    private void _drawHalo(Canvas canvas, float x, float y, float size, float radius,
                           double pull, int colour)
    {
        if (_peakPull <= 0.0) {
            return;
        }

        double share = Math.min(1.0, Math.abs(pull) / _peakPull);

        float extra = (float) (radius * HALO_REACH * share);

        if (extra < 1.0f) {
            return;
        }

        _haloPaint.setColor(colour);

        canvas.drawCircle(x, y, size + extra, _haloPaint);
    }

    private void _setUpPaints()
    {
        float density = getResources().getDisplayMetrics().density;

        _skyPaint.setColor(COLOUR_SKY);
        _skyPaint.setStyle(Paint.Style.FILL);

        _rimPaint.setColor(COLOUR_RIM);
        _rimPaint.setStyle(Paint.Style.STROKE);
        _rimPaint.setStrokeWidth(1.6f * density);

        _gridPaint.setColor(COLOUR_GRID);
        _gridPaint.setStyle(Paint.Style.STROKE);
        _gridPaint.setStrokeWidth(1.0f * density);

        _textPaint.setColor(COLOUR_CARDINAL);
        _textPaint.setTextSize(12.0f * density);
        _textPaint.setTextAlign(Paint.Align.CENTER);
        _textPaint.setFakeBoldText(true);

        _trackPaint.setStyle(Paint.Style.STROKE);
        _trackPaint.setStrokeWidth(2.2f * density);
        _trackPaint.setStrokeCap(Paint.Cap.ROUND);

        // The hidden half: same colour, thinner and dotted, so it reads as the same road
        // seen through the Earth.
        _hiddenPaint.setStyle(Paint.Style.STROKE);
        _hiddenPaint.setStrokeWidth(1.5f * density);
        _hiddenPaint.setStrokeCap(Paint.Cap.ROUND);
        _hiddenPaint.setPathEffect(new android.graphics.DashPathEffect(
                new float[] { 2.0f * density, 4.0f * density }, 0.0f));

        _dayLinePaint.setStyle(Paint.Style.STROKE);
        _dayLinePaint.setStrokeWidth(1.4f * density);
        _dayLinePaint.setPathEffect(new android.graphics.DashPathEffect(
                new float[] { 6.0f * density, 4.0f * density }, 0.0f));

        _haloPaint.setStyle(Paint.Style.FILL);

        _heightPaint.setColor(COLOUR_HEIGHT);
        _heightPaint.setTextSize(10.0f * density);

        _heightHalo.setColor(COLOUR_SKY);
        _heightHalo.setTextSize(10.0f * density);
        _heightHalo.setStyle(Paint.Style.STROKE);
        _heightHalo.setStrokeWidth(3.0f * density);

        _gaugePaint.setTextSize(9.5f * density);
        _gaugePaint.setTextAlign(Paint.Align.CENTER);

        _gaugeTickPaint.setStyle(Paint.Style.STROKE);
        _gaugeTickPaint.setStrokeWidth(1.2f * density);
        _gaugeTickPaint.setStrokeCap(Paint.Cap.ROUND);

        // Thin, and solid: it is a scale, not the dashed chord it replaces.
        _gaugeLinePaint.setStyle(Paint.Style.STROKE);
        _gaugeLinePaint.setStrokeWidth(1.6f * density);
        _gaugeLinePaint.setStrokeCap(Paint.Cap.ROUND);

        _cursorFill.setStyle(Paint.Style.FILL);

        _edgePaint.setStyle(Paint.Style.STROKE);
        _edgePaint.setStrokeWidth(1.6f * density);
        _edgePaint.setStrokeCap(Paint.Cap.ROUND);

        // Translucent on purpose: a bearing aid laid over the sky, never a reading of its
        // own. The head and the pivot are the same colour, filled.
        _needlePaint.setColor(COLOUR_NEEDLE);
        _needlePaint.setStyle(Paint.Style.STROKE);
        _needlePaint.setStrokeWidth(2.4f * density);
        _needlePaint.setStrokeCap(Paint.Cap.ROUND);

        _needleFill.setColor(COLOUR_NEEDLE);
        _needleFill.setStyle(Paint.Style.FILL);
    }
}
