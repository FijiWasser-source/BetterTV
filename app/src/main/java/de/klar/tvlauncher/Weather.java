package de.klar.tvlauncher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Wetter über Open-Meteo (open-meteo.com): kostenlos, ohne API-Schlüssel, ohne Konto.
 * Es wird nur der Ort (Koordinaten) übertragen.
 */
final class Weather {
    private Weather() {}

    static final class Place {
        final String name;
        final double lat;
        final double lon;
        Place(String name, double lat, double lon) { this.name = name; this.lat = lat; this.lon = lon; }
    }

    static final class Current {
        final double temperature;
        final int code;
        Current(double temperature, int code) { this.temperature = temperature; this.code = code; }
    }

    /** Ort per Name suchen. Liefert null, wenn nichts gefunden wurde. */
    static Place geocode(String query) throws IOException {
        String url = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=de&format=json&name="
                + URLEncoder.encode(query.trim(), "UTF-8");
        try {
            JSONObject json = new JSONObject(get(url));
            JSONArray results = json.optJSONArray("results");
            if (results == null || results.length() == 0) return null;
            JSONObject r = results.getJSONObject(0);
            return new Place(r.getString("name"), r.getDouble("latitude"), r.getDouble("longitude"));
        } catch (org.json.JSONException e) {
            throw new IOException("Ungültige Antwort", e);
        }
    }

    static Current current(double lat, double lon) throws IOException {
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&current=temperature_2m,weather_code&timezone=auto", lat, lon);
        try {
            JSONObject cur = new JSONObject(get(url)).getJSONObject("current");
            return new Current(cur.getDouble("temperature_2m"), cur.getInt("weather_code"));
        } catch (org.json.JSONException e) {
            throw new IOException("Ungültige Antwort", e);
        }
    }

    static String formatTemp(double celsius) {
        long rounded = Math.round(celsius);
        return rounded + "°";
    }

    /** WMO-Wettercode → kurze deutsche Beschreibung. */
    static String describe(int code) {
        switch (code) {
            case 0: return "Klar";
            case 1: return "Überwiegend klar";
            case 2: return "Teilweise bewölkt";
            case 3: return "Bedeckt";
            case 45: case 48: return "Nebel";
            case 51: case 53: case 55: return "Nieselregen";
            case 56: case 57: return "Gefrierender Nieselregen";
            case 61: case 63: return "Regen";
            case 65: return "Starker Regen";
            case 66: case 67: return "Gefrierender Regen";
            case 71: case 73: case 75: case 77: return "Schnee";
            case 80: case 81: case 82: return "Regenschauer";
            case 85: case 86: return "Schneeschauer";
            case 95: return "Gewitter";
            case 96: case 99: return "Gewitter mit Hagel";
            default: return "";
        }
    }

    private static String get(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        try {
            int status = conn.getResponseCode();
            if (status != 200) throw new IOException("HTTP " + status);
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            conn.disconnect();
        }
    }
}
