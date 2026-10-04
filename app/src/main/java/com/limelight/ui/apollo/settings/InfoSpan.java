package com.limelight.ui.apollo.settings;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.widget.TextView;

/**
 * A soft filled "i" after the name of a setting that has an explanation. Part of the text,
 * so it stays right after the last word when the name wraps.
 */
final class InfoSpan extends ReplacementSpan {
    private final int circleColor;
    private final int glyphColor;
    private final float size;
    private final float gap;

    InfoSpan(int circleColor, int glyphColor, float size, float gap) {
        this.circleColor = circleColor;
        this.glyphColor = glyphColor;
        this.size = size;
        this.gap = gap;
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        return Math.round(gap + size);
    }

    // Centered on the capitals, which sit between the baseline and about two thirds of the ascent
    private static float centerY(float baseline, Paint paint) {
        return baseline + paint.ascent() * 0.36f;
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        int oldColor = paint.getColor();
        Paint.Style oldStyle = paint.getStyle();
        float oldStroke = paint.getStrokeWidth();
        Paint.Cap oldCap = paint.getStrokeCap();

        float r = size / 2;
        float cx = x + gap + r;
        float cy = centerY(y, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(circleColor);
        canvas.drawCircle(cx, cy, r, paint);

        // The "i": a dot over a short stem, drawn on a 24 unit grid like the icons
        float unit = size / 22;
        paint.setColor(glyphColor);
        canvas.drawCircle(cx, cy - 4.6f * unit, 1.5f * unit, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.4f * unit);
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(cx, cy - 1f * unit, cx, cy + 5f * unit, paint);

        paint.setColor(oldColor);
        paint.setStyle(oldStyle);
        paint.setStrokeWidth(oldStroke);
        paint.setStrokeCap(oldCap);
    }

    /**
     * Where the "i" is drawn in a label, in the label's coordinates: {center x, center y},
     * or null before the label is laid out.
     */
    float[] center(TextView label) {
        Layout layout = label.getLayout();
        if (layout == null || !(label.getText() instanceof Spanned)) {
            return null;
        }
        int start = ((Spanned) label.getText()).getSpanStart(this);
        if (start < 0) {
            return null;
        }
        int line = layout.getLineForOffset(start);
        float x = label.getTotalPaddingLeft() + layout.getPrimaryHorizontal(start) + gap + size / 2;
        float y = label.getTotalPaddingTop() + centerY(layout.getLineBaseline(line), label.getPaint());
        return new float[] { x, y };
    }
}
