package com.limelight.ui.apollo.settings;

import android.content.Context;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.preference.TwoStatePreference;

import com.limelight.R;

/**
 * When a setting has no effect because of another one: what its row says under the name, what its
 * explanation adds, and the change that makes it work. It only reads the settings: turning a
 * preference off stays with the original logic, these rules only explain it, or turn off a row
 * whose setting would do nothing.
 */
final class SettingsRules {
    private SettingsRules() {
    }

    /** The names the settings screen shows, with its shorter labels. */
    interface Labels {
        CharSequence title(Preference pref);

        CharSequence option(ListPreference pref, int index);
    }

    static final class Block {
        // Under the name of the setting
        final CharSequence hint;
        // In its explanation
        final CharSequence notice;
        // A note only: the setting still works in some cases, so its row stays on
        final boolean soft;
        // What the explanation's button changes, or null
        final Preference actionPref;
        final Object actionValue;
        final CharSequence actionLabel;

        Block(CharSequence hint, CharSequence notice, boolean soft,
              Preference actionPref, Object actionValue, CharSequence actionLabel) {
            this.hint = hint;
            this.notice = notice;
            this.soft = soft;
            this.actionPref = actionPref;
            this.actionValue = actionValue;
            this.actionLabel = actionLabel;
        }
    }

    private static final String PACING_HOST_TIMED = "host-timed";
    private static final String LEAVE_APP_RECONNECT = "reconnect";
    private static final String LEAVE_APP_KEEP = "keep";

    static Block check(Context context, PreferenceScreen screen, Preference pref, Labels labels) {
        String key = pref.getKey();
        if (key != null) {
            // With PyroWave the host can still lack it: the stream then falls back to HEVC or H.264,
            // and the settings of those apply again
            boolean pyrowave = "forcepyrowave".equals(value(screen, "video_format"));
            switch (key) {
                case "video_renderer":
                    if (pyrowave) {
                        return note(context, R.string.apollo_rule_renderer_pyrowave_hint, R.string.apollo_rule_renderer_pyrowave);
                    }
                    break;
                case "checkbox_ultra_low_latency":
                    if (pyrowave) {
                        return note(context, R.string.apollo_rule_ull_pyrowave_hint, R.string.apollo_rule_ull_pyrowave);
                    }
                    break;
                case "pyrowave_late_frames":
                    if (!pyrowave) {
                        return new Block(context.getString(R.string.apollo_rule_needs_pyrowave_hint),
                                context.getString(R.string.apollo_rule_needs_pyrowave), false, null, null, null);
                    }
                    return needsValue(context, screen, "frame_pacing", PACING_HOST_TIMED, labels);
                case "jitter_buffer":
                    return needsValue(context, screen, "frame_pacing", PACING_HOST_TIMED, labels);
                case "spatial_dithering":
                case "checkbox_sharpening":
                case "seekbar_sharpening_strength": {
                    // PyroWave always shows through the Vulkan renderer; the strength then follows its switch
                    Block block = pyrowave ? null : needsValue(context, screen, "video_renderer", "vulkan", labels);
                    if (block != null) {
                        return block;
                    }
                    break;
                }
                case "checkbox_reduce_refresh_rate":
                    // The other pacing modes decide on their own (Game.mayReduceRefreshRate)
                    return needsValue(context, screen, "frame_pacing", "balanced", labels);
                case "list_gesture_3_finger":
                case "list_gesture_4_finger":
                case "list_gesture_5_finger": {
                    // The switch turns them off in multi-touch mode only (Game.handleMultiFingerTap)
                    Preference gestures = screen.findPreference("checkbox_multi_touch_gestures");
                    if ("multi_touch".equals(value(screen, "list_touch_mode")) &&
                            gestures instanceof TwoStatePreference && !((TwoStatePreference) gestures).isChecked()) {
                        CharSequence title = labels.title(gestures);
                        return new Block(context.getString(R.string.apollo_rule_needs_on_hint, title),
                                context.getString(R.string.apollo_rule_needs_on, title), false,
                                gestures, Boolean.TRUE, context.getString(R.string.apollo_rule_turn_on));
                    }
                    break;
                }
                case "seekbar_mouse_scroll_speed":
                case "checkbox_invert_scroll":
                    if ("none".equals(value(screen, "analog_scrolling"))) {
                        return needsValue(context, screen, "analog_scrolling", "right", labels);
                    }
                    break;
                case "checkbox_background_audio":
                    return needsValue(context, screen, "list_leave_app", LEAVE_APP_KEEP, labels);
                case "checkbox_auto_resume_stream": {
                    // Game keeps or reconnects the stream after sleep too, without the resume of PcView
                    String leave = value(screen, "list_leave_app");
                    if (LEAVE_APP_RECONNECT.equals(leave) || LEAVE_APP_KEEP.equals(leave)) {
                        ListPreference list = (ListPreference) screen.findPreference("list_leave_app");
                        CharSequence title = labels.title(list);
                        int index = list.findIndexOfValue(leave);
                        CharSequence current = index >= 0 ? shortLabel(labels.option(list, index)) : "";
                        return new Block(context.getString(R.string.apollo_rule_resume_covered_hint, title),
                                context.getString(R.string.apollo_rule_resume_covered, title, current), false,
                                null, null, null);
                    }
                    break;
                }
            }
        }

        // A dependency of preferences.xml on a switch
        String dependency = pref.getDependency();
        Preference other = dependency != null ? screen.findPreference(dependency) : null;
        if (other instanceof TwoStatePreference && !((TwoStatePreference) other).isChecked()) {
            CharSequence title = labels.title(other);
            return new Block(context.getString(R.string.apollo_rule_needs_on_hint, title),
                    context.getString(R.string.apollo_rule_needs_on, title), false,
                    other, Boolean.TRUE, context.getString(R.string.apollo_rule_turn_on));
        }
        return null;
    }

    private static Block note(Context context, int hint, int notice) {
        return new Block(context.getString(hint), context.getString(notice), true, null, null, null);
    }

    // Null when the other setting already has the value
    private static Block needsValue(Context context, PreferenceScreen screen, String otherKey, String wanted, Labels labels) {
        Preference other = screen.findPreference(otherKey);
        if (!(other instanceof ListPreference)) {
            return null;
        }
        ListPreference list = (ListPreference) other;
        if (wanted.equals(list.getValue())) {
            return null;
        }
        int wantedIndex = list.findIndexOfValue(wanted);
        if (wantedIndex < 0) {
            // Not offered on this device
            return null;
        }
        int currentIndex = list.findIndexOfValue(list.getValue());
        CharSequence wantedLabel = shortLabel(labels.option(list, wantedIndex));
        CharSequence currentLabel = currentIndex >= 0 ? shortLabel(labels.option(list, currentIndex)) : "";
        return new Block(context.getString(R.string.apollo_rule_needs_value_hint, wantedLabel),
                context.getString(R.string.apollo_rule_needs_value, labels.title(list), wantedLabel, currentLabel), false,
                list, wanted, context.getString(R.string.apollo_rule_switch_to, wantedLabel));
    }

    private static String value(PreferenceScreen screen, String key) {
        Preference pref = screen.findPreference(key);
        return pref instanceof ListPreference ? ((ListPreference) pref).getValue() : null;
    }

    // "Vulkan (Experimental)" reads as "Vulkan" in a sentence
    private static String shortLabel(CharSequence label) {
        String text = label.toString();
        int bracket = text.indexOf(" (");
        return bracket > 0 ? text.substring(0, bracket) : text;
    }
}
