package com.limelight;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.ImageView;

import com.limelight.ui.apollo.UiSounds;
import com.limelight.ui.theme.ApolloBackground;
import com.limelight.ui.theme.ApolloColors;
import com.limelight.ui.theme.SystemBars;
import com.limelight.ui.theme.ApolloMotion;
import com.limelight.ui.theme.ThemeColors;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Applies the app-wide window setup of MoonVibe to every screen except the stream,
 * which manages its own window.
 */
public class ApolloApplication extends Application {
    // The theme color and background each open screen was made with (ThemeColors.appearance)
    private final Map<Activity, String> appearances = new WeakHashMap<>();
    private Activity resumed;
    // Screens started (visible) and streams open, to change the launcher icon only when nothing is in use
    private int started;
    private int streams;
    private boolean iconPending;
    // Menus and streams on screen, for the music: it plays under the menus only
    private int menusStarted;
    private int streamsStarted;
    private final Handler handler = new Handler(Looper.getMainLooper());
    // The look of a screen just before it was made again with the new color, faded out over the new one
    private Bitmap lookBefore;
    private Class<?> lookBeforeOf;
    // A screen made again, or one screen opening another, stops and starts screens in a row: wait a moment
    private final Runnable musicCheck = () -> UiSounds.setScreens(menusStarted > 0, streamsStarted > 0);

    // Kept here: the preferences only hold a weak reference to their listeners
    private final SharedPreferences.OnSharedPreferenceChangeListener lookListener = (prefs, key) -> {
        // Changing the launcher entry closes the task it was opened from: the icon changes once the app is left
        if (ThemeColors.PREF_COLOR.equals(key) || ThemeColors.PREF_TINT_ICON.equals(key)) {
            iconPending = true;
        }
        // The screen in use takes the new look right away with a fade, the ones under it are made again too,
        // so going back to them never shows the old look first
        if ((ThemeColors.PREF_COLOR.equals(key) || ThemeColors.PREF_BACKGROUND.equals(key)) && resumed != null) {
            // After the code that saved the setting is done, and after a menu that picked it has faded out
            // (otherwise the look kept on top would hold it half gone)
            Activity activity = resumed;
            activity.getWindow().getDecorView().postDelayed(() -> {
                for (Activity other : new ArrayList<>(appearances.keySet())) {
                    if (other != activity) {
                        recreateIfChanged(other);
                    }
                }
                recreateIfChanged(activity);
            }, ApolloMotion.SHORT + 50);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(lookListener);
        // In case the app was closed before a new icon was shown
        iconPending = true;
        UiSounds.init(this);

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityPreCreated(Activity activity, Bundle savedInstanceState) {
                // Before the screen makes its views (Android 10+; below, onActivityCreated does it)
                applyColor(activity);
            }

            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                if (activity instanceof Game) {
                    streams++;
                    if (savedInstanceState == null) {
                        UiSounds.play(UiSounds.Sound.LAUNCH);
                    }
                    return;
                }
                UiSounds.attach(activity);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    applyColor(activity);
                }
                appearances.put(activity, ThemeColors.appearance(activity));
                fadeLookBefore(activity);
                // The animated background, on full screens only (not on dialogs or see-through screens)
                if (!activity.getWindow().isFloating() && !isTranslucent(activity)) {
                    activity.getWindow().setBackgroundDrawable(new ApolloBackground(ApolloColors.dark(activity),
                            ThemeColors.background(activity)));
                }
                // Dialogs and the keyboard can bring the system bars back, hide them again
                // whenever the screen gets the focus back
                activity.getWindow().getDecorView().getViewTreeObserver().addOnWindowFocusChangeListener(hasFocus -> {
                    if (hasFocus) {
                        SystemBars.apply(activity);
                    }
                });
            }

            @Override
            public void onActivityResumed(Activity activity) {
                resumed = activity;
                if (!(activity instanceof Game)) {
                    SystemBars.apply(activity);
                    recreateIfChanged(activity);
                }
            }

            @Override
            public void onActivityStarted(Activity activity) {
                started++;
                if (activity instanceof Game) {
                    streamsStarted++;
                } else {
                    menusStarted++;
                }
                updateMusic();
            }

