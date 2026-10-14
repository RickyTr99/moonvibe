package com.limelight.setup;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.SystemClock;
import android.preference.PreferenceScreen;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.ui.apollo.ApolloTopBar;
import com.limelight.ui.apollo.ApolloUi;
import com.limelight.ui.apollo.GameCardView;
import com.limelight.ui.apollo.PcCardRow;
import com.limelight.ui.apollo.UiSounds;
import com.limelight.ui.apollo.hints.HintRow;
import com.limelight.ui.apollo.hints.ScreenHints;
import com.limelight.ui.apollo.settings.ProfileMenu;
import com.limelight.ui.apollo.settings.QuickSettingsPanel;
import com.limelight.ui.apollo.settings.SettingsView;
import com.limelight.ui.gamemenu.GameMenuView;
import com.limelight.ui.gamemenu.GameMenuView.QuickAction;
import com.limelight.ui.gamemenu.GameMenuView.QuickSlider;
import com.limelight.ui.gamemenu.GameMenuView.Tab;
import com.limelight.ui.theme.ApolloBackground;
import com.limelight.ui.theme.ApolloColors;
import com.limelight.ui.theme.ApolloMotion;
import com.limelight.ui.theme.ThemeColors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The tour of the setup: the real app on the handheld, in three chapters. Home and Settings (the D-pad on
 * the games, RB to the Settings and through them, LB back), the profiles and the quick settings (Select picks
 * a profile, Start opens the quick settings), then a game (A starts it, Select held opens the game menu, the
 * sticks move brightness and volume, B closes it). Each press goes to the screen as the key event of a
 * gamepad; the profiles are made up and nothing of the app's settings changes. The view zooms on each menu
 * while it is open, and the speakers of the handheld play as loud as the volume of the game.
 */
class TourIllustration extends HandheldIllustration {
    /** What the page around shows while the tour plays. */
    interface Listener {
        // The button just pressed and what it does
        void onStep(int key, int labelRes);

        void onFinished();
    }

    private static final String[] GAMES = {"Desktop", "Steam Big Picture", "Playnite", "Xbox", "Battle.net"};
    // The quick launch row, as QuickLaunchView lays it out
    private static final int CARD_GAP_DP = 14;
    private static final long DURATION_MS = 35400;
    private static final long START_DELAY_MS = 1000;
    private static final int START_BRIGHTNESS = 25;
    private static final int START_VOLUME = 70;

    // The moments of the timeline, in ms. Home and Settings, the games and then the settings zoomed in
    private static final int HOME_ZOOM = 200;
    private static final int RIGHT_1 = 1500;
    private static final int RIGHT_2 = 2500;
    private static final int HOME_ZOOM_OUT = 3300;
    private static final int RB = 4700;
    private static final int SETTINGS_ZOOM = 5200;
    private static final int CATEGORY_DOWN = 6500;
    private static final int TO_SETTINGS = 7600;
    private static final int SETTING_DOWN_1 = 8600;
    private static final int SETTING_DOWN_2 = 9400;
    private static final int SETTINGS_ZOOM_OUT = 10200;
    private static final int LB = 11600;
    // Profiles and quick settings, each menu zoomed in while it is open
    private static final int SELECT = 12600;
    private static final int PROFILES_ZOOM = 12950;
    private static final int PROFILE_UP_1 = 14400;
    private static final int PROFILE_UP_2 = 15300;
    private static final int PROFILE_A = 16300;
    private static final int START = 17800;
    private static final int QUICK_ZOOM = 18150;
    private static final int QUICK_DOWN = 19600;
    private static final int QUICK_B = 20700;
    // The game: the menu zoomed in for 1.8 s, then the sticks on the whole screen
    private static final int GAME_A = 22100;
    private static final int LAUNCH = 22400;
    private static final int MENU_HOLD = 23900;
    private static final int MENU_OPEN = 24900;
    private static final int MENU_ZOOM = 25200;
    private static final int MENU_ZOOM_OUT = 27000;
    private static final int LEFT_STICK = 28200;
    private static final int LEFT_STICK_END = 29800;
    private static final int RIGHT_STICK = 30800;
    private static final int RIGHT_STICK_END = 32400;
    private static final int MENU_B = 33600;

