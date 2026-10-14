package com.limelight.utils;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;

import com.limelight.LimeLog;
import com.limelight.nvstream.http.HostHttpResponseException;
import com.limelight.nvstream.http.HostMetrics;
import com.limelight.nvstream.http.NvHTTP;

import java.io.FileNotFoundException;
import java.util.concurrent.Callable;

/**
 * What some hosts offer during a stream beyond Moonlight's protocol: changing the bitrate while it runs
 * (Vibepollo, Rubylight) and the PC's CPU and GPU readings (Rubylight). Asked for on its own thread over
 * the paired HTTPS connection; what it finds out comes back on the main thread.
 */
public class HostExtras {
    public interface Listener {
        /** What the host supports is now known (capabilities), or the bitrate it applied changed. */
        void onHostExtrasChanged(boolean capabilities);
    }

    // A change waits for the slider to stop, so the encoder is not reset at every step
    private static final long BITRATE_DELAY_MS = 600;
    // The readings are asked for at most this often, and are too old to show past the other
    private static final long METRICS_MIN_INTERVAL_MS = 2000;
    private static final long METRICS_STALE_MS = 6000;
    private static final long METRICS_MAX_BACKOFF_MS = 30000;
    private static final int DEFAULT_FEC_PERCENT = 20;

    private final Callable<NvHTTP> httpFactory;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final HandlerThread thread = new HandlerThread("HostExtras");
    private final Handler worker;
    private NvHTTP http;

    // Main thread state
    private boolean bitrateAdjustable;
    // Read by the worker once set: the encoder's part of the whole stream's bitrate
    private volatile float encoderShare = 1f;
    private boolean metricsSupported;
    private int bitrateKbps;
    private int pendingKbps;
    private boolean metricsWanted;
    private volatile boolean metricsPolling;
    private boolean stopped;

    // Written by the worker, read on the main thread
    private volatile HostMetrics metrics;
    private volatile long metricsAtMs;
    private long metricsBackoffMs;

    public HostExtras(Callable<NvHTTP> httpFactory, int bitrateKbps, Listener listener) {
        this.httpFactory = httpFactory;
        this.bitrateKbps = bitrateKbps;
        this.listener = listener;
        thread.start();
        worker = new Handler(thread.getLooper());
    }

    /**
     * Once the stream runs: finds out what the host supports. The bitrate chosen in the settings is the whole
     * stream's, which the host splits into the encoder's share, FEC and audio; a change during the stream sets
     * the encoder's share alone, so it is scaled the same way.
     */
    public void start(boolean pyrowave, int audioChannels) {
        final int totalKbps = bitrateKbps;
        worker.post(() -> {
            boolean adjustable = false;
            boolean readings = false;
            int encoderKbps = 0;
            try {
                http = httpFactory.call();
                adjustable = http.supportsRuntimeBitrate();
                String serverInfo = http.getServerInfo(true);
                readings = NvHTTP.getHardwareMetricsVersion(serverInfo) == 1;
                if (adjustable) {
                    encoderKbps = NvHTTP.getInitialEncoderBitrateKbps(serverInfo);
                }
            } catch (Exception e) {
                LimeLog.warning("Host extras unavailable: " + e);
            }
            // Rubylight says what it chose; the others do as Sunshine
            if (encoderKbps <= 0) {
                encoderKbps = sunshineEncoderKbps(totalKbps, pyrowave, audioChannels);
            }
            final boolean canAdjust = adjustable;
            final boolean canRead = readings;
            final float share = Math.min(1f, encoderKbps / (float) Math.max(1, totalKbps));
            LimeLog.info("Host extras: runtime bitrate " + canAdjust + " (encoder share " + share
                    + "), hardware metrics " + canRead);
            main.post(() -> {
                if (stopped) {
                    return;
                }
                bitrateAdjustable = canAdjust;
                encoderShare = share;
                metricsSupported = canRead;
                updatePolling();
                listener.onHostExtrasChanged(true);
            });
        });
    }

    public void stop() {
        stopped = true;
        main.removeCallbacksAndMessages(null);
        worker.removeCallbacksAndMessages(null);
        thread.quitSafely();
    }

    // ---- The bitrate ----

    public boolean isBitrateAdjustable() {
        return bitrateAdjustable;
    }

    /**
     * The encoder's share of the whole bitrate as Sunshine works it out when the stream starts: the FEC
     * percentage (20 unless changed on the host, none for PyroWave), then the audio and 500 kbps of overhead.
     */
    private static int sunshineEncoderKbps(int totalKbps, boolean pyrowave, int audioChannels) {
        int kbps = pyrowave ? totalKbps : totalKbps * (100 - DEFAULT_FEC_PERCENT) / 100;
        kbps -= Math.min(audioChannels * 96, kbps / 5);
        kbps -= 500;
        return Math.max(1, kbps);
    }

