package com.limelight.ui.apollo.launch;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import com.limelight.ui.theme.ApolloColors;
import com.limelight.ui.theme.ApolloMotion;

/**
 * The launch progress bar: a rounded track with a fill that moves one step at a time,
 * and a light that runs across the fill while it waits.
 */
public class LaunchProgressBar extends View {
    private final ApolloColors colors;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix shineMatrix = new Matrix();
    private final RectF rect = new RectF();

    private float progress;
    private float shinePhase;
    private ValueAnimator progressAnimator;
    private ValueAnimator shineAnimator;

    public LaunchProgressBar(Context context, ApolloColors colors) {
        super(context);
        this.colors = colors;
        track.setColor(colors.surfaceContainerHighest);
        fill.setColor(colors.primary);
    }

    public void reset() {
        if (progressAnimator != null) {
            progressAnimator.cancel();
        }
        progress = 0f;
        fill.setColor(colors.primary);
        startShine();
        invalidate();
    }

    public void setProgress(float target) {
        if (progressAnimator != null) {
            progressAnimator.cancel();
        }
        progressAnimator = ValueAnimator.ofFloat(progress, target);
        progressAnimator.setDuration(600);
        progressAnimator.setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE);
        progressAnimator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        progressAnimator.start();
    }

    public void setFailed() {
        stopShine();
        fill.setColor(colors.error);
        invalidate();
    }

    public void stop() {
        stopShine();
        if (progressAnimator != null) {
            progressAnimator.end();
        }
    }

    private void startShine() {
        if (shineAnimator != null) {
            return;
        }
        shineAnimator = ValueAnimator.ofFloat(0f, 1f);
        shineAnimator.setDuration(1600);
        shineAnimator.setRepeatCount(ValueAnimator.INFINITE);
        shineAnimator.setInterpolator(ApolloMotion.STANDARD);
        shineAnimator.addUpdateListener(a -> {
            shinePhase = (float) a.getAnimatedValue();
            invalidate();
        });
        shineAnimator.start();
    }

    private void stopShine() {
        if (shineAnimator != null) {
            shineAnimator.cancel();
            shineAnimator = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stop();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        shine.setShader(new LinearGradient(0, 0, w * 0.15f, 0,
                new int[] {0x00FFFFFF, 0x59FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float radius = h / 2f;

        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, radius, radius, track);

        float fillWidth = w * progress;
        if (fillWidth <= 0f) {
            return;
        }
        rect.set(0, 0, Math.max(fillWidth, h), h);
        canvas.drawRoundRect(rect, radius, radius, fill);

        if (shineAnimator != null) {
            canvas.save();
            canvas.clipRect(rect);
            float shineWidth = w * 0.15f;
            shineMatrix.setTranslate(-shineWidth + (fillWidth + shineWidth) * shinePhase, 0);
            shine.getShader().setLocalMatrix(shineMatrix);
            canvas.drawRect(rect, shine);
            canvas.restore();
        }
    }
}
