package com.limelight.ui.apollo;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.SoundPool;
import android.media.audiofx.Equalizer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;

import com.limelight.LimeLog;
import com.limelight.R;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The sounds of the menus: an effect for each press of the gamepad (D-pad, A, B, LB/RB) and for what opens,
 * switches or starts, and the music under the menus. Never during a stream. Each can be turned off on its own
 * (Settings > App, or the setup).
 */
public final class UiSounds {
    public static final String PREF_MUSIC = "checkbox_menu_music";
    public static final String PREF_EFFECTS = "checkbox_ui_sounds";
    public static final String PREF_MUSIC_VOLUME = "seekbar_menu_music_volume";
    public static final String PREF_EFFECTS_VOLUME = "seekbar_ui_sounds_volume";
    // Both sliders start at 80, which plays the levels tried on the handheld: room to go up and down
    public static final int DEFAULT_MUSIC_VOLUME = 80;
    public static final int DEFAULT_EFFECTS_VOLUME = 80;

    public enum Sound {
        MOVE(R.raw.sfx_move, 0.5f, true),
        SELECT(R.raw.sfx_select, 0.55f, true),
        BACK(R.raw.sfx_back, 0.55f, true),
        TAB(R.raw.sfx_tab, 0.6f, true),
        OPEN(R.raw.sfx_open, 0.55f, false),
        TOGGLE_ON(R.raw.sfx_toggle_on, 0.6f, false),
        TOGGLE_OFF(R.raw.sfx_toggle_off, 0.6f, false),
        ERROR(R.raw.sfx_error, 0.65f, false),
        LAUNCH(R.raw.sfx_launch, 0.7f, false),
        DONE(R.raw.sfx_done, 0.7f, false),
        // X held to confirm: rises for the second it takes
        HOLD(R.raw.sfx_hold, 0.6f, false),
        // A slider moving, higher with the value
        TICK(R.raw.sfx_tick, 0.5f, false);

        final int res;
        final float volume;
        // Played for a button by itself; a sound of what the button did takes its place
        final boolean button;
        int id;
        boolean loaded;

        Sound(int res, float volume, boolean button) {
            this.res = res;
            this.volume = volume;
            this.button = button;
        }
    }

    private static UiSounds instance;

    private final Context context;
    private final SharedPreferences prefs;
    private final SoundPool pool;
    private final MenuMusic music;
    private static final long BUTTON_WAIT_MS = 50;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Sound pendingSound;
    private final Runnable pendingButton = () -> {
        if (pendingSound != null) {
            emit(pendingSound);
            pendingSound = null;
        }
    };
    private long lastMoveAt;
    // The last effect played, the music waits for it to end
    private static long lastEffectAt;
    // The last sound of a button: A and the confirm key Android makes of it, B and back, sound once
    private long lastButtonAt;
    // A sound of what a press did (a menu opening, a switch): the sound of the button itself is left out
    private long actionAt;
    private boolean streamShown;

    // Kept here: the preferences only hold a weak reference to their listeners
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener = (p, key) -> {
        if (PREF_MUSIC.equals(key)) {
            UiSounds.this.music.setEnabled(p.getBoolean(PREF_MUSIC, true));
        } else if (PREF_MUSIC_VOLUME.equals(key)) {
            UiSounds.this.music.setVolume(musicVolume(p.getInt(PREF_MUSIC_VOLUME, DEFAULT_MUSIC_VOLUME)));
        }
    };

