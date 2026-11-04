package de.klar.tvlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * Ersatz-Hintergrund "Midnight" (Inhaltsebene, falls kein eigenes Bild abgelegt ist) und Hilfen für das Glas:
 * Rendern, Bildschirmfoto, Weichzeichnen. Alles läuft einmalig, nicht pro Bild –
 * wichtig für ältere Fernseher wie den BRAVIA von 2016.
 */
final class Aurora {
    private Aurora() {}

    /** Kantenlänge der weichgezeichneten Vorlage im Verhältnis zum Bildschirm. */
    static final int BLUR_DOWNSCALE = 12;

    /** Rendert den Hintergrund in halber Auflösung (weiche Verläufe brauchen nicht mehr). */
    static Bitmap render(Context c, int width, int height) {
        int w = Math.max(1, width / 2), h = Math.max(1, height / 2);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(color(c, R.color.bg));
        blob(canvas, w * 0.75f, h * 0.15f, w * 0.60f, color(c, R.color.midnight_glow), 0.55f);
        blob(canvas, w * 0.15f, h * 0.95f, w * 0.65f, color(c, R.color.midnight), 0.60f);
        blob(canvas, w * 0.50f, h * 0.45f, w * 0.50f, color(c, R.color.midnight_deep), 0.40f);
        return bmp;
    }

    private static void blob(Canvas canvas, float x, float y, float r, int color, float strength) {
        int a = Math.round(255 * strength);
        int center = (a << 24) | (color & 0xFFFFFF);
        int mid = ((a / 3) << 24) | (color & 0xFFFFFF);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setShader(new RadialGradient(x, y, r, new int[]{center, mid, color & 0xFFFFFF},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, r, p);
    }

    /** Verkleinertes Bildschirmfoto einer View (für Menüs, die über Inhalt schweben). */
    static Bitmap snapshot(View root) {
        int w = Math.max(1, root.getWidth() / BLUR_DOWNSCALE), h = Math.max(1, root.getHeight() / BLUR_DOWNSCALE);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.scale(1f / BLUR_DOWNSCALE, 1f / BLUR_DOWNSCALE);
        root.draw(c);
        boxBlur(bmp, 2, 3);
        return bmp;
    }

    /** Weichgezeichnete Vorlage eines (Hintergrund-)Bildes in Bildschirm-Proportionen. */
    static Bitmap blurred(Bitmap src, int screenW, int screenH) {
        int w = Math.max(1, screenW / BLUR_DOWNSCALE), h = Math.max(1, screenH / BLUR_DOWNSCALE);
        Bitmap small = Bitmap.createScaledBitmap(src, w, h, true);
        if (small == src) small = src.copy(Bitmap.Config.ARGB_8888, true);
        else if (!small.isMutable()) small = small.copy(Bitmap.Config.ARGB_8888, true);
        boxBlur(small, 2, 3);
        return small;
    }

    /** Mehrfacher Box-Blur (nähert eine Gauß-Unschärfe an), direkt auf dem Bitmap. */
    static void boxBlur(Bitmap bmp, int radius, int passes) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] a = new int[w * h], b = new int[w * h];
        bmp.getPixels(a, 0, w, 0, 0, w, h);
        for (int i = 0; i < passes; i++) {
            pass(a, b, w, h, radius, true);
            pass(b, a, w, h, radius, false);
        }
        bmp.setPixels(a, 0, w, 0, 0, w, h);
    }

    private static void pass(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int lines = horizontal ? h : w, len = horizontal ? w : h;
        for (int line = 0; line < lines; line++) {
            for (int i = 0; i < len; i++) {
                int sa = 0, sr = 0, sg = 0, sb = 0, n = 0;
                for (int k = -r; k <= r; k++) {
                    int j = Math.max(0, Math.min(len - 1, i + k));
                    int px = horizontal ? in[line * w + j] : in[j * w + line];
                    sa += px >>> 24; sr += (px >> 16) & 0xFF; sg += (px >> 8) & 0xFF; sb += px & 0xFF; n++;
                }
                int v = ((sa / n) << 24) | ((sr / n) << 16) | ((sg / n) << 8) | (sb / n);
                if (horizontal) out[line * w + i] = v; else out[i * w + line] = v;
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static int color(Context c, int id) { return c.getResources().getColor(id); }
}
