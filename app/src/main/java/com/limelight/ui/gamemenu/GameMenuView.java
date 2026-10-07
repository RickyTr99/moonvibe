package com.limelight.ui.gamemenu;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.ui.apollo.ApolloUi;
import com.limelight.ui.apollo.ApolloWidgets;
import com.limelight.ui.apollo.BumperDrawable;
import com.limelight.ui.apollo.hints.InputMode;
import com.limelight.ui.apollo.StatusRowView;
import com.limelight.ui.apollo.settings.SliderView;
import com.limelight.ui.theme.ApolloBackground;
import com.limelight.ui.theme.ApolloColors;
import com.limelight.ui.theme.ApolloMotion;

import java.util.ArrayList;
import java.util.List;

/**
 * In-stream menu shown as a side panel over the left part of the stream.
 *
 * The first tab holds the quick actions: each one is bound to a gamepad button and runs as soon
 * as that button is pressed. The other tabs are lists, navigated with the D-pad and confirmed
 * with A. LB/RB switch tab and B closes the menu everywhere. Everything also works by touch.
 */
public class GameMenuView extends FrameLayout {
    private static final int PANEL_MIN_WIDTH_DP = 300;
    private static final int PANEL_MAX_WIDTH_DP = 360;
    private static final float PANEL_SCREEN_FRACTION = 0.42f;
    private static final long HOLD_TO_CONFIRM_MS = 1000;
    private static final float ANALOG_STICK_THRESHOLD = 0.5f;
    private static final long ANALOG_NAV_THROTTLE_MS = 200;
    private static final float PRESSED_SCALE = 0.96f;
    // No more than the side margin of the panel, so the sliding content never reaches the edge
    private static final int TAB_SLIDE_DP = 10;
    private static final int BUMPER_WIDTH_DP = 26;
    private static final int BUMPER_HEIGHT_DP = 19;
    private static final float SLIDER_STICK_DEADZONE = 0.2f;
    // Speed of a slider with the stick fully tilted
    private static final float SLIDER_STICK_PERCENT_PER_SECOND = 40f;
    private static final long SLIDER_STICK_TICK_MS = 16;
    // Room around the lone slider shown over the stream, and how long it stays after the last move
    private static final int SOLO_PADDING_DP = 12;
    private static final long SOLO_LINGER_MS = 700;

    private static final int COLOR_SCRIM = 0x52000000;
    private static final int COLOR_RIPPLE = 0x33FFFFFF;
    private static final ArgbEvaluator ARGB = new ArgbEvaluator();

    public interface Toggle {
        boolean isOn();
    }

    public interface Selection {
        int get();
        void set(int index);
    }

    /** An action bound to a gamepad button, shown in the quick actions tab. */
    public static class QuickAction {
        final int keyCode;
        final String label;
        final Runnable action;
        // Shown in the button, the full label stays for the confirmation dialog
        String shortLabel;
        boolean keepOpen;
        boolean danger;
        boolean holdToConfirm;
        Toggle active;

        public QuickAction(int keyCode, String label, Runnable action) {
            this.keyCode = keyCode;
            this.label = label;
            this.action = action;
        }

        public QuickAction shortLabel(String shortLabel) {
            this.shortLabel = shortLabel;
            return this;
        }

        // The menu stays open after the action and shows the new state
        public QuickAction keepOpen() {
            keepOpen = true;
            return this;
        }

        // The tile is highlighted while this is on
        public QuickAction active(Toggle active) {
            this.active = active;
            return this;
        }

        // Runs only after holding the button, or after a confirmation when tapped
        public QuickAction holdToConfirm() {
            holdToConfirm = true;
            danger = true;
            return this;
        }
    }

    /**
     * A slider at the top of the quick actions tab, moved by touch or by a gamepad stick,
     * with a button on its left that toggles a state (automatic brightness, mute).
     */
    public static class QuickSlider {
        public interface Value {
            // 0-100
            int get();
            void set(int value);
        }

        final boolean rightStick;
        final int iconResId;
        final int activeIconResId;
        final String name;
        final String buttonDescription;
        final int min;
        final Value value;
        final Toggle active;
        final Runnable toggle;
        // Shown instead of the value while the button is on
        String activeText;
        // While it is being adjusted the menu steps aside and leaves only this slider over the stream
        boolean solo;

        public QuickSlider(boolean rightStick, int iconResId, int activeIconResId, String name, String buttonDescription,
                           int min, Value value, Toggle active, Runnable toggle) {
            this.rightStick = rightStick;
            this.iconResId = iconResId;
            this.activeIconResId = activeIconResId;
            this.name = name;
            this.buttonDescription = buttonDescription;
            this.min = min;
            this.value = value;
            this.active = active;
            this.toggle = toggle;
        }

        public QuickSlider activeText(String activeText) {
            this.activeText = activeText;
            return this;
        }

        /** For the brightness: the stream shows without the menu and its dimming while the slider moves. */
        public QuickSlider soloWhileAdjusting() {
            this.solo = true;
            return this;
        }
    }

    /** A row of a list tab. */
    public static class Item {
        final int iconResId;
        final String label;
        final Runnable action;
        final boolean header;
        boolean keepOpen;
        boolean accent;
        Toggle toggle;
        String trailingText;
        String[] options;
        Selection selection;
        Runnable longPressAction;
        QuickSlider.Value sliderValue;
        int sliderMin, sliderMax, sliderStep;
        String sliderSuffix;

        private Item(int iconResId, String label, Runnable action, boolean header) {
            this.iconResId = iconResId;
            this.label = label;
            this.action = action;
            this.header = header;
        }

        public static Item header(String label) {
            return new Item(0, label, null, true);
        }

        public static Item action(int iconResId, String label, Runnable action) {
            return new Item(iconResId, label, action, false);
        }

        // A switch showing the state, the action toggles it
        public static Item toggle(int iconResId, String label, Toggle toggle, Runnable action) {
            Item item = new Item(iconResId, label, action, false);
            item.toggle = toggle;
            item.keepOpen = true;
            return item;
        }

        // A dropdown to pick one of the options
        public static Item dropdown(int iconResId, String label, String[] options, Selection selection) {
            Item item = new Item(iconResId, label, null, false);
            item.options = options;
            item.selection = selection;
            item.keepOpen = true;
            return item;
        }

        // A slider in the row: touch drags it, left and right step it on a gamepad
        public static Item slider(int iconResId, String label, int min, int max, int step, String suffix,
                                  QuickSlider.Value value) {
            Item item = new Item(iconResId, label, null, false);
            item.sliderValue = value;
            item.sliderMin = min;
            item.sliderMax = max;
            item.sliderStep = step;
            item.sliderSuffix = suffix;
            item.keepOpen = true;
            return item;
        }

        public Item keepOpen() {
            keepOpen = true;
            return this;
        }

        public Item accent() {
            accent = true;
            return this;
        }

        public Item trailingText(String text) {
            trailingText = text;
            return this;
        }

        public Item onLongPress(Runnable action) {
            longPressAction = action;
            return this;
        }
    }

    public static class Tab {
        final String title;
        final List<Item> items;

        public Tab(String title, List<Item> items) {
            this.title = title;
            this.items = items;
        }
    }

    public interface Listener {
        List<QuickAction> buildQuickActions();
        List<QuickSlider> buildQuickSliders();
        // The tabs after the quick actions one
        List<Tab> buildTabs();
        void onMenuClosed();
    }

    // A quick action tile and the views that follow its state
    private static class Tile {
        final QuickAction action;
        final View view;
        final GradientDrawable background;
        final TextView label;
        boolean active;

        Tile(QuickAction action, View view, GradientDrawable background, TextView label, boolean active) {
            this.action = action;
            this.view = view;
            this.background = background;
            this.label = label;
            this.active = active;
        }
    }

    // A quick slider and the views that follow its value
    private static class SliderRow {
        final QuickSlider slider;
        View container;
        final SliderView view;
        final TextView value;
        final ImageView icon;
        final GradientDrawable buttonBackground;
        boolean active;
        // Kept as a float while a stick moves it, so a slight tilt still adds up
        float position;
        // Last stick tilt, -1 to 1
        float stick;
        boolean moving;

        SliderRow(QuickSlider slider, SliderView view, TextView value, ImageView icon, GradientDrawable buttonBackground) {
            this.slider = slider;
            this.view = view;
            this.value = value;
            this.icon = icon;
            this.buttonBackground = buttonBackground;
        }
    }

    // A list row and the views that follow the selection and the state
    private static class Row {
        final Item item;
        final View view;
        final GradientDrawable background;
        final TextView label;
        final ImageView icon;
        ApolloWidgets.SwitchView toggle;
        TextView value;
        SliderView slider;
        int backgroundColor = Color.TRANSPARENT;
        int labelColor;
        int iconColor;
        ValueAnimator animator;

        Row(Item item, View view, GradientDrawable background, TextView label, ImageView icon) {
            this.item = item;
            this.view = view;
            this.background = background;
            this.label = label;
            this.icon = icon;
        }
    }

