package com.limelight.ui.gamemenu;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import com.limelight.ui.theme.ApolloMotion;

/**
 * Small views drawn in code for the game menu, in the Material 3 style.
 */
final class MenuWidgets {
    private MenuWidgets() {
    }

    private static float dp(View view, float value) {
        return value * view.getResources().getDisplayMetrics().density;
    }

    /** Material 3 switch, display only: the whole row is the touch target. */
    static class SwitchView extends View {
        private static final ArgbEvaluator ARGB = new ArgbEvaluator();

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        // 0 is off, 1 is on, values in between while animating
        private float progress;
        private ValueAnimator animator;
        private int checkedTrack, checkedKnob, uncheckedTrack, uncheckedOutline;

        SwitchView(Context context) {
            super(context);
        }

        void setColors(int checkedTrack, int checkedKnob, int uncheckedTrack, int uncheckedOutline) {
            this.checkedTrack = checkedTrack;
            this.checkedKnob = checkedKnob;
            this.uncheckedTrack = uncheckedTrack;
            this.uncheckedOutline = uncheckedOutline;
            invalidate();
        }

        void setChecked(boolean checked, boolean animate) {
            float target = checked ? 1 : 0;
            if (animator != null) {
                animator.cancel();
            }
            if (!animate || progress == target) {
                progress = target;
                invalidate();
                return;
            }
            animator = ValueAnimator.ofFloat(progress, target);
            animator.setDuration(ApolloMotion.MEDIUM);
            animator.setInterpolator(ApolloMotion.STANDARD);
            animator.addUpdateListener(animation -> {
                progress = (float) animation.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension((int) dp(this, 44), (int) dp(this, 26));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            float border = dp(this, 2);
            float radius = h / 2;

            rect.set(0, 0, w, h);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor((int) ARGB.evaluate(progress, uncheckedTrack, checkedTrack));
            canvas.drawRoundRect(rect, radius, radius, paint);

            // The outline of the off state fades into the track
            if (progress < 1) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(border);
                paint.setColor((int) ARGB.evaluate(progress, uncheckedOutline, checkedTrack));
                rect.inset(border / 2, border / 2);
                canvas.drawRoundRect(rect, radius, radius, paint);
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setColor((int) ARGB.evaluate(progress, uncheckedOutline, checkedKnob));
            float knobRadius = dp(this, 6 + 3 * progress);
            float cx = radius + (w - 2 * radius) * progress;
            canvas.drawCircle(cx, h / 2, knobRadius, paint);
        }
    }

    /** Progress ring around a gamepad button badge, for hold-to-confirm actions. */
    static class RingView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float progress;

        RingView(Context context, int color) {
            super(context);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(dp(this, 2.5f));
            paint.setColor(color);
        }

        void setProgress(float progress) {
            this.progress = progress;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (progress <= 0) {
                return;
            }
            float inset = paint.getStrokeWidth() / 2;
            rect.set(inset, inset, getWidth() - inset, getHeight() - inset);
            canvas.drawArc(rect, -90, 360 * progress, false, paint);
        }
    }

    /** Horizontal battery icon whose fill follows the charge level. */
    static class BatteryView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private int level = 100;
        private int outlineColor, fillColor;

        BatteryView(Context context) {
            super(context);
        }

        void setState(int level, int outlineColor, int fillColor) {
            this.level = Math.max(0, Math.min(100, level));
            this.outlineColor = outlineColor;
            this.fillColor = fillColor;
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension((int) dp(this, 24), (int) dp(this, 24));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float u = getWidth() / 24f;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f * u);
            paint.setColor(outlineColor);
            rect.set(2 * u, 7 * u, 20 * u, 17 * u);
            canvas.drawRoundRect(rect, 2.5f * u, 2.5f * u, paint);

            paint.setStyle(Paint.Style.FILL);
            rect.set(20.5f * u, 10 * u, 22.3f * u, 14 * u);
            canvas.drawRoundRect(rect, 0.8f * u, 0.8f * u, paint);

            paint.setColor(fillColor);
            rect.set(4 * u, 9 * u, (4 + 14 * level / 100f) * u, 15 * u);
            canvas.drawRoundRect(rect, u, u, paint);
        }
    }

    /** Wi-Fi fan icon showing the signal level from 0 to 4. */
    static class WifiView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path fan = new Path();
        private final Path clip = new Path();
        private int level = 4;
        private int color;

        WifiView(Context context) {
            super(context);
        }

        void setState(int level, int color) {
            this.level = Math.max(0, Math.min(4, level));
            this.color = color;
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension((int) dp(this, 18), (int) dp(this, 18));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            float cx = w / 2, cy = h * 0.92f;
            float radius = h * 0.86f;

            fan.reset();
            fan.moveTo(cx, cy);
            fan.arcTo(new RectF(cx - radius, cy - radius, cx + radius, cy + radius), 225, 90);
            fan.close();

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            paint.setAlpha(Math.round(Color.alpha(color) * 0.3f));
            canvas.drawPath(fan, paint);

            if (level > 0) {
                float levelRadius = radius * (0.25f + 0.75f * level / 4f);
                clip.reset();
                clip.addCircle(cx, cy, levelRadius, Path.Direction.CW);
                canvas.save();
                canvas.clipPath(clip);
                paint.setColor(color);
                canvas.drawPath(fan, paint);
                canvas.restore();
            }
        }
    }
}
