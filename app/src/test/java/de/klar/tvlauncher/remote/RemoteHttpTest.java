package de.klar.tvlauncher.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RemoteHttpTest {

    static final class Fake implements RemoteHttp.Backend {
        String pin;
        final Set<String> tokens = new HashSet<>();
        final List<String> keys = new ArrayList<>();
        final List<String> launched = new ArrayList<>();
        String psk = "";
        public String tvName() { return "KD-65XD7505"; }
        public String appsJson() { return "{\"apps\":[{\"pkg\":\"com.netflix.ninja\",\"label\":\"Netflix\",\"dock\":true}]}"; }
        public byte[] icon(String pkg) { return "com.netflix.ninja".equals(pkg) ? new byte[]{(byte) 0x89, 'P', 'N', 'G'} : null; }
        public byte[] wallpaper() { return null; }
        public boolean launch(String pkg) { launched.add(pkg); return pkg.equals("com.netflix.ninja"); }
        public String key(String name) { keys.add(name); return psk.isEmpty() && name.equals("up") ? "needs_psk" : "ok"; }
        public boolean hasPsk() { return !psk.isEmpty(); }
        public void setPsk(String p) { psk = p; }
        public void showPin(String p) { pin = p; }
        public boolean isToken(String t) { return tokens.contains(t); }
        public void addToken(String t) { tokens.add(t); }
        public void removeToken(String t) { tokens.remove(t); }
    }

    private Fake fake;
    private RemoteHttp http;
    private String base;

    @Before public void setUp() throws Exception {
        fake = new Fake();
        http = new RemoteHttp(fake, name -> name.equals("index.html") ? "<html>Klar</html>".getBytes(StandardCharsets.UTF_8) : null);
        http.start(0);
        base = "http://127.0.0.1:" + http.port();
    }

    @After public void tearDown() { http.stop(); }

    static final class Res { int status; String body; String type; }

    private Res call(String method, String path, String body, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
        c.setRequestMethod(method);
        if (token != null) c.setRequestProperty("X-Klar-Token", token);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        }
        Res r = new Res();
        r.status = c.getResponseCode();
        r.type = c.getContentType();
        InputStream in = r.status < 400 ? c.getInputStream() : c.getErrorStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (in != null) { byte[] b = new byte[4096]; int n; while ((n = in.read(b)) > 0) out.write(b, 0, n); }
        r.body = out.toString("UTF-8");
        return r;
    }

    private String pair() throws Exception {
        assertEquals(200, call("POST", "/api/pair/start", "", null).status);
        assertNotNull(fake.pin);
        Res r = call("POST", "/api/pair", "{\"pin\":\"" + fake.pin + "\"}", null);
        assertEquals(200, r.status);
        return new JSONObject(r.body).getString("token");
    }

    @Test public void servesWebAppWithoutToken() throws Exception {
        Res r = call("GET", "/", null, null);
        assertEquals(200, r.status);
        assertTrue(r.type.startsWith("text/html"));
        assertEquals("<html>Klar</html>", r.body);
        assertEquals(404, call("GET", "/../secret", null, null).status);
    }

    @Test public void apiNeedsToken() throws Exception {
        assertEquals(401, call("GET", "/api/apps", null, null).status);
        assertEquals(401, call("POST", "/api/key", "{\"key\":\"ok\"}", "falsch").status);
        assertTrue(fake.keys.isEmpty());
    }

    @Test public void pairingWithCorrectPinGivesWorkingToken() throws Exception {
        String token = pair();
        Res status = call("GET", "/api/status", null, token);
        assertEquals(200, status.status);
        assertEquals("KD-65XD7505", new JSONObject(status.body).getString("name"));
        assertEquals(200, call("GET", "/api/apps", null, token).status);
        // PIN ist nach Gebrauch verbraucht
        assertEquals(403, call("POST", "/api/pair", "{\"pin\":\"" + fake.pin + "\"}", null).status);
    }

    @Test public void tooManyWrongPinsInvalidatePin() throws Exception {
        call("POST", "/api/pair/start", "", null);
        String wrong = fake.pin.equals("0000") ? "1111" : "0000";
        int last = 0;
        for (int i = 0; i < RemoteHttp.MAX_PIN_TRIES; i++) {
            last = call("POST", "/api/pair", "{\"pin\":\"" + wrong + "\"}", null).status;
        }
        assertEquals(429, last);
        // selbst die richtige PIN gilt jetzt nicht mehr
        assertEquals(403, call("POST", "/api/pair", "{\"pin\":\"" + fake.pin + "\"}", null).status);
    }

    @Test public void keysLaunchAndPsk() throws Exception {
        String token = pair();
        Res needs = call("POST", "/api/key", "{\"key\":\"up\"}", token);
        assertEquals(409, needs.status);
        assertEquals("needs_psk", new JSONObject(needs.body).getString("result"));
        assertEquals(200, call("POST", "/api/psk", "{\"psk\":\" 1234 \"}", token).status);
        assertEquals("1234", fake.psk);
        assertEquals(200, call("POST", "/api/key", "{\"key\":\"up\"}", token).status);
        assertEquals(200, call("POST", "/api/launch", "{\"pkg\":\"com.netflix.ninja\"}", token).status);
        assertEquals(404, call("POST", "/api/launch", "{\"pkg\":\"gibts.nicht\"}", token).status);
    }

    @Test public void iconViaQueryTokenAndUnpair() throws Exception {
        String token = pair();
        Res icon = call("GET", "/api/icon?pkg=com.netflix.ninja&t=" + token, null, null);
        assertEquals(200, icon.status);
        assertEquals("image/png", icon.type);
        assertEquals(200, call("POST", "/api/unpair", "{}", token).status);
        assertEquals(401, call("GET", "/api/status", null, token).status);
    }

    @Test public void busyPortFailsSoNextPortCanBeTried() throws Exception {
        RemoteHttp second = new RemoteHttp(fake, name -> null);
        try {
            second.start(http.port());
            org.junit.Assert.fail("Port war belegt, start() hätte scheitern müssen");
        } catch (java.io.IOException expected) {
            second.start(0); // nächster Versuch klappt
            assertTrue(second.port() > 0);
        } finally {
            second.stop();
        }
    }
}
