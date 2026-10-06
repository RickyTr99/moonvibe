package com.limelight.profiles;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The settings screen while a profile is in use: it works on {@link #WORK_FILE}, filled with the general
 * settings and the profile's over them, so every setting shows what the stream will use, as the quick
 * settings do. A change to a setting a profile can hold goes to the profile, any other to the general
 * settings; changes made elsewhere (the quick settings) come back into the file.
 */
public final class ActiveProfileSettings implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String WORK_FILE = "moonvibe_settings_with_profile";

    private final String id;
    private final String name;
    private final SharedPreferences work;
    private final SharedPreferences general;
    private final SharedPreferences profile;
    private final Set<String> overridden = new HashSet<>();
    // While this class itself writes, a change is not the user's
    private boolean writing;
    // While the settings screen is built, its preferences store their defaults: not a choice for the profile
    private boolean loading;

    public ActiveProfileSettings(Context context, Profiles.Profile active) {
        Context app = context.getApplicationContext();
        this.id = active.id;
        this.name = active.name;
        this.work = app.getSharedPreferences(WORK_FILE, Context.MODE_PRIVATE);
        this.general = PreferenceManager.getDefaultSharedPreferences(app);
        this.profile = Profiles.values(app, active.id);

        Map<String, ?> values = profile.getAll();
        overridden.addAll(values.keySet());
        writing = true;
        SharedPreferences.Editor editor = work.edit().clear();
        for (Map.Entry<String, ?> entry : general.getAll().entrySet()) {
            put(editor, entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            put(editor, entry.getKey(), entry.getValue());
        }
        editor.commit();
        writing = false;

        work.registerOnSharedPreferenceChangeListener(this);
        general.registerOnSharedPreferenceChangeListener(this);
        profile.registerOnSharedPreferenceChangeListener(this);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
    }

    public static boolean isProfileSetting(String key) {
        return key != null && Profiles.KEYS.contains(key);
    }

    /** Whether the profile changes this setting. */
    public boolean isOverridden(String key) {
        return overridden.contains(key);
    }

    /** The setting goes back to the general value, in the screen and in the stream. */
    public void useGeneral(String key) {
        if (!overridden.remove(key)) {
            return;
        }
        writing = true;
        profile.edit().remove(key).commit();
        SharedPreferences.Editor editor = work.edit();
        put(editor, key, general.getAll().get(key));
        editor.commit();
        writing = false;
    }

    public void close() {
        work.unregisterOnSharedPreferenceChangeListener(this);
        general.unregisterOnSharedPreferenceChangeListener(this);
        profile.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (writing || key == null) {
            return;
        }
        writing = true;
        try {
            if (prefs == work) {
                Object value = work.getAll().get(key);
                if (isProfileSetting(key)) {
                    if (loading) {
                        // A default stored by the screen: the general settings would have stored it too, and
                        // with it a value chosen later can be told equal to the general one
                        if (!general.contains(key)) {
                            commit(general.edit(), key, value);
                        }
                    } else if (Profiles.sameValue(value, general.getAll().get(key))) {
                        // Back to the general value: the profile stops changing the setting
                        overridden.remove(key);
                        commit(profile.edit(), key, null);
                    } else {
                        overridden.add(key);
                        commit(profile.edit(), key, value);
                    }
                } else {
                    commit(general.edit(), key, value);
                }
            } else if (prefs == general) {
                // A general value shows unless the profile changes it
                if (!overridden.contains(key)) {
                    commit(work.edit(), key, general.getAll().get(key));
                }
            } else if (prefs == profile) {
                Object value = profile.getAll().get(key);
                if (value != null) {
                    overridden.add(key);
                    commit(work.edit(), key, value);
                } else if (overridden.remove(key)) {
                    commit(work.edit(), key, general.getAll().get(key));
                }
            }
        } finally {
            writing = false;
        }
    }

    private static void commit(SharedPreferences.Editor editor, String key, Object value) {
        put(editor, key, value);
        editor.commit();
    }

    private static void put(SharedPreferences.Editor editor, String key, Object value) {
        if (value == null) {
            editor.remove(key);
        } else if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        } else if (value instanceof Set) {
            @SuppressWarnings("unchecked")
            Set<String> set = (Set<String>) value;
            editor.putStringSet(key, set);
        } else {
            editor.putString(key, value.toString());
        }
    }
}
