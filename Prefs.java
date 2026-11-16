package com.kouame.appelpredictions;

import android.content.Context;
import android.content.SharedPreferences;

/** Réglages enregistrés sur le téléphone : base Supabase, session du compte, dernier appel traité, état de l'abonnement. */
final class Prefs {
    // Base de données (projet tv-kouame). La clé publique ne peut rien lire directement : tout passe par des fonctions sécurisées (compte + abonnement).
    static final String DEFAULT_URL = "https://hvjgqaxejqnvgvefjhnj.supabase.co";
    static final String DEFAULT_KEY = "sb_publishable_65fqWs6C2pi_ToCuTxnqZg_V9OyUA3v";
    // Lien de paiement (bouton « Payer »)
    static final String PAY_URL = "https://new.sebpay.bj/pay/liker-Z20B0F";

    /** Vrai seulement après la saisie du PIN (ou de l'empreinte) depuis le lancement de l'application. */
    static volatile boolean unlocked = false;

    private Prefs() { }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("appel", Context.MODE_PRIVATE);
    }

    // ── base ────────────────────────────────────────────────────────────
    static String url(Context c) { return sp(c).getString("url3", DEFAULT_URL); }
    static String key(Context c) { return sp(c).getString("key3", DEFAULT_KEY); }

    static void setConnection(Context c, String url, String key) {
        sp(c).edit().putString("url3", url.trim()).putString("key3", key.trim()).putLong("lastId3", -1).apply();
    }

    // ── session ─────────────────────────────────────────────────────────
    static String token(Context c) { return sp(c).getString("token", ""); }
    static String email(Context c) { return sp(c).getString("email", ""); }
    static boolean isAdmin(Context c) { return sp(c).getBoolean("admin", false); }

    static void setSession(Context c, String token, String email, boolean admin) {
        sp(c).edit().putString("token", token).putString("email", email).putBoolean("admin", admin).putLong("lastId3", -1).apply();
    }

    /** Déconnexion : le jeton est oublié, l'email reste proposé à la prochaine connexion. */
    static void clearSession(Context c) {
        sp(c).edit().remove("token").putBoolean("admin", false).putBoolean("active", false).putLong("lastId3", -1).apply();
        unlocked = false;
    }

    static boolean bio(Context c) { return sp(c).getBoolean("bio", false); }
    static void setBio(Context c, boolean v) { sp(c).edit().putBoolean("bio", v).apply(); }

    // ── abonnement (copie de ce que dit le serveur) ─────────────────────
    static void setAccess(Context c, boolean admin, boolean active, String status, long untilMs, long serverNowMs, boolean pendingProof) {
        sp(c).edit().putBoolean("admin", admin).putBoolean("active", active).putString("st", status)
                .putLong("until", untilMs).putLong("offset", serverNowMs - System.currentTimeMillis())
                .putBoolean("proof", pendingProof).apply();
    }
    static boolean accessActive(Context c) { return sp(c).getBoolean("active", false); }
    static String accessStatus(Context c) { return sp(c).getString("st", ""); }
    static long accessUntil(Context c) { return sp(c).getLong("until", 0); }
    static long serverOffset(Context c) { return sp(c).getLong("offset", 0); }
    static boolean pendingProof(Context c) { return sp(c).getBoolean("proof", false); }

    // ── surveillance ────────────────────────────────────────────────────
    /** Dernier appel déjà traité (-1 = pas encore initialisé : les anciens appels ne sonnent pas). */
    static long lastId(Context c) { return sp(c).getLong("lastId3", -1); }
    static void setLastId(Context c, long id) { sp(c).edit().putLong("lastId3", id).apply(); }

    static boolean running(Context c) { return sp(c).getBoolean("running", false); }
    static void setRunning(Context c, boolean v) { sp(c).edit().putBoolean("running", v).apply(); }

    static String status(Context c) { return sp(c).getString("status", "Arrêté"); }
    static void setStatus(Context c, String s) { sp(c).edit().putString("status", s).apply(); }
}