    // When a press shows under the handheld: moment, button, what it does
    private static final int[][] STEPS = {
            {RIGHT_1, KeyEvent.KEYCODE_DPAD_RIGHT, R.string.apollo_tour_games},
            {RB, KeyEvent.KEYCODE_BUTTON_R1, R.string.apollo_tab_settings},
            {CATEGORY_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, R.string.apollo_tour_categories},
            {TO_SETTINGS, KeyEvent.KEYCODE_DPAD_RIGHT, R.string.apollo_tour_open_category},
            {SETTING_DOWN_1, KeyEvent.KEYCODE_DPAD_DOWN, R.string.apollo_tour_scroll},
            {LB, KeyEvent.KEYCODE_BUTTON_L1, R.string.apollo_tab_home},
            {SELECT, KeyEvent.KEYCODE_BUTTON_SELECT, R.string.apollo_profiles},
            {PROFILE_UP_1, KeyEvent.KEYCODE_DPAD_UP, R.string.apollo_tour_pick_profile},
            {PROFILE_A, KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_tour_pick_profile},
            {START, KeyEvent.KEYCODE_BUTTON_START, R.string.apollo_hint_quick_settings},
            {QUICK_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, R.string.apollo_tour_scroll},
            {QUICK_B, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_close},
            {GAME_A, KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_tour_start_game},
            {MENU_HOLD, KeyEvent.KEYCODE_BUTTON_SELECT, R.string.apollo_tour_game_menu},
            {LEFT_STICK, KeyEvent.KEYCODE_BUTTON_THUMBL, R.string.game_menu_brightness},
            {RIGHT_STICK, KeyEvent.KEYCODE_BUTTON_THUMBR, R.string.game_menu_volume},
            {MENU_B, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_close},
    };

    /** The preferences the real settings draw, or null while they are not built yet. */
    interface ScreenSource {
        PreferenceScreen get();
    }

    private final ScreenSource settingsSource;
    private final Listener listener;
    private final String[] profiles;

    private final View home;
    private final ApolloTopBar homeBar;
    private final GameCardView[] cards = new GameCardView[GAMES.length];
    private final LinearLayout settingsColumn;
    private final ApolloTopBar settingsBar;
    private final FrameLayout settingsContainer;
    private SettingsView settings;
    private boolean settingsShown;
    // Where the focus was on the Home, which it finds again when the Settings close
    private View homeFocus;
    private final ProfileMenu profileMenu;
    private final QuickSettingsPanel quickSettings;

    private final FrameLayout game;
    private final View dim;
    private final GameMenuView menu;
    private int brightness = START_BRIGHTNESS;
    private int volume = START_VOLUME;
    // The menu music stands for the sound of the game: it goes down with the volume set in the game menu
    private float musicLevel = 1f;

