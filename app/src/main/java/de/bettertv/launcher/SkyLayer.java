package de.bettertv.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Lebendiger Pixel-Himmel hinter der ganzen Startansicht: Wolken, Vogelschwärme, ab und zu
 * ein Flugzeug oder Heißluftballon, abends/nachts Mond, Sternschnuppen und Glühwürmchen,
 * tagsüber eine kleine Sonne. Alles dezent (Texte und Kacheln bleiben lesbar), ~20 Bilder/s,
 * pausiert, sobald eine andere App im Vordergrund ist.
 */
public class SkyLayer extends View {

    private static final String[] CLOUD_S = {".XXX.", "XXXXX"};
    private static final String[] CLOUD_M = {"..XXX...", ".XXXXXX.", "XXXXXXXX"};
    private static final String[] CLOUD_L = {"....XXX.....", "..XXXXXXX...", ".XXXXXXXXXX.", "XXXXXXXXXXXX"};
    private static final String[] BIRD_UP = {"X...X", ".X.X.", "..X.."};
    private static final String[] BIRD_DOWN = {".....", "XX.XX", "..X.."};
    private static final String[] PLANE = {
            "X.........",
            "XX..XX....",
            "XXXXXXXXX.",
            ".XXXXXXXXX",
            "....XX....",
    };
    private static final String[] BALLOON = {
            "..OOBB..", ".OOBBOO.", "OOBBOOBB", "OOBBOOBB", ".OBBOOB.", "..BBOO..", "...WW...", "...WW...", "..KKKK..", "..KKKK.."};
    // Vollmond 9×9: X = Mond, C = Krater (etwas dunkler)
    private static final String[] MOON = {
            "...XXX...",
            ".XXXXXXX.",
            ".XXCCXXX.",
            "XXXCCXXXX",
            "XXXXXXCXX",
            "XCXXXXXXX",
            ".XXXXCCX.",
            ".XXXXXXX.",
            "...XXX...",
    };
    private static final String[] SUN = {"..XXX..", ".XXXXX.", "XXXXXXX", "XXXXXXX", "XXXXXXX", ".XXXXX.", "..XXX.."};

    static final class Sprite {
        int kind;          // 0 Wolke, 1 Vögel, 2 Flugzeug, 3 Ballon, 4 Sternschnuppe
        float x, y, vx, vy, size; int variant; float age; long seed;
        Sprite(int kind) { this.kind = kind; }
    }

