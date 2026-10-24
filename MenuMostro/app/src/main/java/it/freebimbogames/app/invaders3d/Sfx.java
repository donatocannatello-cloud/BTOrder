package it.freebimbogames.app.invaders3d;

import it.freebimbogames.app.R;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.util.HashMap;
import java.util.Map;

/** Tiny SoundPool wrapper for the retro blips generated into res/raw. */
public final class Sfx {

    private final SoundPool pool;
    private final Map<Integer, Integer> samples = new HashMap<>();
    private final Map<Integer, Float> volumes = new HashMap<>();
    private volatile boolean anyLoaded;

    public Sfx(Context context) {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        pool = new SoundPool.Builder()
                .setMaxStreams(6)
                .setAudioAttributes(attrs)
                .build();
        pool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                if (status == 0) {
                    anyLoaded = true;
                }
            }
        });
        load(context, Game3D.EV_SHOOT, R.raw.shoot, 0.30f);
        load(context, Game3D.EV_ALIEN_HIT, R.raw.alien, 0.55f);
        load(context, Game3D.EV_PLAYER_HIT, R.raw.player, 0.85f);
        load(context, Game3D.EV_UFO_HIT, R.raw.ufo, 0.70f);
        load(context, Game3D.EV_LEVEL_UP, R.raw.level, 0.65f);
        load(context, Game3D.EV_POWERUP, R.raw.powerup, 0.65f);
        load(context, Game3D.EV_SHIELD_ABSORB, R.raw.shield, 0.50f);
        load(context, Game3D.EV_BOSS_DOWN, R.raw.boss, 0.90f);
        load(context, Game3D.EV_BOSS_HIT, R.raw.alien, 0.40f);
    }

    private void load(Context context, int event, int rawRes, float volume) {
        volumes.put(event, volume);
        try {
            samples.put(event, pool.load(context, rawRes, 1));
        } catch (RuntimeException ignored) {
            // Missing or unreadable sound must never break the game.
        }
    }

    public void play(int event) {
        if (!anyLoaded) {
            return;
        }
        Integer id = samples.get(event);
        if (id == null) {
            return;
        }
        Float v = volumes.get(event);
        float vol = v == null ? 0.5f : v;
        try {
            pool.play(id, vol, vol, 1, 0, 1f);
        } catch (RuntimeException ignored) {
            // ignore playback failures
        }
    }

    public void release() {
        try {
            pool.release();
        } catch (RuntimeException ignored) {
            // ignore
        }
    }
}
