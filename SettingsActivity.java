package com.kouame.appelpredictions;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Paramètres : adresses et clés des deux bases, cachées par défaut. */
public class SettingsActivity extends Activity {
    private final EditText[] urls = new EditText[2];
    private final EditText[] keys = new EditText[2];
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

        TextView title = UI.text(this, "⚙️  Paramètres", 28, Color.WHITE, true);
        root.addView(title);
        TextView sub = UI.text(this, "Les adresses et les clés sont cachées. Touche « Afficher » pour les voir ou les modifier.", 14, 0xFF94A3B8, false);
        sub.setPadding(0, dp(6), 0, 0);
        root.addView(sub);

        String[] names = {"🗄️  Base principale", "🛟  Base de secours"};
        for (int w = 0; w < 2; w++) {
            LinearLayout card = UI.card(this);
            card.addView(UI.text(this, names[w], 16, Color.WHITE, true));
            card.addView(label("Adresse du projet"));
            urls[w] = secret(Prefs.url(this, w));
            card.addView(urls[w]);
            card.addView(label("Clé publique (anon / publishable)"));
            keys[w] = secret(Prefs.key(this, w));
            card.addView(keys[w]);
            if (w == 1) {
                TextView hint = UI.text(this, "Laisse les deux champs vides pour désactiver la base de secours.", 12, 0xFF64748B, false);
                hint.setPadding(0, dp(10), 0, 0);
                card.addView(hint);
            }
            root.addView(card);
            UI.enter(card, w + 1);
        }

        toggle = UI.button(this, "👁  Afficher les clés", 0xFF3B82F6, 0xFF6366F1);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        tlp.topMargin = dp(20);
        root.addView(toggle, tlp);

        Button save = UI.button(this, "💾  Enregistrer", 0xFF22C55E, 0xFF16A34A);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        slp.topMargin = dp(12);
        root.addView(save, slp);

        toggle.setOnClickListener(v -> {
            shown = !shown;
            applyVisibility();
        });
        save.setOnClickListener(v -> save());
        setContentView(scroll);
    }

    private void applyVisibility() {
        for (int w = 0; w < 2; w++) {
            for (EditText e : new EditText[]{urls[w], keys[w]}) {
                e.setTransformationMethod(shown ? HideReturnsTransformationMethod.getInstance() : PasswordTransformationMethod.getInstance());
                e.setSelection(e.getText().length());
            }
        }
        toggle.setText(shown ? "🙈  Masquer les clés" : "👁  Afficher les clés");
    }

    private void save() {
        String[] names = {"principale", "de secours"};
        String[] newUrl = new String[2];
        String[] newKey = new String[2];
        for (int w = 0; w < 2; w++) {
            newUrl[w] = urls[w].getText().toString().trim();
            newKey[w] = keys[w].getText().toString().trim();
            boolean empty = newUrl[w].isEmpty() && newKey[w].isEmpty();
            if (empty && w == 1) continue; // le secours peut être désactivé
            if (!newUrl[w].startsWith("https://") || newKey[w].length() < 20) {
                Toast.makeText(this, "Base " + names[w] + " : adresse (https://…) ou clé invalide.", Toast.LENGTH_LONG).show();
                return;
            }
        }
        for (int w = 0; w < 2; w++) {
            if (!newUrl[w].equals(Prefs.url(this, w)) || !newKey[w].equals(Prefs.key(this, w))) {
                Prefs.setConnection(this, w, newUrl[w], newKey[w]);
            }
        }
        if (Prefs.active(this) == 1 && !Prefs.hasBase(this, 1)) Prefs.setActive(this, 0);
        Toast.makeText(this, "Enregistré ✅", Toast.LENGTH_SHORT).show();
        finish();
    }
}
