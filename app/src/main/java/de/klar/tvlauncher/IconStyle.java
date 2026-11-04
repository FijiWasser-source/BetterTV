package de.klar.tvlauncher;

/**
 * Einheitliche App-Symbole (nur innerhalb von Klar): Logo hell bzw. in einer Tönung auf
 * dunklem Grund. Funktioniert ohne Android-Klassen (reine Pixel-Arrays), damit testbar.
 */
final class IconStyle {
    private IconStyle() {}

    static final String ORIGINAL = "original", DARK = "dunkel", ORANGE = "orange", BLUE = "blau",
            GLASS = "glas", GLASS_ORANGE = "glas_orange";
    static final String[] ALL = {ORIGINAL, DARK, ORANGE, BLUE, GLASS, GLASS_ORANGE};

    static String label(String style) {
        switch (style) {
            case DARK: return "Dunkel";
            case ORANGE: return "Orange getönt";
            case BLUE: return "Blau getönt";
            case GLASS: return "Glas Blau";
            case GLASS_ORANGE: return "Glas Orange";
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
        if (GLASS.equals(style)) { glass(px, lum, lo, range, width, height, false); return; }
        if (GLASS_ORANGE.equals(style)) { glass(px, lum, lo, range, width, height, true); return; }
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

    static boolean isGlass(String style) { return GLASS.equals(style) || GLASS_ORANGE.equals(style); }

    /** Logo-Maske 0..1 (0 = Hintergrund), gleiche Erkennung wie bei den Stilen. */
    static float[] mask(int[] px, int width) {
        int height = px.length / width;
        float[] lum = new float[px.length];
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            lum[i] = (0.2126f * ((c >> 16) & 0xFF) + 0.7152f * ((c >> 8) & 0xFF) + 0.0722f * (c & 0xFF)) / 255f;
        }
        double sum = 0; int n = 0;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            boolean edge = y == 0 || y == height - 1 || x == 0 || x == width - 1;
            int i = y * width + x;
            if (edge && (px[i] >>> 24) > 128) { sum += lum[i]; n++; }
        }
        boolean bright = n > 0 && sum / n > 0.5;
        float lo = 1, hi = 0;
        for (int i = 0; i < lum.length; i++) {
            if (bright) lum[i] = 1 - lum[i];
            if ((px[i] >>> 24) > 128) { lo = Math.min(lo, lum[i]); hi = Math.max(hi, lum[i]); }
        }
        float range = Math.max(0.15f, hi - lo);
        float[] m = new float[px.length];
        for (int i = 0; i < px.length; i++) {
            float v = Math.max(0, Math.min(1, (lum[i] - lo) / range));
            v = Math.max(0, (v - 0.3f) / 0.7f);
            m[i] = v * v * (3 - 2 * v) * ((px[i] >>> 24) / 255f);
        }
        return m;
    }

    /**
     * Oranges Leuchten, das der Form des Logos folgt: Maske weichzeichnen und nur außerhalb
     * des Logos zeigen. Ergebnis: ARGB-Pixel (Orange mit Deckkraft).
     */
    static int[] halo(float[] m, int width, int radius) {
        int height = m.length / width;
        float[] a = m.clone(), b = new float[m.length];
        for (int pass = 0; pass < 3; pass++) {
            blur(a, b, width, height, radius, true);
            blur(b, a, width, height, radius, false);
        }
        int[] out = new int[m.length];
        for (int i = 0; i < m.length; i++) {
            float g = Math.min(1f, a[i] * 2.6f) * (1 - m[i]);
            out[i] = (Math.round(g * 255) << 24) | 0xFF8A1F;
        }
        return out;
    }

    private static void blur(float[] in, float[] out, int w, int h, int r, boolean horizontal) {
        int lines = horizontal ? h : w, len = horizontal ? w : h;
        for (int line = 0; line < lines; line++) {
            float acc = 0; int count = 0;
            for (int k = -r; k <= r; k++) {
                int j = Math.max(0, Math.min(len - 1, k));
                acc += horizontal ? in[line * w + j] : in[j * w + line]; count++;
            }
            for (int i = 0; i < len; i++) {
                if (horizontal) out[line * w + i] = acc / count; else out[i * w + line] = acc / count;
                int add = Math.min(len - 1, i + r + 1), rem = Math.max(0, i - r);
                acc += (horizontal ? in[line * w + add] : in[add * w + line]) - (horizontal ? in[line * w + rem] : in[rem * w + line]);
            }
        }
    }

    /**
     * Glas: ohne Hintergrund – nur das Logo wird zu Glas (blau oder orange, leicht
     * durchscheinend) mit Lichtreflex oben und heller Glanzkante. Die Kachel selbst hat
     * dann ebenfalls keinen Hintergrund, nur die leuchtende orange Umrandung.
     */
    private static void glass(int[] px, float[] lum, float lo, float range, int width, int height, boolean orange) {
        float[] t = new float[px.length];
        for (int i = 0; i < px.length; i++) {
            float v = Math.max(0, Math.min(1, (lum[i] - lo) / range));
            // Hintergrund konsequent wegschneiden (auch Farbverläufe), Rest neu spreizen
            v = Math.max(0, (v - 0.3f) / 0.7f);
            t[i] = v * v * (3 - 2 * v);
        }
        // Logo als Glas direkt auf dem dunklen Grund: hell getöntes Blau bzw. Orange
        int base = orange ? 0xFF8A1F : 0x8FB4FF;      // Glasfarbe
        int light = orange ? 0xFFE0BF : 0xE3ECFF;     // Farbe des Lichtreflexes
        float maxAlpha = 0.9f;
        float highlightRows = height * 0.5f;
        for (int y = 0; y < height; y++) {
            float sheen = y < highlightRows ? 0.38f * (1 - y / highlightRows) : 0;
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                float m = t[i];                                   // 0 = Hintergrund (fällt weg), 1 = Logo
                if (m < 0.02f) { px[i] &= 0x00FFFFFF; continue; } // Hintergrund ganz durchsichtig
                // Glanzkante: Logopixel, über dem kein Logo mehr ist
                boolean topEdge = m > 0.5f && (y == 0 || t[i - width] < 0.5f);
                int r, g, b; float a;
                if (topEdge) {
                    r = g = b = 255; a = 0.75f;
                } else {
                    r = mix((base >> 16) & 0xFF, (light >> 16) & 0xFF, sheen);
                    g = mix((base >> 8) & 0xFF, (light >> 8) & 0xFF, sheen);
                    b = mix(base & 0xFF, light & 0xFF, sheen);
                    a = maxAlpha * m + sheen * 0.15f * m;
                }
                int alpha = Math.round(Math.min(1f, a) * (px[i] >>> 24));
                px[i] = (alpha << 24) | (r << 16) | (g << 8) | b;
            }
        }
    }
}
