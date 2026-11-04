package de.bettertv.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

/**
 * Hintergrund des Designs "Abend": Grau mit gleichmäßigem Punkteraster
 * (wie Dot-Grid-Papier). Das Raster ist eine winzige, wiederholte Kachel –
 * gestochen scharf und praktisch ohne Speicherbedarf.
 */
final class AbendBackground {
    private AbendBackground() {}

    static final float SPACING_DP = 26f;
    static final float DOT_RADIUS_DP = 1.3f;

    static Drawable create(Context c) {
        float d = c.getResources().getDisplayMetrics().density;
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF383B41, 0xFF2C2F34, 0xFF222428});

        int size = Math.max(4, Math.round(SPACING_DP * d));
        Bitmap tile = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0x33FFFFFF);
        new Canvas(tile).drawCircle(size / 2f, size / 2f, DOT_RADIUS_DP * d, p);
        BitmapDrawable dots = new BitmapDrawable(c.getResources(), tile);
        dots.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);

        return new LayerDrawable(new Drawable[]{base, dots});
    }
}
