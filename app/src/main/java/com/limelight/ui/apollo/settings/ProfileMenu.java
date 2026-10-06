package com.limelight.ui.apollo.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import com.limelight.R;
import com.limelight.preferences.ProfilesActivity;
import com.limelight.profiles.Profiles;
import com.limelight.ui.apollo.ApolloUi;
import com.limelight.ui.apollo.hints.HintRow;
import com.limelight.ui.theme.ApolloColors;
import com.limelight.ui.theme.ApolloMotion;

import java.util.ArrayList;
import java.util.List;

/**
 * The profiles menu, dropping down from the name in the top bar (Select on a gamepad): each profile,
 * none (the general settings), and the way to manage them.
 */
public class ProfileMenu extends FrameLayout {
    private static final int WIDTH_DP = 310;

    private final ApolloColors colors;
    private final View scrim;
    private final LinearLayout panel;
    private final LinearLayout list;
    private final List<View> rows = new ArrayList<>();
    private View focusBeforeShow;
    // The row that takes the focus while the menu is open
    private View focusTarget;
    private boolean showing;
    private Object backCallback;

    public static ProfileMenu of(Activity activity) {
        ViewGroup content = (ViewGroup) activity.getWindow().getDecorView();
        for (int i = 0; i < content.getChildCount(); i++) {
            if (content.getChildAt(i) instanceof ProfileMenu) {
                return (ProfileMenu) content.getChildAt(i);
            }
        }
        ProfileMenu menu = new ProfileMenu(activity);
        content.addView(menu, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        return menu;
    }

    private ProfileMenu(Context context) {
        super(context);
        colors = ApolloColors.dark(context);
        setVisibility(GONE);

        scrim = new View(context);
        scrim.setBackgroundColor(0x47000000);
        scrim.setOnClickListener(v -> dismiss());
        addView(scrim, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClickable(true);
        panel.setBackground(ApolloUi.roundRect(colors.surfaceContainerHigh, dp(20)));
        panel.setPadding(dp(8), dp(6), dp(8), dp(4));
        addView(panel, new LayoutParams(dp(WIDTH_DP), LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START));

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        panel.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);

        HintRow hints = new HintRow(context, colors, Gravity.START);
        hints.setPadding(dp(14), 0, dp(14), 0);
        panel.addView(hints, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30)));
        hints.setScope(this);
    }

    private int dp(float value) {
        return ApolloUi.dp(getContext(), value);
    }

    // --- Rows

    private void buildRows() {
        Context context = getContext();
        list.removeAllViews();
        rows.clear();

        TextView header = ApolloUi.text(context, context.getString(R.string.apollo_profile_menu_title).toUpperCase(), 12, colors.primary, true);
        header.setLetterSpacing(0.08f);
        header.setPadding(dp(12), dp(8), dp(12), dp(6));
        list.addView(header);

        Profiles.Profile active = Profiles.active(context);
        for (Profiles.Profile profile : Profiles.list(context)) {
            addRow(profile.name, null, active != null && profile.id.equals(active.id),
                    () -> Profiles.setSelection(context, profile.id));
        }
        addRow(context.getString(R.string.apollo_profile_none), context.getString(R.string.apollo_profile_none_detail),
                active == null, () -> Profiles.setSelection(context, Profiles.SELECTION_NONE));

        View divider = new View(context);
        divider.setBackgroundColor(colors.outlineVariant);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        dividerParams.setMargins(dp(10), dp(4), dp(10), dp(4));
        list.addView(divider, dividerParams);

        View manage = row(context.getString(R.string.apollo_profile_manage), null, false, R.drawable.ic_apollo_cat_menu);
        manage.setOnClickListener(v -> {
            dismiss();
            context.startActivity(new Intent(context, ProfilesActivity.class));
        });
        HintRow.set(manage, KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_open, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_close);
        list.addView(manage);
        rows.add(manage);
        linkFocus();
    }

    // D-pad paths from row to row: the divider in between would make the search skip or jump,
    // and the focus never leaves the menu at its ends
    private void linkFocus() {
        for (View row : rows) {
            row.setId(View.generateViewId());
        }
        for (int i = 0; i < rows.size(); i++) {
            View row = rows.get(i);
            row.setNextFocusUpId(rows.get(Math.max(0, i - 1)).getId());
            row.setNextFocusDownId(rows.get(Math.min(rows.size() - 1, i + 1)).getId());
            row.setNextFocusLeftId(row.getId());
            row.setNextFocusRightId(row.getId());
        }
    }

    private void addRow(String name, String detail, boolean selected, Runnable choose) {
        View row = row(name, detail, selected, 0);
        row.setOnClickListener(v -> {
            choose.run();
            dismiss();
        });
        HintRow.set(row, KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_choose, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_close);
        list.addView(row);
        rows.add(row);
    }

    // A check (or an icon) on the left, the name, and on the right a short detail
    private View row(String name, String detail, boolean selected, int iconRes) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(42));
        row.setPadding(dp(12), 0, dp(12), 0);
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(ApolloUi.stateLayer(colors.surfaceContainerHighest, Color.TRANSPARENT, dp(ApolloUi.ROW_RADIUS_DP)));

        ImageView icon = new ImageView(getContext());
        if (iconRes != 0) {
            icon.setImageResource(iconRes);
            icon.setImageTintList(ColorStateList.valueOf(colors.onSurfaceVariant));
        } else {
            icon.setImageResource(R.drawable.ic_menu_check);
            icon.setImageTintList(ColorStateList.valueOf(colors.primary));
            icon.setVisibility(selected ? VISIBLE : INVISIBLE);
        }
        row.addView(icon, new LinearLayout.LayoutParams(dp(18), dp(18)));

        TextView label = ApolloUi.text(getContext(), name, 13.5f,
                selected ? colors.primary : iconRes != 0 ? colors.onSurfaceVariant : colors.onSurface, true);
        label.setPadding(dp(10), 0, dp(10), 0);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        if (detail != null) {
            TextView detailView = ApolloUi.text(getContext(), detail, 11.5f, colors.outline, false);
            detailView.setSingleLine(true);
            row.addView(detailView);
        }
        return row;
    }

    // --- Show and hide

    private View anchor() {
        return ((Activity) getContext()).findViewById(R.id.apollo_profile_anchor);
    }

    // Right under the name in the top bar, its left edges lined up
    private void placeUnderAnchor() {
        int top = dp(56), left = dp(12);
        View anchor = anchor();
        if (anchor != null && anchor.isShown()) {
            View frame = (View) getParent();
            int[] anchorLocation = new int[2];
            int[] frameLocation = new int[2];
            anchor.getLocationInWindow(anchorLocation);
            frame.getLocationInWindow(frameLocation);
            top = anchorLocation[1] - frameLocation[1] + anchor.getHeight() + dp(6);
            left = Math.max(dp(8), anchorLocation[0] - frameLocation[0]);
        }
        LayoutParams params = (LayoutParams) panel.getLayoutParams();
        params.topMargin = top;
        params.leftMargin = left;
        params.bottomMargin = dp(12);
        panel.setLayoutParams(params);
    }

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
        QuickSettingsPanel.of((Activity) getContext()).dismiss();
        buildRows();
        Activity activity = (Activity) getContext();
        focusBeforeShow = activity.getCurrentFocus();
        showing = true;
        bringToFront();
        placeUnderAnchor();
        setVisibility(VISIBLE);

        // It unfolds from the name, at its top left corner
        panel.animate().cancel();
        scrim.animate().cancel();
        panel.setPivotX(0);
        panel.setPivotY(0);
        panel.setAlpha(0);
        panel.setScaleX(0.92f);
        panel.setScaleY(0.8f);
        panel.setTranslationY(-dp(8));
        scrim.setAlpha(0);
        panel.animate().alpha(1).scaleX(1).scaleY(1).translationY(0)
                .setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE).start();
        scrim.animate().alpha(1)
                .setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD).start();

        // A gamepad lands on the choice in use
        Profiles.Profile active = Profiles.active(getContext());
        List<Profiles.Profile> profiles = Profiles.list(getContext());
        View target = rows.get(profiles.size());
        for (int i = 0; i < profiles.size(); i++) {
            if (active != null && profiles.get(i).id.equals(active.id)) {
                target = rows.get(i);
            }
        }
        // Like the quick settings: the menu is where the focus belongs while it is open, and it takes the
        // focus once laid out (a request made before that may be dropped, the first time above all)
        View first = target;
        focusTarget = first;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            first.setFocusedByDefault(true);
        }
        post(() -> {
            if (showing && !first.isInTouchMode()) {
                first.requestFocus();
            }
        });
        // The first time the menu is only just added to the window: ask again once it is laid out
        panel.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                panel.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                if (showing && focusTarget == first && !first.isInTouchMode() && !first.hasFocus()) {
                    first.requestFocus();
                }
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            OnBackInvokedCallback callback = this::dismiss;
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback);
            backCallback = callback;
        }
    }

    /** Returns true if the menu was open. */
    public boolean dismiss() {
        if (!showing) {
            return false;
        }
        showing = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusTarget != null) {
            // The screen's own default focus takes over again
            focusTarget.setFocusedByDefault(false);
        }
        focusTarget = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backCallback != null) {
            ((Activity) getContext()).getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((OnBackInvokedCallback) backCallback);
            backCallback = null;
        }
        panel.animate().alpha(0).scaleY(0.9f).translationY(-dp(6))
                .setDuration(ApolloMotion.SHORT).setInterpolator(ApolloMotion.EMPHASIZED_ACCELERATE)
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
