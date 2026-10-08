package com.limelight.preferences;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.MediaCodecInfo;
import android.os.Build;
import android.os.Bundle;
import android.app.Activity;
import android.os.Handler;
import android.os.Vibrator;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.DisplayMetrics;
import android.util.Range;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;

import com.limelight.LimeLog;
import com.limelight.PcView;
import com.limelight.R;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.binding.video.MediaCodecHelper;
import com.limelight.profiles.ActiveProfileSettings;
import com.limelight.profiles.ProfileEditor;
import com.limelight.profiles.Profiles;
import com.limelight.ui.apollo.ApolloTopBar;
import com.limelight.ui.apollo.ApolloUi;
import com.limelight.ui.apollo.hints.HintRow;
import com.limelight.ui.apollo.hints.ScreenHints;
import com.limelight.ui.apollo.settings.ProfileMenu;
import com.limelight.ui.apollo.settings.QuickSettingsPanel;
import com.limelight.ui.apollo.settings.SettingsView;
import com.limelight.ui.apollo.stats.StatsPrefs;
import com.limelight.ui.theme.ApolloColors;
import com.limelight.utils.Dialog;
import com.limelight.utils.UiHelper;

import java.lang.reflect.Method;
import java.util.Arrays;

public class StreamSettings extends Activity {
    /** MoonVibe: edit this profile instead of the general settings (see ProfileEditor). */
    public static final String EXTRA_PROFILE_ID = "ProfileId";

    private PreferenceConfiguration previousPrefs;
    private int previousDisplayPixelCount;
    private ApolloTopBar topBar;
    // Opened from Home with LB/RB: the categories take the gamepad focus once the window is active
    private boolean focusForGamepad;
    private SettingsView settingsView;
    // Non-null while a profile is edited
    ProfileEditor profileEditor;
    // Non-null on the general settings while a profile is in use: the screen shows its values
    ActiveProfileSettings withProfile;
    // Another profile picked while the screen is open: the screen is built again on its values
    private final Runnable profilesListener = () -> {
        Profiles.Profile active = Profiles.active(this);
        boolean same = active == null ? withProfile == null
                : withProfile != null && active.id.equals(withProfile.id()) && active.name.equals(withProfile.name());
        if (!same && !isFinishing()) {
            useActiveProfile();
            reloadSettings();
        }
    };

    private void useActiveProfile() {
        if (withProfile != null) {
            withProfile.close();
        }
        Profiles.Profile active = Profiles.active(this);
        withProfile = active != null ? new ActiveProfileSettings(this, active) : null;
    }

    // HACK for Android 9
    static DisplayCutout displayCutoutP;

