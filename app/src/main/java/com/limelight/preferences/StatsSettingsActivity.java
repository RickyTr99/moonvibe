package com.limelight.preferences;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.LinearLayout;

import com.limelight.R;
import com.limelight.ui.apollo.ApolloUi;
import com.limelight.ui.apollo.hints.HintRow;
import com.limelight.ui.apollo.hints.ScreenHints;
import com.limelight.ui.apollo.settings.StatsSettingsView;
import com.limelight.ui.theme.ApolloColors;
import com.limelight.ui.theme.ApolloMotion;
import com.limelight.utils.UiHelper;

/** MoonVibe: the page of the stats overlay, opened from the Codec settings. */
public class StatsSettingsActivity extends Activity {
    private StatsSettingsView settingsView;
    private LinearLayout column;
    private ApolloColors colors;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiHelper.setLocale(this);
        overridePendingTransition(R.anim.apollo_fade_in, R.anim.apollo_fade_out);

        colors = ApolloColors.dark(this);
        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        settingsView = new StatsSettingsView(this, colors, this::rebuild);
        column.addView(settingsView, 0, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(column);

        HintRow hintRow = ScreenHints.attach(this, column);
        hintRow.setFallback(HintRow.hint(this, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_back));
        ApolloUi.padForCutout(column);

        UiHelper.notifyNewRootView(this);
    }

    // The defaults are back: a new page in place of the old one, faded in
    private void rebuild() {
        column.removeView(settingsView);
        settingsView = new StatsSettingsView(this, colors, this::rebuild);
        column.addView(settingsView, 0, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        settingsView.setAlpha(0);
        settingsView.animate().alpha(1).setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD).start();
        settingsView.post(settingsView::focusFirstRow);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getRepeatCount() == 0) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_BUTTON_L1:
                    settingsView.switchStyle(-1);
                    return true;
                case KeyEvent.KEYCODE_BUTTON_R1:
                    settingsView.switchStyle(1);
                    return true;
                case KeyEvent.KEYCODE_BUTTON_B:
                    if (!settingsView.onButtonB()) {
                        finish();
                    }
                    return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        ScreenHints.onKeyEvent(event);
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        ScreenHints.onTouchEvent(event);
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (!settingsView.onButtonB()) {
            super.onBackPressed();
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.apollo_fade_in, R.anim.apollo_fade_out);
    }
}
