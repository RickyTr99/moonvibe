package com.limelight.ui.apollo;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import com.limelight.R;
import com.limelight.ui.theme.ApolloColors;
import com.limelight.ui.theme.ApolloMotion;

/**
 * Over the stream while comparing the sharpening: a line where the renderer splits the picture,
 * original on the left, with a handle that drags it. It's all drawn here, nothing moves over the
 * stream's SurfaceView. Touches away from the line go through to the stream.
 */
public class CompareSplitView extends View {
    public interface Listener {
        // 0 to 1 across the picture
        void onSplitMoved(float split);
    }

    private static final float GRAB_DP = 28;
    private static final float HANDLE_RADIUS_DP = 20;
    private static final float HANDLE_PRESSED_SCALE = 1.15f;
    private static final float LINE_DP = 2;
    private static final float LABEL_HEIGHT_DP = 28;
    private static final float LABEL_GAP_DP = 12;
    private static final float LABEL_TOP_DP = 16;

    private final ApolloColors colors;
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chevronPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path chevrons = new Path();
    private final RectF rect = new RectF();
    private final String originalLabel;
    private final String sharpenedLabel;

    private View picture;
    private Listener listener;
    private float split = 0.5f;
    private boolean dragging;
    private float handleScale = 1f;
    private ValueAnimator scaleAnimator;

    public CompareSplitView(Context context) {
        this(context, null);
    }

    public CompareSplitView(Context context, AttributeSet attrs) {
        super(context, attrs);
        colors = ApolloColors.dark(context);
        originalLabel = context.getString(R.string.compare_original);
        sharpenedLabel = context.getString(R.string.compare_sharpened);

        linePaint.setColor(withAlpha(colors.onSurface, 0xCC));
        linePaint.setStrokeWidth(dp(LINE_DP));
        // Almost opaque, as the mouse pill, so it reads over any game
        fillPaint.setColor(withAlpha(colors.surfaceContainerHigh, 0xF0));
        chevronPaint.setColor(colors.onSurface);
        chevronPaint.setStyle(Paint.Style.STROKE);
        chevronPaint.setStrokeWidth(dp(2));
        chevronPaint.setStrokeCap(Paint.Cap.ROUND);
        chevronPaint.setStrokeJoin(Paint.Join.ROUND);
        textPaint.setColor(colors.onSurface);
        textPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12,
                getResources().getDisplayMetrics()));
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    /** The view the picture is drawn in, a sibling of this one: the split goes across it. */
    public void setPicture(View picture) {
        this.picture = picture;
        picture.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> invalidate());
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public float getSplit() {
        return split;
    }

    public void setSplit(float split) {
        this.split = Math.max(0f, Math.min(1f, split));
        invalidate();
    }

    public void show() {
        animate().cancel();
        if (getVisibility() != VISIBLE) {
            setAlpha(0f);
            setVisibility(VISIBLE);
        }
        animate().alpha(1f).setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD).start();
    }

    public void hide() {
        dragging = false;
        animate().cancel();
        animate().alpha(0f).setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD)
                .withEndAction(() -> setVisibility(GONE)).start();
    }

    private float dp(float value) {
        return ApolloUi.dp(getContext(), value);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private float pictureLeft() {
        return picture != null ? picture.getLeft() : 0;
    }

    private float pictureWidth() {
        return picture != null ? picture.getWidth() : getWidth();
    }

    private float lineX() {
        return pictureLeft() + pictureWidth() * split;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float top = picture != null ? picture.getTop() : 0;
        float bottom = picture != null ? picture.getBottom() : getHeight();
        float x = lineX();

        canvas.drawLine(x, top, x, bottom, linePaint);

        // The handle, with a chevron to each side
        float cy = (top + bottom) / 2;
        float radius = dp(HANDLE_RADIUS_DP) * handleScale;
        canvas.drawCircle(x, cy, radius, fillPaint);
        float arm = dp(4);
        float offset = dp(5);
        chevrons.reset();
        chevrons.moveTo(x - offset + arm / 2, cy - arm);
        chevrons.lineTo(x - offset - arm / 2, cy);
        chevrons.lineTo(x - offset + arm / 2, cy + arm);
        chevrons.moveTo(x + offset - arm / 2, cy - arm);
        chevrons.lineTo(x + offset + arm / 2, cy);
        chevrons.lineTo(x + offset - arm / 2, cy + arm);
        canvas.drawPath(chevrons, chevronPaint);

        // Which side is which, each shown only while it fits on its side
        float labelTop = top + dp(LABEL_TOP_DP);
        drawLabel(canvas, originalLabel, x - dp(LABEL_GAP_DP), labelTop, true, pictureLeft());
        drawLabel(canvas, sharpenedLabel, x + dp(LABEL_GAP_DP), labelTop, false, pictureLeft() + pictureWidth());
    }

    private void drawLabel(Canvas canvas, String label, float edge, float top, boolean leftOfEdge, float limit) {
        float padding = dp(12);
        float width = textPaint.measureText(label) + padding * 2;
        float left = leftOfEdge ? edge - width : edge;
        float right = left + width;
        if (leftOfEdge ? left < limit : right > limit) {
            return;
        }
        float height = dp(LABEL_HEIGHT_DP);
        rect.set(left, top, right, top + height);
        canvas.drawRoundRect(rect, height / 2, height / 2, fillPaint);
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float baseline = top + height / 2 - (metrics.ascent + metrics.descent) / 2;
        canvas.drawText(label, left + padding, baseline, textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Only a touch on the line or its handle is ours
                if (Math.abs(event.getX() - lineX()) > dp(GRAB_DP)) {
                    return false;
                }
                dragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                animateHandle(HANDLE_PRESSED_SCALE);
                moveTo(event.getX());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    moveTo(event.getX());
                }
                return dragging;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    dragging = false;
                    animateHandle(1f);
                }
                return true;
        }
        return dragging;
    }

    private void moveTo(float x) {
        float width = pictureWidth();
        if (width <= 0) {
            return;
        }
        setSplit((x - pictureLeft()) / width);
        if (listener != null) {
            listener.onSplitMoved(split);
        }
    }

    private void animateHandle(float target) {
        if (scaleAnimator != null) {
            scaleAnimator.cancel();
        }
        scaleAnimator = ValueAnimator.ofFloat(handleScale, target);
        scaleAnimator.setDuration(ApolloMotion.SHORT);
        scaleAnimator.setInterpolator(ApolloMotion.STANDARD);
        scaleAnimator.addUpdateListener(animation -> {
            handleScale = (float) animation.getAnimatedValue();
            invalidate();
        });
        scaleAnimator.start();
    }
}
