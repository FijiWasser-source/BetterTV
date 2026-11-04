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

    private boolean abend;
    private TextView greeting;
    private PixelText pixelClock, pixelDate, pixelWeather;
    private View continueButton;
    private Mascot mascot;
    private SoundFx sound;
    private HelperLayer helper;
    private TextView continueLabel;
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            updateClock();
            main.postDelayed(this, 1000);
        }
    };

    private static final String KEY_ALL = "klar:all";
    private static final String KEY_SETTINGS = "klar:settings";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        abend = "abend".equals(prefs.design());
        setContentView(abend ? R.layout.activity_main_abend : R.layout.activity_main);
        dockList = findViewById(R.id.dock_list);
        dockList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        dockList.setItemAnimator(null);

        if (abend) setupAbend(); else setupGlass();

        View root = findViewById(R.id.root);
        root.post(this::applyWallpaper);

        if (prefs.firstRun()) Toast.makeText(this, R.string.hint, Toast.LENGTH_LONG).show();
        de.klar.tvlauncher.remote.RemoteService.start(this);
    }

    private void setupGlass() {
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
        adapter = new TileAdapter(this, R.layout.item_dock, 1.15f, false);
        adapter.setIconStyle(prefs.iconStyle());
        dockList.setAdapter(adapter);
    }

    private void setupAbend() {
        greeting = findViewById(R.id.greeting);
        greeting.setTypeface(Fonts.rounded(this));
        pixelClock = findViewById(R.id.pixel_clock);
        pixelDate = findViewById(R.id.pixel_date);
        pixelWeather = findViewById(R.id.pixel_weather);
        mascot = findViewById(R.id.mascot);
        sound = new SoundFx(this);
        sound.setEnabled(prefs.soundOn());
        mascot.setEvents(new Mascot.Events() {
            @Override public void onJump() { sound.jump(); }
            @Override public void onStar() { sound.star(); }
            @Override public void onBump() { sound.bump(); }
            @Override public void onLand() { sound.land(); }
            @Override public void onLevelUp(int level) { sound.levelUp(); }
            @Override public void onTalk(String text) { if (!text.startsWith("IIII")) sound.talk(text); }
            @Override public void onScream() { sound.scream(); }
            @Override public void onStartle() { sound.startle(); }
            @Override public void onCrumble(float x, float y, float size, int color) { helper.addPiece(0); }
            @Override public void onLeftBehind(int type) { helper.addPiece(type + 1); }
        });
        continueButton = findViewById(R.id.continue_button);
        continueLabel = findViewById(R.id.continue_label);
        continueLabel.setTypeface(Fonts.rounded(this));
        applyPillStyle();
        continueButton.setOnFocusChangeListener((v, f) -> AbendTile.focus(v, f));
        continueButton.setOnClickListener(v -> {
            for (AppEntry e : AppLoader.load(this, prefs)) {
                if (e.packageName.equals(prefs.lastLaunched())) { AppMenu.open(this, e); return; }
            }
        });
        adapter = new TileAdapter(this, R.layout.item_tile_abend, 1.06f, false, true);
        adapter.setIconStyle(prefs.iconStyle());
        dockList.setAdapter(adapter);
        helper = findViewById(R.id.helper);
        helper.attach(mascot, dockList, new HelperLayer.Events() {
            @Override public void onSuck() { sound.suck(); }
            @Override public void onCrunch() { sound.crunch(); }
        });
    }

    /** "Weiter mit"-Knopf im Stil der Kacheln (bei Glas-Symbolen nur Umrandung). */
    private void applyPillStyle() {
        float d = getResources().getDisplayMetrics().density;
        boolean glass = IconStyle.freeStanding(prefs.iconStyle()); // ohne Kachel -> Knopf nur mit Umrandung
        continueButton.setBackground(new AbendTile(d, -1, glass));
        continueLabel.setTextColor(glass ? 0xFFFFFFFF : AbendTile.INK);
        ((TextView) findViewById(R.id.continue_caption)).setTextColor(glass ? 0xFFA9ADB5 : AbendTile.INK_SOFT);
    }

    /** Begrüßung je nach Tageszeit, zweite Zeile mit orangem Schlusswort. */
    static CharSequence greetingFor(int hour) {
        String first = hour >= 5 && hour < 11 ? "Guten Morgen." : hour < 17 && hour >= 11 ? "Guten Tag."
                : hour < 22 && hour >= 17 ? "Guten Abend." : "Gute Nacht.";
        String second = hour >= 22 || hour < 5 ? "Noch eine Folge?" : "Was läuft heute?";
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(first + "\n" + second);
        int lastWord = sb.toString().lastIndexOf(' ') + 1;
        sb.setSpan(new android.text.style.ForegroundColorSpan(AbendTile.ORANGE), lastWord, sb.length(), 0);
        sb.setSpan(new android.text.style.ForegroundColorSpan(0xFF8FB4FF), 0, first.length(), 0); // wie das Datum
        return sb;
    }

    private void updateClock() {
        if (!abend) return;
        java.util.Calendar now = java.util.Calendar.getInstance();
        pixelClock.setText(String.format(java.util.Locale.GERMAN, "%02d:%02d",
                now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE)));
        pixelClock.tick();
        pixelDate.setText(new java.text.SimpleDateFormat("EEEE, d. MMMM", java.util.Locale.GERMAN).format(now.getTime()));
        greeting.setText(greetingFor(now.get(java.util.Calendar.HOUR_OF_DAY)));
        if (sound != null && now.get(java.util.Calendar.SECOND) == 0) {
            sound.startMusic(mascot.isSleeping());
            if (mascot.isSleeping()) sound.stopEngine(); else sound.startEngine();
        }
    }

    private void applyWallpaper() {
        View root = findViewById(R.id.root);
        if (root.getWidth() == 0) return;
        ImageView wall = findViewById(R.id.wallpaper);
        if (abend) {
            wall.setImageDrawable(null);
            wall.setBackground(AbendBackground.create(this));
            return;
        }
        Wallpaper.Images img = Wallpaper.load(this, root.getWidth(), root.getHeight());
        wall.setImageBitmap(img.full);
        ((GlassLayout) findViewById(R.id.dock)).setBackdrop(img.blurred,
                (float) root.getWidth() / img.blurred.getWidth());
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadDock();
        if (!abend) findViewById(R.id.root).post(this::applyWallpaper); // falls ein neues Bild abgelegt wurde
        main.removeCallbacks(weatherTick);
        main.post(weatherTick);
        main.removeCallbacks(clockTick);
        if (abend) main.post(clockTick);
        if (sound != null) {
            sound.startMusic(mascot.isSleeping());
            if (mascot.isSleeping()) sound.stopEngine(); else sound.startEngine();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        main.removeCallbacks(weatherTick);
        main.removeCallbacks(clockTick);
        if (sound != null) { sound.pauseMusic(); sound.stopEngine(); } // Netflix & Co. bekommen Ruhe
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
        if (sound != null) sound.release();
    }

    /** Zurück bleibt auf der Startseite; Zifferntasten 1–9 starten die Apps im Dock. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) { focusKey = null; focusDock(0); }
            return true;
        }
        // Farbtasten (sonst kaum gebraucht) und 0: selbst spielen / springen
        boolean colorKey = code == KeyEvent.KEYCODE_PROG_RED || code == KeyEvent.KEYCODE_PROG_GREEN
                || code == KeyEvent.KEYCODE_PROG_YELLOW || code == KeyEvent.KEYCODE_PROG_BLUE || code == KeyEvent.KEYCODE_0;
        if (colorKey && mascot != null) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) mascot.playerJump();
            return true;
        }
        if (code >= KeyEvent.KEYCODE_1 && code <= KeyEvent.KEYCODE_9) {
            if (event.getAction() == KeyEvent.ACTION_UP) launchNumber(code - KeyEvent.KEYCODE_1);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void launchNumber(int index) {
        List<AppEntry> dock = AppLoader.dock(AppLoader.load(this, prefs));
        if (index < dock.size()) {
            focusKey = dock.get(index).key;
            AppMenu.open(this, dock.get(index));
        }
    }

    // --- Dock ---------------------------------------------------------------

    private void reloadDock() {
        View focused = getCurrentFocus();
        if (focused != null && focused.getTag() instanceof String) focusKey = (String) focused.getTag();
        List<AppEntry> all = AppLoader.load(this, prefs);
        List<AppEntry> dock = AppLoader.dock(all);
        if (abend) {
            // "Alle Apps" und "Einstellungen" als letzte Kacheln
            dock.add(new AppEntry(KEY_ALL, getString(R.string.all_apps), "", null,
                    getResources().getDrawable(R.drawable.ic_apps_color), null, false));
            dock.add(new AppEntry(KEY_SETTINGS, getString(R.string.settings), "", null,
                    getResources().getDrawable(R.drawable.ic_settings_large), null, false));
            AppEntry last = null;
            for (AppEntry e : all) if (e.packageName.equals(prefs.lastLaunched())) last = e;
            continueButton.setVisibility(last == null ? View.GONE : View.VISIBLE);
            if (last != null) continueLabel.setText(last.label);
            adapter.submit(dock);
        } else {
            adapter.submit(dock);
            dockEmpty.setVisibility(dock.isEmpty() ? View.VISIBLE : View.GONE);
            dockList.setVisibility(dock.isEmpty() ? View.GONE : View.VISIBLE);
            if (dock.isEmpty()) {
                findViewById(R.id.all_apps_button).requestFocus();
                return;
            }
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
        if (dockLabel != null) dockLabel.setText(entry.label);
        if (abend) {
            dockList.smoothScrollToPosition(Math.max(0, adapter.indexOf(entry.key)));
            // Maskottchen schaut zur gewählten Kachel
            View f = getCurrentFocus();
            if (f != null && mascot != null) {
                int[] a = new int[2], b = new int[2];
                f.getLocationOnScreen(a);
                mascot.getLocationOnScreen(b);
                float dx = (a[0] + f.getWidth() / 2f) - (b[0] + mascot.getWidth() / 2f);
                mascot.lookAt(Math.abs(dx) < f.getWidth() / 2f ? 0 : Math.signum(dx));
            }
        }
    }

    @Override
    public void onOpen(AppEntry entry) {
        focusKey = entry.key;
        if (KEY_ALL.equals(entry.key)) { startActivity(new Intent(this, AppsActivity.class)); return; }
        if (KEY_SETTINGS.equals(entry.key)) { showSettings(); return; }
        if (mascot != null) mascot.cheer();
        AppMenu.open(this, entry);
    }

    @Override
    public void onMenu(AppEntry entry) {
        focusKey = entry.key;
        if (entry.launchIntent == null) { onOpen(entry); return; }
        AppMenu.show(this, prefs, entry, this::reloadDock);
    }

    // --- Einstellungen ------------------------------------------------------

    private void showSettings() {
        String city = prefs.city().isEmpty() ? "nicht gesetzt" : prefs.city();
        new Sheet(this, getString(R.string.settings))
                .button("Design: " + (abend ? "Abend" : "Glas (Hintergrundbild)"), this::chooseDesign)
                .button("Musik & Sounds: " + (prefs.soundOn() ? "an" : "aus") + " – wechseln", () -> {
                    prefs.setSoundOn(!prefs.soundOn());
                    if (sound != null) {
                        sound.setEnabled(prefs.soundOn());
                        sound.startMusic(mascot != null && mascot.isSleeping());
                        if (prefs.soundOn() && mascot != null && !mascot.isSleeping()) sound.startEngine();
                    }
                })
                .button("App-Symbole: " + IconStyle.label(prefs.iconStyle()), this::chooseIconStyle)
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

    /** Alle Designs auf einen Blick, das aktive ist markiert. */
    private void chooseDesign() {
        String current = prefs.design();
        Sheet sheet = new Sheet(this, "Design wählen");
        String[][] options = {{"abend", "Abend (Begrüßung, Pixel, Kacheln)"}, {"glas", "Glas (Hintergrundbild, Glas-Dock)"}};
        for (String[] o : options) {
            Runnable pick = () -> { if (!o[0].equals(prefs.design())) { prefs.setDesign(o[0]); recreate(); } };
            if (o[0].equals(current)) sheet.primary("✓  " + o[1], pick); else sheet.button(o[1], pick);
        }
        sheet.button("Abbrechen", null).show();
    }

    /** Alle Symbol-Stile auf einen Blick, der aktive ist markiert. */
    private void chooseIconStyle() {
        String current = prefs.iconStyle();
        Sheet sheet = new Sheet(this, "App-Symbole wählen");
        for (String style : IconStyle.ALL) {
            Runnable pick = () -> {
                prefs.setIconStyle(style);
                adapter.setIconStyle(style);
                if (abend) applyPillStyle();
            };
            String label = IconStyle.label(style);
            if (style.equals(current)) sheet.primary("✓  " + label, pick); else sheet.button(label, pick);
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
        showWeather("", getString(R.string.weather_loading));
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

    private boolean weatherShown;

    private void showWeather(String temp, String text) {
        weatherShown = !temp.isEmpty();
        if (abend) {
            // Pixel-Schrift: ohne Ort/Stadt, kurz und knapp
            pixelWeather.setText(temp.isEmpty() ? "" : temp + " " + text.split(" · ")[0]);
        } else {
            weatherTemp.setText(temp);
            weatherText.setText(text);
        }
    }

    // --- Wetter -------------------------------------------------------------

    private void refreshWeather() {
        if (!prefs.hasLocation()) {
            showWeather("", getString(R.string.weather_off));
            return;
        }
        double lat = prefs.lat(), lon = prefs.lon();
        String city = prefs.city();
        io.execute(() -> {
            try {
                Weather.Current c = Weather.current(lat, lon);
                main.post(() -> {
                    String desc = Weather.describe(c.code);
                    showWeather(Weather.formatTemp(c.temperature), desc.isEmpty() ? city : desc + " · " + city);
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (!weatherShown) showWeather("", getString(R.string.weather_error));
                });
            }
        });
    }

    @SuppressWarnings("deprecation") // Context#getColor gibt es erst ab Android 6
    private int color(int id) {
        return getResources().getColor(id);
    }
}
