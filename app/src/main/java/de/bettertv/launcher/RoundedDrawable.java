package de.bettertv.launcher;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * Zeichnet ein beliebiges Bild mit runden Ecken – ohne View-Zuschnitt (clipToOutline),
 * der bei Software-Zeichnung (fokussierte Kachel mit Leuchten) nicht greift.
 */
final class RoundedDrawable extends Drawable {
    private final Drawable source;
    private final float radius;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private Bitmap cache;
    private Bitmap halo;           // nur bei Glas: oranges Leuchten um das Logo
    private final Paint haloPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private boolean focused;
    private final String style;

    RoundedDrawable(Drawable source, float radius) { this(source, radius, IconStyle.ORIGINAL); }

    private final float ring;      // > 0: leuchtender oranger Rand direkt um das Symbol (Breite in px)
    private final RectF inner = new RectF();
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    RoundedDrawable(Drawable source, float radius, String style) { this(source, radius, style, 0); }

    RoundedDrawable(Drawable source, float radius, String style, float ring) {
        this.ring = ring;
        ringPaint.setStyle(Paint.Style.STROKE);
        this.style = style;
        this.source = source.getConstantState() != null ? source.getConstantState().newDrawable().mutate() : source;
        this.radius = radius;
    }

    @Override protected void onBoundsChange(Rect full) {
        if (full.width() <= 0 || full.height() <= 0) return;
        // Mit Leucht-Rand: Bild etwas einrücken, damit das Leuchten in den Rahmen passt
        Rect b = new Rect(full);
        if (ring > 0) b.inset(Math.round(ring), Math.round(ring));
        inner.set(b);
        if (b.width() <= 0 || b.height() <= 0) return;
        if (cache != null) cache.recycle();
        cache = Bitmap.createBitmap(b.width(), b.height(), Bitmap.Config.ARGB_8888);
        source.setBounds(0, 0, b.width(), b.height());
        source.draw(new Canvas(cache));
        if (!IconStyle.ORIGINAL.equals(style)) {
            int w = cache.getWidth(), h = cache.getHeight();
            int[] px = new int[w * h];
            cache.getPixels(px, 0, w, 0, 0, w, h);
            if (IconStyle.isGlass(style)) {
                float[] m = IconStyle.mask(px, w);
                if (halo != null) halo.recycle();
                halo = Bitmap.createBitmap(IconStyle.halo(m, w, Math.max(3, Math.round(h * 0.06f))), w, h, Bitmap.Config.ARGB_8888);
            }
            IconStyle.apply(px, w, style);
            cache.setPixels(px, 0, w, 0, 0, w, h);
        }
        BitmapShader shader = new BitmapShader(cache, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setTranslate(b.left, b.top);
        shader.setLocalMatrix(m);
        paint.setShader(shader);
    }

    /** Bei Glas: ausgewählte App leuchtet stärker. */
    void setFocused(boolean f) {
        if (focused == f) return;
        focused = f;
        invalidateSelf();
    }

    @Override public void draw(Canvas c) {
        if (cache == null) return;
        drawImageAndGlow(c);
    }

    private void drawImageAndGlow(Canvas c) {
        // 1. Glas: Leuchten entlang der Logo-Form (unter dem Logo)
        if (halo != null) {
            haloPaint.setAlpha(focused ? 255 : 120);
            c.drawBitmap(halo, inner.left, inner.top, haloPaint);
            if (focused) c.drawBitmap(halo, inner.left, inner.top, haloPaint); // doppelt = kräftiger
        }
        // 2. das Bild selbst (mit runden Ecken)
        c.drawRoundRect(inner, radius, radius, paint);
        // 3. Dunkel/Orange/Blau: leuchtender oranger Rand direkt um das Symbol
        if (ring > 0) {
            float base = focused ? 0.34f : 0.16f;
            for (int k = 4; k >= 1; k--) {
                ringPaint.setStrokeWidth(ring * 0.22f * (1 + k));
                ringPaint.setColor(((int) (255 * base / k) << 24) | 0xFF8A1F);
                c.drawRoundRect(inner, radius, radius, ringPaint);
            }
            ringPaint.setStrokeWidth(ring * (focused ? 0.42f : 0.28f));
            ringPaint.setColor(focused ? 0xFFFF8A1F : 0xD9FF8A1F);
            c.drawRoundRect(inner, radius, radius, ringPaint);
        }
    }

    @Override public int getIntrinsicWidth() { return source.getIntrinsicWidth(); }
    @Override public int getIntrinsicHeight() { return source.getIntrinsicHeight(); }
    @Override public void setAlpha(int a) { paint.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
