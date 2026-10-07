package com.limelight.ui.apollo.settings;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Point;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.ui.apollo.ApolloUi;
import com.limelight.ui.apollo.ApolloWidgets;
import com.limelight.ui.apollo.BumperDrawable;
import com.limelight.ui.apollo.DragReorder;
import com.limelight.ui.apollo.hints.ButtonGlyph;
import com.limelight.ui.apollo.hints.HintRow;
import com.limelight.ui.apollo.stats.StatsOverlayView;
import com.limelight.ui.apollo.stats.StatsPrefs;
import com.limelight.ui.theme.ApolloColors;
import com.limelight.ui.theme.ApolloMotion;

import java.util.ArrayList;
import java.util.List;

/**
 * The page of the stats overlay. On the left the style, switched with LB/RB, over a preview of the whole screen;
 * on the right whether it shows, where, how opaque, and what the chosen style shows.
 */
public class StatsSettingsView extends FrameLayout {
    private static final ArgbEvaluator ARGB = new ArgbEvaluator();

    private final Activity activity;
    private final ApolloColors colors;
    private final LinearLayout column;
    private final OptionsPopup popup;
    private final List<TextView> segments = new ArrayList<>();
    private final StatsOverlayView preview;
    private final LinearLayout itemRows;
    private StatsPrefs.Style style;
    private TextView positionValue;
    private TextView opacityValue;
    private SliderView opacitySlider;
    private View firstRow;
    private DragReorder reorder;
    // The item row moved with the gamepad, and the order before it
    private View movingRow;
    private List<StatsPrefs.Item> orderBeforeMove;

