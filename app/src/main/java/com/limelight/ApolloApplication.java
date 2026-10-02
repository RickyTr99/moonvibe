package com.limelight;

import android.app.Activity;
import android.app.Application;
import android.content.res.TypedArray;
import android.os.Bundle;

import com.limelight.ui.theme.ApolloBackground;
import com.limelight.ui.theme.ApolloColors;
import com.limelight.ui.theme.SystemBars;

/**
 * Applies the app-wide window setup of MoonVibe to every screen except the stream,
 * which manages its own window.
 */
public class ApolloApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                if (activity instanceof Game) {
                    return;
                }
                // The animated background, on full screens only (not on dialogs or see-through screens)
                if (!activity.getWindow().isFloating() && !isTranslucent(activity)) {
                    activity.getWindow().setBackgroundDrawable(new ApolloBackground(ApolloColors.dark(activity)));
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
                if (!(activity instanceof Game)) {
                    SystemBars.apply(activity);
                }
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        });
    }

    private static boolean isTranslucent(Activity activity) {
        TypedArray attrs = activity.obtainStyledAttributes(new int[] {android.R.attr.windowIsTranslucent});
        boolean translucent = attrs.getBoolean(0, false);
        attrs.recycle();
        return translucent;
    }
}
