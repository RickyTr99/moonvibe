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
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.profiles.Profiles;
import com.limelight.ui.apollo.hints.InputMode;
import com.limelight.ui.theme.ApolloColors;
import com.limelight.ui.theme.ApolloMotion;
import com.limelight.ui.theme.SystemBars;

/**
 * Top bar of the app screens: the name on the left, the tabs in the middle (LB/RB on a gamepad)
 * with a short mark under the selected one, and on the right the device status on a light veil with
 * a small arrow, which opens the quick settings under itself (Start on a gamepad).
 */
public class ApolloTopBar extends FrameLayout {
    public interface TabListener {
        void onTabSelected(int index);
    }

    private static final int INDICATOR_WIDTH_DP = 18;
    private static final int INDICATOR_HEIGHT_DP = 4;

    private final ApolloColors colors;
    // The veil behind the status, a bit lighter while the quick settings are open
    private static final int VEIL_ALPHA = 0x10;
    private static final int VEIL_OPEN_ALPHA = 0x29;

    private final LinearLayout quickSettingsButton;
    private final ImageView arrow;
    private final LinearLayout.LayoutParams arrowParams;
    private final GradientDrawable buttonBackground;
    private final LinearLayout tabs;
    private final View tabIndicator;
    private boolean switching;
    private boolean quickSettingsOpen;
    // The tab of the screen just left, for the bar of the next screen
    private static int slideFrom = -1;
    // How long a screen waits before its heavy work while the mark slides in (idle wait + slide)
    public static final long SLIDE_SETTLE_MS = 350;
    private final StatusRowView status;
    // The name and the profile icon open the profiles. A profile in use shows in a veil shaped like
    // the status one, with its name and a green dot (the same green as a PC online)
    private static final int PROFILE_DOT = 0xFF6DD58C;
    private final LinearLayout profileButton;
    private final LinearLayout profileChip;
    private final GradientDrawable profileChipBackground;
    private final ImageView profileIcon;
    private final FrameLayout profileNameFrame;
    private final TextView profileName;
    private final TextView appNameView;
    private final LinearLayout center;
    private boolean profileActive;
    private final Runnable profilesListener = () -> showProfile(true);
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
        // The status veil on the right ends as far from the edge as the content below
        setPadding(dp(24), 0, dp(22), 0);
        setMinimumHeight(dp(52));

