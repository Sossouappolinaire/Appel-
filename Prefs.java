package com.kouame.appelpredictions;

import android.content.Context;
import android.content.SharedPreferences;

/** Réglages enregistrés sur le téléphone : base principale (0) et base de secours (1). */
final class Prefs {
    // Base principale (modifiable dans Paramètres)
    static final String DEFAULT_URL = "https://kmvbsntmzjkpwwqgkqhz.supabase.co";
    // Clé publique « publishable » : elle ne permet que de LIRE la table apk_calls (règle RLS), pas de modifier la base
    static final String DEFAULT_KEY = "sb_publishable_hMKTMYKHMccROZVz5zd_aw_k6JcuSDu";
    // Base de secours (projet tv-kouame)
    static final String BACKUP_URL = "https://hvjgqaxejqnvgvefjhnj.supabase.co";
    static final String BACKUP_KEY = "sb_publishable_65fqWs6C2pi_ToCuTxnqZg_V9OyUA3v";

    private Prefs() { }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("appel", Context.MODE_PRIVATE);
    }

    static String url(Context c, int which) {
        return sp(c).getString(which == 0 ? "url" : "url2", which == 0 ? DEFAULT_URL : BACKUP_URL);
    }

    static String key(Context c, int which) {
        return sp(c).getString(which == 0 ? "key" : "key2", which == 0 ? DEFAULT_KEY : BACKUP_KEY);
    }

    static boolean hasBase(Context c, int which) {
        return !url(c, which).trim().isEmpty() && !key(c, which).trim().isEmpty();
    }

    static void setConnection(Context c, int which, String url, String key) {
        sp(c).edit()
                .putString(which == 0 ? "url" : "url2", url.trim())
                .putString(which == 0 ? "key" : "key2", key.trim())
                .putLong(which == 0 ? "lastId" : "lastId2", -1)
                .apply();
    }

    /** Dernier appel déjà traité sur cette base (-1 = pas encore initialisé : les anciens appels ne sonnent pas). */
    static long lastId(Context c, int which) { return sp(c).getLong(which == 0 ? "lastId" : "lastId2", -1); }
    static void setLastId(Context c, int which, long id) { sp(c).edit().putLong(which == 0 ? "lastId" : "lastId2", id).apply(); }

    /** Base actuellement utilisée : 0 = principale, 1 = secours. */
    static int active(Context c) { return sp(c).getInt("active", 0); }
    static void setActive(Context c, int which) { sp(c).edit().putInt("active", which).apply(); }

    static boolean running(Context c) { return sp(c).getBoolean("running", false); }
    static void setRunning(Context c, boolean v) { sp(c).edit().putBoolean("running", v).apply(); }

    static String status(Context c) { return sp(c).getString("status", "Arrêté"); }
    static void setStatus(Context c, String s) { sp(c).edit().putString("status", s).apply(); }
}
