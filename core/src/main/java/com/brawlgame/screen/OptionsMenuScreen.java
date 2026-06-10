package com.brawlgame.screen;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Vector2;
import com.brawlgame.audio.AudioManager;
import com.brawlgame.ui.OptionsPanel;
import com.brawlgame.ui.UiViewport;

/**
 * Full-screen wrapper around {@link OptionsPanel} accessible from the main menu.
 * Uses a {@link UiViewport} (1280×720 virtual canvas) so text stays readable at any resolution
 * including fullscreen. ESC / "Done" returns to the main menu.
 */
public final class OptionsMenuScreen implements Screen, InputProcessor {

    private final Game game;
    private final UiViewport uv  = new UiViewport();
    private final ShapeRenderer sh = new ShapeRenderer();
    private final SpriteBatch   bt = new SpriteBatch();
    private final BitmapFont    fn = new BitmapFont();
    private final OptionsPanel  op = new OptionsPanel();

    public OptionsMenuScreen(Game game) { this.game = game; }

    @Override
    public void show() {
        op.reset();
        uv.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.input.setInputProcessor(this);
    }

    @Override
    public void render(float delta) {
        float W = uv.width(), H = uv.height();
        Vector2 m = uv.unproject(Gdx.input.getX(), Gdx.input.getY());

        // Panel occupies 86 % of the virtual canvas, centred
        float panelW = W * 0.86f;
        float panelH = H * 0.88f;
        float panelX = (W - panelW) * 0.5f;
        float panelY = (H - panelH) * 0.5f;

        Gdx.gl.glClearColor(0.06f, 0.06f, 0.09f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        uv.apply();
        sh.setProjectionMatrix(uv.combined());
        bt.setProjectionMatrix(uv.combined());

        sh.begin(ShapeType.Filled);
        op.renderBg(sh, panelX, panelY, panelW, panelH, m.x, m.y);
        sh.end();

        bt.begin();
        fn.getData().setScale(1f);
        op.renderText(bt, fn);
        bt.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    @Override
    public void resize(int w, int h) {
        uv.resize(w, h);
        sh.setProjectionMatrix(uv.combined());
        bt.setProjectionMatrix(uv.combined());
    }

    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void hide()   { Gdx.input.setInputProcessor(null); }

    @Override
    public void dispose() {
        sh.dispose();
        bt.dispose();
        fn.dispose();
    }

    // ---------------------------------------------------------------- input processor
    @Override
    public boolean keyDown(int keycode) {
        if (op.keyDown(keycode)) return true;
        if (keycode == Input.Keys.ESCAPE) {
            game.setScreen(new MainMenuScreen(game));
            return true;
        }
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        Vector2 m = uv.unproject(screenX, screenY);
        if (op.click(m.x, m.y)) {
            AudioManager.get().click();
            game.setScreen(new MainMenuScreen(game));
        }
        return true;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        Vector2 m = uv.unproject(screenX, screenY);
        op.drag(m.x);
        return true;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        op.release();
        return true;
    }

    @Override public boolean keyUp(int keycode) { return false; }
    @Override public boolean keyTyped(char character) { return false; }
    @Override public boolean mouseMoved(int screenX, int screenY) { return false; }
    @Override public boolean scrolled(float amountX, float amountY) { return false; }
    @Override public boolean touchCancelled(int screenX, int screenY, int pointer, int button) { return false; }
}
