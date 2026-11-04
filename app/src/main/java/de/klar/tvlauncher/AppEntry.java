package de.klar.tvlauncher;

import android.content.Intent;
import android.graphics.drawable.Drawable;

/** Eine startbare App. Öffentlich lesbar für die Handy-Fernbedienung. */
public final class AppEntry {
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

    public String packageName() { return packageName; }
    public String label() { return label; }
    public boolean pinned() { return pinned; }
    public boolean hasBanner() { return banner != null; }
    public Drawable bannerDrawable() { return banner; }
    public Drawable iconDrawable() { return icon; }
    public Intent launch() { return launchIntent; }
}
