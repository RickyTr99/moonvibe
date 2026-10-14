package com.limelight.setup;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

import com.limelight.ui.theme.ApolloColors;

/**
 * The app played on its real views: the whole screen of the device at its real size, scaled down onto a
 * handheld drawn in lines. A timeline, played once a moment after the view shows, presses the buttons of the
 * handheld (they light up) and sends the same input to the screen; then it stays on its last frame. The view
 * can zoom on a part of the screen, to show what just opened.
 */
abstract class HandheldIllustration extends FrameLayout {
    // The handheld in units: the screen 150 wide between two grips 70 wide, a border of 16 above and below,
    // the bumpers over the top edge, room under it for the sound of the speakers
    private static final float GRIP = 70;
    private static final float SCREEN_UNITS = 150;
    private static final float BORDER = 16;
    private static final float BUMPERS = 4.6f;
    // The bumpers: thin bands that follow the rounded top corners of the body, as on the AYN Odin
    private static final float BUMPER_GAP = 0.8f;
    private static final float BUMPER_THICKNESS = 3.6f;
    private static final float BUMPER_SPAN = 48;
    private static final float SOUND_ROOM = 8;
    // The handheld takes this much of the frame, with room around it
    private static final float CONSOLE_SIZE = 0.98f;
    // A button press: down in this long, held, then up
    private static final long PRESS_DOWN_MS = 60;
    private static final long PRESS_HELD_MS = 140;
    private static final long PRESS_UP_MS = 90;
    // A zoom fills this much of the view at most, and goes no closer than this
    private static final float ZOOM_FILL = 0.88f;
    private static final float MAX_ZOOM = 2.2f;
    // A slow camera: it eases in and out, and the closer it gets the slower it seems to go
    private static final long ZOOM_IN_MS = 1200;
    private static final long ZOOM_OUT_MS = 1000;
    private static final Interpolator CAMERA = new PathInterpolator(0.45f, 0f, 0.3f, 1f);
    // Zoomed in, the edges of the view fade out over this much of its shorter side
    private static final float EDGE_FADE = 0.12f;
    // One wave of sound leaving a speaker
    private static final long WAVE_MS = 900;

    protected final Activity activity;
    protected final ApolloColors colors;
    protected final int screenWidth;
    protected final int screenHeight;
    private final float bodyHeight;
    protected final ConsoleView console;
    /** The screen of the device, at its real size: the views of the illustration go in it. */
    protected final FrameLayout screen;
    // The handheld and its screen together, what a zoom moves
    private final FrameLayout stage;

    private long startMs;
    private float lastT = -1;
    private boolean running;
    private boolean finished;
    // Paused at this moment, -1 while playing
    private long pausedAt = -1;
    // One frame waiting at a time, however often the timeline is held and let go
    private final Runnable frameCallback = this::frame;
    // The camera: how close it is and the point of the stage in the middle of the view (-1 for the middle)
    private float zoom = 1;
    private float focusX = -1;
    private float focusY = -1;
    private ValueAnimator camera;
    private final Paint edgePaint = new Paint();