    TourIllustration(Activity activity, ApolloColors colors, ScreenSource settingsSource, String pcName, Listener listener) {
        super(activity, colors);
        this.settingsSource = settingsSource;
        this.listener = listener;
        profiles = new String[]{activity.getString(R.string.apollo_tour_profile_home), activity.getString(R.string.apollo_tour_profile_away)};
        // The focus moves inside, but the hints of the setup stay its own
        HintRow.ignoreFocusIn(this);
        // The window background of the app's screens
        screen.setBackground(new ApolloBackground(colors, ThemeColors.background(activity)));
        String[] tabs = {activity.getString(R.string.apollo_tab_home), activity.getString(R.string.apollo_tab_settings)};

        // The Home, as PcView builds it
        home = LayoutInflater.from(activity).inflate(R.layout.activity_pc_view, screen, false);
        screen.addView(home);
        LinearLayout homeColumn = home.findViewById(R.id.homeColumn);
        homeBar = new ApolloTopBar(activity, colors);
        homeBar.setTabs(tabs, 0, index -> showSettings());
        homeBar.showIllustrationProfile(null, false);
        ((FrameLayout) home.findViewById(R.id.topBarContainer)).addView(homeBar);
        ApolloUi.padForCutout(homeColumn);
        ApolloUi.fadeEdges(home.findViewById(R.id.homeScroll));

        home.findViewById(R.id.quickLaunchSection).setVisibility(VISIBLE);
        LinearLayout quickLaunch = home.findViewById(R.id.quickLaunchContainer);
        ApolloUi.allowFocusOverflow(quickLaunch, dp(8));
        quickLaunch.setPadding(quickLaunch.getPaddingLeft(), dp(14), quickLaunch.getPaddingRight(), quickLaunch.getPaddingBottom());
        String pc = pcName != null ? pcName : "PC";
        for (int i = 0; i < GAMES.length; i++) {
            GameCardView card = new GameCardView(activity, colors, GAMES[i], pc, null, i + 1, false);
            HintRow.set(card, KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_start, KeyEvent.KEYCODE_BUTTON_Y, R.string.apollo_hint_options,
                    KeyEvent.KEYCODE_BUTTON_X, R.string.apollo_hint_add_pc, KeyEvent.KEYCODE_BUTTON_SELECT, R.string.apollo_profiles,
                    KeyEvent.KEYCODE_BUTTON_START, R.string.apollo_hint_quick_settings);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                params.leftMargin = dp(CARD_GAP_DP);
            }
            quickLaunch.addView(card, params);
            cards[i] = card;
        }

        PcCardRow pcs = new PcCardRow(home.findViewById(R.id.pcRow), colors, new PcCardRow.Listener() {
            @Override
            public void onPcClicked(ComputerDetails computer) {
            }

            @Override
            public void onPcLongClicked(ComputerDetails computer) {
            }

            @Override
            public void onAddPcClicked() {
            }
        });
        ComputerDetails computer = new ComputerDetails();
        computer.uuid = "illustration";
        computer.name = pc;
        computer.state = ComputerDetails.State.ONLINE;
        computer.pairState = PairingManager.PairState.PAIRED;
        pcs.update(Collections.singletonList(computer));
        home.findViewById(R.id.no_pc_found_layout).setVisibility(GONE);

        HintRow homeHints = ScreenHints.attach(activity, homeColumn);
        homeHints.setFallback(HintRow.hint(activity, KeyEvent.KEYCODE_BUTTON_X, R.string.apollo_hint_add_pc),
                HintRow.hint(activity, KeyEvent.KEYCODE_BUTTON_SELECT, R.string.apollo_profiles),
                HintRow.hint(activity, KeyEvent.KEYCODE_BUTTON_START, R.string.apollo_hint_quick_settings));

