package com.limelight.preferences;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ReplacementSpan;
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
import com.limelight.profiles.Profiles;
import com.limelight.ui.apollo.ApolloUi;
import com.limelight.ui.apollo.hints.ButtonGlyph;
import com.limelight.ui.apollo.hints.HintRow;
import com.limelight.ui.apollo.hints.ScreenHints;
import com.limelight.ui.theme.ApolloColors;
import com.limelight.ui.theme.ApolloMotion;
import com.limelight.utils.UiHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Manage profiles: the list in the order of the profiles menu, reordered by dragging the handle
 * (X then up/down on a gamepad), a tap or A to edit one, and a new profile at the end.
 */
public class ProfilesActivity extends Activity {
    private static final int COLUMN_MAX_WIDTH_DP = 640;

    private ApolloColors colors;
    private LinearLayout rowsBox;
    private View newRow;
    private HintRow hintRow;
    private final List<Profiles.Profile> profiles = new ArrayList<>();
    // The row moved with the gamepad, or null
    private View movingRow;
    private List<Profiles.Profile> orderBeforeMove;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiHelper.setLocale(this);
        overridePendingTransition(R.anim.apollo_fade_in, R.anim.apollo_fade_out);
        colors = ApolloColors.dark(this);

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
        TextView title = ApolloUi.text(this, getString(R.string.apollo_profiles), 20, colors.onSurface, true);
        title.setPadding(dp(8), 0, 0, 0);
        header.addView(title);
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        // The list in a column in the middle, scrolling when long
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        FrameLayout center = new FrameLayout(this);
        center.setPadding(dp(16), 0, dp(16), dp(16));
        scroll.addView(center);
        LinearLayout column = new LinearLayout(this) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = Math.min(MeasureSpec.getSize(widthMeasureSpec), dp(COLUMN_MAX_WIDTH_DP));
                super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);
            }
        };
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackground(ApolloUi.roundRect(colors.surfaceContainerLow, dp(20)));
        column.setPadding(dp(6), dp(10), dp(6), dp(10));
        center.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        TextView explanation = ApolloUi.text(this, getString(R.string.apollo_profiles_explanation), 12.5f, colors.outline, false);
        explanation.setLineSpacing(0, 1.25f);
        explanation.setPadding(dp(14), dp(12), dp(14), dp(10));
        column.addView(explanation);

        rowsBox = new LinearLayout(this);
        rowsBox.setOrientation(LinearLayout.VERTICAL);
        column.addView(rowsBox);

        newRow = newProfileRow();
        column.addView(newRow);

        setContentView(root);
        UiHelper.notifyNewRootView(this);
        ApolloUi.padForCutout(root);

        hintRow = ScreenHints.attach(this, root);
        hintRow.setFallback(HintRow.hint(this, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_back));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Back from the editor: names and settings may have changed
        rebuild();
    }

    private int dp(float value) {
        return ApolloUi.dp(this, value);
    }

    // --- Rows

    private void rebuild() {
        View focused = getCurrentFocus();
        int focusedIndex = focused != null ? rowsBox.indexOfChild(focused) : -1;

        profiles.clear();
        profiles.addAll(Profiles.list(this));
        rowsBox.removeAllViews();
        Profiles.Profile active = Profiles.active(this);
        for (Profiles.Profile profile : profiles) {
            rowsBox.addView(profileRow(profile, active != null && active.id.equals(profile.id)));
        }

        View target = focusedIndex >= 0 && focusedIndex < rowsBox.getChildCount()
                ? rowsBox.getChildAt(focusedIndex) : rowsBox.getChildCount() > 0 ? rowsBox.getChildAt(0) : newRow;
        ApolloUi.focusByDefault(this, target);
        if (focused != null) {
            target.requestFocus();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private View profileRow(Profiles.Profile profile, boolean inUse) {
        LinearLayout row = new LinearLayout(this);
        row.setTag(profile);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(60));
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

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(6), 0, dp(12), 0);
        SpannableStringBuilder name = new SpannableStringBuilder(profile.name);
        if (inUse) {
            name.append("  ");
            int start = name.length();
            name.append(getString(R.string.apollo_profile_in_use));
            name.setSpan(new BadgeSpan(colors.secondaryContainer, colors.onSecondaryContainer), start, name.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        TextView nameView = ApolloUi.text(this, name, 14.5f, colors.onSurface, true);
        texts.addView(nameView);
        TextView summary = ApolloUi.text(this, summary(this, profile), 12, colors.outline, false);
        summary.setSingleLine(true);
        summary.setEllipsize(TextUtils.TruncateAt.END);
        summary.setPadding(0, dp(3), 0, 0);
        texts.addView(summary);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        row.setOnClickListener(v -> {
            if (movingRow == null) {
                edit(profile.id);
            }
        });
        row.setOnKeyListener((v, keyCode, event) -> onRowKey(row, keyCode, event));
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
                    HintRow.hint(this, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_back)});
        }
    }

    private View newProfileRow() {
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
        TextView label = ApolloUi.text(this, getString(R.string.apollo_profile_new), 14, colors.primary, true);
        label.setPadding(dp(12), 0, 0, 0);
        row.addView(label);
        row.setOnClickListener(v -> {
            Profiles.Profile profile = Profiles.create(this,
                    getString(R.string.apollo_profile_default_name, Profiles.list(this).size() + 1));
            edit(profile.id);
        });
        HintRow.set(row, KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_open, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_back);
        return row;
    }

    private void edit(String id) {
        Intent intent = new Intent(this, StreamSettings.class);
        intent.putExtra(StreamSettings.EXTRA_PROFILE_ID, id);
        startActivity(intent);
        overridePendingTransition(R.anim.apollo_fade_in, R.anim.apollo_fade_out);
    }

    /** "PyroWave · 1080p · 120 FPS · 500 Mbps", from the settings the profile changes. */
    static String summary(Context context, Profiles.Profile profile) {
        SharedPreferences values = Profiles.values(context, profile.id);
        Map<String, ?> all = values.getAll();
        if (all.isEmpty()) {
            return context.getString(R.string.apollo_profile_summary_empty);
        }
        List<String> parts = new ArrayList<>();
        int shown = 0;
        Object codec = all.get("video_format");
        if (codec != null) {
            parts.add(codecName(codec.toString()));
            shown++;
        }
        Object resolution = all.get("list_resolution");
        if (resolution != null) {
            parts.add(resolutionName(resolution.toString()));
            shown++;
        }
        Object fps = all.get("list_fps");
        if (fps != null) {
            parts.add(fps + " FPS");
            shown++;
        }
        Object bitrate = all.get("seekbar_bitrate_kbps");
        if (bitrate instanceof Integer) {
            parts.add(((Integer) bitrate) / 1000 + " Mbps");
            shown++;
        }
        if (Boolean.TRUE.equals(all.get("checkbox_enable_hdr"))) {
            parts.add("HDR");
        }
        if (all.containsKey("checkbox_enable_hdr")) {
            shown++;
        }
        int others = all.size() - shown;
        if (others > 0) {
            parts.add(context.getResources().getQuantityString(R.plurals.apollo_profile_summary_more, others, others));
        }
        return TextUtils.join(" · ", parts);
    }

    private static String codecName(String value) {
        switch (value) {
            case "forceav1":
                return "AV1";
            case "forceh265":
                return "HEVC";
            case "neverh265":
                return "H.264";
            case "forcepyrowave":
                return "PyroWave";
            default:
                return "Auto";
        }
    }

    private static String resolutionName(String value) {
        String[] parts = value.split("x");
        if (parts.length != 2) {
            return value;
        }
        if ("2160".equals(parts[1])) {
            return "4K";
        }
        return parts[1] + "p";
    }

    // --- Reordering

    private void saveOrder() {
        List<Profiles.Profile> order = new ArrayList<>();
        for (int i = 0; i < rowsBox.getChildCount(); i++) {
            order.add((Profiles.Profile) rowsBox.getChildAt(i).getTag());
        }
        Profiles.save(this, order);
    }

    private boolean onRowKey(View row, int keyCode, KeyEvent event) {
        if (movingRow == null) {
            if (keyCode == KeyEvent.KEYCODE_BUTTON_X) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    startMove(row);
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
        orderBeforeMove = new ArrayList<>(profiles);
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
            profiles.clear();
            profiles.addAll(Profiles.list(this));
        } else {
            Profiles.save(this, orderBeforeMove);
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
                        swap(index, index + 1, neighbor, -row.getHeight());
                        offset += neighbor.getHeight();
                        dy -= neighbor.getHeight();
                    } else if (dy < -row.getHeight() / 2f && index > 0) {
                        View neighbor = rowsBox.getChildAt(index - 1);
                        swap(index, index - 1, neighbor, row.getHeight());
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
                    profiles.clear();
                    profiles.addAll(Profiles.list(ProfilesActivity.this));
                    return true;
            }
            return false;
        }

        private void swap(int from, int to, View neighbor, int neighborShift) {
            rowsBox.removeView(row);
            rowsBox.addView(row, to);
            neighbor.setTranslationY(neighborShift);
            neighbor.animate().translationY(0).setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD).start();
        }
    }

    // --- Keys

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B && movingRow != null) {
            finishMove(false);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
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

    // "IN USE" after the name, in a small pill
    private class BadgeSpan extends ReplacementSpan {
        private final int fill;
        private final int textColor;

        BadgeSpan(int fill, int textColor) {
            this.fill = fill;
            this.textColor = textColor;
        }

        @Override
        public int getSize(android.graphics.Paint paint, CharSequence text, int start, int end, android.graphics.Paint.FontMetricsInt fm) {
            android.graphics.Paint small = smallPaint(paint);
            return Math.round(small.measureText(text, start, end)) + dp(16);
        }

        private android.graphics.Paint smallPaint(android.graphics.Paint paint) {
            android.graphics.Paint small = new android.graphics.Paint(paint);
            small.setTextSize(ApolloUi.dp(ProfilesActivity.this, 10.5f) * getResources().getConfiguration().fontScale);
            small.setLetterSpacing(0.06f);
            return small;
        }

        @Override
        public void draw(android.graphics.Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom,
                         android.graphics.Paint paint) {
            android.graphics.Paint small = smallPaint(paint);
            float width = small.measureText(text, start, end) + dp(16);
            float height = dp(18);
            float centerY = (top + bottom) / 2f;
            android.graphics.Paint fillPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            fillPaint.setColor(fill);
            canvas.drawRoundRect(x, centerY - height / 2, x + width, centerY + height / 2, height / 2, height / 2, fillPaint);
            small.setColor(textColor);
            android.graphics.Paint.FontMetrics metrics = small.getFontMetrics();
            canvas.drawText(text, start, end, x + dp(8), centerY - (metrics.ascent + metrics.descent) / 2, small);
        }
    }
}
