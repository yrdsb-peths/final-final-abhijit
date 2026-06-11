package com.brawlgame;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.brawlgame.audio.AudioManager;
import com.brawlgame.game.PlayerProfile;
import com.brawlgame.screen.MainMenuScreen;
import com.brawlgame.screen.TutorialScreen;

/**
 * Application entry point. Checks whether this is the first ever launch (via {@link PlayerProfile}
 * preferences) and either shows the card-based {@link TutorialScreen} or jumps straight to the
 * {@link MainMenuScreen}.
 */
public class DungeonGame extends Game {

    @Override
    public void create() {
        // Ensure the local skins/ folder exists so SkinsScreen doesn't crash on a fresh install.
        Gdx.files.local("assets/skins").mkdirs();

        // Initialise persisted profile (lazy singleton, safe to call now that Gdx.app is ready).
        PlayerProfile profile = PlayerProfile.get();

        if (profile.firstLaunch) {
            setScreen(new TutorialScreen(this));
        } else {
            setScreen(new MainMenuScreen(this));
        }
    }

    @Override
    public void dispose() {
        if (getScreen() != null) getScreen().dispose();
        AudioManager.disposeGlobal();
    }
}
