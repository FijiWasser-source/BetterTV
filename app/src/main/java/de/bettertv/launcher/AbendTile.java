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
import android.view.View;

/**
 * Kachel-Hintergrund für das Design "Abend": hellblaue Fläche (#8FB4FF, wie Datum und Begrüßung) mit feiner Kante;
 * fokussiert mit oranger Kante und Leuchten. Der Leuchtrand liegt innerhalb der
 * View ({@link #GLOW_DP}), damit er nicht abgeschnitten wird.
 */
final class AbendTile extends Drawable {
    static final float GLOW_DP = 10f;
    static final int ORANGE = 0xFFFF8A1F;
    /** Schrift auf den hellen Kacheln */
    static final int INK = 0xFF1B2640;
    static final int INK_SOFT = 0xFF3D5788;

    private final float density;
    private final float radius; // < 0 = Kapsel
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private boolean focused;
    /** Nur Umrandung: kein Hintergrund, leuchtender oranger Rand (z. B. "Weiter mit"-Knopf). */
    final boolean outline;
    /** Gar nichts zeichnen: Glas-Logo steht frei auf dem Hintergrund, das Leuchten macht das Logo selbst. */
    final boolean invisible;

    AbendTile(float density, float radiusDp) { this(density, radiusDp, false); }

    AbendTile(float density, float radiusDp, boolean outline) { this(density, radiusDp, outline, false); }

    AbendTile(float density, float radiusDp, boolean outline, boolean invisible) {
        this.outline = outline || invisible;
        this.invisible = invisible;
        this.density = density;
        this.radius = radiusDp < 0 ? -1 : radiusDp * density;
        rim.setStyle(Paint.Style.STROKE);
        glow.setStyle(Paint.Style.STROKE);
        glow.setColor(ORANGE);
        glow.setStrokeWidth(3 * density);
        glow.setShadowLayer(9 * density, 0, 0, ORANGE);
    }

    /** Für das Leuchten braucht Android vor Version 9 eine Software-Ebene – nur solange fokussiert. */
    static void focus(View card, boolean f) {
        if (card.getBackground() instanceof AbendTile) {
            AbendTile t = (AbendTile) card.getBackground();
            t.setFocused(f);
            // Umrandungs-Variante leuchtet ohne Software-Ebene (weiche Linien statt Schatten)
            card.setLayerType(f && !t.outline ? View.LAYER_TYPE_SOFTWARE : View.LAYER_TYPE_NONE, null);
        }
    }

    void setFocused(boolean f) {
        if (focused == f) return;
        focused = f;
        invalidateSelf();
    }

    @Override protected void onBoundsChange(Rect b) {
        float m = GLOW_DP * density;
        r.set(b.left + m, b.top + m, b.right - m, b.bottom - m);
        fill.setShader(new LinearGradient(0, r.top, 0, r.bottom, 0xFF9CBDFF, 0xFF82A9F5, Shader.TileMode.CLAMP));
    }

    @Override public void draw(Canvas c) {
        float rad = radius < 0 ? r.height() / 2f : radius;
        if (invisible) return;
        if (outline) { drawOutline(c, rad); return; }
        if (focused) c.drawRoundRect(r, rad, rad, glow);
        c.drawRoundRect(r, rad, rad, fill);
        rim.setStrokeWidth((focused ? 2.5f : 1.5f) * density);
        rim.setColor(focused ? ORANGE : 0x59FFFFFF);
        float h = rim.getStrokeWidth() / 2f;
        RectF in = new RectF(r.left + h, r.top + h, r.right - h, r.bottom - h);
        c.drawRoundRect(in, rad - h, rad - h, rim);
    }

    private void drawOutline(Canvas c, float rad) {
        // weiches Leuchten: mehrere breite, sehr transparente Linien übereinander
        float base = focused ? 0.30f : 0.14f;
        for (int k = 4; k >= 1; k--) {
            glowLine.setStrokeWidth((1.5f + k * 2.2f) * density);
            glowLine.setColor(((int) (255 * base / k) << 24) | (ORANGE & 0xFFFFFF));
            c.drawRoundRect(r, rad, rad, glowLine);
        }
        rim.setStrokeWidth((focused ? 2.5f : 1.6f) * density);
        rim.setColor(focused ? ORANGE : 0xD9FF8A1F);
        c.drawRoundRect(r, rad, rad, rim);
    }

    private final Paint glowLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    { glowLine.setStyle(Paint.Style.STROKE); }

    @Override public void setAlpha(int a) {}
    @Override public void setColorFilter(ColorFilter cf) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
