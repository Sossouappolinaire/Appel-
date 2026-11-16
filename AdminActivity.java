package com.kouame.appelpredictions;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Panneau administrateur : validation des inscriptions, captures de paiement, durées accordées, accès à la clé de la base. */
public class AdminActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat fmt = new SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE);
    private LinearLayout usersBox;
    private LinearLayout proofsBox;
    private TextView info;
    private boolean visible = false;

    private final Runnable auto = new Runnable() {
        @Override
        public void run() {
            if (!visible) return;
            load();
            handler.postDelayed(this, 15000);
        }
    };

    private int dp(int v) { return UI.dp(this, v); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Prefs.isAdmin(this)) {
            finish();
            return;
        }
        getWindow().setStatusBarColor(0xFF0B1020);
        getWindow().setNavigationBarColor(0xFF1E1B4B);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(UI.background(0xFF0B1020, 0xFF1E1B4B));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(32));
        scroll.addView(root);

        TextView back = UI.text(this, "←  Retour", 15, 0xFF38BDF8, true);
        back.setPadding(0, dp(4), dp(16), dp(12));
        back.setOnClickListener(v -> finish());
        root.addView(back);

        root.addView(UI.text(this, "👑  Administration", 26, Color.WHITE, true));
        info = UI.text(this, "Chargement…", 13, 0xFF94A3B8, false);
        info.setPadding(0, dp(6), 0, 0);
        root.addView(info);

        Button keys = UI.button(this, "🔑  Clé de la base", 0xFF0EA5E9, 0xFF2563EB);
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        klp.topMargin = dp(16);
        root.addView(keys, klp);
        keys.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        Button refresh = UI.button(this, "🔄  Actualiser", 0xFFA855F7, 0xFF7C3AED);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        rlp.topMargin = dp(12);
        root.addView(refresh, rlp);
        refresh.setOnClickListener(v -> load());

        LinearLayout proofsCard = UI.card(this);
        proofsCard.addView(UI.text(this, "💳  Paiements à vérifier", 16, Color.WHITE, true));
        proofsBox = new LinearLayout(this);
        proofsBox.setOrientation(LinearLayout.VERTICAL);
        proofsCard.addView(proofsBox);
        root.addView(proofsCard);

        LinearLayout usersCard = UI.card(this);
        usersCard.addView(UI.text(this, "👥  Inscriptions", 16, Color.WHITE, true));
        usersBox = new LinearLayout(this);
        usersBox.setOrientation(LinearLayout.VERTICAL);
        usersCard.addView(usersBox);
        root.addView(usersCard);

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        handler.post(auto);
    }

    @Override
    protected void onPause() {
        super.onPause();
        visible = false;
        handler.removeCallbacks(auto);
    }

    private Button small(String t, int c1, int c2) {
        Button b = UI.button(this, t, c1, c2);
        b.setTextSize(13);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        return b;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        lp.setMargins(dp(3), dp(8), dp(3), 0);
        return lp;
    }

    private String when(long ms) { return fmt.format(new Date(ms)); }

    private void load() {
        new Thread(() -> {
            JSONObject r = Server.call(this, "adm_list", "p_token", Prefs.token(this));
            handler.post(() -> {
                if (!r.optBoolean("ok")) {
                    if ("session".equals(r.optString("error"))) {
                        Prefs.clearSession(this);
                        finish();
                        return;
                    }
                    info.setText("❌ " + Server.message(r));
                    return;
                }
                render(r);
            });
        }).start();
    }

    private void render(JSONObject r) {
        JSONArray users = r.optJSONArray("users");
        JSONArray proofs = r.optJSONArray("proofs");
        int pending = 0;
        int toCheck = 0;
        usersBox.removeAllViews();
        proofsBox.removeAllViews();
        long serverNow = System.currentTimeMillis() + Prefs.serverOffset(this);

        if (users != null) {
            for (int i = 0; i < users.length(); i++) {
                JSONObject u = users.optJSONObject(i);
                if (u == null) continue;
                if ("pending".equals(u.optString("status"))) pending++;
                usersBox.addView(userRow(u, serverNow));
            }
        }
        if (proofs != null) {
            for (int i = 0; i < proofs.length(); i++) {
                JSONObject p = proofs.optJSONObject(i);
                if (p == null) continue;
                if ("pending".equals(p.optString("status"))) toCheck++;
                proofsBox.addView(proofRow(p));
            }
        }
        if (usersBox.getChildCount() == 0) usersBox.addView(UI.text(this, "Aucune inscription.", 13, 0xFF94A3B8, false));
        if (proofsBox.getChildCount() == 0) proofsBox.addView(UI.text(this, "Aucune capture reçue.", 13, 0xFF94A3B8, false));
        info.setText(pending + " compte(s) à valider • " + toCheck + " capture(s) à vérifier");
    }

    private View userRow(JSONObject u, long serverNow) {
        final String id = u.optString("id");
        String status = u.optString("status");
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(14), 0, 0);
        row.addView(UI.text(this, u.optString("email"), 15, Color.WHITE, true));

        String line;
        int color = 0xFF94A3B8;
        if (u.optBoolean("is_admin")) {
            line = "👑 Administrateur";
        } else if ("pending".equals(status)) {
            line = "🕓 En attente • inscrit le " + when(u.optLong("created_ms"));
            color = 0xFFF59E0B;
        } else if ("blocked".equals(status)) {
            line = "⛔ Bloqué";
            color = 0xFFEF4444;
        } else {
            long until = u.optLong("access_until_ms", 0);
            if (until > serverNow) {
                line = "✅ Accès jusqu'au " + when(until) + " (reste " + Server.duration(until - serverNow) + ")";
                color = 0xFF22C55E;
            } else {
                line = "⌛ Accès terminé le " + when(until);
                color = 0xFFEF4444;
            }
        }
        TextView st = UI.text(this, line, 12, color, false);
        st.setPadding(0, dp(2), 0, 0);
        row.addView(st);

        if (!u.optBoolean("is_admin")) {
            LinearLayout btns = new LinearLayout(this);
            btns.setOrientation(LinearLayout.HORIZONTAL);
            if ("pending".equals(status)) {
                Button ok = small("✅ Valider", 0xFF22C55E, 0xFF16A34A);
                ok.setOnClickListener(v -> userAction(id, "approve", "Compte validé : 24 h d'essai"));
                btns.addView(ok, weight());
            }
            if ("blocked".equals(status)) {
                Button un = small("🔓 Débloquer", 0xFF3B82F6, 0xFF6366F1);
                un.setOnClickListener(v -> userAction(id, "unblock", "Compte débloqué"));
                btns.addView(un, weight());
            } else {
                Button bl = small("⛔ Bloquer", 0xFFEF4444, 0xFFB91C1C);
                bl.setOnClickListener(v -> userAction(id, "block", "Compte bloqué"));
                btns.addView(bl, weight());
            }
            row.addView(btns);
        }
        return row;
    }

    private View proofRow(JSONObject p) {
        final long proofId = p.optLong("id");
        final String userId = p.optString("user_id");
        boolean done = "done".equals(p.optString("status"));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(14), 0, 0);
        row.addView(UI.text(this, p.optString("email"), 15, Color.WHITE, true));
        String line = "Reçue le " + when(p.optLong("created_ms")) + (done ? " • traitée : +" + p.optInt("granted_hours") + " h accordées" : " • à traiter");
        TextView st = UI.text(this, line, 12, done ? 0xFF22C55E : 0xFFF59E0B, false);
        st.setPadding(0, dp(2), 0, 0);
        row.addView(st);

        Button view = small("🖼  Voir la capture", 0xFF0EA5E9, 0xFF2563EB);
        view.setOnClickListener(v -> showProof(proofId));
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        vlp.topMargin = dp(8);
        row.addView(view, vlp);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        int[] hours = {24, 48, 72};
        for (final int h : hours) {
            Button b = small("+" + h + " h", 0xFF22C55E, 0xFF16A34A);
            b.setOnClickListener(v -> grant(userId, h, proofId));
            btns.addView(b, weight());
        }
        row.addView(btns);
        return row;
    }

    private void userAction(final String id, final String action, final String okText) {
        new Thread(() -> {
            JSONObject r = Server.call(this, "adm_user_status", "p_token", Prefs.token(this), "p_user", id, "p_action", action);
            handler.post(() -> {
                Toast.makeText(this, r.optBoolean("ok") ? okText : "❌ " + Server.message(r), Toast.LENGTH_SHORT).show();
                load();
            });
        }).start();
    }

    /** Chaque appui ajoute la durée à ce que le compte a déjà : 24 h puis 24 h = 48 h. */
    private void grant(final String userId, final int hours, final long proofId) {
        new Thread(() -> {
            JSONObject r = Server.call(this, "adm_grant", "p_token", Prefs.token(this), "p_user", userId, "p_hours", hours, "p_proof", proofId);
            handler.post(() -> {
                if (r.optBoolean("ok")) {
                    long left = r.optLong("access_until_ms") - (System.currentTimeMillis() + Prefs.serverOffset(this));
                    Toast.makeText(this, "✅ +" + hours + " h accordées • total de cette capture : " + r.optInt("granted_total") + " h • reste " + Server.duration(left), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "❌ " + Server.message(r), Toast.LENGTH_LONG).show();
                }
                load();
            });
        }).start();
    }

    private void showProof(final long proofId) {
        Toast.makeText(this, "Chargement de la capture…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            JSONObject r = Server.callTimeout(this, "adm_proof", 60000, "p_token", Prefs.token(this), "p_id", proofId);
            Bitmap bmp = null;
            if (r.optBoolean("ok")) {
                try {
                    byte[] bytes = Base64.decode(r.optString("image"), Base64.DEFAULT);
                    bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                } catch (Exception ignored) {
                    bmp = null;
                }
            }
            final Bitmap shown = bmp;
            handler.post(() -> {
                if (shown == null) {
                    Toast.makeText(this, "❌ Capture illisible", Toast.LENGTH_LONG).show();
                    return;
                }
                ImageView iv = new ImageView(this);
                iv.setImageBitmap(shown);
                iv.setAdjustViewBounds(true);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                ScrollView sv = new ScrollView(this);
                sv.addView(iv);
                new AlertDialog.Builder(this).setView(sv).setPositiveButton("Fermer", null).show();
            });
        }).start();
    }
}
