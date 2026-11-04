package de.klar.tvlauncher;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class FavoritesTest {

    @Test
    public void firstStartPutsInstalledDefaultsInOrder() {
        List<String> installed = Arrays.asList("com.disney.disneyplus", "tv.arte.plus7", "com.netflix.ninja",
                "com.google.android.youtube.tv", "com.android.vending", "de.ard");
        Favorites.Result r = Favorites.seed(Collections.emptyList(), Collections.emptySet(), installed);
        assertEquals(Arrays.asList("com.netflix.ninja", "com.google.android.youtube.tv", "com.android.vending",
                "tv.arte.plus7", "com.disney.disneyplus"), r.pinned);
    }

    @Test
    public void removedDefaultStaysRemovedButNewInstallIsAdded() {
        // Nutzer hat Netflix entfernt; später wird Prime Video installiert
        List<String> pinned = Arrays.asList("com.google.android.youtube.tv");
        HashSet<String> known = new HashSet<>(Arrays.asList("com.netflix.ninja", "com.google.android.youtube.tv"));
        List<String> installed = Arrays.asList("com.netflix.ninja", "com.google.android.youtube.tv",
                "com.amazon.amazonvideo.livingroom");
        Favorites.Result r = Favorites.seed(pinned, known, installed);
        assertEquals(Arrays.asList("com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom"), r.pinned);
    }

    @Test
    public void tvVersionPreferredOverPhoneVersion() {
        List<String> installed = Arrays.asList("com.google.android.youtube", "com.google.android.youtube.tv");
        Favorites.Result r = Favorites.seed(Collections.emptyList(), Collections.emptySet(), installed);
        assertEquals(Collections.singletonList("com.google.android.youtube.tv"), r.pinned);
    }
}
