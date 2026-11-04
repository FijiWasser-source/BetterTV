package de.klar.tvlauncher;

import android.content.Context;
import android.graphics.Typeface;

/** Nunito ExtraBold (SIL Open Font License, siehe assets/fonts/Nunito-OFL.txt). */
final class Fonts {
    private Fonts() {}
    private static Typeface rounded;

    static synchronized Typeface rounded(Context c) {
        if (rounded == null) {
            try {
                rounded = Typeface.createFromAsset(c.getAssets(), "fonts/Nunito-ExtraBold.ttf");
            } catch (RuntimeException e) {
                rounded = Typeface.DEFAULT_BOLD;
            }
        }
        return rounded;
    }
}
