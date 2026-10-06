package com.limelight.ui.apollo.settings;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import com.limelight.R;
import com.limelight.ui.apollo.ApolloUi;
import com.limelight.ui.apollo.ScreenLayer;
import com.limelight.ui.apollo.hints.ButtonGlyph;
import com.limelight.ui.apollo.hints.HintRow;
import com.limelight.ui.apollo.hints.InputMode;
import com.limelight.ui.theme.ApolloColors;
import com.limelight.ui.theme.ApolloMotion;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * A value typed by hand for a slider setting, also past the end of the slider (the bitrate): − and +
 * (LB and RB), a row of shortcuts (left and right), the number itself for the keyboard (X), A to set it.
 * B, back or a tap outside close it without a change. The bumpers show only with a gamepad connected.
 */
class ValuePopup {
    private static final int WIDTH_DP = 480;
    // The number field holds up to this many digits
    private static final int MAX_DIGITS = 4;
    // A shade lighter than the buttons beside it: the keys act on the number
    private static final int FIELD_FILL = 0xFF3A3C42;

    private final FrameLayout host;
    // The top of the window, where the veil and the card go (ScreenLayer)
    private FrameLayout layer;
    private final ViewGroup content;
    private final ApolloColors colors;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout scrim;
    private View card;
    private View anchor;
    private Object backCallback;

    // The value in the setting's own units (kbps for the bitrate); unit = one step of − and +
    private int value, min, sliderMax, unit;
    private int[] shortcuts;
    private IntConsumer onSet;
    private EditText field;
    private final List<TextView> shortcutViews = new ArrayList<>();
    private final List<View> bumpers = new ArrayList<>();
    // Set while the field is written to from here, so its watcher leaves the value alone
    private boolean writingField;

    private final InputManager.InputDeviceListener deviceListener = new InputManager.InputDeviceListener() {
        @Override
        public void onInputDeviceAdded(int deviceId) {
            updateBumpers();
        }

        @Override
        public void onInputDeviceRemoved(int deviceId) {
            updateBumpers();
        }

        @Override
        public void onInputDeviceChanged(int deviceId) {
            updateBumpers();
        }
    };

    ValuePopup(FrameLayout host, ViewGroup content, ApolloColors colors) {
        this.host = host;
        this.content = content;
        this.colors = colors;
    }

    private Context context() {
        return host.getContext();
    }

    private int dp(float value) {
        return ApolloUi.dp(context(), value);
    }

    boolean isShowing() {
        return scrim != null;
    }

    /**
     * @param unit the setting's units in one shown unit (1000 kbps in a Mbps)
     * @param sliderMax where the slider ends: above it the value shows in red
     * @param shortcuts values in the setting's units
     */
    void show(View anchor, CharSequence title, String suffix, int value, int min, int sliderMax, int unit,
              int[] shortcuts, IntConsumer onSet) {
        dismiss(false);
        this.anchor = anchor;
        this.value = value;
        this.min = min;
        this.sliderMax = sliderMax;
        this.unit = Math.max(1, unit);
        this.shortcuts = shortcuts;
        this.onSet = onSet;
        shortcutViews.clear();
        bumpers.clear();
        content.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        registerBack();
        ((InputManager) context().getSystemService(Context.INPUT_SERVICE)).registerInputDeviceListener(deviceListener, null);

        scrim = new FrameLayout(context());
        scrim.setBackgroundColor(Color.argb(128, 0, 0, 0));
        scrim.setClickable(true);
        scrim.setOnClickListener(v -> dismiss(true));
        layer = ScreenLayer.of(host);
        ScreenLayer.blockApp(host, true);
        layer.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout column = new LinearLayout(context());
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(24), dp(20), dp(24), dp(20));
        column.setBackground(ApolloUi.roundRect(colors.surfaceContainerHigh, dp(20)));
        // Takes the focus, so the gamepad keys and hints belong to the card while it is open
        column.setFocusable(true);
        column.setFocusableInTouchMode(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            column.setDefaultFocusHighlightEnabled(false);
        }
        column.setClickable(true);
        column.setOnKeyListener((v, keyCode, event) -> onKey(keyCode, event));
        HintRow.set(column, new HintRow.Hint[]{
                HintRow.hint(context(), KeyEvent.KEYCODE_BUTTON_L1, R.string.apollo_hint_less),
                HintRow.hint(context(), KeyEvent.KEYCODE_BUTTON_R1, R.string.apollo_hint_more),
                HintRow.hint(context(), ButtonGlyph.DPAD_LEFT_RIGHT, R.string.apollo_hint_shortcuts),
                HintRow.hint(context(), KeyEvent.KEYCODE_BUTTON_X, R.string.apollo_hint_write),
                HintRow.hint(context(), KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_set),
                HintRow.hint(context(), KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_cancel)});
        card = column;

