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
 * Durchsichtige Ebene über der Startansicht. Die Steine, die hinter Pixel wegbröckeln, fallen
 * herunter und werden zur ersten App gezogen. Dort steht "Schluck", ein schlaksiger Kerl mit
 * langen Gliedmaßen (eigene Figur), und saugt sie ein – je höher das Level, desto hektischer.
 * Ab Level 4 beißt er manchmal ein Stück ab. Nachts schläft er.
 */
public class HelperLayer extends View {

    public interface Events {
        void onSuck();
        void onCrunch();
    }

    /** Ein Teil auf dem Haufen: 0 Bahnstein, 1 Stein, 2 Kiste, 3 Block. */
    static final class Item {
        int type; float x;          // x: Position auf der Fläche (px, lokal)
        float drop, vy;             // Höhe über der Fläche beim Herunterfallen
        float suck = -1;            // >= 0: wird gerade eingesaugt (0..1)
        boolean eat;
    }

    private static final int BODY = 0xFF8FB4FF, BODY_DARK = 0xFF6F94DF, INK = 0xFF1B2640;

    private final Paint paint = new Paint();
    private final Paint soft = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Random rnd = new Random();
    private final List<Item> pile = new ArrayList<>();
    private static final int MAX_PILE = 10;
    private float nextBite = 1f;
    private float spotLeft, spotRight;   // Fläche des Icons rechts neben Schluck
    private final List<float[]> crumbs = new ArrayList<>(); // {x, y, vy, alter}
    private final int[] loc = new int[2], other = new int[2];
    private float p; // Pixelgröße der Figur
    private Mascot mascot;
    private RecyclerView dock;
    private Events events;
    private boolean running;
    private long last, lastSuckSound;
    private float time, mouthOpen, chewing, armReach, sweatPhase;
    private String bubble; private float bubbleAge;
    private Integer forceLevel; private boolean forceSleep; // nur Vorschau
    // Fuß-Mitte der Figur (lokal) und ob eine Standfläche gefunden wurde
    private float footX, footY; private boolean hasSpot;

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

    /** Ein Teil fällt auf das Icon (0 Bahnstein, 1 Stein, 2 Kiste, 3 Block). */
    public void addPiece(int type) {
        if (pile.size() >= MAX_PILE || !hasSpot) return;
        Item it = new Item();
        it.type = type;
        it.x = spotLeft + rnd.nextFloat() * Math.max(1, spotRight - spotLeft - 6 * p);
        it.drop = (12 + rnd.nextFloat() * 6) * p;
        pile.add(it);
    }

    /** Kompatibel zum alten Aufruf (Bildschirmkoordinaten werden nicht mehr gebraucht). */
    public void addPiece(float screenX, float screenY, float size, int color) { addPiece(0); }

    /** Vorschau: Figur mitten beim Einsaugen bzw. Essen. */
    void preview(int level, boolean eating) {
        forceLevel = level; forceSleep = false;
        pile.clear();
        previewPieces = true;
        mouthOpen = eating ? 0.5f : 1f; armReach = 1f; chewing = eating ? 0.5f : 0;
        if (eating) { bubble = "MAMPF!"; bubbleAge = 0.3f; }
        invalidate();
    }

    private boolean previewPieces;