    private final float density;
    private final ApolloColors colors;
    private Listener listener;
    private boolean flipFaceButtons;

    private View scrim;
    private FrameLayout panelFrame;
    private LinearLayout panel;
    private StatusRowView statusRow;
    private LinearLayout tabsRow;
    private View tabIndicator;
    private final List<TextView> tabViews = new ArrayList<>();
    private ScrollView scrollView;
    private LinearLayout content;
    private LinearLayout hintRow;
    private LinearLayout dropdownCard;
    private FrameLayout dialogLayer;
    private View dialogCard;
    private TextView dialogTitle;
    private Runnable dialogConfirm;

    private List<QuickAction> quickActions = new ArrayList<>();
    private List<QuickSlider> quickSliders = new ArrayList<>();
    private List<Tab> tabs = new ArrayList<>();
    private final List<Tile> tiles = new ArrayList<>();
    private final List<SliderRow> sliderRows = new ArrayList<>();
    private long lastStickTick;
    private boolean stickTickerRunning;
    // The lone slider shown over the stream while a solo slider is adjusted
    private LinearLayout soloCard;
    private ImageView soloIcon;
    private SliderView soloSlider;
    private TextView soloValue;
    private SliderRow soloRow;
    private boolean soloExitPending;
    private final Runnable soloExit = this::exitSolo;
    private final Runnable stickTicker = this::moveSlidersWithSticks;
    private final List<Row> rows = new ArrayList<>();
    private int currentTab = 0;
    private int selectedRow = 0;
    // The list selection is only drawn while the menu is driven by a gamepad
    private boolean gamepadMode;
    private boolean padConnected = true;
    // The A/B badges of the confirm dialog, built once and shown only with a gamepad
    private final List<View> dialogBadges = new ArrayList<>();
    private final List<TextView> dialogLabels = new ArrayList<>();
    // Set while the close animation runs: the menu no longer takes input
    private boolean closing;

    private Row dropdownRow;
    private int dropdownIndex;
    private int dropdownGeneration;
    private final List<View> dropdownOptions = new ArrayList<>();

    private QuickAction holdingAction;
    private Tile holdTile;
    private ApolloWidgets.RingView holdRing;
    private ValueAnimator holdAnimator;
    private boolean holdCompleted;

    private int lastHatX, lastHatY;
    private long lastAnalogNavTime;

    public GameMenuView(Context context) {
        this(context, null);
    }

