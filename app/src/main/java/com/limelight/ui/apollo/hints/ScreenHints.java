package com.limelight.ui.apollo.hints;

import android.app.Activity;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.LinearLayout;

import com.limelight.ui.apollo.ApolloUi;
import com.limelight.ui.theme.ApolloColors;

/**
 * The hint bar at the bottom of an app screen, and the input tracking that shows or hides it.
 */
public final class ScreenHints {
    private ScreenHints() {
    }

    /** Adds the bar at the bottom of the screen column, which is also the scope of its hints. */
    public static HintRow attach(Activity activity, LinearLayout column) {
        HintRow row = new HintRow(activity, ApolloColors.dark(activity), Gravity.END);
        row.setPadding(ApolloUi.dp(activity, 22), 0, ApolloUi.dp(activity, 22), 0);
        column.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ApolloUi.dp(activity, 32)));
        row.setScope(column);
        return row;
    }

    // Called by the screens from dispatchKeyEvent and dispatchTouchEvent
    public static void onKeyEvent(KeyEvent event) {
        InputMode.onKeyEvent(event);
    }

    public static void onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            InputMode.onTouch();
        }
    }
}
