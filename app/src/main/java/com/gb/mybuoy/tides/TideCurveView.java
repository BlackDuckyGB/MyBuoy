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
 * The tide curve, with the fishing windows behind it and a line on the present moment.
 * Drag left or right to walk through the days.
 *
 * Nothing is computed while drawing. The whole fortnight is turned into one Path in world
 * units the moment the model arrives; dragging only moves a matrix, and draw() does no more
 * than hand that Path to the canvas.
 */
package com.gb.mybuoy.tides;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.OverScroller;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Draws the prepared tide curve and lets the finger scroll through the days. */
public final class TideCurveView extends View
{
    /** Width of the window on screen, in seconds: half a day reads well on a phone. */
    private static final long VISIBLE_SECONDS = 12L * 3600L;

    /** Height of the strip carrying the whole month, as a share of the view. */
    private static final float OVERVIEW_SHARE = 0.20f;

    private static final int COLOUR_OVERVIEW = 0xFF64B5F6;
    private static final int COLOUR_WINDOW = 0x552196F3;

    private static final int COLOUR_CURVE = 0xFF2196F3;
    private static final int COLOUR_FILL = 0x332196F3;
    private static final int COLOUR_FISHING = 0x3343A047;
    private static final int COLOUR_NOW = 0xFFD32F2F;
    private static final int COLOUR_GRID = 0xFFBDBDBD;
    private static final int COLOUR_TEXT = 0xFF424242;
    private static final int COLOUR_CURSOR = 0xFF212121;

    private final Paint _curvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _fishingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _nowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _overviewPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _windowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _nowTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _dayTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint _cursorTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path _arrow = new Path();

    private final Path _curve = new Path();
    private final Path _overview = new Path();
    private final Path _filled = new Path();
    private final Matrix _matrix = new Matrix();
    private final Path _drawn = new Path();
    private final RectF _band = new RectF();

    private TideModel _model = null;
    private long _centreUnixSeconds = 0L;
    private boolean _followsNow = true;
    private long _nowUnixSeconds = 0L;
    private float _lowest = 0.0f;
    private float _highest = 1.0f;

    private float _lastTouchX = 0.0f;
    private float _downX = 0.0f;
    private float _downY = 0.0f;
    private boolean _dragging = false;
    private boolean _decided = false;

    private OverScroller _scroller = null;
    private VelocityTracker _velocity = null;
    private int _touchSlop = 0;
    private int _maxFlingVelocity = 0;

    private TimeZone _zone = TimeZone.getDefault();

    public TideCurveView(Context context)
    {
        super(context);
        _setUpPaints();
    }

    public TideCurveView(Context context, AttributeSet attributes)
    {
        super(context, attributes);
        _setUpPaints();
    }

    /**
     * Hands the view its model. Builds the Path once, here, so that drawing and dragging
     * stay free of arithmetic.
     */
    public void setModel(TideModel model, long nowUnixSeconds)
    {
        _model = model;
        _nowUnixSeconds = nowUnixSeconds;
        _centreUnixSeconds = nowUnixSeconds;
        _followsNow = true;

        _buildPath();
        invalidate();
    }

    /** Moment the middle of the view is showing. */
    public long centreUnixSeconds()
    {
        return _centreUnixSeconds;
    }

    /** Brings the view back to the present moment, and keeps it riding it. */
    public void showNow(long nowUnixSeconds)
    {
        _nowUnixSeconds = nowUnixSeconds;
        _centreUnixSeconds = nowUnixSeconds;
        _followsNow = true;
        invalidate();
    }

    /**
     * Whether the view is still riding the present, nobody having dragged it away.
     *
     * A page that keeps itself current asks this before advancing: left alone the chart
     * follows the clock, and once the reader has taken it somewhere that moment is his.
     */
    public boolean followsNow()
    {
        return _followsNow;
    }

