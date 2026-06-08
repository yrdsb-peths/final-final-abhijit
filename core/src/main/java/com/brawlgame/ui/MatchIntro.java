package com.brawlgame.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.utils.Disposable;

/**
 * Pre-match intro: trading-card panels with live 3D character previews (not broken 2D skin crops).
 */
public final class MatchIntro implements Disposable {

    private static final float SLIDE = 0.8f;
    private static final float STAGGER = 0.3f;
    private static final float FADE = 0.55f;
    private static final float CARD_W = 168f, CARD_H = 220f, GAP = 28f;
    private static final Color PANEL = BedrockWidgets.rgb(0x23, 0x23, 0x2A);
    private static final Color NAMEBAR = BedrockWidgets.rgb(0x12, 0x12, 0x16);

    private final CharacterShowcase showcase;
    private final int[] entryIdx;
    private final String[] names;
    private final UiViewport uiv = new UiViewport();
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout gl = new GlyphLayout();

    private float t, duration;

    public MatchIntro(CharacterShowcase showcase, int[] entryIdx, String[] names, float duration) {
        this.showcase = showcase;
        this.entryIdx = entryIdx;
        this.names = names;
        this.duration = duration;
    }

    public void resize(int w, int h) {
        uiv.resize(w, h);
        batch.setProjectionMatrix(uiv.combined());
        shapes.setProjectionMatrix(uiv.combined());
    }

    public void update(float delta) { t += delta; }
    public boolean isDone() { return t >= duration; }

    public void render() {
        if (isDone()) return;
        int n = entryIdx.length;
        float w = uiv.width(), h = uiv.height();
        float totalW = n * CARD_W + (n - 1) * GAP;
        float left = (w - totalW) * 0.5f;
        float cy = h * 0.52f;
        float globalFade = t > duration - FADE ? Math.max(0f, (duration - t) / FADE) : 1f;

        uiv.apply();
        batch.setProjectionMatrix(uiv.combined());
        shapes.setProjectionMatrix(uiv.combined());
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapes.begin(ShapeType.Filled);
        for (int i = 0; i < n; i++) {
            float p = clamp01((t - i * STAGGER) / SLIDE);
            if (p <= 0f) continue;
            float ease = p * p;
            float targetX = left + i * (CARD_W + GAP);
            float x = lerp(-CARD_W - 40f, targetX, ease);
            float a = globalFade * clamp01(p * 2.5f);
            fill(PANEL, a, x, cy, CARD_W, CARD_H);
            BedrockWidgets.rect(shapes, x, cy, CARD_W, CARD_H, tmp(BedrockWidgets.PANEL_EDGE, a));
            fill(PANEL, a, x + 3, cy + 3, CARD_W - 6, CARD_H - 6);
            fill(NAMEBAR, a, x + 3, cy + 3, CARD_W - 6, 34);
        }
        shapes.end();

        for (int i = 0; i < n; i++) {
            float p = clamp01((t - i * STAGGER) / SLIDE);
            if (p <= 0f) continue;
            float ease = p * p;
            float targetX = left + i * (CARD_W + GAP);
            float x = lerp(-CARD_W - 40f, targetX, ease);
            float a = globalFade * clamp01(p * 2.5f);
            float spin = i == 0 ? -22f : 22f;
            showcase.render(uiv, entryIdx[i], x + 8, cy + 40, CARD_W - 16, CARD_H - 52, spin, 0f,
                new Color(1f, 1f, 1f, a));
        }

        batch.begin();
        for (int i = 0; i < n; i++) {
            float p = clamp01((t - i * STAGGER) / SLIDE);
            if (p <= 0f) continue;
            float ease = p * p;
            float targetX = left + i * (CARD_W + GAP);
            float x = lerp(-CARD_W - 40f, targetX, ease);
            float a = globalFade * clamp01(p * 2.5f);
            font.setColor(1f, 1f, 1f, a);
            gl.setText(font, names[i]);
            font.draw(batch, gl, x + (CARD_W - gl.width) * 0.5f, cy + 26);
        }
        batch.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void fill(Color c, float a, float x, float y, float fw, float fh) {
        shapes.setColor(c.r, c.g, c.b, a);
        shapes.rect(x, y, fw, fh);
    }

    private static Color tmp(Color base, float a) { return new Color(base.r, base.g, base.b, a); }
    private static float clamp01(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    @Override
    public void dispose() { batch.dispose(); shapes.dispose(); font.dispose(); }
}
