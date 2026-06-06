package com.brawlgame.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.utils.Disposable;

/**
 * A standard Minecraft-style 9-slot hotbar pinned to the bottom-centre of the screen. Reusable: the
 * editor and real players both feed it icons + labels. Each slot is a dark translucent rounded
 * square; the selected slot gains a bright white outline and its label is drawn just above the bar.
 *
 * <p>Owns its own {@link SpriteBatch}, {@link ShapeRenderer} and {@link BitmapFont}, and drives them
 * in a pixel-space ortho2D projection sized to the backbuffer each {@link #render()} so resizes are
 * handled for free. Caller is responsible for {@link #dispose()}.
 */
public final class Hotbar implements Disposable {

    private static final float SLOT_SIZE = 64f; // outer slot square, px
    private static final float GAP       = 6f;  // gap between slots, px
    private static final float BOTTOM     = 16f; // px above the screen bottom
    private static final float ICON_PAD   = 6f;  // inset of the icon inside its slot, px

    private final int slotCount;
    private final Texture[] icons;
    private final String[] labels;
    private int selected;

    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();

    public Hotbar(int slotCount) {
        this.slotCount = Math.max(1, slotCount);
        this.icons = new Texture[this.slotCount];
        this.labels = new String[this.slotCount];
    }

    /** Sets the icon + label for a slot; a null icon draws an empty (frame-only) slot. */
    public void setIcon(int slot, Texture icon, String label) {
        if (slot < 0 || slot >= slotCount) return;
        icons[slot] = icon;
        labels[slot] = label;
    }

    /** Selects a slot, clamped to [0, slotCount-1]. */
    public void setSelected(int slot) {
        selected = clamp(slot);
    }

    public int getSelected() {
        return selected;
    }

    /** Moves the selection by {@code amount}, wrapping around (libGDX mouse wheel up = -1). */
    public void scroll(int amount) {
        int n = slotCount;
        selected = ((selected + amount) % n + n) % n;
    }

    public void render() {
        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();

        float barWidth = slotCount * SLOT_SIZE + (slotCount - 1) * GAP;
        float startX = (w - barWidth) * 0.5f;
        float y = BOTTOM;

        shapes.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        batch.getProjectionMatrix().setToOrtho2D(0, 0, w, h);

        // --- Slot backgrounds: flat Bedrock item slots, selected slot gets the bright border. ---
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeType.Filled);
        for (int i = 0; i < slotCount; i++) {
            float x = startX + i * (SLOT_SIZE + GAP);
            BedrockUi.button(shapes, x, y, SLOT_SIZE, SLOT_SIZE, false, i == selected, true);
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // --- Icons. ---
        batch.begin();
        for (int i = 0; i < slotCount; i++) {
            Texture icon = icons[i];
            if (icon == null) continue;
            float x = startX + i * (SLOT_SIZE + GAP);
            batch.draw(icon,
                    x + ICON_PAD, y + ICON_PAD,
                    SLOT_SIZE - 2f * ICON_PAD, SLOT_SIZE - 2f * ICON_PAD);
        }

        // --- Selected label, centred just above the bar. ---
        String label = labels[selected];
        if (label != null && !label.isEmpty()) {
            font.setColor(Color.WHITE);
            layout.setText(font, label);
            float lx = (w - layout.width) * 0.5f;
            float ly = y + SLOT_SIZE + 8f + layout.height;
            font.draw(batch, layout, lx, ly);
        }
        batch.end();
    }

    private int clamp(int slot) {
        if (slot < 0) return 0;
        if (slot >= slotCount) return slotCount - 1;
        return slot;
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        font.dispose();
    }
}