    /** @param onRestored after the defaults are back: the page is built again */
    public StatsSettingsView(Activity activity, ApolloColors colors, Runnable onRestored) {
        super(activity);
        this.activity = activity;
        this.colors = colors;
        style = StatsPrefs.style(activity);

        column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        addView(column, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        popup = new OptionsPopup(this, column, colors);

        // Back and title
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), 0, dp(16), 0);
        ImageView back = new ImageView(activity);
        back.setImageResource(R.drawable.ic_apollo_arrow_left);
        back.setImageTintList(ColorStateList.valueOf(colors.onSurface));
        back.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        back.setPadding(dp(8), dp(8), dp(8), dp(8));
        back.setBackground(ApolloUi.ripple(ApolloUi.roundRect(0, dp(20)), dp(20)));
        back.setContentDescription(activity.getString(R.string.apollo_hint_back));
        back.setFocusable(false);
        back.setOnClickListener(v -> activity.finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView title = ApolloUi.text(activity, activity.getString(R.string.apollo_section_stats), 20, colors.onSurface, true);
        title.setPadding(dp(8), 0, 0, 0);
        header.addView(title);
        column.addView(header, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(56)));

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setPadding(dp(16), 0, dp(16), 0);
        body.setBaselineAligned(false);
        column.addView(body, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));

        // Left: the style and how it looks
        LinearLayout left = new LinearLayout(activity);
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 0.47f);
        leftParams.rightMargin = dp(16);
        body.addView(left, leftParams);
        left.addView(buildStyleSwitch(), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(40)));

        preview = new StatsOverlayView(activity);
        preview.setPreview();
        PreviewBox box = new PreviewBox(activity, preview);
        LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        boxParams.topMargin = dp(12);
        left.addView(box, boxParams);

        // Right: the settings
        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, dp(12));
        body.addView(scroll, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 0.53f));
        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        firstRow = switchRow(rows, activity.getString(R.string.stats_settings_show),
                () -> StatsPrefs.isShown(activity), on -> StatsPrefs.setShown(activity, on));
        positionRow(rows);
        opacityRow(rows);
        switchRow(rows, activity.getString(R.string.stats_settings_warnings),
                () -> StatsPrefs.warnings(activity), on -> {
                    StatsPrefs.setWarnings(activity, on);
                    preview.showStyle(style, false);
                });

        itemRows = new LinearLayout(activity);
        itemRows.setOrientation(LinearLayout.VERTICAL);
        // The header stays, the rows after it move
        reorder = new DragReorder(itemRows, 1, this::saveOrder);
        rows.addView(itemRows, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        restoreRow(rows, onRestored);

        updateSegments(false);
        buildItemRows();
        preview.showStyle(style, false);
        ApolloUi.focusByDefault(activity, firstRow);
    }

    private int dp(float value) {
        return ApolloUi.dp(getContext(), value);
    }

    // ---- The style ----

    private View buildStyleSwitch() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        row.addView(bumper("LB", -1), new LinearLayout.LayoutParams(dp(26), dp(19)));

        LinearLayout group = new LinearLayout(activity);
        group.setOrientation(LinearLayout.HORIZONTAL);
        group.setPadding(dp(4), dp(4), dp(4), dp(4));
        group.setBackground(ApolloUi.roundRect(colors.surfaceContainer, dp(20)));
        LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1);
        groupParams.leftMargin = dp(6);
        groupParams.rightMargin = dp(6);
        row.addView(group, groupParams);

        StatsPrefs.Style[] styles = StatsPrefs.Style.values();
        for (int i = 0; i < styles.length; i++) {
            StatsPrefs.Style each = styles[i];
            TextView segment = ApolloUi.text(activity, activity.getString(each.labelRes), 14, colors.onSurfaceVariant, true);
            segment.setGravity(Gravity.CENTER);
            segment.setSingleLine(true);
            segment.setEllipsize(TextUtils.TruncateAt.END);
            segment.setBackground(ApolloUi.roundRect(0, dp(16)));
            segment.setOnClickListener(v -> selectStyle(each));
            ApolloUi.pressFeedback(segment);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1);
            if (i > 0) {
                params.leftMargin = dp(4);
            }
            group.addView(segment, params);
            segments.add(segment);
        }

        row.addView(bumper("RB", 1), new LinearLayout.LayoutParams(dp(26), dp(19)));
        return row;
    }

    private TextView bumper(String label, int direction) {
        TextView chip = ApolloUi.text(activity, label, 9.5f, colors.onSurfaceVariant, true);
        chip.setGravity(Gravity.CENTER);
        chip.setIncludeFontPadding(false);
        chip.setBackground(new BumperDrawable(colors.surfaceContainerHighest, direction < 0,
                getResources().getDisplayMetrics().density));
        // The letters a little low, where the shape is fullest
        chip.setPadding(0, dp(1), 0, 0);
        chip.setOnClickListener(v -> switchStyle(direction));
        ApolloUi.pressFeedback(chip);
        return chip;
    }

    /** LB/RB: the previous or next style, around. */
    public void switchStyle(int direction) {
        StatsPrefs.Style[] styles = StatsPrefs.Style.values();
        selectStyle(styles[(style.ordinal() + direction + styles.length) % styles.length]);
    }

    private void selectStyle(StatsPrefs.Style newStyle) {
        if (newStyle == style) {
            return;
        }
        popup.dismiss(false);
        style = newStyle;
        StatsPrefs.setStyle(activity, style);
        updateSegments(true);
        preview.showStyle(style, true);
        positionValue.setText(StatsPrefs.position(activity, style).labelRes);

        // The item rows of the new style, faded in
        boolean hadFocus = itemRows.hasFocus();
        buildItemRows();
        itemRows.setAlpha(0);
        itemRows.animate().alpha(1).setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD).start();
        if (hadFocus && itemRows.getChildCount() > 1) {
            itemRows.getChildAt(1).requestFocus();
        }
    }

    private void updateSegments(boolean animate) {
        for (int i = 0; i < segments.size(); i++) {
            TextView segment = segments.get(i);
            boolean selected = i == style.ordinal();
            int toBackground = selected ? colors.secondaryContainer : 0;
            int toText = selected ? colors.onSecondaryContainer : colors.onSurfaceVariant;
            GradientDrawable background = (GradientDrawable) segment.getBackground();
            if (!animate) {
                background.setColor(toBackground);
                segment.setTextColor(toText);
                segment.setTag(toBackground);
                continue;
            }
            int fromBackground = segment.getTag() instanceof Integer ? (Integer) segment.getTag() : 0;
            int fromText = segment.getCurrentTextColor();
            segment.setTag(toBackground);
            ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
            animator.setDuration(ApolloMotion.MEDIUM);
            animator.setInterpolator(ApolloMotion.STANDARD);
            animator.addUpdateListener(animation -> {
                float f = animation.getAnimatedFraction();
                background.setColor((int) ARGB.evaluate(f, fromBackground, toBackground));
                segment.setTextColor((int) ARGB.evaluate(f, fromText, toText));
            });
            animator.start();
        }
    }

    // ---- The rows ----

    private interface Getter {
        boolean get();
    }

    private interface Setter {
        void set(boolean on);
    }

    private LinearLayout row(LinearLayout parent, String label) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(46));
        row.setPadding(dp(14), 0, dp(14), 0);
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(ApolloUi.stateLayer(colors.surfaceContainerHigh, 0, dp(ApolloUi.ROW_RADIUS_DP)));
        TextView text = ApolloUi.text(activity, label, 14, colors.onSurface, true);
        text.setPadding(0, dp(6), dp(12), dp(6));
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(2);
        parent.addView(row, params);
        return row;
    }

    private View switchRow(LinearLayout parent, String label, Getter getter, Setter setter) {
        LinearLayout row = row(parent, label);
        ApolloWidgets.SwitchView toggle = new ApolloWidgets.SwitchView(activity);
        toggle.setColors(colors.primary, colors.onPrimary, colors.surfaceContainerHighest, colors.outline);
        toggle.setChecked(getter.get(), false);
        row.addView(toggle);
        row.setOnClickListener(v -> {
            boolean on = !getter.get();
            setter.set(on);
            toggle.setChecked(on, true);
        });
        HintRow.set(row, KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_change, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_back);
        return row;
    }

    private void positionRow(LinearLayout parent) {
        LinearLayout row = row(parent, activity.getString(R.string.stats_settings_position));
        positionValue = ApolloUi.text(activity, activity.getString(StatsPrefs.position(activity, style).labelRes),
                13, colors.primary, true);
        positionValue.setSingleLine(true);
        row.addView(positionValue);
        row.setOnClickListener(v -> {
            StatsPrefs.Position[] positions = StatsPrefs.Position.values();
            CharSequence[] labels = new CharSequence[positions.length];
            for (int i = 0; i < positions.length; i++) {
                labels[i] = activity.getString(positions[i].labelRes);
            }
            int rightEdge = getWidth() - dp(30);
            popup.show(row, rightEdge, labels, StatsPrefs.position(activity, style).ordinal(), index -> {
                StatsPrefs.setPosition(activity, style, positions[index]);
                positionValue.setText(positions[index].labelRes);
                preview.showStyle(style, true);
            });
        });
        HintRow.set(row, KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_change, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_back);
    }

    private void opacityRow(LinearLayout parent) {
        LinearLayout row = row(parent, activity.getString(R.string.stats_settings_opacity));
        opacitySlider = new SliderView(activity, colors);
        opacitySlider.setRange(StatsPrefs.OPACITY_MIN, StatsPrefs.OPACITY_MAX, StatsPrefs.OPACITY_STEP);
        opacitySlider.setValue(StatsPrefs.opacity(activity));
        opacitySlider.setListener(new SliderView.Listener() {
            @Override
            public void onSliderMoved(int value) {
                setOpacity(value);
            }

            @Override
            public void onSliderReleased(int value) {
                setOpacity(value);
            }
        });
        row.addView(opacitySlider, new LinearLayout.LayoutParams(dp(130), dp(40)));
        opacityValue = ApolloUi.text(activity, StatsPrefs.opacity(activity) + "%", 13, colors.onSurface, true);
        opacityValue.setGravity(Gravity.END);
        row.addView(opacityValue, new LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN
                    || (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT)) {
                return false;
            }
            int step = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -StatsPrefs.OPACITY_STEP : StatsPrefs.OPACITY_STEP;
            int value = Math.max(StatsPrefs.OPACITY_MIN, Math.min(StatsPrefs.OPACITY_MAX, StatsPrefs.opacity(activity) + step));
            opacitySlider.setValue(value);
            setOpacity(value);
            return true;
        });
        HintRow.set(row, ButtonGlyph.DPAD_LEFT_RIGHT, R.string.apollo_hint_adjust, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_back);
    }

    private void setOpacity(int value) {
        StatsPrefs.setOpacity(activity, value);
        opacityValue.setText(value + "%");
        preview.refreshOpacity();
    }

    // Like the categories of the settings: everything on the page back to its default, after asking
    private void restoreRow(LinearLayout parent, Runnable onRestored) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(46));
        row.setPadding(dp(14), 0, dp(14), 0);
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(ApolloUi.stateLayer(colors.surfaceContainerHigh, 0, dp(ApolloUi.ROW_RADIUS_DP)));
        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.ic_apollo_restore);
        icon.setImageTintList(ColorStateList.valueOf(colors.primary));
        row.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView label = ApolloUi.text(activity, activity.getString(R.string.apollo_settings_restore), 14, colors.primary, true);
        label.setPadding(dp(12), 0, 0, 0);
        row.addView(label);
        row.setOnClickListener(v -> new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.apollo_settings_restore_title, activity.getString(R.string.apollo_section_stats)))
                .setMessage(R.string.stats_restore_message)
                .setNegativeButton(R.string.apollo_settings_restore_cancel, null)
                .setPositiveButton(R.string.apollo_settings_restore_confirm, (dialog, which) -> {
                    StatsPrefs.restoreDefaults(activity);
                    onRestored.run();
                })
                .show());
        HintRow.set(row, KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_select, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_back);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        parent.addView(row, params);
    }

    private void buildItemRows() {
        finishMove();
        itemRows.removeAllViews();
        int title = style == StatsPrefs.Style.PANEL ? R.string.stats_settings_items_panel
                : style == StatsPrefs.Style.MINI ? R.string.stats_settings_items_mini : R.string.stats_settings_items_bar;
        TextView header = ApolloUi.sectionHeader(activity, colors, activity.getString(title));
        header.setPadding(dp(14), dp(16), dp(14), dp(6));
        itemRows.addView(header);
        StatsPrefs.Style rowsStyle = style;
        for (StatsPrefs.Item item : StatsPrefs.order(activity, style)) {
            itemRow(item, rowsStyle);
        }
    }

    // A value the style can show: its switch, and a handle to put it elsewhere in the order
    @SuppressLint("ClickableViewAccessibility")
    private void itemRow(StatsPrefs.Item item, StatsPrefs.Style rowsStyle) {
        View row = switchRow(itemRows, activity.getString(item.labelRes),
                () -> StatsPrefs.items(activity, rowsStyle).contains(item), on -> {
                    StatsPrefs.setItem(activity, rowsStyle, item, on);
                    preview.showStyle(rowsStyle, true);
                });
        row.setTag(item);
        row.setPadding(dp(4), 0, dp(14), 0);
        // The selected look while it moves
        row.setBackground(ApolloUi.stateLayer(colors.surfaceContainerHigh, colors.secondaryContainer, dp(ApolloUi.ROW_RADIUS_DP)));

        ImageView handle = new ImageView(activity);
        handle.setImageResource(R.drawable.ic_apollo_reorder);
        handle.setImageTintList(ColorStateList.valueOf(colors.outline));
        handle.setPadding(dp(8), dp(8), dp(8), dp(8));
        handle.setContentDescription(activity.getString(R.string.apollo_hint_move));
        ((LinearLayout) row).addView(handle, 0, new LinearLayout.LayoutParams(dp(36), dp(40)));
        reorder.attachHandle(handle, row);
        reorder.attachLongPress(row);

        row.setOnKeyListener((v, keyCode, event) -> onItemKey(row, keyCode, event));
        setItemHints(row, false);
    }

    private void setItemHints(View row, boolean moving) {
        if (moving) {
            HintRow.set(row, new HintRow.Hint[]{
                    HintRow.hint(activity, ButtonGlyph.DPAD_UP_DOWN, R.string.apollo_hint_move),
                    HintRow.hint(activity, KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_done),
                    HintRow.hint(activity, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_cancel)});
        } else {
            HintRow.set(row, new HintRow.Hint[]{
                    HintRow.hint(activity, KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_change),
                    HintRow.hint(activity, KeyEvent.KEYCODE_BUTTON_X, R.string.apollo_hint_move),
                    HintRow.hint(activity, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_back)});
        }
        HintRow.refreshAll();
    }

    // X picks the row up, the D-pad moves it, A or X puts it down, B puts it back
    private boolean onItemKey(View row, int keyCode, KeyEvent event) {
        if (movingRow == null) {
            if (keyCode == KeyEvent.KEYCODE_BUTTON_X) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    movingRow = row;
                    orderBeforeMove = StatsPrefs.order(activity, style);
                    row.setActivated(true);
                    setItemHints(row, true);
                }
                return true;
            }
            return false;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                    || keyCode == KeyEvent.KEYCODE_BUTTON_A || keyCode == KeyEvent.KEYCODE_BUTTON_X
                    || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_BUTTON_B;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                reorder.step(row, -1);
                saveOrder();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                reorder.step(row, 1);
                saveOrder();
                return true;
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BUTTON_X:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                finishMove();
                return true;
            case KeyEvent.KEYCODE_BUTTON_B:
                // The activity puts it back
                return false;
        }
        return true;
    }

    private void finishMove() {
        View row = movingRow;
        movingRow = null;
        orderBeforeMove = null;
        if (row != null) {
            row.setActivated(false);
            setItemHints(row, false);
        }
    }

    // B while moving: back to the order it had
    private boolean cancelMove() {
        if (movingRow == null) {
            return false;
        }
        List<StatsPrefs.Item> before = orderBeforeMove;
        View row = movingRow;
        finishMove();
        StatsPrefs.setOrder(activity, style, before);
        preview.showStyle(style, true);
        buildItemRowsKeepingFocus(row.getTag());
        return true;
    }

    private void buildItemRowsKeepingFocus(Object item) {
        buildItemRows();
        for (int i = 1; i < itemRows.getChildCount(); i++) {
            if (itemRows.getChildAt(i).getTag() == item) {
                itemRows.getChildAt(i).requestFocus();
            }
        }
    }

    // The order on screen becomes the style's order
    private void saveOrder() {
        List<StatsPrefs.Item> order = new ArrayList<>();
        for (int i = 1; i < itemRows.getChildCount(); i++) {
            order.add((StatsPrefs.Item) itemRows.getChildAt(i).getTag());
        }
        StatsPrefs.setOrder(activity, style, order);
        preview.showStyle(style, true);
    }

    /** After the defaults came back from the gamepad: the focus on the first row. */
    public void focusFirstRow() {
        if (!isInTouchMode()) {
            firstRow.requestFocus();
        }
    }

    /** B puts back a row being moved, then closes the menu of the position. */
    public boolean onButtonB() {
        return cancelMove() || popup.dismiss(true);
    }

    /**
     * The preview: a picture of the whole screen in its proportions, with the overlay laid out at the real size
     * of the screen and scaled down to fit.
     */
    private static class PreviewBox extends FrameLayout {
        private final FrameLayout screen;
        private final int screenWidth, screenHeight;

        PreviewBox(Activity activity, StatsOverlayView overlay) {
            super(activity);
            Point size = new Point();
            activity.getWindowManager().getDefaultDisplay().getRealSize(size);
            screenWidth = Math.max(size.x, size.y);
            screenHeight = Math.min(size.x, size.y);

            setBackground(ApolloUi.roundRect(0xFF0D1016, ApolloUi.dp(activity, 12)));
            setClipToOutline(true);

            screen = new FrameLayout(activity);
            // A dusk sky over dark ground, so the veil of the overlay shows as it does over a game
            GradientDrawable scene = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {0xFF24364D, 0xFF3D5168, 0xFF2A2F26, 0xFF1B1F18});
            screen.setBackground(scene);
            screen.setPivotX(0);
            screen.setPivotY(0);
            overlay.setVisibility(VISIBLE);
            screen.addView(overlay, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            addView(screen, new LayoutParams(screenWidth, screenHeight));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = Math.round(width * screenHeight / (float) screenWidth);
            // Inside the column on a short screen
            if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
                int maxHeight = MeasureSpec.getSize(heightMeasureSpec);
                if (height > maxHeight) {
                    height = maxHeight;
                    width = Math.round(height * screenWidth / (float) screenHeight);
                }
            }
            screen.measure(MeasureSpec.makeMeasureSpec(screenWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(screenHeight, MeasureSpec.EXACTLY));
            setMeasuredDimension(width, height);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            screen.layout(0, 0, screenWidth, screenHeight);
            float scale = (right - left) / (float) screenWidth;
            screen.setScaleX(scale);
            screen.setScaleY(scale);
        }
    }
}
