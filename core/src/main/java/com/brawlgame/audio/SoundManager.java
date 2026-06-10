package com.brawlgame.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import com.brawlgame.ui.Settings;

/**
 * Simple sound manager for the game. Loads and caches sounds from assets/sounds/
 * and plays them with the master volume applied. Currently plays: sword hits,
 * gun shots, step sounds, and hurt sounds.
 */
public final class SoundManager implements Disposable {

    private static SoundManager instance;

    public static SoundManager get() {
        if (instance == null) instance = new SoundManager();
        return instance;
    }

    private final ObjectMap<String, Sound> sounds = new ObjectMap<>();

    // Sound paths in the assets folder (relative to assets root, without leading "assets/")
    private static final String[] SWING_SOUNDS = {
        "sounds/entity/player/attack/sweep1.ogg",
        "sounds/entity/player/attack/sweep2.ogg",
        "sounds/entity/player/attack/sweep3.ogg"
    };

    private static final String[] STEP_SOUNDS = {
        "sounds/step/stone1.ogg", "sounds/step/stone2.ogg", "sounds/step/stone3.ogg",
        "sounds/step/stone4.ogg", "sounds/step/stone5.ogg", "sounds/step/stone6.ogg",
        "sounds/step/grass1.ogg", "sounds/step/grass2.ogg", "sounds/step/grass3.ogg",
        "sounds/step/grass4.ogg", "sounds/step/grass5.ogg", "sounds/step/grass6.ogg"
    };

    private static final String[] HURT_SOUNDS = {
        "sounds/damage/hit1.ogg", "sounds/damage/hit2.ogg", "sounds/damage/hit3.ogg"
    };

    private static final String GUN_SHOT_SOUND = "sounds/random/bow.ogg";

    private SoundManager() {
        // Load all sounds on creation
        for (String s : SWING_SOUNDS) {
            try {
                FileHandle f = Gdx.files.internal(s);
                if (f.exists()) {
                    sounds.put(s, Gdx.audio.newSound(f));
                }
            } catch (Exception e) {
                Gdx.app.error("SoundManager", "Failed to load: " + s, e);
            }
        }
        for (String s : STEP_SOUNDS) {
            try {
                FileHandle f = Gdx.files.internal(s);
                if (f.exists()) {
                    sounds.put(s, Gdx.audio.newSound(f));
                }
            } catch (Exception e) {
                Gdx.app.error("SoundManager", "Failed to load: " + s, e);
            }
        }
        for (String s : HURT_SOUNDS) {
            try {
                FileHandle f = Gdx.files.internal(s);
                if (f.exists()) {
                    sounds.put(s, Gdx.audio.newSound(f));
                }
            } catch (Exception e) {
                Gdx.app.error("SoundManager", "Failed to load: " + s, e);
            }
        }
        try {
            FileHandle f = Gdx.files.internal(GUN_SHOT_SOUND);
            if (f.exists()) {
                sounds.put(GUN_SHOT_SOUND, Gdx.audio.newSound(f));
            }
        } catch (Exception e) {
            Gdx.app.error("SoundManager", "Failed to load: " + GUN_SHOT_SOUND, e);
        }
    }

    private float volume() {
        return com.badlogic.gdx.math.MathUtils.clamp(Settings.get().masterVolume, 0f, 1f);
    }

    /** No-op for now as SoundManager only plays one-shots, but keeps API consistent with AudioManager. */
    public void syncVolume() {}

    /** Play a random sword swing hit sound. */
    public void playSwordHit() {
        if (SWING_SOUNDS.length == 0) return;
        int idx = (int) (Math.random() * SWING_SOUNDS.length);
        Sound s = sounds.get(SWING_SOUNDS[idx]);
        if (s != null) s.play(volume());
    }

    /** Play a random step sound. */
    public void playStep() {
        if (STEP_SOUNDS.length == 0) return;
        int idx = (int) (Math.random() * STEP_SOUNDS.length);
        Sound s = sounds.get(STEP_SOUNDS[idx]);
        if (s != null) s.play(volume() * 0.7f);
    }

    /** Play a random hurt sound. */
    public void playHurt() {
        if (HURT_SOUNDS.length == 0) return;
        int idx = (int) (Math.random() * HURT_SOUNDS.length);
        Sound s = sounds.get(HURT_SOUNDS[idx]);
        if (s != null) s.play(volume());
    }

    /** Play gun shot sound. */
    public void playGun() {
        Sound s = sounds.get(GUN_SHOT_SOUND);
        if (s != null) s.play(volume() * 0.9f);
    }

    @Override
    public void dispose() {
        for (Sound s : sounds.values()) {
            if (s != null) s.dispose();
        }
        sounds.clear();
    }
}