package de.bettertv.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import java.io.File;

/**
 * Hintergrundbild der Startansicht (Inhaltsebene).
 *
 * Reihenfolge: 1. eigenes Bild auf dem TV (per adb abgelegt, ohne neue APK austauschbar):
 *   /sdcard/Android/data/de.bettertv.launcher/files/hintergrund.jpg (oder .png)
 * 2. das in der App mitgelieferte Bild (res/drawable-nodpi/wallpaper.*)
 * 3. der Midnight-Verlauf.
 */
final class Wallpaper {
    private Wallpaper() {}

    private static Bitmap cached;
    private static Bitmap cachedBlur;
    private static String cachedId;

    static final class Images {
        final Bitmap full;
        final Bitmap blurred;
        Images(Bitmap full, Bitmap blurred) { this.full = full; this.blurred = blurred; }
    }

    static File customFile(Context c) {
        File dir = c.getExternalFilesDir(null);
        if (dir == null) return null;
        for (String name : new String[]{"hintergrund.jpg", "hintergrund.jpeg", "hintergrund.png"}) {
            File f = new File(dir, name);
            if (f.isFile()) return f;
        }
        return new File(dir, "hintergrund.jpg");
    }

    /** Lädt (zwischengespeichert) das Bild in Bildschirmgröße plus weichgezeichnete Vorlage fürs Glas. */
    static synchronized Images load(Context c, int w, int h) {
        File file = customFile(c);
        boolean useFile = file != null && file.isFile();
        String id = (useFile ? file.getAbsolutePath() + file.lastModified() : "bundled") + "@" + w + "x" + h;
        if (cached == null || !id.equals(cachedId)) {
            Bitmap src = useFile ? decodeFile(file.getAbsolutePath(), w, h) : decodeBundled(c, w, h);
            cached = src != null ? centerCrop(src, w, h) : Aurora.render(c, w, h);
            cachedBlur = Aurora.blurred(cached, w, h);
            cachedId = id;
        }
        return new Images(cached, cachedBlur);
    }

    private static Bitmap decodeFile(String path, int w, int h) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        o.inSampleSize = sample(o.outWidth, o.outHeight, w, h);
        o.inJustDecodeBounds = false;
        try { return BitmapFactory.decodeFile(path, o); } catch (OutOfMemoryError e) { return null; }
    }

    @SuppressWarnings("DiscouragedApi") // Bild ist optional; ohne es soll die App trotzdem bauen
    private static Bitmap decodeBundled(Context c, int w, int h) {
        int res = c.getResources().getIdentifier("wallpaper", "drawable", c.getPackageName());
        if (res == 0) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(c.getResources(), res, o);
        o.inSampleSize = sample(o.outWidth, o.outHeight, w, h);
        o.inJustDecodeBounds = false;
        try { return BitmapFactory.decodeResource(c.getResources(), res, o); } catch (OutOfMemoryError e) { return null; }
    }

    private static int sample(int sw, int sh, int w, int h) {
        int s = 1;
        while (sw / (s * 2) >= w && sh / (s * 2) >= h) s *= 2;
        return s;
    }

    /** Füllt den Bildschirm ohne Verzerrung (überstehende Ränder werden abgeschnitten). */
    static Bitmap centerCrop(Bitmap src, int w, int h) {
        if (src.getWidth() == w && src.getHeight() == h) return src;
        float scale = Math.max((float) w / src.getWidth(), (float) h / src.getHeight());
        int cw = Math.round(w / scale), ch = Math.round(h / scale);
        int x = (src.getWidth() - cw) / 2, y = (src.getHeight() - ch) / 2;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        new Canvas(out).drawBitmap(src, new Rect(x, y, x + cw, y + ch), new Rect(0, 0, w, h),
                new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG));
        if (out != src) src.recycle();
        return out;
    }
}
