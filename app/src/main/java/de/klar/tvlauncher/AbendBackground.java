package de.klar.tvlauncher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.Random;

/** Hintergrund des Designs "Abend": Nachtblau ins Violette, mit feinen Sternenpunkten. */
final class AbendBackground {
    private AbendBackground() {}

    static Bitmap render(int width, int height) {
        int w = Math.max(1, width / 2), h = Math.max(1, height / 2);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setShader(new LinearGradient(0, 0, w * 0.3f, h, 0xFF151B44, 0xFF2A2A66, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new RadialGradient(w * 0.8f, h * 0.1f, w * 0.5f, 0x332F6BFF, 0x002F6BFF, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        Random rnd = new Random(7); // immer dasselbe Muster
        for (int i = 0; i < w * h / 900; i++) {
            p.setColor(((18 + rnd.nextInt(40)) << 24) | 0xFFFFFF);
            c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, 0.6f + rnd.nextFloat() * 0.7f, p);
        }
        return bmp;
    }
}
