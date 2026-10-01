package com.limelight;

import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Popup menu with quick actions for the ongoing stream, ported from Artemis.
 */
public class GameMenu {
    private static final long TEST_GAME_FOCUS_DELAY = 10;

    private static class MenuOption {
        private final String label;
        private final boolean withGameFocus;
        private final Runnable runnable;

        MenuOption(String label, boolean withGameFocus, Runnable runnable) {
            this.label = label;
            this.withGameFocus = withGameFocus;
            this.runnable = runnable;
        }

        MenuOption(String label, Runnable runnable) {
            this(label, false, runnable);
        }
    }

    private final Game game;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AlertDialog currentDialog;

    public GameMenu(Game game) {
        this.game = game;
    }

    private String getString(int id) {
        return game.getResources().getString(id);
    }

    // Some actions (like the soft keyboard) only work once the stream window has focus again
    // after the dialog is dismissed
    private void runWithGameFocus(Runnable runnable) {
        if (game.isFinishing()) {
            return;
        }
        if (!game.hasWindowFocus()) {
            handler.postDelayed(() -> runWithGameFocus(runnable), TEST_GAME_FOCUS_DELAY);
            return;
        }
        runnable.run();
    }

    private void run(MenuOption option) {
        if (option.runnable == null) {
            return;
        }

        if (option.withGameFocus) {
            runWithGameFocus(option.runnable);
        } else {
            option.runnable.run();
        }
    }

    private void showMenuDialog(String title, List<MenuOption> options) {
        options.add(new MenuOption(getString(R.string.game_menu_cancel), null));

        String[] labels = new String[options.size()];
        for (int i = 0; i < options.size(); i++) {
            labels[i] = options.get(i).label;
        }

        hideMenu();
        currentDialog = new AlertDialog.Builder(game)
                .setTitle(title)
                .setItems(labels, (dialog, which) -> run(options.get(which)))
                .show();
    }

    private void showSendKeysMenu() {
        List<MenuOption> options = new ArrayList<>();

        options.add(new MenuOption(getString(R.string.game_menu_send_keys_esc), true,
                () -> game.sendKeys(KeyEvent.KEYCODE_ESCAPE)));
        options.add(new MenuOption(getString(R.string.game_menu_send_keys_f11), true,
                () -> game.sendKeys(KeyEvent.KEYCODE_F11)));
        options.add(new MenuOption(getString(R.string.game_menu_send_keys_alt_f4), true,
                () -> game.sendKeys(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_F4)));
        options.add(new MenuOption(getString(R.string.game_menu_send_keys_alt_enter), true,
                () -> game.sendKeys(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ENTER)));
        options.add(new MenuOption(getString(R.string.game_menu_send_keys_alt_tab), true,
                () -> game.sendKeys(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_TAB)));
        options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_v), true,
                () -> game.sendKeys(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_V)));
        options.add(new MenuOption(getString(R.string.game_menu_send_keys_win), true,
                () -> game.sendKeys(KeyEvent.KEYCODE_META_LEFT)));
        options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_d), true,
                () -> game.sendKeys(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_D)));
        options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_g), true,
                () -> game.sendKeys(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_G)));
        options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_alt_tab), true,
                () -> game.sendKeys(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_TAB)));
        options.add(new MenuOption(getString(R.string.game_menu_send_keys_shift_tab), true,
                () -> game.sendKeys(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_TAB)));
        options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_shift_left), true,
                () -> game.sendKeys(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_DPAD_LEFT)));
        options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_alt_shift_f1), true,
                () -> game.sendKeys(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_F1)));
        options.add(new MenuOption(getString(R.string.game_menu_send_keys_ctrl_alt_shift_f12), true,
                () -> game.sendKeys(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_F12)));
        options.add(new MenuOption(getString(R.string.game_menu_send_keys_win_alt_b), true,
                () -> game.sendKeys(KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_B)));

        showMenuDialog(getString(R.string.game_menu_send_keys), options);
    }

    private void showAdvancedMenu() {
        List<MenuOption> options = new ArrayList<>();

        options.add(new MenuOption(getString(R.string.game_menu_task_manager), true,
                () -> game.sendKeys(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_ESCAPE)));
        options.add(new MenuOption(getString(R.string.game_menu_toggle_stats), true, game::toggleStatsOverlay));
        options.add(new MenuOption(getString(R.string.game_menu_toggle_virtual_controller), true, game::toggleVirtualController));
        options.add(new MenuOption(getString(R.string.game_menu_open_overlay_menu), true, game::showOverlayMenu));

        showMenuDialog(getString(R.string.game_menu_advanced), options);
    }

    public void showMenu() {
        List<MenuOption> options = new ArrayList<>();

        options.add(new MenuOption(getString(R.string.game_menu_disconnect), game::disconnectFromMenu));
        options.add(new MenuOption(getString(R.string.game_menu_quit_session), this::confirmQuitSession));
        options.add(new MenuOption(getString(R.string.game_menu_toggle_keyboard), true, game::toggleKeyboard));
        options.add(new MenuOption(getString(R.string.game_menu_toggle_full_keyboard), true, game::toggleFullKeyboard));
        options.add(new MenuOption(getString(R.string.game_menu_send_keys), this::showSendKeysMenu));
        options.add(new MenuOption(getString(R.string.game_menu_touch_mode), this::showTouchModeMenu));
        options.add(new MenuOption(getString(R.string.game_menu_advanced), this::showAdvancedMenu));

        showMenuDialog(getString(R.string.game_menu_title), options);
    }

    private void showTouchModeMenu() {
        String[] names = game.getResources().getStringArray(R.array.touch_mode_names);
        String[] values = game.getResources().getStringArray(R.array.touch_mode_values);
        int current = Arrays.asList(values).indexOf(game.getTouchMode());

        hideMenu();
        currentDialog = new AlertDialog.Builder(game)
                .setTitle(R.string.game_menu_touch_mode)
                .setSingleChoiceItems(names, current, (dialog, which) -> {
                    game.setTouchMode(values[which]);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.game_menu_cancel, null)
                .show();
    }

    private void confirmQuitSession() {
        hideMenu();
        currentDialog = new AlertDialog.Builder(game)
                .setTitle(R.string.game_menu_quit_confirm_title)
                .setMessage(R.string.game_menu_quit_confirm_message)
                .setPositiveButton(R.string.yes, (dialog, which) -> game.quitSessionFromMenu())
                .setNegativeButton(R.string.no, null)
                .show();
    }

    public void hideMenu() {
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
        }
        currentDialog = null;
    }

    public boolean isMenuOpen() {
        return currentDialog != null && currentDialog.isShowing();
    }
}
