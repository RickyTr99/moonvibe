package com.limelight.ui.apollo.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.TwoStatePreference;

import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.preferences.SeekBarPreference;

import java.util.Map;

/**
 * The default value of each setting, to tell which ones were changed and to put them back.
 * The defaults come from preferences.xml, read into a scratch file the same way the app writes them
 * on its first start, plus the few that the app works out at run time.
 */
final class SettingsDefaults {
    private static final String SCRATCH_PREFS = "moonvibe_settings_defaults";

    private final Context context;
    private final SharedPreferences prefs;
    private final Map<String, ?> xmlDefaults;

    SettingsDefaults(Context context) {
        this.context = context;
        this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences scratch = context.getSharedPreferences(SCRATCH_PREFS, Context.MODE_PRIVATE);
        scratch.edit().clear().commit();
        PreferenceManager.setDefaultValues(context, SCRATCH_PREFS, Context.MODE_PRIVATE, R.xml.preferences, true);
        xmlDefaults = scratch.getAll();
    }

    /** The default of the setting, or null when it has none (buttons, links) or it is not known. */
    Object defaultOf(Preference pref) {
        String key = pref.getKey();
        if (key == null) {
            return null;
        }
        switch (key) {
            case PreferenceConfiguration.BITRATE_PREF_STRING:
                // It follows the resolution and the frame rate
                return PreferenceConfiguration.getDefaultBitrate(
                        prefs.getString(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.DEFAULT_RESOLUTION),
                        prefs.getString(PreferenceConfiguration.FPS_PREF_STRING, PreferenceConfiguration.DEFAULT_FPS));
            case "list_touch_mode":
                return PreferenceConfiguration.TOUCH_MODE_MULTI_TOUCH;
            case "checkbox_small_icon_mode":
                return PreferenceConfiguration.getDefaultSmallMode(context);
            case "checkbox_gamepad_motion_sensors":
                // Off on Android 12, which crashes with them (see PreferenceConfiguration)
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.S) {
                    return false;
                }
                break;
        }
        if (pref instanceof SeekBarPreference) {
            return ((SeekBarPreference) pref).getDefaultValue();
        }
        if (pref instanceof TwoStatePreference || pref instanceof ListPreference) {
            return xmlDefaults.get(key);
        }
        return null;
    }

    /** Whether the stored value differs from the default; a setting never stored is at its default. */
    boolean isModified(Preference pref) {
        Object def = defaultOf(pref);
        Object current = pref.getKey() != null ? prefs.getAll().get(pref.getKey()) : null;
        return def != null && current != null && !def.toString().equals(current.toString());
    }
}
