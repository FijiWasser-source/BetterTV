package de.bettertv.launcher.remote;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Hält den Fernbedienungs-Server im Hintergrund am Laufen. */
public class RemoteService extends Service {

    /** 8080 ist auf Sony-TVs schon von einem Systemdienst belegt – daher eigene Ports. */
    static final int[] PORTS = {8765, 8766, 8767, 9876};
    private static volatile int activePort = -1;
    private static final String CHANNEL = "remote";
    private RemoteHttp http;
    private AndroidBackend backend;

    public static void start(Context c) {
        Intent i = new Intent(c, RemoteService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (RuntimeException e) {
            Log.w("BetterTV", "Fernbedienung konnte nicht starten", e);
        }
    }

    /** Adresse, die man am Handy eingibt, z. B. http://192.168.1.20:8765 */
    public static String url() {
        String ip = AndroidBackend.lanAddress();
        if (ip == null || activePort < 0) return null;
        return "http://" + ip + ":" + activePort;
    }

    /** Alle gekoppelten Handys trennen. */
    public static void unpairAll(Context c) {
        c.getSharedPreferences("bettertv_remote", Context.MODE_PRIVATE).edit().remove("tokens").apply();
    }

    public static int pairedCount(Context c) {
        return c.getSharedPreferences("bettertv_remote", Context.MODE_PRIVATE)
                .getStringSet("tokens", java.util.Collections.emptySet()).size();
    }

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) goForeground();
        backend = new AndroidBackend(this);
        http = new RemoteHttp(backend, this::asset);
        for (int port : PORTS) {
            try {
                http.start(port);
                activePort = port;
                Log.i("BetterTV", "Fernbedienung erreichbar auf Port " + port);
                return;
            } catch (IOException e) {
                Log.w("BetterTV", "Port " + port + " belegt, versuche den nächsten");
            }
        }
        Log.e("BetterTV", "Kein freier Port für die Fernbedienung");
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override public void onDestroy() {
        if (http != null) http.stop();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private byte[] asset(String name) {
        try (InputStream in = getAssets().open("remote/" + name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    /** Ab Android 8 brauchen Hintergrunddienste eine (stille) Benachrichtigung. */
    @androidx.annotation.RequiresApi(26)
    private void goForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Handy-Fernbedienung",
                NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setContentTitle("Handy-Fernbedienung aktiv")
                .setSmallIcon(de.bettertv.launcher.R.drawable.ic_launcher)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
    }
}
