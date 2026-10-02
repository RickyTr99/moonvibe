package com.limelight.ui.apollo.hints;

import android.view.InputDevice;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether the app is being used with a gamepad (or other keys) or by touch, to show the button hints
 * only while they are useful. The app screens feed it their key and touch events.
 */
public final class InputMode {
    public interface Listener {
        void onInputModeChanged(boolean gamepad);
    }

    private static boolean gamepad;
    private static final List<Listener> listeners = new ArrayList<>();

    private InputMode() {
    }

    public static boolean isGamepad() {
        return gamepad;
    }

    public static void addListener(Listener listener) {
        listeners.add(listener);
    }

    public static void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private static void set(boolean value) {
        if (gamepad != value) {
            gamepad = value;
            for (Listener listener : new ArrayList<>(listeners)) {
                listener.onInputModeChanged(value);
            }
        }
    }

    public static void onKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() != KeyEvent.KEYCODE_BACK) {
            set(true);
        }
        // The back key of a gamepad (B) counts too, the one of the navigation bar does not
        else if (event.getAction() == KeyEvent.ACTION_DOWN && (event.getSource() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) {
            set(true);
        }
    }

    public static void onTouch() {
        set(false);
    }

    public static void onTouchModeChanged(boolean inTouchMode) {
        set(!inTouchMode);
    }

    /**
     * A physical gamepad is connected (built in, like on handhelds, or external).
     * Without one the button symbols mean nothing, so icons stand in for them.
     */
    public static boolean isGamepadConnected() {
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null || device.isVirtual()) {
                continue;
            }
            int sources = device.getSources();
            if ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
                return true;
            }
        }
        return false;
    }
}