        column.addView(ApolloUi.text(context(), title, 16, colors.onSurface, true));

        // − | the number | +
        LinearLayout valueRow = new LinearLayout(context());
        valueRow.setOrientation(LinearLayout.HORIZONTAL);
        valueRow.addView(stepButton(KeyEvent.KEYCODE_BUTTON_L1, R.drawable.ic_apollo_remove, -1),
                new LinearLayout.LayoutParams(dp(56), dp(64)));
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(0, dp(64), 1);
        fieldParams.leftMargin = dp(10);
        fieldParams.rightMargin = dp(10);
        valueRow.addView(numberField(suffix), fieldParams);
        valueRow.addView(stepButton(KeyEvent.KEYCODE_BUTTON_R1, R.drawable.ic_apollo_add, 1),
                new LinearLayout.LayoutParams(dp(56), dp(64)));
        LinearLayout.LayoutParams valueRowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueRowParams.topMargin = dp(16);
        column.addView(valueRow, valueRowParams);

        // The shortcuts, all as wide
        if (shortcuts != null && shortcuts.length > 0) {
            LinearLayout shortcutRow = new LinearLayout(context());
            shortcutRow.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < shortcuts.length; i++) {
                int shortcut = shortcuts[i];
                TextView view = ApolloUi.text(context(), String.valueOf(shortcut / this.unit), 13, colors.onSurfaceVariant, true);
                view.setGravity(Gravity.CENTER);
                view.setSingleLine(true);
                view.setOnClickListener(v -> setValue(shortcut));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(36), 1);
                params.leftMargin = i > 0 ? dp(8) : 0;
                shortcutRow.addView(view, params);
                shortcutViews.add(view);
            }
            LinearLayout.LayoutParams shortcutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            shortcutParams.topMargin = dp(16);
            column.addView(shortcutRow, shortcutParams);
        }

        LinearLayout buttons = new LinearLayout(context());
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        buttons.addView(button(context().getString(R.string.apollo_hint_cancel), Color.TRANSPARENT, colors.primary, v -> dismiss(true)));
        TextView set = button(context().getString(R.string.apollo_hint_set), colors.secondaryContainer, colors.onSecondaryContainer, v -> confirm());
        LinearLayout.LayoutParams setParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        setParams.leftMargin = dp(8);
        buttons.addView(set, setParams);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonsParams.topMargin = dp(18);
        column.addView(buttons, buttonsParams);

        updateBumpers();
        showValue(true);

        int width = Math.min(dp(WIDTH_DP), layer.getWidth() - dp(32));
        // Near the top, so the keyboard has room below it
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        params.topMargin = Math.max(dp(16), layer.getHeight() / 8);
        scrim.addView(column, params);

        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(ApolloMotion.SHORT).setInterpolator(ApolloMotion.STANDARD).start();
        column.setScaleX(0.92f);
        column.setScaleY(0.92f);
        column.animate().scaleX(1f).scaleY(1f).setDuration(ApolloMotion.MEDIUM)
                .setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE).start();

        column.requestFocus();
    }

    // − or +, with its bumper above the sign; held down it repeats, faster and faster
    @SuppressLint("ClickableViewAccessibility")
    private View stepButton(int bumperKey, int iconRes, int direction) {
        LinearLayout button = new LinearLayout(context());
        button.setOrientation(LinearLayout.VERTICAL);
        button.setGravity(Gravity.CENTER);
        button.setBackground(ApolloUi.ripple(ApolloUi.roundRect(colors.surfaceContainerHighest, dp(16)), dp(16)));
        button.setFocusable(false);
        button.setContentDescription(direction < 0 ? "−" : "+");

        View bumper = ButtonGlyph.create(context(), colors, bumperKey);
        LinearLayout.LayoutParams bumperParams = new LinearLayout.LayoutParams(dp(26), dp(14));
        bumperParams.bottomMargin = dp(5);
        button.addView(bumper, bumperParams);
        bumpers.add(bumper);

        ImageView icon = new ImageView(context());
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(colors.onSurface));
        button.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));

        button.setOnTouchListener(new View.OnTouchListener() {
            private int repeats;
            private final Runnable repeat = new Runnable() {
                @Override
                public void run() {
                    repeats++;
                    step(direction, speed(repeats));
                    handler.postDelayed(this, 60);
                }
            };

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        repeats = 0;
                        step(direction, 1);
                        handler.postDelayed(repeat, 400);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setPressed(false);
                        handler.removeCallbacks(repeat);
                        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                            v.performClick();
                        }
                        return true;
                }
                return false;
            }
        });
        return button;
    }

    // The number, centered with its unit after it; a tap (or X) brings the keyboard
    private View numberField(String suffix) {
        LinearLayout box = new LinearLayout(context());
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER);
        box.setBackground(ApolloUi.roundRect(FIELD_FILL, dp(16)));
        box.setOnClickListener(v -> openKeyboard());

        field = new EditText(context());
        field.setBackground(null);
        field.setPadding(0, 0, 0, 0);
        field.setTextSize(30);
        field.setTypeface(ApolloUi.text(context(), "", 30, 0, true).getTypeface());
        field.setTextColor(colors.onSurface);
        field.setGravity(Gravity.CENTER);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_DIGITS)});
        field.setCursorVisible(false);
        field.setFocusable(false);
        field.setOnClickListener(v -> openKeyboard());
        field.setOnEditorActionListener((v, actionId, event) -> {
            closeKeyboard();
            return true;
        });
        field.setOnKeyListener((v, keyCode, event) -> {
            // B or back closes the keyboard first, keeping what was typed; the other gamepad keys work as on the card
            if (keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    closeKeyboard();
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_BUTTON_A || keyCode == KeyEvent.KEYCODE_BUTTON_L1
                    || keyCode == KeyEvent.KEYCODE_BUTTON_R1 || keyCode == KeyEvent.KEYCODE_BUTTON_X) {
                return onKey(keyCode, event);
            }
            return false;
        });
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (writingField) {
                    return;
                }
                try {
                    value = Math.max(min, Integer.parseInt(s.toString()) * unit);
                } catch (NumberFormatException ignored) {
                    // Empty while typing: the last value stays
                }
                showValue(false);
            }
        });
        box.addView(field, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (suffix != null) {
            TextView unitView = ApolloUi.text(context(), suffix, 15, colors.onSurfaceVariant, false);
            LinearLayout.LayoutParams unitParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            unitParams.leftMargin = dp(6);
            box.addView(unitView, unitParams);
        }
        box.setBaselineAligned(true);
        return box;
    }

    private TextView button(CharSequence label, int fill, int textColor, View.OnClickListener listener) {
        TextView button = ApolloUi.text(context(), label, 14, textColor, true);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setMinHeight(dp(40));
        button.setPadding(dp(18), 0, dp(18), 0);
        button.setBackground(ApolloUi.ripple(ApolloUi.roundRect(fill, dp(20)), dp(20)));
        button.setFocusable(false);
        button.setOnClickListener(listener);
        return button;
    }

    // --- The value

    // One step at a time at first, then more while held, like the slider
    private static int speed(int repeats) {
        return repeats >= 25 ? 10 : repeats >= 8 ? 5 : 1;
    }

    private void step(int direction, int steps) {
        int maxValue = maxValue();
        // From a value between two units, the first step lands on a whole one
        int whole = direction > 0 ? (value / unit) * unit : ((value + unit - 1) / unit) * unit;
        setValue(Math.max(min, Math.min(maxValue, whole + direction * steps * unit)));
    }

    private int maxValue() {
        int max = 1;
        for (int i = 0; i < MAX_DIGITS; i++) {
            max *= 10;
        }
        return (max - 1) * unit;
    }

    // Left and right: the shortcut before or after the value
    private void moveShortcut(int direction) {
        if (shortcuts == null || shortcuts.length == 0) {
            return;
        }
        int target = -1;
        if (direction > 0) {
            for (int shortcut : shortcuts) {
                if (shortcut > value) {
                    target = shortcut;
                    break;
                }
            }
        } else {
            for (int i = shortcuts.length - 1; i >= 0; i--) {
                if (shortcuts[i] < value) {
                    target = shortcuts[i];
                    break;
                }
            }
        }
        if (target >= 0) {
            setValue(target);
        }
    }

    private void setValue(int newValue) {
        value = newValue;
        showValue(true);
    }

    // The number (red past the slider) and the shortcut it matches
    private void showValue(boolean writeField) {
        boolean over = value > sliderMax;
        if (writeField) {
            writingField = true;
            String text = String.valueOf(value / unit);
            field.setText(text);
            field.setSelection(text.length());
            writingField = false;
        }
        field.setTextColor(over ? SliderView.OVER_RANGE_TEXT : colors.onSurface);
        for (int i = 0; i < shortcutViews.size(); i++) {
            TextView view = shortcutViews.get(i);
            boolean chosen = shortcuts[i] == value;
            boolean shortcutOver = shortcuts[i] > sliderMax;
            int fill = chosen ? (shortcutOver ? SliderView.OVER_RANGE_FILL : colors.secondaryContainer) : colors.surfaceContainerHighest;
            int text = chosen ? (shortcutOver ? SliderView.OVER_RANGE_ON_FILL : colors.onSecondaryContainer)
                    : shortcutOver ? SliderView.OVER_RANGE_TEXT : colors.onSurfaceVariant;
            view.setBackground(ApolloUi.ripple(ApolloUi.roundRect(fill, dp(12)), dp(12)));
            view.setTextColor(text);
        }
    }

    private void updateBumpers() {
        int visibility = InputMode.isGamepadConnected() ? View.VISIBLE : View.GONE;
        for (View bumper : bumpers) {
            bumper.setVisibility(visibility);
        }
    }

    // --- The keyboard

    private void openKeyboard() {
        field.setFocusable(true);
        field.setFocusableInTouchMode(true);
        field.setCursorVisible(true);
        field.requestFocus();
        field.selectAll();
        InputMethodManager imm = (InputMethodManager) context().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
    }

    private boolean keyboardOpen() {
        return field != null && field.hasFocus();
    }

    // Back to the card, with the typed value shown again in full
    private void closeKeyboard() {
        InputMethodManager imm = (InputMethodManager) context().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(field.getWindowToken(), 0);
        field.setCursorVisible(false);
        field.setFocusable(false);
        if (card != null) {
            card.requestFocus();
        }
        showValue(true);
    }

    // --- Keys

    private boolean onKey(int keyCode, KeyEvent event) {
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_BUTTON_R1:
                if (down) {
                    step(keyCode == KeyEvent.KEYCODE_BUTTON_L1 ? -1 : 1, speed(event.getRepeatCount()));
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) {
                    moveShortcut(keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return true;
            case KeyEvent.KEYCODE_BUTTON_X:
                if (down && event.getRepeatCount() == 0) {
                    openKeyboard();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    confirm();
                }
                return true;
        }
        return false;
    }

    private void confirm() {
        if (keyboardOpen()) {
            closeKeyboard();
        }
        IntConsumer callback = onSet;
        int chosen = Math.max(min, value);
        dismiss(true);
        if (callback != null) {
            callback.accept(chosen);
        }
    }

    /** @return whether the popup was open */
    boolean dismiss(boolean animate) {
        if (scrim == null) {
            return false;
        }
        if (keyboardOpen()) {
            InputMethodManager imm = (InputMethodManager) context().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(field.getWindowToken(), 0);
        }
        FrameLayout closing = scrim;
        scrim = null;
        card = null;
        handler.removeCallbacksAndMessages(null);
        ((InputManager) context().getSystemService(Context.INPUT_SERVICE)).unregisterInputDeviceListener(deviceListener);
        content.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        ScreenLayer.blockApp(host, false);
        unregisterBack();
        if (anchor != null && anchor.isAttachedToWindow()) {
            anchor.requestFocus();
        }
        anchor = null;
        if (animate) {
            closing.setClickable(false);
            closing.animate().alpha(0f).setDuration(ApolloMotion.SHORT)
                    .setInterpolator(ApolloMotion.EMPHASIZED_ACCELERATE)
                    .withEndAction(() -> ScreenLayer.remove(closing)).start();
        } else {
            ScreenLayer.remove(closing);
        }
        return true;
    }

    /** B of a gamepad or back: closes the keyboard first, then the popup. */
    boolean back() {
        if (keyboardOpen()) {
            closeKeyboard();
            return true;
        }
        return dismiss(true);
    }

    private void registerBack() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && host.getContext() instanceof Activity) {
            OnBackInvokedCallback callback = this::back;
            ((Activity) host.getContext()).getOnBackInvokedDispatcher()
                    .registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback);
            backCallback = callback;
        }
    }

    private void unregisterBack() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backCallback != null) {
            ((Activity) host.getContext()).getOnBackInvokedDispatcher()
                    .unregisterOnBackInvokedCallback((OnBackInvokedCallback) backCallback);
            backCallback = null;
        }
    }
}
