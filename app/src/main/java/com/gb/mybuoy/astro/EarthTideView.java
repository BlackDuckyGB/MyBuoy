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
 * The earth tide over a month, read three days at a time.
 *
 * The curve under the finger is the change in gravity. The ground rises and
 * falls with it, in the opposite direction and in strict proportion, so the
 * right-hand scale reads centimetres off the very same line rather than
 * drawing a second one on top of the first.
 *
 * Three days in view and a strip underneath carrying the whole month. Three
 * days is what it takes to see the twice-daily rhythm and the difference
 * between the two humps of a day; a month is what it takes to see the envelope
 * swell at new and full moon and collapse at the quarters, which is the same
 * spring and neap that governs the sea. Neither span shows both, so both are
 * drawn.
 *
 * The reference instant is the middle of the view, not the finger: dragging
 * slides the month past a fixed mark, which is steadier to read and leaves the
 * value legible while the hand is still on the glass. Everything that follows
 * the instant elsewhere on the page follows that mark.
 *
 * No arithmetic happens here. The samples arrive computed.
 */
package com.gb.mybuoy.astro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.OverScroller;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** Draws the earth tide and lets the user scrub through the month. */
public final class EarthTideView extends View
{
    /*
     * Colours, sizes and spacings are the tide curve's, deliberately and to the value.
     * The two charts sit two screens apart in the same application and are read the
     * same way, so anything that differed would read as meaning something.
     */
    private static final int COLOUR_CURVE = 0xFF2196F3;
    private static final int COLOUR_FILL = 0x332196F3;
    private static final int COLOUR_NOW = 0xFFD32F2F;
    private static final int COLOUR_GRID = 0xFFBDBDBD;
    private static final int COLOUR_TEXT = 0xFF424242;
    private static final int COLOUR_CURSOR = 0xFF212121;

    /** One colour per quantity, carried by the scale, the reading and the legend alike. */
    private static final int COLOUR_GRAVITY = 0xFF1565C0;
    private static final int COLOUR_GROUND = 0xFF00897B;
    private static final int COLOUR_OVERVIEW = 0xFF64B5F6;
    private static final int COLOUR_WINDOW = 0x552196F3;

    /** A day on screen, as the tide chart shows half of one. */
    private static final long VISIBLE_SECONDS = 24L * 3600L;

    /** Height of the strip carrying the whole month, as a share of the view. */
    private static final float OVERVIEW_SHARE = 0.20f;

    /** Told when the mark in the middle has moved. */
    public interface OnInstantChanged
    {
        void instantChanged(long unixSeconds);
    }

    private final Paint _curvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _dayTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _nowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _nowTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _cursorTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _overviewPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _windowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path _curve = new Path();
    private final Path _overview = new Path();
    private final Path _arrow = new Path();

    private OverScroller _scroller = null;
    private VelocityTracker _velocity = null;

    private TidePotential.Series _series = null;
    private OnInstantChanged _listener = null;

    private TimeZone _zone = TimeZone.getDefault();

    private long _centreUnixSeconds = 0L;
    private long _nowUnixSeconds = 0L;

    private float _peakMicroGal = 1.0f;

    private float _lastTouchX = 0.0f;
    private float _downX = 0.0f;
    private float _downY = 0.0f;
    private boolean _dragging = false;
    private boolean _decided = false;
    private boolean _followsNow = true;
    private int _touchSlop = 0;

    public EarthTideView(Context context)
    {
        super(context);
        _setUpPaints();
    }

    public EarthTideView(Context context, AttributeSet attributes)
    {
        super(context, attributes);
        _setUpPaints();
    }

    /** Hands over the samples and places the mark on the present. */
    public void setSeries(TidePotential.Series series, long nowUnixSeconds)
    {
        _series = series;
        _nowUnixSeconds = nowUnixSeconds;
        _centreUnixSeconds = _clamp(nowUnixSeconds);
        _followsNow = true;

        // A little air above and below so the curve does not touch the frame, the same
        // eight percent the tide chart leaves itself.
        _peakMicroGal = series.peakMicroGal() * 1.08f;

        invalidate();
    }

