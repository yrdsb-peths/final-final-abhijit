package com.brawlgame.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;

/**
 * The match-intro "character cards": a short sequence of trading-card portraits (each = a face cropped
 * from the player skin's head region + a nameplate) that <b>slide in left-to-right</b> over the camera
 * pan, <b>starting slow and accelerating</b>, then <b>fade/vanish</b> as the camera settles on the
 * player. Drawn in real screen pixels, re-laid out each frame. Small (2-player) implementation: two cards.
 */
public final class MatchIntro implements Disposable {

    private static final float SLIDE = 0.8f;   // per-card slide-in time
    private static final float STAGGER = 0.3f; // delay between cards
    private static final float FADE = 0.55f;   // fade-out window at the end

    private static final float CARD_W = 150f, CARD_H = 196f, GAP = 28f;
    private static final Color PANEL = BedrockWidgets.rgb(0x23, 0x23, 0x2A);
    private static final Color NAMEBAR = BedrockWidgets.rgb(0x12, 0x12, 0x16);

    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout gl = new GlyphLayout();
    private final Matrix4 proj = new Matrix4();

    private final TextureRegion[] faces;
    private final String[] names;
    private float t = 0f, duration;

    /** Faces are cropped from each skin's head-front region (8,8,8,8 on a 64×64 skin). */
    public MatchIntro(Texture[] skins, String[] names, float duration) {
        this.names = names;
        this.duration = duration;
        faces = new TextureRegion[skins.length];
        for (int i = 0; i < skins.length; i++) {
            skins[i].setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            faces[i] = new TextureRegion(skins[i], 8, 8, 8, 8);
        }
    }

    public void update(float delta) { t += delta; }
    public boolean isDone() { return t >= duration; }

    public void render() {
        if (isDone()) return;
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        int n = faces.length;
        float totalW = n * CARD_W + (n - 1) * GAP;
        float left = (w - totalW) * 0.5f;
        float cy = h * 0.56f;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        proj.setToOrtho2D(0, 0, w, h);
        shapes.setProjectionMatrix(proj);
        batch.setProjectionMatrix(proj);

        float globalFade = t > duration - FADE ? Math.max(0f, (duration - t) / FADE) : 1f;

        for (int i = 0; i < n; i++) {
            float p = clamp01((t - i * STAGGER) / SLIDE);
            if (p <= 0f) continue;
            float ease = p * p;                 // slow start → accelerate
            float targetX = left + i * (CARD_W + GAP);
            float x = lerp(-CARD_W - 40f, targetX, ease); // slide in from off the left edge
            float a = globalFade * clamp01(p * 2.5f);

            // card panel + nameplate (shapes pass)
            shapes.begin(ShapeType.Filled);
            fill(PANEL, a, x, cy, CARD_W, CARD_H);
            BedrockWidgets.rect(shapes, x, cy, CARD_W, CARD_H, tmp(BedrockWidgets.PANEL_EDGE, a)); // thin border feel
            fill(PANEL, a, x + 3, cy + 3, CARD_W - 6, CARD_H - 6);
            fill(NAMEBAR, a, x + 3, cy + 3, CARD_W - 6, 34);
            shapes.end();

            // face + name (batch pass)
            batch.begin();
            float fs = CARD_W - 36;
            batch.setColor(1f, 1f, 1f, a);
            batch.draw(faces[i], x + (CARD_W - fs) * 0.5f, cy + 46, fs, fs);
            font.setColor(1f, 1f, 1f, a);
            gl.setText(font, names[i]);
            font.draw(batch, gl, x + (CARD_W - gl.width) * 0.5f, cy + 26);
            batch.end();
        }
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void fill(Color c, float a, float x, float y, float w, float h) {
        shapes.setColor(c.r, c.g, c.b, a);
        shapes.rect(x, y, w, h);
    }
    private static Color tmp(Color base, float a) { return new Color(base.r, base.g, base.b, a); }
    private static float clamp01(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    @Override
    public void dispose() { batch.dispose(); shapes.dispose(); font.dispose(); }
}
