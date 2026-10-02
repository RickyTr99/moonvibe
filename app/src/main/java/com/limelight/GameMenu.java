package com.limelight;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.limelight.preferences.CustomCommandEditorDialog;
import com.limelight.ui.apollo.hints.InputMode;
import com.limelight.ui.gamemenu.GameMenuView;
import com.limelight.ui.gamemenu.GameMenuView.Item;
import com.limelight.ui.gamemenu.GameMenuView.QuickAction;
import com.limelight.ui.gamemenu.GameMenuView.Tab;
import com.limelight.ui.overlay.CustomCommand;
import com.limelight.ui.overlay.CustomCommand.KeyCombination;
import com.limelight.ui.overlay.CustomCommandsManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The in-stream menu, opened by a multi-finger tap or by holding the overlay trigger button
 * on a gamepad. It merges the game menu from Artemis with the overlay menu from MoreOrLess.
 * Key shortcuts are the overlay menu's custom commands.
 */
public class GameMenu implements GameMenuView.Listener {
    private static final String PREF_DEFAULT_COMMANDS_ADDED = "moonvibe_default_commands_added";

    private static final KeyCombination SWITCH_WINDOW = new KeyCombination(false, true, false, false, KeyEvent.KEYCODE_TAB);
    private static final KeyCombination SHOW_DESKTOP = new KeyCombination(false, false, false, true, KeyEvent.KEYCODE_D);
    // Same as the Guide button (Start quick action), which opens the Game Bar on the host
    private static final KeyCombination GAME_BAR = new KeyCombination(false, false, false, true, KeyEvent.KEYCODE_G);

    private final Game game;
    private final GameMenuView view;
    private final CustomCommandsManager commandsManager;
    private AlertDialog currentDialog;

    public GameMenu(Game game, GameMenuView view) {
        this.game = game;
        this.view = view;
        this.commandsManager = new CustomCommandsManager(game);
        view.setListener(this);
    }

    private String getString(int id) {
        return game.getString(id);
    }

    public void show(boolean fromGamepad) {
        addDefaultCommands();
        commandsManager.reload();
        view.show(fromGamepad);
    }

    public void hide() {
        dismissDialog();
        view.close();
    }

    public boolean isOpen() {
        return view.isOpen();
    }

    public void refresh() {
        view.refresh();
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        return view.dispatchKeyEvent(event);
    }

    public boolean onGenericMotionEvent(MotionEvent event) {
        return view.onGenericMotionEvent(event);
    }

    @Override
    public List<QuickAction> buildQuickActions() {
        return Arrays.asList(
                new QuickAction(KeyEvent.KEYCODE_BUTTON_A, getString(R.string.game_menu_disconnect),
                        game::disconnectFromMenu),
                new QuickAction(KeyEvent.KEYCODE_BUTTON_X, getString(R.string.game_menu_quit_session),
                        game::quitSessionFromMenu).holdToConfirm(),
                new QuickAction(KeyEvent.KEYCODE_BUTTON_Y, getString(R.string.game_menu_stats),
                        game::toggleStatsOverlay).keepOpen().active(game::isStatsOverlayVisible),
                new QuickAction(KeyEvent.KEYCODE_BUTTON_START, getString(R.string.game_menu_key_game_bar),
                        this::openGameBar),
                new QuickAction(KeyEvent.KEYCODE_DPAD_UP, getString(R.string.game_menu_toggle_keyboard),
                        game::toggleKeyboard),
                new QuickAction(KeyEvent.KEYCODE_DPAD_DOWN, getString(R.string.game_menu_toggle_full_keyboard),
                        game::toggleFullKeyboard),
                new QuickAction(KeyEvent.KEYCODE_DPAD_LEFT, getString(R.string.game_menu_key_switch_window),
                        () -> sendKeys(SWITCH_WINDOW)),
                new QuickAction(KeyEvent.KEYCODE_DPAD_RIGHT, getString(R.string.game_menu_key_show_desktop),
                        () -> sendKeys(SHOW_DESKTOP)));
    }

    @Override
    public List<Tab> buildTabs() {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(new Tab(getString(R.string.game_menu_tab_input), buildInputItems()));
        tabs.add(new Tab(getString(R.string.game_menu_tab_keys), buildKeyItems()));
        return tabs;
    }

    @Override
    public void onMenuClosed() {
        dismissDialog();
    }

    // The Guide button works only from a gamepad the host knows: without one, Win+G opens the Game Bar
    private void openGameBar() {
        if (InputMode.isGamepadConnected()) {
            game.sendGuideButton();
        } else {
            sendKeys(GAME_BAR);
        }
    }

    private void sendKeys(KeyCombination combination) {
        CustomCommand command = new CustomCommand("", 0, combination);
        command.setPostAction(CustomCommand.POST_ACTION_CLOSE_MENU);
        game.runCustomCommand(command);
    }

    private List<Item> buildInputItems() {
        List<Item> items = new ArrayList<>();

        items.add(Item.header(getString(R.string.game_menu_section_controller)));
        items.add(Item.toggle(R.drawable.ic_overlay_gamepad, getString(R.string.game_menu_virtual_controller),
                game::isVirtualControllerVisible, game::toggleVirtualController));
        items.add(Item.toggle(R.drawable.ic_overlay_mouse, getString(R.string.game_menu_mouse_emulation),
                game::isMouseEmulationActive, game::toggleMouseEmulation));

        items.add(Item.header(getString(R.string.game_menu_section_touch)));
        String[] names = game.getResources().getStringArray(R.array.touch_mode_names);
        String[] values = game.getResources().getStringArray(R.array.touch_mode_values);
        items.add(Item.dropdown(R.drawable.ic_menu_touch, getString(R.string.game_menu_touch_mode), names,
                new GameMenuView.Selection() {
                    @Override
                    public int get() {
                        return Arrays.asList(values).indexOf(game.getTouchMode());
                    }

                    @Override
                    public void set(int index) {
                        game.setTouchMode(values[index]);
                    }
                }));

        return items;
    }

