package de.klar.tvlauncher;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reine Sortier-Logik (ohne Android-Abhängigkeiten, damit sie testbar ist). */
final class AppOrder {
    private AppOrder() {}

    /**
     * Angeheftete Apps zuerst (in der gespeicherten Reihenfolge), danach alle übrigen
     * alphabetisch. Ausgeblendete Apps fallen weg; angeheftete, aber deinstallierte Apps
     * werden ignoriert.
     *
     * @param labels Schlüssel → angezeigter Name aller installierten Apps
     */
    static List<String> order(Map<String, String> labels, List<String> pinned, Collection<String> hidden) {
        List<String> result = new ArrayList<>();
        for (String key : pinned) {
            if (labels.containsKey(key) && !hidden.contains(key) && !result.contains(key)) {
                result.add(key);
            }
        }
        List<String> rest = new ArrayList<>();
        for (String key : labels.keySet()) {
            if (!hidden.contains(key) && !result.contains(key)) rest.add(key);
        }
        Collator collator = Collator.getInstance(Locale.GERMAN);
        rest.sort((a, b) -> collator.compare(labels.get(a), labels.get(b)));
        result.addAll(rest);
        return result;
    }

    /** Verschiebt einen angehefteten Eintrag um {@code delta} Plätze (begrenzt auf die Liste). */
    static List<String> move(List<String> pinned, String key, int delta) {
        List<String> copy = new ArrayList<>(pinned);
        int from = copy.indexOf(key);
        if (from < 0) return copy;
        int to = Math.max(0, Math.min(copy.size() - 1, from + delta));
        copy.remove(from);
        copy.add(to, key);
        return copy;
    }
}
