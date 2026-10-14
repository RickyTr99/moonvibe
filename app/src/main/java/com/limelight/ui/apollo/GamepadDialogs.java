package com.limelight.ui.apollo;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import com.limelight.ui.apollo.hints.ButtonGlyph;
import com.limelight.ui.apollo.hints.InputMode;
import com.limelight.ui.theme.ApolloColors;

/**
 * Confirmations straight from the gamepad: A presses the confirm button and B the cancel one, wherever the
 * focus is, and with a gamepad connected each of the two buttons shows its button symbol before its text.
 */
public final class GamepadDialogs {
    private GamepadDialogs() {
    }

    /** For a framework dialog, once shown (its buttons exist only then). The middle button goes on Y. */
    public static AlertDialog attach(AlertDialog dialog) {
        bind(dialog, shown(dialog.getButton(AlertDialog.BUTTON_POSITIVE)), shown(dialog.getButton(AlertDialog.BUTTON_NEGATIVE)),
                shown(dialog.getButton(AlertDialog.BUTTON_NEUTRAL)));
        return dialog;
    }

    private static View shown(View button) {
        return button != null && button.getVisibility() == View.VISIBLE ? button : null;
    }

    /**
     * @param confirm the button A presses; null leaves A to the focused view (a dialog with a list)
     * @param cancel the button B presses; null makes B close the dialog
     */
    public static void bind(Dialog dialog, View confirm, View cancel) {
        bind(dialog, confirm, cancel, null);
    }

    /** @param other a third button, pressed by Y (Help in the error dialogs); may be null */
    public static void bind(Dialog dialog, View confirm, View cancel, View other) {
        boolean gamepad = InputMode.isGamepadConnected();
        View[] buttons = {confirm, cancel, other};
        int[] keys = {KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_Y};
        for (int i = 0; i < buttons.length; i++) {
            if (buttons[i] instanceof TextView) {
                addGlyph((TextView) buttons[i], keys[i]);
            }
            // Its own gamepad button presses it: the focus on it would only repeat that
            if (buttons[i] != null && gamepad) {
                buttons[i].clearFocus();
                buttons[i].setFocusable(false);
            }
        }
        dialog.setOnKeyListener((d, keyCode, event) -> {
            boolean a = keyCode == KeyEvent.KEYCODE_BUTTON_A && confirm != null;
            boolean b = keyCode == KeyEvent.KEYCODE_BUTTON_B;
            boolean y = keyCode == KeyEvent.KEYCODE_BUTTON_Y && other != null;
            if (!a && !b && !y) {
                return false;
            }
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                if (a) {
                    confirm.performClick();
                } else if (y) {
                    other.performClick();
                } else if (cancel != null) {
                    cancel.performClick();
                } else {
                    d.cancel();
                }
            }
            return true;
        });
    }

    // The symbol of the button, drawn as the app draws it in the hints
    private static void addGlyph(TextView button, int key) {
        if (!InputMode.isGamepadConnected()) {
            return;
        }
        Context context = button.getContext();
        View glyph = ButtonGlyph.create(context, ApolloColors.dark(context), key);
        int size = ApolloUi.dp(context, 18);
        glyph.measure(View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY));
        glyph.layout(0, 0, size, size);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        glyph.draw(new Canvas(bitmap));
        BitmapDrawable drawable = new BitmapDrawable(context.getResources(), bitmap);
        drawable.setBounds(0, 0, size, size);
        button.setCompoundDrawablesRelative(drawable, null, null, null);
        button.setCompoundDrawablePadding(ApolloUi.dp(context, 8));
    }
}
