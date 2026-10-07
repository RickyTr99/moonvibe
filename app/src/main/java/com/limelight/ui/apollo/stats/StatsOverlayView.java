package com.limelight.ui.apollo.stats;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.TypefaceSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.binding.video.PerfStats;
import com.limelight.ui.apollo.ApolloUi;
import com.limelight.ui.theme.ApolloMotion;

import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The stats over the stream, in one of three styles: a bar of values with icons, a panel with a value per row,
 * or a small pill with a couple of numbers. What it shows and where follow {@link StatsPrefs}.
 * It covers the whole stream and lets touches through; only its card is drawn.
 */
public class StatsOverlayView extends FrameLayout {
    private static final int COLOR_VEIL = 0x121318;
    private static final int COLOR_VALUE = 0xFFE2E2E9;
    private static final int COLOR_LABEL = 0xFFA9ACB6;
    private static final int COLOR_FAINT = 0xFF8E9099;
    private static final int COLOR_LINE = 0xFF33353A;
    private static final int COLOR_WARNING = 0xFFFFB77A;
    // The bar goes on a new line past this part of the screen
    private static final float BAR_MAX_FRACTION = 0.7f;

    private static final Pattern PACING_BUFFER = Pattern.compile("^(\\d+(?:\\.\\d+)?) ms buffer");

    private StatsPrefs.Style style;
    private StatsPrefs.Position position;
    private List<StatsPrefs.Item> items = new ArrayList<>();
    private boolean warnings;
    private View card;
    private PerfStats stats;
    private boolean preview;

    // The views of the card that follow the numbers
    private final Map<StatsPrefs.Item, Cell> cells = new EnumMap<>(StatsPrefs.Item.class);

    // A value on the bar or the mini, or a row of the panel
    private static class Cell {
        View view;
        ImageView icon;
        TextView value;
        // The panel's rows of an item after the first (the FPS on screen, the pacing details)
        final List<Cell> more = new ArrayList<>();
    }

    public StatsOverlayView(Context context) {
        super(context);
    }

    public StatsOverlayView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    private int dp(float value) {
        return ApolloUi.dp(getContext(), value);
    }

    /** In the settings: drawn with sample numbers, as it would look over a stream. */
    public void setPreview() {
        preview = true;
        stats = sample();
    }

    /** Rereads the settings and rebuilds the card; with a fade when it changes style while shown. */
    public void reload(boolean animate) {
        applyStyle(StatsPrefs.style(getContext()), animate);
    }

    /** The preview of the settings shows the style picked there, not the saved one. */
    public void showStyle(StatsPrefs.Style newStyle, boolean animate) {
        applyStyle(newStyle, animate);
    }

    private void applyStyle(StatsPrefs.Style newStyle, boolean animate) {
        Context context = getContext();
        style = newStyle;
        position = StatsPrefs.position(context, style);
        items = StatsPrefs.items(context, style);
        warnings = StatsPrefs.warnings(context);

        View old = card;
        cells.clear();
        switch (style) {
            case PANEL:
                card = buildPanel();
                break;
            case MINI:
                card = buildMini();
                break;
            default:
                card = buildBar();
                break;
        }
        card.setBackground(ApolloUi.roundRect(veil(StatsPrefs.opacity(context)), radius()));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !preview) {
            card.setPreferKeepClear(true);
        }
        addView(card, cardParams());
        bind();

