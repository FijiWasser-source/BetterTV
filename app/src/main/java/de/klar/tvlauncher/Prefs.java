package de.klar.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Lokale Einstellungen. Nichts davon verlässt das Gerät. */
public final class Prefs {
    private static final String PINNED = "pinned";
    private static final String HIDDEN = "hidden";
    private static final String CITY = "city";
    private static final String LAT = "lat";
    private static final String LON = "lon";
    private static final String SEP = "\n";

    private final SharedPreferences sp;

    public Prefs(Context context) {
        sp = context.getSharedPreferences("klar", Context.MODE_PRIVATE);
    }

    List<String> pinned() {
        String raw = sp.getString(PINNED, "");
        List<String> list = new ArrayList<>();
        if (!raw.isEmpty()) list.addAll(Arrays.asList(raw.split(SEP)));
        return list;
    }

    void setPinned(List<String> keys) {
        sp.edit().putString(PINNED, TextUtils.join(SEP, keys)).apply();
    }

    Set<String> hidden() {
        return new HashSet<>(sp.getStringSet(HIDDEN, new HashSet<>()));
    }

    void setHidden(Set<String> keys) {
        sp.edit().putStringSet(HIDDEN, new HashSet<>(keys)).apply();
    }

    /** true nur beim allerersten Aufruf. */
    boolean firstRun() {
        if (sp.getBoolean("seen_hint", false)) return false;
        sp.edit().putBoolean("seen_hint", true).apply();
        return true;
    }

    Set<String> knownDefaults() {
        return new HashSet<>(sp.getStringSet("known_defaults", new HashSet<>()));
    }

    void setKnownDefaults(Set<String> keys) {
        sp.edit().putStringSet("known_defaults", new HashSet<>(keys)).apply();
    }

    /** "abend" (Begrüßung, Pixel-Uhr, Kacheln) oder "glas" (Hintergrundbild, Glas-Dock). */
    String design() { return sp.getString("design", "abend"); }

    void setDesign(String d) { sp.edit().putString("design", d).apply(); }

    String lastLaunched() { return sp.getString("last_launched", ""); }

    void setLastLaunched(String pkg) { sp.edit().putString("last_launched", pkg).apply(); }

    String iconStyle() { return sp.getString("icon_style", IconStyle.ORIGINAL); }

    void setIconStyle(String s) { sp.edit().putString("icon_style", s).apply(); }

    boolean soundOn() { return sp.getBoolean("sound_on", true); }

    void setSoundOn(boolean on) { sp.edit().putBoolean("sound_on", on).apply(); }

    /** "voll", "sparsam" oder "aus". */
    String animations() { return sp.getString("animations", "voll"); }

    void setAnimations(String a) { sp.edit().putString("animations", a).apply(); }

    /** "echt", "regen", "schnee", "sonne" oder "aus". */
    String gameWeather() { return sp.getString("game_weather", "echt"); }

    void setGameWeather(String w) { sp.edit().putString("game_weather", w).apply(); }

    /** Geheime iCal-Adresse des Kalenders ("" = keiner). */
    public String calendarUrl() { return sp.getString("calendar_url", ""); }

    public void setCalendarUrl(String url) { sp.edit().putString("calendar_url", url).apply(); }

    /** Tagesübersicht: "morgens" (5–11 Uhr), "immer" oder "aus". */
    String overview() { return sp.getString("overview", "morgens"); }

    void setOverview(String o) { sp.edit().putString("overview", o).apply(); }

    SharedPreferences raw() { return sp; }

    String city() { return sp.getString(CITY, ""); }

    boolean hasLocation() { return sp.contains(LAT) && sp.contains(LON); }

    double lat() { return Double.longBitsToDouble(sp.getLong(LAT, 0)); }

    double lon() { return Double.longBitsToDouble(sp.getLong(LON, 0)); }

    void setLocation(String city, double lat, double lon) {
        sp.edit().putString(CITY, city)
                .putLong(LAT, Double.doubleToRawLongBits(lat))
                .putLong(LON, Double.doubleToRawLongBits(lon))
                .apply();
    }

    void clearLocation() {
        sp.edit().remove(CITY).remove(LAT).remove(LON).apply();
    }
}
