package com.limelight.ui.theme;

import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

/**
 * Material 3 motion: easing curves and durations for the Apollo X UI.
 */
public final class ApolloMotion {
    private ApolloMotion() {
    }

    // Elements entering the screen
    public static final Interpolator EMPHASIZED_DECELERATE = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    // Elements leaving the screen
    public static final Interpolator EMPHASIZED_ACCELERATE = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);
    // Elements changing in place
    public static final Interpolator STANDARD = new PathInterpolator(0.2f, 0f, 0f, 1f);

    public static final long SHORT = 150;
    public static final long MEDIUM = 250;
    public static final long LONG = 400;
}
