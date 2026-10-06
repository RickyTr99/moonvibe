package com.limelight.profiles;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.Map;
import java.util.Set;

/**
 * The settings of the profile in use, for a screen that stays open while the profile changes
 * (the quick settings): each read and write goes to {@link Profiles#prefs} as it is at that moment.
 */
final class LivePreferences implements SharedPreferences {
    private final Context context;

    LivePreferences(Context context) {
        this.context = context;
    }

    private SharedPreferences now() {
        return Profiles.prefs(context);
    }

    @Override
    public Map<String, ?> getAll() {
        return now().getAll();
    }

    @Override
    public String getString(String key, String defValue) {
        return now().getString(key, defValue);
    }

    @Override
    public Set<String> getStringSet(String key, Set<String> defValues) {
        return now().getStringSet(key, defValues);
    }

    @Override
    public int getInt(String key, int defValue) {
        return now().getInt(key, defValue);
    }

    @Override
    public long getLong(String key, long defValue) {
        return now().getLong(key, defValue);
    }

    @Override
    public float getFloat(String key, float defValue) {
        return now().getFloat(key, defValue);
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        return now().getBoolean(key, defValue);
    }

    @Override
    public boolean contains(String key) {
        return now().contains(key);
    }

    @Override
    public Editor edit() {
        return now().edit();
    }

    // Changes are watched on the general settings: the profiles notify through Profiles' listeners
    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        PreferenceManager.getDefaultSharedPreferences(context).registerOnSharedPreferenceChangeListener(listener);
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        PreferenceManager.getDefaultSharedPreferences(context).unregisterOnSharedPreferenceChangeListener(listener);
    }
}
