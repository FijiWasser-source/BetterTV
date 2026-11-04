package de.klar.tvlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.DisplayMetrics;

import java.util.List;

/** Schmale, öffentliche Schnittstelle für die Handy-Fernbedienung (Paket remote). */
public final class KlarAccess {
    private KlarAccess() {}

    /** Alle sichtbaren Apps, Dock-Apps zuerst (in Dock-Reihenfolge). */
    public static List<AppEntry> apps(Context c) {
        return AppLoader.load(c, new Prefs(c));
    }

    /** Hintergrundbild der Startansicht in Bildschirmgröße. */
    public static Bitmap wallpaper(Context c) {
        DisplayMetrics m = c.getResources().getDisplayMetrics();
        int w = Math.max(m.widthPixels, m.heightPixels), h = Math.min(m.widthPixels, m.heightPixels);
        return Wallpaper.load(c, w, h).full;
    }

    /** Für "Weiter mit …": merkt sich die zuletzt gestartete App. */
    public static void noteLaunched(Context c, String pkg) {
        new Prefs(c).setLastLaunched(pkg);
    }

    /** Gewählter Stil der App-Symbole (für die Handy-Fernbedienung). */
    public static String iconStyle(Context c) { return new Prefs(c).iconStyle(); }

    /** Bild im gewählten Symbol-Stil (ohne Rundung), z. B. für die Handy-Fernbedienung. */
    public static android.graphics.drawable.Drawable styled(Context c, android.graphics.drawable.Drawable d) {
        String st = iconStyle(c);
        return IconStyle.ORIGINAL.equals(st) || d == null ? d : new RoundedDrawable(d, 0, st);
    }
}
