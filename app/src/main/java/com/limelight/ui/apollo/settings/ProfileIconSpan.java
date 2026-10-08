package com.limelight.ui.apollo.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.style.ReplacementSpan;

import com.limelight.R;

/**
 * The profile icon inside a line of text, as in the note on top of a category: where the changes
 * go while a profile is in use.
 */
final class ProfileIconSpan extends ReplacementSpan {
    private final Drawable icon;
    private final float size;
    private final float gap;
    private int alpha;

    ProfileIconSpan(Context context, int color, float size, float gap) {
        this.icon = context.getDrawable(R.drawable.ic_apollo_profile).mutate();
        this.icon.setTint(color);
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
        // Centered on the capitals, like the "i"
        float centerY = y + paint.ascent() * 0.36f;
        int left = Math.round(x + gap);
        int iconTop = Math.round(centerY - size / 2);
        icon.setBounds(left, iconTop, left + Math.round(size), iconTop + Math.round(size));
        icon.setAlpha(alpha);
        icon.draw(canvas);
    }
}
