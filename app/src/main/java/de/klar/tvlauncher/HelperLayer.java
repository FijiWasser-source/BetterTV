package de.klar.tvlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Durchsichtige Ebene über der Startansicht.
 *
 * Was hinter Pixel von der Bahn bröckelt, fällt senkrecht herunter auf die Höhe der Icons
 * (YouTube bzw. eine kleine Brücke zwischen YouTube und Netflix) und wird dort im Tempo der
 * Straße nach links geschoben – zu "Schluck" (eigene Figur, lange Arme und Beine) auf der
 * ersten App. Er saugt die Teile mit seinem Staubsauger ein oder isst ab und zu eins.
 * Je höher das Level, desto mehr kommt an und desto schneller arbeitet er. Nachts schläft er.
 */
public class HelperLayer extends View {

    public interface Events {
        void onSuck();
        void onCrunch();
        /** Schluck hat lange genug geschlafen – Pixel soll ihn wecken. */
        default void onNapOver() {}
    }

    /** Ein Teil: 0 Bahnstein, 1 Stein, 2 Kiste, 3 Block. */
    static final class Item {
        int type; float x, y, vy;
        int state;          // 0 fällt, 1 wird geschoben, 2 wird eingesaugt, 3 wird gegessen
        float t;            // Fortschritt beim Einsaugen/Essen (0..1)
        float startX, startY;
    }

    private static final int BODY = 0xFF8FB4FF, BODY_DARK = 0xFF6F94DF, INK = 0xFF1B2640;
    private static final int MAX_ITEMS = 14;
    private static final java.util.Comparator<Item> ITEM_ORDER = (a, b) -> Float.compare(a.x, b.x);

    private final Paint paint = new Paint();
    private final Paint soft = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Random rnd = new Random();
    private final List<Item> items = new ArrayList<>();
    private final List<float[]> crumbs = new ArrayList<>(); // {x, y, vy, alter, vx}
    private final int[] loc = new int[2], other = new int[2];
    private float p; // Pixelgröße
    private Mascot mascot;
    private RecyclerView dock;
    private Events events;
    private boolean running;
    private long last, lastSuckSound;
    private float time, mouthOpen, chewing, armReach, hoseWiggle, sweatPhase;
    private String bubble; private float bubbleAge;
    private Integer forceLevel; private boolean forceSleep, previewPending, previewEat;
    private int frameDelay = 33; // 0 = angehalten
    // Satt werden: Bauch wächst mit jedem Stein, dann ein Nickerchen
    private float fullness, napTimer, wakeTimer;
    private int fullAt = 18;
    // Wasserball (bei Sonne): hüpft über den Bildschirm
    private int weather = Mascot.CLEAR;
    private boolean ball; private float ballX, ballY, ballVx, ballVy, ballSpin, nextBall = 6;

    public void setWeather(int w) { weather = w; if (w != Mascot.SUN) ball = false; }

    /** Vorschau: Schluck satt und schlafend. */
    void previewNap() { preview(2, false); napTimer = 10; fullness = 1; bubble = null; }

    /** Vorschau: Wasserball mitten im Flug. */
    void previewBall(float fx, float fy) { ball = true; ballX = fx; ballY = fy; ballSpin = 0.7f; }

    public void setFrameDelay(int ms) { frameDelay = ms; last = SystemClock.uptimeMillis(); invalidate(); }

    // Geometrie (lokal): Füße von Schluck, Oberkante der Icons, Düse, Brücke
    private float footX, surfaceY, nozzleX;
    private float bridgeL, bridgeR; private boolean hasBridge;
    private boolean hasSpot;

    public HelperLayer(Context c) { this(c, null); }

    public HelperLayer(Context c, AttributeSet a) {
        super(c, a);
        p = 2.6f * getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setFocusable(false);
        setClickable(false);
    }

    public void attach(Mascot m, RecyclerView dockList, Events e) { mascot = m; dock = dockList; events = e; }

