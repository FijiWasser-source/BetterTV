package de.klar.tvlauncher;

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
 * "Pixel", das Maskottchen der Startansicht (eigene Figur im Pixel-Stil der Uhr),
 * in einem kleinen Endlos-Jump-and-Run: rennt über eine Pixel-Laufbahn, springt
 * automatisch über Hindernisse, sammelt Sterne, stolpert ab und zu und schläft nachts.
 * Läuft mit ~30 Bildern/s und nur, solange die Startansicht sichtbar ist.
 */
public class Mascot extends View {

    // Körper 14×12 Zellen. B = Körper, H = Glanz oben, S = Schatten unten, A = Antenne, L/R = Beine
    private static final String[] BODY = {
            "......A.......",
            "......A.......",
            "...HHHHHHHH...",
            "..BBBBBBBBBB..",
            ".BBBBBBBBBBBB.",
            ".BBBBBBBBBBBB.",
            ".BBBBBBBBBBBB.",
            ".BBBBBBBBBBBB.",
            ".BBBBBBBBBBBB.",
            "..SSSSSSSSSS..",
    };

    enum Emote { NONE, HEART, NOTE, STAR, BANG, QUESTION, SLEEP }

    private static final String[][] EMOTE_ICONS = {
            {},
            {".XX.XX.", "XXXXXXX", "XXXXXXX", ".XXXXX.", "..XXX..", "...X..."},
            {"...XXX.", "...X.X.", "...X..X", "...X...", ".XXX...", "XXXX...", ".XX...."},
            {"...X...", "...X...", "XXXXXXX", ".XXXXX.", ".XX.XX.", "X.....X"},
            {"..XX...", "..XX...", "..XX...", "..XX...", ".......", "..XX..."},
            {".XXXX..", "XX..XX.", "...XX..", "..XX...", ".......", "..XX..."},
            {"XXXX...", "..X....", ".X.....", "XXXX...", "....XXX", ".....X.", "....XXX"},
    };
    private static final int[] EMOTE_COLORS = {0, 0xFFE5484D, 0xFF1B2640, 0xFFFF8A1F, 0xFFFF8A1F, 0xFF1B2640, 0xFF6E7BA8};
    private static final String[] STAR = {"..X..", ".XXX.", "XXXXX", ".XXX.", ".X.X."};

    private static final int BODY_COLOR = 0xFFF0884A, HIGHLIGHT = 0xFFFFA86B, SHADOW = 0xFFD06A33;
    private static final int INK = 0xFF2E2922, BUBBLE = 0xFF9CBDFF;

    // Spielwelt in Zellen
    private static final float SPEED = 14f;         // Zellen pro Sekunde
    private static final float JUMP_V = 36f, GRAVITY = 56f; // ~1,3 s Flugzeit, Scheitel ~11,6 Zellen
    private static final float HERO_X = 16f;        // linker Rand der Figur auf der Bahn
    // Trefferfläche = Bereich der Beine (Spalten 2–11), damit Sprünge fair wirken
    private static final float HERO_L_OFF = 2f, HERO_R = HERO_X + 12f;
    private static final float GROUND_FROM_BOTTOM = 6f;

    static final class Thing {
        final int type; // 0 Stein (3×2), 1 Kiste (3×3), 2 Block (2×4), 3 Stern
        float x; final float y; boolean gone, hit, miss;
        Thing(int type, float x, float y) { this.type = type; this.x = x; this.y = y; }
        float w() { return type == 0 ? 3 : type == 1 ? 3 : type == 2 ? 2 : 5 * 0.8f; }
        float h() { return type == 0 ? 2 : type == 1 ? 3 : type == 2 ? 4 : 5 * 0.8f; }
    }

    private final Paint paint = new Paint(); // bewusst ohne Kantenglättung: echte Pixel
    private final Paint soft = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Random rnd = new Random();
    private final List<Thing> things = new ArrayList<>();
    private float cell;

    private boolean running;
    private long start, lastFrame;
    private float scroll, nextSpawn = 20;
    private float jumpY, vy;
    private boolean airborne;
    private float stumble;           // Sekunden, die Pixel noch durchgeschüttelt ist
    private int stars;
    int stumbles, obstaclesSpawned; // für den Test
    private long nextBlink, blinkUntil;
    private long nextEmote, emoteStart; private boolean emoteOn;
    private Emote emote = Emote.NONE;
    private Boolean forceNight; // nur Vorschau

