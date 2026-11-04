package de.bettertv.launcher;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;

/**
 * Lo-Fi-Musik und Soundeffekte der Startansicht. Alles selbst erzeugt
 * (tools/make_audio.py), keine fremden Aufnahmen. Leise, nur solange die
 * Startansicht sichtbar ist, abschaltbar in den Einstellungen.
 */
final class SoundFx {
    private static final float MUSIC_VOLUME = 0.28f;
    private static final float FX_VOLUME = 0.45f;

    private final Context context;
    private SoundPool pool;
    private int jump, star, bump, talk, levelUp, land, engine, suck, crunch, scream, startle;
    private int engineStream; private boolean engineWanted, engineLoaded;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.Random rnd = new java.util.Random();
    private MediaPlayer music;
    private boolean enabled;

    SoundFx(Context c) {
        context = c.getApplicationContext();
    }

    void setEnabled(boolean on) {
        enabled = on;
        if (!on) { pauseMusic(); stopEngine(); }
    }

    boolean enabled() { return enabled; }

    private void ensurePool() {
        if (pool != null) return;
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        pool = new SoundPool.Builder().setMaxStreams(5).setAudioAttributes(attrs).build();
        jump = pool.load(context, R.raw.sfx_jump, 1);
        star = pool.load(context, R.raw.sfx_star, 1);
        bump = pool.load(context, R.raw.sfx_bump, 1);
        talk = pool.load(context, R.raw.sfx_talk, 1);
        levelUp = pool.load(context, R.raw.sfx_levelup, 1);
        land = pool.load(context, R.raw.sfx_land, 1);
        engine = pool.load(context, R.raw.sfx_engine, 1);
        suck = pool.load(context, R.raw.sfx_suck, 1);
        crunch = pool.load(context, R.raw.sfx_crunch, 1);
        scream = pool.load(context, R.raw.sfx_scream, 1);
        startle = pool.load(context, R.raw.sfx_startle, 1);
        pool.setOnLoadCompleteListener((p, id, status) -> {
            if (id == engine && status == 0) { engineLoaded = true; if (engineWanted) startEngine(); }
        });
    }

    void jump() { play(jump, 0.6f); }
    void star() { play(star, 1f); }
    void bump() { play(bump, 0.9f); }
    void land() { play(land, 0.7f); }
    void levelUp() { play(levelUp, 1f); }
    void suck() { play(suck, 0.45f); }
    void crunch() { play(crunch, 0.8f); }
    void scream() { play(scream, 0.9f); }
    void startle() { play(startle, 0.6f); }

    /** Plapper-Laute zum Spruch: eine Silbe je zwei Buchstaben, Tonhöhe leicht wechselnd. */
    void talk(String text) {
        if (!enabled) return;
        ensurePool();
        int syllables = Math.min(9, Math.max(2, text.replace(" ", "").length() / 2));
        for (int i = 0; i < syllables; i++) {
            handler.postDelayed(() -> {
                if (!enabled || pool == null) return;
                float v = FX_VOLUME * 0.55f;
                pool.play(talk, v, v, 1, 0, 0.85f + rnd.nextFloat() * 0.5f);
            }, i * 75L);
        }
    }

    private void play(int id, float vol) {
        if (!enabled) return;
        ensurePool();
        float v = FX_VOLUME * vol;
        pool.play(id, v, v, 1, 0, 1f);
    }

    /** Ganz leises Motorbrummen des Verfolger-Autos (Schleife). */
    void startEngine() {
        engineWanted = true;
        if (!enabled) return;
        ensurePool();
        if (!engineLoaded || engineStream != 0) return;
        engineStream = pool.play(engine, 0.06f, 0.06f, 0, -1, 1f);
    }

    void stopEngine() {
        engineWanted = false;
        if (pool != null && engineStream != 0) { pool.stop(engineStream); engineStream = 0; }
    }

    /** Musik starten oder weiterlaufen lassen (nachts bleibt sie aus). */
    void startMusic(boolean night) {
        if (!enabled || night) { pauseMusic(); return; }
        try {
            if (music == null) {
                music = MediaPlayer.create(context, R.raw.lofi_loop);
                if (music == null) return;
                music.setLooping(true);
                music.setVolume(MUSIC_VOLUME, MUSIC_VOLUME);
            }
            if (!music.isPlaying()) music.start();
        } catch (RuntimeException ignored) {
        }
    }

    void pauseMusic() {
        try { if (music != null && music.isPlaying()) music.pause(); } catch (RuntimeException ignored) {}
    }

    void release() {
        handler.removeCallbacksAndMessages(null);
        if (music != null) { music.release(); music = null; }
        if (pool != null) { pool.release(); pool = null; }
    }
}