    public void setOnInstantChanged(OnInstantChanged listener)
    {
        _listener = listener;
    }

    public void setTimeZone(TimeZone zone)
    {
        _zone = zone;
        invalidate();
    }

    /** The instant under the mark, which is what the rest of the page follows. */
    public long centreUnixSeconds()
    {
        return _centreUnixSeconds;
    }

    /**
     * Moves the line marking the present, and nothing else.
     *
     * The present goes on passing whatever the reader is looking at, so a page that
     * keeps itself current advances this line even when the mark is somewhere else
     * entirely: a red line standing on an hour that is gone is simply wrong.
     */
    public void setNow(long nowUnixSeconds)
    {
        _nowUnixSeconds = nowUnixSeconds;

        invalidate();
    }

    /** Brings the mark back to the present, and keeps it riding it. */
    public void showNow(long nowUnixSeconds)
    {
        _followsNow = true;

        setNow(nowUnixSeconds);

        _moveTo(_clamp(nowUnixSeconds));
    }

    /**
     * Whether the mark is still riding the present, nobody having taken it elsewhere.
     *
     * A page that keeps itself current asks this before advancing: once the reader has
     * dragged the curve, the instant on show is his choice and nothing should move it.
     */
    public boolean followsNow()
    {
        return _followsNow;
    }