        // The Settings, as StreamSettings builds them, over the Home while they are open
        settingsColumn = new LinearLayout(activity);
        settingsColumn.setOrientation(LinearLayout.VERTICAL);
        screen.addView(settingsColumn, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        settingsBar = new ApolloTopBar(activity, colors);
        settingsBar.setTabs(tabs, 1, index -> showHome());
        settingsBar.showIllustrationProfile(null, false);
        FrameLayout settingsBarContainer = new FrameLayout(activity);
        settingsBarContainer.addView(settingsBar);
        settingsColumn.addView(settingsBarContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        settingsContainer = new FrameLayout(activity);
        settingsColumn.addView(settingsContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        HintRow settingsHints = ScreenHints.attach(activity, settingsColumn);
        settingsHints.setFallback(HintRow.hint(activity, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_home),
                HintRow.hint(activity, KeyEvent.KEYCODE_BUTTON_SELECT, R.string.apollo_profiles),
                HintRow.hint(activity, KeyEvent.KEYCODE_BUTTON_START, R.string.apollo_hint_quick_settings));
        ApolloUi.padForCutout(settingsColumn);
        settingsColumn.setVisibility(GONE);

        // The menus of the Home's top bar, with made-up profiles: the one picked shows in the bars
        profileMenu = ProfileMenu.illustration(screen, homeBar.findViewById(R.id.apollo_profile_anchor), profiles, index -> {
            String name = index >= 0 ? profiles[index] : null;
            homeBar.showIllustrationProfile(name, true);
            settingsBar.showIllustrationProfile(name, false);
        });
        quickSettings = QuickSettingsPanel.illustration(screen, homeBar);

        // The game, with the real game menu of the stream over it; the D-pad never reaches them
        game = new FrameLayout(activity);
        game.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        game.addView(new GameView(activity), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        dim = new View(activity);
        dim.setBackgroundColor(Color.BLACK);
        game.addView(dim, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        menu = new GameMenuView(activity);
        menu.setListener(new MenuContent(activity));
        game.addView(menu, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        screen.addView(game, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        game.setVisibility(GONE);
        applyBrightness();
    }

    private int dp(float value) {
        return ApolloUi.dp(activity, value);
    }

    @Override
    protected long durationMs() {
        return DURATION_MS;
    }

    @Override
    protected long startDelayMs() {
        return START_DELAY_MS;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        homeBar.onResume();
    }

    @Override
    protected void onRestart() {
        // The Settings start again from their first category
        if (settings != null) {
            settingsContainer.removeView(settings);
            settings = null;
        }
        buildSettings();
        settingsShown = false;
        settingsColumn.animate().cancel();
        settingsColumn.setVisibility(GONE);
        home.animate().cancel();
        home.setAlpha(1f);
        home.setVisibility(VISIBLE);
        homeBar.onResume();

        profileMenu.dismiss();
        profileMenu.resetIllustration();
        homeBar.showIllustrationProfile(null, false);
        settingsBar.showIllustrationProfile(null, false);
        quickSettings.dismiss();

        game.animate().cancel();
        game.setVisibility(GONE);
        brightness = START_BRIGHTNESS;
        volume = START_VOLUME;
        restoreMusic(600);
        applyBrightness();
        menu.close();
        homeFocus = null;
        focus(cards[0]);
    }

    @Override
    protected void onStop() {
        restoreMusic(1500);
        homeBar.onPause();
        settingsBar.onPause();
        sendSticks(0, 0);
        menu.close();
    }

    // The sticks let go, or the menu would go on moving its slider
    @Override
    protected void onPaused() {
        sendSticks(0, 0);
    }

    @Override
    protected void onFinished() {
        // The game is left without sound: nothing moves any more
        console.sound = 0;
        console.invalidate();
        restoreMusic(2500);
        listener.onFinished();
    }

    private void restoreMusic(long fadeMs) {
        if (musicLevel != 1f) {
            musicLevel = 1f;
            UiSounds.setMusicLevel(1f, fadeMs);
        }
    }

    // The settings are drawn once the setup has built them
    private void buildSettings() {
        PreferenceScreen preferences = settingsSource.get();
        if (settings != null || preferences == null) {
            return;
        }
        settings = new SettingsView(activity, colors);
        settingsContainer.addView(settings);
        settings.setScreen(preferences);
    }

    private static float at(int ms) {
        return ms / (float) DURATION_MS;
    }

    private boolean reachedAt(float t, int ms) {
        return reached(t, at(ms));
    }

    private float pressAt(float t, int... ms) {
        float down = 0;
        for (int moment : ms) {
            down = Math.max(down, press(t, at(moment)));
        }
        return down;
    }

    @Override
    protected void onFrame(float t) {
        buildSettings();
        for (int[] step : STEPS) {
            if (reachedAt(t, step[0])) {
                listener.onStep(step[1], step[2]);
            }
        }
        homeAndSettings(t);
        profilesAndQuickSettings(t);
        inGame(t);
    }

    private void homeAndSettings(float t) {
        console.right = pressAt(t, RIGHT_1, RIGHT_2, TO_SETTINGS);
        console.down = pressAt(t, CATEGORY_DOWN, SETTING_DOWN_1, SETTING_DOWN_2, QUICK_DOWN);
        console.rb = pressAt(t, RB);
        console.lb = pressAt(t, LB);
        if (reachedAt(t, RIGHT_1) || reachedAt(t, RIGHT_2) || reachedAt(t, TO_SETTINGS)) {
            dpad(KeyEvent.KEYCODE_DPAD_RIGHT, FOCUS_RIGHT);
        }
        if (reachedAt(t, CATEGORY_DOWN) || reachedAt(t, SETTING_DOWN_1) || reachedAt(t, SETTING_DOWN_2)) {
            dpad(KeyEvent.KEYCODE_DPAD_DOWN, FOCUS_DOWN);
        }
        // The games, then the settings under the top bar
        if (reachedAt(t, HOME_ZOOM)) {
            zoomTo(boundsOf(home.findViewById(R.id.quickLaunchSection)));
        }
        if (reachedAt(t, HOME_ZOOM_OUT) || reachedAt(t, SETTINGS_ZOOM_OUT)) {
            zoomOut();
        }
        if (reachedAt(t, SETTINGS_ZOOM)) {
            zoomTo(boundsOf(settingsContainer));
        }
        // As PcView and StreamSettings do with these buttons
        if (reachedAt(t, RB) && !settingsShown) {
            UiSounds.playShown(UiSounds.Sound.TAB);
            homeBar.switchTab(1);
        }
        if (reachedAt(t, LB) && settingsShown) {
            UiSounds.playShown(UiSounds.Sound.TAB);
            settingsBar.switchTab(-1);
        }
    }

    private void profilesAndQuickSettings(float t) {
        console.select = pressAt(t, SELECT);
        console.up = pressAt(t, PROFILE_UP_1, PROFILE_UP_2);
        console.a = pressAt(t, PROFILE_A, GAME_A);
        console.start = pressAt(t, START);
        console.b = pressAt(t, QUICK_B, MENU_B);
        // As PcView does: Select opens the profiles, Start the quick settings
        if (reachedAt(t, SELECT)) {
            UiSounds.playShown(UiSounds.Sound.OPEN);
            profileMenu.show();
        }
        if (reachedAt(t, PROFILES_ZOOM)) {
            zoomTo(boundsOf(profileMenu.getPanel()));
        }
        if (reachedAt(t, PROFILE_UP_1) || reachedAt(t, PROFILE_UP_2)) {
            dpad(KeyEvent.KEYCODE_DPAD_UP, FOCUS_UP);
        }
        if (reachedAt(t, PROFILE_A) && profileMenu.isShowing()) {
            UiSounds.playShown(UiSounds.Sound.SELECT);
            View row = profileMenu.findFocus();
            if (row != null) {
                row.performClick();
            }
            zoomOut();
        }
        if (reachedAt(t, START)) {
            UiSounds.playShown(UiSounds.Sound.OPEN);
            quickSettings.show();
        }
        if (reachedAt(t, QUICK_ZOOM)) {
            zoomTo(boundsOf(quickSettings.getPanel()));
        }
        if (reachedAt(t, QUICK_DOWN)) {
            dpad(KeyEvent.KEYCODE_DPAD_DOWN, FOCUS_DOWN);
        }
        if (reachedAt(t, QUICK_B)) {
            UiSounds.playShown(UiSounds.Sound.BACK);
            quickSettings.dismiss();
            zoomOut();
        }
    }

    private void inGame(float t) {
        // A on the focused game: the card answers, then the game fades in as the stream does
        if (reachedAt(t, GAME_A)) {
            UiSounds.playShown(UiSounds.Sound.SELECT);
            View card = home.findFocus();
            if (card != null) {
                card.setPressed(true);
                card.postDelayed(() -> card.setPressed(false), 150);
            }
        }
        if (reachedAt(t, LAUNCH)) {
            UiSounds.playShown(UiSounds.Sound.LAUNCH);
            game.setVisibility(VISIBLE);
            game.setAlpha(0f);
            game.animate().alpha(1f).setDuration(ApolloMotion.LONG).setInterpolator(ApolloMotion.STANDARD).start();
        }

        // Select held opens the menu, as from a gamepad
        float hold0 = at(MENU_HOLD), open = at(MENU_OPEN), ramp = at(200);
        if (t >= hold0 - ramp && t < open + 2 * ramp) {
            console.select = Math.max(console.select, pulse(t, hold0 - ramp, hold0, open, open + ramp));
            console.selectRing = t > hold0 && t < open + ramp ? seg(t, hold0, open) : 0;
        }
        if (reachedAt(t, MENU_OPEN)) {
            UiSounds.playShown(UiSounds.Sound.OPEN);
            menu.show(true);
        }
        // The upper part of the menu, where its tabs, sliders and actions are
        if (reachedAt(t, MENU_ZOOM)) {
            Rect panel = boundsOf(menu.getPanel());
            panel.bottom = Math.min(panel.bottom, panel.top + Math.round(panel.width() * 1.15f));
            zoomTo(panel);
        }
        if (reachedAt(t, MENU_ZOOM_OUT)) {
            zoomOut();
        }

        // Left stick right: the brightness goes up, the menu steps aside while it moves. Right stick left:
        // the volume goes down
        boolean left = t >= at(LEFT_STICK) && t < at(LEFT_STICK_END);
        boolean right = t >= at(RIGHT_STICK) && t < at(RIGHT_STICK_END);
        if (left || right || reachedAt(t, LEFT_STICK_END) || reachedAt(t, RIGHT_STICK_END)) {
            sendSticks(left ? 1f : 0f, right ? -1f : 0f);
        }
        float leftShown = pulse(t, at(LEFT_STICK - 200), at(LEFT_STICK), at(LEFT_STICK_END), at(LEFT_STICK_END + 200));
        float rightShown = pulse(t, at(RIGHT_STICK - 200), at(RIGHT_STICK), at(RIGHT_STICK_END), at(RIGHT_STICK_END + 200));
        console.leftDx = 7 * leftShown;
        console.leftLit = leftShown;
        console.rightDx = -7 * rightShown;
        console.rightLit = rightShown;

        // The speakers play the game, as loud as its volume
        if (game.getVisibility() == VISIBLE) {
            console.sound = game.getAlpha() * volume / 100f;
            float level = Math.min(1f, volume / (float) START_VOLUME);
            if (level != musicLevel) {
                musicLevel = level;
                UiSounds.setMusicLevel(level, 0);
            }
        }

        // B closes it
        if (reachedAt(t, MENU_B)) {
            UiSounds.playShown(UiSounds.Sound.BACK);
            long now = SystemClock.uptimeMillis();
            menu.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B, 0));
            menu.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_B, 0));
        }
    }

    // --- The two screens, changing as the activities do: the new one fades in over the old one, and its
    // top bar slides the mark over from the tab left

    private void showSettings() {
        homeFocus = home.findFocus();
        settingsShown = true;
        fade(settingsColumn, home);
        settingsBar.onResume();
        // Opened with RB: the selected category takes the focus
        if (settings != null) {
            focus(settings.selectedCategoryView());
        }
    }

    private void showHome() {
        settingsShown = false;
        fade(home, settingsColumn);
        homeBar.onResume();
        focus(homeFocus != null ? homeFocus : cards[0]);
    }

    // The old screen goes, then the new one comes: never both at once (the screens have no background of their
    // own, half-faded together they show through each other)
    private void fade(View in, View out) {
        out.animate().cancel();
        in.animate().cancel();
        in.setVisibility(VISIBLE);
        in.setAlpha(0f);
        out.animate().alpha(0f).setDuration(120).setInterpolator(ApolloMotion.EMPHASIZED_ACCELERATE)
                .withEndAction(() -> {
                    out.setVisibility(GONE);
                    in.animate().alpha(1f).setDuration(220).setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE).start();
                }).start();
    }

    // The game menu, game sticks and brightness

    // The game dims as the stream does with the brightness of the menu
    private void applyBrightness() {
        dim.setAlpha(0.7f * (1 - brightness / 100f));
    }

    // The tilt of both sticks, as a gamepad reports it (the right one on Z, or on RX for some pads)
    private void sendSticks(float left, float right) {
        MotionEvent.PointerProperties[] properties = {new MotionEvent.PointerProperties()};
        properties[0].id = 0;
        properties[0].toolType = MotionEvent.TOOL_TYPE_UNKNOWN;
        MotionEvent.PointerCoords[] coords = {new MotionEvent.PointerCoords()};
        coords[0].setAxisValue(MotionEvent.AXIS_X, left);
        coords[0].setAxisValue(MotionEvent.AXIS_Z, right);
        coords[0].setAxisValue(MotionEvent.AXIS_RX, right);
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE, 1, properties, coords, 0, 0, 1f, 1f,
                -1, 0, InputDevice.SOURCE_JOYSTICK, 0);
        menu.onGenericMotionEvent(event);
        event.recycle();
    }

