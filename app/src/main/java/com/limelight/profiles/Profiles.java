package com.limelight.profiles;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;

import com.limelight.LimeLog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stream profiles: named sets of stream settings laid over the general ones, picked by hand in the
 * profiles menu. The settings of a single game still win over the profile.
 *
 * The list and the choice live in {@link #INDEX_FILE}; each profile keeps only the settings it changes,
 * with their own types, in a preferences file of its own.
 */
public final class Profiles {
    private Profiles() {
    }

    public static final String SELECTION_NONE = "none";

    /** The settings a profile can change: those of the Video, Codec and Audio categories. */
    public static final Set<String> KEYS = new HashSet<>(Arrays.asList(
            "list_resolution", "list_fps", "seekbar_bitrate_kbps", "checkbox_enable_hdr", "checkbox_stretch_video",
            "checkbox_full_range", "spatial_dithering", "checkbox_unlock_fps",
            "video_format", "video_renderer", "pyrowave_late_frames", "checkbox_ultra_low_latency",
            "frame_pacing", "jitter_buffer", "checkbox_reduce_refresh_rate", "text_actual_display_refresh_rate",
            "checkbox_enable_perf_overlay", "checkbox_enable_post_stream_toast", "checkbox_disable_warnings",
            "list_audio_config", "checkbox_enable_audiofx", "checkbox_host_audio"));

    private static final String INDEX_FILE = "moonvibe_profiles";
    private static final String KEY_LIST = "list";
    private static final String KEY_SELECTION = "selection";
    private static final String VALUES_FILE_PREFIX = "profile_";

    public static final class Profile {
        public final String id;
        public String name;

        Profile(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // --- The list

    private static SharedPreferences index(Context context) {
        return context.getApplicationContext().getSharedPreferences(INDEX_FILE, Context.MODE_PRIVATE);
    }

    public static List<Profile> list(Context context) {
        List<Profile> profiles = new ArrayList<>();
        String json = index(context).getString(KEY_LIST, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                profiles.add(new Profile(item.getString("id"), item.optString("name")));
            }
        } catch (JSONException e) {
            LimeLog.warning("Profiles: unreadable list: " + e);
        }
        return profiles;
    }

    /** Saves the list in its order, the one of the profiles menu. */
    public static void save(Context context, List<Profile> profiles) {
        JSONArray array = new JSONArray();
        try {
            for (Profile profile : profiles) {
                array.put(new JSONObject().put("id", profile.id).put("name", profile.name));
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        index(context).edit().putString(KEY_LIST, array.toString()).apply();
        notifyChanged();
    }

    public static Profile find(Context context, String id) {
        for (Profile profile : list(context)) {
            if (profile.id.equals(id)) {
                return profile;
            }
        }
        return null;
    }

    public static Profile create(Context context, String name) {
        Profile profile = new Profile(UUID.randomUUID().toString(), name);
        List<Profile> profiles = list(context);
        profiles.add(profile);
        save(context, profiles);
        return profile;
    }

    public static void update(Context context, Profile changed) {
        List<Profile> profiles = list(context);
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).id.equals(changed.id)) {
                profiles.set(i, changed);
            }
        }
        save(context, profiles);
    }

    public static void delete(Context context, String id) {
        List<Profile> profiles = list(context);
        for (int i = profiles.size() - 1; i >= 0; i--) {
            if (profiles.get(i).id.equals(id)) {
                profiles.remove(i);
            }
        }
        values(context, id).edit().clear().apply();
        if (id.equals(selection(context))) {
            index(context).edit().putString(KEY_SELECTION, SELECTION_NONE).apply();
        }
        save(context, profiles);
    }

    /** The settings the profile changes, with their own types. */
    public static SharedPreferences values(Context context, String id) {
        return context.getApplicationContext().getSharedPreferences(VALUES_FILE_PREFIX + id, Context.MODE_PRIVATE);
    }

    // --- Which one is used

    /** {@link #SELECTION_NONE} or the id of the profile picked in the menu. */
    public static String selection(Context context) {
        return index(context).getString(KEY_SELECTION, SELECTION_NONE);
    }

    public static void setSelection(Context context, String selection) {
        index(context).edit().putString(KEY_SELECTION, selection).apply();
        notifyChanged();
    }

    /** The profile in use now, or null for the general settings (also for a choice an earlier build saved, like "auto"). */
    public static Profile active(Context context) {
        return find(context, selection(context));
    }

    /**
     * The settings to read: the general ones with the active profile's over them. Writes to a setting
     * of a profile go to the profile, the others to the general settings.
     */
    public static SharedPreferences prefs(Context context) {
        SharedPreferences general = PreferenceManager.getDefaultSharedPreferences(context);
        Profile profile = active(context);
        return profile == null ? general : new OverlayPreferences(general, values(context, profile.id), KEYS);
    }

    /** Like {@link #prefs}, but looking up the profile in use again at every read and write. */
    public static SharedPreferences live(Context context) {
        return new LivePreferences(context.getApplicationContext());
    }

    // --- Changes

    /** Called on the main thread when the list or the choice changes. */
    public static void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public static void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private static void notifyChanged() {
        mainHandler.post(() -> {
            for (Runnable listener : listeners) {
                listener.run();
            }
        });
    }
}
