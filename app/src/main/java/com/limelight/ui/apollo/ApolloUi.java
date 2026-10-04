package com.limelight.ui.apollo;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.ui.theme.ApolloColors;
import com.limelight.ui.theme.ApolloMotion;

/**
 * Small helpers to build the MoonVibe UI in code with the Material 3 look.
 */
public final class ApolloUi {
    public static final int COLOR_RIPPLE = 0x33FFFFFF;
    private static final float FOCUSED_SCALE = 1.05f;
    // Corners of the rows of lists (settings, sheets, menus) and of their focus tint: Material 3 medium shape
    public static final int ROW_RADIUS_DP = 12;

    private ApolloUi() {
    }

    public static int dp(Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static GradientDrawable roundRect(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    public static RippleDrawable ripple(Drawable content, float radius) {
        return new RippleDrawable(ColorStateList.valueOf(COLOR_RIPPLE), content, roundRect(Color.WHITE, radius));
    }

    /**
     * A light layer while the view is pressed, and nothing on focus: the focus ring alone marks the focus,
     * so what the row shows stays readable. Unlike a ripple it never flashes when the focus moves on.
     */
    public static Drawable pressLayer(float radius) {
        return stateLayer(Color.TRANSPARENT, Color.TRANSPARENT, radius);
    }

    /**
     * Background of a list row: a soft fill on focus, a lighter one while pressed and the selected color
     * when activated, cross-fading between them as the focus moves.
     */
    public static Drawable stateLayer(int focusedColor, int activatedColor, float radius) {
        StateListDrawable states = new StateListDrawable();
        states.setEnterFadeDuration((int) ApolloMotion.SHORT);
        states.setExitFadeDuration((int) ApolloMotion.MEDIUM);
        states.addState(new int[] {android.R.attr.state_pressed}, roundRect(COLOR_RIPPLE, radius));
        if (activatedColor != Color.TRANSPARENT) {
            states.addState(new int[] {android.R.attr.state_activated}, roundRect(activatedColor, radius));
        }
        if (focusedColor != Color.TRANSPARENT) {
            states.addState(new int[] {android.R.attr.state_focused}, roundRect(focusedColor, radius));
        }
        states.addState(new int[] {}, roundRect(Color.TRANSPARENT, radius));
        return states;
    }

    /**
     * Lets the cards of a row grow on focus without being cut: room around them, and no clipping
     * from the row up to the scrolling page, which keeps its own bounds.
     */
    public static void allowFocusOverflow(ViewGroup row, int roomPx) {
        row.setPadding(roomPx, roomPx, roomPx, roomPx);
        ViewGroup group = row;
        while (group != null && !(group instanceof ScrollView)) {
            group.setClipChildren(false);
            group.setClipToPadding(false);
            group = group.getParent() instanceof ViewGroup ? (ViewGroup) group.getParent() : null;
        }
    }

    /**
     * Makes the view the one a gamepad lands on: the default focus once touch mode ends,
     * and the focus right away if nothing has it yet.
     */
    public static void focusByDefault(Activity activity, View target) {
        if (target == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            target.setFocusedByDefault(true);
        }
        if (!target.isInTouchMode() && activity.getCurrentFocus() == null) {
            target.requestFocus();
        }
    }

    // Grows the view a little while it has the focus
    public static void scaleOnFocus(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // The ring drawn by the card replaces the gray highlight of the system
            view.setDefaultFocusHighlightEnabled(false);
        }
        view.setOnFocusChangeListener((v, hasFocus) -> v.animate()
                .scaleX(hasFocus ? FOCUSED_SCALE : 1)
                .scaleY(hasFocus ? FOCUSED_SCALE : 1)
                .setDuration(ApolloMotion.SHORT)
                .setInterpolator(ApolloMotion.STANDARD)
                .start());
    }

    public static TextView text(Context context, CharSequence value, float sizeSp, int color, boolean medium) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        if (medium) {
            view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        return view;
    }

    public static TextView sectionHeader(Context context, ApolloColors colors, String label) {
        TextView header = text(context, label.toUpperCase(), 12, colors.primary, true);
        header.setLetterSpacing(0.08f);
        return header;
    }
}
