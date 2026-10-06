package com.limelight.ui.apollo.settings;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
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

/**
 * The options of a list setting, in a menu that drops down next to its row.
 */
class OptionsPopup {
    interface Listener {
        void onOptionSelected(int index);
    }

    // The menu fits its options within these widths
    private static final int MIN_WIDTH_DP = 160;
    private static final int MAX_WIDTH_DP = 320;

    private final FrameLayout host;
    // The screen behind the menu, kept out of reach of the D-pad while the menu is open
    private final ViewGroup content;
    private final ApolloColors colors;
    private FrameLayout scrim;
    private View anchor;
    // Android 13+ with predictive back sends back to this instead of onBackPressed()
    private Object backCallback;

    OptionsPopup(FrameLayout host, ViewGroup content, ApolloColors colors) {
        this.content = content;
        this.host = host;
        this.colors = colors;
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

    private int dp(float value) {
        return ApolloUi.dp(host.getContext(), value);
    }

    boolean isShowing() {
        return scrim != null;
    }

    /**
     * @param rightEdge where the menu ends, in host coordinates
     */
    void show(View anchor, int rightEdge, CharSequence[] options, int selected, Listener listener) {
        dismiss(false);
        this.anchor = anchor;
        content.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        registerBack();

        // Taps outside the menu close it
        scrim = new FrameLayout(host.getContext());
        scrim.setClickable(true);
        scrim.setOnClickListener(v -> dismiss(true));
        host.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView card = new ScrollView(host.getContext());
        card.setBackground(ApolloUi.roundRect(colors.surfaceContainerHigh, dp(14)));
        card.setClipToOutline(true);
        card.setVerticalScrollBarEnabled(false);

        LinearLayout list = new LinearLayout(host.getContext());
        list.setOrientation(LinearLayout.VERTICAL);
        // No padding: the focus tint of the first and last option reaches the rounded edge of the card
        list.setPadding(0, 0, 0, 0);
        card.addView(list);

        View selectedView = null;
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(host.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(40));
            row.setPadding(dp(12), dp(6), dp(16), dp(6));
            row.setFocusable(true);
            row.setClickable(true);
            // A light tint marks the option with the focus, as in the settings
            row.setBackground(ApolloUi.stateLayer(colors.surfaceContainerHighest, Color.TRANSPARENT, 0));

            ImageView check = new ImageView(host.getContext());
            check.setImageResource(R.drawable.ic_menu_check);
            check.setImageTintList(ColorStateList.valueOf(colors.primary));
            check.setVisibility(i == selected ? View.VISIBLE : View.INVISIBLE);
            row.addView(check, new LinearLayout.LayoutParams(dp(18), dp(18)));

            TextView label = ApolloUi.text(host.getContext(), options[i], 13.5f, colors.onSurface, i == selected);
            label.setPadding(dp(10), 0, 0, 0);
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            HintRow.set(row, KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_choose, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_cancel);
            row.setOnClickListener(v -> {
                dismiss(true);
                listener.onOptionSelected(index);
            });
            list.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (i == selected) {
                selectedView = row;
            }
        }

        // As wide as its longest option, between a minimum and a maximum; next to the row, inside the screen
        list.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int width = Math.max(dp(MIN_WIDTH_DP), Math.min(list.getMeasuredWidth(), dp(MAX_WIDTH_DP)));
        width = Math.min(width, host.getWidth() - dp(32));
        int[] hostLocation = new int[2], anchorLocation = new int[2];
        host.getLocationInWindow(hostLocation);
        anchor.getLocationInWindow(anchorLocation);
        int maxHeight = host.getHeight() - dp(32);
        list.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        int height = Math.min(list.getMeasuredHeight(), maxHeight);
        int top = anchorLocation[1] - hostLocation[1];
        top = Math.max(dp(16), Math.min(top, host.getHeight() - dp(16) - height));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.leftMargin = Math.max(dp(16), rightEdge - width);
        params.topMargin = top;
        scrim.addView(card, params);

        card.setAlpha(0f);
        card.setPivotY(0);
        card.setScaleY(0.9f);
        card.animate().alpha(1f).scaleY(1f).setDuration(ApolloMotion.MEDIUM)
                .setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE).start();

        View focusTarget = selectedView != null ? selectedView : list.getChildAt(0);
        if (focusTarget != null) {
            focusTarget.requestFocus();
            if (selectedView != null) {
                View target = selectedView;
                card.post(() -> card.scrollTo(0, Math.max(0, target.getTop() - height / 2)));
            }
        }
    }

    /** @return whether a menu was open */
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
}
