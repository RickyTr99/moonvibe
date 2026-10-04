package com.limelight.ui.apollo.settings;

import android.content.Context;

import com.limelight.R;
import com.limelight.preferences.ConfirmDeleteOscPreference;
import com.limelight.preferences.WebLauncherPreference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How the settings of preferences.xml are grouped in the MoonVibe settings screen.
 * Only the grouping and a few shorter labels are ours: the preferences, their options and defaults are the original ones.
 */
final class SettingsLayout {
    private SettingsLayout() {
    }

    /** A setting, found by key, or by class for the few without a key. */
    static final class Item {
        final String key;
        final Class<?> type;

        Item(String key) {
            this.key = key;
            this.type = null;
        }

        Item(Class<?> type) {
            this.key = null;
            this.type = type;
        }
    }

    /** A collapsible group of settings inside a category. */
    static final class Section {
        final int titleRes;
        final boolean openByDefault;
        final List<Item> items;

        Section(int titleRes, boolean openByDefault, Item... items) {
            this.titleRes = titleRes;
            this.openByDefault = openByDefault;
            this.items = new ArrayList<>(Arrays.asList(items));
        }
    }

    static final class Category {
        final int titleRes;
        final int iconRes;
        // Item or Section
        final List<Object> entries;

        // A title given as text, for a page built at run time
        final CharSequence title;

        Category(CharSequence title, Object... entries) {
            this.title = title;
            this.titleRes = 0;
            this.iconRes = 0;
            this.entries = new ArrayList<>(Arrays.asList(entries));
        }

        CharSequence title(Context context) {
            return title != null ? title : context.getString(titleRes);
        }

        Category(int titleRes, int iconRes, Object... entries) {
            this.title = null;
            this.titleRes = titleRes;
            this.iconRes = iconRes;
            this.entries = new ArrayList<>(Arrays.asList(entries));
        }
    }

    private static Item k(String key) {
        return new Item(key);
    }

    static List<Category> categories() {
        List<Category> categories = new ArrayList<>();
        categories.add(new Category(R.string.apollo_settings_video, R.drawable.ic_apollo_cat_video,
                k("list_resolution"), k("list_fps"), k("seekbar_bitrate_kbps"), k("video_format"),
                k("checkbox_enable_hdr"), k("checkbox_stretch_video"),
                new Section(R.string.apollo_section_advanced, false,
                        k("pyrowave_late_frames"), k("video_renderer"), k("spatial_dithering"), k("checkbox_full_range"),
                        k("checkbox_unlock_fps"), k("checkbox_reduce_refresh_rate"), k("text_actual_display_refresh_rate"))));
        categories.add(new Category(R.string.apollo_settings_latency, R.drawable.ic_apollo_cat_speed,
                k("frame_pacing"), k("jitter_buffer"), k("checkbox_ultra_low_latency"),
                k("checkbox_enable_perf_overlay"), k("checkbox_enable_post_stream_toast"), k("checkbox_disable_warnings")));
        categories.add(new Category(R.string.apollo_settings_audio, R.drawable.ic_apollo_cat_audio,
                k("list_audio_config"), k("checkbox_enable_audiofx"), k("checkbox_host_audio")));
        categories.add(new Category(R.string.apollo_settings_controller, R.drawable.ic_apollo_cat_gamepad,
                k("seekbar_deadzone"), k("checkbox_multi_controller"), k("checkbox_flip_face_buttons"),
                k("checkbox_gamepad_touchpad_as_mouse"), k("checkbox_gamepad_motion_sensors"),
                k("checkbox_gamepad_motion_fallback"), k("analog_scrolling"),
                new Section(R.string.apollo_section_vibration, false,
                        k("checkbox_vibrate_fallback"), k("seekbar_vibrate_fallback_strength")),
                // Only on AYN handhelds, the settings are removed elsewhere
                new Section(R.string.apollo_section_ayn_buttons, true,
                        k("list_ayn_back_button"), k("list_ayn_m1_button"), k("list_ayn_m2_button")),
                new Section(R.string.apollo_section_advanced, false,
                        k("checkbox_usb_driver"), k("checkbox_usb_bind_all"))));
        categories.add(new Category(R.string.apollo_settings_touch, R.drawable.ic_apollo_cat_touch,
                k("list_touch_mode"), k("checkbox_multi_touch_gestures"), k("list_gesture_3_finger"),
                k("list_gesture_4_finger"), k("list_gesture_5_finger"), k("checkbox_mouse_nav_buttons"),
                k("checkbox_absolute_mouse_mode"), k("checkbox_brightness_edge_slider")));
        categories.add(new Category(R.string.apollo_settings_onscreen, R.drawable.ic_apollo_cat_keyboard,
                new Section(R.string.apollo_section_onscreen_gamepad, true,
                        k("checkbox_show_onscreen_controls"), k("checkbox_vibrate_osc"), k("checkbox_only_show_L3R3"),
                        k("checkbox_show_guide_button"), k("seekbar_osc_opacity"), new Item(ConfirmDeleteOscPreference.class)),
                new Section(R.string.apollo_section_full_keyboard, true,
                        k("checkbox_enable_sticky_modifier_key_virtual_keyboard"), k("checkbox_vibrate_keyboard"),
                        k("seekbar_keyboard_axi_opacity"))));
        categories.add(new Category(R.string.apollo_settings_game_menu, R.drawable.ic_apollo_cat_menu,
                k("overlay_trigger_button"), k("overlay_hold_duration"), k("overlay_custom_commands")));
        categories.add(new Category(R.string.apollo_settings_session, R.drawable.ic_apollo_desktop,
                k("checkbox_auto_resume_stream"), k("checkbox_enable_sops"), k("checkbox_enable_pip")));
        categories.add(new Category(R.string.apollo_settings_app, R.drawable.ic_apollo_info,
                k("checkbox_fullscreen_ui"), k("list_languages"), k("checkbox_small_icon_mode"),
                new Item(WebLauncherPreference.class)));
        return categories;
    }

