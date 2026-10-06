package com.limelight.profiles;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Editing a profile with the settings screen itself: the screen works on {@link #WORK_FILE}, filled
 * with the general settings and the profile's over them, so every setting shows what the stream
 * would use. A setting changed there becomes part of the profile; "use general" takes it out again.
 * The name is kept in the same file, under {@link #KEY_NAME}.
 */
public final class ProfileEditor implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String WORK_FILE = "moonvibe_profile_editing";
    public static final String KEY_NAME = "profile_name";

    private final Context context;
    private final String id;
    private final SharedPreferences work;
    private final SharedPreferences general;
    private final Set<String> overridden = new HashSet<>();
    // While the editor itself writes to the work file, a change is not the user's
    private boolean writing;

    public ProfileEditor(Context context, String id) {
        this.context = context.getApplicationContext();
        this.id = id;
        this.work = this.context.getSharedPreferences(WORK_FILE, Context.MODE_PRIVATE);
        this.general = PreferenceManager.getDefaultSharedPreferences(context);

        Profiles.Profile profile = Profiles.find(context, id);
        Map<String, ?> values = Profiles.values(context, id).getAll();
        overridden.addAll(values.keySet());

        writing = true;
        SharedPreferences.Editor editor = work.edit().clear();
        for (String key : Profiles.KEYS) {
            Object value = values.containsKey(key) ? values.get(key) : general.getAll().get(key);
            put(editor, key, value);
        }
        editor.putString(KEY_NAME, profile != null ? profile.name : "");
        editor.commit();
        writing = false;
        work.registerOnSharedPreferenceChangeListener(this);
    }

    public String id() {
        return id;
    }

    public boolean exists() {
        return Profiles.find(context, id) != null;
    }

    /** Whether the profile changes this setting; false for a setting a profile can't hold. */
    public boolean isOverridden(String key) {
        return overridden.contains(key);
    }

    public static boolean isProfileSetting(String key) {
        return key != null && Profiles.KEYS.contains(key);
    }

    /** The setting goes back to the general value, in the screen and in the stream. */
    public void useGeneral(String key) {
        if (!overridden.remove(key)) {
            return;
        }
        writing = true;
        SharedPreferences.Editor editor = work.edit();
        put(editor, key, general.getAll().get(key));
        editor.commit();
        writing = false;
        save();
    }

    public void close() {
        work.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (writing || key == null) {
            return;
        }
        if (KEY_NAME.equals(key)) {
            Profiles.Profile profile = Profiles.find(context, id);
            if (profile != null) {
                profile.name = work.getString(KEY_NAME, profile.name).trim();
                Profiles.update(context, profile);
            }
            return;
        }
        if (Profiles.KEYS.contains(key)) {
            overridden.add(key);
            save();
        }
    }

    // The profile keeps the settings it changes, with their own types
    private void save() {
        Map<String, ?> values = work.getAll();
        SharedPreferences.Editor editor = Profiles.values(context, id).edit().clear();
        for (String key : overridden) {
            put(editor, key, values.get(key));
        }
        editor.apply();
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
