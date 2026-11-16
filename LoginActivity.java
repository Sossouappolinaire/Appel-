package com.kouame.appelpredictions;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

/** Connexion par email + PIN ou empreinte digitale. La première fois, l'utilisateur crée son compte. */
public class LoginActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean registerMode = false;
    private boolean busy = false;

    private TextView modeTitle;
    private TextView message;
    private EditText email;
    private EditText pin;
    private EditText pin2;
    private Button main;
    private TextView switchMode;
    private Button finger;

    private int dp(int v) { return UI.dp(this, v); }

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
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(20), dp(48), dp(20), dp(32));
        scroll.addView(root);

        TextView icon = new TextView(this);
        icon.setText("📞");
        icon.setTextSize(38);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(UI.disc(0xFF22C55E, 0xFF22D3EE));
        icon.setElevation(dp(10));
        root.addView(icon, new LinearLayout.LayoutParams(dp(88), dp(88)));

        TextView title = UI.text(this, "Appel Prédictions", 26, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(16), 0, 0);
        root.addView(title);

        LinearLayout card = UI.card(this);
        modeTitle = UI.text(this, "Connexion", 18, Color.WHITE, true);
        card.addView(modeTitle);

        email = UI.field(this, "Email", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        email.setText(Prefs.email(this));
        card.addView(email);

        pin = UI.field(this, "PIN (4 à 8 chiffres)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});
        card.addView(pin);

        pin2 = UI.field(this, "Confirme le PIN", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin2.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});
        pin2.setVisibility(View.GONE);
        card.addView(pin2);

        message = UI.text(this, "", 13, 0xFFF87171, false);
        message.setPadding(0, dp(10), 0, 0);
        card.addView(message);

        main = UI.button(this, "Se connecter", 0xFF22C55E, 0xFF16A34A);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        mlp.topMargin = dp(14);
        card.addView(main, mlp);

        finger = UI.button(this, "🔐  Utiliser l'empreinte digitale", 0xFF3B82F6, 0xFF6366F1);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        flp.topMargin = dp(10);
        card.addView(finger, flp);

        switchMode = UI.text(this, "Pas encore de compte ?  Créer un compte", 14, 0xFF38BDF8, true);
        switchMode.setGravity(Gravity.CENTER);
        switchMode.setPadding(0, dp(16), 0, dp(4));
        card.addView(switchMode);
        root.addView(card);

        main.setOnClickListener(v -> submit());
        finger.setOnClickListener(v -> promptBiometric());
        switchMode.setOnClickListener(v -> setMode(!registerMode));

        setContentView(scroll);
        updateFingerButton();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // empreinte proposée tout de suite quand un compte est déjà enregistré sur ce téléphone
        if (!registerMode && canUseFinger()) handler.postDelayed(this::promptBiometric, 400);
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true); // pas de retour vers l'écran verrouillé
    }

    private void setMode(boolean register) {
        registerMode = register;
        modeTitle.setText(register ? "Créer un compte" : "Connexion");
        main.setText(register ? "Créer mon compte" : "Se connecter");
        pin2.setVisibility(register ? View.VISIBLE : View.GONE);
        switchMode.setText(register ? "J'ai déjà un compte  Se connecter" : "Pas encore de compte ?  Créer un compte");
        message.setText("");
        updateFingerButton();
    }

    private void setMessage(String t, boolean error) {
        message.setTextColor(error ? 0xFFF87171 : 0xFF4ADE80);
        message.setText(t);
    }

    private void setBusy(boolean b) {
        busy = b;
        main.setEnabled(!b);
        main.setAlpha(b ? 0.6f : 1f);
    }

    private void submit() {
        if (busy) return;
        final String e = email.getText().toString().trim().toLowerCase();
        final String p = pin.getText().toString().trim();
        if (e.isEmpty() || p.isEmpty()) {
            setMessage("Saisis ton email et ton PIN.", true);
            return;
        }
        if (registerMode && !p.equals(pin2.getText().toString().trim())) {
            setMessage("Les deux PIN ne sont pas identiques.", true);
            return;
        }
        // session déjà enregistrée sur ce téléphone et PIN correct : on rouvre sans passer par internet
        if (!registerMode && !Prefs.token(this).isEmpty() && Prefs.checkLocalPin(this, e, p)) {
            Prefs.unlocked = true;
            if (canUseFinger()) Prefs.setBio(this, true);
            finish();
            return;
        }
        setBusy(true);
        setMessage("Un instant…", false);
        final boolean register = registerMode;
        new Thread(() -> {
            JSONObject r = Server.call(this, register ? "app_register" : "app_login", "p_email", e, "p_pin", p);
            handler.post(() -> {
                setBusy(false);
                if (!r.optBoolean("ok")) {
                    setMessage(Server.message(r), true);
                    return;
                }
                Prefs.setSession(this, r.optString("token"), e, r.optBoolean("is_admin"));
                Prefs.setLocalPin(this, e, p);
                Server.applyState(this, r);
                Prefs.unlocked = true;
                if (canUseFinger()) Prefs.setBio(this, true); // l'empreinte sera proposée à la prochaine ouverture
                finish();
            });
        }).start();
    }

    // ── empreinte digitale ────────────────────────────────────────────────
    private boolean canUseFinger() {
        if (Build.VERSION.SDK_INT < 28) return false;
        if (Prefs.token(this).isEmpty()) return false;
        try {
            if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) return false;
            if (Build.VERSION.SDK_INT >= 29) {
                BiometricManager bm = (BiometricManager) getSystemService(Context.BIOMETRIC_SERVICE);
                return bm != null && bm.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS;
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void updateFingerButton() {
        finger.setVisibility(!registerMode && canUseFinger() ? View.VISIBLE : View.GONE);
    }

    private void promptBiometric() {
        if (busy || registerMode || !canUseFinger()) return;
        try {
            BiometricPrompt prompt = new BiometricPrompt.Builder(this)
                    .setTitle("Appel Prédictions")
                    .setSubtitle("Pose ton doigt pour te connecter")
                    .setNegativeButton("Utiliser le PIN", getMainExecutor(), (d, w) -> { })
                    .build();
            prompt.authenticate(new CancellationSignal(), getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    fingerOk();
                }

                @Override
                public void onAuthenticationError(int errorCode, CharSequence errString) {
                    // annulé ou indisponible : on reste sur la saisie du PIN
                }
            });
        } catch (Throwable t) {
            setMessage("Empreinte indisponible : utilise ton PIN.", true);
        }
    }

    /** Empreinte reconnue : on réutilise la session enregistrée (vérifiée auprès du serveur). */
    private void fingerOk() {
        setBusy(true);
        setMessage("Vérification…", false);
        new Thread(() -> {
            JSONObject r = Server.refreshState(this);
            handler.post(() -> {
                setBusy(false);
                if (r.optBoolean("ok")) {
                    Prefs.unlocked = true;
                    finish();
                } else if ("session".equals(r.optString("error"))) {
                    setMessage("Session expirée : saisis ton PIN.", true);
                    updateFingerButton();
                } else if (isNetworkError(r.optString("error"))) {
                    Prefs.unlocked = true; // empreinte reconnue, session gardée : on ouvre même sans internet
                    finish();
                } else {
                    setMessage(Server.message(r), true);
                }
            });
        }).start();
    }

    private static boolean isNetworkError(String e) {
        return "reseau".equals(e) || "cle".equals(e) || "service".equals(e) || "reponse".equals(e);
    }
}
