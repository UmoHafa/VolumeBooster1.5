package com.volumeboostersoundboost.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class BoosterService extends Service {
    public static final String ACTION_SET_BOOST = "com.volumeboostersoundboost.app.SET_BOOST";
    public static final String ACTION_RETRY = "com.volumeboostersoundboost.app.RETRY";
    public static final String ACTION_STOP = "com.volumeboostersoundboost.app.STOP";
    public static final String EXTRA_BOOST_PERCENT = "boost_percent";
    public static final String PREFS = "booster_prefs";
    public static final String KEY_BOOST = "boost_percent";
    public static final String KEY_MAX = "max_boost";
    public static final String KEY_SHAPE = "non_uniform";
    public static final String KEY_VOLUME = "show_volume";
    public static final String KEY_ENGINE = "engine_mode";
    private static final String CHANNEL = "booster_service";
    private static volatile boolean running;
    private static volatile String status = "Boost engine stopped";
    private static volatile String diagnostics = "Start the service to inspect audio effects.";
    private static volatile int enabledCount;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<Integer> sessions = new LinkedHashSet<Integer>();
    private final Map<Integer, SessionEffect> effects = new LinkedHashMap<Integer, SessionEffect>();
    private final ArrayList<String> failures = new ArrayList<String>();
    private SharedPreferences prefs;
    private AudioManager audio;
    private BroadcastReceiver receiver;
    private AudioDeviceCallback devices;
    private int boost;
    private int mode;
    private boolean shape;
    private boolean destroyed;
    private final Runnable rebuildTask = new Runnable() {
        @Override public void run() { if (!destroyed) rebuild(); }
    };
    private final Runnable monitor = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            publishStatus();
            handler.postDelayed(this, 1500);
        }
    };

    public static boolean isRunning() { return running; }
    public static String getEngineStatus() { return status; }
    public static String getDiagnostics() { return diagnostics; }
    public static int getActiveEffectCount() { return enabledCount; }

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        readSettings();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Boost service",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Shows the running audio-effect service and its Stop control");
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
        startForeground(1, notification());
        running = true;
        registerSessionReceiver();
        devices = new AudioDeviceCallback() {
            @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added) { scheduleRebuild(); }
            @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) { scheduleRebuild(); }
        };
        audio.registerAudioDeviceCallback(devices, handler);
        rebuild();
        handler.postDelayed(monitor, 1500);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        int oldMode = mode;
        readSettings();
        if (intent != null && intent.hasExtra(EXTRA_BOOST_PERCENT)) {
            boost = BoostConfig.clamp(intent.getIntExtra(EXTRA_BOOST_PERCENT, 0),
                    prefs.getInt(KEY_MAX, BoostConfig.HARD_LIMIT));
            prefs.edit().putInt(KEY_BOOST, boost).apply();
        }
        int session = intent == null ? 0 : intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);
        boolean added = session > 0 && sessions.size() < 16 && sessions.add(session);
        if (added || oldMode != mode || (intent != null && ACTION_RETRY.equals(intent.getAction()))) {
            rebuild();
        } else {
            for (SessionEffect effect : effects.values()) effect.apply(boost, shape);
            publishStatus();
        }
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(1, notification());
        // Never restart a high-gain service unexpectedly after the system stops it.
        return START_NOT_STICKY;
    }

    private void readSettings() {
        boost = BoostConfig.clamp(prefs.getInt(KEY_BOOST, 0), prefs.getInt(KEY_MAX, BoostConfig.HARD_LIMIT));
        mode = prefs.getInt(KEY_ENGINE, BoostConfig.AUTO);
        shape = prefs.getBoolean(KEY_SHAPE, true);
    }

    private void registerSessionReceiver() {
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                int id = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);
                if (id <= 0) return;
                String action = intent.getAction();
                if (AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.equals(action)) {
                    int kind = intent.getIntExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC);
                    if (kind == AudioEffect.CONTENT_TYPE_VOICE) return;
                    if (sessions.size() < 16 && sessions.add(id)) scheduleRebuild();
                } else if (AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION.equals(action)) {
                    if (sessions.remove(id)) scheduleRebuild();
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        filter.addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        try {
            // Cooperating players must be able to announce bounded session IDs.
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            else registerReceiver(receiver, filter);
        } catch (RuntimeException error) {
            receiver = null;
        }
    }

    private void scheduleRebuild() {
        handler.removeCallbacks(rebuildTask);
        handler.postDelayed(rebuildTask, 250);
    }

    private SessionEffect createEffect(int session) {
        SessionEffect result = new SessionEffect(session, mode, boost, shape, new Runnable() {
            @Override public void run() { if (!destroyed) publishStatus(); }
        });
        failures.addAll(result.failures);
        return result;
    }

    private void rebuild() {
        releaseEffects();
        failures.clear();
        readSettings();
        // Never process a player and its global mix together.
        for (Integer session : sessions) effects.put(session, createEffect(session));
        boolean hasUsablePlayer = false;
        for (SessionEffect effect : effects.values()) hasUsablePlayer |= effect.usable();
        if (!hasUsablePlayer) {
            releaseEffects();
            effects.put(0, createEffect(0));
        }
        publishStatus();
    }

    private void releaseEffects() {
        for (SessionEffect effect : effects.values()) effect.release();
        effects.clear();
    }

    private void publishStatus() {
        int count = 0;
        for (SessionEffect effect : effects.values()) if (effect.enabledAndControlled()) count++;
        enabledCount = count;
        boolean global = effects.containsKey(0);
        if (boost == 0) status = "Boost is 0% — no amplification requested";
        else if (count == 0) status = "Boost unavailable or not controlled. Open Boost diagnostics.";
        else if (global) status = "Boost engine attached to Android global mix; test audible output now.";
        else status = "Boost engine attached to the active player session; test audible output now.";

        StringBuilder text = new StringBuilder("Volume Booster v1.4 — local diagnostics\n");
        text.append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" · Android ").append(Build.VERSION.RELEASE).append(" (API ")
                .append(Build.VERSION.SDK_INT).append(")\n");
        text.append("Mode: ").append(BoostConfig.engineName(mode)).append("\nBoost: ")
                .append(boost).append("% / max ").append(prefs.getInt(KEY_MAX, BoostConfig.HARD_LIMIT))
                .append("%\nRequested ceiling: ").append(BoostConfig.gainMb(boost) / 100f)
                .append(" dB (not measured output)\nNon-uniform EQ: ").append(shape)
                .append("\nPlayer sessions announced: ").append(sessions.size())
                .append("\nScope: ").append(global ? "global fallback (session 0)" : "player sessions only")
                .append("\n").append(status).append("\n");
        try {
            text.append("Music active: ").append(audio.isMusicActive()).append('\n');
            text.append("Connected output types (not a verified active route): ");
            for (AudioDeviceInfo device : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                text.append(deviceType(device.getType())).append(' ');
            }
            text.append('\n');
        } catch (RuntimeException error) { text.append("Audio route information unavailable\n"); }
        for (Map.Entry<Integer, SessionEffect> item : effects.entrySet()) {
            text.append("Session ").append(item.getKey()).append(": ")
                    .append(item.getValue().describe()).append('\n');
        }
        if (receiver == null) text.append("Session broadcast receiver unavailable\n");
        for (String failure : failures) text.append("Detail: ").append(failure).append('\n');
        text.append("\nEnabled/control/readback are software checks, not an acoustic measurement.")
                .append("\nNo audible change? Try the other engine, restart the player after starting this service,")
                .append(" then copy this report. Some players/routes bypass global effects.")
                .append("\nNo microphone recording, audio capture, or automatic report upload.");
        diagnostics = text.toString();
    }

    private static String deviceType(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: return "speaker";
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE: return "earpiece";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES: return "wired-headphones";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET: return "wired-headset";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: return "Bluetooth-A2DP";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: return "Bluetooth-SCO";
            default: return "type-" + type;
        }
    }

    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 10,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 11,
                new Intent(this, BoosterService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return builder.setContentTitle("Volume Booster")
                .setContentText("Service running · Requested Boost " + boost + "%")
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off).setContentIntent(open)
                .addAction(android.R.drawable.ic_media_pause, "Stop", stop)
                .setOnlyAlertOnce(true).setOngoing(true).build();
    }

    @Override public void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        if (receiver != null) try { unregisterReceiver(receiver); } catch (RuntimeException ignored) {}
        if (devices != null) audio.unregisterAudioDeviceCallback(devices);
        releaseEffects();
        sessions.clear();
        running = false;
        enabledCount = 0;
        status = "Boost engine stopped";
        diagnostics = "Service stopped. Start it to generate fresh diagnostics.";
        stopForeground(true);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