            @Override
            public void onActivityPaused(Activity activity) {
                if (resumed == activity) {
                    resumed = null;
                }
            }

            @Override
            public void onActivityStopped(Activity activity) {
                started--;
                if (activity instanceof Game) {
                    streamsStarted--;
                } else {
                    menusStarted--;
                }
                updateMusic();
                // Not while a screen is made again (the new one starts right after)
                if (!activity.isChangingConfigurations()) {
                    applyPendingIcon();
                }
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                appearances.remove(activity);
                if (activity instanceof Game) {
                    streams--;
                    applyPendingIcon();
                }
            }
        });
    }

    private void updateMusic() {
        handler.removeCallbacks(musicCheck);
        // Starting it, or a stream silencing it, right away; stopping it waits, a screen made again starts right after
        boolean stops = menusStarted <= 0 && streamsStarted <= 0;
        handler.postDelayed(musicCheck, stops ? 400 : 0);
    }

    // Left in the background with no stream open (a stream in the background or in PiP is in the same task)
    private void applyPendingIcon() {
        if (iconPending && started <= 0 && streams <= 0) {
            iconPending = false;
            ThemeColors.updateLauncherIcon(this);
        }
    }

    // The stream keeps its own theme; its game menu reads the color by itself
    private static void applyColor(Activity activity) {
        if (!(activity instanceof Game)) {
            ThemeColors.applyTo(activity);
        }
    }

    // A screen made with another theme color or background is made again
    private void recreateIfChanged(Activity activity) {
        String made = appearances.get(activity);
        if (made != null && !made.equals(ThemeColors.appearance(activity)) && !activity.isFinishing()) {
            appearances.remove(activity);
            if (activity == resumed) {
                recreateWithFade(activity);
            } else {
                activity.recreate();
            }
        }
    }

    // The screen in use keeps its old look on top while the new one is made, then it fades out
    private void recreateWithFade(Activity activity) {
        View decor = activity.getWindow().getDecorView();
        Bitmap shot = null;
        if (decor.getWidth() > 0 && decor.getHeight() > 0) {
            try {
                shot = Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888);
            } catch (OutOfMemoryError e) {
                // No fade then
            }
        }
        if (shot == null) {
            activity.recreate();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Bitmap copy = shot;
            try {
                PixelCopy.request(activity.getWindow(), copy, result -> {
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        copy.recycle();
                        return;
                    }
                    keepLookBefore(activity, result == PixelCopy.SUCCESS ? copy : null);
                    activity.recreate();
                }, handler);
                return;
            } catch (IllegalArgumentException e) {
                // No surface yet: drawn by hand below
            }
        }
        try {
            decor.draw(new Canvas(shot));
            keepLookBefore(activity, shot);
        } catch (RuntimeException e) {
            // Hardware bitmaps can't be drawn on a software canvas: no fade then
            keepLookBefore(activity, null);
        }
        activity.recreate();
    }

    private void keepLookBefore(Activity activity, Bitmap shot) {
        if (lookBefore != null && lookBefore != shot) {
            lookBefore.recycle();
        }
        lookBefore = shot;
        lookBeforeOf = shot != null ? activity.getClass() : null;
    }

    private void fadeLookBefore(Activity activity) {
        Bitmap shot = lookBefore;
        if (shot == null || lookBeforeOf != activity.getClass()) {
            return;
        }
        lookBefore = null;
        lookBeforeOf = null;
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        ImageView cover = new ImageView(activity);
        cover.setImageBitmap(shot);
        cover.setScaleType(ImageView.ScaleType.MATRIX);
        cover.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        decor.addView(cover, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        // Once the new screen has drawn its first frames under it; touches already reach it
        cover.animate().alpha(0).setStartDelay(120).setDuration(550)
                .setInterpolator(new PathInterpolator(0.4f, 0f, 0.2f, 1f))
                .withEndAction(() -> {
                    decor.removeView(cover);
                    cover.setImageDrawable(null);
                    shot.recycle();
                })
                .start();
    }

    private static boolean isTranslucent(Activity activity) {
        TypedArray attrs = activity.obtainStyledAttributes(new int[] {android.R.attr.windowIsTranslucent});
        boolean translucent = attrs.getBoolean(0, false);
        attrs.recycle();
        return translucent;
    }
}
