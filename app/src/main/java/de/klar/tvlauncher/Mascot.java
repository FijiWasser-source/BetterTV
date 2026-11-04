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

    /** Für Soundeffekte. */
    public interface Events {
        void onJump();
        void onStar();
        void onBump();
        default void onLand() {}
        default void onLevelUp(int level) {}
        default void onTalk(String text) {}
        /** Ein Stein bröckelt ab (Bildschirmkoordinaten, Größe in px). */
        default void onCrumble(float screenX, float screenY, float size, int color) {}
    }

    private Events events;

    public void setEvents(Events e) { events = e; }

    public boolean isSleeping() { return night(); }

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
    private static final float HERO_X = 40f;        // linker Rand der Figur – weit genug rechts, dass hinter ihm viel Weg ist
    // Trefferfläche = Bereich der Beine (Spalten 2–11), damit Sprünge fair wirken
    private static final float HERO_L_OFF = 2f, HERO_R = HERO_X + 12f;
    private static final float GROUND_FROM_BOTTOM = 6f;

    static final class Thing {
        final int type; // 0 Stein (3×2), 1 Kiste (3×3), 2 Block (2×4), 3 Stern, 4 Plattform
        float x; final float y; boolean gone, hit, miss; float len = 12;
        Thing(int type, float x, float y) { this.type = type; this.x = x; this.y = y; }
        float w() { return type == 0 ? 3 : type == 1 ? 3 : type == 2 ? 2 : type == 4 ? len : 5 * 0.8f; }
        float h() { return type == 0 ? 2 : type == 1 ? 3 : type == 2 ? 4 : type == 4 ? 1.5f : 5 * 0.8f; }
        /** Oberkante einer Plattform (Höhe, auf der man steht). */
        float top() { return y + 1.5f; }
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
    int stumbles, obstaclesSpawned, landings; // für den Test
    final List<String> stumbleLog = new ArrayList<>(); // für den Test: Umstände jedes Stolperers
    private boolean simulating;
    private float floor;             // aktuelle Standhöhe (0 = Boden, sonst Plattform)
    private boolean manual;          // Spieler steuert selbst (Farbtasten)
    private long lastInput;
    private int level = 1;
    private String speech; private long speechStart, nextChatter;
    private static final long MANUAL_TIMEOUT = 20000;

    // Verfolger-Auto (eigene Figur): fährt hinter Pixel her, Rauch aus dem Auspuff
    private static final String[] CAR = {
            ".....BBBBBB.......",
            "....BWWBWWWB......",
            "...BWWWBWWWWB.....",
            "BBBBBBBBBBBBBBBBB.",
            "RBBBBOOOOOOOOBBBBY",
            "BBBBBBBBBBBBBBBBB.",
            "..KKK.......KKK...",
            "..KHK.......KHK...",
    };
    private static final float CAR_GAP = 22f;      // Wunschabstand hinter Pixel (Zellen)
    private float carX = HERO_X - CAR_GAP, carY, carVy;
    private boolean carAir;
    private float puffTimer, wheelPhase;
    private final List<float[]> puffs = new ArrayList<>(); // {x, y, alter}
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
        nextChatter = now + 6000;
    }

    /** Taste 0: Pixel springt selbst. */
    public void jump() {
        if (!airborne && !night()) {
            vy = JUMP_V; airborne = true;
            if (events != null && forceNight == null) events.onJump();
        }
    }

    /** Farbtasten / Taste 0: Spieler übernimmt und springt. */
    public void playerJump() {
        if (night()) return;
        long now = SystemClock.uptimeMillis();
        if (!manual) { manual = true; say("Du spielst!"); }
        lastInput = now;
        jump();
    }

    public boolean isManual() { return manual; }

    public int level() { return level; }

    private long lastCrumbled = Long.MIN_VALUE;
    private final int[] screen = new int[2];

    /** Kurzer Spruch in einer Sprechblase. */
    public void say(String text) {
        speech = text;
        speechStart = SystemClock.uptimeMillis();
        if (events != null && forceNight == null) events.onTalk(text);
    }

    /** Kleine Freude, z. B. wenn eine App gestartet wird. */
    public void cheer() { jump(); say("Viel Spaß!"); }

    private static final String[] CHATTER = {
            "Los geht's!", "Hui!", "Sterne sammeln!", "Pixel-Power!", "Ich renn für dich!",
            "Schöner Tag heute!", "Hopp hopp!", "Wer hat Lust zu spielen?", "Farbtaste drücken!"};

    private String chatterLine() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h >= 5 && h < 10 && rnd.nextInt(3) == 0) return "Guten Morgen!";
        if (h >= 21 && rnd.nextInt(3) == 0) return rnd.nextBoolean() ? "Noch eine Folge?" : "Gleich Schlafenszeit!";
        if (h >= 17 && h < 21 && rnd.nextInt(4) == 0) return "Schöner Abend!";
        return CHATTER[rnd.nextInt(CHATTER.length)];
    }

    /** Früher: Blick zur Kachel. Beim Rennen schaut Pixel nach vorn – bleibt als leere Methode. */
    public void lookAt(float dir) {}

    /** Für den Test: Spiel ohne Bildschirm laufen lassen. */
    int simulate(float seconds, long seed) {
        rnd.setSeed(seed);
        simulating = true;
        long now = 1_000_000L;
        for (float t = 0; t < seconds; t += 1f / 30) {
            now += 33;
            step(1f / 30, now, false, 120);
            if (Float.isNaN(jumpY) || jumpY < 0 || things.size() > 50) throw new IllegalStateException("Spielzustand kaputt");
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
        Thing pf = new Thing(4, HERO_X + 26, 6); pf.len = 14; things.add(pf);
        jumpY = 6; stars = 7; level = 2;
        puffs.clear();
        for (int k = 0; k < 5; k++) puffs.add(new float[]{carX - 0.5f - k * 1.6f, 3.5f + k * 0.6f, k * 0.25f});
        if (e == Emote.HEART) { speech = "Los geht's!"; speechStart = SystemClock.uptimeMillis(); }
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
            manual = false;
            if (!emoteOn || now - emoteStart > 2600) showEmote(Emote.SLEEP);
            return;
        }
        if (now >= nextEmote) { showEmote(rnd.nextBoolean() ? Emote.NOTE : Emote.QUESTION); nextEmote = now + 20000 + rnd.nextInt(20000); }
        if (now >= nextChatter) { say(chatterLine()); nextChatter = now + 25000 + rnd.nextInt(20000); }
        if (manual && now - lastInput > MANUAL_TIMEOUT) { manual = false; say("Ich übernehme!"); }

        float speed = SPEED * Math.min(1.5f, 1 + 0.07f * (level - 1));
        if (stumble > 0) speed *= 0.3f;
        stumble = Math.max(0, stumble - dt);
        float dx = speed * dt;
        scroll += dx;

        // neue Dinge am rechten Rand der Bahn
        nextSpawn -= dx;
        if (nextSpawn <= 0) {
            int t = rnd.nextInt(20);
            if (t < 3) { // Plattform in einer von zwei Höhen, oft mit Stern darüber
                Thing pf = new Thing(4, trackCells, rnd.nextBoolean() ? 6 : 9.5f);
                pf.len = 10 + rnd.nextInt(7);
                things.add(pf);
                if (rnd.nextInt(3) > 0) things.add(new Thing(3, trackCells + pf.len / 2, pf.top() + 6 + rnd.nextInt(3)));
                // genug Abstand: wer von der Plattform fällt, braucht danach Anlauf zum nächsten Sprung
                nextSpawn = pf.len + 24 + rnd.nextInt(12);
            } else {
                int type = t < 10 ? 0 : t < 14 ? 1 : t < 16 ? 2 : 3;
                Thing th = new Thing(type, trackCells, type == 3 ? 14 + rnd.nextInt(5) : 0);
                th.miss = type != 3 && rnd.nextInt(100) < 3; // ganz selten verpasst er den Absprung
                things.add(th);
                if (type != 3) obstaclesSpawned++;
                nextSpawn = 16 + rnd.nextInt(22);
            }
        }
        for (Iterator<Thing> it = things.iterator(); it.hasNext(); ) {
            Thing th = it.next();
            th.x -= dx;
            if (th.x + th.w() < 0 || th.gone) it.remove();
        }

        float heroL = HERO_X + HERO_L_OFF, heroR = HERO_R;

        // automatisch springen (nur solange der Spieler nicht selbst steuert)
        if (!airborne && !manual) {
            // Hindernisse haben Vorrang: für Sterne/Plattformen nur springen, wenn die Landezone frei ist
            boolean obstacleSoon = false;
            for (Thing th : things) {
                if (th.type == 3 || th.type == 4) continue;
                float gap = th.x - heroR;
                if (floor == 0 && gap > 2.8f && gap < 24f) obstacleSoon = true;
            }
            for (Thing th : things) {
                float gap = th.x - heroR;
                boolean window;
                if (th.type == 4) {
                    float rise = th.top() - jumpY;
                    window = !obstacleSoon && rise > 0.5f && rise < 10.5f && gap > 1.2f && gap < 3.2f;
                } else if (th.type == 3) {
                    float rel = th.y - jumpY;
                    window = !obstacleSoon && rel > 2 && rel < 20 && gap > 0.5f && gap < 3f;
                } else {
                    window = floor == 0 && gap > 1.8f && gap < 2.8f && !th.miss;
                }
                if (window) { jump(); break; }
            }
        }

        // Sprungphysik mit Plattformen
        if (airborne) {
            float prev = jumpY;
            vy -= GRAVITY * dt;
            jumpY += vy * dt;
            if (vy < 0) {
                for (Thing th : things) {
                    if (th.type != 4) continue;
                    boolean overX = th.x < heroR - 1 && th.x + th.w() > heroL + 1;
                    if (overX && prev >= th.top() - 0.01f && jumpY <= th.top()) {
                        jumpY = th.top(); floor = th.top(); vy = 0; airborne = false; landings++;
                        if (events != null) events.onLand();
                        break;
                    }
                }
            }
            if (airborne && jumpY <= 0) { jumpY = 0; floor = 0; vy = 0; airborne = false; }
        } else if (floor > 0) {
            // noch auf der Plattform? Sonst herunterfallen
            boolean onIt = false;
            for (Thing th : things) {
                if (th.type == 4 && Math.abs(th.top() - floor) < 0.01f && th.x < heroR - 1 && th.x + th.w() > heroL + 1) onIt = true;
            }
            if (!onIt) { airborne = true; vy = 0; floor = 0; }
        }

        stepCar(dt, speed);

        // Treffer: Hindernis -> Stolpern, Stern -> einsammeln
        float feet = jumpY, head = jumpY + 12;
        for (Thing th : things) {
            if (th.type == 4) continue;
            boolean overlapX = th.x < heroR && th.x + th.w() > heroL;
            if (!overlapX) continue;
            if (th.type == 3) {
                if (th.y < head && th.y + th.h() > feet) {
                    th.gone = true;
                    stars++;
                    showEmote(stars % 10 == 0 ? Emote.HEART : Emote.STAR);
                    if (events != null) events.onStar();
                    int newLevel = 1 + stars / 20;
                    if (newLevel > level) {
                        level = newLevel;
                        say("Level " + level + "!");
                        if (events != null) events.onLevelUp(level);
                    } else if (stars % 5 == 0) {
                        say(rnd.nextBoolean() ? "Stark!" : stars + " Sterne!");
                    }
                }
            } else if (!th.hit && feet < th.h() - 0.3f) {
                th.hit = true; // pro Hindernis höchstens einmal stolpern
                if (simulating && stumbleLog.size() < 50) stumbleLog.add("typ=" + th.type + " miss=" + th.miss + " air=" + airborne + " floor=" + floor + " jumpY=" + String.format(java.util.Locale.ROOT, "%.1f", jumpY) + " vy=" + String.format(java.util.Locale.ROOT, "%.1f", vy));
                stumble = 0.9f;
                stumbles++;
                showEmote(Emote.BANG);
                say(rnd.nextBoolean() ? "Autsch!" : "Hoppla!");
                if (events != null) events.onBump();
                vy = 14; airborne = true; // kleiner Hüpfer beim Stolpern
            }
        }
    }

    /** Auto folgt Pixel: rückt näher, wenn er stolpert, hüpft über Hindernisse, pafft Rauch. */
    private void stepCar(float dt, float speed) {
        float target = HERO_X - (stumble > 0 ? CAR_GAP * 0.55f : CAR_GAP);
        carX += (target - carX) * Math.min(1, dt * 1.2f);
        carX = Math.min(carX, HERO_X - 19);                 // nie auffahren
        wheelPhase += speed * dt;
        // Hindernis direkt vor dem Auto -> kleiner Hüpfer
        if (!carAir) {
            for (Thing th : things) {
                if (th.type == 4 || th.type == 3) continue;
                float gap = th.x - (carX + 17);
                if (gap > 0.5f && gap < 2.5f) { carVy = 26; carAir = true; break; }
            }
        } else {
            carVy -= GRAVITY * dt;
            carY += carVy * dt;
            if (carY <= 0) { carY = 0; carVy = 0; carAir = false; }
        }
        // Rauchwölkchen aus dem Auspuff (hinten links)
        puffTimer -= dt;
        if (puffTimer <= 0) {
            puffs.add(new float[]{carX - 0.5f, carY + 3.5f, 0});
            puffTimer = 0.18f + rnd.nextFloat() * 0.12f;
        }
        for (Iterator<float[]> it = puffs.iterator(); it.hasNext(); ) {
            float[] p = it.next();
            p[2] += dt; p[0] -= (speed * 0.35f + 3) * dt; p[1] += 2.2f * dt;
            if (p[2] > 1.4f || p[0] < 1) it.remove();
        }
    }

    private void drawCar(Canvas c, float ground, boolean sleeping, long now) {
        float s = cell * 1.05f;
        float x = carX * cell, bounce = carAir || sleeping ? 0 : ((now / 90) % 2 == 0 ? 0 : s * 0.3f);
        float top = ground - CAR.length * s - carY * cell - bounce;
        // Rauch hinter dem Auto
        for (float[] p : puffs) {
            float life = p[2] / 1.4f;
            int a = Math.round(90 * (1 - life));
            soft.setColor((a << 24) | 0xC9CCD2);
            float r = s * (0.6f + life * 1.6f);
            float px0 = p[0] * cell, py0 = ground - p[1] * cell - carY * cell;
            rect.set(px0 - r, py0 - r, px0 + r, py0 + r);
            c.drawRect(rect, soft);
        }
        soft.setColor(0x4D000000);
        // Schatten
        rect.set(x + s, ground - s * 0.4f, x + 17 * s, ground + s * 0.5f);
        c.drawOval(rect, soft);
        boolean hubUp = ((int) (wheelPhase * 1.5f)) % 2 == 0;
        for (int r = 0; r < CAR.length; r++) {
            String line = CAR[r];
            for (int k = 0; k < line.length(); k++) {
                char ch = line.charAt(k);
                int col;
                switch (ch) {
                    case 'B': col = 0xFF8FB4FF; break;                       // Lack im Klar-Blau
                    case 'W': col = 0xFF2E3642; break;                       // Scheiben
                    case 'O': col = 0xFFFF8A1F; break;                       // Zierstreifen
                    case 'K': col = 0xFF1E2024; break;                       // Reifen
                    case 'H': col = hubUp ? 0xFFB3B7BE : 0xFF6C7078; break;  // Radnabe "dreht"
                    case 'Y': col = sleeping ? 0xFF6C6A55 : 0xFFFFE07A; break;  // Scheinwerfer
                    case 'R': col = sleeping ? 0xFF5A2A2A : 0xFFFF5A5A; break;  // Rücklicht
                    default: continue;
                }
                paint.setColor(col);
                rect.set(x + k * s, top + r * s, x + (k + 1) * s + 0.5f, top + (r + 1) * s + 0.5f);
                c.drawRect(rect, paint);
            }
        }
        // Lichtkegel nach vorn
        if (!sleeping) {
            soft.setColor(0x22FFE07A);
            rect.set(x + 18 * s, top + 3.5f * s, x + 26 * s, top + 5.5f * s);
            c.drawRect(rect, soft);
            soft.setColor(0x4D000000);
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
        drawBackdrop(canvas, ground);
        drawTrack(canvas, ground, sleeping);

        // Dinge auf der Bahn (nur innerhalb der Bahn sichtbar)
        for (Thing th : things) {
            if (th.x < 1 || th.x + th.w() > trackCells - 1) continue;
            drawThing(canvas, th, ground);
        }

        drawCar(canvas, ground, sleeping, now);

        // Figur
        float t = (now - start) / 1000f;
        float ox = HERO_X * cell;
        float bob = airborne || sleeping ? 0 : (float) Math.abs(Math.sin(t * Math.PI * 3)) * cell * 0.6f;
        float sit = sleeping ? cell * 1.5f : 0;
        float shake = stumble > 0 ? (float) Math.sin(t * 60) * cell * 0.5f : 0;
        float oy = ground - 12 * cell - jumpY * cell - bob + sit;

        // Schatten (wird beim Springen kleiner)
        float shadowY = ground - shadowFloor() * cell;
        float sw = 12 * cell * Math.max(0.3f, 1 - (jumpY - shadowFloor()) / 14f);
        rect.set(ox + 7 * cell - sw / 2, shadowY - cell * 0.5f, ox + 7 * cell + sw / 2, shadowY + cell * 0.6f);
        canvas.drawOval(rect, soft);

        drawHero(canvas, ox + shake, oy, now, t, sleeping);

        // Sternzähler, Level und Spieler-Anzeige in Pixel-Schrift
        float hudY = cell * 1.5f; // oben links, außerhalb der Fahrbahn
        paint.setColor(0xFFFFC24A);
        drawIcon(canvas, STAR, cell * 2.5f, hudY, cell * 0.7f);
        paint.setColor(0xFFD7DAE0);
        String count = String.valueOf(stars);
        PixelText.draw(canvas, count, cell * 7f, hudY, cell * 0.5f, paint);
        float hudX = cell * 7f + PixelText.measure(count, cell * 0.5f) + cell * 2.5f;
        paint.setColor(0xFF8FB4FF);
        PixelText.draw(canvas, "LV " + level, hudX, hudY, cell * 0.5f, paint);
        if (manual) {
            paint.setColor(0xFFFF8A1F);
            PixelText.draw(canvas, "DU SPIELST", getWidth() - cell * 2.5f - PixelText.measure("DU SPIELST", cell * 0.5f),
                    hudY, cell * 0.5f, paint);
        }

        if (!drawSpeech(canvas, now, ox + 14 * cell, oy - cell * 2f)) {
            drawEmote(canvas, now, ox + 14 * cell + cell * 0.5f, oy - cell * 2.5f);
        }

        if (running) postInvalidateDelayed(33); // ~30 Bilder/s reichen und schonen den TV
    }

    /** Ferne Hügel, die langsamer vorbeiziehen (Tiefe). Wolken & Co. zeichnet der SkyLayer. */
    private void drawBackdrop(Canvas c, float ground) {
        float left = cell, right = getWidth() - cell;
        // ferne Hügel (Treppen-Silhouette, sehr dezent)
        paint.setColor(0x14FFFFFF);
        float far = scroll * 0.25f;
        for (float x = left; x < right; x += cell) {
            double u = (x / cell + far) / 9.0;
            int hgt = (int) Math.round(3 + 2.2 * Math.sin(u) + 1.3 * Math.sin(u * 2.7 + 1));
            rect.set(x, ground - hgt * cell, x + cell + 0.5f, ground);
            c.drawRect(rect, paint);
        }
    }

    /** Sprechblase mit Pixel-Text. @return true, wenn gerade gesprochen wird */
    private boolean drawSpeech(Canvas c, long now, float x, float y) {
        if (speech == null || (forceNight == null && now - speechStart > 2600)) return false;
        float gc = cell * 0.45f;
        float tw = PixelText.measure(speech, gc), th = 7 * gc;
        float pad = cell * 1.2f;
        float bw = tw + 2 * pad, bh = th + 2 * pad;
        float bx = Math.min(getWidth() - bw - cell, x), by = Math.max(cell * 0.5f, y - bh);
        soft.setColor(BUBBLE);
        rect.set(bx, by, bx + bw, by + bh);
        c.drawRoundRect(rect, cell * 1.6f, cell * 1.6f, soft);
        rect.set(bx + cell, by + bh - cell * 0.2f, bx + cell * 2.2f, by + bh + cell);
        c.drawRect(rect, soft);
        soft.setColor(0x4D000000);
        paint.setColor(0xFF1B2640);
        PixelText.draw(c, speech, bx + pad, by + pad, gc, paint);
        return true;
    }

    // Bahn aus Steinblöcken (je 2 Zellen breit): baut sich rechts auf, bröckelt links ab
    private static final float BLOCK = 2f, BUILD_ZONE = 14f, BREAK_ZONE = 9f;

    /** Pseudo-Zufall je Block (stabil, damit jeder Stein "seine" Verzögerung behält). */
    private static float jitter(long i) {
        long h = i * 0x9E3779B97F4A7C15L;
        h ^= (h >>> 31);
        return ((h >>> 11) & 0xFFFF) / 65535f;
    }

    /**
     * Versatz eines Blocks an Bildschirmposition x (in Zellen): {dy in Zellen, Deckkraft 0..1}.
     * Rechts fällt er von oben herein, links fällt er nach unten weg.
     */
    private float[] blockState(float xCells, float trackCells, long index) {
        float j = jitter(index) * 0.35f;
        float fromRight = (trackCells - 1 - xCells) / BUILD_ZONE - j;      // < 1: wird gerade gebaut
        float fromLeft = (xCells - 1) / BREAK_ZONE - j;                       // < 1: bröckelt gerade
        if (fromRight < 1) {
            float p = Math.max(0, fromRight);
            float e = 1 - p;
            return new float[]{-e * e * 12, Math.min(1, p * 1.6f)};
        }
        if (fromLeft < 1) {
            float p = Math.max(0, fromLeft);
            float e = 1 - p;
            return new float[]{e * e * 9, Math.min(1, p * 1.4f)};
        }
        return new float[]{0, 1};
    }

    private void drawTrack(Canvas c, float ground, boolean sleeping) {
        float trackCells = getWidth() / cell;
        int[] colors = {0xFFA3A7AE, 0xFF868A92, 0xFF4A4D54, 0xFF3C3E44, 0xFF2E3035};
        long first = (long) Math.floor(scroll / BLOCK) - 1;
        for (long i = first; ; i++) {
            float xCells = i * BLOCK - scroll;                 // linke Kante des Blocks in Zellen
            if (xCells > trackCells) break;
            if (xCells + BLOCK < 1) continue;
            float[] st = blockState(xCells + BLOCK / 2, trackCells, i);
            if (events != null && forceNight == null && !sleeping && i > lastCrumbled && st[0] > 0 && st[0] < 4 && xCells < BREAK_ZONE) {
                lastCrumbled = i;
                int every = Math.max(1, 4 - (level - 1) / 2);   // Level 1–2: jeder 4., 3–4: 3., 5–6: 2., ab 7: jeder
                if (i % every == 0) {
                    getLocationOnScreen(screen);
                    events.onCrumble(screen[0] + Math.max(cell, xCells * cell), screen[1] + ground + st[0] * cell, BLOCK * cell * 0.8f, colors[1]);
                }
            }
            if (st[1] <= 0.01f) continue;
            float x0 = Math.max(cell, xCells * cell), x1 = Math.min(getWidth() - cell, (xCells + BLOCK) * cell);
            if (x1 <= x0) continue;
            float dy = st[0] * cell;
            int alpha = Math.round(st[1] * 255);
            for (int r = 0; r < colors.length; r++) {
                paint.setColor((alpha << 24) | (colors[r] & 0xFFFFFF));
                rect.set(x0, ground + r * cell + dy, x1 + 0.5f, ground + (r + 1) * cell + 0.5f + dy);
                c.drawRect(rect, paint);
            }
            // feine Fuge zwischen den Steinen + Kiesel
            paint.setColor((Math.round(alpha * 0.5f) << 24) | 0x2E3035);
            rect.set(x0, ground + dy, x0 + 1, ground + 2 * cell + dy);
            c.drawRect(rect, paint);
            if (jitter(i * 7 + 3) > 0.6f) {
                paint.setColor((alpha << 24) | 0x767A82);
                px(c, x0 + cell * 0.6f, ground + cell + dy, 0.5f);
            }
        }
    }

    private void drawThing(Canvas c, Thing th, float ground) {
        float x = th.x * cell;
        if (th.type == 4) { // schwebende Plattform im Stil der Bahn
            float top = ground - th.top() * cell, w = th.w() * cell;
            paint.setColor(0x33000000);
            rect.set(x + cell, top + 2 * cell, x + w - cell, top + 2.6f * cell); c.drawRect(rect, paint);
            paint.setColor(0xFFA3A7AE); rect.set(x, top, x + w, top + cell * 0.6f); c.drawRect(rect, paint);
            paint.setColor(0xFF868A92); rect.set(x, top + cell * 0.6f, x + w, top + cell * 1.1f); c.drawRect(rect, paint);
            paint.setColor(0xFF4A4D54); rect.set(x + cell * 0.5f, top + cell * 1.1f, x + w - cell * 0.5f, top + cell * 1.5f); c.drawRect(rect, paint);
            return;
        }
        if (th.type == 3) {
            float bobS = (float) Math.sin((SystemClock.uptimeMillis() - start) / 300.0 + th.x) * cell * 0.4f;
            paint.setColor(0xFFFFC24A);
            drawIcon(c, STAR, x, ground - (th.y + th.h()) * cell + bobS, cell * 0.8f);
            return;
        }
        float[] st = blockState(th.x + th.w() / 2, getWidth() / cell, (long) Math.floor((th.x + scroll) / BLOCK));
        ground += st[0] * cell;
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

    /** Höchste Fläche unter der Figur (für den Schatten). */
    private float shadowFloor() {
        float best = 0, heroL = HERO_X + HERO_L_OFF;
        for (Thing th : things) {
            if (th.type == 4 && th.top() <= jumpY + 0.01f && th.x < HERO_R - 1 && th.x + th.w() > heroL + 1) best = Math.max(best, th.top());
        }
        return best;
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
