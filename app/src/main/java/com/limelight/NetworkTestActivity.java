package com.limelight;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.nvstream.jni.MoonBridge;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.apollo.ApolloUi;
import com.limelight.ui.apollo.hints.ButtonGlyph;
import com.limelight.ui.apollo.hints.InputMode;
import com.limelight.ui.apollo.launch.LaunchProgressBar;
import com.limelight.ui.apollo.launch.StepIconView;
import com.limelight.ui.theme.ApolloColors;
import com.limelight.ui.theme.ApolloMotion;
import com.limelight.utils.NetworkProbe;
import com.limelight.utils.UiHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The connection test with a PC, on the home background: the link of this device (speed and signal),
 * the round trip to the PC, and from them an estimate of the usable speed and a bitrate to use.
 * Laid out like the launch screen: steps with a progress bar, then the results. B closes, Y repeats,
 * A applies the bitrate.
 */
public class NetworkTestActivity extends Activity {
    public static final String EXTRA_PC_NAME = "PcName";
    public static final String EXTRA_HOST = "Host";
    public static final String EXTRA_PORT = "Port";

    private static final int PROBE_COUNT = 30;
    private static final int STEP_LINK = 0;
    private static final int STEP_LATENCY = 1;
    private static final int STEP_PORTS = 2;
    private static final int STEP_USABLE = 3;
    private static final int STEP_COUNT = 4;
    private static final int COLUMN_MAX_WIDTH_DP = 560;
    // The bitrate setting goes up to 500 Mbps
    private static final int MAX_BITRATE_MBPS = 500;

    private ApolloColors colors;
    private String pcName;
    private String host;
    private int port;

    private FrameLayout stage;
    private LinearLayout progressView;
    private LinearLayout resultView;
    private LinearLayout chips;
    private final StepIconView[] stepIcons = new StepIconView[STEP_COUNT];
    private final TextView[] stepLabels = new TextView[STEP_COUNT];
    private final TextView[] stepDetails = new TextView[STEP_COUNT];
    private LaunchProgressBar progressBar;
    private LinearLayout buttons;

    private AtomicBoolean cancelled = new AtomicBoolean();
    private boolean finished;
    private int recommendedMbps;
    private boolean applied;
    private View applyButton;
    private WifiManager.WifiLock highPerfLock;
    private WifiManager.WifiLock lowLatencyLock;

    public static void start(Activity parent, String pcName, String host, int port) {
        Intent intent = new Intent(parent, NetworkTestActivity.class);
        intent.putExtra(EXTRA_PC_NAME, pcName);
        intent.putExtra(EXTRA_HOST, host);
        intent.putExtra(EXTRA_PORT, port);
        parent.startActivity(intent);
        parent.overridePendingTransition(R.anim.apollo_fade_in, R.anim.apollo_fade_out);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiHelper.setLocale(this);

        colors = ApolloColors.dark(this);
        pcName = getIntent().getStringExtra(EXTRA_PC_NAME);
        host = getIntent().getStringExtra(EXTRA_HOST);
        port = getIntent().getIntExtra(EXTRA_PORT, 47989);

        FrameLayout root = new FrameLayout(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // The column sits in the middle; the bottom padding keeps it clear of the buttons in the corner
        FrameLayout center = new FrameLayout(this);
        center.setPadding(dp(48), dp(24), dp(48), dp(64));
        scroll.addView(center, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        stage = new FrameLayout(this) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = Math.min(MeasureSpec.getSize(widthMeasureSpec), dp(COLUMN_MAX_WIDTH_DP));
                super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);
            }
        };
        center.addView(stage, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        buildProgressView();

        buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        FrameLayout.LayoutParams buttonsParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        buttonsParams.setMargins(0, 0, dp(28), dp(20));
        root.addView(buttons, buttonsParams);

        setContentView(root);
        UiHelper.notifyNewRootView(this);
        ApolloUi.padForCutout(root);

        startTest();
    }

