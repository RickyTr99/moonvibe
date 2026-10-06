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
import com.limelight.binding.input.Shortcuts;
import com.limelight.ui.apollo.ApolloUi;
import com.limelight.ui.apollo.ScreenLayer;
import com.limelight.ui.apollo.hints.HintRow;
import com.limelight.ui.theme.ApolloColors;
import com.limelight.ui.theme.ApolloMotion;

import java.util.List;

/**
 * The actions of a controller shortcut, in a popup over the settings: the actions of MoonVibe on the left,
 * the Windows keys and the custom commands on the right.
 */
class ShortcutPopup {
    interface Listener {
        void onActionSelected(String value);
    }

    private static final int MAX_WIDTH_DP = 460;

    private final FrameLayout host;
    // Where the veil goes while the popup is open, and the views kept out of the D-pad meanwhile
    private FrameLayout root;
    // The screen behind the popup, kept out of reach of the D-pad while it is open
    private final ViewGroup content;
    private final ApolloColors colors;
    private FrameLayout scrim;
    private View anchor;
    // Android 13+ with predictive back sends back to this instead of onBackPressed()
    private Object backCallback;

    ShortcutPopup(FrameLayout host, ViewGroup content, ApolloColors colors) {
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

    /**
     * @param glyphs the buttons of the shortcut, shown before the title, or null
     */
    void show(View anchor, View glyphs, CharSequence title, List<Shortcuts.Group> groups, boolean aynButton,
              String selected, Listener listener) {
        dismiss(false);
        this.anchor = anchor;
        root = ScreenLayer.of(anchor);
        blockOthers();
        registerBack();

        // A dark veil over the settings; taps on it close the popup
        scrim = new FrameLayout(host.getContext());
        scrim.setBackgroundColor(0x99000000);
        scrim.setClickable(true);
        scrim.setOnClickListener(v -> dismiss(true));
        root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout card = new LinearLayout(host.getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(ApolloUi.roundRect(colors.surfaceContainerHigh, dp(28)));
        card.setPadding(dp(12), dp(20), dp(12), dp(12));
        // Taps on the card do not close it
        card.setClickable(true);

        LinearLayout header = new LinearLayout(host.getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBaselineAligned(false);
        header.setPadding(dp(14), 0, dp(14), dp(10));
        if (glyphs != null) {
            header.addView(glyphs);
        }
        TextView titleView = ApolloUi.text(host.getContext(), title, 18, colors.onSurface, true);
        titleView.setPadding(glyphs != null ? dp(4) : 0, 0, 0, 0);
        header.addView(titleView);
        card.addView(header);

        ScrollView scroll = new ScrollView(host.getContext());
        scroll.setVerticalScrollBarEnabled(false);
        // One list, the groups one after the other
        LinearLayout target = column();
        scroll.addView(target);
        card.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        View selectedView = null;
        View first = null;
        for (Shortcuts.Group group : groups) {
            target.addView(groupHeader(group.title));
            for (Shortcuts.Option option : group.options) {
                boolean isSelected = option.value.equals(selected);
                View row = optionRow(option, isSelected, () -> {
                    dismiss(true);
                    listener.onActionSelected(option.value);
                });
                target.addView(row);
                if (first == null) {
                    first = row;
                }
                if (isSelected) {
                    selectedView = row;
                }
            }
        }

        // Centered; at most as wide as two comfortable columns, inside the screen
        int width = Math.min(dp(MAX_WIDTH_DP), root.getWidth() - dp(32));
        int maxHeight = root.getHeight() - dp(48);
        card.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        int height = Math.min(card.getMeasuredHeight(), maxHeight);
        scrim.addView(card, new FrameLayout.LayoutParams(width, height, Gravity.CENTER));

        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(ApolloMotion.SHORT).start();
        card.setScaleX(0.94f);
        card.setScaleY(0.94f);
        card.animate().scaleX(1f).scaleY(1f).setDuration(ApolloMotion.MEDIUM)
                .setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE).start();

        View focusTarget = selectedView != null ? selectedView : first;
        if (focusTarget != null) {
            focusTarget.requestFocus();
            if (selectedView != null) {
                View chosen = selectedView;
                scroll.post(() -> scroll.scrollTo(0, Math.max(0, chosen.getTop() - scroll.getHeight() / 2)));
            }
        }
    }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(host.getContext());
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private View groupHeader(CharSequence title) {
        TextView header = ApolloUi.text(host.getContext(), title, 12.5f, colors.primary, true);
        header.setPadding(dp(12), dp(10), dp(12), dp(4));
        return header;
    }

    private View optionRow(Shortcuts.Option option, boolean selected, Runnable onClick) {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(42));
        row.setPadding(dp(12), dp(4), dp(12), dp(4));
        row.setFocusable(true);
        row.setClickable(true);
        // A light veil marks the option with the focus, as in the settings
        row.setBackground(ApolloUi.stateLayer(colors.surfaceContainerHighest, Color.TRANSPARENT, dp(ApolloUi.ROW_RADIUS_DP)));

        TextView label = ApolloUi.text(host.getContext(), option.label, 14, selected ? colors.primary : colors.onSurface, selected);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        if (option.keys != null) {
            TextView keys = ApolloUi.text(host.getContext(), option.keys, 12, colors.outline, false);
            keys.setPadding(dp(8), 0, 0, 0);
            keys.setSingleLine(true);
            row.addView(keys);
        }
        if (selected) {
            ImageView check = new ImageView(host.getContext());
            check.setImageResource(R.drawable.ic_menu_check);
            check.setImageTintList(ColorStateList.valueOf(colors.primary));
            LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(dp(18), dp(18));
            checkParams.setMarginStart(dp(8));
            row.addView(check, checkParams);
        }

        HintRow.set(row, KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_choose, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_cancel);
        row.setOnClickListener(v -> onClick.run());
        return row;
    }

    /** @return whether the popup was open */
    boolean dismiss(boolean animate) {
        if (scrim == null) {
            return false;
        }
        FrameLayout closing = scrim;
        scrim = null;
        unblockOthers();
        unregisterBack();
        if (anchor != null && anchor.isAttachedToWindow()) {
            anchor.requestFocus();
        }
        anchor = null;
        FrameLayout parent = root;
        if (animate) {
            closing.setClickable(false);
            closing.animate().alpha(0f).setDuration(ApolloMotion.SHORT)
                    .setInterpolator(ApolloMotion.EMPHASIZED_ACCELERATE)
                    .withEndAction(() -> parent.removeView(closing)).start();
        } else {
            parent.removeView(closing);
        }
        return true;
    }

    // While the popup is open the D-pad stays in it: the rest of the app takes no focus
    private void blockOthers() {
        content.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        ScreenLayer.blockApp(host, true);
    }

    private void unblockOthers() {
        content.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        ScreenLayer.blockApp(host, false);
    }
}
