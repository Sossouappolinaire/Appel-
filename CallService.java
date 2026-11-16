package com.kouame.appelpredictions;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Service de premier plan : interroge la base (fonction sécurisée « app_calls », compte validé + accès en cours) toutes les 3 secondes ; dès qu'une nouvelle ligne
 * apparaît (prédiction lancée dans le canal choisi sur le site), le téléphone sonne comme un appel entrant,
 * en boucle, jusqu'à « Décrocher » (ou « Refuser »).
 */
public class CallService extends Service {
    static final String ACTION_ANSWER = "com.kouame.appelpredictions.ANSWER";
    static final String ACTION_DECLINE = "com.kouame.appelpredictions.DECLINE";
    static final String ACTION_TEST = "com.kouame.appelpredictions.TEST";
    static final String ACTION_WATCHDOG = "com.kouame.appelpredictions.WATCHDOG";
    private static final long WATCHDOG_MS = 5 * 60 * 1000L;

    private static final String CH_SERVICE = "surveillance";
    private static final String CH_CALL = "appel";
    private static final int ID_SERVICE = 1;
    private static final int ID_CALL = 2;
    private static final long POLL_MS = 3000;

    /** Lus par l'écran d'appel. */
    static volatile boolean RINGING = false;
    static volatile String CALL_TEXT = "";

    private volatile boolean polling = false;
    private Thread poller;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private MediaPlayer player;
    private int savedVolume = -1;
    private int pendingCalls = 0;
    private String lastStatus = "";

    @Override
    public IBinder onBind(Intent intent) { return null; }

