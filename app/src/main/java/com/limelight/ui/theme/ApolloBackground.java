package com.limelight.ui.theme;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import java.util.Random;

/**
 * The window background of the MoonVibe screens: a dark vertical gradient, and over it the animation chosen
 * in Settings > App: stars that twinkle with now and then a shooting star, controller symbols rising from the
 * bottom, an aurora of slow blurred colors, or a soft glow of the primary color at the bottom that drifts
 * sideways and breathes. It only animates while visible, at a low frame rate, since the movement is too slow
 * to need more.
 */
public class ApolloBackground extends Drawable implements Runnable {
    private static final long FRAME_MS = 33;
    private static final double DRIFT_PERIOD_MS = 40000;
    private static final double BREATH_PERIOD_MS = 12000;

    // Space
    private static final int STAR_COUNT = 90;
    private static final double SHOOTING_PERIOD_MS = 11000;
    private static final double SHOOTING_DURATION_MS = 1100;
    // Rising symbols
    private static final int SYMBOL_COUNT = 14;
    // Aurora
    private static final double[] AURORA_PERIODS_MS = {26000, 32000, 38000};

    private final String style;
    private final int primary;
    private final int secondColor;
    private final float dp = Resources.getSystem().getDisplayMetrics().density;

    private final int top;
    private final int middle;
    private final int bottom;

    private final Paint gradientPaint = new Paint(Paint.DITHER_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    // Dark gradients show bands on OLED screens without it
    private final GrainDrawable grain = new GrainDrawable();
    private final Matrix glowMatrix = new Matrix();
    private final long start = SystemClock.uptimeMillis();
    private boolean running = true;

    // Placed again when the size changes: x and y as fractions of the bounds, the rest as the style needs
    private float[][] stars;
    private float[][] symbols;
    private Paint[] auroraPaints;

    /** The soft glow, as on the panel of the game menu. */
    public ApolloBackground(ApolloColors colors) {
        this(colors, ThemeColors.BACKGROUND_GLOW);
    }

    public ApolloBackground(ApolloColors colors, String style) {
        this.style = style;
        primary = colors.primary;
        float[] hsv = new float[3];
        Color.colorToHSV(colors.primary, hsv);
        // The second color of the aurora, a little further around the color wheel
        hsv[0] = (hsv[0] + 45) % 360;
        secondColor = Color.HSVToColor(hsv);

        top = ApolloColors.blend(colors.surfaceContainerLow, Color.BLACK, ThemeColors.BACKGROUND_SPACE.equals(style) ? 0.45f : 0.3f);
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
        grain.setBounds(bounds);
        gradientPaint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                new int[] {top, middle, bottom}, new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));

