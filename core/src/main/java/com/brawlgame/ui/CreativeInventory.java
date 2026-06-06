package com.brawlgame.ui;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import com.brawlgame.map.BlockCategory;
import com.brawlgame.map.BlockLibrary;
import com.brawlgame.map.BlockType;
import com.brawlgame.map.Theme;

/**
 * A Minecraft Bedrock-style creative inventory overlay for the Map Maker. A dim scrim covers the
 * viewport; a flat dark-grey {@link BedrockUi} panel holds vertical category tabs on the left
 * (Construction Blocks, Equipment / Spawns, Nature / Bushes) and a scrollable grid of block items on
 * the right. Picking an item returns its {@link BlockType} so the screen can bind it to the active
 * hotbar slot. Toggle it with the inventory button or the {@code E} key.
 *
 * <p>Owns its own renderers and is driven by {@link #update()} (input → optional pick) and
 * {@link #render()} (draw) each frame while {@link #isOpen() open}.
 */
public final class CreativeInventory implements Disposable {

    /** The three left-hand tabs and the block categories each surfaces. */
    private enum Tab {
        CONSTRUCTION("Construction", BlockCategory.SOLID, BlockCategory.FENCE),
        EQUIPMENT("Equipment / Spawns", BlockCategory.CHEST, BlockCategory.SPAWN, BlockCategory.ERASER),
        NATURE("Nature / Bushes", BlockCategory.BUSH, BlockCategory.WATER);

        final String label;
        final BlockCategory[] cats;
        Tab(String label, BlockCategory... cats) { this.label = label; this.cats = cats; }
    }

    private static final float CELL = 66f;   // item cell size, px
    private static final float CELL_GAP = 8f;
    private static final float TAB_W = 190f;

    private final BlockLibrary lib;
    private final List<List<BlockType>> itemsByTab = new ArrayList<>();

    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();

    private boolean open;
    private int tab;
    private float scroll; // px scrolled down within the active tab's grid

    public CreativeInventory(Theme theme, BlockLibrary lib) {
        this.lib = lib;
        // Bucket the theme's palette into tabs (skipping nothing — the eraser tool lives under Equipment).
        for (Tab t : Tab.values()) {
            List<BlockType> list = new ArrayList<>();
            for (BlockType bt : theme.palette()) {
                if (bt == null) continue;
                for (BlockCategory c : t.cats) {
                    if (bt.category() == c) { list.add(bt); break; }
                }
            }
            itemsByTab.add(list);
        }
    }

    public boolean isOpen() { return open; }
    public void toggle()    { open = !open; scroll = 0f; }
    public void close()     { open = false; }

    public void scroll(int amount) {
        if (open) scroll = Math.max(0f, scroll + amount * (CELL + CELL_GAP) * 0.6f);
    }

    /**
     * Processes one frame of input. Returns the picked {@link BlockType} if the user clicked an item
     * this frame (the screen binds it to the active hotbar slot), otherwise {@code null}. Also handles
     * tab switching. Always "consumes" clicks while open so they don't leak to the world.
     */
    public BlockType update() {
        if (!open) return null;
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        float mx = Gdx.input.getX();
        float my = h - Gdx.input.getY(); // to y-up
        boolean click = Gdx.input.justTouched();

        float[] L = layout(w, h);
        float px = L[0], py = L[1], pw = L[2], ph = L[3];

        if (click) {
            // Tab hit-testing (vertical stack on the left).
            float tabH = 54f, ty = py + ph - 16f - tabH;
            for (int i = 0; i < Tab.values().length; i++) {
                if (inside(mx, my, px + 12f, ty, TAB_W - 24f, tabH)) {
                    if (tab != i) { tab = i; scroll = 0f; }
                    return null;
                }
                ty -= tabH + 8f;
            }
        }

        // Item grid hit-testing.
        float gridX = px + TAB_W, gridY = py + 16f;
        float gridW = pw - TAB_W - 16f, gridH = ph - 64f;
        int cols = Math.max(1, (int) (gridW / (CELL + CELL_GAP)));
        List<BlockType> items = itemsByTab.get(tab);
        clampScroll(items.size(), cols, gridH);

        if (click) {
            for (int i = 0; i < items.size(); i++) {
                int col = i % cols, row = i / cols;
                float ix = gridX + col * (CELL + CELL_GAP);
                float iy = gridY + gridH - CELL - row * (CELL + CELL_GAP) + scroll;
                if (iy < gridY - CELL || iy > gridY + gridH) continue; // off-view
                if (inside(mx, my, ix, iy, CELL, CELL)) return items.get(i);
            }
        }
        return null;
    }

