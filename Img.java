package com.kouame.appelpredictions;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Prépare la capture d'écran : réduite et compressée en JPEG, puis encodée en texte (base64) pour l'envoi. */
final class Img {
    private Img() { }

    static String toJpegBase64(Context c, Uri uri, int maxSide) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        InputStream in = c.getContentResolver().openInputStream(uri);
        if (in == null) throw new Exception("Image illisible");
        BitmapFactory.decodeStream(in, null, bounds);
        in.close();
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        in = c.getContentResolver().openInputStream(uri);
        if (in == null) throw new Exception("Image illisible");
        Bitmap bmp = BitmapFactory.decodeStream(in, null, opts);
        in.close();
        if (bmp == null) throw new Exception("Image illisible");
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        float scale = Math.min(1f, (float) maxSide / Math.max(w, h));
        if (scale < 1f) bmp = Bitmap.createScaledBitmap(bmp, Math.round(w * scale), Math.round(h * scale), true);
        int quality = 75;
        byte[] bytes;
        do {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, bo);
            bytes = bo.toByteArray();
            quality -= 10;
        } while (bytes.length > 1500000 && quality > 25);
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }
}
