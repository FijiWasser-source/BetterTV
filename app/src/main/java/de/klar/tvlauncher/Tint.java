package de.klar.tvlauncher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

/** Ermittelt die Grundfarbe eines App-Bildes – für Kachelflächen und den Hintergrund. */
final class Tint {
    private Tint() {}

    private static final int SIZE = 12;

    static int average(Drawable d, int fallback) {
        if (d == null) return fallback;
        try {
            Bitmap bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            Drawable copy = d.getConstantState() != null ? d.getConstantState().newDrawable().mutate() : d;
            copy.setBounds(0, 0, SIZE, SIZE);
            copy.draw(c);
            long r = 0, g = 0, b = 0, n = 0;
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    int px = bmp.getPixel(x, y);
                    if (Color.alpha(px) < 128) continue;
                    r += Color.red(px); g += Color.green(px); b += Color.blue(px); n++;
                }
            }
            bmp.recycle();
            if (n == 0) return fallback;
            return Color.rgb((int) (r / n), (int) (g / n), (int) (b / n));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** Mischt eine Farbe mit Schwarz (0 = unverändert, 1 = schwarz). */
    static int darken(int color, float amount) {
        float k = 1f - amount;
        return Color.rgb((int) (Color.red(color) * k), (int) (Color.green(color) * k), (int) (Color.blue(color) * k));
    }

    /** Mischt {@code b} zu Anteil {@code t} in {@code a}. */
    static int mix(int a, int b, float t) {
        return Color.rgb(
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }
}
