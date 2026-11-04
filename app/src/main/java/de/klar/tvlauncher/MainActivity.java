package de.klar.tvlauncher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Startansicht: Hintergrundbild, Uhr, Wetter und ein Glas-Dock mit den wichtigsten Apps,
 * dazu "Alle Apps" und "Einstellungen". Alles andere bleibt aus dem Blick.
 */
public class MainActivity extends Activity implements TileAdapter.Listener {

    private static final long WEATHER_REFRESH_MS = 30 * 60 * 1000L;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable weatherTick = new Runnable() {
        @Override public void run() {
            refreshWeather();
            main.postDelayed(this, WEATHER_REFRESH_MS);
        }
    };

    private Prefs prefs;
    private TileAdapter adapter;
    private RecyclerView dockList;
    private TextView dockLabel;
    private TextView dockEmpty;
    private TextView weatherTemp;
    private TextView weatherText;
    private String focusKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = new Prefs(this);
        weatherTemp = findViewById(R.id.weather_temp);
        weatherText = findViewById(R.id.weather_text);
        dockLabel = findViewById(R.id.dock_label);
        dockEmpty = findViewById(R.id.dock_empty);

        View allApps = findViewById(R.id.all_apps_button);
        View settings = findViewById(R.id.settings_button);
        allApps.setOnClickListener(v -> startActivity(new Intent(this, AppsActivity.class)));
        settings.setOnClickListener(v -> showSettings());
        allApps.setOnFocusChangeListener((v, f) -> { if (f) dockLabel.setText(R.string.all_apps); });
        settings.setOnFocusChangeListener((v, f) -> { if (f) dockLabel.setText(R.string.settings); });

        dockList = findViewById(R.id.dock_list);
        dockList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        dockList.setItemAnimator(null);
        adapter = new TileAdapter(this, R.layout.item_dock, 1.15f, false);
        dockList.setAdapter(adapter);

        View root = findViewById(R.id.root);
        root.post(this::applyWallpaper);

