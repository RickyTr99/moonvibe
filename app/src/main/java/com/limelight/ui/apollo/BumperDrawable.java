package com.limelight.ui.apollo;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * The shape of a shoulder button, like the LB/RB glyphs of Xbox: a low, wide bar with the outer side rounded
 * and the inner corners almost square. Flatter than a trigger (LT/RT), which is tall and curved on top.
 */
public class BumperDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final boolean left;
    private final float dp;

    /**
     * @param left the left bumper (LB), whose outer side is on the left
     * @param dp one dp in pixels
     */
    public BumperDrawable(int color, boolean left, float dp) {
        this.left = left;
        this.dp = dp;
        paint.setColor(color);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        buildPath(bounds.width(), bounds.height());
    }

    // Drawn as the left bumper, mirrored for the right one
    private void buildPath(float w, float h) {
        float inner = Math.min(3 * dp, h / 3);
        float outerTop = Math.min(h * 0.65f, w / 3);
        float outerBottom = Math.min(h * 0.4f, w / 4);

        path.reset();
        path.moveTo(outerTop, 0);
        path.lineTo(w - inner, 0);
        path.quadTo(w, 0, w, inner);
        path.lineTo(w, h - inner);
        path.quadTo(w, h, w - inner, h);
        path.lineTo(outerBottom, h);
        path.quadTo(0, h, 0, h - outerBottom);
        path.lineTo(0, outerTop);
        // The outer top corner is the roundest, as on the controller
        path.quadTo(0, 0, outerTop, 0);
        path.close();
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        canvas.save();
        canvas.translate(bounds.left, bounds.top);
        if (!left) {
            canvas.scale(-1, 1, bounds.width() / 2f, 0);
        }
        canvas.drawPath(path, paint);
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
