package com.limelight.utils;

import java.util.ArrayList;
import java.util.Iterator;

import android.app.Activity;
import android.widget.TextView;

import com.limelight.ui.apollo.ApolloDialog;
import com.limelight.ui.apollo.ApolloUi;
import com.limelight.R;

public class Dialog implements Runnable {
    private final String title;
    private final String message;
    private final Activity activity;
    private final Runnable runOnDismiss;

    private ApolloDialog alert;

    private static final ArrayList<Dialog> rundownDialogs = new ArrayList<>();

    private Dialog(Activity activity, String title, String message, Runnable runOnDismiss)
    {
        this.activity = activity;
        this.title = title;
        this.message = message;
        this.runOnDismiss = runOnDismiss;
    }

    // Only closes the given activity's dialogs, so one activity stopping doesn't
    // dismiss a dialog another activity has just shown
    public static void closeDialogs(Activity activity)
    {
        synchronized (rundownDialogs) {
            Iterator<Dialog> i = rundownDialogs.iterator();
            while (i.hasNext()) {
                Dialog d = i.next();
                if (d.activity == activity) {
                    i.remove();
                    if (d.alert.dialog.isShowing()) {
                        d.alert.dismiss();
                    }
                }
            }
        }
    }

    public static void displayDialog(final Activity activity, String title, String message, final boolean endAfterDismiss)
    {
        activity.runOnUiThread(new Dialog(activity, title, message, new Runnable() {
            @Override
            public void run() {
                if (endAfterDismiss) {
                    activity.finish();
                }
            }
        }));
    }

    public static void displayDialog(Activity activity, String title, String message, Runnable runOnDismiss)
    {
        activity.runOnUiThread(new Dialog(activity, title, message, runOnDismiss));
    }

    @Override
    public void run() {
        // If we're dying, don't bother creating a dialog
        if (activity.isFinishing())
            return;

        // In the look of the app: a card over a dark veil, OK filled and Help beside it
        alert = new ApolloDialog(activity, title, 460);
        TextView text = ApolloUi.text(activity, message, 14, alert.colors.onSurfaceVariant, false);
        text.setPadding(alert.dp(14), 0, alert.dp(14), 0);
        text.setLineSpacing(0, 1.25f);
        alert.body.addView(text);
        alert.dialog.setCancelable(false);
        alert.dialog.setCanceledOnTouchOutside(false);

        // A is OK, Y Help, and B closes it as OK does
        alert.buttons(activity.getString(R.string.help), () -> {
            close();
            HelpLauncher.launchTroubleshooting(activity);
        }, activity.getString(android.R.string.ok), this::close);
        alert.dialog.setOnCancelListener(dialog -> close());

        synchronized (rundownDialogs) {
            rundownDialogs.add(this);
            alert.show();
        }
    }

    private void close() {
        synchronized (rundownDialogs) {
            if (!rundownDialogs.remove(this)) {
                // Already closed
                return;
            }
            alert.dismiss();
        }

        runOnDismiss.run();
    }

}