    public Mascot(Context c) { this(c, null); }

    public Mascot(Context c, AttributeSet a) {
        super(c, a);
        cell = 4 * getResources().getDisplayMetrics().density;
        soft.setColor(0x4D000000);
        long now = SystemClock.uptimeMillis();
        start = lastFrame = now;
        nextBlink = now + 2500;
        nextEmote = now + 12000;
    }

    /** Taste 0: Pixel springt selbst. */
    public void jump() { if (!airborne && !night()) { vy = JUMP_V; airborne = true; } }

    /** Kleine Freude, z. B. wenn eine App gestartet wird. */
    public void cheer() { jump(); showEmote(Emote.STAR); }

    /** Früher: Blick zur Kachel. Beim Rennen schaut Pixel nach vorn – bleibt als leere Methode. */
    public void lookAt(float dir) {}

    /** Für den Test: Spiel ohne Bildschirm laufen lassen. */
    int simulate(float seconds, long seed) {
        rnd.setSeed(seed);
        long now = 1_000_000L;
        for (float t = 0; t < seconds; t += 1f / 30) {
            now += 33;
            step(1f / 30, now, false, 120);
            if (Float.isNaN(jumpY) || jumpY < 0 || things.size() > 40) throw new IllegalStateException("Spielzustand kaputt");
        }
        return stars;
    }

    /** Nur für die Vorschau: feste Szene. */
    void preview(Emote e, boolean night) {
        forceNight = night;
        emote = e; emoteOn = e != Emote.NONE; emoteStart = SystemClock.uptimeMillis();
        things.clear();
        things.add(new Thing(1, HERO_X + 20, 0));
        things.add(new Thing(3, HERO_X + 32, 16));
        things.add(new Thing(0, HERO_X + 44, 0));
        things.add(new Thing(2, HERO_X + 62, 0));
        jumpY = 6; stars = 7;
        invalidate();
    }

    private boolean night() {
        if (forceNight != null) return forceNight;
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h >= 23 || h < 6;
    }

