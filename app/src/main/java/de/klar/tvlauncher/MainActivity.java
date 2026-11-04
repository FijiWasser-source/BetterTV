package de.klar.tvlauncher;

import android.app.Activity;
import android.animation.ObjectAnimator;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements TileAdapter.Listener {

    private static final int COLUMNS = 5;
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
    private RecyclerView grid;
    private TextView weatherTemp;
    private TextView weatherText;
    private String focusKey;
    private View ambient;
    private int ambientColor;
    private ObjectAnimator ambientAnim;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = new Prefs(this);
        weatherTemp = findViewById(R.id.weather_temp);
        weatherText = findViewById(R.id.weather_text);
        ambient = findViewById(R.id.ambient);
        ambientColor = color(R.color.bg_raised);

        grid = findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        grid.setItemAnimator(null);
        adapter = new TileAdapter(this);
        grid.setAdapter(adapter);

        if (prefs.firstRun()) Toast.makeText(this, R.string.hint, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadApps();
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
            if (event.getAction() == KeyEvent.ACTION_UP) focusPosition(0);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    // --- Apps ---------------------------------------------------------------

    private void reloadApps() {
        View focused = getCurrentFocus();
        if (focused != null && focused.getTag() instanceof String) focusKey = (String) focused.getTag();
        adapter.submit(AppLoader.load(this, prefs));
        int pos = focusKey == null ? 0 : Math.max(0, adapter.indexOf(focusKey));
        focusPosition(pos);
    }

    private void focusPosition(int pos) {
        if (adapter.getItemCount() == 0) return;
        grid.scrollToPosition(pos);
        grid.post(() -> {
            RecyclerView.ViewHolder h = grid.findViewHolderForAdapterPosition(pos);
            if (h != null) h.itemView.findViewById(R.id.card).requestFocus();
        });
    }

    @Override
    public void onOpen(AppEntry entry) {
        if (entry.isSettings()) { showSettings(); return; }
        focusKey = entry.key;
        try {
            startActivity(entry.launchIntent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "App lässt sich nicht starten", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onMenu(AppEntry entry) {
        if (entry.isSettings()) { showSettings(); return; }
        focusKey = entry.key;
        Sheet sheet = new Sheet(this, entry.label);
        if (entry.pinned) {
            sheet.button("Lösen", () -> setPinned(entry.key, false))
                 .button("Nach vorne", () -> movePin(entry.key, -1))
                 .button("Nach hinten", () -> movePin(entry.key, 1));
        } else {
            sheet.button("Oben anheften", () -> setPinned(entry.key, true));
        }
        sheet.button("Ausblenden", () -> hide(entry.key))
             .button("App-Info", () -> openAppInfo(entry.packageName))
             .button("Abbrechen", null)
             .show();
    }

    @Override
    public void onFocus(AppEntry entry, int color) {
        int target = Tint.darken(color, 0.45f);
        if (target == ambientColor) return;
        if (ambientAnim != null) ambientAnim.cancel();
        ambientAnim = ObjectAnimator.ofArgb(ambient, "backgroundColor", ambientColor, target);
        ambientAnim.setDuration(600);
        ambientAnim.start();
        ambientColor = target;
    }

    private void setPinned(String key, boolean pin) {
        List<String> pinned = prefs.pinned();
        pinned.remove(key);
        if (pin) pinned.add(key);
        prefs.setPinned(pinned);
        reloadApps();
    }

    private void movePin(String key, int delta) {
        prefs.setPinned(AppOrder.move(prefs.pinned(), key, delta));
        reloadApps();
    }

    private void hide(String key) {
        Set<String> hidden = prefs.hidden();
        hidden.add(key);
        prefs.setHidden(hidden);
        List<String> pinned = prefs.pinned();
        if (pinned.remove(key)) prefs.setPinned(pinned);
        focusKey = null;
        reloadApps();
    }

    private void openAppInfo(String pkg) {
        tryStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
    }

    // --- Einstellungen ------------------------------------------------------

    private void showSettings() {
        String city = prefs.city().isEmpty() ? "nicht gesetzt" : prefs.city();
        new Sheet(this, getString(R.string.settings))
                .button("Wetter-Ort: " + city, this::askCity)
                .button("Ausgeblendete Apps (" + prefs.hidden().size() + ")", this::showHidden)
                .button("Android-Einstellungen", () -> tryStart(new Intent(Settings.ACTION_SETTINGS)))
                .button("Standard-Startbildschirm", () -> {
                    if (!tryStart(new Intent(Settings.ACTION_HOME_SETTINGS))) {
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
                reloadApps();
            });
        }
        sheet.button("Abbrechen", null).show();
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
        input.setHintTextColor(color(R.color.text_faint));
        input.setTextSize(18);
        input.setGravity(Gravity.CENTER);
        input.setBackgroundResource(R.drawable.sheet_input);
        int pad = Math.round(14 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        new Sheet(this, "Ort für das Wetter")
                .message("Nur dieser Ort wird an Open-Meteo gesendet.")
                .extra(input)
                .button("Speichern", () -> saveCity(input.getText().toString()))
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

    private boolean tryStart(Intent intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation") // Context#getColor gibt es erst ab Android 6
    private int color(int id) {
        return getResources().getColor(id);
    }
}
