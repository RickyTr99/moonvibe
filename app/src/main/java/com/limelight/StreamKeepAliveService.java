package com.limelight;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * Keeps the app running while a stream goes on in the background ("Keep the stream running"):
 * without a foreground service, Android freezes the app and the host drops the stream. Its
 * notification brings the stream back, or disconnects it.
 */
public class StreamKeepAliveService extends Service {
    private static final String CHANNEL_ID = "stream_keep_alive";
    private static final int NOTIFICATION_ID = 0x4d56;
    private static final String EXTRA_PC_NAME = "pcName";
    private static final String EXTRA_APP_NAME = "appName";

    static void start(Context context, String pcName, String appName) {
        Intent intent = new Intent(context, StreamKeepAliveService.class)
                .putExtra(EXTRA_PC_NAME, pcName)
                .putExtra(EXTRA_APP_NAME, appName);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (RuntimeException e) {
            // Not allowed right now: the stream then ends in the background as before
            LimeLog.warning("Keep-alive service not started: " + e);
        }
    }

    static void stop(Context context) {
        context.stopService(new Intent(context, StreamKeepAliveService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String pcName = intent != null ? intent.getStringExtra(EXTRA_PC_NAME) : null;
        String appName = intent != null ? intent.getStringExtra(EXTRA_APP_NAME) : null;
        Notification notification = buildNotification(pcName, appName);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        // Started again by Game if it's needed after the app was killed
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification(String pcName, String appName) {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.keep_alive_channel), NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        int immutable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, Game.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT | immutable);
        PendingIntent disconnect = PendingIntent.getBroadcast(this, 1,
                new Intent(Game.ACTION_DISCONNECT).setPackage(getPackageName()),
                PendingIntent.FLAG_UPDATE_CURRENT | immutable);

        builder.setSmallIcon(R.drawable.ic_apollo_play)
                .setContentTitle(appName != null ? appName : pcName)
                .setContentText(pcName != null ? getString(R.string.keep_alive_text, pcName) : null)
                .setContentIntent(open)
                .setOngoing(true)
                .setShowWhen(false)
                .addAction(new Notification.Action.Builder(0, getString(R.string.keep_alive_disconnect), disconnect).build());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return builder.build();
    }
}
