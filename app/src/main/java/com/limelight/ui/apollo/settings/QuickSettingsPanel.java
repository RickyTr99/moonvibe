package com.limelight.ui.apollo.settings;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.apollo.ApolloUi;
import com.limelight.ui.apollo.ApolloWidgets;
import com.limelight.ui.apollo.hints.ButtonGlyph;
import com.limelight.ui.apollo.hints.HintRow;
import com.limelight.ui.theme.ApolloColors;
import com.limelight.ui.theme.ApolloMotion;
import com.limelight.utils.Dialog;

import java.util.ArrayList;
import java.util.List;

/**
 * Quick settings, from the left with Select or the button at the top left: shortcuts to the settings
 * changed most often. They write the very same settings as the settings screen.
 */
public class QuickSettingsPanel extends FrameLayout {
    private static final int WIDTH_DP = 340;
    private static final String PREF_BITRATE = "seekbar_bitrate_kbps";
    private static final String PREF_VIDEO_FORMAT = "video_format";
    private static final String PREF_PERF_OVERLAY = "checkbox_enable_perf_overlay";
    private static final String PREF_RESOLUTION = "list_resolution";
    private static final String PREF_FPS = "list_fps";
    // The defaults of PreferenceConfiguration
    private static final String DEFAULT_RESOLUTION = "1280x720";
    private static final String DEFAULT_FPS = "60";
    // The same range and steps as the bitrate in the settings (preferences.xml)
    private static final int BITRATE_MIN_KBPS = 1000;
    private static final int BITRATE_MAX_KBPS = 500000;
    private static final int BITRATE_STEP_KBPS = 1000;

    private final ApolloColors colors;
    private final SharedPreferences prefs;
    private final View scrim;
    private final LinearLayout panel;
    private final LinearLayout list;
    private final OptionsPopup popup;
    private final List<View> rows = new ArrayList<>();
    private View focusBeforeShow;
    private boolean showing;
    private Object backCallback;

    private ApolloWidgets.SwitchView statsSwitch;
    private SliderView bitrateSlider;
    private TextView resolutionValue, fpsValue, bitrateValue, codecValue;

