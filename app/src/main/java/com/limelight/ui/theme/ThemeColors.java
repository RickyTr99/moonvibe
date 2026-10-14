package com.limelight.ui.theme;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.preference.PreferenceManager;

import com.limelight.R;

/**
 * The look chosen in Settings > App: the theme color (the wallpaper colors on Android 12+, or one of the
 * fixed palettes of themes_apollo_colors.xml), the animated background and the color of the launcher icon.
 */
public final class ThemeColors {
    public static final String PREF_COLOR = "list_theme_color";
    public static final String PREF_BACKGROUND = "list_app_background";
    public static final String PREF_TINT_ICON = "checkbox_tint_app_icon";

    public static final String DYNAMIC = "dynamic";
    public static final String BACKGROUND_SPACE = "space";
    public static final String BACKGROUND_SYMBOLS = "symbols";
    public static final String BACKGROUND_AURORA = "aurora";
    public static final String BACKGROUND_GLOW = "glow";

    // The fixed colors, in the order of theme_color_values; the launcher has one entry for each
    private static final String[] FIXED = {"blue", "purple", "pink", "red", "orange", "yellow", "green", "teal", "gray"};

    private ThemeColors() {
    }

    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    /** The chosen color: DYNAMIC or one of the fixed ones, always one this device offers. */
    public static String color(Context context) {
        String value = prefs(context).getString(PREF_COLOR, null);
        if (DYNAMIC.equals(value) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return DYNAMIC;
        }
        if (overlay(value) != 0) {
            return value;
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? DYNAMIC : FIXED[0];
    }

    public static String background(Context context) {
        String value = prefs(context).getString(PREF_BACKGROUND, BACKGROUND_AURORA);
        switch (value) {
            case BACKGROUND_SPACE:
            case BACKGROUND_SYMBOLS:
            case BACKGROUND_GLOW:
                return value;
            default:
                return BACKGROUND_AURORA;
        }
    }

    /** What the screens are drawn with: when it changes, the open screens are built again. */
    public static String appearance(Context context) {
        return color(context) + "/" + background(context);
    }

    /** The theme overlay of a fixed color, 0 for the wallpaper colors. */
    public static int overlay(String color) {
        if (color == null) {
            return 0;
        }
        switch (color) {
            case "blue": return R.style.ThemeOverlay_Apollo_Blue;
            case "purple": return R.style.ThemeOverlay_Apollo_Purple;
            case "pink": return R.style.ThemeOverlay_Apollo_Pink;
            case "red": return R.style.ThemeOverlay_Apollo_Red;
            case "orange": return R.style.ThemeOverlay_Apollo_Orange;
            case "yellow": return R.style.ThemeOverlay_Apollo_Yellow;
            case "green": return R.style.ThemeOverlay_Apollo_Green;
            case "teal": return R.style.ThemeOverlay_Apollo_Teal;
            case "gray": return R.style.ThemeOverlay_Apollo_Gray;
            default: return 0;
        }
    }

    /** Puts the chosen color over the theme of a screen, before its views are made. */
    public static void applyTo(Activity activity) {
        int overlay = overlay(color(activity));
        if (overlay != 0) {
            activity.getTheme().applyStyle(overlay, true);
        }
    }

    /**
     * Shows the launcher entry with the icon of the chosen color (the blue one when the icon is not tinted,
     * or with the wallpaper colors), and hides the others. Nothing is done when the right one already shows.
     */
    public static void updateLauncherIcon(Context context) {
        String wanted = "blue";
        String color = color(context);
        if (prefs(context).getBoolean(PREF_TINT_ICON, false) && !DYNAMIC.equals(color)) {
            wanted = color;
        }
        PackageManager pm = context.getPackageManager();
        // The one to show first, so the app is never left without an entry
        for (int pass = 0; pass < 2; pass++) {
            for (String fixed : FIXED) {
                if (fixed.equals(wanted) != (pass == 0)) {
                    continue;
                }
                ComponentName alias = new ComponentName(context, launcherAlias(fixed));
                int state = fixed.equals(wanted) ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                boolean enabledNow = isEnabled(pm, alias, fixed.equals("blue"));
                if (enabledNow != (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED)) {
                    pm.setComponentEnabledSetting(alias, state, PackageManager.DONT_KILL_APP);
                }
            }
        }
    }

    // The manifest enables only the blue entry
    private static boolean isEnabled(PackageManager pm, ComponentName component, boolean enabledInManifest) {
        int state = pm.getComponentEnabledSetting(component);
        if (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
            return enabledInManifest;
        }
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    private static String launcherAlias(String color) {
        return "com.limelight.Launcher" + (color.equals("blue") ? "" : Character.toUpperCase(color.charAt(0)) + color.substring(1));
    }
}
