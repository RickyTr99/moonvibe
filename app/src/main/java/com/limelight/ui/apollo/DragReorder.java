package com.limelight.ui.apollo;

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.limelight.ui.theme.ApolloMotion;

/**
 * Reordering the rows of a vertical list by dragging, from a handle (at once) or from the whole row
 * (after holding it). The dragged row follows the finger and the rows it passes slide out of its way;
 * the list only changes order when the finger lifts. Taking the row out of the list while the finger is on it
 * would cancel the touch, so nothing moves in the list until then.
 */
public final class DragReorder {
    public interface Listener {
        /** The row was dropped at a new place: the list already has the new order. */
        void onReordered();
    }

    // Near the top or bottom of the scrolling list, it scrolls by itself
    private static final int EDGE_DP = 48;
    private static final int SCROLL_STEP_DP = 6;

    private final LinearLayout box;
    // The rows that move are box's children from here to the end: the ones before (a header) stay
    private final int firstRow;
    private final Listener listener;

    private View row;
    private int from;
    private int target;
    private float startRawY;
    private float lastRawY;
    private int startScroll;

    public DragReorder(LinearLayout box, int firstRow, Listener listener) {
        this.box = box;
        this.firstRow = firstRow;
        this.listener = listener;
    }

    public boolean isDragging() {
        return row != null;
    }

