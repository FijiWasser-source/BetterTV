package de.klar.tvlauncher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Die Apps, die von Anfang an im Dock der Startansicht stehen.
 * Pro App mehrere Paketnamen, weil TV- und Handy-Versionen verschieden heißen.
 */
final class Favorites {
    private Favorites() {}

    static final String[][] DEFAULTS = {
            {"com.netflix.ninja", "com.netflix.mediaclient"},                       // Netflix
            {"com.google.android.youtube.tv", "com.google.android.youtube"},        // YouTube
            {"com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"}, // Prime Video
            {"com.android.vending"},                                                // Google Play Store
            {"tv.arte.plus7"},                                                      // Arte
            {"com.wbd.stream", "com.hbo.max", "com.hbo.hbonow"},                    // HBO Max
            {"com.disney.disneyplus"},                                              // Disney+
    };

    static final class Result {
        final List<String> pinned;
        final Set<String> known;
        Result(List<String> pinned, Set<String> known) { this.pinned = pinned; this.known = known; }
    }

    /**
     * Ergänzt das Dock um Standard-Apps, die installiert sind und noch nie eingeplant wurden.
     * Wer eine Standard-App selbst aus dem Dock nimmt, bekommt sie nicht wieder hineingesetzt
     * ({@code known} merkt sich das). Später installierte Standard-Apps kommen hinten dazu.
     */
    static Result seed(List<String> pinned, Set<String> known, Collection<String> installed) {
        List<String> newPinned = new ArrayList<>(pinned);
        Set<String> newKnown = new HashSet<>(known);
        for (String[] group : DEFAULTS) {
            for (String pkg : group) {
                if (!installed.contains(pkg)) continue;
                if (newKnown.add(pkg) && !newPinned.contains(pkg)) newPinned.add(pkg);
                break;
            }
        }
        return new Result(newPinned, newKnown);
    }
}
