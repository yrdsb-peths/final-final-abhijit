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
 * 60-second match clock with time-based gas phases:
 * <ul>
 *   <li>0:40 remaining — gas appears at outer edges</li>
 *   <li>0:20 remaining — gas closes to ~50% of map (top/bottom quarters covered)</li>
 *   <li>0:10 remaining — gas collapses to a small center square</li>
 *   <li>0:00 — sudden death; gas covers everything</li>
 * </ul>
 */
public final class MatchManager implements Disposable {

    public enum Outcome { NONE, PLAYER_WIN, RIVAL_WIN, DRAW }

    private static final float MATCH_DURATION = 60f;
    // Gas activates when 40s remain (20s into the match).
    private static final float GAS_GRACE = 20f;

    private final GasZone gas;
    private final UiViewport uiv = new UiViewport();
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();

    private float timeLeft = MATCH_DURATION;
    private float gasDelay = GAS_GRACE;
    private boolean running;
    private boolean suddenDeath;
    private Outcome outcome = Outcome.NONE;
    private int playerDeathFrame = -1;
    private int rivalDeathFrame = -1;
    private int frame;

    public MatchManager(GasZone gas, GameMap map) {
        this.gas = gas;
    }

    public static float computeMatchSeconds(GameMap map) { return MATCH_DURATION; }

    public void resize(int width, int height) {
        uiv.resize(width, height);
        batch.setProjectionMatrix(uiv.combined());
        shapes.setProjectionMatrix(uiv.combined());
    }

    public void start() { running = true; }

    public void update(float delta) {
        if (!running || outcome != Outcome.NONE) return;
        frame++;
        timeLeft = Math.max(0f, timeLeft - delta);

        // Gas grace: activate when 40 seconds remain (20s into the match).
        if (gasDelay > 0f) {
            gasDelay -= delta;
            if (gasDelay <= 0f) gas.activate();
        }

        // Drive gas phases directly from time remaining.
        if (gas.isActive() && !suddenDeath) {
            float cX = gas.centerX(), cZ = gas.centerZ();
            float halfW = (gas.fullMaxX() - gas.fullMinX()) * 0.5f;
            float halfD = (gas.fullMaxZ() - gas.fullMinZ()) * 0.5f;

            if (timeLeft <= 10f) {
                // Phase 3: small center square (~15% of map each way)
                gas.setTargetBounds(cX - halfW*0.15f, cX + halfW*0.15f,
                                    cZ - halfD*0.15f, cZ + halfD*0.15f);
            } else if (timeLeft <= 20f) {
                // Phase 2: top/bottom quarters covered (~40% center zone)
                gas.setTargetBounds(cX - halfW*0.55f, cX + halfW*0.55f,
                                    cZ - halfD*0.38f, cZ + halfD*0.38f);
            } else if (timeLeft <= 40f) {
                // Phase 1: outer edges visible (~75% center zone)
                gas.setTargetBounds(cX - halfW*0.80f, cX + halfW*0.80f,
                                    cZ - halfD*0.80f, cZ + halfD*0.80f);
            }
        }

        if (timeLeft <= 0f && !suddenDeath) {
            suddenDeath = true;
            gas.suddenDeath();
        }
    }

    public float timeLeft()           { return timeLeft; }
    public float gasGraceRemaining()  { return Math.max(0f, gasDelay); }
    public boolean isSuddenDeath()    { return suddenDeath; }
    public Outcome outcome()          { return outcome; }
    public boolean isOver()           { return outcome != Outcome.NONE; }

    public void registerDeath(boolean player, boolean rival) {
        if (player) playerDeathFrame = frame;
        if (rival)  rivalDeathFrame  = frame;
        if (outcome != Outcome.NONE) return;
        if (player && rival && playerDeathFrame == rivalDeathFrame) outcome = Outcome.DRAW;
        else if (player && !rival) outcome = Outcome.RIVAL_WIN;
        else if (rival  && !player) outcome = Outcome.PLAYER_WIN;
    }

    public static String formatTime(float seconds) {
        int total = Math.max(0, Math.round(seconds));
        int m = total / 60, s = total % 60;
        return m + ":" + (s < 10 ? "0" : "") + s;
    }

    public void renderTimer() {
        float w = uiv.width(), cx = w * 0.5f, top = uiv.height() - 18f;
        String label;
        if (suddenDeath)       label = "SUDDEN DEATH";
        else if (gasDelay > 0f) label = formatTime(timeLeft) + "  |  Gas " + formatTime(gasDelay);
        else                    label = formatTime(timeLeft);

        uiv.apply();
        batch.setProjectionMatrix(uiv.combined());
        shapes.setProjectionMatrix(uiv.combined());

        float bw = label.length() > 8 ? 210f : 184f;
        shapes.begin(ShapeType.Filled);
        shapes.setColor(0.08f, 0.08f, 0.10f, 0.82f);
        shapes.rect(cx - bw*0.5f - 2f, top - 34f, bw + 4f, 40f);
        shapes.setColor(0.45f, 0.45f, 0.48f, 1f);
        shapes.rect(cx - bw*0.5f,      top - 32f, bw,      36f);
        shapes.end();

        batch.begin();
        font.getData().setScale(1.25f);
        font.setColor(0f, 0f, 0f, 0.9f);
        layout.setText(font, label);
        font.draw(batch, label, cx - layout.width*0.5f + 1f, top + 1f);
        font.setColor(suddenDeath ? Color.ORANGE : Color.WHITE);
        font.draw(batch, label, cx - layout.width*0.5f, top);
        font.getData().setScale(1f);
        batch.end();
    }

    @Override
    public void dispose() { batch.dispose(); shapes.dispose(); font.dispose(); }
}
