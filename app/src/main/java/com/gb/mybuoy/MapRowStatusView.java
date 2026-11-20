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
 * The second line of a map row: its size on the left, its state on the right.
 *
 * Both are short until a download starts, and then the state grows a percentage and a
 * speed while the row keeps the width it had. Rather than wrap, which looked broken, or
 * hide one outright, which lost information that fitted perfectly well most of the time,
 * this lays them out itself and gives way in order:
 *
 *   1. shrink the size until both fit,
 *   2. drop the size and put the state back at full height,
 *   3. shrink the state,
 *   4. let the state end in an ellipsis.
 *
 * The decision is made with a scratch paint, so no child is touched while measuring; the
 * children are changed once, at the end, and only when the outcome actually differs.
 */
package com.gb.mybuoy;

import android.content.Context;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Lays out the size and the state of a map row side by side, adapting when room runs out. */
public final class MapRowStatusView extends ViewGroup
{
    /** How small the size may get before it is dropped instead. */
    private static final float MIN_SIZE_SP = 9.0f;

    /** How small the state may get before it is left to the ellipsis. */
    private static final float MIN_STATE_SP = 10.0f;

    private static final float STEP_SP = 0.5f;

    /** Room kept between the two so they never touch. */
    private static final float GAP_DP = 8.0f;

    private float _baseSizeSp = 0.0f;
    private float _baseStateSp = 0.0f;

    private float _appliedSizeSp = -1.0f;
    private float _appliedStateSp = -1.0f;
    private int _appliedSizeVisibility = -1;

    private final TextPaint _scratch = new TextPaint();

    public MapRowStatusView(Context context)
    {
        super(context);
    }

    public MapRowStatusView(Context context, AttributeSet attributes)
    {
        super(context, attributes);
    }

    public MapRowStatusView(Context context, AttributeSet attributes, int style)
    {
        super(context, attributes, style);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec)
    {
        TextView size = _childAt(0);
        TextView state = _childAt(1);

        if (size == null || state == null)
        {
            setMeasuredDimension(resolveSize(0, widthSpec), resolveSize(0, heightSpec));
            return;
        }

        _rememberBaseSizes(size, state);

        int available = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
        float gap = GAP_DP * getResources().getDisplayMetrics().density;

        String sizeText = size.getText().toString();
        String stateText = state.getText().toString();

        float stateWidth = _widthOf(state, stateText, _baseStateSp);

        float chosenSizeSp = _baseSizeSp;
        float chosenStateSp = _baseStateSp;
        boolean showSize = true;

        // 1. shrink the size alone, which is the one that can afford to be small
        while (chosenSizeSp >= MIN_SIZE_SP
                && _widthOf(size, sizeText, chosenSizeSp) + gap + stateWidth > available)
        {
            chosenSizeSp -= STEP_SP;
        }

        if (chosenSizeSp < MIN_SIZE_SP)
        {
            // 2. still too wide: the size goes, the state goes back to full height
            showSize = false;
            chosenSizeSp = _baseSizeSp;

            // 3. the state on its own may still be too long
            while (chosenStateSp > MIN_STATE_SP
                    && _widthOf(state, stateText, chosenStateSp) > available)
            {
                chosenStateSp -= STEP_SP;
            }
        }

        _apply(size, state, chosenSizeSp, chosenStateSp, showSize);

        int childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);

        state.measure(MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST), childHeightSpec);

        int left = showSize ? available - state.getMeasuredWidth() - (int) gap : 0;

        if (showSize)
        {
            size.measure(MeasureSpec.makeMeasureSpec(Math.max(0, left), MeasureSpec.AT_MOST),
                    childHeightSpec);
        }

        int height = Math.max(state.getMeasuredHeight(),
                showSize ? size.getMeasuredHeight() : 0);

        setMeasuredDimension(resolveSize(available + getPaddingLeft() + getPaddingRight(), widthSpec),
                height + getPaddingTop() + getPaddingBottom());
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom)
    {
        TextView size = _childAt(0);
        TextView state = _childAt(1);

        if (size == null || state == null)
        {
            return;
        }

        int innerLeft = getPaddingLeft();
        int innerTop = getPaddingTop();
        int innerRight = right - left - getPaddingRight();

        if (size.getVisibility() != GONE)
        {
            size.layout(innerLeft, innerTop,
                    innerLeft + size.getMeasuredWidth(), innerTop + size.getMeasuredHeight());
        }

        // The state is the one that must stay readable, so it keeps the right edge.
        state.layout(innerRight - state.getMeasuredWidth(), innerTop,
                innerRight, innerTop + state.getMeasuredHeight());
    }

    private void _rememberBaseSizes(TextView size, TextView state)
    {
        if (_baseSizeSp > 0.0f)
        {
            return;
        }

        float density = getResources().getDisplayMetrics().scaledDensity;

        _baseSizeSp = size.getTextSize() / density;
        _baseStateSp = state.getTextSize() / density;

        // Neither may wrap: the whole point is to keep the row one line high.
        size.setSingleLine(true);
        state.setSingleLine(true);
        state.setEllipsize(TextUtils.TruncateAt.END);
    }

    /** Width the text would take at that size, measured without touching the child. */
    private float _widthOf(TextView view, String text, float sizeSp)
    {
        _scratch.set(view.getPaint());
        _scratch.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp,
                getResources().getDisplayMetrics()));

        return _scratch.measureText(text);
    }

    /** Changes the children only when the outcome differs, which keeps the pass stable. */
    private void _apply(TextView size, TextView state,
                        float sizeSp, float stateSp, boolean showSize)
    {
        int visibility = showSize ? VISIBLE : GONE;

        if (visibility != _appliedSizeVisibility)
        {
            size.setVisibility(visibility);
            _appliedSizeVisibility = visibility;
        }

        if (sizeSp != _appliedSizeSp)
        {
            size.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
            _appliedSizeSp = sizeSp;
        }

        if (stateSp != _appliedStateSp)
        {
            state.setTextSize(TypedValue.COMPLEX_UNIT_SP, stateSp);
            _appliedStateSp = stateSp;
        }
    }

    private TextView _childAt(int index)
    {
        if (getChildCount() <= index)
        {
            return null;
        }

        View child = getChildAt(index);

        return (child instanceof TextView) ? (TextView) child : null;
    }
}