    void reloadSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display.Mode mode = getWindowManager().getDefaultDisplay().getMode();
            previousDisplayPixelCount = mode.getPhysicalWidth() * mode.getPhysicalHeight();
        }
        getFragmentManager().beginTransaction().replace(
                R.id.stream_settings, new SettingsFragment()
        ).commitAllowingStateLoss();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        previousPrefs = PreferenceConfiguration.readPreferences(this);

        String profileId = getIntent().getStringExtra(EXTRA_PROFILE_ID);
        if (profileId != null) {
            if (Profiles.find(this, profileId) == null) {
                finish();
                return;
            }
            profileEditor = new ProfileEditor(this, profileId);
        } else {
            useActiveProfile();
            Profiles.addListener(profilesListener);
        }

        UiHelper.setLocale(this);

        setContentView(R.layout.activity_stream_settings);
        overridePendingTransition(R.anim.apollo_fade_in, R.anim.apollo_fade_out);
        if (profileEditor != null) {
            initializeProfileViews();
        } else {
            initializeApolloViews();
        }

        UiHelper.notifyNewRootView(this);
    }

    // MoonVibe: top bar and settings view, which draws the preferences built by SettingsFragment
    private void initializeApolloViews() {
        ApolloColors colors = ApolloColors.dark(this);

        focusForGamepad = ApolloTopBar.takeSwitchedByGamepad();
        topBar = new ApolloTopBar(this, colors);
        topBar.setTabs(new String[] {getString(R.string.apollo_tab_home), getString(R.string.apollo_tab_settings)}, 1,
                index -> goHome());
        topBar.setOnQuickSettingsClickListener(v -> QuickSettingsPanel.of(this).toggle());
        topBar.setOnProfileClickListener(v -> ProfileMenu.of(this).toggle());
        ((FrameLayout) findViewById(R.id.topBarContainer)).addView(topBar);

        settingsView = new SettingsView(this, colors);
        ((FrameLayout) findViewById(R.id.settingsContainer)).addView(settingsView);

        // Gamepad hints at the bottom
        HintRow hintRow = ScreenHints.attach(this, findViewById(R.id.settingsColumn));
        hintRow.setFallback(HintRow.hint(this, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_home),
                HintRow.hint(this, KeyEvent.KEYCODE_BUTTON_SELECT, R.string.apollo_profiles),
                HintRow.hint(this, KeyEvent.KEYCODE_BUTTON_START, R.string.apollo_hint_quick_settings));

        // Keep the content clear of a notch, the window draws under it
        ApolloUi.padForCutout(findViewById(R.id.settingsColumn));
    }

    // MoonVibe: a profile's page, without the top bar: its name and rule, then the settings it can change
    private void initializeProfileViews() {
        ApolloColors colors = ApolloColors.dark(this);
        findViewById(R.id.topBarContainer).setVisibility(View.GONE);

        SettingsView.ProfileMode mode = new SettingsView.ProfileMode() {
            @Override
            public boolean isProfileSetting(String key) {
                return ProfileEditor.isProfileSetting(key);
            }

            @Override
            public boolean isOverridden(String key) {
                return profileEditor.isOverridden(key);
            }

            @Override
            public void useGeneral(String key) {
                profileEditor.useGeneral(key);
            }

            @Override
            public CharSequence note(Preference pref) {
                String key = pref.getKey();
                if (ProfileEditor.isProfileSetting(key) && !profileEditor.isOverridden(key)) {
                    return getString(R.string.apollo_profile_follows_general);
                }
                return null;
            }

            @Override
            public void delete() {
                Profiles.Profile profile = Profiles.find(StreamSettings.this, profileEditor.id());
                if (profile != null) {
                    ProfilesActivity.confirmDelete(StreamSettings.this, profile.id, profile.name, StreamSettings.this::finish);
                }
            }
        };
        settingsView = SettingsView.profilePage(this, colors, getString(R.string.apollo_profile_editor_title), mode,
                ProfileEditor.KEY_NAME);
        FrameLayout container = findViewById(R.id.settingsContainer);
        container.setPadding(0, ApolloUi.dp(this, 16), 0, 0);
        container.addView(settingsView);

        HintRow hintRow = ScreenHints.attach(this, findViewById(R.id.settingsColumn));
        hintRow.setFallback(HintRow.hint(this, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_back));

        ApolloUi.padForCutout(findViewById(R.id.settingsColumn));
    }


    void onPreferencesReady(PreferenceScreen screen) {
        ActiveProfileSettings profile = withProfile;
        if (profile != null) {
            profile.setLoading(false);
        }
        if (profileEditor == null) {
            settingsView.setActiveProfile(profile == null ? null : new SettingsView.ActiveProfile() {
                @Override
                public CharSequence name() {
                    return profile.name();
                }

                @Override
                public boolean isProfileSetting(String key) {
                    return ActiveProfileSettings.isProfileSetting(key);
                }

                @Override
                public boolean isOverridden(String key) {
                    return profile.isOverridden(key);
                }

                @Override
                public void useGeneral(String key) {
                    profile.useGeneral(key);
                }
            });
        }
        settingsView.setScreen(screen);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (topBar != null) {
            topBar.onResume();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && focusForGamepad) {
            focusForGamepad = false;
            settingsView.focusFromGamepad();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (topBar != null) {
            topBar.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (profileEditor != null) {
            profileEditor.close();
        }
        Profiles.removeListener(profilesListener);
        if (withProfile != null) {
            withProfile.close();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (profileEditor != null) {
            // A profile's page: B goes back from the page's own menus, then leaves
            if (keyCode == KeyEvent.KEYCODE_BUTTON_B && event.getRepeatCount() == 0) {
                if (!settingsView.onButtonB()) {
                    finish();
                }
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }
        if (event.getRepeatCount() == 0) {
            // Select opens and closes the profiles; while they are open the other buttons are theirs
            if (keyCode == KeyEvent.KEYCODE_BUTTON_SELECT) {
                ProfileMenu.of(this).toggle();
                return true;
            }
            if (ProfileMenu.of(this).isShowing()) {
                // Start goes on to the quick settings, as Select goes from them to the profiles
                if (keyCode == KeyEvent.KEYCODE_BUTTON_START) {
                    QuickSettingsPanel.of(this).show();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_BUTTON_B) {
                    ProfileMenu.of(this).dismiss();
                    return true;
                }
                return super.onKeyDown(keyCode, event);
            }
            switch (keyCode) {
                case KeyEvent.KEYCODE_BUTTON_L1:
                    topBar.switchTab(-1);
                    return true;
                case KeyEvent.KEYCODE_BUTTON_R1:
                    topBar.switchTab(1);
                    return true;
                case KeyEvent.KEYCODE_BUTTON_B:
                    // Panels and menus close first, the settings go back to their category, then Home
                    if (QuickSettingsPanel.of(this).dismiss() || settingsView.onButtonB()) {
                        return true;
                    }
                    break;
                case KeyEvent.KEYCODE_BUTTON_START:
                    QuickSettingsPanel.of(this).toggle();
                    return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.apollo_fade_in, R.anim.apollo_fade_out);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        // We have to use this hack on Android 9 because we don't have Display.getCutout()
        // which was added in Android 10.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
            // Insets can be null when the activity is recreated on screen rotation
            // https://stackoverflow.com/questions/61241255/windowinsets-getdisplaycutout-is-null-everywhere-except-within-onattachedtowindo
            WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
            if (insets != null) {
                displayCutoutP = insets.getDisplayCutout();
            }
        }

        reloadSettings();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display.Mode mode = getWindowManager().getDefaultDisplay().getMode();

            // If the display's physical pixel count has changed, we consider that it's a new display
            // and we should reload our settings (which include display-dependent values).
            //
            // NB: We aren't using displayId here because that stays the same (DEFAULT_DISPLAY) when
            // switching between screens on a foldable device.
            if (mode.getPhysicalWidth() * mode.getPhysicalHeight() != previousDisplayPixelCount) {
                reloadSettings();
            }
        }
    }

    // The gamepad hints show while keys are used and hide at the first touch
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        ScreenHints.onKeyEvent(event);
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        ScreenHints.onTouchEvent(event);
        return super.dispatchTouchEvent(event);
    }

    @Override
    // NOTE: This will NOT be called on Android 13+ with android:enableOnBackInvokedCallback="true"
    public void onBackPressed() {
        if (profileEditor != null) {
            if (!settingsView.onBackPressed()) {
                finish();
            }
            return;
        }
        if (ProfileMenu.of(this).dismiss() || QuickSettingsPanel.of(this).dismiss() || settingsView.onBackPressed()) {
            return;
        }
        goHome();
    }

    // The Home tab leaves straight away, even from inside a category
    private void goHome() {
        QuickSettingsPanel.of(this).dismiss();
        topBar.markLeaving();
        finish();
        // The top bar is the same on both screens, so it stays still while the content fades
        overridePendingTransition(R.anim.apollo_fade_in, R.anim.apollo_fade_out);

        // Language changes are handled via configuration changes in Android 13+,
        // so manual activity relaunching is no longer required.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            PreferenceConfiguration newPrefs = PreferenceConfiguration.readPreferences(this);
            if (!newPrefs.language.equals(previousPrefs.language)) {
                // Restart the PC view to apply UI changes
                Intent intent = new Intent(this, PcView.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent, null);
            }
        }
    }

    public static class SettingsFragment extends PreferenceFragment {
        private int nativeResolutionStartIndex = Integer.MAX_VALUE;
        private boolean nativeFramerateShown = false;

        private void setValue(String preferenceKey, String value) {
            ListPreference pref = (ListPreference) findPreference(preferenceKey);

            pref.setValue(value);
        }

        private void appendPreferenceEntry(ListPreference pref, String newEntryName, String newEntryValue) {
            CharSequence[] newEntries = Arrays.copyOf(pref.getEntries(), pref.getEntries().length + 1);
            CharSequence[] newValues = Arrays.copyOf(pref.getEntryValues(), pref.getEntryValues().length + 1);

            // Add the new option
            newEntries[newEntries.length - 1] = newEntryName;
            newValues[newValues.length - 1] = newEntryValue;

            pref.setEntries(newEntries);
            pref.setEntryValues(newValues);
        }

        private void addNativeResolutionEntry(int nativeWidth, int nativeHeight, boolean insetsRemoved, boolean portrait) {
            ListPreference pref = (ListPreference) findPreference(PreferenceConfiguration.RESOLUTION_PREF_STRING);

            String newName;

            if (insetsRemoved) {
                newName = getResources().getString(R.string.resolution_prefix_native_fullscreen);
            }
            else {
                newName = getResources().getString(R.string.resolution_prefix_native);
            }

            if (PreferenceConfiguration.isSquarishScreen(nativeWidth, nativeHeight)) {
                if (portrait) {
                    newName += " " + getResources().getString(R.string.resolution_prefix_native_portrait);
                }
                else {
                    newName += " " + getResources().getString(R.string.resolution_prefix_native_landscape);
                }
            }

            newName += " ("+nativeWidth+"x"+nativeHeight+")";

            String newValue = nativeWidth+"x"+nativeHeight;

            // Check if the native resolution is already present
            for (CharSequence value : pref.getEntryValues()) {
                if (newValue.equals(value.toString())) {
                    // It is present in the default list, so don't add it again
                    return;
                }
            }

            if (pref.getEntryValues().length < nativeResolutionStartIndex) {
                nativeResolutionStartIndex = pref.getEntryValues().length;
            }
            appendPreferenceEntry(pref, newName, newValue);
        }

        private void addNativeResolutionEntries(int nativeWidth, int nativeHeight, boolean insetsRemoved) {
            if (PreferenceConfiguration.isSquarishScreen(nativeWidth, nativeHeight)) {
                addNativeResolutionEntry(nativeHeight, nativeWidth, insetsRemoved, true);
            }
            addNativeResolutionEntry(nativeWidth, nativeHeight, insetsRemoved, false);
        }

        private void addNativeFrameRateEntry(float framerate) {
            int frameRateRounded = Math.round(framerate);
            if (frameRateRounded == 0) {
                return;
            }

            ListPreference pref = (ListPreference) findPreference(PreferenceConfiguration.FPS_PREF_STRING);
            String fpsValue = Integer.toString(frameRateRounded);
            String fpsName = getResources().getString(R.string.resolution_prefix_native) +
                    " (" + fpsValue + " " + getResources().getString(R.string.fps_suffix_fps) + ")";

            // Check if the native frame rate is already present
            for (CharSequence value : pref.getEntryValues()) {
                if (fpsValue.equals(value.toString())) {
                    // It is present in the default list, so don't add it again
                    nativeFramerateShown = false;
                    return;
                }
            }

            appendPreferenceEntry(pref, fpsName, fpsValue);
            nativeFramerateShown = true;
        }

        private void removeValue(String preferenceKey, String value, Runnable onMatched) {
            int matchingCount = 0;

            ListPreference pref = (ListPreference) findPreference(preferenceKey);

            // Count the number of matching entries we'll be removing
            for (CharSequence seq : pref.getEntryValues()) {
                if (seq.toString().equalsIgnoreCase(value)) {
                    matchingCount++;
                }
            }

            // Create the new arrays
            CharSequence[] entries = new CharSequence[pref.getEntries().length-matchingCount];
            CharSequence[] entryValues = new CharSequence[pref.getEntryValues().length-matchingCount];
            int outIndex = 0;
            for (int i = 0; i < pref.getEntryValues().length; i++) {
                if (pref.getEntryValues()[i].toString().equalsIgnoreCase(value)) {
                    // Skip matching values
                    continue;
                }

                entries[outIndex] = pref.getEntries()[i];
                entryValues[outIndex] = pref.getEntryValues()[i];
                outIndex++;
            }

            if (pref.getValue().equalsIgnoreCase(value)) {
                onMatched.run();
            }

            // Update the preference with the new list
            pref.setEntries(entries);
            pref.setEntryValues(entryValues);
        }

        // A new resolution or frame rate leaves the bitrate as it is. Never set, it would follow the new
        // default, so the one in use is kept.
        private void keepBitrate() {
            SharedPreferences prefs = prefs();
            if (!prefs.contains(PreferenceConfiguration.BITRATE_PREF_STRING)) {
                prefs.edit()
                        .putInt(PreferenceConfiguration.BITRATE_PREF_STRING, PreferenceConfiguration.getDefaultBitrate(
                                prefs.getString(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.DEFAULT_RESOLUTION),
                                prefs.getString(PreferenceConfiguration.FPS_PREF_STRING, PreferenceConfiguration.DEFAULT_FPS)))
                        .apply();
            }
        }

        // The settings this screen edits: the general ones, or the work file of a profile
        private SharedPreferences prefs() {
            return getPreferenceManager().getSharedPreferences();
        }

        private void addProfilePreferences(PreferenceScreen screen, ProfileEditor profileEditor) {
            EditTextPreference name = new EditTextPreference(getActivity());
            name.setKey(ProfileEditor.KEY_NAME);
            name.setTitle(R.string.apollo_profile_name);
            name.setDialogTitle(R.string.apollo_profile_name);
            name.setPersistent(true);
            screen.addPreference(name);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            UiHelper.applyStatusBarPadding(view);
            return view;
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            // MoonVibe: the settings view draws the preferences built here
            ((StreamSettings) getActivity()).onPreferencesReady(getPreferenceScreen());
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // MoonVibe: a profile is edited in a work file of its own (ProfileEditor), with its name and rule
            ProfileEditor profileEditor = ((StreamSettings) getActivity()).profileEditor;
            if (profileEditor != null) {
                getPreferenceManager().setSharedPreferencesName(ProfileEditor.WORK_FILE);
            }
            // With a profile in use, the general settings with its values over them (ActiveProfileSettings)
            ActiveProfileSettings withProfile = ((StreamSettings) getActivity()).withProfile;
            if (profileEditor == null && withProfile != null) {
                getPreferenceManager().setSharedPreferencesName(ActiveProfileSettings.WORK_FILE);
                withProfile.setLoading(true);
            }

            addPreferencesFromResource(R.xml.preferences);
            PreferenceScreen screen = getPreferenceScreen();
            if (profileEditor != null) {
                addProfilePreferences(screen, profileEditor);
            }

            // hide on-screen controls category on non touch screen devices
            if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_onscreen_controls");
                screen.removePreference(category);
            }

            // Hide remote desktop mouse mode on pre-Oreo (which doesn't have pointer capture)
            // and NVIDIA SHIELD devices (which support raw mouse input in pointer capture mode)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    getActivity().getPackageManager().hasSystemFeature("com.nvidia.feature.shield")) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_input_settings");
                category.removePreference(findPreference("checkbox_absolute_mouse_mode"));
            }

            // Hide gamepad motion sensor option when running on OSes before Android 12.
            // Support for motion, LED, battery, and other extensions were introduced in S.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_gamepad_settings");
                category.removePreference(findPreference("checkbox_gamepad_motion_sensors"));
            }

            // Hide gamepad motion sensor fallback option if the device has no gyro or accelerometer
            if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER) &&
                    !getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE)) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_gamepad_settings");
                category.removePreference(findPreference("checkbox_gamepad_motion_fallback"));
            }

            // The extra buttons of the built-in controller exist only on AYN handhelds
            if (!ControllerHandler.IS_AYN_DEVICE) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_gamepad_settings");
                category.removePreference(findPreference("list_ayn_back_button"));
                category.removePreference(findPreference("list_ayn_m1_button"));
                category.removePreference(findPreference("list_ayn_m2_button"));
            }

            // Hide USB driver options on devices without USB host support
            if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST)) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_gamepad_settings");
                category.removePreference(findPreference("checkbox_usb_bind_all"));
                category.removePreference(findPreference("checkbox_usb_driver"));
            }

            // Remove PiP mode on devices pre-Oreo, where the feature is not available (some low RAM devices),
            // and on Fire OS where it violates the Amazon App Store guidelines for some reason.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    !getActivity().getPackageManager().hasSystemFeature("android.software.picture_in_picture") ||
                    getActivity().getPackageManager().hasSystemFeature("com.amazon.software.fireos")) {
                // MoonVibe: it's an option of "When you leave the app"
                if (findPreference("list_leave_app") != null) {
                    removeValue("list_leave_app", PreferenceConfiguration.LEAVE_APP_PIP_VALUE, () ->
                            ((ListPreference) findPreference("list_leave_app")).setValue("close"));
                }
            }

            // A stream kept in the background shows a notification, to come back or disconnect: ask for it
            // as soon as it's chosen (the stream still runs without it)
            Preference leaveApp = findPreference("list_leave_app");
            if (leaveApp != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                leaveApp.setOnPreferenceChangeListener((pref, value) -> {
                    if ("keep".equals(value) && getActivity().checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {
                        getActivity().requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 0);
                    }
                    return true;
                });
            }

            // Fire TV apps are not allowed to use WebViews or browsers, so hide the Help category
            /*if (getActivity().getPackageManager().hasSystemFeature("amazon.hardware.fire_tv")) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_help");
                screen.removePreference(category);
            }*/
            PreferenceCategory category_gamepad_settings =
                    (PreferenceCategory) findPreference("category_gamepad_settings");
            // Remove the vibration options if the device can't vibrate
            if (!((Vibrator)getActivity().getSystemService(Context.VIBRATOR_SERVICE)).hasVibrator()) {
                category_gamepad_settings.removePreference(findPreference("checkbox_vibrate_fallback"));
                category_gamepad_settings.removePreference(findPreference("seekbar_vibrate_fallback_strength"));
                // The entire OSC category may have already been removed by the touchscreen check above
                PreferenceCategory category = (PreferenceCategory) findPreference("category_onscreen_controls");
                if (category != null) {
                    category.removePreference(findPreference("checkbox_vibrate_osc"));
                }
            }


            Display display = getActivity().getWindowManager().getDefaultDisplay();
            float maxSupportedFps = display.getRefreshRate();

            // Hide non-supported resolution/FPS combinations
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int maxSupportedResW = 0;

                // Add a native resolution with any insets included for users that don't want content
                // behind the notch of their display
                boolean hasInsets = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    DisplayCutout cutout;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Use the much nicer Display.getCutout() API on Android 10+
                        cutout = display.getCutout();
                    }
                    else {
                        // Android 9 only
                        cutout = displayCutoutP;
                    }

                    if (cutout != null) {
                        int widthInsets = cutout.getSafeInsetLeft() + cutout.getSafeInsetRight();
                        int heightInsets = cutout.getSafeInsetBottom() + cutout.getSafeInsetTop();

                        if (widthInsets != 0 || heightInsets != 0) {
                            DisplayMetrics metrics = new DisplayMetrics();
                            display.getRealMetrics(metrics);

                            int width = Math.max(metrics.widthPixels - widthInsets, metrics.heightPixels - heightInsets);
                            int height = Math.min(metrics.widthPixels - widthInsets, metrics.heightPixels - heightInsets);

                            addNativeResolutionEntries(width, height, false);
                            hasInsets = true;
                        }
                    }
                }

                // Always allow resolutions that are smaller or equal to the active
                // display resolution because decoders can report total non-sense to us.
                // For example, a p201 device reports:
                // AVC Decoder: OMX.amlogic.avc.decoder.awesome
                // HEVC Decoder: OMX.amlogic.hevc.decoder.awesome
                // AVC supported width range: 64 - 384
                // HEVC supported width range: 64 - 544
                for (Display.Mode candidate : display.getSupportedModes()) {
                    // Some devices report their dimensions in the portrait orientation
                    // where height > width. Normalize these to the conventional width > height
                    // arrangement before we process them.

                    int width = Math.max(candidate.getPhysicalWidth(), candidate.getPhysicalHeight());
                    int height = Math.min(candidate.getPhysicalWidth(), candidate.getPhysicalHeight());

                    // Some TVs report strange values here, so let's avoid native resolutions on a TV
                    // unless they report greater than 4K resolutions.
                    if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
                            (width > 3840 || height > 2160)) {
                        addNativeResolutionEntries(width, height, hasInsets);
                    }

                    if ((width >= 3840 || height >= 2160) && maxSupportedResW < 3840) {
                        maxSupportedResW = 3840;
                    }
                    else if ((width >= 2560 || height >= 1440) && maxSupportedResW < 2560) {
                        maxSupportedResW = 2560;
                    }
                    else if ((width >= 1920 || height >= 1080) && maxSupportedResW < 1920) {
                        maxSupportedResW = 1920;
                    }

                    if (candidate.getRefreshRate() > maxSupportedFps) {
                        maxSupportedFps = candidate.getRefreshRate();
                    }
                }

                // This must be called to do runtime initialization before calling functions that evaluate
                // decoder lists.
                MediaCodecHelper.initialize(getContext(), GlPreferences.readPreferences(getContext()).glRenderer);

                MediaCodecInfo avcDecoder = MediaCodecHelper.findProbableSafeDecoder("video/avc", -1);
                MediaCodecInfo hevcDecoder = MediaCodecHelper.findProbableSafeDecoder("video/hevc", -1);

                if (avcDecoder != null) {
                    Range<Integer> avcWidthRange = avcDecoder.getCapabilitiesForType("video/avc").getVideoCapabilities().getSupportedWidths();

                    LimeLog.info("AVC supported width range: "+avcWidthRange.getLower()+" - "+avcWidthRange.getUpper());

                    // If 720p is not reported as supported, ignore all results from this API
                    if (avcWidthRange.contains(1280)) {
                        if (avcWidthRange.contains(3840) && maxSupportedResW < 3840) {
                            maxSupportedResW = 3840;
                        }
                        else if (avcWidthRange.contains(1920) && maxSupportedResW < 1920) {
                            maxSupportedResW = 1920;
                        }
                        else if (maxSupportedResW < 1280) {
                            maxSupportedResW = 1280;
                        }
                    }
                }

                if (hevcDecoder != null) {
                    Range<Integer> hevcWidthRange = hevcDecoder.getCapabilitiesForType("video/hevc").getVideoCapabilities().getSupportedWidths();

                    LimeLog.info("HEVC supported width range: "+hevcWidthRange.getLower()+" - "+hevcWidthRange.getUpper());

                    // If 720p is not reported as supported, ignore all results from this API
                    if (hevcWidthRange.contains(1280)) {
                        if (hevcWidthRange.contains(3840) && maxSupportedResW < 3840) {
                            maxSupportedResW = 3840;
                        }
                        else if (hevcWidthRange.contains(1920) && maxSupportedResW < 1920) {
                            maxSupportedResW = 1920;
                        }
                        else if (maxSupportedResW < 1280) {
                            maxSupportedResW = 1280;
                        }
                    }
                }

                LimeLog.info("Maximum resolution slot: "+maxSupportedResW);

                if (maxSupportedResW != 0) {
                    if (maxSupportedResW < 3840) {
                        // 4K is unsupported
                        removeValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_4K, new Runnable() {
                            @Override
                            public void run() {
                                setValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1440P);
                            }
                        });
                    }
                    if (maxSupportedResW < 2560) {
                        // 1440p is unsupported
                        removeValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1440P, new Runnable() {
                            @Override
                            public void run() {
                                setValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1080P);
                            }
                        });
                    }
                    if (maxSupportedResW < 1920) {
                        // 1080p is unsupported
                        removeValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1080P, new Runnable() {
                            @Override
                            public void run() {
                                setValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_720P);
                            }
                        });
                    }
                    // Never remove 720p
                }
            }
            else {
                // We can get the true metrics via the getRealMetrics() function (unlike the lies
                // that getWidth() and getHeight() tell to us).
                DisplayMetrics metrics = new DisplayMetrics();
                display.getRealMetrics(metrics);
                int width = Math.max(metrics.widthPixels, metrics.heightPixels);
                int height = Math.min(metrics.widthPixels, metrics.heightPixels);
                addNativeResolutionEntries(width, height, false);
            }

            // MoonVibe: from the settings this screen edits (a profile may unlock them on its own)
            if (!prefs().getBoolean(PreferenceConfiguration.UNLOCK_FPS_STRING, false)) {
                // We give some extra room in case the FPS is rounded down
                if (maxSupportedFps < 118) {
                    removeValue(PreferenceConfiguration.FPS_PREF_STRING, "120", new Runnable() {
                        @Override
                        public void run() {
                            setValue(PreferenceConfiguration.FPS_PREF_STRING, "90");
                        }
                    });
                }
                if (maxSupportedFps < 88) {
                    // 1080p is unsupported
                    removeValue(PreferenceConfiguration.FPS_PREF_STRING, "90", new Runnable() {
                        @Override
                        public void run() {
                            setValue(PreferenceConfiguration.FPS_PREF_STRING, "60");
                        }
                    });
                }
                // Never remove 30 FPS or 60 FPS
            }
            addNativeFrameRateEntry(maxSupportedFps);

            // Android L introduces the drop duplicate behavior of releaseOutputBuffer()
            // that the unlock FPS option relies on to not massively increase latency.
            findPreference(PreferenceConfiguration.UNLOCK_FPS_STRING).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    // HACK: We need to let the preference change succeed before reinitializing to ensure
                    // it's reflected in the new layout.
                    final Handler h = new Handler();
                    h.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            // Ensure the activity is still open when this timeout expires
                            StreamSettings settingsActivity = (StreamSettings) SettingsFragment.this.getActivity();
                            if (settingsActivity != null) {
                                settingsActivity.reloadSettings();
                            }
                        }
                    }, 500);

                    // Allow the original preference change to take place
                    return true;
                }
            });

            // The jitter buffer only applies to host frame timing pacing
            final Preference jitterBufferPref = findPreference("jitter_buffer");
            final ListPreference framePacingPref = (ListPreference) findPreference("frame_pacing");
            if (jitterBufferPref != null && framePacingPref != null) {
                jitterBufferPref.setEnabled("host-timed".equals(framePacingPref.getValue()));
                framePacingPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        jitterBufferPref.setEnabled("host-timed".equals(newValue));
                        return true;
                    }
                });
            }

            // Remove HDR preference for devices below Nougat
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                LimeLog.info("Excluding HDR toggle based on OS");
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_advanced_settings");
                category.removePreference(findPreference("checkbox_enable_hdr"));
            }
            else {
                Display.HdrCapabilities hdrCaps = display.getHdrCapabilities();

                // We must now ensure our display is compatible with HDR10
                boolean foundHdr10 = false;
                if (hdrCaps != null) {
                    // getHdrCapabilities() returns null on Lenovo Lenovo Mirage Solo (vega), Android 8.0
                    for (int hdrType : hdrCaps.getSupportedHdrTypes()) {
                        if (hdrType == Display.HdrCapabilities.HDR_TYPE_HDR10) {
                            foundHdr10 = true;
                            break;
                        }
                    }
                }

                if (!foundHdr10) {
                    LimeLog.info("Excluding HDR toggle based on display capabilities");
                    PreferenceCategory category =
                            (PreferenceCategory) findPreference("category_advanced_settings");
                    category.removePreference(findPreference("checkbox_enable_hdr"));
                }
                else if (PreferenceConfiguration.isShieldAtvFirmwareWithBrokenHdr()) {
                    LimeLog.info("Disabling HDR toggle on old broken SHIELD TV firmware");
                    PreferenceCategory category =
                            (PreferenceCategory) findPreference("category_advanced_settings");
                    CheckBoxPreference hdrPref = (CheckBoxPreference) category.findPreference("checkbox_enable_hdr");
                    hdrPref.setEnabled(false);
                    hdrPref.setChecked(false);
                    hdrPref.setSummary("Update the firmware on your NVIDIA SHIELD Android TV to enable HDR");
                }
            }

            // Add a listener to the FPS and resolution preference
            // so the bitrate can be auto-adjusted
            findPreference(PreferenceConfiguration.RESOLUTION_PREF_STRING).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    String valueStr = (String) newValue;
                    keepBitrate();

                    // Detect if this value is the native resolution option
                    CharSequence[] values = ((ListPreference)preference).getEntryValues();
                    boolean isNativeRes = true;
                    for (int i = 0; i < values.length; i++) {
                        // Look for a match prior to the start of the native resolution entries
                        if (valueStr.equals(values[i].toString()) && i < nativeResolutionStartIndex) {
                            isNativeRes = false;
                            break;
                        }
                    }

                    // If this is native resolution, show the warning dialog
                    if (isNativeRes) {
                        Dialog.displayDialog(getActivity(),
                                getResources().getString(R.string.title_native_res_dialog),
                                getResources().getString(R.string.text_native_res_dialog),
                                false);
                    }

                    // Allow the original preference change to take place
                    return true;
                }
            });
            findPreference(PreferenceConfiguration.FPS_PREF_STRING).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    String valueStr = (String) newValue;
                    keepBitrate();

                    // If this is native frame rate, show the warning dialog
                    CharSequence[] values = ((ListPreference)preference).getEntryValues();
                    if (nativeFramerateShown && values[values.length - 1].toString().equals(newValue.toString())) {
                        Dialog.displayDialog(getActivity(),
                                getResources().getString(R.string.title_native_fps_dialog),
                                getResources().getString(R.string.text_native_res_dialog),
                                false);
                    }

                    // Allow the original preference change to take place
                    return true;
                }
            });

            // MoonVibe: the page of the stats overlay
            Preference statsPref = findPreference(StatsPrefs.PAGE_KEY);
            if (statsPref != null) {
                statsPref.setOnPreferenceClickListener(preference -> {
                    startActivity(new Intent(getActivity(), StatsSettingsActivity.class));
                    return true;
                });
            }

            // Setup custom commands preference click listener
            Preference customCommandsPref = findPreference("overlay_custom_commands");
            if (customCommandsPref != null) {
                customCommandsPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        Intent intent = new Intent(getActivity(), CustomCommandsActivity.class);
                        startActivity(intent);
                        return true;
                    }
                });
            }
        }
    }
}