        if (prefs.firstRun()) Toast.makeText(this, R.string.hint, Toast.LENGTH_LONG).show();
        de.klar.tvlauncher.remote.RemoteService.start(this);
    }

    private void applyWallpaper() {
        View root = findViewById(R.id.root);
        if (root.getWidth() == 0) return;
        Wallpaper.Images img = Wallpaper.load(this, root.getWidth(), root.getHeight());
        ((ImageView) findViewById(R.id.wallpaper)).setImageBitmap(img.full);
        ((GlassLayout) findViewById(R.id.dock)).setBackdrop(img.blurred,
                (float) root.getWidth() / img.blurred.getWidth());
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadDock();
        findViewById(R.id.root).post(this::applyWallpaper); // falls ein neues Bild abgelegt wurde
        main.removeCallbacks(weatherTick);
        main.post(weatherTick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        main.removeCallbacks(weatherTick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    /** Als Startbildschirm darf "Zurück" die Oberfläche nicht schließen. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) { focusKey = null; focusDock(0); }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    // --- Dock ---------------------------------------------------------------

    private void reloadDock() {
        View focused = getCurrentFocus();
        if (focused != null && focused.getTag() instanceof String) focusKey = (String) focused.getTag();
        List<AppEntry> dock = AppLoader.dock(AppLoader.load(this, prefs));
        adapter.submit(dock);
        dockEmpty.setVisibility(dock.isEmpty() ? View.VISIBLE : View.GONE);
        dockList.setVisibility(dock.isEmpty() ? View.GONE : View.VISIBLE);
        if (dock.isEmpty()) {
            findViewById(R.id.all_apps_button).requestFocus();
            return;
        }
        focusDock(focusKey == null ? 0 : Math.max(0, adapter.indexOf(focusKey)));
    }

    private void focusDock(int pos) {
        if (adapter.getItemCount() == 0) return;
        dockList.scrollToPosition(pos);
        dockList.post(() -> {
            RecyclerView.ViewHolder h = dockList.findViewHolderForAdapterPosition(pos);
            if (h != null) h.itemView.findViewById(R.id.card).requestFocus();
        });
    }

    @Override
    public void onFocus(AppEntry entry) {
        dockLabel.setText(entry.label);
    }

    @Override
    public void onOpen(AppEntry entry) {
        focusKey = entry.key;
        AppMenu.open(this, entry);
    }

    @Override
    public void onMenu(AppEntry entry) {
        focusKey = entry.key;
        AppMenu.show(this, prefs, entry, this::reloadDock);
    }

    // --- Einstellungen ------------------------------------------------------

    private void showSettings() {
        String city = prefs.city().isEmpty() ? "nicht gesetzt" : prefs.city();
        new Sheet(this, getString(R.string.settings))
                .button("Wetter-Ort: " + city, this::askCity)
                .button("Handy-Fernbedienung", this::showRemoteInfo)
                .button("Hintergrundbild", this::showWallpaperInfo)
                .button("Ausgeblendete Apps (" + prefs.hidden().size() + ")", this::showHidden)
                .button("Android-Einstellungen", () -> AppMenu.tryStart(this, new Intent(Settings.ACTION_SETTINGS)))
                .button("Standard-Startbildschirm", () -> {
                    if (!AppMenu.tryStart(this, new Intent(Settings.ACTION_HOME_SETTINGS))) {
                        Toast.makeText(this, "Auf diesem Gerät per Einrichtungs-Skript (siehe Anleitung)",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .button("Fertig", null)
                .show();
    }

    private void showHidden() {
        Set<String> hidden = prefs.hidden();
        if (hidden.isEmpty()) {
            Toast.makeText(this, "Keine ausgeblendeten Apps", Toast.LENGTH_SHORT).show();
            return;
        }
        Map<String, String> all = AppLoader.allLabels(this);
        Sheet sheet = new Sheet(this, "Wieder anzeigen");
        for (String key : hidden) {
            String label = all.get(key);
            sheet.button(label != null ? label : key + " (deinstalliert)", () -> {
                Set<String> h = prefs.hidden();
                h.remove(key);
                prefs.setHidden(h);
                reloadDock();
            });
        }
        sheet.button("Abbrechen", null).show();
    }

    private void showRemoteInfo() {
        String url = de.klar.tvlauncher.remote.RemoteService.url();
        int paired = de.klar.tvlauncher.remote.RemoteService.pairedCount(this);
        Sheet sheet = new Sheet(this, "Handy-Fernbedienung")
                .message(url == null ? "Fernbedienung nicht aktiv – keine Netzwerkverbindung oder kein freier Port."
                        : "Am Handy im selben WLAN öffnen:\n\n" + url
                        + "\n\nDie PIN zum Koppeln erscheint dann hier auf dem Fernseher."
                        + (paired > 0 ? "\n\nGekoppelt: " + paired + (paired == 1 ? " Gerät" : " Geräte") : ""));
        if (paired > 0) {
            sheet.button("Alle Handys trennen", () -> {
                de.klar.tvlauncher.remote.RemoteService.unpairAll(this);
                Toast.makeText(this, "Getrennt", Toast.LENGTH_SHORT).show();
            });
        }
        sheet.button("Fertig", null).show();
    }

    private void showWallpaperInfo() {
        File f = Wallpaper.customFile(this);
        String path = f != null ? f.getAbsolutePath().replace("/storage/emulated/0", "/sdcard") : "?";
        new Sheet(this, "Hintergrundbild")
                .message("Eigenes Bild (JPG oder PNG, am besten 1920 × 1080) vom Computer aus ablegen:\n\n"
                        + "adb push bild.jpg " + path + "\n\nDanach einmal Home drücken.")
                .button("Fertig", null)
                .show();
    }

    private void askCity() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setSingleLine(true);
        input.setHint("z. B. Hamburg");
        input.setText(prefs.city());
        input.setSelectAllOnFocus(true);
        input.setTextColor(color(R.color.text));
        input.setHintTextColor(0x99FFFFFF);
        input.setTextSize(18);
        input.setGravity(Gravity.CENTER);
        input.setBackgroundResource(R.drawable.sheet_input);
        int pad = Math.round(14 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        new Sheet(this, "Ort für das Wetter")
                .message("Nur dieser Ort wird an Open-Meteo gesendet.")
                .extra(input)
                .primary("Speichern", () -> saveCity(input.getText().toString()))
                .button("Kein Wetter anzeigen", () -> { prefs.clearLocation(); refreshWeather(); })
                .button("Abbrechen", null)
                .show();
        input.requestFocus();
    }

    private void saveCity(String query) {
        if (query.trim().isEmpty()) return;
        weatherText.setText(R.string.weather_loading);
        io.execute(() -> {
            try {
                Weather.Place place = Weather.geocode(query);
                main.post(() -> {
                    if (place == null) {
                        Toast.makeText(this, "Ort nicht gefunden", Toast.LENGTH_SHORT).show();
                    } else {
                        prefs.setLocation(place.name, place.lat, place.lon);
                    }
                    refreshWeather();
                });
            } catch (Exception e) {
                main.post(() -> {
                    Toast.makeText(this, "Keine Verbindung", Toast.LENGTH_SHORT).show();
                    refreshWeather();
                });
            }
        });
    }

    // --- Wetter -------------------------------------------------------------

    private void refreshWeather() {
        if (!prefs.hasLocation()) {
            weatherTemp.setText("");
            weatherText.setText(R.string.weather_off);
            return;
        }
        double lat = prefs.lat(), lon = prefs.lon();
        String city = prefs.city();
        io.execute(() -> {
            try {
                Weather.Current c = Weather.current(lat, lon);
                main.post(() -> {
                    weatherTemp.setText(Weather.formatTemp(c.temperature));
                    String desc = Weather.describe(c.code);
                    weatherText.setText(desc.isEmpty() ? city : desc + " · " + city);
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (weatherTemp.getText().length() == 0) weatherText.setText(R.string.weather_error);
                });
            }
        });
    }

    @SuppressWarnings("deprecation") // Context#getColor gibt es erst ab Android 6
    private int color(int id) {
        return getResources().getColor(id);
    }
}
