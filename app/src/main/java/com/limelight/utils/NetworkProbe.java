package com.limelight.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * What the connection test measures without anything on the PC: the link of this device (Wi-Fi or cable)
 * as Android reports it, and the round trip to the PC, timed on TCP connections to its HTTP port.
 * The usable speed and the bitrate are estimates from those, not a transfer.
 */
public final class NetworkProbe {
    public static final int TYPE_NONE = 0;
    public static final int TYPE_WIFI = 1;
    public static final int TYPE_ETHERNET = 2;
    public static final int TYPE_OTHER = 3;

    // Signal levels, from the RSSI
    public static final int SIGNAL_WEAK = 1;
    public static final int SIGNAL_FAIR = 2;
    public static final int SIGNAL_GOOD = 3;
    public static final int SIGNAL_EXCELLENT = 4;

    private static final int CONNECT_TIMEOUT_MS = 1000;
    private static final int PROBE_INTERVAL_MS = 60;

    private NetworkProbe() {
    }

    public static class Link {
        public int type = TYPE_NONE;
        public int rssi;
        public int rxMbps;
        public int txMbps;
        public int frequencyMhz;
        // 4 to 7 for Wi-Fi 4 to 7, 0 when unknown
        public int generation;
        public boolean sixGhz;
        // Speed reported for a cable, 0 when unknown
        public int ethernetMbps;

        public int signalLevel() {
            if (rssi >= -55) {
                return SIGNAL_EXCELLENT;
            } else if (rssi >= -67) {
                return SIGNAL_GOOD;
            } else if (rssi >= -75) {
                return SIGNAL_FAIR;
            }
            return SIGNAL_WEAK;
        }

        public String bandName() {
            if (frequencyMhz >= 5925) {
                return "6 GHz";
            } else if (frequencyMhz >= 4900) {
                return "5 GHz";
            }
            return "2,4 GHz";
        }

        public int channel() {
            if (frequencyMhz == 2484) {
                return 14;
            } else if (frequencyMhz >= 5925) {
                return (frequencyMhz - 5950) / 5;
            } else if (frequencyMhz >= 4900) {
                return (frequencyMhz - 5000) / 5;
            }
            return (frequencyMhz - 2407) / 5;
        }
    }

    public static class Latency {
        public int sent;
        public int received;
        public float averageMs;
        public float jitterMs;

        public int lost() {
            return sent - received;
        }
    }

    public interface LatencyListener {
        void onProbe(Latency soFar);
    }

