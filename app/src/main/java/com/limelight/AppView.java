package com.limelight;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.grid.AppGridAdapter;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.preferences.StreamSettings;
import com.limelight.ui.apollo.ActionSheet;
import com.limelight.ui.apollo.ApolloTopBar;
import com.limelight.ui.apollo.hints.HintRow;
import com.limelight.ui.apollo.hints.ScreenHints;
import com.limelight.ui.apollo.ApolloUi;
import com.limelight.ui.apollo.GameCardView;
import com.limelight.ui.apollo.settings.QuickSettingsPanel;
import com.limelight.ui.theme.ApolloColors;
import com.limelight.utils.QuickLaunchManager;
import com.limelight.utils.CacheHelper;
import com.limelight.utils.Dialog;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.ShortcutHelper;
import com.limelight.utils.SpinnerDialog;
import com.limelight.utils.UiHelper;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.xmlpull.v1.XmlPullParserException;

public class AppView extends Activity {
    private static final int COLOR_ONLINE = 0xFF6DD58C;
    private static final int LARGE_CARD_DP = 124;
    private static final int SMALL_CARD_DP = 96;

    private AppGridAdapter appGridAdapter;
    private ApolloTopBar topBar;
    private RecyclerView libraryGrid;
    private GridLayoutManager layoutManager;
    private LibraryAdapter libraryAdapter;
    private int cardWidth = 1;
    private String uuidString;
    private ShortcutHelper shortcutHelper;
    private QuickLaunchManager quickLaunchManager;

    private ComputerDetails computer;
    private ComputerManagerService.ApplistPoller poller;
    private SpinnerDialog blockingLoadSpinner;
    private String lastRawApplist;
    private int lastRunningAppId;
    private boolean suspendGridUpdates;
    private boolean inForeground;
    private boolean showHiddenApps;
    private HashSet<Integer> hiddenAppIds = new HashSet<>();

    private final static int START_OR_RESUME_ID = 1;
    private final static int QUIT_ID = 2;
    private final static int START_WITH_QUIT = 4;
    private final static int VIEW_DETAILS_ID = 5;
    private final static int CREATE_SHORTCUT_ID = 6;
    private final static int HIDE_APP_ID = 7;
    private final static int APP_SETTINGS_ID = 8;
    private final static int ADD_QUICK_LAUNCH_ID = 9;
    
    private final static int REQUEST_APP_SETTINGS = 1001;

    public final static String HIDDEN_APPS_PREF_FILENAME = "HiddenApps";