    /** A handle: touching it starts dragging its row. */
    @SuppressLint("ClickableViewAccessibility")
    public void attachHandle(View handle, View row) {
        handle.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    start(row, event.getRawY());
                    return true;
                case MotionEvent.ACTION_MOVE:
                    move(event.getRawY());
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    drop();
                    return true;
            }
            return false;
        });
    }

    /** The whole row: held still for a moment, then dragged. A short tap stays a tap. */
    @SuppressLint("ClickableViewAccessibility")
    public void attachLongPress(View row) {
        int slop = ViewConfiguration.get(row.getContext()).getScaledTouchSlop();
        long timeout = ViewConfiguration.getLongPressTimeout();
        float[] down = new float[2];
        Runnable[] pending = new Runnable[1];
        row.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    down[0] = event.getRawX();
                    down[1] = event.getRawY();
                    lastRawY = event.getRawY();
                    pending[0] = () -> {
                        pending[0] = null;
                        // The row stops being pressed: no tap on release
                        MotionEvent cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                        row.onTouchEvent(cancel);
                        cancel.recycle();
                        row.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                        start(row, lastRawY);
                    };
                    row.postDelayed(pending[0], timeout);
                    return false;
                }
                case MotionEvent.ACTION_MOVE:
                    lastRawY = event.getRawY();
                    if (this.row == row) {
                        move(event.getRawY());
                        return true;
                    }
                    if (pending[0] != null && (Math.abs(event.getRawX() - down[0]) > slop
                            || Math.abs(event.getRawY() - down[1]) > slop)) {
                        // Scrolling or a swipe, not a hold
                        row.removeCallbacks(pending[0]);
                        pending[0] = null;
                    }
                    return false;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (pending[0] != null) {
                        row.removeCallbacks(pending[0]);
                        pending[0] = null;
                    }
                    if (this.row == row) {
                        drop();
                        return true;
                    }
                    return false;
            }
            return false;
        });
    }

    private ScrollView scrollView() {
        ViewParent parent = box.getParent();
        while (parent != null && !(parent instanceof ScrollView)) {
            parent = parent.getParent();
        }
        return (ScrollView) parent;
    }

    private void start(View dragged, float rawY) {
        if (row != null) {
            return;
        }
        row = dragged;
        from = box.indexOfChild(dragged);
        target = from;
        startRawY = rawY;
        lastRawY = rawY;
        ScrollView scroll = scrollView();
        startScroll = scroll != null ? scroll.getScrollY() : 0;
        dragged.getParent().requestDisallowInterceptTouchEvent(true);
        dragged.setActivated(true);
        // Above the other rows while it passes them, without a shadow
        dragged.setOutlineProvider(null);
        dragged.setTranslationZ(1);
        dragged.postOnAnimation(autoScroll);
    }

    private void move(float rawY) {
        if (row == null) {
            return;
        }
        lastRawY = rawY;
        ScrollView scroll = scrollView();
        int scrolled = scroll != null ? scroll.getScrollY() - startScroll : 0;
        float dy = rawY - startRawY + scrolled;

        // Not past the first and last row
        View first = box.getChildAt(firstRow);
        View last = box.getChildAt(box.getChildCount() - 1);
        dy = Math.max(first.getTop() - row.getTop(), Math.min(last.getBottom() - row.getBottom(), dy));
        row.setTranslationY(dy);

        // The place under its middle
        float middle = row.getTop() + dy + row.getHeight() / 2f;
        int newTarget = from;
        for (int i = firstRow; i < box.getChildCount(); i++) {
            View other = box.getChildAt(i);
            if (other == row) {
                continue;
            }
            float otherMiddle = other.getTop() + other.getHeight() / 2f;
            if (i > from && middle > otherMiddle) {
                newTarget = i;
            } else if (i < from && middle < otherMiddle && newTarget == from) {
                newTarget = i;
            }
        }
        if (newTarget != target) {
            target = newTarget;
            // The rows between the old and the new place slide by one row
            for (int i = firstRow; i < box.getChildCount(); i++) {
                View other = box.getChildAt(i);
                if (other == row) {
                    continue;
                }
                float shift = 0;
                if (from < target && i > from && i <= target) {
                    shift = -pitch(row);
                } else if (target < from && i >= target && i < from) {
                    shift = pitch(row);
                }
                if (other.getTranslationY() != shift) {
                    other.animate().translationY(shift).setDuration(ApolloMotion.SHORT)
                            .setInterpolator(ApolloMotion.STANDARD).start();
                }
            }
        }
    }

    // The room a row takes in the list, with its margins
    private static int pitch(View view) {
        int pitch = view.getHeight();
        if (view.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams params = (android.view.ViewGroup.MarginLayoutParams) view.getLayoutParams();
            pitch += params.topMargin + params.bottomMargin;
        }
        return pitch;
    }

    // While the finger is near the edge of the scrolling list, it scrolls
    private final Runnable autoScroll = new Runnable() {
        @Override
        public void run() {
            if (row == null) {
                return;
            }
            ScrollView scroll = scrollView();
            if (scroll != null) {
                int[] location = new int[2];
                scroll.getLocationOnScreen(location);
                float y = lastRawY - location[1];
                int edge = ApolloUi.dp(scroll.getContext(), EDGE_DP);
                int step = ApolloUi.dp(scroll.getContext(), SCROLL_STEP_DP);
                int before = scroll.getScrollY();
                if (y < edge) {
                    scroll.scrollBy(0, -step);
                } else if (y > scroll.getHeight() - edge) {
                    scroll.scrollBy(0, step);
                }
                if (scroll.getScrollY() != before) {
                    move(lastRawY);
                }
            }
            row.postOnAnimation(this);
        }
    };

    private void drop() {
        if (row == null) {
            return;
        }
        View dropped = row;
        row = null;
        dropped.removeCallbacks(autoScroll);
        int to = target;
        // Where it shows now, to slide from there into its new place
        float shownTop = dropped.getTop() + dropped.getTranslationY();

        Runnable settle = () -> {
            dropped.setActivated(false);
            dropped.setTranslationZ(0);
            dropped.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        };
        if (to == from) {
            dropped.animate().translationY(0).setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD)
                    .withEndAction(settle).start();
            return;
        }

        // After this touch: the list changes order and every row is where it was drawn
        int newTop = to > from ? box.getChildAt(to).getBottom() - dropped.getHeight() : box.getChildAt(to).getTop();
        dropped.post(() -> {
            boolean focused = dropped.hasFocus();
            box.removeView(dropped);
            box.addView(dropped, to);
            for (int i = firstRow; i < box.getChildCount(); i++) {
                View other = box.getChildAt(i);
                other.animate().cancel();
                other.setTranslationY(0);
            }
            dropped.setTranslationY(shownTop - newTop);
            dropped.animate().translationY(0).setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD)
                    .withEndAction(settle).start();
            if (focused) {
                dropped.requestFocus();
            }
            listener.onReordered();
        });
    }

    /** One place up or down with the D-pad; the neighbor slides into the place left. */
    public void step(View moving, int direction) {
        int index = box.indexOfChild(moving);
        int to = index + direction;
        if (to < firstRow || to >= box.getChildCount()) {
            return;
        }
        View neighbor = box.getChildAt(to);
        int distance = direction > 0 ? neighbor.getHeight() : -neighbor.getHeight();
        box.removeView(moving);
        box.addView(moving, to);
        moving.requestFocus();
        moving.setTranslationY(-distance);
        moving.animate().translationY(0).setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD).start();
        neighbor.setTranslationY(distance > 0 ? moving.getHeight() : -moving.getHeight());
        neighbor.animate().translationY(0).setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD).start();
    }
}
