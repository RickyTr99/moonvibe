package com.limelight.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.os.Build;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.preferences.AppStreamSettings;
import com.limelight.ui.apollo.ActionSheet;
import com.limelight.ui.apollo.GameCardView;
import com.limelight.ui.apollo.hints.ButtonGlyph;
import com.limelight.ui.apollo.hints.HintRow;
import com.limelight.ui.apollo.ApolloUi;
import com.limelight.ui.theme.ApolloColors;
import com.limelight.ui.theme.ApolloMotion;
import com.limelight.utils.CacheHelper;
import com.limelight.utils.QuickLaunchManager;
import com.limelight.utils.RecentGames;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.UiHelper;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The quick launch row of the home screen: the pinned games, then the recent ones, each in a fixed place.
 * Their options open in a side sheet with a long press, or Y on a gamepad.
 */
public class QuickLaunchView {
    public interface QuickLaunchCallback {
        ComputerManagerService.ComputerManagerBinder getManagerBinder();

        // The row was rebuilt
        void onQuickLaunchChanged();

        // What the gamepad buttons do on the focused card has changed
        void onQuickLaunchHintsChanged();
    }

    // Size and lift of the card being moved, when no focus lifts it (touch)
    private static final float MOVING_SCALE = 1.08f;
    private static final int MOVING_LIFT_DP = 6;
    // The other cards while one is moved
    private static final float MOVING_OTHERS_ALPHA = 0.45f;
    private static final int CARD_GAP_DP = 14;

    private final Activity activity;
    private final LinearLayout quickLaunchSection;
    private final LinearLayout quickLaunchContainer;
    private final QuickLaunchManager quickLaunchManager;
    private final QuickLaunchCallback callback;
    private final ApolloColors colors;

    private boolean receiverRegistered = false;
    // What the row shows, to skip rebuilding it when a poll changes nothing
    private String shownSignature;
    // Names of games that are not pinned, read once from the cached app list
    private final Map<String, String> appNames = new HashMap<>();
    // The pinned games come first in the row: their cards and keys, in the order shown
    private final Map<View, String> pinnedKeys = new HashMap<>();
    private int pinnedCount;
    // The card being moved to reorder the pinned games, null otherwise
    private GameCardView movingCard;
    // A rebuild asked while moving waits for the end
    private boolean reloadPending;
    private float dragStartX;
    private boolean dragging;

