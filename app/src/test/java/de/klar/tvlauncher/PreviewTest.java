package de.klar.tvlauncher;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import app.cash.paparazzi.DeviceConfig;
import app.cash.paparazzi.EnvironmentKt;
import app.cash.paparazzi.Paparazzi;

import com.android.resources.*;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Rendert eine Vorschau der Startseite (ohne Gerät): ./gradlew recordPaparazziDebug */
public class PreviewTest {
    @Rule
    public Paparazzi paparazzi = new Paparazzi(EnvironmentKt.detectEnvironment(),
            new DeviceConfig(1920, 1080, 320, 320, ScreenOrientation.LANDSCAPE, UiMode.TELEVISION,
                    NightMode.NIGHT, Density.XHIGH, 1f, LayoutDirection.LTR, "de", ScreenRatio.LONG,
                    ScreenSize.XLARGE, Keyboard.NOKEY, TouchScreen.NOTOUCH, KeyboardState.HIDDEN, true,
                    Navigation.DPAD, ScreenRound.NOTROUND, "tv"),
            "Theme.Klar");

    /** Platzhalter-Banner: Farbverlauf mit App-Namen (echte Apps liefern ihr eigenes Banner). */
    static final class FakeBanner extends Drawable {
        private final String name; private final int color; private final boolean white;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        FakeBanner(String name, int color) { this(name, color, false); }
        FakeBanner(String name, int color, boolean white) { this.name = name; this.color = color; this.white = white; }
        @Override public int getIntrinsicWidth() { return 320; }
        @Override public int getIntrinsicHeight() { return 180; }
        @Override public void draw(Canvas c) {
            float w = getBounds().width(), h = getBounds().height();
            if (white) { paint.setColor(0xFFF7F7F7); c.drawRect(getBounds(), paint); }
            else {
                paint.setShader(new LinearGradient(0, 0, w, h, color, Tint.darken(color, 0.4f), Shader.TileMode.CLAMP));
                c.drawRect(getBounds(), paint);
                paint.setShader(null);
            }
            paint.setColor(white ? color : Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            paint.setTextSize(h * 0.2f);
            c.drawText(name, w / 2, h / 2 + h * 0.07f, paint);
        }
        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter f) {}
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    /** Platzhalter im Stil deines Bildes: Mondnacht über Bergsee. */
    static android.graphics.Bitmap fakeWallpaper() {
        int w = 1920, h = 1080;
        float horizon = h * 0.60f;
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, horizon, 0xFF0B2233, 0xFF15405C, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, horizon, p);
        float mx = w * 0.76f, my = h * 0.24f;
        p.setShader(new android.graphics.RadialGradient(mx, my, 300, 0x5590CFFF, 0x0090CFFF, Shader.TileMode.CLAMP));
        c.drawCircle(mx, my, 300, p);
        p.setShader(new android.graphics.RadialGradient(mx - 30, my - 30, 140, 0xFFE6F4FF, 0xFF7DB8E8, Shader.TileMode.CLAMP));
        c.drawCircle(mx, my, 120, p);
        p.setShader(null);
        drawRidge(c, p, w, h, 0xFF4F86B3, h * 0.36f, 0.8f, horizon);   // verschneite Berge
        drawRidge(c, p, w, h, 0xFF0A1822, h * 0.50f, 1.9f, horizon);   // dunkler Wald
        p.setShader(new LinearGradient(0, horizon, 0, h, 0xFF123247, 0xFF050D14, Shader.TileMode.CLAMP));
        c.drawRect(0, horizon, w, h, p);
        p.setShader(new android.graphics.RadialGradient(mx, h * 0.86f, 130, 0xAA9FD3FF, 0x009FD3FF, Shader.TileMode.CLAMP));
        c.drawOval(mx - 130, h * 0.78f, mx + 130, h * 0.94f, p);       // Spiegelung im See
        return b;
    }

    private static void drawRidge(Canvas c, Paint p, int w, int h, int color, float base, float freq, float horizon) {
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(0, horizon);
        for (int x = 0; x <= w; x += 16) {
            double y = base + 110 * Math.sin(x / 420.0 * freq + 0.4) - 60 * Math.sin(x / 130.0 * freq + 1.3);
            path.lineTo(x, (float) Math.min(y, horizon));
        }
        path.lineTo(w, horizon);
        path.close();
        p.setColor(color);
        c.drawPath(path, p);
    }

    private static final String[] NAMES = {"Netflix", "YouTube", "Prime Video", "Play Store", "Arte", "HBO Max", "Disney+"};
    private static final int[] COLORS = {0xFF8E1B1B, 0xFF6E1515, 0xFF1C4E80, 0xFF1E6B4A, 0xFF3A3A3A, 0xFF4B2C83, 0xFF14306B};

