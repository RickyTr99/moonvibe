package com.limelight.ui.apollo;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.ui.apollo.hints.ButtonGlyph;
import com.limelight.ui.theme.ApolloColors;
import com.limelight.ui.theme.ApolloMotion;

/**
 * The pill at the top of the stream when the controller mouse turns on (with its buttons) or off.
 * It goes away by itself.
 */
public class MousePill {
    private static final long SHOWN_ON_MS = 3500;
    private static final long SHOWN_OFF_MS = 1500;

    private final FrameLayout host;
    private final ApolloColors colors;
    private View pill;
    private FrameLayout layer;
    private final Runnable hide = this::hide;

    public MousePill(FrameLayout host) {
        this.host = host;
        this.colors = ApolloColors.dark(host.getContext());
    }

    private int dp(float value) {
        return ApolloUi.dp(host.getContext(), value);
    }

    public void show(boolean active) {
        Context context = host.getContext();
        removeNow();

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        // Centered, not on the baseline of the texts: with the glyphs that pushed some of them below the pill
        row.setBaselineAligned(false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(18), 0);
        // Almost opaque, so it reads over any game
        row.setBackground(ApolloUi.roundRect((colors.surfaceContainerHigh & 0x00FFFFFF) | 0xF0000000, dp(20)));

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.ic_overlay_mouse);
        icon.setImageTintList(ColorStateList.valueOf(active ? colors.primary : colors.onSurfaceVariant));
        row.addView(icon, new LinearLayout.LayoutParams(dp(18), dp(18)));

        TextView title = ApolloUi.text(context, context.getString(active ? R.string.mouse_pill_on : R.string.mouse_pill_off),
                14, colors.onSurface, true);
        title.setPadding(dp(8), 0, 0, 0);
        title.setSingleLine(true);
        row.addView(title);

        if (active) {
            View divider = new View(context);
            divider.setBackgroundColor(colors.outlineVariant);
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(dp(1), dp(18));
            dividerParams.setMarginStart(dp(14));
            dividerParams.setMarginEnd(dp(4));
            row.addView(divider, dividerParams);

            addButton(row, R.string.mouse_pill_click, KeyEvent.KEYCODE_BUTTON_A);
            addButton(row, R.string.mouse_pill_right, KeyEvent.KEYCODE_BUTTON_B);
            addButton(row, R.string.mouse_pill_middle, KeyEvent.KEYCODE_BUTTON_X);
            addButton(row, R.string.mouse_pill_back_forward, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1);
            addButton(row, R.string.mouse_pill_turn_off, KeyEvent.KEYCODE_BUTTON_START);
        }

        // A fixed height, its contents centered in it: nothing can push the pill taller than its background
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(44), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        params.topMargin = dp(20);
        // On the top of the window, over every other view of the stream
        layer = ScreenLayer.of(host);
        layer.addView(row, params);
        pill = row;

        row.setAlpha(0f);
        // A fade only: the stream is a SurfaceView, and the window works out what covers it at layout time.
        // A pill sliding into place stayed "see-through" over the stretch it moved (cut at the bottom)
        // until something else asked for a layout, like the stats or the game menu.
        row.animate().alpha(1f).setDuration(ApolloMotion.MEDIUM)
                .setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE)
                .withEndAction(row::requestLayout).start();
        host.postDelayed(hide, active ? SHOWN_ON_MS : SHOWN_OFF_MS);
    }

    private void addButton(LinearLayout row, int label, int... keys) {
        Context context = row.getContext();
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setBaselineAligned(false);
        item.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < keys.length; i++) {
            FrameLayout slot = new FrameLayout(context);
            slot.addView(ButtonGlyph.create(context, colors, keys[i]));
            LinearLayout.LayoutParams slotParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                slotParams.setMarginStart(dp(3));
            }
            item.addView(slot, slotParams);
        }
        TextView text = ApolloUi.text(context, context.getString(label), 13, colors.onSurfaceVariant, false);
        text.setPadding(dp(6), 0, 0, 0);
        text.setSingleLine(true);
        item.addView(text);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(dp(12));
        row.addView(item, params);
    }

    private void hide() {
        View closing = pill;
        pill = null;
        if (closing == null) {
            return;
        }
        closing.animate().alpha(0f).setDuration(ApolloMotion.SHORT)
                .setInterpolator(ApolloMotion.EMPHASIZED_ACCELERATE)
                .withEndAction(() -> ScreenLayer.remove(closing)).start();
    }

    private void removeNow() {
        host.removeCallbacks(hide);
        if (pill != null) {
            pill.animate().cancel();
            ScreenLayer.remove(pill);
            pill = null;
        }
    }
}