    @SuppressWarnings("deprecation")
    public static Link readLink(Context context) {
        Link link = new Link();
        ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = connectivity != null ? connectivity.getActiveNetwork() : null;
        NetworkCapabilities caps = network != null ? connectivity.getNetworkCapabilities(network) : null;
        if (caps == null) {
            return link;
        }

        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            link.type = TYPE_ETHERNET;
            link.ethernetMbps = caps.getLinkDownstreamBandwidthKbps() / 1000;
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            link.type = TYPE_WIFI;
            WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wifi != null ? wifi.getConnectionInfo() : null;
            if (info != null) {
                link.rssi = info.getRssi();
                link.frequencyMhz = info.getFrequency();
                link.rxMbps = info.getLinkSpeed();
                link.txMbps = info.getLinkSpeed();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (info.getRxLinkSpeedMbps() > 0) {
                        link.rxMbps = info.getRxLinkSpeedMbps();
                    }
                    if (info.getTxLinkSpeedMbps() > 0) {
                        link.txMbps = info.getTxLinkSpeedMbps();
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // ScanResult.WIFI_STANDARD_*: 4 = 11n, 5 = 11ac, 6 = 11ax, 8 = 11be
                    switch (info.getWifiStandard()) {
                        case 4: link.generation = 4; break;
                        case 5: link.generation = 5; break;
                        case 6: link.generation = 6; break;
                        case 8: link.generation = 7; break;
                        default: break;
                    }
                }
                link.sixGhz = link.frequencyMhz >= 5925;
            }
        } else {
            link.type = TYPE_OTHER;
        }
        return link;
    }

    /** Times connections to the PC, one every few ms; runs on the calling thread. */
    public static Latency measureLatency(String host, int port, int count, AtomicBoolean cancelled,
                                         LatencyListener listener) {
        Latency latency = new Latency();
        float total = 0;
        float jitterTotal = 0;
        float previous = -1;
        for (int i = 0; i < count && !cancelled.get(); i++) {
            latency.sent++;
            long start = System.nanoTime();
            try (Socket socket = new Socket()) {
                socket.setTcpNoDelay(true);
                socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
                float ms = (System.nanoTime() - start) / 1_000_000f;
                latency.received++;
                total += ms;
                if (previous >= 0) {
                    jitterTotal += Math.abs(ms - previous);
                }
                previous = ms;
                latency.averageMs = total / latency.received;
                latency.jitterMs = latency.received > 1 ? jitterTotal / (latency.received - 1) : 0;
            } catch (Exception ignored) {
                // A probe without an answer counts as lost
            }
            if (listener != null) {
                listener.onProbe(latency);
            }
            try {
                Thread.sleep(PROBE_INTERVAL_MS);
            } catch (InterruptedException e) {
                break;
            }
        }
        return latency;
    }

    /** What the link can carry in practice, in Mbps; 0 when it can't be told */
    public static int usableMbps(Link link) {
        if (link.type == TYPE_ETHERNET) {
            return link.ethernetMbps > 0 ? Math.round(link.ethernetMbps * 0.9f) : 900;
        }
        if (link.type != TYPE_WIFI || link.rxMbps <= 0) {
            return 0;
        }
        // Wi-Fi carries a little over half of its link speed, less when the signal is weak
        float factor;
        switch (link.signalLevel()) {
            case SIGNAL_EXCELLENT: factor = 1f; break;
            case SIGNAL_GOOD: factor = 0.85f; break;
            case SIGNAL_FAIR: factor = 0.6f; break;
            default: factor = 0.35f; break;
        }
        return Math.round(link.rxMbps * 0.55f * factor);
    }

    public static class Recommendation {
        // 0 when it can't be told
        public int mbps;
        // Lowered because answers were lost or came at uneven times
        public boolean unsteady;
        // Lowered because the PC answers slowly
        public boolean slow;
        // Held at the highest bitrate the setting allows
        public boolean capped;
    }

    /**
     * A bitrate that leaves room for the peaks of the video (a full frame weighs many times an average one)
     * and for the swings of Wi-Fi: a third of the usable speed, less when the PC answers unevenly or slowly.
     */
    public static Recommendation recommendBitrate(Link link, Latency latency, int maxMbps) {
        Recommendation recommendation = new Recommendation();
        int usable = usableMbps(link);
        if (usable <= 0) {
            return recommendation;
        }
        float bitrate = usable * (1f / 3);
        if (latency != null && latency.received > 0) {
            if (latency.lost() > 0 || latency.jitterMs > 5) {
                bitrate *= 0.8f;
                recommendation.unsteady = true;
            }
            if (latency.averageMs > 20) {
                bitrate *= 0.9f;
                recommendation.slow = true;
            }
        }
        int rounded = ((int) bitrate) / 5 * 5;
        recommendation.capped = rounded > maxMbps;
        recommendation.mbps = Math.max(5, Math.min(maxMbps, rounded));
        return recommendation;
    }

    /**
     * Whether a game can be streamed away from home through this network: Moonlight's test server tries
     * every port the stream uses. Blocking, it takes a few seconds; returns the blocked ports' flags,
     * 0 when none, or MoonBridge.ML_TEST_RESULT_INCONCLUSIVE without internet.
     */
    public static int testPorts() {
        return com.limelight.nvstream.jni.MoonBridge.testClientConnectivity(ServerHelper.CONNECTION_TEST_SERVER, 443,
                com.limelight.nvstream.jni.MoonBridge.ML_PORT_FLAG_ALL);
    }
}