        if (old != null) {
            if (animate) {
                old.animate().alpha(0).setDuration(ApolloMotion.SHORT).setInterpolator(ApolloMotion.STANDARD)
                        .withEndAction(() -> removeView(old)).start();
                card.setAlpha(0);
                card.animate().alpha(1).setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD).start();
            } else {
                old.animate().cancel();
                removeView(old);
            }
        }
    }

    /** The veil follows the opacity setting, while it is being dragged. */
    public void refreshOpacity() {
        if (card != null) {
            card.setBackground(ApolloUi.roundRect(veil(StatsPrefs.opacity(getContext())), radius()));
        }
    }

    /** New numbers from the decoder, about once a second. */
    public void update(PerfStats stats) {
        this.stats = stats;
        bind();
    }

    /** Shows or hides it with a fade. */
    public void setShown(boolean shown, boolean animate) {
        animate().cancel();
        if (!animate) {
            setAlpha(1);
            setVisibility(shown ? VISIBLE : GONE);
            return;
        }
        if (shown) {
            if (getVisibility() != VISIBLE) {
                setAlpha(0);
                setVisibility(VISIBLE);
            }
            animate().alpha(1).setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD).start();
        } else if (getVisibility() == VISIBLE) {
            animate().alpha(0).setDuration(ApolloMotion.SHORT).setInterpolator(ApolloMotion.STANDARD)
                    .withEndAction(() -> {
                        setVisibility(GONE);
                        setAlpha(1);
                    }).start();
        }
    }

    private static int veil(int opacityPercent) {
        return (Math.round(opacityPercent * 2.55f) << 24) | COLOR_VEIL;
    }

    private float radius() {
        switch (style) {
            case PANEL:
                return dp(16);
            case MINI:
                return dp(8);
            default:
                return dp(13);
        }
    }

    private LayoutParams cardParams() {
        int gravity = (position.top() ? Gravity.TOP : Gravity.BOTTOM)
                | (position.left() ? Gravity.LEFT : position.right() ? Gravity.RIGHT : Gravity.CENTER_HORIZONTAL);
        LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, gravity);
        int margin = dp(style == StatsPrefs.Style.MINI ? 6 : 8);
        params.setMargins(margin, margin, margin, margin);
        return params;
    }

    // ---- Building the card ----

    private TextView text(float sizeSp, int color) {
        TextView view = new TextView(getContext());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setSingleLine(true);
        return view;
    }

    private static int iconOf(StatsPrefs.Item item) {
        switch (item) {
            case DECODER:
                return R.drawable.ic_stats_decoder;
            case FPS:
                return R.drawable.ic_stats_fps;
            case NETWORK:
                return R.drawable.ic_stats_network;
            case HOST:
                return R.drawable.ic_stats_host;
            case DECODE:
                return R.drawable.ic_stats_decode;
            case TOTAL:
                return R.drawable.ic_stats_total;
            case LOSS:
                return R.drawable.ic_stats_loss;
            case BANDWIDTH:
                return R.drawable.ic_stats_bandwidth;
            case PACING:
                return R.drawable.ic_stats_pacing;
            case BATTERY:
                return R.drawable.ic_stats_battery;
            default:
                return 0;
        }
    }

    private View buildBar() {
        BarLayout bar = new BarLayout(getContext(), dp(14), dp(22), BAR_MAX_FRACTION);
        bar.setPadding(dp(12), dp(2), dp(12), dp(2));
        for (StatsPrefs.Item item : items) {
            Cell cell = new Cell();
            LinearLayout view = new LinearLayout(getContext());
            view.setOrientation(LinearLayout.HORIZONTAL);
            view.setGravity(Gravity.CENTER_VERTICAL);
            int icon = iconOf(item);
            if (icon != 0) {
                cell.icon = new ImageView(getContext());
                cell.icon.setImageResource(icon);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(14), dp(14));
                iconParams.rightMargin = dp(5);
                view.addView(cell.icon, iconParams);
            }
            cell.value = text(12.5f, COLOR_VALUE);
            view.addView(cell.value);
            cell.view = view;
            bar.addView(view, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(22)));
            cells.put(item, cell);
        }
        return bar;
    }

    private View buildMini() {
        LinearLayout mini = new LinearLayout(getContext());
        mini.setOrientation(LinearLayout.HORIZONTAL);
        mini.setGravity(Gravity.CENTER_VERTICAL);
        mini.setPadding(dp(7), 0, dp(7), 0);
        mini.setMinimumHeight(dp(16));
        for (StatsPrefs.Item item : items) {
            Cell cell = new Cell();
            cell.value = text(10, COLOR_VALUE);
            cell.view = cell.value;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (mini.getChildCount() > 0) {
                params.leftMargin = dp(6);
            }
            mini.addView(cell.value, params);
            cells.put(item, cell);
        }
        return mini;
    }

    private View buildPanel() {
        LinearLayout panel = new PanelLayout(getContext(), dp(200), dp(360));
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));

        for (StatsPrefs.Item item : items) {
            Cell cell = new Cell();
            cells.put(item, cell);
            if (item == StatsPrefs.Item.DECODER) {
                // The heading: decoder and renderer, then the stream
                LinearLayout header = new LinearLayout(getContext());
                header.setOrientation(LinearLayout.VERTICAL);
                header.setPadding(0, 0, 0, dp(6));
                cell.value = text(12.5f, COLOR_VALUE);
                cell.value.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                header.addView(cell.value);
                Cell details = new Cell();
                details.value = text(11, COLOR_LABEL);
                details.value.setPadding(0, dp(3), 0, 0);
                header.addView(details.value);
                cell.more.add(details);
                cell.view = header;
                panel.addView(header);
                continue;
            }
            if (item == StatsPrefs.Item.TOTAL) {
                // Under a line: what the rows above add up to
                LinearLayout total = new LinearLayout(getContext());
                total.setOrientation(LinearLayout.VERTICAL);
                View line = new View(getContext());
                line.setBackgroundColor(COLOR_LINE);
                LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
                lineParams.topMargin = dp(6);
                lineParams.bottomMargin = dp(6);
                total.addView(line, lineParams);
                Cell row = panelRow(total, R.string.stats_label_total, 0xFFC4C6D0, 13);
                cell.value = row.value;
                cell.view = total;
                panel.addView(total);
                continue;
            }

            LinearLayout rows = new LinearLayout(getContext());
            rows.setOrientation(LinearLayout.VERTICAL);
            Cell first = panelRow(rows, labelOf(item), COLOR_LABEL, 12);
            cell.value = first.value;
            if (item == StatsPrefs.Item.FPS) {
                cell.more.add(panelRow(rows, R.string.stats_label_fps_shown, COLOR_LABEL, 12));
            } else if (item == StatsPrefs.Item.PACING) {
                // The lock state and skipped frames, under the buffer
                Cell details = new Cell();
                details.value = text(11, COLOR_FAINT);
                details.value.setGravity(Gravity.END);
                details.value.setPadding(0, 0, 0, dp(2));
                rows.addView(details.value, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                cell.more.add(details);
            }
            cell.view = rows;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(2);
            panel.addView(rows, params);
        }
        return panel;
    }

    private Cell panelRow(LinearLayout parent, int labelRes, int labelColor, float valueSp) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(18));
        TextView label = text(11.5f, labelColor);
        label.setText(labelRes);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Cell cell = new Cell();
        cell.value = text(valueSp, COLOR_VALUE);
        cell.value.setPadding(dp(12), 0, 0, 0);
        cell.value.setGravity(Gravity.END);
        row.addView(cell.value);
        cell.view = row;
        parent.addView(row);
        return cell;
    }

    private static int labelOf(StatsPrefs.Item item) {
        switch (item) {
            case FPS:
                return R.string.stats_label_fps_received;
            case NETWORK:
                return R.string.stats_label_latency;
            case BANDWIDTH:
                return R.string.stats_label_bandwidth;
            case LOSS:
                return R.string.stats_label_loss;
            case HOST:
                return R.string.stats_label_host;
            case DECODE:
                return R.string.stats_label_decode;
            case PACING:
                return R.string.stats_label_pacing;
            case BATTERY:
                return R.string.stats_label_battery;
            default:
                return R.string.stats_label_clock;
        }
    }

    // ---- Filling in the numbers ----

    private static String number(float value, int decimals) {
        return String.format(Locale.getDefault(), "%." + decimals + "f", value);
    }

    // A value with its unit in the smaller gray, and an optional note after it
    private CharSequence value(String value, String unit, String note, float unitSp) {
        SpannableStringBuilder text = new SpannableStringBuilder(value);
        text.setSpan(new TypefaceSpan("sans-serif-medium"), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (unit != null) {
            int start = text.length();
            text.append(' ').append(unit);
            text.setSpan(new AbsoluteSizeSpan(Math.round(unitSp), true), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new ForegroundColorSpan(COLOR_LABEL), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (note != null) {
            int start = text.length();
            text.append("  ").append(note);
            text.setSpan(new AbsoluteSizeSpan(11, true), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new ForegroundColorSpan(COLOR_FAINT), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return text;
    }

    static String codecName(String decoder) {
        if (decoder == null) {
            return "";
        }
        String name = decoder.toLowerCase(Locale.ROOT);
        if (name.contains("pyrowave")) {
            return "PyroWave";
        } else if (name.contains("av1")) {
            return "AV1";
        } else if (name.contains("hevc") || name.contains("h265")) {
            return "HEVC";
        } else if (name.contains("avc") || name.contains("h264")) {
            return "H.264";
        }
        return decoder;
    }

    private int battery() {
        BatteryManager manager = (BatteryManager) getContext().getSystemService(Context.BATTERY_SERVICE);
        return manager != null ? manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) : -1;
    }

    private String clock() {
        return android.text.format.DateFormat.getTimeFormat(getContext()).format(new Date());
    }

    private void bind() {
        if (stats == null || card == null) {
            return;
        }
        PerfStats s = stats;
        float frameMs = s.streamFps > 0 ? 1000f / s.streamFps : Float.MAX_VALUE;
        boolean panel = style == StatsPrefs.Style.PANEL;
        boolean mini = style == StatsPrefs.Style.MINI;
        float unitSp = mini ? 9 : 11;

        for (Map.Entry<StatsPrefs.Item, Cell> entry : cells.entrySet()) {
            StatsPrefs.Item item = entry.getKey();
            Cell cell = entry.getValue();
            CharSequence text = null;
            boolean warn = false;
            switch (item) {
                case DECODER: {
                    String renderer = s.renderer != null && !s.renderer.isEmpty() ? s.renderer.split(" ")[0] : null;
                    if (panel) {
                        text = codecName(s.decoder) + (renderer != null ? " · " + renderer : "");
                        cell.more.get(0).value.setText(s.width + "×" + s.height + " · "
                                + number(s.streamFps, 0) + " FPS · " + (s.hdr ? "HDR" : "SDR"));
                    } else {
                        text = value(codecName(s.decoder), s.height + "p" + (s.hdr ? " HDR" : ""), null, unitSp);
                    }
                    break;
                }
                case FPS:
                    warn = s.streamFps > 0 && s.renderedFps < s.streamFps * 0.95f;
                    if (panel) {
                        text = value(number(s.receivedFps, 2), null, null, unitSp);
                        Cell shown = cell.more.get(0);
                        shown.value.setText(value(number(s.renderedFps, 2), null,
                                String.format(Locale.getDefault(), "%+.1f %%", s.fpsVariance), unitSp));
                        shown.value.setTextColor(warnings && warn ? COLOR_WARNING : COLOR_VALUE);
                        warn = false;
                    } else {
                        text = value(number(s.renderedFps, 0), "FPS", null, unitSp);
                    }
                    break;
                case NETWORK:
                    warn = s.rttMs > 30;
                    text = value(String.valueOf(s.rttMs), "ms", panel ? "± " + s.rttVarianceMs : null, unitSp);
                    break;
                case HOST:
                    if (s.hostLatencyAvg >= 0) {
                        warn = s.hostLatencyAvg > frameMs;
                        text = value(number(s.hostLatencyAvg, 1), "ms", panel
                                ? number(s.hostLatencyMin, 1) + "–" + number(s.hostLatencyMax, 1) : null, unitSp);
                    }
                    break;
                case DECODE:
                    warn = s.decodeMs > frameMs;
                    text = value(number(s.decodeMs, panel ? 2 : 1), "ms", null, unitSp);
                    break;
                case TOTAL:
                    text = value("≈ " + Math.round(s.totalLatencyMs()), "ms", null, unitSp);
                    break;
                case LOSS:
                    warn = s.droppedPercent > 1;
                    String partial = panel && s.partialPercent >= 0
                            ? getContext().getString(R.string.stats_label_partial, number(s.partialPercent, 2)) : null;
                    text = value(number(s.droppedPercent, s.droppedPercent < 10 ? 2 : 1), "%", partial, unitSp);
                    break;
                case BANDWIDTH:
                    if (s.bandwidthMbps >= 0) {
                        text = value(number(s.bandwidthMbps, panel || s.bandwidthMbps < 10 ? 1 : 0), "Mbps", null, unitSp);
                    }
                    break;
                case PACING:
                    if (panel) {
                        if (s.pacingHeadline != null || s.pacingDetails != null) {
                            text = value(s.pacingHeadline != null ? s.pacingHeadline : s.pacingDetails, null, null, unitSp);
                            String details = s.pacingHeadline != null ? s.pacingDetails : null;
                            cell.more.get(0).value.setText(details);
                            cell.more.get(0).value.setVisibility(details != null ? VISIBLE : GONE);
                        }
                    } else if (s.pacingHeadline != null) {
                        Matcher buffer = PACING_BUFFER.matcher(s.pacingHeadline);
                        if (buffer.find()) {
                            text = value(number(Float.parseFloat(buffer.group(1)), 1), "ms", null, unitSp);
                        }
                    }
                    break;
                case BATTERY: {
                    int level = battery();
                    if (level >= 0) {
                        warn = level <= 15;
                        text = value(String.valueOf(level), "%", null, unitSp);
                    }
                    break;
                }
                case CLOCK:
                    text = value(clock(), null, null, unitSp);
                    break;
            }

            // A value that is not known leaves its place
            cell.view.setVisibility(text != null ? VISIBLE : GONE);
            if (text == null) {
                continue;
            }
            cell.value.setText(text);
            cell.value.setTextColor(warnings && warn ? COLOR_WARNING : COLOR_VALUE);
            if (cell.icon != null) {
                cell.icon.setImageTintList(ColorStateList.valueOf(warnings && warn ? COLOR_WARNING : COLOR_LABEL));
            }
        }

        // Nothing to show: no card
        boolean anyShown = false;
        for (Cell cell : cells.values()) {
            anyShown |= cell.view.getVisibility() == VISIBLE;
        }
        card.setVisibility(anyShown ? VISIBLE : INVISIBLE);
    }

    private static PerfStats sample() {
        PerfStats s = new PerfStats();
        s.decoder = "PyroWave";
        s.renderer = "Vulkan";
        s.width = 1920;
        s.height = 1080;
        s.streamFps = 120;
        s.receivedFps = 119.98f;
        s.renderedFps = 119.97f;
        s.droppedPercent = 0.05f;
        s.partialPercent = 0.12f;
        s.rttMs = 3;
        s.rttVarianceMs = 1;
        s.bandwidthMbps = 182.4f;
        s.hostLatencyMin = 1.2f;
        s.hostLatencyMax = 3.8f;
        s.hostLatencyAvg = 2.1f;
        s.decodeMs = 1.84f;
        s.pacingHeadline = "4.2 ms buffer, 120.00 Hz";
        s.pacingDetails = "locked at 1 vsync/frame";
        return s;
    }

    /**
     * The values of the bar in lines: one while they fit in a part of the screen, otherwise as many lines
     * as needed, balanced so they come out about the same length. Each line is centered.
     */
    private static class BarLayout extends ViewGroup {
        private final int gap;
        private final int lineHeight;
        private final float maxFraction;
        private final List<int[]> lines = new ArrayList<>();
        private final List<Integer> lineWidths = new ArrayList<>();

        BarLayout(Context context, int gap, int lineHeight, float maxFraction) {
            super(context);
            this.gap = gap;
            this.lineHeight = lineHeight;
            this.maxFraction = maxFraction;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int available = MeasureSpec.getSize(widthMeasureSpec);
            int maxLine = Math.max(1, Math.round(available * maxFraction) - getPaddingLeft() - getPaddingRight());

            List<View> shown = new ArrayList<>();
            int total = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) {
                    continue;
                }
                child.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                        MeasureSpec.makeMeasureSpec(lineHeight, MeasureSpec.EXACTLY));
                total += (shown.isEmpty() ? 0 : gap) + child.getMeasuredWidth();
                shown.add(child);
            }

            // Lines of about the same length
            lines.clear();
            lineWidths.clear();
            int count = Math.max(1, (int) Math.ceil(total / (double) maxLine));
            float target = total / (float) count;
            int start = 0;
            int width = 0;
            for (int i = 0; i < shown.size(); i++) {
                int add = (i == start ? 0 : gap) + shown.get(i).getMeasuredWidth();
                boolean last = lines.size() == count - 1;
                if (i > start && !last && width + add / 2f > target) {
                    lines.add(new int[] {start, i});
                    lineWidths.add(width);
                    start = i;
                    width = shown.get(i).getMeasuredWidth();
                } else {
                    width += add;
                }
            }
            if (start < shown.size()) {
                lines.add(new int[] {start, shown.size()});
                lineWidths.add(width);
            }
            int widest = 0;
            for (int lineWidth : lineWidths) {
                widest = Math.max(widest, lineWidth);
            }
            shownViews = shown;
            setMeasuredDimension(widest + getPaddingLeft() + getPaddingRight(),
                    Math.max(1, lines.size()) * lineHeight + getPaddingTop() + getPaddingBottom());
        }

        private List<View> shownViews = new ArrayList<>();

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int inner = r - l - getPaddingLeft() - getPaddingRight();
            int top = getPaddingTop();
            for (int line = 0; line < lines.size(); line++) {
                int[] range = lines.get(line);
                int x = getPaddingLeft() + (inner - lineWidths.get(line)) / 2;
                for (int i = range[0]; i < range[1]; i++) {
                    View child = shownViews.get(i);
                    child.layout(x, top, x + child.getMeasuredWidth(), top + lineHeight);
                    x += child.getMeasuredWidth() + gap;
                }
                top += lineHeight;
            }
        }
    }

    /**
     * The panel is as wide as its longest row, between a minimum and a maximum: its rows stretch to put the
     * values on the right, and alone they would take all the width they are offered.
     */
    private static class PanelLayout extends LinearLayout {
        private final int minWidth;
        private final int maxWidth;

        PanelLayout(Context context, int minWidth, int maxWidth) {
            super(context);
            this.minWidth = minWidth;
            this.maxWidth = maxWidth;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), heightMeasureSpec);
            int width = Math.max(minWidth, Math.min(maxWidth, getMeasuredWidth()));
            if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
                width = Math.min(width, MeasureSpec.getSize(widthMeasureSpec));
            }
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);
        }
    }
}
