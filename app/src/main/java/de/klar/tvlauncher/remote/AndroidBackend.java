package de.klar.tvlauncher.remote;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.widget.Toast;

import de.klar.tvlauncher.AppEntry;
import de.klar.tvlauncher.KlarAccess;
import de.klar.tvlauncher.MainActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Verbindet die Fernbedienung mit dem Fernseher. */
final class AndroidBackend implements RemoteHttp.Backend {

    private final Context context;
    private final SharedPreferences sp;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SonyIrcc ircc;
    private final Map<String, byte[]> iconCache = new HashMap<>();

    AndroidBackend(Context context) {
        this.context = context.getApplicationContext();
        this.sp = this.context.getSharedPreferences("klar_remote", Context.MODE_PRIVATE);
        String lan = lanAddress();
        this.ircc = lan == null ? new SonyIrcc("127.0.0.1") : new SonyIrcc("127.0.0.1", lan);
    }

    /** Eigene IPv4-Adresse im Heimnetz (WLAN oder Kabel), oder null. */
    static String lanAddress() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override public String tvName() {
        return Build.MODEL == null ? "Fernseher" : Build.MODEL;
    }

    @Override public String appsJson() {
        try {
            List<AppEntry> all = KlarAccess.apps(context);
            JSONArray arr = new JSONArray();
            for (AppEntry e : all) {
                arr.put(new JSONObject().put("pkg", e.packageName()).put("label", e.label())
                        .put("dock", e.pinned()).put("banner", e.hasBanner()));
            }
            return new JSONObject().put("apps", arr).toString();
        } catch (Exception e) {
            return "{\"apps\":[]}";
        }
    }

    @Override public synchronized byte[] icon(String pkg, String style) {
        if (pkg == null) return null;
        String st = KlarAccess.validStyle(style) ? style : KlarAccess.iconStyle(context);
        String cacheKey = pkg + "|" + st;
        if (iconCache.containsKey(cacheKey)) return iconCache.get(cacheKey);
        byte[] png = null;
        for (AppEntry e : KlarAccess.apps(context)) {
            if (!pkg.equals(e.packageName())) continue;
            Drawable d = KlarAccess.styled(e.hasBanner() ? e.bannerDrawable() : e.iconDrawable(), st);
            if (d == null) break;
            int w = e.hasBanner() ? 320 : 192, h = e.hasBanner() ? 180 : 192;
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Drawable copy = d.getConstantState() != null ? d.getConstantState().newDrawable().mutate() : d;
            copy.setBounds(0, 0, w, h);
            copy.draw(new Canvas(bmp));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            bmp.recycle();
            png = out.toByteArray();
            break;
        }
        iconCache.put(cacheKey, png);
        return png;
    }

    @Override public byte[] wallpaper() {
        Bitmap b = KlarAccess.wallpaper(context);
        if (b == null) return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Für das Handy reicht eine kleinere Fassung
        Bitmap small = Bitmap.createScaledBitmap(b, Math.min(b.getWidth(), 1280),
                Math.round(Math.min(b.getWidth(), 1280) * (float) b.getHeight() / b.getWidth()), true);
        small.compress(Bitmap.CompressFormat.JPEG, 82, out);
        if (small != b) small.recycle();
        return out.toByteArray();
    }

    @Override public boolean launch(String pkg) {
        for (AppEntry e : KlarAccess.apps(context)) {
            if (pkg.equals(e.packageName())) {
                try { context.startActivity(e.launch()); KlarAccess.noteLaunched(context, pkg); return true; } catch (Exception ex) { return false; }
            }
        }
        return false;
    }

    @Override public String key(String name) {
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        // Ohne Sony-Steuercode möglich:
        switch (name) {
            case "home":
                context.startActivity(new Intent(context, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
                return "ok";
            case "playpause":
                media(audio, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                return "ok";
            case "volup":
                if (!hasPsk()) { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); return "ok"; }
                break;
            case "voldown":
                if (!hasPsk()) { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); return "ok"; }
                break;
            case "mute":
                if (!hasPsk() && Build.VERSION.SDK_INT >= 23) {
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
                    return "ok";
                }
                break;
            default:
                break;
        }
        String sony = SONY_NAMES.get(name);
        if (sony == null) return "error";
        if (!hasPsk()) return "needs_psk";
        int status = ircc.send(sony, sp.getString("psk", ""));
        if (status == 200) return "ok";
        if (status == 401 || status == 403) return "needs_psk";
        return "error";
    }

    private static final Map<String, String> SONY_NAMES = new HashMap<>();
    static {
        SONY_NAMES.put("up", "Up");
        SONY_NAMES.put("down", "Down");
        SONY_NAMES.put("left", "Left");
        SONY_NAMES.put("right", "Right");
        SONY_NAMES.put("ok", "Confirm");
        SONY_NAMES.put("back", "Return");
        SONY_NAMES.put("volup", "VolumeUp");
        SONY_NAMES.put("voldown", "VolumeDown");
        SONY_NAMES.put("mute", "Mute");
        SONY_NAMES.put("input", "Input");
        SONY_NAMES.put("power", "PowerOff");
        SONY_NAMES.put("menu", "ActionMenu");
    }

    private static void media(AudioManager audio, int code) {
        long now = SystemClock.uptimeMillis();
        audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0));
        audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0));
    }

    @Override public boolean hasPsk() { return !sp.getString("psk", "").isEmpty(); }

    @Override public void setPsk(String psk) { sp.edit().putString("psk", psk).apply(); }

    @Override public void showPin(String pin) {
        main.post(() -> {
            String text = "Handy-Fernbedienung koppeln\nPIN: " + pin;
            Toast.makeText(context, text, Toast.LENGTH_LONG).show();
            // Ein Toast ist kurz – nach 3,5 s noch einmal zeigen
            main.postDelayed(() -> Toast.makeText(context, text, Toast.LENGTH_LONG).show(), 3500);
        });
    }

    @Override public boolean isToken(String token) { return tokens().contains(token); }

    @Override public void addToken(String token) {
        Set<String> t = tokens();
        t.add(token);
        sp.edit().putStringSet("tokens", t).apply();
    }

    @Override public void removeToken(String token) {
        Set<String> t = tokens();
        t.remove(token);
        sp.edit().putStringSet("tokens", t).apply();
    }

    void clearTokens() { sp.edit().remove("tokens").apply(); }

    int pairedCount() { return tokens().size(); }

    private Set<String> tokens() { return new HashSet<>(sp.getStringSet("tokens", new HashSet<>())); }
}