    public final static String NAME_EXTRA = "Name";
    public final static String UUID_EXTRA = "UUID";
    public final static String NEW_PAIR_EXTRA = "NewPair";
    public final static String SHOW_HIDDEN_APPS_EXTRA = "ShowHiddenApps";

    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder binder) {
            final ComputerManagerService.ComputerManagerBinder localBinder =
                    ((ComputerManagerService.ComputerManagerBinder)binder);

            // Wait in a separate thread to avoid stalling the UI
            new Thread() {
                @Override
                public void run() {
                    // Wait for the binder to be ready
                    localBinder.waitForReady();

                    // Get the computer object
                    computer = localBinder.getComputer(uuidString);
                    if (computer == null) {
                        finish();
                        return;
                    }

                    // Add a launcher shortcut for this PC (forced, since this is user interaction)
                    shortcutHelper.createAppViewShortcut(computer, true, getIntent().getBooleanExtra(NEW_PAIR_EXTRA, false));
                    shortcutHelper.reportComputerShortcutUsed(computer);

                    try {
                        appGridAdapter = new AppGridAdapter(AppView.this,
                                PreferenceConfiguration.readPreferences(AppView.this),
                                computer, localBinder.getUniqueId(),
                                showHiddenApps);
                    } catch (Exception e) {
                        e.printStackTrace();
                        finish();
                        return;
                    }

                    appGridAdapter.updateHiddenApps(hiddenAppIds, true);

                    // Now make the binder visible. We must do this after appGridAdapter
                    // is set to prevent us from reaching updateUiWithServerinfo() and
                    // touching the appGridAdapter prior to initialization.
                    managerBinder = localBinder;

                    // Load the app grid with cached data (if possible).
                    // This must be done _before_ startComputerUpdates()
                    // so the initial serverinfo response can update the running
                    // icon.
                    populateAppGridWithCache();

                    // Start updates
                    startComputerUpdates();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing() || isChangingConfigurations()) {
                                return;
                            }

                            setupLibraryGrid();
                        }
                    });
                }
            }.start();
        }

        public void onServiceDisconnected(ComponentName className) {
            managerBinder = null;
        }
    };

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // If appGridAdapter is initialized, let it know about the configuration change.
        // If not, it will pick it up when it initializes.
        if (appGridAdapter != null) {
            // Update the app grid adapter to create grid items with the correct layout
            appGridAdapter.updateLayoutWithPreferences(this, PreferenceConfiguration.readPreferences(this));

            // The number of columns follows the new width
            if (libraryAdapter != null) {
                libraryGrid.requestLayout();
                libraryAdapter.notifyDataSetChanged();
            }
        }
    }

    private void startComputerUpdates() {
        // Don't start polling if we're not bound or in the foreground
        if (managerBinder == null || !inForeground) {
            return;
        }

        managerBinder.startPolling(new ComputerManagerListener() {
            @Override
            public void notifyComputerUpdated(final ComputerDetails details, boolean isFreshPoll) {
                // Do nothing if updates are suspended
                if (suspendGridUpdates) {
                    return;
                }

                // Don't care about other computers
                if (!details.uuid.equalsIgnoreCase(uuidString)) {
                    return;
                }

                if (details.state == ComputerDetails.State.OFFLINE) {
                    // The PC is unreachable now
                    AppView.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Display a toast to the user and quit the activity
                            Toast.makeText(AppView.this, getResources().getText(R.string.lost_connection), Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });

                    return;
                }

                // Close immediately if the PC is no longer paired
                if (details.state == ComputerDetails.State.ONLINE && details.pairState != PairingManager.PairState.PAIRED) {
                    AppView.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Disable shortcuts referencing this PC for now
                            shortcutHelper.disableComputerShortcut(details,
                                    getResources().getString(R.string.scut_not_paired));

                            // Display a toast to the user and quit the activity
                            Toast.makeText(AppView.this, getResources().getText(R.string.scut_not_paired), Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });

                    return;
                }

                // App list is the same or empty
                if (details.rawAppList == null || details.rawAppList.equals(lastRawApplist)) {

                    // Let's check if the running app ID changed
                    if (details.runningGameId != lastRunningAppId) {
                        // Update the currently running game using the app ID
                        lastRunningAppId = details.runningGameId;
                        updateUiWithServerinfo(details);
                    }

                    return;
                }

                lastRunningAppId = details.runningGameId;
                lastRawApplist = details.rawAppList;

                try {
                    updateUiWithAppList(NvHTTP.getAppListByReader(new StringReader(details.rawAppList)));
                    updateUiWithServerinfo(details);

                    if (blockingLoadSpinner != null) {
                        blockingLoadSpinner.dismiss();
                        blockingLoadSpinner = null;
                    }
                } catch (XmlPullParserException | IOException e) {
                    e.printStackTrace();
                }
            }
        });

        if (poller == null) {
            poller = managerBinder.createAppListPoller(computer);
        }
        poller.start();
    }

    private void stopComputerUpdates() {
        if (poller != null) {
            poller.stop();
        }

        if (managerBinder != null) {
            managerBinder.stopPolling();
        }

        if (appGridAdapter != null) {
            appGridAdapter.cancelQueuedOperations();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Assume we're in the foreground when created to avoid a race
        // between binding to CMS and onResume()
        inForeground = true;

        shortcutHelper = new ShortcutHelper(this);
        quickLaunchManager = QuickLaunchManager.getInstance(this);

        UiHelper.setLocale(this);

        setContentView(R.layout.activity_app_view);
        overridePendingTransition(R.anim.apollo_fade_in, R.anim.apollo_fade_out);

        // Allow floating expanded PiP overlays while browsing apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setShouldDockBigOverlays(false);
        }

        UiHelper.notifyNewRootView(this);

        showHiddenApps = getIntent().getBooleanExtra(SHOW_HIDDEN_APPS_EXTRA, false);
        uuidString = getIntent().getStringExtra(UUID_EXTRA);

        SharedPreferences hiddenAppsPrefs = getSharedPreferences(HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE);
        for (String hiddenAppIdStr : hiddenAppsPrefs.getStringSet(uuidString, new HashSet<String>())) {
            hiddenAppIds.add(Integer.parseInt(hiddenAppIdStr));
        }

        String computerName = getIntent().getStringExtra(NAME_EXTRA);

        TextView label = findViewById(R.id.appListText);
        setTitle(computerName);
        label.setText(computerName);
        initializeHeader();

        // Bind to the computer manager service
        bindService(new Intent(this, ComputerManagerService.class), serviceConnection,
                Service.BIND_AUTO_CREATE);
    }

    private void updateHiddenApps(boolean hideImmediately) {
        HashSet<String> hiddenAppIdStringSet = new HashSet<>();

        for (Integer hiddenAppId : hiddenAppIds) {
            hiddenAppIdStringSet.add(hiddenAppId.toString());
        }

        getSharedPreferences(HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE)
                .edit()
                .putStringSet(uuidString, hiddenAppIdStringSet)
                .apply();

        appGridAdapter.updateHiddenApps(hiddenAppIds, hideImmediately);
    }

    private void populateAppGridWithCache() {
        try {
            // Try to load from cache
            lastRawApplist = CacheHelper.readInputStreamToString(CacheHelper.openCacheFileForInput(getCacheDir(), "applist", uuidString));
            List<NvApp> applist = NvHTTP.getAppListByReader(new StringReader(lastRawApplist));
            updateUiWithAppList(applist);
            LimeLog.info("Loaded applist from cache");
        } catch (IOException | XmlPullParserException e) {
            if (lastRawApplist != null) {
                LimeLog.warning("Saved applist corrupted: "+lastRawApplist);
                e.printStackTrace();
            }
            LimeLog.info("Loading applist from the network");
            // We'll need to load from the network
            loadAppsBlocking();
        }
    }

    private void loadAppsBlocking() {
        blockingLoadSpinner = SpinnerDialog.displayDialog(this, getResources().getString(R.string.applist_refresh_title),
                getResources().getString(R.string.applist_refresh_msg), true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        SpinnerDialog.closeDialogs(this);
        Dialog.closeDialogs();

        if (managerBinder != null) {
            unbindService(serviceConnection);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Display a decoder crash notification if we've returned after a crash
        UiHelper.showDecoderCrashDialog(this);

        inForeground = true;
        // Back from Settings the tab pill slides: the updates wait for it, or it stutters
        if (topBar.onResume()) {
            topBar.postDelayed(() -> {
                if (inForeground) {
                    startComputerUpdates();
                }
            }, ApolloTopBar.SLIDE_SETTLE_MS);
        } else {
            startComputerUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        inForeground = false;
        stopComputerUpdates();
        topBar.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_APP_SETTINGS) {
            // Refresh the grid adapter to update settings indicators
            if (appGridAdapter != null) {
                appGridAdapter.notifyDataSetChanged();
            }
        }
    }

    // Options of a game, in the side sheet opened with Y or a long press (the context menu before)
    private void showAppActions(AppObject selectedApp, GameCardView card) {
        List<ActionSheet.Action> actions = new ArrayList<>();
        boolean running = lastRunningAppId == selectedApp.app.getAppId();

        if (lastRunningAppId != 0) {
            if (running) {
                actions.add(appAction(R.drawable.ic_apollo_play, R.string.apollo_action_resume, START_OR_RESUME_ID, selectedApp, card));
                actions.add(appAction(R.drawable.ic_apollo_stop, R.string.apollo_action_quit_game, QUIT_ID, selectedApp, card).danger());
            }
            else {
                actions.add(appAction(R.drawable.ic_apollo_play, R.string.apollo_action_quit_and_start, START_WITH_QUIT, selectedApp, card));
            }
        }
        else {
            actions.add(appAction(R.drawable.ic_apollo_play, R.string.apollo_action_start, START_OR_RESUME_ID, selectedApp, card));
        }

        actions.add(appAction(R.drawable.ic_apollo_star, R.string.apollo_action_add_quick_launch, ADD_QUICK_LAUNCH_ID, selectedApp, card));
        actions.add(appAction(R.drawable.ic_apollo_tune, R.string.apollo_action_game_settings, APP_SETTINGS_ID, selectedApp, card));

        // Only offer to hide the game if it is not the one running, or to show it again if it is hidden
        if (!running || selectedApp.isHidden) {
            actions.add(appAction(R.drawable.ic_apollo_hide,
                    selectedApp.isHidden ? R.string.apollo_action_unhide : R.string.apollo_action_hide,
                    HIDE_APP_ID, selectedApp, card));
        }

        // A launcher shortcut needs the box art
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && card.hasCoverArt()
                && card.getCover().getDrawable() instanceof BitmapDrawable) {
            actions.add(appAction(R.drawable.ic_apollo_shortcut, R.string.apollo_action_shortcut, CREATE_SHORTCUT_ID, selectedApp, card));
        }

        actions.add(appAction(R.drawable.ic_apollo_info, R.string.apollo_action_details, VIEW_DETAILS_ID, selectedApp, card));

        String subtitle = computer.name;
        if (running) {
            subtitle = getString(R.string.apollo_running_on, computer.name);
        }
        ActionSheet.of(this).show(selectedApp.app.getAppName(), subtitle, actions);
    }

    private ActionSheet.Action appAction(int icon, int label, int id, AppObject app, GameCardView card) {
        return new ActionSheet.Action(icon, getString(label), () -> runAppAction(id, app, card));
    }

    private boolean runAppAction(int itemId, final AppObject app, GameCardView card) {
        switch (itemId) {
            case START_WITH_QUIT:
                // Display a confirmation dialog first
                UiHelper.displayQuitConfirmationDialog(this, new Runnable() {
                    @Override
                    public void run() {
                        ServerHelper.doStart(AppView.this, app.app, computer, managerBinder);
                    }
                }, null);
                return true;

            case START_OR_RESUME_ID:
                // Resume is the same as start for us
                ServerHelper.doStart(AppView.this, app.app, computer, managerBinder);
                return true;

            case QUIT_ID:
                // Display a confirmation dialog first
                UiHelper.displayQuitConfirmationDialog(this, new Runnable() {
                    @Override
                    public void run() {
                        suspendGridUpdates = true;
                        ServerHelper.doQuit(AppView.this, computer,
                                app.app, managerBinder, new Runnable() {
                            @Override
                            public void run() {
                                // Trigger a poll immediately
                                suspendGridUpdates = false;
                                if (poller != null) {
                                    poller.pollNow();
                                }
                            }
                        });
                    }
                }, null);
                return true;

            case VIEW_DETAILS_ID:
                Dialog.displayDialog(AppView.this, getResources().getString(R.string.title_details), app.app.toString(), false);
                return true;

            case APP_SETTINGS_ID:
                Intent settingsIntent = new Intent(AppView.this, com.limelight.preferences.AppStreamSettings.class);
                settingsIntent.putExtra(com.limelight.preferences.AppStreamSettings.EXTRA_APP_KEY, computer.uuid + ":" + app.app.getAppId());
                settingsIntent.putExtra(com.limelight.preferences.AppStreamSettings.EXTRA_APP_NAME, app.app.getAppName());
                startActivityForResult(settingsIntent, REQUEST_APP_SETTINGS);
                return true;

            case HIDE_APP_ID:
                if (app.isHidden) {
                    // Transitioning hidden to shown
                    hiddenAppIds.remove(app.app.getAppId());
                }
                else {
                    // Transitioning shown to hidden
                    hiddenAppIds.add(app.app.getAppId());
                }
                updateHiddenApps(false);
                return true;

            case CREATE_SHORTCUT_ID:
                Bitmap appBits = ((BitmapDrawable)card.getCover().getDrawable()).getBitmap();
                if (!shortcutHelper.createPinnedGameShortcut(computer, app.app, appBits)) {
                    Toast.makeText(AppView.this, getResources().getString(R.string.unable_to_pin_shortcut), Toast.LENGTH_LONG).show();
                }
                return true;

            case ADD_QUICK_LAUNCH_ID:
                addToQuickLaunch(computer, app.app);
                return true;

            default:
                return false;
        }
    }

    private void updateUiWithServerinfo(final ComputerDetails details) {
        AppView.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean updated = false;

                    // Look through our current app list to tag the running app
                for (int i = 0; i < appGridAdapter.getCount(); i++) {
                    AppObject existingApp = (AppObject) appGridAdapter.getItem(i);

                    // There can only be one or zero apps running.
                    if (existingApp.isRunning &&
                            existingApp.app.getAppId() == details.runningGameId) {
                        // This app was running and still is, so we're done now
                        return;
                    }
                    else if (existingApp.app.getAppId() == details.runningGameId) {
                        // This app wasn't running but now is
                        existingApp.isRunning = true;
                        updated = true;
                    }
                    else if (existingApp.isRunning) {
                        // This app was running but now isn't
                        existingApp.isRunning = false;
                        updated = true;
                    }
                    else {
                        // This app wasn't running and still isn't
                    }
                }

                if (updated) {
                    appGridAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    private void updateUiWithAppList(final List<NvApp> appList) {
        AppView.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean updated = false;

                // First handle app updates and additions
                for (NvApp app : appList) {
                    boolean foundExistingApp = false;

                    // Try to update an existing app in the list first
                    for (int i = 0; i < appGridAdapter.getCount(); i++) {
                        AppObject existingApp = (AppObject) appGridAdapter.getItem(i);
                        if (existingApp.app.getAppId() == app.getAppId()) {
                            // Found the app; update its properties
                            if (!existingApp.app.getAppName().equals(app.getAppName())) {
                                existingApp.app.setAppName(app.getAppName());
                                updated = true;
                            }

                            foundExistingApp = true;
                            break;
                        }
                    }

                    if (!foundExistingApp) {
                        // This app must be new
                        appGridAdapter.addApp(new AppObject(app));

                        // We could have a leftover shortcut from last time this PC was paired
                        // or if this app was removed then added again. Enable those shortcuts
                        // again if present.
                        shortcutHelper.enableAppShortcut(computer, app);

                        updated = true;
                    }
                }

                // Next handle app removals
                int i = 0;
                while (i < appGridAdapter.getCount()) {
                    boolean foundExistingApp = false;
                    AppObject existingApp = (AppObject) appGridAdapter.getItem(i);

                    // Check if this app is in the latest list
                    for (NvApp app : appList) {
                        if (existingApp.app.getAppId() == app.getAppId()) {
                            foundExistingApp = true;
                            break;
                        }
                    }

                    // This app was removed in the latest app list
                    if (!foundExistingApp) {
                        shortcutHelper.disableAppShortcut(computer, existingApp.app, "App removed from PC");
                        appGridAdapter.removeApp(existingApp);
                        updated = true;

                        // Check this same index again because the item at i+1 is now at i after
                        // the removal
                        continue;
                    }

                    // Move on to the next item
                    i++;
                }

                if (updated) {
                    appGridAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    // Top bar, back button and status of the PC above the grid
    private void initializeHeader() {
        ApolloColors colors = ApolloColors.dark(this);

        topBar = new ApolloTopBar(this, colors);
        topBar.setTabs(new String[] {getString(R.string.apollo_tab_home), getString(R.string.apollo_tab_settings)}, 0,
                index -> {
                    startActivity(new Intent(AppView.this, StreamSettings.class));
                    overridePendingTransition(R.anim.apollo_fade_in, R.anim.apollo_fade_out);
                });
        topBar.setOnQuickSettingsClickListener(v -> QuickSettingsPanel.of(this).toggle());
        ((FrameLayout) findViewById(R.id.topBarContainer)).addView(topBar);

        findViewById(R.id.libraryStatus).setBackground(ApolloUi.roundRect(colors.surfaceContainerHigh, ApolloUi.dp(this, 14)));
        findViewById(R.id.libraryStatusDot).setBackground(ApolloUi.roundRect(COLOR_ONLINE, ApolloUi.dp(this, 4)));
        ((TextView) findViewById(R.id.libraryStatusText)).setText(showHiddenApps
                ? getString(R.string.apollo_pc_online) + " · " + getString(R.string.apollo_library_hidden_shown)
                : getString(R.string.apollo_pc_online));

        // Keep the content clear of a notch, the window draws under it
        findViewById(R.id.libraryColumn).setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && insets.getDisplayCutout() != null) {
                v.setPadding(insets.getDisplayCutout().getSafeInsetLeft(), 0,
                        insets.getDisplayCutout().getSafeInsetRight(), 0);
            }
            return insets;
        });

        // Gamepad hints at the bottom
        HintRow hintRow = ScreenHints.attach(this, findViewById(R.id.libraryColumn));
        hintRow.setFallback(HintRow.hint(this, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_back),
                HintRow.hint(this, KeyEvent.KEYCODE_BUTTON_SELECT, R.string.apollo_hint_quick_settings));

        libraryGrid = findViewById(R.id.libraryGrid);
        int side = ApolloUi.dp(this, 22) - gridRoom(this);
        libraryGrid.setPadding(side, ApolloUi.dp(this, 10), side, ApolloUi.dp(this, 16));
        libraryGrid.setClipToPadding(false);
        libraryGrid.setClipChildren(false);
        libraryGrid.setItemAnimator(null);
        // Only the cards take the focus, not the grid around them
        libraryGrid.setFocusableInTouchMode(false);
        libraryGrid.setFocusable(false);
        layoutManager = new GridLayoutManager(this, 1);
        libraryGrid.setLayoutManager(layoutManager);
        libraryGrid.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                int room = gridRoom(AppView.this);
                outRect.set(room, room, room, room);
            }
        });
        // The columns follow the width of the screen
        libraryGrid.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft) {
                v.post(this::updateGridColumns);
            }
        });
    }

    // Space around each card, so it grows on focus without touching its neighbours
    private static int gridRoom(Activity activity) {
        return ApolloUi.dp(activity, 7);
    }

    private void updateGridColumns() {
        int available = libraryGrid.getWidth() - libraryGrid.getPaddingLeft() - libraryGrid.getPaddingRight();
        if (available <= 0) {
            return;
        }
        int room = gridRoom(this) * 2;
        int target = ApolloUi.dp(this, PreferenceConfiguration.readPreferences(this).smallIconMode
                ? SMALL_CARD_DP : LARGE_CARD_DP) + room;
        int columns = Math.max(1, available / target);
        int width = available / columns - room;
        if (columns != layoutManager.getSpanCount() || width != cardWidth) {
            layoutManager.setSpanCount(columns);
            cardWidth = width;
            if (libraryAdapter != null) {
                libraryAdapter.notifyDataSetChanged();
            }
        }
    }

    private void setupLibraryGrid() {
        libraryAdapter = new LibraryAdapter();
        libraryAdapter.setHasStableIds(true);
        libraryGrid.setAdapter(libraryAdapter);
        appGridAdapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                libraryAdapter.notifyDataSetChanged();
            }
        });
        updateGridColumns();
    }

    private void onAppClicked(AppObject app, GameCardView card) {
        // Only open the options if something is running, otherwise start it
        if (lastRunningAppId != 0) {
            showAppActions(app, card);
        } else {
            ServerHelper.doStart(AppView.this, app.app, computer, managerBinder);
        }
    }

    private static class CardHolder extends RecyclerView.ViewHolder {
        final GameCardView card;
        AppObject app;

        CardHolder(GameCardView card) {
            super(card);
            this.card = card;
        }
    }

    // The library grid, a view of the apps kept by the AppGridAdapter
    private class LibraryAdapter extends RecyclerView.Adapter<CardHolder> {
        private final ApolloColors colors = ApolloColors.dark(AppView.this);

        @Override
        public CardHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            GameCardView card = new GameCardView(AppView.this, colors, Math.max(cardWidth, 1));
            CardHolder holder = new CardHolder(card);
            card.setOnClickListener(v -> {
                if (holder.app != null) {
                    onAppClicked(holder.app, card);
                }
            });
            card.setOnLongClickListener(v -> {
                if (holder.app != null) {
                    showAppActions(holder.app, card);
                }
                return true;
            });
            return holder;
        }

        @Override
        public void onBindViewHolder(CardHolder holder, int position) {
            AppObject app = (AppObject) appGridAdapter.getItem(position);
            holder.app = app;
            holder.card.setCardWidth(cardWidth);
            holder.card.bind(app.app.getAppName(), null, app.app.getAppId());
            holder.card.setRunning(app.isRunning);
            holder.card.setCustomSettings(appGridAdapter.hasCustomSettings(app));
            holder.card.setAlpha(app.isHidden ? 0.4f : 1f);
            HintRow.set(holder.card, KeyEvent.KEYCODE_BUTTON_A, app.isRunning ? R.string.apollo_hint_resume : R.string.apollo_hint_start,
                    KeyEvent.KEYCODE_BUTTON_Y, R.string.apollo_hint_options, KeyEvent.KEYCODE_BUTTON_B, R.string.apollo_hint_back,
                    KeyEvent.KEYCODE_BUTTON_SELECT, R.string.apollo_hint_quick_settings);
            appGridAdapter.populateCover(app, holder.card.getCover(), holder.card.getPlaceholderSignal());

            // A gamepad starts from the first game
            if (position == 0) {
                holder.card.post(() -> {
                    if (holder.getAdapterPosition() == 0) {
                        ApolloUi.focusByDefault(AppView.this, holder.card);
                    }
                });
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                holder.card.setFocusedByDefault(false);
            }
        }

        @Override
        public int getItemCount() {
            return appGridAdapter.getCount();
        }

        @Override
        public long getItemId(int position) {
            return ((AppObject) appGridAdapter.getItem(position)).app.getAppId();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getRepeatCount() == 0) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_BUTTON_Y: {
                    // The options of the focused game, like a long press
                    View focused = getCurrentFocus();
                    if (focused instanceof GameCardView) {
                        focused.performLongClick();
                    }
                    return true;
                }
                case KeyEvent.KEYCODE_BUTTON_L1:
                    topBar.switchTab(-1);
                    return true;
                case KeyEvent.KEYCODE_BUTTON_R1:
                    topBar.switchTab(1);
                    return true;
                case KeyEvent.KEYCODE_BUTTON_SELECT:
                    QuickSettingsPanel.of(this).toggle();
                    return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    // The gamepad hints show while keys are used and hide at the first touch
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

    @Override
    public void onBackPressed() {
        if (ActionSheet.of(this).dismiss() || QuickSettingsPanel.of(this).dismiss()) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.apollo_fade_in, R.anim.apollo_fade_out);
    }

    private void addToQuickLaunch(ComputerDetails computer, NvApp app) {
        quickLaunchManager.addQuickLaunchItem(computer, app);
        Toast.makeText(this, getResources().getString(R.string.quick_launch_added), Toast.LENGTH_SHORT).show();
    }

    public static class AppObject {
        public final NvApp app;
        public boolean isRunning;
        public boolean isHidden;

        public AppObject(NvApp app) {
            if (app == null) {
                throw new IllegalArgumentException("app must not be null");
            }
            this.app = app;
        }

        @Override
        public String toString() {
            return app.getAppName();
        }
    }
}
