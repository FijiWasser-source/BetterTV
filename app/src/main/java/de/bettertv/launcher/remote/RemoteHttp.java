package de.bettertv.launcher.remote;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kleiner HTTP-Server für die Handy-Fernbedienung (nur im Heimnetz).
 *
 * Ohne Android-Abhängigkeiten, damit er auf dem Rechner getestet werden kann.
 * Alles unter /api/ außer der Kopplung verlangt ein Token, das man nur mit der
 * PIN bekommt, die auf dem Fernseher erscheint.
 */
public final class RemoteHttp {

    /** Was der Fernseher können muss. */
    public interface Backend {
        String tvName();
        String appsJson();               // {"apps":[{"pkg":..,"label":..,"dock":true}]}
        byte[] icon(String pkg, String style); // PNG oder null; style null = wie am Fernseher
        byte[] wallpaper();              // JPEG oder null
        boolean launch(String pkg);
        /** @return "ok", "needs_psk" (Sony-Steuercode fehlt/falsch) oder "error" */
        String key(String name);
        boolean hasPsk();
        void setPsk(String psk);
        boolean hasCalendar();
        /** Geheime iCal-Adresse (Google Kalender); leer = trennen. Bleibt nur auf dem Fernseher. */
        void setCalendarUrl(String url);
        void showPin(String pin);
        boolean isToken(String token);
        void addToken(String token);
        void removeToken(String token);
    }

    /** Liefert Dateien der Web-App (index.html, Icons …) oder null. */
    public interface Assets {
        byte[] read(String name);
    }

    static final int MAX_HEADER = 16 * 1024;
    static final int MAX_BODY = 64 * 1024;
    static final int MAX_PIN_TRIES = 5;

    private final Backend backend;
    private final Assets assets;
    private final SecureRandom random = new SecureRandom();
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private ServerSocket server;
    private Thread acceptor;

    private String pin;
    private int pinTries;

    public RemoteHttp(Backend backend, Assets assets) {
        this.backend = backend;
        this.assets = assets;
    }

