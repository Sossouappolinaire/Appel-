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
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import java.util.ArrayList;
import java.util.List;

/** Écran principal : compte à rebours de l'accès, historique des appels, démarrer / arrêter, tester. Réservé aux comptes validés. */
public class MainActivity extends Activity {
    private static final int GREEN = 0xFF22C55E;
    private static final int ORANGE = 0xFFF59E0B;
    private static final int RED = 0xFFEF4444;
    private static final int GREY = 0xFF94A3B8;
    private static final int CYAN = 0xFF22D3EE;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<ValueAnimator> animators = new ArrayList<>();
    private final GradientDrawable dotMain = new GradientDrawable();
    private TextView stateMain;

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

    // v148 : compte, accès et paiement
    private static final int REQ_PICK = 77;
    private final SimpleDateFormat hm = new SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE);
    private TextView countdown;
    private LinearLayout overlay;
    private TextView ovIcon;
    private TextView ovTitle;
    private TextView ovText;
    private TextView ovInfo;
    private Button ovPay;
    private Button ovPaid;
    private LinearLayout historyBox;
    private Button adminBtn;
    private LinearLayout connCard;
    private boolean fetching = false;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            handler.postDelayed(this, 1500);
        }
    };

    private final Runnable clock = new Runnable() {
        @Override
        public void run() {
            renderCountdown();
            handler.postDelayed(this, 1000);
        }
    };

    private final Runnable statePoll = new Runnable() {
        @Override
        public void run() {
            fetchState();
            handler.postDelayed(this, overlay != null && overlay.getVisibility() == View.VISIBLE ? 4000 : 10000);
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

    private LinearLayout baseRow(String name, GradientDrawable dot, TextView state) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, 0);
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(GREY);
        View d = new View(this);
        d.setBackground(dot);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(10), dp(10));
        dl.rightMargin = dp(10);
        row.addView(d, dl);
        row.addView(text(name, 14, Color.WHITE, false), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(state);
        return row;
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

        // ----- Historique : les 5 derniers appels -----
        LinearLayout hist = card();
        hist.addView(text("🕘  5 derniers appels", 15, Color.WHITE, true));
        historyBox = new LinearLayout(this);
        historyBox.setOrientation(LinearLayout.VERTICAL);
        historyBox.setPadding(0, dp(8), 0, 0);
        historyBox.addView(text("Chargement…", 13, GREY, false));
        hist.addView(historyBox);
        root.addView(hist);
        blocks.add(hist);

        // ----- Connexion à la base (administrateur seulement ; aucune clé affichée ici) -----
        connCard = card();
        connCard.addView(text("🗄️  Base de données", 15, Color.WHITE, true));
        stateMain = text("", 13, GREY, true);
        connCard.addView(baseRow("Connexion", dotMain, stateMain));
        TextView note = text("L'application lit la base à ta place : la clé reste cachée (administrateur : « Administration »).", 12, 0xFF64748B, false);
        note.setPadding(0, dp(12), 0, 0);
        connCard.addView(note);
        root.addView(connCard);
        blocks.add(connCard);

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
        adminBtn = button("👑  Administration", 0xFFEAB308, 0xFFCA8A04);
        actions.addView(adminBtn, full());
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
        adminBtn.setOnClickListener(v -> startActivity(new Intent(this, AdminActivity.class)));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(0xFF0B1020);
        column.addView(buildTopBar(), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        FrameLayout content = new FrameLayout(this);
        content.addView(scroll, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        overlay = buildOverlay();
        overlay.setVisibility(View.GONE);
        content.addView(overlay, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        column.addView(content, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(column);
        refreshStatus();
        applyAccess();
        renderCountdown();
        for (int i = 0; i < blocks.size(); i++) UI.enter(blocks.get(i), i);
    }

    // ── barre du haut : compte à rebours ─────────────────────────────────
    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xFF111827);
        bar.setPadding(dp(16), dp(10), dp(8), dp(10));
        bar.setElevation(dp(4));
        countdown = text("…", 16, Color.WHITE, true);
        bar.addView(countdown, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView out = text("Déconnexion", 12, GREY, true);
        out.setPadding(dp(12), dp(8), dp(12), dp(8));
        out.setOnClickListener(v -> logout());
        bar.addView(out);
        return bar;
    }

    /** Compte à rebours calculé avec l'heure du serveur (changer l'heure du téléphone ne sert à rien). */
    private void renderCountdown() {
        if (countdown == null) return;
        if (Prefs.isAdmin(this)) {
            countdown.setText("👑 Administrateur • accès illimité");
            countdown.setTextColor(0xFFFACC15);
            return;
        }
        String st = Prefs.accessStatus(this);
        if ("pending".equals(st)) {
            countdown.setText("🕓 Compte en attente de validation");
            countdown.setTextColor(ORANGE);
            return;
        }
        if ("blocked".equals(st)) {
            countdown.setText("⛔ Compte bloqué");
            countdown.setTextColor(RED);
            return;
        }
        long left = Prefs.accessUntil(this) - (System.currentTimeMillis() + Prefs.serverOffset(this));
        if (!Prefs.accessActive(this) || left <= 0) {
            countdown.setText("⌛ Temps écoulé");
            countdown.setTextColor(RED);
            if (Prefs.accessActive(this)) fetchState(); // le temps vient de finir : on demande au serveur (une durée a peut-être été ajoutée)
            return;
        }
        long s = left / 1000;
        long d = s / 86400;
        long h = (s % 86400) / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        String t = d > 0 ? String.format(Locale.FRANCE, "%d j %02d:%02d:%02d", d, h, m, sec) : String.format(Locale.FRANCE, "%02d:%02d:%02d", h, m, sec);
        countdown.setText("⏳ Temps restant : " + t);
        countdown.setTextColor(s > 3600 ? GREEN : (s > 600 ? ORANGE : RED));
    }

    // ── écran de blocage : attente de validation, compte bloqué, accès terminé ──
    private LinearLayout buildOverlay() {
        LinearLayout o = new LinearLayout(this);
        o.setOrientation(LinearLayout.VERTICAL);
        o.setGravity(Gravity.CENTER);
        o.setBackground(UI.background(0xFF0B1020, 0xFF1E1B4B));
        o.setPadding(dp(24), dp(24), dp(24), dp(24));
        o.setClickable(true); // les touches ne passent pas à l'écran dessous

        ovIcon = text("🔒", 54, Color.WHITE, false);
        ovIcon.setGravity(Gravity.CENTER);
        o.addView(ovIcon);
        ovTitle = text("", 22, Color.WHITE, true);
        ovTitle.setGravity(Gravity.CENTER);
        ovTitle.setPadding(0, dp(12), 0, 0);
        o.addView(ovTitle);
        ovText = text("", 14, 0xFFCBD5E1, false);
        ovText.setGravity(Gravity.CENTER);
        ovText.setPadding(0, dp(10), 0, 0);
        o.addView(ovText);
        ovInfo = text("", 13, 0xFF4ADE80, false);
        ovInfo.setGravity(Gravity.CENTER);
        ovInfo.setPadding(0, dp(12), 0, 0);
        o.addView(ovInfo);

        ovPay = button("💳  Payer", 0xFFF59E0B, 0xFFEA580C);
        ovPaid = button("✅  J'ai déjà payé", 0xFF22C55E, 0xFF16A34A);
        Button refresh = button("🔄  Actualiser", 0xFF3B82F6, 0xFF6366F1);
        Button out = button("Se déconnecter", 0xFF475569, 0xFF334155);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        lp.topMargin = dp(14);
        o.addView(ovPay, lp);
        o.addView(ovPaid, full());
        o.addView(refresh, full());
        o.addView(out, full());

        ovPay.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Prefs.PAY_URL)));
            } catch (Exception e) {
                Toast.makeText(this, "Impossible d'ouvrir le lien de paiement.", Toast.LENGTH_LONG).show();
            }
        });
        ovPaid.setOnClickListener(v -> pickScreenshot());
        refresh.setOnClickListener(v -> fetchState());
        out.setOnClickListener(v -> logout());
        return o;
    }

    /** Affiche ou cache l'écran de blocage selon l'état du compte, et les parties réservées à l'administrateur. */
    private void applyAccess() {
        boolean admin = Prefs.isAdmin(this);
        boolean active = Prefs.accessActive(this);
        String st = Prefs.accessStatus(this);
        if (adminBtn != null) adminBtn.setVisibility(admin ? View.VISIBLE : View.GONE);
        if (connCard != null) connCard.setVisibility(admin ? View.VISIBLE : View.GONE);
        if (overlay == null) return;
        if (active) {
            overlay.setVisibility(View.GONE);
            return;
        }
        overlay.setVisibility(View.VISIBLE);
        if ("pending".equals(st)) {
            ovIcon.setText("🕓");
            ovTitle.setText("Inscription envoyée");
            ovText.setText("L'administrateur doit valider ton compte. Tu auras ensuite 24 h d'essai. Cette page se met à jour toute seule.");
            ovInfo.setText("");
            ovPay.setVisibility(View.GONE);
            ovPaid.setVisibility(View.GONE);
        } else if ("blocked".equals(st)) {
            ovIcon.setText("⛔");
            ovTitle.setText("Compte bloqué");
            ovText.setText("L'administrateur a bloqué ce compte. Contacte-le pour en savoir plus.");
            ovInfo.setText("");
            ovPay.setVisibility(View.GONE);
            ovPaid.setVisibility(View.GONE);
        } else {
            ovIcon.setText("⌛");
            ovTitle.setText("Ton accès est terminé");
            ovText.setText("Appuie sur « Payer », puis sur « J'ai déjà payé » pour envoyer la capture d'écran de ton paiement. L'administrateur te rouvre l'accès après vérification.");
            ovInfo.setText(Prefs.pendingProof(this) ? "✅ Ta capture a été envoyée : l'administrateur va la vérifier." : "");
            ovPay.setVisibility(View.VISIBLE);
            ovPaid.setVisibility(View.VISIBLE);
            ovPaid.setText(Prefs.pendingProof(this) ? "✅  Envoyer une autre capture" : "✅  J'ai déjà payé");
        }
    }

    /** Demande l'état du compte (et les 5 derniers appels) au serveur. */
    private void fetchState() {
        if (fetching || Prefs.token(this).isEmpty()) return;
        fetching = true;
        new Thread(() -> {
            JSONObject r = Server.refreshState(this);
            JSONObject h = null;
            if (r.optBoolean("ok") && Prefs.accessActive(this)) h = Server.call(this, "app_history", "p_token", Prefs.token(this));
            final JSONObject hist = h;
            handler.post(() -> {
                fetching = false;
                if (r.optBoolean("ok")) {
                    applyAccess();
                    renderCountdown();
                    if (hist != null && hist.optBoolean("ok")) renderHistory(hist.optJSONArray("calls"));
                    else if (!Prefs.accessActive(this)) renderHistory(null);
                } else if ("session".equals(r.optString("error"))) {
                    goLogin();
                }
            });
        }).start();
    }

    private void renderHistory(JSONArray a) {
        if (historyBox == null) return;
        historyBox.removeAllViews();
        if (a == null || a.length() == 0) {
            historyBox.addView(text("Aucun appel pour l'instant.", 13, GREY, false));
            return;
        }
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            int game = o.optInt("game_number", 0);
            String line = hm.format(new Date(o.optLong("at_ms"))) + "   " + o.optString("suit") + (game > 0 ? " N°" + game : "") + "  •  " + o.optString("channel_name");
            TextView row = text(line, 13, 0xFFE2E8F0, false);
            row.setPadding(0, dp(6), 0, dp(6));
            historyBox.addView(row);
        }
    }

    private void goLogin() {
        Prefs.unlocked = false;
        startActivity(new Intent(this, LoginActivity.class));
    }

    private void logout() {
        final String token = Prefs.token(this);
        Prefs.setRunning(this, false);
        Prefs.setStatus(this, "Arrêté");
        stopService(new Intent(this, CallService.class));
        new Thread(() -> Server.call(this, "app_logout", "p_token", token)).start();
        Prefs.clearSession(this);
        Prefs.setBio(this, false);
        goLogin();
    }

    // ── preuve de paiement : capture d'écran envoyée à l'administrateur ───────
    private void pickScreenshot() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, "Choisis la capture d'écran du paiement"), REQ_PICK);
        } catch (Exception e) {
            Toast.makeText(this, "Impossible d'ouvrir la galerie.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK && resultCode == RESULT_OK && data != null && data.getData() != null) uploadProof(data.getData());
    }

    private void uploadProof(final Uri uri) {
        ovInfo.setTextColor(0xFFCBD5E1);
        ovInfo.setText("⏳ Envoi de la capture…");
        ovPaid.setEnabled(false);
        new Thread(() -> {
            String msg;
            boolean ok = false;
            try {
                String b64 = Img.toJpegBase64(this, uri, 1280);
                JSONObject r = Server.callTimeout(this, "app_submit_payment", 90000, "p_token", Prefs.token(this), "p_image", b64);
                ok = r.optBoolean("ok");
                msg = ok ? "✅ Capture envoyée : l'administrateur va la vérifier." : "❌ " + Server.message(r);
            } catch (Exception e) {
                msg = "❌ " + e.getMessage();
            }
            final String shown = msg;
            final boolean sent = ok;
            handler.post(() -> {
                ovPaid.setEnabled(true);
                ovInfo.setTextColor(sent ? 0xFF4ADE80 : 0xFFF87171);
                ovInfo.setText(shown);
                if (sent) fetchState();
            });
        }).start();
    }

    /** Met à jour la pastille, le texte d'état et la barre de progression. */
    private void refreshStatus() {
        boolean running = Prefs.running(this);
        String st = Prefs.status(this);
        String low = st.toLowerCase();
        boolean ok = running && st.startsWith("Connecté");
        boolean err = low.startsWith("erreur") || low.startsWith("pas de connexion") || low.startsWith("clé refusée")
                || low.startsWith("table") || low.startsWith("renseigne") || low.startsWith("accès") || low.startsWith("connexion requise")
                || low.startsWith("compte");

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

        boolean configured = true;
        int progress = ok ? 100 : (running && !err ? 67 : (configured ? 34 : 8));
        stepBar.setProgress(progress, true);
        step1.setTextColor(progress >= 34 ? CYAN : 0xFF64748B);
        step2.setTextColor(progress >= 67 ? CYAN : 0xFF64748B);
        step3.setTextColor(progress >= 100 ? GREEN : 0xFF64748B);
        activityBar.setVisibility(running && !err ? View.VISIBLE : View.GONE);

        showBase(dotMain, stateMain, true, running, ok, err);
    }

    private void showBase(GradientDrawable dot, TextView state, boolean configured, boolean active, boolean ok, boolean err) {
        int color;
        String label;
        if (!configured) {
            color = GREY;
            label = "Non configurée";
        } else if (active) {
            if (err) { color = RED; label = "Problème"; }
            else if (ok) { color = GREEN; label = "Active"; }
            else { color = ORANGE; label = "Connexion…"; }
        } else {
            color = CYAN;
            label = "Surveillée";
        }
        dot.setColor(color);
        state.setText(label);
        state.setTextColor(color);
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
        if (!Prefs.accessActive(this)) {
            Toast.makeText(this, "Ton accès n'est pas actif : voir l'écran de blocage.", Toast.LENGTH_LONG).show();
            return;
        }
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
        final CharSequence original = button.getText();
        button.setEnabled(false);
        button.setAlpha(0.6f);
        button.setText("⏳  Test en cours…");
        new Thread(() -> {
            JSONObject r = Server.refreshState(this);
            String shown;
            if (r.optBoolean("ok")) shown = "✅ Connexion OK" + (Prefs.accessActive(this) ? "" : " (accès en attente ou terminé)");
            else shown = "❌ " + Server.message(r);
            final String text = shown;
            handler.post(() -> {
                button.setEnabled(true);
                button.setAlpha(1f);
                button.setText(original);
                Toast.makeText(MainActivity.this, text, Toast.LENGTH_LONG).show();
                applyAccess();
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
        // verrouillé : PIN ou empreinte obligatoire à chaque lancement de l'application
        if (!Prefs.unlocked || Prefs.token(this).isEmpty()) {
            goLogin();
            return;
        }
        startAnimations();
        applyAccess();
        renderCountdown();
        handler.post(tick);
        handler.post(clock);
        handler.post(statePoll);
    }

    @Override
    protected void onPause() {
        super.onPause();
        UI.stopAll(animators);
        handler.removeCallbacks(tick);
        handler.removeCallbacks(clock);
        handler.removeCallbacks(statePoll);
    }
}
