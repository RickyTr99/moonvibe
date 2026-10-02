package com.limelight.utils;

import java.util.ArrayList;
import java.util.Iterator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.limelight.ui.apollo.ApolloUi;
import com.limelight.ui.theme.ApolloColors;

public class SpinnerDialog implements Runnable,OnCancelListener {
    private final String title;
    private final String message;
    private final Activity activity;
    private AlertDialog progress;
    private TextView messageView;
    private final boolean finish;

    private static final ArrayList<SpinnerDialog> rundownDialogs = new ArrayList<>();

    private SpinnerDialog(Activity activity, String title, String message, boolean finish)
    {
        this.activity = activity;
        this.title = title;
        this.message = message;
        this.progress = null;
        this.finish = finish;
    }

    public static SpinnerDialog displayDialog(Activity activity, String title, String message, boolean finish)
    {
        SpinnerDialog spinner = new SpinnerDialog(activity, title, message, finish);
        activity.runOnUiThread(spinner);
        return spinner;
    }

    public static void closeDialogs(Activity activity)
    {
        synchronized (rundownDialogs) {
            Iterator<SpinnerDialog> i = rundownDialogs.iterator();
            while (i.hasNext()) {
                SpinnerDialog dialog = i.next();
                if (dialog.activity == activity) {
                    i.remove();
                    if (dialog.progress.isShowing()) {
                        dialog.progress.dismiss();
                    }
                }
            }
        }
    }

    public void dismiss()
    {
        // Running again with progress != null will destroy it
        activity.runOnUiThread(this);
    }

    public void setMessage(final String message)
    {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (messageView != null) {
                    messageView.setText(message);
                }
            }
        });
    }

    // Material 3 waiting dialog: a circular indicator over the title and the message, all centered
    private LinearLayout createContent() {
        ApolloColors colors = ApolloColors.dark(activity);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        int side = ApolloUi.dp(activity, 24);
        content.setPadding(side, ApolloUi.dp(activity, 28), side, finish ? 0 : side);

        ProgressBar indicator = new ProgressBar(activity);
        indicator.setIndeterminate(true);
        indicator.setIndeterminateTintList(ColorStateList.valueOf(colors.primary));
        int size = ApolloUi.dp(activity, 44);
        content.addView(indicator, new LinearLayout.LayoutParams(size, size));

        TextView titleView = ApolloUi.text(activity, title, 22, colors.onSurface, false);
        titleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = ApolloUi.dp(activity, 20);
        content.addView(titleView, titleParams);

        messageView = ApolloUi.text(activity, message, 14, colors.onSurfaceVariant, false);
        messageView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.topMargin = ApolloUi.dp(activity, 8);
        content.addView(messageView, messageParams);

        return content;
    }

    @Override
    public void run() {

        // If we're dying, don't bother doing anything
        if (activity.isFinishing()) {
            return;
        }

        if (progress == null)
        {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity).setView(createContent());

            // If we want to finish the activity when this is killed, make it cancellable
            if (finish) {
                builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
            }

            progress = builder.create();
            progress.setOnCancelListener(this);

            if (finish)
            {
                progress.setCancelable(true);
                progress.setCanceledOnTouchOutside(false);
            }
            else
            {
                progress.setCancelable(false);
            }

            synchronized (rundownDialogs) {
                rundownDialogs.add(this);
                progress.show();
            }
        }
        else
        {
            synchronized (rundownDialogs) {
                if (rundownDialogs.remove(this) && progress.isShowing()) {
                    progress.dismiss();
                }
            }
        }
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        synchronized (rundownDialogs) {
            rundownDialogs.remove(this);
        }

        // This will only be called if finish was true, so we don't need to check again
        activity.finish();
    }
}
