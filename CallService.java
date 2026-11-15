package com.kouame.appelpredictions;

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
 * Service de premier plan : lit la table « apk_calls » de Supabase toutes les 3 secondes ; dès qu'une nouvelle ligne
 * apparaît (prédiction lancée dans le canal choisi sur le site), le téléphone sonne comme un appel entrant,
 * en boucle, jusqu'à « Décrocher » (ou « Refuser »).
 */
public class CallService extends Service {
    static final String ACTION_ANSWER = "com.kouame.appelpredictions.ANSWER";
    static final String ACTION_DECLINE = "com.kouame.appelpredictions.DECLINE";
    static final String ACTION_TEST = "com.kouame.appelpredictions.TEST";

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
    private MediaPlayer player;
    private int savedVolume = -1;
    private int pendingCalls = 0;
    private String lastStatus = "";

    @Override
    public IBinder onBind(Intent intent) { return null; }

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
            startForegroundCompat("Arrêté");
            stopSelf();
            return START_NOT_STICKY;
        }
        startForegroundCompat(Prefs.status(this));
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
    }

    private void setStatus(String s) {
        Prefs.setStatus(this, s);
        if (s.equals(lastStatus)) return;
        lastStatus = s;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(ID_SERVICE, serviceNotification(s));
    }

    // ── lecture de Supabase ─────────────────────────────────────────────────
    private void startPolling() {
        if (poller != null && poller.isAlive()) return;
        polling = true;
        poller = new Thread(() -> {
            int sinceCheck = 0;
            while (polling) {
                try {
                    sinceCheck = pollOnce(sinceCheck);
                } catch (Throwable t) {
                    setStatus("Erreur : " + t.getMessage());
                }
                try { Thread.sleep(POLL_MS); } catch (InterruptedException e) { return; }
            }
        }, "lecture-supabase");
        poller.start();
    }

    private String now() { return new SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(new Date()); }

    private int pollOnce(int sinceCheck) throws Exception {
        String url = Prefs.url(this);
        String key = Prefs.key(this);
        if (url.isEmpty() || key.isEmpty()) { setStatus("Renseigne l'adresse Supabase et la clé publique."); return sinceCheck; }
        long last = Prefs.lastId(this);
        if (last < 0) { // première connexion : on part du dernier appel existant (les anciens ne sonnent pas)
            Api.Result r = Api.get(url, key, "/rest/v1/apk_calls?select=id&order=id.desc&limit=1");
            String p = Api.problem(r);
            if (p != null) { setStatus(p); return sinceCheck; }
            JSONArray a = new JSONArray(r.body);
            Prefs.setLastId(this, a.length() > 0 ? a.getJSONObject(0).getLong("id") : 0);
            setStatus("Connecté • en attente d'une prédiction (" + now() + ")");
            return 0;
        }
        Api.Result r = Api.get(url, key, "/rest/v1/apk_calls?select=id,channel_name,game_number,suit,kind&id=gt." + last + "&order=id.asc&limit=20");
        String p = Api.problem(r);
        if (p != null) { setStatus(p); return sinceCheck; }
        JSONArray a = new JSONArray(r.body);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            Prefs.setLastId(this, o.getLong("id"));
            String game = o.isNull("game_number") || o.optInt("game_number", 0) == 0 ? "" : " N°" + o.optInt("game_number");
            String text;
            if ("test".equals(o.optString("kind"))) text = "🧪 Appel de test du site";
            else text = (o.optString("suit") + game + "\n" + o.optString("channel_name")).trim();
            startRinging(text);
        }
        setStatus("Connecté • dernière vérification " + now());
        // le compteur d'appels peut repartir de zéro si la table a été recréée : on se recale de temps en temps
        sinceCheck++;
        if (sinceCheck >= 20) {
            sinceCheck = 0;
            Api.Result m = Api.get(url, key, "/rest/v1/apk_calls?select=id&order=id.desc&limit=1");
            if (Api.problem(m) == null) {
                JSONArray ma = new JSONArray(m.body);
                long max = ma.length() > 0 ? ma.getJSONObject(0).getLong("id") : 0;
                if (max < Prefs.lastId(this)) Prefs.setLastId(this, max);
            }
        }
        return sinceCheck;
    }

    // ── sonnerie ────────────────────────────────────────────────────────────
    private synchronized void startRinging(String text) {
        pendingCalls = RINGING ? pendingCalls + 1 : 1;
        CALL_TEXT = pendingCalls > 1 ? text + "\n(+" + (pendingCalls - 1) + " autre(s) prédiction(s))" : text;
        RINGING = true;
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
        super.onDestroy();
    }
}
