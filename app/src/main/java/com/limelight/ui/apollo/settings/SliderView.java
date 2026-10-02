package com.limelight.ui.apollo.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import com.limelight.ui.theme.ApolloColors;

/**
 * Material 3 slider track drawn inline in a settings row. Touch drags it; the row moves it with the D-pad.
 */
class SliderView extends View {
    interface Listener {
        // While dragging
        void onSliderMoved(int value);

        // Finger lifted
        void onSliderReleased(int value);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final ApolloColors colors;
    private int min, max, step, value;
    private Listener listener;

    SliderView(Context context, ApolloColors colors) {
        super(context);
        this.colors = colors;
    }

    void setRange(int min, int max, int step) {
        this.min = min;
        this.max = Math.max(max, min + 1);
        this.step = Math.max(step, 1);
    }

    void setValue(int value) {
        this.value = Math.max(min, Math.min(max, value));
        invalidate();
    }

    void setListener(Listener listener) {
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

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float cy = h / 2;
        // Material 3 track: thick and rounded, split by a tall thin handle
        float track = dp(12);
        float handleW = dp(4), handleH = dp(32);
        float fraction = (value - min) / (float) (max - min);
        float x = handleW / 2 + fraction * (w - handleW);

        // Inactive part, then active part, with a gap around the handle as in Material 3
        paint.setColor(colors.surfaceContainerHighest);
        rect.set(Math.min(x + dp(6), w), cy - track / 2, w, cy + track / 2);
        canvas.drawRoundRect(rect, track / 2, track / 2, paint);
        paint.setColor(colors.primary);
        rect.set(0, cy - track / 2, Math.max(x - dp(6), 0), cy + track / 2);
        canvas.drawRoundRect(rect, track / 2, track / 2, paint);
        rect.set(x - handleW / 2, cy - handleH / 2, x + handleW / 2, cy + handleH / 2);
        canvas.drawRoundRect(rect, handleW / 2, handleW / 2, paint);
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