    private final Paint paint = new Paint();
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG); // weicher Mondschein
    private float glowKey = Float.NaN;
    private static final int[] GLOW_COLORS = {0x55DCE6FF, 0x22DCE6FF, 0x00DCE6FF};
    private static final float[] GLOW_STOPS = {0f, 0.45f, 1f};
    // Glühwürmchen: feste Grundpositionen (einmal berechnet statt pro Bild)
    private static final float[] FLY_X = new float[9], FLY_Y = new float[9];
    static {
        Random fr = new Random(11);
        for (int k = 0; k < 9; k++) { FLY_X[k] = fr.nextFloat(); FLY_Y[k] = 0.6f + fr.nextFloat() * 0.35f; }
    }
    private final RectF rect = new RectF();
    private final Random rnd = new Random();
    private final List<Sprite> sprites = new ArrayList<>();
    private float px; // Pixelgröße
    private boolean running, seeded;
    private long last;
    private float nextBirds = 4, nextPlane = 18, nextBalloon = 60, nextShooting = 8;
    private Integer forceHour; // nur Vorschau
    private boolean animated = true;
    private int weather = Mascot.CLEAR;
    // Regentropfen und Schneeflocken: feste Arrays, keine Objekte pro Bild
    private static final int DROPS = 90;
    private final float[] dropX = new float[DROPS], dropY = new float[DROPS], dropV = new float[DROPS];
    private boolean dropsSeeded;

    public void setWeather(int w) { weather = w; dropsSeeded = false; invalidate(); }

    private void seedDrops() {
        if (dropsSeeded || getWidth() == 0) return;
        dropsSeeded = true;
        Random r = new Random(5);
        for (int i = 0; i < DROPS; i++) {
            dropX[i] = r.nextFloat() * getWidth(); dropY[i] = r.nextFloat() * getHeight();
            dropV[i] = 0.6f + r.nextFloat() * 0.8f;
        }
    }

    private void stepDrops(float dt) {
        int w = getWidth(), h = getHeight();
        for (int i = 0; i < DROPS; i++) {
            if (weather == Mascot.RAIN) { dropY[i] += dropV[i] * 260 * px * dt; dropX[i] -= 30 * px * dt; }
            else { dropY[i] += dropV[i] * 18 * px * dt; dropX[i] += (float) Math.sin(dropY[i] / (14 * px) + i) * 8 * px * dt; }
            if (dropY[i] > h) { dropY[i] -= h + 10 * px; dropX[i] = (dropX[i] + w * 0.37f) % w; }
            if (dropX[i] < 0) dropX[i] += w;
        }
    }

    private void drawDrops(Canvas c) {
        if (weather != Mascot.RAIN && weather != Mascot.SNOW) return;
        seedDrops();
        for (int i = 0; i < DROPS; i++) {
            if (weather == Mascot.RAIN) {
                paint.setColor(i % 4 == 0 ? 0xA6B9CCE6 : 0x809FB8D9);
                rect.set(dropX[i], dropY[i], dropX[i] + px * 0.6f, dropY[i] + px * 3.6f);
            } else {
                paint.setColor(i % 3 == 0 ? 0xCCFFFFFF : 0x8CFFFFFF);
                float f = px * (i % 3 == 0 ? 1.2f : 0.8f);
                rect.set(dropX[i], dropY[i], dropX[i] + f, dropY[i] + f);
            }
            c.drawRect(rect, paint);
        }
    }

    /** false: Himmel steht still (einmal gezeichnet). */
    public void setAnimated(boolean on) { animated = on; last = SystemClock.uptimeMillis(); invalidate(); }

    public SkyLayer(Context c) { this(c, null); }

    public SkyLayer(Context c, AttributeSet a) {
        super(c, a);
        px = 3 * getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    private int hour() {
        return forceHour != null ? forceHour : Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
    }

    private boolean evening() { int h = hour(); return h >= 18 || h < 6; }

    /** Vorschau: feste, gut gefüllte Szene. */
    void preview(int hour) {
        forceHour = hour;
        rnd.setSeed(3);
        sprites.clear();
        seeded = false;
        seed();
        Sprite b = new Sprite(1); b.x = getWidth() * 0.42f; b.y = getHeight() * 0.33f; b.vx = 30; b.variant = 3; sprites.add(b);
        Sprite pl = new Sprite(2); pl.x = getWidth() * 0.58f; pl.y = getHeight() * 0.1f; pl.vx = -20; sprites.add(pl);
        Sprite bal = new Sprite(3); bal.x = getWidth() * 0.86f; bal.y = getHeight() * 0.45f; bal.vx = -4; bal.vy = -2; sprites.add(bal);
        Sprite sh = new Sprite(4); sh.x = getWidth() * 0.3f; sh.y = getHeight() * 0.06f; sh.vx = 260; sh.vy = 90; sh.age = 0.25f; sprites.add(sh);
        invalidate();
    }

    private void seed() {
        if (seeded || getWidth() == 0) return;
        seeded = true;
        for (int i = 0; i < 7; i++) {
            Sprite s = new Sprite(0);
            s.variant = i % 3;
            s.x = rnd.nextFloat() * getWidth();
            s.y = getHeight() * (0.04f + rnd.nextFloat() * 0.5f);
            s.vx = (3 + rnd.nextFloat() * 6) * px / 3 * (s.variant + 1) * 0.6f;
            sprites.add(s);
        }
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); resume(); }
    @Override protected void onDetachedFromWindow() { running = false; super.onDetachedFromWindow(); }
    @Override protected void onWindowVisibilityChanged(int v) { super.onWindowVisibilityChanged(v); if (v == VISIBLE) resume(); else running = false; }

    private void resume() {
        running = isAttachedToWindow() && getWindowVisibility() == VISIBLE;
        last = SystemClock.uptimeMillis();
        if (running) postInvalidateOnAnimation();
    }

    private void step(float dt) {
        int w = getWidth(), h = getHeight();
        nextBirds -= dt; nextPlane -= dt; nextBalloon -= dt; nextShooting -= dt;
        if (nextBirds <= 0) {
            Sprite s = new Sprite(1);
            boolean ltr = rnd.nextBoolean();
            s.x = ltr ? -40 * px : w + 10 * px; s.vx = (ltr ? 1 : -1) * (18 + rnd.nextFloat() * 10) * px;
            s.y = h * (0.12f + rnd.nextFloat() * 0.35f); s.variant = 2 + rnd.nextInt(3); s.seed = rnd.nextLong();
            sprites.add(s); nextBirds = 14 + rnd.nextFloat() * 18;
        }
        if (nextPlane <= 0) {
            Sprite s = new Sprite(2);
            boolean ltr = rnd.nextBoolean();
            s.x = ltr ? -14 * px : w + 4 * px; s.vx = (ltr ? 1 : -1) * (7 + rnd.nextFloat() * 4) * px;
            s.y = h * (0.04f + rnd.nextFloat() * 0.12f);
            sprites.add(s); nextPlane = 40 + rnd.nextFloat() * 35;
        }
        if (nextBalloon <= 0) {
            Sprite s = new Sprite(3);
            s.x = w * (0.2f + rnd.nextFloat() * 0.6f); s.y = h + 4 * px;
            s.vx = (rnd.nextBoolean() ? 1 : -1) * 2.5f * px; s.vy = -3.2f * px;
            sprites.add(s); nextBalloon = 90 + rnd.nextFloat() * 90;
        }
        if (evening() && nextShooting <= 0) {
            Sprite s = new Sprite(4);
            s.x = w * rnd.nextFloat() * 0.7f; s.y = h * rnd.nextFloat() * 0.25f;
            s.vx = (220 + rnd.nextFloat() * 120) * px / 3; s.vy = s.vx * 0.35f;
            sprites.add(s); nextShooting = 12 + rnd.nextFloat() * 20;
        }
        for (Iterator<Sprite> it = sprites.iterator(); it.hasNext(); ) {
            Sprite s = it.next();
            s.age += dt; s.x += s.vx * dt; s.y += s.vy * dt;
            if (s.kind == 0) { // Wolken laufen endlos durch
                float cw = 14 * px;
                if (s.vx > 0 && s.x > w) s.x = -cw;
                continue;
            }
            boolean gone = s.x < -60 * px || s.x > w + 60 * px || s.y < -30 * px || (s.kind == 4 && s.age > 0.9f);
            if (gone) it.remove();
        }
    }

    @Override protected void onDraw(Canvas c) {
        seed();
        long now = SystemClock.uptimeMillis();
        float dt = Math.min(0.1f, (now - last) / 1000f);
        last = now;
        if (forceHour == null && animated) { step(dt); if (weather == Mascot.RAIN || weather == Mascot.SNOW) { seedDrops(); stepDrops(dt); } }
        float t = now / 1000f;
        int w = getWidth(), h = getHeight();

        // Mond (abends/nachts) oder Sonne, zwischen Begrüßung und Uhr
        if (evening()) {
            // heller Mond mit sanftem Schein
            float mx = w * 0.5f, my = h * 0.07f, ms = px * 1.3f;
            float cx = mx + 4.5f * ms, cy = my + 4.5f * ms, r = 13 * ms;   // Mitte des Vollmonds
            if (glow.getShader() == null || glowKey != cx * 31 + cy * 7 + r) {
                glowKey = cx * 31 + cy * 7 + r;
                glow.setShader(new android.graphics.RadialGradient(cx, cy, r, GLOW_COLORS, GLOW_STOPS,
                        android.graphics.Shader.TileMode.CLAMP));
            }
            c.drawCircle(cx, cy, r, glow);
            for (int row = 0; row < MOON.length; row++) {
                for (int k = 0; k < MOON[row].length(); k++) {
                    char ch = MOON[row].charAt(k);
                    if (ch == '.') continue;
                    paint.setColor(ch == 'C' ? 0xFFC9D2E6 : 0xFFF2F5FF);
                    rect.set(mx + k * ms, my + row * ms, mx + (k + 1) * ms + 0.5f, my + (row + 1) * ms + 0.5f);
                    c.drawRect(rect, paint);
                }
            }
        }
        else { paint.setColor(0x33FFD27A); draw(c, SUN, w * 0.5f, h * 0.07f, px * 1.3f); }

        for (Sprite s : sprites) {
            switch (s.kind) {
                case 0: {
                    String[] shape = s.variant == 0 ? CLOUD_S : s.variant == 1 ? CLOUD_M : CLOUD_L;
                    int cloudA = (weather == Mascot.RAIN ? 0x22 : 0x10) + s.variant * 0x06;
                    paint.setColor(cloudA << 24 | (weather == Mascot.RAIN ? 0xB3B7BE : 0xFFFFFF));
                    draw(c, shape, s.x, s.y, px * (1 + s.variant * 0.35f));
                    break;
                }
                case 1: { // Schwarm: versetzte Vögel, Flügelschlag, leichte Welle
                    for (int k = 0; k < s.variant; k++) {
                        float bx = s.x - Math.signum(s.vx) * k * 9 * px;
                        float by = s.y + ((k % 2 == 0) ? k * 2.5f : -k * 2f) * px + (float) Math.sin(t * 2 + k) * 2 * px;
                        boolean up = ((int) (t * 6 + k)) % 2 == 0;
                        paint.setColor(0x80C9CCD2);
                        draw(c, up ? BIRD_UP : BIRD_DOWN, bx, by, px * 0.9f);
                    }
                    break;
                }
                case 2: { // Flugzeug mit Kondensstreifen und Lichtern
                    boolean ltr = s.vx > 0;
                    paint.setColor(0x1AFFFFFF);
                    float trailLen = 40 * px, ty = s.y + 2.5f * px;
                    for (float d = 0; d < trailLen; d += px * 1.5f) {
                        float x = ltr ? s.x - d : s.x + 10 * px + d;
                        int a = Math.round(0x22 * (1 - d / trailLen));
                        paint.setColor(a << 24 | 0xFFFFFF);
                        rect.set(x, ty, x + px, ty + px * 0.6f); c.drawRect(rect, paint);
                    }
                    paint.setColor(0x8CB3B7BE);
                    drawMirrored(c, PLANE, s.x, s.y, px * 0.7f, !ltr);
                    if (((int) (t * 2)) % 2 == 0) {
                        paint.setColor(0xCCFF5A5A);
                        rect.set(s.x + 5 * px * 0.7f, s.y + 4 * px * 0.7f, s.x + 6 * px * 0.7f, s.y + 5 * px * 0.7f); c.drawRect(rect, paint);
                    }
                    break;
                }
                case 3: { // Heißluftballon in Orange/Blau
                    float sway = (float) Math.sin(t * 0.8) * 2 * px;
                    drawColored(c, BALLOON, s.x + sway, s.y, px * 1.1f);
                    break;
                }
                case 4: { // Sternschnuppe
                    float life = s.age / 0.9f;
                    int a = Math.round(220 * (1 - life));
                    for (int k = 0; k < 10; k++) {
                        float f = k / 10f;
                        paint.setColor(Math.round(a * (1 - f)) << 24 | 0xFFF2C8);
                        float x = s.x - s.vx * 0.012f * k, y = s.y - s.vy * 0.012f * k;
                        rect.set(x, y, x + px * (1 - f * 0.6f), y + px * (1 - f * 0.6f)); c.drawRect(rect, paint);
                    }
                    break;
                }
                default: break;
            }
        }

        drawDrops(c);

        // Glühwürmchen abends/nachts im unteren Drittel
        if (evening() && weather == Mascot.CLEAR) {
            for (int k = 0; k < 9; k++) {
                float fx = FLY_X[k] * w + (float) Math.sin(t * 0.4 + k) * 12 * px;
                float fy = h * FLY_Y[k] + (float) Math.cos(t * 0.5 + k * 2) * 6 * px;
                float glow = (float) (0.5 + 0.5 * Math.sin(t * 1.7 + k * 1.3));
                paint.setColor(Math.round(110 * glow) << 24 | 0xFFE07A);
                rect.set(fx, fy, fx + px * 0.8f, fy + px * 0.8f); c.drawRect(rect, paint);
            }
        }

        if (running && forceHour == null && animated) postInvalidateDelayed(66); // ~15 Bilder/s reichen für den Himmel
    }

    private void draw(Canvas c, String[] shape, float x, float y, float size) {
        drawMirrored(c, shape, x, y, size, false);
    }

    private void drawMirrored(Canvas c, String[] shape, float x, float y, float size, boolean mirror) {
        for (int r = 0; r < shape.length; r++) {
            String line = shape[r];
            for (int k = 0; k < line.length(); k++) {
                char ch = line.charAt(mirror ? line.length() - 1 - k : k);
                if (ch == '.') continue;
                rect.set(x + k * size, y + r * size, x + (k + 1) * size + 0.5f, y + (r + 1) * size + 0.5f);
                c.drawRect(rect, paint);
            }
        }
    }

    private void drawColored(Canvas c, String[] shape, float x, float y, float size) {
        for (int r = 0; r < shape.length; r++) {
            String line = shape[r];
            for (int k = 0; k < line.length(); k++) {
                char ch = line.charAt(k);
                int col;
                switch (ch) {
                    case 'O': col = 0x99FF8A1F; break;
                    case 'B': col = 0x998FB4FF; break;
                    case 'W': col = 0x66B3B7BE; break;
                    case 'K': col = 0x99A0663A; break;
                    default: continue;
                }
                paint.setColor(col);
                rect.set(x + k * size, y + r * size, x + (k + 1) * size + 0.5f, y + (r + 1) * size + 0.5f);
                c.drawRect(rect, paint);
            }
        }
    }
}
