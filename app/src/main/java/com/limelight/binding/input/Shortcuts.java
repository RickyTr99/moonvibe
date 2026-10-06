package com.limelight.binding.input;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

import com.limelight.R;
import com.limelight.ui.overlay.CustomCommand;
import com.limelight.ui.overlay.CustomCommand.KeyCombination;
import com.limelight.ui.overlay.CustomCommandsManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The controller shortcuts of the stream: which gesture (a button held, or Select with a bumper)
 * runs which action, as chosen in the Shortcuts settings.
 */
public final class Shortcuts {
    private Shortcuts() {
    }

    public static final String HOLD_SELECT = "shortcut_hold_select";
    public static final String HOLD_START = "shortcut_hold_start";
    public static final String HOLD_GUIDE = "shortcut_hold_guide";
    public static final String HOLD_LB_RB = "shortcut_hold_lb_rb";
    public static final String SELECT_LB = "shortcut_select_lb";
    public static final String SELECT_RB = "shortcut_select_rb";
    public static final String HOLD_DURATION = "overlay_hold_duration";
    public static final String AYN_BACK = "list_ayn_back_button";
    public static final String AYN_M1 = "list_ayn_m1_button";
    public static final String AYN_M2 = "list_ayn_m2_button";

    public static final String NONE = "none";
    public static final String GAME_MENU = "game_menu";
    public static final String MOUSE = "mouse";
    public static final String KEYBOARD = "keyboard";
    public static final String FULL_KEYBOARD = "full_keyboard";
    public static final String STATS = "stats";
    public static final String VIRTUAL_CONTROLLER = "virtual_controller";
    public static final String TOUCH_MODE = "touch_mode";
    public static final String DISCONNECT = "disconnect";
    public static final String WIN_GAME_BAR = "win_game_bar";
    public static final String WIN_QUICK_SETTINGS = "win_quick_settings";
    public static final String WIN_DESKTOP = "win_desktop";
    public static final String WIN_SWITCH_WINDOW = "win_switch_window";
    public static final String WIN_TASK_VIEW = "win_task_view";
    public static final String WIN_TASK_MANAGER = "win_task_manager";
    public static final String WIN_SCREENSHOT = "win_screenshot";
    public static final String WIN_RECORD = "win_record";
    // Followed by the id of one of the custom commands of the game menu
    public static final String COMMAND = "command:";
    // AYN buttons only: they act as another controller button
    public static final String AS_SELECT = "select";
    public static final String AS_GUIDE = "guide";
    public static final String AS_SHARE = "share";

    private static final String[] APP_ACTIONS = {NONE, GAME_MENU, MOUSE, KEYBOARD, FULL_KEYBOARD, STATS,
            VIRTUAL_CONTROLLER, TOUCH_MODE, DISCONNECT};
    private static final int[] APP_LABELS = {R.string.shortcut_action_none, R.string.shortcut_action_game_menu,
            R.string.shortcut_action_mouse, R.string.shortcut_action_keyboard, R.string.shortcut_action_full_keyboard,
            R.string.shortcut_action_stats, R.string.shortcut_action_virtual_controller,
            R.string.shortcut_action_touch_mode, R.string.shortcut_action_disconnect};
    private static final String[] WINDOWS_ACTIONS = {WIN_GAME_BAR, WIN_QUICK_SETTINGS, WIN_DESKTOP,
            WIN_SWITCH_WINDOW, WIN_TASK_VIEW, WIN_TASK_MANAGER, WIN_SCREENSHOT, WIN_RECORD};
    private static final int[] WINDOWS_LABELS = {R.string.shortcut_action_game_bar,
            R.string.game_menu_key_windows_quick_settings, R.string.shortcut_action_desktop,
            R.string.shortcut_action_switch_window, R.string.shortcut_action_task_view, R.string.shortcut_action_task_manager,
            R.string.shortcut_action_screenshot, R.string.shortcut_action_record};
    private static final String[] AYN_ACTIONS = {AS_SELECT, AS_GUIDE, AS_SHARE};
    private static final int[] AYN_LABELS = {R.string.ayn_button_select, R.string.ayn_button_guide,
            R.string.ayn_button_share};

