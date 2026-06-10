package com.brawlgame.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import com.brawlgame.ui.Settings;

/** Central Minecraft-sound loader. Missing files fail silently so dev builds still run. */
public final class AudioManager implements Disposable {
    private static AudioManager instance;

    public static AudioManager get() {
        if (instance == null) instance = new AudioManager();
        return instance;
    }

    public static void disposeGlobal() {
        if (instance != null) { instance.dispose(); instance = null; }
    }

    private final Sound[] grassSteps;
    private final Sound click, swing, shoot, hurt, gameOver;
    private Music menuMusic;
    private float stepTimer;

    private AudioManager() {
        grassSteps = loadMany("step/grass", 1, 6);
        click = loadSound("random/click.ogg");
        swing = loadSound("entity/player/attack/weak3.ogg");
        shoot = loadSound("item/crossbow/shoot1.ogg", "random/bow.ogg");
        hurt = loadSound("random/classic_hurt.ogg");
        gameOver = loadSound("entity/armorstand/break1.ogg", "random/break.ogg");
        menuMusic = loadMusic("music/menu/moog_city_2.ogg", "music/game/minecraft.ogg", "music/game/sweden.ogg");
        if (menuMusic != null) {
            menuMusic.setLooping(true);
            menuMusic.setVolume(0.38f * volume());
        }
    }

    public void playMenuMusic() {
        if (menuMusic == null) return;
        menuMusic.setVolume(0.38f * volume());
        if (!menuMusic.isPlaying()) menuMusic.play();
    }

    public void syncVolume() {
        if (menuMusic != null && menuMusic.isPlaying()) {
            menuMusic.setVolume(0.38f * volume());
        }
    }

    public void stopMenuMusic() {
        if (menuMusic != null && menuMusic.isPlaying()) menuMusic.stop();
    }

    public void click() { play(click, 0.65f, 1.15f); }
    public void swing() { play(swing, 0.55f, 0.92f + MathUtils.random(0.16f)); }
    public void shoot() { play(shoot, 0.75f, 0.95f + MathUtils.random(0.12f)); }
    public void hurt() { play(hurt, 0.70f, 0.92f + MathUtils.random(0.12f)); }
    public void gameOver() { play(gameOver, 0.85f, 0.85f); }

    public void updateFootsteps(float delta, boolean moving, boolean sprinting, boolean onGround) {
        if (!moving || !onGround) {
            stepTimer = 0f;
            return;
        }
        stepTimer -= delta;
        if (stepTimer <= 0f) {
            Sound s = grassSteps.length == 0 ? null : grassSteps[MathUtils.random(grassSteps.length - 1)];
            play(s, sprinting ? 0.45f : 0.32f, 0.92f + MathUtils.random(0.18f));
            stepTimer = sprinting ? 0.24f : 0.34f;
        }
    }

    private void play(Sound sound, float baseVolume, float pitch) {
        if (sound != null) sound.play(baseVolume * volume(), pitch, 0f);
    }

    private float volume() { return MathUtils.clamp(Settings.get().masterVolume, 0f, 1f); }

    private Sound[] loadMany(String prefix, int first, int last) {
        java.util.ArrayList<Sound> sounds = new java.util.ArrayList<>();
        for (int i = first; i <= last; i++) {
            Sound s = loadSound(prefix + i + ".ogg");
            if (s != null) sounds.add(s);
        }
        return sounds.toArray(new Sound[0]);
    }

    private Sound loadSound(String... rels) {
        for (String rel : rels) {
            FileHandle f = resolve(rel);
            if (f != null && f.exists()) {
                try { return Gdx.audio.newSound(f); } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    private Music loadMusic(String... rels) {
        for (String rel : rels) {
            FileHandle f = resolve(rel);
            if (f != null && f.exists()) {
                try { return Gdx.audio.newMusic(f); } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    private FileHandle resolve(String rel) {
        String path = "assets/sounds/" + rel;
        FileHandle f = Gdx.files.internal(path);
        if (f.exists()) return f;
        f = Gdx.files.local(path);
        if (f.exists()) return f;
        f = Gdx.files.local("../" + path);
        if (f.exists()) return f;
        return Gdx.files.absolute(System.getProperty("user.dir") + "/" + path);
    }

    @Override
    public void dispose() {
        for (Sound s : grassSteps) if (s != null) s.dispose();
        if (click != null) click.dispose();
        if (swing != null) swing.dispose();
        if (shoot != null) shoot.dispose();
        if (hurt != null) hurt.dispose();
        if (gameOver != null) gameOver.dispose();
        if (menuMusic != null) menuMusic.dispose();
    }
}
