package com.limelight.ui.apollo.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import com.limelight.ui.theme.ApolloColors;

/**
 * Material 3 slider track drawn inline in a settings row. Touch drags it; the row moves it with the D-pad.
 */
public class SliderView extends View {
    public interface Listener {
        // While dragging
        void onSliderMoved(int value);

        // Finger lifted
        void onSliderReleased(int value);
    }

    private static final float STICK_RADIUS_DP = 12;

    // A value typed past the end of the slider (the bitrate over 500 Mbps): the track, its number, a chosen shortcut
    public static final int OVER_RANGE_COLOR = 0xFFE5736A;
    public static final int OVER_RANGE_TEXT = 0xFFF2A39C;
    public static final int OVER_RANGE_FILL = 0xFF5A2E2B;
    public static final int OVER_RANGE_ON_FILL = 0xFFFFDAD6;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final ApolloColors colors;
    private int min, max, step, value;
    private Listener listener;
    private String stickLetter;
    private boolean overRange;

    public SliderView(Context context, ApolloColors colors) {
        super(context);
        this.colors = colors;
    }

    public void setRange(int min, int max, int step) {
        this.min = min;
        this.max = Math.max(max, min + 1);
        this.step = Math.max(step, 1);
    }

    /** A value past the end: the slider stays full, in red. */
    public void setOverRange(boolean overRange) {
        if (this.overRange != overRange) {
            this.overRange = overRange;
            invalidate();
        }
    }

    public void setValue(int value) {
        this.value = Math.max(min, Math.min(max, value));
        invalidate();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // 200 x 40 dp unless the layout sets a size
        int width = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY
                ? MeasureSpec.getSize(widthMeasureSpec) : (int) dp(200);
        int height = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY
                ? MeasureSpec.getSize(heightMeasureSpec) : (int) dp(40);
        setMeasuredDimension(width, height);
    }

    /**
     * Draws the handle as the top of a gamepad stick with its letter ("L" or "R"), for a slider moved
     * by that stick; null for the usual thin handle.
     */
    public void setStickHandle(String letter) {
        stickLetter = letter;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float cy = h / 2;
        // Material 3 track: thick and rounded, split by a tall thin handle (or the round stick)
        float track = dp(12);
        float handleW = stickLetter != null ? dp(STICK_RADIUS_DP * 2) : dp(4);
        float handleH = dp(32);
        float gap = stickLetter != null ? handleW / 2 + dp(4) : dp(6);
        float fraction = (value - min) / (float) (max - min);
        float x = handleW / 2 + fraction * (w - handleW);

        // Inactive part, then active part, with a gap around the handle as in Material 3
        paint.setColor(colors.surfaceContainerHighest);
        rect.set(Math.min(x + gap, w), cy - track / 2, w, cy + track / 2);
        if (rect.width() > 0) {
            canvas.drawRoundRect(rect, track / 2, track / 2, paint);
        }
        paint.setColor(overRange ? OVER_RANGE_COLOR : colors.primary);
        rect.set(0, cy - track / 2, Math.max(x - gap, 0), cy + track / 2);
        if (rect.width() > 0) {
            canvas.drawRoundRect(rect, track / 2, track / 2, paint);
        }

        if (stickLetter == null) {
            rect.set(x - handleW / 2, cy - handleH / 2, x + handleW / 2, cy + handleH / 2);
            canvas.drawRoundRect(rect, handleW / 2, handleW / 2, paint);
            return;
        }

        // The stick seen from above: a round cap with a ring for the grip and the letter in the middle
        float radius = handleW / 2;
        canvas.drawCircle(x, cy, radius, paint);
        stickPaint.setStyle(Paint.Style.STROKE);
        stickPaint.setStrokeWidth(dp(1.5f));
        stickPaint.setColor(colors.onPrimary);
        stickPaint.setAlpha(110);
        canvas.drawCircle(x, cy, radius - dp(3.5f), stickPaint);
        stickPaint.setStyle(Paint.Style.FILL);
        stickPaint.setAlpha(255);
        stickPaint.setTextSize(dp(10));
        stickPaint.setTextAlign(Paint.Align.CENTER);
        stickPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        Paint.FontMetrics metrics = stickPaint.getFontMetrics();
        canvas.drawText(stickLetter, x, cy - (metrics.ascent + metrics.descent) / 2, stickPaint);
    }

    private int valueAt(float x) {
        float fraction = Math.max(0, Math.min(1, x / getWidth()));
        int raw = min + Math.round(fraction * (max - min));
        int stepped = Math.round(raw / (float) step) * step;
        return Math.max(min, Math.min(max, stepped));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                // Fall through
            case MotionEvent.ACTION_MOVE:
                setValue(valueAt(event.getX()));
                if (listener != null) {
                    listener.onSliderMoved(value);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                setValue(valueAt(event.getX()));
                if (listener != null) {
                    listener.onSliderReleased(value);
                }
                return true;
        }
        return super.onTouchEvent(event);
    }
}