    private int dp(float value) {
        return ApolloUi.dp(this, value);
    }

    private TextView text(CharSequence value, float sizeSp, int color, boolean medium) {
        return ApolloUi.text(this, value, sizeSp, color, medium);
    }

    private TextView eyebrow(int textRes) {
        TextView eyebrow = text(getString(textRes).toUpperCase(Locale.getDefault()), 12, colors.primary, true);
        eyebrow.setLetterSpacing(0.1f);
        return eyebrow;
    }

    // --- While the test runs: the same steps and bar as the launch screen

    private void buildProgressView() {
        progressView = new LinearLayout(this);
        progressView.setOrientation(LinearLayout.VERTICAL);
        stage.addView(progressView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        progressView.addView(eyebrow(R.string.apollo_nettest_title));
        TextView title = text(getString(R.string.apollo_nettest_checking), 30, colors.onSurface, true);
        progressView.addView(title);
        TextView subtitle = text(host != null ? pcName + " · " + host : getString(R.string.apollo_nettest_pc_off, pcName),
                15, colors.onSurfaceVariant, false);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        progressView.addView(subtitle);

        chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams chipsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipsParams.topMargin = dp(14);
        progressView.addView(chips, chipsParams);

        int[] labels = {R.string.apollo_nettest_step_link, R.string.apollo_nettest_step_latency,
                R.string.apollo_nettest_step_ports, R.string.apollo_nettest_step_usable};
        for (int i = 0; i < STEP_COUNT; i++) {
            LinearLayout stepRow = new LinearLayout(this);
            stepRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams stepParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            stepParams.topMargin = dp(i == 0 ? 28 : 12);
            progressView.addView(stepRow, stepParams);

            stepIcons[i] = new StepIconView(this, colors);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(20), dp(20));
            iconParams.setMarginEnd(dp(12));
            iconParams.topMargin = dp(1);
            stepRow.addView(stepIcons[i], iconParams);

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            stepRow.addView(texts);
            stepLabels[i] = text(getString(labels[i]), 16, colors.outline, false);
            texts.addView(stepLabels[i]);
            stepDetails[i] = text("", 12, colors.outline, false);
            stepDetails[i].setVisibility(View.GONE);
            texts.addView(stepDetails[i]);
        }

        progressBar = new LaunchProgressBar(this, colors);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(dp(320), dp(5));
        barParams.topMargin = dp(24);
        progressView.addView(progressBar, barParams);
    }

    private void setChips(List<String> labels) {
        chips.removeAllViews();
        for (String label : labels) {
            TextView chip = text(label, 12, colors.onSurfaceVariant, false);
            chip.setPadding(dp(10), dp(4), dp(10), dp(4));
            GradientDrawable border = ApolloUi.roundRect(0, dp(8));
            border.setStroke(dp(1), colors.outlineVariant);
            chip.setBackground(border);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(dp(6));
            chips.addView(chip, params);
        }
    }