    /** The bitrate of the whole stream as the host last applied it, or as asked for while a change waits, in kbps. */
    public int getBitrateKbps() {
        return pendingKbps > 0 ? pendingKbps : bitrateKbps;
    }

    /** Asks for a new bitrate for this stream only; sent once it stops changing. */
    public void requestBitrate(int kbps) {
        if (!bitrateAdjustable || stopped) {
            return;
        }
        pendingKbps = kbps;
        main.removeCallbacks(sendBitrate);
        main.postDelayed(sendBitrate, BITRATE_DELAY_MS);
    }

    private final Runnable sendBitrate = new Runnable() {
        @Override
        public void run() {
            final int requested = pendingKbps;
            if (requested <= 0 || requested == bitrateKbps) {
                pendingKbps = 0;
                return;
            }
            worker.post(() -> {
                int applied;
                try {
                    int encoder = Math.max(1, Math.round(requested * encoderShare));
                    int appliedEncoder = http.setStreamBitrate(encoder);
                    applied = Math.round(appliedEncoder / encoderShare);
                    LimeLog.info("Stream bitrate: asked " + requested + " kbps (encoder " + encoder
                            + "), the host applied " + appliedEncoder + " (" + applied + " in all)");
                } catch (Exception e) {
                    LimeLog.warning("Stream bitrate not changed: " + e);
                    applied = -1;
                }
                final int result = applied;
                main.post(() -> {
                    if (stopped) {
                        return;
                    }
                    // A newer request waits: it decides
                    if (pendingKbps == requested) {
                        pendingKbps = 0;
                    }
                    if (result > 0) {
                        bitrateKbps = result;
                    }
                    listener.onHostExtrasChanged(false);
                });
            });
        }
    };

    // ---- The PC's readings ----

    public boolean areMetricsSupported() {
        return metricsSupported;
    }

    /** Asked for only while something shows them: the stats are on and the stream is in front. */
    public void setMetricsWanted(boolean wanted) {
        metricsWanted = wanted;
        updatePolling();
    }

    /** The latest readings, or null when there are none recent enough. */
    public HostMetrics getMetrics() {
        HostMetrics latest = metrics;
        return latest != null && SystemClock.elapsedRealtime() - metricsAtMs < METRICS_STALE_MS ? latest : null;
    }

    private void updatePolling() {
        boolean poll = metricsSupported && metricsWanted && !stopped;
        if (poll == metricsPolling) {
            return;
        }
        metricsPolling = poll;
        worker.removeCallbacks(pollMetrics);
        if (poll) {
            metricsBackoffMs = 0;
            worker.post(pollMetrics);
        }
    }

    private final Runnable pollMetrics = new Runnable() {
        @Override
        public void run() {
            long nextMs = METRICS_MIN_INTERVAL_MS;
            try {
                HostMetrics latest = HostMetrics.parse(http.getHardwareMetrics());
                metrics = latest;
                metricsAtMs = SystemClock.elapsedRealtime();
                metricsBackoffMs = 0;
                nextMs = Math.max(METRICS_MIN_INTERVAL_MS, latest.intervalMs);
            } catch (HostHttpResponseException e) {
                if (e.getErrorCode() == 401 || e.getErrorCode() == 403) {
                    // Turned off on the host, or not allowed: not again during this stream
                    LimeLog.warning("Host hardware metrics refused: " + e);
                    metrics = null;
                    main.post(() -> {
                        metricsSupported = false;
                        updatePolling();
                    });
                    return;
                }
                // Busy, timed out or the stream not active yet: again a little later each time
                metrics = null;
                metricsBackoffMs = Math.min(METRICS_MAX_BACKOFF_MS, Math.max(METRICS_MIN_INTERVAL_MS, metricsBackoffMs * 2));
                nextMs = metricsBackoffMs;
            } catch (FileNotFoundException e) {
                LimeLog.warning("Host hardware metrics not found");
                metrics = null;
                main.post(() -> {
                    metricsSupported = false;
                    updatePolling();
                });
                return;
            } catch (Exception e) {
                metrics = null;
                metricsBackoffMs = Math.min(METRICS_MAX_BACKOFF_MS, Math.max(METRICS_MIN_INTERVAL_MS, metricsBackoffMs * 2));
                nextMs = metricsBackoffMs;
            }
            // Stopped while this request was out
            if (metricsPolling) {
                worker.postDelayed(this, nextMs);
            }
        }
    };
}