        // "Moon" in white, "Vibe" in the accent color
        String appName = context.getString(R.string.apollo_app_name);
        SpannableString styledName = new SpannableString(appName);
        int split = appName.indexOf("Vibe");
        if (split > 0) {
            styledName.setSpan(new ForegroundColorSpan(colors.onSurface), 0, split, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        TextView name = ApolloUi.text(context, styledName, 18, colors.primary, true);
        appNameView = name;

        // The name and the profile icon after it are the profiles button (Select on a gamepad)
        profileButton = new LinearLayout(context);
        profileButton.setId(R.id.apollo_profile_anchor);
        profileButton.setOrientation(LinearLayout.HORIZONTAL);
        profileButton.setGravity(Gravity.CENTER_VERTICAL);
        profileButton.setFocusable(false);
        profileButton.setClickable(true);
        profileButton.setContentDescription(context.getString(R.string.apollo_profiles));
        profileButton.addView(name);

        // The profile in use: icon and name on a veil as tall and round as the status one
        profileChip = new LinearLayout(context);
        profileChip.setOrientation(LinearLayout.HORIZONTAL);
        profileChip.setGravity(Gravity.CENTER_VERTICAL);
        profileChipBackground = ApolloUi.roundRect(Color.TRANSPARENT, dp(ApolloUi.ROW_RADIUS_DP));
        profileChip.setBackground(profileChipBackground);
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(40));
        chipParams.leftMargin = dp(8);
        profileButton.addView(profileChip, chipParams);
        profileIcon = new ImageView(context);
        profileIcon.setImageResource(R.drawable.ic_apollo_profile);
        profileChip.addView(profileIcon, new LinearLayout.LayoutParams(dp(18), dp(18)));

        // The name with a small green dot at its top right
        profileNameFrame = new FrameLayout(context);
        profileName = ApolloUi.text(context, "", 13.5f, colors.onSurface, true);
        profileName.setSingleLine(true);
        profileName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        profileName.setPadding(0, dp(2), dp(6), 0);
        profileNameFrame.addView(profileName, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
        View dot = new View(context);
        dot.setBackground(ApolloUi.roundRect(PROFILE_DOT, dp(2.5f)));
        profileNameFrame.addView(dot, new FrameLayout.LayoutParams(dp(5), dp(5), Gravity.TOP | Gravity.END));
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        nameParams.leftMargin = dp(8);
        profileChip.addView(profileNameFrame, nameParams);

        addView(profileButton, new LayoutParams(LayoutParams.WRAP_CONTENT, dp(40), Gravity.START | Gravity.CENTER_VERTICAL));
        ApolloUi.pressFeedback(profileButton);
        showProfile(false);

        center = new LinearLayout(context);
        center.setOrientation(LinearLayout.HORIZONTAL);
        center.setGravity(Gravity.CENTER_VERTICAL);
        // The whole height of the bar, so the mark sits under the tab names and not over them
        addView(center, new LayoutParams(LayoutParams.WRAP_CONTENT, dp(52), Gravity.CENTER));

        leftBumper = shoulderChip("LB", -1);
        center.addView(leftBumper, new LinearLayout.LayoutParams(dp(30), dp(17)));
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
        center.addView(rightBumper, new LinearLayout.LayoutParams(dp(30), dp(17)));
        updateBumpers();

        // The status on a light fixed veil with a small arrow after it: one button that opens the quick settings
        quickSettingsButton = new LinearLayout(context);
        quickSettingsButton.setId(R.id.apollo_quick_settings_anchor);
        quickSettingsButton.setOrientation(LinearLayout.HORIZONTAL);
        quickSettingsButton.setGravity(Gravity.CENTER_VERTICAL);
        quickSettingsButton.setMinimumWidth(dp(40));
        buttonBackground = ApolloUi.roundRect(veil(VEIL_ALPHA), dp(ApolloUi.ROW_RADIUS_DP));
        quickSettingsButton.setBackground(buttonBackground);
        // The top bar is for touch, a gamepad has Start for this and LB/RB for the tabs
        quickSettingsButton.setFocusable(false);
        quickSettingsButton.setClickable(true);
        quickSettingsButton.setContentDescription(context.getString(R.string.apollo_quick_settings));
        addView(quickSettingsButton, new LayoutParams(LayoutParams.WRAP_CONTENT, dp(40), Gravity.END | Gravity.CENTER_VERTICAL));

        status = new StatusRowView(context, colors, false);
        quickSettingsButton.addView(status, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        arrow = new ImageView(context);
        arrow.setImageResource(R.drawable.ic_apollo_expand);
        arrow.setImageTintList(ColorStateList.valueOf(colors.onSurfaceVariant));
        arrow.setScaleType(ImageView.ScaleType.FIT_CENTER);
        arrowParams = new LinearLayout.LayoutParams(dp(18), dp(18));
        quickSettingsButton.addView(arrow, arrowParams);
        showStatus(true);
        ApolloUi.pressFeedback(quickSettingsButton);
    }

    private int veil(int alpha) {
        return (colors.onSurface & 0x00FFFFFF) | (alpha << 24);
    }

    // Without the status (system bars shown) the button keeps just the arrow
    private void showStatus(boolean show) {
        status.setVisibility(show ? VISIBLE : GONE);
        arrowParams.leftMargin = show ? dp(6) : 0;
        arrow.setLayoutParams(arrowParams);
        quickSettingsButton.setPadding(show ? dp(12) : dp(11), 0, show ? dp(8) : dp(11), 0);
    }

    private int dp(float value) {
        return ApolloUi.dp(getContext(), value);
    }

    // A long profile name stops short of the tabs in the middle and ends with "…"
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width > 0) {
            int unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            center.measure(unspecified, MeasureSpec.makeMeasureSpec(dp(52), MeasureSpec.EXACTLY));
            appNameView.measure(unspecified, unspecified);
            int free = (width - center.getMeasuredWidth()) / 2 - getPaddingLeft() - dp(12);
            // Name margin, veil paddings, icon and the margin before the profile name
            int taken = appNameView.getMeasuredWidth() + dp(8) + dp(10) + dp(18) + dp(8) + dp(12);
            int maxWidth = Math.max(dp(40), free - taken);
            if (profileName.getMaxWidth() != maxWidth) {
                profileName.setMaxWidth(maxWidth);
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
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
        Profiles.addListener(profilesListener);
        showProfile(false);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        ((InputManager) getContext().getSystemService(Context.INPUT_SERVICE)).unregisterInputDeviceListener(deviceListener);
        Profiles.removeListener(profilesListener);
    }

    public void setOnProfileClickListener(OnClickListener listener) {
        profileButton.setOnClickListener(listener);
    }

    // With a profile in use the veil, its name and the dot fade in; without one only a grey icon stays
    private void showProfile(boolean animate) {
        Profiles.Profile profile = Profiles.active(getContext());
        boolean active = profile != null;
        boolean changed = active != profileActive;
        profileActive = active;
        int fromWidth = profileChip.getWidth();
        if (active) {
            profileName.setText(profile.name);
        }
        profileNameFrame.setVisibility(active ? VISIBLE : GONE);
        profileChip.setPadding(active ? dp(10) : dp(2), 0, active ? dp(12) : dp(2), 0);
        profileIcon.setImageTintList(ColorStateList.valueOf(active ? colors.onSurfaceVariant : colors.outline));
        int to = active ? veil(VEIL_ALPHA) : Color.TRANSPARENT;
        if (animate && changed && fromWidth > 0) {
            // The veil widens or narrows to its new content instead of jumping
            profileChip.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(dp(40), MeasureSpec.EXACTLY));
            int toWidth = profileChip.getMeasuredWidth();
            ViewGroup.LayoutParams params = profileChip.getLayoutParams();
            ValueAnimator resize = ValueAnimator.ofInt(fromWidth, toWidth);
            resize.setDuration(ApolloMotion.MEDIUM);
            resize.setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE);
            resize.addUpdateListener(a -> {
                params.width = (int) a.getAnimatedValue();
                profileChip.setLayoutParams(params);
            });
            resize.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                    profileChip.setLayoutParams(params);
                }
            });
            params.width = fromWidth;
            profileChip.setLayoutParams(params);
            resize.start();
        }
        if (animate && changed) {
            ValueAnimator fade = ValueAnimator.ofArgb(active ? Color.TRANSPARENT : veil(VEIL_ALPHA), to);
            fade.setDuration(ApolloMotion.MEDIUM);
            fade.setInterpolator(ApolloMotion.STANDARD);
            fade.addUpdateListener(a -> profileChipBackground.setColor((int) a.getAnimatedValue()));
            fade.start();
            if (active) {
                profileNameFrame.setAlpha(0f);
                profileNameFrame.animate().alpha(1f).setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD).start();
            }
        } else {
            profileChipBackground.setColor(to);
        }
    }

    private TextView shoulderChip(String label, int direction) {
        TextView chip = ApolloUi.text(getContext(), label, 9.5f, colors.onSurfaceVariant, true);
        chip.setGravity(Gravity.CENTER);
        chip.setIncludeFontPadding(false);
        chip.setBackground(new BumperDrawable(colors.surfaceContainerHighest, direction < 0, getResources().getDisplayMetrics().density));
        chip.setOnClickListener(v -> switchTab(direction));
        chip.setFocusable(false);
        return chip;
    }

    public void setOnQuickSettingsClickListener(OnClickListener listener) {
        quickSettingsButton.setOnClickListener(listener);
    }

    /** The veil gets lighter and the arrow turns while the quick settings are open under it. */
    public void setQuickSettingsOpen(boolean open) {
        if (open == quickSettingsOpen) {
            return;
        }
        quickSettingsOpen = open;
        ValueAnimator fade = ValueAnimator.ofArgb(veil(open ? VEIL_ALPHA : VEIL_OPEN_ALPHA), veil(open ? VEIL_OPEN_ALPHA : VEIL_ALPHA));
        fade.setDuration(ApolloMotion.MEDIUM);
        fade.setInterpolator(ApolloMotion.STANDARD);
        fade.addUpdateListener(a -> buttonBackground.setColor((int) a.getAnimatedValue()));
        fade.start();
        arrow.animate().rotation(open ? 180 : 0).setDuration(ApolloMotion.MEDIUM)
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
        // A profile may have been changed or its rule met while this screen was away
        showProfile(false);
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

        // The status duplicates the system bar, so it only shows in full screen
        boolean fullscreen = SystemBars.isFullscreen(getContext());
        showStatus(fullscreen);
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
