package com.limelight.ui.apollo.hints;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.ui.apollo.ApolloUi;
import com.limelight.ui.apollo.BumperDrawable;
import com.limelight.ui.theme.ApolloColors;

/**
 * The symbol of a gamepad button, like the ones of the controller in use. Xbox: dark A/B/X/Y with colored
 * letters, bumpers for LB/RB, the View and Menu icons for Select and Start. PlayStation: dark buttons with
 * the colored cross, circle, square and triangle, L1/R1, Create and Options. Arrows for the D-pad, a ring
 * with L or R for the sticks.
 */
public final class ButtonGlyph {
    // Left and right on the D-pad, as one symbol
    public static final int DPAD_LEFT_RIGHT = -2;
    // Up and down on the D-pad
    public static final int DPAD_UP_DOWN = -3;

    private ButtonGlyph() {
    }

    /**
     * The color of the letter or symbol of a face button, as on the controller in use (dark button,
     * colored mark); 0 for the other buttons.
     */
    public static int letterColor(int key) {
        boolean playStation = InputMode.isPlayStation();
        switch (key) {
            case KeyEvent.KEYCODE_BUTTON_A: return playStation ? 0xFF7FB0EE : 0xFF6CC24A;
            case KeyEvent.KEYCODE_BUTTON_B: return playStation ? 0xFFF26B6B : 0xFFF0483E;
            case KeyEvent.KEYCODE_BUTTON_X: return playStation ? 0xFFE58DC8 : 0xFF3D8EF0;
            case KeyEvent.KEYCODE_BUTTON_Y: return playStation ? 0xFF3FCFA6 : 0xFFFFC52E;
            default: return 0;
        }
    }

    /** The name of a face button as text, like in a toast: the letter, or the PlayStation symbol. */
    public static String faceLabel(int key) {
        boolean playStation = InputMode.isPlayStation();
        switch (key) {
            case KeyEvent.KEYCODE_BUTTON_A: return playStation ? "✕" : "A";
            case KeyEvent.KEYCODE_BUTTON_B: return playStation ? "○" : "B";
            case KeyEvent.KEYCODE_BUTTON_X: return playStation ? "□" : "X";
            case KeyEvent.KEYCODE_BUTTON_Y: return playStation ? "△" : "Y";
            default: return null;
        }
    }

    /** The name of a shoulder button: LB/RB, or L1/R1 with a PlayStation controller. */
    public static String bumperLabel(boolean left) {
        if (InputMode.isPlayStation()) {
            return left ? "L1" : "R1";
        }
        return left ? "LB" : "RB";
    }

    /** The drawn symbol of a PlayStation face button, white to be tinted; 0 with an Xbox controller. */
    public static int symbolResource(int key) {
        if (!InputMode.isPlayStation()) {
            return 0;
        }
        switch (key) {
            case KeyEvent.KEYCODE_BUTTON_A: return R.drawable.ic_ps_cross;
            case KeyEvent.KEYCODE_BUTTON_B: return R.drawable.ic_ps_circle;
            case KeyEvent.KEYCODE_BUTTON_X: return R.drawable.ic_ps_square;
            case KeyEvent.KEYCODE_BUTTON_Y: return R.drawable.ic_ps_triangle;
            default: return 0;
        }
    }

