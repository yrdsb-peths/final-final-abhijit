package com.brawlgame.ui;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * A reusable clickable rectangle button in y-up screen pixels. It owns no renderers; the caller
 * passes shared ones, and the draw step is split into {@link #renderBackground(ShapeRenderer)} and
 * {@link #renderLabel(SpriteBatch, BitmapFont)} so a screen can batch every button's fill in one
 * {@code Filled} pass and every label in one {@code SpriteBatch} pass without begin/end coupling.
 */
public final class UiButton {

    private final String text;
    private float x;
    private float y;
    private float w;
    private float h;
    private boolean hovered;

    private final GlyphLayout layout = new GlyphLayout();

    /** Position is the bottom-left corner in y-up screen pixels. */
    public UiButton(String text, float x, float y, float w, float h) {
        this.text = text;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    /** Repositions the button (screens recompute layout each frame so resize works). */
    public void setBounds(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    /** True if the point (in y-up screen coords) falls inside the button. */
    public boolean contains(float screenX, float screenY) {
        return screenX >= x && screenX <= x + w && screenY >= y && screenY <= y + h;
    }

    public void setHovered(boolean h) {
        this.hovered = h;
    }

    /** Draws the rounded filled background. Assumes {@code shapes} is begun in {@code Filled} mode. */
    public void renderBackground(ShapeRenderer shapes) {
        if (hovered) {
            shapes.setColor(0.35f, 0.40f, 0.50f, 1f);
        } else {
            shapes.setColor(0.18f, 0.20f, 0.26f, 1f);
        }
        roundedRect(shapes, x, y, w, h, 8f);
    }

    /** Draws the centred label. Assumes {@code batch} is already begun. */
    public void renderLabel(SpriteBatch batch, BitmapFont font) {
        font.getData().setScale(1f);
        font.setColor(1f, 1f, 1f, 1f);
        layout.setText(font, text);
        float lx = x + (w - layout.width) * 0.5f;
        float ly = y + (h + layout.height) * 0.5f;
        font.draw(batch, layout, lx, ly);
    }

    private static void roundedRect(ShapeRenderer shapes, float x, float y, float w, float h, float r) {
        r = Math.min(r, Math.min(w, h) * 0.5f);
        shapes.rect(x + r, y, w - 2f * r, h);
        shapes.rect(x, y + r, w, h - 2f * r);
        shapes.circle(x + r, y + r, r);
        shapes.circle(x + w - r, y + r, r);
        shapes.circle(x + r, y + h - r, r);
        shapes.circle(x + w - r, y + h - r, r);
    }

    public float x()       { return x; }
    public float y()       { return y; }
    public float w()       { return w; }
    public float h()       { return h; }
    public String text()   { return text; }
}
