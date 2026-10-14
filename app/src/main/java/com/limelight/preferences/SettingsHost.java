package com.limelight.preferences;

import android.preference.PreferenceScreen;

import com.limelight.profiles.ActiveProfileSettings;
import com.limelight.profiles.ProfileEditor;

/**
 * The screen that holds the hidden settings fragment (StreamSettings.SettingsFragment) and draws its preferences:
 * the Settings, or the first-run setup for the few settings it shows.
 */
public interface SettingsHost {
    /** The profile being edited, or null for the general settings. */
    ProfileEditor profileEditor();

    /** The profile in use, whose values the general settings show, or null. */
    ActiveProfileSettings withProfile();

    /** The fragment has built its preferences, it builds them again on some changes. */
    void onPreferencesReady(PreferenceScreen screen);

    /** Builds the fragment again, after a change that alters the options of other settings. */
    void reloadSettings();
}