    /** Vorschau-Haufen erst anlegen, wenn die Position des Icons feststeht. */
    private void placePreviewPieces() {
        previewPieces = false;
        if (!hasSpot) return;
        pile.clear();
        int[] types = {0, 2, 1, 0, 3, 0};
        for (int k = 0; k < types.length; k++) {
            Item it = new Item(); it.type = types[k];
            it.x = spotLeft + k * (spotRight - spotLeft - 4 * p) / types.length;
            pile.add(it);
        }
        pile.get(0).suck = 0.55f;
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); resume(); }
    @Override protected void onDetachedFromWindow() { running = false; super.onDetachedFromWindow(); }
    @Override protected void onWindowVisibilityChanged(int v) { super.onWindowVisibilityChanged(v); if (v == VISIBLE) resume(); else running = false; }

    private void resume() {
        running = isAttachedToWindow() && getWindowVisibility() == VISIBLE;
        last = SystemClock.uptimeMillis();
        if (running) postInvalidateOnAnimation();
    }

    /** Standfläche: Oberkante der ersten sichtbaren App (Kachel bzw. Logo). */
    private void findSpot() {
        hasSpot = false;
        if (dock == null || dock.getChildCount() == 0) return;
        View item = dock.getChildAt(0);
        View card = item.findViewById(R.id.card);
        if (card == null) return;
        boolean free = card.getBackground() instanceof AbendTile && ((AbendTile) card.getBackground()).invisible;
        View base = free ? card.findViewById(R.id.banner) : card;
        if (base == null || base.getVisibility() != VISIBLE) base = card;
        getLocationOnScreen(loc);
        base.getLocationOnScreen(other);
        float d = getResources().getDisplayMetrics().density;
        float top = other[1] - loc[1] + (base == card ? 10 * d : 0);
        float left = other[0] - loc[0];
        footX = left + base.getWidth() * 0.2f;
        footY = top + 1;
        spotLeft = footX + 8 * p;
        spotRight = left + base.getWidth() - 2 * p;
        hasSpot = true;
    }

    private float mouthX() { return footX + 3 * p; }
    private float mouthY() { return footY - 27 * p; }

    private void step(float dt) {
        int lv = level();
        boolean crowded = pile.size() >= 6;
        float stress = Math.min(3f, 1 + 0.28f * (lv - 1) + (crowded ? 0.6f : 0));
        time += dt * stress;
        sweatPhase += dt;
        // herunterfallende Teile landen auf der Fläche
        for (Item it : pile) {
            if (it.drop > 0) { it.vy += 160 * p * dt; it.drop = Math.max(0, it.drop - it.vy * dt); if (it.drop == 0) it.vy = 0; }
        }
        // Tempo: Level 1 gemütlich (~1 pro Sekunde), höher und bei vollem Haufen schneller
        float interval = Math.max(0.28f, 1.0f / (1 + 0.35f * (lv - 1))) * (crowded ? 0.7f : 1f);
        Item current = null;
        for (Item it : pile) if (it.suck >= 0) { current = it; break; }
        if (current == null) {
            nextBite -= dt;
            if (nextBite <= 0 && !pile.isEmpty()) {
                Item nearest = null;
                for (Item it : pile) if (it.drop == 0 && (nearest == null || it.x < nearest.x)) nearest = it;
                if (nearest != null) {
                    nearest.suck = 0;
                    nearest.eat = (lv >= 4 || crowded) && rnd.nextInt(100) < 10 + lv * 3 + (crowded ? 15 : 0);
                    current = nearest;
                }
                nextBite = interval;
            }
        }
        if (current != null) {
            current.suck += dt / (0.18f + 0.5f * interval);
            if (current.suck >= 1) {
                pile.remove(current);
                if (current.eat) {
                    chewing = 0.9f;
                    if (rnd.nextBoolean()) { bubble = rnd.nextBoolean() ? "MAMPF!" : "LECKER!"; bubbleAge = 0; }
                    for (int k = 0; k < 6; k++) crumbs.add(new float[]{mouthX(), mouthY() + 2 * p, (20 + rnd.nextFloat() * 30) * p, 0, (rnd.nextFloat() - 0.5f) * 30 * p});
                    if (events != null) events.onCrunch();
                } else {
                    long now = SystemClock.uptimeMillis();
                    if (events != null && now - lastSuckSound > 250) { events.onSuck(); lastSuckSound = now; }
                }
            }
        }
        boolean active = current != null;
        mouthOpen += ((active && !(current != null && current.eat) ? 1f : 0.1f) - mouthOpen) * Math.min(1, dt * 8);
        armReach += ((active ? 1f : 0f) - armReach) * Math.min(1, dt * 6);
        chewing = Math.max(0, chewing - dt);
        bubbleAge += dt;
        if (bubbleAge > 1.6f) bubble = null;
        if (bubble == null && pile.size() >= 8 && rnd.nextInt(240) == 0) { bubble = "ZU VIELE!"; bubbleAge = 0; }
        for (Iterator<float[]> it = crumbs.iterator(); it.hasNext(); ) {
            float[] c = it.next();
            c[3] += dt; c[2] += 160 * p * dt; c[1] += c[2] * dt; c[0] += c[4] * dt;
            if (c[3] > 1.2f) it.remove();
        }
    }

    private void drawItem(Canvas c, Item it) {
        float w, h; int top, side;
        switch (it.type) {
            case 1: w = 3; h = 2; top = 0xFF8E929A; side = 0xFF6C7078; break;   // Stein
            case 2: w = 3; h = 3; top = 0xFFC98A4B; side = 0xFF9C6534; break;   // Kiste
            case 3: w = 2; h = 3.5f; top = 0xFF8E929A; side = 0xFF5F636A; break; // Block
            default: w = 4; h = 1.6f; top = 0xFFA3A7AE; side = 0xFF6C7078; break; // Bahnstein
        }
        float x = it.x, y = footY - it.drop - h * p * 1.5f;
        float scale = 1.5f;   // gut sichtbar auf dem Fernseher
        if (it.suck >= 0) { // auf dem Weg in den Mund: hinziehen und kleiner werden
            float k = it.suck * it.suck;
            x = it.x + (mouthX() - it.x) * k;
            y = y + (mouthY() - y) * k;
            scale = 1.5f * (1 - 0.7f * k);
        }
        paint.setColor(side);
        rect.set(x, y, x + w * p * scale, y + h * p * scale);
        c.drawRect(rect, paint);
        paint.setColor(top);
        rect.set(x, y, x + w * p * scale, y + Math.min(h, 0.9f) * p * scale);
        c.drawRect(rect, paint);
    }

    @Override protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float dt = Math.min(0.05f, (now - last) / 1000f);
        last = now;
        findSpot();
        if (previewPieces) placePreviewPieces();
        if (!hasSpot) pile.clear();
        if (forceLevel == null && !sleeping()) step(dt);

        if (hasSpot) for (Item it : pile) if (it.suck < 0) drawItem(c, it);
        if (hasSpot) drawHelper(c);
        if (hasSpot) for (Item it : pile) if (it.suck >= 0) drawItem(c, it);   // vor dem Mund
        for (float[] cr : crumbs) {
            paint.setColor(((int) (255 * Math.max(0, 1 - cr[3] / 1.2f)) << 24) | 0xA3A7AE);
            rect.set(cr[0], cr[1], cr[0] + p * 0.8f, cr[1] + p * 0.8f);
            c.drawRect(rect, paint);
        }
        boolean busy = !pile.isEmpty() || !crumbs.isEmpty() || chewing > 0;
        if (running && forceLevel == null) postInvalidateDelayed(busy ? 40 : 90); // ruhig = seltener zeichnen
    }

    private void px(Canvas c, float gx, float gy, float w, float h, int color) {
        paint.setColor(color);
        rect.set(footX + gx * p, footY + gy * p, footX + (gx + w) * p + 0.5f, footY + (gy + h) * p + 0.5f);
        c.drawRect(rect, paint);
    }

    /** Schluck: Kopf, dünner Körper, lange Beine und Arme – alles in Rasterzellen relativ zu den Füßen. */
    private void drawHelper(Canvas c) {
        int lv = level();
        boolean sleep = sleeping();
        float stress = Math.min(3f, 1 + 0.28f * (lv - 1));
        float bob = sleep ? 0 : (float) Math.sin(time * 5) * (0.4f + 0.25f * stress);
        float step = sleep ? 0 : (float) Math.sin(time * 4);
        // Schatten
        soft.setColor(0x40000000);
        rect.set(footX - 5 * p, footY - p * 0.6f, footX + 6 * p, footY + p * 0.6f);
        c.drawOval(rect, soft);
        // lange Beine (leichtes Trippeln, wenn gestresst)
        float lift = stress > 1.5f ? Math.max(0, step) * 1.2f : 0;
        px(c, -3, -11 - lift, 1.4f, 11 + lift - lift, BODY_DARK);
        px(c, 2, -11 + lift * 0, 1.4f, 11 - lift * 0.0f, BODY_DARK);
        px(c, -4, -1, 3, 1, INK); px(c, 2, -1, 3, 1, INK); // Füße
        float bodyTop = -19 + bob;
        // Körper (dünn)
        px(c, -2.5f, bodyTop, 5, 8.5f, BODY);
        px(c, -2.5f, bodyTop + 6.5f, 5, 2, BODY_DARK);
        // Arme: ausgestreckt nach oben-rechts beim Einsaugen, sonst locker
        float reach = sleep ? 0 : armReach;
        for (int k = 0; k < 9; k++) {
            float ax = 2.5f + k * (0.35f + 0.65f * reach);
            float ay = bodyTop + 1 + k * (1 - reach) * 0.9f - k * reach * 0.8f;
            px(c, ax, ay, 1, 1, BODY_DARK);
            float bx = -3.5f - k * (0.35f - 0.2f * reach);
            float by = bodyTop + 1 + k * (0.9f - 0.5f * reach);
            px(c, bx, by, 1, 1, BODY_DARK);
        }
        // Kopf
        float headTop = bodyTop - 9;
        px(c, -5, headTop, 11, 9, BODY);
        px(c, -5, headTop, 11, 1.2f, 0xFFB9CEFF); // Glanz
        // Augen: gestresst weit offen, nachts zu
        if (sleep) {
            px(c, -3, headTop + 3.5f, 2.2f, 0.6f, INK); px(c, 2, headTop + 3.5f, 2.2f, 0.6f, INK);
        } else {
            float eye = lv >= 5 ? 2.6f : 2f;
            px(c, -3, headTop + 2.2f, eye, eye, 0xFFFFFFFF); px(c, 2, headTop + 2.2f, eye, eye, 0xFFFFFFFF);
            px(c, -2.2f, headTop + 3f, 1, 1, INK); px(c, 2.8f, headTop + 3f, 1, 1, INK);
            if (lv >= 5) { px(c, -3.4f, headTop + 1.2f, 2.4f, 0.5f, INK); px(c, 2.2f, headTop + 0.8f, 2.4f, 0.5f, INK); } // Brauen
        }
        // Mund: rund offen beim Saugen, kauend beim Essen
        float open = sleep ? 0.1f : chewing > 0 ? (((int) (chewing * 10)) % 2 == 0 ? 0.6f : 0.15f) : mouthOpen;
        float mh = 0.6f + 2.6f * open, mw = 2 + 1.4f * open;
        px(c, 3 - mw / 2 + 0.5f, headTop + 6.2f - mh / 2 + 0.8f, mw, mh, INK);
        // Schweißtropfen bei hohem Level
        if (!sleep && lv >= 5) {
            float sy = headTop + 1 + (sweatPhase * 6 % 5);
            px(c, -6.5f, sy, 0.9f, 1.3f, 0xFF9FD3FF);
        }
        if (sleep) { paint.setColor(0xFF6E7BA8); PixelText.draw(c, "Z", footX + 7 * p, footY + (headTop - 4) * p, p * 0.5f, paint); }
        // Sprechblase
        if (bubble != null && !sleep) {
            float gc = p * 0.5f, tw = PixelText.measure(bubble, gc), pad = p * 1.2f;
            float bx = footX + 8 * p, by = footY + (headTop - 7) * p;
            soft.setColor(0xFF9CBDFF);
            rect.set(bx, by, bx + tw + 2 * pad, by + 7 * gc + 2 * pad);
            c.drawRoundRect(rect, p * 1.5f, p * 1.5f, soft);
            paint.setColor(INK);
            PixelText.draw(c, bubble, bx + pad, by + pad, gc, paint);
        }
    }
}
