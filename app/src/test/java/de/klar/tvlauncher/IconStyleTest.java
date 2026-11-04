package de.klar.tvlauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IconStyleTest {

    private static float lum(int c) {
        return (0.2126f * ((c >> 16) & 0xFF) + 0.7152f * ((c >> 8) & 0xFF) + 0.0722f * (c & 0xFF)) / 255f;
    }

    /** Weißes Banner mit rotem Logo (wie Netflix): danach dunkler Grund, helles Logo. */
    @Test public void brightBannerIsInvertedToLightLogoOnDark() {
        int[] px = banner(0xFFFFFFFF, 0xFFE50914);
        IconStyle.apply(px, 10, IconStyle.DARK);
        assertTrue("Grund dunkel", lum(px[0]) < 0.2f);
        assertTrue("Logo hell", lum(px[55]) > 0.8f);
    }

    /** Blaues Banner mit großem weißem Schriftzug (wie Prime Video): Logo bleibt hell. */
    @Test public void darkBannerKeepsLightLogoEvenWithBigLettering() {
        int[] px = banner(0xFF0F79AF, 0xFFFFFFFF); // Logo = 64 % der Fläche
        IconStyle.apply(px, 10, IconStyle.DARK);
        assertTrue("Grund dunkel", lum(px[0]) < 0.2f);
        assertTrue("Logo hell", lum(px[55]) > 0.8f);
    }

    /** 10×10-Bild: Rand = Hintergrund, Mitte 8×8 = Logo. */
    private static int[] banner(int bg, int logo) {
        int[] px = new int[100];
        for (int y = 0; y < 10; y++) for (int x = 0; x < 10; x++)
            px[y * 10 + x] = (x == 0 || y == 0 || x == 9 || y == 9) ? bg : logo;
        return px;
    }

    @Test public void tintUsesChosenColorAndKeepsAlpha() {
        int[] px = {0x00FFFFFF, 0xFFFFFFFF, 0xFF000000, 0xFF000000, 0xFF000000};
        IconStyle.apply(px, 5, IconStyle.ORANGE);
        assertEquals(0xFFFF8A1F, px[1]);          // Logo = Orange
        assertEquals(0x00, px[0] >>> 24);         // Transparenz bleibt
        assertEquals(0xFF1C1E22, px[2]);          // Grund = dunkel
    }

    @Test public void originalChangesNothingAndCycleWraps() {
        int[] px = {0xFF123456};
        IconStyle.apply(px, 1, IconStyle.ORIGINAL);
        assertEquals(0xFF123456, px[0]);
        assertEquals(IconStyle.DARK, IconStyle.next(IconStyle.ORIGINAL));
        assertEquals(IconStyle.GLASS, IconStyle.next(IconStyle.BLUE));
        assertEquals(IconStyle.ORIGINAL, IconStyle.next(IconStyle.GLASS));
    }

    /** Glas: Logo fast deckend weiß, Grund durchscheinend, Rand heller als der Grund. */
    @Test public void glassMakesLogoWhiteAndBackgroundTranslucent() {
        // 10×10: Rand weiß (Hintergrund), Logo nur ein 4×4-Feld in der Mitte
        int[] px = new int[100];
        for (int y = 0; y < 10; y++) for (int x = 0; x < 10; x++)
            px[y * 10 + x] = (x >= 3 && x <= 6 && y >= 3 && y <= 6) ? 0xFFE50914 : 0xFFFFFFFF;
        IconStyle.apply(px, 10, IconStyle.GLASS);
        int logo = px[55], bottomGround = px[8 * 10 + 9];
        assertEquals(0xFFFFFF, logo & 0xFFFFFF);
        assertTrue("Logo fast deckend", (logo >>> 24) > 200);
        assertTrue("Grund durchscheinend", (px[22] >>> 24) < 140 && (px[22] & 0xFF) < 120);
        assertTrue("Rand durchscheinend", (bottomGround >>> 24) < 120);
        assertTrue("obere Kante heller als untere", (px[5] >>> 24) > (px[95] >>> 24));
    }
}
