package com.limelight.ui.apollo.stats;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.limelight.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * How the stats overlay looks: its style, and for each style where it sits and what it shows.
 * General settings, not part of the profiles.
 */
public final class StatsPrefs {
    private StatsPrefs() {
    }

    // The row of the settings that opens the page
    public static final String PAGE_KEY = "stats_overlay";
    // The original switch: the overlay is shown at all
    public static final String KEY_SHOW = "checkbox_enable_perf_overlay";
    public static final String KEY_STYLE = "stats_style";
    public static final String KEY_OPACITY = "stats_opacity";
    public static final String KEY_WARNINGS = "stats_warnings";
    private static final String KEY_POSITION = "stats_position_";
    private static final String KEY_ITEMS = "stats_items_";
    private static final String KEY_ORDER = "stats_order_";

    public static final int OPACITY_MIN = 20;
    public static final int OPACITY_MAX = 100;
    public static final int OPACITY_STEP = 5;
    private static final int DEFAULT_OPACITY = 90;

    public enum Style {
        BAR("bar", R.string.stats_style_bar, Position.TOP_CENTER,
                Item.FPS, Item.NETWORK, Item.DECODE, Item.LOSS, Item.BANDWIDTH),
        PANEL("panel", R.string.stats_style_panel, Position.TOP_LEFT,
                Item.DECODER, Item.FPS, Item.NETWORK, Item.BANDWIDTH, Item.LOSS, Item.HOST, Item.DECODE,
                Item.PACING, Item.TOTAL),
        MINI("mini", R.string.stats_style_mini, Position.TOP_RIGHT,
                Item.FPS, Item.TOTAL);

        public final String value;
        public final int labelRes;
        final Position defaultPosition;
        final Item[] defaultItems;

        Style(String value, int labelRes, Position defaultPosition, Item... defaultItems) {
            this.value = value;
            this.labelRes = labelRes;
            this.defaultPosition = defaultPosition;
            this.defaultItems = defaultItems;
        }

        /** The items in the order this style shows them. */
        public Item[] order() {
            return this == PANEL ? PANEL_ORDER : Item.values();
        }

        static Style of(String value) {
            for (Style style : values()) {
                if (style.value.equals(value)) {
                    return style;
                }
            }
            return BAR;
        }
    }

    public enum Position {
        TOP_LEFT("top_left", R.string.stats_position_top_left),
        TOP_CENTER("top_center", R.string.stats_position_top_center),
        TOP_RIGHT("top_right", R.string.stats_position_top_right),
        BOTTOM_LEFT("bottom_left", R.string.stats_position_bottom_left),
        BOTTOM_CENTER("bottom_center", R.string.stats_position_bottom_center),
        BOTTOM_RIGHT("bottom_right", R.string.stats_position_bottom_right);

        public final String value;
        public final int labelRes;

        Position(String value, int labelRes) {
            this.value = value;
            this.labelRes = labelRes;
        }

        public boolean top() {
            return this == TOP_LEFT || this == TOP_CENTER || this == TOP_RIGHT;
        }

        public boolean left() {
            return this == TOP_LEFT || this == BOTTOM_LEFT;
        }

        public boolean right() {
            return this == TOP_RIGHT || this == BOTTOM_RIGHT;
        }
    }

    /** What the overlay can show, in the order of the bar. */
    public enum Item {
        DECODER("decoder", R.string.stats_item_decoder),
        FPS("fps", R.string.stats_item_fps),
        NETWORK("network", R.string.stats_item_network),
        HOST("host", R.string.stats_item_host),
        DECODE("decode", R.string.stats_item_decode),
        TOTAL("total", R.string.stats_item_total),
        LOSS("loss", R.string.stats_item_loss),
        BANDWIDTH("bandwidth", R.string.stats_item_bandwidth),
        PACING("pacing", R.string.stats_item_pacing),
        BATTERY("battery", R.string.stats_item_battery),
        CLOCK("clock", R.string.stats_item_clock);

        public final String value;
        public final int labelRes;

        Item(String value, int labelRes) {
            this.value = value;
            this.labelRes = labelRes;
        }

        static Item of(String value) {
            for (Item item : values()) {
                if (item.value.equals(value)) {
                    return item;
                }
            }
            return null;
        }
    }