    /** The keys sent to the PC by a Windows action, or null for the other actions. */
    public static KeyCombination keys(String action) {
        switch (action) {
            case WIN_GAME_BAR:
                return new KeyCombination(false, false, false, true, KeyEvent.KEYCODE_G);
            case WIN_QUICK_SETTINGS:
                return new KeyCombination(false, false, false, true, KeyEvent.KEYCODE_A);
            case WIN_DESKTOP:
                return new KeyCombination(false, false, false, true, KeyEvent.KEYCODE_D);
            case WIN_SWITCH_WINDOW:
                return new KeyCombination(false, true, false, false, KeyEvent.KEYCODE_TAB);
            case WIN_TASK_VIEW:
                return new KeyCombination(false, false, false, true, KeyEvent.KEYCODE_TAB);
            case WIN_TASK_MANAGER:
                return new KeyCombination(true, false, true, false, KeyEvent.KEYCODE_ESCAPE);
            case WIN_SCREENSHOT:
                return new KeyCombination(false, false, false, true, KeyEvent.KEYCODE_SYSRQ);
            case WIN_RECORD:
                return new KeyCombination(false, true, false, true, KeyEvent.KEYCODE_R);
            default:
                return null;
        }
    }

    public static final class Option {
        public final String value;
        public final CharSequence label;
        // The keys of a Windows action, or null
        public final String keys;

        Option(String value, CharSequence label, String keys) {
            this.value = value;
            this.label = label;
            this.keys = keys;
        }
    }

    public static final class Group {
        public final CharSequence title;
        public final List<Option> options = new ArrayList<>();

        Group(CharSequence title) {
            this.title = title;
        }
    }

    /** What a shortcut can do, in groups; the AYN buttons can also act as another controller button. */
    public static List<Group> groups(Context context, boolean aynButton) {
        List<Group> groups = new ArrayList<>();
        Group app = new Group(context.getString(R.string.shortcut_group_app));
        for (int i = 0; i < APP_ACTIONS.length; i++) {
            app.options.add(new Option(APP_ACTIONS[i], context.getString(APP_LABELS[i]), null));
        }
        groups.add(app);

        if (aynButton) {
            Group buttons = new Group(context.getString(R.string.shortcut_group_buttons));
            for (int i = 0; i < AYN_ACTIONS.length; i++) {
                buttons.options.add(new Option(AYN_ACTIONS[i], context.getString(AYN_LABELS[i]), null));
            }
            groups.add(buttons);
        }

        Group windows = new Group(context.getString(R.string.shortcut_group_windows));
        Set<String> shown = new HashSet<>();
        for (int i = 0; i < WINDOWS_ACTIONS.length; i++) {
            String combination = keys(WINDOWS_ACTIONS[i]).toDisplayString();
            shown.add(combination);
            windows.options.add(new Option(WINDOWS_ACTIONS[i], context.getString(WINDOWS_LABELS[i]), combination));
        }
        groups.add(windows);

        // The custom commands that the Windows group does not have already (the game menu adds
        // Task Manager and others as commands); a command without a name is its keys, shown once
        Group own = new Group(context.getString(R.string.shortcut_group_commands));
        for (CustomCommand command : new CustomCommandsManager(context).getCommands()) {
            String combination = command.getKeyCombination() != null ? command.getKeyCombination().toDisplayString() : null;
            if (combination != null && !shown.add(combination)) {
                continue;
            }
            String name = command.getName();
            own.options.add(new Option(COMMAND + command.getId(), name,
                    combination != null && !combination.equals(name) ? combination : null));
        }
        if (!own.options.isEmpty()) {
            groups.add(own);
        }
        return groups;
    }

    /** The custom command an action runs, or null. */
    public static CustomCommand command(Context context, String action) {
        if (action == null || !action.startsWith(COMMAND)) {
            return null;
        }
        String id = action.substring(COMMAND.length());
        for (CustomCommand command : new CustomCommandsManager(context).getCommands()) {
            if (id.equals(command.getId())) {
                return command;
            }
        }
        return null;
    }

    public static String defaultValue(String key) {
        switch (key) {
            case HOLD_SELECT:
                return GAME_MENU;
            case HOLD_START:
                return MOUSE;
            case AYN_BACK:
                return GAME_MENU;
            case AYN_M1:
                return AS_GUIDE;
            default:
                return NONE;
        }
    }

    /**
     * Before the Shortcuts settings, one button held opened the game menu (overlay_trigger_button):
     * that choice moves to the new settings once.
     */
    public static void migrate(SharedPreferences prefs) {
        if (prefs.contains(HOLD_SELECT) || !prefs.contains("overlay_trigger_button")) {
            return;
        }
        String trigger = prefs.getString("overlay_trigger_button", "select");
        SharedPreferences.Editor editor = prefs.edit();
        switch (trigger) {
            case "start":
                editor.putString(HOLD_SELECT, NONE).putString(HOLD_START, GAME_MENU);
                break;
            case "guide":
                editor.putString(HOLD_SELECT, NONE).putString(HOLD_GUIDE, GAME_MENU);
                break;
            case "lb_rb":
                editor.putString(HOLD_SELECT, NONE).putString(HOLD_LB_RB, GAME_MENU);
                break;
            default:
                editor.putString(HOLD_SELECT, GAME_MENU);
                break;
        }
        editor.apply();
    }
}
