package de.klar.tvlauncher;

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
 * Kachel-Hintergrund für das Design "Abend": helle, cremig-beige Fläche mit feiner Kante;
 * fokussiert mit oranger Kante und Leuchten. Der Leuchtrand liegt innerhalb der
 * View ({@link #GLOW_DP}), damit er nicht abgeschnitten wird.
 */
final class AbendTile extends Drawable {
    static final float GLOW_DP = 10f;
    static final int ORANGE = 0xFFFF8A1F;
    /** Schrift auf den hellen Kacheln */
    static final int INK = 0xFF2E2922;
    static final int INK_SOFT = 0xFF8C7A5E;

    private final float density;
    private final float radius; // < 0 = Kapsel
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private boolean focused;

    AbendTile(float density, float radiusDp) {
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
            ((AbendTile) card.getBackground()).setFocused(f);
            card.setLayerType(f ? View.LAYER_TYPE_SOFTWARE : View.LAYER_TYPE_NONE, null);
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
        fill.setShader(new LinearGradient(0, r.top, 0, r.bottom, 0xFFF2E6CF, 0xFFE4D3B4, Shader.TileMode.CLAMP));
    }

    @Override public void draw(Canvas c) {
        float rad = radius < 0 ? r.height() / 2f : radius;
        if (focused) c.drawRoundRect(r, rad, rad, glow);
        c.drawRoundRect(r, rad, rad, fill);
        rim.setStrokeWidth((focused ? 2.5f : 1.5f) * density);
        rim.setColor(focused ? ORANGE : 0x66FFF8EA);
        float h = rim.getStrokeWidth() / 2f;
        RectF in = new RectF(r.left + h, r.top + h, r.right - h, r.bottom - h);
        c.drawRoundRect(in, rad - h, rad - h, rim);
    }

    @Override public void setAlpha(int a) {}
    @Override public void setColorFilter(ColorFilter cf) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