    // The panel of the activity, added over its content the first time
    public static QuickSettingsPanel of(Activity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        for (int i = 0; i < content.getChildCount(); i++) {
            if (content.getChildAt(i) instanceof QuickSettingsPanel) {
                return (QuickSettingsPanel) content.getChildAt(i);
            }
        }
        QuickSettingsPanel panel = new QuickSettingsPanel(activity);
        content.addView(panel, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        return panel;
    }

    private QuickSettingsPanel(Context context) {
        super(context);
        colors = ApolloColors.dark(context);
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        setVisibility(GONE);

        scrim = new View(context);
        scrim.setBackgroundColor(0x52000000);
        scrim.setOnClickListener(v -> dismiss());
        addView(scrim, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClickable(true);
        GradientDrawable background = new GradientDrawable();
        background.setColor(colors.surfaceContainerLow);
        float radius = dp(24);
        background.setCornerRadii(new float[] {0, 0, radius, radius, radius, radius, 0, 0});
        panel.setBackground(background);
        panel.setPadding(dp(10), dp(18), dp(10), dp(10));
        addView(panel, new LayoutParams(dp(WIDTH_DP), LayoutParams.MATCH_PARENT, Gravity.START));

        TextView title = ApolloUi.text(context, context.getString(R.string.apollo_quick_settings), 18, colors.onSurface, true);
        title.setPadding(dp(14), dp(2), dp(14), dp(20));
        panel.addView(title);

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        panel.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);

        popup = new OptionsPopup(this, panel, colors);

        // Gamepad hints at the bottom of the panel
        HintRow hints = new HintRow(context, colors, Gravity.START);
        hints.setPadding(dp(14), 0, dp(14), 0);
        panel.addView(hints, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30)));
        hints.setScope(panel);
        buildRows();
    }

    private int dp(float value) {
        return ApolloUi.dp(getContext(), value);
    }

    // --- Rows

    private void buildRows() {
        Context context = getContext();

        resolutionValue = valueChip();
        View resolutionRow = row(context.getString(R.string.title_resolution_list), resolutionValue);
        HintRow.set(resolutionRow, KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_open, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_close);
        resolutionRow.setOnClickListener(v -> showChoice(resolutionRow, resolutionChoices(), PREF_RESOLUTION, DEFAULT_RESOLUTION));

        fpsValue = valueChip();
        View fpsRow = row(context.getString(R.string.title_fps_list), fpsValue);
        HintRow.set(fpsRow, KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_open, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_close);
        fpsRow.setOnClickListener(v -> showChoice(fpsRow, fpsChoices(), PREF_FPS, DEFAULT_FPS));

        // Bitrate: label above, a wide slider moved with left/right below
        bitrateSlider = new SliderView(context, colors);
        bitrateSlider.setRange(BITRATE_MIN_KBPS, BITRATE_MAX_KBPS, BITRATE_STEP_KBPS);
        bitrateValue = ApolloUi.text(context, "", 13, colors.onSurface, true);
        View bitrateRow = sliderRow(context.getString(R.string.title_seekbar_bitrate), bitrateSlider, bitrateValue);
        bitrateSlider.setListener(new SliderView.Listener() {
            @Override
            public void onSliderMoved(int value) {
                bitrateValue.setText(bitrateText(value));
            }

            @Override
            public void onSliderReleased(int value) {
                setBitrate(value);
            }
        });
        HintRow.set(bitrateRow, ButtonGlyph.DPAD_LEFT_RIGHT, R.string.apollo_hint_adjust, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_close);
        bitrateRow.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) {
                return false;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                int step = BITRATE_STEP_KBPS * SettingsView.sliderSpeed(event)
                        * (keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                setBitrate(Math.max(BITRATE_MIN_KBPS, Math.min(BITRATE_MAX_KBPS, bitrate() + step)));
            }
            return true;
        });

        codecValue = valueChip();
        View codecRow = row(context.getString(R.string.apollo_qs_codec), codecValue);
        HintRow.set(codecRow, KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_open, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_close);
        codecRow.setOnClickListener(v -> {
            String[] values = getResources().getStringArray(R.array.video_format_values);
            String current = prefs.getString(PREF_VIDEO_FORMAT, "auto");
            int selected = 0;
            for (int i = 0; i < values.length; i++) {
                if (values[i].equals(current)) {
                    selected = i;
                }
            }
            popup.show(codecRow, dp(WIDTH_DP) - dp(16), getResources().getStringArray(R.array.video_format_names), selected,
                    index -> {
                        prefs.edit().putString(PREF_VIDEO_FORMAT, values[index]).apply();
                        bind(true);
                    });
        });

        statsSwitch = new ApolloWidgets.SwitchView(context);
        View statsRow = row(context.getString(R.string.apollo_qs_stats), statsSwitch);
        statsRow.setOnClickListener(v -> toggle(PREF_PERF_OVERLAY));
        HintRow.set(statsRow, KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_change, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_close);
        statsSwitch.setColors(colors.primary, colors.onPrimary, colors.surfaceContainerHighest, colors.outline);

        // Keep the gamepad focus inside the panel, wrapping at the ends
        for (View row : rows) {
            row.setId(View.generateViewId());
        }
        for (int i = 0; i < rows.size(); i++) {
            View row = rows.get(i);
            row.setNextFocusUpId(rows.get((i - 1 + rows.size()) % rows.size()).getId());
            row.setNextFocusDownId(rows.get((i + 1) % rows.size()).getId());
            row.setNextFocusLeftId(row.getId());
            row.setNextFocusRightId(row.getId());
        }
    }

    private LinearLayout baseRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setMinimumHeight(dp(46));
        row.setPadding(dp(14), 0, dp(14), 0);
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(ApolloUi.stateLayer(colors.surfaceContainerHigh, Color.TRANSPARENT, dp(ApolloUi.ROW_RADIUS_DP)));
        list.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rows.add(row);
        return row;
    }

    private View row(String label, View widget) {
        LinearLayout row = baseRow();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView text = ApolloUi.text(getContext(), label, 14, colors.onSurface, true);
        text.setPadding(0, dp(6), dp(12), dp(6));
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(widget);
        return row;
    }

    // Label and value on one line, the slider across the whole row below
    private View sliderRow(String label, SliderView slider, TextView value) {
        LinearLayout row = baseRow();
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(14), dp(10), dp(14), dp(4));
        row.setClickable(false);

        LinearLayout top = new LinearLayout(getContext());
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView text = ApolloUi.text(getContext(), label, 14, colors.onSurface, true);
        top.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(value);
        row.addView(top);
        row.addView(slider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        return row;
    }

    private TextView valueChip() {
        TextView chip = ApolloUi.text(getContext(), "", 13, colors.onSurface, true);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setMaxWidth(dp(170));
        chip.setMinHeight(dp(30));
        chip.setPadding(dp(12), 0, dp(6), 0);
        chip.setBackground(ApolloUi.roundRect(colors.surfaceContainerHighest, dp(8)));
        chip.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_apollo_expand, 0);
        chip.setCompoundDrawableTintList(ColorStateList.valueOf(colors.onSurfaceVariant));
        chip.setCompoundDrawablePadding(dp(2));
        return chip;
    }

    // --- Values

    private int bitrate() {
        return prefs.getInt(PREF_BITRATE, PreferenceConfiguration.getDefaultBitrate(getContext()));
    }

    private String bitrateText(int kbps) {
        return kbps % 1000 == 0 ? (kbps / 1000) + " Mbps" : String.format((java.util.Locale) null, "%.1f Mbps", kbps / 1000f);
    }

    private void setBitrate(int kbps) {
        prefs.edit().putInt(PREF_BITRATE, kbps).apply();
        bind(true);
    }

    private void toggle(String key) {
        prefs.edit().putBoolean(key, !prefs.getBoolean(key, false)).apply();
        bind(true);
    }

    private void bind(boolean animate) {
        int bitrate = bitrate();
        bitrateSlider.setValue(bitrate);
        bitrateValue.setText(bitrateText(bitrate));

        String[] values = getResources().getStringArray(R.array.video_format_values);
        String[] names = getResources().getStringArray(R.array.video_format_names);
        String current = prefs.getString(PREF_VIDEO_FORMAT, "auto");
        codecValue.setText(names[0]);
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                codecValue.setText(names[i]);
            }
        }

        statsSwitch.setChecked(prefs.getBoolean(PREF_PERF_OVERLAY, false), animate);

        resolutionValue.setText(resolutionChoices().nameOf(prefs.getString(PREF_RESOLUTION, DEFAULT_RESOLUTION)));
        fpsValue.setText(fpsChoices().nameOf(prefs.getString(PREF_FPS, DEFAULT_FPS)));
    }

    // --- Resolution and frame rate

    // The options of a list setting: names, values and the index where the native options start
    private static final class Choices {
        final List<String> names = new ArrayList<>();
        final List<String> values = new ArrayList<>();
        int nativeStart = -1;

        void add(String name, String value, boolean isNative) {
            if (values.contains(value)) {
                return;
            }
            if (isNative && nativeStart < 0) {
                nativeStart = values.size();
            }
            names.add(name);
            values.add(value);
        }

        boolean isNative(String value) {
            int index = values.indexOf(value);
            return nativeStart >= 0 && index >= nativeStart;
        }

        // A value the list does not have (picked from a display mode in the settings) shows as it is
        String nameOf(String value) {
            int index = values.indexOf(value);
            return index >= 0 ? names.get(index) : value;
        }
    }

    // The same options as the settings: the standard resolutions plus the native one of this screen,
    // and without the notch area when the screen has one
    private Choices resolutionChoices() {
        Choices choices = new Choices();
        String[] names = getResources().getStringArray(R.array.resolution_names);
        String[] values = getResources().getStringArray(R.array.resolution_values);
        for (int i = 0; i < values.length; i++) {
            choices.add(names[i], values[i], false);
        }

        Display display = getDisplayCompat();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getRealMetrics(metrics);
        int width = Math.max(metrics.widthPixels, metrics.heightPixels);
        int height = Math.min(metrics.widthPixels, metrics.heightPixels);

        boolean hasInsets = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && display.getCutout() != null) {
            DisplayCutout cutout = display.getCutout();
            int widthInsets = cutout.getSafeInsetLeft() + cutout.getSafeInsetRight();
            int heightInsets = cutout.getSafeInsetTop() + cutout.getSafeInsetBottom();
            if (widthInsets != 0 || heightInsets != 0) {
                int safeWidth = Math.max(metrics.widthPixels - widthInsets, metrics.heightPixels - heightInsets);
                int safeHeight = Math.min(metrics.widthPixels - widthInsets, metrics.heightPixels - heightInsets);
                choices.add(nativeName(R.string.resolution_prefix_native, safeWidth, safeHeight), safeWidth + "x" + safeHeight, true);
                hasInsets = true;
            }
        }
        choices.add(nativeName(hasInsets ? R.string.resolution_prefix_native_fullscreen : R.string.resolution_prefix_native,
                width, height), width + "x" + height, true);
        return choices;
    }

    private String nativeName(int prefixRes, int width, int height) {
        return getContext().getString(prefixRes) + " (" + width + "x" + height + ")";
    }

    // The standard frame rates the screen can show (all of them with "Unlock all frame rates"), plus its native one
    private Choices fpsChoices() {
        Choices choices = new Choices();
        float refreshRate = getDisplayCompat().getRefreshRate();
        boolean unlockFps = PreferenceConfiguration.readPreferences(getContext()).unlockFps;
        String[] names = getResources().getStringArray(R.array.fps_names);
        String[] values = getResources().getStringArray(R.array.fps_values);
        for (int i = 0; i < values.length; i++) {
            // The same thresholds as the settings, with some room for a rate rounded down
            boolean tooFast = (values[i].equals("120") && refreshRate < 118) || (values[i].equals("90") && refreshRate < 88);
            if (unlockFps || !tooFast) {
                choices.add(names[i], values[i], false);
            }
        }
        int nativeFps = Math.round(refreshRate);
        if (nativeFps > 0) {
            choices.add(getContext().getString(R.string.resolution_prefix_native) + " (" + nativeFps + " "
                    + getContext().getString(R.string.fps_suffix_fps) + ")", Integer.toString(nativeFps), true);
        }
        return choices;
    }

    private Display getDisplayCompat() {
        return ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
    }

    private void showChoice(View row, Choices choices, String key, String defaultValue) {
        String current = prefs.getString(key, defaultValue);
        popup.show(row, dp(WIDTH_DP) - dp(16), choices.names.toArray(new String[0]), Math.max(0, choices.values.indexOf(current)),
                index -> {
                    String value = choices.values.get(index);
                    // Like the settings: a new resolution or frame rate resets the bitrate to its default
                    String resolution = key.equals(PREF_RESOLUTION) ? value : prefs.getString(PREF_RESOLUTION, DEFAULT_RESOLUTION);
                    String fps = key.equals(PREF_FPS) ? value : prefs.getString(PREF_FPS, DEFAULT_FPS);
                    prefs.edit()
                            .putString(key, value)
                            .putInt(PREF_BITRATE, PreferenceConfiguration.getDefaultBitrate(resolution, fps))
                            .apply();
                    bind(true);

                    if (choices.isNative(value) && getContext() instanceof Activity) {
                        Dialog.displayDialog((Activity) getContext(),
                                getContext().getString(key.equals(PREF_RESOLUTION)
                                        ? R.string.title_native_res_dialog : R.string.title_native_fps_dialog),
                                getContext().getString(R.string.text_native_res_dialog), false);
                    }
                });
    }

    // --- Show and hide

    public boolean isShowing() {
        return showing;
    }

    public void toggle() {
        if (showing) {
            dismiss();
        } else {
            show();
        }
    }

    public void show() {
        if (showing) {
            return;
        }
        bind(false);
        Activity activity = (Activity) getContext();
        focusBeforeShow = activity.getCurrentFocus();
        showing = true;
        bringToFront();
        setVisibility(VISIBLE);

        panel.animate().cancel();
        scrim.animate().cancel();
        panel.setTranslationX(-dp(WIDTH_DP));
        scrim.setAlpha(0);
        panel.animate().translationX(0)
                .setDuration(ApolloMotion.LONG).setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE).start();
        scrim.animate().alpha(1)
                .setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD).start();
        rows.get(0).requestFocus();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            OnBackInvokedCallback callback = this::dismiss;
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback);
            backCallback = callback;
        }
    }

    // Returns true if the panel was open; B closes the options menu first
    public boolean dismiss() {
        if (popup.dismiss(true)) {
            return true;
        }
        if (!showing) {
            return false;
        }
        showing = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backCallback != null) {
            ((Activity) getContext()).getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((OnBackInvokedCallback) backCallback);
            backCallback = null;
        }
        panel.animate().translationX(-panel.getWidth())
                .setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.EMPHASIZED_ACCELERATE)
                .withEndAction(() -> {
                    if (!showing) {
                        setVisibility(GONE);
                    }
                })
                .start();
        scrim.animate().alpha(0)
                .setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD).start();

        if (focusBeforeShow != null && focusBeforeShow.isAttachedToWindow()) {
            focusBeforeShow.requestFocus();
        }
        focusBeforeShow = null;
        return true;
    }
}