    /** Shorter labels for the titles that do not fit on one line. */
    static final Map<String, Integer> SHORT_TITLES = new HashMap<>();
    static {
        SHORT_TITLES.put("checkbox_enable_perf_overlay", R.string.apollo_label_perf_overlay);
        SHORT_TITLES.put("checkbox_enable_post_stream_toast", R.string.apollo_label_post_stream_toast);
        SHORT_TITLES.put("seekbar_deadzone", R.string.apollo_label_deadzone);
        SHORT_TITLES.put("checkbox_gamepad_motion_sensors", R.string.apollo_label_motion_sensors);
        SHORT_TITLES.put("checkbox_gamepad_motion_fallback", R.string.apollo_label_motion_fallback);
        SHORT_TITLES.put("checkbox_enable_audiofx", R.string.apollo_label_audiofx);
    }

    static final int SHORT_TITLE_RESET_OSC = R.string.apollo_label_reset_osc;

    /**
     * What a setting does, for the popup of its "i". A list setting can also explain its options:
     * short names and texts in the order of the values array of preferences.xml.
     */
    static final class Info {
        final int text;
        final int optionValues;
        final int optionNames;
        final int optionTexts;

        Info(int text) {
            this(text, 0, 0, 0);
        }

        Info(int text, int optionValues, int optionNames, int optionTexts) {
            this.text = text;
            this.optionValues = optionValues;
            this.optionNames = optionNames;
            this.optionTexts = optionTexts;
        }
    }

