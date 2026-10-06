package com.limelight.preferences;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.ui.apollo.ApolloDialog;
import com.limelight.ui.apollo.ApolloUi;
import com.limelight.ui.overlay.CustomCommand;
import com.limelight.ui.overlay.OverlayIcons;
import com.limelight.ui.theme.ApolloColors;

import java.util.UUID;

/**
 * Adds or edits a custom command of the game menu: its name, icon, keys and what happens after.
 * A popup in the look of the app, over the settings or over the stream.
 */
public class CustomCommandEditorDialog extends DialogFragment {
    private static final String ARG_COMMAND = "command";
    private static final String ARG_IS_EDIT = "is_edit";

    private static final String[] KEY_NAMES = {
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
            "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
            "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12",
            "Win", "Space", "Enter", "Tab", "Esc", "Backspace", "Delete",
            "←", "→", "↑", "↓", "Insert", "Home", "End", "Page Up", "Page Down",
            "Print Screen", "Pause"
    };
    private static final int[] KEY_CODES = {
            KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_D,
            KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H,
            KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L,
            KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P,
            KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_T,
            KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_X,
            KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_Z,
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3,
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7,
            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9,
            KeyEvent.KEYCODE_F1, KeyEvent.KEYCODE_F2, KeyEvent.KEYCODE_F3, KeyEvent.KEYCODE_F4,
            KeyEvent.KEYCODE_F5, KeyEvent.KEYCODE_F6, KeyEvent.KEYCODE_F7, KeyEvent.KEYCODE_F8,
            KeyEvent.KEYCODE_F9, KeyEvent.KEYCODE_F10, KeyEvent.KEYCODE_F11, KeyEvent.KEYCODE_F12,
            KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_TAB,
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_INSERT, KeyEvent.KEYCODE_MOVE_HOME,
            KeyEvent.KEYCODE_MOVE_END, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_SYSRQ, KeyEvent.KEYCODE_BREAK
    };

    private CustomCommand editingCommand;
    private boolean isEditMode;
    private int selectedIconResId = R.drawable.ic_overlay_key_press;
    private int selectedKeyCode = 0;
    private int postAction = CustomCommand.POST_ACTION_NONE;
    private final boolean[] modifiers = new boolean[4];

    private ApolloDialog popup;
    private EditText nameInput;
    private ImageView iconView;
    private TextView keyChip;
    private TextView thenValue;

    private OnCommandSavedListener listener;

    public interface OnCommandSavedListener {
        void onCommandSaved(CustomCommand command);
    }

    public static CustomCommandEditorDialog newInstance(CustomCommand command) {
        CustomCommandEditorDialog dialog = new CustomCommandEditorDialog();
        Bundle args = new Bundle();
        try {
            args.putString(ARG_COMMAND, command.toJson().toString());
        } catch (org.json.JSONException e) {
            e.printStackTrace();
        }
        args.putBoolean(ARG_IS_EDIT, true);
        dialog.setArguments(args);
        return dialog;
    }

