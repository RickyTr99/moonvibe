package com.limelight.ui.apollo;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.ui.apollo.hints.HintRow;
import com.limelight.ui.theme.ApolloColors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The row of PC cards in the home screen, followed by a small "+" card to add a PC.
 * Cards are updated in place on every poll so the focus and the animations are not disturbed.
 */
public class PcCardRow {
    private static final int COLOR_ONLINE = 0xFF6DD58C;
    private static final int COLOR_UNPAIRED = 0xFFF0B428;

    public interface Listener {
        void onPcClicked(ComputerDetails computer);
        void onPcLongClicked(ComputerDetails computer);
        void onAddPcClicked();
    }

    private static class Card {
        final FrameLayout view;
        final View iconBackground;
        final ImageView icon;
        final TextView name;
        final View dot;
        final TextView status;
        ComputerDetails computer;

        Card(FrameLayout view, View iconBackground, ImageView icon, TextView name, View dot, TextView status) {
            this.view = view;
            this.iconBackground = iconBackground;
            this.icon = icon;
            this.name = name;
            this.dot = dot;
            this.status = status;
        }
    }

    private final Context context;
    private final LinearLayout container;
    private final ApolloColors colors;
    private final Listener listener;
    private final Map<String, Card> cards = new HashMap<>();
    private final View addCard;

    public PcCardRow(LinearLayout container, ApolloColors colors, Listener listener) {
        this.context = container.getContext();
        this.container = container;
        this.colors = colors;
        this.listener = listener;
        this.addCard = createAddCard();
        ApolloUi.allowFocusOverflow(container, dp(8));
    }

    private int dp(float value) {
        return ApolloUi.dp(context, value);
    }

    public void update(List<ComputerDetails> computers) {
        List<View> order = new ArrayList<>();
        Map<String, Card> stillPresent = new HashMap<>();

        for (ComputerDetails computer : computers) {
            Card card = cards.get(computer.uuid);
            if (card == null) {
                card = createCard();
            }
            card.computer = computer;
            bind(card);
            stillPresent.put(computer.uuid, card);
            order.add(card.view);
        }
        cards.clear();
        cards.putAll(stillPresent);
        order.add(addCard);

        // Only touch the children when the order changes, it would move the focus
        boolean same = container.getChildCount() == order.size();
        for (int i = 0; same && i < order.size(); i++) {
            same = container.getChildAt(i) == order.get(i);
        }
        if (same) {
            return;
        }

        View focused = container.findFocus();
        container.removeAllViews();
        for (int i = 0; i < order.size(); i++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                params.leftMargin = dp(12);
            }
            container.addView(order.get(i), params);
        }
        if (focused != null && order.contains(focused)) {
            focused.requestFocus();
        }
    }

    private FrameLayout cardFrame(int widthDp) {
        int radius = dp(16);
        FrameLayout frame = new FrameLayout(context);
        frame.setBackground(ApolloUi.ripple(ApolloUi.roundRect(colors.surfaceContainerHigh, radius), radius));
        frame.setFocusable(true);
        frame.setClickable(true);
        frame.setMinimumWidth(dp(widthDp));
        frame.setMinimumHeight(dp(64));
        ApolloUi.scaleOnFocus(frame);
        return frame;
    }

    private Card createCard() {
        FrameLayout frame = cardFrame(200);
        frame.setLongClickable(true);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(dp(14), 0, dp(16), 0);
        frame.addView(content, new FrameLayout.LayoutParams(dp(200), dp(64)));

        FrameLayout iconBox = new FrameLayout(context);
        content.addView(iconBox, new LinearLayout.LayoutParams(dp(36), dp(36)));
        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.ic_apollo_desktop);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(dp(8), dp(8), dp(8), dp(8));
        iconBox.addView(icon, new FrameLayout.LayoutParams(dp(36), dp(36)));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, 0, 0);
        content.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView name = ApolloUi.text(context, "", 14, colors.onSurface, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(name);

        LinearLayout statusLine = new LinearLayout(context);
        statusLine.setOrientation(LinearLayout.HORIZONTAL);
        statusLine.setGravity(Gravity.CENTER_VERTICAL);
        statusLine.setPadding(0, dp(3), 0, 0);
        texts.addView(statusLine);

        View dot = new View(context);
        statusLine.addView(dot, new LinearLayout.LayoutParams(dp(7), dp(7)));
        TextView status = ApolloUi.text(context, "", 11.5f, colors.onSurfaceVariant, false);
        status.setPadding(dp(6), 0, 0, 0);
        statusLine.addView(status);

        Card card = new Card(frame, iconBox, icon, name, dot, status);
        HintRow.set(frame, KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_open, KeyEvent.KEYCODE_BUTTON_Y, R.string.apollo_hint_options,
                KeyEvent.KEYCODE_BUTTON_X, R.string.apollo_hint_add_pc, KeyEvent.KEYCODE_BUTTON_START, R.string.apollo_hint_quick_settings);
        frame.setOnClickListener(v -> listener.onPcClicked(card.computer));
        frame.setOnLongClickListener(v -> {
            listener.onPcLongClicked(card.computer);
            return true;
        });
        return card;
    }

    private void bind(Card card) {
        ComputerDetails computer = card.computer;
        boolean online = computer.state == ComputerDetails.State.ONLINE;
        boolean paired = computer.pairState == PairingManager.PairState.PAIRED;

        card.name.setText(computer.name);

        int statusText;
        int dotColor;
        if (computer.state == ComputerDetails.State.UNKNOWN) {
            statusText = R.string.apollo_pc_checking;
            dotColor = colors.outline;
        } else if (!online) {
            statusText = R.string.apollo_pc_offline;
            dotColor = colors.outline;
        } else if (!paired) {
            statusText = R.string.apollo_pc_unpaired;
            dotColor = COLOR_UNPAIRED;
        } else {
            statusText = R.string.apollo_pc_online;
            dotColor = COLOR_ONLINE;
        }
        card.status.setText(statusText);
        card.dot.setBackground(ApolloUi.roundRect(dotColor, dp(4)));

        card.iconBackground.setBackground(ApolloUi.roundRect(online ? colors.secondaryContainer : colors.surfaceContainerHighest, dp(18)));
        card.icon.setImageTintList(ColorStateList.valueOf(online ? colors.onSecondaryContainer : colors.onSurfaceVariant));
    }

    private View createAddCard() {
        FrameLayout frame = cardFrame(64);
        frame.setContentDescription(context.getString(R.string.apollo_add_pc));
        ImageView plus = new ImageView(context);
        plus.setImageResource(R.drawable.ic_apollo_add);
        plus.setImageTintList(ColorStateList.valueOf(colors.onSurfaceVariant));
        plus.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        frame.addView(plus, new FrameLayout.LayoutParams(dp(64), dp(64)));
        frame.setOnClickListener(v -> listener.onAddPcClicked());
        HintRow.set(frame, KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_add_pc, KeyEvent.KEYCODE_BUTTON_START, R.string.apollo_hint_quick_settings);
        return frame;
    }
}
