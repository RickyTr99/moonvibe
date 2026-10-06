package com.limelight.profiles;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The general settings with a profile's over them. Reading a setting the profile has gives the
 * profile's value; writing a setting a profile can hold goes to the profile, any other to the
 * general settings.
 */
final class OverlayPreferences implements SharedPreferences {
    private final SharedPreferences general;
    private final SharedPreferences profile;
    private final Set<String> profileKeys;

    OverlayPreferences(SharedPreferences general, SharedPreferences profile, Set<String> profileKeys) {
        this.general = general;
        this.profile = profile;
        this.profileKeys = profileKeys;
    }

    private SharedPreferences source(String key) {
        return profile.contains(key) ? profile : general;
    }

    @Override
    public Map<String, ?> getAll() {
        Map<String, Object> all = new HashMap<>(general.getAll());
        all.putAll(profile.getAll());
        return all;
    }

    @Override
    public String getString(String key, String defValue) {
        return source(key).getString(key, defValue);
    }

    @Override
    public Set<String> getStringSet(String key, Set<String> defValues) {
        return source(key).getStringSet(key, defValues);
    }

    @Override
    public int getInt(String key, int defValue) {
        return source(key).getInt(key, defValue);
    }

    @Override
    public long getLong(String key, long defValue) {
        return source(key).getLong(key, defValue);
    }

    @Override
    public float getFloat(String key, float defValue) {
        return source(key).getFloat(key, defValue);
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        return source(key).getBoolean(key, defValue);
    }

    @Override
    public boolean contains(String key) {
        return profile.contains(key) || general.contains(key);
    }

    @Override
    public Editor edit() {
        return new OverlayEditor(general.edit(), profile.edit());
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        general.registerOnSharedPreferenceChangeListener(listener);
        profile.registerOnSharedPreferenceChangeListener(listener);
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        general.unregisterOnSharedPreferenceChangeListener(listener);
        profile.unregisterOnSharedPreferenceChangeListener(listener);
    }

    private final class OverlayEditor implements Editor {
        private final Editor general;
        private final Editor profile;

        OverlayEditor(Editor general, Editor profile) {
            this.general = general;
            this.profile = profile;
        }

        private Editor target(String key) {
            return profileKeys.contains(key) ? profile : general;
        }

        // A setting of the profile set back to the general value is taken out of the profile: null, nothing to put
        private Editor target(String key, Object value) {
            if (profileKeys.contains(key) && Profiles.sameValue(value, OverlayPreferences.this.general.getAll().get(key))) {
                profile.remove(key);
                return null;
            }
            return target(key);
        }

        @Override
        public Editor putString(String key, String value) {
            Editor target = target(key, value);
            if (target != null) {
                target.putString(key, value);
            }
            return this;
        }

        @Override
        public Editor putStringSet(String key, Set<String> values) {
            Editor target = target(key, values);
            if (target != null) {
                target.putStringSet(key, values);
            }
            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            Editor target = target(key, value);
            if (target != null) {
                target.putInt(key, value);
            }
            return this;
        }

        @Override
        public Editor putLong(String key, long value) {
            Editor target = target(key, value);
            if (target != null) {
                target.putLong(key, value);
            }
            return this;
        }

        @Override
        public Editor putFloat(String key, float value) {
            Editor target = target(key, value);
            if (target != null) {
                target.putFloat(key, value);
            }
            return this;
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            Editor target = target(key, value);
            if (target != null) {
                target.putBoolean(key, value);
            }
            return this;
        }

        @Override
        public Editor remove(String key) {
            target(key).remove(key);
            return this;
        }

        @Override
        public Editor clear() {
            general.clear();
            profile.clear();
            return this;
        }

        @Override
        public boolean commit() {
            boolean profileSaved = profile.commit();
            return general.commit() && profileSaved;
        }

        @Override
        public void apply() {
            profile.apply();
            general.apply();
        }
    }
}
