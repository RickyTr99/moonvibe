package com.limelight.ui.apollo;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
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
 * Top bar of the app screens: quick settings button and name on the left, the tabs in the
 * middle (LB/RB on a gamepad) and the device status on the right while in full screen.
 */
public class ApolloTopBar extends FrameLayout {
    public interface TabListener {
        void onTabSelected(int index);
    }

    private final ApolloColors colors;
    private final ImageView quickSettingsButton;
    private final LinearLayout tabs;
    private final View tabIndicator;
    private boolean switching;
    // The tab of the screen just left, for the bar of the next screen
    private static int slideFrom = -1;
    // How long a screen waits before its heavy work while the pill slides in (idle wait + slide)
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
        setPadding(dp(16), 0, dp(22), 0);
        setMinimumHeight(dp(64));

        LinearLayout start = new LinearLayout(context);
        start.setOrientation(LinearLayout.HORIZONTAL);
        start.setGravity(Gravity.CENTER_VERTICAL);
        addView(start, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.START | Gravity.CENTER_VERTICAL));

        quickSettingsButton = new ImageView(context);
        quickSettingsButton.setImageResource(R.drawable.ic_apollo_tune);
        quickSettingsButton.setImageTintList(ColorStateList.valueOf(colors.onSurface));
        quickSettingsButton.setScaleType(ImageView.ScaleType.CENTER);
        quickSettingsButton.setBackground(ApolloUi.ripple(ApolloUi.roundRect(colors.surfaceContainerHigh, dp(22)), dp(22)));
        // The top bar is for touch, a gamepad has Select for this and LB/RB for the tabs
        quickSettingsButton.setFocusable(false);
        quickSettingsButton.setContentDescription(context.getString(R.string.apollo_quick_settings));
        start.addView(quickSettingsButton, new LinearLayout.LayoutParams(dp(44), dp(44)));

        // "Moon" in white, "Vibe" in the accent color
        String appName = context.getString(R.string.apollo_app_name);
        SpannableString styledName = new SpannableString(appName);
        int split = appName.indexOf("Vibe");
        if (split > 0) {
            styledName.setSpan(new ForegroundColorSpan(colors.onSurface), 0, split, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        TextView name = ApolloUi.text(context, styledName, 20, colors.primary, true);
        name.setPadding(dp(14), 0, 0, 0);
        start.addView(name);

        LinearLayout center = new LinearLayout(context);
        center.setOrientation(LinearLayout.HORIZONTAL);
        center.setGravity(Gravity.CENTER_VERTICAL);
        addView(center, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));

        leftBumper = shoulderChip("LB", -1);
        center.addView(leftBumper, new LinearLayout.LayoutParams(dp(36), dp(24)));
        // The selected tab pill is a separate view that slides from tab to tab, as in the game menu
        FrameLayout tabsFrame = new FrameLayout(context);
        tabsFrame.setPadding(dp(5), dp(5), dp(5), dp(5));
        tabsFrame.setBackground(ApolloUi.roundRect(colors.surfaceContainerLow, dp(25)));
        LinearLayout.LayoutParams tabsParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tabsParams.leftMargin = dp(10);
        tabsParams.rightMargin = dp(10);
        center.addView(tabsFrame, tabsParams);
        tabIndicator = new View(context);
        tabIndicator.setBackground(ApolloUi.roundRect(colors.secondaryContainer, dp(20)));
        tabsFrame.addView(tabIndicator, new FrameLayout.LayoutParams(0, dp(40)));
        tabs = new LinearLayout(context);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabsFrame.addView(tabs, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(40)));
        rightBumper = shoulderChip("RB", 1);
        center.addView(rightBumper, new LinearLayout.LayoutParams(dp(36), dp(24)));
        updateBumpers();

        status = new StatusRowView(context, colors, false);
        addView(status, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.END | Gravity.CENTER_VERTICAL));
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

    public void setTabs(String[] titles, int selected, TabListener listener) {
        tabListener = listener;
        selectedTab = selected;
        tabCount = titles.length;
        tabs.removeAllViews();

        for (int i = 0; i < titles.length; i++) {
            final int index = i;
            TextView tab = ApolloUi.text(getContext(), titles[i], 15,
                    i == selected ? colors.onSecondaryContainer : colors.onSurfaceVariant, true);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(22), 0, dp(22), 0);
            tab.setMinHeight(dp(40));
            tab.setBackground(ApolloUi.ripple(ApolloUi.roundRect(Color.TRANSPARENT, dp(20)), dp(20)));
            tab.setOnClickListener(v -> selectTab(index));
            tab.setFocusable(false);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT);
            if (i > 0) {
                params.leftMargin = dp(4);
            }
            tabs.addView(tab, params);
        }
        showTab(selected, false, null);
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
        // The screen changes right away; the bar of the new screen slides the pill over from this tab
        // once it is built (sliding here stutters, the new screen is being built at the same time)
        switching = true;
        markLeaving();
        tabListener.onTabSelected(index);
    }

    // Moves the pill to a tab and swaps the text colors, then runs the action
    private void showTab(int index, boolean animate, Runnable then) {
        tabIndicator.post(() -> {
            View tab = tabs.getChildAt(index);
            if (tab != null && tab.getWidth() == 0) {
                // Not measured yet: try again after the first layout
                tabs.addOnLayoutChangeListener(new OnLayoutChangeListener() {
                    @Override
                    public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                        tabs.removeOnLayoutChangeListener(this);
                        showTab(index, animate, then);
                    }
                });
                return;
            }
            if (tab == null) {
                if (then != null) {
                    then.run();
                }
                return;
            }
            if (tabIndicator.getLayoutParams().width != tab.getWidth()) {
                tabIndicator.getLayoutParams().width = tab.getWidth();
                tabIndicator.requestLayout();
            }
            for (int i = 0; i < tabs.getChildCount(); i++) {
                TextView child = (TextView) tabs.getChildAt(i);
                int color = i == index ? colors.onSecondaryContainer : colors.onSurfaceVariant;
                if (animate && child.getCurrentTextColor() != color) {
                    ValueAnimator fade = ValueAnimator.ofArgb(child.getCurrentTextColor(), color);
                    fade.setDuration(ApolloMotion.MEDIUM);
                    fade.addUpdateListener(a -> child.setTextColor((int) a.getAnimatedValue()));
                    fade.start();
                } else {
                    child.setTextColor(color);
                }
            }
            tabIndicator.animate().cancel();
            if (animate) {
                tabIndicator.animate().translationX(tab.getLeft())
                        .setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD)
                        .withEndAction(then).start();
            } else {
                tabIndicator.setTranslationX(tab.getLeft());
                if (then != null) {
                    then.run();
                }
            }
        });
    }

    /** This screen is being left for another tab's one (also by Back), whose bar then slides the pill from here. */
    public void markLeaving() {
        slideFrom = selectedTab;
    }

    public View getQuickSettingsButton() {
        return quickSettingsButton;
    }

    /**
     * Returns true when the pill slides in from another tab: the screen should then hold back its own
     * heavy work for {@link #SLIDE_SETTLE_MS}, or the slide stutters on fast screens.
     */
    public boolean onResume() {
        // Coming from another tab, the pill slides from that tab to this screen's one
        switching = false;
        boolean sliding = false;
        if (tabCount > 0) {
            int from = slideFrom;
            slideFrom = -1;
            if (from >= 0 && from < tabCount && from != selectedTab) {
                showTab(from, false, null);
                // Slide only once the new screen has finished its first work, or it stutters on fast screens
                Looper.myQueue().addIdleHandler(() -> {
                    showTab(selectedTab, true, null);
                    return false;
                });
                sliding = true;
            } else {
                showTab(selectedTab, false, null);
            }
        }

        // The status duplicates the system bar, so it only shows in full screen
        boolean fullscreen = SystemBars.isFullscreen(getContext());
        status.setVisibility(fullscreen ? VISIBLE : GONE);
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