    public synchronized void start(int port) throws IOException {
        if (server != null) return;
        // ausdrücklich auf allen Adressen (IPv4 + IPv6) lauschen, nicht nur intern
        ServerSocket s = new ServerSocket();
        try {
            s.setReuseAddress(true);
            s.bind(new InetSocketAddress(port), 16);
        } catch (IOException e) {
            s.close();
            throw e;
        }
        server = s;
        acceptor = new Thread(this::acceptLoop, "bettertv-remote");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    public synchronized int port() { return server == null ? -1 : server.getLocalPort(); }

    public synchronized void stop() {
        try { if (server != null) server.close(); } catch (IOException ignored) {}
        server = null;
        pool.shutdownNow();
    }

    private void acceptLoop() {
        ServerSocket s;
        synchronized (this) { s = server; }
        while (s != null && !s.isClosed()) {
            try {
                Socket c = s.accept();
                c.setSoTimeout(10_000);
                pool.execute(() -> handle(c));
            } catch (IOException e) {
                if (s.isClosed()) return;
            }
        }
    }

    // --- HTTP -----------------------------------------------------------------

    static final class Request {
        String method = "", path = "";
        final Map<String, String> query = new HashMap<>();
        final Map<String, String> headers = new HashMap<>();
        byte[] body = new byte[0];

        JSONObject json() {
            try { return new JSONObject(new String(body, StandardCharsets.UTF_8)); }
            catch (JSONException e) { return new JSONObject(); }
        }

        String token() {
            String t = headers.get("x-bettertv-token");
            return t != null ? t : query.get("t");
        }
    }

    static final class Response {
        final int status; final String type; final byte[] body; String cache = "no-store";
        Response(int status, String type, byte[] body) { this.status = status; this.type = type; this.body = body; }
    }

    private void handle(Socket c) {
        try (Socket socket = c) {
            Request req = parse(socket.getInputStream());
            Response res = req == null ? text(400, "Bad Request") : route(req);
            write(socket.getOutputStream(), res);
        } catch (SocketTimeoutException ignored) {
        } catch (IOException | RuntimeException ignored) {
        }
    }

    static Request parse(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int last4 = 0;
        while (last4 != 0x0D0A0D0A) { // Ende der Kopfzeilen: \r\n\r\n
            int b = in.read();
            if (b < 0) return null;
            head.write(b);
            if (head.size() > MAX_HEADER) return null;
            last4 = (last4 << 8) | b;
        }
        String[] lines = head.toString(StandardCharsets.ISO_8859_1.name()).split("\r\n");
        String[] first = lines[0].split(" ");
        if (first.length < 2) return null;
        Request r = new Request();
        r.method = first[0].toUpperCase(Locale.ROOT);
        String target = first[1];
        int q = target.indexOf('?');
        r.path = q < 0 ? target : target.substring(0, q);
        if (q >= 0) {
            for (String pair : target.substring(q + 1).split("&")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) continue;
                r.query.put(URLDecoder.decode(pair.substring(0, eq), "UTF-8"),
                        URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
            }
        }
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) r.headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT),
                    lines[i].substring(colon + 1).trim());
        }
        int len = 0;
        String cl = r.headers.get("content-length");
        try { len = cl == null ? 0 : Integer.parseInt(cl); } catch (NumberFormatException ignored) {}
        if (len < 0 || len > MAX_BODY) return null;
        byte[] body = new byte[len];
        int off = 0;
        while (off < len) {
            int n = in.read(body, off, len - off);
            if (n < 0) return null;
            off += n;
        }
        r.body = body;
        return r;
    }

    private static void write(OutputStream out, Response res) throws IOException {
        String head = "HTTP/1.1 " + res.status + " " + reason(res.status) + "\r\n"
                + "Content-Type: " + res.type + "\r\n"
                + "Content-Length: " + res.body.length + "\r\n"
                + "Cache-Control: " + res.cache + "\r\n"
                + "X-Content-Type-Options: nosniff\r\n"
                + "Referrer-Policy: no-referrer\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(res.body);
        out.flush();
    }

    private static String reason(int s) {
        switch (s) {
            case 200: return "OK";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 429: return "Too Many Requests";
            default: return "Error";
        }
    }

    // --- Routen ---------------------------------------------------------------

    Response route(Request r) {
        if (r.method.equals("GET") && !r.path.startsWith("/api/")) return staticFile(r.path);

        if (r.path.equals("/api/pair/start") && r.method.equals("POST")) return startPairing();
        if (r.path.equals("/api/pair") && r.method.equals("POST")) return pair(r.json().optString("pin"));

        String token = r.token();
        if (token == null || !backend.isToken(token)) return json(401, "{\"error\":\"unpaired\"}");

        switch (r.method + " " + r.path) {
            case "GET /api/status":
                return json(200, obj("name", backend.tvName(), "psk", backend.hasPsk(), "calendar", backend.hasCalendar()));
            case "GET /api/apps":
                return json(200, backend.appsJson());
            case "GET /api/icon": {
                byte[] png = backend.icon(r.query.get("pkg"), r.query.get("style"));
                if (png == null) return text(404, "Not Found");
                Response res = new Response(200, "image/png", png);
                res.cache = "private, max-age=86400";
                return res;
            }
            case "GET /api/wallpaper": {
                byte[] jpg = backend.wallpaper();
                return jpg == null ? text(404, "Not Found") : new Response(200, "image/jpeg", jpg);
            }
            case "POST /api/launch":
                return backend.launch(r.json().optString("pkg")) ? ok() : json(404, "{\"error\":\"app\"}");
            case "POST /api/key": {
                String result = backend.key(r.json().optString("key"));
                return json("ok".equals(result) ? 200 : 409, obj("result", result));
            }
            case "POST /api/psk":
                backend.setPsk(r.json().optString("psk").trim());
                return ok();
            case "POST /api/calendar": {
                String url = calendarUrl(r.json().optString("url"));
                if (url == null) return json(400, "{\"error\":\"url\"}");
                backend.setCalendarUrl(url);
                return ok();
            }
            case "POST /api/unpair":
                backend.removeToken(token);
                return ok();
            default:
                return json(404, "{\"error\":\"route\"}");
        }
    }

    private synchronized Response startPairing() {
        pin = String.format(Locale.ROOT, "%04d", random.nextInt(10_000));
        pinTries = 0;
        backend.showPin(pin);
        return ok();
    }

    private synchronized Response pair(String attempt) {
        if (pin == null) return json(403, "{\"error\":\"no_pin\"}");
        if (!pin.equals(attempt)) {
            if (++pinTries >= MAX_PIN_TRIES) pin = null; // neue PIN anfordern
            return json(pin == null ? 429 : 403, "{\"error\":\"wrong_pin\"}");
        }
        pin = null;
        byte[] raw = new byte[24];
        random.nextBytes(raw);
        StringBuilder sb = new StringBuilder();
        for (byte b : raw) sb.append(String.format(Locale.ROOT, "%02x", b));
        String token = sb.toString();
        backend.addToken(token);
        return json(200, obj("token", token));
    }

    private Response staticFile(String path) {
        String name = path.equals("/") ? "index.html" : path.substring(1);
        if (name.contains("..") || name.contains("/")) return text(404, "Not Found");
        byte[] data = assets.read(name);
        if (data == null) return text(404, "Not Found");
        Response res = new Response(200, mime(name), data);
        res.cache = name.endsWith(".png") || name.endsWith(".ttf") ? "public, max-age=604800" : "no-cache";
        return res;
    }

    static String mime(String name) {
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".webmanifest")) return "application/manifest+json";
        if (name.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".ttf")) return "font/ttf";
        return "application/octet-stream";
    }

    private static JSONObject obj(Object... kv) {
        try {
            JSONObject o = new JSONObject();
            for (int i = 0; i + 1 < kv.length; i += 2) o.put((String) kv[i], kv[i + 1]);
            return o;
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Response ok() { return json(200, "{\"ok\":true}"); }

    /** webcal:// wird zu https://; nur http(s), höchstens 2048 Zeichen. null = ungültig. */
    static String calendarUrl(String raw) {
        String u = raw == null ? "" : raw.trim();
        if (u.isEmpty()) return "";
        if (u.regionMatches(true, 0, "webcal://", 0, 9)) u = "https://" + u.substring(9);
        if (u.length() > 2048 || u.matches(".*\\s.*")) return null;
        if (!u.regionMatches(true, 0, "https://", 0, 8) && !u.regionMatches(true, 0, "http://", 0, 7)) return null;
        return u;
    }

    private static Response json(int status, JSONObject o) { return json(status, o.toString()); }

    private static Response json(int status, String body) {
        return new Response(status, "application/json; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    private static Response text(int status, String body) {
        return new Response(status, "text/plain; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }
}
