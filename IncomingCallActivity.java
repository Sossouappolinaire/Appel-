package com.kouame.appelpredictions;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Écran d'appel entrant plein écran : sonne jusqu'à « Décrocher » (ou « Refuser »). */
public class IncomingCallActivity extends Activity {
    private static final int GREEN = 0xFF22C55E;
    private static final int RED = 0xFFEF4444;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<ValueAnimator> animators = new ArrayList<>();
    private TextView detail;
    private TextView timer;
    private View ring1;
    private View ring2;
    private View ring3;
    private TextView phone;
    private Button answer;
    private long startedAt;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            if (!CallService.RINGING) { finish(); return; }
            detail.setText(CallService.CALL_TEXT);
            long s = (System.currentTimeMillis() - startedAt) / 1000;
            timer.setText(String.format(java.util.Locale.FRANCE, "Sonne depuis %02d:%02d", s / 60, s % 60));
            handler.postDelayed(this, 700);
        }
    };

    private int dp(int v) { return UI.dp(this, v); }

    private TextView text(String t, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private Button round(String label, int c1, int c2) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(30);
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(UI.rippleCircle(c1, c2));
        b.setStateListAnimator(null);
        b.setElevation(dp(10));
        UI.pressEffect(b);
        return b;
    }

    /** Un bouton rond avec son libellé dessous. */
    private LinearLayout column(Button b, String label, int labelColor) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.addView(b, new LinearLayout.LayoutParams(dp(78), dp(78)));
        TextView t = text(label, 15, labelColor, true);
        t.setPadding(0, dp(10), 0, 0);
        col.addView(t);
        return col;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startedAt = System.currentTimeMillis();
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(0xFF0F172A);
        getWindow().setNavigationBarColor(0xFF312E81);
        // pas de demande de déverrouillage : l'écran d'appel s'affiche par-dessus l'écran de verrouillage

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackground(UI.background(0xFF0F172A, 0xFF1E1B4B, 0xFF312E81));
        root.setPadding(dp(24), dp(56), dp(24), dp(48));

        TextView top = text("APPEL ENTRANT", 13, 0xFF94A3B8, true);
        top.setLetterSpacing(0.25f);
        root.addView(top);

        // Icône du téléphone avec ondes qui pulsent
        FrameLayout hero = new FrameLayout(this);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(250));
        hlp.topMargin = dp(16);
        root.addView(hero, hlp);
        ring1 = UI.ring(this, 112, GREEN, 3);
        ring2 = UI.ring(this, 112, GREEN, 3);
        ring3 = UI.ring(this, 112, 0xFF22D3EE, 2);
        phone = new TextView(this);
        phone.setText("📞");
        phone.setTextSize(48);
        phone.setGravity(Gravity.CENTER);
        phone.setBackground(UI.disc(GREEN, 0xFF06B6D4));
        phone.setElevation(dp(12));
        hero.addView(ring1);
        hero.addView(ring2);
        hero.addView(ring3);
        hero.addView(phone, new FrameLayout.LayoutParams(dp(112), dp(112), Gravity.CENTER));

        TextView title = text("Prédiction lancée !", 30, Color.WHITE, true);
        root.addView(title);

        // Carte avec le détail de la prédiction
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(UI.shape(this, 0x26FFFFFF, 20));
        card.setPadding(dp(20), dp(16), dp(20), dp(16));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(18);
        detail = text(CallService.CALL_TEXT, 20, 0xFFE2E8F0, false);
        card.addView(detail);
        root.addView(card, clp);

        timer = text("Sonne depuis 00:00", 14, 0xFF94A3B8, false);
        timer.setPadding(0, dp(14), 0, dp(10));
        root.addView(timer);

        ProgressBar ringing = UI.activityBar(this, GREEN);
        root.addView(ringing, new LinearLayout.LayoutParams(dp(180), dp(4)));

        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        Button refuse = round("✖", 0xFFEF4444, 0xFFB91C1C);
        answer = round("📞", 0xFF22C55E, 0xFF15803D);
        LinearLayout.LayoutParams colp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        buttons.addView(column(refuse, "Refuser", 0xFFFCA5A5), colp);
        buttons.addView(column(answer, "Décrocher", 0xFF86EFAC), colp);
        root.addView(buttons, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        answer.setOnClickListener(v -> stopCall(CallService.ACTION_ANSWER));
        refuse.setOnClickListener(v -> stopCall(CallService.ACTION_DECLINE));
        setContentView(root);
    }

    private void startAnimations() {
        UI.stopAll(animators);
        UI.pulse(ring1, 0, 2.0f, animators);
        UI.pulse(ring2, 700, 2.0f, animators);
        UI.pulse(ring3, 1400, 2.0f, animators);
        UI.shake(phone, 14f, animators);
        UI.breathe(answer, 1.12f, 600, animators);
    }

    private void stopCall(String action) {
        try {
            startService(new Intent(this, CallService.class).setAction(action));
        } catch (Exception ignored) {
            // le service n'est plus là : rien à arrêter
        }
        CallService.RINGING = false;
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startAnimations();
        handler.removeCallbacks(refresh);
        handler.post(refresh);
    }

    @Override
    protected void onPause() {
        super.onPause();
        UI.stopAll(animators);
        handler.removeCallbacks(refresh);
    }

    @Override
    public void onBackPressed() {
        // on ne ferme pas l'écran d'appel avec « Retour » : il faut décrocher ou refuser
    }
}
