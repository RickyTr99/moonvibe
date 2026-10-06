package com.limelight.preferences;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.ui.apollo.ActionSheet;
import com.limelight.ui.apollo.ApolloUi;
import com.limelight.ui.apollo.hints.ButtonGlyph;
import com.limelight.ui.apollo.hints.HintRow;
import com.limelight.ui.apollo.hints.ScreenHints;
import com.limelight.ui.overlay.CustomCommand;
import com.limelight.ui.overlay.CustomCommandsManager;
import com.limelight.ui.theme.ApolloColors;
import com.limelight.ui.theme.ApolloMotion;
import com.limelight.utils.UiHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * The custom commands of the game menu, like "Manage profiles": reordered by dragging the handle
 * (X then up/down on a gamepad), a tap or A to edit one, Y or a long press for its options, and a
 * new command at the end.
 */
public class CustomCommandsActivity extends Activity {
    private static final int COLUMN_MAX_WIDTH_DP = 640;

    private CustomCommandsManager commandsManager;
    private ApolloColors colors;
    private LinearLayout rowsBox;
    private View newRow;
    private HintRow hintRow;
    // The row moved with the gamepad, or null
    private View movingRow;
    private List<CustomCommand> orderBeforeMove;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiHelper.setLocale(this);
        overridePendingTransition(R.anim.apollo_fade_in, R.anim.apollo_fade_out);
        colors = ApolloColors.dark(this);
        commandsManager = new CustomCommandsManager(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // Back arrow and title
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), 0, dp(20), 0);
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_apollo_arrow_left);
        back.setImageTintList(ColorStateList.valueOf(colors.onSurface));
        back.setPadding(dp(8), dp(8), dp(8), dp(8));
        back.setFocusable(false);
        back.setContentDescription(getString(R.string.apollo_hint_back));
        back.setOnClickListener(v -> finish());
        back.setBackground(ApolloUi.pressLayer(dp(20)));
        header.addView(back, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView title = ApolloUi.text(this, getString(R.string.title_overlay_custom_commands), 20, colors.onSurface, true);
        title.setPadding(dp(8), 0, 0, 0);
        header.addView(title);
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        // The list in a card in the middle, always whole on screen: a long list scrolls inside it
        FrameLayout center = new FrameLayout(this);
        center.setPadding(dp(16), 0, dp(16), dp(16));
        LinearLayout column = new LinearLayout(this) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = Math.min(MeasureSpec.getSize(widthMeasureSpec), dp(COLUMN_MAX_WIDTH_DP));
                super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);
            }
        };
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackground(ApolloUi.roundRect(colors.surfaceContainerLow, dp(20)));
        column.setClipToOutline(true);
        column.setPadding(dp(6), dp(10), dp(6), dp(10));
        center.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        root.addView(center, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // A new command first, so it is there without scrolling the list
        newRow = newCommandRow();
        column.addView(newRow);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        rowsBox = new LinearLayout(this);
        rowsBox.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rowsBox);
        column.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        UiHelper.notifyNewRootView(this);
        ApolloUi.padForCutout(root);

        hintRow = ScreenHints.attach(this, root);
        hintRow.setFallback(HintRow.hint(this, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_back));

        rebuild();
    }

    private int dp(float value) {
        return ApolloUi.dp(this, value);
    }

    // --- Rows

    private void rebuild() {
        View focused = getCurrentFocus();
        int focusedIndex = focused != null ? rowsBox.indexOfChild(focused) : -1;

        commandsManager.reload();
        rowsBox.removeAllViews();
        for (CustomCommand command : commandsManager.getCommands()) {
            rowsBox.addView(commandRow(command));
        }

        View target = focusedIndex >= 0 && focusedIndex < rowsBox.getChildCount()
                ? rowsBox.getChildAt(focusedIndex) : rowsBox.getChildCount() > 0 ? rowsBox.getChildAt(0) : newRow;
        ApolloUi.focusByDefault(this, target);
        if (focused != null) {
            target.requestFocus();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private View commandRow(CustomCommand command) {
        LinearLayout row = new LinearLayout(this);
        row.setTag(command);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));
        row.setPadding(dp(4), dp(6), dp(14), dp(6));
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(ApolloUi.stateLayer(colors.surfaceContainerHigh, colors.secondaryContainer, dp(ApolloUi.ROW_RADIUS_DP)));

        ImageView handle = new ImageView(this);
        handle.setImageResource(R.drawable.ic_apollo_reorder);
        handle.setImageTintList(ColorStateList.valueOf(colors.outline));
        handle.setPadding(dp(8), dp(8), dp(8), dp(8));
        handle.setContentDescription(getString(R.string.apollo_hint_move));
        row.addView(handle, new LinearLayout.LayoutParams(dp(36), dp(40)));
        handle.setOnTouchListener(new DragListener(row));

        ImageView icon = new ImageView(this);
        icon.setImageResource(command.getIconResId());
        icon.setImageTintList(ColorStateList.valueOf(colors.onSurfaceVariant));
        int pad = dp(5);
        icon.setPadding(pad, pad, pad, pad);
        icon.setBackground(ApolloUi.roundRect(colors.surfaceContainerHighest, dp(7)));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        iconParams.setMarginStart(dp(4));
        row.addView(icon, iconParams);

        TextView name = ApolloUi.text(this, command.getName(), 14.5f, colors.onSurface, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setPadding(dp(12), 0, dp(12), 0);
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        // The keys, one chip each
        LinearLayout keys = new LinearLayout(this);
        keys.setOrientation(LinearLayout.HORIZONTAL);
        for (String key : command.getKeyCombination().toDisplayString().split("\\+")) {
            TextView chip = ApolloUi.text(this, key.trim(), 12, colors.onSurfaceVariant, false);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(8), dp(3), dp(8), dp(3));
            chip.setBackground(ApolloUi.roundRect(colors.surfaceContainerHighest, dp(6)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginStart(dp(4));
            keys.addView(chip, params);
        }
        row.addView(keys);

        row.setOnClickListener(v -> {
            if (movingRow == null) {
                edit(command);
            }
        });
        row.setOnLongClickListener(v -> {
            if (movingRow == null) {
                showOptions(command);
            }
            return true;
        });
        row.setOnKeyListener((v, keyCode, event) -> onRowKey(row, command, keyCode, event));
        setRowHints(row, false);
        return row;
    }

    private void setRowHints(View row, boolean moving) {
        if (moving) {
            HintRow.set(row, new HintRow.Hint[]{
                    HintRow.hint(this, ButtonGlyph.DPAD_UP_DOWN, R.string.apollo_hint_move),
                    HintRow.hint(this, KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_done),
                    HintRow.hint(this, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_cancel)});
        } else {
            HintRow.set(row, new HintRow.Hint[]{
                    HintRow.hint(this, KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_edit),
                    HintRow.hint(this, KeyEvent.KEYCODE_BUTTON_X, R.string.apollo_hint_move),
                    HintRow.hint(this, KeyEvent.KEYCODE_BUTTON_Y, R.string.apollo_hint_options),
                    HintRow.hint(this, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_back)});
        }
    }

    private View newCommandRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(48));
        row.setPadding(dp(14), 0, dp(14), 0);
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(ApolloUi.stateLayer(colors.surfaceContainerHigh, Color.TRANSPARENT, dp(ApolloUi.ROW_RADIUS_DP)));
        ImageView plus = new ImageView(this);
        plus.setImageResource(R.drawable.ic_apollo_add);
        plus.setImageTintList(ColorStateList.valueOf(colors.primary));
        row.addView(plus, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView label = ApolloUi.text(this, getString(R.string.custom_commands_add), 14, colors.primary, true);
        label.setPadding(dp(12), 0, 0, 0);
        row.addView(label);
        row.setOnClickListener(v -> edit(null));
        HintRow.set(row, KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_open, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_back);
        return row;
    }

    // Edits the command, or adds a new one if it is null
    private void edit(CustomCommand command) {
        CustomCommandEditorDialog dialog = command == null
                ? new CustomCommandEditorDialog()
                : CustomCommandEditorDialog.newInstance(command);
        dialog.setOnCommandSavedListener(saved -> {
            if (command == null) {
                commandsManager.addCommand(saved);
            } else {
                commandsManager.updateCommand(command.getId(), saved);
            }
            rebuild();
        });
        dialog.show(getFragmentManager(), "command_editor");
    }

    private void showOptions(CustomCommand command) {
        List<ActionSheet.Action> actions = new ArrayList<>();
        actions.add(new ActionSheet.Action(R.drawable.ic_apollo_edit, getString(R.string.apollo_hint_edit), () -> edit(command)));
        actions.add(new ActionSheet.Action(R.drawable.ic_apollo_delete, getString(R.string.custom_commands_delete), () -> {
            commandsManager.removeCommand(command.getId());
            rebuild();
        }).danger());
        ActionSheet.of(this).show(command.getName(), command.getKeyCombination().toDisplayString(), actions);
    }

    // --- Reordering

    private void saveOrder() {
        List<CustomCommand> order = new ArrayList<>();
        for (int i = 0; i < rowsBox.getChildCount(); i++) {
            order.add((CustomCommand) rowsBox.getChildAt(i).getTag());
        }
        saveOrder(order);
    }

    private void saveOrder(List<CustomCommand> order) {
        commandsManager.clearAllCommands();
        for (CustomCommand command : order) {
            commandsManager.addCommand(command);
        }
    }

    private boolean onRowKey(View row, CustomCommand command, int keyCode, KeyEvent event) {
        if (movingRow == null) {
            if (keyCode == KeyEvent.KEYCODE_BUTTON_X || keyCode == KeyEvent.KEYCODE_BUTTON_Y) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    if (keyCode == KeyEvent.KEYCODE_BUTTON_X) {
                        startMove(row);
                    } else {
                        showOptions(command);
                    }
                }
                return true;
            }
            return false;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                    || keyCode == KeyEvent.KEYCODE_BUTTON_A || keyCode == KeyEvent.KEYCODE_BUTTON_B
                    || keyCode == KeyEvent.KEYCODE_BUTTON_X || keyCode == KeyEvent.KEYCODE_DPAD_CENTER;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                step(row, -1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                step(row, 1);
                return true;
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BUTTON_X:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                finishMove(true);
                return true;
            case KeyEvent.KEYCODE_BUTTON_B:
                finishMove(false);
                return true;
        }
        return true;
    }

    private void startMove(View row) {
        movingRow = row;
        orderBeforeMove = commandsManager.getCommands();
        row.setActivated(true);
        setRowHints(row, true);
        hintRow.refresh();
    }

    private void finishMove(boolean keep) {
        if (movingRow == null) {
            return;
        }
        View row = movingRow;
        movingRow = null;
        row.setActivated(false);
        setRowHints(row, false);
        if (keep) {
            saveOrder();
        } else {
            saveOrder(orderBeforeMove);
            rebuild();
        }
        hintRow.refresh();
    }

    // One place up or down; the neighbor slides into the place left
    private void step(View row, int direction) {
        int index = rowsBox.indexOfChild(row);
        int target = index + direction;
        if (target < 0 || target >= rowsBox.getChildCount()) {
            return;
        }
        View neighbor = rowsBox.getChildAt(target);
        int distance = direction > 0 ? neighbor.getHeight() : -neighbor.getHeight();
        rowsBox.removeView(row);
        rowsBox.addView(row, target);
        row.requestFocus();
        row.setTranslationY(-distance);
        row.animate().translationY(0).setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD).start();
        neighbor.setTranslationY(distance > 0 ? row.getHeight() : -row.getHeight());
        neighbor.animate().translationY(0).setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD).start();
    }

    // Dragging a row by its handle: it follows the finger and swaps places with the rows it passes
    private class DragListener implements View.OnTouchListener {
        private final View row;
        private float startY;
        private float offset;

        DragListener(View row) {
            this.row = row;
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startY = event.getRawY();
                    offset = 0;
                    row.setActivated(true);
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dy = event.getRawY() - startY - offset;
                    int index = rowsBox.indexOfChild(row);
                    if (dy > row.getHeight() / 2f && index < rowsBox.getChildCount() - 1) {
                        View neighbor = rowsBox.getChildAt(index + 1);
                        swap(index + 1, neighbor, -row.getHeight());
                        offset += neighbor.getHeight();
                        dy -= neighbor.getHeight();
                    } else if (dy < -row.getHeight() / 2f && index > 0) {
                        View neighbor = rowsBox.getChildAt(index - 1);
                        swap(index - 1, neighbor, row.getHeight());
                        offset -= neighbor.getHeight();
                        dy += neighbor.getHeight();
                    }
                    row.setTranslationY(dy);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    row.animate().translationY(0).setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD)
                            .withEndAction(() -> row.setActivated(false)).start();
                    saveOrder();
                    return true;
            }
            return false;
        }

        private void swap(int to, View neighbor, int neighborShift) {
            rowsBox.removeView(row);
            rowsBox.addView(row, to);
            neighbor.setTranslationY(neighborShift);
            neighbor.animate().translationY(0).setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD).start();
        }
    }

    // --- Keys

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            if (ActionSheet.of(this).dismiss()) {
                return true;
            }
            if (movingRow != null) {
                finishMove(false);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (ActionSheet.of(this).dismiss()) {
            return;
        }
        if (movingRow != null) {
            finishMove(false);
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.apollo_fade_in, R.anim.apollo_fade_out);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        ScreenHints.onKeyEvent(event);
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        ScreenHints.onTouchEvent(event);
        return super.dispatchTouchEvent(event);
    }
}
