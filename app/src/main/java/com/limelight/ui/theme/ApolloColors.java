package com.limelight.ui.theme;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;

/**
 * Material 3 dark color roles for the Apollo X UI. On Android 12+ they come from the system's
 * dynamic palette (Material You, derived from the wallpaper), otherwise from a fixed blue palette.
 */
public final class ApolloColors {
    public final int primary;
    public final int onPrimary;
    public final int secondaryContainer;
    public final int onSecondaryContainer;
    public final int surfaceContainerLow;
    public final int surfaceContainer;
    public final int surfaceContainerHigh;
    public final int surfaceContainerHighest;
    public final int onSurface;
    public final int onSurfaceVariant;
    public final int outline;
    public final int outlineVariant;
    public final int error;

    private static final ApolloColors FALLBACK = new ApolloColors(
            0xFFADC6FF, 0xFF102F60, 0xFF3E4759, 0xFFDAE2F9,
            0xFF1A1B20, 0xFF1E1F25, 0xFF282A2F, 0xFF33353A,
            0xFFE2E2E9, 0xFFC4C6D0, 0xFF8E9099, 0xFF44474F,
            0xFFFFB4AB);

    private ApolloColors(int primary, int onPrimary, int secondaryContainer, int onSecondaryContainer,
                         int surfaceContainerLow, int surfaceContainer, int surfaceContainerHigh,
                         int surfaceContainerHighest, int onSurface, int onSurfaceVariant,
                         int outline, int outlineVariant, int error) {
        this.primary = primary;
        this.onPrimary = onPrimary;
        this.secondaryContainer = secondaryContainer;
        this.onSecondaryContainer = onSecondaryContainer;
        this.surfaceContainerLow = surfaceContainerLow;
        this.surfaceContainer = surfaceContainer;
        this.surfaceContainerHigh = surfaceContainerHigh;
        this.surfaceContainerHighest = surfaceContainerHighest;
        this.onSurface = onSurface;
        this.onSurfaceVariant = onSurfaceVariant;
        this.outline = outline;
        this.outlineVariant = outlineVariant;
        this.error = error;
    }

    public static ApolloColors dark(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return fromSystemPalette(context.getResources());
        }
        return FALLBACK;
    }

    // The system palettes have tones 0, 10, 20 ... 100 (system_*_900 is tone 10, _800 tone 20 and so on).
    // The surface container tones in between are blended from the two closest ones.
    @TargetApi(Build.VERSION_CODES.S)
    private static ApolloColors fromSystemPalette(Resources res) {
        int neutralTone10 = res.getColor(android.R.color.system_neutral1_900, null);
        int neutralTone20 = res.getColor(android.R.color.system_neutral1_800, null);
        int neutralTone30 = res.getColor(android.R.color.system_neutral1_700, null);

        return new ApolloColors(
                res.getColor(android.R.color.system_accent1_200, null),
                res.getColor(android.R.color.system_accent1_800, null),
                res.getColor(android.R.color.system_accent2_700, null),
                res.getColor(android.R.color.system_accent2_100, null),
                neutralTone10,
                blend(neutralTone10, neutralTone20, 0.2f),
                blend(neutralTone10, neutralTone20, 0.7f),
                blend(neutralTone20, neutralTone30, 0.2f),
                res.getColor(android.R.color.system_neutral1_100, null),
                res.getColor(android.R.color.system_neutral2_200, null),
                res.getColor(android.R.color.system_neutral2_400, null),
                res.getColor(android.R.color.system_neutral2_700, null),
                FALLBACK.error);
    }

    private static int blend(int from, int to, float amount) {
        int a = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * amount);
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * amount);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * amount);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
