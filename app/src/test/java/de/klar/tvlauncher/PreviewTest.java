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
        private final String name; private final int color;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        FakeBanner(String name, int color) { this.name = name; this.color = color; }
        @Override public void draw(Canvas c) {
            float w = getBounds().width(), h = getBounds().height();
            paint.setShader(new LinearGradient(0, 0, w, h, color, Tint.darken(color, 0.4f), Shader.TileMode.CLAMP));
            c.drawRect(getBounds(), paint);
            paint.setShader(null);
            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            paint.setTextSize(h * 0.2f);
            c.drawText(name, w / 2, h / 2 + h * 0.07f, paint);
        }
        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter f) {}
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    private View buildHome() {
        View root = paparazzi.inflate(R.layout.activity_main);
        String[] names = {"Netflix", "YouTube", "ARD", "ZDF", "Spotify", "Disney+", "Prime Video",
                "Arte", "Plex", "Joyn", "RTL+", "Kodi", "Fotos", "Musik", "Sky"};
        int[] colors = {0xFF8E1B1B, 0xFF5E1717, 0xFF1F3F7A, 0xFFB8551C, 0xFF1C6B3A, 0xFF14306B,
                0xFF1C4E80, 0xFF3A3A3A, 0xFF6B5418, 0xFF5B2C83, 0xFF7A1E3A, 0xFF1E5C7A, 0xFF4A4A4A,
                0xFF7A2E5C, 0xFF20304F};
        List<AppEntry> list = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            list.add(new AppEntry("p" + i, names[i], "p" + i, new FakeBanner(names[i], colors[i]), null, null, i < 3));
        }
        RecyclerView grid = root.findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(paparazzi.getContext(), 5));
        TileAdapter adapter = new TileAdapter(new TileAdapter.Listener() {
            public void onOpen(AppEntry e) {}
            public void onMenu(AppEntry e) {}
        });
        grid.setAdapter(adapter);
        adapter.submit(list);
        ((TextView) root.findViewById(R.id.weather_temp)).setText("12°");
        ((TextView) root.findViewById(R.id.weather_text)).setText("Teilweise bewölkt · Hamburg");

        layout(root);
        Chrome.install(root, (GlassLayout) root.findViewById(R.id.glass_bar));
        // Fokus auf "Netflix" simulieren (auf dem TV übernimmt das die Fernbedienung)
        View tile = grid.getChildAt(0);
        View card = tile.findViewById(R.id.card);
        card.setScaleX(1.12f); card.setScaleY(1.12f); card.setTranslationZ(44f);
        tile.findViewById(R.id.label).setAlpha(1f);
        tile.findViewById(R.id.label).setTranslationY(8f);
        tile.findViewById(R.id.sheen).setAlpha(1f);
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
        String[] labels = {"Lösen", "Nach vorne", "Nach hinten", "Ausblenden", "Abbrechen"};
        for (int i = 0; i < labels.length; i++) {
            TextView b = (TextView) inf.inflate(R.layout.sheet_button, buttons, false);
            b.setText(labels[i]);
            buttons.addView(b);
        }
        android.widget.FrameLayout.LayoutParams lp = (android.widget.FrameLayout.LayoutParams) sheet.getLayoutParams();
        lp.gravity = android.view.Gravity.CENTER;
        stack.addView(sheet, lp);
        layout(stack);
        // Fokuszustand der ersten Schaltfläche (weiß) nachstellen
        TextView first = (TextView) buttons.getChildAt(0);
        android.graphics.drawable.GradientDrawable focused = new android.graphics.drawable.GradientDrawable();
        focused.setColor(0xFFFFFFFF);
        focused.setCornerRadius(12 * paparazzi.getContext().getResources().getDisplayMetrics().density);
        first.setBackground(focused);
        first.setTextColor(0xFF000000);
        sheet.setBackdrop(backdrop, Aurora.BLUR_DOWNSCALE);
        paparazzi.snapshot(stack, "menu");
    }
}
