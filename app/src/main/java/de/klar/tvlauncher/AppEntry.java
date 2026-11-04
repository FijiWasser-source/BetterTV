package de.klar.tvlauncher;

import android.content.Intent;
import android.graphics.drawable.Drawable;

final class AppEntry {
    /** Spezialschlüssel für die Einstellungs-Kachel am Ende des Rasters. */
    static final String SETTINGS_KEY = "klar:settings";

    final String key;
    final String label;
    final String packageName;
    final Drawable banner;
    final Drawable icon;
    final Intent launchIntent;
    final boolean pinned;

    AppEntry(String key, String label, String packageName, Drawable banner, Drawable icon,
             Intent launchIntent, boolean pinned) {
        this.key = key;
        this.label = label;
        this.packageName = packageName;
        this.banner = banner;
        this.icon = icon;
        this.launchIntent = launchIntent;
        this.pinned = pinned;
    }

    boolean isSettings() { return SETTINGS_KEY.equals(key); }
}
