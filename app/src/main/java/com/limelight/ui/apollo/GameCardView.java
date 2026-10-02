package com.limelight.ui.apollo;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Outline;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.ui.theme.ApolloColors;

/**
 * A game card: box art with an optional "running" badge, the name and an optional subtitle below.
 * Used by the quick launch row and by the library grid.
 */
public class GameCardView extends LinearLayout {
    public static final int WIDTH_DP = 100;
    private static final int[] PLACEHOLDER_COLORS = {
            0xFF3B4A6B, 0xFF5B3F5E, 0xFF2F5D50, 0xFF6B4E2E, 0xFF3E3A6E, 0xFF2E5566, 0xFF5E3A3A, 0xFF46533A
    };

    private final ApolloColors colors;
    private final int radius;
    private final FrameLayout coverFrame;
    private final TextView initial;
    private final CoverImageView cover;
    private final TextView placeholderSignal;
    private final LinearLayout runningBadge;
    private final ImageView settingsBadge;
    private final TextView titleView;
    private final TextView subtitleView;

    /**
     * The cover of the box art loader: it stays hidden while the loader shows its own placeholder image,
     * which it tells by showing the name text, so the colored placeholder with the initial shows instead.
     */
    private static class CoverImageView extends ImageView {
        private boolean placeholder;
        private int requestedVisibility = VISIBLE;

        CoverImageView(Context context) {
            super(context);
        }

        @Override
        public void setVisibility(int visibility) {
            requestedVisibility = visibility;
            super.setVisibility(placeholder ? INVISIBLE : visibility);
        }

        void setPlaceholder(boolean placeholder) {
            this.placeholder = placeholder;
            setVisibility(requestedVisibility);
        }
    }

    public GameCardView(Context context, ApolloColors colors, int widthPx) {
        super(context);
        this.colors = colors;
        this.radius = dp(16);
        setOrientation(VERTICAL);
        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        setClipChildren(false);

        coverFrame = new FrameLayout(context);
        coverFrame.setForeground(ApolloUi.focusRing(context, colors, radius));
        coverFrame.setDuplicateParentStateEnabled(true);
        coverFrame.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        coverFrame.setClipToOutline(true);
        addView(coverFrame);

        initial = ApolloUi.text(context, "", 30, 0x38FFFFFF, true);
        initial.setPadding(dp(10), dp(6), 0, 0);
        coverFrame.addView(initial, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        cover = new CoverImageView(context);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setVisibility(GONE);
        coverFrame.addView(cover, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Never shown, the box art loader writes the name here and shows it for its placeholder image
        placeholderSignal = new TextView(context) {
            @Override
            public void setVisibility(int visibility) {
                super.setVisibility(visibility);
                cover.setPlaceholder(visibility == VISIBLE);
            }
        };

        runningBadge = new LinearLayout(context);
        runningBadge.setOrientation(HORIZONTAL);
        runningBadge.setGravity(Gravity.CENTER_VERTICAL);
        runningBadge.setPadding(dp(7), dp(3), dp(8), dp(3));
        runningBadge.setBackground(ApolloUi.roundRect(colors.primary, dp(12)));
        ImageView play = new ImageView(context);
        play.setImageResource(R.drawable.ic_apollo_play);
        play.setImageTintList(ColorStateList.valueOf(colors.onPrimary));
        runningBadge.addView(play, new LayoutParams(dp(11), dp(11)));
        TextView runningText = ApolloUi.text(context, context.getString(R.string.apollo_running), 10, colors.onPrimary, true);
        runningText.setPadding(dp(3), 0, 0, 0);
        runningBadge.addView(runningText);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        badgeParams.setMargins(dp(8), 0, 0, dp(8));
        coverFrame.addView(runningBadge, badgeParams);
        runningBadge.setVisibility(GONE);

        // The game has its own stream settings
        settingsBadge = new ImageView(context);
        settingsBadge.setImageResource(R.drawable.ic_apollo_tune);
        settingsBadge.setImageTintList(ColorStateList.valueOf(colors.onSecondaryContainer));
        settingsBadge.setBackground(ApolloUi.roundRect(colors.secondaryContainer, dp(12)));
        settingsBadge.setPadding(dp(4), dp(4), dp(4), dp(4));
        FrameLayout.LayoutParams settingsParams = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP | Gravity.END);
        settingsParams.setMargins(0, dp(7), dp(7), 0);
        coverFrame.addView(settingsBadge, settingsParams);
        settingsBadge.setVisibility(GONE);

        titleView = ApolloUi.text(context, "", 13, colors.onSurface, true);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setPadding(0, dp(7), 0, 0);
        addView(titleView);

        subtitleView = ApolloUi.text(context, "", 11, colors.onSurfaceVariant, false);
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        addView(subtitleView);

        setCardWidth(widthPx);
        ApolloUi.scaleOnFocus(this);
    }

    /**
     * A card of the quick launch row, with its box art read from the disk cache.
     */
    public GameCardView(Context context, ApolloColors colors, String title, String subtitle,
                        String computerUuid, int appId, boolean running) {
        this(context, colors, ApolloUi.dp(context, WIDTH_DP));
        bind(title, subtitle, appId);
        setRunning(running);

        if (computerUuid != null) {
            int coverWidth = dp(WIDTH_DP);
            CoverLoader.load(cover, computerUuid, appId, coverWidth, bitmap -> {
                cover.setImageBitmap(bitmap);
                cover.setVisibility(VISIBLE);
            });
        }
    }

    private int dp(float value) {
        return ApolloUi.dp(getContext(), value);
    }

    public void setCardWidth(int widthPx) {
        int coverHeight = widthPx * 4 / 3;
        coverFrame.setLayoutParams(new LayoutParams(widthPx, coverHeight));
        titleView.setLayoutParams(new LayoutParams(widthPx, LayoutParams.WRAP_CONTENT));
        subtitleView.setLayoutParams(new LayoutParams(widthPx, LayoutParams.WRAP_CONTENT));
        setPivotX(widthPx / 2f);
        setPivotY(coverHeight);
    }

    // The color behind the initial of a game without box art, the same wherever the game shows
    public static int placeholderColor(String title, int appId) {
        return PLACEHOLDER_COLORS[Math.abs((title + appId).hashCode()) % PLACEHOLDER_COLORS.length];
    }

    public void bind(String title, String subtitle, int appId) {
        int placeholder = placeholderColor(title, appId);
        coverFrame.setBackground(ApolloUi.ripple(ApolloUi.roundRect(placeholder, radius), radius));
        initial.setText(title.isEmpty() ? "" : title.substring(0, 1).toUpperCase());
        titleView.setText(title);
        subtitleView.setText(subtitle);
        subtitleView.setVisibility(subtitle != null && !subtitle.isEmpty() ? VISIBLE : GONE);
    }

    public void setRunning(boolean running) {
        runningBadge.setVisibility(running ? VISIBLE : GONE);
    }

    public void setCustomSettings(boolean customSettings) {
        settingsBadge.setVisibility(customSettings ? VISIBLE : GONE);
    }

    // The box art view and the text view that a CachedAppAssetLoader fills
    public ImageView getCover() {
        return cover;
    }

    public TextView getPlaceholderSignal() {
        return placeholderSignal;
    }

    // Whether the cover shows real box art, for a launcher shortcut
    public boolean hasCoverArt() {
        return cover.getVisibility() == VISIBLE && cover.getDrawable() != null;
    }
}