    public GameMenuView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GameMenuView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = context.getResources().getDisplayMetrics().density;
        colors = ApolloColors.dark(context);
        init(context);
    }

    private int dp(float value) {
        return (int) (value * density + 0.5f);
    }

    private String str(int id) {
        return getContext().getString(id);
    }

    private void init(Context context) {
        // Never take focus away from the stream view, key events are routed here by Game
        setFocusable(false);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        setVisibility(GONE);

        scrim = new View(context);
        scrim.setBackgroundColor(COLOR_SCRIM);
        scrim.setOnClickListener(v -> close());
        addView(scrim, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        panelFrame = new FrameLayout(context);
        panelFrame.setClickable(true);
        addView(panelFrame, new LayoutParams(dp(PANEL_MAX_WIDTH_DP), LayoutParams.MATCH_PARENT, Gravity.START));

        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        // The same shaded background as the app screens, rounded on the right: the outline reaches
        // past the left edge so only the right corners show
        panel.setBackground(new ApolloBackground(colors));
        int radius = dp(24);
        panel.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(-radius, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        panel.setClipToOutline(true);
        panelFrame.addView(panel, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        statusRow = new StatusRowView(context, colors, true);
        statusRow.setPadding(dp(10), 0, dp(8), 0);
        panel.addView(statusRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(36)));

        tabsRow = new LinearLayout(context);
        tabsRow.setOrientation(LinearLayout.HORIZONTAL);
        tabsRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tabsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tabsParams.topMargin = dp(8);
        panel.addView(tabsRow, tabsParams);

        scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(OVER_SCROLL_NEVER);
        scrollView.setClipToPadding(false);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        scrollParams.topMargin = dp(8);
        panel.addView(scrollView, scrollParams);
        setPanelPadding(dp(10));

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        hintRow = new LinearLayout(context);
        hintRow.setOrientation(LinearLayout.HORIZONTAL);
        hintRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        hintRow.setPadding(0, 0, dp(6), 0);
        panel.addView(hintRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(30)));

        dropdownCard = new LinearLayout(context);
        dropdownCard.setOrientation(LinearLayout.VERTICAL);
        dropdownCard.setPadding(0, dp(6), 0, dp(6));
        dropdownCard.setBackground(roundRect(colors.surfaceContainerHigh, dp(16)));
        dropdownCard.setClipToOutline(true);
        dropdownCard.setClickable(true);
        dropdownCard.setVisibility(GONE);
        panelFrame.addView(dropdownCard, new LayoutParams(dp(200), LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END));

        addView(createDialogLayer(context), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        createSoloCard(context);
    }

    // ---- Building blocks ----

    private GradientDrawable roundRect(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private RippleDrawable ripple(GradientDrawable content, float radius) {
        GradientDrawable mask = roundRect(Color.WHITE, radius);
        return new RippleDrawable(ColorStateList.valueOf(COLOR_RIPPLE), content, mask);
    }

    // Corners of a button in a connected group: round on the outer side of the first and last one
    private float[] groupRadii(int index, int count) {
        float outer = dp(20), inner = dp(8);
        float left = index == 0 ? outer : inner;
        float right = index == count - 1 ? outer : inner;
        return new float[] {left, left, right, right, right, right, left, left};
    }

    private RippleDrawable ripple(GradientDrawable content, float[] radii) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadii(radii);
        return new RippleDrawable(ColorStateList.valueOf(COLOR_RIPPLE), content, mask);
    }

    private TextView text(String value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        return view;
    }

    private ImageView icon(int resId, int color, int sizeDp) {
        ImageView view = new ImageView(getContext());
        view.setImageResource(resId);
        view.setImageTintList(ColorStateList.valueOf(color));
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return view;
    }

    private TextView sectionHeader(String label) {
        TextView header = text(label.toUpperCase(), 11, colors.primary, true);
        header.setLetterSpacing(0.08f);
        header.setPadding(dp(8), dp(8), dp(8), dp(6));
        return header;
    }

    private static String badgeLabel(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A: return "A";
            case KeyEvent.KEYCODE_BUTTON_B: return "B";
            case KeyEvent.KEYCODE_BUTTON_X: return "X";
            case KeyEvent.KEYCODE_BUTTON_Y: return "Y";
            case KeyEvent.KEYCODE_BUTTON_START: return "Start";
            case KeyEvent.KEYCODE_BUTTON_SELECT: return "Select";
            case KeyEvent.KEYCODE_DPAD_UP: return "▲";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "▼";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "◀";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "▶";
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return "LS";
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return "RS";
            default: return "?";
        }
    }

    // What a quick action does, drawn in its tile instead of the button when no gamepad is connected
    private static int actionIcon(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A: return R.drawable.ic_menu_logout;
            case KeyEvent.KEYCODE_BUTTON_X: return R.drawable.ic_apollo_power;
            case KeyEvent.KEYCODE_BUTTON_Y: return R.drawable.ic_menu_perf;
            case KeyEvent.KEYCODE_BUTTON_START: return R.drawable.ic_menu_guide;
            case KeyEvent.KEYCODE_BUTTON_SELECT: return R.drawable.ic_overlay_windows;
            case KeyEvent.KEYCODE_DPAD_UP: return R.drawable.ic_apollo_cat_keyboard;
            case KeyEvent.KEYCODE_DPAD_DOWN: return R.drawable.ic_menu_pckeyboard;
            case KeyEvent.KEYCODE_DPAD_LEFT: return R.drawable.ic_menu_window;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return R.drawable.ic_apollo_desktop;
            default: return R.drawable.ic_apollo_play;
        }
    }

    // The badge of a gamepad button: Xbox colors for the face buttons, neutral for the others
    private TextView buttonBadge(int keyCode) {
        String label = badgeLabel(keyCode);
        int background = colors.surfaceContainerHighest;
        int foreground = colors.onSurface;

        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A: background = 0xFF5DBB3C; foreground = 0xFF0B1A05; break;
            case KeyEvent.KEYCODE_BUTTON_B: background = 0xFFE0453A; foreground = Color.WHITE; break;
            case KeyEvent.KEYCODE_BUTTON_X: background = 0xFF3B7BE0; foreground = Color.WHITE; break;
            case KeyEvent.KEYCODE_BUTTON_Y: background = 0xFFF0B428; foreground = 0xFF231A00; break;
        }

        boolean wide = label.length() > 1;
        TextView badge = text(label, wide ? 9 : 10, foreground, true);
        badge.setGravity(Gravity.CENTER);
        badge.setIncludeFontPadding(false);
        badge.setMinWidth(dp(20));
        badge.setPadding(wide ? dp(5) : 0, 0, wide ? dp(5) : 0, 0);
        badge.setBackground(roundRect(background, isDpad(keyCode) ? dp(6) : dp(10)));
        return badge;
    }

    // The 17 dp badge of the hints and dialog buttons: a one-letter button is round, as wide as it is tall
    private LinearLayout.LayoutParams smallBadgeParams(TextView badge) {
        boolean round = badge.getText().length() == 1;
        if (round) {
            badge.setMinWidth(dp(17));
        }
        return new LinearLayout.LayoutParams(round ? dp(17) : LinearLayout.LayoutParams.WRAP_CONTENT, dp(17));
    }

    private View hint(int keyCode, String label) {
        LinearLayout hint = new LinearLayout(getContext());
        hint.setOrientation(LinearLayout.HORIZONTAL);
        hint.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = buttonBadge(keyCode);
        hint.addView(badge, smallBadgeParams(badge));
        TextView text = text(label, 12, colors.onSurfaceVariant, false);
        text.setPadding(dp(6), 0, 0, 0);
        hint.addView(text);
        return hint;
    }

    // Shaped like the shoulder button, as in the top bar of the app
    private TextView shoulderChip(String label, int direction) {
        TextView chip = text(label, 9.5f, colors.onSurfaceVariant, true);
        chip.setGravity(Gravity.CENTER);
        chip.setIncludeFontPadding(false);
        chip.setBackground(new BumperDrawable(colors.surfaceContainerHighest, direction < 0, density));
        // The letters a little low, where the shape is fullest
        chip.setPadding(0, dp(1), 0, 0);
        addPressFeedback(chip);
        chip.setOnClickListener(v -> {
            gamepadMode = false;
            switchTab(direction);
        });
        return chip;
    }

    // Scales the view down while it is touched, like a Material pressed state
    @SuppressLint("ClickableViewAccessibility")
    private void addPressFeedback(View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(PRESSED_SCALE).scaleY(PRESSED_SCALE)
                            .setDuration(ApolloMotion.SHORT).setInterpolator(ApolloMotion.STANDARD).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1).scaleY(1)
                            .setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD).start();
                    break;
            }
            return false;
        });
    }

    // The same feedback for a press made with a gamepad button
    private void pulse(View view) {
        view.animate().scaleX(PRESSED_SCALE).scaleY(PRESSED_SCALE)
                .setDuration(ApolloMotion.SHORT / 2).setInterpolator(ApolloMotion.STANDARD)
                .withEndAction(() -> view.animate().scaleX(1).scaleY(1)
                        .setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD).start())
                .start();
    }

    private void shake(View view) {
        float d = dp(6);
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 0, d, -d, d * 0.6f, -d * 0.6f, d * 0.3f, 0);
        animator.setDuration(ApolloMotion.LONG);
        animator.start();
    }

    private View createDialogLayer(Context context) {
        dialogLayer = new FrameLayout(context);
        dialogLayer.setBackgroundColor(0x80000000);
        dialogLayer.setClickable(true);
        dialogLayer.setVisibility(GONE);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(24), dp(24), dp(24), dp(16));
        card.setBackground(roundRect(colors.surfaceContainerHigh, dp(28)));
        dialogLayer.addView(card, new LayoutParams(dp(312), LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        dialogCard = card;

        dialogTitle = text("", 22, colors.onSurface, false);
        card.addView(dialogTitle);

        TextView message = text(str(R.string.game_menu_quit_confirm_message), 14, colors.onSurfaceVariant, false);
        message.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.topMargin = dp(12);
        card.addView(message, messageParams);

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonsParams.topMargin = dp(20);
        card.addView(buttons, buttonsParams);

        View cancel = dialogButton(KeyEvent.KEYCODE_BUTTON_B, str(R.string.game_menu_cancel), false);
        cancel.setOnClickListener(v -> dismissDialog());
        buttons.addView(cancel);

        View confirm = dialogButton(KeyEvent.KEYCODE_BUTTON_A, str(R.string.game_menu_quit_confirm), true);
        confirm.setOnClickListener(v -> confirmDialog());
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        confirmParams.leftMargin = dp(8);
        buttons.addView(confirm, confirmParams);

        return dialogLayer;
    }

    private View dialogButton(int keyCode, String label, boolean filled) {
        LinearLayout button = new LinearLayout(getContext());
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setMinimumHeight(dp(40));
        button.setPadding(dp(14), 0, dp(16), 0);
        button.setBackground(ripple(roundRect(filled ? colors.primary : Color.TRANSPARENT, dp(20)), dp(20)));
        TextView badge = buttonBadge(keyCode);
        button.addView(badge, smallBadgeParams(badge));
        dialogBadges.add(badge);
        TextView text = text(label, 14, filled ? colors.onPrimary : colors.primary, true);
        button.addView(text);
        dialogLabels.add(text);
        updateDialogBadges();
        addPressFeedback(button);
        return button;
    }

    private void updateDialogBadges() {
        for (View badge : dialogBadges) {
            badge.setVisibility(padConnected ? VISIBLE : GONE);
        }
        for (TextView label : dialogLabels) {
            label.setPadding(padConnected ? dp(8) : 0, 0, 0, 0);
        }
    }

    // ---- Public API ----

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setFlipFaceButtons(boolean flip) {
        this.flipFaceButtons = flip;
    }

    public boolean isOpen() {
        return getVisibility() == VISIBLE && !closing;
    }

    // fromGamepad shows the list selection right away, for menus opened with a gamepad button
    public void show(boolean fromGamepad) {
        if (listener == null) {
            return;
        }

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int width = Math.min(dp(PANEL_MAX_WIDTH_DP),
                Math.max(dp(PANEL_MIN_WIDTH_DP), (int) (screenWidth * PANEL_SCREEN_FRACTION)));
        width = Math.min(width, screenWidth);
        panelFrame.getLayoutParams().width = width;

        closing = false;
        resetSolo();
        // Without a gamepad the button symbols mean nothing: icons stand in for them
        padConnected = InputMode.isGamepadConnected();
        updateDialogBadges();
        gamepadMode = fromGamepad;
        currentTab = 0;
        selectedRow = 0;
        lastHatX = lastHatY = 0;
        hideDropdown(false);
        dialogLayer.setVisibility(GONE);
        rebuild();
        scrollView.scrollTo(0, 0);
        // The quick actions tab does not go through updateSelection: set the hints for the opening here.
        // Without a gamepad the row gives its height back to the content
        hintRow.animate().cancel();
        hintRow.setAlpha(gamepadMode && padConnected ? 1f : 0f);
        hintRow.setVisibility(padConnected ? VISIBLE : GONE);

        // The on-screen gamepad and keyboard are added to the same parent later, stay above them
        bringToFront();
        setVisibility(VISIBLE);

        // Slide in from the left edge
        panelFrame.animate().cancel();
        scrim.animate().cancel();
        panelFrame.setTranslationX(-width);
        scrim.setAlpha(0);
        panelFrame.animate().translationX(0)
                .setDuration(ApolloMotion.LONG).setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE).start();
        scrim.animate().alpha(1)
                .setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD).start();

        statusRow.start();
    }

    public void close() {
        if (!isOpen()) {
            return;
        }
        closing = true;
        cancelHold(false);
        stopSliderSticks();
        // Closed while only the slider shows: it fades with the dimming, the hidden panel needs no slide
        if (soloRow != null) {
            removeCallbacks(soloExit);
            soloExitPending = false;
            soloRow = null;
            soloCard.animate().cancel();
            soloCard.animate().alpha(0).setDuration(ApolloMotion.SHORT).setInterpolator(ApolloMotion.STANDARD).start();
        }
        hideDropdown(false);
        dialogConfirm = null;
        dialogLayer.animate().cancel();
        dialogLayer.setVisibility(GONE);
        statusRow.stop();
        if (listener != null) {
            listener.onMenuClosed();
        }

        panelFrame.animate().translationX(-panelFrame.getWidth())
                .setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.EMPHASIZED_ACCELERATE)
                .withEndAction(() -> {
                    if (closing) {
                        setVisibility(GONE);
                        closing = false;
                    }
                })
                .start();
        scrim.animate().alpha(0)
                .setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD).start();
    }

    // Rebuilds the content after a change of the items, keeping the tab and the selection
    public void refresh() {
        if (isOpen()) {
            int scroll = scrollView.getScrollY();
            rebuild();
            scrollView.scrollTo(0, scroll);
            // An action may have just added the on-screen gamepad on top of us
            bringToFront();
        }
    }

    // ---- Content ----

    private int tabCount() {
        return 1 + tabs.size();
    }

    private void rebuild() {
        quickActions = listener.buildQuickActions();
        quickSliders = listener.buildQuickSliders();
        tabs = listener.buildTabs();
        if (currentTab >= tabCount()) {
            currentTab = 0;
        }

        buildTabsRow();
        rebuildContent();
    }

    private void rebuildContent() {
        content.removeAllViews();
        rows.clear();
        tiles.clear();
        stopSliderSticks();
        sliderRows.clear();
        holdRing = null;
        holdTile = null;
        if (currentTab == 0) {
            buildQuickActionsContent();
        } else {
            buildListContent(tabs.get(currentTab - 1));
        }

        buildHintRow();
    }

    private void buildTabsRow() {
        tabsRow.removeAllViews();
        tabViews.clear();

        // LB and RB mean something only with a gamepad, like in the top bar of the app
        if (padConnected) {
            tabsRow.addView(shoulderChip("LB", -1), new LinearLayout.LayoutParams(dp(BUMPER_WIDTH_DP), dp(BUMPER_HEIGHT_DP)));
        }

        // The selected tab pill is a separate view that slides from tab to tab
        FrameLayout segmentsFrame = new FrameLayout(getContext());
        segmentsFrame.setPadding(dp(4), dp(4), dp(4), dp(4));
        segmentsFrame.setBackground(roundRect(colors.surfaceContainer, dp(22)));
        LinearLayout.LayoutParams segmentsParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        segmentsParams.leftMargin = padConnected ? dp(6) : 0;
        segmentsParams.rightMargin = padConnected ? dp(6) : 0;
        tabsRow.addView(segmentsFrame, segmentsParams);

        tabIndicator = new View(getContext());
        tabIndicator.setBackground(roundRect(colors.secondaryContainer, dp(17)));
        segmentsFrame.addView(tabIndicator, new LayoutParams(0, dp(34)));

        LinearLayout segments = new LinearLayout(getContext());
        segments.setOrientation(LinearLayout.HORIZONTAL);
        segmentsFrame.addView(segments, new LayoutParams(LayoutParams.MATCH_PARENT, dp(34)));

        for (int i = 0; i < tabCount(); i++) {
            final int index = i;
            String title = i == 0 ? str(R.string.game_menu_tab_quick) : tabs.get(i - 1).title;

            TextView tab = text(title, 12.5f, colors.onSurfaceVariant, true);
            tab.setGravity(Gravity.CENTER);
            tab.setSingleLine(true);
            tab.setBackground(ripple(roundRect(Color.TRANSPARENT, dp(17)), dp(17)));
            tab.setOnClickListener(v -> {
                gamepadMode = false;
                selectTab(index);
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1);
            if (i > 0) {
                params.leftMargin = dp(4);
            }
            segments.addView(tab, params);
            tabViews.add(tab);
        }

        if (padConnected) {
            tabsRow.addView(shoulderChip("RB", 1), new LinearLayout.LayoutParams(dp(BUMPER_WIDTH_DP), dp(BUMPER_HEIGHT_DP)));
        }

        updateTabs(false);
    }

    private void updateTabs(boolean animate) {
        for (int i = 0; i < tabViews.size(); i++) {
            tabViews.get(i).setTextColor(i == currentTab ? colors.onSecondaryContainer : colors.onSurfaceVariant);
        }

        tabIndicator.post(() -> placeTabIndicator(animate));
    }

    private void placeTabIndicator(boolean animate) {
        if (currentTab >= tabViews.size()) {
            return;
        }
        View tab = tabViews.get(currentTab);
        if (tab.getWidth() == 0) {
            // First opening: the tabs are not measured yet, place the pill once they are
            tab.addOnLayoutChangeListener(new OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    v.removeOnLayoutChangeListener(this);
                    tabIndicator.post(() -> placeTabIndicator(false));
                }
            });
            return;
        }
        if (tabIndicator.getLayoutParams().width != tab.getWidth()) {
            tabIndicator.getLayoutParams().width = tab.getWidth();
            tabIndicator.requestLayout();
        }
        tabIndicator.animate().cancel();
        if (animate) {
            tabIndicator.animate().translationX(tab.getLeft())
                    .setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD).start();
        } else {
            tabIndicator.setTranslationX(tab.getLeft());
        }
    }

    private static boolean isDpad(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    private void buildQuickActionsContent() {
        for (int i = 0; i < quickSliders.size(); i++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(40));
            params.bottomMargin = dp(i == quickSliders.size() - 1 ? 12 : 4);
            content.addView(createSliderRow(quickSliders.get(i)), params);
        }

        List<QuickAction> buttons = new ArrayList<>();
        List<QuickAction> dpad = new ArrayList<>();
        for (QuickAction action : quickActions) {
            (isDpad(action.keyCode) ? dpad : buttons).add(action);
        }
        addButtonGroup(buttons);
        addButtonGroup(dpad);
    }

    // ---- Quick sliders ----

    private static String stickLetter(QuickSlider slider) {
        return slider.rightStick ? "R" : "L";
    }

    private View createSliderRow(QuickSlider slider) {
        LinearLayout view = new LinearLayout(getContext());
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout button = new FrameLayout(getContext());
        GradientDrawable buttonBackground = roundRect(colors.surfaceContainerHigh, dp(20));
        button.setBackground(ripple(buttonBackground, dp(20)));
        button.setContentDescription(slider.buttonDescription);
        ImageView icon = icon(slider.iconResId, colors.onSurfaceVariant, 20);
        button.addView(icon, new LayoutParams(dp(20), dp(20), Gravity.CENTER));
        view.addView(button, new LinearLayout.LayoutParams(dp(40), dp(40)));

        SliderView sliderView = new SliderView(getContext(), colors);
        // With a gamepad the handle is the stick that moves it
        sliderView.setStickHandle(padConnected ? stickLetter(slider) : null);
        sliderView.setRange(slider.min, 100, 1);
        LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(0, dp(40), 1);
        sliderParams.leftMargin = dp(10);
        view.addView(sliderView, sliderParams);

        TextView value = text("", 13, colors.onSurface, true);
        value.setGravity(Gravity.END);
        value.setSingleLine(true);
        view.addView(value, new LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.WRAP_CONTENT));

        SliderRow row = new SliderRow(slider, sliderView, value, icon, buttonBackground);
        row.container = view;
        sliderRows.add(row);
        updateSliderRow(row, false);

        addPressFeedback(button);
        button.setOnClickListener(v -> {
            gamepadMode = false;
            slider.toggle.run();
            updateSliderRow(row, true);
        });
        sliderView.setListener(new SliderView.Listener() {
            @Override
            public void onSliderMoved(int value) {
                setSliderValue(row, value);
                holdSolo(row);
            }

            @Override
            public void onSliderReleased(int value) {
                setSliderValue(row, value);
                releaseSolo();
            }
        });
        return view;
    }

    private void setSliderValue(SliderRow row, int value) {
        row.slider.value.set(value);
        row.view.setValue(value);
        updateSliderButton(row, true);
        updateSliderText(row, value);
        if (row == soloRow) {
            soloSlider.setValue(value);
            soloValue.setText(row.value.getText());
        }
    }

    // ---- Solo slider ----

    // The same layout as a slider row (button, slider, value) on a card, so laid over the row the track
    // stays right under the finger
    private void createSoloCard(Context context) {
        soloCard = new LinearLayout(context);
        soloCard.setOrientation(LinearLayout.HORIZONTAL);
        soloCard.setGravity(Gravity.CENTER_VERTICAL);
        soloCard.setPadding(dp(SOLO_PADDING_DP), 0, dp(SOLO_PADDING_DP), 0);
        soloCard.setBackground(roundRect(colors.surfaceContainer, dp(28)));
        soloCard.setAlpha(0);
        soloCard.setVisibility(GONE);

        FrameLayout iconFrame = new FrameLayout(context);
        soloIcon = icon(R.drawable.ic_menu_brightness_auto, colors.onSurfaceVariant, 20);
        iconFrame.addView(soloIcon, new LayoutParams(dp(20), dp(20), Gravity.CENTER));
        soloCard.addView(iconFrame, new LinearLayout.LayoutParams(dp(40), dp(40)));

        soloSlider = new SliderView(context, colors);
        // Only shows the value: the finger still drags the slider of the menu under it
        soloSlider.setEnabled(false);
        LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(0, dp(40), 1);
        sliderParams.leftMargin = dp(10);
        soloCard.addView(soloSlider, sliderParams);

        soloValue = text("", 13, colors.onSurface, true);
        soloValue.setGravity(Gravity.END);
        soloValue.setSingleLine(true);
        soloCard.addView(soloValue, new LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.WRAP_CONTENT));
        addView(soloCard, new LayoutParams(LayoutParams.WRAP_CONTENT, dp(40 + SOLO_PADDING_DP * 2)));
    }

    // The slider is being moved: the menu and its dimming fade out, the lone slider takes the row's place
    private void holdSolo(SliderRow row) {
        if (!row.slider.solo || !isOpen()) {
            return;
        }
        removeCallbacks(soloExit);
        soloExitPending = false;
        if (soloRow == row) {
            return;
        }
        soloRow = row;

        int[] rowLocation = new int[2], ownLocation = new int[2];
        row.container.getLocationInWindow(rowLocation);
        getLocationInWindow(ownLocation);
        LayoutParams params = (LayoutParams) soloCard.getLayoutParams();
        params.width = row.container.getWidth() + dp(SOLO_PADDING_DP) * 2;
        params.leftMargin = rowLocation[0] - ownLocation[0] - dp(SOLO_PADDING_DP);
        params.topMargin = rowLocation[1] - ownLocation[1] + (row.container.getHeight() - dp(40)) / 2 - dp(SOLO_PADDING_DP);
        soloCard.setLayoutParams(params);
        soloIcon.setImageResource(row.slider.active.isOn() ? row.slider.activeIconResId : row.slider.iconResId);
        soloSlider.setRange(row.slider.min, 100, 1);
        soloSlider.setValue(row.slider.value.get());
        soloValue.setText(row.value.getText());
        soloSlider.setStickHandle(padConnected ? stickLetter(row.slider) : null);

        soloCard.animate().cancel();
        panelFrame.animate().cancel();
        scrim.animate().cancel();
        soloCard.bringToFront();
        soloCard.setVisibility(VISIBLE);
        soloCard.animate().alpha(1).setDuration(ApolloMotion.SHORT).setInterpolator(ApolloMotion.STANDARD).start();
        panelFrame.animate().alpha(0).setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD).start();
        scrim.animate().alpha(0).setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD).start();
    }

    // Moving has stopped: the menu comes back after a short look at the result
    private void releaseSolo() {
        if (soloRow != null && !soloExitPending) {
            soloExitPending = true;
            postDelayed(soloExit, SOLO_LINGER_MS);
        }
    }

    private void exitSolo() {
        soloExitPending = false;
        if (soloRow == null) {
            return;
        }
        soloRow = null;
        if (!isOpen()) {
            return;
        }
        soloCard.animate().alpha(0).setDuration(ApolloMotion.SHORT).setInterpolator(ApolloMotion.STANDARD)
                .withEndAction(() -> {
                    if (soloRow == null) {
                        soloCard.setVisibility(GONE);
                    }
                }).start();
        panelFrame.animate().alpha(1).setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD).start();
        scrim.animate().alpha(1).setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD).start();
    }

    // Right away, for the menu opening or closing
    private void resetSolo() {
        removeCallbacks(soloExit);
        soloExitPending = false;
        soloRow = null;
        soloCard.animate().cancel();
        soloCard.setAlpha(0);
        soloCard.setVisibility(GONE);
        panelFrame.setAlpha(1);
    }

    // Reads the value and the state again, after the button or when the row is built
    private void updateSliderRow(SliderRow row, boolean animate) {
        int value = row.slider.value.get();
        row.position = value;
        row.view.setValue(value);
        updateSliderText(row, value);
        updateSliderButton(row, animate);
    }

    private void updateSliderText(SliderRow row, int value) {
        boolean showActiveText = row.slider.activeText != null && row.slider.active.isOn();
        row.value.setText(showActiveText ? row.slider.activeText : value + "%");
    }

    // The button is tinted while its state is on, with a fade
    private void updateSliderButton(SliderRow row, boolean animate) {
        boolean active = row.slider.active.isOn();
        if (!animate) {
            row.active = active;
            applySliderButtonColors(row, active ? 1 : 0);
            return;
        }
        if (active == row.active) {
            return;
        }
        row.active = active;
        ValueAnimator animator = ValueAnimator.ofFloat(active ? 0 : 1, active ? 1 : 0);
        animator.setDuration(ApolloMotion.MEDIUM);
        animator.setInterpolator(ApolloMotion.STANDARD);
        animator.addUpdateListener(animation -> applySliderButtonColors(row, (float) animation.getAnimatedValue()));
        animator.start();
    }

    private void applySliderButtonColors(SliderRow row, float activeFraction) {
        row.buttonBackground.setColor((int) ARGB.evaluate(activeFraction, colors.surfaceContainerHigh, colors.secondaryContainer));
        row.icon.setImageTintList(ColorStateList.valueOf(
                (int) ARGB.evaluate(activeFraction, colors.onSurfaceVariant, colors.onSecondaryContainer)));
        row.icon.setImageResource(activeFraction >= 0.5f ? row.slider.activeIconResId : row.slider.iconResId);
    }

    // The right stick is Z on most gamepads and RX on the others, as in ControllerHandler
    private static int rightStickXAxis(InputDevice device) {
        if (device != null && (device.getMotionRange(MotionEvent.AXIS_Z) == null ||
                device.getMotionRange(MotionEvent.AXIS_RZ) == null)) {
            return MotionEvent.AXIS_RX;
        }
        return MotionEvent.AXIS_Z;
    }

    // Left stick moves the first slider, right stick the second, while the quick actions tab shows
    private void onSliderSticks(MotionEvent event) {
        float left = event.getAxisValue(MotionEvent.AXIS_X);
        float right = event.getAxisValue(rightStickXAxis(event.getDevice()));
        boolean tilted = false;
        for (SliderRow row : sliderRows) {
            row.stick = row.slider.rightStick ? right : left;
            tilted |= Math.abs(row.stick) > SLIDER_STICK_DEADZONE;
        }
        if (tilted && !gamepadMode) {
            gamepadMode = true;
            updateSelection(true);
        }
        if (tilted && !stickTickerRunning) {
            stickTickerRunning = true;
            lastStickTick = System.currentTimeMillis();
            post(stickTicker);
        }
    }

    private void moveSlidersWithSticks() {
        if (!isOpen() || currentTab != 0) {
            stopSliderSticks();
            return;
        }
        long now = System.currentTimeMillis();
        float seconds = Math.min(now - lastStickTick, 100) / 1000f;
        lastStickTick = now;

        boolean moving = false;
        for (SliderRow row : sliderRows) {
            float tilt = Math.abs(row.stick);
            if (tilt <= SLIDER_STICK_DEADZONE) {
                continue;
            }
            moving = true;
            holdSolo(row);
            // Squared, so a slight tilt moves slowly enough for single steps
            float speed = (tilt - SLIDER_STICK_DEADZONE) / (1 - SLIDER_STICK_DEADZONE);
            speed *= speed;
            row.position += Math.signum(row.stick) * speed * SLIDER_STICK_PERCENT_PER_SECOND * seconds;
            row.position = Math.max(row.slider.min, Math.min(100, row.position));
            int value = Math.round(row.position);
            if (value != row.slider.value.get()) {
                setSliderValue(row, value);
            }
        }
        // The stick of the lone slider is back in the middle
        if (soloRow != null && Math.abs(soloRow.stick) <= SLIDER_STICK_DEADZONE) {
            releaseSolo();
        }
        if (moving) {
            postDelayed(stickTicker, SLIDER_STICK_TICK_MS);
        } else {
            stickTickerRunning = false;
        }
    }

    private void stopSliderSticks() {
        removeCallbacks(stickTicker);
        stickTickerRunning = false;
        for (SliderRow row : sliderRows) {
            row.stick = 0;
        }
        releaseSolo();
    }

    // ---- Quick action buttons ----

    // One row of connected buttons, like a Material 3 button group
    private void addButtonGroup(List<QuickAction> actions) {
        if (actions.isEmpty()) {
            return;
        }
        LinearLayout line = new LinearLayout(getContext());
        line.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < actions.size(); i++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(56), 1);
            if (i > 0) {
                params.leftMargin = dp(3);
            }
            Tile tile = createTile(actions.get(i), groupRadii(i, actions.size()));
            tiles.add(tile);
            line.addView(tile.view, params);
        }
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lineParams.bottomMargin = dp(8);
        content.addView(line, lineParams);
    }

    // The name is white on every tile, a dangerous action keeps the red of its icon and of its ring
    private int tileLabelColor(QuickAction action, boolean active) {
        return active ? colors.onSecondaryContainer : colors.onSurface;
    }

    private int tileIconColor(QuickAction action, boolean active) {
        return action.danger ? colors.error : tileLabelColor(action, active);
    }

    private Tile createTile(QuickAction action, float[] radii) {
        boolean active = action.active != null && action.active.isOn();

        LinearLayout view = new LinearLayout(getContext());
        view.setOrientation(LinearLayout.VERTICAL);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(4), 0, dp(4), 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(active ? colors.secondaryContainer : colors.surfaceContainerHigh);
        background.setCornerRadii(radii);
        view.setBackground(ripple(background, radii));

        FrameLayout badgeBox = new FrameLayout(getContext());
        badgeBox.setMinimumWidth(dp(28));
        if (action.holdToConfirm) {
            // Square and centered like the badge, so the ring is a circle around it
            holdRing = new ApolloWidgets.RingView(getContext(), colors.error);
            badgeBox.addView(holdRing, new LayoutParams(dp(28), dp(28), Gravity.CENTER));
        }
        if (padConnected) {
            badgeBox.addView(buttonBadge(action.keyCode), new LayoutParams(LayoutParams.WRAP_CONTENT, dp(20), Gravity.CENTER));
        } else {
            badgeBox.addView(icon(actionIcon(action.keyCode), tileIconColor(action, active), 20),
                    new LayoutParams(dp(20), dp(20), Gravity.CENTER));
        }
        view.addView(badgeBox, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(28)));

        TextView label = text(action.shortLabel != null ? action.shortLabel : action.label, 11.5f,
                tileLabelColor(action, active), true);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        view.addView(label, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        Tile tile = new Tile(action, view, background, label, active);
        if (action.holdToConfirm) {
            holdTile = tile;
        }

        addPressFeedback(view);
        view.setOnClickListener(v -> {
            gamepadMode = false;
            if (action.holdToConfirm) {
                showConfirmDialog(action);
            } else {
                runQuickAction(action);
            }
        });
        return tile;
    }

    private void buildListContent(Tab tab) {
        for (Item item : tab.items) {
            if (item.header) {
                content.addView(sectionHeader(item.label));
            } else {
                Row row = createRow(item);
                rows.add(row);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                params.bottomMargin = dp(2);
                content.addView(row.view, params);
            }
        }

        if (selectedRow >= rows.size()) {
            selectedRow = Math.max(0, rows.size() - 1);
        }
        updateSelection(false);
    }

    private String dropdownValue(Item item) {
        int current = item.selection.get();
        return current >= 0 && current < item.options.length ? item.options[current] : "";
    }

    private Row createRow(Item item) {
        LinearLayout view = new LinearLayout(getContext());
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setMinimumHeight(dp(46));
        view.setPadding(dp(14), 0, dp(12), 0);

        GradientDrawable background = roundRect(Color.TRANSPARENT, dp(ApolloUi.ROW_RADIUS_DP));
        view.setBackground(ripple(background, dp(ApolloUi.ROW_RADIUS_DP)));

        ImageView icon = null;
        if (item.iconResId != 0) {
            icon = icon(item.iconResId, item.accent ? colors.primary : colors.onSurfaceVariant, 22);
            view.addView(icon);
        }

        TextView label = text(item.label, 14, item.accent ? colors.primary : colors.onSurface, true);
        label.setMaxLines(2);
        label.setPadding(icon != null ? dp(14) : 0, 0, dp(8), 0);
        view.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Row row = new Row(item, view, background, label, icon);
        row.labelColor = item.accent ? colors.primary : colors.onSurface;
        row.iconColor = item.accent ? colors.primary : colors.onSurfaceVariant;

        if (item.sliderValue != null) {
            row.slider = new SliderView(getContext(), colors);
            row.slider.setRange(item.sliderMin, item.sliderMax, item.sliderStep);
            row.slider.setValue(item.sliderValue.get());
            view.addView(row.slider, new LinearLayout.LayoutParams(dp(110), dp(36)));
            row.value = text(item.sliderValue.get() + item.sliderSuffix, 13, colors.onSurface, true);
            row.value.setGravity(Gravity.END);
            row.value.setSingleLine(true);
            view.addView(row.value, new LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.WRAP_CONTENT));
            row.slider.setListener(new SliderView.Listener() {
                @Override
                public void onSliderMoved(int value) {
                    setRowSlider(row, value);
                }

                @Override
                public void onSliderReleased(int value) {
                    setRowSlider(row, value);
                }
            });
        } else if (item.toggle != null) {
            row.toggle = new ApolloWidgets.SwitchView(getContext());
            row.toggle.setColors(colors.primary, colors.onPrimary, colors.surfaceContainerHighest, colors.outline);
            row.toggle.setChecked(item.toggle.isOn(), false);
            view.addView(row.toggle);
        } else if (item.options != null) {
            LinearLayout chip = new LinearLayout(getContext());
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(dp(12), 0, dp(6), 0);
            chip.setBackground(roundRect(colors.surfaceContainerHighest, dp(8)));
            row.value = text(dropdownValue(item), 13, colors.onSurface, true);
            chip.addView(row.value);
            chip.addView(icon(R.drawable.ic_menu_dropdown, colors.onSurfaceVariant, 18));
            view.addView(chip, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(30)));
        } else if (item.trailingText != null) {
            TextView keys = text(item.trailingText, 11, colors.onSurfaceVariant, false);
            keys.setTypeface(Typeface.MONOSPACE);
            keys.setSingleLine(true);
            keys.setPadding(dp(7), dp(3), dp(7), dp(3));
            GradientDrawable outline = roundRect(Color.TRANSPARENT, dp(6));
            outline.setStroke(dp(1), colors.outlineVariant);
            keys.setBackground(outline);
            view.addView(keys);
        }

        addPressFeedback(view);
        view.setOnClickListener(v -> {
            gamepadMode = false;
            selectedRow = rows.indexOf(row);
            updateSelection(true);
            activate(row);
        });
        if (item.longPressAction != null) {
            view.setOnLongClickListener(v -> {
                gamepadMode = false;
                updateSelection(true);
                item.longPressAction.run();
                return true;
            });
        }
        return row;
    }

    private void buildHintRow() {
        // LB and RB are already drawn beside the tabs
        hintRow.removeAllViews();
        if (currentTab == 0) {
            for (QuickSlider slider : quickSliders) {
                addHint(hint(slider.rightStick ? KeyEvent.KEYCODE_BUTTON_THUMBR : KeyEvent.KEYCODE_BUTTON_THUMBL, slider.name));
            }
        } else {
            addHint(hint(KeyEvent.KEYCODE_BUTTON_A, str(R.string.game_menu_hint_select)));
        }
        addHint(hint(KeyEvent.KEYCODE_BUTTON_B, str(R.string.game_menu_hint_close)));
    }

    private void addHint(View hint) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (hintRow.getChildCount() > 0) {
            params.leftMargin = dp(16);
        }
        hintRow.addView(hint, params);
    }

    // Moves the selection highlight, fading the rows that change
    private void updateSelection(boolean animate) {
        // The button hints only matter while a gamepad drives the menu
        float hintAlpha = gamepadMode && padConnected ? 1f : 0f;
        if (hintRow.getAlpha() != hintAlpha) {
            hintRow.animate().cancel();
            hintRow.animate().alpha(hintAlpha).setDuration(animate ? ApolloMotion.MEDIUM : 0)
                    .setInterpolator(ApolloMotion.STANDARD).start();
        }

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            boolean selected = gamepadMode && i == selectedRow;
            int background = selected ? colors.secondaryContainer : Color.TRANSPARENT;
            int label = row.item.accent ? colors.primary : selected ? colors.onSecondaryContainer : colors.onSurface;
            int icon = row.item.accent ? colors.primary : selected ? colors.onSecondaryContainer : colors.onSurfaceVariant;

            if (background == row.backgroundColor && label == row.labelColor) {
                continue;
            }
            if (row.animator != null) {
                row.animator.cancel();
            }

            int fromBackground = row.backgroundColor;
            int fromLabel = row.labelColor, fromIcon = row.iconColor;
            row.backgroundColor = background;
            row.labelColor = label;
            row.iconColor = icon;

            if (!animate) {
                applyRowColors(row, background, label, icon);
                continue;
            }
            row.animator = ValueAnimator.ofFloat(0, 1);
            row.animator.setDuration(ApolloMotion.SHORT);
            row.animator.setInterpolator(ApolloMotion.STANDARD);
            row.animator.addUpdateListener(animation -> {
                float f = animation.getAnimatedFraction();
                applyRowColors(row,
                        (int) ARGB.evaluate(f, fromBackground, background),
                        (int) ARGB.evaluate(f, fromLabel, label),
                        (int) ARGB.evaluate(f, fromIcon, icon));
            });
            row.animator.start();
        }

        if (gamepadMode && selectedRow < rows.size()) {
            View view = rows.get(selectedRow).view;
            scrollView.post(() -> {
                int top = view.getTop();
                int bottom = view.getBottom();
                int scrollY = scrollView.getScrollY();
                int height = scrollView.getHeight();
                if (top < scrollY) {
                    scrollView.smoothScrollTo(0, Math.max(0, top - dp(28)));
                } else if (bottom > scrollY + height) {
                    scrollView.smoothScrollTo(0, bottom - height + dp(4));
                }
            });
        }
    }

    private void applyRowColors(Row row, int background, int label, int icon) {
        row.background.setColor(background);
        row.label.setTextColor(label);
        if (row.icon != null) {
            row.icon.setImageTintList(ColorStateList.valueOf(icon));
        }
    }

    // Updates switches, dropdown values and active tiles in place, so the changes can animate
    private void updateStates() {
        for (Tile tile : tiles) {
            boolean active = tile.action.active != null && tile.action.active.isOn();
            if (active == tile.active) {
                continue;
            }
            tile.active = active;
            int fromBackground = active ? colors.surfaceContainerHigh : colors.secondaryContainer;
            int toBackground = active ? colors.secondaryContainer : colors.surfaceContainerHigh;
            int fromLabel = tileLabelColor(tile.action, !active);
            int toLabel = tileLabelColor(tile.action, active);
            ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
            animator.setDuration(ApolloMotion.MEDIUM);
            animator.setInterpolator(ApolloMotion.STANDARD);
            animator.addUpdateListener(animation -> {
                float f = animation.getAnimatedFraction();
                tile.background.setColor((int) ARGB.evaluate(f, fromBackground, toBackground));
                tile.label.setTextColor((int) ARGB.evaluate(f, fromLabel, toLabel));
            });
            animator.start();
        }

        for (Row row : rows) {
            if (row.toggle != null) {
                row.toggle.setChecked(row.item.toggle.isOn(), true);
            }
            // A slider's value is its own, set while it moves
            if (row.value != null && row.item.options != null) {
                row.value.setText(dropdownValue(row.item));
            }
        }
    }

    // ---- Actions ----

    private void selectTab(int index) {
        int target = (index + tabCount()) % tabCount();
        if (target == currentTab) {
            return;
        }
        // Content slides in from the side we are moving towards
        int direction = target > currentTab ? 1 : -1;

        hideDropdown(false);
        currentTab = target;
        selectedRow = 0;
        updateTabs(true);
        rebuildContent();
        scrollView.scrollTo(0, 0);

        content.animate().cancel();
        content.setAlpha(0);
        content.setTranslationX(direction * dp(TAB_SLIDE_DP));
        content.animate().alpha(1).translationX(0)
                .setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE).start();
    }

    private void switchTab(int delta) {
        selectTab(currentTab + delta);
    }

    private void runAndMaybeClose(Runnable action, boolean keepOpen) {
        if (action == null) {
            return;
        }
        if (keepOpen) {
            action.run();
            updateStates();
            // An action may have just added the on-screen gamepad on top of us
            bringToFront();
        } else {
            close();
            // Let the stream view get its focus back first, the soft keyboard needs it
            post(action);
        }
    }

    private void runQuickAction(QuickAction action) {
        runAndMaybeClose(action.action, action.keepOpen);
    }

    private void setRowSlider(Row row, int value) {
        Item item = row.item;
        value = Math.max(item.sliderMin, Math.min(item.sliderMax, value));
        item.sliderValue.set(value);
        row.slider.setValue(value);
        row.value.setText(value + item.sliderSuffix);
    }

    private void activate(Row row) {
        Item item = row.item;
        if (item.sliderValue != null) {
            // Left and right move it
            return;
        }
        if (item.options != null) {
            showDropdown(row);
            return;
        }
        runAndMaybeClose(item.action, item.keepOpen);
    }

    private Tile findTile(int keyCode) {
        for (Tile tile : tiles) {
            if (tile.action.keyCode == keyCode) {
                return tile;
            }
        }
        return null;
    }

    private QuickAction findQuickAction(int keyCode) {
        for (QuickAction action : quickActions) {
            if (action.keyCode == keyCode) {
                return action;
            }
        }
        return null;
    }

    private void startHold(QuickAction action) {
        cancelHold(false);
        holdingAction = action;
        holdCompleted = false;

        holdAnimator = ValueAnimator.ofFloat(0, 1);
        holdAnimator.setDuration(HOLD_TO_CONFIRM_MS);
        holdAnimator.setInterpolator(new LinearInterpolator());
        holdAnimator.addUpdateListener(animation -> {
            if (holdRing != null) {
                holdRing.setProgress((float) animation.getAnimatedValue());
            }
        });
        holdAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!cancelled && holdingAction == action) {
                    holdCompleted = true;
                    holdingAction = null;
                    runAndMaybeClose(action.action, false);
                }
            }
        });
        holdAnimator.start();
    }

    // Called when the held button is released, too early unless the action already ran
    private void cancelHold(boolean showHint) {
        if (holdAnimator != null) {
            holdAnimator.cancel();
            holdAnimator = null;
        }
        if (holdRing != null) {
            holdRing.setProgress(0);
        }
        if (holdingAction != null && showHint && !holdCompleted) {
            if (holdTile != null) {
                shake(holdTile.view);
            }
            Toast.makeText(getContext(), getContext().getString(R.string.game_menu_hold_hint,
                    badgeLabel(holdingAction.keyCode)), Toast.LENGTH_SHORT).show();
        }
        holdingAction = null;
    }

    private void showConfirmDialog(QuickAction action) {
        dialogTitle.setText(action.label + "?");
        dialogConfirm = action.action;

        dialogLayer.animate().cancel();
        dialogCard.animate().cancel();
        dialogLayer.setAlpha(0);
        dialogCard.setScaleX(0.9f);
        dialogCard.setScaleY(0.9f);
        dialogLayer.setVisibility(VISIBLE);
        dialogLayer.animate().alpha(1)
                .setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD).start();
        dialogCard.animate().scaleX(1).scaleY(1)
                .setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE).start();
    }

    private boolean isDialogShown() {
        return dialogLayer.getVisibility() == VISIBLE && dialogConfirm != null;
    }

    private void dismissDialog() {
        dialogConfirm = null;
        dialogLayer.animate().alpha(0)
                .setDuration(ApolloMotion.SHORT).setInterpolator(ApolloMotion.STANDARD)
                .withEndAction(() -> {
                    if (dialogConfirm == null) {
                        dialogLayer.setVisibility(GONE);
                    }
                })
                .start();
    }

    private void confirmDialog() {
        Runnable action = dialogConfirm;
        dialogConfirm = null;
        dialogLayer.setVisibility(GONE);
        runAndMaybeClose(action, false);
    }

    private void showDropdown(Row row) {
        hideDropdown(false);
        dropdownRow = row;
        dropdownIndex = Math.max(0, row.item.selection.get());
        int generation = ++dropdownGeneration;

        for (int i = 0; i < row.item.options.length; i++) {
            final int index = i;
            LinearLayout option = new LinearLayout(getContext());
            option.setOrientation(LinearLayout.HORIZONTAL);
            option.setGravity(Gravity.CENTER_VERTICAL);
            option.setPadding(dp(14), 0, dp(14), 0);
            option.setBackground(ripple(roundRect(Color.TRANSPARENT, 0), 0));

            ImageView check = icon(R.drawable.ic_menu_check, colors.primary, 18);
            check.setVisibility(i == row.item.selection.get() ? VISIBLE : INVISIBLE);
            option.addView(check);

            TextView label = text(row.item.options[i], 14, colors.onSurface, false);
            label.setPadding(dp(10), 0, 0, 0);
            option.addView(label);

            option.setOnClickListener(v -> selectDropdownOption(index));
            dropdownCard.addView(option, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));
            dropdownOptions.add(option);
        }

        updateDropdownSelection();
        updateSelection(true);
        dropdownCard.animate().cancel();
        dropdownCard.setVisibility(INVISIBLE);

        // Below the row if it fits, otherwise above it. It unfolds from the row. A menu too tall for either
        // opens over the row with the chosen option on it, inside the panel.
        int selected = Math.max(0, row.item.selection.get());
        dropdownCard.post(() -> {
            if (dropdownGeneration != generation) {
                return;
            }
            int[] frameLocation = new int[2];
            int[] rowLocation = new int[2];
            panelFrame.getLocationInWindow(frameLocation);
            row.view.getLocationInWindow(rowLocation);
            int rowTop = rowLocation[1] - frameLocation[1];
            int rowBottom = rowTop + row.view.getHeight();
            int cardHeight = dropdownCard.getHeight();
            int frameHeight = panelFrame.getHeight();

            int top;
            float pivotY;
            if (rowBottom + dp(4) + cardHeight <= frameHeight - dp(8)) {
                top = rowBottom + dp(4);
                pivotY = 0;
            } else if (rowTop - dp(4) - cardHeight >= dp(8)) {
                top = rowTop - dp(4) - cardHeight;
                pivotY = cardHeight;
            } else {
                View option = dropdownCard.getChildAt(Math.min(selected, dropdownCard.getChildCount() - 1));
                int optionMiddle = option.getTop() + option.getHeight() / 2;
                top = rowTop + row.view.getHeight() / 2 - optionMiddle;
                top = Math.max(dp(8), Math.min(frameHeight - dp(8) - cardHeight, top));
                pivotY = rowTop + row.view.getHeight() / 2f - top;
            }
            LayoutParams params = (LayoutParams) dropdownCard.getLayoutParams();
            params.topMargin = top;
            params.rightMargin = dp(18);
            dropdownCard.setLayoutParams(params);

            dropdownCard.setPivotX(dropdownCard.getWidth());
            dropdownCard.setPivotY(pivotY);
            dropdownCard.setScaleY(0.6f);
            dropdownCard.setAlpha(0);
            dropdownCard.setVisibility(VISIBLE);
            dropdownCard.animate().scaleY(1).alpha(1)
                    .setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE).start();
        });
    }

    private void hideDropdown(boolean animate) {
        if (dropdownRow == null && dropdownCard.getVisibility() == GONE) {
            return;
        }
        dropdownRow = null;
        dropdownOptions.clear();
        int generation = ++dropdownGeneration;

        if (!animate) {
            dropdownCard.animate().cancel();
            dropdownCard.removeAllViews();
            dropdownCard.setVisibility(GONE);
            return;
        }
        dropdownCard.animate().alpha(0)
                .setDuration(ApolloMotion.SHORT).setInterpolator(ApolloMotion.STANDARD)
                .withEndAction(() -> {
                    if (dropdownGeneration == generation) {
                        dropdownCard.removeAllViews();
                        dropdownCard.setVisibility(GONE);
                    }
                })
                .start();
    }

    private void updateDropdownSelection() {
        for (int i = 0; i < dropdownOptions.size(); i++) {
            dropdownOptions.get(i).setBackground(ripple(roundRect(
                    gamepadMode && i == dropdownIndex ? colors.surfaceContainerHighest : Color.TRANSPARENT, 0), 0));
        }
    }

    private void selectDropdownOption(int index) {
        Row row = dropdownRow;
        hideDropdown(true);
        if (row != null) {
            row.item.selection.set(index);
        }
        updateStates();
        updateSelection(true);
    }

    // ---- Gamepad and keyboard input ----

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN && gamepadMode) {
            gamepadMode = false;
            updateSelection(true);
            updateDropdownSelection();
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = flipFaceButtons ? flipFaceButton(event.getKeyCode()) : event.getKeyCode();

        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (holdingAction != null && keyCode == holdingAction.keyCode) {
                cancelHold(true);
            }
            return true;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0 && !isDpad(keyCode)) {
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            keyCode = KeyEvent.KEYCODE_BUTTON_B;
        }
        handleButton(keyCode, event.getRepeatCount() == 0);
        return true;
    }

    private void handleButton(int keyCode, boolean firstPress) {
        boolean confirm = keyCode == KeyEvent.KEYCODE_BUTTON_A || keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                keyCode == KeyEvent.KEYCODE_ENTER;
        boolean back = keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_ESCAPE;

        if (isDialogShown()) {
            if (confirm) {
                confirmDialog();
            } else if (back) {
                dismissDialog();
            }
            return;
        }

        if (!gamepadMode) {
            gamepadMode = true;
            updateSelection(true);
        }

        if (dropdownRow != null) {
            int count = dropdownRow.item.options.length;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                dropdownIndex = Math.max(0, dropdownIndex - 1);
                updateDropdownSelection();
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                dropdownIndex = Math.min(count - 1, dropdownIndex + 1);
                updateDropdownSelection();
            } else if (confirm) {
                selectDropdownOption(dropdownIndex);
            } else if (back) {
                hideDropdown(true);
                updateSelection(true);
            }
            return;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_L1:
                switchTab(-1);
                return;
            case KeyEvent.KEYCODE_BUTTON_R1:
                switchTab(1);
                return;
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_ESCAPE:
                close();
                return;
            case KeyEvent.KEYCODE_MENU:
                keyCode = KeyEvent.KEYCODE_BUTTON_START;
                break;
        }

        // Start works from every tab
        if (currentTab == 0 || keyCode == KeyEvent.KEYCODE_BUTTON_START) {
            QuickAction action = findQuickAction(keyCode);
            if (action != null && firstPress) {
                Tile tile = findTile(keyCode);
                if (tile != null && !action.holdToConfirm) {
                    pulse(tile.view);
                }
                if (action.holdToConfirm) {
                    startHold(action);
                } else {
                    runQuickAction(action);
                }
            }
            return;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                moveSelection(-1);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                moveSelection(1);
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                int direction = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                Row selected = selectedRow < rows.size() ? rows.get(selectedRow) : null;
                if (selected != null && selected.slider != null) {
                    // On a slider, left and right move it instead of changing tab
                    setRowSlider(selected, selected.item.sliderValue.get() + direction * selected.item.sliderStep);
                } else {
                    switchTab(direction);
                }
                break;
            }
            default:
                if (confirm && firstPress && selectedRow < rows.size()) {
                    Row row = rows.get(selectedRow);
                    pulse(row.view);
                    activate(row);
                }
                break;
        }
    }

    private void moveSelection(int delta) {
        if (rows.isEmpty()) {
            return;
        }
        selectedRow = (selectedRow + delta + rows.size()) % rows.size();
        updateSelection(true);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) {
            return super.onGenericMotionEvent(event);
        }

        // A D-pad reported as a hat axis acts like the D-pad buttons, once per press
        int hatX = Math.round(event.getAxisValue(MotionEvent.AXIS_HAT_X));
        int hatY = Math.round(event.getAxisValue(MotionEvent.AXIS_HAT_Y));
        if (hatX != lastHatX || hatY != lastHatY) {
            if (hatX != 0 && hatX != lastHatX) {
                handleButton(hatX < 0 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT, true);
            } else if (hatY != 0 && hatY != lastHatY) {
                handleButton(hatY < 0 ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN, true);
            }
            lastHatX = hatX;
            lastHatY = hatY;
            return true;
        }

        // In the quick actions tab the sticks move the sliders: navigating would trigger actions by accident
        if (currentTab == 0 && dropdownRow == null) {
            if (!isDialogShown()) {
                onSliderSticks(event);
            }
            return true;
        }

        float x = event.getAxisValue(MotionEvent.AXIS_X);
        float y = event.getAxisValue(MotionEvent.AXIS_Y);

        // The dominant axis wins, so diagonals don't switch tab by accident
        int key = 0;
        float tilt = 0;
        if (Math.abs(x) >= Math.abs(y)) {
            if (Math.abs(x) > ANALOG_STICK_THRESHOLD && dropdownRow == null) {
                key = x < 0 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
                tilt = Math.abs(x);
            }
        } else if (Math.abs(y) > ANALOG_STICK_THRESHOLD) {
            key = y < 0 ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN;
            tilt = Math.abs(y);
        }
        stickNavTilt = tilt;

        // MoonVibe: the stick only sends events while it moves, so held still it stepped once and stopped.
        // Held in a direction it now steps again and again, faster the further it is pushed.
        if (key != stickNavKey) {
            removeCallbacks(stickNavRepeat);
            stickNavKey = key;
            if (key != 0) {
                long now = System.currentTimeMillis();
                // A quick flick back and forth through the center still moves one step at a time
                if (now - lastAnalogNavTime >= ANALOG_NAV_THROTTLE_MS / 2) {
                    handleButton(key, true);
                    lastAnalogNavTime = now;
                }
                postDelayed(stickNavRepeat, STICK_NAV_FIRST_REPEAT_MS);
            }
        }
        return true;
    }

    private static final long STICK_NAV_FIRST_REPEAT_MS = 320;
    private static final long STICK_NAV_SLOW_MS = 150;
    private static final long STICK_NAV_FAST_MS = 55;
    private int stickNavKey;
    private float stickNavTilt;

    private final Runnable stickNavRepeat = new Runnable() {
        @Override
        public void run() {
            if (getVisibility() != VISIBLE) {
                stickNavKey = 0;
                return;
            }
            if (stickNavKey == 0) {
                return;
            }
            // Changing tab is once per push; moving in a list or a slider repeats
            boolean horizontal = stickNavKey == KeyEvent.KEYCODE_DPAD_LEFT || stickNavKey == KeyEvent.KEYCODE_DPAD_RIGHT;
            Row selected = selectedRow < rows.size() ? rows.get(selectedRow) : null;
            if (horizontal && (selected == null || selected.slider == null)) {
                return;
            }
            handleButton(stickNavKey, false);
            lastAnalogNavTime = System.currentTimeMillis();
            float push = Math.min(1f, (stickNavTilt - ANALOG_STICK_THRESHOLD) / (1f - ANALOG_STICK_THRESHOLD));
            postDelayed(this, (long) (STICK_NAV_SLOW_MS - push * (STICK_NAV_SLOW_MS - STICK_NAV_FAST_MS)));
        }
    };

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        // Keep the panel content clear of a notch on the left side
        int left = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            DisplayCutout cutout = insets.getDisplayCutout();
            if (cutout != null) {
                left = cutout.getSafeInsetLeft();
            }
        }
        setPanelPadding(dp(10) + left);
        return super.onApplyWindowInsets(insets);
    }

    // The scroll area reaches the panel edges and pads the content back in, so the content is not
    // cut while it slides between tabs or grows on a press
    private void setPanelPadding(int left) {
        int right = dp(10);
        panel.setPadding(left, dp(10), right, dp(6));
        scrollView.setPadding(left, 0, right, 0);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) scrollView.getLayoutParams();
        params.leftMargin = -left;
        params.rightMargin = -right;
        scrollView.setLayoutParams(params);
    }

    private static int flipFaceButton(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A: return KeyEvent.KEYCODE_BUTTON_B;
            case KeyEvent.KEYCODE_BUTTON_B: return KeyEvent.KEYCODE_BUTTON_A;
            case KeyEvent.KEYCODE_BUTTON_X: return KeyEvent.KEYCODE_BUTTON_Y;
            case KeyEvent.KEYCODE_BUTTON_Y: return KeyEvent.KEYCODE_BUTTON_X;
            default: return keyCode;
        }
    }
}
