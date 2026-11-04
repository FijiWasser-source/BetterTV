package de.bettertv.launcher;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Text aus 5×7-Punkten im Retro-Stil (eigene Zeichen, keine Schriftdatei nötig). */
public class PixelText extends View {

    private static final Map<Character, String[]> GLYPHS = new HashMap<>();
    private static void g(char c, String... rows) { GLYPHS.put(c, rows); }
    static {
        g('0', "01110", "10001", "10011", "10101", "11001", "10001", "01110");
        g('1', "00100", "01100", "00100", "00100", "00100", "00100", "01110");
        g('2', "01110", "10001", "00001", "00010", "00100", "01000", "11111");
        g('3', "11110", "00001", "00001", "01110", "00001", "00001", "11110");
        g('4', "00010", "00110", "01010", "10010", "11111", "00010", "00010");
        g('5', "11111", "10000", "11110", "00001", "00001", "10001", "01110");
        g('6', "00110", "01000", "10000", "11110", "10001", "10001", "01110");
        g('7', "11111", "00001", "00010", "00100", "01000", "01000", "01000");
        g('8', "01110", "10001", "10001", "01110", "10001", "10001", "01110");
        g('9', "01110", "10001", "10001", "01111", "00001", "00010", "01100");
        g('A', "01110", "10001", "10001", "11111", "10001", "10001", "10001");
        g('B', "11110", "10001", "10001", "11110", "10001", "10001", "11110");
        g('C', "01110", "10001", "10000", "10000", "10000", "10001", "01110");
        g('D', "11110", "10001", "10001", "10001", "10001", "10001", "11110");
        g('E', "11111", "10000", "10000", "11110", "10000", "10000", "11111");
        g('F', "11111", "10000", "10000", "11110", "10000", "10000", "10000");
        g('G', "01110", "10001", "10000", "10111", "10001", "10001", "01111");
        g('H', "10001", "10001", "10001", "11111", "10001", "10001", "10001");
        g('I', "01110", "00100", "00100", "00100", "00100", "00100", "01110");
        g('J', "00111", "00010", "00010", "00010", "00010", "10010", "01100");
        g('K', "10001", "10010", "10100", "11000", "10100", "10010", "10001");
        g('L', "10000", "10000", "10000", "10000", "10000", "10000", "11111");
        g('M', "10001", "11011", "10101", "10101", "10001", "10001", "10001");
        g('N', "10001", "10001", "11001", "10101", "10011", "10001", "10001");
        g('O', "01110", "10001", "10001", "10001", "10001", "10001", "01110");
        g('P', "11110", "10001", "10001", "11110", "10000", "10000", "10000");
        g('Q', "01110", "10001", "10001", "10001", "10101", "10010", "01101");
        g('R', "11110", "10001", "10001", "11110", "10100", "10010", "10001");
        g('S', "01111", "10000", "10000", "01110", "00001", "00001", "11110");
        g('T', "11111", "00100", "00100", "00100", "00100", "00100", "00100");
        g('U', "10001", "10001", "10001", "10001", "10001", "10001", "01110");
        g('V', "10001", "10001", "10001", "10001", "10001", "01010", "00100");
        g('W', "10001", "10001", "10001", "10101", "10101", "10101", "01010");
        g('X', "10001", "10001", "01010", "00100", "01010", "10001", "10001");
        g('Y', "10001", "10001", "01010", "00100", "00100", "00100", "00100");
        g('Z', "11111", "00001", "00010", "00100", "01000", "10000", "11111");
        g('Ä', "01010", "00000", "01110", "10001", "11111", "10001", "10001");
        g('Ö', "01010", "00000", "01110", "10001", "10001", "10001", "01110");
        g('Ü', "01010", "00000", "10001", "10001", "10001", "10001", "01110");
        g(':', "000", "010", "010", "000", "010", "010", "000");
        g('.', "00", "00", "00", "00", "00", "11", "11");
        g(',', "00", "00", "00", "00", "11", "01", "10");
        g('°', "0110", "1001", "1001", "0110", "0000", "0000", "0000");
        g('-', "0000", "0000", "0000", "1111", "0000", "0000", "0000");
        g('·', "0", "0", "0", "1", "0", "0", "0");
        g(' ', "00", "00", "00", "00", "00", "00", "00");
        g('!', "1", "1", "1", "1", "1", "0", "1");
        g('?', "01110", "10001", "00001", "00110", "00100", "00000", "00100");
        g('\'', "1", "1", "0", "0", "0", "0", "0");
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dot = new RectF();
    private float cell;
    private String text = "";
    private boolean blinkColon;
    private boolean colonOn = true;

    public PixelText(Context c) { this(c, null); }

    public PixelText(Context c, AttributeSet attrs) {
        super(c, attrs);
        float d = getResources().getDisplayMetrics().density;
        cell = 3 * d;
        int color = 0xFFFFFFFF;
        if (attrs != null) {
            TypedArray a = c.obtainStyledAttributes(attrs, R.styleable.PixelText);
            cell = a.getDimension(R.styleable.PixelText_pixelSize, cell);
            color = a.getColor(R.styleable.PixelText_pixelColor, color);
            blinkColon = a.getBoolean(R.styleable.PixelText_blinkColon, false);
            a.recycle();
        }
        paint.setColor(color);
    }

    public void setText(String t) {
        String up = t == null ? "" : t.toUpperCase(Locale.GERMAN);
        if (up.equals(text)) return;
        text = up;
        requestLayout();
        invalidate();
    }

    /** Doppelpunkt blinken lassen (Uhr). */
    public void tick() {
        if (!blinkColon) return;
        colonOn = !colonOn;
        invalidate();
    }

    private static String[] glyph(char c) {
        String[] g = GLYPHS.get(c);
        return g != null ? g : GLYPHS.get(' ');
    }

    private float textWidth() {
        float w = 0;
        for (int i = 0; i < text.length(); i++) {
            w += glyph(text.charAt(i))[0].length() * cell;
            if (i < text.length() - 1) w += cell; // Abstand
        }
        return w;
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(resolveSize(Math.round(textWidth()) + getPaddingLeft() + getPaddingRight(), wSpec),
                resolveSize(Math.round(7 * cell) + getPaddingTop() + getPaddingBottom(), hSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        float x = getPaddingLeft(), y0 = getPaddingTop();
        float inset = cell * 0.12f, r = cell * 0.22f;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            String[] g = glyph(ch);
            boolean draw = !(ch == ':' && blinkColon && !colonOn);
            if (draw) {
                for (int row = 0; row < 7; row++) {
                    String line = g[row];
                    for (int col = 0; col < line.length(); col++) {
                        if (line.charAt(col) != '1') continue;
                        dot.set(x + col * cell + inset, y0 + row * cell + inset,
                                x + (col + 1) * cell - inset, y0 + (row + 1) * cell - inset);
                        canvas.drawRoundRect(dot, r, r, paint);
                    }
                }
            }
            x += g[0].length() * cell + cell;
        }
    }

    /** Breite eines Textes in Pixel-Schrift. */
    static float measure(String text, float cell) {
        String up = text.toUpperCase(Locale.GERMAN);
        float w = 0;
        for (int i = 0; i < up.length(); i++) w += glyph(up.charAt(i))[0].length() * cell + cell;
        return Math.max(0, w - cell);
    }

    /** Zeichnet Text in Pixel-Schrift direkt auf eine Zeichenfläche (z. B. für das Mini-Spiel). */
    private static final RectF DRAW_RECT = new RectF(); // nur im UI-Thread benutzt

    static void draw(Canvas c, String text, float x, float y, float cell, Paint p) {
        RectF r = DRAW_RECT;
        String up = text.toUpperCase(Locale.GERMAN);
        for (int i = 0; i < up.length(); i++) {
            String[] g = glyph(up.charAt(i));
            for (int row = 0; row < 7; row++) {
                for (int col = 0; col < g[row].length(); col++) {
                    if (g[row].charAt(col) != '1') continue;
                    r.set(x + col * cell, y + row * cell, x + (col + 1) * cell, y + (row + 1) * cell);
                    c.drawRect(r, p);
                }
            }
            x += g[0].length() * cell + cell;
        }
    }
}
