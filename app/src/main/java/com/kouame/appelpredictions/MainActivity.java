package com.kouame.appelpredictions;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

/** Écran de réglage : adresse Supabase, clé publique, démarrer / arrêter, tester. */
public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private EditText urlField;
    private EditText keyField;
    private TextView statusView;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            statusView.setText("État : " + Prefs.status(MainActivity.this) + (Prefs.running(MainActivity.this) ? "" : "\n(surveillance arrêtée)"));
            handler.postDelayed(this, 1500);
        }
    };

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView label(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(14);
        v.setPadding(0, dp(14), 0, dp(4));
        return v;
    }

    private Button button(String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(40), dp(20), dp(30));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("📞 Appel Prédictions");
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView help = new TextView(this);
        help.setText("Le téléphone sonne comme un appel quand une prédiction est lancée dans le canal choisi sur le site (bouton « Appel APK »). Il sonne jusqu'à ce que tu décroches.");
        help.setPadding(0, dp(12), 0, 0);
        root.addView(help);

        root.addView(label("Adresse Supabase"));
        urlField = new EditText(this);
        urlField.setSingleLine(true);
        urlField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlField.setText(Prefs.url(this));
        root.addView(urlField);

        root.addView(label("Clé publique (anon / publishable) du projet Supabase"));
        keyField = new EditText(this);
        keyField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        keyField.setMinLines(2);
        keyField.setText(Prefs.key(this));
        root.addView(keyField);

        statusView = new TextView(this);
        statusView.setPadding(0, dp(16), 0, dp(8));
        root.addView(statusView);

        Button start = button("▶  Enregistrer et démarrer");
        Button stop = button("■  Arrêter");
        Button test = button("🔔  Tester la sonnerie (sans le site)");
        Button conn = button("🔌  Tester la connexion à Supabase");
        Button perm = button("⚙️  Autorisations (appel plein écran, batterie)");
        root.addView(start);
        root.addView(stop);
        root.addView(test);
        root.addView(conn);
        root.addView(perm);

        start.setOnClickListener(v -> startWatching());
        stop.setOnClickListener(v -> {
            Prefs.setRunning(this, false);
            Prefs.setStatus(this, "Arrêté");
            stopService(new Intent(this, CallService.class));
        });
        test.setOnClickListener(v -> {
            askNotifications();
            startForegroundService(new Intent(this, CallService.class).setAction(CallService.ACTION_TEST));
        });
        conn.setOnClickListener(v -> testConnection());
        perm.setOnClickListener(v -> openPermissions());
        setContentView(scroll);
    }

    private void askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    private void startWatching() {
        String url = urlField.getText().toString().trim();
        String key = keyField.getText().toString().trim();
        if (!url.startsWith("https://") || key.length() < 20) {
            Toast.makeText(this, "Renseigne l'adresse (https://…) et la clé publique.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!url.equals(Prefs.url(this)) || !key.equals(Prefs.key(this))) Prefs.setConnection(this, url, key);
        Prefs.setRunning(this, true);
        Prefs.setStatus(this, "Démarrage…");
        askNotifications();
        startForegroundService(new Intent(this, CallService.class));
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {
                // réglage indisponible sur ce téléphone
            }
        }
    }

    private void testConnection() {
        final String url = urlField.getText().toString().trim();
        final String key = keyField.getText().toString().trim();
        new Thread(() -> {
            Api.Result r = Api.get(url, key, "/rest/v1/apk_calls?select=id&order=id.desc&limit=1");
            String p = Api.problem(r);
            String msg;
            if (p != null) msg = p;
            else {
                int n = 0;
                try { n = new JSONArray(r.body).length(); } catch (Exception ignored) { }
                msg = "Connexion réussie ✅ (table « apk_calls » lisible" + (n > 0 ? ", des appels existent" : ", aucun appel pour l'instant") + ").";
            }
            final String shown = msg;
            handler.post(() -> Toast.makeText(MainActivity.this, shown, Toast.LENGTH_LONG).show());
        }).start();
    }

    private void openPermissions() {
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && !nm.canUseFullScreenIntent()) {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + getPackageName())));
                    return;
                } catch (Exception ignored) {
                    // on continue vers les réglages généraux
                }
            }
        }
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }
}