    public void render() {
        if (!open) return;
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        float mx = Gdx.input.getX(), my = h - Gdx.input.getY();
        float[] L = layout(w, h);
        float px = L[0], py = L[1], pw = L[2], ph = L[3];

        shapes.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        batch.getProjectionMatrix().setToOrtho2D(0, 0, w, h);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeType.Filled);
        // Scrim + main panel.
        shapes.setColor(BedrockUi.SCRIM);
        shapes.rect(0, 0, w, h);
        BedrockUi.panel(shapes, px, py, pw, ph, BedrockUi.PANEL);

        // Tabs.
        float tabH = 54f, ty = py + ph - 16f - tabH;
        for (int i = 0; i < Tab.values().length; i++) {
            boolean hov = inside(mx, my, px + 12f, ty, TAB_W - 24f, tabH);
            BedrockUi.button(shapes, px + 12f, ty, TAB_W - 24f, tabH, hov, i == tab, true);
            ty -= tabH + 8f;
        }

        // Item slots.
        float gridX = px + TAB_W, gridY = py + 16f;
        float gridW = pw - TAB_W - 16f, gridH = ph - 64f;
        int cols = Math.max(1, (int) (gridW / (CELL + CELL_GAP)));
        List<BlockType> items = itemsByTab.get(tab);
        clampScroll(items.size(), cols, gridH);
        for (int i = 0; i < items.size(); i++) {
            int col = i % cols, row = i / cols;
            float ix = gridX + col * (CELL + CELL_GAP);
            float iy = gridY + gridH - CELL - row * (CELL + CELL_GAP) + scroll;
            if (iy < gridY - CELL || iy > gridY + gridH) continue;
            boolean hov = inside(mx, my, ix, iy, CELL, CELL);
            BedrockUi.button(shapes, ix, iy, CELL, CELL, hov, false, true);
        }
        shapes.end();

        // Text + icons.
        batch.begin();
        font.setColor(BedrockUi.TEXT);
        font.draw(batch, "CREATIVE INVENTORY   (E / Esc to close)", gridX, py + ph - 22f);
        ty = py + ph - 16f - tabH;
        for (int i = 0; i < Tab.values().length; i++) {
            layout.setText(font, Tab.values()[i].label);
            font.draw(batch, layout, px + 12f + (TAB_W - 24f - layout.width) * 0.5f, ty + tabH * 0.5f + layout.height * 0.5f);
            ty -= tabH + 8f;
        }
        for (int i = 0; i < items.size(); i++) {
            int col = i % cols, row = i / cols;
            float ix = gridX + col * (CELL + CELL_GAP);
            float iy = gridY + gridH - CELL - row * (CELL + CELL_GAP) + scroll;
            if (iy < gridY - CELL || iy > gridY + gridH) continue;
            BlockType bt = items.get(i);
            Texture icon = lib.icon(bt);
            float pad = 8f;
            if (icon != null) {
                batch.draw(icon, ix + pad, iy + pad, CELL - 2f * pad, CELL - 2f * pad);
            } else {
                layout.setText(font, bt.displayName());
                font.draw(batch, bt == BlockType.ERASER ? "ERASE" : "?",
                    ix + (CELL - layout.width) * 0.5f, iy + CELL * 0.5f + layout.height * 0.5f);
            }
        }
        batch.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /** Panel rect {x,y,w,h}: a centred box covering most of the viewport. */
    private float[] layout(float w, float h) {
        float pw = Math.min(w * 0.72f, 920f);
        float ph = Math.min(h * 0.78f, 660f);
        float px = (w - pw) * 0.5f, py = (h - ph) * 0.5f;
        return new float[] {px, py, pw, ph};
    }

    private void clampScroll(int count, int cols, float gridH) {
        int rows = (count + cols - 1) / cols;
        float contentH = rows * (CELL + CELL_GAP);
        float maxScroll = Math.max(0f, contentH - gridH);
        scroll = MathUtils.clamp(scroll, 0f, maxScroll);
    }

    private static boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        font.dispose();
    }
}
