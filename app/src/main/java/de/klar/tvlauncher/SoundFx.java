package de.klar.tvlauncher;

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
    private int jump, star, bump;
    private MediaPlayer music;
    private boolean enabled;

    SoundFx(Context c) {
        context = c.getApplicationContext();
    }

    void setEnabled(boolean on) {
        enabled = on;
        if (!on) pauseMusic();
    }

    boolean enabled() { return enabled; }

    private void ensurePool() {
        if (pool != null) return;
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        pool = new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attrs).build();
        jump = pool.load(context, R.raw.sfx_jump, 1);
        star = pool.load(context, R.raw.sfx_star, 1);
        bump = pool.load(context, R.raw.sfx_bump, 1);
    }

    void jump() { play(jump, 0.6f); }
    void star() { play(star, 1f); }
    void bump() { play(bump, 0.9f); }

    private void play(int id, float vol) {
        if (!enabled) return;
        ensurePool();
        float v = FX_VOLUME * vol;
        pool.play(id, v, v, 1, 0, 1f);
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
        if (music != null) { music.release(); music = null; }
        if (pool != null) { pool.release(); pool = null; }
    }
}