    private int level() { return forceLevel != null ? forceLevel : mascot != null ? mascot.level() : 1; }

    private boolean sleeping() { return forceLevel != null ? forceSleep : mascot != null && mascot.isSleeping(); }

    private float roadSpeed() { return mascot != null ? mascot.roadSpeedPx() : 40 * p; }

    /** Ein Teil bröckelt hinten von der Bahn ab (Bildschirmkoordinaten) und fällt senkrecht herunter. */
    public void addPiece(int type, float screenX, float screenY) {
        if (!hasSpot || items.size() >= MAX_ITEMS) return;
        getLocationOnScreen(loc);
        Item it = new Item();
        it.type = type;
        it.x = Math.max(nozzleX + 4 * p, screenX - loc[0]);
        it.y = screenY - loc[1];
        items.add(it);
    }

    /** Vorschau: ein paar Teile unterwegs, einer wird eingesaugt bzw. gegessen. */
    void preview(int level, boolean eating) {
        forceLevel = level; forceSleep = false;
        previewPending = true; previewEat = eating;
        if (eating) { bubble = "MAMPF!"; bubbleAge = 0.3f; }
        invalidate();
    }

    private void placePreview() {
        previewPending = false;
        items.clear();
        if (!hasSpot) return;
        int[] types = {0, 2, 0, 1};
        float x0 = nozzleX + 2 * p;
        for (int k = 0; k < types.length; k++) {
            Item it = new Item(); it.type = types[k]; it.state = 1;
            it.x = x0 + (k + 1) * 11 * p; it.y = surfaceY - h(it.type);
            items.add(it);
        }
        Item fall = new Item(); fall.type = 0; fall.state = 0;
        fall.x = (hasBridge ? bridgeR : nozzleX + 70 * p) + 6 * p; fall.y = surfaceY - 30 * p;
        items.add(fall);
        Item cur = new Item(); cur.type = 0; cur.state = previewEat ? 3 : 2; cur.t = 0.5f;
        cur.startX = nozzleX + 2 * p; cur.startY = surfaceY - h(0);
        items.add(cur);
        mouthOpen = previewEat ? 0.5f : 0.3f; armReach = 1; chewing = previewEat ? 0.5f : 0; hoseWiggle = 1;
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); resume(); }
    @Override protected void onDetachedFromWindow() { running = false; super.onDetachedFromWindow(); }
    @Override protected void onWindowVisibilityChanged(int v) { super.onWindowVisibilityChanged(v); if (v == VISIBLE) resume(); else running = false; }

    private void resume() {
        running = isAttachedToWindow() && getWindowVisibility() == VISIBLE;
        last = SystemClock.uptimeMillis();
        if (running) postInvalidateOnAnimation();
    }

    /** Fläche eines Dock-Eintrags (Kachel bzw. Logo), lokal: {links, oben, rechts}. */
    private boolean surfaceOf(int index, float[] out) {
        if (dock == null || dock.getChildCount() <= index) return false;
        View card = dock.getChildAt(index).findViewById(R.id.card);
        if (card == null) return false;
        boolean free = card.getBackground() instanceof AbendTile && ((AbendTile) card.getBackground()).invisible;
        View base = free ? card.findViewById(R.id.banner) : card;
        if (base == null || base.getVisibility() != VISIBLE) base = card;
        base.getLocationOnScreen(other);
        float d = getResources().getDisplayMetrics().density;
        float inset = base == card ? 10 * d : 0;   // Leucht-Rand der Kachel
        out[0] = other[0] - loc[0] + inset;
        out[1] = other[1] - loc[1] + inset;
        out[2] = other[0] - loc[0] + base.getWidth() - inset;
        return true;
    }

    private final float[] s0 = new float[3], s1 = new float[3];

    private void findSpot() {
        getLocationOnScreen(loc);
        hasSpot = surfaceOf(0, s0);
        if (!hasSpot) return;
        footX = s0[0] + (s0[2] - s0[0]) * 0.36f;
        surfaceY = s0[1];
        nozzleX = footX + 9 * p;
        hasBridge = surfaceOf(1, s1);
        if (hasBridge) { bridgeL = s0[2] - 2 * p; bridgeR = s1[0] + 2 * p; }
    }

    private static float h(int type) { return type == 2 ? 3 : type == 3 ? 3.5f : type == 1 ? 2 : 1.6f; }
    private static float w(int type) { return type == 0 ? 4 : type == 3 ? 2 : 3; }

    private float mouthX() { return footX + 3 * p; }
    private float mouthY() { return surfaceY - 27 * p; }

    private void step(float dt) {
        int lv = level();
        int queued = 0;
        for (Item it : items) if (it.state == 1) queued++;
        boolean crowded = queued >= 6;
        float stress = Math.min(3f, 1 + 0.28f * (lv - 1) + (crowded ? 0.6f : 0));
        time += dt * stress;
        sweatPhase += dt;
        float road = roadSpeed();
        if (napTimer > 0) {
            napTimer -= dt;
            if (napTimer <= 0) { napTimer = 0; if (events != null) events.onNapOver(); wakeTimer = 0.9f; }
        }
        if (wakeTimer > 0) {
            wakeTimer -= dt;
            if (wakeTimer <= 0) { fullness = 0; fullAt = 16 + rnd.nextInt(8); bubble = "HUCH!"; bubbleAge = 0; }
        }
        boolean napping = napTimer > 0 || wakeTimer > 0;
        Item current = null;
        for (Item it : items) if (it.state >= 2) { current = it; break; }

        // von links nach rechts: das vorderste Teil zuerst, damit sich die Schlange sauber staut
        java.util.Collections.sort(items, ITEM_ORDER);
        float frontLimit = nozzleX + 1 * p;
        for (Iterator<Item> iter = items.iterator(); iter.hasNext(); ) {
            Item it = iter.next();
            float bottom = surfaceY - h(it.type) * p * 1.5f;
            if (it.state == 0) {                       // fällt senkrecht herunter
                it.vy += 260 * p * dt;
                it.y += it.vy * dt;
                if (it.y >= bottom) {
                    it.y = bottom;
                    if (it.vy > 30 * p) it.vy = -it.vy * 0.25f; // kleiner Hopser
                    else { it.vy = 0; it.state = 1; }
                }
            } else if (it.state == 1) {                // wird im Tempo der Straße geschoben
                it.y = bottom;
                float target = Math.max(frontLimit, it.x - road * dt);
                it.x = target;
                frontLimit = it.x + (w(it.type) * 1.5f + 1.5f) * p;  // Abstand zum nächsten
                if (current == null && !napping && it.x <= nozzleX + 1.5f * p) {
                    current = it;
                    boolean eat = (lv >= 3 || crowded) && rnd.nextInt(100) < 8 + lv * 3 + (crowded ? 12 : 0);
                    it.state = eat ? 3 : 2;
                    it.t = 0; it.startX = it.x; it.startY = it.y;
                }
            }
        }
        if (current != null) {
            float dur = current.state == 3 ? 0.45f : Math.max(0.12f, 0.4f / (1 + 0.3f * (lv - 1)));
            current.t += dt / dur;
            hoseWiggle = current.state == 2 ? 1 : hoseWiggle;
            if (current.t >= 1) {
                items.remove(current);
                fullness += 1f / fullAt;
                if (fullness >= 1 && napTimer <= 0) { napTimer = 16 + rnd.nextFloat() * 8; bubble = "SATT!"; bubbleAge = 0; }
                if (current.state == 3) {
                    chewing = 0.8f;
                    if (rnd.nextBoolean()) { bubble = rnd.nextBoolean() ? "MAMPF!" : "LECKER!"; bubbleAge = 0; }
                    for (int k = 0; k < 6; k++) crumbs.add(new float[]{mouthX(), mouthY() + 2 * p, (20 + rnd.nextFloat() * 30) * p, 0, (rnd.nextFloat() - 0.5f) * 30 * p});
                    if (events != null) events.onCrunch();
                } else {
                    long now = SystemClock.uptimeMillis();
                    if (events != null && now - lastSuckSound > 220) { events.onSuck(); lastSuckSound = now; }
                }
            }
        }
        boolean eatingNow = current != null && current.state == 3;
        hoseWiggle = Math.max(0, hoseWiggle - dt * 2.5f);
        mouthOpen += ((eatingNow ? 1f : 0.2f) - mouthOpen) * Math.min(1, dt * 8);
        armReach += ((eatingNow ? 1f : 0f) - armReach) * Math.min(1, dt * 6);
        chewing = Math.max(0, chewing - dt);
        bubbleAge += dt;
        if (bubbleAge > 1.6f) bubble = null;
        if (bubble == null && queued >= 8 && rnd.nextInt(200) == 0) { bubble = "ZU VIELE!"; bubbleAge = 0; }
        for (Iterator<float[]> it = crumbs.iterator(); it.hasNext(); ) {
            float[] c = it.next();
            c[3] += dt; c[2] += 160 * p * dt; c[1] += c[2] * dt; c[0] += c[4] * dt;
            if (c[3] > 1.2f) it.remove();
        }
        stepBall(dt);
    }

    /** Wasserball: kommt von links, springt über die App-Reihe und fliegt rechts hinaus. */
    private void stepBall(float dt) {
        if (weather != Mascot.SUN) return;
        if (!ball) {
            nextBall -= dt;
            if (nextBall <= 0) {
                ball = true; ballX = -12 * p; ballY = surfaceY - 60 * p;
                ballVx = (55 + rnd.nextFloat() * 30) * p; ballVy = -40 * p; nextBall = 20 + rnd.nextFloat() * 20;
            }
            return;
        }
        float r = 6 * p;
        ballVy += 190 * p * dt;
        ballX += ballVx * dt; ballY += ballVy * dt;
        ballSpin += ballVx * dt / (r * 2);
        if (ballY + r >= surfaceY) {               // hüpft auf den Icons
            ballY = surfaceY - r;
            ballVy = -Math.max(120 * p, Math.abs(ballVy) * 0.78f);
        }
        if (ballX - r > getWidth()) ball = false;
    }

    private void drawBall(Canvas c) {
        if (!ball) return;
        float r = 6 * p;
        int n = 12;
        for (int gy = 0; gy < n; gy++) {
            for (int gx = 0; gx < n; gx++) {
                float dx = (gx + 0.5f) / n * 2 - 1, dy = (gy + 0.5f) / n * 2 - 1;
                if (dx * dx + dy * dy > 1) continue;
                double ang = Math.atan2(dy, dx) + ballSpin;
                int seg = (int) Math.floor(((ang / (2 * Math.PI)) % 1 + 1) % 1 * 6);
                int col = seg % 3 == 0 ? 0xFFFF8A1F : seg % 3 == 1 ? 0xFFF2F5FF : 0xFF8FB4FF;
                if (dx * dx + dy * dy < 0.06f) col = 0xFFF2F5FF;                 // Mitte
                if (dx < -0.2f && dy < -0.3f && dx * dx + dy * dy > 0.4f) col = 0xFFFFFFFF; // Glanz
                paint.setColor(col);
                float cell = 2 * r / n;
                rect.set(ballX - r + gx * cell, ballY - r + gy * cell, ballX - r + (gx + 1) * cell + 0.5f, ballY - r + (gy + 1) * cell + 0.5f);
                c.drawRect(rect, paint);
            }
        }
    }

    @Override protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float dt = Math.min(0.05f, (now - last) / 1000f);
        last = now;
        findSpot();
        if (!hasSpot) { items.clear(); return; }
        if (previewPending) placePreview();
        if (forceLevel == null && !sleeping() && frameDelay > 0) step(dt);

        if (hasBridge) drawBridge(c);
        drawVacuum(c);
        for (Item it : items) if (it.state < 2) drawItem(c, it, it.x, it.y, 1.5f);
        drawHelper(c);
        for (Item it : items) {
            if (it.state == 2) {      // in die Düse
                float k = it.t * it.t;
                drawItem(c, it, it.startX + (nozzleX - 1.5f * p - it.startX) * k, it.startY + p * k, 1.5f * (1 - 0.8f * k));
            } else if (it.state == 3) { // zum Mund
                float k = it.t;
                drawItem(c, it, it.startX + (mouthX() - it.startX) * k, it.startY + (mouthY() - it.startY) * k, 1.5f * (1 - 0.4f * k));
            }
        }
        drawBall(c);
        for (float[] cr : crumbs) {
            paint.setColor(((int) (255 * Math.max(0, 1 - cr[3] / 1.2f)) << 24) | 0xA3A7AE);
            rect.set(cr[0], cr[1], cr[0] + p * 0.8f, cr[1] + p * 0.8f);
            c.drawRect(rect, paint);
        }
        boolean busy = !items.isEmpty() || !crumbs.isEmpty() || chewing > 0 || ball || napTimer > 0;
        if (running && forceLevel == null && frameDelay > 0) postInvalidateDelayed(busy ? frameDelay : 90);
    }

    /** Kleine Pixel-Brücke zwischen der ersten und zweiten App, auf Höhe der Icons. */
    private void drawBridge(Canvas c) {
        if (bridgeR - bridgeL < 2 * p) return;
        float y = surfaceY;
        // Fahrbahn
        paint.setColor(0xFF7A7E86); rect.set(bridgeL, y, bridgeR, y + p * 0.9f); c.drawRect(rect, paint);
        paint.setColor(0xFF555860); rect.set(bridgeL, y + p * 0.9f, bridgeR, y + p * 1.6f); c.drawRect(rect, paint);
        // Bogen darunter (Treppenstufen)
        paint.setColor(0xFF3F4248);
        float span = bridgeR - bridgeL;
        for (float x = bridgeL; x < bridgeR; x += p) {
            float u = (x - bridgeL) / span * 2 - 1;      // -1..1
            float depth = (1 - u * u) * 4 * p;
            rect.set(x, y + 1.6f * p, x + p + 0.5f, y + 1.6f * p + Math.max(p * 0.6f, 4.5f * p - depth));
            c.drawRect(rect, paint);
        }
        // Geländerpfosten
        paint.setColor(0xFF6C7078);
        for (float x = bridgeL + p; x < bridgeR - p; x += 4 * p) {
            rect.set(x, y - 2 * p, x + p * 0.7f, y); c.drawRect(rect, paint);
        }
        rect.set(bridgeL + p, y - 2 * p, bridgeR - p, y - 1.5f * p); c.drawRect(rect, paint);
    }

    /** Staubsauger: Behälter mit Rädchen hinter Schluck, Schlauch zur Düse vor ihm. */
    private void drawVacuum(Canvas c) {
        float y = surfaceY;
        float bx = footX - 15 * p;
        // Behälter (orange, mit Griff und Rädchen)
        paint.setColor(0xFFFF8A1F); rect.set(bx, y - 7 * p, bx + 8 * p, y - 1.4f * p); c.drawRect(rect, paint);
        paint.setColor(0xFFFFB066); rect.set(bx, y - 7 * p, bx + 8 * p, y - 6 * p); c.drawRect(rect, paint);
        paint.setColor(0xFFC96A12); rect.set(bx, y - 2.4f * p, bx + 8 * p, y - 1.4f * p); c.drawRect(rect, paint);
        paint.setColor(0xFFD7DAE0); rect.set(bx + 2.5f * p, y - 8.4f * p, bx + 5.5f * p, y - 7.6f * p); c.drawRect(rect, paint);
        paint.setColor(0xFF1E2024);
        rect.set(bx + 0.6f * p, y - 1.6f * p, bx + 2.4f * p, y); c.drawRect(rect, paint);
        rect.set(bx + 5.6f * p, y - 1.6f * p, bx + 7.4f * p, y); c.drawRect(rect, paint);
        // Schlauch: Bogen vom Behälter über Schlucks Hand zur Düse, wackelt beim Saugen
        paint.setColor(0xFFB3B7BE);
        float sx = bx + 8 * p, sy = y - 5 * p, ex = nozzleX - 2 * p, ey = y - 1.5f * p;
        float cx = footX + 4 * p, cy = y - 15 * p;   // Hand
        float wig = hoseWiggle * (float) Math.sin(time * 40) * p;
        for (int k = 0; k <= 18; k++) {
            float t = k / 18f, u = 1 - t;
            float hx = u * u * sx + 2 * u * t * cx + t * t * ex;
            float hy = u * u * sy + 2 * u * t * cy + t * t * ey + wig * (float) Math.sin(t * Math.PI);
            rect.set(hx, hy, hx + p * 1.2f, hy + p * 1.2f); c.drawRect(rect, paint);
        }
        // Düse (Öffnung zeigt nach rechts, dem Förderband entgegen)
        paint.setColor(0xFFD7DAE0); rect.set(nozzleX - 3.5f * p, y - 2.8f * p, nozzleX, y); c.drawRect(rect, paint);
        paint.setColor(0xFF1E2024); rect.set(nozzleX - 0.8f * p, y - 2.2f * p, nozzleX, y - 0.4f * p); c.drawRect(rect, paint);
    }

    private void drawItem(Canvas c, Item it, float x, float y, float scale) {
        float w = w(it.type), h = h(it.type); int top, side;
        switch (it.type) {
            case 1: top = 0xFFB3B7BE; side = 0xFF7A7E86; break;   // Stein
            case 2: top = 0xFFC98A4B; side = 0xFF9C6534; break;   // Kiste
            case 3: top = 0xFF8E929A; side = 0xFF5F636A; break;   // Block
            default: top = 0xFFD7DAE0; side = 0xFF9A9EA6; break;  // Bahnstein (hell, fällt auf)
        }
        paint.setColor(side);
        rect.set(x, y, x + w * p * scale, y + h * p * scale); c.drawRect(rect, paint);
        paint.setColor(top);
        rect.set(x, y, x + w * p * scale, y + Math.min(h, 0.9f) * p * scale); c.drawRect(rect, paint);
    }

    private void px(Canvas c, float gx, float gy, float w, float h, int color) {
        paint.setColor(color);
        rect.set(footX + gx * p, surfaceY + gy * p, footX + (gx + w) * p + 0.5f, surfaceY + (gy + h) * p + 0.5f);
        c.drawRect(rect, paint);
    }

    /** Schluck: Kopf, dünner Körper, lange Beine und Arme – in Rasterzellen relativ zu den Füßen. */
    private void drawHelper(Canvas c) {
        int lv = level();
        boolean sleep = sleeping() || napTimer > 0;
        float stress = Math.min(3f, 1 + 0.28f * (lv - 1));
        float bob = sleep ? 0 : (float) Math.sin(time * 5) * (0.4f + 0.25f * stress);
        soft.setColor(0x40000000);
        rect.set(footX - 5 * p, surfaceY - p * 0.6f, footX + 6 * p, surfaceY + p * 0.6f);
        c.drawOval(rect, soft);
        // lange Beine
        px(c, -3, -11, 1.4f, 11, BODY_DARK);
        px(c, 2, -11, 1.4f, 11, BODY_DARK);
        px(c, -4, -1, 3, 1, INK); px(c, 2, -1, 3, 1, INK);
        float bodyTop = -19 + bob;
        float belly = 2.6f * Math.min(1, fullness);     // runder, je satter
        px(c, -2.5f, bodyTop, 5, 8.5f, BODY);
        if (belly > 0.3f) { px(c, -2.5f - belly / 2, bodyTop + 3, 5 + belly, 5f, BODY); px(c, -1.5f - belly / 2, bodyTop + 2, 3 + belly, 7f, BODY); }
        px(c, -2.5f - belly / 2, bodyTop + 6.5f, 5 + belly, 2, BODY_DARK);
        // rechter Arm hält den Schlauch (zur Hand oben vorn), linker Arm locker bzw. zum Mund beim Essen
        for (int k = 0; k < 6; k++) px(c, 2.5f + k * 0.4f, bodyTop + 1 + k * 0.9f, 1, 1, BODY_DARK);
        float reach = sleep ? 0 : armReach;
        for (int k = 0; k < 8; k++) {
            float bx = -3.5f - k * 0.35f + k * reach * 0.8f;
            float by = bodyTop + 1 + k * (0.9f - 1.6f * reach);
            px(c, bx, by, 1, 1, BODY_DARK);
        }
        float headTop = bodyTop - 9;
        px(c, -5, headTop, 11, 9, BODY);
        px(c, -5, headTop, 11, 1.2f, 0xFFB9CEFF);
        if (sleep) {
            px(c, -3, headTop + 3.5f, 2.2f, 0.6f, INK); px(c, 2, headTop + 3.5f, 2.2f, 0.6f, INK);
        } else {
            float eye = lv >= 5 ? 2.6f : 2f;
            px(c, -3, headTop + 2.2f, eye, eye, 0xFFFFFFFF); px(c, 2, headTop + 2.2f, eye, eye, 0xFFFFFFFF);
            px(c, -1.8f, headTop + 3f, 1, 1, INK); px(c, 3.2f, headTop + 3f, 1, 1, INK); // schaut nach rechts zum Band
            if (lv >= 5) { px(c, -3.4f, headTop + 1.2f, 2.4f, 0.5f, INK); px(c, 2.2f, headTop + 0.8f, 2.4f, 0.5f, INK); }
        }
        float open = sleep ? 0.1f : chewing > 0 ? (((int) (chewing * 10)) % 2 == 0 ? 0.6f : 0.15f) : mouthOpen;
        float mh = 0.6f + 2.4f * open, mw = 2 + 1.2f * open;
        px(c, 3 - mw / 2 + 0.5f, headTop + 6.2f - mh / 2 + 0.8f, mw, mh, INK);
        if (!sleep && lv >= 5) {
            float sy = headTop + 1 + (sweatPhase * 6 % 5);
            px(c, -6.5f, sy, 0.9f, 1.3f, 0xFF9FD3FF);
        }
        if (sleep) {
            paint.setColor(0xFF6E7BA8);
            boolean big = ((int) (time * 1.5f)) % 2 == 0 || forceLevel != null;
            PixelText.draw(c, big ? "ZZ" : "Z", footX + 7 * p, surfaceY + (headTop - 4) * p, p * (big ? 0.6f : 0.5f), paint);
        }
        if (bubble != null && !sleep) {
            float gc = p * 0.5f, tw = PixelText.measure(bubble, gc), pad = p * 1.2f;
            float bx = footX + 8 * p, by = surfaceY + (headTop - 7) * p;
            soft.setColor(0xFF9CBDFF);
            rect.set(bx, by, bx + tw + 2 * pad, by + 7 * gc + 2 * pad);
            c.drawRoundRect(rect, p * 1.5f, p * 1.5f, soft);
            paint.setColor(INK);
            PixelText.draw(c, bubble, bx + pad, by + pad, gc, paint);
        }
    }
}
