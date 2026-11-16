package com.kouame.appelpredictions;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.List;

/** Petites aides de design (dégradés, boutons colorés, anneaux qui pulsent, barres de progression). */
final class UI {
    private UI() { }

    static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** Forme pleine arrondie. */
    static GradientDrawable shape(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    /** Dégradé arrondi (couleur 1 → couleur 2). */
    static GradientDrawable gradient(Context c, int c1, int c2, GradientDrawable.Orientation o, float radiusDp) {
        GradientDrawable g = new GradientDrawable(o, new int[]{c1, c2});
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    /** Fond d'écran en dégradé vertical à plusieurs couleurs. */
    static GradientDrawable background(int... colors) {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors);
    }

    /** Bouton rectangulaire arrondi en dégradé, avec effet d'onde au toucher. */
    static Drawable rippleButton(Context c, int c1, int c2, float radiusDp) {
        GradientDrawable content = gradient(c, c1, c2, GradientDrawable.Orientation.LEFT_RIGHT, radiusDp);
        GradientDrawable mask = shape(c, Color.WHITE, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(0x66FFFFFF), content, mask);
    }

    /** Bouton rond en dégradé, avec effet d'onde au toucher. */
    static Drawable rippleCircle(int c1, int c2) {
        GradientDrawable content = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
        content.setShape(GradientDrawable.OVAL);
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.WHITE);
        return new RippleDrawable(ColorStateList.valueOf(0x66FFFFFF), content, mask);
    }

    /** Disque en dégradé. */
    static GradientDrawable disc(int c1, int c2) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
        g.setShape(GradientDrawable.OVAL);
        return g;
    }

    /** Effet « appui » : la vue rétrécit un peu quand on la touche. */
    static void pressEffect(View v) {
        v.setOnTouchListener((view, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90).start();
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                view.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
            }
            return false;
        });
    }

    /** Apparition en fondu + glissement vers le haut, l'une après l'autre. */
    static void enter(View v, int index) {
        v.setAlpha(0f);
        v.setTranslationY(dp(v.getContext(), 28));
        v.animate().alpha(1f).translationY(0f).setStartDelay(index * 90L).setDuration(450)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    /** Anneau (cercle vide) qui servira d'onde. */
    static View ring(Context c, int sizeDp, int color, float strokeDp) {
        View v = new View(c);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Color.TRANSPARENT);
        g.setStroke(dp(c, strokeDp), color);
        v.setBackground(g);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp), Gravity.CENTER);
        v.setLayoutParams(lp);
        v.setAlpha(0f);
        return v;
    }

    /** Onde qui grandit et s'estompe en boucle. L'animateur est ajouté à la liste pour pouvoir l'arrêter. */
    static void pulse(final View ring, long delayMs, float maxScale, List<ValueAnimator> keep) {
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(2200);
        va.setStartDelay(delayMs);
        va.setRepeatCount(ValueAnimator.INFINITE);
        va.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            float s = 1f + f * (maxScale - 1f);
            ring.setScaleX(s);
            ring.setScaleY(s);
            ring.setAlpha(0.75f * (1f - f));
        });
        keep.add(va);
        va.start();
    }

    /** Respiration (la vue grossit / rétrécit doucement) en boucle. */
    static void breathe(final View v, float to, long durationMs, List<ValueAnimator> keep) {
        ValueAnimator va = ValueAnimator.ofFloat(1f, to);
        va.setDuration(durationMs);
        va.setRepeatCount(ValueAnimator.INFINITE);
        va.setRepeatMode(ValueAnimator.REVERSE);
        va.addUpdateListener(a -> {
            float s = (float) a.getAnimatedValue();
            v.setScaleX(s);
            v.setScaleY(s);
        });
        keep.add(va);
        va.start();
    }

    /** Balancement (le téléphone qui vibre) en boucle. */
    static void shake(final View v, float degrees, List<ValueAnimator> keep) {
        ValueAnimator va = ValueAnimator.ofFloat(-degrees, degrees);
        va.setDuration(130);
        va.setRepeatCount(ValueAnimator.INFINITE);
        va.setRepeatMode(ValueAnimator.REVERSE);
        va.addUpdateListener(a -> v.setRotation((float) a.getAnimatedValue()));
        keep.add(va);
        va.start();
    }

    static void stopAll(List<ValueAnimator> list) {
        for (ValueAnimator a : list) a.cancel();
        list.clear();
    }

    /** Barre de progression arrondie en dégradé (valeur de 0 à 100). */
    static ProgressBar bar(Context c, int c1, int c2) {
        ProgressBar p = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        p.setMax(100);
        GradientDrawable track = shape(c, 0x33FFFFFF, 8);
        GradientDrawable fill = gradient(c, c1, c2, GradientDrawable.Orientation.LEFT_RIGHT, 8);
        ClipDrawable clip = new ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL);
        LayerDrawable layers = new LayerDrawable(new Drawable[]{track, clip});
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);
        p.setProgressDrawable(layers);
        return p;
    }

    /** Barre qui tourne sans fin (activité en cours). */
    static ProgressBar activityBar(Context c, int color) {
        ProgressBar p = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        p.setIndeterminate(true);
        p.setIndeterminateTintList(ColorStateList.valueOf(color));
        return p;
    }

    static TextView text(Context c, String t, int sp, int color, boolean bold) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    /** Carte sombre arrondie. */
    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = shape(c, 0xFF1E293B, 20);
        g.setStroke(dp(c, 1), 0xFF334155);
        l.setBackground(g);
        l.setPadding(dp(c, 18), dp(c, 16), dp(c, 18), dp(c, 18));
        l.setElevation(dp(c, 6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 16);
        l.setLayoutParams(lp);
        return l;
    }

    /** Bouton en dégradé avec effet d'onde et d'appui. */
    static Button button(Context c, String t, int c1, int c2) {
        Button b = new Button(c);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(rippleButton(c, c1, c2, 16));
        b.setStateListAnimator(null);
        b.setElevation(dp(c, 6));
        b.setPadding(dp(c, 12), 0, dp(c, 12), 0);
        pressEffect(b);
        return b;
    }

    /** Champ de saisie sombre arrondi. */
    static android.widget.EditText field(Context c, String hint, int inputType) {
        final android.widget.EditText e = new android.widget.EditText(c);
        final GradientDrawable g = shape(c, 0xFF0F172A, 12);
        g.setStroke(dp(c, 1), 0xFF334155);
        e.setBackground(g);
        e.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(0xFF64748B);
        e.setTextSize(16);
        e.setHint(hint);
        e.setInputType(inputType);
        e.setSingleLine(true);
        e.setOnFocusChangeListener((v, focused) -> g.setStroke(dp(c, focused ? 2 : 1), focused ? 0xFF38BDF8 : 0xFF334155));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 10);
        e.setLayoutParams(lp);
        return e;
    }
}
