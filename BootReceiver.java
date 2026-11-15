package com.kouame.appelpredictions;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Relance la surveillance au redémarrage du téléphone (si elle était démarrée). */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (!Prefs.running(context)) return;
        try {
            context.startForegroundService(new Intent(context, CallService.class));
        } catch (Exception ignored) {
            // démarrage refusé par le système : l'utilisateur relancera depuis l'application
        }
    }
}
