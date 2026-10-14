package com.limelight.ui.apollo.hints;

import android.os.Build;
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

    private static final int SONY_VENDOR_ID = 0x054C;

    private static boolean gamepad;
    private static int lastGamepadId = -1;
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
        noteDevice(event.getDevice());
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() != KeyEvent.KEYCODE_BACK) {
            set(true);
        }
        // The back key of a gamepad (B) counts too, the one of the navigation bar does not
        else if (event.getAction() == KeyEvent.ACTION_DOWN && (event.getSource() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) {
            set(true);
        }
    }

    /**
     * Remembers the gamepad that pressed a key, whose brand picks the button symbols. A change of brand
     * reaches the listeners, so the hints shown are drawn again.
     */
    public static void noteDevice(InputDevice device) {
        if (device == null || device.isVirtual() || !isGamepadDevice(device) || device.getId() == lastGamepadId) {
            return;
        }
        boolean wasPlayStation = isPlayStation();
        lastGamepadId = device.getId();
        if (wasPlayStation != isPlayStation()) {
            for (Listener listener : new ArrayList<>(listeners)) {
                listener.onInputModeChanged(gamepad);
            }
        }
    }

    /**
     * The buttons are drawn as PlayStation ones (cross, circle, square, triangle): the last gamepad used
     * is a Sony one or, before any was used, the first external gamepad connected is.
     */
    public static boolean isPlayStation() {
        InputDevice last = lastGamepadId != -1 ? InputDevice.getDevice(lastGamepadId) : null;
        if (last != null) {
            return last.getVendorId() == SONY_VENDOR_ID;
        }
        InputDevice fallback = null;
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null || device.isVirtual() || !isGamepadDevice(device)) {
                continue;
            }
            // The controls built into a handheld come after an external controller
            if (fallback == null || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && device.isExternal())) {
                fallback = device;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && device.isExternal()) {
                    break;
                }
            }
        }
        return fallback != null && fallback.getVendorId() == SONY_VENDOR_ID;
    }

    private static boolean isGamepadDevice(InputDevice device) {
        int sources = device.getSources();
        return (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
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