    // The panel groups them: the stream, the network, the processing, the device
    private static final Item[] PANEL_ORDER = {Item.DECODER, Item.FPS, Item.NETWORK, Item.BANDWIDTH, Item.LOSS,
            Item.HOST, Item.DECODE, Item.PACING, Item.TOTAL, Item.BATTERY, Item.CLOCK};

    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static boolean isShown(Context context) {
        return prefs(context).getBoolean(KEY_SHOW, false);
    }

    public static void setShown(Context context, boolean shown) {
        prefs(context).edit().putBoolean(KEY_SHOW, shown).apply();
    }

    public static Style style(Context context) {
        return Style.of(prefs(context).getString(KEY_STYLE, Style.BAR.value));
    }

    public static void setStyle(Context context, Style style) {
        prefs(context).edit().putString(KEY_STYLE, style.value).apply();
    }

    public static Position position(Context context, Style style) {
        String value = prefs(context).getString(KEY_POSITION + style.value, null);
        for (Position position : Position.values()) {
            if (position.value.equals(value)) {
                return position;
            }
        }
        return style.defaultPosition;
    }

    public static void setPosition(Context context, Style style, Position position) {
        prefs(context).edit().putString(KEY_POSITION + style.value, position.value).apply();
    }

    /** Every item in the order the user gave the style, shown or not; new ones at the end. */
    public static List<Item> order(Context context, Style style) {
        List<Item> order = new ArrayList<>();
        String stored = prefs(context).getString(KEY_ORDER + style.value, "");
        for (String value : stored.split(",")) {
            Item item = Item.of(value);
            if (item != null && !order.contains(item)) {
                order.add(item);
            }
        }
        for (Item item : style.order()) {
            if (!order.contains(item)) {
                order.add(item);
            }
        }
        return order;
    }

    public static void setOrder(Context context, Style style, List<Item> order) {
        prefs(context).edit().putString(KEY_ORDER + style.value, join(order)).apply();
    }

    /** The items the style shows, in its order. */
    public static List<Item> items(Context context, Style style) {
        String stored = prefs(context).getString(KEY_ITEMS + style.value, null);
        List<String> values = new ArrayList<>();
        if (stored == null) {
            for (Item item : style.defaultItems) {
                values.add(item.value);
            }
        } else if (!stored.isEmpty()) {
            values.addAll(Arrays.asList(stored.split(",")));
        }
        List<Item> items = new ArrayList<>();
        for (Item item : order(context, style)) {
            if (values.contains(item.value)) {
                items.add(item);
            }
        }
        return items;
    }

    public static void setItem(Context context, Style style, Item item, boolean shown) {
        List<Item> items = items(context, style);
        items.remove(item);
        if (shown) {
            items.add(item);
        }
        prefs(context).edit().putString(KEY_ITEMS + style.value, join(items)).apply();
    }

    private static String join(List<Item> items) {
        StringBuilder value = new StringBuilder();
        for (Item each : items) {
            if (value.length() > 0) {
                value.append(',');
            }
            value.append(each.value);
        }
        return value.toString();
    }

    public static int opacity(Context context) {
        return prefs(context).getInt(KEY_OPACITY, DEFAULT_OPACITY);
    }

    public static void setOpacity(Context context, int opacity) {
        prefs(context).edit().putInt(KEY_OPACITY, opacity).apply();
    }

    public static boolean warnings(Context context) {
        return prefs(context).getBoolean(KEY_WARNINGS, true);
    }

    public static void setWarnings(Context context, boolean warnings) {
        prefs(context).edit().putBoolean(KEY_WARNINGS, warnings).apply();
    }

    /** Everything on the stats page back to its default; whether they show stays as it is. */
    public static void restoreDefaults(Context context) {
        SharedPreferences.Editor editor = prefs(context).edit()
                .remove(KEY_STYLE).remove(KEY_OPACITY).remove(KEY_WARNINGS);
        for (Style style : Style.values()) {
            editor.remove(KEY_POSITION + style.value).remove(KEY_ITEMS + style.value).remove(KEY_ORDER + style.value);
        }
        editor.apply();
    }

    /** What the settings row shows: the style, or that the stats are off. */
    public static String summary(Context context) {
        return context.getString(isShown(context) ? style(context).labelRes : R.string.stats_off);
    }
}
