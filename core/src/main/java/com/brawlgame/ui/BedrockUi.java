package com.brawlgame.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * Shared visual language for the Map Maker's Minecraft Bedrock-style UI: flat dark-grey panels with
 * crisp thin light borders — no gradients, no bevels, no rounded corners. All chrome (side buttons,
 * the hotbar, the creative inventory) draws through these helpers so the look stays consistent.
 *
 * <p>Helpers assume a {@link ShapeRenderer} already begun in {@link ShapeRenderer.ShapeType#Filled}
 * mode with alpha blending enabled; borders are drawn as four thin filled rects (cheaper and crisper
 * than line mode at any DPI). Text + icons are layered on afterwards by the caller's SpriteBatch.
 */
public final class BedrockUi {

    /** Panel fill — rgba(58,58,58,0.95), the Bedrock menu grey. */
    public static final Color PANEL      = new Color(0.227f, 0.227f, 0.227f, 0.95f);
    /** Slightly lighter fill for an interactive slot/button at rest (#4A4A4A). */
    public static final Color SLOT       = new Color(0.29f, 0.29f, 0.29f, 1f);
    /** Hover fill for buttons. */
    public static final Color SLOT_HOVER = new Color(0.40f, 0.40f, 0.40f, 0.98f);
    /** A disabled/inactive control. */
    public static final Color SLOT_OFF   = new Color(0.20f, 0.20f, 0.20f, 0.85f);
    /** Thin border — soft light grey. */
    public static final Color BORDER     = new Color(0.78f, 0.78f, 0.80f, 1f);
    /** Bright border for the active/selected element. */
    public static final Color BORDER_SEL = new Color(1f, 1f, 1f, 1f);
    /** Dim backdrop behind the full-screen inventory overlay. */
    public static final Color SCRIM      = new Color(0f, 0f, 0f, 0.55f);
    public static final Color TEXT       = new Color(0.94f, 0.94f, 0.96f, 1f);

    /** Border thickness in px. */
    public static final float BW = 2f;

    private BedrockUi() {}

    /** Flat panel: a fill plus a thin border, in {@code fill}'s colour and {@link #BORDER}. */
    public static void panel(ShapeRenderer s, float x, float y, float w, float h, Color fill) {
        panel(s, x, y, w, h, fill, BORDER, BW);
    }

    /** Flat panel with an explicit border colour + thickness (0 thickness = no border). */
    public static void panel(ShapeRenderer s, float x, float y, float w, float h,
                             Color fill, Color border, float bw) {
        s.setColor(fill);
        s.rect(x, y, w, h);
        if (bw <= 0f) return;
        s.setColor(border);
        s.rect(x, y, w, bw);                 // bottom
        s.rect(x, y + h - bw, w, bw);        // top
        s.rect(x, y, bw, h);                 // left
        s.rect(x + w - bw, y, bw, h);        // right
    }

    /** A square/rect button: hover-aware fill, selected/active gets the bright border. */
    public static void button(ShapeRenderer s, float x, float y, float w, float h,
                              boolean hovered, boolean active, boolean enabled) {
        Color fill = !enabled ? SLOT_OFF : (hovered ? SLOT_HOVER : SLOT);
        Color border = active ? BORDER_SEL : BORDER;
        panel(s, x, y, w, h, fill, border, active ? BW + 1f : BW);
    }
}
