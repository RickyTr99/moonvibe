package com.limelight.ui.apollo;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.input.InputManager;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.ui.apollo.hints.InputMode;
import com.limelight.ui.theme.ApolloColors;
import com.limelight.ui.theme.ApolloMotion;
import com.limelight.ui.theme.SystemBars;

/**
 * Top bar of the app screens: the name on the left, the tabs in the middle (LB/RB on a gamepad)
 * with a short mark under the selected one, and on the right the device status in a pill with a
 * small tab under it, which opens the quick settings under itself (Start on a gamepad).
 */
public class ApolloTopBar extends FrameLayout {
    public interface TabListener {
        void onTabSelected(int index);
    }

    private static final int INDICATOR_WIDTH_DP = 18;
    private static final int INDICATOR_HEIGHT_DP = 4;

    private final ApolloColors colors;
    private final LinearLayout quickSettingsButton;
    private final FrameLayout statusPill;
    private final ImageView pillArrow;
    private final View pillTab;
    private final ImageView pillTabArrow;
    private final GradientDrawable pillBackground;
    private final GradientDrawable tabBackground;
    private final LinearLayout tabs;
    private final View tabIndicator;
    private boolean switching;
    private boolean quickSettingsOpen;
    // The tab of the screen just left, for the bar of the next screen
    private static int slideFrom = -1;
    // How long a screen waits before its heavy work while the mark slides in (idle wait + slide)
    public static final long SLIDE_SETTLE_MS = 350;
    private final StatusRowView status;
    private final TextView leftBumper;
    private final TextView rightBumper;
    // A gamepad connected or disconnected shows or hides LB and RB
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
    private TabListener tabListener;
    private int selectedTab;
    private int tabCount;

    public ApolloTopBar(Context context, ApolloColors colors) {
        super(context);
        this.colors = colors;
        setPadding(dp(24), 0, dp(16), 0);
        setMinimumHeight(dp(52));

        // "Moon" in white, "Vibe" in the accent color
        String appName = context.getString(R.string.apollo_app_name);
        SpannableString styledName = new SpannableString(appName);
        int split = appName.indexOf("Vibe");
        if (split > 0) {
            styledName.setSpan(new ForegroundColorSpan(colors.onSurface), 0, split, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        TextView name = ApolloUi.text(context, styledName, 18, colors.primary, true);
        addView(name, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL));

        LinearLayout center = new LinearLayout(context);
        center.setOrientation(LinearLayout.HORIZONTAL);
        center.setGravity(Gravity.CENTER_VERTICAL);
        addView(center, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));

        leftBumper = shoulderChip("LB", -1);
        center.addView(leftBumper, new LinearLayout.LayoutParams(dp(36), dp(24)));
        // The mark under the selected tab is a separate view that slides from tab to tab
        FrameLayout tabsFrame = new FrameLayout(context);
        LinearLayout.LayoutParams tabsParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT);
        tabsParams.leftMargin = dp(12);
        tabsParams.rightMargin = dp(12);
        center.addView(tabsFrame, tabsParams);
        tabs = new LinearLayout(context);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabsFrame.addView(tabs, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        tabIndicator = new View(context);
        tabIndicator.setBackground(ApolloUi.roundRect(colors.primary, dp(INDICATOR_HEIGHT_DP / 2f)));
        FrameLayout.LayoutParams indicatorParams = new FrameLayout.LayoutParams(dp(INDICATOR_WIDTH_DP), dp(INDICATOR_HEIGHT_DP), Gravity.BOTTOM | Gravity.START);
        indicatorParams.bottomMargin = dp(7);
        tabsFrame.addView(tabIndicator, indicatorParams);
        tabIndicator.setVisibility(INVISIBLE);
        rightBumper = shoulderChip("RB", 1);
        center.addView(rightBumper, new LinearLayout.LayoutParams(dp(36), dp(24)));
        updateBumpers();

        // The status pill with its small tab under it: one button that opens the quick settings
        quickSettingsButton = new LinearLayout(context);
        quickSettingsButton.setId(R.id.apollo_quick_settings_anchor);
        quickSettingsButton.setOrientation(LinearLayout.VERTICAL);
        quickSettingsButton.setGravity(Gravity.CENTER_HORIZONTAL);
        quickSettingsButton.setPadding(0, dp(6), 0, 0);
        // The top bar is for touch, a gamepad has Start for this and LB/RB for the tabs
        quickSettingsButton.setFocusable(false);
        quickSettingsButton.setClickable(true);
        quickSettingsButton.setContentDescription(context.getString(R.string.apollo_quick_settings));
        addView(quickSettingsButton, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.END));

