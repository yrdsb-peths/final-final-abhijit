package com.brawlgame.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Disposable;
import com.brawlgame.game.MatchManager;
import com.brawlgame.game.MatchStats;
import com.brawlgame.audio.AudioManager;

/**
 * Brawl-Stars-style 1v1 end screen with 3D character portraits, match stats, and PROCEED.
 */
public final class EndScreenOverlay implements Disposable {

    private final UiViewport uiv = new UiViewport();
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();
    private final UiButton proceed = new UiButton("PROCEED", 0, 0, 200, 52);

    private CharacterShowcase showcase;
    private int playerIdx, rivalIdx;
    private MatchManager.Outcome outcome = MatchManager.Outcome.NONE;
    private MatchStats playerStats, rivalStats;
    private float anim;
    private float defeatFade; // 0→1: drives the red-tint + alpha-fade on the losing portrait
    private Runnable onProceed;

    public void bindShowcase(CharacterShowcase showcase, int playerIdx, int rivalIdx) {
        this.showcase = showcase;
        this.playerIdx = playerIdx;
        this.rivalIdx = rivalIdx;
    }

    public void show(MatchManager.Outcome outcome, MatchStats player, MatchStats rival, Runnable onProceed) {
        this.outcome = outcome;
        this.playerStats = player;
        this.rivalStats = rival;
        this.onProceed = onProceed;
        this.anim = 0f;
        this.defeatFade = 0f;
        AudioManager.get().gameOver();
    }

    public boolean isVisible() { return outcome != MatchManager.Outcome.NONE; }

    public void resize(int width, int height) {
        uiv.resize(width, height);
        batch.setProjectionMatrix(uiv.combined());
        shapes.setProjectionMatrix(uiv.combined());
    }

    public void update(float delta) {
        if (!isVisible()) return;
        anim = Math.min(1f, anim + delta * 1.8f);
        defeatFade = Math.min(1f, defeatFade + delta * 0.65f); // gradual red tint + alpha fade
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) tryProceed();
        if (Gdx.input.justTouched()) {
            Vector2 m = uiv.unproject(Gdx.input.getX(), Gdx.input.getY());
            click(m.x, m.y);
        }
    }

    public void render() {
        if (!isVisible()) return;

        float w = uiv.width(), h = uiv.height();
        Vector2 m = uiv.unproject(Gdx.input.getX(), Gdx.input.getY());
        proceed.setBounds(w - 230f, 28f, 200f, 52f);
        proceed.setHovered(proceed.contains(m.x, m.y));

        uiv.apply();
        batch.setProjectionMatrix(uiv.combined());
        shapes.setProjectionMatrix(uiv.combined());
        Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);

        shapes.begin(ShapeType.Filled);
        shapes.setColor(0.06f, 0.10f, 0.22f, 0.94f);
        shapes.rect(0, 0, w * 0.5f + 40f, h);
        shapes.setColor(0.22f, 0.06f, 0.08f, 0.94f);
        shapes.rect(w * 0.5f - 40f, 0, w * 0.5f + 40f, h);
        shapes.setColor(0.12f, 0.12f, 0.14f, 0.55f);
        shapes.rect(w * 0.5f - 3f, 0, 6f, h);
        shapes.end();

        boolean playerWon = outcome == MatchManager.Outcome.PLAYER_WIN
            || outcome == MatchManager.Outcome.DRAW;
        boolean rivalWon = outcome == MatchManager.Outcome.RIVAL_WIN
            || outcome == MatchManager.Outcome.DRAW;
        float slide = MathUtils.lerp(-80f, 0f, anim);

        renderSide(true, playerWon, playerIdx, playerStats, slide, w, h);
        renderSide(false, rivalWon, rivalIdx, rivalStats, -slide, w, h);

        if (outcome == MatchManager.Outcome.DRAW) {
            batch.begin();
            font.getData().setScale(2.2f);
            font.setColor(Color.GOLD);
            String draw = "DRAW";
            layout.setText(font, draw);
            font.draw(batch, draw, (w - layout.width) * 0.5f, h * 0.92f);
            font.getData().setScale(1f);
            batch.end();
        }

        shapes.begin(ShapeType.Filled);
        proceed.renderBackground(shapes);
        shapes.end();
        batch.begin();
        proceed.renderLabel(batch, font);
        batch.end();
        Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
    }

    public boolean click(float vx, float vy) {
        if (!isVisible()) return false;
        if (proceed.contains(vx, vy)) { tryProceed(); return true; }
        return true;
    }

    private void tryProceed() {
        if (onProceed != null) {
            // Defer screen transition to avoid calling dispose() during render cycle
            Runnable pending = onProceed;
            onProceed = null; // Clear to prevent double-invocation
            // Post the transition to run after current frame completes
            Gdx.app.postRunnable(pending);
        }
    }

    private void renderSide(boolean left, boolean won, int showIdx, MatchStats stats,
                            float slideX, float w, float h) {
        float halfW = w * 0.5f;
        float cx = left ? halfW * 0.5f + slideX : halfW + halfW * 0.5f + slideX;
        float pw = 200f, ph = 260f;
        float px = cx - pw * 0.5f, py = h * 0.34f;
        float roll = won ? 0f : 14f;
        float spin = left ? -16f : 16f;
        // Defeat: smoothly tint to pure red while fading alpha (sprite.setColor(1,0,0,alpha))
        Color tint;
        if (won || outcome == MatchManager.Outcome.DRAW) {
            tint = Color.WHITE;
        } else {
            float g = MathUtils.lerp(1f, 0f, defeatFade);
            float a = MathUtils.lerp(1f, 0.30f, defeatFade);
            tint = new Color(1f, g, g, a);
        }

        if (showcase != null) {
            showcase.render(uiv, showIdx, px, py, pw, ph, spin, roll, tint);
        }

        batch.begin();
        font.getData().setScale(won ? 2.4f : 1.8f);
        font.setColor(won ? new Color(1f, 0.92f, 0.2f, 1f) : new Color(0.85f, 0.2f, 0.2f, 1f));
        String header = won ? "VICTORY!" : "DEFEAT";
        if (outcome == MatchManager.Outcome.DRAW) header = "DRAW";
        layout.setText(font, header);
        font.draw(batch, header, cx - layout.width * 0.5f, h * 0.82f);

        font.getData().setScale(1.3f);
        font.setColor(Color.WHITE);
        String name = stats != null ? stats.name : (left ? "You" : "Rival");
        layout.setText(font, name);
        font.draw(batch, name, cx - layout.width * 0.5f, h * 0.74f);

        font.getData().setScale(1.05f);
        font.setColor(0.85f, 0.88f, 0.92f, 1f);
        if (stats != null) {
            drawStat(cx, h * 0.26f, "Takedowns", stats.takedowns);
            drawStat(cx, h * 0.19f, "Damage Dealt", Math.round(stats.damageDealt));
            drawStat(cx, h * 0.12f, "Healing", Math.round(stats.healing));
        }
        font.getData().setScale(1f);
        batch.end();
    }

    private void drawStat(float cx, float y, String label, Object value) {
        String line = label + ": " + value;
        layout.setText(font, line);
        font.draw(batch, line, cx - layout.width * 0.5f, y);
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        font.dispose();
    }
}
