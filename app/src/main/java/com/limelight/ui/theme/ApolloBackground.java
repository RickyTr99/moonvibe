package com.limelight.ui.theme;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import java.util.Random;

/**
 * The window background of the MoonVibe screens: a dark vertical gradient with a soft glow of the primary
 * color at the bottom, which drifts sideways and breathes very slowly. It only animates while visible,
 * at a low frame rate, since the movement is too slow to need more.
 */
public class ApolloBackground extends Drawable implements Runnable {
    private static final long FRAME_MS = 33;
    private static final double DRIFT_PERIOD_MS = 40000;
    private static final double BREATH_PERIOD_MS = 12000;

    private final int top;
    private final int middle;
    private final int bottom;
    private static final int NOISE_SIZE = 128;
    private static final int NOISE_MAX_ALPHA = 3;

    private final Paint gradientPaint = new Paint(Paint.DITHER_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint noisePaint = new Paint();
    private final Matrix glowMatrix = new Matrix();
    private final long start = SystemClock.uptimeMillis();
    private boolean running = true;

    public ApolloBackground(ApolloColors colors) {
        // Dark gradients have so few color steps that OLED screens show them as bands: a fixed grain
        // of a few levels on top dithers the steps away. Not every renderer honors the dither flag.
        Bitmap noise = Bitmap.createBitmap(NOISE_SIZE, NOISE_SIZE, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[NOISE_SIZE * NOISE_SIZE];
        Random random = new Random(42);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = Color.argb(random.nextInt(NOISE_MAX_ALPHA + 1), 255, 255, 255);
        }
        noise.setPixels(pixels, 0, NOISE_SIZE, 0, 0, NOISE_SIZE, NOISE_SIZE);
        noisePaint.setShader(new BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));

        top = ApolloColors.blend(colors.surfaceContainerLow, Color.BLACK, 0.3f);
        middle = colors.surfaceContainerLow;
        bottom = ApolloColors.blend(colors.surfaceContainerLow, colors.primary, 0.07f);
        // A unit circle, placed and sized by the matrix on every frame
        int glow = colors.primary & 0x00FFFFFF;
        glowPaint.setShader(new RadialGradient(0, 0, 1,
                new int[] {0x30000000 | glow, 0x12000000 | glow, glow},
                new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        gradientPaint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                new int[] {top, middle, bottom}, new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        canvas.drawRect(bounds, gradientPaint);

        long now = SystemClock.uptimeMillis() - start;
        double drift = Math.sin(2 * Math.PI * now / DRIFT_PERIOD_MS);
        double breath = 0.5 + 0.5 * Math.sin(2 * Math.PI * now / BREATH_PERIOD_MS);

        float width = bounds.width();
        float height = bounds.height();
        float centerX = bounds.left + width * (0.5f + 0.15f * (float) drift);
        float centerY = bounds.bottom + height * 0.1f;
        float radius = Math.max(width, height) * (0.55f + 0.05f * (float) breath);
        glowMatrix.setScale(radius, radius * 0.6f);
        glowMatrix.postTranslate(centerX, centerY);
        glowPaint.getShader().setLocalMatrix(glowMatrix);
        glowPaint.setAlpha((int) (180 + 75 * breath));
        canvas.drawRect(bounds, glowPaint);
        canvas.drawRect(bounds, noisePaint);

        if (running) {
            scheduleSelf(this, SystemClock.uptimeMillis() + FRAME_MS);
        }
    }

    @Override
    public void run() {
        invalidateSelf();
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        running = visible;
        if (visible) {
            invalidateSelf();
        } else {
            unscheduleSelf(this);
        }
        return changed;
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
