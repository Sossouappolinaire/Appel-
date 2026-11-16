package com.kouame.appelpredictions;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Appels à la base Supabase (API REST « PostgREST »). Tout passe par des fonctions sécurisées (RPC) : la clé publique ne lit aucune table. */
final class Api {
    private Api() { }

    static final class Result {
        int code;
        String body = "";
        String error;
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[8192];
        int n;
        while ((n = br.read(buf)) > 0) sb.append(buf, 0, n);
        br.close();
        return sb.toString();
    }

    /** Appel d'une fonction de la base : POST /rest/v1/rpc/{nom} avec un corps JSON. */
    static Result post(String baseUrl, String key, String path, String json, int readTimeoutMs) {
        Result r = new Result();
        HttpURLConnection c = null;
        try {
            String b = baseUrl.trim();
            while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
            c = (HttpURLConnection) new URL(b + path).openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(10000);
            c.setReadTimeout(readTimeoutMs);
            c.setDoOutput(true);
            c.setRequestProperty("apikey", key);
            if (key.startsWith("eyJ")) c.setRequestProperty("Authorization", "Bearer " + key);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Accept", "application/json");
            byte[] data = json.getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(data.length);
            OutputStream os = c.getOutputStream();
            os.write(data);
            os.close();
            r.code = c.getResponseCode();
            r.body = readAll(r.code >= 400 ? c.getErrorStream() : c.getInputStream());
        } catch (Exception e) {
            r.error = e.getClass().getSimpleName() + (e.getMessage() != null ? " : " + e.getMessage() : "");
        } finally {
            if (c != null) c.disconnect();
        }
        return r;
    }

    static Result rpc(Context ctx, String fn, JSONObject args, int readTimeoutMs) {
        return post(Prefs.url(ctx), Prefs.key(ctx), "/rest/v1/rpc/" + fn, args == null ? "{}" : args.toString(), readTimeoutMs);
    }
}