        statusPill = new FrameLayout(context);
        pillBackground = ApolloUi.roundRect(colors.surfaceContainer, dp(16));
        statusPill.setBackground(pillBackground);
        statusPill.setMinimumWidth(dp(32));
        status = new StatusRowView(context, colors, false);
        status.setPadding(dp(14), 0, dp(14), 0);
        statusPill.addView(status, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));
        // Without the status (system bars shown) the pill keeps just the arrow
        pillArrow = new ImageView(context);
        pillArrow.setImageResource(R.drawable.ic_apollo_expand);
        pillArrow.setImageTintList(ColorStateList.valueOf(colors.onSurfaceVariant));
        pillArrow.setVisibility(GONE);
        statusPill.addView(pillArrow, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        quickSettingsButton.addView(statusPill, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32)));

        // The tab under the pill says it pulls down
        FrameLayout tab = new FrameLayout(context);
        tabBackground = new GradientDrawable();
        tabBackground.setColor(colors.surfaceContainer);
        float r = dp(10);
        tabBackground.setCornerRadii(new float[] {0, 0, 0, 0, r, r, r, r});
        tab.setBackground(tabBackground);
        ImageView tabArrow = new ImageView(context);
        tabArrow.setImageResource(R.drawable.ic_apollo_expand);
        tabArrow.setImageTintList(ColorStateList.valueOf(colors.onSurfaceVariant));
        tabArrow.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tab.addView(tabArrow, new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER_HORIZONTAL | Gravity.TOP));
        pillTab = tab;
        pillTabArrow = tabArrow;
        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(dp(36), dp(14));
        quickSettingsButton.addView(tab, tabParams);
        ApolloUi.pressFeedback(quickSettingsButton);
    }

    private int dp(float value) {
        return ApolloUi.dp(getContext(), value);
    }

    // LB and RB mean something only with a gamepad, like the hints at the bottom
    private void updateBumpers() {
        int visibility = InputMode.isGamepadConnected() ? VISIBLE : GONE;
        leftBumper.setVisibility(visibility);
        rightBumper.setVisibility(visibility);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ((InputManager) getContext().getSystemService(Context.INPUT_SERVICE)).registerInputDeviceListener(deviceListener, null);
        updateBumpers();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        ((InputManager) getContext().getSystemService(Context.INPUT_SERVICE)).unregisterInputDeviceListener(deviceListener);
    }

    private TextView shoulderChip(String label, int direction) {
        TextView chip = ApolloUi.text(getContext(), label, 11, colors.onSurfaceVariant, true);
        chip.setGravity(Gravity.CENTER);
        // A little lower: the top edge of the bumper dips towards the inner side
        chip.setIncludeFontPadding(false);
        chip.setPadding(0, dp(3), 0, 0);
        chip.setBackground(new BumperDrawable(colors.surfaceContainerHighest, direction < 0, getResources().getDisplayMetrics().density));
        chip.setOnClickListener(v -> switchTab(direction));
        chip.setFocusable(false);
        return chip;
    }

    public void setOnQuickSettingsClickListener(OnClickListener listener) {
        quickSettingsButton.setOnClickListener(listener);
    }

    /** The pill takes the color of a selected item while the quick settings are open under it. */
    public void setQuickSettingsOpen(boolean open) {
        if (open == quickSettingsOpen) {
            return;
        }
        quickSettingsOpen = open;
        int from = open ? colors.surfaceContainer : colors.secondaryContainer;
        int to = open ? colors.secondaryContainer : colors.surfaceContainer;
        ValueAnimator fade = ValueAnimator.ofArgb(from, to);
        fade.setDuration(ApolloMotion.MEDIUM);
        fade.setInterpolator(ApolloMotion.STANDARD);
        fade.addUpdateListener(a -> {
            int color = (int) a.getAnimatedValue();
            pillBackground.setColor(color);
            tabBackground.setColor(color);
        });
        fade.start();
        pillTabArrow.animate().rotation(open ? 180 : 0).setDuration(ApolloMotion.MEDIUM)
                .setInterpolator(ApolloMotion.STANDARD).start();
    }

    public void setTabs(String[] titles, int selected, TabListener listener) {
        tabListener = listener;
        selectedTab = selected;
        tabCount = titles.length;
        tabs.removeAllViews();

        for (int i = 0; i < titles.length; i++) {
            final int index = i;
            TextView tab = ApolloUi.text(getContext(), titles[i], 15,
                    i == selected ? colors.onSurface : colors.outline, true);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(4), 0, dp(4), 0);
            tab.setOnClickListener(v -> selectTab(index));
            tab.setFocusable(false);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT);
            if (i > 0) {
                params.leftMargin = dp(18);
            }
            tabs.addView(tab, params);
        }
        // Coming from another tab, onResume places the mark there first and slides it: placing it here
        // too would make it jump to this tab, back, and over again
        if (slideFrom < 0 || slideFrom == selected || slideFrom >= tabCount) {
            showTab(selected, false, null);
        }
    }

    // LB/RB
    public void switchTab(int direction) {
        if (tabCount > 1) {
            selectTab((selectedTab + direction + tabCount) % tabCount);
        }
    }

    private void selectTab(int index) {
        if (index == selectedTab || tabListener == null || switching) {
            return;
        }
        // The screen changes right away; the bar of the new screen slides the mark over from this tab
        // once it is built (sliding here stutters, the new screen is being built at the same time)
        switching = true;
        markLeaving();
        tabListener.onTabSelected(index);
    }

    // Runs once the tabs have their size, right away if they already have it
    private void whenTabsMeasured(Runnable action) {
        View first = tabs.getChildAt(0);
        if (first == null || first.getWidth() > 0) {
            action.run();
            return;
        }
        tabs.addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                tabs.removeOnLayoutChangeListener(this);
                action.run();
            }
        });
    }

    private void showTab(int index, boolean animate, Runnable then) {
        whenTabsMeasured(() -> placeIndicator(index, animate, then));
    }

    // Moves the mark under a tab and swaps the text colors, then runs the action. The mark stays hidden
    // until it is first placed, so it never shows at the start of the row and jumps
    private void placeIndicator(int index, boolean animate, Runnable then) {
        View tab = tabs.getChildAt(index);
        if (tab == null) {
            if (then != null) {
                then.run();
            }
            return;
        }
        boolean firstPlacement = tabIndicator.getVisibility() != VISIBLE;
        boolean slide = animate && !firstPlacement;

        for (int i = 0; i < tabs.getChildCount(); i++) {
            TextView child = (TextView) tabs.getChildAt(i);
            int color = i == index ? colors.onSurface : colors.outline;
            if (slide && child.getCurrentTextColor() != color) {
                ValueAnimator fade = ValueAnimator.ofArgb(child.getCurrentTextColor(), color);
                fade.setDuration(ApolloMotion.MEDIUM);
                fade.addUpdateListener(a -> child.setTextColor((int) a.getAnimatedValue()));
                fade.start();
            } else {
                child.setTextColor(color);
            }
        }

        float x = tab.getLeft() + (tab.getWidth() - dp(INDICATOR_WIDTH_DP)) / 2f;
        tabIndicator.animate().cancel();
        if (!firstPlacement) {
            // Stopping the animator also stops the fade in of the first placement: never leave it half seen
            tabIndicator.setAlpha(1f);
        }
        if (slide) {
            tabIndicator.animate().translationX(x)
                    .setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD)
                    .withEndAction(then).start();
            return;
        }
        tabIndicator.setTranslationX(x);
        if (firstPlacement) {
            tabIndicator.setAlpha(0f);
            tabIndicator.setVisibility(VISIBLE);
            tabIndicator.animate().alpha(1f).setDuration(ApolloMotion.SHORT).setInterpolator(ApolloMotion.STANDARD).start();
        }
        if (then != null) {
            then.run();
        }
    }

    /** This screen is being left for another tab's one (also by Back), whose bar then slides the mark from here. */
    public void markLeaving() {
        slideFrom = selectedTab;
    }

    public View getQuickSettingsButton() {
        return quickSettingsButton;
    }

    /**
     * Returns true when the mark slides in from another tab: the screen should then hold back its own
     * heavy work for {@link #SLIDE_SETTLE_MS}, or the slide stutters on fast screens.
     */
    public boolean onResume() {
        // Coming from another tab, the mark slides from that tab to this screen's one
        switching = false;
        boolean sliding = false;
        if (tabCount > 0) {
            int from = slideFrom;
            slideFrom = -1;
            if (from >= 0 && from < tabCount && from != selectedTab) {
                // First under the tab just left, then a slide once the new screen has finished its first
                // work (or it stutters on fast screens); in this order even before the tabs are measured
                whenTabsMeasured(() -> {
                    placeIndicator(from, false, null);
                    Looper.myQueue().addIdleHandler(() -> {
                        placeIndicator(selectedTab, true, null);
                        return false;
                    });
                });
                sliding = true;
            } else {
                showTab(selectedTab, false, null);
            }
        }

        // The status duplicates the system bar, so it only shows in full screen: the pill keeps an arrow
        boolean fullscreen = SystemBars.isFullscreen(getContext());
        status.setVisibility(fullscreen ? VISIBLE : GONE);
        pillArrow.setVisibility(fullscreen ? GONE : VISIBLE);
        pillTab.setVisibility(fullscreen ? VISIBLE : INVISIBLE);
        if (fullscreen) {
            status.start();
        } else {
            status.stop();
        }
        return sliding;
    }

    public void onPause() {
        status.stop();
    }
}