    // Each function shows up in one place only: shortcuts that are quick actions are left out
    private static boolean isQuickActionShortcut(KeyCombination combination) {
        String display = combination.toDisplayString();
        return display.equals(SWITCH_WINDOW.toDisplayString()) || display.equals(SHOW_DESKTOP.toDisplayString()) ||
                display.equals(GAME_BAR.toDisplayString());
    }

    private List<Item> buildKeyItems() {
        List<Item> items = new ArrayList<>();

        for (CustomCommand command : commandsManager.getCommands()) {
            if (isQuickActionShortcut(command.getKeyCombination())) {
                continue;
            }
            String combination = command.getKeyCombination().toDisplayString();
            items.add(Item.action(command.getIconResId(), command.getName(), () -> game.runCustomCommand(command))
                    // runCustomCommand() closes the menu itself if the command asks for it
                    .keepOpen()
                    .trailingText(combination.equals(command.getName()) ? null : combination)
                    .onLongPress(() -> showCommandOptions(command)));
        }

        items.add(Item.action(R.drawable.ic_add, getString(R.string.game_menu_add_shortcut),
                () -> showCommandEditor(null)).keepOpen().accent());

        return items;
    }

    private void showCommandOptions(CustomCommand command) {
        String[] options = {getString(R.string.game_menu_edit), getString(R.string.game_menu_delete)};

        dismissDialog();
        currentDialog = new AlertDialog.Builder(game)
                .setTitle(command.getName())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showCommandEditor(command);
                    } else {
                        commandsManager.removeCommand(command.getId());
                        view.refresh();
                    }
                })
                .show();
    }

    // Edits the command, or adds a new one if it is null. This is a dialog on top of the stream,
    // opening the settings would stop it.
    private void showCommandEditor(CustomCommand command) {
        dismissDialog();

        CustomCommandEditorDialog dialog = command == null
                ? new CustomCommandEditorDialog()
                : CustomCommandEditorDialog.newInstance(command);
        dialog.setOnCommandSavedListener(saved -> {
            if (command == null) {
                commandsManager.addCommand(saved);
            } else {
                commandsManager.updateCommand(command.getId(), saved);
            }
            view.refresh();
        });
        dialog.show(game.getFragmentManager(), "game_menu_command_editor");
    }

    private void dismissDialog() {
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
        }
        currentDialog = null;
    }

    // The fixed shortcuts of the old "Send keys" menu become regular commands, added once
    // so they can be edited, reordered or deleted like any other
    private void addDefaultCommands() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(game);
        if (prefs.getBoolean(PREF_DEFAULT_COMMANDS_ADDED, false)) {
            return;
        }

        addDefault("", R.drawable.ic_overlay_key_press, new KeyCombination(KeyEvent.KEYCODE_ESCAPE));
        addDefault(getString(R.string.game_menu_key_start_menu), R.drawable.ic_overlay_windows,
                new KeyCombination(KeyEvent.KEYCODE_META_LEFT));
        addDefault(getString(R.string.game_menu_key_task_manager), R.drawable.ic_overlay_task_manager,
                new KeyCombination(true, false, true, false, KeyEvent.KEYCODE_ESCAPE));
        addDefault(getString(R.string.game_menu_key_close_window), R.drawable.ic_overlay_close,
                new KeyCombination(false, true, false, false, KeyEvent.KEYCODE_F4));
        addDefault(getString(R.string.game_menu_key_toggle_fullscreen), R.drawable.ic_overlay_fullscreen,
                new KeyCombination(false, true, false, false, KeyEvent.KEYCODE_ENTER));
        addDefault(getString(R.string.game_menu_key_fullscreen), R.drawable.ic_overlay_fullscreen,
                new KeyCombination(KeyEvent.KEYCODE_F11));
        addDefault(getString(R.string.game_menu_key_paste), R.drawable.ic_overlay_key_press,
                new KeyCombination(true, false, false, false, KeyEvent.KEYCODE_V));
        addDefault("", R.drawable.ic_overlay_key_press,
                new KeyCombination(false, false, true, false, KeyEvent.KEYCODE_TAB));
        addDefault("", R.drawable.ic_overlay_window_menu,
                new KeyCombination(true, true, false, false, KeyEvent.KEYCODE_TAB));
        addDefault(getString(R.string.game_menu_key_move_monitor), R.drawable.ic_overlay_snap_left,
                new KeyCombination(false, false, true, true, KeyEvent.KEYCODE_DPAD_LEFT));
        addDefault(getString(R.string.game_menu_key_toggle_hdr), R.drawable.ic_overlay_hdr,
                new KeyCombination(false, true, false, true, KeyEvent.KEYCODE_B));
        addDefault("", R.drawable.ic_overlay_monitor,
                new KeyCombination(true, true, true, false, KeyEvent.KEYCODE_F1));
        addDefault("", R.drawable.ic_overlay_monitor,
                new KeyCombination(true, true, true, false, KeyEvent.KEYCODE_F12));

        prefs.edit().putBoolean(PREF_DEFAULT_COMMANDS_ADDED, true).apply();
    }

    private void addDefault(String name, int iconResId, KeyCombination combination) {
        CustomCommand command = new CustomCommand(name, iconResId, combination);
        command.setPostAction(CustomCommand.POST_ACTION_CLOSE_MENU);
        commandsManager.addCommand(command);
    }
}
