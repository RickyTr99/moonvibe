package com.limelight.ui.apollo;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * The shape of a shoulder button, like the LB/RB glyphs of Xbox: the top rises in a long curve towards
 * the inner side, which ends in a round bulge; the bottom is flat with rounded corners, the sides lean in.
 */
public class BumperDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final boolean left;

    /**
     * @param left the left bumper (LB), whose outer side is on the left
     * @param dp one dp in pixels
     */
    public BumperDrawable(int color, boolean left, float dp) {
        this.left = left;
        paint.setColor(color);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        buildPath(bounds.width(), bounds.height());
    }

    // Drawn as the left bumper on a unit box stretched to the bounds, mirrored for the right one
    private void buildPath(float w, float h) {
        path.reset();
        path.moveTo(0.109f * w, h);
        path.lineTo(0.782f * w, h);
        path.cubicTo(0.832f * w, h, 0.866f * w, 0.95f * h, 0.882f * w, 0.896f * h);
        // The inner side leans in, then bulges round into the top
        path.lineTo(0.996f * w, 0.363f * h);
        path.cubicTo(1.01f * w, 0.25f * h, 0.86f * w, 0, 0.737f * w, 0);
        // The long curve of the top, down to the outer corner
        path.cubicTo(0.42f * w, 0, 0.17f * w, 0.1f * h, 0.061f * w, 0.301f * h);
        path.quadTo(0.03f * w, 0.35f * h, 0.026f * w, 0.401f * h);
        path.lineTo(0, 0.827f * h);
        path.cubicTo(-0.004f * w, 0.93f * h, 0.047f * w, h, 0.109f * w, h);
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
