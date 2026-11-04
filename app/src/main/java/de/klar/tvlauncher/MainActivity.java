package de.klar.tvlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = new Prefs(this);
        weatherTemp = findViewById(R.id.weather_temp);
        weatherText = findViewById(R.id.weather_text);

        grid = findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        grid.setItemAnimator(null);
        adapter = new TileAdapter(this);
        grid.setAdapter(adapter);
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
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        if (entry.pinned) {
            labels.add("Lösen"); actions.add(() -> setPinned(entry.key, false));
            labels.add("Nach vorne"); actions.add(() -> movePin(entry.key, -1));
            labels.add("Nach hinten"); actions.add(() -> movePin(entry.key, 1));
        } else {
            labels.add("An den Anfang heften"); actions.add(() -> setPinned(entry.key, true));
        }
        labels.add("Ausblenden"); actions.add(() -> hide(entry.key));
        labels.add("App-Info"); actions.add(() -> openAppInfo(entry.packageName));

        new AlertDialog.Builder(this, R.style.Theme_Klar_Dialog)
                .setTitle(entry.label)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
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
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
        tryStart(i);
    }

    // --- Einstellungen ------------------------------------------------------

    private void showSettings() {
        Set<String> hidden = prefs.hidden();
        String city = prefs.city().isEmpty() ? "nicht gesetzt" : prefs.city();
        String[] items = {
                "Wetter-Ort: " + city,
                "Ausgeblendete Apps (" + hidden.size() + ")",
                "Android-Einstellungen öffnen",
                "Standard-Startbildschirm wählen",
        };
        new AlertDialog.Builder(this, R.style.Theme_Klar_Dialog)
                .setTitle(R.string.settings)
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: askCity(); break;
                        case 1: showHidden(); break;
                        case 2: tryStart(new Intent(Settings.ACTION_SETTINGS)); break;
                        case 3:
                            if (!tryStart(new Intent(Settings.ACTION_HOME_SETTINGS))) {
                                Toast.makeText(this, "Auf diesem Gerät nur per ADB möglich – siehe Anleitung",
                                        Toast.LENGTH_LONG).show();
                            }
                            break;
                    }
                })
                .show();
    }

    private void showHidden() {
        Set<String> hidden = prefs.hidden();
        if (hidden.isEmpty()) {
            Toast.makeText(this, "Keine ausgeblendeten Apps", Toast.LENGTH_SHORT).show();
            return;
        }
        Map<String, String> all = AppLoader.allLabels(this);
        List<String> keys = new ArrayList<>(hidden);
        String[] names = new String[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            String label = all.get(keys.get(i));
            names[i] = label != null ? label : keys.get(i) + " (deinstalliert)";
        }
        new AlertDialog.Builder(this, R.style.Theme_Klar_Dialog)
                .setTitle("Wieder anzeigen")
                .setItems(names, (d, which) -> {
                    Set<String> h = prefs.hidden();
                    h.remove(keys.get(which));
                    prefs.setHidden(h);
                    reloadApps();
                })
                .show();
    }

    private void askCity() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setHint("z. B. Hamburg");
        input.setText(prefs.city());
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this, R.style.Theme_Klar_Dialog)
                .setTitle("Ort für das Wetter")
                .setView(input)
                .setPositiveButton("Speichern", (d, w) -> saveCity(input.getText().toString()))
                .setNeutralButton("Kein Wetter", (d, w) -> { prefs.clearLocation(); refreshWeather(); })
                .setNegativeButton("Abbrechen", null)
                .show();
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
                        refreshWeather();
                    } else {
                        prefs.setLocation(place.name, place.lat, place.lon);
                        refreshWeather();
                    }
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
}
