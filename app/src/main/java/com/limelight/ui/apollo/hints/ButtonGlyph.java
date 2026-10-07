package com.limelight.ui.apollo.hints;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
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
 * The symbol of a gamepad button, Xbox style: colored A/B/X/Y, bumpers for LB/RB, the View and Menu
 * icons for Select and Start, arrows for the D-pad.
 */
public final class ButtonGlyph {
    // Left and right on the D-pad, as one symbol
    public static final int DPAD_LEFT_RIGHT = -2;
    // Up and down on the D-pad
    public static final int DPAD_UP_DOWN = -3;

    private ButtonGlyph() {
    }

    public static View create(Context context, ApolloColors colors, int key) {
        int background = colors.surfaceContainerHighest;
        int foreground = colors.onSurfaceVariant;
        String letter = null;
        switch (key) {
            case KeyEvent.KEYCODE_BUTTON_A: letter = "A"; background = 0xFF5DBB3C; foreground = 0xFF0B1A05; break;
            case KeyEvent.KEYCODE_BUTTON_B: letter = "B"; background = 0xFFE0453A; foreground = Color.WHITE; break;
            case KeyEvent.KEYCODE_BUTTON_X: letter = "X"; background = 0xFF3B7BE0; foreground = Color.WHITE; break;
            case KeyEvent.KEYCODE_BUTTON_Y: letter = "Y"; background = 0xFFF0B428; foreground = 0xFF231A00; break;
        }

        if (letter != null) {
            TextView circle = ApolloUi.text(context, letter, 10, foreground, true);
            circle.setGravity(Gravity.CENTER);
            circle.setIncludeFontPadding(false);
            circle.setBackground(ApolloUi.roundRect(background, ApolloUi.dp(context, 9)));
            circle.setLayoutParams(new FrameLayout.LayoutParams(ApolloUi.dp(context, 18), ApolloUi.dp(context, 18)));
            return circle;
        }

        if (key == KeyEvent.KEYCODE_BUTTON_L1 || key == KeyEvent.KEYCODE_BUTTON_R1) {
            TextView bumper = ApolloUi.text(context, key == KeyEvent.KEYCODE_BUTTON_L1 ? "LB" : "RB", 8.5f, foreground, true);
            bumper.setGravity(Gravity.CENTER);
            bumper.setIncludeFontPadding(false);
            bumper.setPadding(0, ApolloUi.dp(context, 1), 0, 0);
            bumper.setBackground(new BumperDrawable(background, key == KeyEvent.KEYCODE_BUTTON_L1,
                    context.getResources().getDisplayMetrics().density));
            // Lower than the round buttons, centered on them
            bumper.setLayoutParams(new FrameLayout.LayoutParams(ApolloUi.dp(context, 22), ApolloUi.dp(context, 16), Gravity.CENTER));
            return bumper;
        }

        if (key == KeyEvent.KEYCODE_BUTTON_SELECT || key == KeyEvent.KEYCODE_BUTTON_START ||
                key == KeyEvent.KEYCODE_BUTTON_MODE || key == KeyEvent.KEYCODE_BACK) {
            ImageView icon = new ImageView(context);
            icon.setImageResource(key == KeyEvent.KEYCODE_BUTTON_SELECT ? R.drawable.ic_apollo_btn_view :
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