    private void setStep(int index, int state, String detail) {
        stepIcons[index].setState(state);
        stepLabels[index].setTextColor(state == StepIconView.ACTIVE ? colors.onSurface
                : state == StepIconView.PENDING ? colors.outline : colors.onSurfaceVariant);
        boolean shown = stepDetails[index].getVisibility() == View.VISIBLE;
        if (detail == null) {
            stepDetails[index].setVisibility(View.GONE);
            return;
        }
        stepDetails[index].setText(detail);
        stepDetails[index].setTextColor(state == StepIconView.ACTIVE ? colors.onSurfaceVariant : colors.outline);
        if (!shown) {
            stepDetails[index].setVisibility(View.VISIBLE);
            stepDetails[index].setAlpha(0f);
            stepDetails[index].setTranslationY(dp(6));
            stepDetails[index].animate().alpha(1f).translationY(0)
                    .setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE).start();
        }
    }

    private void startTest() {
        cancelled.set(true);
        AtomicBoolean token = new AtomicBoolean();
        cancelled = token;
        finished = false;
        applied = false;

        boolean hadResult = resultView != null;
        if (resultView != null) {
            View old = resultView;
            resultView = null;
            old.animate().alpha(0f).translationY(-dp(8)).setDuration(ApolloMotion.SHORT)
                    .setInterpolator(ApolloMotion.STANDARD).withEndAction(() -> stage.removeView(old)).start();
        }
        chips.removeAllViews();
        for (int i = 0; i < STEP_COUNT; i++) {
            setStep(i, StepIconView.PENDING, null);
        }
        progressBar.reset();
        progressView.setVisibility(View.VISIBLE);
        progressView.setAlpha(0f);
        progressView.setTranslationY(dp(10));
        progressView.animate().alpha(1f).translationY(0).setStartDelay(hadResult ? ApolloMotion.SHORT : 0)
                .setDuration(500).setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE).start();
        setButtons(false);

        // Wi-Fi powered up as during a stream, or its power saving adds to the round trip
        acquireWifiLocks();

        // 1. The link, read at once
        NetworkProbe.Link link = NetworkProbe.readLink(this);
        setChips(linkChips(link));
        setStep(STEP_LINK, StepIconView.DONE, linkDetail(link));
        setStep(STEP_LATENCY, StepIconView.ACTIVE, getString(R.string.apollo_nettest_latency_waiting));
        progressBar.setProgress(0.1f);

        // 2. The round trip, 3. the ports for streaming away from home, then 4. the estimate
        new Thread(() -> {
            NetworkProbe.Latency latency;
            if (host != null) {
                latency = NetworkProbe.measureLatency(host, port, PROBE_COUNT, token, soFar -> {
                    int received = soFar.received;
                    int sent = soFar.sent;
                    String average = formatMs(soFar.averageMs);
                    runOnUiThread(() -> {
                        if (token.get()) {
                            return;
                        }
                        setStep(STEP_LATENCY, StepIconView.ACTIVE, received > 0
                                ? getString(R.string.apollo_nettest_latency_progress, received, PROBE_COUNT, average)
                                : getString(R.string.apollo_nettest_latency_progress_none, sent, PROBE_COUNT));
                        progressBar.setProgress(0.1f + 0.5f * sent / PROBE_COUNT);
                    });
                });
            } else {
                latency = new NetworkProbe.Latency();
            }
            runOnUiThread(() -> {
                if (token.get()) {
                    return;
                }
                setStep(STEP_LATENCY, latency.received > 0 ? StepIconView.DONE : StepIconView.FAILED, latencySummary(latency));
                setStep(STEP_PORTS, StepIconView.ACTIVE, getString(R.string.apollo_nettest_ports_waiting));
                progressBar.setProgress(0.75f);
            });
            if (token.get()) {
                return;
            }

            int ports = NetworkProbe.testPorts();
            runOnUiThread(() -> {
                if (token.get()) {
                    return;
                }
                releaseWifiLocks();
                setStep(STEP_PORTS, ports == 0 || ports == MoonBridge.ML_TEST_RESULT_INCONCLUSIVE
                        ? StepIconView.DONE : StepIconView.FAILED, portsSummary(ports));
                setStep(STEP_USABLE, StepIconView.ACTIVE, null);
                progressBar.setProgress(1f);
                // A short beat on the last step, then the results
                stage.postDelayed(() -> {
                    if (!token.get()) {
                        setStep(STEP_USABLE, StepIconView.DONE, null);
                        showResults(link, latency, ports);
                    }
                }, 450);
            });
        }, "NetworkTest").start();
    }

    private String portsSummary(int ports) {
        if (ports == 0) {
            return getString(R.string.apollo_nettest_ports_open);
        } else if (ports == MoonBridge.ML_TEST_RESULT_INCONCLUSIVE) {
            return getString(R.string.apollo_nettest_ports_unknown);
        }
        return getString(R.string.apollo_nettest_ports_blocked, MoonBridge.stringifyPortFlags(ports, ", "));
    }

    private void acquireWifiLocks() {
        if (highPerfLock != null) {
            return;
        }
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        try {
            highPerfLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MoonVibe network test");
            highPerfLock.setReferenceCounted(false);
            highPerfLock.acquire();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                lowLatencyLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "MoonVibe network test");
                lowLatencyLock.setReferenceCounted(false);
                lowLatencyLock.acquire();
            }
        } catch (SecurityException e) {
            // Some Samsung devices refuse it, as in Game
        }
    }

    private void releaseWifiLocks() {
        if (highPerfLock != null) {
            highPerfLock.release();
            highPerfLock = null;
        }
        if (lowLatencyLock != null) {
            lowLatencyLock.release();
            lowLatencyLock = null;
        }
    }

    private List<String> linkChips(NetworkProbe.Link link) {
        List<String> list = new ArrayList<>();
        switch (link.type) {
            case NetworkProbe.TYPE_WIFI:
                if (link.generation > 0) {
                    list.add("Wi-Fi " + link.generation + (link.generation == 6 && link.sixGhz ? "E" : ""));
                }
                if (link.frequencyMhz > 0) {
                    list.add(getString(R.string.apollo_nettest_band, link.bandName(), link.channel()));
                }
                list.add(getString(R.string.apollo_nettest_signal_chip, link.rssi, signalName(link.signalLevel())));
                break;
            case NetworkProbe.TYPE_ETHERNET:
                list.add(getString(R.string.apollo_nettest_wired));
                break;
            case NetworkProbe.TYPE_OTHER:
                list.add(getString(R.string.apollo_nettest_other_network));
                break;
            default:
                list.add(getString(R.string.apollo_nettest_no_network));
                break;
        }
        return list;
    }

    private String linkDetail(NetworkProbe.Link link) {
        switch (link.type) {
            case NetworkProbe.TYPE_WIFI:
                return getString(R.string.apollo_nettest_link_detail, link.rxMbps, link.txMbps);
            case NetworkProbe.TYPE_ETHERNET:
                return link.ethernetMbps > 0 ? getString(R.string.apollo_nettest_wired_speed, link.ethernetMbps)
                        : getString(R.string.apollo_nettest_wired);
            case NetworkProbe.TYPE_OTHER:
                return getString(R.string.apollo_nettest_other_network);
            default:
                return getString(R.string.apollo_nettest_no_network);
        }
    }

    private String signalName(int level) {
        switch (level) {
            case NetworkProbe.SIGNAL_EXCELLENT: return getString(R.string.apollo_nettest_signal_excellent);
            case NetworkProbe.SIGNAL_GOOD: return getString(R.string.apollo_nettest_signal_good);
            case NetworkProbe.SIGNAL_FAIR: return getString(R.string.apollo_nettest_signal_fair);
            default: return getString(R.string.apollo_nettest_signal_weak);
        }
    }

    private static String formatMs(float ms) {
        return ms < 10 ? String.format(Locale.getDefault(), "%.1f", ms) : String.valueOf(Math.round(ms));
    }

    private String latencySummary(NetworkProbe.Latency latency) {
        if (latency.received == 0) {
            return getString(R.string.apollo_nettest_no_answer);
        }
        String summary = getString(R.string.apollo_nettest_latency_line, formatMs(latency.averageMs), formatMs(latency.jitterMs));
        return summary + " · " + getString(R.string.apollo_nettest_lost, latency.lost(), latency.sent);
    }

    // --- The results

    // One result in a line: what it is, then the value
    private View fact(String label, String value) {
        TextView view = text(label + "  " + value, 13, colors.onSurfaceVariant, false);
        android.text.SpannableString styled = new android.text.SpannableString(view.getText());
        styled.setSpan(new android.text.style.ForegroundColorSpan(colors.onSurface), 0, label.length(), 0);
        view.setText(styled);
        view.setPadding(0, dp(2), 0, 0);
        return view;
    }

    private void showResults(NetworkProbe.Link link, NetworkProbe.Latency latency, int ports) {
        finished = true;
        boolean pcAnswers = latency.received * 2 >= latency.sent && latency.received > 0;
        NetworkProbe.Recommendation recommendation = pcAnswers
                ? NetworkProbe.recommendBitrate(link, latency, MAX_BITRATE_MBPS) : new NetworkProbe.Recommendation();
        recommendedMbps = recommendation.mbps;
        int currentMbps = Math.max(1, PreferenceConfiguration.readPreferences(this).bitrate / 1000);

        resultView = new LinearLayout(this);
        resultView.setOrientation(LinearLayout.VERTICAL);

        resultView.addView(eyebrow(R.string.apollo_nettest_title_done));
        resultView.addView(text(getString(verdict(link, latency, pcAnswers)), 30, colors.onSurface, true));
        List<String> details = new ArrayList<>();
        details.add(pcName);
        details.addAll(linkChips(link));
        if (link.type == NetworkProbe.TYPE_WIFI) {
            // The signal has its own card
            details.remove(details.size() - 1);
        }
        TextView subtitle = text(TextUtils.join(" · ", details), 15, colors.onSurfaceVariant, false);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        resultView.addView(subtitle);

        // The two numbers that matter most: link speed and signal
        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams cardsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardsParams.topMargin = dp(22);
        resultView.addView(cards, cardsParams);
        if (link.type == NetworkProbe.TYPE_WIFI) {
            addCard(cards, getString(R.string.apollo_nettest_card_link), String.valueOf(link.rxMbps), "Mbps",
                    getString(R.string.apollo_nettest_card_link_detail, link.txMbps), null);
            addCard(cards, getString(R.string.apollo_nettest_card_signal), String.valueOf(link.rssi), "dBm",
                    signalName(link.signalLevel()), signalBars(link.signalLevel()));
        } else {
            addCard(cards, getString(R.string.apollo_nettest_card_link),
                    link.type == NetworkProbe.TYPE_ETHERNET && link.ethernetMbps > 0 ? String.valueOf(link.ethernetMbps) : "—",
                    link.type == NetworkProbe.TYPE_ETHERNET && link.ethernetMbps > 0 ? "Mbps" : "",
                    linkDetail(link), null);
            addCard(cards, getString(R.string.apollo_nettest_card_latency),
                    latency.received > 0 ? formatMs(latency.averageMs) : "—", latency.received > 0 ? "ms" : "",
                    latency.received > 0 ? getString(R.string.apollo_nettest_jitter, formatMs(latency.jitterMs))
                            : getString(R.string.apollo_nettest_no_answer), null);
        }

        // Then, smaller, the estimate and the round trip
        int usable = NetworkProbe.usableMbps(link);
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.VERTICAL);
        line.setPadding(dp(4), 0, dp(4), 0);
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lineParams.topMargin = dp(12);
        resultView.addView(line, lineParams);
        if (usable > 0) {
            line.addView(fact(getString(R.string.apollo_nettest_usable, usable), ""));
        }
        if (link.type == NetworkProbe.TYPE_WIFI) {
            line.addView(fact(getString(R.string.apollo_nettest_fact_latency), latencySummary(latency)));
        }
        line.addView(fact(getString(R.string.apollo_nettest_fact_ports), portsSummary(ports)));

        if (recommendation.mbps > 0) {
            resultView.addView(recommendation(currentMbps, recommendation));
        }

        stage.addView(resultView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        progressView.animate().cancel();
        progressView.animate().alpha(0f).translationY(-dp(8)).setStartDelay(0).setDuration(ApolloMotion.SHORT)
                .setInterpolator(ApolloMotion.STANDARD).withEndAction(() -> progressView.setVisibility(View.INVISIBLE)).start();
        resultView.setAlpha(0f);
        resultView.setTranslationY(dp(10));
        resultView.animate().alpha(1f).translationY(0).setStartDelay(ApolloMotion.SHORT)
                .setDuration(500).setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE).start();
        setButtons(true);
    }

    private int verdict(NetworkProbe.Link link, NetworkProbe.Latency latency, boolean pcAnswers) {
        if (link.type == NetworkProbe.TYPE_NONE) {
            return R.string.apollo_nettest_verdict_no_network;
        }
        if (!pcAnswers) {
            return R.string.apollo_nettest_verdict_no_pc;
        }
        if (recommendedMbps == 0) {
            return latency.averageMs <= 10 && latency.lost() == 0
                    ? R.string.apollo_nettest_verdict_good : R.string.apollo_nettest_verdict_fair;
        }
        if (recommendedMbps >= 80) {
            return R.string.apollo_nettest_verdict_excellent;
        } else if (recommendedMbps >= 40) {
            return R.string.apollo_nettest_verdict_good;
        } else if (recommendedMbps >= 20) {
            return R.string.apollo_nettest_verdict_fair;
        }
        return R.string.apollo_nettest_verdict_weak;
    }

    private void addCard(LinearLayout row, String label, String value, String unit, String detail, View extra) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(ApolloUi.roundRect(colors.surfaceContainer, dp(12)));

        card.addView(text(label, 12, colors.onSurfaceVariant, false));
        LinearLayout valueRow = new LinearLayout(this);
        valueRow.setOrientation(LinearLayout.HORIZONTAL);
        valueRow.setGravity(Gravity.BOTTOM);
        valueRow.addView(text(value, 32, colors.onSurface, true));
        if (!unit.isEmpty()) {
            TextView unitView = text(unit, 15, colors.onSurfaceVariant, false);
            unitView.setPadding(dp(4), 0, 0, dp(6));
            valueRow.addView(unitView);
        }
        if (extra != null) {
            LinearLayout.LayoutParams extraParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            extraParams.setMarginStart(dp(10));
            extraParams.bottomMargin = dp(10);
            valueRow.addView(extra, extraParams);
        }
        card.addView(valueRow);
        card.addView(text(detail, 12, colors.outline, false));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        if (row.getChildCount() > 0) {
            params.setMarginStart(dp(10));
        }
        row.addView(card, params);
    }

    private View signalBars(int level) {
        LinearLayout bars = new LinearLayout(this);
        bars.setOrientation(LinearLayout.HORIZONTAL);
        bars.setGravity(Gravity.BOTTOM);
        for (int i = 1; i <= 4; i++) {
            View bar = new View(this);
            bar.setBackground(ApolloUi.roundRect(i <= level ? colors.primary : colors.surfaceContainerHighest, dp(2)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(5), dp(2 + i * 4));
            if (i > 1) {
                params.setMarginStart(dp(3));
            }
            bars.addView(bar, params);
        }
        return bars;
    }

    private View recommendation(int currentMbps, NetworkProbe.Recommendation recommendation) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(ApolloUi.roundRect(colors.secondaryContainer, dp(12)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        card.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        texts.addView(text(getString(R.string.apollo_nettest_recommended), 12, colors.onSecondaryContainer, false));
        texts.addView(text(getString(R.string.apollo_nettest_recommended_value, recommendedMbps)
                + (currentMbps != recommendedMbps ? getString(R.string.apollo_nettest_current, currentMbps) : ""),
                18, colors.onSecondaryContainer, true));
        TextView reason = text(reasonFor(recommendation), 12, colors.onSecondaryContainer, false);
        reason.setAlpha(0.8f);
        reason.setPadding(0, dp(2), 0, 0);
        texts.addView(reason);

        if (currentMbps != recommendedMbps) {
            applyButton = pill(KeyEvent.KEYCODE_BUTTON_A, getString(R.string.apollo_nettest_use, recommendedMbps),
                    colors.primary, colors.onPrimary, v -> applyBitrate());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginStart(dp(14));
            card.addView(applyButton, params);
        } else {
            applyButton = null;
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(16);
        card.setLayoutParams(params);
        return card;
    }

    // Why the bitrate is what it is
    private String reasonFor(NetworkProbe.Recommendation recommendation) {
        if (recommendation.capped) {
            return getString(R.string.apollo_nettest_reason_capped);
        }
        String reason = getString(R.string.apollo_nettest_reason_base);
        if (recommendation.unsteady) {
            reason += " " + getString(R.string.apollo_nettest_reason_unsteady);
        }
        if (recommendation.slow) {
            reason += " " + getString(R.string.apollo_nettest_reason_slow);
        }
        return reason;
    }

    private void applyBitrate() {
        if (applied || recommendedMbps <= 0 || applyButton == null) {
            return;
        }
        applied = true;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putInt(PreferenceConfiguration.BITRATE_PREF_STRING, recommendedMbps * 1000).apply();

        // The button turns into a quiet confirmation
        LinearLayout button = (LinearLayout) applyButton;
        button.setClickable(false);
        button.animate().alpha(0f).setDuration(ApolloMotion.SHORT).setInterpolator(ApolloMotion.STANDARD).withEndAction(() -> {
            button.removeAllViews();
            button.setBackground(null);
            button.setPadding(dp(8), dp(8), dp(4), dp(8));
            button.addView(text(getString(R.string.apollo_nettest_applied), 14, colors.onSecondaryContainer, true));
            button.animate().alpha(1f).setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD).start();
        }).start();
    }

    // --- Buttons in the corner, as on the launch screen

    private void setButtons(boolean done) {
        buttons.removeAllViews();
        if (done) {
            addCornerButton(pill(KeyEvent.KEYCODE_BUTTON_Y, getString(R.string.apollo_nettest_retry),
                    0x14000000 | (colors.onSurface & 0x00FFFFFF), colors.onSurface, v -> startTest()));
            addCornerButton(pill(KeyEvent.KEYCODE_BUTTON_B, getString(R.string.apollo_nettest_close),
                    0x14000000 | (colors.onSurface & 0x00FFFFFF), colors.onSurface, v -> close()));
        } else {
            addCornerButton(pill(KeyEvent.KEYCODE_BUTTON_B, getString(R.string.apollo_launch_cancel),
                    0x14000000 | (colors.onSurface & 0x00FFFFFF), colors.onSurface, v -> close()));
        }
    }

    private void addCornerButton(View button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (buttons.getChildCount() > 0) {
            params.setMarginStart(dp(10));
        }
        buttons.addView(button, params);
    }

    // A pill with the gamepad button in front when a gamepad is connected
    private LinearLayout pill(int key, String label, int background, int foreground, View.OnClickListener listener) {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        float radius = dp(20);
        pill.setBackground(ApolloUi.ripple(ApolloUi.roundRect(background, radius), radius));
        if (InputMode.isGamepadConnected()) {
            pill.setPadding(dp(10), dp(8), dp(16), dp(8));
            View glyph = ButtonGlyph.create(this, colors, key);
            LinearLayout.LayoutParams glyphParams = new LinearLayout.LayoutParams(dp(18), dp(18));
            glyphParams.setMarginEnd(dp(8));
            pill.addView(glyph, glyphParams);
        } else {
            pill.setPadding(dp(16), dp(8), dp(16), dp(8));
        }
        pill.addView(text(label, 14, foreground, true));
        pill.setOnClickListener(listener);
        ApolloUi.pressFeedback(pill);
        return pill;
    }

    // --- Keys: B or back closes, Y repeats, A applies the bitrate

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        boolean up = event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled();
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_ESCAPE:
                if (up) {
                    close();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_Y:
                if (up && finished) {
                    startTest();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (up && finished) {
                    applyBitrate();
                }
                return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        close();
    }

    private void close() {
        releaseWifiLocks();
        cancelled.set(true);
        finish();
        overridePendingTransition(R.anim.apollo_fade_in, R.anim.apollo_fade_out);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseWifiLocks();
        cancelled.set(true);
    }
}