    public void setOnCommandSavedListener(OnCommandSavedListener listener) {
        this.listener = listener;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String name = "";
        if (getArguments() != null && getArguments().getBoolean(ARG_IS_EDIT, false)) {
            String json = getArguments().getString(ARG_COMMAND);
            if (json != null) {
                try {
                    editingCommand = CustomCommand.fromJson(new org.json.JSONObject(json));
                    isEditMode = true;
                    selectedIconResId = editingCommand.getIconResId();
                    CustomCommand.KeyCombination keys = editingCommand.getKeyCombination();
                    selectedKeyCode = keys.getKeyCode();
                    modifiers[0] = keys.isCtrl();
                    modifiers[1] = keys.isAlt();
                    modifiers[2] = keys.isShift();
                    modifiers[3] = keys.isMeta();
                    postAction = editingCommand.getPostAction();
                    // A command without a name shows its keys, which are not a name to edit
                    name = editingCommand.getName().equals(keys.toDisplayString()) ? "" : editingCommand.getName();
                } catch (org.json.JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        Context context = getActivity();
        popup = new ApolloDialog(context, getString(isEditMode ? R.string.editor_title_edit : R.string.editor_title_add), 560);
        ApolloColors colors = popup.colors;

        // Name: written in place; empty, the command shows its keys
        nameInput = new EditText(context);
        nameInput.setText(name);
        nameInput.setSingleLine(true);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        nameInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        nameInput.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        nameInput.setBackground(null);
        nameInput.setPadding(popup.dp(12), 0, 0, 0);
        nameInput.setTextColor(colors.onSurfaceVariant);
        nameInput.setHintTextColor(colors.outline);
        nameInput.setTextSize(14);
        LinearLayout nameRow = popup.row(getString(R.string.editor_command_name), null, v -> editName());
        nameRow.addView(nameInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
        // The row has the focus; A or a tap writes in the field
        nameInput.setFocusable(false);
        nameInput.setOnClickListener(v -> editName());
        nameInput.setOnEditorActionListener((v, actionId, event) -> {
            finishName(nameRow);
            return true;
        });
        nameInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                finishName(null);
            }
        });
        popup.body.addView(nameRow);

        iconView = new ImageView(context);
        iconView.setImageResource(selectedIconResId);
        iconView.setImageTintList(ColorStateList.valueOf(colors.onSurfaceVariant));
        int pad = popup.dp(6);
        iconView.setPadding(pad, pad, pad, pad);
        iconView.setBackground(ApolloUi.roundRect(colors.surfaceContainerHighest, popup.dp(8)));
        iconView.setLayoutParams(new LinearLayout.LayoutParams(popup.dp(32), popup.dp(32)));
        popup.body.addView(popup.row(getString(R.string.editor_icon), iconView, v -> pickIcon()));

        // Keys: the modifiers as chips to turn on, then the key
        popup.body.addView(popup.sectionHeader(getString(R.string.editor_key_combination)));
        LinearLayout keysRow = new LinearLayout(context);
        keysRow.setOrientation(LinearLayout.HORIZONTAL);
        keysRow.setBaselineAligned(false);
        keysRow.setGravity(Gravity.CENTER_VERTICAL);
        keysRow.setPadding(popup.dp(16), 0, popup.dp(16), popup.dp(4));
        String[] modifierNames = {"Ctrl", "Alt", "Shift", "Win"};
        for (int i = 0; i < modifierNames.length; i++) {
            final int index = i;
            TextView chip = chip(modifierNames[i], colors);
            chip.setActivated(modifiers[i]);
            styleChipText(chip, colors);
            chip.setOnClickListener(v -> {
                modifiers[index] = !modifiers[index];
                v.setActivated(modifiers[index]);
                styleChipText((TextView) v, colors);
                updateNameHint();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, popup.dp(38));
            params.setMarginEnd(popup.dp(8));
            keysRow.addView(chip, params);
        }
        TextView plus = ApolloUi.text(context, "+", 18, colors.outline, false);
        plus.setPadding(popup.dp(2), 0, popup.dp(10), 0);
        keysRow.addView(plus);
        keyChip = chip("", colors);
        keyChip.setActivated(false);
        keyChip.setOnClickListener(v -> pickKey());
        keysRow.addView(keyChip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, popup.dp(38)));
        popup.body.addView(keysRow);
        showKey();

        thenValue = ApolloUi.text(context, "", 14, colors.onSurfaceVariant, false);
        showThen();
        LinearLayout thenRow = popup.row(getString(R.string.editor_then), thenValue, v -> pickThen());
        LinearLayout.LayoutParams thenParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        thenParams.topMargin = popup.dp(4);
        popup.body.addView(thenRow, thenParams);

        popup.buttons(getString(R.string.editor_cancel), getString(R.string.editor_save), () -> {
            if (validateAndSave()) {
                dismiss();
            }
        });

        updateNameHint();
        nameRow.post(nameRow::requestFocus);
        return popup.dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        // ApolloDialog sets the width and the entry animation; a DialogFragment shows the dialog itself
        if (popup != null && popup.card.getAlpha() == 1f) {
            popup.card.setAlpha(0f);
            popup.card.setScaleX(0.94f);
            popup.card.setScaleY(0.94f);
            popup.card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(com.limelight.ui.theme.ApolloMotion.MEDIUM)
                    .setInterpolator(com.limelight.ui.theme.ApolloMotion.EMPHASIZED_DECELERATE).start();
        }
    }

    // A chip of the keys: filled when on, outlined when off, a light veil with the focus
    private TextView chip(String text, ApolloColors colors) {
        TextView chip = ApolloUi.text(getActivity(), text, 14, colors.onSurfaceVariant, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(popup.dp(14), 0, popup.dp(14), 0);
        chip.setMinWidth(popup.dp(44));
        chip.setFocusable(true);
        chip.setClickable(true);
        int radius = popup.dp(10);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_activated, android.R.attr.state_focused},
                ApolloUi.roundRect(ApolloColors.blend(colors.secondaryContainer, Color.WHITE, 0.12f), radius));
        states.addState(new int[]{android.R.attr.state_activated}, ApolloUi.roundRect(colors.secondaryContainer, radius));
        states.addState(new int[]{android.R.attr.state_focused}, outlined(colors.surfaceContainerHighest, colors.outline, radius));
        states.addState(new int[]{}, outlined(Color.TRANSPARENT, colors.outlineVariant, radius));
        chip.setBackground(states);
        return chip;
    }

    private GradientDrawable outlined(int fill, int stroke, int radius) {
        GradientDrawable drawable = ApolloUi.roundRect(fill, radius);
        drawable.setStroke(popup.dp(1), stroke);
        return drawable;
    }

    private static void styleChipText(TextView chip, ApolloColors colors) {
        chip.setTextColor(chip.isActivated() ? colors.onSecondaryContainer : colors.onSurfaceVariant);
    }