    // --- Input

    // The keys of the window never reach the app on the handheld: the timeline sends its own
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return false;
    }

    // What the D-pad reaches: a menu of the top bar while it is open, else the screen shown
    private View inputRoot() {
        if (profileMenu.isShowing()) {
            return profileMenu;
        }
        if (quickSettings.isShowing()) {
            return quickSettings;
        }
        return settingsShown ? settingsColumn : home;
    }

    // In touch mode only views that take the focus in touch mode can have it: these views take it
    // from a gamepad, which leaves touch mode, but a press here must not take the whole window out of it
    private static void allowTouchFocus(View view) {
        if (view.isFocusable()) {
            view.setFocusableInTouchMode(true);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                allowTouchFocus(group.getChildAt(i));
            }
        }
    }

    private void focus(View target) {
        if (target == null) {
            return;
        }
        if (target.isInTouchMode()) {
            allowTouchFocus(screen);
        }
        target.requestFocus();
    }

    private static boolean inside(View view, View group) {
        for (View v = view; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            if (v == group) {
                return true;
            }
        }
        return false;
    }

    /**
     * A press of the D-pad, as the window handles it: first to the focused view (a list may scroll or move
     * the focus itself), then, if no view took it, the focus moves to the next view that way.
     */
    private void dpad(int keyCode, int direction) {
        UiSounds.playShown(UiSounds.Sound.MOVE);
        View root = inputRoot();
        View focused = root.findFocus();
        if (focused == null) {
            return;
        }
        if (focused.isInTouchMode()) {
            allowTouchFocus(root);
        }
        long now = SystemClock.uptimeMillis();
        boolean handled = root.dispatchKeyEvent(key(now, KeyEvent.ACTION_DOWN, keyCode));
        root.dispatchKeyEvent(key(now, KeyEvent.ACTION_UP, keyCode));
        if (handled) {
            return;
        }
        View next = focused.focusSearch(direction);
        if (next != null && inside(next, root)) {
            next.requestFocus(direction);
        }
    }

    private static KeyEvent key(long time, int action, int keyCode) {
        return new KeyEvent(time, time, action, keyCode, 0, 0, -1, 0, 0,
                InputDevice.SOURCE_GAMEPAD | InputDevice.SOURCE_DPAD);
    }

    // --- What the game menu shows: the actions and sliders of the stream (GameMenu), here doing nothing

    private final class MenuContent implements GameMenuView.Listener {
        private final Activity activity;

        MenuContent(Activity activity) {
            this.activity = activity;
        }

        private String string(int id) {
            return activity.getString(id);
        }

        @Override
        public List<QuickAction> buildQuickActions() {
            Runnable none = () -> { };
            return Arrays.asList(
                    new QuickAction(KeyEvent.KEYCODE_BUTTON_A, string(R.string.game_menu_disconnect), none),
                    new QuickAction(KeyEvent.KEYCODE_BUTTON_X, string(R.string.game_menu_quit_session), none)
                            .holdToConfirm().shortLabel(string(R.string.game_menu_short_quit)),
                    new QuickAction(KeyEvent.KEYCODE_BUTTON_Y, string(R.string.game_menu_stats), none)
                            .shortLabel(string(R.string.game_menu_short_stats)),
                    new QuickAction(KeyEvent.KEYCODE_BUTTON_START, string(R.string.game_menu_key_game_bar), none)
                            .shortLabel(string(R.string.game_menu_short_game_bar)),
                    new QuickAction(KeyEvent.KEYCODE_BUTTON_SELECT, string(R.string.game_menu_key_start_menu), none)
                            .shortLabel(string(R.string.game_menu_short_start_menu)),
                    new QuickAction(KeyEvent.KEYCODE_DPAD_UP, string(R.string.game_menu_toggle_keyboard), none)
                            .shortLabel(string(R.string.game_menu_short_keyboard)),
                    new QuickAction(KeyEvent.KEYCODE_DPAD_DOWN, string(R.string.game_menu_toggle_full_keyboard), none)
                            .shortLabel(string(R.string.game_menu_short_pc_keyboard)),
                    new QuickAction(KeyEvent.KEYCODE_DPAD_LEFT, string(R.string.game_menu_key_switch_window), none)
                            .shortLabel(string(R.string.game_menu_short_switch_window)),
                    new QuickAction(KeyEvent.KEYCODE_DPAD_RIGHT, string(R.string.game_menu_key_show_desktop), none)
                            .shortLabel(string(R.string.game_menu_short_desktop)));
        }

        @Override
        public List<QuickSlider> buildQuickSliders() {
            return Arrays.asList(
                    new QuickSlider(false, R.drawable.ic_menu_brightness_auto, R.drawable.ic_menu_brightness_auto,
                            string(R.string.game_menu_brightness), string(R.string.game_menu_brightness_auto_description),
                            1, new QuickSlider.Value() {
                                @Override
                                public int get() {
                                    return brightness;
                                }

                                @Override
                                public void set(int value) {
                                    brightness = value;
                                    applyBrightness();
                                }
                            }, () -> false, () -> { })
                            .activeText(string(R.string.game_menu_brightness_auto))
                            .soloWhileAdjusting(),
                    new QuickSlider(true, R.drawable.ic_overlay_volume, R.drawable.ic_overlay_volume_mute,
                            string(R.string.game_menu_volume), string(R.string.game_menu_mute),
                            0, new QuickSlider.Value() {
                                @Override
                                public int get() {
                                    return volume;
                                }

                                @Override
                                public void set(int value) {
                                    volume = value;
                                }
                            }, () -> volume == 0, () -> { }));
        }

        @Override
        public List<Tab> buildTabs() {
            List<Tab> tabs = new ArrayList<>();
            tabs.add(new Tab(string(R.string.game_menu_tab_input), new ArrayList<>()));
            tabs.add(new Tab(string(R.string.game_menu_tab_keys), new ArrayList<>()));
            return tabs;
        }

        @Override
        public void onMenuClosed() {
        }
    }

    /** A game on the screen: sky, sun, hills and ground. */
    private static final class GameView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        GameView(Activity activity) {
            super(activity);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            paint.setShader(new LinearGradient(0, 0, 0, h, 0xFF2F4B72, 0xFF6C86A6, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
            paint.setColor(0xFFE4CF98);
            canvas.drawCircle(w * 0.74f, h * 0.24f, h * 0.08f, paint);
            path.reset();
            path.moveTo(0, h * 0.70f);
            path.lineTo(w * 0.22f, h * 0.46f);
            path.lineTo(w * 0.40f, h * 0.60f);
            path.lineTo(w * 0.64f, h * 0.38f);
            path.lineTo(w, h * 0.64f);
            path.lineTo(w, h);
            path.lineTo(0, h);
            path.close();
            paint.setColor(0xFF3C5170);
            canvas.drawPath(path, paint);
            paint.setColor(0xFF2B3A30);
            canvas.drawRect(0, h * 0.80f, w, h, paint);
        }
    }
}
