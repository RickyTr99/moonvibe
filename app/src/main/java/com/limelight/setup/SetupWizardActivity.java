package com.limelight.setup;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.text.SpannableString;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.binding.PlatformBinding;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.binding.video.VulkanRendererBridge;
import com.limelight.computers.ComputerDatabaseManager;
import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.http.PairingManager.PairState;
import com.limelight.preferences.AddComputerManually;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.preferences.SettingsHost;
import com.limelight.preferences.StreamSettings;
import com.limelight.profiles.ActiveProfileSettings;
import com.limelight.profiles.ProfileEditor;
import com.limelight.ui.apollo.ApolloDialog;
import com.limelight.ui.apollo.ApolloUi;
import com.limelight.ui.apollo.ApolloWidgets;
import com.limelight.ui.apollo.UiSounds;
import com.limelight.ui.apollo.hints.ButtonGlyph;
import com.limelight.ui.apollo.hints.HintRow;
import com.limelight.ui.apollo.hints.InputMode;
import com.limelight.ui.apollo.hints.ScreenHints;
import com.limelight.ui.apollo.settings.SettingsView;
import com.limelight.ui.theme.ApolloColors;
import com.limelight.ui.theme.ApolloMotion;
import com.limelight.ui.theme.ThemeColors;
import com.limelight.utils.NetworkProbe;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.UiHelper;

import org.xmlpull.v1.XmlPullParserException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The first-run setup: the look of the app, a PC found on the network and paired, a connection test that
 * sets the bitrate, the stream quality, the AYN buttons on AYN handhelds, and a tour of what MoonVibe does.
 * It opens on the first start without saved PCs, and again from Settings > App. Skip (X) leaves it after a
 * confirmation; the settings steps draw the real settings, through the hidden settings fragment.
 */
public class SetupWizardActivity extends Activity implements SettingsHost {
    /** Set once the setup is finished or skipped. */
    public static final String PREF_DONE = "setup_wizard_done";
    // Opened on the first start and not finished yet: it opens again from the start, also once a PC is paired
    private static final String PREF_STARTED = "setup_wizard_started";
    // As long as the actions held to confirm in the game menu
    private static final long FINISH_HOLD_MS = 1000;

    private static final int STEP_WELCOME = 0;
    private static final int STEP_APPEARANCE = 1;
    private static final int STEP_PC = 2;
    private static final int STEP_PIN = 3;
    private static final int STEP_NETWORK = 4;
    private static final int STEP_QUALITY = 5;
    private static final int STEP_AYN = 6;
    private static final int STEP_EXPLORE = 7;
    private static final int STEP_DONE = 8;

    private static final String STATE_STEP = "step";
    private static final String STATE_PAIRED_NAME = "pairedName";
    private static final String STATE_PAIRED_HOST = "pairedHost";
    private static final String STATE_PAIRED_PORT = "pairedPort";

    private static final int PAGE_MAX_WIDTH_DP = 640;
    // The sides of the pages, clear of the arrows (48 dp, 20 from the edge, 8 of gap)
    private static final int SIDE_DP = 76;
    // The left column of the last page: the check, the title and the way to the Home
    private static final int DONE_END_DP = 220;
    private static final int PROBE_COUNT = 30;
    private static final int MAX_BITRATE_MBPS = 500;

    private ApolloColors colors;
    private LinearLayout column;
    private LinearLayout dots;
    private int[] dotColors = new int[0];
    private ValueAnimator dotsAnimator;
    // Finish, at the top right: a tap asks, X held finishes once its ring is full
    private LinearLayout finishButton;
    private View finishGlyph;
    private ApolloWidgets.RingView finishRing;
    private ValueAnimator finishHold;
    // The sound rising while X is held, stopped if it is let go
    private int finishHoldSound;
    private ImageView backArrow;
    private ImageView nextArrow;
    private FrameLayout stage;
    private HintRow hintRow;
    private View page;
    private int step = STEP_WELCOME;

    // The settings of the current step, drawn from the hidden fragment
    private SettingsView settingsView;

    // PCs on the network, by uuid
    private final Map<String, ComputerDetails> computers = new LinkedHashMap<>();
    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private volatile boolean polling;
    private boolean resumed;
    private LinearLayout pcList;
    private TextView pcStatus;
    private String pcMessage;

    // Pairing: a new token makes the running attempt be ignored
    private int pairToken;
    private String pin;
    private ComputerDetails pairing;
    private String pairedName;
    private String pairedHost;
    private int pairedPort;

    private AtomicBoolean testCancelled = new AtomicBoolean();
    private WifiManager.WifiLock highPerfLock;
    private WifiManager.WifiLock lowLatencyLock;