    HandheldIllustration(Activity activity, ApolloColors colors) {
        super(activity);
        this.activity = activity;
        this.colors = colors;
        Point size = new Point();
        activity.getWindowManager().getDefaultDisplay().getRealSize(size);
        screenWidth = Math.max(size.x, size.y);
        screenHeight = Math.min(size.x, size.y);
        bodyHeight = SCREEN_UNITS * screenHeight / screenWidth + 2 * BORDER;
        setClipChildren(true);
        edgePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

        stage = new FrameLayout(activity);
        stage.setClipChildren(false);
        stage.setPivotX(0);
        stage.setPivotY(0);
        addView(stage, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        console = new ConsoleView(activity);
        stage.addView(console, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        screen = new FrameLayout(activity);
        screen.setPivotX(0);
        screen.setPivotY(0);
        // Nothing of the app spills onto the border
        screen.setOutlineProvider(ViewOutlineProvider.BOUNDS);
        screen.setClipToOutline(true);
        stage.addView(screen, new LayoutParams(screenWidth, screenHeight));
    }

    /** How long the timeline lasts. */
    protected abstract long durationMs();

    /** How long the screen waits as it is before the timeline starts. */
    protected abstract long startDelayMs();

    /** The timeline at t (0 to 1): sets the buttons of the console and sends what they do to the screen. */
    protected abstract void onFrame(float t);

    /** Back at the start of the timeline: the screen as it was at the start. */
    protected abstract void onRestart();

    /** Off the screen: stop what the timeline left going. */
    protected void onStop() {
    }

    /** The timeline has played to its end. */
    protected void onFinished() {
    }

    boolean isFinished() {
        return finished;
    }

    boolean isPaused() {
        return pausedAt >= 0;
    }

    /** Holds the timeline, and the camera with it, where it is. */
    void pause() {
        if (finished || pausedAt >= 0) {
            return;
        }
        pausedAt = SystemClock.uptimeMillis();
        if (camera != null) {
            camera.pause();
        }
        onPaused();
    }

    /** Goes on from where it was held. */
    void resume() {
        if (pausedAt < 0) {
            return;
        }
        if (startMs >= 0) {
            startMs += SystemClock.uptimeMillis() - pausedAt;
        }
        pausedAt = -1;
        if (camera != null) {
            camera.resume();
        }
        if (running) {
            scheduleFrame();
        }
    }

    /** Held: stop what keeps moving by itself. */
    protected void onPaused() {
    }

    /** From the start again, after the same wait. */
    void replay() {
        startMs = -1;
        pausedAt = -1;
        boolean stopped = finished;
        finished = false;
        if (running && stopped) {
            scheduleFrame();
        }
    }

    // Only to look at: touches stay out of the screen
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return true;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        float unit = console.unit();
        float screenScale = SCREEN_UNITS * unit / screenWidth;
        screen.layout(0, 0, screenWidth, screenHeight);
        screen.setTranslationX(console.originX() + GRIP * unit);
        screen.setTranslationY(console.originY() + (BUMPERS + BORDER) * unit);
        screen.setScaleX(screenScale);
        screen.setScaleY(screenScale);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        // The timeline starts at the first frame, once the views of the screen are attached too
        startMs = -1;
        finished = false;
        pausedAt = -1;
        scheduleFrame();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        running = false;
        resetCamera();
        onStop();
    }

    private void scheduleFrame() {
        removeCallbacks(frameCallback);
        postOnAnimation(frameCallback);
    }

    private void frame() {
        if (!running || finished || pausedAt >= 0) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (startMs < 0) {
            startMs = now + startDelayMs();
            lastT = -1;
            resetCamera();
            onRestart();
        }
        float t = Math.max(0, now - startMs) / (float) durationMs();
        console.release();
        onFrame(Math.min(t, 1));
        console.invalidate();
        lastT = Math.min(t, 1);
        if (t >= 1) {
            finished = true;
            onFinished();
            return;
        }
        scheduleFrame();
    }

    // --- Zoom

    /** Brings this part of the screen (in its layout coordinates) to the middle of the view, as large as fits. */
    void zoomTo(Rect part) {
        float scale = SCREEN_UNITS * console.unit() / screenWidth;
        float width = part.width() * scale;
        float height = part.height() * scale;
        if (width <= 0 || height <= 0) {
            return;
        }
        float target = Math.max(1, Math.min(MAX_ZOOM, ZOOM_FILL * Math.min(getWidth() / width, getHeight() / height)));
        moveCamera(target, screen.getTranslationX() + part.exactCenterX() * scale,
                screen.getTranslationY() + part.exactCenterY() * scale, ZOOM_IN_MS);
    }

    /** Where a view of the screen is, in the screen's layout coordinates. */
    Rect boundsOf(View view) {
        Rect bounds = new Rect(0, 0, view.getWidth(), view.getHeight());
        screen.offsetDescendantRectToMyCoords(view, bounds);
        return bounds;
    }

    /** The whole handheld again. */
    void zoomOut() {
        moveCamera(1, getWidth() / 2f, getHeight() / 2f, ZOOM_OUT_MS);
    }

    // The point in the middle moves straight, the closeness changes by the same ratio each moment: a steady
    // camera, without the swing of scale and position moving each on its own
    private void moveCamera(float toZoom, float toX, float toY, long duration) {
        if (camera != null) {
            camera.cancel();
        }
        float fromZoom = zoom;
        float fromX = focusX < 0 ? getWidth() / 2f : focusX;
        float fromY = focusY < 0 ? getHeight() / 2f : focusY;
        camera = ValueAnimator.ofFloat(0f, 1f);
        camera.setDuration(duration);
        camera.setInterpolator(CAMERA);
        camera.addUpdateListener(a -> {
            float p = (float) a.getAnimatedValue();
            zoom = (float) (fromZoom * Math.pow(toZoom / fromZoom, p));
            focusX = fromX + (toX - fromX) * p;
            focusY = fromY + (toY - fromY) * p;
            applyCamera();
        });
        camera.start();
    }

    private void resetCamera() {
        if (camera != null) {
            camera.cancel();
            camera = null;
        }
        zoom = 1;
        focusX = focusY = -1;
        applyCamera();
    }

    private void applyCamera() {
        float x = focusX < 0 ? getWidth() / 2f : focusX;
        float y = focusY < 0 ? getHeight() / 2f : focusY;
        stage.setScaleX(zoom);
        stage.setScaleY(zoom);
        stage.setTranslationX(getWidth() / 2f - zoom * x);
        stage.setTranslationY(getHeight() / 2f - zoom * y);
        invalidate();
    }

    // Zoomed in, the handheld cut by the edges of the view fades out instead of ending sharp
    @Override
    protected void dispatchDraw(Canvas canvas) {
        float amount = Math.min(1, (zoom - 1) / 0.25f);
        int width = getWidth();
        int height = getHeight();
        if (amount <= 0.01f || width == 0 || height == 0) {
            super.dispatchDraw(canvas);
            return;
        }
        int layer = canvas.saveLayer(0, 0, width, height, null);
        super.dispatchDraw(canvas);
        float edge = EDGE_FADE * Math.min(width, height) * amount;
        fadeEdge(canvas, 0, 0, 0, edge, 0, 0, width, edge);
        fadeEdge(canvas, 0, height, 0, height - edge, 0, height - edge, width, height);
        fadeEdge(canvas, 0, 0, edge, 0, 0, 0, edge, height);
        fadeEdge(canvas, width, 0, width - edge, 0, width - edge, 0, width, height);
        canvas.restoreToCount(layer);
    }

    // Erases from fully at (x0, y0) to nothing at (x1, y1), over the given rectangle
    private void fadeEdge(Canvas canvas, float x0, float y0, float x1, float y1, float l, float t, float r, float b) {
        edgePaint.setShader(new LinearGradient(x0, y0, x1, y1, Color.BLACK, Color.TRANSPARENT, android.graphics.Shader.TileMode.CLAMP));
        canvas.drawRect(l, t, r, b, edgePaint);
    }

    // --- Timeline

    static float seg(float t, float a, float b) {
        if (t <= a) {
            return 0;
        }
        if (t >= b) {
            return 1;
        }
        float x = (t - a) / (b - a);
        return x * x * (3 - 2 * x);
    }

    static float pulse(float t, float a, float b, float c, float d) {
        return Math.min(seg(t, a, b), 1 - seg(t, c, d));
    }

    /** Whether the moment at has come since the last frame. */
    boolean reached(float t, float at) {
        return lastT < at && t >= at;
    }

    /** How far down a button pressed at the moment at is: it goes down just before, then comes back up. */
    float press(float t, float at) {
        float loop = durationMs();
        float held = at + PRESS_HELD_MS / loop;
        return pulse(t, at - PRESS_DOWN_MS / loop, at, held, held + PRESS_UP_MS / loop);
    }

    /** The same for a button pressed at several moments. */
    float press(float t, float... at) {
        float down = 0;
        for (float moment : at) {
            down = Math.max(down, press(t, moment));
        }
        return down;
    }

    /**
     * The handheld, drawn in lines: body, bumpers, the screen's border (the screen itself is the app on it),
     * left stick and D-pad on the left grip, face buttons and the right stick on the right one, Select and
     * Start. Each button lights up in the main color while it is pressed (0 up, 1 down).
     */
    final class ConsoleView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        // The left bumper, for the body it was made for; the right one is its mirror
        private final Path bumperPath = new Path();
        private float bumperFor;
        float select, selectRing, start;
        float a, b, x, y, lb, rb;
        float up, down, left, right;
        float leftDx, leftLit, rightDx, rightLit;
        // How loud the speakers play, 0 for silence
        float sound;

        ConsoleView(Activity activity) {
            super(activity);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeCap(Paint.Cap.ROUND);
        }

        // Every button up, before the timeline presses some again
        void release() {
            select = selectRing = start = 0;
            a = b = x = y = lb = rb = 0;
            up = down = left = right = 0;
            leftDx = leftLit = rightDx = rightLit = 0;
            sound = 0;
        }

        float width() {
            return 2 * GRIP + SCREEN_UNITS;
        }

        private float height() {
            return BUMPERS + bodyHeight + SOUND_ROOM;
        }

        float unit() {
            return CONSOLE_SIZE * Math.min(getWidth() / width(), getHeight() / height());
        }

        float originX() {
            return (getWidth() - width() * unit()) / 2;
        }

        float originY() {
            return (getHeight() - height() * unit()) / 2;
        }

        private int lit(int color, float amount) {
            return ApolloColors.blend(color, colors.primary, amount);
        }

        private void round(Canvas c, float l, float t, float r, float b, float radius, int color) {
            fill.setColor(color);
            rect.set(l, t, r, b);
            c.drawRoundRect(rect, radius, radius, fill);
        }

        private void stroke(Canvas c, float l, float t, float r, float b, float radius, int color, float width) {
            line.setColor(color);
            line.setStrokeWidth(width);
            rect.set(l, t, r, b);
            c.drawRoundRect(rect, radius, radius, line);
        }

        @Override
        protected void onDraw(Canvas c) {
            float unit = unit();
            if (unit <= 0) {
                return;
            }
            c.save();
            c.translate(originX(), originY());
            c.scale(unit, unit);
            float w = width();
            // The bumpers hug the top corners, a little lower while pressed
            bumper(c, false, lb);
            bumper(c, true, rb);

            c.translate(0, BUMPERS);
            float h = bodyHeight;
            round(c, 0, 0, w, h, h * 0.38f, colors.surfaceContainer);
            stroke(c, 0, 0, w, h, h * 0.38f, colors.outlineVariant, 1.6f);
            // The border of the screen
            round(c, GRIP - 5, BORDER - 5, GRIP + SCREEN_UNITS + 5, h - BORDER + 5, 6, 0xFF0B0C0F);
            stroke(c, GRIP - 5, BORDER - 5, GRIP + SCREEN_UNITS + 5, h - BORDER + 5, 6, colors.outlineVariant, 1.2f);

            float cx = GRIP / 2;
            stick(c, cx, h * 0.32f, leftDx, leftLit);
            dpad(c, cx, h * 0.70f);
            // Select and Start at the top, next to the screen, as on the AYN Odin
            button(c, GRIP - 16, 12, select, selectRing);
            speaker(c, cx, h - 10);

            float rx = w - GRIP / 2;
            float fy = h * 0.32f;
            face(c, rx, fy - 10, y);
            face(c, rx - 10, fy, x);
            face(c, rx + 10, fy, b);
            face(c, rx, fy + 10, a);
            stick(c, rx, h * 0.70f, rightDx, rightLit);
            button(c, w - GRIP + 16, 12, start, 0);
            speaker(c, rx, h - 10);
            c.restore();
        }

        // The holes of a speaker under the controls, and while it plays waves leaving it, wider and stronger
        // the louder it is
        private void speaker(Canvas c, float x, float y) {
            fill.setColor(colors.outlineVariant);
            for (int row = 0; row < 2; row++) {
                for (int i = -2; i <= 2; i++) {
                    c.drawCircle(x + i * 4 + (row == 0 ? 0 : 2), y - 2 + row * 4, 0.9f, fill);
                }
            }
            if (sound <= 0) {
                return;
            }
            line.setStrokeWidth(1.4f);
            long now = SystemClock.uptimeMillis();
            for (int wave = 0; wave < 3; wave++) {
                float phase = ((now % WAVE_MS) / (float) WAVE_MS + wave / 3f) % 1f;
                float radius = 5 + phase * (5 + 9 * sound);
                line.setColor(alpha(colors.primary, sound * (1 - phase)));
                rect.set(x - radius, y - radius, x + radius, y + radius);
                c.drawArc(rect, 40, 100, false, line);
            }
        }

        private void bumper(Canvas c, boolean right, float pressed) {
            float w = width();
            float h = bodyHeight;
            if (bumperFor != h) {
                bumperFor = h;
                buildBumper(h);
            }
            c.save();
            c.translate(0, BUMPERS + 0.8f * pressed);
            if (right) {
                c.scale(-1, 1, w / 2, 0);
            }
            fill.setColor(lit(colors.surfaceContainerHighest, pressed));
            c.drawPath(bumperPath, fill);
            c.restore();
        }

        // A band just off the body's outline around its top left corner, from along the top down into the curve,
        // with rounded ends; in body units, the body's top at 0
        private void buildBumper(float h) {
            float radius = h * 0.38f;
            float inner = BUMPER_GAP;
            float outer = BUMPER_GAP + BUMPER_THICKNESS;
            Path band = new Path();
            band.addRoundRect(-outer, -outer, width() + outer, h + outer, radius + outer, radius + outer, Path.Direction.CW);
            Path body = new Path();
            body.addRoundRect(-inner, -inner, width() + inner, h + inner, radius + inner, radius + inner, Path.Direction.CW);
            band.op(body, Path.Op.DIFFERENCE);
            Path reach = new Path();
            reach.addRoundRect(-outer - 1, -outer - 1, BUMPER_SPAN, radius * 0.6f, BUMPER_THICKNESS, BUMPER_THICKNESS,
                    Path.Direction.CW);
            band.op(reach, Path.Op.INTERSECT);
            bumperPath.set(band);
        }

        private void stick(Canvas c, float x, float y, float dx, float lit) {
            if (lit > 0) {
                fill.setColor(alpha(colors.primary, 0.18f * lit));
                c.drawCircle(x, y, 22, fill);
            }
            line.setColor(lit(colors.outlineVariant, lit));
            line.setStrokeWidth(2);
            c.drawCircle(x, y, 15, line);
            fill.setColor(colors.surfaceContainerHighest);
            c.drawCircle(x + dx, y, 9, fill);
            line.setColor(colors.outline);
            line.setStrokeWidth(1.4f);
            c.drawCircle(x + dx, y, 9, line);
        }

        // The cross, each arm lit on its own
        private void dpad(Canvas c, float x, float y) {
            line.setStrokeWidth(5);
            arm(c, x, y, 0, -1, up);
            arm(c, x, y, 0, 1, down);
            arm(c, x, y, -1, 0, left);
            arm(c, x, y, 1, 0, right);
        }

        private void arm(Canvas c, float x, float y, float dx, float dy, float pressed) {
            line.setColor(lit(colors.outlineVariant, pressed));
            c.drawLine(x + dx * 2, y + dy * 2, x + dx * 9, y + dy * 9, line);
        }

        private void face(Canvas c, float x, float y, float pressed) {
            if (pressed > 0) {
                fill.setColor(alpha(colors.primary, 0.25f * pressed));
                c.drawCircle(x, y, 7, fill);
            }
            fill.setColor(lit(colors.outlineVariant, pressed));
            c.drawCircle(x, y, 4, fill);
        }

        // Select or Start, with a ring that fills while it is held
        private void button(Canvas c, float x, float y, float held, float ring) {
            round(c, x - 6, y - 3, x + 6, y + 3, 3, lit(colors.surfaceContainerHighest, held));
            if (ring > 0) {
                line.setColor(colors.primary);
                line.setStrokeWidth(2);
                rect.set(x - 9, y - 9, x + 9, y + 9);
                c.drawArc(rect, -90, 360 * ring, false, line);
            }
        }
    }

    static int alpha(int color, float amount) {
        int a = Math.round(Color.alpha(color) * Math.max(0, Math.min(1, amount)));
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
