package de.klar.tvlauncher;

/**
 * Einheitliche App-Symbole (nur innerhalb von Klar): Logo hell bzw. in einer Tönung auf
 * dunklem Grund. Funktioniert ohne Android-Klassen (reine Pixel-Arrays), damit testbar.
 */
final class IconStyle {
    private IconStyle() {}

    static final String ORIGINAL = "original", DARK = "dunkel", ORANGE = "orange", BLUE = "blau", GLASS = "glas";
    static final String[] ALL = {ORIGINAL, DARK, ORANGE, BLUE, GLASS};

    static String label(String style) {
        switch (style) {
            case DARK: return "Dunkel";
            case ORANGE: return "Orange getönt";
            case BLUE: return "Blau getönt";
            case GLASS: return "Glas";
            default: return "Original";
        }
    }

    static String next(String style) {
        for (int i = 0; i < ALL.length; i++) if (ALL[i].equals(style)) return ALL[(i + 1) % ALL.length];
        return ALL[0];
    }

    private static final int BG = 0x1C1E22;

    private static int fg(String style) {
        switch (style) {
            case ORANGE: return 0xFF8A1F;
            case BLUE: return 0x8FB4FF;
            default: return 0xF2F2F2;
        }
    }

    /**
     * Färbt die Pixel (ARGB, zeilenweise, Breite {@code width}) direkt um.
     * Ob der Hintergrund hell ist, entscheidet der Bildrand – dort liegt fast immer der Grund,
     * nicht das Logo. Bei ORIGINAL passiert nichts.
     */
    static void apply(int[] px, int width, String style) {
        if (ORIGINAL.equals(style) || px.length == 0 || width <= 0) return;
        int height = px.length / width;
        float[] lum = new float[px.length];
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            lum[i] = (0.2126f * ((c >> 16) & 0xFF) + 0.7152f * ((c >> 8) & 0xFF) + 0.0722f * (c & 0xFF)) / 255f;
        }
        // 1. Helligkeit des Randes (oben, unten, links, rechts; nur sichtbare Pixel)
        double sum = 0; int n = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean edge = y == 0 || y == height - 1 || x == 0 || x == width - 1;
                int i = y * width + x;
                if (edge && (px[i] >>> 24) > 128) { sum += lum[i]; n++; }
            }
        }
        boolean brightBackground = n > 0 && sum / n > 0.5;
        // 2. Heller Hintergrund (z. B. weißes Banner) -> umdrehen, damit das Logo hell wird
        float lo = 1, hi = 0;
        for (int i = 0; i < lum.length; i++) {
            if (brightBackground) lum[i] = 1 - lum[i];
            if ((px[i] >>> 24) > 128) { lo = Math.min(lo, lum[i]); hi = Math.max(hi, lum[i]); }
        }
        float range = Math.max(0.15f, hi - lo);
        if (GLASS.equals(style)) { glass(px, lum, lo, range, width, height); return; }
        // 3. Auf Verlauf dunkler Grund -> Tönung abbilden (Kontrast gestreckt)
        int f = fg(style);
        for (int i = 0; i < px.length; i++) {
            float t = Math.max(0, Math.min(1, (lum[i] - lo) / range));
            t = t * t * (3 - 2 * t); // weiche Kurve: Grund bleibt ruhig, Logo klar
            int r = mix((BG >> 16) & 0xFF, (f >> 16) & 0xFF, t);
            int g = mix((BG >> 8) & 0xFF, (f >> 8) & 0xFF, t);
            int b = mix(BG & 0xFF, f & 0xFF, t);
            px[i] = (px[i] & 0xFF000000) | (r << 16) | (g << 8) | b;
        }
    }

    private static int mix(int a, int b, float t) { return Math.round(a + (b - a) * t); }

    /**
     * Glas: getöntes, dunkel durchscheinendes Glas (Kachelfarbe schimmert durch), das Logo
     * hell und fast deckend darauf, oben ein Lichtreflex und eine feine helle Kante.
     */
    private static void glass(int[] px, float[] lum, float lo, float range, int width, int height) {
        float highlightRows = height * 0.45f;
        for (int y = 0; y < height; y++) {
            float sheen = y < highlightRows ? 0.22f * (1 - y / highlightRows) : 0;
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                float t = Math.max(0, Math.min(1, (lum[i] - lo) / range));
                t = t * t * (3 - 2 * t);
                // Farbe: Grund fast schwarz (mit Reflex aufgehellt) -> Logo weiß
                float g = 0.06f + sheen;
                float v = g + (1 - g) * t;
                float a = 0.42f + (0.95f - 0.42f) * t;                  // Grund ~42 %, Logo ~95 %
                boolean edge = y == 0 || x == 0 || x == width - 1 || y == height - 1;
                if (edge) { v = 1f; a = y == 0 ? 0.6f : 0.35f; }         // helle Kante, oben kräftiger
                int c = Math.round(v * 255);
                int alpha = Math.round(a * (px[i] >>> 24));
                px[i] = (alpha << 24) | (c << 16) | (c << 8) | c;
            }
        }
    }
}
