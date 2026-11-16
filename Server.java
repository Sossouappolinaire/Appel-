package com.kouame.appelpredictions;

import android.content.Context;

import org.json.JSONObject;

import java.util.Locale;

/** Appels aux fonctions du serveur (comptes, abonnement, appels, administration). Renvoie toujours un objet JSON avec « ok ». */
final class Server {
    private Server() { }

    /** Exemple : Server.call(ctx, "app_state", "p_token", token). Les erreurs réseau donnent {ok:false, error:"reseau", detail:"…"}. */
    static JSONObject call(Context c, String fn, Object... kv) {
        return callTimeout(c, fn, 20000, kv);
    }

    static JSONObject callTimeout(Context c, String fn, int timeoutMs, Object... kv) {
        try {
            JSONObject args = new JSONObject();
            for (int i = 0; i + 1 < kv.length; i += 2) args.put(String.valueOf(kv[i]), kv[i + 1]);
            Api.Result r = Api.rpc(c, fn, args, timeoutMs);
            if (r.error != null) return fail("reseau", r.error);
            if (r.code == 401 || r.code == 403) return fail("cle", "Clé refusée (" + r.code + ")");
            if (r.code == 404) return fail("service", "Service introuvable (la base n'est pas préparée)");
            JSONObject o;
            try {
                o = new JSONObject(r.body);
            } catch (Exception e) {
                String b = r.body.length() > 120 ? r.body.substring(0, 120) : r.body;
                return fail("reponse", "Réponse inattendue (" + r.code + ") " + b);
            }
            return o;
        } catch (Exception e) {
            return fail("reseau", String.valueOf(e.getMessage()));
        }
    }

    private static JSONObject fail(String error, String detail) {
        JSONObject o = new JSONObject();
        try {
            o.put("ok", false);
            o.put("error", error);
            o.put("detail", detail);
        } catch (Exception ignored) { }
        return o;
    }

    /** Range l'état du compte (abonnement, rôle) renvoyé par le serveur. */
    static void applyState(Context c, JSONObject o) {
        if (o == null || !o.optBoolean("ok")) return;
        Prefs.setAccess(c, o.optBoolean("is_admin"), o.optBoolean("active"), o.optString("status"),
                o.optLong("access_until_ms", 0), o.optLong("server_now_ms", System.currentTimeMillis()), o.optBoolean("pending_proof"));
    }

    /** Demande l'état au serveur avec le jeton enregistré. Une session expirée efface le jeton. */
    static JSONObject refreshState(Context c) {
        String token = Prefs.token(c);
        if (token.isEmpty()) return fail("session", "Pas de session");
        JSONObject o = call(c, "app_state", "p_token", token);
        if (o.optBoolean("ok")) applyState(c, o);
        else if ("session".equals(o.optString("error"))) Prefs.clearSession(c);
        return o;
    }

    /** Message lisible pour une erreur renvoyée par le serveur. */
    static String message(JSONObject o) {
        String e = o.optString("error");
        switch (e) {
            case "reseau": return "Pas de connexion : " + o.optString("detail");
            case "identifiants": return "Email ou PIN incorrect.";
            case "verrouille": return "Trop d'essais. Réessaie dans " + Math.max(1, (o.optInt("retry_s", 600) + 59) / 60) + " min.";
            case "email_invalide": return "Email invalide.";
            case "pin_invalide": return "Le PIN doit avoir 4 à 8 chiffres.";
            case "email_existe": return "Cet email a déjà un compte : connecte-toi.";
            case "session": return "Session expirée : reconnecte-toi.";
            case "image_invalide": return "Image trop grande ou illisible.";
            case "trop_de_demandes": return "Tu as déjà envoyé plusieurs captures : attends la réponse de l'administrateur.";
            case "statut": return "Ton compte n'est pas encore validé.";
            case "admin": return "Action réservée à l'administrateur.";
            case "soi_meme": return "Tu ne peux pas te bloquer toi-même.";
            case "cle": case "service": case "reponse": return o.optString("detail");
            default: return "Erreur : " + e + " " + o.optString("detail");
        }
    }

    /** Durée en texte : « 2 j 03 h 12 min ». */
    static String duration(long ms) {
        if (ms <= 0) return "0 min";
        long min = ms / 60000;
        long d = min / 1440, h = (min % 1440) / 60, m = min % 60;
        if (d > 0) return String.format(Locale.FRANCE, "%d j %02d h %02d min", d, h, m);
        if (h > 0) return String.format(Locale.FRANCE, "%d h %02d min", h, m);
        return m + " min";
    }
}
