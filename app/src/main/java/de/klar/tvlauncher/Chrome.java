package de.klar.tvlauncher;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;

/** Legt den Polarnacht-Hintergrund an und gibt den Glasflächen ihre Vorlage. */
final class Chrome {
    private Chrome() {}

    static void install(View root, GlassLayout... glass) {
        int w = root.getWidth(), h = root.getHeight();
        if (w == 0 || h == 0) return;
        Bitmap bg = Aurora.render(root.getContext(), w, h);
        root.setBackground(new BitmapDrawable(root.getResources(), bg));
        Bitmap blurred = Aurora.blurred(bg, w, h);
        float scale = (float) w / blurred.getWidth();
        for (GlassLayout g : glass) g.setBackdrop(blurred, scale);
    }
}
