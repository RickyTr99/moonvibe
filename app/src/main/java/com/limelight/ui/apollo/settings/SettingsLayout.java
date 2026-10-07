package com.limelight.ui.apollo.settings;

import android.content.Context;
import android.view.KeyEvent;

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

    /**
     * A profile's page: name and rule, the settings a profile can change grouped as in the Video,
     * Codec and Audio categories, and the row that deletes it. Profiles.KEYS lists the same settings.
     */
    static Object[] profileEntries(String nameKey, String deleteKey) {
        return new Object[] {
                k(nameKey),
                new Section(R.string.apollo_settings_video, true,
                        k("list_resolution"), k("list_fps"), k("seekbar_bitrate_kbps"), k("checkbox_enable_hdr"),
                        k("checkbox_stretch_video"), k("checkbox_full_range"), k("spatial_dithering"), k("checkbox_unlock_fps"),
                        k("checkbox_enable_post_stream_toast"), k("checkbox_disable_warnings")),
                new Section(R.string.apollo_settings_latency, true,
                        k("video_format"), k("video_renderer"), k("pyrowave_late_frames"), k("checkbox_ultra_low_latency"),
                        k("frame_pacing"), k("jitter_buffer"), k("checkbox_reduce_refresh_rate"),
                        k("text_actual_display_refresh_rate")),
                new Section(R.string.apollo_settings_audio, true,
                        k("list_audio_config"), k("checkbox_enable_audiofx"), k("checkbox_host_audio")),
                k(deleteKey)};
    }

    static List<Category> categories() {
        List<Category> categories = new ArrayList<>();
        // Video is what the picture looks like, Codec how it gets to the screen: the settings tuned together
        // (codec, renderer, pacing) sit on one page. The stats go with the picture
        categories.add(new Category(R.string.apollo_settings_video, R.drawable.ic_apollo_cat_video,
                k("list_resolution"), k("list_fps"), k("seekbar_bitrate_kbps"),
                k("checkbox_enable_hdr"), k("checkbox_stretch_video"),
                new Section(R.string.apollo_section_stats, true,
                        k("stats_overlay"), k("checkbox_enable_post_stream_toast"), k("checkbox_disable_warnings")),
                new Section(R.string.apollo_section_advanced, false,
                        k("checkbox_full_range"), k("spatial_dithering"), k("checkbox_unlock_fps"))));
        categories.add(new Category(R.string.apollo_settings_latency, R.drawable.ic_apollo_cat_speed,
                new Section(R.string.apollo_section_decoding, true,
                        k("video_format"), k("video_renderer"), k("pyrowave_late_frames"), k("checkbox_ultra_low_latency")),
                new Section(R.string.apollo_section_frame_pacing, true,
                        k("frame_pacing"), k("jitter_buffer"), k("checkbox_reduce_refresh_rate"), k("text_actual_display_refresh_rate"))));
        categories.add(new Category(R.string.apollo_settings_audio, R.drawable.ic_apollo_cat_audio,
                k("list_audio_config"), k("checkbox_enable_audiofx"), k("checkbox_host_audio")));
        categories.add(new Category(R.string.apollo_settings_controller, R.drawable.ic_apollo_cat_gamepad,
                k("seekbar_deadzone"), k("checkbox_multi_controller"), k("checkbox_auto_connect_controllers"),
                k("checkbox_flip_face_buttons"),
                k("checkbox_gamepad_touchpad_as_mouse"), k("checkbox_gamepad_motion_sensors"),
                k("checkbox_gamepad_motion_fallback"),
                new Section(R.string.apollo_section_mouse_emulation, true,
                        k("seekbar_mouse_emulation_speed"), k("analog_scrolling"), k("seekbar_mouse_scroll_speed"), k("checkbox_invert_scroll")),
                new Section(R.string.apollo_section_vibration, false,
                        k("checkbox_vibrate_fallback"), k("seekbar_vibrate_fallback_strength")),
                new Section(R.string.apollo_section_advanced, false,
                        k("checkbox_usb_driver"), k("checkbox_usb_bind_all"))));
        categories.add(new Category(R.string.apollo_settings_shortcuts, R.drawable.ic_apollo_cat_shortcuts,
                new Section(R.string.apollo_section_hold, true,
                        k("shortcut_hold_select"), k("shortcut_hold_start"), k("shortcut_hold_guide"),
                        k("shortcut_hold_lb_rb"), k("overlay_hold_duration")),
                new Section(R.string.apollo_section_combinations, true,
                        k("shortcut_select_lb"), k("shortcut_select_rb")),
                // Only on AYN handhelds, the settings are removed elsewhere
                new Section(R.string.apollo_section_ayn_buttons, true,
                        k("list_ayn_back_button"), k("list_ayn_m1_button"), k("list_ayn_m2_button")),
                new Section(R.string.apollo_settings_game_menu, true,
                        k("overlay_custom_commands"))));
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
        categories.add(new Category(R.string.apollo_settings_session, R.drawable.ic_apollo_desktop,
                k("checkbox_auto_resume_stream"), k("list_leave_app"), k("checkbox_background_audio"),
                k("checkbox_enable_sops")));
        categories.add(new Category(R.string.apollo_settings_app, R.drawable.ic_apollo_info,
                k("checkbox_fullscreen_ui"), k("list_languages"), k("checkbox_small_icon_mode"),
                new Item(WebLauncherPreference.class)));
        return categories;
    }

    /** Shorter labels for the titles that do not fit on one line. */
    static final Map<String, Integer> SHORT_TITLES = new HashMap<>();
    static {
        SHORT_TITLES.put("checkbox_enable_post_stream_toast", R.string.apollo_label_post_stream_toast);
        SHORT_TITLES.put("seekbar_deadzone", R.string.apollo_label_deadzone);
        SHORT_TITLES.put("checkbox_gamepad_motion_sensors", R.string.apollo_label_motion_sensors);
        SHORT_TITLES.put("checkbox_gamepad_motion_fallback", R.string.apollo_label_motion_fallback);
        SHORT_TITLES.put("checkbox_enable_audiofx", R.string.apollo_label_audiofx);
        SHORT_TITLES.put("shortcut_hold_select", R.string.apollo_label_select);
        SHORT_TITLES.put("shortcut_hold_start", R.string.apollo_label_start);
        SHORT_TITLES.put("shortcut_hold_guide", R.string.apollo_label_guide);
        SHORT_TITLES.put("shortcut_hold_lb_rb", R.string.apollo_label_lb_rb);
        SHORT_TITLES.put("overlay_hold_duration", R.string.apollo_label_hold_duration);
    }

    static final int SHORT_TITLE_RESET_OSC = R.string.apollo_label_reset_osc;

    /** The controller buttons drawn before the name of a shortcut (key codes of ButtonGlyph). */
    static final Map<String, int[]> ROW_GLYPHS = new HashMap<>();
    // Not a button: the clock of the hold duration
    static final int GLYPH_TIMER = -100;
    static {
        ROW_GLYPHS.put("shortcut_hold_select", new int[]{KeyEvent.KEYCODE_BUTTON_SELECT});
        ROW_GLYPHS.put("shortcut_hold_start", new int[]{KeyEvent.KEYCODE_BUTTON_START});
        ROW_GLYPHS.put("shortcut_hold_guide", new int[]{KeyEvent.KEYCODE_BUTTON_MODE});
        ROW_GLYPHS.put("shortcut_hold_lb_rb", new int[]{KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1});
        ROW_GLYPHS.put("overlay_hold_duration", new int[]{GLYPH_TIMER});
        ROW_GLYPHS.put("shortcut_select_lb", new int[]{KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_L1});
        ROW_GLYPHS.put("shortcut_select_rb", new int[]{KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_R1});
        ROW_GLYPHS.put("list_ayn_back_button", new int[]{KeyEvent.KEYCODE_BACK});
        ROW_GLYPHS.put("list_ayn_m1_button", new int[]{KeyEvent.KEYCODE_BUTTON_C});
        ROW_GLYPHS.put("list_ayn_m2_button", new int[]{KeyEvent.KEYCODE_BUTTON_Z});
    }

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
        INFO.put("checkbox_auto_connect_controllers", new Info(R.string.apollo_info_auto_connect_controllers));
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
        INFO.put("overlay_hold_duration", new Info(R.string.apollo_info_hold_duration));
        INFO.put("shortcut_select_lb", new Info(R.string.apollo_info_select_combo));
        INFO.put("shortcut_select_rb", new Info(R.string.apollo_info_select_combo));
        INFO.put("overlay_custom_commands", new Info(R.string.apollo_info_overlay_commands));
        INFO.put("checkbox_auto_resume_stream", new Info(R.string.apollo_info_auto_resume));
        INFO.put("list_leave_app", new Info(R.string.apollo_info_leave_app, R.array.leave_app_values,
                R.array.apollo_info_leave_app_names, R.array.apollo_info_leave_app_texts));
        INFO.put("checkbox_background_audio", new Info(R.string.apollo_info_background_audio));
        INFO.put("checkbox_enable_sops", new Info(R.string.apollo_info_sops));
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