        // The same sky every time
        Random random = new Random(7);
        switch (style) {
            case ThemeColors.BACKGROUND_SPACE:
                stars = new float[STAR_COUNT][];
                for (int i = 0; i < STAR_COUNT; i++) {
                    // x, y, radius (dp), period (ms), phase, tinted (1) or white (0)
                    stars[i] = new float[] {random.nextFloat(), random.nextFloat(), 0.5f + random.nextFloat() * 0.8f,
                            3000 + random.nextFloat() * 5000, random.nextFloat(), random.nextFloat() < 0.25f ? 1 : 0};
                }
                break;
            case ThemeColors.BACKGROUND_SYMBOLS:
                symbols = new float[SYMBOL_COUNT][];
                for (int i = 0; i < SYMBOL_COUNT; i++) {
                    // x, size (dp), rise time (ms), phase, shape, sway (1) or straight (0), turn direction
                    symbols[i] = new float[] {(i + 0.2f + random.nextFloat() * 0.6f) / SYMBOL_COUNT, 22 + random.nextFloat() * 20,
                            19000 + random.nextFloat() * 11000, random.nextFloat(), i % 4, random.nextFloat() < 0.5f ? 1 : 0,
                            random.nextFloat() < 0.5f ? 1 : -1};
                }
                break;
            case ThemeColors.BACKGROUND_AURORA:
                auroraPaints = new Paint[3];
                int[] colors = {primary, secondColor, primary};
                int[] alphas = {0x30, 0x28, 0x18};
                for (int i = 0; i < 3; i++) {
                    int color = colors[i] & 0x00FFFFFF;
                    auroraPaints[i] = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
                    auroraPaints[i].setShader(new RadialGradient(0, 0, 1,
                            new int[] {(alphas[i] << 24) | color, ((alphas[i] / 3) << 24) | color, color},
                            new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
                }
                break;
        }
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        canvas.drawRect(bounds, gradientPaint);

        long now = SystemClock.uptimeMillis() - start;
        switch (style) {
            case ThemeColors.BACKGROUND_SPACE:
                drawSpace(canvas, bounds, now);
                break;
            case ThemeColors.BACKGROUND_SYMBOLS:
                drawSymbols(canvas, bounds, now);
                break;
            case ThemeColors.BACKGROUND_AURORA:
                drawAurora(canvas, bounds, now);
                break;
            default:
                drawGlow(canvas, bounds, now);
                break;
        }
        grain.draw(canvas);

        if (running) {
            scheduleSelf(this, SystemClock.uptimeMillis() + FRAME_MS);
        }
    }

    private void drawGlow(Canvas canvas, Rect bounds, long now) {
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
    }

    private void drawSpace(Canvas canvas, Rect bounds, long now) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        for (float[] star : stars) {
            double twinkle = 0.5 + 0.5 * Math.sin(2 * Math.PI * (now / star[3] + star[4]));
            paint.setColor(star[5] == 1 ? primary : Color.WHITE);
            paint.setAlpha((int) (255 * (0.12 + 0.7 * twinkle)));
            canvas.drawCircle(bounds.left + star[0] * bounds.width(), bounds.top + star[1] * bounds.height(), star[2] * dp, paint);
        }

        // A shooting star now and then, from the upper right towards the lower left, each time somewhere else
        long round = (long) (now / SHOOTING_PERIOD_MS);
        double into = now - round * SHOOTING_PERIOD_MS;
        if (into < SHOOTING_DURATION_MS) {
            Random random = new Random(round * 31 + 11);
            float progress = (float) (into / SHOOTING_DURATION_MS);
            float startX = bounds.left + bounds.width() * (0.55f + random.nextFloat() * 0.4f);
            float startY = bounds.top + bounds.height() * random.nextFloat() * 0.35f;
            float travel = Math.min(bounds.width(), bounds.height()) * 0.7f;
            // About 24 degrees down from the horizontal
            float dx = -travel * 0.91f, dy = travel * 0.41f;
            float headX = startX + dx * progress, headY = startY + dy * progress;
            float tail = 0.18f;
            float tailX = headX - dx * tail, tailY = headY - dy * tail;
            int alpha = (int) (255 * Math.sin(Math.PI * progress));
            paint.setShader(new LinearGradient(headX, headY, tailX, tailY,
                    (alpha << 24) | 0xFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.6f * dp);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setAlpha(255);
            canvas.drawLine(headX, headY, tailX, tailY, paint);
            paint.setShader(null);
        }
    }

    private void drawSymbols(Canvas canvas, Rect bounds, long now) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(primary);
        for (float[] symbol : symbols) {
            float size = symbol[1] * dp;
            float progress = (float) (((now / symbol[2]) + symbol[3]) % 1.0);
            float travel = bounds.height() + size * 2;
            float y = bounds.bottom + size - travel * progress;
            float sway = symbol[5] == 1 ? (float) Math.sin(2 * Math.PI * progress) * 30 * dp : 0;
            float x = bounds.left + symbol[0] * bounds.width() + sway;
            // Fades in at the bottom and out at the top
            float fade = Math.min(1f, Math.min(progress, 1f - progress) / 0.15f);
            paint.setAlpha((int) (0x40 * fade));
            paint.setStrokeWidth(size * 0.075f);

            canvas.save();
            canvas.translate(x, y);
            canvas.rotate(symbol[6] * 90 * progress);
            float r = size * 0.32f;
            switch ((int) symbol[4]) {
                case 0:
                    canvas.drawLine(-r, -r, r, r, paint);
                    canvas.drawLine(r, -r, -r, r, paint);
                    break;
                case 1:
                    canvas.drawCircle(0, 0, r * 1.05f, paint);
                    break;
                case 2:
                    canvas.drawRect(-r, -r, r, r, paint);
                    break;
                default:
                    path.reset();
                    path.moveTo(0, -r * 1.15f);
                    path.lineTo(r * 1.15f, r * 0.85f);
                    path.lineTo(-r * 1.15f, r * 0.85f);
                    path.close();
                    canvas.drawPath(path, paint);
                    break;
            }
            canvas.restore();
        }
    }

    private void drawAurora(Canvas canvas, Rect bounds, long now) {
        float width = bounds.width();
        float height = bounds.height();
        // Center x, center y, width and height as fractions of the bounds, and how far each one wanders
        float[][] blobs = {
                {0.25f, 0.85f, 0.55f, 0.45f, 0.22f, 0.06f},
                {0.75f, 0.9f, 0.5f, 0.4f, -0.26f, 0.05f},
                {0.5f, 0.05f, 0.4f, 0.3f, 0.1f, 0.08f}};
        for (int i = 0; i < blobs.length; i++) {
            float[] blob = blobs[i];
            double wave = Math.sin(2 * Math.PI * now / AURORA_PERIODS_MS[i] + i * 1.7);
            double breath = 1 + 0.12 * Math.sin(2 * Math.PI * now / (AURORA_PERIODS_MS[i] * 0.7) + i);
            float centerX = bounds.left + width * (blob[0] + blob[4] * (float) wave);
            float centerY = bounds.top + height * (blob[1] - blob[5] * (float) wave);
            glowMatrix.setScale(width * blob[2] * (float) breath, height * blob[3] * (float) breath);
            glowMatrix.postTranslate(centerX, centerY);
            auroraPaints[i].getShader().setLocalMatrix(glowMatrix);
            canvas.drawRect(bounds, auroraPaints[i]);
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
