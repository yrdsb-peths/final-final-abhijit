package com.brawlgame.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;

/**
 * The Minecraft "took damage" screen tint: a red vignette that bleeds in from the four screen edges
 * (strongest in the corners) and fades out. Driven each frame by the player's hurt fraction (1 just
 * after a hit → 0). Drawn in full-window screen pixels with gradient rects so the centre stays clear.
 */
public final class DamageVignette implements Disposable {

    private static final float MAX_ALPHA = 0.55f;
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final Matrix4 proj = new Matrix4();
    private final Color edge = new Color(0.80f, 0.04f, 0.04f, 1f);
    private final Color clear = new Color(0.80f, 0.04f, 0.04f, 0f);

    /** Red damage flash (default colour). */
    public void render(float intensity) { render(intensity, 0.80f, 0.04f, 0.04f); }

    /** A full-screen semi-transparent red wash that rapidly fades out — the "you took damage" flash. */
    public void renderFlash(float intensity) {
        if (intensity <= 0f) return;
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        // Quadratic ease so it pops hard on the hit then fades fast; capped so it never fully blinds.
        float a = Math.min(0.45f, intensity * intensity * 0.5f);
        edge.set(0.85f, 0.05f, 0.05f, a);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(proj.setToOrtho2D(0, 0, w, h));
        shapes.begin(ShapeType.Filled);
        shapes.setColor(edge);
        shapes.rect(0, 0, w, h);
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /** Same vignette in an arbitrary colour (e.g. purple for the gas). */
    public void render(float intensity, float r, float g, float bcol) {
        if (intensity <= 0f) return;
        edge.set(r, g, bcol, 1f);
        clear.set(r, g, bcol, 0f);
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        edge.a = Math.min(MAX_ALPHA, intensity * MAX_ALPHA);
        float t = Math.min(w, h) * 0.22f; // how far the red bleeds inward

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(proj.setToOrtho2D(0, 0, w, h));
        shapes.begin(ShapeType.Filled);
        // rect(x,y,w,h, c_bottomLeft, c_bottomRight, c_topRight, c_topLeft)
        shapes.rect(0, 0, w, t, edge, edge, clear, clear);       // bottom
        shapes.rect(0, h - t, w, t, clear, clear, edge, edge);   // top
        shapes.rect(0, 0, t, h, edge, clear, clear, edge);       // left
        shapes.rect(w - t, 0, t, h, clear, edge, edge, clear);   // right
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    @Override
    public void dispose() { shapes.dispose(); }
}
