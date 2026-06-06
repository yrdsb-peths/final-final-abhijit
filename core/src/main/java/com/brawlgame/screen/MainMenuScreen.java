package com.brawlgame.screen;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.brawlgame.ui.UiButton;

/**
 * The root menu. Two centred stacked buttons route to the player sandbox and the map-maker flow, and
 * a scaled-up title sits at the top. Buttons are re-laid-out from the live width/height every frame
 * so resizing needs no extra work. Owns its own {@link ShapeRenderer}, {@link SpriteBatch} and
 * {@link BitmapFont}, all driven in a pixel-space ortho2D projection.
 */
public final class MainMenuScreen implements Screen {

    private static final String TITLE = "MINECRAFT DUNGEONS — MAP MAKER";
    private static final float BTN_W = 320f;
    private static final float BTN_H = 64f;
    private static final float BTN_GAP = 20f;

    private final Game game;

    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();

    private final UiButton testPlayer = new UiButton("Test Player", 0, 0, BTN_W, BTN_H);
    private final UiButton mapMaker   = new UiButton("Map Maker", 0, 0, BTN_W, BTN_H);

    public MainMenuScreen(Game game) {
        this.game = game;
    }

    @Override
    public void render(float delta) {
        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();

        Gdx.gl.glClearColor(0.10f, 0.11f, 0.14f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // --- Layout (centred, stacked). ---
        float cx = (w - BTN_W) * 0.5f;
        float topY = h * 0.5f + (BTN_H + BTN_GAP) * 0.5f;
        testPlayer.setBounds(cx, topY, BTN_W, BTN_H);
        mapMaker.setBounds(cx, topY - BTN_H - BTN_GAP, BTN_W, BTN_H);

        // --- Mouse (convert y-down input to y-up). ---
        float mx = Gdx.input.getX();
        float my = h - Gdx.input.getY();
        testPlayer.setHovered(testPlayer.contains(mx, my));
        mapMaker.setHovered(mapMaker.contains(mx, my));

        shapes.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        batch.getProjectionMatrix().setToOrtho2D(0, 0, w, h);

        shapes.begin(ShapeType.Filled);
        testPlayer.renderBackground(shapes);
        mapMaker.renderBackground(shapes);
        shapes.end();

        batch.begin();
        // Title, scaled up ~2x, centred near the top.
        font.getData().setScale(2f);
        font.setColor(1f, 1f, 1f, 1f);
        layout.setText(font, TITLE);
        font.draw(batch, layout, (w - layout.width) * 0.5f, h - 60f);
        font.getData().setScale(1f);

        testPlayer.renderLabel(batch, font);
        mapMaker.renderLabel(batch, font);
        batch.end();

        // --- Click handling. ---
        if (Gdx.input.justTouched()) {
            if (testPlayer.contains(mx, my)) {
                game.setScreen(new TestPlayerScreen(game));
            } else if (mapMaker.contains(mx, my)) {
                game.setScreen(new MapMakerMenuScreen(game));
            }
        }
    }

    @Override public void show()    { }
    @Override public void hide()    { }
    @Override public void pause()   { }
    @Override public void resume()  { }
    @Override public void resize(int width, int height) { }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}
