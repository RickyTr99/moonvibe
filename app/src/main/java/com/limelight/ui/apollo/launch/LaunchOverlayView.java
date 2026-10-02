package com.limelight.ui.apollo.launch;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.apollo.ApolloUi;
import com.limelight.ui.apollo.CoverLoader;
import com.limelight.ui.apollo.GameCardView;
import com.limelight.ui.apollo.hints.ButtonGlyph;
import com.limelight.ui.apollo.hints.InputMode;
import com.limelight.ui.theme.ApolloColors;
import com.limelight.ui.theme.ApolloMotion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The screen shown while a stream starts, over the stream view: the game's cover on a blurred copy of it,
 * the game, the PC and the stream settings, and the launch in three steps with a progress bar.
 * It replaces the old "Establishing connection" spinner dialog; B, Back or the Cancel button leave.
 */
public class LaunchOverlayView extends FrameLayout {
    // The stages of moonlight-common-c (LiGetStageName) that belong to the "Connecting" step,
    // the later ones open the stream. Before them comes the app launch, reported with the app's name.
    private static final List<String> CONNECT_STAGES = Arrays.asList(
            "platform initialization", "name resolution", "audio stream initialization",
            "RTSP handshake", "control stream initialization");
    private static final int STEP_COUNT = 3;

    private final ApolloColors colors;
    private final ImageView background;
    private final FrameLayout coverFrame;
    private final ImageView cover;
    private final TextView initial;
    private final LinearLayout info;
    private final TextView title;
    private final TextView subtitle;
    private final LinearLayout chips;
    private final StepIconView[] stepIcons = new StepIconView[STEP_COUNT];
    private final TextView[] stepLabels = new TextView[STEP_COUNT];
    private final TextView[] stepDetails = new TextView[STEP_COUNT];
    private final LaunchProgressBar progressBar;

    private Runnable onCancel;
    private int step = -1;
    private boolean showing;
    private boolean failed;

    public LaunchOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        colors = ApolloColors.dark(context);
        setVisibility(GONE);
        setClickable(true);
        setBackgroundColor(colors.surfaceContainerLow);

