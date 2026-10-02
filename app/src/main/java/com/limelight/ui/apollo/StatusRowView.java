package com.limelight.ui.apollo;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.ui.theme.ApolloColors;

import java.util.Date;

/**
 * Device status shown by the app when the system bars are hidden: clock, network,
 * Bluetooth and battery. Refreshes itself while attached and started.
 */
public class StatusRowView extends LinearLayout {
    private static final long REFRESH_MS = 10000;
    private static final int LOW_BATTERY_PERCENT = 15;

    private final ApolloColors colors;
    private final TextView clockView;
    private final ApolloWidgets.WifiView wifiView;
    private final ImageView networkIcon;
    private final ImageView bluetoothIcon;
    private final ApolloWidgets.BatteryView batteryView;
    private final TextView batteryText;
    private boolean started;

    private final Runnable updater = new Runnable() {
        @Override
        public void run() {
            refresh();
            postDelayed(this, REFRESH_MS);
        }
    };

    // largeClock puts a big clock first, like the game menu; otherwise the clock comes last
    public StatusRowView(Context context, ApolloColors colors, boolean largeClock) {
        super(context);
        this.colors = colors;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        clockView = ApolloUi.text(context, "", largeClock ? 26 : 15, colors.onSurface, !largeClock);
        clockView.setIncludeFontPadding(false);

        wifiView = new ApolloWidgets.WifiView(context);
        networkIcon = icon(R.drawable.ic_menu_ethernet);
        bluetoothIcon = icon(R.drawable.ic_menu_bluetooth);
        batteryView = new ApolloWidgets.BatteryView(context);
        batteryText = ApolloUi.text(context, "", 13, colors.onSurfaceVariant, true);
        batteryText.setPadding(ApolloUi.dp(context, 4), 0, 0, 0);

        if (largeClock) {
            addView(clockView, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        }
        addView(wifiView);
        addView(networkIcon);
        addSpaced(bluetoothIcon);
        addSpaced(batteryView);
        addView(batteryText);
        if (!largeClock) {
            addSpaced(clockView);
        }
    }

    private ImageView icon(int resId) {
        ImageView view = new ImageView(getContext());
        view.setImageResource(resId);
        view.setImageTintList(ColorStateList.valueOf(colors.onSurfaceVariant));
        int size = ApolloUi.dp(getContext(), 18);
        view.setLayoutParams(new LayoutParams(size, size));
        return view;
    }

    private void addSpaced(android.view.View view) {
        LayoutParams params = view.getLayoutParams() != null
                ? new LayoutParams(view.getLayoutParams().width, view.getLayoutParams().height)
                : new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.leftMargin = ApolloUi.dp(getContext(), 10);
        addView(view, params);
    }

    public void start() {
        started = true;
        removeCallbacks(updater);
        updater.run();
    }

    public void stop() {
        started = false;
        removeCallbacks(updater);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(updater);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (started) {
            start();
        }
    }

    public void refresh() {
        Context context = getContext();
        clockView.setText(DateFormat.getTimeFormat(context).format(new Date()));

        refreshNetwork(context);

        boolean bluetoothOn = false;
        try {
            bluetoothOn = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BLUETOOTH_ON, 0) == 1;
        } catch (Exception ignored) {
        }
        bluetoothIcon.setVisibility(bluetoothOn ? VISIBLE : GONE);

        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery != null) {
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
            int percent = level >= 0 && scale > 0 ? Math.round(level * 100f / scale) : 100;
            int fill = charging ? colors.primary : percent <= LOW_BATTERY_PERCENT ? colors.error : colors.onSurfaceVariant;
            batteryView.setState(percent, colors.onSurfaceVariant, fill);
            batteryText.setText(percent + "%");
            batteryView.setVisibility(VISIBLE);
            batteryText.setVisibility(VISIBLE);
        } else {
            batteryView.setVisibility(GONE);
            batteryText.setVisibility(GONE);
        }
    }

    @SuppressWarnings("deprecation")
    private void refreshNetwork(Context context) {
        int wifiLevel = -1;
        int otherIcon = 0;

        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = cm.getActiveNetwork();
                NetworkCapabilities caps = network != null ? cm.getNetworkCapabilities(network) : null;
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    int rssi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? caps.getSignalStrength() : Integer.MIN_VALUE;
                    if (rssi == Integer.MIN_VALUE) {
                        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                        rssi = wm.getConnectionInfo().getRssi();
                    }
                    wifiLevel = WifiManager.calculateSignalLevel(rssi, 5);
                } else if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    otherIcon = R.drawable.ic_menu_ethernet;
                } else if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    otherIcon = R.drawable.ic_menu_cellular;
                }
            } else {
                NetworkInfo info = cm.getActiveNetworkInfo();
                if (info != null && info.getType() == ConnectivityManager.TYPE_WIFI) {
                    wifiLevel = 4;
                } else if (info != null && info.getType() == ConnectivityManager.TYPE_ETHERNET) {
                    otherIcon = R.drawable.ic_menu_ethernet;
                } else if (info != null && info.getType() == ConnectivityManager.TYPE_MOBILE) {
                    otherIcon = R.drawable.ic_menu_cellular;
                }
            }
        } catch (Exception ignored) {
        }

        if (otherIcon != 0) {
            wifiView.setVisibility(GONE);
            networkIcon.setImageResource(otherIcon);
            networkIcon.setVisibility(VISIBLE);
        } else {
            // No connection shows an empty Wi-Fi fan
            wifiView.setState(Math.max(0, wifiLevel), colors.onSurfaceVariant);
            wifiView.setVisibility(VISIBLE);
            networkIcon.setVisibility(GONE);
        }
    }
}
