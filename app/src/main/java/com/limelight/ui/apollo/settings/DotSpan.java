package com.limelight.ui.apollo.settings;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;

/**
 * A small dot right after the name of a setting, raised to the height of the capitals: the setting
 * differs from its default. It always takes its room, so fading it in or out never moves the text.
 */
final class DotSpan extends ReplacementSpan {
    private final int color;
    private final float size;
    private final float gap;
    private int alpha;

    DotSpan(int color, float size, float gap) {
        this.color = color;
        this.size = size;
        this.gap = gap;
    }

    void setAlpha(int alpha) {
        this.alpha = alpha;
    }

    int getAlpha() {
        return alpha;
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        return Math.round(gap + size);
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        if (alpha == 0) {
            return;
        }
        int oldColor = paint.getColor();
        Paint.Style oldStyle = paint.getStyle();
        paint.setColor(color);
        paint.setAlpha(alpha);
        paint.setStyle(Paint.Style.FILL);
        // Centered on the upper part of the capitals, like a badge
        float centerY = y + paint.ascent() * 0.62f;
        canvas.drawCircle(x + gap + size / 2, centerY, size / 2, paint);
        paint.setColor(oldColor);
        paint.setStyle(oldStyle);
    }
}
