package com.kouame.appelpredictions;

import android.content.Context;
import android.content.SharedPreferences;

/** Réglages enregistrés sur le téléphone : base Supabase, session du compte, dernier appel traité, état de l'abonnement. */
final class Prefs {
    // Base de données (projet tv-kouame). La clé publique ne peut rien lire directement : tout passe par des fonctions sécurisées (compte + abonnement + canaux).
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
    // clés « url4 / key4 » : les installations enregistrées pendant les essais sur l'autre projet repartent sur tv-kouame
    static String url(Context c) { return sp(c).getString("url4", DEFAULT_URL); }
    static String key(Context c) { return sp(c).getString("key4", DEFAULT_KEY); }

    static void setConnection(Context c, String url, String key) {
        sp(c).edit().putString("url4", url.trim()).putString("key4", key.trim()).putLong("lastId3", -1).apply();
    }

    // ── session ─────────────────────────────────────────────────────────
    static String token(Context c) { return sp(c).getString("token", ""); }
    static String email(Context c) { return sp(c).getString("email", ""); }
    static boolean isAdmin(Context c) { return sp(c).getBoolean("admin", false); }

    static void setSession(Context c, String token, String email, boolean admin) {
        sp(c).edit().putString("token", token).putString("email", email).putBoolean("admin", admin).putLong("lastId3", -1).apply();
    }

    // ── PIN vérifié sur le téléphone (haché, jamais en clair) : permet de rouvrir l'application sans internet tant que la session existe ──
    private static byte[] pbkdf2(String pin, byte[] salt) throws Exception {
        javax.crypto.SecretKeyFactory f = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return f.generateSecret(new javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, 60000, 256)).getEncoded();
    }

    static void setLocalPin(Context c, String email, String pin) {
        try {
            byte[] salt = new byte[16];
            new java.security.SecureRandom().nextBytes(salt);
            sp(c).edit().putString("pinEmail", email.trim().toLowerCase())
                    .putString("pinSalt", android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
                    .putString("pinHash", android.util.Base64.encodeToString(pbkdf2(pin, salt), android.util.Base64.NO_WRAP)).apply();
        } catch (Exception ignored) { }
    }

    static boolean checkLocalPin(Context c, String email, String pin) {
        try {
            String h = sp(c).getString("pinHash", "");
            String s = sp(c).getString("pinSalt", "");
            if (h.isEmpty() || s.isEmpty() || !email.trim().toLowerCase().equals(sp(c).getString("pinEmail", ""))) return false;
            byte[] calc = pbkdf2(pin, android.util.Base64.decode(s, android.util.Base64.NO_WRAP));
            return java.security.MessageDigest.isEqual(calc, android.util.Base64.decode(h, android.util.Base64.NO_WRAP));
        } catch (Exception e) {
            return false;
        }
    }

    /** Déconnexion : le jeton est oublié, l'email reste proposé à la prochaine connexion. */
    static void clearSession(Context c) {
        sp(c).edit().remove("pinHash").remove("pinSalt").remove("pinEmail").remove("token").putBoolean("admin", false).putBoolean("active", false).remove("chans").putLong("lastId3", -1).apply();
        unlocked = false;
        Server.showDetail = false;
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

    // ── canaux accordés par l'administrateur (noms seulement, un par ligne) ──
    static void setChannels(Context c, String joined) { sp(c).edit().putString("chans", joined).apply(); }
    static String[] channels(Context c) {
        String s = sp(c).getString("chans", "");
        return s.isEmpty() ? new String[0] : s.split("\n");
    }

    // ── surveillance ────────────────────────────────────────────────────
    /** Dernier appel déjà traité (-1 = pas encore initialisé : les anciens appels ne sonnent pas). */
    static long lastId(Context c) { return sp(c).getLong("lastId3", -1); }
    static void setLastId(Context c, long id) { sp(c).edit().putLong("lastId3", id).apply(); }

    static boolean running(Context c) { return sp(c).getBoolean("running", false); }
    static void setRunning(Context c, boolean v) { sp(c).edit().putBoolean("running", v).apply(); }

    static String status(Context c) { return sp(c).getString("status", "Arrêté"); }
    static void setStatus(Context c, String s) { sp(c).edit().putString("status", s).apply(); }
}