    public static View create(Context context, ApolloColors colors, int key) {
        int background = colors.surfaceContainerHighest;
        int foreground = colors.onSurfaceVariant;
        boolean playStation = InputMode.isPlayStation();

        int symbol = symbolResource(key);
        if (symbol != 0) {
            ImageView circle = new ImageView(context);
            circle.setImageResource(symbol);
            circle.setImageTintList(ColorStateList.valueOf(letterColor(key)));
            circle.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int pad = ApolloUi.dp(context, 4);
            circle.setPadding(pad, pad, pad, pad);
            circle.setBackground(ApolloUi.roundRect(background, ApolloUi.dp(context, 9)));
            circle.setLayoutParams(new FrameLayout.LayoutParams(ApolloUi.dp(context, 18), ApolloUi.dp(context, 18)));
            return circle;
        }

        String letter = faceLabel(key);
        if (letter != null) {
            TextView circle = ApolloUi.text(context, letter, 11, letterColor(key), true);
            circle.setGravity(Gravity.CENTER);
            circle.setIncludeFontPadding(false);
            circle.setBackground(ApolloUi.roundRect(background, ApolloUi.dp(context, 9)));
            circle.setLayoutParams(new FrameLayout.LayoutParams(ApolloUi.dp(context, 18), ApolloUi.dp(context, 18)));
            return circle;
        }

        if (key == KeyEvent.KEYCODE_BUTTON_L1 || key == KeyEvent.KEYCODE_BUTTON_R1) {
            boolean left = key == KeyEvent.KEYCODE_BUTTON_L1;
            TextView bumper = ApolloUi.text(context, bumperLabel(left), 8.5f, foreground, true);
            bumper.setGravity(Gravity.CENTER);
            bumper.setIncludeFontPadding(false);
            bumper.setPadding(0, ApolloUi.dp(context, 1), 0, 0);
            bumper.setBackground(new BumperDrawable(background, left,
                    context.getResources().getDisplayMetrics().density));
            // Lower than the round buttons, centered on them
            bumper.setLayoutParams(new FrameLayout.LayoutParams(ApolloUi.dp(context, 22), ApolloUi.dp(context, 16), Gravity.CENTER));
            return bumper;
        }

        if (key == KeyEvent.KEYCODE_BUTTON_SELECT || key == KeyEvent.KEYCODE_BUTTON_START ||
                key == KeyEvent.KEYCODE_BUTTON_MODE || key == KeyEvent.KEYCODE_BACK) {
            ImageView icon = new ImageView(context);
            icon.setImageResource(key == KeyEvent.KEYCODE_BUTTON_SELECT ? (playStation ? R.drawable.ic_ps_create : R.drawable.ic_apollo_btn_view) :
                    key == KeyEvent.KEYCODE_BUTTON_MODE ? R.drawable.ic_overlay_guide :
                    key == KeyEvent.KEYCODE_BACK ? R.drawable.ic_apollo_arrow_left : R.drawable.ic_apollo_cat_menu);
            icon.setImageTintList(ColorStateList.valueOf(foreground));
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int pad = ApolloUi.dp(context, 2);
            icon.setPadding(pad, pad, pad, pad);
            icon.setBackground(ApolloUi.roundRect(background, ApolloUi.dp(context, 9)));
            icon.setLayoutParams(new FrameLayout.LayoutParams(ApolloUi.dp(context, 18), ApolloUi.dp(context, 18)));
            return icon;
        }

        // The sticks, as a ring with their side inside
        if (key == KeyEvent.KEYCODE_BUTTON_THUMBL || key == KeyEvent.KEYCODE_BUTTON_THUMBR) {
            TextView stick = ApolloUi.text(context, key == KeyEvent.KEYCODE_BUTTON_THUMBL ? "L" : "R", 9, foreground, true);
            stick.setGravity(Gravity.CENTER);
            stick.setIncludeFontPadding(false);
            GradientDrawable ring = ApolloUi.roundRect(background, ApolloUi.dp(context, 9));
            ring.setStroke(ApolloUi.dp(context, 1.5f), colors.outline);
            stick.setBackground(ring);
            stick.setLayoutParams(new FrameLayout.LayoutParams(ApolloUi.dp(context, 18), ApolloUi.dp(context, 18)));
            return stick;
        }

        String arrows;
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_UP: arrows = "▲"; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: arrows = "▼"; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: arrows = "◀"; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: arrows = "▶"; break;
            case DPAD_UP_DOWN: arrows = "▲ ▼"; break;
            // The extra buttons of AYN handhelds
            case KeyEvent.KEYCODE_BUTTON_C: arrows = "M1"; break;
            case KeyEvent.KEYCODE_BUTTON_Z: arrows = "M2"; break;
            default: arrows = "◀ ▶"; break;
        }
        TextView dpad = ApolloUi.text(context, arrows, 9, foreground, true);
        dpad.setGravity(Gravity.CENTER);
        dpad.setIncludeFontPadding(false);
        dpad.setPadding(ApolloUi.dp(context, 5), 0, ApolloUi.dp(context, 5), 0);
        dpad.setMinWidth(ApolloUi.dp(context, 18));
        dpad.setBackground(ApolloUi.roundRect(background, ApolloUi.dp(context, 5)));
        dpad.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, ApolloUi.dp(context, 18)));
        return dpad;
    }
}
