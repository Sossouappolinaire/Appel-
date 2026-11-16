package com.kouame.appelpredictions;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Clé de la base : visible et modifiable seulement par l'administrateur (jamais par les autres utilisateurs). */
public class SettingsActivity extends Activity {
    private EditText url;
    private EditText key;
    private boolean shown = false;
    private Button toggle;

    private int dp(int v) { return UI.dp(this, v); }

    /** Champ dont le contenu est caché (points) tant qu'on ne touche pas « Afficher ». */
    private EditText secret(String value) {
        EditText e = new EditText(this);
        final GradientDrawable g = UI.shape(this, 0xFF0F172A, 12);
        g.setStroke(dp(1), 0xFF334155);
        e.setBackground(g);
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(0xFF64748B);
        e.setTextSize(14);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setSingleLine(true);
        e.setText(value);
        e.setTransformationMethod(PasswordTransformationMethod.getInstance());
        e.setOnFocusChangeListener((v, focused) -> g.setStroke(dp(focused ? 2 : 1), focused ? 0xFF38BDF8 : 0xFF334155));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        e.setLayoutParams(lp);
        return e;
    }

    private TextView label(String t) {
        TextView v = UI.text(this, t, 12, 0xFF94A3B8, false);
        v.setPadding(0, dp(12), 0, 0);
        return v;
    }

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

        root.addView(UI.text(this, "🔑  Clé de la base", 28, Color.WHITE, true));
        TextView sub = UI.text(this, "Réservé à l'administrateur. L'adresse et la clé sont cachées : touche « Afficher » pour les voir ou les modifier.", 14, 0xFF94A3B8, false);
        sub.setPadding(0, dp(6), 0, 0);
        root.addView(sub);

        LinearLayout card = UI.card(this);
        card.addView(UI.text(this, "🗄️  Base de données", 16, Color.WHITE, true));
        card.addView(label("Adresse du projet"));
        url = secret(Prefs.url(this));
        card.addView(url);
        card.addView(label("Clé publique (anon / publishable)"));
        key = secret(Prefs.key(this));
        card.addView(key);
        root.addView(card);
        UI.enter(card, 1);

        toggle = UI.button(this, "👁  Afficher la clé", 0xFF3B82F6, 0xFF6366F1);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        tlp.topMargin = dp(20);
        root.addView(toggle, tlp);

        Button save = UI.button(this, "💾  Enregistrer", 0xFF22C55E, 0xFF16A34A);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        slp.topMargin = dp(12);
        root.addView(save, slp);

        toggle.setOnClickListener(v -> {
            shown = !shown;
            for (EditText e : new EditText[]{url, key}) {
                e.setTransformationMethod(shown ? HideReturnsTransformationMethod.getInstance() : PasswordTransformationMethod.getInstance());
                e.setSelection(e.getText().length());
            }
            toggle.setText(shown ? "🙈  Masquer la clé" : "👁  Afficher la clé");
        });
        save.setOnClickListener(v -> save());
        setContentView(scroll);
    }

    private void save() {
        String u = url.getText().toString().trim();
        String k = key.getText().toString().trim();
        if (!u.startsWith("https://") || k.length() < 20) {
            Toast.makeText(this, "Adresse (https://…) ou clé invalide.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!u.equals(Prefs.url(this)) || !k.equals(Prefs.key(this))) Prefs.setConnection(this, u, k);
        Toast.makeText(this, "Enregistré ✅", Toast.LENGTH_SHORT).show();
        finish();
    }
}
