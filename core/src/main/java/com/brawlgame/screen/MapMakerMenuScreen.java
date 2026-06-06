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
import com.brawlgame.map.MapSize;
import com.brawlgame.map.Theme;
import com.brawlgame.ui.UiButton;

/**
 * The "new map" setup screen: the dev picks a {@link Theme} from a toggle row, then a {@link MapSize}
 * to launch the editor with. The currently-selected theme button is shown highlighted (forced
 * hovered). A Back button returns to the {@link MainMenuScreen}. Renders with the same approach as
 * the main menu: owned ShapeRenderer/SpriteBatch/BitmapFont, ortho2D each frame, y-up mouse.
 */
public final class MapMakerMenuScreen implements Screen {

    private static final String TITLE = "NEW MAP";
    private static final float THEME_W = 260f;
    private static final float THEME_H = 56f;
    private static final float SIZE_W = 200f;
    private static final float SIZE_H = 80f;
    private static final float BACK_W = 140f;
    private static final float BACK_H = 48f;

    private final Game game;

    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();

    private Theme selectedTheme = Theme.OVERWORLD;

    private final UiButton overworld = new UiButton(Theme.OVERWORLD.displayName(), 0, 0, THEME_W, THEME_H);
    private final UiButton deepDark  = new UiButton(Theme.DEEP_DARK.displayName(), 0, 0, THEME_W, THEME_H);
    private final UiButton small     = new UiButton("SMALL", 0, 0, SIZE_W, SIZE_H);
    private final UiButton large     = new UiButton("LARGE", 0, 0, SIZE_W, SIZE_H);
    private final UiButton back      = new UiButton("Back", 0, 0, BACK_W, BACK_H);

    public MapMakerMenuScreen(Game game) {
        this.game = game;
    }

    @Override
    public void render(float delta) {
        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();

        Gdx.gl.glClearColor(0.10f, 0.11f, 0.14f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // --- Layout (centred). ---
        float midX = w * 0.5f;
        float themeRowY = h * 0.62f;
        overworld.setBounds(midX - THEME_W - 10f, themeRowY, THEME_W, THEME_H);
        deepDark.setBounds(midX + 10f, themeRowY, THEME_W, THEME_H);

        float sizeRowY = h * 0.38f;
        small.setBounds(midX - SIZE_W - 15f, sizeRowY, SIZE_W, SIZE_H);
        large.setBounds(midX + 15f, sizeRowY, SIZE_W, SIZE_H);

        back.setBounds(30f, 30f, BACK_W, BACK_H);

        // --- Mouse (y-down -> y-up). ---
        float mx = Gdx.input.getX();
        float my = h - Gdx.input.getY();

        // Theme buttons: the selected one is forced active/highlighted; others highlight on hover.
        overworld.setHovered(selectedTheme == Theme.OVERWORLD || overworld.contains(mx, my));
        deepDark.setHovered(selectedTheme == Theme.DEEP_DARK || deepDark.contains(mx, my));
        small.setHovered(small.contains(mx, my));
        large.setHovered(large.contains(mx, my));
        back.setHovered(back.contains(mx, my));

        shapes.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        batch.getProjectionMatrix().setToOrtho2D(0, 0, w, h);

        shapes.begin(ShapeType.Filled);
        overworld.renderBackground(shapes);
        deepDark.renderBackground(shapes);
        small.renderBackground(shapes);
        large.renderBackground(shapes);
        back.renderBackground(shapes);
        shapes.end();

        batch.begin();
        font.getData().setScale(2f);
        font.setColor(1f, 1f, 1f, 1f);
        layout.setText(font, TITLE);
        font.draw(batch, layout, (w - layout.width) * 0.5f, h - 60f);
        font.getData().setScale(1f);

        overworld.renderLabel(batch, font);
        deepDark.renderLabel(batch, font);
        small.renderLabel(batch, font);
        large.renderLabel(batch, font);
        back.renderLabel(batch, font);
        batch.end();

        // --- Click handling. ---
        if (Gdx.input.justTouched()) {
            if (overworld.contains(mx, my)) {
                selectedTheme = Theme.OVERWORLD;
            } else if (deepDark.contains(mx, my)) {
                selectedTheme = Theme.DEEP_DARK;
            } else if (small.contains(mx, my)) {
                game.setScreen(new MapMakerScreen(game, selectedTheme, MapSize.SMALL));
            } else if (large.contains(mx, my)) {
                game.setScreen(new MapMakerScreen(game, selectedTheme, MapSize.LARGE));
            } else if (back.contains(mx, my)) {
                game.setScreen(new MainMenuScreen(game));
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
