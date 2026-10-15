package com.limelight.ui.apollo;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.ui.theme.ApolloColors;

/**
 * The PIN to type on the PC while pairing, as on the PIN step of the setup: the PC, the digits in
 * their boxes, where to type them and a spinner while the PC hasn't answered. It closes by itself
 * once the pairing ends; Cancel (or B) gives up waiting.
 */
public class PairingPinDialog {
    private final ApolloDialog card;
    private boolean closed;

    public PairingPinDialog(Context context, String pcName, String address, String pin, Runnable onCancel) {
        card = new ApolloDialog(context, null, 440);
        ApolloColors colors = card.colors;
        card.dialog.setCanceledOnTouchOutside(false);

        LinearLayout content = card.body;
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(card.dp(14), card.dp(4), card.dp(14), 0);

        TextView pc = ApolloUi.text(context, pcName + " · " + address, 14, colors.onSurfaceVariant, false);
        pc.setGravity(Gravity.CENTER);
        content.addView(pc);
        TextView title = ApolloUi.text(context, context.getString(R.string.apollo_setup_pin_title), 18, colors.onSurface, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, card.dp(2), 0, 0);
        content.addView(title);

        LinearLayout digits = new LinearLayout(context);
        digits.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < pin.length(); i++) {
            TextView digit = ApolloUi.text(context, String.valueOf(pin.charAt(i)), 26, colors.onSurface, true);
            digit.setTypeface(Typeface.create("monospace", Typeface.BOLD));
            digit.setGravity(Gravity.CENTER);
            digit.setBackground(ApolloUi.roundRect(colors.surfaceContainer, card.dp(12)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(card.dp(42), card.dp(52));
            params.leftMargin = i > 0 ? card.dp(8) : 0;
            digits.addView(digit, params);
        }
        LinearLayout.LayoutParams digitsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        digitsParams.topMargin = card.dp(14);
        content.addView(digits, digitsParams);

        TextView where = ApolloUi.text(context, context.getString(R.string.apollo_setup_pin_where,
                "https://" + address + ":47990"), 14, colors.onSurfaceVariant, false);
        where.setGravity(Gravity.CENTER);
        where.setPadding(0, card.dp(12), 0, 0);
        content.addView(where);

        LinearLayout waiting = new LinearLayout(context);
        waiting.setOrientation(LinearLayout.HORIZONTAL);
        waiting.setGravity(Gravity.CENTER_VERTICAL);
        ProgressBar spinner = new ProgressBar(context);
        spinner.setIndeterminate(true);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(colors.primary));
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(card.dp(14), card.dp(14));
        spinnerParams.rightMargin = card.dp(8);
        waiting.addView(spinner, spinnerParams);
        waiting.addView(ApolloUi.text(context, context.getString(R.string.apollo_setup_pin_waiting), 14, colors.primary, false));
        LinearLayout.LayoutParams waitingParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        waitingParams.topMargin = card.dp(10);
        content.addView(waiting, waitingParams);

        TextView cancel = ApolloUi.text(context, context.getString(R.string.apollo_setup_cancel), 14, colors.primary, true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setSingleLine(true);
        cancel.setPadding(card.dp(18), 0, card.dp(18), 0);
        cancel.setFocusable(true);
        cancel.setClickable(true);
        cancel.setBackground(ApolloUi.stateLayer(colors.surfaceContainerHighest, Color.TRANSPARENT, card.dp(20)));
        Runnable cancelled = () -> {
            if (!closed) {
                dismiss();
                onCancel.run();
            }
        };
        cancel.setOnClickListener(v -> cancelled.run());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, card.dp(40));
        cancelParams.topMargin = card.dp(8);
        content.addView(cancel, cancelParams);

        card.dialog.setOnCancelListener(d -> cancelled.run());
        // B cancels; there's nothing for A to confirm
        GamepadDialogs.bind(card.dialog, null, cancel);
    }

    public void show() {
        card.show();
    }

    public void dismiss() {
        closed = true;
        if (card.dialog.isShowing()) {
            card.dismiss();
        }
    }
}
