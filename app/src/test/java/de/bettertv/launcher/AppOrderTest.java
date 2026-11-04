package de.bettertv.launcher;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class AppOrderTest {

    private static Map<String, String> labels() {
        Map<String, String> m = new HashMap<>();
        m.put("com.netflix", "Netflix");
        m.put("com.youtube", "YouTube");
        m.put("de.ard", "ARD Mediathek");
        m.put("de.zdf", "ZDFmediathek");
        m.put("com.spotify", "Spotify");
        return m;
    }

    @Test
    public void pinnedFirstInSavedOrderThenAlphabetical() {
        List<String> order = AppOrder.order(labels(),
                Arrays.asList("com.youtube", "com.netflix"), Collections.emptySet());
        assertEquals(Arrays.asList("com.youtube", "com.netflix", "de.ard", "com.spotify", "de.zdf"), order);
    }

    @Test
    public void hiddenAndUninstalledAppsAreDropped() {
        List<String> order = AppOrder.order(labels(),
                Arrays.asList("gone.app", "com.spotify"), new HashSet<>(Arrays.asList("com.youtube", "com.spotify")));
        assertEquals(Arrays.asList("de.ard", "com.netflix", "de.zdf"), order);
    }

    @Test
    public void moveIsClampedToListBounds() {
        List<String> pinned = Arrays.asList("a", "b", "c");
        assertEquals(Arrays.asList("b", "a", "c"), AppOrder.move(pinned, "a", 1));
        assertEquals(Arrays.asList("a", "b", "c"), AppOrder.move(pinned, "a", -1));
        assertEquals(Arrays.asList("a", "b", "c"), AppOrder.move(pinned, "c", 5));
        assertEquals(Arrays.asList("a", "b", "c"), AppOrder.move(pinned, "x", 1));
    }

    @Test
    public void weatherFormatting() {
        assertEquals("0°", Weather.formatTemp(-0.3));
        assertEquals("13°", Weather.formatTemp(12.6));
        assertEquals("Teilweise bewölkt", Weather.describe(2));
        assertEquals("", Weather.describe(1234));
    }
}
