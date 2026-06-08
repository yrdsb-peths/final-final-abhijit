package com.brawlgame.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.utils.Disposable;
import com.brawlgame.gfx.GasZone;
import com.brawlgame.map.GameMap;
import com.brawlgame.ui.UiViewport;

/**
 * Showdown match clock, delayed gas activation, and sudden-death at 0:00. Match length scales with
 * map size so tiny mazes get a ~1:30 clock instead of an irrelevant 5:00.
 */
public final class MatchManager implements Disposable {

    public enum Outcome { NONE, PLAYER_WIN, RIVAL_WIN, DRAW }

    private final GasZone gas;
    private final UiViewport uiv = new UiViewport();
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();

    private final float matchSeconds;
    private final float gasGrace;

    private float timeLeft;
    private boolean running;
    private boolean suddenDeath;
    private float gasDelay;
    private Outcome outcome = Outcome.NONE;
    private int playerDeathFrame = -1;
    private int rivalDeathFrame = -1;
    private int frame;

    public MatchManager(GasZone gas, GameMap map) {
        this.gas = gas;
        float cols = map.cols(), rows = map.rows();
        float minDim = Math.max(cols, rows) * GameMap.CELL;
        // Small maze ≈ 90s, medium ≈ 150s, large caps at 5:00.
        matchSeconds = Math.max(90f, Math.min(300f, 45f + minDim * 9f));
        gasGrace = Math.max(35f, Math.min(75f, matchSeconds * 0.38f));
        timeLeft = matchSeconds;
        gasDelay = gasGrace;
    }

    public static float computeMatchSeconds(GameMap map) {
        float minDim = Math.max(map.cols(), map.rows()) * GameMap.CELL;
        return Math.max(90f, Math.min(300f, 45f + minDim * 9f));
    }

    public void resize(int width, int height) {
        uiv.resize(width, height);
        batch.setProjectionMatrix(uiv.combined());
        shapes.setProjectionMatrix(uiv.combined());
    }

    /** Begin the match clock; gas starts after the grace window. */
    public void start() {
        running = true;
    }

    public void update(float delta) {
        if (!running || outcome != Outcome.NONE) return;
        frame++;
        timeLeft = Math.max(0f, timeLeft - delta);

        if (gasDelay > 0f) {
            gasDelay -= delta;
            if (gasDelay <= 0f) gas.activate();
        }

        if (timeLeft <= 0f && !suddenDeath) {
            suddenDeath = true;
            gas.suddenDeath();
        }
    }

    public float timeLeft() { return timeLeft; }
    public float gasGraceRemaining() { return Math.max(0f, gasDelay); }
    public boolean isSuddenDeath() { return suddenDeath; }
    public Outcome outcome() { return outcome; }
    public boolean isOver() { return outcome != Outcome.NONE; }

    public void registerDeath(boolean player, boolean rival) {
        if (player) playerDeathFrame = frame;
        if (rival) rivalDeathFrame = frame;
        if (outcome != Outcome.NONE) return;
        if (player && rival && playerDeathFrame == rivalDeathFrame) {
            outcome = Outcome.DRAW;
        } else if (player && !rival) {
            outcome = Outcome.RIVAL_WIN;
        } else if (rival && !player) {
            outcome = Outcome.PLAYER_WIN;
        }
    }

    public static String formatTime(float seconds) {
        int total = Math.max(0, Math.round(seconds));
        int m = total / 60;
        int s = total % 60;
        return m + ":" + (s < 10 ? "0" : "") + s;
    }

    public void renderTimer() {
        float w = uiv.width();
        float cx = w * 0.5f;
        float top = uiv.height() - 18f;
        String label;
        if (suddenDeath) label = "SUDDEN DEATH";
        else if (gasDelay > 0f) label = formatTime(timeLeft) + "  |  Gas " + formatTime(gasDelay);
        else label = formatTime(timeLeft);

        uiv.apply();
        batch.setProjectionMatrix(uiv.combined());
        shapes.setProjectionMatrix(uiv.combined());

        float bw = label.length() > 8 ? 210f : 184f;
        shapes.begin(ShapeType.Filled);
        shapes.setColor(0.08f, 0.08f, 0.10f, 0.82f);
        shapes.rect(cx - bw * 0.5f - 2f, top - 34f, bw + 4f, 40f);
        shapes.setColor(0.45f, 0.45f, 0.48f, 1f);
        shapes.rect(cx - bw * 0.5f, top - 32f, bw, 36f);
        shapes.end();

        batch.begin();
        font.getData().setScale(1.25f);
        font.setColor(0f, 0f, 0f, 0.9f);
        layout.setText(font, label);
        font.draw(batch, label, cx - layout.width * 0.5f + 1f, top + 1f);
        font.setColor(suddenDeath ? Color.ORANGE : Color.WHITE);
        font.draw(batch, label, cx - layout.width * 0.5f, top);
        font.getData().setScale(1f);
        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        font.dispose();
    }
}