        background = new ImageView(context);
        background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        background.setScaleX(1.2f);
        background.setScaleY(1.2f);
        addView(background, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        View scrim = new View(context);
        int base = colors.surfaceContainerLow & 0x00FFFFFF;
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {0x80000000 | base, 0xD1000000 | base, 0xF0000000 | base}));
        addView(scrim, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int side = dp(72);
        row.setPadding(side, dp(24), side, dp(24));
        addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        int coverRadius = dp(16);
        coverFrame = new FrameLayout(context);
        coverFrame.setClipToOutline(true);
        coverFrame.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), coverRadius);
            }
        });
        initial = ApolloUi.text(context, "", 56, 0xCCFFFFFF, true);
        initial.setGravity(Gravity.CENTER);
        coverFrame.addView(initial, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        cover = new ImageView(context);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        coverFrame.addView(cover, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(dp(180), dp(240));
        coverParams.setMarginEnd(dp(48));
        row.addView(coverFrame, coverParams);

        info = new LinearLayout(context);
        info.setOrientation(LinearLayout.VERTICAL);
        row.addView(info, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView eyebrow = ApolloUi.text(context, context.getString(R.string.apollo_launch_starting).toUpperCase(),
                12, colors.primary, true);
        eyebrow.setLetterSpacing(0.1f);
        info.addView(eyebrow);

        title = ApolloUi.text(context, "", 30, colors.onSurface, true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(title);

        subtitle = ApolloUi.text(context, "", 15, colors.onSurfaceVariant, false);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(subtitle);

        chips = new LinearLayout(context);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams chipsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chipsParams.topMargin = dp(14);
        info.addView(chips, chipsParams);

        int[] labels = {R.string.apollo_launch_step_app, R.string.apollo_launch_step_connect,
                R.string.apollo_launch_step_stream};
        for (int i = 0; i < STEP_COUNT; i++) {
            LinearLayout stepRow = new LinearLayout(context);
            stepRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams stepParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            stepParams.topMargin = dp(i == 0 ? 28 : 12);
            info.addView(stepRow, stepParams);

            stepIcons[i] = new StepIconView(context, colors);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(20), dp(20));
            iconParams.setMarginEnd(dp(12));
            iconParams.topMargin = dp(1);
            stepRow.addView(stepIcons[i], iconParams);

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            stepRow.addView(texts);

            stepLabels[i] = ApolloUi.text(context, context.getString(labels[i]), 16, colors.outline, false);
            texts.addView(stepLabels[i]);
            stepDetails[i] = ApolloUi.text(context, "", 12, colors.outline, false);
            stepDetails[i].setVisibility(GONE);
            texts.addView(stepDetails[i]);
        }

        progressBar = new LaunchProgressBar(context, colors);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(dp(320), dp(5));
        barParams.topMargin = dp(24);
        info.addView(progressBar, barParams);

        LinearLayout cancel = new LinearLayout(context);
        cancel.setOrientation(LinearLayout.HORIZONTAL);
        cancel.setGravity(Gravity.CENTER_VERTICAL);
        cancel.setPadding(dp(10), dp(8), dp(16), dp(8));
        float pill = dp(20);
        cancel.setBackground(ApolloUi.ripple(ApolloUi.roundRect(0x14000000 | (colors.onSurface & 0x00FFFFFF), pill), pill));
        if (InputMode.isGamepadConnected()) {
            View glyph = ButtonGlyph.create(context, colors, KeyEvent.KEYCODE_BUTTON_B);
            LinearLayout.LayoutParams glyphParams = new LinearLayout.LayoutParams(dp(18), dp(18));
            glyphParams.setMarginEnd(dp(8));
            cancel.addView(glyph, glyphParams);
        } else {
            cancel.setPadding(dp(16), dp(8), dp(16), dp(8));
        }
        cancel.addView(ApolloUi.text(context, context.getString(R.string.apollo_launch_cancel), 14, colors.onSurface, true));
        cancel.setOnClickListener(v -> cancel());
        LayoutParams cancelParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        cancelParams.setMargins(0, 0, dp(28), dp(20));
        addView(cancel, cancelParams);
    }

    private int dp(float value) {
        return ApolloUi.dp(getContext(), value);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // On short screens the cover shrinks, so the steps and the bar still fit
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (height > 0) {
            int coverHeight = Math.min(dp(240), (int) (height * 0.55f));
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) coverFrame.getLayoutParams();
            if (params.height != coverHeight) {
                params.height = coverHeight;
                params.width = coverHeight * 3 / 4;
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public void show(String appName, String pcName, String pcUuid, int appId,
                     PreferenceConfiguration prefConfig, Runnable onCancel) {
        this.onCancel = onCancel;
        String name = appName != null ? appName : "";

        title.setText(name);
        subtitle.setText(pcName != null ? getContext().getString(R.string.apollo_launch_on_pc, pcName) : "");
        subtitle.setVisibility(pcName != null ? VISIBLE : GONE);

        initial.setText(name.isEmpty() ? "" : name.substring(0, 1).toUpperCase());
        coverFrame.setBackgroundColor(GameCardView.placeholderColor(name, appId));
        if (pcUuid != null) {
            CoverLoader.load(cover, pcUuid, appId, dp(180), this::onCoverLoaded);
        }

        chips.removeAllViews();
        for (String chip : chipsFor(prefConfig)) {
            TextView view = ApolloUi.text(getContext(), chip, 12, colors.onSurfaceVariant, false);
            view.setPadding(dp(10), dp(4), dp(10), dp(4));
            GradientDrawable border = ApolloUi.roundRect(0, dp(8));
            border.setStroke(dp(1), colors.outlineVariant);
            view.setBackground(border);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(dp(6));
            chips.addView(view, params);
        }

        showing = true;
        failed = false;
        step = -1;
        progressBar.reset();
        setStep(0, getContext().getString(R.string.apollo_launch_detail_app));

        setVisibility(VISIBLE);
        setAlpha(0f);
        animate().alpha(1f).setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.STANDARD).start();

        coverFrame.setTranslationY(dp(16));
        coverFrame.setScaleX(0.94f);
        coverFrame.setScaleY(0.94f);
        coverFrame.animate().translationY(0).scaleX(1f).scaleY(1f)
                .setDuration(500).setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE).start();

        info.setAlpha(0f);
        info.setTranslationY(dp(10));
        info.animate().alpha(1f).translationY(0).setStartDelay(80)
                .setDuration(500).setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE).start();
    }

    private static List<String> chipsFor(PreferenceConfiguration prefConfig) {
        List<String> list = new ArrayList<>();
        list.add(prefConfig.width + "×" + prefConfig.height);
        list.add(prefConfig.fps + " FPS");
        list.add(Math.max(1, prefConfig.bitrate / 1000) + " Mbps");
        switch (prefConfig.videoFormat) {
            case FORCE_H264: list.add("H.264"); break;
            case FORCE_HEVC: list.add("HEVC"); break;
            case FORCE_AV1: list.add("AV1"); break;
            case FORCE_PYROWAVE: list.add("PyroWave"); break;
            default: break;
        }
        if (prefConfig.enableHdr) {
            list.add("HDR");
        }
        return list;
    }

    private void onCoverLoaded(Bitmap bitmap) {
        cover.setImageBitmap(bitmap);
        cover.setAlpha(0f);
        cover.animate().alpha(1f).setDuration(ApolloMotion.MEDIUM).start();

        // A tiny copy, stretched over the screen, is already blurry; the render effect smooths it out
        int width = 32;
        int height = Math.max(1, bitmap.getHeight() * width / Math.max(1, bitmap.getWidth()));
        background.setImageBitmap(Bitmap.createScaledBitmap(bitmap, width, height, true));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            float radius = dp(24);
            background.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
        }
        background.setAlpha(0f);
        background.animate().alpha(0.5f).setDuration(ApolloMotion.LONG).start();
    }

    /** A stage reported by the connection: the app's name for the launch, then the moonlight-common-c stages. */
    public void setStage(String stage) {
        if (!showing || failed) {
            return;
        }
        int newStep;
        String detail = stage;
        if (CONNECT_STAGES.contains(stage)) {
            newStep = 1;
        } else if (stage != null && (stage.endsWith("stream initialization") || stage.endsWith("establishment"))) {
            newStep = 2;
        } else {
            newStep = 0;
            detail = getContext().getString(R.string.apollo_launch_detail_app);
        }
        setStep(Math.max(step, newStep), detail);
    }

    private void setStep(int newStep, String detail) {
        boolean changed = newStep != step;
        step = newStep;
        for (int i = 0; i < STEP_COUNT; i++) {
            int state = i < step ? StepIconView.DONE : (i == step ? StepIconView.ACTIVE : StepIconView.PENDING);
            stepIcons[i].setState(state);
            animateTextColor(stepLabels[i], i == step ? colors.onSurface : (i < step ? colors.onSurfaceVariant : colors.outline));

            if (i == step && step < STEP_COUNT) {
                stepDetails[i].setText(detail);
                if (changed) {
                    stepDetails[i].setVisibility(VISIBLE);
                    stepDetails[i].setAlpha(0f);
                    stepDetails[i].setTranslationY(dp(6));
                    stepDetails[i].animate().alpha(1f).translationY(0)
                            .setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE).start();
                }
            } else {
                stepDetails[i].setVisibility(GONE);
            }
        }
        if (changed) {
            progressBar.setProgress(Math.min(1f, (step + (step < STEP_COUNT ? 0.5f : 0f)) / STEP_COUNT));
        }
    }

    private static void animateTextColor(TextView view, int color) {
        int from = view.getCurrentTextColor();
        if (from == color) {
            return;
        }
        ValueAnimator animator = ValueAnimator.ofArgb(from, color);
        animator.setDuration(ApolloMotion.MEDIUM);
        animator.addUpdateListener(a -> view.setTextColor((int) a.getAnimatedValue()));
        animator.start();
    }

    /** The stream started: every step is done, then the screen fades into the stream. */
    public void dismiss() {
        if (!showing) {
            return;
        }
        showing = false;
        setStep(STEP_COUNT, null);
        animate().alpha(0f).scaleX(1.04f).scaleY(1.04f).setStartDelay(200)
                .setDuration(ApolloMotion.LONG).setInterpolator(ApolloMotion.EMPHASIZED_ACCELERATE)
                .withEndAction(() -> {
                    setVisibility(GONE);
                    setScaleX(1f);
                    setScaleY(1f);
                    for (StepIconView icon : stepIcons) {
                        icon.stop();
                    }
                    progressBar.stop();
                    cover.setImageDrawable(null);
                    background.setImageDrawable(null);
                })
                .start();
    }

    /** The launch failed: the running step turns into a cross. The screen stays under the error dialog. */
    public void fail() {
        if (!showing || failed) {
            return;
        }
        failed = true;
        int failedStep = Math.min(step, STEP_COUNT - 1);
        stepIcons[failedStep].setState(StepIconView.FAILED);
        animateTextColor(stepLabels[failedStep], colors.error);
        progressBar.setFailed();
    }

    public boolean isShowing() {
        return showing;
    }

    /** While the screen shows, it takes every key: B, Back or Escape cancel the launch. */
    public boolean handleKeyEvent(KeyEvent event) {
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_ESCAPE:
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                    cancel();
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                return false;
            default:
                return true;
        }
    }

    private void cancel() {
        if (onCancel != null) {
            onCancel.run();
        }
    }
}
