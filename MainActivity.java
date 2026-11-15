package com.kouame.appelpredictions;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/** Écran de réglage : adresse Supabase, clé publique, démarrer / arrêter, tester. */
public class MainActivity extends Activity {
    private static final int GREEN = 0xFF22C55E;
    private static final int ORANGE = 0xFFF59E0B;
    private static final int RED = 0xFFEF4444;
    private static final int GREY = 0xFF94A3B8;
    private static final int CYAN = 0xFF22D3EE;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<ValueAnimator> animators = new ArrayList<>();
    private EditText urlField;
    private EditText keyField;

    private GradientDrawable dotBg;
    private TextView statusTitle;
    private TextView statusDetail;
    private ProgressBar stepBar;
    private ProgressBar activityBar;
    private TextView step1;
    private TextView step2;
    private TextView step3;
    private View ring1;
    private View ring2;
    private View core;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            handler.postDelayed(this, 1500);
        }
    };

    private int dp(int v) { return UI.dp(this, v); }

    private TextView text(String t, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = UI.shape(this, 0xFF1E293B, 20);
        g.setStroke(dp(1), 0xFF334155);
        c.setBackground(g);
        c.setPadding(dp(18), dp(16), dp(18), dp(18));
        c.setElevation(dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(16);
        c.setLayoutParams(lp);
        return c;
    }

    private EditText field(String value, boolean multi) {
        EditText e = new EditText(this);
        final GradientDrawable g = UI.shape(this, 0xFF0F172A, 12);
        g.setStroke(dp(1), 0xFF334155);
        e.setBackground(g);
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(0xFF64748B);
        e.setTextSize(14);
        e.setText(value);
        if (multi) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            e.setMinLines(2);
        } else {
            e.setSingleLine(true);
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        }
        e.setOnFocusChangeListener((v, focused) -> g.setStroke(dp(focused ? 2 : 1), focused ? 0xFF38BDF8 : 0xFF334155));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        e.setLayoutParams(lp);
        return e;
    }

    private Button button(String t, int c1, int c2) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(UI.rippleButton(this, c1, c2, 16));
        b.setStateListAnimator(null);
        b.setElevation(dp(6));
        b.setPadding(dp(12), 0, dp(12), 0);
        UI.pressEffect(b);
        return b;
    }

    private LinearLayout.LayoutParams full() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        lp.topMargin = dp(12);
        return lp;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF0B1020);
        getWindow().setNavigationBarColor(0xFF1E1B4B);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(UI.background(0xFF0B1020, 0xFF1E1B4B));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(32));
        scroll.addView(root);

        final List<View> blocks = new ArrayList<>();

        // ----- En-tête : icône qui pulse -----
        FrameLayout header = new FrameLayout(this);
        header.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(170)));
        ring1 = UI.ring(this, 88, CYAN, 2);
        ring2 = UI.ring(this, 88, GREEN, 2);
        core = new TextView(this);
        TextView icon = (TextView) core;
        icon.setText("📞");
        icon.setTextSize(38);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(UI.disc(GREEN, CYAN));
        icon.setElevation(dp(10));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp(88), dp(88), Gravity.CENTER);
        header.addView(ring1);
        header.addView(ring2);
        header.addView(icon, clp);
        root.addView(header);
        blocks.add(header);

        TextView title = text("Appel Prédictions", 28, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        blocks.add(title);

        TextView sub = text("Ton téléphone sonne comme un vrai appel dès qu'une prédiction est lancée sur le site.", 14, GREY, false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(dp(8), dp(8), dp(8), 0);
        root.addView(sub);
        blocks.add(sub);

        // ----- Carte d'état avec progression -----
        LinearLayout status = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(GREY);
        dot.setBackground(dotBg);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(12), dp(12));
        dlp.rightMargin = dp(10);
        row.addView(dot, dlp);
        statusTitle = text("Arrêté", 17, Color.WHITE, true);
        row.addView(statusTitle);
        status.addView(row);

        statusDetail = text("", 13, 0xFFCBD5E1, false);
        statusDetail.setPadding(0, dp(8), 0, dp(12));
        status.addView(statusDetail);

        stepBar = UI.bar(this, 0xFF3B82F6, GREEN);
        status.addView(stepBar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(10)));

        LinearLayout steps = new LinearLayout(this);
        steps.setOrientation(LinearLayout.HORIZONTAL);
        steps.setPadding(0, dp(8), 0, 0);
        step1 = text("① Réglé", 12, 0xFF64748B, true);
        step2 = text("② Actif", 12, 0xFF64748B, true);
        step3 = text("③ Connecté", 12, 0xFF64748B, true);
        step1.setGravity(Gravity.START);
        step2.setGravity(Gravity.CENTER);
        step3.setGravity(Gravity.END);
        steps.addView(step1, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        steps.addView(step2, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        steps.addView(step3, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        status.addView(steps);

        activityBar = UI.activityBar(this, CYAN);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(4));
        alp.topMargin = dp(12);
        status.addView(activityBar, alp);
        root.addView(status);
        blocks.add(status);

        // ----- Carte de connexion -----
        LinearLayout conn = card();
        conn.addView(text("🔗  Connexion Supabase", 15, Color.WHITE, true));
        TextView l1 = text("Adresse du projet", 12, GREY, false);
        l1.setPadding(0, dp(12), 0, 0);
        conn.addView(l1);
        urlField = field(Prefs.url(this), false);
        conn.addView(urlField);
        TextView l2 = text("Clé publique (anon / publishable)", 12, GREY, false);
        l2.setPadding(0, dp(12), 0, 0);
        conn.addView(l2);
        keyField = field(Prefs.key(this), true);
        conn.addView(keyField);
        root.addView(conn);
        blocks.add(conn);

        // ----- Boutons colorés -----
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams alp2 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        alp2.topMargin = dp(8);
        actions.setLayoutParams(alp2);

        Button start = button("▶  Démarrer", 0xFF22C55E, 0xFF16A34A);
        Button stop = button("■  Arrêter", 0xFFEF4444, 0xFFB91C1C);
        LinearLayout duo = new LinearLayout(this);
        duo.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, dp(54), 1f);
        l.setMargins(0, dp(12), dp(6), 0);
        LinearLayout.LayoutParams r = new LinearLayout.LayoutParams(0, dp(54), 1f);
        r.setMargins(dp(6), dp(12), 0, 0);
        duo.addView(start, l);
        duo.addView(stop, r);
        actions.addView(duo);

        final Button test = button("🔔  Tester la sonnerie", 0xFFF59E0B, 0xFFEA580C);
        final Button testConn = button("🔌  Tester la connexion", 0xFF3B82F6, 0xFF6366F1);
        Button perm = button("⚙️  Autorisations (plein écran, batterie)", 0xFFA855F7, 0xFF7C3AED);
        actions.addView(test, full());
        actions.addView(testConn, full());
        actions.addView(perm, full());
        root.addView(actions);
        blocks.add(actions);

        start.setOnClickListener(v -> startWatching());
        stop.setOnClickListener(v -> {
            Prefs.setRunning(this, false);
            Prefs.setStatus(this, "Arrêté");
            stopService(new Intent(this, CallService.class));
            refreshStatus();
        });
        test.setOnClickListener(v -> {
            askNotifications();
            startForegroundService(new Intent(this, CallService.class).setAction(CallService.ACTION_TEST));
        });
        testConn.setOnClickListener(v -> testConnection(testConn));
        perm.setOnClickListener(v -> openPermissions());

        setContentView(scroll);
        refreshStatus();
        for (int i = 0; i < blocks.size(); i++) UI.enter(blocks.get(i), i);
    }

    /** Met à jour la pastille, le texte d'état et la barre de progression. */
    private void refreshStatus() {
        boolean running = Prefs.running(this);
        String st = Prefs.status(this);
        String low = st.toLowerCase();
        boolean ok = running && st.startsWith("Connecté");
        boolean err = low.startsWith("erreur") || low.startsWith("pas de connexion") || low.startsWith("clé refusée")
                || low.startsWith("table") || low.startsWith("renseigne");

        int color;
        String title;
        String detail;
        if (err) {
            color = RED;
            title = "Problème";
            detail = st;
        } else if (ok) {
            color = GREEN;
            title = "En surveillance";
            detail = st;
        } else if (running) {
            color = ORANGE;
            title = "Démarrage…";
            detail = st;
        } else {
            color = GREY;
            title = "Arrêté";
            detail = "La surveillance est arrêtée. Appuie sur « Démarrer ».";
        }
        dotBg.setColor(color);
        statusTitle.setText(title);
        statusTitle.setTextColor(color == GREY ? Color.WHITE : color);
        statusDetail.setText(detail);

        boolean configured = urlField.getText().toString().trim().startsWith("https://") && keyField.getText().toString().trim().length() >= 20;
        int progress = ok ? 100 : (running && !err ? 67 : (configured ? 34 : 8));
        stepBar.setProgress(progress, true);
        step1.setTextColor(progress >= 34 ? CYAN : 0xFF64748B);
        step2.setTextColor(progress >= 67 ? CYAN : 0xFF64748B);
        step3.setTextColor(progress >= 100 ? GREEN : 0xFF64748B);
        activityBar.setVisibility(running && !err ? View.VISIBLE : View.GONE);
    }

    private void startAnimations() {
        UI.stopAll(animators);
        UI.pulse(ring1, 0, 1.9f, animators);
        UI.pulse(ring2, 1100, 1.9f, animators);
        UI.breathe(core, 1.07f, 900, animators);
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
        refreshStatus();
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

    private void testConnection(final Button button) {
        final String url = urlField.getText().toString().trim();
        final String key = keyField.getText().toString().trim();
        final CharSequence original = button.getText();
        button.setEnabled(false);
        button.setAlpha(0.6f);
        button.setText("⏳  Test en cours…");
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
            handler.post(() -> {
                button.setEnabled(true);
                button.setAlpha(1f);
                button.setText(original);
                Toast.makeText(MainActivity.this, shown, Toast.LENGTH_LONG).show();
            });
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
        startAnimations();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        UI.stopAll(animators);
        handler.removeCallbacks(tick);
    }
}
