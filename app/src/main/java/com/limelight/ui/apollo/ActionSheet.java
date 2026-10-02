package com.limelight.ui.apollo;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.ui.apollo.hints.HintRow;
import com.limelight.ui.theme.ApolloColors;
import com.limelight.ui.theme.ApolloMotion;

import java.util.ArrayList;
import java.util.List;

/**
 * Side sheet with the options of a PC or a game, opened with Y on a gamepad or a long press.
 * It slides in from the right over the screen; B, back or a tap outside closes it.
 */
public class ActionSheet extends FrameLayout {
    private static final int WIDTH_DP = 300;

    public static class Action {
        final int iconResId;
        final String label;
        final Runnable action;
        boolean danger;

        public Action(int iconResId, String label, Runnable action) {
            this.iconResId = iconResId;
            this.label = label;
            this.action = action;
        }

        // Shown in the error color, for destructive actions
        public Action danger() {
            danger = true;
            return this;
        }
    }

    private final ApolloColors colors;
    private final View scrim;
    private final LinearLayout panel;
    private final TextView title;
    private final TextView subtitle;
    private final LinearLayout list;
    private View focusBeforeShow;
    private boolean showing;

    // The sheet of the activity, added over its content the first time
    public static ActionSheet of(Activity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        for (int i = 0; i < content.getChildCount(); i++) {
            if (content.getChildAt(i) instanceof ActionSheet) {
                return (ActionSheet) content.getChildAt(i);
            }
        }
        ActionSheet sheet = new ActionSheet(activity);
        content.addView(sheet, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        return sheet;
    }

    private ActionSheet(Context context) {
        super(context);
        colors = ApolloColors.dark(context);
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
        background.setCornerRadii(new float[] {radius, radius, 0, 0, 0, 0, radius, radius});
        panel.setBackground(background);
        panel.setPadding(dp(10), dp(18), dp(10), dp(10));
        addView(panel, new LayoutParams(dp(WIDTH_DP), LayoutParams.MATCH_PARENT, Gravity.END));

        title = ApolloUi.text(context, "", 18, colors.onSurface, true);
        title.setPadding(dp(14), 0, dp(14), 0);
        panel.addView(title);

        subtitle = ApolloUi.text(context, "", 12.5f, colors.onSurfaceVariant, false);
        subtitle.setPadding(dp(14), dp(2), dp(14), dp(12));
        panel.addView(subtitle);

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        panel.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);

        // Gamepad hints at the bottom of the sheet
        HintRow hints = new HintRow(context, colors, Gravity.START);
        hints.setPadding(dp(14), 0, dp(14), 0);
        panel.addView(hints, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30)));
        hints.setScope(panel);
    }

    private int dp(float value) {
        return ApolloUi.dp(getContext(), value);
    }

    public boolean isShowing() {
        return showing;
    }

    public void show(String titleText, String subtitleText, List<Action> actions) {
        title.setText(titleText);
        subtitle.setText(subtitleText);
        subtitle.setVisibility(subtitleText == null || subtitleText.isEmpty() ? GONE : VISIBLE);

        list.removeAllViews();
        List<View> rows = new ArrayList<>();
        for (Action action : actions) {
            View row = createRow(action);
            list.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            rows.add(row);
        }

        // Keep the gamepad focus inside the sheet, wrapping at the ends
        for (int i = 0; i < rows.size(); i++) {
            View row = rows.get(i);
            row.setId(View.generateViewId());
        }
        for (int i = 0; i < rows.size(); i++) {
            View row = rows.get(i);
            row.setNextFocusUpId(rows.get((i - 1 + rows.size()) % rows.size()).getId());
            row.setNextFocusDownId(rows.get((i + 1) % rows.size()).getId());
            row.setNextFocusLeftId(row.getId());
            row.setNextFocusRightId(row.getId());
        }

        Activity activity = (Activity) getContext();
        focusBeforeShow = activity.getCurrentFocus();
        showing = true;
        bringToFront();
        setVisibility(VISIBLE);

        panel.animate().cancel();
        scrim.animate().cancel();
        panel.setTranslationX(dp(WIDTH_DP));
        scrim.setAlpha(0);
        panel.animate().translationX(0)
                .setDuration(ApolloMotion.LONG).setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE).start();
        scrim.animate().alpha(1)
                .setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD).start();

        if (!rows.isEmpty()) {
            rows.get(0).requestFocus();
        }
    }

    // Returns true if the sheet was open
    public boolean dismiss() {
        if (!showing) {
            return false;
        }
        showing = false;
        panel.animate().translationX(panel.getWidth())
                .setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.EMPHASIZED_ACCELERATE)
                .withEndAction(() -> {
                    if (!showing) {
                        setVisibility(GONE);
                        list.removeAllViews();
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

    private View createRow(Action action) {
        Context context = getContext();
        int color = action.danger ? colors.error : colors.onSurface;

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(46));
        row.setPadding(dp(14), 0, dp(14), 0);
        row.setFocusable(true);
        HintRow.set(row, KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_select, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_close);
        row.setClickable(true);

        row.setBackground(new LayerDrawable(new Drawable[] {
                ApolloUi.ripple(ApolloUi.roundRect(Color.TRANSPARENT, dp(23)), dp(23)),
                ApolloUi.focusRing(context, colors, dp(23))
        }));

        ImageView icon = new ImageView(context);
        icon.setImageResource(action.iconResId);
        icon.setImageTintList(ColorStateList.valueOf(color));
        row.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));

        TextView label = ApolloUi.text(context, action.label, 14, color, true);
        label.setPadding(dp(14), dp(10), 0, dp(10));
        row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        row.setOnClickListener(v -> {
            dismiss();
            if (action.action != null) {
                post(action.action);
            }
        });
        return row;
    }
}