    /**
     * Puts the middle of the view on a given moment, leaving the "now" line where it is.
     *
     * This is what lets the sun and moon page hand its own reading time over: the two
     * charts then show the same instant, and the reader keeps his place when he moves
     * from one to the other.
     */
    public void showAt(long unixSeconds)
    {
        if (_model == null)
        {
            return;
        }

        long first = _model.fromUnixSeconds() + (VISIBLE_SECONDS / 2L);
        long last = _model.toUnixSeconds() - (VISIBLE_SECONDS / 2L);

        _centreUnixSeconds = Math.min(Math.max(unixSeconds, first), last);
        _followsNow = false;

        invalidate();
    }

    /** Keeps the "now" line current without moving what the user is looking at. */
    public void setNow(long nowUnixSeconds)
    {
        _nowUnixSeconds = nowUnixSeconds;
        invalidate();
    }

    public void setTimeZone(TimeZone zone)
    {
        _zone = zone;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        if (_model == null)
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
                // The page scrolls vertically and this view scrolls horizontally. Until the
                // finger has said which it means, neither takes the gesture; a vertical one
                // is handed straight back so the page underneath keeps working, which it did
                // not in landscape when this view grabbed everything.
                if (!_decided)
                {
                    float movedX = Math.abs(event.getX() - _downX);
                    float movedY = Math.abs(event.getY() - _downY);

                    if (movedX < _touchSlop && movedY < _touchSlop)
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

                    // From here the moment on show is the reader's own.
                    _followsNow = false;
                }

                if (!_dragging)
                {
                    return false;
                }

                float moved = event.getX() - _lastTouchX;
                _lastTouchX = event.getX();

                _moveCentreBy(_secondsFor(-moved));

                invalidate();
                return true;
            }

            case MotionEvent.ACTION_UP:
            {
                if (_dragging)
                {
                    _velocity.computeCurrentVelocity(1000, _maxFlingVelocity);
                    _startFling(_velocity.getXVelocity());
                }

                _releaseGesture();
                return true;
            }

