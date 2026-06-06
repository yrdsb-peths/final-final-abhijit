package com.brawlgame;

import com.badlogic.gdx.Game;
import com.brawlgame.screen.MainMenuScreen;

/**
 * Application entry point. A thin {@link Game} that opens on the main menu, from which the developer
 * chooses "Test Player" (the Phase-1 gameplay sandbox) or "Map Maker" (the Brawl-Stars-style level
 * editor). Each mode is a self-contained {@link com.badlogic.gdx.Screen}.
 */
public class DungeonGame extends Game {

    @Override
    public void create() {
        setScreen(new MainMenuScreen(this));
    }

    @Override
    public void dispose() {
        if (getScreen() != null) getScreen().dispose();
    }
}