    private UiSounds(Application app) {
        context = app;
        prefs = PreferenceManager.getDefaultSharedPreferences(app);
        pool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        pool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
            for (Sound sound : Sound.values()) {
                if (sound.id == sampleId) {
                    sound.loaded = status == 0;
                }
            }
        });
        for (Sound sound : Sound.values()) {
            sound.id = pool.load(app, sound.res, 1);
        }
        music = new MenuMusic(app);
        music.setVolume(musicVolume(prefs.getInt(PREF_MUSIC_VOLUME, DEFAULT_MUSIC_VOLUME)));
        music.setEnabled(prefs.getBoolean(PREF_MUSIC, true));
        prefs.registerOnSharedPreferenceChangeListener(prefsListener);
    }

    // The slider from 0 to 100 as the volume of the player
    private static float musicVolume(int slider) {
        // 80 is 0.7 of the player, 3 dB over the level first tried
        return Math.min(1f, slider / 115f);
    }

    public static void init(Application app) {
        if (instance == null) {
            instance = new UiSounds(app);
        }
    }

    /** Plays an effect, if the effects are on. @return its stream, for stop(), or 0 */
    public static int play(Sound sound) {
        return instance != null ? instance.playSound(sound, false) : 0;
    }

    /** An effect of a press shown in an illustration (the setup tour): no real button is behind it. */
    public static void playShown(Sound sound) {
        if (instance != null) {
            instance.playSound(sound, true);
        }
    }

    /** A slider moved to this fraction of its range (0 to 1): a tick, higher toward the end. */
    public static void tick(float fraction) {
        if (instance != null) {
            instance.playTick(fraction);
        }
    }

    /** Stops an effect early (a hold let go). */
    public static void stop(int stream) {
        if (instance != null && stream != 0) {
            instance.pool.stop(stream);
        }
    }

    /**
     * How loud the music is, from 0 to 1, over its own volume: the tour lowers it with the volume of the game it
     * shows. Back to 1 when the tour is left.
     */
    public static void setMusicLevel(float level, long fadeMs) {
        if (instance != null) {
            instance.music.setLevel(level, fadeMs);
        }
    }

    /** What is on screen: the music plays under the menus only, the effects never over a stream. */
    public static void setScreens(boolean menusShown, boolean streamShown) {
        if (instance != null) {
            instance.streamShown = streamShown;
            instance.music.setShown(menusShown && !streamShown);
        }
    }

    /** The presses of the gamepad in this window make their sounds. */
    public static void attach(Window window) {
        if (instance == null || window == null || window.getCallback() instanceof KeyCallback) {
            return;
        }
        Window.Callback callback = window.getCallback();
        if (callback == null) {
            return;
        }
        window.setCallback(new KeyCallback(callback, window));
        // The D-pad moving the focus: one tick per move, not for a focus that the screen moves by itself
        View decor = window.getDecorView();
        decor.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (newFocus != null && !decor.isInTouchMode()
                    && SystemClock.uptimeMillis() - KeyCallback.lastNavigationAt < 250) {
                play(Sound.MOVE);
            }
        });
    }

    public static void attach(Activity activity) {
        attach(activity.getWindow());
    }

    private int playSound(Sound sound, boolean shown) {
        // The stream starting is the last one
        if (!sound.loaded || !prefs.getBoolean(PREF_EFFECTS, true) || (streamShown && sound != Sound.LAUNCH)) {
            return 0;
        }
        long now = SystemClock.uptimeMillis();
        if (sound.button && !shown) {
            // What the press did already made its sound (a popup opens on the press, A sounds on the release)
            // A value confirmed in a popup moves its slider: the tick is the sound of that A
            if (now - actionAt < 150 || (sound == Sound.SELECT && now - lastTickAt < 150) || (actionAt >= KeyCallback.pressAt && now - KeyCallback.pressAt < 1500)) {
                return 0;
            }
            // Holding the D-pad: not a tick for each repeat
            if (sound == Sound.MOVE) {
                if (now - lastMoveAt < 60) {
                    return 0;
                }
                lastMoveAt = now;
            } else {
                // One press, one sound (A also comes as the confirm key, B as back)
                if (now - lastButtonAt < 150) {
                    return 0;
                }
                lastButtonAt = now;
                // A and B wait a moment: Android sends A again as the confirm key, and what that opens (a menu)
                // makes its own sound, which then plays alone
                if (sound == Sound.SELECT || sound == Sound.BACK) {
                    handler.removeCallbacks(pendingButton);
                    pendingSound = sound;
                    handler.postDelayed(pendingButton, BUTTON_WAIT_MS);
                    return 0;
                }
            }
        } else if (!sound.button && sound != Sound.HOLD && sound != Sound.TICK) {
            actionAt = now;
            handler.removeCallbacks(pendingButton);
        }
        return emit(sound);
    }

    private int emit(Sound sound) {
        lastEffectAt = SystemClock.uptimeMillis();
        float volume = Math.min(1f, sound.volume * effectsScale());
        return pool.play(sound.id, volume, volume, sound.button ? 1 : 2, 0, 1f);
    }

    // The slider at 80 plays the effects 3 dB over the levels first tried on the handheld
    private float effectsScale() {
        return prefs.getInt(PREF_EFFECTS_VOLUME, DEFAULT_EFFECTS_VOLUME) / 80f * 1.4f;
    }

    private long lastTickAt;

    private void playTick(float fraction) {
        Sound sound = Sound.TICK;
        long now = SystemClock.uptimeMillis();
        // A drag moves many steps: a tick at most every 35 ms
        if (!sound.loaded || !prefs.getBoolean(PREF_EFFECTS, true) || streamShown || now - lastTickAt < 35) {
            return;
        }
        lastTickAt = now;
        lastEffectAt = now;
        float volume = Math.min(1f, sound.volume * effectsScale());
        float pitch = 0.85f + 0.4f * Math.max(0f, Math.min(1f, fraction));
        pool.play(sound.id, volume, volume, 1, 0, pitch);
    }

    // The presses come here before the screen gets them: the sound of a button plays once the screen has
    // handled it, unless what it did made its own sound
    private static final class KeyCallback extends WindowCallbackWrapper {
        static long lastNavigationAt;
        // When the last button went down
        static long pressAt;
        private final Window window;

        KeyCallback(Window.Callback wrapped, Window window) {
            super(wrapped);
            this.window = window;
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            int code = event.getKeyCode();
            boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
            if (down && isNavigation(code)) {
                lastNavigationAt = SystemClock.uptimeMillis();
            }
            if (down && event.getRepeatCount() == 0) {
                pressAt = SystemClock.uptimeMillis();
            }
            boolean handled = super.dispatchKeyEvent(event);
            if (event.isCanceled()) {
                return handled;
            }
            if (down && event.getRepeatCount() == 0
                    && (code == KeyEvent.KEYCODE_BUTTON_L1 || code == KeyEvent.KEYCODE_BUTTON_R1)) {
                play(Sound.TAB);
            } else if (!down && isSelect(code) && window.getCurrentFocus() != null) {
                play(Sound.SELECT);
            } else if (!down && (code == KeyEvent.KEYCODE_BUTTON_B || code == KeyEvent.KEYCODE_BACK
                    || code == KeyEvent.KEYCODE_ESCAPE)) {
                play(Sound.BACK);
            }
            return handled;
        }

        @Override
        public boolean dispatchGenericMotionEvent(MotionEvent event) {
            // The left stick moves the focus like the D-pad
            if ((event.getSource() & android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK) {
                lastNavigationAt = SystemClock.uptimeMillis();
            }
            return super.dispatchGenericMotionEvent(event);
        }

        private static boolean isNavigation(int code) {
            return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                    || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                    || code == KeyEvent.KEYCODE_TAB;
        }

        private static boolean isSelect(int code) {
            return code == KeyEvent.KEYCODE_BUTTON_A || code == KeyEvent.KEYCODE_DPAD_CENTER
                    || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        }
    }

    /**
     * The music: 64 s that loop. The MP3 is decoded once into memory and played by a thread that goes back to the
     * start at the exact sample: two players with a cross-fade never lined up and scraped when the loop restarted.
     * The file holds 2 s more than the loop (its start again), so the encoder delay at the head can be skipped:
     * the turn goes from LOOP_START to LOOP_START + 64 s, where the music is the same as at LOOP_START.
     */
    private static final class MenuMusic {
        private static final int LOOP_MS = 64000;
        private static final int LOOP_START_MS = 500;
        // Where it starts: the first chord has opened (its pad swells over 3 s), so it is heard at once
        private static final int PLAY_FROM_MS = 3000;
        // Decoded ahead of where it starts before it starts; the decoder goes much faster than the music
        private static final int DECODED_AHEAD_MS = 1000;
        private static final long FADE_IN_MS = 900;
        private static final long FADE_OUT_MS = 700;
        private static final long FRAME_MS = 50;
        // The longest effect, and how often to look again while another app plays
        private static final long EFFECT_MS = 4000;
        private static final long RETRY_MS = 10000;

        private final Context context;
        private final AudioManager audio;
        private final Handler handler = new Handler(Looper.getMainLooper());
        // From the settings, and the level the tour sets (the volume of the game it shows)
        private float volume = musicVolume(DEFAULT_MUSIC_VOLUME);
        private float level = 1f;
        private float levelTarget = 1f;
        private float levelStep;
        private boolean enabled;
        private boolean shown;
        private boolean playing;
        // The volume of the whole music, fading in and out
        private float master;
        private float masterTarget;
        private float masterStep;

        // The decoded music, stereo frames; null until the first ones are decoded. It plays while the rest is
        // decoded: pcmLength samples are there, all of them once pcmDone
        private volatile short[] pcm;
        private volatile int pcmLength;
        private volatile boolean pcmDone;
        private boolean startPosted;
        // The volume the writer gives the samples: the fades are in the sound itself, since some devices let the
        // first moments of a track out at full volume whatever setVolume() said
        private volatile float gain;
        private int channels;
        private int rate;
        private boolean decoding;
        private AudioTrack track;
        private Equalizer equalizer;
        private Thread writer;
        private volatile boolean writing;

        private final Runnable retry = this::update;
        private final Runnable tick = this::tick;

        MenuMusic(Context context) {
            this.context = context;
            this.audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            // Decoded as the app starts, so the music is ready when the first screen shows
            decoding = true;
            new Thread(() -> {
                boolean done = decode();
                handler.post(() -> {
                    decoding = false;
                    if (done) {
                        update();
                    }
                });
            }, "MenuMusicDecoder").start();
            // Headphones in or out: the low cut follows
            if (audio != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audio.registerAudioDeviceCallback(new AudioDeviceCallback() {
                    @Override
                    public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
                        applySpeakerCut();
                    }

                    @Override
                    public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
                        applySpeakerCut();
                    }
                }, handler);
            }
        }

        // The music has full lows, clean on headphones; the small speakers of a handheld rattle on them and cannot
        // play them anyway, so on the speakers an equalizer takes them out
        private void applySpeakerCut() {
            if (equalizer == null) {
                return;
            }
            try {
                boolean speaker = onSpeaker();
                short[] range = equalizer.getBandLevelRange();
                for (short band = 0; band < equalizer.getNumberOfBands(); band++) {
                    int centerHz = equalizer.getCenterFreq(band) / 1000;
                    short bandLevel = 0;
                    if (speaker && centerHz < 150) {
                        bandLevel = range[0];
                    } else if (speaker && centerHz < 400) {
                        bandLevel = (short) Math.max(range[0], -600);
                    }
                    equalizer.setBandLevel(band, bandLevel);
                }
                equalizer.setEnabled(speaker);
            } catch (RuntimeException e) {
                LimeLog.warning("Menu music equalizer: " + e);
            }
        }

        private boolean onSpeaker() {
            if (audio == null) {
                return true;
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                //noinspection deprecation
                return !audio.isWiredHeadsetOn() && !audio.isBluetoothA2dpOn();
            }
            for (AudioDeviceInfo device : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                switch (device.getType()) {
                    case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                    case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                    case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                    case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                    case AudioDeviceInfo.TYPE_USB_HEADSET:
                    case AudioDeviceInfo.TYPE_USB_DEVICE:
                    case AudioDeviceInfo.TYPE_LINE_ANALOG:
                    case AudioDeviceInfo.TYPE_LINE_DIGITAL:
                    case AudioDeviceInfo.TYPE_HDMI:
                        return false;
                    default:
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                                && device.getType() == AudioDeviceInfo.TYPE_BLE_HEADSET) {
                            return false;
                        }
                }
            }
            return true;
        }

        void setEnabled(boolean enabled) {
            this.enabled = enabled;
            update();
        }

        void setVolume(float volume) {
            this.volume = volume;
            if (playing && masterTarget > 0f) {
                fadeTo(volume, 300);
            }
        }

        void setLevel(float target, long ms) {
            levelTarget = Math.max(0f, Math.min(1f, target));
            if (ms <= 0) {
                level = levelTarget;
            }
            levelStep = Math.abs(levelTarget - level) * FRAME_MS / Math.max(1, ms);
            handler.removeCallbacks(tick);
            handler.post(tick);
        }

        void setShown(boolean shown) {
            this.shown = shown;
            update();
        }

        private void update() {
            boolean wanted = enabled && shown;
            if (wanted && playing) {
                // Back while it was fading out
                fadeTo(volume, FADE_IN_MS);
            } else if (wanted) {
                // Not over the music of another app (our own effects count as music too: not while one plays)
                handler.removeCallbacks(retry);
                if (track == null && audio != null && audio.isMusicActive()) {
                    boolean ours = SystemClock.uptimeMillis() - lastEffectAt < EFFECT_MS;
                    LimeLog.info("Menu music: something is playing" + (ours ? " (an effect)" : ""));
                    handler.postDelayed(retry, ours ? EFFECT_MS : RETRY_MS);
                    return;
                }
                start();
            } else {
                handler.removeCallbacks(retry);
                if (playing) {
                    fadeTo(0f, FADE_OUT_MS);
                }
            }
        }

        private void start() {
            if (!startDecoded()) {
                // Still decoding its first seconds: it starts once they are there, if still wanted
                return;
            }
            try {
                if (track == null) {
                    track = createTrack();
                    if (track == null) {
                        return;
                    }
                }
                playing = true;
                track.play();
                startWriter();
                fadeTo(volume, FADE_IN_MS);
            } catch (IllegalStateException e) {
                LimeLog.warning("Menu music: " + e);
                release();
            }
        }

        private boolean startDecoded() {
            return pcm != null && (pcmDone
                    || pcmLength >= (long) rate * channels * (PLAY_FROM_MS + DECODED_AHEAD_MS) / 1000);
        }

        // The whole MP3 into 16-bit samples
        private boolean decode() {
            MediaExtractor extractor = new MediaExtractor();
            MediaCodec codec = null;
            try (AssetFileDescriptor fd = context.getResources().openRawResourceFd(R.raw.menu_music)) {
                extractor.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
                MediaFormat format = extractor.getTrackFormat(0);
                extractor.selectTrack(0);
                codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
                codec.configure(format, null, null, 0);
                codec.start();
                int outChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                int outRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                long durationUs = format.containsKey(MediaFormat.KEY_DURATION) ? format.getLong(MediaFormat.KEY_DURATION) : 70_000_000L;
                short[] samples = null;
                int length = 0;
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                boolean inputDone = false;
                while (true) {
                    if (!inputDone) {
                        int in = codec.dequeueInputBuffer(10000);
                        if (in >= 0) {
                            int size = extractor.readSampleData(codec.getInputBuffer(in), 0);
                            if (size < 0) {
                                codec.queueInputBuffer(in, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputDone = true;
                            } else {
                                codec.queueInputBuffer(in, 0, size, extractor.getSampleTime(), 0);
                                extractor.advance();
                            }
                        }
                    }
                    int index = codec.dequeueOutputBuffer(info, 10000);
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat changed = codec.getOutputFormat();
                        outChannels = changed.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                        outRate = changed.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    } else if (index >= 0) {
                        ByteBuffer buffer = codec.getOutputBuffer(index);
                        if (info.size > 0 && buffer != null) {
                            buffer.position(info.offset);
                            buffer.limit(info.offset + info.size);
                            java.nio.ShortBuffer shorts = buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
                            int count = shorts.remaining();
                            if (samples == null) {
                                // The format is sure from the first output: the samples can be played from here on
                                samples = new short[(int) (durationUs * outRate / 1_000_000L * outChannels) + outRate * outChannels];
                                channels = outChannels;
                                rate = outRate;
                                pcm = samples;
                            }
                            if (length + count > samples.length) {
                                samples = java.util.Arrays.copyOf(samples, Math.max(samples.length * 5 / 4, length + count));
                                pcm = samples;
                            }
                            shorts.get(samples, length, count);
                            length += count;
                            pcmLength = length;
                            if (!startPosted && startDecoded()) {
                                startPosted = true;
                                handler.post(this::update);
                            }
                        }
                        codec.releaseOutputBuffer(index, false);
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            break;
                        }
                    }
                }
                if (samples == null) {
                    return false;
                }
                pcmDone = true;
                return true;
            } catch (IOException | RuntimeException e) {
                LimeLog.warning("Menu music: not decoded: " + e);
                return false;
            } finally {
                if (codec != null) {
                    codec.release();
                }
                extractor.release();
            }
        }

        private AudioTrack createTrack() {
            int session = audio != null ? audio.generateAudioSessionId() : AudioManager.AUDIO_SESSION_ID_GENERATE;
            int channelMask = channels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
            int minBuffer = AudioTrack.getMinBufferSize(rate, channelMask, AudioFormat.ENCODING_PCM_16BIT);
            AudioTrack created = new AudioTrack(
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    new AudioFormat.Builder()
                            .setSampleRate(rate)
                            .setChannelMask(channelMask)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build(),
                    Math.max(minBuffer, rate * channels / 5 * 2), AudioTrack.MODE_STREAM, session);
            if (created.getState() != AudioTrack.STATE_INITIALIZED) {
                LimeLog.warning("Menu music: the track could not be made");
                created.release();
                return null;
            }
            try {
                equalizer = new Equalizer(0, created.getAudioSessionId());
                applySpeakerCut();
            } catch (RuntimeException e) {
                LimeLog.warning("Menu music equalizer: " + e);
                equalizer = null;
            }
            return created;
        }

        // Writes the music round and round; while the track is paused, write() waits
        private void startWriter() {
            if (writer != null) {
                return;
            }
            AudioTrack target = track;
            int channelCount = channels;
            int loopStart = rate * LOOP_START_MS / 1000;
            int fullLoopEnd = loopStart + (int) ((long) rate * LOOP_MS / 1000);
            writing = true;
            writer = new Thread(() -> {
                int position = rate * PLAY_FROM_MS / 1000;
                int chunkFrames = rate / 50;
                short[] out = new short[chunkFrames * channelCount];
                float from = 0f;
                while (writing) {
                    // The rest may still be decoding: wait for it (it never really happens, the decoder is quicker)
                    boolean done = pcmDone;
                    int decodedFrames = pcmLength / channelCount;
                    int loopEnd = done ? Math.min(fullLoopEnd, decodedFrames) : fullLoopEnd;
                    if (position >= loopEnd) {
                        position = loopStart;
                    }
                    int count = Math.min(chunkFrames, loopEnd - position);
                    if (count <= 0) {
                        break;
                    }
                    if (!done && position + count > decodedFrames) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            break;
                        }
                        continue;
                    }
                    short[] samples = pcm;
                    // The volume moves smoothly across the chunk, from where the last one ended
                    float to = gain;
                    for (int frame = 0; frame < count; frame++) {
                        float g = from + (to - from) * frame / count;
                        for (int c = 0; c < channelCount; c++) {
                            int i = frame * channelCount + c;
                            out[i] = (short) (samples[(position + frame) * channelCount + c] * g);
                        }
                    }
                    from = to;
                    int written = target.write(out, 0, count * channelCount);
                    if (written < 0) {
                        break;
                    }
                    position += count;
                }
            }, "MenuMusic");
            writer.start();
        }

        private void fadeTo(float target, long ms) {
            masterTarget = target;
            masterStep = Math.abs(target - master) * FRAME_MS / Math.max(1, ms);
            handler.removeCallbacks(tick);
            handler.post(tick);
        }

        // Each frame: the volume a step closer to where it goes
        private void tick() {
            if (master < masterTarget) {
                master = Math.min(masterTarget, master + masterStep);
            } else if (master > masterTarget) {
                master = Math.max(masterTarget, master - masterStep);
            }
            if (level < levelTarget) {
                level = Math.min(levelTarget, level + levelStep);
            } else if (level > levelTarget) {
                level = Math.max(levelTarget, level - levelStep);
            }
            gain = master * level;
            if (master == 0f && masterTarget == 0f) {
                pause();
                return;
            }
            if (master != masterTarget || level != levelTarget) {
                handler.postDelayed(tick, FRAME_MS);
            }
        }

        private void pause() {
            if (!playing) {
                return;
            }
            playing = false;
            try {
                if (track != null) {
                    track.pause();
                }
            } catch (IllegalStateException e) {
                release();
            }
        }

        private void release() {
            playing = false;
            master = 0f;
            handler.removeCallbacks(tick);
            writing = false;
            if (track != null) {
                try {
                    track.pause();
                    track.flush();
                } catch (IllegalStateException ignored) {
                }
                track.release();
                track = null;
            }
            writer = null;
            if (equalizer != null) {
                equalizer.release();
                equalizer = null;
            }
        }
    }
}
