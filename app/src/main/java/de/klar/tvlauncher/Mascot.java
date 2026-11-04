package de.klar.tvlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Calendar;
import java.util.Random;

/**
 * "Pixel", das kleine Maskottchen der Startansicht (eigene Figur im Pixel-Stil der Uhr).
 * Wippt, blinzelt, hüpft, winkt, schaut zur gewählten Kachel, zeigt ab und zu ein Emote
 * und schläft nachts. Läuft nur, solange es sichtbar ist.
 */
public class Mascot extends View {

    // Körper 14×12 Zellen. B = Körper, H = Glanz oben, S = Schatten unten, A = Antenne, L = Beine
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
            "...LL....LL...",
            "...LL....LL...",
    };

    enum Emote { NONE, HEART, NOTE, STAR, BANG, QUESTION, SLEEP }

    private static final String[][] EMOTE_ICONS = {
            {},
            {".XX.XX.", "XXXXXXX", "XXXXXXX", ".XXXXX.", "..XXX..", "...X..."},          // Herz
            {"...XXX.", "...X.X.", "...X..X", "...X...", ".XXX...", "XXXX...", ".XX...."}, // Note
            {"...X...", "...X...", "XXXXXXX", ".XXXXX.", ".XX.XX.", "X.....X"},           // Stern
            {"..XX...", "..XX...", "..XX...", "..XX...", ".......", "..XX..."},           // !
            {".XXXX..", "XX..XX.", "...XX..", "..XX...", ".......", "..XX..."},           // ?
            {"XXXX...", "..X....", ".X.....", "XXXX...", "....XXX", ".....X.", "....XXX"}, // Zz
    };
    private static final int[] EMOTE_COLORS = {0, 0xFFE5484D, 0xFF3B342B, 0xFFFF8A1F, 0xFFFF8A1F, 0xFF3B342B, 0xFF6E7BA8};

    private static final int BODY_COLOR = 0xFFF0884A;
    private static final int HIGHLIGHT = 0xFFFFA86B;
    private static final int SHADOW = 0xFFD06A33;
    private static final int INK = 0xFF2E2922;
    private static final int BUBBLE = 0xFFF2E6CF;

    private final Paint paint = new Paint(); // bewusst ohne Kantenglättung: echte Pixel
    private final RectF rect = new RectF();
    private final Random rnd = new Random();
    private float cell;

    private boolean running;
    private long start;
    private long nextBlink, blinkUntil;
    private long nextHop, hopStart; private boolean hopping;
    private long nextWave, waveStart; private boolean waveOn;
    private long nextEmote, emoteStart; private boolean emoteOn;
    private Emote emote = Emote.NONE;
    private float look;        // -1 = links, 1 = rechts
    private float lookTarget;
    private Boolean forceNight; // für Vorschau/Tests

    public Mascot(Context c) { this(c, null); }

    public Mascot(Context c, AttributeSet a) {
        super(c, a);
        cell = 5 * getResources().getDisplayMetrics().density;
        long now = SystemClock.uptimeMillis();
        start = now;
        nextBlink = now + 2500;
        nextHop = now + 9000;
        nextWave = now + 5000;
        nextEmote = now + 3500;
    }

    /** Blickrichtung zur fokussierten Kachel: -1 (links) … 1 (rechts). */
    public void lookAt(float dir) { lookTarget = Math.max(-1f, Math.min(1f, dir)); invalidate(); }

    /** Kurze Reaktion, z. B. wenn eine App gestartet wird. */
    public void cheer() { showEmote(Emote.STAR); hopStart = SystemClock.uptimeMillis(); hopping = true; }

    /** Nur für die Vorschau: festen Zustand zeigen. */
    void preview(Emote e, boolean night) {
        forceNight = night;
        emote = e;
        emoteStart = SystemClock.uptimeMillis();
        emoteOn = true;
        invalidate();
    }

    private boolean night() {
        if (forceNight != null) return forceNight;
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h >= 23 || h < 6;
    }

    private void showEmote(Emote e) { emote = e; emoteStart = SystemClock.uptimeMillis(); emoteOn = true; }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); running = true; postInvalidateOnAnimation(); }

    @Override protected void onDetachedFromWindow() { running = false; super.onDetachedFromWindow(); }

    @Override protected void onVisibilityChanged(View v, int visibility) {
        super.onVisibilityChanged(v, visibility);
        running = visibility == VISIBLE && isAttachedToWindow();
        if (running) postInvalidateOnAnimation();
    }

    /** Pausiert, sobald eine andere App im Vordergrund ist. */
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        running = visibility == VISIBLE && isAttachedToWindow();
        if (running) postInvalidateOnAnimation();
    }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(resolveSize(Math.round(cell * 26), w), resolveSize(Math.round(cell * 34), h));
    }

    private void schedule(long now, boolean sleeping) {
        if (now >= nextBlink && !sleeping) { blinkUntil = now + 140; nextBlink = now + 2500 + rnd.nextInt(3500); }
        if (now >= nextHop && !sleeping) { hopStart = now; hopping = true; nextHop = now + 14000 + rnd.nextInt(14000); }
        if (now >= nextWave && !sleeping) { waveStart = now; waveOn = true; nextWave = now + 18000 + rnd.nextInt(18000); }
        if (now >= nextEmote) {
            Emote[] pool = sleeping ? new Emote[]{Emote.SLEEP}
                    : new Emote[]{Emote.HEART, Emote.NOTE, Emote.STAR, Emote.BANG, Emote.QUESTION, Emote.HEART};
            showEmote(pool[rnd.nextInt(pool.length)]);
            nextEmote = now + (sleeping ? 5000 : 9000 + rnd.nextInt(9000));
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        boolean sleeping = night();
        if (forceNight == null) schedule(now, sleeping);
        look += (lookTarget - look) * 0.15f;

        float t = (now - start) / 1000f;
        float bob = (float) Math.sin(t * Math.PI / (sleeping ? 2.2 : 1.2)) * cell * (sleeping ? 0.25f : 0.5f);
        float hop = 0, squash = 0;
        if (hopping) {
            float p = (now - hopStart) / 520f;
            if (p >= 1) hopping = false;
            else { hop = (float) Math.sin(p * Math.PI) * cell * 3.2f; squash = p < 0.12f || p > 0.88f ? 0.6f : 0; }
        }
        boolean waving = waveOn && now - waveStart < 1600;
        if (waveOn && !waving) waveOn = false;

        float w = 14 * cell, h = 12 * cell;
        float ox = (getWidth() - w) / 2f;
        float ground = getHeight() - cell * 7.5f; // Boden etwas über dem unteren Rand -> Figur mittig im freien Raum
        float oy = ground - h - hop + bob;

        drawPlatform(canvas, getWidth() / 2f, ground);

        // Schatten auf dem Sockel (wird beim Hüpfen kleiner)
        paint.setColor(0x4D000000);
        float sw = w * (0.75f - hop / (cell * 20));
        rect.set(getWidth() / 2f - sw / 2, ground - cell * 0.5f, getWidth() / 2f + sw / 2, ground + cell * 0.7f);
        canvas.drawOval(rect, paint);

        // Körper
        for (int row = 0; row < BODY.length; row++) {
            String line = BODY[row];
            for (int col = 0; col < line.length(); col++) {
                char ch = line.charAt(col);
                if (ch == '.') continue;
                if (ch == 'A') paint.setColor(row == 0 ? 0xFFFFC07A : SHADOW);
                else if (ch == 'H') paint.setColor(HIGHLIGHT);
                else if (ch == 'S' || ch == 'L') paint.setColor(SHADOW);
                else paint.setColor(BODY_COLOR);
                float y = oy + row * cell + (ch == 'L' ? 0 : squash * cell);
                px(canvas, ox + col * cell, y);
            }
        }
        // Antennen-Spitze leuchtet sanft
        int glow = (int) Math.round(140 + 115 * (0.5 + 0.5 * Math.sin(t * 3)));
        paint.setColor((glow << 24) | 0xFFD27A);
        rect.set(ox + 6 * cell - cell * 0.3f, oy - cell * 1.3f, ox + 7 * cell + cell * 0.3f, oy - cell * 0.1f);
        canvas.drawOval(rect, paint);

        // Arme (beim Winken hebt sich der rechte)
        paint.setColor(SHADOW);
        px(canvas, ox, oy + 6 * cell + squash * cell);
        px(canvas, ox - cell, oy + 7 * cell + squash * cell);
        if (waving) {
            boolean up = ((now - waveStart) / 200) % 2 == 0;
            px(canvas, ox + 13 * cell, oy + 5 * cell);
            px(canvas, ox + 14 * cell, oy + (up ? 3 : 4) * cell);
        } else {
            px(canvas, ox + 13 * cell, oy + 6 * cell + squash * cell);
            px(canvas, ox + 14 * cell, oy + 7 * cell + squash * cell);
        }

        // Augen: schauen zur Kachel, blinzeln, nachts zu
        int shift = Math.round(look);
        boolean closed = sleeping || now < blinkUntil;
        float ey = oy + 5 * cell + squash * cell;
        for (int eye = 0; eye < 2; eye++) {
            float ex = ox + (eye == 0 ? 3 : 9) * cell + shift * cell;
            paint.setColor(INK);
            if (closed) {
                rect.set(ex, ey + cell, ex + 2 * cell, ey + cell * 1.5f);
                canvas.drawRect(rect, paint);
            } else {
                rect.set(ex, ey, ex + 2 * cell, ey + 2 * cell);
                canvas.drawRoundRect(rect, cell * 0.4f, cell * 0.4f, paint);
                paint.setColor(0xFFFFFFFF);
                rect.set(ex + cell * 0.25f, ey + cell * 0.25f, ex + cell * 0.85f, ey + cell * 0.85f);
                canvas.drawRect(rect, paint);
            }
        }
        // Mund: Lächeln, bei "!" ein kleines "o"
        paint.setColor(INK);
        float my = oy + 8 * cell + squash * cell;
        if (emote == Emote.BANG && emoteActive(now)) {
            rect.set(ox + 6.25f * cell, my - cell * 0.2f, ox + 7.75f * cell, my + cell);
            canvas.drawOval(rect, paint);
        } else if (!sleeping) {
            px(canvas, ox + 5 * cell + shift * cell, my - cell * 0.4f, 0.5f);
            rect.set(ox + 5.5f * cell + shift * cell, my, ox + 8.5f * cell + shift * cell, my + cell * 0.5f);
            canvas.drawRect(rect, paint);
            px(canvas, ox + 8.5f * cell + shift * cell, my - cell * 0.4f, 0.5f);
        }
        // Wangen
        paint.setColor(0x55FF5A5A);
        rect.set(ox + 1.6f * cell, ey + 2.4f * cell, ox + 2.8f * cell, ey + 3f * cell);
        canvas.drawOval(rect, paint);
        rect.set(ox + 11.2f * cell, ey + 2.4f * cell, ox + 12.4f * cell, ey + 3f * cell);
        canvas.drawOval(rect, paint);

        drawEmote(canvas, now, ox + w + cell * 0.5f, oy - cell * 2.5f);

        if (running) postInvalidateDelayed(33); // ~30 Bilder/s reichen und schonen den TV
    }

    // Sockel: Ellipse (Oberseite) mit Seitenwand, in Grautönen und Pixel-Kanten
    private static final float PLATFORM_W = 24f, PLATFORM_TOP = 7f, PLATFORM_DEPTH = 3f;

    private void drawPlatform(Canvas canvas, float cx, float ground) {
        int cols = (int) PLATFORM_W, rows = (int) PLATFORM_TOP;
        float left = cx - cols * cell / 2f;
        float top = ground - rows * cell / 2f;
        // Breite jeder Ellipsen-Zeile (in Zellen, gerade Anzahl -> symmetrisch)
        int[] widths = new int[rows];
        for (int r = 0; r < rows; r++) {
            float y = (r + 0.5f - rows / 2f) / (rows / 2f);
            int w = Math.round(cols * (float) Math.sqrt(Math.max(0, 1 - y * y)));
            widths[r] = Math.max(2, w - (w % 2));
        }
        // Seitenwand: untere Ellipsenhälfte, um die Tiefe nach unten versetzt
        for (int d = (int) PLATFORM_DEPTH; d >= 1; d--) {
            for (int r = rows / 2; r < rows; r++) {
                // Seitenwand dunkler, nach unten noch dunkler
                paint.setColor(d == (int) PLATFORM_DEPTH ? 0xFF2E3035 : d == 1 ? 0xFF4A4D54 : 0xFF3C3E44);
                row(canvas, left, top + (r + d) * cell, cols, widths[r]);
            }
        }
        // Oberseite: hell, oben mit Lichtkante, zur Vorderkante etwas dunkler
        for (int r = 0; r < rows; r++) {
            paint.setColor(r == 0 ? 0xFFA3A7AE : r <= rows / 2 ? 0xFF868A92 : 0xFF767A82);
            row(canvas, left, top + r * cell, cols, widths[r]);
        }
        // feine Lichtkante an den oberen Rändern
        paint.setColor(0xFFB3B7BE);
        int w0 = widths[0], w1 = widths[1];
        px(canvas, left + (cols - w1) / 2f * cell, top + cell);
        px(canvas, left + (cols + w1) / 2f * cell - cell, top + cell);
        rect.set(left + (cols - w0) / 2f * cell, top, left + (cols + w0) / 2f * cell, top + cell * 0.35f);
        canvas.drawRect(rect, paint);
    }

    private void row(Canvas canvas, float left, float y, int cols, int width) {
        float x0 = left + (cols - width) / 2f * cell;
        rect.set(Math.round(x0), Math.round(y), Math.round(x0 + width * cell) + 0.5f, Math.round(y + cell) + 0.5f);
        canvas.drawRect(rect, paint);
    }

    private boolean emoteActive(long now) {
        return emote != Emote.NONE && emoteOn && (forceNight != null || now - emoteStart < 2600);
    }

    private void drawEmote(Canvas canvas, long now, float x, float y) {
        if (!emoteActive(now)) return;
        float p = forceNight != null ? 0.5f : (now - emoteStart) / 2600f;
        float rise = Math.min(1f, p * 5) * cell * 2;
        int alpha = p < 0.12f ? Math.round(p / 0.12f * 255) : p > 0.85f ? Math.round((1 - p) / 0.15f * 255) : 255;
        String[] icon = EMOTE_ICONS[emote.ordinal()];
        float ic = cell * 0.8f;
        float bw = 7 * ic + ic * 3, bh = 7 * ic + ic * 3;
        float bx = x - bw / 2, by = y - bh - rise;
        paint.setColor((alpha << 24) | (BUBBLE & 0xFFFFFF));
        rect.set(bx, by, bx + bw, by + bh);
        canvas.drawRoundRect(rect, ic * 2.5f, ic * 2.5f, paint);
        // kleiner Zipfel zur Figur
        rect.set(bx + ic, by + bh - ic * 0.2f, bx + ic * 2.4f, by + bh + ic * 1.2f);
        canvas.drawRect(rect, paint);
        paint.setColor((alpha << 24) | (EMOTE_COLORS[emote.ordinal()] & 0xFFFFFF));
        float iy = by + ic * 1.5f + (7 - icon.length) * ic / 2f;
        for (int r = 0; r < icon.length; r++) {
            for (int c = 0; c < icon[r].length(); c++) {
                if (icon[r].charAt(c) != 'X') continue;
                rect.set(bx + ic * 1.5f + c * ic, iy + r * ic, bx + ic * 1.5f + (c + 1) * ic, iy + (r + 1) * ic);
                canvas.drawRect(rect, paint);
            }
        }
    }

    private void px(Canvas c, float x, float y) { px(c, x, y, 1f); }

    private void px(Canvas c, float x, float y, float size) {
        // auf ganze Bildpunkte runden, leicht überlappen -> keine Lücken
        rect.set(Math.round(x), Math.round(y), Math.round(x + cell * size) + 0.5f, Math.round(y + cell * size) + 0.5f);
        c.drawRect(rect, paint);
    }
}
