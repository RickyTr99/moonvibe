package com.limelight.ui.apollo;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * The shape of a shoulder button, like the LB/RB glyphs of Xbox: flat at the bottom, a wide round corner
 * on the outer side and a top edge that curves down towards the inner side.
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
        float small = 4 * dp;
        float outer = Math.min(h * 0.75f, w * 0.5f);
        float dip = h * 0.28f;

        path.reset();
        path.moveTo(0, h - small);
        path.lineTo(0, outer);
        // Wide outer corner
        path.quadTo(0, 0, outer, 0);
        // Top edge, curving down to the inner side
        path.cubicTo(w * 0.62f, 0, w - small, dip * 0.4f, w - small * 0.3f, dip);
        path.quadTo(w, dip + small * 0.3f, w, dip + small);
        path.lineTo(w, h - small);
        path.quadTo(w, h, w - small, h);
        path.lineTo(small, h);
        path.quadTo(0, h, 0, h - small);
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