    // The settings the fragment built last: the Settings of the tour show them in full
    private PreferenceScreen lastScreen;
    private TourIllustration tour;
    private LinearLayout tourButton;
    private ImageView tourIcon;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            ComputerManagerService.ComputerManagerBinder localBinder = (ComputerManagerService.ComputerManagerBinder) binder;
            new Thread(() -> {
                localBinder.waitForReady();
                runOnUiThread(() -> {
                    managerBinder = localBinder;
                    startPolling();
                });
            }).start();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            managerBinder = null;
            polling = false;
        }
    };

    /** Opens the setup on the first start of the app, when no PC is saved yet, and again until it is finished. */
    public static boolean startIfFirstRun(Activity from) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(from);
        if (prefs.getBoolean(PREF_DONE, false)) {
            return false;
        }
        if (prefs.getBoolean(PREF_STARTED, false)) {
            from.startActivity(new Intent(from, SetupWizardActivity.class));
            from.overridePendingTransition(R.anim.apollo_fade_in, R.anim.apollo_fade_out);
            return true;
        }
        boolean noPcs;
        ComputerDatabaseManager database = new ComputerDatabaseManager(from);
        try {
            noPcs = database.getAllComputers().isEmpty();
        } finally {
            database.close();
        }
        if (!noPcs) {
            // Someone who already uses the app, from before the setup existed
            prefs.edit().putBoolean(PREF_DONE, true).apply();
            return false;
        }
        prefs.edit().putBoolean(PREF_STARTED, true).apply();
        from.startActivity(new Intent(from, SetupWizardActivity.class));
        from.overridePendingTransition(R.anim.apollo_fade_in, R.anim.apollo_fade_out);
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiHelper.setLocale(this);
        colors = ApolloColors.dark(this);
        if (savedInstanceState != null) {
            step = savedInstanceState.getInt(STATE_STEP, STEP_WELCOME);
            pairedName = savedInstanceState.getString(STATE_PAIRED_NAME);
            pairedHost = savedInstanceState.getString(STATE_PAIRED_HOST);
            pairedPort = savedInstanceState.getInt(STATE_PAIRED_PORT);
        }

        FrameLayout root = new FrameLayout(this);
        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // At the top: where the setup is, and Finish
        FrameLayout header = new FrameLayout(this);
        dots = new LinearLayout(this);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams dotsParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START);
        dotsParams.leftMargin = dp(32);
        header.addView(dots, dotsParams);
        finishButton = finishButton();
        FrameLayout.LayoutParams finishParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40),
                Gravity.END | Gravity.CENTER_VERTICAL);
        finishParams.rightMargin = dp(20);
        header.addView(finishButton, finishParams);
        column.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        stage = new FrameLayout(this);
        column.addView(stage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // The settings fragment builds its preferences here, hidden; the settings steps draw them
        FrameLayout settingsHolder = new FrameLayout(this);
        settingsHolder.setId(R.id.stream_settings);
        settingsHolder.setVisibility(View.GONE);
        column.addView(settingsHolder, new LinearLayout.LayoutParams(0, 0));

        hintRow = ScreenHints.attach(this, column);
        // B still goes back, but LB/RB say it already
        hintRow.hide(KeyEvent.KEYCODE_BUTTON_B, getString(R.string.apollo_hint_back));

        // Back and next for touch, on the sides of the page (a gamepad has LB/RB)
        backArrow = arrowButton(R.drawable.ic_apollo_arrow_left, v -> back());
        root.addView(backArrow, arrowParams(Gravity.START));
        nextArrow = arrowButton(R.drawable.ic_apollo_arrow_right, v -> next());
        root.addView(nextArrow, arrowParams(Gravity.END));

        setContentView(root);
        UiHelper.notifyNewRootView(this);
        ApolloUi.padForCutout(column);

        bindService(new Intent(this, ComputerManagerService.class), serviceConnection, Service.BIND_AUTO_CREATE);
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(sharpeningListener);
        // Android 13+ sends back here instead of onBackPressed(); the popups put theirs above it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }

        if (step == STEP_PIN || (step == STEP_NETWORK && pairedHost == null)) {
            step = STEP_PC;
        }
        showStep(step, 0);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_STEP, step == STEP_PIN ? STEP_PC : step);
        outState.putString(STATE_PAIRED_NAME, pairedName);
        outState.putString(STATE_PAIRED_HOST, pairedHost);
        outState.putInt(STATE_PAIRED_PORT, pairedPort);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        startPolling();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        stopPolling(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        testCancelled.set(true);
        releaseWifiLocks();
        // A pairing still running is ignored
        pairToken++;
        unbindService(serviceConnection);
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(sharpeningListener);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.apollo_fade_in, R.anim.apollo_fade_out);
    }

    private int dp(float value) {
        return ApolloUi.dp(this, value);
    }

    private TextView text(CharSequence value, float sizeSp, int color, boolean medium) {
        return ApolloUi.text(this, value, sizeSp, color, medium);
    }

    // --- Steps

    // The steps in order; the connection test only once a PC is paired, the AYN buttons only on AYN handhelds
    private List<Integer> flow() {
        List<Integer> steps = new ArrayList<>();
        steps.add(STEP_WELCOME);
        steps.add(STEP_APPEARANCE);
        steps.add(STEP_PC);
        if (pairedHost != null) {
            steps.add(STEP_NETWORK);
        }
        steps.add(STEP_QUALITY);
        if (ControllerHandler.IS_AYN_DEVICE) {
            steps.add(STEP_AYN);
        }
        steps.add(STEP_EXPLORE);
        steps.add(STEP_DONE);
        return steps;
    }

    private void next() {
        List<Integer> steps = flow();
        int current = steps.indexOf(step == STEP_PIN ? STEP_PC : step);
        if (current >= 0 && current + 1 < steps.size()) {
            showStep(steps.get(current + 1), 1);
        }
    }

    private void back() {
        if (step == STEP_PIN) {
            cancelPairing();
            return;
        }
        List<Integer> steps = flow();
        int current = steps.indexOf(step);
        if (current > 0) {
            showStep(steps.get(current - 1), -1);
        }
    }

    private void showStep(int newStep, int direction) {
        if (step == STEP_NETWORK && newStep != STEP_NETWORK) {
            testCancelled.set(true);
            releaseWifiLocks();
        }
        step = newStep;
        settingsView = null;
        tour = null;
        tourButton = null;
        tourIcon = null;
        pcList = null;
        pcStatus = null;

        View old = page;
        if (old instanceof ViewGroup) {
            // While it fades out the D-pad can no longer reach it
            ((ViewGroup) old).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
        if (old != null) {
            // From now (its animator still holds the delay it came in with), so the new title never shows over it
            old.animate().cancel();
            old.animate().alpha(0f).translationX(-direction * dp(24)).setStartDelay(0).setDuration(ApolloMotion.SHORT)
                    .setInterpolator(ApolloMotion.EMPHASIZED_ACCELERATE).withEndAction(() -> stage.removeView(old)).start();
        }
        page = buildPage(newStep);
        stage.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (direction != 0) {
            page.setAlpha(0f);
            page.setTranslationX(direction * dp(24));
            // Once the old page is gone, so their titles never show over each other
            page.animate().alpha(1f).translationX(0).setStartDelay(ApolloMotion.SHORT).setDuration(ApolloMotion.LONG)
                    .setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE).start();
        }
        updateHeader();
        if (step == STEP_DONE && direction != 0) {
            UiSounds.play(UiSounds.Sound.DONE);
        }
        // LB/RB move between the steps after the welcome, whatever has the focus on the page
        if (step == STEP_WELCOME) {
            hintRow.setLeading();
        } else if (step == STEP_DONE) {
            // Past the end RB does nothing: A goes to the Home
            hintRow.setLeading(new HintRow.Hint(KeyEvent.KEYCODE_BUTTON_L1, getString(R.string.apollo_hint_back)));
        } else {
            hintRow.setLeading(new HintRow.Hint(KeyEvent.KEYCODE_BUTTON_L1, ""),
                    new HintRow.Hint(KeyEvent.KEYCODE_BUTTON_R1, getString(R.string.apollo_setup_steps)));
        }
    }

    private View buildPage(int which) {
        switch (which) {
            case STEP_APPEARANCE:
                return settingsPage(getString(R.string.apollo_setup_appearance),
                        ThemeColors.PREF_COLOR, ThemeColors.PREF_BACKGROUND, UiSounds.PREF_MUSIC, UiSounds.PREF_MUSIC_VOLUME,
                        UiSounds.PREF_EFFECTS, UiSounds.PREF_EFFECTS_VOLUME);
            case STEP_PC:
                return pcPage();
            case STEP_PIN:
                return pinPage();
            case STEP_NETWORK:
                return networkPage();
            case STEP_QUALITY:
                return vulkanAvailable()
                        ? settingsPage(getString(R.string.apollo_setup_quality), "list_resolution", "list_fps",
                                PreferenceConfiguration.BITRATE_PREF_STRING, PreferenceConfiguration.SHARPENING_PREF_STRING,
                                PreferenceConfiguration.SHARPENING_STRENGTH_PREF_STRING)
                        : settingsPage(getString(R.string.apollo_setup_quality), "list_resolution", "list_fps",
                                PreferenceConfiguration.BITRATE_PREF_STRING);
            case STEP_AYN:
                return settingsPage(getString(R.string.apollo_section_ayn_buttons),
                        "list_ayn_back_button", "list_ayn_m1_button", "list_ayn_m2_button");
            case STEP_EXPLORE:
                return explorePage();
            case STEP_DONE:
                return donePage();
            default:
                return welcomePage();
        }
    }

    /**
     * The dots of the steps between the welcome and the end, the current one longer: on a step change the
     * new current one stretches while the old one shrinks, and the dots passed take the main color.
     */
    private void updateHeader() {
        finishButton.setVisibility(step == STEP_DONE ? View.INVISIBLE : View.VISIBLE);
        // Without a gamepad only the label shows, with the same margin on both sides
        boolean gamepad = InputMode.isGamepadConnected();
        finishGlyph.setVisibility(gamepad ? View.VISIBLE : View.GONE);
        finishButton.setPadding(gamepad ? dp(8) : dp(16), 0, dp(16), 0);
        showArrow(backArrow, step != STEP_WELCOME);
        showArrow(nextArrow, step != STEP_WELCOME && step != STEP_PIN && step != STEP_DONE);
        boolean shown = step != STEP_WELCOME && step != STEP_DONE;
        dots.animate().cancel();
        dots.animate().alpha(shown ? 1f : 0f).setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD).start();
        if (!shown) {
            return;
        }
        List<Integer> steps = new ArrayList<>();
        for (int s : flow()) {
            if (s != STEP_WELCOME && s != STEP_DONE) {
                steps.add(s);
            }
        }
        int current = steps.indexOf(step == STEP_PIN ? STEP_PC : step);
        int count = steps.size();
        int[] toWidth = new int[count];
        int[] toColor = new int[count];
        for (int i = 0; i < count; i++) {
            toWidth[i] = dp(i == current ? 22 : 6);
            toColor[i] = i <= current ? colors.primary : colors.outlineVariant;
        }
        if (dotsAnimator != null) {
            dotsAnimator.cancel();
        }
        // A step added or removed (the connection test after a pairing): the dots are made again, from the welcome without a move
        if (dots.getChildCount() != count) {
            dots.removeAllViews();
            dotColors = new int[count];
            for (int i = 0; i < count; i++) {
                View dot = new View(this);
                dot.setBackground(ApolloUi.roundRect(toColor[i], dp(3)));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(toWidth[i], dp(6));
                params.rightMargin = dp(6);
                dots.addView(dot, params);
                dotColors[i] = toColor[i];
            }
            return;
        }
        int[] fromWidth = new int[count];
        int[] fromColor = dotColors.clone();
        for (int i = 0; i < count; i++) {
            fromWidth[i] = dots.getChildAt(i).getLayoutParams().width;
        }
        dotsAnimator = ValueAnimator.ofFloat(0f, 1f);
        dotsAnimator.setDuration(ApolloMotion.LONG);
        dotsAnimator.setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE);
        dotsAnimator.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            for (int i = 0; i < count; i++) {
                View dot = dots.getChildAt(i);
                dot.getLayoutParams().width = Math.round(fromWidth[i] + (toWidth[i] - fromWidth[i]) * f);
                dotColors[i] = ApolloColors.blend(fromColor[i], toColor[i], f);
                ((GradientDrawable) dot.getBackground()).setColor(dotColors[i]);
            }
            dots.requestLayout();
        });
        dotsAnimator.start();
    }

    private void setHints(HintRow.Hint... hints) {
        hintRow.setFallback(hints);
    }

    private HintRow.Hint hint(int key, int label) {
        return HintRow.hint(this, key, label);
    }

    // --- Building blocks

    // A column in the middle of the screen, as wide as it reads well
    private LinearLayout pageColumn() {
        LinearLayout content = new LinearLayout(this) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = Math.min(MeasureSpec.getSize(widthMeasureSpec), dp(PAGE_MAX_WIDTH_DP));
                super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);
            }
        };
        content.setOrientation(LinearLayout.VERTICAL);
        return content;
    }

    // A page that scrolls when the screen is short, with its column centered
    private View scrollingPage(LinearLayout content, boolean centerVertically) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        ApolloUi.fadeEdges(scroll);
        FrameLayout center = new FrameLayout(this);
        center.setPadding(dp(SIDE_DP), dp(4), dp(SIDE_DP), dp(16));
        center.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                centerVertically ? Gravity.CENTER : Gravity.CENTER_HORIZONTAL));
        scroll.addView(center, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private TextView title(int textRes) {
        return title(getString(textRes));
    }

    private TextView title(CharSequence value) {
        TextView title = text(value, 28, colors.onSurface, true);
        title.setPadding(0, 0, 0, dp(12));
        return title;
    }

    private TextView textButton(CharSequence label, View.OnClickListener listener) {
        TextView button = text(label, 14, colors.primary, true);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setPadding(dp(18), 0, dp(18), 0);
        button.setFocusable(true);
        button.setClickable(true);
        button.setBackground(ApolloUi.stateLayer(colors.surfaceContainerHighest, Color.TRANSPARENT, dp(20)));
        button.setOnClickListener(listener);
        return button;
    }

    // A round tonal icon button with a Material arrow, lighter while pressed
    private ImageView arrowButton(int iconRes, View.OnClickListener listener) {
        ImageView button = new ImageView(this);
        button.setImageResource(iconRes);
        button.setImageTintList(ColorStateList.valueOf(colors.onSurface));
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setFocusable(false);
        button.setClickable(true);
        StateListDrawable fill = new StateListDrawable();
        fill.setExitFadeDuration((int) ApolloMotion.MEDIUM);
        fill.addState(new int[]{android.R.attr.state_pressed}, ApolloUi.roundRect(colors.surfaceContainerHighest, dp(24)));
        fill.addState(new int[]{}, ApolloUi.roundRect(colors.surfaceContainerHigh, dp(24)));
        button.setBackground(fill);
        button.setOnClickListener(listener);
        return button;
    }

    private FrameLayout.LayoutParams arrowParams(int side) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(48), dp(48), side | Gravity.CENTER_VERTICAL);
        params.leftMargin = dp(20);
        params.rightMargin = dp(20);
        return params;
    }

    private void showArrow(View arrow, boolean shown) {
        arrow.setEnabled(shown);
        arrow.animate().cancel();
        arrow.animate().alpha(shown ? 1f : 0f).setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD)
                .withEndAction(() -> arrow.setVisibility(arrow.isEnabled() ? View.VISIBLE : View.INVISIBLE)).start();
        if (shown) {
            arrow.setVisibility(View.VISIBLE);
        }
    }

    // Filled, a lighter fill with the focus, as in the dialogs
    private TextView filledButton(CharSequence label, View.OnClickListener listener) {
        TextView button = text(label, 14, colors.onPrimary, true);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setPadding(dp(24), 0, dp(24), 0);
        button.setFocusable(true);
        button.setClickable(true);
        StateListDrawable fill = new StateListDrawable();
        fill.setEnterFadeDuration((int) ApolloMotion.SHORT);
        fill.setExitFadeDuration((int) ApolloMotion.MEDIUM);
        int lighter = ApolloColors.blend(colors.primary, Color.WHITE, 0.25f);
        fill.addState(new int[]{android.R.attr.state_pressed}, ApolloUi.roundRect(lighter, dp(20)));
        fill.addState(new int[]{android.R.attr.state_focused}, ApolloUi.roundRect(lighter, dp(20)));
        fill.addState(new int[]{}, ApolloUi.roundRect(colors.primary, dp(20)));
        button.setBackground(fill);
        button.setOnClickListener(listener);
        return button;
    }

    // Buttons at the right end of a page
    private LinearLayout buttonRow(View... buttons) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        for (View button : buttons) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
            params.leftMargin = dp(8);
            row.addView(button, params);
        }
        return row;
    }

    private void addWithTopMargin(LinearLayout parent, View child, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topDp);
        parent.addView(child, params);
    }

    // A small spinner and a line of text
    private LinearLayout statusLine(CharSequence value, int color, boolean spinning) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        if (spinning) {
            android.widget.ProgressBar spinner = new android.widget.ProgressBar(this);
            spinner.setIndeterminate(true);
            spinner.setIndeterminateTintList(ColorStateList.valueOf(colors.primary));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(14), dp(14));
            params.rightMargin = dp(8);
            line.addView(spinner, params);
        }
        TextView label = text(value, 14, color, false);
        label.setTag("label");
        line.addView(label);
        return line;
    }

    /**
     * Whether the page should give the focus at once, as for a gamepad: the window is out of touch mode, the
     * last input was a gamepad, or this is the first page with a gamepad connected (a handheld). Otherwise
     * the first press of A would only bring the focus.
     */
    private boolean wantsGamepadFocus() {
        return !getWindow().getDecorView().isInTouchMode() || InputMode.isGamepad()
                || (step == STEP_WELCOME && InputMode.isGamepadConnected());
    }

    private void focusFirst(View target) {
        if (target == null) {
            return;
        }
        ApolloUi.focusByDefault(this, target);
        if (wantsGamepadFocus()) {
            // Leaves touch mode too, which a plain request does nothing in
            target.requestFocusFromTouch();
        }
    }

    // --- Welcome

    private View welcomePage() {
        LinearLayout content = pageColumn();
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        content.addView(icon, new LinearLayout.LayoutParams(dp(88), dp(88)));
        // The name as in the Home: "Vibe" in the accent color
        SpannableString brand = new SpannableString("MoonVibe");
        brand.setSpan(new android.text.style.ForegroundColorSpan(colors.primary), 4, 8, 0);
        TextView name = text(brand, 40, colors.onSurface, true);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(16), 0, 0);
        content.addView(name);
        TextView subtitle = text(getString(R.string.apollo_setup_welcome_subtitle), 16, colors.onSurfaceVariant, false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(4), 0, 0);
        content.addView(subtitle);
        TextView start = filledButton(getString(R.string.apollo_setup_start), v -> next());
        start.setPadding(dp(32), 0, dp(32), 0);
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        startParams.topMargin = dp(32);
        content.addView(start, startParams);

        setHints(hint(KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_setup_start));
        View result = scrollingPage(content, true);
        start.post(() -> focusFirst(start));
        return result;
    }

    // --- Settings steps: the real settings rows, from the hidden fragment

    private View settingsPage(CharSequence titleText, String... keys) {
        LinearLayout content = pageColumn();
        content.addView(title(titleText));
        settingsView = SettingsView.singlePage(this, colors, titleText, keys);
        settingsView.hidePageTitle();
        // The sharpening works with the Vulkan renderer: here turning it on picks that renderer (sharpeningListener),
        // so its rows never show turned off for a reason the page does not show
        settingsView.ignoreRule(PreferenceConfiguration.SHARPENING_PREF_STRING, PreferenceConfiguration.SHARPENING_STRENGTH_PREF_STRING);
        settingsView.setTitleOverride(PreferenceConfiguration.SHARPENING_PREF_STRING, getString(R.string.apollo_setup_sharpening_vulkan));
        // As tall as its settings, and never taller than the page
        FrameLayout fit = new FrameLayout(this) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                View child = getChildAt(0);
                int available = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE
                        : MeasureSpec.getSize(heightMeasureSpec);
                child.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int height = Math.max(0, Math.min(child.getMeasuredHeight(), available));
                child.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height);
            }
        };
        fit.addView(settingsView);
        content.addView(fit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        reloadSettings();

        setHints();
        FrameLayout holder = new FrameLayout(this);
        holder.setPadding(dp(SIDE_DP), dp(4), dp(SIDE_DP), dp(8));
        holder.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL));
        return holder;
    }

    @Override
    public ProfileEditor profileEditor() {
        return null;
    }

    @Override
    public ActiveProfileSettings withProfile() {
        return null;
    }

    @Override
    public void onPreferencesReady(PreferenceScreen screen) {
        lastScreen = screen;
        if (settingsView != null) {
            settingsView.setScreen(screen);
            // The first setting takes the focus, not what kept it while the page changed
            settingsView.focusFirstRow(wantsGamepadFocus());
        }
    }

    // Sharpening turned on in the setup also picks the Vulkan renderer it needs (PyroWave always uses it)
    private final SharedPreferences.OnSharedPreferenceChangeListener sharpeningListener = (prefs, key) -> {
        if (PreferenceConfiguration.SHARPENING_PREF_STRING.equals(key) && prefs.getBoolean(key, false)
                && !"vulkan".equals(prefs.getString("video_renderer", "auto"))
                && !"forcepyrowave".equals(prefs.getString("video_format", "auto"))) {
            prefs.edit().putString("video_renderer", "vulkan").apply();
        }
    };

    // The sharpening runs in the Vulkan renderer: without it on this device the setup leaves it out
    private static boolean vulkanAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && VulkanRendererBridge.isSupported();
    }

    @Override
    public void reloadSettings() {
        getFragmentManager().beginTransaction().replace(R.id.stream_settings, new StreamSettings.SettingsFragment())
                .commitAllowingStateLoss();
    }

    // --- PC

    private View pcPage() {
        LinearLayout content = pageColumn();
        content.addView(title(R.string.apollo_setup_pc_title));
        content.addView(statusLine(getString(R.string.apollo_setup_pc_searching), colors.onSurfaceVariant, true));
        // Why the last pairing did not work
        pcStatus = text("", 14, colors.error, false);
        pcStatus.setVisibility(View.GONE);
        addWithTopMargin(content, pcStatus, 6);

        pcList = new LinearLayout(this);
        pcList.setOrientation(LinearLayout.VERTICAL);
        addWithTopMargin(content, pcList, 14);

        View divider = new View(this);
        divider.setBackgroundColor(colors.outlineVariant);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        dividerParams.setMargins(dp(16), dp(8), dp(16), dp(8));
        content.addView(divider, dividerParams);

        View addRow = listRow(R.drawable.ic_apollo_add, getString(R.string.apollo_setup_pc_add_ip), null, null, colors.onSurfaceVariant,
                v -> startActivity(new Intent(this, AddComputerManually.class)));
        HintRow.set(addRow, hint(KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_open));
        content.addView(addRow);

        setHints();
        if (pcMessage != null) {
            pcStatus.setText(pcMessage);
            pcStatus.setVisibility(View.VISIBLE);
            pcMessage = null;
        }
        refreshPcList();
        View result = scrollingPage(content, false);
        result.post(() -> focusFirst(pcList != null && pcList.getChildCount() > 0 ? pcList.getChildAt(0) : addRow));
        return result;
    }

    // A row of a list: icon, name, a line under it and a value on the right
    private LinearLayout listRow(int iconRes, CharSequence name, CharSequence detail, CharSequence value, int iconColor,
                                 View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));
        row.setPadding(dp(16), dp(8), dp(16), dp(8));
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(ApolloUi.stateLayer(colors.surfaceContainerHighest, Color.TRANSPARENT, dp(ApolloUi.ROW_RADIUS_DP)));
        row.setOnClickListener(listener);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(iconColor));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(24), dp(24));
        iconParams.rightMargin = dp(16);
        row.addView(icon, iconParams);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView nameView = text(name, 16, colors.onSurface, detail != null);
        nameView.setSingleLine(true);
        nameView.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(nameView);
        if (detail != null) {
            texts.addView(text(detail, 13, colors.onSurfaceVariant, false));
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (value != null) {
            row.addView(text(value, 13, colors.onSurfaceVariant, false));
        }
        return row;
    }

    private void refreshPcList() {
        if (pcList == null) {
            return;
        }
        // The polling brings the same PCs every few seconds: a row is rebuilt only when what it shows changed,
        // so the focus stays where it is
        View focused = pcList.findFocus();
        int index = 0;
        for (ComputerDetails computer : computers.values()) {
            boolean offline = computer.state == ComputerDetails.State.OFFLINE;
            boolean paired = computer.pairState == PairState.PAIRED;
            ComputerDetails.AddressTuple address = computer.activeAddress != null ? computer.activeAddress
                    : computer.localAddress != null ? computer.localAddress : computer.manualAddress;
            String name = computer.name != null ? computer.name : "PC";
            String detail = address != null ? address.address : null;
            String state = getString(offline ? R.string.apollo_setup_pc_offline
                    : paired ? R.string.apollo_setup_pc_paired : R.string.apollo_setup_pc_unpaired);
            String signature = name + "\n" + detail + "\n" + state;
            View existing = index < pcList.getChildCount() ? pcList.getChildAt(index) : null;
            if (existing != null && computer.uuid != null && computer.uuid.equals(existing.getTag())
                    && signature.equals(existing.getContentDescription())) {
                existing.setOnClickListener(v -> onPcClicked(computer));
                index++;
                continue;
            }
            LinearLayout row = listRow(R.drawable.ic_apollo_desktop, name, detail, state, offline ? colors.outline : colors.primary,
                    v -> onPcClicked(computer));
            row.setTag(computer.uuid);
            row.setContentDescription(signature);
            HintRow.set(row, hint(KeyEvent.KEYCODE_BUTTON_A, paired ? R.string.apollo_setup_next : R.string.apollo_setup_pair));
            boolean hadFocus = false;
            if (existing != null && computer.uuid != null && computer.uuid.equals(existing.getTag())) {
                // The same PC with something new to show: the new row takes its place and its focus
                hadFocus = existing == focused;
                pcList.removeViewAt(index);
            }
            pcList.addView(row, index);
            if (hadFocus) {
                row.requestFocus();
            }
            index++;
        }
        // PCs no longer found
        while (pcList.getChildCount() > index) {
            pcList.removeViewAt(index);
        }
        hintRow.refresh();
    }

    private void showPcMessage(String message) {
        if (pcStatus != null) {
            pcStatus.setText(message);
            pcStatus.setVisibility(message != null ? View.VISIBLE : View.GONE);
        }
    }

    private void onPcClicked(ComputerDetails computer) {
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            showPcMessage(getString(R.string.pair_pc_offline));
            return;
        }
        if (managerBinder == null) {
            showPcMessage(getString(R.string.error_manager_not_running));
            return;
        }
        if (computer.pairState == PairState.PAIRED) {
            usePaired(computer);
            return;
        }
        pair(computer);
    }

    private void usePaired(ComputerDetails computer) {
        pairedName = computer.name;
        pairedHost = computer.activeAddress != null ? computer.activeAddress.address : null;
        pairedPort = computer.activeAddress != null ? computer.activeAddress.port : 0;
        showStep(pairedHost != null ? STEP_NETWORK : STEP_QUALITY, 1);
    }

    // As in the Home, without the dialog: the PIN shows on a step of its own
    private void pair(ComputerDetails computer) {
        int token = ++pairToken;
        pairing = computer;
        showPcMessage(null);
        String pinString = PairingManager.generatePinString();
        ComputerManagerService.ComputerManagerBinder binder = managerBinder;
        new Thread(() -> {
            String message = null;
            boolean success = false;
            try {
                stopPolling(true);
                NvHTTP http = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer), computer.httpsPort,
                        binder.getUniqueId(), computer.serverCert, PlatformBinding.getCryptoProvider(this));
                if (http.getPairState() == PairState.PAIRED) {
                    success = true;
                } else {
                    runOnUiThread(() -> {
                        if (token == pairToken && !isFinishing()) {
                            pin = pinString;
                            showStep(STEP_PIN, 1);
                        }
                    });
                    PairingManager pm = http.getPairingManager();
                    PairState state = pm.pair(http.getServerInfo(true), pinString);
                    if (state == PairState.PIN_WRONG) {
                        message = getString(R.string.pair_incorrect_pin);
                    } else if (state == PairState.FAILED) {
                        message = getString(computer.runningGameId != 0 ? R.string.pair_pc_ingame : R.string.pair_fail);
                    } else if (state == PairState.ALREADY_IN_PROGRESS) {
                        message = getString(R.string.pair_already_in_progress);
                    } else if (state == PairState.PAIRED) {
                        success = true;
                        ComputerDetails polled = binder.getComputer(computer.uuid);
                        if (polled != null) {
                            polled.serverCert = pm.getPairedCert();
                        }
                        binder.invalidateStateForComputer(computer.uuid);
                    }
                }
            } catch (UnknownHostException e) {
                message = getString(R.string.error_unknown_host);
            } catch (FileNotFoundException e) {
                message = getString(R.string.error_404);
            } catch (XmlPullParserException | IOException e) {
                LimeLog.warning("Setup: pairing failed: " + e);
                message = e.getMessage();
            }

            String result = message;
            boolean paired = success;
            runOnUiThread(() -> {
                startPolling();
                if (token != pairToken || isFinishing()) {
                    return;
                }
                if (paired) {
                    usePaired(computer);
                } else {
                    UiSounds.play(UiSounds.Sound.ERROR);
                    pcMessage = result != null ? result : getString(R.string.pair_fail);
                    showStep(STEP_PC, -1);
                }
            });
        }, "SetupPairing").start();
    }

    private void cancelPairing() {
        pairToken++;
        pairing = null;
        startPolling();
        showStep(STEP_PC, -1);
    }

    private View pinPage() {
        LinearLayout content = pageColumn();
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        ComputerDetails computer = pairing;
        String address = computer != null && computer.activeAddress != null ? computer.activeAddress.address : "";
        TextView pc = text(computer != null ? computer.name + " · " + address : "", 14, colors.onSurfaceVariant, false);
        content.addView(pc);
        TextView titleView = text(getString(R.string.apollo_setup_pin_title), 28, colors.onSurface, true);
        titleView.setPadding(0, dp(2), 0, 0);
        content.addView(titleView);

        LinearLayout digits = new LinearLayout(this);
        digits.setOrientation(LinearLayout.HORIZONTAL);
        String value = pin != null ? pin : "";
        for (int i = 0; i < value.length(); i++) {
            TextView digit = text(String.valueOf(value.charAt(i)), 44, colors.onSurface, true);
            digit.setTypeface(android.graphics.Typeface.create("monospace", android.graphics.Typeface.BOLD));
            digit.setGravity(Gravity.CENTER);
            digit.setBackground(ApolloUi.roundRect(colors.surfaceContainer, dp(12)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(68), dp(84));
            params.leftMargin = i > 0 ? dp(12) : 0;
            digits.addView(digit, params);
        }
        LinearLayout.LayoutParams digitsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        digitsParams.topMargin = dp(24);
        content.addView(digits, digitsParams);

        TextView where = text(getString(R.string.apollo_setup_pin_where, "https://" + address + ":47990"), 14,
                colors.onSurfaceVariant, false);
        where.setGravity(Gravity.CENTER);
        where.setPadding(0, dp(22), 0, 0);
        content.addView(where);
        LinearLayout waiting = statusLine(getString(R.string.apollo_setup_pin_waiting), colors.primary, true);
        LinearLayout.LayoutParams waitingParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        waitingParams.topMargin = dp(16);
        content.addView(waiting, waitingParams);
        TextView cancel = textButton(getString(R.string.apollo_setup_cancel), v -> cancelPairing());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        cancelParams.topMargin = dp(16);
        content.addView(cancel, cancelParams);

        setHints();
        View result = scrollingPage(content, true);
        cancel.post(() -> focusFirst(cancel));
        return result;
    }

    private void startPolling() {
        if (managerBinder == null || polling || !resumed) {
            return;
        }
        polling = true;
        managerBinder.startPolling(new ComputerManagerListener() {
            @Override
            public void notifyComputerUpdated(ComputerDetails details, boolean isFreshPoll) {
                runOnUiThread(() -> {
                    if (details.uuid == null) {
                        return;
                    }
                    computers.put(details.uuid, details);
                    if (step == STEP_PC) {
                        refreshPcList();
                    }
                });
            }
        });
    }

    private void stopPolling(boolean wait) {
        ComputerManagerService.ComputerManagerBinder binder = managerBinder;
        if (binder == null || !polling) {
            return;
        }
        polling = false;
        binder.stopPolling();
        if (wait) {
            binder.waitForPollingStopped();
        }
    }

    // --- Connection test, as the one of the PC menu: the link, the round trip, a bitrate set from them

    private View networkPage() {
        LinearLayout content = pageColumn();
        content.addView(statusLine(getString(R.string.apollo_setup_paired, pairedName), colors.onSurfaceVariant, false));
        TextView titleView = title(R.string.apollo_nettest_checking);
        titleView.setPadding(0, dp(2), 0, dp(2));
        content.addView(titleView);
        LinearLayout progress = statusLine(getString(R.string.apollo_nettest_latency_waiting), colors.onSurfaceVariant, true);
        content.addView(progress);
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        content.addView(results);

        TextView retry = textButton(getString(R.string.apollo_setup_net_retry), v -> showStep(STEP_NETWORK, 0));
        addWithTopMargin(content, buttonRow(retry), 18);

        setHints(hint(KeyEvent.KEYCODE_BUTTON_Y, R.string.apollo_setup_net_retry));
        runNetworkTest(titleView, progress, results);
        View result = scrollingPage(content, true);
        retry.post(() -> focusFirst(retry));
        return result;
    }

    private void runNetworkTest(TextView titleView, LinearLayout progress, LinearLayout results) {
        testCancelled.set(true);
        AtomicBoolean token = new AtomicBoolean();
        testCancelled = token;
        acquireWifiLocks();
        TextView progressLabel = progress.findViewWithTag("label");
        NetworkProbe.Link link = NetworkProbe.readLink(this);
        String host = pairedHost;
        int port = pairedPort;
        new Thread(() -> {
            NetworkProbe.Latency latency = NetworkProbe.measureLatency(host, port, PROBE_COUNT, token, soFar -> {
                int received = soFar.received;
                int sent = soFar.sent;
                runOnUiThread(() -> {
                    if (!token.get()) {
                        progressLabel.setText(received > 0
                                ? getString(R.string.apollo_nettest_latency_progress, received, PROBE_COUNT, formatMs(soFar.averageMs))
                                : getString(R.string.apollo_nettest_latency_progress_none, sent, PROBE_COUNT));
                    }
                });
            });
            runOnUiThread(() -> {
                if (token.get()) {
                    return;
                }
                releaseWifiLocks();
                showNetworkResult(link, latency, titleView, progress, results);
            });
        }, "SetupNetworkTest").start();
    }

    private void showNetworkResult(NetworkProbe.Link link, NetworkProbe.Latency latency, TextView titleView,
                                   LinearLayout progress, LinearLayout results) {
        boolean pcAnswers = latency.received > 0 && latency.received * 2 >= latency.sent;
        int mbps = pcAnswers ? NetworkProbe.recommendBitrate(link, latency, MAX_BITRATE_MBPS).mbps : 0;
        if (mbps > 0) {
            // The quality step shows it, the user can still change it there
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putInt(PreferenceConfiguration.BITRATE_PREF_STRING, mbps * 1000).apply();
        }

        titleView.setText(verdict(link, latency, pcAnswers, mbps));
        TextView subtitle = (TextView) progress.findViewWithTag("label");
        progress.removeAllViews();
        subtitle.setText(TextUtils.join(" · ", linkChips(link)));
        progress.addView(subtitle);

        LinearLayout tiles = new LinearLayout(this);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        tiles.setBaselineAligned(false);
        boolean wifi = link.type == NetworkProbe.TYPE_WIFI;
        boolean wired = link.type == NetworkProbe.TYPE_ETHERNET;
        String linkValue = wifi ? String.valueOf(link.rxMbps) : wired && link.ethernetMbps > 0 ? String.valueOf(link.ethernetMbps) : "—";
        addTile(tiles, getString(R.string.apollo_setup_net_link), linkValue, "Mbps");
        addTile(tiles, getString(R.string.apollo_setup_net_signal), wifi ? String.valueOf(link.rssi) : "—", wifi ? "dBm" : "");
        addTile(tiles, getString(R.string.apollo_nettest_fact_latency), latency.received > 0 ? formatMs(latency.averageMs) : "—", "ms");
        addWithTopMargin(results, tiles, 20);

        if (mbps > 0) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(16), dp(12), dp(16), dp(12));
            card.setBackground(ApolloUi.roundRect(colors.secondaryContainer, dp(12)));
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.addView(text(getString(R.string.apollo_nettest_recommended), 12, colors.onSecondaryContainer, false));
            texts.addView(text(getString(R.string.apollo_nettest_recommended_value, mbps), 20, colors.onSecondaryContainer, true));
            card.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            card.addView(text(getString(R.string.apollo_setup_net_applied), 13, colors.onSecondaryContainer, false));
            addWithTopMargin(results, card, 12);
        }
        results.setAlpha(0f);
        results.setTranslationY(dp(8));
        results.animate().alpha(1f).translationY(0).setDuration(ApolloMotion.LONG)
                .setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE).start();
    }

    private void addTile(LinearLayout row, String label, String value, String unit) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(16), dp(12), dp(16), dp(12));
        tile.setBackground(ApolloUi.roundRect(colors.surfaceContainer, dp(12)));
        tile.addView(text(label, 12, colors.onSurfaceVariant, false));
        android.text.SpannableStringBuilder styled = new android.text.SpannableStringBuilder(value);
        if (!unit.isEmpty() && !"—".equals(value)) {
            int start = styled.length();
            styled.append(' ').append(unit);
            styled.setSpan(new android.text.style.AbsoluteSizeSpan(14, true), start, styled.length(), 0);
            styled.setSpan(new android.text.style.ForegroundColorSpan(colors.onSurfaceVariant), start, styled.length(), 0);
        }
        tile.addView(text(styled, 26, colors.onSurface, true));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        params.leftMargin = row.getChildCount() > 0 ? dp(10) : 0;
        row.addView(tile, params);
    }

    private List<String> linkChips(NetworkProbe.Link link) {
        List<String> list = new ArrayList<>();
        switch (link.type) {
            case NetworkProbe.TYPE_WIFI:
                if (link.generation > 0) {
                    list.add("Wi-Fi " + link.generation + (link.generation == 6 && link.sixGhz ? "E" : ""));
                }
                if (link.frequencyMhz > 0) {
                    list.add(getString(R.string.apollo_nettest_band, link.bandName(), link.channel()));
                }
                break;
            case NetworkProbe.TYPE_ETHERNET:
                list.add(getString(R.string.apollo_nettest_wired));
                break;
            case NetworkProbe.TYPE_OTHER:
                list.add(getString(R.string.apollo_nettest_other_network));
                break;
            default:
                list.add(getString(R.string.apollo_nettest_no_network));
                break;
        }
        return list;
    }

    private String verdict(NetworkProbe.Link link, NetworkProbe.Latency latency, boolean pcAnswers, int mbps) {
        int res;
        if (link.type == NetworkProbe.TYPE_NONE) {
            res = R.string.apollo_nettest_verdict_no_network;
        } else if (!pcAnswers) {
            res = R.string.apollo_nettest_verdict_no_pc;
        } else if (mbps == 0) {
            res = latency.averageMs <= 10 && latency.lost() == 0 ? R.string.apollo_nettest_verdict_good : R.string.apollo_nettest_verdict_fair;
        } else if (mbps >= 80) {
            res = R.string.apollo_nettest_verdict_excellent;
        } else if (mbps >= 40) {
            res = R.string.apollo_nettest_verdict_good;
        } else if (mbps >= 20) {
            res = R.string.apollo_nettest_verdict_fair;
        } else {
            res = R.string.apollo_nettest_verdict_weak;
        }
        return getString(res);
    }

    private static String formatMs(float ms) {
        return ms < 10 ? String.format(Locale.getDefault(), "%.1f", ms) : String.valueOf(Math.round(ms));
    }

    // Wi-Fi powered up as during a stream, or its power saving adds to the round trip
    private void acquireWifiLocks() {
        if (highPerfLock != null) {
            return;
        }
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        try {
            highPerfLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MoonVibe setup");
            highPerfLock.setReferenceCounted(false);
            highPerfLock.acquire();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                lowLatencyLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "MoonVibe setup");
                lowLatencyLock.setReferenceCounted(false);
                lowLatencyLock.acquire();
            }
        } catch (SecurityException e) {
            // Some Samsung devices refuse it
        }
    }

    private void releaseWifiLocks() {
        if (highPerfLock != null) {
            highPerfLock.release();
            highPerfLock = null;
        }
        if (lowLatencyLock != null) {
            lowLatencyLock.release();
            lowLatencyLock = null;
        }
    }

    // --- Explore: the tour of the app on the handheld, once, a moment after the page shows. The button just
    // pressed shows under it; at the end Y (or a tap) plays it again

    private View explorePage() {
        // The Settings of the tour show all the settings: built here if no settings page came before
        if (lastScreen == null) {
            reloadSettings();
        }
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(SIDE_DP), dp(4), dp(SIDE_DP), dp(4));
        // The title, with the name as in the Home ("Vibe" in the accent color), and play/pause on its right
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPadding(0, 0, 0, dp(12));
        TextView titleView = title(R.string.apollo_setup_explore);
        titleView.setPadding(0, 0, 0, 0);
        SpannableString styledTitle = new SpannableString(titleView.getText());
        int vibe = styledTitle.toString().indexOf("Vibe");
        if (vibe > 0) {
            styledTitle.setSpan(new android.text.style.ForegroundColorSpan(colors.primary), vibe, vibe + 4, 0);
        }
        titleView.setText(styledTitle);
        titleRow.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        tourButton = tourButton();
        titleRow.addView(tourButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
        content.addView(titleRow);
        LinearLayout caption = new LinearLayout(this);
        caption.setOrientation(LinearLayout.HORIZONTAL);
        caption.setGravity(Gravity.CENTER);
        TourIllustration illustration = new TourIllustration(this, colors, () -> lastScreen, pairedName,
                new TourIllustration.Listener() {
                    @Override
                    public void onStep(int key, int labelRes) {
                        crossFade(caption, () -> {
                            caption.removeAllViews();
                            caption.addView(ButtonGlyph.create(SetupWizardActivity.this, colors, key));
                            TextView label = text(getString(labelRes), 14, colors.onSurface, false);
                            label.setPadding(dp(8), 0, 0, 0);
                            caption.addView(label);
                        });
                    }

                    @Override
                    public void onFinished() {
                        crossFade(caption, caption::removeAllViews);
                        // Only for the tour of the page shown, not one still fading out
                        if (tour != null && tour.isFinished()) {
                            showTourPlaying(false);
                        }
                    }
                });
        content.addView(illustration, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        content.addView(caption, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));
        tour = illustration;
        setHints();
        return content;
    }

    // Y or the button: pauses the tour while it plays, goes on when paused, plays it again once over
    private void toggleTour() {
        if (tour == null) {
            return;
        }
        if (tour.isFinished()) {
            tour.replay();
        } else if (tour.isPaused()) {
            tour.resume();
        } else {
            tour.pause();
        }
        showTourPlaying(!tour.isFinished() && !tour.isPaused());
    }

    // A veil like the one of Finish: the Y symbol with a gamepad, then pause while it plays, play otherwise
    private LinearLayout tourButton() {
        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(dp(10), 0, dp(12), 0);
        button.setClickable(true);
        // For touch: a gamepad has Y
        button.setFocusable(false);
        StateListDrawable veil = new StateListDrawable();
        veil.setExitFadeDuration((int) ApolloMotion.MEDIUM);
        veil.addState(new int[]{android.R.attr.state_pressed}, ApolloUi.roundRect(0x29FFFFFF, dp(20)));
        veil.addState(new int[]{}, ApolloUi.roundRect(0x10FFFFFF, dp(20)));
        button.setBackground(veil);
        button.setOnClickListener(v -> toggleTour());
        if (InputMode.isGamepadConnected()) {
            View glyph = ButtonGlyph.create(this, colors, KeyEvent.KEYCODE_BUTTON_Y);
            LinearLayout.LayoutParams glyphParams = new LinearLayout.LayoutParams(dp(18), dp(18));
            glyphParams.rightMargin = dp(8);
            button.addView(glyph, glyphParams);
        }
        tourIcon = new ImageView(this);
        tourIcon.setImageTintList(ColorStateList.valueOf(colors.onSurface));
        button.addView(tourIcon, new LinearLayout.LayoutParams(dp(22), dp(22)));
        tourIcon.setImageResource(R.drawable.ic_apollo_pause);
        button.setContentDescription(getString(R.string.apollo_tour_pause));
        return button;
    }

    // The icon turns into the other one with a short fade
    private void showTourPlaying(boolean playing) {
        if (tourIcon == null) {
            return;
        }
        int icon = playing ? R.drawable.ic_apollo_pause : R.drawable.ic_apollo_play;
        tourButton.setContentDescription(getString(playing ? R.string.apollo_tour_pause : R.string.apollo_tour_play));
        ImageView view = tourIcon;
        view.animate().cancel();
        view.animate().alpha(0f).scaleX(0.7f).scaleY(0.7f).setDuration(ApolloMotion.SHORT)
                .setInterpolator(ApolloMotion.EMPHASIZED_ACCELERATE).withEndAction(() -> {
                    view.setImageResource(icon);
                    view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(ApolloMotion.MEDIUM)
                            .setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE).start();
                }).start();
    }

    // Out with the old content, in with the new one
    private void crossFade(View view, Runnable change) {
        view.animate().cancel();
        view.animate().alpha(0f).setDuration(ApolloMotion.SHORT).setInterpolator(ApolloMotion.EMPHASIZED_ACCELERATE)
                .withEndAction(() -> {
                    change.run();
                    view.animate().alpha(1f).setDuration(ApolloMotion.MEDIUM)
                            .setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE).start();
                }).start();
    }

    // --- Done

    // Side by side, to fit a landscape screen without scrolling: the end and the way out, then the summary
    private View donePage() {
        // As wide as the page lets it, up to what the two columns need; the summary takes what is left
        LinearLayout content = new LinearLayout(this) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = Math.min(MeasureSpec.getSize(widthMeasureSpec), dp(DONE_END_DP + 24 + 400));
                super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);
            }
        };
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout end = new LinearLayout(this);
        end.setOrientation(LinearLayout.VERTICAL);
        end.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView check = new ImageView(this);
        check.setImageResource(R.drawable.ic_menu_check);
        check.setImageTintList(ColorStateList.valueOf(colors.onPrimary));
        check.setBackground(ApolloUi.roundRect(colors.primary, dp(28)));
        check.setPadding(dp(14), dp(14), dp(14), dp(14));
        end.addView(check, new LinearLayout.LayoutParams(dp(56), dp(56)));
        TextView titleView = text(getString(R.string.apollo_setup_done_title), 28, colors.onSurface, true);
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(0, dp(12), 0, 0);
        end.addView(titleView);
        TextView home = filledButton(getString(R.string.apollo_setup_go_home), v -> finishSetup());
        home.setPadding(dp(32), 0, dp(32), 0);
        LinearLayout.LayoutParams homeParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        homeParams.topMargin = dp(20);
        end.addView(home, homeParams);
        TextView reopen = text(getString(R.string.apollo_setup_reopen), 13, colors.outline, false);
        reopen.setGravity(Gravity.CENTER);
        reopen.setPadding(0, dp(10), 0, 0);
        end.addView(reopen);
        content.addView(end, new LinearLayout.LayoutParams(dp(DONE_END_DP), ViewGroup.LayoutParams.WRAP_CONTENT));

        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(this);
        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.VERTICAL);
        summary.setPadding(0, dp(6), 0, dp(6));
        summary.setBackground(ApolloUi.roundRect(colors.surfaceContainer, dp(12)));
        // The PC picked or paired here, or else one paired before (the PC step passed with RB or the arrow)
        String pcName = pairedName;
        for (ComputerDetails computer : computers.values()) {
            if (pcName == null && computer.pairState == PairState.PAIRED) {
                pcName = computer.name;
            }
        }
        summaryRow(summary, R.string.apollo_setup_done_pc, pcName != null ? pcName : getString(R.string.apollo_setup_done_none));
        summaryRow(summary, R.string.apollo_setup_done_stream, config.width + " × " + config.height + " · " + config.fps + " FPS · "
                + Math.max(1, config.bitrate / 1000) + " Mbps");
        summaryRow(summary, R.string.apollo_setup_done_sharpening, config.sharpening
                ? getString(R.string.apollo_setup_done_sharpening_on, config.sharpeningStrength)
                : getString(R.string.apollo_setup_done_off));
        summaryRow(summary, R.string.apollo_setup_appearance, entryName(R.array.theme_color_values, R.array.theme_color_names,
                ThemeColors.color(this)) + " · " + entryName(R.array.app_background_values, R.array.app_background_names,
                ThemeColors.background(this)));
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        summaryParams.leftMargin = dp(24);
        content.addView(summary, summaryParams);

        setHints(hint(KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_setup_go_home));
        View result = scrollingPage(content, true);
        home.post(() -> focusFirst(home));
        return result;
    }

    private void summaryRow(LinearLayout parent, int labelRes, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(16), dp(9), dp(16), dp(9));
        row.addView(text(getString(labelRes), 15, colors.onSurfaceVariant, false));
        // The value on the right, on two lines when the column is narrow
        TextView valueView = text(value, 15, colors.onSurface, false);
        valueView.setGravity(Gravity.END);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        valueParams.leftMargin = dp(16);
        row.addView(valueView, valueParams);
        parent.addView(row);
    }

    private String entryName(int valuesRes, int namesRes, String value) {
        String[] values = getResources().getStringArray(valuesRes);
        String[] names = getResources().getStringArray(namesRes);
        for (int i = 0; i < values.length && i < names.length; i++) {
            if (values[i].equals(value)) {
                return names[i];
            }
        }
        return value;
    }

    // --- Leaving

    /**
     * Finish, on a light veil like the status of the Home: the X symbol with a ring that fills while X is held,
     * as the actions held to confirm in the game menu, and the label. A tap asks first.
     */
    private LinearLayout finishButton() {
        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(dp(8), 0, dp(16), 0);
        button.setClickable(true);
        // For touch: a gamepad has X, and the focus would land here between the pages
        button.setFocusable(false);
        StateListDrawable veil = new StateListDrawable();
        veil.setExitFadeDuration((int) ApolloMotion.MEDIUM);
        veil.addState(new int[]{android.R.attr.state_pressed}, ApolloUi.roundRect(0x29FFFFFF, dp(20)));
        veil.addState(new int[]{}, ApolloUi.roundRect(0x10FFFFFF, dp(20)));
        button.setBackground(veil);
        button.setOnClickListener(v -> confirmSkip());

        FrameLayout badge = new FrameLayout(this);
        finishRing = new ApolloWidgets.RingView(this, colors.primary);
        badge.addView(finishRing, new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));
        badge.addView(ButtonGlyph.create(this, colors, KeyEvent.KEYCODE_BUTTON_X),
                new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        badgeParams.rightMargin = dp(6);
        button.addView(badge, badgeParams);
        // The whole badge goes without a gamepad, so no empty space is left before the label
        finishGlyph = badge;
        button.addView(text(getString(R.string.apollo_setup_skip), 14, colors.onSurface, true));
        return button;
    }

    // X held: the ring fills in a second, then the setup ends
    private void startFinishHold() {
        cancelFinishHold(false);
        finishHold = ValueAnimator.ofFloat(0f, 1f);
        finishHold.setDuration(FINISH_HOLD_MS);
        finishHold.setInterpolator(new LinearInterpolator());
        finishHold.addUpdateListener(a -> finishRing.setProgress((float) a.getAnimatedValue()));
        finishHold.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!cancelled) {
                    finishHold = null;
                    finishSetup();
                }
            }
        });
        finishHold.start();
        finishHoldSound = UiSounds.play(UiSounds.Sound.HOLD);
    }

    // Released too early: the ring empties and a note says to hold it, as in the game menu
    private void cancelFinishHold(boolean showHint) {
        if (finishHold == null) {
            return;
        }
        finishHold.cancel();
        finishHold = null;
        UiSounds.stop(finishHoldSound);
        finishHoldSound = 0;
        finishRing.setProgress(0);
        if (showHint) {
            Toast.makeText(this, getString(R.string.game_menu_hold_hint, ButtonGlyph.faceLabel(KeyEvent.KEYCODE_BUTTON_X)),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmSkip() {
        ApolloDialog dialog = new ApolloDialog(this, getString(R.string.apollo_setup_skip_title), 420);
        TextView message = text(getString(R.string.apollo_setup_skip_message), 14, colors.onSurfaceVariant, false);
        message.setPadding(dp(14), 0, dp(14), 0);
        message.setLineSpacing(0, 1.25f);
        dialog.body.addView(message);
        dialog.buttons(getString(R.string.apollo_setup_cancel), getString(R.string.apollo_setup_skip), () -> {
            dialog.dismiss();
            finishSetup();
        });
        dialog.show();
    }

    private void finishSetup() {
        PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean(PREF_DONE, true).apply();
        finish();
    }

    // --- Keys: X skips, Y does the step's second action, B goes back

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        ScreenHints.onKeyEvent(event);
        // The tour plays the app on the handheld by itself: the D-pad and A never reach it
        if (step == STEP_EXPLORE && tourKey(event.getKeyCode())) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private static boolean tourKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        ScreenHints.onTouchEvent(event);
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getRepeatCount() > 0) {
            return super.onKeyDown(keyCode, event);
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_B:
                // As in the Settings: B closes the settings' own menus first, then goes back
                if (settingsView == null || !settingsView.onButtonB()) {
                    back();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_L1:
                if (pageHasFocus()) {
                    back();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_R1:
                // Not while a pairing waits for its PIN, nor past the end
                if (pageHasFocus() && step != STEP_PIN && step != STEP_DONE) {
                    next();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_X:
                // Held, it finishes the setup
                if (step != STEP_DONE && pageHasFocus()) {
                    startFinishHold();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_Y:
                // Handled on release
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (event.isCanceled()) {
            return super.onKeyUp(keyCode, event);
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_X:
                cancelFinishHold(true);
                return true;
            case KeyEvent.KEYCODE_BUTTON_Y:
                onButtonY();
                return true;
            case KeyEvent.KEYCODE_BUTTON_B:
                return true;
            default:
                return super.onKeyUp(keyCode, event);
        }
    }

    // Not while a popup over the page (a setting's options) has the focus
    private boolean pageHasFocus() {
        View focused = getCurrentFocus();
        for (View v = focused; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            if (v == page) {
                return true;
            }
        }
        return focused == null;
    }

    private void onButtonY() {
        switch (step) {
            case STEP_NETWORK:
                showStep(STEP_NETWORK, 0);
                break;
            case STEP_EXPLORE:
                toggleTour();
                break;
            default:
                break;
        }
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    private void handleBack() {
        if (settingsView != null && settingsView.onBackPressed()) {
            return;
        }
        if (step == STEP_WELCOME) {
            confirmSkip();
            return;
        }
        back();
    }
}