    private final BroadcastReceiver quickLaunchUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (QuickLaunchManager.QUICK_LAUNCH_UPDATE_ACTION.equals(intent.getAction())) {
                loadQuickLaunchButtons();
            }
        }
    };

    // A card of the row: a pinned game, or a running or recent game that is not pinned (item == null)
    private static class Entry {
        final QuickLaunchManager.QuickLaunchItem item;
        final String computerUuid;
        final int appId;
        final String title;
        final String computerName;
        final boolean running;
        // False when the title is a placeholder because the app list of the PC is not cached
        final boolean nameKnown;

        Entry(QuickLaunchManager.QuickLaunchItem item, String computerUuid, int appId, String title,
              String computerName, boolean running) {
            this(item, computerUuid, appId, title, computerName, running, true);
        }

        Entry(QuickLaunchManager.QuickLaunchItem item, String computerUuid, int appId, String title,
              String computerName, boolean running, boolean nameKnown) {
            this.item = item;
            this.computerUuid = computerUuid;
            this.appId = appId;
            this.title = title;
            this.computerName = computerName;
            this.running = running;
            this.nameKnown = nameKnown;
        }
    }

    public QuickLaunchView(Activity activity, LinearLayout quickLaunchSection,
                          LinearLayout quickLaunchContainer, QuickLaunchCallback callback) {
        this.activity = activity;
        this.quickLaunchSection = quickLaunchSection;
        this.quickLaunchContainer = quickLaunchContainer;
        this.quickLaunchManager = QuickLaunchManager.getInstance(activity);
        this.callback = callback;
        this.colors = ApolloColors.dark(activity);
        ApolloUi.allowFocusOverflow(quickLaunchContainer, ApolloUi.dp(activity, 8));
        // More room on top: the focused card grows upwards from the bottom of its cover and lifts
        quickLaunchContainer.setPadding(quickLaunchContainer.getPaddingLeft(), ApolloUi.dp(activity, 14),
                quickLaunchContainer.getPaddingRight(), quickLaunchContainer.getPaddingBottom());

        // The running game goes first, so a change rebuilds the row
        this.quickLaunchManager.setRunningStatusListener(new QuickLaunchManager.RunningStatusListener() {
            @Override
            public void onRunningStatusChanged() {
                activity.runOnUiThread(QuickLaunchView.this::loadQuickLaunchButtons);
            }
        });

        loadQuickLaunchButtons();
    }

    /**
     * Add an app to Quick Launch
     */
    public void addToQuickLaunch(ComputerDetails computer, NvApp app) {
        quickLaunchManager.addQuickLaunchItem(computer, app);
        Toast.makeText(activity, activity.getString(R.string.quick_launch_added), Toast.LENGTH_SHORT).show();
    }

    /**
     * Register broadcast receiver for updates
     */
    public void onResume() {
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter(QuickLaunchManager.QUICK_LAUNCH_UPDATE_ACTION);

            // For API 34+, we need to specify RECEIVER_NOT_EXPORTED for internal broadcasts
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                activity.registerReceiver(quickLaunchUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                activity.registerReceiver(quickLaunchUpdateReceiver, filter);
            }
            receiverRegistered = true;
        }

        // Force an immediate refresh to ensure we show any recently added items
        loadQuickLaunchButtons();
    }

    /**
     * Unregister broadcast receiver
     */
    public void onPause() {
        finishMove(true);
        unregisterReceiver();
    }

    /**
     * Cleanup on destroy
     */
    public void onDestroy() {
        unregisterReceiver();
    }

    private void unregisterReceiver() {
        if (receiverRegistered) {
            try {
                activity.unregisterReceiver(quickLaunchUpdateReceiver);
                receiverRegistered = false;
            } catch (IllegalArgumentException e) {
                // Receiver not registered, ignore
                receiverRegistered = false;
            }
        }
    }

    // Every game keeps its place: the pinned ones in their order, then the recent ones.
    // A running game is only marked, so the row does not move when a game starts.
    private List<Entry> buildEntries() {
        List<Entry> entries = new ArrayList<>();
        for (QuickLaunchManager.QuickLaunchItem item : quickLaunchManager.getAllQuickLaunchItems()) {
            entries.add(new Entry(item, item.computerUuid, item.appId, item.getDisplayName(), item.computerName,
                    quickLaunchManager.isQuickLaunchItemRunning(item.key)));
        }

        // A game running on a PC may have been started from another device: it joins the recent ones
        Map<String, Integer> runningApps = quickLaunchManager.getRunningAppIds();
        for (Map.Entry<String, Integer> server : runningApps.entrySet()) {
            ComputerDetails computer = getComputer(server.getKey());
            RecentGames.add(activity, server.getKey(), server.getValue(), appName(server.getKey(), server.getValue()),
                    computer != null ? computer.name : null);
        }

        // The recent games keep their PC name, so they show before the PCs are loaded
        for (RecentGames.Game game : RecentGames.get(activity)) {
            if (contains(entries, game.computerUuid, game.appId)) {
                continue;
            }
            String computerName = game.computerName;
            if (computerName == null) {
                // Saved by an older version: learn the name once the PC is known
                ComputerDetails computer = getComputer(game.computerUuid);
                if (computer == null) {
                    continue;
                }
                computerName = computer.name;
                RecentGames.add(activity, game.computerUuid, game.appId, null, computerName);
            }

            Integer runningAppId = runningApps.get(game.computerUuid);
            boolean running = runningAppId != null && runningAppId == game.appId;
            String name = game.name != null ? game.name : appName(game.computerUuid, game.appId);
            if (name != null) {
                entries.add(new Entry(null, game.computerUuid, game.appId, name, computerName, running, true));
            } else if (running) {
                // The library of that PC was never opened, so the name is unknown
                entries.add(new Entry(null, game.computerUuid, game.appId,
                        activity.getString(R.string.apollo_running_on, computerName), computerName, true, false));
            }
        }
        return entries;
    }

    private static boolean contains(List<Entry> entries, String computerUuid, int appId) {
        for (Entry entry : entries) {
            if (entry.computerUuid.equals(computerUuid) && entry.appId == appId) {
                return true;
            }
        }
        return false;
    }

    private ComputerDetails getComputer(String computerUuid) {
        ComputerManagerService.ComputerManagerBinder binder = callback.getManagerBinder();
        return binder != null ? binder.getComputer(computerUuid) : null;
    }

    // The name comes from the app list cached by the library of that PC
    private String appName(String computerUuid, int appId) {
        String cacheKey = computerUuid + "/" + appId;
        String name = appNames.get(cacheKey);
        if (name == null) {
            try {
                String rawAppList = CacheHelper.readInputStreamToString(
                        CacheHelper.openCacheFileForInput(activity.getCacheDir(), "applist", computerUuid));
                for (NvApp app : NvHTTP.getAppListByReader(new StringReader(rawAppList))) {
                    if (app.getAppId() == appId) {
                        name = app.getAppName();
                        appNames.put(cacheKey, name);
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return name;
    }

    private void loadQuickLaunchButtons() {
        if (quickLaunchContainer == null) {
            return; // Not initialized yet
        }
        if (movingCard != null) {
            reloadPending = true;
            return;
        }

        List<Entry> entries = buildEntries();
        StringBuilder signature = new StringBuilder();
        for (Entry entry : entries) {
            signature.append(entry.computerUuid).append('/').append(entry.appId).append('/')
                    .append(entry.title).append('/').append(entry.running).append(';');
        }
        if (signature.toString().equals(shownSignature)) {
            return;
        }
        shownSignature = signature.toString();

        // Keep the focus on the same game when the row is rebuilt
        View focused = quickLaunchContainer.findFocus();
        Object focusedKey = focused != null ? focused.getTag() : null;

        quickLaunchContainer.removeAllViews();
        pinnedKeys.clear();
        pinnedCount = 0;
        if (entries.isEmpty()) {
            quickLaunchSection.setVisibility(View.GONE);
            callback.onQuickLaunchChanged();
            return;
        }
        quickLaunchSection.setVisibility(View.VISIBLE);

        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            GameCardView card = new GameCardView(activity, colors, entry.title, entry.computerName,
                    entry.computerUuid, entry.appId, entry.running);
            card.setTag(entry.computerUuid + "/" + entry.appId);
            HintRow.set(card, KeyEvent.KEYCODE_BUTTON_A, entry.running ? R.string.apollo_hint_resume : R.string.apollo_hint_start,
                    KeyEvent.KEYCODE_BUTTON_Y, R.string.apollo_hint_options, KeyEvent.KEYCODE_BUTTON_X, R.string.apollo_hint_add_pc,
                    KeyEvent.KEYCODE_BUTTON_START, R.string.apollo_hint_quick_settings);
            card.setOnClickListener(v -> launch(entry));
            card.setOnLongClickListener(v -> {
                showActions(entry);
                return true;
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                params.leftMargin = ApolloUi.dp(activity, CARD_GAP_DP);
            }
            quickLaunchContainer.addView(card, params);
            if (entry.item != null) {
                pinnedKeys.put(card, entry.item.key);
                pinnedCount++;
            }

            if (focusedKey != null && focusedKey.equals(card.getTag())) {
                card.requestFocus();
            }
        }
        callback.onQuickLaunchChanged();
    }

    private void launch(Entry entry) {
        if (entry.item != null) {
            launchQuickLaunchApp(entry.item);
        } else {
            startGame(entry);
        }
    }

    private void showActions(Entry entry) {
        List<ActionSheet.Action> actions = new ArrayList<>();
        actions.add(new ActionSheet.Action(R.drawable.ic_apollo_play,
                activity.getString(entry.running ? R.string.apollo_action_resume : R.string.apollo_action_start),
                () -> launch(entry)));
        if (entry.running) {
            actions.add(new ActionSheet.Action(R.drawable.ic_apollo_stop,
                    activity.getString(R.string.apollo_action_quit_game), () -> quitGame(entry)).danger());
        }

        if (entry.item != null) {
            String key = entry.item.key;
            actions.add(new ActionSheet.Action(R.drawable.ic_apollo_edit,
                    activity.getString(R.string.quick_launch_rename), () -> showRenameQuickLaunchDialog(key)));
            actions.add(new ActionSheet.Action(R.drawable.ic_apollo_tune,
                    activity.getString(R.string.quick_launch_settings), () -> openQuickLaunchSettings(key)));
            if (pinnedCount > 1) {
                actions.add(new ActionSheet.Action(R.drawable.ic_apollo_reorder,
                        activity.getString(R.string.apollo_quick_launch_reorder), () -> startMove(key)));
            }
            actions.add(new ActionSheet.Action(R.drawable.ic_apollo_delete,
                    activity.getString(R.string.quick_launch_delete), () -> removeFromQuickLaunch(key)).danger());
        } else {
            actions.add(new ActionSheet.Action(R.drawable.ic_apollo_star,
                    activity.getString(R.string.apollo_action_add_quick_launch), () -> pinGame(entry)));
            if (!entry.running) {
                actions.add(new ActionSheet.Action(R.drawable.ic_apollo_delete,
                        activity.getString(R.string.apollo_action_remove_recent), () -> {
                            RecentGames.remove(activity, entry.computerUuid, entry.appId);
                            loadQuickLaunchButtons();
                        }).danger());
            }
        }

        String subtitle = entry.running
                ? activity.getString(R.string.apollo_running_on, entry.computerName)
                : entry.computerName;
        ActionSheet.of(activity).show(entry.title, subtitle, actions);
    }

    private ComputerDetails findComputer(String computerUuid) {
        ComputerManagerService.ComputerManagerBinder managerBinder = callback.getManagerBinder();
        if (managerBinder == null) {
            Toast.makeText(activity, "Computer manager not ready", Toast.LENGTH_SHORT).show();
            return null;
        }
        ComputerDetails computer = managerBinder.getComputer(computerUuid);
        if (computer == null) {
            Toast.makeText(activity, "PC not found", Toast.LENGTH_SHORT).show();
        }
        return computer;
    }

    private void startGame(Entry entry) {
        ComputerDetails computer = findComputer(entry.computerUuid);
        if (computer != null) {
            ServerHelper.doStart(activity, new NvApp(entry.title, entry.appId, false), computer, callback.getManagerBinder());
            if (!entry.nameKnown) {
                // The title is not the real name, the recent list reads it once the library is opened
                RecentGames.clearName(activity, entry.computerUuid, entry.appId);
            }
        }
    }

    private void pinGame(Entry entry) {
        ComputerDetails computer = findComputer(entry.computerUuid);
        if (computer != null) {
            addToQuickLaunch(computer, new NvApp(entry.title, entry.appId, false));
        }
    }

    private void quitGame(Entry entry) {
        if (entry.item != null) {
            quitQuickLaunchApp(entry.item.key);
            return;
        }

        ComputerDetails computer = findComputer(entry.computerUuid);
        if (computer == null) {
            return;
        }
        UiHelper.displayQuitConfirmationDialog(activity, () -> ServerHelper.doQuit(activity, computer,
                new NvApp(entry.title, entry.appId, false), callback.getManagerBinder(),
                () -> quickLaunchManager.updateRunningAppId(0, computer.uuid)), null);
    }

    private void launchQuickLaunchApp(QuickLaunchManager.QuickLaunchItem item) {
        ComputerManagerService.ComputerManagerBinder managerBinder = callback.getManagerBinder();
        if (managerBinder == null) {
            Toast.makeText(activity, "Computer manager not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        // Find the computer by UUID
        ComputerDetails computer = managerBinder.getComputer(item.computerUuid);
        if (computer == null) {
            Toast.makeText(activity, "PC not found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Set this Quick Launch item as the currently running one
        quickLaunchManager.updateLastStartedQuickLaunchKey(item.key);

        // Create NvApp object with the appId
        NvApp app = new NvApp(item.originalAppName, item.appId, false);

        // Use ServerHelper to launch the app with the Quick Launch key
        ServerHelper.doStart(activity, app, computer, managerBinder, item.key);
    }

    private void openQuickLaunchSettings(String key) {
        QuickLaunchManager.QuickLaunchItem targetItem = getQuickLaunchItemByKey(key);
        if (targetItem != null) {
            // Open AppStreamSettings with the Quick Launch key
            Intent settingsIntent = new Intent(activity, AppStreamSettings.class);
            settingsIntent.putExtra(AppStreamSettings.EXTRA_APP_KEY, key);
            settingsIntent.putExtra(AppStreamSettings.EXTRA_APP_NAME, "Quick Launch: " + targetItem.getDisplayNameLong());
            activity.startActivity(settingsIntent);
        }
    }

    private void showRenameQuickLaunchDialog(final String key) {
        // Get current custom name from the key
        String currentCustomName = quickLaunchManager.getCustomName(key);
        String originalName = quickLaunchManager.getOriginalName(key);

        // Create the input dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(activity.getString(R.string.quick_launch_rename_title));
        builder.setMessage("Display name for " + originalName + ":");

        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(currentCustomName);
        input.selectAll();
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String newCustomName = input.getText().toString().trim();
            if (!newCustomName.isEmpty()) {
                quickLaunchManager.updateCustomName(key, newCustomName);
                Toast.makeText(activity, activity.getString(R.string.quick_launch_renamed), Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void removeFromQuickLaunch(final String key) {
        QuickLaunchManager.QuickLaunchItem item = getQuickLaunchItemByKey(key);
        if (item == null) {
            return;
        }

        // Show confirmation dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Remove from Quick Launch");
        builder.setMessage("Remove \"" + item.getDisplayName() + "\" from Quick Launch?");

        builder.setPositiveButton("Remove", (dialog, which) -> {
            quickLaunchManager.removeQuickLaunchItem(key);
            Toast.makeText(activity, activity.getString(R.string.quick_launch_removed), Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    // --- Reordering: the card lifts, the others fade, and it moves with the D-pad or the finger

    private void startMove(String key) {
        GameCardView card = null;
        for (Map.Entry<View, String> pinned : pinnedKeys.entrySet()) {
            if (pinned.getValue().equals(key)) {
                card = (GameCardView) pinned.getKey();
            }
        }
        if (card == null || movingCard != null) {
            return;
        }
        movingCard = card;
        dragging = false;

        for (int i = 0; i < quickLaunchContainer.getChildCount(); i++) {
            View other = quickLaunchContainer.getChildAt(i);
            if (other != card) {
                other.animate().alpha(MOVING_OTHERS_ALPHA).setDuration(ApolloMotion.MEDIUM)
                        .setInterpolator(ApolloMotion.STANDARD).start();
            }
        }
        if (card.isInTouchMode()) {
            // No focus lifts it: lift it here
            card.animate().scaleX(MOVING_SCALE).scaleY(MOVING_SCALE).translationY(-ApolloUi.dp(activity, MOVING_LIFT_DP))
                    .setDuration(ApolloMotion.MEDIUM).setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE).start();
        } else {
            card.requestFocus();
        }
        setSectionTitle(R.string.apollo_quick_launch_reordering);

        HintRow.set(card, ButtonGlyph.DPAD_LEFT_RIGHT, R.string.apollo_hint_move,
                KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_done, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_cancel);
        callback.onQuickLaunchHintsChanged();

        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                    case KeyEvent.KEYCODE_BUTTON_L1:
                        moveBy(-1, true);
                        break;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                    case KeyEvent.KEYCODE_BUTTON_R1:
                        moveBy(1, true);
                        break;
                    case KeyEvent.KEYCODE_BUTTON_A:
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER:
                        if (event.getRepeatCount() == 0) {
                            finishMove(true);
                        }
                        break;
                    case KeyEvent.KEYCODE_BUTTON_B:
                    case KeyEvent.KEYCODE_BACK:
                    case KeyEvent.KEYCODE_ESCAPE:
                        if (event.getRepeatCount() == 0) {
                            finishMove(false);
                        }
                        break;
                }
            }
            // Nothing else moves the focus or opens something while the card moves; the volume keys still work
            return KeyEvent.isGamepadButton(keyCode) || keyCode == KeyEvent.KEYCODE_BACK
                    || keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == KeyEvent.KEYCODE_ENTER
                    || (keyCode >= KeyEvent.KEYCODE_DPAD_UP && keyCode <= KeyEvent.KEYCODE_DPAD_CENTER);
        });

        // A drag moves it over the others, letting go puts it down
        card.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragStartX = event.getRawX();
                    dragging = false;
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - dragStartX;
                    if (!dragging && Math.abs(dx) > ViewConfiguration.get(activity).getScaledTouchSlop()) {
                        dragging = true;
                    }
                    if (dragging) {
                        v.setTranslationX(dx);
                        int index = quickLaunchContainer.indexOfChild(v);
                        View next = index + 1 < pinnedCount ? quickLaunchContainer.getChildAt(index + 1) : null;
                        View previous = index > 0 ? quickLaunchContainer.getChildAt(index - 1) : null;
                        // Past the middle of a neighbor: they swap, and the finger keeps its place on the card
                        if (next != null && dx > (next.getWidth() + gap()) / 2f) {
                            dragStartX += next.getWidth() + gap();
                            moveBy(1, false);
                            v.setTranslationX(event.getRawX() - dragStartX);
                        } else if (previous != null && dx < -(previous.getWidth() + gap()) / 2f) {
                            dragStartX -= previous.getWidth() + gap();
                            moveBy(-1, false);
                            v.setTranslationX(event.getRawX() - dragStartX);
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // Letting go after a drag puts it down; a tap ends the reordering as it is
                    v.animate().translationX(0).setDuration(ApolloMotion.SHORT)
                            .setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE)
                            .withEndAction(() -> finishMove(true)).start();
                    return true;
            }
            return false;
        });
    }

    private int gap() {
        return ApolloUi.dp(activity, CARD_GAP_DP);
    }

    // Swaps the moving card with its neighbor among the pinned games; animate slides the card too
    private void moveBy(int direction, boolean animate) {
        GameCardView card = movingCard;
        int index = quickLaunchContainer.indexOfChild(card);
        int target = index + direction;
        if (card == null || target < 0 || target >= pinnedCount) {
            return;
        }
        View neighbor = quickLaunchContainer.getChildAt(target);
        int cardLeft = card.getLeft();
        int neighborLeft = neighbor.getLeft();

        // The neighbor moves, so the focus stays on the card
        quickLaunchContainer.removeView(neighbor);
        quickLaunchContainer.addView(neighbor, index);
        for (int i = 0; i < quickLaunchContainer.getChildCount(); i++) {
            ((LinearLayout.LayoutParams) quickLaunchContainer.getChildAt(i).getLayoutParams()).leftMargin = i > 0 ? gap() : 0;
        }

        // Once laid out, each slides from where it was
        quickLaunchContainer.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                quickLaunchContainer.getViewTreeObserver().removeOnPreDrawListener(this);
                slideFrom(neighbor, neighborLeft - neighbor.getLeft());
                if (animate) {
                    slideFrom(card, cardLeft - card.getLeft());
                }
                card.requestRectangleOnScreen(new Rect(0, 0, card.getWidth(), card.getHeight()));
                return true;
            }
        });
    }

    private static void slideFrom(View view, int offset) {
        view.animate().cancel();
        view.setTranslationX(view.getTranslationX() + offset);
        view.animate().translationX(0).setDuration(ApolloMotion.MEDIUM)
                .setInterpolator(ApolloMotion.EMPHASIZED_DECELERATE).start();
    }

    /** Ends the reordering: keeps the new order, or puts the cards back as they were */
    public boolean finishMove(boolean keep) {
        if (movingCard == null) {
            return false;
        }
        List<String> order = new ArrayList<>();
        for (int i = 0; i < pinnedCount; i++) {
            order.add(pinnedKeys.get(quickLaunchContainer.getChildAt(i)));
        }
        boolean refocus = movingCard.hasFocus();
        Object movedTag = movingCard.getTag();
        movingCard = null;
        reloadPending = false;
        setSectionTitle(R.string.apollo_section_quick_launch);

        // The row is built again, without the lift and the fade
        shownSignature = null;
        if (keep) {
            quickLaunchManager.setOrder(order);
        }
        loadQuickLaunchButtons();
        View card = quickLaunchContainer.findViewWithTag(movedTag);
        if (refocus && card != null) {
            card.requestFocus();
        }
        callback.onQuickLaunchHintsChanged();
        return true;
    }

    private void setSectionTitle(int textRes) {
        TextView title = quickLaunchSection.findViewById(R.id.quickLaunchTitle);
        title.animate().cancel();
        title.animate().alpha(0).setDuration(ApolloMotion.SHORT).setInterpolator(ApolloMotion.STANDARD)
                .withEndAction(() -> {
                    title.setText(textRes);
                    title.animate().alpha(1).setDuration(ApolloMotion.SHORT).setInterpolator(ApolloMotion.STANDARD).start();
                }).start();
    }

    private QuickLaunchManager.QuickLaunchItem getQuickLaunchItemByKey(String key) {
        for (QuickLaunchManager.QuickLaunchItem item : quickLaunchManager.getAllQuickLaunchItems()) {
            if (item.key.equals(key)) {
                return item;
            }
        }
        return null;
    }

    private void quitQuickLaunchApp(String key) {
        QuickLaunchManager.QuickLaunchItem item = getQuickLaunchItemByKey(key);
        if (item == null) {
            Toast.makeText(activity, "Quick Launch item not found", Toast.LENGTH_SHORT).show();
            return;
        }

        ComputerManagerService.ComputerManagerBinder managerBinder = callback.getManagerBinder();
        if (managerBinder == null) {
            Toast.makeText(activity, "Computer manager not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        // Find the computer by UUID
        ComputerDetails computer = managerBinder.getComputer(item.computerUuid);
        if (computer == null) {
            Toast.makeText(activity, "PC not found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create NvApp object with the appId
        NvApp app = new NvApp(item.originalAppName, item.appId, false);

        // Display a confirmation dialog first
        UiHelper.displayQuitConfirmationDialog(activity, () -> {
            // Use ServerHelper to quit the app
            ServerHelper.doQuit(activity, computer, app, managerBinder,
                    () -> quickLaunchManager.updateRunningAppId(0, computer.uuid));
        }, null);
    }

    /**
     * Update running app status from external source (e.g., PcView)
     */
    public void updateRunningStatus(int runningAppId, String serverUuid) {
        quickLaunchManager.updateRunningAppId(runningAppId, serverUuid);
    }
}
