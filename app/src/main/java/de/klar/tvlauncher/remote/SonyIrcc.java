package de.klar.tvlauncher.remote;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Tasten über Sonys eingebaute Netzwerksteuerung (IRCC) senden – das kann jede
 * Taste der Fernbedienung, auch in anderen Apps. Voraussetzung am TV:
 * Netzwerk → Heimnetzwerk → IP-Steuerung → Authentifizierung "Normal und Pre-Shared Key".
 */
public final class SonyIrcc {

    /** Bekannte BRAVIA-Codes als Rückfall, falls der TV seine Liste nicht liefert. */
    static final Map<String, String> FALLBACK = new HashMap<>();
    static {
        FALLBACK.put("Up", "AAAAAQAAAAEAAAB0Aw==");
        FALLBACK.put("Down", "AAAAAQAAAAEAAAB1Aw==");
        FALLBACK.put("Left", "AAAAAQAAAAEAAAA0Aw==");
        FALLBACK.put("Right", "AAAAAQAAAAEAAAAzAw==");
        FALLBACK.put("Confirm", "AAAAAQAAAAEAAABlAw==");
        FALLBACK.put("Return", "AAAAAgAAAJcAAAAjAw==");
        FALLBACK.put("Home", "AAAAAQAAAAEAAABgAw==");
        FALLBACK.put("VolumeUp", "AAAAAQAAAAEAAAASAw==");
        FALLBACK.put("VolumeDown", "AAAAAQAAAAEAAAATAw==");
        FALLBACK.put("Mute", "AAAAAQAAAAEAAAAUAw==");
        FALLBACK.put("Input", "AAAAAQAAAAEAAAAlAw==");
        FALLBACK.put("Play", "AAAAAgAAAJcAAAAaAw==");
        FALLBACK.put("Pause", "AAAAAgAAAJcAAAAZAw==");
        FALLBACK.put("PowerOff", "AAAAAQAAAAEAAAAvAw==");
        FALLBACK.put("ActionMenu", "AAAAAgAAAMQAAABLAw==");
    }

    private final String[] hosts;
    private Map<String, String> codes;

    /** @param hosts Adressen des TVs, in dieser Reihenfolge versucht (z. B. 127.0.0.1, LAN-IP) */
    public SonyIrcc(String... hosts) {
        this.hosts = hosts;
    }

    /** @return HTTP-Status (200 = ok, 403 = Steuercode falsch), oder -1 wenn nicht erreichbar */
    public int send(String name, String psk) {
        String code = codes().get(name);
        if (code == null) code = FALLBACK.get(name);
        if (code == null) return 404;
        String body = "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                + "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>"
                + "<u:X_SendIRCC xmlns:u=\"urn:schemas-sony-com:service:IRCC:1\"><IRCCCode>" + code
                + "</IRCCCode></u:X_SendIRCC></s:Body></s:Envelope>";
        for (String host : hosts) {
            try {
                return post("http://" + host + "/sony/IRCC", body, "text/xml; charset=UTF-8", psk,
                        "\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\"").status;
            } catch (IOException ignored) {
                // nächste Adresse probieren
            }
        }
        return -1;
    }

    /** Codeliste des TVs (einmal abgefragt, sonst Rückfall). */
    synchronized Map<String, String> codes() {
        if (codes != null) return codes;
        Map<String, String> map = new HashMap<>();
        String req = "{\"method\":\"getRemoteControllerInfo\",\"version\":\"1.0\",\"params\":[],\"id\":10}";
        for (String host : hosts) {
            try {
                Result r = post("http://" + host + "/sony/system", req, "application/json", null, null);
                if (r.status != 200) continue;
                JSONArray list = new JSONObject(r.body).getJSONArray("result").getJSONArray(1);
                for (int i = 0; i < list.length(); i++) {
                    JSONObject o = list.getJSONObject(i);
                    map.put(o.getString("name"), o.getString("value"));
                }
                break;
            } catch (Exception ignored) {
            }
        }
        codes = map;
        return codes;
    }

    static final class Result {
        final int status; final String body;
        Result(int status, String body) { this.status = status; this.body = body; }
    }

    private static Result post(String url, String body, String type, String psk, String soapAction) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(1500);
        c.setReadTimeout(3000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", type);
        if (psk != null && !psk.isEmpty()) c.setRequestProperty("X-Auth-PSK", psk);
        if (soapAction != null) c.setRequestProperty("SOAPACTION", soapAction);
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(data.length);
        try (OutputStream out = c.getOutputStream()) { out.write(data); }
        int status = c.getResponseCode();
        InputStream in = status < 400 ? c.getInputStream() : c.getErrorStream();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        if (in != null) {
            try (InputStream i = in) {
                byte[] b = new byte[4096];
                int n;
                while ((n = i.read(b)) > 0) buf.write(b, 0, n);
            }
        }
        c.disconnect();
        return new Result(status, buf.toString(StandardCharsets.UTF_8.name()));
    }
}
