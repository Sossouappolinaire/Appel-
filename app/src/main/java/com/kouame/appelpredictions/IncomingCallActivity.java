package com.kouame.appelpredictions;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Écran d'appel entrant plein écran : sonne jusqu'à « Décrocher » (ou « Refuser »). */
public class IncomingCallActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView detail;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            if (!CallService.RINGING) { finish(); return; }
            detail.setText(CallService.CALL_TEXT);
            handler.postDelayed(this, 700);
        }
    };

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private Button round(String text, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(18);
        b.setAllCaps(false);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(40));
        b.setBackground(g);
        return b;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (km != null) km.requestDismissKeyguard(this, null);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.parseColor("#0f172a"));
        root.setPadding(dp(24), dp(80), dp(24), dp(60));

        TextView icon = new TextView(this);
        icon.setText("📞");
        icon.setTextSize(64);
        icon.setGravity(Gravity.CENTER);
        root.addView(icon);

        TextView title = new TextView(this);
        title.setText("Prédiction lancée !");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(16), 0, dp(12));
        root.addView(title);

        detail = new TextView(this);
        detail.setText(CallService.CALL_TEXT);
        detail.setTextColor(Color.parseColor("#cbd5e1"));
        detail.setTextSize(22);
        detail.setGravity(Gravity.CENTER);
        root.addView(detail);

        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        Button refuse = round("✖  Refuser", Color.parseColor("#dc2626"));
        Button answer = round("📞  Décrocher", Color.parseColor("#16a34a"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(72), 1f);
        lp.setMargins(dp(8), 0, dp(8), 0);
        buttons.addView(refuse, lp);
        buttons.addView(answer, lp);
        root.addView(buttons, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        answer.setOnClickListener(v -> stopCall(CallService.ACTION_ANSWER));
        refuse.setOnClickListener(v -> stopCall(CallService.ACTION_DECLINE));
        setContentView(root);
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
        handler.removeCallbacks(refresh);
        handler.post(refresh);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refresh);
    }

    @Override
    public void onBackPressed() {
        // on ne ferme pas l'écran d'appel avec « Retour » : il faut décrocher ou refuser
    }
}