    private List<AppEntry> apps(boolean all) {
        List<AppEntry> list = new ArrayList<>();
        for (int i = 0; i < NAMES.length; i++) {
            list.add(new AppEntry("p" + i, NAMES[i], "p" + i, new FakeBanner(NAMES[i], COLORS[i]), null, null, true));
        }
        if (all) {
            String[] more = {"ARD", "ZDF", "Spotify", "Plex", "Joyn", "RTL+", "Kodi", "Fotos"};
            int[] mc = {0xFF1F3F7A, 0xFFB8551C, 0xFF1C6B3A, 0xFF6B5418, 0xFF5B2C83, 0xFF7A1E3A, 0xFF1E5C7A, 0xFF4A4A4A};
            for (int i = 0; i < more.length; i++) {
                list.add(new AppEntry("m" + i, more[i], "m" + i, new FakeBanner(more[i], mc[i]), null, null, false));
            }
        }
        return list;
    }

    private static final TileAdapter.Listener NOOP = new TileAdapter.Listener() {
        public void onOpen(AppEntry e) {}
        public void onMenu(AppEntry e) {}
    };

    private View buildHome() {
        View root = paparazzi.inflate(R.layout.activity_main);
        RecyclerView dock = root.findViewById(R.id.dock_list);
        dock.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(
                paparazzi.getContext(), androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false));
        TileAdapter adapter = new TileAdapter(NOOP, R.layout.item_dock, 1.15f, false);
        dock.setAdapter(adapter);
        adapter.submit(apps(false));
        ((TextView) root.findViewById(R.id.weather_temp)).setText("12°");
        ((TextView) root.findViewById(R.id.weather_text)).setText("Teilweise bewölkt · Hamburg");
        ((TextView) root.findViewById(R.id.dock_label)).setText("Netflix");

