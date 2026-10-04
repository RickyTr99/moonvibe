package com.limelight.ui.apollo.settings;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.limelight.R;
import com.limelight.ui.apollo.ApolloUi;
import com.limelight.ui.apollo.hints.HintRow;
import com.limelight.ui.theme.ApolloColors;
import com.limelight.ui.theme.ApolloMotion;

/**
 * The search box on top of the categories: a lens, the text and, while there is some, a button that clears it.
 * A on a gamepad opens the keyboard; its search key hands the focus to the results.
 */
final class SearchField extends LinearLayout {
    interface Listener {
        void onQueryChanged(String query);

        /** The search key of the keyboard: the focus can go to the results */
        void onSearchSubmitted();
    }

    final EditText edit;
    private final ImageView clear;
    private final ApolloColors colors;
    private final GradientDrawable background;
    private Listener listener;

    SearchField(Context context, ApolloColors colors) {
        super(context);
        this.colors = colors;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(14), 0, dp(4), 0);
        background = ApolloUi.roundRect(colors.surfaceContainerHigh, dp(ApolloUi.ROW_RADIUS_DP));
        setBackground(background);

        ImageView lens = new ImageView(context);
        lens.setImageResource(R.drawable.ic_apollo_search);
        lens.setImageTintList(ColorStateList.valueOf(colors.onSurfaceVariant));
        addView(lens, new LayoutParams(dp(18), dp(18)));

        edit = new EditText(context);
        edit.setId(View.generateViewId());
        edit.setBackground(null);
        edit.setPadding(dp(10), 0, dp(4), 0);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        edit.setTextColor(colors.onSurface);
        edit.setHintTextColor(colors.outline);
        edit.setHint(R.string.apollo_search_hint);
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        // A keyboard that leaves the screen visible, so the results show while typing
        edit.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            GradientDrawable cursor = new GradientDrawable();
            cursor.setColor(colors.primary);
            cursor.setSize(dp(2), 0);
            edit.setTextCursorDrawable(cursor);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            edit.setDefaultFocusHighlightEnabled(false);
        }
        HintRow.set(edit, KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_write, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_close_search);
        addView(edit, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        clear = new ImageView(context);
        clear.setImageResource(R.drawable.ic_apollo_clear);
        clear.setImageTintList(ColorStateList.valueOf(colors.onSurfaceVariant));
        clear.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        clear.setPadding(dp(8), dp(8), dp(8), dp(8));
        clear.setContentDescription(context.getString(R.string.apollo_search_clear));
        clear.setBackground(ApolloUi.pressLayer(dp(16)));
        clear.setVisibility(GONE);
        clear.setOnClickListener(v -> setQuery(""));
        addView(clear, new LayoutParams(dp(32), dp(32)));

        edit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                boolean hasText = s.length() > 0;
                if (hasText != (clear.getVisibility() == VISIBLE)) {
                    clear.setVisibility(hasText ? VISIBLE : GONE);
                    if (hasText) {
                        clear.setAlpha(0f);
                        clear.animate().alpha(1f).setDuration(ApolloMotion.SHORT).start();
                    }
                }
                if (listener != null) {
                    listener.onQueryChanged(s.toString().trim());
                }
            }
        });
        edit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard();
                if (listener != null) {
                    listener.onSearchSubmitted();
                }
                return true;
            }
            return false;
        });
        // A little lighter while it has the focus, like the rows
        edit.setOnFocusChangeListener((v, hasFocus) ->
                background.setColor(hasFocus ? colors.surfaceContainerHighest : colors.surfaceContainerHigh));
        // A tap anywhere on the box goes to the text
        setOnClickListener(v -> openKeyboard());
    }

    private int dp(float value) {
        return ApolloUi.dp(getContext(), value);
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    String query() {
        return edit.getText().toString().trim();
    }

    void setQuery(String query) {
        edit.setText(query);
        edit.setSelection(edit.length());
    }

    /** Focus on the text and the keyboard up: X on a gamepad, or a tap */
    void openKeyboard() {
        edit.requestFocus();
        edit.post(() -> {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(edit.getWindowToken(), 0);
    }
}
