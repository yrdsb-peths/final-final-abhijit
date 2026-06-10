package com.brawlgame.screen;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.brawlgame.map.GameMap;
import com.brawlgame.map.MapSerializer;
import com.brawlgame.ui.UiButton;

/**
 * "Play Custom Map" browser: reads every {@code .map} file in the local {@code maps/} directory and
 * lists them as Bedrock-style buttons. Selecting one loads it via {@link MapSerializer} and hands the
 * rebuilt {@link GameMap} to a fresh {@link GameScreen}, which spawns the player at its spawn point.
 * A Back button returns to the main menu.
 */
public final class MapListScreen implements Screen {

    private static final float ROW_W = 460f, ROW_H = 50f, ROW_GAP = 10f;

    private final Game game;
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();

    private final List<FileHandle> maps = new ArrayList<>();
    private final List<UiButton> rows = new ArrayList<>();
    private final UiButton back = new UiButton("Back", 30f, 30f, 140f, 48f);

    public MapListScreen(Game game) {
        this.game = game;
        FileHandle dir = Gdx.files.local("maps");
        if (dir.exists() && dir.isDirectory()) {
            for (FileHandle f : dir.list(".map")) maps.add(f);
        }
        for (FileHandle f : maps) rows.add(new UiButton(f.nameWithoutExtension(), 0, 0, ROW_W, ROW_H));
    }

    @Override
    public void render(float delta) {
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        Gdx.gl.glClearColor(0.10f, 0.11f, 0.14f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float mx = Gdx.input.getX(), my = h - Gdx.input.getY();
        float topY = h - 130f;
        for (int i = 0; i < rows.size(); i++) {
            float y = topY - i * (ROW_H + ROW_GAP);
            UiButton b = rows.get(i);
            b.setBounds((w - ROW_W) * 0.5f, y, ROW_W, ROW_H);
            b.setHovered(b.contains(mx, my));
        }
        back.setHovered(back.contains(mx, my));

        shapes.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        batch.getProjectionMatrix().setToOrtho2D(0, 0, w, h);

        shapes.begin(ShapeType.Filled);
        for (UiButton b : rows) b.renderBackground(shapes);
        back.renderBackground(shapes);
        shapes.end();

        batch.begin();
        font.getData().setScale(2f);
        font.setColor(1f, 1f, 1f, 1f);
        layout.setText(font, "PLAY CUSTOM MAP");
        font.draw(batch, layout, (w - layout.width) * 0.5f, h - 60f);
        font.getData().setScale(1f);
        if (maps.isEmpty()) {
            layout.setText(font, "No saved maps found in maps/ — build and save one first.");
            font.draw(batch, layout, (w - layout.width) * 0.5f, h * 0.5f);
        }
        for (UiButton b : rows) b.renderLabel(batch, font);
        back.renderLabel(batch, font);
        batch.end();

        if (Gdx.input.justTouched()) {
            if (back.contains(mx, my)) {
                game.setScreen(new MainMenuScreen(game));
                return;
            }
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).contains(mx, my)) {
                    com.brawlgame.audio.AudioManager.get().stopMenuMusic();
                    GameMap loaded = MapSerializer.load(maps.get(i));
                    game.setScreen(new GameScreen(game, loaded));
                    return;
                }
            }
        }
    }

    @Override public void show()   {}
    @Override public void hide()   {}
    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void resize(int width, int height) {}

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}
