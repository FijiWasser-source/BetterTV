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

    static final class Piece {
        float x, y, vx, vy, size; int color; float age; boolean sucking;
    }

    private static final int BODY = 0xFF8FB4FF, BODY_DARK = 0xFF6F94DF, INK = 0xFF1B2640;

    private final Paint paint = new Paint();
    private final Paint soft = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Random rnd = new Random();
    private final List<Piece> pieces = new ArrayList<>();
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

    /** Ein Stein bröckelt ab (Bildschirmkoordinaten). */
    public void addPiece(float screenX, float screenY, float size, int color) {
        getLocationOnScreen(loc);
        Piece pc = new Piece();
        pc.x = screenX - loc[0]; pc.y = screenY - loc[1]; pc.size = size; pc.color = color;
        pc.vx = -(20 + rnd.nextFloat() * 30) * p; pc.vy = -(4 + rnd.nextFloat() * 8) * p;
        if (pieces.size() < 40) pieces.add(pc);
    }

    /** Vorschau: Figur mitten beim Einsaugen bzw. Essen. */
    void preview(int level, boolean eating) {
        forceLevel = level; forceSleep = false;
        pieces.clear();
        previewPieces = true;
        mouthOpen = eating ? 0.5f : 1f; armReach = 1f; chewing = eating ? 0.5f : 0;
        if (eating) { bubble = "MAMPF!"; bubbleAge = 0.3f; }
        invalidate();
    }

    private boolean previewPieces;

    /** Vorschau-Steine erst anlegen, wenn die Position der Kachel feststeht. */
    private void placePreviewPieces() {
        previewPieces = false;
        if (hasSpot) {
            for (int k = 0; k < 4; k++) {   // auf der Flugbahn von der Laufbahn zum Mund
                Piece pc = new Piece();
                pc.x = mouthX() + (8 + k * 15) * p; pc.y = mouthY() - (2 + k * 3.5f) * p;
                pc.size = (2.4f + k * 0.4f) * p; pc.color = 0xFF868A92; pc.sucking = k < 2;
                pieces.add(pc);
            }
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
        footX = other[0] - loc[0] + base.getWidth() * 0.32f;
        footY = top + 1;
        hasSpot = true;
    }

    private float mouthX() { return footX + 3 * p; }
    private float mouthY() { return footY - 27 * p; }

    private void step(float dt) {
        int lv = level();
        float stress = Math.min(3f, 1 + 0.28f * (lv - 1));
        time += dt * stress;
        sweatPhase += dt;
        boolean anyClose = false;
        for (Iterator<Piece> it = pieces.iterator(); it.hasNext(); ) {
            Piece pc = it.next();
            pc.age += dt;
            float dx = mouthX() - pc.x, dy = mouthY() - pc.y;
            float dist = (float) Math.hypot(dx, dy);
            if (!hasSpot) { pc.vy += 220 * p * dt; pc.x += pc.vx * dt; pc.y += pc.vy * dt; }
            else if (pc.sucking || dist < 30 * p) {
                pc.sucking = true; anyClose = true;
                float sp = (90 + 60 * stress) * p;              // Sog
                pc.x += dx / Math.max(1, dist) * sp * dt;
                pc.y += dy / Math.max(1, dist) * sp * dt;
                pc.size = Math.max(p * 0.8f, pc.size - p * 2.5f * dt);
                if (dist < 3 * p) {
                    it.remove();
                    boolean bite = lv >= 4 && chewing <= 0 && rnd.nextInt(100) < 6 + lv * 2;
                    if (bite) {
                        chewing = 0.9f;
                        if (rnd.nextBoolean()) { bubble = rnd.nextBoolean() ? "MAMPF!" : "LECKER!"; bubbleAge = 0; }
                        for (int k = 0; k < 6; k++) crumbs.add(new float[]{mouthX(), mouthY() + 2 * p, (20 + rnd.nextFloat() * 30) * p, 0, (rnd.nextFloat() - 0.5f) * 30 * p});
                        if (events != null) events.onCrunch();
                    } else {
                        long now = SystemClock.uptimeMillis();
                        if (events != null && now - lastSuckSound > 300) { events.onSuck(); lastSuckSound = now; }
                    }
                    continue;
                }
            } else {
                // Fallen, dabei sanft zur Figur gezogen
                pc.vy += 140 * p * dt;
                pc.vx += dx * 0.9f * dt;
                pc.vy = Math.min(pc.vy, 70 * p);
                pc.x += pc.vx * dt; pc.y += pc.vy * dt;
            }
            if (pc.y > getHeight() + 20 * p || pc.age > 8) it.remove();
        }
        mouthOpen += ((anyClose ? 1f : 0.1f) - mouthOpen) * Math.min(1, dt * 8);
        armReach += ((anyClose ? 1f : 0f) - armReach) * Math.min(1, dt * 6);
        chewing = Math.max(0, chewing - dt);
        bubbleAge += dt;
        if (bubbleAge > 1.6f) bubble = null;
        if (bubble == null && lv >= 6 && rnd.nextInt(600) == 0) { bubble = "ZU SCHNELL!"; bubbleAge = 0; }
        for (Iterator<float[]> it = crumbs.iterator(); it.hasNext(); ) {
            float[] c = it.next();
            c[3] += dt; c[2] += 160 * p * dt; c[1] += c[2] * dt; c[0] += c[4] * dt;
            if (c[3] > 1.2f) it.remove();
        }
    }

    @Override protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float dt = Math.min(0.05f, (now - last) / 1000f);
        last = now;
        findSpot();
        if (previewPieces) placePreviewPieces();
        if (forceLevel == null && !sleeping()) step(dt);

        // fallende / eingesaugte Steine
        for (Piece pc : pieces) {
            paint.setColor(pc.color);
            rect.set(pc.x, pc.y, pc.x + pc.size, pc.y + pc.size * 0.7f);
            c.drawRect(rect, paint);
            if (pc.sucking) { // kurze, verblassende Sog-Spur hinter dem Stein
                float dx = pc.x - mouthX(), dy = pc.y - mouthY();
                float len = Math.max(1, (float) Math.hypot(dx, dy));
                for (int k = 1; k <= 3; k++) {
                    paint.setColor(((70 - k * 18) << 24) | 0xFFFFFF);
                    float tx = pc.x + dx / len * k * 2.2f * p, ty = pc.y + dy / len * k * 2.2f * p;
                    float sz = pc.size * (1 - k * 0.22f);
                    rect.set(tx, ty, tx + sz, ty + sz * 0.7f);
                    c.drawRect(rect, paint);
                }
            }
        }
        if (hasSpot) drawHelper(c);
        for (float[] cr : crumbs) {
            paint.setColor(((int) (255 * Math.max(0, 1 - cr[3] / 1.2f)) << 24) | 0xA3A7AE);
            rect.set(cr[0], cr[1], cr[0] + p * 0.8f, cr[1] + p * 0.8f);
            c.drawRect(rect, paint);
        }
        if (running && forceLevel == null) postInvalidateDelayed(33);
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