    private void editName() {
        nameInput.setFocusable(true);
        nameInput.setFocusableInTouchMode(true);
        nameInput.requestFocus();
        nameInput.setSelection(nameInput.getText().length());
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(nameInput, InputMethodManager.SHOW_IMPLICIT);
    }

    private void finishName(View focusAfter) {
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(nameInput.getWindowToken(), 0);
        if (focusAfter != null) {
            focusAfter.requestFocus();
        }
        nameInput.setFocusable(false);
    }

    private void showKey() {
        String name = keyName(selectedKeyCode);
        keyChip.setText(name != null ? name : getString(R.string.editor_key_code_hint));
        keyChip.setTextColor(name != null ? popup.colors.onSurface : popup.colors.primary);
    }

    private void showThen() {
        String[] actions = getResources().getStringArray(R.array.custom_command_post_actions);
        thenValue.setText(postAction >= 0 && postAction < actions.length ? actions[postAction] : "");
    }

    private static String keyName(int keyCode) {
        for (int i = 0; i < KEY_CODES.length; i++) {
            if (KEY_CODES[i] == keyCode) {
                return KEY_NAMES[i];
            }
        }
        if (keyCode == 0) {
            return null;
        }
        String name = KeyEvent.keyCodeToString(keyCode);
        return name.startsWith("KEYCODE_") ? name.substring(8) : name;
    }

    private void pickIcon() {
        Context context = getActivity();
        View[] cells = new View[OverlayIcons.AVAILABLE_ICONS.length];
        int selected = -1;
        for (int i = 0; i < cells.length; i++) {
            ImageView icon = new ImageView(context);
            icon.setImageResource(OverlayIcons.AVAILABLE_ICONS[i].resourceId);
            icon.setImageTintList(ColorStateList.valueOf(popup.colors.onSurface));
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            int pad = popup.dp(12);
            icon.setPadding(pad, pad, pad, pad);
            cells[i] = icon;
            if (OverlayIcons.AVAILABLE_ICONS[i].resourceId == selectedIconResId) {
                selected = i;
            }
        }
        ApolloDialog.grid(context, getString(R.string.editor_icon_select), cells, 8, selected, index -> {
            selectedIconResId = OverlayIcons.AVAILABLE_ICONS[index].resourceId;
            iconView.setImageResource(selectedIconResId);
        });
    }

    private void pickKey() {
        Context context = getActivity();
        View[] cells = new View[KEY_NAMES.length];
        int selected = -1;
        for (int i = 0; i < cells.length; i++) {
            TextView key = ApolloUi.text(context, KEY_NAMES[i], 13, popup.colors.onSurface, true);
            key.setGravity(Gravity.CENTER);
            key.setSingleLine(true);
            cells[i] = key;
            if (KEY_CODES[i] == selectedKeyCode) {
                selected = i;
            }
        }
        ApolloDialog.grid(context, getString(R.string.editor_key_code_hint), cells, 8, selected, index -> {
            selectedKeyCode = KEY_CODES[index];
            showKey();
            updateNameHint();
        });
    }

    private void pickThen() {
        String[] actions = getResources().getStringArray(R.array.custom_command_post_actions);
        ApolloDialog.choose(getActivity(), getString(R.string.editor_then), actions, postAction, index -> {
            postAction = index;
            showThen();
        });
    }

    // With no name, the command is shown with its keys: they are the hint of the field
    private void updateNameHint() {
        nameInput.setHint(selectedKeyCode != 0 ? keys().toDisplayString() : "");
    }

    private CustomCommand.KeyCombination keys() {
        return new CustomCommand.KeyCombination(modifiers[0], modifiers[1], modifiers[2], modifiers[3], selectedKeyCode);
    }

    private boolean validateAndSave() {
        if (selectedKeyCode == 0) {
            Toast.makeText(getActivity(), R.string.editor_error_key_empty, Toast.LENGTH_SHORT).show();
            keyChip.requestFocus();
            return false;
        }

        String name = nameInput.getText().toString().trim();
        String id = isEditMode ? editingCommand.getId() : UUID.randomUUID().toString();
        CustomCommand command = new CustomCommand(id, name, selectedIconResId, keys(), postAction);
        if (listener != null) {
            listener.onCommandSaved(command);
        }

        if (postAction == CustomCommand.POST_ACTION_SLEEP) {
            android.app.admin.DevicePolicyManager dpm =
                    (android.app.admin.DevicePolicyManager) getActivity().getSystemService(Context.DEVICE_POLICY_SERVICE);
            android.content.ComponentName adminComponent =
                    new android.content.ComponentName(getActivity(), com.limelight.SleepDeviceAdmin.class);
            if (!dpm.isAdminActive(adminComponent)) {
                android.content.Intent intent = new android.content.Intent(
                        android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
                intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        getString(R.string.sleep_admin_explanation));
                getActivity().startActivity(intent);
            }
        }
        return true;
    }
}