            case MotionEvent.ACTION_CANCEL:
                _releaseGesture();
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }

    /** Lets the movement carry on after the finger leaves, and die out on its own. */
    private void _startFling(float velocityPixelsPerSecond)
    {
        if (Math.abs(velocityPixelsPerSecond) < _touchSlop)
        {
            return;
        }

        int velocity = (int) _secondsFor(-velocityPixelsPerSecond);

        int first = (int) (_model.fromUnixSeconds() + VISIBLE_SECONDS / 2L);
        int last = (int) (_model.toUnixSeconds() - VISIBLE_SECONDS / 2L);

        _scroller.fling((int) _centreUnixSeconds, 0, velocity, 0, first, last, 0, 0);

        postInvalidateOnAnimation();
    }

    @Override
    public void computeScroll()
    {
        if (_scroller == null || !_scroller.computeScrollOffset())
        {
            return;
        }

        _centreUnixSeconds = _scroller.getCurrX();
        postInvalidateOnAnimation();
    }

    /** Seconds of tide time a movement of that many pixels stands for. */
    private float _secondsFor(float pixels)
    {
        return pixels * VISIBLE_SECONDS / Math.max(1, getWidth());
    }

    private void _releaseGesture()
    {
        _dragging = false;
        _decided = false;

        getParent().requestDisallowInterceptTouchEvent(false);

        if (_velocity != null)
        {
            _velocity.recycle();
            _velocity = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);

        if (_model == null || getWidth() == 0 || getHeight() == 0)
        {
            return;
        }

        float left = _paddingLeft();
        float top = _paddingTop();
        float right = getWidth() - _paddingRight();
        float bottom = (getHeight() * (1.0f - OVERVIEW_SHARE)) - _paddingBottom();

        long from = _centreUnixSeconds - VISIBLE_SECONDS / 2L;
        long to = _centreUnixSeconds + VISIBLE_SECONDS / 2L;

        float scaleX = (right - left) / (float) VISIBLE_SECONDS;
        float range = Math.max(0.2f, _highest - _lowest);
        float scaleY = (bottom - top) / range;

        _drawFishingWindows(canvas, from, to, left, top, right, bottom, scaleX);
        _drawGrid(canvas, from, to, left, top, right, bottom, scaleX);

        // World units are (seconds since the model start, metres above chart datum); the
        // matrix alone turns them into pixels, so no point of the curve is recomputed here.
        _matrix.reset();
        _matrix.postTranslate(-(float) (from - _model.fromUnixSeconds()), -_lowest);
        _matrix.postScale(scaleX, -scaleY);
        _matrix.postTranslate(left, bottom);

        canvas.save();
        canvas.clipRect(left, top, right, bottom);

        _filled.transform(_matrix, _drawn);
        canvas.drawPath(_drawn, _fillPaint);

        _curve.transform(_matrix, _drawn);
        canvas.drawPath(_drawn, _curvePaint);

        canvas.restore();

        _drawNowLine(canvas, from, left, top, right, bottom, scaleX);
        _drawCursor(canvas, left, top, right, bottom);
        _drawOverview(canvas, left, right);
    }

    /**
     * The fixed reading line. The curve slides under it, so what it reports is simply what
     * is in the middle of the view: the height above it, the moment below it.
     */
    private void _drawCursor(Canvas canvas, float left, float top, float right, float bottom)
    {
        float x = (left + right) / 2.0f;

        canvas.drawLine(x, top, x, bottom, _cursorPaint);

        String height = String.format(Locale.UK, "%.2f m", _model.heightAt(_centreUnixSeconds));

        // Second row above the frame, under the red clock.
        canvas.drawText(height, _keepInside(x, height, left, right),
                _nowTextPaint.getTextSize() + _cursorTextPaint.getTextSize() + 4.0f,
                _cursorTextPaint);

        Calendar calendar = Calendar.getInstance(_zone);
        calendar.setTimeInMillis(_centreUnixSeconds * 1000L);

        String moment = String.format(Locale.UK, "%td/%tm  %02d:%02d",
                calendar, calendar,
                calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE));

        // Second row below the frame, under the hours.
        canvas.drawText(moment, _keepInside(x, moment, left, right),
                bottom + _textPaint.getTextSize() + _cursorTextPaint.getTextSize() + 8.0f,
                _cursorTextPaint);
    }

    /** Keeps a label from running off the edge of the view. */
    private float _keepInside(float x, String text, float left, float right)
    {
        float half = _cursorTextPaint.measureText(text) / 2.0f;

        return Math.min(Math.max(x, left + half), right - half);
    }

    /*
     * The whole prepared window in a strip, with the half day on screen picked out.
     * Twelve hours show the shape of one tide; only a month shows the springs swell and
     * the neaps collapse, and that is the other half of what a tide is.
     */
    private void _drawOverview(Canvas canvas, float left, float right)
    {
        float top = getHeight() * (1.0f - OVERVIEW_SHARE);
        float bottom = getHeight() - 1.0f;

        long first = _model.fromUnixSeconds();
        long last = _model.toUnixSeconds();

        float span = Math.max(1.0f, last - first);
        float range = Math.max(0.2f, _highest - _lowest);
        float scaleY = (bottom - top) / range;

        _overview.reset();

        int steps = (int) (right - left);

        for (int pixel = 0; pixel <= steps; pixel++)
        {
            long when = first + (long) ((pixel / (right - left)) * span);

            float y = bottom - (float) ((_model.heightAt(when) - _lowest) * scaleY);

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

    private void _drawFishingWindows(Canvas canvas, long from, long to,
                                     float left, float top, float right, float bottom,
                                     float scaleX)
    {
        for (TideModel.Window window : _model.windows())
        {
            if (window.toUnixSeconds < from || window.fromUnixSeconds > to)
            {
                continue;
            }

            float x1 = left + (window.fromUnixSeconds - from) * scaleX;
            float x2 = left + (window.toUnixSeconds - from) * scaleX;

            _band.set(Math.max(left, x1), top, Math.min(right, x2), bottom);
            canvas.drawRect(_band, _fishingPaint);
        }
    }

    private void _drawGrid(Canvas canvas, long from, long to,
                           float left, float top, float right, float bottom,
                           float scaleX)
    {
        Calendar calendar = Calendar.getInstance(_zone);
        calendar.setTimeInMillis(from * 1000L);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        while (calendar.getTimeInMillis() / 1000L <= to)
        {
            long moment = calendar.getTimeInMillis() / 1000L;
            int hour = calendar.get(Calendar.HOUR_OF_DAY);

            if (moment >= from && hour % 3 == 0)
            {
                float x = left + (moment - from) * scaleX;

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

    private void _drawNowLine(Canvas canvas, long from,
                              float left, float top, float right, float bottom,
                              float scaleX)
    {
        float x = left + (_nowUnixSeconds - from) * scaleX;
        float baseline = _nowTextPaint.getTextSize();   // first row, above everything

        // Off screen: no line to draw, but the reader has just dragged and needs to know
        // which way the present moment went.
        if (x < left || x > right)
        {
            _drawNowArrow(canvas, x < left, left, right, baseline);
            return;
        }

        _nowPaint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(x, top, x, bottom, _nowPaint);

        // The label sits in the margin above the frame, clear of the curve, exactly as the
        // hours sit in the margin below it.
        Calendar calendar = Calendar.getInstance(_zone);
        calendar.setTimeInMillis(_nowUnixSeconds * 1000L);

        String label = String.format(Locale.UK, "%02d:%02d",
                calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE));

        float half = _nowTextPaint.measureText(label) / 2.0f;
        float centre = Math.min(Math.max(x, left + half), right - half);

        canvas.drawText(label, centre, baseline, _nowTextPaint);
    }

    /** Points back towards the present moment once it has been scrolled off the view. */
    private void _drawNowArrow(Canvas canvas, boolean toTheLeft,
                               float left, float right, float baseline)
    {
        float size = _nowTextPaint.getTextSize() * 0.55f;
        float middle = baseline - size * 0.6f;
        float edge = toTheLeft ? left + size : right - size;
        float tip = toTheLeft ? edge - size : edge + size;

        _arrow.reset();
        _arrow.moveTo(tip, middle);
        _arrow.lineTo(edge, middle - size);
        _arrow.lineTo(edge, middle + size);
        _arrow.close();

        canvas.drawPath(_arrow, _fishingArrowPaint());

        canvas.drawText("now", toTheLeft ? edge + size * 2.2f : edge - size * 2.2f,
                baseline, _nowTextPaint);
    }

    private Paint _fishingArrowPaint()
    {
        _nowPaint.setStyle(Paint.Style.FILL);

        return _nowPaint;
    }

    private void _moveCentreBy(float seconds)
    {
        long moved = _centreUnixSeconds + (long) seconds;

        long first = _model.fromUnixSeconds() + VISIBLE_SECONDS / 2L;
        long last = _model.toUnixSeconds() - VISIBLE_SECONDS / 2L;

        if (moved < first)
        {
            moved = first;
        }

        if (moved > last)
        {
            moved = last;
        }

        _centreUnixSeconds = moved;
    }

    /** Turns the prepared heights into one Path in world units, once. */
    private void _buildPath()
    {
        _curve.reset();
        _filled.reset();

        if (_model == null)
        {
            return;
        }

        float[] heights = _model.heights();

        _lowest = heights[0];
        _highest = heights[0];

        for (float height : heights)
        {
            _lowest = Math.min(_lowest, height);
            _highest = Math.max(_highest, height);
        }

        // A little air above and below so the curve does not touch the frame.
        float margin = Math.max(0.1f, (_highest - _lowest) * 0.08f);
        _lowest -= margin;
        _highest += margin;

        for (int index = 0; index < heights.length; index++)
        {
            float x = (float) index * TideModel.CURVE_STEP_SECONDS;

            if (index == 0)
            {
                _curve.moveTo(x, heights[index]);
                _filled.moveTo(x, _lowest);
                _filled.lineTo(x, heights[index]);
                continue;
            }

            _curve.lineTo(x, heights[index]);
            _filled.lineTo(x, heights[index]);
        }

        _filled.lineTo((float) (heights.length - 1) * TideModel.CURVE_STEP_SECONDS, _lowest);
        _filled.close();
    }

    private float _paddingLeft()
    {
        return 8.0f;
    }

    private float _paddingRight()
    {
        return 8.0f;
    }

    // Two rows above the frame: the red clock on top, then the height under the cursor.
    // They are kept apart so a reading in red and a reading in black never overlap.
    private float _paddingTop()
    {
        return _nowTextPaint.getTextSize() + _cursorTextPaint.getTextSize() + 10.0f;
    }

    // Two rows below: the hours, then the moment the cursor is sitting on.
    private float _paddingBottom()
    {
        return _textPaint.getTextSize() + _cursorTextPaint.getTextSize() + 14.0f;
    }

    private void _setUpPaints()
    {
        float density = getResources().getDisplayMetrics().density;

        _scroller = new OverScroller(getContext());

        ViewConfiguration configuration = ViewConfiguration.get(getContext());
        _touchSlop = configuration.getScaledTouchSlop();
        _maxFlingVelocity = configuration.getScaledMaximumFlingVelocity();

        _curvePaint.setColor(COLOUR_CURVE);
        _curvePaint.setStyle(Paint.Style.STROKE);
        _curvePaint.setStrokeWidth(2.0f * density);

        _fillPaint.setColor(COLOUR_FILL);
        _fillPaint.setStyle(Paint.Style.FILL);

        _fishingPaint.setColor(COLOUR_FISHING);
        _fishingPaint.setStyle(Paint.Style.FILL);

        _nowPaint.setColor(COLOUR_NOW);
        _nowPaint.setStyle(Paint.Style.STROKE);
        _nowPaint.setStrokeWidth(2.0f * density);

        _overviewPaint.setColor(COLOUR_OVERVIEW);
        _overviewPaint.setStyle(Paint.Style.STROKE);
        _overviewPaint.setStrokeWidth(1.4f);

        _windowPaint.setColor(COLOUR_WINDOW);
        _windowPaint.setStyle(Paint.Style.FILL);

        _gridPaint.setColor(COLOUR_GRID);
        _gridPaint.setStyle(Paint.Style.STROKE);
        _gridPaint.setStrokeWidth(1.0f);

        _textPaint.setColor(COLOUR_TEXT);
        _textPaint.setTextSize(11.0f * density);
        _textPaint.setTextAlign(Paint.Align.CENTER);

        _nowTextPaint.setColor(COLOUR_NOW);
        _nowTextPaint.setTextSize(12.0f * density);
        _nowTextPaint.setTextAlign(Paint.Align.CENTER);
        _nowTextPaint.setFakeBoldText(true);

        // The day changes are the landmarks once the curve has been dragged, so they carry
        // more weight than the hours between them.
        _dayTextPaint.setColor(COLOUR_TEXT);
        _dayTextPaint.setTextSize(11.0f * density);
        _dayTextPaint.setTextAlign(Paint.Align.CENTER);
        _dayTextPaint.setFakeBoldText(true);

        // The reading cursor: it never moves, the curve moves under it.
        _cursorPaint.setColor(COLOUR_CURSOR);
        _cursorPaint.setStyle(Paint.Style.STROKE);
        _cursorPaint.setStrokeWidth(1.6f * density);

        _cursorTextPaint.setColor(COLOUR_CURSOR);
        _cursorTextPaint.setTextSize(12.0f * density);
        _cursorTextPaint.setTextAlign(Paint.Align.CENTER);
        _cursorTextPaint.setFakeBoldText(true);
    }
}
