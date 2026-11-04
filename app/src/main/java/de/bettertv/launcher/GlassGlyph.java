package de.bettertv.launcher;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * Symbol im Stil der Glas-Icons: dunkles, leicht durchscheinendes Glas mit Glanz oben,
 * feiner heller Kante, oranges Zeichen in der Mitte und oranges Leuchten drumherum.
 * Für "Einstellungen" und "Alle Apps".
 */
final class GlassGlyph extends Drawable {
    private static final int ORANGE = 0xFFFF8A1F;
    private final Drawable glyph;
    private final float density;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF(), outer = new RectF();
    private float radius;

    GlassGlyph(Drawable glyph, float density) {
        this.glyph = glyph.mutate();
        this.glyph.setTint(ORANGE);
        this.density = density;
        line.setStyle(Paint.Style.STROKE);
    }

    @Override protected void onBoundsChange(Rect b) {
        float inset = 5 * density;                       // Platz fürs Leuchten
        box.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
        radius = box.width() * 0.26f;
        fill.setShader(new LinearGradient(0, box.top, 0, box.bottom, 0xE62A2D33, 0xE6141619, Shader.TileMode.CLAMP));
        sheen.setShader(new LinearGradient(0, box.top, 0, box.top + box.height() * 0.5f, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        int g = Math.round(box.width() * 0.56f);
        int cx = Math.round(box.centerX()), cy = Math.round(box.centerY());
        glyph.setBounds(cx - g / 2, cy - g / 2, cx + g / 2, cy + g / 2);
    }

    @Override public void draw(Canvas c) {
        // oranges Leuchten (weiche Linien, ohne Software-Ebene)
        for (int k = 4; k >= 1; k--) {
            line.setStrokeWidth((1.2f + k * 1.6f) * density);
            line.setColor(((int) (255 * 0.16f / k) << 24) | 0xFF8A1F);
            c.drawRoundRect(box, radius, radius, line);
        }
        c.drawRoundRect(box, radius, radius, fill);       // dunkles Glas
        c.drawRoundRect(box, radius, radius, sheen);      // Glanz oben
        line.setStrokeWidth(1.2f * density);
        line.setColor(0x59FFFFFF);                        // feine helle Kante
        c.drawRoundRect(box, radius, radius, line);
        line.setStrokeWidth(1.4f * density);
        line.setColor(0xCCFF8A1F);                        // oranger Rand
        outer.set(box);
        outer.inset(-1.2f * density, -1.2f * density);
        c.drawRoundRect(outer, radius + 1.2f * density, radius + 1.2f * density, line);
        glyph.draw(c);
    }

    @Override public int getIntrinsicWidth() { return Math.round(54 * density); }
    @Override public int getIntrinsicHeight() { return Math.round(54 * density); }
    @Override public void setAlpha(int a) {}
    @Override public void setColorFilter(ColorFilter cf) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
