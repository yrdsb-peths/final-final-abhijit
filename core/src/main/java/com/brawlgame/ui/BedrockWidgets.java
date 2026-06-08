package com.brawlgame.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * Procedural "Minecraft Bedrock Preview UI" primitives — flat fills with thick near-black borders, a
 * light bottom bevel, inset slots, two-cell toggles and track+knob sliders — matching the reference
 * art. Every method draws backgrounds through a {@link ShapeRenderer} already begun in {@code Filled}
 * mode; labels are drawn separately by the caller through a {@code SpriteBatch} (same split as the rest
 * of the UI). Hit-testing is left to the caller (plain rectangles).
 */
public final class BedrockWidgets {

    private BedrockWidgets() {}

    // ---- palette (sampled from the Bedrock Preview reference) ----
    public static final Color PANEL       = rgb(0xC6, 0xC6, 0xC6); // light-grey inventory panel
    public static final Color PANEL_INNER = rgb(0x8B, 0x8B, 0x8B); // darker recessed area
    public static final Color PANEL_EDGE  = rgb(0x20, 0x20, 0x20);
    public static final Color SLOT        = rgb(0x8B, 0x8B, 0x8B);
    public static final Color SLOT_HOVER  = rgb(0xAE, 0xAE, 0xAE);
    public static final Color SLOT_SHADOW = rgb(0x37, 0x37, 0x37); // top/left (recessed)
    public static final Color SLOT_LIGHT  = rgb(0xFF, 0xFF, 0xFF); // bottom/right
    public static final Color BTN         = rgb(0x53, 0x55, 0x57); // dark menu button
    public static final Color BTN_HOVER   = rgb(0x6C, 0x6F, 0x73);
    public static final Color BTN_EDGE    = rgb(0x10, 0x10, 0x10);
    public static final Color BTN_BEVEL   = rgb(0x97, 0x99, 0x9B);
    public static final Color GREEN       = rgb(0x7C, 0xB3, 0x42); // toggle-on / selected accent
    public static final Color GREY        = rgb(0x84, 0x84, 0x84);
    public static final Color TRACK       = rgb(0xCE, 0xCE, 0xCE);
    public static final Color TRACK_FILL  = rgb(0x33, 0x33, 0x33);
    public static final Color TEXT_LIGHT  = rgb(0xF2, 0xF2, 0xF2);
    public static final Color TEXT_DARK   = rgb(0x20, 0x20, 0x20);

    public enum BtnState { NORMAL, HOVER, SELECTED }

    static Color rgb(int r, int g, int b) { return new Color(r / 255f, g / 255f, b / 255f, 1f); }

    // ---- low-level (shapes already begun Filled) ----
    public static void rect(ShapeRenderer s, float x, float y, float w, float h, Color c) {
        s.setColor(c); s.rect(x, y, w, h);
    }

    /** Four-sided border of thickness {@code t} drawn inside the rect. */
    public static void border(ShapeRenderer s, float x, float y, float w, float h, float t, Color c) {
        rect(s, x, y, w, t, c);
        rect(s, x, y + h - t, w, t, c);
        rect(s, x, y, t, h, c);
        rect(s, x + w - t, y, t, h, c);
    }

    public static void panel(ShapeRenderer s, float x, float y, float w, float h) {
        rect(s, x, y, w, h, PANEL);
        border(s, x, y, w, h, 3f, PANEL_EDGE);
    }

    /** Inset slot: recessed shadow on the top+left, bright on the bottom+right. */
    public static void slot(ShapeRenderer s, float x, float y, float sz, boolean hovered) {
        rect(s, x, y, sz, sz, hovered ? SLOT_HOVER : SLOT);
        float b = 3f;
        rect(s, x, y, sz, b, SLOT_LIGHT);            // bottom light
        rect(s, x + sz - b, y, b, sz, SLOT_LIGHT);   // right light
        rect(s, x, y + sz - b, sz, b, SLOT_SHADOW);  // top dark
        rect(s, x, y, b, sz, SLOT_SHADOW);           // left dark
    }

    /** Flat button: grey fill, light bottom bevel, thick border (green when selected). */
    public static void button(ShapeRenderer s, float x, float y, float w, float h, BtnState st) {
        rect(s, x, y, w, h, st == BtnState.HOVER ? BTN_HOVER : BTN);
        rect(s, x + 3f, y + 3f, w - 6f, 2f, BTN_BEVEL); // bottom bevel highlight
        border(s, x, y, w, h, 3f, st == BtnState.SELECTED ? GREEN : BTN_EDGE);
    }

    /** Two-cell switch: ON = green "I" left + light knob right; OFF = light knob left + grey "o" right. */
    public static void toggle(ShapeRenderer s, float x, float y, float w, float h, boolean on) {
        rect(s, x, y, w, h, BTN_EDGE); // border base
        float b = 3f, cw = (w - 3 * b) * 0.5f;
        float lx = x + b, rx = x + b + cw + b, iy = y + b, ih = h - 2 * b;
        rect(s, lx, iy, cw, ih, on ? GREEN : SLOT_LIGHT);
        rect(s, rx, iy, cw, ih, on ? SLOT_LIGHT : GREY);
    }

    /** Track + knob slider. value 0..1. Returns the knob's left x so the caller can hit-test it. */
    public static float slider(ShapeRenderer s, float x, float y, float w, float h, float value) {
        float th = h * 0.45f, ty = y + (h - th) * 0.5f;
        rect(s, x, ty, w, th, BTN_EDGE);
        rect(s, x + 2f, ty + 2f, w - 4f, th - 4f, TRACK);
        float kx = x + value * (w - h);
        rect(s, x + 2f, ty + 2f, Math.max(0f, kx - x - 2f), th - 4f, TRACK_FILL); // filled portion
        rect(s, kx, y, h, h, BTN_EDGE);                 // knob border
        rect(s, kx + 2f, y + 2f, h - 4f, h - 4f, SLOT_LIGHT);
        return kx;
    }
}
