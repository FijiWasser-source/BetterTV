package de.bettertv.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

import java.util.List;
import java.util.Set;

/** Gemeinsame Aktionen für eine App (Startansicht und "Alle Apps"). */
final class AppMenu {
    private AppMenu() {}

    static void open(Activity a, AppEntry e) {
        try {
            a.startActivity(e.launchIntent);
            new Prefs(a).setLastLaunched(e.packageName);
        } catch (ActivityNotFoundException | SecurityException ex) {
            Toast.makeText(a, "App lässt sich nicht starten", Toast.LENGTH_SHORT).show();
        }
    }

    /** Menü per langem OK-Druck. {@code changed} lädt danach die Ansicht neu. */
    static void show(Activity a, Prefs prefs, AppEntry e, Runnable changed) {
        Sheet sheet = new Sheet(a, e.label);
        if (e.pinned) {
            sheet.button("Vom Start entfernen", () -> { setPinned(prefs, e.key, false); changed.run(); })
                 .button("Nach links", () -> { move(prefs, e.key, -1); changed.run(); })
                 .button("Nach rechts", () -> { move(prefs, e.key, 1); changed.run(); });
        } else {
            sheet.primary("Zum Start hinzufügen", () -> { setPinned(prefs, e.key, true); changed.run(); });
        }
        sheet.button("Ausblenden", () -> { hide(prefs, e.key); changed.run(); })
             .button("App-Info", () -> tryStart(a, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                     Uri.parse("package:" + e.packageName))))
             .button("Abbrechen", null)
             .show();
    }

    static void setPinned(Prefs prefs, String key, boolean pin) {
        List<String> pinned = prefs.pinned();
        pinned.remove(key);
        if (pin) pinned.add(key);
        prefs.setPinned(pinned);
    }

    static void move(Prefs prefs, String key, int delta) {
        prefs.setPinned(AppOrder.move(prefs.pinned(), key, delta));
    }

    static void hide(Prefs prefs, String key) {
        Set<String> hidden = prefs.hidden();
        hidden.add(key);
        prefs.setHidden(hidden);
        setPinned(prefs, key, false);
    }

    static boolean tryStart(Activity a, Intent intent) {
        try {
            a.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException ex) {
            return false;
        }
    }
}