    private void showEmote(Emote e) { emote = e; emoteStart = SystemClock.uptimeMillis(); emoteOn = true; }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); resume(); }

    @Override protected void onDetachedFromWindow() { running = false; super.onDetachedFromWindow(); }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) resume(); else running = false;
    }

    private void resume() {
        running = isAttachedToWindow() && getWindowVisibility() == VISIBLE;
        lastFrame = SystemClock.uptimeMillis(); // keine Riesensprünge nach einer Pause
        if (running) postInvalidateOnAnimation();
    }

    @Override protected void onMeasure(int w, int h) {
        int want = Math.round(cell * 120);
        int width = MeasureSpec.getMode(w) == MeasureSpec.UNSPECIFIED ? want : Math.min(want, MeasureSpec.getSize(w));
        setMeasuredDimension(width, resolveSize(Math.round(cell * 46), h));
    }

    // --- Spiel ------------------------------------------------------------------

    private void step(float dt, long now, boolean sleeping, float trackCells) {
        if (now >= nextBlink && !sleeping) { blinkUntil = now + 140; nextBlink = now + 2500 + rnd.nextInt(3500); }
        if (sleeping) {
            if (!emoteOn || now - emoteStart > 2600) showEmote(Emote.SLEEP);
            return;
        }
        if (now >= nextEmote) { showEmote(rnd.nextBoolean() ? Emote.NOTE : Emote.QUESTION); nextEmote = now + 20000 + rnd.nextInt(20000); }

        float speed = stumble > 0 ? SPEED * 0.3f : SPEED;
        stumble = Math.max(0, stumble - dt);
        float dx = speed * dt;
        scroll += dx;

        // neue Dinge am rechten Rand der Bahn
        nextSpawn -= dx;
        if (nextSpawn <= 0) {
            int t = rnd.nextInt(10);
            int type = t < 4 ? 0 : t < 6 ? 1 : t < 7 ? 2 : 3;
            Thing th = new Thing(type, trackCells, type == 3 ? 14 + rnd.nextInt(5) : 0);
            th.miss = type != 3 && rnd.nextInt(100) < 5; // ganz selten verpasst er den Absprung
            things.add(th);
            if (type != 3) obstaclesSpawned++;
            nextSpawn = 16 + rnd.nextInt(22);
        }
        for (Iterator<Thing> it = things.iterator(); it.hasNext(); ) {
            Thing th = it.next();
            th.x -= dx;
            if (th.x + th.w() < 0 || th.gone) it.remove();
        }

        // automatisch springen: Abstand von der Körpervorderkante bis zum Ding
        if (!airborne) {
            for (Thing th : things) {
                float gap = th.x - HERO_R;
                boolean window = th.type == 3 ? gap > 0.5f && gap < 3f : gap > 1.8f && gap < 2.8f;
                if (window && !th.miss) { jump(); break; }
            }
        }

        // Sprungphysik
        if (airborne) {
            vy -= GRAVITY * dt;
            jumpY += vy * dt;
            if (jumpY <= 0) { jumpY = 0; vy = 0; airborne = false; }
        }

        // Treffer: Hindernis -> Stolpern, Stern -> einsammeln
        float heroL = HERO_X + HERO_L_OFF, heroR = HERO_R, feet = jumpY, head = jumpY + 12;
        for (Thing th : things) {
            boolean overlapX = th.x < heroR && th.x + th.w() > heroL;
            if (!overlapX) continue;
            if (th.type == 3) {
                if (th.y < head && th.y + th.h() > feet) {
                    th.gone = true;
                    stars++;
                    showEmote(stars % 10 == 0 ? Emote.HEART : Emote.STAR);
                }
            } else if (!th.hit && feet < th.h() - 0.3f) {
                th.hit = true; // pro Hindernis höchstens einmal stolpern
                stumble = 0.9f;
                stumbles++;
                showEmote(Emote.BANG);
                vy = 14; airborne = true; // kleiner Hüpfer beim Stolpern
            }
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        float dt = Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        boolean sleeping = night();
        float trackCells = getWidth() / cell;
        if (forceNight == null) step(dt, now, sleeping, trackCells);

        float ground = getHeight() - GROUND_FROM_BOTTOM * cell;
        drawTrack(canvas, ground, sleeping);

        // Dinge auf der Bahn (nur innerhalb der Bahn sichtbar)
        for (Thing th : things) {
            if (th.x < 1 || th.x + th.w() > trackCells - 1) continue;
            drawThing(canvas, th, ground);
        }

        // Figur
        float t = (now - start) / 1000f;
        float ox = HERO_X * cell;
        float bob = airborne || sleeping ? 0 : (float) Math.abs(Math.sin(t * Math.PI * 3)) * cell * 0.6f;
        float sit = sleeping ? cell * 1.5f : 0;
        float shake = stumble > 0 ? (float) Math.sin(t * 60) * cell * 0.5f : 0;
        float oy = ground - 12 * cell - jumpY * cell - bob + sit;

        // Schatten (wird beim Springen kleiner)
        float sw = 12 * cell * Math.max(0.3f, 1 - jumpY / 14f);
        rect.set(ox + 7 * cell - sw / 2, ground - cell * 0.5f, ox + 7 * cell + sw / 2, ground + cell * 0.6f);
        canvas.drawOval(rect, soft);

        drawHero(canvas, ox + shake, oy, now, t, sleeping);

        // Sternzähler in Pixel-Schrift
        paint.setColor(0xFFFFC24A);
        drawIcon(canvas, STAR, cell * 2.5f, ground - cell * 4.2f, cell * 0.7f);
        paint.setColor(0xFFD7DAE0);
        PixelText.draw(canvas, String.valueOf(stars), cell * 7f, ground - cell * 4.2f, cell * 0.5f, paint);

        drawEmote(canvas, now, ox + 14 * cell + cell * 0.5f, oy - cell * 2.5f);

        if (running) postInvalidateDelayed(33); // ~30 Bilder/s reichen und schonen den TV
    }

    private void drawTrack(Canvas c, float ground, boolean sleeping) {
        float left = cell, right = getWidth() - cell;
        // Oberfläche + Seitenwand, Enden abgestuft wie Pixel-Art
        int[] colors = {0xFFA3A7AE, 0xFF868A92, 0xFF4A4D54, 0xFF3C3E44, 0xFF2E3035};
        for (int r = 0; r < colors.length; r++) {
            float inset = r == 0 ? cell : r >= 3 ? cell * (r - 2) : 0;
            paint.setColor(colors[r]);
            rect.set(left + inset, ground + r * cell, right - inset, ground + (r + 1) * cell + 0.5f);
            c.drawRect(rect, paint);
        }
        // Kiesel auf der Oberfläche scrollen mit
        paint.setColor(0xFF767A82);
        float period = 9 * cell;
        float off = -((scroll * cell) % period);
        for (float x = left + off; x < right; x += period) {
            if (x < left + cell) continue;
            px(c, x, ground + cell);
            if (x + 4 * cell < right - cell) px(c, x + 4 * cell, ground + cell * 1.5f, 0.5f);
        }
    }

    private void drawThing(Canvas c, Thing th, float ground) {
        float x = th.x * cell;
        if (th.type == 3) {
            float bobS = (float) Math.sin((SystemClock.uptimeMillis() - start) / 300.0 + th.x) * cell * 0.4f;
            paint.setColor(0xFFFFC24A);
            drawIcon(c, STAR, x, ground - (th.y + th.h()) * cell + bobS, cell * 0.8f);
            return;
        }
        float w = th.w() * cell, h = th.h() * cell, top = ground - h;
        if (th.type == 1) { // Kiste
            paint.setColor(0xFFC98A4B); rect.set(x, top, x + w, ground); c.drawRect(rect, paint);
            paint.setColor(0xFF9C6534);
            rect.set(x, top, x + w, top + cell * 0.5f); c.drawRect(rect, paint);
            rect.set(x + w / 2 - cell * 0.25f, top, x + w / 2 + cell * 0.25f, ground); c.drawRect(rect, paint);
        } else { // Stein / Block
            paint.setColor(0xFF6C7078); rect.set(x, top, x + w, ground); c.drawRect(rect, paint);
            paint.setColor(0xFF8E929A); rect.set(x, top, x + w - cell, top + cell); c.drawRect(rect, paint);
            paint.setColor(0xFF4F5259); rect.set(x + w - cell, top + cell, x + w, ground); c.drawRect(rect, paint);
        }
    }

    private void drawHero(Canvas c, float ox, float oy, long now, float t, boolean sleeping) {
        for (int row = 0; row < BODY.length; row++) {
            String line = BODY[row];
            for (int col = 0; col < line.length(); col++) {
                char ch = line.charAt(col);
                if (ch == '.') continue;
                paint.setColor(ch == 'A' ? (row == 0 ? 0xFFFFC07A : SHADOW) : ch == 'H' ? HIGHLIGHT
                        : ch == 'S' ? SHADOW : BODY_COLOR);
                px(c, ox + col * cell, oy + row * cell);
            }
        }
        // Beine: im Lauf abwechselnd, in der Luft angezogen, im Schlaf gesessen
        paint.setColor(SHADOW);
        float ly = oy + 10 * cell;
        if (sleeping) {
            px(c, ox + 2 * cell, ly - cell * 0.5f); px(c, ox + 11 * cell, ly - cell * 0.5f);
        } else if (airborne) {
            px(c, ox + 3 * cell, ly); px(c, ox + 4 * cell, ly);
            px(c, ox + 9 * cell, ly); px(c, ox + 10 * cell, ly);
        } else {
            boolean a = ((now / 120) % 2) == 0;
            px(c, ox + (a ? 2 : 3) * cell, ly); px(c, ox + (a ? 2 : 3) * cell, ly + cell);
            px(c, ox + (a ? 10 : 9) * cell, ly); px(c, ox + (a ? 11 : 10) * cell, ly + cell);
        }
        // Arme schwingen beim Rennen
        boolean swing = !sleeping && ((now / 160) % 2 == 0);
        px(c, ox, oy + (swing ? 5 : 6) * cell);
        px(c, ox + 13 * cell, oy + (swing ? 6 : 5) * cell);

        // Antennenspitze leuchtet
        int glow = (int) Math.round(140 + 115 * (0.5 + 0.5 * Math.sin(t * 3)));
        soft.setColor((glow << 24) | 0xFFD27A);
        rect.set(ox + 6 * cell - cell * 0.3f, oy - cell * 1.3f, ox + 7 * cell + cell * 0.3f, oy - cell * 0.1f);
        c.drawOval(rect, soft);
        soft.setColor(0x4D000000);

        // Augen (schauen nach vorn), Mund, Wangen
        boolean closed = sleeping || now < blinkUntil;
        float ey = oy + 5 * cell;
        for (int eye = 0; eye < 2; eye++) {
            float ex = ox + (eye == 0 ? 4 : 10) * cell;
            paint.setColor(INK);
            if (closed) {
                rect.set(ex, ey + cell, ex + 2 * cell, ey + cell * 1.5f);
                c.drawRect(rect, paint);
            } else {
                rect.set(ex, ey, ex + 2 * cell, ey + 2 * cell);
                c.drawRect(rect, paint);
                paint.setColor(0xFFFFFFFF);
                rect.set(ex + cell, ey + cell * 0.25f, ex + cell * 1.6f, ey + cell * 0.85f);
                c.drawRect(rect, paint);
            }
        }
        paint.setColor(INK);
        float my = oy + 8 * cell;
        if (stumble > 0 || (emote == Emote.BANG && emoteActive(now))) {
            rect.set(ox + 7 * cell, my - cell * 0.2f, ox + 8.5f * cell, my + cell);
            c.drawRect(rect, paint);
        } else if (!sleeping) {
            px(c, ox + 6 * cell, my - cell * 0.4f, 0.5f);
            rect.set(ox + 6.5f * cell, my, ox + 9.5f * cell, my + cell * 0.5f);
            c.drawRect(rect, paint);
            px(c, ox + 9.5f * cell, my - cell * 0.4f, 0.5f);
        }
        soft.setColor(0x55FF5A5A);
        rect.set(ox + 2.4f * cell, ey + 2.4f * cell, ox + 3.6f * cell, ey + 3f * cell); c.drawOval(rect, soft);
        rect.set(ox + 11.6f * cell, ey + 2.4f * cell, ox + 12.8f * cell, ey + 3f * cell); c.drawOval(rect, soft);
        soft.setColor(0x4D000000);
    }

    private boolean emoteActive(long now) {
        return emote != Emote.NONE && emoteOn && (forceNight != null || now - emoteStart < 2200);
    }

    private void drawEmote(Canvas c, long now, float x, float y) {
        if (!emoteActive(now)) return;
        float p = forceNight != null ? 0.5f : (now - emoteStart) / 2200f;
        float rise = Math.min(1f, p * 5) * cell * 2;
        int alpha = p < 0.12f ? Math.round(p / 0.12f * 255) : p > 0.85f ? Math.round((1 - p) / 0.15f * 255) : 255;
        String[] icon = EMOTE_ICONS[emote.ordinal()];
        float ic = cell * 0.8f;
        float bw = 10 * ic, bh = 10 * ic;
        float bx = x - bw / 2, by = y - bh - rise;
        soft.setColor((alpha << 24) | (BUBBLE & 0xFFFFFF));
        rect.set(bx, by, bx + bw, by + bh);
        c.drawRoundRect(rect, ic * 2.5f, ic * 2.5f, soft);
        rect.set(bx + ic, by + bh - ic * 0.2f, bx + ic * 2.4f, by + bh + ic * 1.2f);
        c.drawRect(rect, soft);
        soft.setColor(0x4D000000);
        paint.setColor((alpha << 24) | (EMOTE_COLORS[emote.ordinal()] & 0xFFFFFF));
        drawIcon(c, icon, bx + ic * 1.5f, by + ic * 1.5f + (7 - icon.length) * ic / 2f, ic);
    }

    private void drawIcon(Canvas c, String[] icon, float x, float y, float size) {
        for (int r = 0; r < icon.length; r++) {
            for (int col = 0; col < icon[r].length(); col++) {
                if (icon[r].charAt(col) != 'X') continue;
                rect.set(x + col * size, y + r * size, x + (col + 1) * size, y + (r + 1) * size);
                c.drawRect(rect, paint);
            }
        }
    }

    private void px(Canvas c, float x, float y) { px(c, x, y, 1f); }

    private void px(Canvas c, float x, float y, float size) {
        rect.set(Math.round(x), Math.round(y), Math.round(x + cell * size) + 0.5f, Math.round(y + cell * size) + 0.5f);
        c.drawRect(rect, paint);
    }
}