        layout(root);
        android.graphics.Bitmap wp = fakeWallpaper();
        ((android.widget.ImageView) root.findViewById(R.id.wallpaper)).setImageBitmap(wp);
        android.graphics.Bitmap blur = Aurora.blurred(wp, 1920, 1080);
        ((GlassLayout) root.findViewById(R.id.dock)).setBackdrop(blur, 1920f / blur.getWidth());
        layout(root);
        View card = dock.getChildAt(0).findViewById(R.id.card);
        card.setScaleX(1.15f); card.setScaleY(1.15f); card.setTranslationZ(36f);
        dock.getChildAt(0).findViewById(R.id.sheen).setAlpha(1f);
        return root;
    }

    private static void layout(View v) {
        v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, 1920, 1080);
    }

    @Test
    public void home() {
        paparazzi.snapshot(buildHome(), "home");
    }

    @Test public void allApps() { snapshotAllApps(IconStyle.ORIGINAL, "apps"); }
    @Test public void allAppsDunkel() { snapshotAllApps(IconStyle.DARK, "apps_dunkel"); }

    private void snapshotAllApps(String style, String name) {
        View root = paparazzi.inflate(R.layout.activity_apps);
        RecyclerView grid = root.findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(paparazzi.getContext(), 5));
        TileAdapter adapter = new TileAdapter(NOOP);
        adapter.setIconStyle(style);
        grid.setAdapter(adapter);
        adapter.submit(apps(true));
        layout(root);
        ((android.widget.ImageView) root.findViewById(R.id.wallpaper))
                .setImageBitmap(Aurora.blurred(fakeWallpaper(), 1920, 1080));
        layout(root);
        View tile = grid.getChildAt(7);
        View card = tile.findViewById(R.id.card);
        card.setScaleX(1.12f); card.setScaleY(1.12f); card.setTranslationZ(44f);
        tile.findViewById(R.id.label).setAlpha(1f);
        tile.findViewById(R.id.label).setTranslationY(8f);
        tile.findViewById(R.id.sheen).setAlpha(1f);
        paparazzi.snapshot(root, name);
    }

    @Test
    public void menu() {
        View homeView = buildHome();
        android.graphics.Bitmap backdrop = Aurora.snapshot(homeView);

        android.widget.FrameLayout stack = new android.widget.FrameLayout(paparazzi.getContext());
        stack.addView(homeView);
        View dim = new View(paparazzi.getContext());
        dim.setBackgroundColor(0x66000000);
        stack.addView(dim);

        android.view.LayoutInflater inf = android.view.LayoutInflater.from(paparazzi.getContext());
        GlassLayout sheet = (GlassLayout) inf.inflate(R.layout.sheet, stack, false);
        ((TextView) sheet.findViewById(R.id.sheet_title)).setText("Netflix");
        android.widget.LinearLayout buttons = sheet.findViewById(R.id.sheet_buttons);
        String[] labels = {"Vom Start entfernen", "Nach links", "Nach rechts", "Ausblenden", "App-Info", "Abbrechen"};
        for (String label : labels) {
            TextView b = (TextView) inf.inflate(R.layout.sheet_button, buttons, false);
            b.setText(label);
            buttons.addView(b);
        }
        android.widget.FrameLayout.LayoutParams lp = (android.widget.FrameLayout.LayoutParams) sheet.getLayoutParams();
        lp.gravity = android.view.Gravity.CENTER;
        stack.addView(sheet, lp);
        layout(stack);
        TextView first = (TextView) buttons.getChildAt(0);
        android.graphics.drawable.GradientDrawable focused = new android.graphics.drawable.GradientDrawable();
        focused.setColor(0xFFFFFFFF);
        focused.setCornerRadius(12 * paparazzi.getContext().getResources().getDisplayMetrics().density);
        first.setBackground(focused);
        first.setTextColor(0xFF000000);
        sheet.setBackdrop(backdrop, Aurora.BLUR_DOWNSCALE);
        paparazzi.snapshot(stack, "menu");
    }

    @Test public void abend() { snapshotAbend(IconStyle.ORIGINAL, "abend"); }
    @Test public void abendDunkel() { snapshotAbend(IconStyle.DARK, "abend_dunkel"); }
    @Test public void abendOrange() { snapshotAbend(IconStyle.ORANGE, "abend_orange"); }
    @Test public void abendGlas() { snapshotAbend(IconStyle.GLASS, "abend_glas"); }

    private void snapshotAbend(String style, String name) {
        View root = paparazzi.inflate(R.layout.activity_main_abend);
        RecyclerView dock = root.findViewById(R.id.dock_list);
        dock.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(
                paparazzi.getContext(), androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false));
        TileAdapter adapter = new TileAdapter(NOOP, R.layout.item_tile_abend, 1.06f, false, true);
        adapter.setIconStyle(style);
        dock.setAdapter(adapter);
        List<AppEntry> list = new ArrayList<>();
        int[] logo = {0xFFE50914, 0xFFFF0000, 0xFF00A8E1, 0xFF01875F, 0xFFFF5A1F, 0xFF5A2DA8, 0xFF113CCF};
        for (int i = 0; i < NAMES.length; i++) {
            boolean white = i == 0 || i == 1 || i == 3; // wie die echten Banner von Netflix, YouTube, Play Store
            list.add(new AppEntry("p" + i, NAMES[i], "p" + i,
                    white ? new FakeBanner(NAMES[i], logo[i], true) : new FakeBanner(NAMES[i], COLORS[i]), null,
                    new android.content.Intent(), true));
        }
        list.add(new AppEntry("klar:all", "Alle Apps", "", null,
                paparazzi.getContext().getDrawable(R.drawable.ic_apps_color), null, false));
        adapter.submit(list);

        TextView greeting = root.findViewById(R.id.greeting);
        greeting.setTypeface(Fonts.rounded(paparazzi.getContext()));
        greeting.setText(MainActivity.greetingFor(19));
        ((PixelText) root.findViewById(R.id.pixel_clock)).setText("19:27");
        ((PixelText) root.findViewById(R.id.pixel_date)).setText("Mittwoch, 4. November");
        ((PixelText) root.findViewById(R.id.pixel_weather)).setText("7° Bedeckt");
        View cont = root.findViewById(R.id.continue_button);
        cont.setVisibility(View.VISIBLE);
        float d = paparazzi.getContext().getResources().getDisplayMetrics().density;
        cont.setBackground(new AbendTile(d, -1));
        TextView cl = root.findViewById(R.id.continue_label);
        cl.setTypeface(Fonts.rounded(paparazzi.getContext()));
        cl.setText("Netflix");

        layout(root);
        root.findViewById(R.id.wallpaper).setBackground(AbendBackground.create(paparazzi.getContext()));
        layout(root);
        // Fokus auf der dritten Kachel nachstellen
        View card = dock.getChildAt(2).findViewById(R.id.card);
        AbendTile.focus(card, true); // wie auf dem TV: Software-Ebene für das Leuchten
        card.setScaleX(1.06f); card.setScaleY(1.06f);
        ((TextView) card.findViewById(R.id.number)).setTextColor(AbendTile.ORANGE);
        ((Mascot) root.findViewById(R.id.mascot)).preview(Mascot.Emote.HEART, false);
        paparazzi.snapshot(root, name);
    }

    @Test
    public void mascot() {
        android.widget.FrameLayout f = new android.widget.FrameLayout(paparazzi.getContext());
        f.setBackground(AbendBackground.create(paparazzi.getContext()));
        Mascot m = new Mascot(paparazzi.getContext());
        f.addView(m, new android.widget.FrameLayout.LayoutParams(-2, -2, android.view.Gravity.CENTER));
        m.preview(Mascot.Emote.HEART, false);
        paparazzi.snapshot(f, "mascot");
    }

    /** Zwei Minuten Spiel: Pixel muss Hindernisse fast immer überspringen und Sterne sammeln. */
    @Test
    public void jumpAndRunPlaysWell() {
        for (long seed = 1; seed <= 3; seed++) {
            Mascot m = new Mascot(paparazzi.getContext());
            int stars = m.simulate(120, seed);
            System.out.println("Seed " + seed + ": Sterne=" + stars + " Hindernisse=" + m.obstaclesSpawned + " Stolperer=" + m.stumbles);
            org.junit.Assert.assertTrue("sammelt Sterne", stars >= 5);
            org.junit.Assert.assertTrue("genug Hindernisse", m.obstaclesSpawned >= 30);
            // ~5 % absichtlich verpasst; mehr als 15 % hieße, die Sprünge passen nicht
            org.junit.Assert.assertTrue("springt zuverlässig", m.stumbles <= m.obstaclesSpawned * 0.15);
        }
    }
}
