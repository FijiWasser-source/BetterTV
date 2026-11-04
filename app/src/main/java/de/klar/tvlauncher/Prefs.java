package de.klar.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Lokale Einstellungen. Nichts davon verlässt das Gerät. */
final class Prefs {
    private static final String PINNED = "pinned";
    private static final String HIDDEN = "hidden";
    private static final String CITY = "city";
    private static final String LAT = "lat";
    private static final String LON = "lon";
    private static final String SEP = "\n";

    private final SharedPreferences sp;

    Prefs(Context context) {
        sp = context.getSharedPreferences("klar", Context.MODE_PRIVATE);
    }

    List<String> pinned() {
        String raw = sp.getString(PINNED, "");
        List<String> list = new ArrayList<>();
        if (!raw.isEmpty()) list.addAll(Arrays.asList(raw.split(SEP)));
        return list;
    }

    void setPinned(List<String> keys) {
        sp.edit().putString(PINNED, String.join(SEP, keys)).apply();
    }

    Set<String> hidden() {
        return new HashSet<>(sp.getStringSet(HIDDEN, new HashSet<>()));
    }

    void setHidden(Set<String> keys) {
        sp.edit().putStringSet(HIDDEN, new HashSet<>(keys)).apply();
    }

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
