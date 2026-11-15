package com.kouame.appelpredictions;

import android.content.Context;
import android.content.SharedPreferences;

/** Réglages enregistrés sur le téléphone. */
final class Prefs {
    // Projet Supabase du site (modifiable dans l'application)
    static final String DEFAULT_URL = "https://kmvbsntmzjkpwwqgkqhz.supabase.co";
    // Clé publique « publishable » du projet : elle ne permet que de LIRE la table apk_calls (règle RLS), pas de modifier la base
    static final String DEFAULT_KEY = "sb_publishable_hMKTMYKHMccROZVz5zd_aw_k6JcuSDu";

    private Prefs() { }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("appel", Context.MODE_PRIVATE);
    }

    static String url(Context c) { return sp(c).getString("url", DEFAULT_URL); }
    static String key(Context c) { return sp(c).getString("key", DEFAULT_KEY); }
    static void setConnection(Context c, String url, String key) {
        sp(c).edit().putString("url", url.trim()).putString("key", key.trim()).putLong("lastId", -1).apply();
    }

    /** Dernier appel déjà traité (-1 = pas encore initialisé : les anciens appels ne sonnent pas). */
    static long lastId(Context c) { return sp(c).getLong("lastId", -1); }
    static void setLastId(Context c, long id) { sp(c).edit().putLong("lastId", id).apply(); }

    static boolean running(Context c) { return sp(c).getBoolean("running", false); }
    static void setRunning(Context c, boolean v) { sp(c).edit().putBoolean("running", v).apply(); }

    static String status(Context c) { return sp(c).getString("status", "Arrêté"); }
    static void setStatus(Context c, String s) { sp(c).edit().putString("status", s).apply(); }
}
