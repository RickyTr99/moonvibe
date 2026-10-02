package com.limelight.ui.theme;

import android.app.Activity;
import android.content.Context;
import android.preference.PreferenceManager;
import android.view.Window;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Full screen for the app screens, following the "Schermo intero" setting: the status and
 * navigation bars are hidden and come back for a moment with a swipe from the edge.
 */
public final class SystemBars {
    public static final String PREF_FULLSCREEN = "checkbox_fullscreen_ui";
    public static final boolean DEFAULT_FULLSCREEN = true;

    private SystemBars() {
    }

    public static boolean isFullscreen(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_FULLSCREEN, DEFAULT_FULLSCREEN);
    }

    public static void apply(Activity activity) {
        Window window = activity.getWindow();
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (isFullscreen(activity)) {
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars());
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars());
        }
    }
}