    /**
     * Puts the mark on a given moment, leaving the present where it is. The tide page
     * hands its own reading time over this way, so the two charts agree.
     */
    public void showAt(long unixSeconds)
    {
        if (_series == null)
        {
            return;
        }

        _followsNow = false;

        _moveTo(_clamp(unixSeconds));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        if (_series == null)
        {
            return false;
        }

        if (_velocity == null)
        {
            _velocity = VelocityTracker.obtain();
        }

        _velocity.addMovement(event);

        switch (event.getActionMasked())
        {
            case MotionEvent.ACTION_DOWN:
                _scroller.forceFinished(true);
                _downX = event.getX();
                _downY = event.getY();
                _lastTouchX = _downX;
                _dragging = false;
                _decided = false;
                return true;

            case MotionEvent.ACTION_MOVE:
            {
                // The page scrolls vertically and this view scrolls horizontally. Until
                // the finger has said which it means, neither takes the gesture; a
                // vertical one is handed straight back so the page underneath keeps
                // working. Grabbing everything on the first touch, as this did, left the
                // page unable to scroll at all wherever the chart lay under the thumb.
                if (!_decided)
                {
                    float movedX = Math.abs(event.getX() - _downX);
                    float movedY = Math.abs(event.getY() - _downY);

                    if ((movedX < _touchSlop) && (movedY < _touchSlop))
                    {
                        return true;
                    }

                    _decided = true;
                    _dragging = movedX > movedY;

                    if (!_dragging)
                    {
                        return false;
                    }

                    getParent().requestDisallowInterceptTouchEvent(true);
                    _lastTouchX = event.getX();

                    // From here the instant on show is the reader's own.
                    _followsNow = false;
                }

                if (!_dragging)
                {
                    return false;
                }

                float moved = event.getX() - _lastTouchX;
                _lastTouchX = event.getX();

                _moveTo(_clamp(_centreUnixSeconds - _secondsFor(moved)));

                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
            {
                if (_dragging)
                {
                    _velocity.computeCurrentVelocity(1000);

                    // The scroller counts in seconds of the series rather than pixels, so
                    // the fling behaves the same whatever the width of the view.
                    _scroller.fling((int) _centreUnixSeconds, 0,
                            -(int) _secondsFor(_velocity.getXVelocity()), 0,
                            (int) (_series.fromUnixSeconds() + (VISIBLE_SECONDS / 2L)),
                            (int) (_series.toUnixSeconds() - (VISIBLE_SECONDS / 2L)),
                            0, 0);

                    postInvalidateOnAnimation();

                    getParent().requestDisallowInterceptTouchEvent(false);
                }

                _dragging = false;
                _decided = false;

                _velocity.recycle();
                _velocity = null;

                return true;
            }

            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public void computeScroll()
    {
        if ((_scroller != null) && _scroller.computeScrollOffset())
        {
            _moveTo(_clamp(_scroller.getCurrX()));

            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);

        if ((_series == null) || (getWidth() == 0) || (getHeight() == 0))
        {
            return;
        }

        float left = 8.0f;
        float right = getWidth() - 8.0f;
        float top = _paddingTop();
        float bottom = (getHeight() * (1.0f - OVERVIEW_SHARE)) - _paddingBottom();

        if ((right <= left) || (bottom <= top))
        {
            return;
        }

        long from = _centreUnixSeconds - (VISIBLE_SECONDS / 2L);
        long to = _centreUnixSeconds + (VISIBLE_SECONDS / 2L);

        float scaleX = (right - left) / (float) VISIBLE_SECONDS;
        float middle = (top + bottom) / 2.0f;
        float scaleY = ((bottom - top) / 2.0f) / _peakMicroGal;

        _drawGrid(canvas, from, to, left, top, bottom, scaleX);
        _drawScales(canvas, left, top, right, bottom);
        _drawCurve(canvas, from, left, top, right, bottom, middle, scaleX, scaleY);
        _drawNowLine(canvas, from, left, top, right, bottom, scaleX);
        _drawCursor(canvas, left, top, right, bottom);
        _drawOverview(canvas, left, right);
    }

    /*
     * Three-hourly lines, and the date instead of the hour where a day turns over. The
     * tide chart does exactly this, and on a window of a day it comes to eight lines.
     */
    private void _drawGrid(Canvas canvas, long from, long to,
                           float left, float top, float bottom, float scaleX)
    {
        Calendar calendar = Calendar.getInstance(_zone);
        calendar.setTimeInMillis(from * 1000L);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        while ((calendar.getTimeInMillis() / 1000L) <= to)
        {
            long moment = calendar.getTimeInMillis() / 1000L;
            int hour = calendar.get(Calendar.HOUR_OF_DAY);

            if ((moment >= from) && ((hour % 3) == 0))
            {
                float x = left + ((moment - from) * scaleX);

                canvas.drawLine(x, top, x, bottom, _gridPaint);

                boolean midnight = hour == 0;

                String label = midnight
                        ? String.format(Locale.UK, "%td/%tm", calendar, calendar)
                        : String.format(Locale.UK, "%02d:00", hour);

                canvas.drawText(label, x, bottom + _textPaint.getTextSize() + 4.0f,
                        midnight ? _dayTextPaint : _textPaint);
            }

            calendar.add(Calendar.HOUR_OF_DAY, 1);
        }
    }

    /*
     * One line, two readings. Microgals down the left, centimetres of ground down the
     * right, and they run in opposite directions because the quantities do.
     */
    private void _drawScales(Canvas canvas, float left, float top, float right, float bottom)
    {
        // The unit travels with each figure rather than sitting once at mid height: a
        // lone "uGal" against the zero line reads as a label for the line, not for the
        // scale.
        _textPaint.setTextAlign(Paint.Align.LEFT);
        _textPaint.setColor(COLOUR_GRAVITY);

        canvas.drawText(String.format(Locale.UK, "%+.0f \u00b5Gal", -_peakMicroGal),
                left + 3.0f, top + _textPaint.getTextSize(), _textPaint);

        canvas.drawText(String.format(Locale.UK, "%+.0f \u00b5Gal", _peakMicroGal),
                left + 3.0f, bottom - 3.0f, _textPaint);

        double centimetres = _peakMicroGal * Math.abs(_series.centimetresPerMicroGal());

        _textPaint.setTextAlign(Paint.Align.RIGHT);
        _textPaint.setColor(COLOUR_GROUND);

        canvas.drawText(String.format(Locale.UK, "%+.0f cm", centimetres),
                right - 3.0f, top + _textPaint.getTextSize(), _textPaint);

        canvas.drawText(String.format(Locale.UK, "%+.0f cm", -centimetres),
                right - 3.0f, bottom - 3.0f, _textPaint);

        _textPaint.setColor(COLOUR_TEXT);
        _textPaint.setTextAlign(Paint.Align.CENTER);
    }

    private void _drawCurve(Canvas canvas, long from, float left, float top, float right,
                            float bottom, float middle, float scaleX, float scaleY)
    {
        // Zero is where the sky pulls neither up nor down, and it earns its own line.
        canvas.drawLine(left, middle, right, middle, _gridPaint);

        _curve.reset();

        int steps = (int) (right - left);

        for (int pixel = 0; pixel <= steps; pixel++)
        {
            long when = from + (long) (pixel / scaleX);

            // Plus, not minus: the curve is drawn in the sense of the GROUND, which
            // rises as gravity falls. The ground is the thing anyone can picture, so it
            // is the one that goes up when the line goes up; the gravity scale on the
            // left then counts downwards, which is the same statement read the other way.
            float y = middle + (_series.gravityAt(when) * scaleY);

            if (pixel == 0)
            {
                _curve.moveTo(left, y);
            }
            else
            {
                _curve.lineTo(left + pixel, y);
            }
        }

        canvas.drawPath(_curve, _curvePaint);

        _curve.lineTo(right, middle);
        _curve.lineTo(left, middle);
        _curve.close();

        // Even-odd, not winding. Closing the curve onto the zero line makes the humps
        // above and the troughs below run in opposite senses, and under the winding rule
        // they cancel: only half the area would be filled.
        _curve.setFillType(Path.FillType.EVEN_ODD);

        canvas.drawPath(_curve, _fillPaint);
    }

    private void _drawNowLine(Canvas canvas, long from, float left, float top, float right,
                              float bottom, float scaleX)
    {
        float x = left + ((_nowUnixSeconds - from) * scaleX);
        float baseline = _nowTextPaint.getTextSize();   // first row, above everything

        if ((x < left) || (x > right))
        {
            _drawNowArrow(canvas, x < left, left, right, baseline);
            return;
        }

        _nowPaint.setStyle(Paint.Style.STROKE);

        canvas.drawLine(x, top, x, bottom, _nowPaint);

        String label = _clock(_nowUnixSeconds);

        float half = _nowTextPaint.measureText(label) / 2.0f;

        canvas.drawText(label, Math.min(Math.max(x, left + half), right - half), baseline,
                _nowTextPaint);
    }

    /** Points back towards the present moment once it has been scrolled off the view. */
    private void _drawNowArrow(Canvas canvas, boolean toTheLeft, float left, float right,
                               float baseline)
    {
        float size = _nowTextPaint.getTextSize() * 0.55f;
        float middle = baseline - (size * 0.6f);
        float edge = toTheLeft ? (left + size) : (right - size);
        float tip = toTheLeft ? (edge - size) : (edge + size);

        _arrow.reset();
        _arrow.moveTo(tip, middle);
        _arrow.lineTo(edge, middle - size);
        _arrow.lineTo(edge, middle + size);
        _arrow.close();

        _nowPaint.setStyle(Paint.Style.FILL);

        canvas.drawPath(_arrow, _nowPaint);

        canvas.drawText("now", toTheLeft ? (edge + (size * 2.2f)) : (edge - (size * 2.2f)),
                baseline, _nowTextPaint);
    }

    /**
     * The fixed reading line. The curve slides under it, so what it reports is simply
     * what is in the middle of the view: the two readings above it, the moment below.
     */
    private void _drawCursor(Canvas canvas, float left, float top, float right, float bottom)
    {
        float x = (left + right) / 2.0f;

        canvas.drawLine(x, top, x, bottom, _cursorPaint);

        float gravity = _series.gravityAt(_centreUnixSeconds);

        // Second row above the frame, under the red clock: a reading in black and one in
        // red never share a line. The two quantities then sit either side of the cursor,
        // each in its own colour, so neither a separator nor a label is needed to tell
        // them apart.
        float baseline = _nowTextPaint.getTextSize() + _cursorTextPaint.getTextSize() + 4.0f;

        float gap = _cursorTextPaint.getTextSize() * 0.4f;

        String left_ = String.format(Locale.UK, "%+.0f µGal", gravity);
        String right_ = String.format(Locale.UK, "%+.1f cm",
                gravity * _series.centimetresPerMicroGal());

        _cursorTextPaint.setColor(COLOUR_GRAVITY);
        _cursorTextPaint.setTextAlign(Paint.Align.RIGHT);

        canvas.drawText(left_,
                Math.max(x - gap, left + _cursorTextPaint.measureText(left_)),
                baseline, _cursorTextPaint);

        _cursorTextPaint.setColor(COLOUR_GROUND);
        _cursorTextPaint.setTextAlign(Paint.Align.LEFT);

        canvas.drawText(right_,
                Math.min(x + gap, right - _cursorTextPaint.measureText(right_)),
                baseline, _cursorTextPaint);

        _cursorTextPaint.setColor(COLOUR_CURSOR);
        _cursorTextPaint.setTextAlign(Paint.Align.CENTER);

        String moment = _stamp(_centreUnixSeconds);

        // Second row below the frame, under the hours.
        canvas.drawText(moment, _keepInside(x, moment, left, right),
                bottom + _textPaint.getTextSize() + _cursorTextPaint.getTextSize() + 8.0f,
                _cursorTextPaint);
    }

    /*
     * The whole month in a strip, with the day on screen picked out. The envelope
     * swelling and collapsing across it is the spring and neap cycle, and it is the
     * only place on the page where a whole lunation can be seen at once.
     */
    private void _drawOverview(Canvas canvas, float left, float right)
    {
        float top = getHeight() * (1.0f - OVERVIEW_SHARE);
        float bottom = getHeight() - 1.0f;
        float middle = (top + bottom) / 2.0f;

        long first = _series.fromUnixSeconds();
        long last = _series.toUnixSeconds();

        float span = Math.max(1.0f, last - first);
        float scaleY = ((bottom - top) / 2.0f) / _peakMicroGal;

        _overview.reset();

        int steps = (int) (right - left);

        for (int pixel = 0; pixel <= steps; pixel++)
        {
            long when = first + (long) ((pixel / (right - left)) * span);

            float y = middle + (_series.gravityAt(when) * scaleY);

            if (pixel == 0)
            {
                _overview.moveTo(left, y);
            }
            else
            {
                _overview.lineTo(left + pixel, y);
            }
        }

        canvas.drawPath(_overview, _overviewPaint);

        float windowFrom = left
                + (((_centreUnixSeconds - (VISIBLE_SECONDS / 2L) - first) / span) * (right - left));

        float windowTo = windowFrom + ((VISIBLE_SECONDS / span) * (right - left));

        canvas.drawRect(Math.max(left, windowFrom), top, Math.min(right, windowTo), bottom,
                _windowPaint);
    }

    /** Keeps a label from running off the edge of the view. */
    private float _keepInside(float x, String label, float left, float right)
    {
        float half = _cursorTextPaint.measureText(label) / 2.0f;

        return Math.min(Math.max(x, left + half), right - half);
    }

    // Two rows above the frame: the red clock on top, then the readings under the cursor.
    private float _paddingTop()
    {
        return _nowTextPaint.getTextSize() + _cursorTextPaint.getTextSize() + 10.0f;
    }

    // Two rows below: the hours, then the moment the cursor is sitting on.
    private float _paddingBottom()
    {
        return _textPaint.getTextSize() + _cursorTextPaint.getTextSize() + 14.0f;
    }

    private String _clock(long unixSeconds)
    {
        Calendar when = Calendar.getInstance(_zone);
        when.setTimeInMillis(unixSeconds * 1000L);

        return String.format(Locale.UK, "%02d:%02d",
                when.get(Calendar.HOUR_OF_DAY), when.get(Calendar.MINUTE));
    }

    private String _stamp(long unixSeconds)
    {
        Calendar when = Calendar.getInstance(_zone);
        when.setTimeInMillis(unixSeconds * 1000L);

        return String.format(Locale.UK, "%td/%tm  %02d:%02d", when, when,
                when.get(Calendar.HOUR_OF_DAY), when.get(Calendar.MINUTE));
    }

    private long _secondsFor(float pixels)
    {
        return (long) ((pixels / Math.max(1.0f, getWidth())) * VISIBLE_SECONDS);
    }

    private long _clamp(long unixSeconds)
    {
        long first = _series.fromUnixSeconds() + (VISIBLE_SECONDS / 2L);
        long last = _series.toUnixSeconds() - (VISIBLE_SECONDS / 2L);

        return Math.min(Math.max(unixSeconds, first), last);
    }

    private void _moveTo(long unixSeconds)
    {
        if (unixSeconds == _centreUnixSeconds)
        {
            return;
        }

        _centreUnixSeconds = unixSeconds;

        invalidate();

        if (_listener != null)
        {
            _listener.instantChanged(unixSeconds);
        }
    }

    private void _setUpPaints()
    {
        float density = getResources().getDisplayMetrics().density;

        _scroller = new OverScroller(getContext());
        _touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();

        _curvePaint.setColor(COLOUR_CURVE);
        _curvePaint.setStyle(Paint.Style.STROKE);
        _curvePaint.setStrokeWidth(2.0f * density);

        _fillPaint.setColor(COLOUR_FILL);
        _fillPaint.setStyle(Paint.Style.FILL);

        _gridPaint.setColor(COLOUR_GRID);
        _gridPaint.setStyle(Paint.Style.STROKE);
        _gridPaint.setStrokeWidth(1.0f);

        _textPaint.setColor(COLOUR_TEXT);
        _textPaint.setTextSize(11.0f * density);
        _textPaint.setTextAlign(Paint.Align.CENTER);

        _dayTextPaint.setColor(COLOUR_TEXT);
        _dayTextPaint.setTextSize(11.0f * density);
        _dayTextPaint.setTextAlign(Paint.Align.CENTER);
        _dayTextPaint.setFakeBoldText(true);

        _nowPaint.setColor(COLOUR_NOW);
        _nowPaint.setStyle(Paint.Style.STROKE);
        _nowPaint.setStrokeWidth(2.0f * density);

        _nowTextPaint.setColor(COLOUR_NOW);
        _nowTextPaint.setTextSize(12.0f * density);
        _nowTextPaint.setTextAlign(Paint.Align.CENTER);
        _nowTextPaint.setFakeBoldText(true);

        _cursorPaint.setColor(COLOUR_CURSOR);
        _cursorPaint.setStyle(Paint.Style.STROKE);
        _cursorPaint.setStrokeWidth(1.6f * density);

        _cursorTextPaint.setColor(COLOUR_CURSOR);
        _cursorTextPaint.setTextSize(12.0f * density);
        _cursorTextPaint.setTextAlign(Paint.Align.CENTER);

        _overviewPaint.setColor(COLOUR_OVERVIEW);
        _overviewPaint.setStyle(Paint.Style.STROKE);
        _overviewPaint.setStrokeWidth(1.4f);

        _windowPaint.setColor(COLOUR_WINDOW);
        _windowPaint.setStyle(Paint.Style.FILL);
    }
}
