package com.kouame.appelpredictions;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Relance la surveillance au redémarrage, après une mise à jour, ou si le système l'a arrêtée (réveil de secours toutes les 5 minutes). */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent == null ? null : intent.getAction();
        boolean ok = Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a) || CallService.ACTION_WATCHDOG.equals(a);
        if (!ok) return;
        if (!Prefs.running(context)) return;
        CallService.scheduleWatchdog(context);
        try {
            context.startForegroundService(new Intent(context, CallService.class));
        } catch (Exception ignored) {
            // démarrage refusé par le système : l'utilisateur relancera depuis l'application
        }
    }
}