    /** The settings with an "i", by preference key; the obvious ones have none. */
    static final Map<String, Info> INFO = new HashMap<>();
    static {
        INFO.put("seekbar_bitrate_kbps", new Info(R.string.apollo_info_bitrate));
        INFO.put("video_format", new Info(R.string.apollo_info_video_format, R.array.video_format_values,
                R.array.apollo_info_video_format_names, R.array.apollo_info_video_format_texts));
        INFO.put("checkbox_enable_hdr", new Info(R.string.apollo_info_hdr));
        INFO.put("checkbox_stretch_video", new Info(R.string.apollo_info_stretch_video));
        INFO.put("pyrowave_late_frames", new Info(R.string.apollo_info_pyrowave_late_frames, R.array.pyrowave_late_frames_values,
                R.array.apollo_info_pyrowave_late_frames_names, R.array.apollo_info_pyrowave_late_frames_texts));
        INFO.put("video_renderer", new Info(R.string.apollo_info_video_renderer, R.array.video_renderer_values,
                R.array.apollo_info_video_renderer_names, R.array.apollo_info_video_renderer_texts));
        INFO.put("spatial_dithering", new Info(R.string.apollo_info_spatial_dithering));
        INFO.put("checkbox_full_range", new Info(R.string.apollo_info_full_range));
        INFO.put("checkbox_unlock_fps", new Info(R.string.apollo_info_unlock_fps));
        INFO.put("checkbox_reduce_refresh_rate", new Info(R.string.apollo_info_reduce_refresh_rate));
        INFO.put("text_actual_display_refresh_rate", new Info(R.string.apollo_info_actual_refresh_rate));
        INFO.put("frame_pacing", new Info(R.string.apollo_info_frame_pacing, R.array.video_frame_pacing_values,
                R.array.apollo_info_frame_pacing_names, R.array.apollo_info_frame_pacing_texts));
        INFO.put("jitter_buffer", new Info(R.string.apollo_info_jitter_buffer, R.array.jitter_buffer_values,
                R.array.apollo_info_jitter_buffer_names, R.array.apollo_info_jitter_buffer_texts));
        INFO.put("checkbox_ultra_low_latency", new Info(R.string.apollo_info_ultra_low_latency));
        INFO.put("checkbox_disable_warnings", new Info(R.string.apollo_info_disable_warnings));
        INFO.put("checkbox_enable_audiofx", new Info(R.string.apollo_info_audiofx));
        INFO.put("checkbox_host_audio", new Info(R.string.apollo_info_host_audio));
        INFO.put("seekbar_deadzone", new Info(R.string.apollo_info_deadzone));
        INFO.put("checkbox_multi_controller", new Info(R.string.apollo_info_multi_controller));
        INFO.put("checkbox_flip_face_buttons", new Info(R.string.apollo_info_flip_face_buttons));
        INFO.put("checkbox_gamepad_touchpad_as_mouse", new Info(R.string.apollo_info_touchpad_as_mouse));
        INFO.put("checkbox_gamepad_motion_sensors", new Info(R.string.apollo_info_motion_sensors));
        INFO.put("checkbox_gamepad_motion_fallback", new Info(R.string.apollo_info_motion_fallback));
        INFO.put("analog_scrolling", new Info(R.string.apollo_info_analog_scrolling));
        INFO.put("checkbox_vibrate_fallback", new Info(R.string.apollo_info_vibrate_fallback));
        INFO.put("checkbox_usb_driver", new Info(R.string.apollo_info_usb_driver));
        INFO.put("checkbox_usb_bind_all", new Info(R.string.apollo_info_usb_bind_all));
        INFO.put("list_touch_mode", new Info(R.string.apollo_info_touch_mode, R.array.touch_mode_values,
                R.array.apollo_info_touch_mode_names, R.array.apollo_info_touch_mode_texts));
        INFO.put("checkbox_multi_touch_gestures", new Info(R.string.apollo_info_multi_touch_gestures));
        INFO.put("checkbox_mouse_nav_buttons", new Info(R.string.apollo_info_mouse_nav_buttons));
        INFO.put("checkbox_absolute_mouse_mode", new Info(R.string.apollo_info_absolute_mouse_mode));
        INFO.put("checkbox_brightness_edge_slider", new Info(R.string.apollo_info_brightness_edge_slider));
        INFO.put("checkbox_only_show_L3R3", new Info(R.string.apollo_info_only_l3r3));
        INFO.put("checkbox_enable_sticky_modifier_key_virtual_keyboard", new Info(R.string.apollo_info_sticky_modifiers));
        INFO.put("overlay_trigger_button", new Info(R.string.apollo_info_overlay_trigger));
        INFO.put("overlay_custom_commands", new Info(R.string.apollo_info_overlay_commands));
        INFO.put("checkbox_auto_resume_stream", new Info(R.string.apollo_info_auto_resume));
        INFO.put("checkbox_enable_sops", new Info(R.string.apollo_info_sops));
        INFO.put("checkbox_enable_pip", new Info(R.string.apollo_info_pip));
        INFO.put("checkbox_fullscreen_ui", new Info(R.string.apollo_info_fullscreen_ui));
        INFO.put("checkbox_small_icon_mode", new Info(R.string.apollo_info_small_icon_mode));
    }

    /** Shorter labels for a few options, by preference key and option value. */
    static final Map<String, Integer> SHORT_OPTIONS = new HashMap<>();
    static {
        SHORT_OPTIONS.put("frame_pacing/smoothness", R.string.apollo_option_pacing_smoothness);
        SHORT_OPTIONS.put("frame_pacing/host-timed", R.string.apollo_option_pacing_host_timed);
        SHORT_OPTIONS.put("analog_scrolling/none", R.string.apollo_option_analogscroll_none);
    }
}
