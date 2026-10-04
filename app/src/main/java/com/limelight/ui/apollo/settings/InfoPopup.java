package com.limelight.ui.apollo.settings;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import com.limelight.R;
import com.limelight.ui.apollo.ApolloUi;
import com.limelight.ui.apollo.hints.HintRow;
import com.limelight.ui.theme.ApolloColors;
import com.limelight.ui.theme.ApolloMotion;

import java.util.List;

/**
 * What a setting does, in a card over the dimmed screen: its name, a few lines about it
 * and, for a list setting, what each option does, with the chosen one in the accent color.
 * B, back, A, Y (see SettingsView) or a tap outside close it.
 */
class InfoPopup {
    /** An option of a list setting, as the popup shows it. */
    static final class Option {
        final CharSequence name;
        final CharSequence text;
        final boolean selected;

        Option(CharSequence name, CharSequence text, boolean selected) {
            this.name = name;
            this.text = text;
            this.selected = selected;
        }
    }

    private static final int MAX_WIDTH_DP = 420;
    // The names of the options line up in a column as wide as the longest one, up to this
    private static final int MAX_NAME_WIDTH_DP = 140;

    private final FrameLayout host;
    // The screen behind the card, kept out of reach of the D-pad while it is open
    private final ViewGroup content;
    private final ApolloColors colors;
    private FrameLayout scrim;
    private View anchor;
    // Android 13+ with predictive back sends back to this instead of onBackPressed()
    private Object backCallback;

    InfoPopup(FrameLayout host, ViewGroup content, ApolloColors colors) {
        this.host = host;
        this.content = content;
        this.colors = colors;
    }

    private int dp(float value) {
        return ApolloUi.dp(host.getContext(), value);
    }

    boolean isShowing() {
        return scrim != null;
    }

    void show(View anchor, CharSequence title, CharSequence text, List<Option> options) {
        dismiss(false);
        this.anchor = anchor;
        content.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        registerBack();

        // Taps outside the card close it
        scrim = new FrameLayout(host.getContext());
        scrim.setBackgroundColor(Color.argb(128, 0, 0, 0));
        scrim.setClickable(true);
        scrim.setOnClickListener(v -> dismiss(true));
        host.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView card = new ScrollView(host.getContext());
        card.setBackground(ApolloUi.roundRect(colors.surfaceContainerHigh, dp(20)));
        card.setClipToOutline(true);
        card.setVerticalScrollBarEnabled(false);
        // Takes the focus, so the gamepad hints and keys belong to the card while it is open
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            card.setDefaultFocusHighlightEnabled(false);
        }
        // Taps on the card itself keep it open
        card.setClickable(true);
        HintRow.set(card, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_close);
        card.setOnKeyListener((v, keyCode, event) -> {
            // Y is taken by the settings screen, which closes the card with it
            if (keyCode == KeyEvent.KEYCODE_BUTTON_A || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    dismiss(true);
                }
                return true;
            }
            return false;
        });

        LinearLayout column = new LinearLayout(host.getContext());
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(20), dp(18), dp(20), dp(18));
        card.addView(column);

        TextView titleView = ApolloUi.text(host.getContext(), title, 16, colors.onSurface, true);
        titleView.setPadding(0, 0, 0, dp(8));
        column.addView(titleView);

        TextView textView = ApolloUi.text(host.getContext(), text, 13.5f, colors.onSurfaceVariant, false);
        textView.setLineSpacing(0, 1.3f);
        column.addView(textView);

        if (options != null && !options.isEmpty()) {
            TextView header = ApolloUi.text(host.getContext(), host.getContext().getString(R.string.apollo_info_options),
                    12, colors.outline, true);
            header.setPadding(0, dp(14), 0, dp(4));
            column.addView(header);

            int nameWidth = 0;
            for (Option option : options) {
                TextView probe = ApolloUi.text(host.getContext(), option.name, 13, colors.onSurface, true);
                nameWidth = Math.max(nameWidth, (int) Math.ceil(probe.getPaint().measureText(option.name.toString())));
            }
            nameWidth = Math.min(nameWidth, dp(MAX_NAME_WIDTH_DP));

            for (Option option : options) {
                LinearLayout row = new LinearLayout(host.getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, dp(4), 0, dp(4));

                TextView name = ApolloUi.text(host.getContext(), option.name, 13,
                        option.selected ? colors.primary : colors.onSurface, true);
                row.addView(name, new LinearLayout.LayoutParams(nameWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

                TextView optionText = ApolloUi.text(host.getContext(), option.text, 12.5f, colors.onSurfaceVariant, false);
                optionText.setLineSpacing(0, 1.2f);
                LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                textParams.leftMargin = dp(12);
                row.addView(optionText, textParams);

                column.addView(row);
            }
        }

        // As wide as it reads well, centered, and scrolling when it is taller than the screen
        int width = Math.min(dp(MAX_WIDTH_DP), host.getWidth() - dp(32));
        column.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        int height = Math.min(column.getMeasuredHeight(), host.getHeight() - dp(32));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height, Gravity.CENTER);
        scrim.addView(card, params);

        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(ApolloMotion.SHORT).setInterpolator(ApolloMotion.STANDARD).start();
        card.setScaleX(0.92f);
        card.setScaleY(0.92f);
        card.animate().scaleX(1f).scaleY(1f).setDuration(ApolloMotion.MEDIUM)
                .setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE).start();

        card.requestFocus();
    }

    /** @return whether the card was open */
    boolean dismiss(boolean animate) {
        if (scrim == null) {
            return false;
        }
        FrameLayout closing = scrim;
        scrim = null;
        content.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        unregisterBack();
        if (anchor != null && anchor.isAttachedToWindow()) {
            anchor.requestFocus();
        }
        anchor = null;
        if (animate) {
            closing.setClickable(false);
            closing.animate().alpha(0f).setDuration(ApolloMotion.SHORT)
                    .setInterpolator(ApolloMotion.EMPHASIZED_ACCELERATE)
                    .withEndAction(() -> host.removeView(closing)).start();
        } else {
            host.removeView(closing);
        }
        return true;
    }

    private void registerBack() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && host.getContext() instanceof Activity) {
            OnBackInvokedCallback callback = () -> dismiss(true);
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
