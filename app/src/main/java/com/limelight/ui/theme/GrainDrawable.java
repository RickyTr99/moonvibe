package com.limelight.ui.theme;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import java.util.Random;

/**
 * A fixed grain of a few levels, drawn over dark gradients and blurred images. They have so few color
 * steps that OLED screens show them as bands: the grain dithers the steps away, which the dither flag
 * alone does not do on every renderer.
 */
public class GrainDrawable extends Drawable {
    private static final int SIZE = 128;
    // Strong enough to break the bands, too faint to be seen as noise
    public static final int DEFAULT_MAX_ALPHA = 3;

    private final Paint paint = new Paint();

    public GrainDrawable() {
        this(DEFAULT_MAX_ALPHA);
    }

    public GrainDrawable(int maxAlpha) {
        Bitmap noise = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[SIZE * SIZE];
        Random random = new Random(42);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = Color.argb(random.nextInt(maxAlpha + 1), 255, 255, 255);
        }
        noise.setPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE);
        paint.setShader(new BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawRect(getBounds(), paint);
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
