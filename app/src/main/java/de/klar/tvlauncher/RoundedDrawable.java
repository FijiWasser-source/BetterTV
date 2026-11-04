package de.klar.tvlauncher;

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

    RoundedDrawable(Drawable source, float radius, String style) {
        this.style = style;
        this.source = source.getConstantState() != null ? source.getConstantState().newDrawable().mutate() : source;
        this.radius = radius;
    }

    @Override protected void onBoundsChange(Rect b) {
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
        if (halo != null) {
            haloPaint.setAlpha(focused ? 255 : 120);
            Rect b = getBounds();
            c.drawBitmap(halo, b.left, b.top, haloPaint);
            if (focused) c.drawBitmap(halo, b.left, b.top, haloPaint); // doppelt = kräftiger
        }
        rect.set(getBounds());
        c.drawRoundRect(rect, radius, radius, paint);
    }

    @Override public int getIntrinsicWidth() { return source.getIntrinsicWidth(); }
    @Override public int getIntrinsicHeight() { return source.getIntrinsicHeight(); }
    @Override public void setAlpha(int a) { paint.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
