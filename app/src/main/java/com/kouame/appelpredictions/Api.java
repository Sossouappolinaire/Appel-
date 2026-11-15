package com.kouame.appelpredictions;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Lecture de la base Supabase (API REST « PostgREST », clé publique anon / publishable, lecture seule). */
final class Api {
    private Api() { }

    static final class Result {
        int code;
        String body = "";
        String error;
    }

    static Result get(String baseUrl, String key, String path) {
        Result r = new Result();
        HttpURLConnection c = null;
        try {
            String b = baseUrl.trim();
            while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
            c = (HttpURLConnection) new URL(b + path).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setRequestProperty("apikey", key);
            if (key.startsWith("eyJ")) c.setRequestProperty("Authorization", "Bearer " + key);
            c.setRequestProperty("Accept", "application/json");
            r.code = c.getResponseCode();
            InputStream in = r.code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in != null) {
                BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                r.body = sb.toString();
            }
        } catch (Exception e) {
            r.error = e.getClass().getSimpleName() + (e.getMessage() != null ? " : " + e.getMessage() : "");
        } finally {
            if (c != null) c.disconnect();
        }
        return r;
    }

    /** Message lisible si la réponse n'est pas bonne, sinon null. */
    static String problem(Result r) {
        if (r.error != null) return "Pas de connexion : " + r.error;
        if (r.code == 401 || r.code == 403) return "Clé refusée (" + r.code + ") : vérifie la clé publique (anon / publishable).";
        if (r.code == 404) return "Table « apk_calls » introuvable : sur le site, ouvre « Appel APK », active-le et vérifie que la table est prête.";
        if (r.code >= 300) {
            String b = r.body.length() > 120 ? r.body.substring(0, 120) : r.body;
            return "Erreur " + r.code + " : " + b;
        }
        return null;
    }
}