    /** Programme (ou reprogramme) le réveil de secours qui relance le service s'il a été arrêté par le système. */
    static void scheduleWatchdog(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            PendingIntent pi = PendingIntent.getBroadcast(c, 9, new Intent(c, BootReceiver.class).setAction(ACTION_WATCHDOG),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + WATCHDOG_MS, pi);
        } catch (Exception ignored) { }
    }

    static void cancelWatchdog(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            am.cancel(PendingIntent.getBroadcast(c, 9, new Intent(c, BootReceiver.class).setAction(ACTION_WATCHDOG),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        } catch (Exception ignored) { }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // application retirée de la liste des applications récentes : la surveillance continue et se relance si besoin
        if (Prefs.running(this)) scheduleWatchdog(this);
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String act = intent == null ? null : intent.getAction();
        if (ACTION_ANSWER.equals(act) || ACTION_DECLINE.equals(act)) {
            stopRinging();
            if (!Prefs.running(this)) { stopSelf(); return START_NOT_STICKY; }
            return START_STICKY;
        }
        boolean test = ACTION_TEST.equals(act);
        if (!test && !Prefs.running(this)) {
            cancelWatchdog(this);
            startForegroundCompat("Arrêté");
            stopSelf();
            return START_NOT_STICKY;
        }
        startForegroundCompat(Prefs.status(this));
        if (Prefs.running(this)) scheduleWatchdog(this);
        if (test) startRinging("🧪 Test de la sonnerie");
        if (Prefs.running(this)) startPolling();
        return START_STICKY;
    }

    // ── premier plan ────────────────────────────────────────────────────────
    private void ensureChannels() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel s = new NotificationChannel(CH_SERVICE, "Surveillance", NotificationManager.IMPORTANCE_LOW);
        s.setShowBadge(false);
        nm.createNotificationChannel(s);
        NotificationChannel c = new NotificationChannel(CH_CALL, "Appel prédiction", NotificationManager.IMPORTANCE_HIGH);
        c.setSound(null, null);
        c.enableVibration(false);
        c.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        try { c.setBypassDnd(true); } catch (Exception ignored) { }
        nm.createNotificationChannel(c);
    }

    private Notification serviceNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CH_SERVICE)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Appel Prédictions")
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    private void startForegroundCompat(String text) {
        ensureChannels();
        Notification n = serviceNotification(text);
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID_SERVICE, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(ID_SERVICE, n);
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "appel:surveillance");
                wakeLock.acquire();
            }
        }
        if (wifiLock == null) { // garde le Wi-Fi actif écran éteint, pour que la lecture de la base continue
            try {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "appel:wifi");
                    wifiLock.setReferenceCounted(false);
                    wifiLock.acquire();
                }
            } catch (Exception ignored) { }
        }
    }

    private void setStatus(String s) {
        Prefs.setStatus(this, s);
        if (s.equals(lastStatus)) return;
        lastStatus = s;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(ID_SERVICE, serviceNotification(s));
    }

    // ── lecture de la base : fonction sécurisée « app_calls » (il faut un compte validé et un accès en cours) ────────
    private static final long DEDUP_MS = 45000;
    private final java.util.HashMap<String, Long> recent = new java.util.HashMap<>();

    private void startPolling() {
        if (poller != null && poller.isAlive()) return;
        polling = true;
        poller = new Thread(() -> {
            while (polling) {
                try {
                    pollOnce();
                } catch (Throwable t) {
                    setStatus("Erreur : " + t.getMessage());
                }
                try { Thread.sleep(POLL_MS); } catch (InterruptedException e) { return; }
            }
        }, "lecture-supabase");
        poller.start();
    }

    private String now() { return new SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(new Date()); }

    private void pollOnce() throws Exception {
        String err = pollBase();
        if (err == null) setStatus("Connecté • dernière vérification " + now());
        else setStatus(err);
    }

    /** Vrai si le même appel vient déjà de sonner. */
    private boolean isDuplicate(String key) {
        long t = System.currentTimeMillis();
        java.util.Iterator<java.util.Map.Entry<String, Long>> it = recent.entrySet().iterator();
        while (it.hasNext()) if (t - it.next().getValue() > DEDUP_MS) it.remove();
        if (recent.containsKey(key)) return true;
        recent.put(key, t);
        return false;
    }

    /** Interroge la base. Renvoie null si tout va bien, sinon le message d'erreur. */
    private String pollBase() throws Exception {
        String token = Prefs.token(this);
        if (token.isEmpty()) return "Connexion requise : ouvre l'application et connecte-toi.";
        long last = Prefs.lastId(this);
        JSONObject r = Server.call(this, "app_calls", "p_token", token, "p_after", last);
        if (!r.optBoolean("ok")) {
            String e = r.optString("error");
            if ("reseau".equals(e) || "cle".equals(e) || "service".equals(e) || "reponse".equals(e)) {
                return Server.showDetail ? "Hors ligne : " + r.optString("detail") : "Hors ligne";
            }
            if ("session".equals(e)) {
                Prefs.clearSession(this);
                return "Connexion requise : session expirée, reconnecte-toi dans l'application.";
            }
            if ("expire".equals(e)) {
                Prefs.setLastId(this, -1); // au retour de l'accès, les appels manqués ne sonnent pas d'un coup
                return "Accès terminé : paiement requis pour continuer.";
            }
            if ("pending".equals(e)) return "Compte en attente de validation par l'administrateur.";
            if ("blocked".equals(e)) return "Compte bloqué par l'administrateur.";
            return "Erreur : " + e + " " + r.optString("detail");
        }
        long max = r.optLong("max_id", 0);
        if (last < 0) { // première connexion : on part du dernier appel existant (les anciens ne sonnent pas)
            Prefs.setLastId(this, max);
            return null;
        }
        JSONArray a = r.optJSONArray("calls");
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Prefs.setLastId(this, o.getLong("id"));
                String game = o.isNull("game_number") || o.optInt("game_number", 0) == 0 ? "" : " N°" + o.optInt("game_number");
                String text;
                if ("test".equals(o.optString("kind"))) text = "🧪 Appel de test du site";
                else text = (o.optString("suit") + game + "\n" + o.optString("channel_name")).trim();
                String dedupKey = o.optString("kind") + "|" + o.optString("channel_name") + "|" + o.optInt("game_number", 0) + "|" + o.optString("suit");
                if (isDuplicate(dedupKey)) continue;
                startRinging(text);
            }
        }
        // le compteur d'appels peut repartir de zéro si la table a été recréée : on se recale
        if (max < Prefs.lastId(this)) Prefs.setLastId(this, max);
        return null;
    }

    // ── sonnerie ────────────────────────────────────────────────────────────
    private synchronized void startRinging(String text) {
        pendingCalls = RINGING ? pendingCalls + 1 : 1;
        CALL_TEXT = pendingCalls > 1 ? text + "\n(+" + (pendingCalls - 1) + " autre(s) prédiction(s))" : text;
        RINGING = true;
        wakeScreen();
        showCallNotification();
        if (player == null) {
            try {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    savedVolume = am.getStreamVolume(AudioManager.STREAM_ALARM);
                    am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
                }
                Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                MediaPlayer mp = new MediaPlayer();
                mp.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());
                mp.setDataSource(this, uri);
                mp.setLooping(true);
                mp.prepare();
                mp.start();
                player = mp;
            } catch (Exception e) {
                releasePlayer();
            }
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(VibrationEffect.createWaveform(new long[]{0, 800, 700}, 0));
        }
        try { // si l'appli est déjà au premier plan ou autorisée, l'écran d'appel s'ouvre tout de suite
            startActivity(new Intent(this, IncomingCallActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        } catch (Exception ignored) {
            // sinon : notification plein écran ci-dessous
        }
    }

    /** Allume l'écran pour l'appel, même téléphone verrouillé. */
    @SuppressWarnings("deprecation")
    private void wakeScreen() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm == null) return;
            PowerManager.WakeLock w = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "appel:ecran");
            w.acquire(20000);
        } catch (Exception ignored) { }
    }

    private void showCallNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        int imm = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        Intent full = new Intent(this, IncomingCallActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent fullPi = PendingIntent.getActivity(this, 1, full, imm);
        PendingIntent answerPi = PendingIntent.getService(this, 2, new Intent(this, CallService.class).setAction(ACTION_ANSWER), imm);
        PendingIntent declinePi = PendingIntent.getService(this, 3, new Intent(this, CallService.class).setAction(ACTION_DECLINE), imm);
        Notification n = new Notification.Builder(this, CH_CALL)
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle("📞 Prédiction lancée !")
                .setContentText(CALL_TEXT.replace('\n', ' '))
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(fullPi)
                .setFullScreenIntent(fullPi, true)
                .addAction(new Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.sym_action_call), "Décrocher", answerPi).build())
                .addAction(new Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), "Refuser", declinePi).build())
                .build();
        nm.notify(ID_CALL, n);
    }

    private void releasePlayer() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
        if (savedVolume >= 0) {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                try { am.setStreamVolume(AudioManager.STREAM_ALARM, savedVolume, 0); } catch (Exception ignored) { }
            }
            savedVolume = -1;
        }
    }

    private synchronized void stopRinging() {
        RINGING = false;
        pendingCalls = 0;
        releasePlayer();
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) v.cancel();
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(ID_CALL);
    }

    @Override
    public void onDestroy() {
        polling = false;
        if (poller != null) poller.interrupt();
        stopRinging();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        super.onDestroy();
    }
}
