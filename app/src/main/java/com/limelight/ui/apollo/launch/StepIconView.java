package com.limelight.ui.apollo.launch;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.limelight.ui.apollo.ApolloUi;
import com.limelight.ui.theme.ApolloColors;
import com.limelight.ui.theme.ApolloMotion;

/**
 * The icon in front of a launch step: an empty ring while waiting, a spinning arc while running,
 * a check when done and a cross when the step failed. Check and cross pop in.
 */
public class StepIconView extends View {
    public static final int PENDING = 0;
    public static final int ACTIVE = 1;
    public static final int DONE = 2;
    public static final int FAILED = 3;

    private static final int ON_ERROR = 0xFF690005;

    private final ApolloColors colors;
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF arc = new RectF();

    private int state = PENDING;
    private float spinPhase;
    private float pop = 1f;
    private ValueAnimator spinner;
    private ValueAnimator popper;

    public StepIconView(Context context, ApolloColors colors) {
        super(context);
        this.colors = colors;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeCap(Paint.Cap.ROUND);
        mark.setStyle(Paint.Style.STROKE);
        mark.setStrokeCap(Paint.Cap.ROUND);
        mark.setStrokeJoin(Paint.Join.ROUND);
        mark.setStrokeWidth(ApolloUi.dp(context, 2.2f));
    }

    public void setState(int newState) {
        if (newState == state) {
            return;
        }
        state = newState;

        if (state == ACTIVE) {
            startSpinner();
        } else {
            stopSpinner();
        }

        if (state == DONE || state == FAILED) {
            if (popper != null) {
                popper.cancel();
            }
            popper = ValueAnimator.ofFloat(0.4f, 1.12f, 1f);
            popper.setDuration(ApolloMotion.LONG);
            popper.setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE);
            popper.addUpdateListener(a -> {
                pop = (float) a.getAnimatedValue();
                invalidate();
            });
            popper.start();
        }
        invalidate();
    }

    public void stop() {
        stopSpinner();
        if (popper != null) {
            popper.end();
        }
    }

    private void startSpinner() {
        if (spinner != null) {
            return;
        }
        spinner = ValueAnimator.ofFloat(0f, 1f);
        spinner.setDuration(1400);
        spinner.setRepeatCount(ValueAnimator.INFINITE);
        spinner.setInterpolator(new LinearInterpolator());
        spinner.addUpdateListener(a -> {
            spinPhase = (float) a.getAnimatedValue();
            invalidate();
        });
        spinner.start();
    }

    private void stopSpinner() {
        if (spinner != null) {
            spinner.cancel();
            spinner = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stop();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float size = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;

        switch (state) {
            case PENDING: {
                float stroke = ApolloUi.dp(getContext(), 1.8f);
                ring.setStrokeWidth(stroke);
                ring.setColor(colors.outlineVariant);
                canvas.drawCircle(cx, cy, size * 0.4f, ring);
                break;
            }
            case ACTIVE: {
                float stroke = ApolloUi.dp(getContext(), 2.4f);
                float r = size * 0.4f;
                ring.setStrokeWidth(stroke);
                ring.setColor(colors.primary);
                arc.set(cx - r, cy - r, cx + r, cy + r);
                // The arc grows and shrinks while it turns, like the Material circular indicator
                float sweep = 30f + 220f * (float) Math.sin(Math.PI * spinPhase);
                canvas.drawArc(arc, spinPhase * 540f - 90f, sweep, false, ring);
                break;
            }
            default: {
                boolean done = state == DONE;
                canvas.save();
                canvas.scale(pop, pop, cx, cy);
                fill.setColor(done ? colors.primary : colors.error);
                canvas.drawCircle(cx, cy, size / 2f, fill);
                mark.setColor(done ? colors.onPrimary : ON_ERROR);
                path.reset();
                float u = size / 24f;
                if (done) {
                    path.moveTo(cx - 5f * u, cy + 0.5f * u);
                    path.lineTo(cx - 1.7f * u, cy + 3.8f * u);
                    path.lineTo(cx + 5f * u, cy - 2.8f * u);
                } else {
                    path.moveTo(cx - 4f * u, cy - 4f * u);
                    path.lineTo(cx + 4f * u, cy + 4f * u);
                    path.moveTo(cx + 4f * u, cy - 4f * u);
                    path.lineTo(cx - 4f * u, cy + 4f * u);
                }
                canvas.drawPath(path, mark);
                canvas.restore();
                break;
            }
        }
    }
}
