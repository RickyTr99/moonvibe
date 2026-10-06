package com.limelight.ui.apollo;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.ui.theme.ApolloColors;
import com.limelight.ui.theme.ApolloMotion;

/**
 * A dialog in the look of the app: a card with rounded corners over a dark veil, usable with touch
 * and with a gamepad. It works over the settings and over the stream alike.
 */
public class ApolloDialog {
    public interface OnPick {
        void onPick(int index);
    }

    public final Dialog dialog;
    public final LinearLayout card;
    // What the dialog shows, between the title and the buttons; it scrolls when the screen is short
    public final LinearLayout body;
    public final ApolloColors colors;
    private final Context context;

    public ApolloDialog(Context context, CharSequence title, int widthDp) {
        this.context = context;
        this.colors = ApolloColors.dark(context);
        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBaselineAligned(false);
        card.setBackground(ApolloUi.roundRect(colors.surfaceContainerHigh, dp(28)));
        card.setPadding(dp(12), dp(22), dp(12), dp(14));
        if (title != null) {
            TextView titleView = ApolloUi.text(context, title, 20, colors.onSurface, true);
            titleView.setPadding(dp(14), 0, dp(14), dp(12));
            card.addView(titleView);
        }
        body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        card.addView(scrolling(context, body));
        dialog.setContentView(card);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.6f);
            int screen = context.getResources().getDisplayMetrics().widthPixels;
            window.setLayout(Math.min(dp(widthDp), screen - dp(32)), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    public int dp(float value) {
        return ApolloUi.dp(context, value);
    }

    public void show() {
        card.setAlpha(0f);
        card.setScaleX(0.94f);
        card.setScaleY(0.94f);
        dialog.show();
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(ApolloMotion.MEDIUM)
                .setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE).start();
    }

    public void dismiss() {
        dialog.dismiss();
    }

    /** A row of the card: a name on the left and what it is set to on the right; focus shows a light veil. */
    public LinearLayout row(CharSequence label, View value, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(50));
        row.setPadding(dp(16), dp(4), dp(16), dp(4));
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(ApolloUi.stateLayer(colors.surfaceContainerHighest, Color.TRANSPARENT, dp(ApolloUi.ROW_RADIUS_DP)));
        TextView name = ApolloUi.text(context, label, 15, colors.onSurface, true);
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (value != null) {
            row.addView(value);
        }
        row.setOnClickListener(onClick);
        return row;
    }

    public TextView sectionHeader(CharSequence title) {
        TextView header = ApolloUi.text(context, title, 13, colors.primary, true);
        header.setPadding(dp(16), dp(12), dp(16), dp(6));
        return header;
    }

    /** Cancel and the main action at the bottom right. */
    public void buttons(CharSequence cancel, CharSequence confirm, Runnable onConfirm) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(14), dp(8), 0);

        TextView cancelButton = ApolloUi.text(context, cancel, 14, colors.primary, true);
        cancelButton.setGravity(Gravity.CENTER);
        cancelButton.setPadding(dp(20), 0, dp(20), 0);
        cancelButton.setFocusable(true);
        cancelButton.setClickable(true);
        cancelButton.setBackground(ApolloUi.stateLayer(colors.surfaceContainerHighest, Color.TRANSPARENT, dp(20)));
        cancelButton.setOnClickListener(v -> dismiss());
        bar.addView(cancelButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));

        TextView confirmButton = ApolloUi.text(context, confirm, 14, colors.onPrimary, true);
        confirmButton.setGravity(Gravity.CENTER);
        confirmButton.setPadding(dp(24), 0, dp(24), 0);
        confirmButton.setFocusable(true);
        confirmButton.setClickable(true);
        // Filled; a lighter fill with the focus
        android.graphics.drawable.StateListDrawable fill = new android.graphics.drawable.StateListDrawable();
        fill.addState(new int[]{android.R.attr.state_focused}, ApolloUi.roundRect(ApolloColors.blend(colors.primary, Color.WHITE, 0.25f), dp(20)));
        fill.addState(new int[]{android.R.attr.state_pressed}, ApolloUi.roundRect(ApolloColors.blend(colors.primary, Color.WHITE, 0.25f), dp(20)));
        fill.addState(new int[]{}, ApolloUi.roundRect(colors.primary, dp(20)));
        confirmButton.setBackground(fill);
        confirmButton.setOnClickListener(v -> onConfirm.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        params.setMarginStart(dp(8));
        bar.addView(confirmButton, params);
        card.addView(bar);
    }

    /** One choice among options, with a check on the current one; it closes when one is picked. */
    public static void choose(Context context, CharSequence title, CharSequence[] options, int selected, OnPick onPick) {
        ApolloDialog chooser = new ApolloDialog(context, title, 400);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        View focus = null;
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            ImageView check = new ImageView(context);
            check.setImageResource(R.drawable.ic_menu_check);
            check.setImageTintList(ColorStateList.valueOf(chooser.colors.primary));
            check.setVisibility(i == selected ? View.VISIBLE : View.INVISIBLE);
            check.setLayoutParams(new LinearLayout.LayoutParams(chooser.dp(18), chooser.dp(18)));
            LinearLayout row = chooser.row(options[i], check, v -> {
                chooser.dismiss();
                onPick.onPick(index);
            });
            list.addView(row);
            if (i == selected || focus == null) {
                focus = row;
            }
        }
        chooser.body.addView(list);
        chooser.show();
        if (focus != null) {
            focus.requestFocus();
        }
    }

    /** A grid of cells (icons or keys) to pick one from. */
    public static void grid(Context context, CharSequence title, View[] cells, int columns, int selected, OnPick onPick) {
        ApolloDialog chooser = new ApolloDialog(context, title, 600);
        GridLayout grid = new GridLayout(context);
        grid.setColumnCount(columns);
        grid.setPadding(chooser.dp(8), 0, chooser.dp(8), 0);
        for (int i = 0; i < cells.length; i++) {
            final int index = i;
            View cell = cells[i];
            cell.setFocusable(true);
            cell.setClickable(true);
            cell.setBackground(ApolloUi.stateLayer(chooser.colors.surfaceContainerHighest,
                    chooser.colors.secondaryContainer, chooser.dp(ApolloUi.ROW_RADIUS_DP)));
            cell.setActivated(i == selected);
            cell.setOnClickListener(v -> {
                chooser.dismiss();
                onPick.onPick(index);
            });
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            params.width = 0;
            params.height = chooser.dp(48);
            params.setMargins(chooser.dp(2), chooser.dp(2), chooser.dp(2), chooser.dp(2));
            grid.addView(cell, params);
        }
        chooser.body.addView(grid);
        chooser.show();
        View focus = selected >= 0 && selected < cells.length ? cells[selected] : cells.length > 0 ? cells[0] : null;
        if (focus != null) {
            focus.requestFocus();
        }
    }

    // The screen less the title, the buttons and a margin; scrolls beyond, so the card is never cut
    private static View scrolling(Context context, View content) {
        int maxHeight = context.getResources().getDisplayMetrics().heightPixels - ApolloUi.dp(context, 190);
        ScrollView scroll = new ScrollView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
            }
        };
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(content);
        return scroll;
    }
}
