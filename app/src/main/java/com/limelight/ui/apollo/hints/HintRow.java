package com.limelight.ui.apollo.hints;

import android.content.Context;
import android.hardware.input.InputManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.ui.apollo.ApolloUi;
import com.limelight.ui.theme.ApolloColors;
import com.limelight.ui.theme.ApolloMotion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A row of gamepad hints ("A Start  Y Options ..."), for the view that has the focus inside its scope.
 * It shows whenever a gamepad is connected, also while the screen is touched, and hides without one.
 * Views give their hints with {@link #set}; the row also takes fixed hints for its whole scope.
 */
public class HintRow extends LinearLayout implements InputMode.Listener {
    public static final class Hint {
        public final int key;
        final String label;

        public Hint(int key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    // The rows on screen, so a change of what a button does on the focused view can show at once
    private static final java.util.Set<HintRow> ATTACHED = Collections.newSetFromMap(new java.util.WeakHashMap<>());

    /** Rereads the hints of every row on screen: the focused view kept the focus but its buttons changed. */
    public static void refreshAll() {
        for (HintRow row : new ArrayList<>(ATTACHED)) {
            row.refresh();
        }
    }

    private final ApolloColors colors;
    private ViewGroup scope;
    private List<Hint> fallback = Collections.emptyList();
    private List<Hint> shown;
    private boolean visible;

    private final ViewTreeObserver.OnGlobalFocusChangeListener focusListener = (oldFocus, newFocus) -> refresh();

    // A gamepad connected or disconnected shows or hides the row
    private final InputManager.InputDeviceListener deviceListener = new InputManager.InputDeviceListener() {
        @Override
        public void onInputDeviceAdded(int deviceId) {
            updateVisibility(true);
        }

        @Override
        public void onInputDeviceRemoved(int deviceId) {
            updateVisibility(true);
        }

        @Override
        public void onInputDeviceChanged(int deviceId) {
            updateVisibility(true);
        }
    };

    public HintRow(Context context, ApolloColors colors, int gravity) {
        super(context);
        this.colors = colors;
        setOrientation(HORIZONTAL);
        setGravity(gravity | Gravity.CENTER_VERTICAL);
        setAlpha(0f);
        setVisibility(GONE);
    }

    /** The hints of a focusable view, or of a group for the views inside it. */
    public static void set(View view, Hint... hints) {
        view.setTag(R.id.apollo_hints, Arrays.asList(hints));
    }

    /** Same as {@link #set}, from pairs of a key and a label resource: set(view, BUTTON_A, R.string.x, ...) */
    public static void set(View view, int... keysAndLabels) {
        Hint[] hints = new Hint[keysAndLabels.length / 2];
        for (int i = 0; i < hints.length; i++) {
            hints[i] = hint(view.getContext(), keysAndLabels[i * 2], keysAndLabels[i * 2 + 1]);
        }
        set(view, hints);
    }

    public static Hint hint(Context context, int key, int labelRes) {
        return new Hint(key, context.getString(labelRes));
    }

    /** Only the focus inside this group drives the row; null for the whole window. */
    public void setScope(ViewGroup scope) {
        this.scope = scope;
        refresh();
    }

    /** Shown when the focused view has no hints of its own, or nothing has the focus. */
    public void setFallback(Hint... hints) {
        this.fallback = Arrays.asList(hints);
        refresh();
    }

    // Android leaves touch mode on the first key and keeps that key to itself, so the mode is the signal
    private final ViewTreeObserver.OnTouchModeChangeListener touchModeListener =
            inTouchMode -> InputMode.onTouchModeChanged(inTouchMode);

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ATTACHED.add(this);
        InputMode.addListener(this);
        getViewTreeObserver().addOnGlobalFocusChangeListener(focusListener);
        getViewTreeObserver().addOnTouchModeChangeListener(touchModeListener);
        // Opened with a gamepad, the window starts out of touch mode
        InputMode.onTouchModeChanged(isInTouchMode());
        ((InputManager) getContext().getSystemService(Context.INPUT_SERVICE)).registerInputDeviceListener(deviceListener, null);
        refresh();
        updateVisibility(false);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        ATTACHED.remove(this);
        InputMode.removeListener(this);
        ((InputManager) getContext().getSystemService(Context.INPUT_SERVICE)).unregisterInputDeviceListener(deviceListener);
        getViewTreeObserver().removeOnGlobalFocusChangeListener(focusListener);
        getViewTreeObserver().removeOnTouchModeChangeListener(touchModeListener);
    }

    @Override
    public void onInputModeChanged(boolean gamepad) {
        refresh();
        updateVisibility(true);
    }

    @SuppressWarnings("unchecked")
    private List<Hint> currentHints() {
        View root = scope != null ? scope : getRootView();
        View focused = root.findFocus();
        if (focused == null && scope != null && getRootView().findFocus() != null) {
            // The focus is in something over this scope, like a side panel with its own hints
            return Collections.emptyList();
        }
        for (View view = focused; view != null; ) {
            Object tag = view.getTag(R.id.apollo_hints);
            if (tag instanceof List) {
                return (List<Hint>) tag;
            }
            if (view == root || !(view.getParent() instanceof View)) {
                break;
            }
            view = (View) view.getParent();
        }
        return fallback;
    }

    /** Rereads the hints of the focused view, after something changed what a button does. */
    public void refresh() {
        // A touch takes the focus away: keep the hints of the last focused view instead of emptying the row
        if (!InputMode.isGamepad() && getRootView().findFocus() == null && shown != null && !shown.isEmpty()) {
            return;
        }
        List<Hint> hints = currentHints();
        if (hints.equals(shown)) {
            return;
        }
        shown = new ArrayList<>(hints);
        removeAllViews();
        for (Hint hint : hints) {
            LinearLayout item = new LinearLayout(getContext());
            item.setOrientation(HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.addView(ButtonGlyph.create(getContext(), colors, hint.key));
            TextView label = ApolloUi.text(getContext(), hint.label, 12, colors.onSurfaceVariant, false);
            label.setPadding(ApolloUi.dp(getContext(), 6), 0, 0, 0);
            item.addView(label);

            LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            if (getChildCount() > 0) {
                params.leftMargin = ApolloUi.dp(getContext(), 16);
            }
            addView(item, params);
        }
        updateVisibility(true);
    }

    private void updateVisibility(boolean animate) {
        // Like LB/RB in the top bar: shown whenever a gamepad is connected, touch or not
        boolean connected = InputMode.isGamepadConnected();
        boolean show = connected && shown != null && !shown.isEmpty();
        // Without a gamepad the row gives its height back to the screen content (touch-only phones); with one
        // it keeps its place while it has nothing to say (the focus in a panel or menu over it), or the
        // screen under it would grow and shrink
        int hiddenVisibility = connected ? INVISIBLE : GONE;
        if (show == visible && animate && (show || getVisibility() == hiddenVisibility)) {
            return;
        }
        visible = show;
        animate().cancel();
        if (show) {
            setVisibility(VISIBLE);
        }
        if (animate && getVisibility() == VISIBLE) {
            animate().alpha(show ? 1f : 0f).setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD)
                    .withEndAction(show ? null : () -> setVisibility(visible ? VISIBLE : hiddenVisibility)).start();
        } else {
            setAlpha(show ? 1f : 0f);
            setVisibility(show ? VISIBLE : hiddenVisibility);
        }
    }
}
