package com.limelight;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.limelight.preferences.CustomCommandEditorDialog;
import com.limelight.ui.apollo.hints.InputMode;
import com.limelight.ui.apollo.stats.StatsPrefs;
import com.limelight.ui.gamemenu.GameMenuView;
import com.limelight.ui.gamemenu.GameMenuView.Item;
import com.limelight.ui.BrightnessSliderView;
import com.limelight.ui.gamemenu.GameMenuView.QuickAction;
import com.limelight.ui.gamemenu.GameMenuView.QuickSlider;
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
    // Win+A: the quick settings of Windows (Wi-Fi, Bluetooth, volume...) at the bottom right
    private static final KeyCombination WINDOWS_QUICK_SETTINGS = new KeyCombination(false, false, false, true, KeyEvent.KEYCODE_A);
    // The Windows key alone: the Start menu
    private static final KeyCombination START_MENU = new KeyCombination(KeyEvent.KEYCODE_META_LEFT);

    private final Game game;
    private final GameMenuView view;
    private final CustomCommandsManager commandsManager;
    private final AudioManager audio;
    private int volumeBeforeMute;
    private AlertDialog currentDialog;

    public GameMenu(Game game, GameMenuView view) {
        this.game = game;
        this.view = view;
        this.audio = (AudioManager) game.getSystemService(Context.AUDIO_SERVICE);
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
                        game::quitSessionFromMenu).holdToConfirm().shortLabel(getString(R.string.game_menu_short_quit)),
                new QuickAction(KeyEvent.KEYCODE_BUTTON_Y, getString(R.string.game_menu_stats),
                        game::toggleStatsOverlay).active(game::isStatsOverlayVisible)
                        .shortLabel(getString(R.string.game_menu_short_stats)),
                new QuickAction(KeyEvent.KEYCODE_BUTTON_START, getString(R.string.game_menu_key_game_bar),
                        this::openGameBar).shortLabel(getString(R.string.game_menu_short_game_bar)),
                new QuickAction(KeyEvent.KEYCODE_BUTTON_SELECT, getString(R.string.game_menu_key_start_menu),
                        () -> sendKeys(START_MENU)).shortLabel(getString(R.string.game_menu_short_start_menu)),
                new QuickAction(KeyEvent.KEYCODE_DPAD_UP, getString(R.string.game_menu_toggle_keyboard),
                        game::toggleKeyboard).shortLabel(getString(R.string.game_menu_short_keyboard)),
                new QuickAction(KeyEvent.KEYCODE_DPAD_DOWN, getString(R.string.game_menu_toggle_full_keyboard),
                        game::toggleFullKeyboard).shortLabel(getString(R.string.game_menu_short_pc_keyboard)),
                new QuickAction(KeyEvent.KEYCODE_DPAD_LEFT, getString(R.string.game_menu_key_switch_window),
                        () -> sendKeys(SWITCH_WINDOW)).shortLabel(getString(R.string.game_menu_short_switch_window)),
                new QuickAction(KeyEvent.KEYCODE_DPAD_RIGHT, getString(R.string.game_menu_key_show_desktop),
                        () -> sendKeys(SHOW_DESKTOP)).shortLabel(getString(R.string.game_menu_short_desktop)));
    }

    @Override
    public List<QuickSlider> buildQuickSliders() {
        BrightnessSliderView brightness = game.getBrightness();
        return Arrays.asList(
                new QuickSlider(false, R.drawable.ic_menu_brightness_auto, R.drawable.ic_menu_brightness_auto,
                        getString(R.string.game_menu_brightness), getString(R.string.game_menu_brightness_auto_description),
                        1, new QuickSlider.Value() {
                            @Override
                            public int get() {
                                return brightness.getPercent();
                            }

                            @Override
                            public void set(int value) {
                                brightness.setPercent(value);
                            }
                        }, brightness::isAuto, () -> brightness.setAuto(!brightness.isAuto()))
                        .activeText(getString(R.string.game_menu_brightness_auto))
                        .soloWhileAdjusting(),
                new QuickSlider(true, R.drawable.ic_overlay_volume, R.drawable.ic_overlay_volume_mute,
                        getString(R.string.game_menu_volume), getString(R.string.game_menu_mute),
                        0, new QuickSlider.Value() {
                            @Override
                            public int get() {
                                return Math.round(audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / maxVolume());
                            }

                            @Override
                            public void set(int value) {
                                setVolume(Math.round(value * maxVolume() / 100f));
                            }
                        }, this::isMuted, this::toggleMute));
    }

    // ---- Media volume of the device ----

    private int maxVolume() {
        return Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
    }

    private void setVolume(int index) {
        if (index == audio.getStreamVolume(AudioManager.STREAM_MUSIC)) {
            return;
        }
        try {
            // No flags: the menu shows the level itself
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0);
        } catch (SecurityException e) {
            // Do Not Disturb may refuse the change
        }
    }

    private boolean isMuted() {
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0;
    }

    // Mute remembers the level, to give it back
    private void toggleMute() {
        if (isMuted()) {
            setVolume(volumeBeforeMute > 0 ? volumeBeforeMute : Math.max(1, maxVolume() / 2));
        } else {
            volumeBeforeMute = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            setVolume(0);
        }
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

        // Sharpening is done by the Vulkan renderer only
        if (game.canSharpen()) {
            items.add(Item.header(getString(R.string.game_menu_section_picture)));
            items.add(Item.toggle(R.drawable.ic_apollo_cat_video, getString(R.string.title_checkbox_sharpening),
                    game::isSharpeningOn, game::toggleSharpening));
            items.add(Item.slider(R.drawable.ic_apollo_tune, getString(R.string.game_menu_sharpening_strength),
                    10, 100, 5, "%", new GameMenuView.QuickSlider.Value() {
                        @Override
                        public int get() {
                            return game.getSharpeningStrength();
                        }

                        @Override
                        public void set(int value) {
                            game.setSharpeningStrength(value);
                        }
                    }).enabledWhen(game::isSharpeningOn));
            // The line over the stream drags by touch
            items.add(Item.toggle(R.drawable.ic_menu_window, getString(R.string.game_menu_compare_sharpening),
                    game::isComparingSharpening, game::toggleCompareSharpening).enabledWhen(game::isSharpeningOn));
        }

        // The stats over the stream: their style, or off, and where the style in use sits
        items.add(Item.header(getString(R.string.apollo_section_stats)));
        StatsPrefs.Style[] styles = StatsPrefs.Style.values();
        String[] styleNames = new String[styles.length + 1];
        styleNames[0] = getString(R.string.stats_off);
        for (int i = 0; i < styles.length; i++) {
            styleNames[i + 1] = getString(styles[i].labelRes);
        }
        items.add(Item.dropdown(R.drawable.ic_menu_perf, getString(R.string.stats_menu_style), styleNames,
                new GameMenuView.Selection() {
                    @Override
                    public int get() {
                        return game.getStatsChoice();
                    }

                    @Override
                    public void set(int index) {
                        game.setStatsChoice(index);
                    }
                }));
        StatsPrefs.Position[] positions = StatsPrefs.Position.values();
        String[] positionNames = new String[positions.length];
        for (int i = 0; i < positions.length; i++) {
            positionNames[i] = getString(positions[i].labelRes);
        }
        items.add(Item.dropdown(R.drawable.ic_menu_window, getString(R.string.stats_settings_position), positionNames,
                new GameMenuView.Selection() {
                    @Override
                    public int get() {
                        return game.getStatsPosition().ordinal();
                    }

                    @Override
                    public void set(int index) {
                        game.setStatsPosition(positions[index]);
                    }
                }));

        items.add(Item.header(getString(R.string.game_menu_section_controller)));
        items.add(Item.toggle(R.drawable.ic_overlay_gamepad, getString(R.string.game_menu_virtual_controller),
                game::isVirtualControllerVisible, game::toggleVirtualController));
        items.add(Item.toggle(R.drawable.ic_overlay_mouse, getString(R.string.game_menu_mouse_emulation),
                game::isMouseEmulationActive, game::toggleMouseEmulation));
        items.add(Item.slider(R.drawable.ic_apollo_tune, getString(R.string.title_seekbar_mouse_emulation_speed),
                10, 200, 5, "%", new GameMenuView.QuickSlider.Value() {
                    @Override
                    public int get() {
                        return game.getMouseEmulationSpeed();
                    }

                    @Override
                    public void set(int value) {
                        game.setMouseEmulationSpeed(value);
                    }
                }));
        items.add(Item.slider(R.drawable.ic_apollo_tune, getString(R.string.title_seekbar_mouse_scroll_speed),
                10, 200, 5, "%", new GameMenuView.QuickSlider.Value() {
                    @Override
                    public int get() {
                        return game.getMouseScrollSpeed();
                    }

                    @Override
                    public void set(int value) {
                        game.setMouseScrollSpeed(value);
                    }
                }));

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
                display.equals(GAME_BAR.toDisplayString()) || display.equals(START_MENU.toDisplayString()) ||
                display.equals(WINDOWS_QUICK_SETTINGS.toDisplayString());
    }

    private List<Item> buildKeyItems() {
        List<Item> items = new ArrayList<>();

        // The quick settings of Windows first, with the other keys for the PC
        items.add(Item.action(R.drawable.ic_apollo_tune, getString(R.string.game_menu_key_windows_quick_settings),
                () -> sendKeys(WINDOWS_QUICK_SETTINGS)).keepOpen().trailingText(WINDOWS_QUICK_SETTINGS.toDisplayString()));

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
