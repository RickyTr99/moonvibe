package com.limelight;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import com.limelight.binding.PlatformBinding;
import com.limelight.binding.crypto.AndroidCryptoProvider;
import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.grid.PcGridAdapter;
import com.limelight.grid.assets.DiskAssetLoader;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.http.PairingManager.PairState;
import com.limelight.nvstream.wol.WakeOnLanSender;
import com.limelight.preferences.AddComputerManually;
import com.limelight.preferences.GlPreferences;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.preferences.StreamSettings;
import com.limelight.ui.apollo.settings.ProfileMenu;
import com.limelight.ui.apollo.settings.QuickSettingsPanel;
import com.limelight.ui.QuickLaunchView;
import com.limelight.ui.apollo.ActionSheet;
import com.limelight.ui.apollo.ApolloTopBar;
import com.limelight.ui.apollo.ApolloUi;
import com.limelight.ui.apollo.hints.HintRow;
import com.limelight.ui.apollo.hints.ScreenHints;
import com.limelight.ui.apollo.PcCardRow;
import com.limelight.ui.theme.ApolloColors;
import com.limelight.utils.Dialog;
import com.limelight.utils.HelpLauncher;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.SessionResumeManager;
import com.limelight.utils.ShortcutHelper;
import com.limelight.utils.UiHelper;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.database.DataSetObserver;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.text.Html;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParserException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class PcView extends Activity implements QuickLaunchView.QuickLaunchCallback {
    private View noPcFoundLayout;
    private ApolloTopBar topBar;
    private HintRow hintRow;
    private PcCardRow pcCardRow;
    private DataSetObserver pcCardRowObserver;
    private PcGridAdapter pcGridAdapter;
    private ShortcutHelper shortcutHelper;
    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private boolean freezeUpdates, runningPolling, inForeground, completeOnCreateCalled;
    private int autoResumeNoGamePollCount = 0;
    private boolean hasShownRefreshRateToast = false;
    private QuickLaunchView quickLaunchView;
    
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

                    // Now make the binder visible
                    managerBinder = localBinder;

                    // Start updates
                    startComputerUpdates();

                    // Force a keypair to be generated early to avoid discovery delays
                    new AndroidCryptoProvider(PcView.this).getClientCertificate();
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

        // Only reinitialize views if completeOnCreate() was called
        // before this callback. If it was not, completeOnCreate() will
        // handle initializing views with the config change accounted for.
        // This is not prone to races because both callbacks are invoked
        // in the main thread.
        if (completeOnCreateCalled) {
            // Reinitialize views just in case orientation changed
            initializeViews();
        }
    }

    private final static int PAIR_ID = 2;
    private final static int UNPAIR_ID = 3;
    private final static int WOL_ID = 4;
    private final static int DELETE_ID = 5;
    private final static int RESUME_ID = 6;
    private final static int QUIT_ID = 7;
    private final static int VIEW_DETAILS_ID = 8;
    private final static int FULL_APP_LIST_ID = 9;
    private final static int TEST_NETWORK_ID = 10;
    private final static int GAMESTREAM_EOL_ID = 11;

    private void initializeViews() {
        setContentView(R.layout.activity_pc_view);

        UiHelper.notifyNewRootView(this);

        // Allow floating expanded PiP overlays while browsing PCs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setShouldDockBigOverlays(false);
        }

        // Set default preferences if we've never been run
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        ApolloColors colors = ApolloColors.dark(this);

        // Top bar: quick settings, Home / Settings tabs, device status
        topBar = new ApolloTopBar(this, colors);
        topBar.setTabs(new String[] {getString(R.string.apollo_tab_home), getString(R.string.apollo_tab_settings)}, 0,
                index -> {
                    startActivity(new Intent(PcView.this, StreamSettings.class));
                    overridePendingTransition(R.anim.apollo_fade_in, R.anim.apollo_fade_out);
                });
        topBar.setOnQuickSettingsClickListener(v -> toggleQuickSettings());
        topBar.setOnProfileClickListener(v -> ProfileMenu.of(this).toggle());
        ((FrameLayout) findViewById(R.id.topBarContainer)).addView(topBar);
        topBar.onResume();

        // Keep the content clear of a notch, the window draws under it
        ApolloUi.padForCutout(findViewById(R.id.homeColumn));

        // Initialize Quick Launch component
        LinearLayout quickLaunchSection = findViewById(R.id.quickLaunchSection);
        LinearLayout quickLaunchContainer = findViewById(R.id.quickLaunchContainer);
        quickLaunchView = new QuickLaunchView(this, quickLaunchSection, quickLaunchContainer, this);

        // PC cards, rebuilt from the adapter that the polling keeps up to date
        pcCardRow = new PcCardRow(findViewById(R.id.pcRow), colors, new PcCardRow.Listener() {
            @Override
            public void onPcClicked(ComputerDetails computer) {
                onComputerClicked(computer);
            }

            @Override
            public void onPcLongClicked(ComputerDetails computer) {
                showComputerActions(computer);
            }

            @Override
            public void onAddPcClicked() {
                startActivity(new Intent(PcView.this, AddComputerManually.class));
            }
        });
        if (pcCardRowObserver != null) {
            pcGridAdapter.unregisterDataSetObserver(pcCardRowObserver);
        }
        pcCardRowObserver = new DataSetObserver() {
            @Override
            public void onChanged() {
                refreshPcCards();
            }
        };
        pcGridAdapter.registerDataSetObserver(pcCardRowObserver);

        // Gamepad hints at the bottom, from the focused card
        hintRow = ScreenHints.attach(this, findViewById(R.id.homeColumn));
        hintRow.setFallback(HintRow.hint(this, KeyEvent.KEYCODE_BUTTON_X, R.string.apollo_hint_add_pc),
                HintRow.hint(this, KeyEvent.KEYCODE_BUTTON_SELECT, R.string.apollo_profiles),
                HintRow.hint(this, KeyEvent.KEYCODE_BUTTON_START, R.string.apollo_hint_quick_settings));

        noPcFoundLayout = findViewById(R.id.no_pc_found_layout);
        View emptyAddPcButton = findViewById(R.id.emptyAddPcButton);
        emptyAddPcButton.setOnClickListener(v -> startActivity(new Intent(PcView.this, AddComputerManually.class)));
        ApolloUi.scaleOnFocus(emptyAddPcButton);
        HintRow.set(emptyAddPcButton, KeyEvent.KEYCODE_BUTTON_A, R.string.apollo_hint_add_pc,
                KeyEvent.KEYCODE_BUTTON_START, R.string.apollo_hint_quick_settings);
        if (pcGridAdapter.getCount() == 0) {
            noPcFoundLayout.setVisibility(View.VISIBLE);
        }
        else {
            noPcFoundLayout.setVisibility(View.GONE);
        }
        pcGridAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Assume we're in the foreground when created to avoid a race
        // between binding to CMS and onResume()
        inForeground = true;

        // Create a GLSurfaceView to fetch GLRenderer unless we have
        // a cached result already.
        final GlPreferences glPrefs = GlPreferences.readPreferences(this);
        if (!glPrefs.savedFingerprint.equals(Build.FINGERPRINT) || glPrefs.glRenderer.isEmpty()) {
            GLSurfaceView surfaceView = new GLSurfaceView(this);
            surfaceView.setRenderer(new GLSurfaceView.Renderer() {
                @Override
                public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
                    // Save the GLRenderer string so we don't need to do this next time
                    glPrefs.glRenderer = gl10.glGetString(GL10.GL_RENDERER);
                    glPrefs.savedFingerprint = Build.FINGERPRINT;
                    glPrefs.writePreferences();

                    LimeLog.info("Fetched GL Renderer: " + glPrefs.glRenderer);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            completeOnCreate();
                        }
                    });
                }

                @Override
                public void onSurfaceChanged(GL10 gl10, int i, int i1) {
                }

                @Override
                public void onDrawFrame(GL10 gl10) {
                }
            });
            setContentView(surfaceView);
        }
        else {
            LimeLog.info("Cached GL Renderer: " + glPrefs.glRenderer);
            completeOnCreate();
        }
    }

    private void completeOnCreate() {
        completeOnCreateCalled = true;

        shortcutHelper = new ShortcutHelper(this);

        UiHelper.setLocale(this);

        // Bind to the computer manager service
        bindService(new Intent(PcView.this, ComputerManagerService.class), serviceConnection,
                Service.BIND_AUTO_CREATE);

        pcGridAdapter = new PcGridAdapter(this, PreferenceConfiguration.readPreferences(this));

        initializeViews();
    }

    private void refreshPcCards() {
        List<ComputerDetails> computers = new ArrayList<>();
        for (int i = 0; i < pcGridAdapter.getCount(); i++) {
            computers.add(((ComputerObject) pcGridAdapter.getItem(i)).details);
        }
        pcCardRow.update(computers);
        findViewById(R.id.pcScroll).setVisibility(computers.isEmpty() ? View.GONE : View.VISIBLE);
        updateDefaultFocus();
    }

    @Override
    public void onQuickLaunchChanged() {
        updateDefaultFocus();
    }

    @Override
    public void onQuickLaunchHintsChanged() {
        if (hintRow != null) {
            hintRow.refresh();
        }
    }

    // A gamepad starts from the first game of the quick launch row, or the first PC without one
    private void updateDefaultFocus() {
        LinearLayout quickLaunchContainer = findViewById(R.id.quickLaunchContainer);
        LinearLayout pcRow = findViewById(R.id.pcRow);
        if (quickLaunchContainer == null || pcRow == null) {
            return;
        }
        // Without PCs the row is hidden and the empty state offers to add one
        View target = quickLaunchContainer.isShown() && quickLaunchContainer.getChildCount() > 0
                ? quickLaunchContainer.getChildAt(0)
                : findViewById(R.id.pcScroll).getVisibility() == View.VISIBLE ? pcRow.getChildAt(0) : findViewById(R.id.emptyAddPcButton);
        ApolloUi.focusByDefault(this, target);
    }

    // Quick settings panel from the left: shortcuts to the settings changed most often
    private void toggleQuickSettings() {
        QuickSettingsPanel.of(this).toggle();
    }

    private void startComputerUpdates() {
        // Only allow polling to start if we're bound to CMS, polling is not already running,
        // and our activity is in the foreground.
        if (managerBinder != null && !runningPolling && inForeground) {
            freezeUpdates = false;
            managerBinder.startPolling(new ComputerManagerListener() {
                @Override
                public void notifyComputerUpdated(final ComputerDetails details, final boolean isFreshPoll) {
                    if (!freezeUpdates) {
                        PcView.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateComputer(details, isFreshPoll);
                            }
                        });

                        // Add a launcher shortcut for this PC (off the main thread to prevent ANRs)
                        if (details.pairState == PairState.PAIRED) {
                            shortcutHelper.createAppViewShortcutForOnlineHost(details);
                        }
                    }
                }
            });
            runningPolling = true;
        }
    }

    private void stopComputerUpdates(boolean wait) {
        if (managerBinder != null) {
            if (!runningPolling) {
                return;
            }

            freezeUpdates = true;

            managerBinder.stopPolling();

            if (wait) {
                managerBinder.waitForPollingStopped();
            }

            runningPolling = false;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Notify QuickLaunchView of destroy
        if (quickLaunchView != null) {
            quickLaunchView.onDestroy();
        }

        if (managerBinder != null) {
            unbindService(serviceConnection);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Display a decoder crash notification if we've returned after a crash
        UiHelper.showDecoderCrashDialog(this);

        // Display current refresh rate only on first load
        if (!hasShownRefreshRateToast) {
            Display display = getWindowManager().getDefaultDisplay();
            float refreshRate = display.getRefreshRate();
            Toast.makeText(this, Html.fromHtml("Current refresh rate: <b>" + UiHelper.formatRefreshRate(refreshRate) + "</b>"), Toast.LENGTH_LONG).show();
            hasShownRefreshRateToast = true;
        }

        inForeground = true;

        // The full screen setting may have changed in Settings
        boolean sliding = topBar != null && topBar.onResume();

        // Back from Settings the tab pill slides: the PC updates and Quick Launch wait for it, or it stutters
        Runnable resumeWork = () -> {
            if (!inForeground) {
                return;
            }
            startComputerUpdates();

            // Notify QuickLaunchView of resume
            if (quickLaunchView != null) {
                quickLaunchView.onResume();
            }
        };
        if (sliding) {
            topBar.postDelayed(resumeWork, ApolloTopBar.SLIDE_SETTLE_MS);
        } else {
            resumeWork.run();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        inForeground = false;
        stopComputerUpdates(false);

        if (topBar != null) {
            topBar.onPause();
        }

        // Notify QuickLaunchView of pause
        if (quickLaunchView != null) {
            quickLaunchView.onPause();
        }

    }

    @Override
    protected void onStop() {
        super.onStop();

        Dialog.closeDialogs();
    }

    // Options of a PC, in the side sheet opened with Y or a long press
    private void showComputerActions(ComputerDetails computer) {
        List<ActionSheet.Action> actions = new ArrayList<>();
        boolean offline = computer.state == ComputerDetails.State.OFFLINE ||
                computer.state == ComputerDetails.State.UNKNOWN;
        // Wake-on-LAN is always offered, first for a PC that looks offline
        ActionSheet.Action wakeOnLan = new ActionSheet.Action(R.drawable.ic_apollo_power,
                getString(R.string.apollo_action_wol), () -> runComputerAction(WOL_ID, computer));

        if (offline) {
            actions.add(wakeOnLan);
            actions.add(new ActionSheet.Action(R.drawable.ic_apollo_help,
                    getString(R.string.pcview_menu_eol), () -> runComputerAction(GAMESTREAM_EOL_ID, computer)));
        }
        else if (computer.pairState != PairState.PAIRED) {
            actions.add(new ActionSheet.Action(R.drawable.ic_apollo_link,
                    getString(R.string.apollo_action_pair), () -> runComputerAction(PAIR_ID, computer)));
            actions.add(wakeOnLan);
            if (computer.nvidiaServer) {
                actions.add(new ActionSheet.Action(R.drawable.ic_apollo_help,
                        getString(R.string.pcview_menu_eol), () -> runComputerAction(GAMESTREAM_EOL_ID, computer)));
            }
        }
        else {
            actions.add(new ActionSheet.Action(R.drawable.ic_apollo_library,
                    getString(R.string.apollo_action_open_library), () -> doAppList(computer, false, false)));
            if (computer.runningGameId != 0) {
                actions.add(new ActionSheet.Action(R.drawable.ic_apollo_play,
                        getString(R.string.apollo_action_resume), () -> runComputerAction(RESUME_ID, computer)));
                actions.add(new ActionSheet.Action(R.drawable.ic_apollo_stop,
                        getString(R.string.apollo_action_quit_game), () -> runComputerAction(QUIT_ID, computer)).danger());
            }
            actions.add(wakeOnLan);
            actions.add(new ActionSheet.Action(R.drawable.ic_apollo_hide,
                    getString(R.string.apollo_action_full_library), () -> runComputerAction(FULL_APP_LIST_ID, computer)));
            if (computer.nvidiaServer) {
                actions.add(new ActionSheet.Action(R.drawable.ic_apollo_help,
                        getString(R.string.pcview_menu_eol), () -> runComputerAction(GAMESTREAM_EOL_ID, computer)));
            }
        }

        // Link, latency to the PC (when it is on) and the ports for streaming away from home
        ComputerDetails.AddressTuple address = offline ? null : computer.activeAddress;
        actions.add(new ActionSheet.Action(R.drawable.ic_apollo_network, getString(R.string.apollo_action_test_connection),
                () -> NetworkTestActivity.start(this, computer.name, address != null ? address.address : null,
                        address != null ? address.port : 0)));
        actions.add(new ActionSheet.Action(R.drawable.ic_apollo_info,
                getString(R.string.apollo_action_details), () -> runComputerAction(VIEW_DETAILS_ID, computer)));
        actions.add(new ActionSheet.Action(R.drawable.ic_apollo_delete,
                getString(R.string.apollo_action_remove_pc), () -> runComputerAction(DELETE_ID, computer)).danger());

        int status;
        if (computer.state == ComputerDetails.State.UNKNOWN) {
            status = R.string.apollo_pc_checking;
        } else if (offline) {
            status = R.string.apollo_pc_offline;
        } else if (computer.pairState != PairState.PAIRED) {
            status = R.string.apollo_pc_unpaired;
        } else {
            status = R.string.apollo_pc_online;
        }
        ActionSheet.of(this).show(computer.name, getString(status), actions);
    }

    private void doPair(final ComputerDetails computer) {
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.pair_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(PcView.this, getResources().getString(R.string.pairing), Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                NvHTTP httpConn;
                String message;
                boolean success = false;
                try {
                    // Stop updates and wait while pairing
                    stopComputerUpdates(true);

                    httpConn = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer),
                            computer.httpsPort, managerBinder.getUniqueId(), computer.serverCert,
                            PlatformBinding.getCryptoProvider(PcView.this));
                    if (httpConn.getPairState() == PairState.PAIRED) {
                        // Don't display any toast, but open the app list
                        message = null;
                        success = true;
                    }
                    else {
                        final String pinStr = PairingManager.generatePinString();

                        // Spin the dialog off in a thread because it blocks
                        Dialog.displayDialog(PcView.this, getResources().getString(R.string.pair_pairing_title),
                                getResources().getString(R.string.pair_pairing_msg)+" "+pinStr+"\n\n"+
                                getResources().getString(R.string.pair_pairing_help), false);

                        PairingManager pm = httpConn.getPairingManager();

                        PairState pairState = pm.pair(httpConn.getServerInfo(true), pinStr);
                        if (pairState == PairState.PIN_WRONG) {
                            message = getResources().getString(R.string.pair_incorrect_pin);
                        }
                        else if (pairState == PairState.FAILED) {
                            if (computer.runningGameId != 0) {
                                message = getResources().getString(R.string.pair_pc_ingame);
                            }
                            else {
                                message = getResources().getString(R.string.pair_fail);
                            }
                        }
                        else if (pairState == PairState.ALREADY_IN_PROGRESS) {
                            message = getResources().getString(R.string.pair_already_in_progress);
                        }
                        else if (pairState == PairState.PAIRED) {
                            // Just navigate to the app view without displaying a toast
                            message = null;
                            success = true;

                            // Pin this certificate for later HTTPS use
                            managerBinder.getComputer(computer.uuid).serverCert = pm.getPairedCert();

                            // Invalidate reachability information after pairing to force
                            // a refresh before reading pair state again
                            managerBinder.invalidateStateForComputer(computer.uuid);
                        }
                        else {
                            // Should be no other values
                            message = null;
                        }
                    }
                } catch (UnknownHostException e) {
                    message = getResources().getString(R.string.error_unknown_host);
                } catch (FileNotFoundException e) {
                    message = getResources().getString(R.string.error_404);
                } catch (XmlPullParserException | IOException e) {
                    e.printStackTrace();
                    message = e.getMessage();
                }

                Dialog.closeDialogs();

                final String toastMessage = message;
                final boolean toastSuccess = success;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (toastMessage != null) {
                            Toast.makeText(PcView.this, toastMessage, Toast.LENGTH_LONG).show();
                        }

                        if (toastSuccess) {
                            // Open the app list after a successful pairing attempt
                            doAppList(computer, true, false);
                        }
                        else {
                            // Start polling again if we're still in the foreground
                            startComputerUpdates();
                        }
                    }
                });
            }
        }).start();
    }

    // Sent whatever the state looks like: a sleeping PC can still show up as online for a while
    private void doWakeOnLan(final ComputerDetails computer) {
        if (computer.macAddress == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.wol_no_mac), Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                String message;
                try {
                    WakeOnLanSender.sendWolPacket(computer);
                    message = getResources().getString(R.string.wol_waking_msg);
                } catch (IOException e) {
                    message = getResources().getString(R.string.wol_fail);
                }

                final String toastMessage = message;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(PcView.this, toastMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void doUnpair(final ComputerDetails computer) {
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(PcView.this, getResources().getString(R.string.unpairing), Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                NvHTTP httpConn;
                String message;
                try {
                    httpConn = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer),
                            computer.httpsPort, managerBinder.getUniqueId(), computer.serverCert,
                            PlatformBinding.getCryptoProvider(PcView.this));
                    if (httpConn.getPairState() == PairingManager.PairState.PAIRED) {
                        httpConn.unpair();
                        if (httpConn.getPairState() == PairingManager.PairState.NOT_PAIRED) {
                            message = getResources().getString(R.string.unpair_success);
                        }
                        else {
                            message = getResources().getString(R.string.unpair_fail);
                        }
                    }
                    else {
                        message = getResources().getString(R.string.unpair_error);
                    }
                } catch (UnknownHostException e) {
                    message = getResources().getString(R.string.error_unknown_host);
                } catch (FileNotFoundException e) {
                    message = getResources().getString(R.string.error_404);
                } catch (XmlPullParserException | IOException e) {
                    message = e.getMessage();
                    e.printStackTrace();
                }

                final String toastMessage = message;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(PcView.this, toastMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void doAppList(ComputerDetails computer, boolean newlyPaired, boolean showHiddenGames) {
        if (computer.state == ComputerDetails.State.OFFLINE) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        Intent i = new Intent(this, AppView.class);
        i.putExtra(AppView.NAME_EXTRA, computer.name);
        i.putExtra(AppView.UUID_EXTRA, computer.uuid);
        i.putExtra(AppView.NEW_PAIR_EXTRA, newlyPaired);
        i.putExtra(AppView.SHOW_HIDDEN_APPS_EXTRA, showHiddenGames);
        startActivity(i);
    }

    // The actions of the PC options, the same as the context menu of the original app had
    private boolean runComputerAction(int actionId, final ComputerDetails computer) {
        switch (actionId) {
            case PAIR_ID:
                doPair(computer);
                return true;

            case UNPAIR_ID:
                doUnpair(computer);
                return true;

            case WOL_ID:
                doWakeOnLan(computer);
                return true;

            case DELETE_ID:
                if (ActivityManager.isUserAMonkey()) {
                    LimeLog.info("Ignoring delete PC request from monkey");
                    return true;
                }
                UiHelper.displayDeletePcConfirmationDialog(this, computer, new Runnable() {
                    @Override
                    public void run() {
                        if (managerBinder == null) {
                            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
                            return;
                        }
                        removeComputer(computer);
                    }
                }, null);
                return true;

            case FULL_APP_LIST_ID:
                doAppList(computer, false, true);
                return true;

            case RESUME_ID:
                if (managerBinder == null) {
                    Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
                    return true;
                }

                ServerHelper.doStart(this, new NvApp("app", computer.runningGameId, false), computer, managerBinder);
                return true;

            case QUIT_ID:
                if (managerBinder == null) {
                    Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
                    return true;
                }

                // Display a confirmation dialog first
                UiHelper.displayQuitConfirmationDialog(this, new Runnable() {
                    @Override
                    public void run() {
                        ServerHelper.doQuit(PcView.this, computer,
                                new NvApp("app", 0, false), managerBinder, null);
                    }
                }, null);
                return true;

            case VIEW_DETAILS_ID:
                Dialog.displayDialog(PcView.this, getResources().getString(R.string.title_details), computer.toString(), false);
                return true;

            case TEST_NETWORK_ID:
                ServerHelper.doNetworkTest(PcView.this);
                return true;

            case GAMESTREAM_EOL_ID:
                HelpLauncher.launchGameStreamEolFaq(PcView.this);
                return true;

            default:
                return false;
        }
    }
    
    private void removeComputer(ComputerDetails details) {
        managerBinder.removeComputer(details);

        new DiskAssetLoader(this).deleteAssetsForComputer(details.uuid);

        // Delete hidden games preference value
        getSharedPreferences(AppView.HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE)
                .edit()
                .remove(details.uuid)
                .apply();

        for (int i = 0; i < pcGridAdapter.getCount(); i++) {
            ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(i);

            if (details.equals(computer.details)) {
                // Disable or delete shortcuts referencing this PC
                shortcutHelper.disableComputerShortcut(details,
                        getResources().getString(R.string.scut_deleted_pc));

                pcGridAdapter.removeComputer(computer);
                pcGridAdapter.notifyDataSetChanged();

                if (pcGridAdapter.getCount() == 0) {
                    // Show the "Discovery in progress" view
                    noPcFoundLayout.setVisibility(View.VISIBLE);
                }

                break;
            }
        }
    }
    
    private void updateComputer(ComputerDetails details, boolean isFreshPoll) {
        ComputerObject existingEntry = null;

        for (int i = 0; i < pcGridAdapter.getCount(); i++) {
            ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(i);

            // Check if this is the same computer
            if (details.uuid.equals(computer.details.uuid)) {
                existingEntry = computer;
                break;
            }
        }

        if (existingEntry != null) {
            // Replace the information in the existing entry
            existingEntry.details = details;
        }
        else {
            // Add a new entry
            pcGridAdapter.addComputer(new ComputerObject(details));

            // Remove the "Discovery in progress" view
            noPcFoundLayout.setVisibility(View.GONE);
        }

        // Notify the view that the data has changed
        pcGridAdapter.notifyDataSetChanged();

        // Update Quick Launch running status
        if (quickLaunchView != null) {
            quickLaunchView.updateRunningStatus(details.runningGameId, details.uuid);
        }

        // Check for a session to resume after device sleep — only when PcView is fully visible
        // and the update reflects a real network poll, not the initial stale cache dispatch.
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (isFreshPoll && pm.isInteractive() && inForeground && SessionResumeManager.hasPendingSession(this)) {
            String pendingUuid = SessionResumeManager.getPendingPcUuid(this);
            int pendingAppId = SessionResumeManager.getPendingAppId(this);
            android.util.Log.d("SessionResume", "updateComputer: pending uuid=" + pendingUuid
                    + " appId=" + pendingAppId
                    + " | computer uuid=" + details.uuid
                    + " runningGameId=" + details.runningGameId
                    + " state=" + details.state);
            if (details.uuid.equals(pendingUuid)) {
                if (details.runningGameId == pendingAppId) {
                    autoResumeNoGamePollCount = 0;
                    android.util.Log.d("SessionResume", "Match — launching resume");
                    startActivity(SessionResumeManager.buildResumeIntent(this));
                    SessionResumeManager.clear(this);
                } else if (details.runningGameId != 0) {
                    autoResumeNoGamePollCount = 0;
                    android.util.Log.d("SessionResume", "Different app running — discarding");
                    SessionResumeManager.clear(this);
                } else if (details.state != ComputerDetails.State.OFFLINE) {
                    autoResumeNoGamePollCount++;
                    android.util.Log.d("SessionResume", "runningGameId=0, online — waiting (poll " + autoResumeNoGamePollCount + "/3)");
                    if (autoResumeNoGamePollCount >= 3) {
                        autoResumeNoGamePollCount = 0;
                        android.util.Log.d("SessionResume", "No game after 3 polls — discarding");
                        SessionResumeManager.clear(this);
                    }
                } else {
                    android.util.Log.d("SessionResume", "Computer offline — waiting for next poll");
                }
            } else {
                android.util.Log.d("SessionResume", "UUID mismatch — not this computer");
            }
        }
    }

    private void onComputerClicked(ComputerDetails computer) {
        if (computer.state == ComputerDetails.State.UNKNOWN ||
            computer.state == ComputerDetails.State.OFFLINE) {
            // Open the options if a PC is offline or refreshing
            showComputerActions(computer);
        } else if (computer.pairState != PairState.PAIRED) {
            // Pair an unpaired machine by default
            doPair(computer);
        } else {
            doAppList(computer, false, false);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (topBar != null && event.getRepeatCount() == 0) {
            // Select opens and closes the profiles; while they are open the other buttons are theirs
            if (keyCode == KeyEvent.KEYCODE_BUTTON_SELECT) {
                ProfileMenu.of(this).toggle();
                return true;
            }
            if (ProfileMenu.of(this).isShowing()) {
                return super.onKeyDown(keyCode, event);
            }
            switch (keyCode) {
                case KeyEvent.KEYCODE_BUTTON_Y: {
                    // The options of the focused card, like a long press
                    View focused = getCurrentFocus();
                    if (focused != null && focused.isLongClickable()) {
                        focused.performLongClick();
                    }
                    return true;
                }
                case KeyEvent.KEYCODE_BUTTON_X:
                    startActivity(new Intent(this, AddComputerManually.class));
                    return true;
                case KeyEvent.KEYCODE_BUTTON_L1:
                    topBar.switchTab(-1);
                    return true;
                case KeyEvent.KEYCODE_BUTTON_R1:
                    topBar.switchTab(1);
                    return true;
                case KeyEvent.KEYCODE_BUTTON_START:
                    toggleQuickSettings();
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
        if (ActionSheet.of(this).dismiss() || ProfileMenu.of(this).dismiss() || QuickSettingsPanel.of(this).dismiss()
                || (quickLaunchView != null && quickLaunchView.finishMove(false))) {
            return;
        }
        super.onBackPressed();
    }
    
    // QuickLaunchCallback implementation
    @Override
    public ComputerManagerService.ComputerManagerBinder getManagerBinder() {
        return managerBinder;
    }

    public static class ComputerObject {
        public ComputerDetails details;

        public ComputerObject(ComputerDetails details) {
            if (details == null) {
                throw new IllegalArgumentException("details must not be null");
            }
            this.details = details;
        }

        @Override
        public String toString() {
            return details.name;
        }
    }
}
