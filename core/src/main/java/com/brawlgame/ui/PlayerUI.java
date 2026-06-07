package com.brawlgame.ui;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.utils.Disposable;
import com.brawlgame.item.Inventory;
import com.brawlgame.item.ItemStack;
import com.brawlgame.item.ItemType;

/**
 * The in-game player UI and its input router, rendered in a strict Minecraft <b>Bedrock</b> style with
 * custom geometry (no default skin):
 * <ul>
 *   <li>Panel bg {@code #313233}, 2px {@code #5C5C5C} border.</li>
 *   <li>Slots are perfect squares ({@value #SLOT}px) on {@code #232323}, with an inset bevel — top/left
 *       edges {@code #111111}, bottom/right {@code #7A7A7A} — and {@value #PAD}px gaps between them.</li>
 *   <li>The selected hotbar slot gets a thick bright-white border.</li>
 *   <li>Icons are centred with an inner margin so they never touch the bevel.</li>
 * </ul>
 *
 * <p>It implements {@link InputProcessor} so, placed first in the screen's {@code InputMultiplexer}, it
 * receives clicks/keys before the world and consumes them while a panel is open. Keys 1–9 do nothing
 * but set the hotbar selection; the active weapon is derived from {@link #selectedItem()} by the
 * player, not from any hardcoded key.
 */
public final class PlayerUI implements InputProcessor, Disposable {

    private enum Mode { NONE, INVENTORY, CREATIVE, CHEST }

    private static final int STORE_INV = 0, STORE_CHEST = 1, STORE_CREATIVE = 2;

    // ---- Bedrock palette (exact hex) ----
    private static final Color PANEL_BG     = rgb(0x31, 0x32, 0x33);
    private static final Color PANEL_BORDER = rgb(0x5C, 0x5C, 0x5C);
    private static final Color SLOT_BG      = rgb(0x23, 0x23, 0x23);
    private static final Color BEVEL_DARK   = rgb(0x11, 0x11, 0x11);
    private static final Color BEVEL_LIGHT  = rgb(0x7A, 0x7A, 0x7A);
    private static final Color SLOT_HOVER   = rgb(0x3A, 0x3A, 0x3B);
    private static final Color SEL          = Color.WHITE;
    private static final Color SCRIM        = new Color(0f, 0f, 0f, 0.6f);
    private static final Color TEXT         = rgb(0xEE, 0xEE, 0xF0);
    private static final Color PORTRAIT_BG  = rgb(0x1A, 0x1A, 0x1A);

    // ---- dimensions (px) ----
    private static final float SLOT = 50f;   // perfect square
    private static final float PAD  = 6f;    // gap between slots
    private static final float BORDER = 2f, BEVEL = 2f, SEL_W = 3f;
    private static final float HB_SLOT = 58f, HB_PAD = 6f, HB_BOTTOM = 14f;

    // ---- creative tabs (whitelist only) ----
    private static final ItemType[] COMBAT = {
        ItemType.WOOD_SWORD, ItemType.STONE_SWORD, ItemType.IRON_SWORD,
        ItemType.GOLD_SWORD, ItemType.DIAMOND_SWORD, ItemType.POTATO_GUN,
    };
    private static final ItemType[] ARMOR = {
        ItemType.LEATHER_HELMET, ItemType.LEATHER_CHESTPLATE, ItemType.LEATHER_LEGGINGS, ItemType.LEATHER_BOOTS,
        ItemType.IRON_HELMET, ItemType.IRON_CHESTPLATE, ItemType.IRON_LEGGINGS, ItemType.IRON_BOOTS,
        ItemType.DIAMOND_HELMET, ItemType.DIAMOND_CHESTPLATE, ItemType.DIAMOND_LEGGINGS, ItemType.DIAMOND_BOOTS,
    };
    private static final String[] TAB_LABELS = {"Combat", "Armor"};

    private static final class Slot {
        final float x, y, size;
        final int store, index;
        Slot(float x, float y, float size, int store, int index) {
            this.x = x; this.y = y; this.size = size; this.store = store; this.index = index;
        }
        boolean hit(float mx, float my) { return mx >= x && mx <= x + size && my >= y && my <= y + size; }
    }

    private final Inventory inv;

    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();
    private final ItemIcons icons = new ItemIcons();

    private Mode mode = Mode.NONE;
    private boolean creativeMode;
    private int creativeTab;
    private int selectedHotbar;

    private ItemStack carried;
    private ItemStack[] chestSlots;
    private String chestTitle = "Chest";

    // ---- health bar (hearts) ----
    private static final Color HEART_EMPTY = new Color(0.16f, 0.10f, 0.10f, 0.85f);
    private final Texture heartTex;
    private final TextureRegion heartFull, heartHalf;
    private float health = 20f, maxHealth = 20f;

    public PlayerUI(Inventory inv) {
        this.inv = inv;
        heartTex = new Texture(Gdx.files.internal("textures/fx/heart.png"));
        heartTex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        heartFull = new TextureRegion(heartTex);
        heartHalf = new TextureRegion(heartTex, 0, 0, heartTex.getWidth() / 2, heartTex.getHeight());
    }

    /** Feed the player's current/max health each frame so the hotbar can draw the hearts row. */
    public void setHealth(float current, float max) {
        this.health = current;
        this.maxHealth = max;
    }

    private static Color rgb(int r, int g, int b) { return new Color(r / 255f, g / 255f, b / 255f, 1f); }

    // ---------------------------------------------------------------- public state

    public boolean isModalOpen() { return mode != Mode.NONE; }
    public boolean isCreative()  { return creativeMode; }
    public int selectedHotbar()  { return selectedHotbar; }
    public Inventory inventory() { return inv; }

    /** The item in the selected hotbar slot (drives the active weapon), or null if empty. */
    public ItemType selectedItem() {
        ItemStack s = inv.hotbar(selectedHotbar);
        return s == null ? null : s.type;
    }

    public void openChest(ItemStack[] slots, String title) {
        this.chestSlots = slots;
        this.chestTitle = title != null ? title : "Chest";
        mode = Mode.CHEST;
    }

    public void closeModal() {
        if (carried != null) inv.add(carried);
        carried = null;
        chestSlots = null;
        mode = Mode.NONE;
    }

    private ItemType[] tabItems() { return creativeTab == 0 ? COMBAT : ARMOR; }

    // ---------------------------------------------------------------- InputProcessor (routing)

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.SLASH) { creativeMode = !creativeMode; return true; }
        if (keycode == Input.Keys.E) {
            if (mode == Mode.NONE) mode = creativeMode ? Mode.CREATIVE : Mode.INVENTORY;
            else closeModal();
            return true;
        }
        if (keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_9) { // strictly hotbar selection
            selectedHotbar = keycode - Input.Keys.NUM_1;
            return true;
        }
        return false; // Esc, WASD, etc. fall through to the screen/world.
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (mode == Mode.NONE) return false;
        if (button == Input.Buttons.LEFT) handleClick(screenX, screenY);
        return true; // consume so the player can't shoot/move through the menu
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (mode != Mode.NONE) return true;
        int n = Inventory.HOTBAR;
        selectedHotbar = ((selectedHotbar + (amountY > 0 ? 1 : -1)) % n + n) % n;
        return true;
    }

    @Override public boolean keyUp(int keycode) { return false; }
    @Override public boolean keyTyped(char character) { return mode != Mode.NONE; }
    @Override public boolean touchUp(int x, int y, int p, int b) { return mode != Mode.NONE; }
    @Override public boolean touchDragged(int x, int y, int p) { return mode != Mode.NONE; }
    @Override public boolean touchCancelled(int x, int y, int p, int b) { return false; }
    @Override public boolean mouseMoved(int x, int y) { return false; }

    // ---------------------------------------------------------------- click → move items / tabs

    private void handleClick(int screenX, int screenY) {
        float mx = screenX, my = Gdx.graphics.getHeight() - screenY;

        if (mode == Mode.CREATIVE) {
            float[] tr = tabRects();
            for (int i = 0; i < TAB_LABELS.length; i++) {
                if (mx >= tr[i * 4] && mx <= tr[i * 4] + tr[i * 4 + 2]
                        && my >= tr[i * 4 + 1] && my <= tr[i * 4 + 1] + tr[i * 4 + 3]) {
                    creativeTab = i; carried = null; return;
                }
            }
        }

        Slot s = null;
        for (Slot cand : buildSlots()) if (cand.hit(mx, my)) { s = cand; break; }
        if (s == null) { carried = null; return; }
        if (s.store == STORE_CREATIVE) { carried = new ItemStack(tabItems()[s.index], 1); return; }

        ItemStack target = read(s);
        if (carried == null) {
            if (target != null) { carried = target; write(s, null); }
            return;
        }
        if (isArmorSlot(s) && carried.type.armorSlot() != s.index - Inventory.ARMOR_BASE) return;
        if (target == null) {
            write(s, carried); carried = null;
        } else if (target.stacksWith(carried)) {
            int move = Math.min(ItemStack.MAX - target.count, carried.count);
            target.count += move; carried.count -= move;
            if (carried.count <= 0) carried = null;
        } else {
            write(s, carried); carried = target;
        }
    }

    private boolean isArmorSlot(Slot s) { return s.store == STORE_INV && inv.isArmorSlot(s.index); }

    private ItemStack read(Slot s) {
        switch (s.store) {
            case STORE_CHEST: return chestSlots == null ? null : chestSlots[s.index];
            case STORE_INV:   return inv.get(s.index);
            default:          return null;
        }
    }

    private void write(Slot s, ItemStack stack) {
        ItemStack v = (stack != null && stack.isEmpty()) ? null : stack;
        if (s.store == STORE_CHEST) { if (chestSlots != null) chestSlots[s.index] = v; }
        else if (s.store == STORE_INV) inv.set(s.index, v);
    }

    // ---------------------------------------------------------------- layout

    private List<Slot> buildSlots() {
        List<Slot> out = new ArrayList<>();
        if (mode == Mode.NONE) return out;
        float[] P = panelRect();
        float px = P[0], py = P[1], pw = P[2], ph = P[3];

        if (mode == Mode.CREATIVE) {
            ItemType[] items = tabItems();
            float gx = px + 20f + tabColW() + 14f;
            float gyTop = py + ph - 56f;
            int cols = Math.max(1, (int) ((px + pw - 16f - gx) / (SLOT + PAD)));
            for (int i = 0; i < items.length; i++) {
                int c = i % cols, r = i / cols;
                out.add(new Slot(gx + c * (SLOT + PAD), gyTop - SLOT - r * (SLOT + PAD), SLOT, STORE_CREATIVE, i));
            }
            // Player hotbar row along the bottom, so picked items can be dragged straight onto it.
            addRow(out, gx, py + 18f, Inventory.HOTBAR, STORE_INV, Inventory.HOTBAR_BASE);
            return out;
        }

        // INVENTORY / CHEST: bottom block = 18 storage (2×9) + hotbar row.
        float blockW = 9 * SLOT + 8 * PAD;
        float bx = px + (pw - blockW) * 0.5f;
        float hbY = py + 22f;
        addRow(out, bx, hbY, Inventory.HOTBAR, STORE_INV, Inventory.HOTBAR_BASE);
        float store0Y = hbY + SLOT + 22f;
        addRow(out, bx, store0Y,              9, STORE_INV, Inventory.STORAGE_BASE + 9); // bottom store row
        addRow(out, bx, store0Y + SLOT + PAD, 9, STORE_INV, Inventory.STORAGE_BASE);     // top store row

        if (mode == Mode.CHEST && chestSlots != null) {
            float cTop = py + ph - 52f;
            for (int i = 0; i < chestSlots.length; i++) {
                int c = i % 9, r = i / 9;
                out.add(new Slot(bx + c * (SLOT + PAD), cTop - SLOT - r * (SLOT + PAD), SLOT, STORE_CHEST, i));
            }
        } else if (mode == Mode.INVENTORY) {
            float armX = px + 24f, armTop = py + ph - 58f;
            for (int i = 0; i < Inventory.ARMOR; i++) {
                out.add(new Slot(armX, armTop - i * (SLOT + PAD), SLOT, STORE_INV, Inventory.ARMOR_BASE + i));
            }
        }
        return out;
    }

    private void addRow(List<Slot> out, float x, float y, int n, int store, int baseIndex) {
        for (int i = 0; i < n; i++) out.add(new Slot(x + i * (SLOT + PAD), y, SLOT, store, baseIndex + i));
    }

    private float tabColW() { return SLOT; }

    /** Creative tab rects flattened {x,y,w,h} per tab. */
    private float[] tabRects() {
        float[] P = panelRect();
        float x = P[0] + 20f, top = P[1] + P[3] - 56f;
        float[] r = new float[TAB_LABELS.length * 4];
        for (int i = 0; i < TAB_LABELS.length; i++) {
            r[i * 4] = x; r[i * 4 + 1] = top - SLOT - i * (SLOT + PAD); r[i * 4 + 2] = tabColW(); r[i * 4 + 3] = SLOT;
        }
        return r;
    }

    private float[] panelRect() {
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        if (mode == Mode.CREATIVE) {
            float pw = Math.min(w * 0.84f, 1040f), ph = Math.min(h * 0.82f, 660f);
            return new float[] {(w - pw) * 0.5f, (h - ph) * 0.5f, pw, ph};
        }
        float pw = Math.min(w * 0.62f, 660f), ph = Math.min(h * 0.8f, 580f);
        return new float[] {(w - pw) * 0.5f, (h - ph) * 0.5f, pw, ph};
    }

    // ---------------------------------------------------------------- render

    public void render() {
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        float mx = Gdx.input.getX(), my = h - Gdx.input.getY();
        shapes.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        batch.getProjectionMatrix().setToOrtho2D(0, 0, w, h);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        if (mode == Mode.NONE) renderHotbar(w);
        else renderModal(w, h, mx, my);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void renderHotbar(float w) {
        float barW = Inventory.HOTBAR * HB_SLOT + (Inventory.HOTBAR - 1) * HB_PAD;
        float x0 = (w - barW) * 0.5f, y = HB_BOTTOM;

        shapes.begin(ShapeType.Filled);
        for (int i = 0; i < Inventory.HOTBAR; i++) {
            float x = x0 + i * (HB_SLOT + HB_PAD);
            drawSlot(x, y, HB_SLOT, false);
            if (i == selectedHotbar) drawSelected(x, y, HB_SLOT);
        }
        shapes.end();

        batch.begin();
        for (int i = 0; i < Inventory.HOTBAR; i++) {
            drawStack(inv.hotbar(i), x0 + i * (HB_SLOT + HB_PAD), y, HB_SLOT);
        }
        drawHearts(x0, y + HB_SLOT + 12f, barW);
        batch.setColor(Color.WHITE);
        batch.end();
    }

    /** Minecraft-style row of hearts above the hotbar (each heart = 2 HP; half hearts for odd points). */
    private void drawHearts(float x0, float y, float barW) {
        int hearts = Math.max(1, Math.round(maxHealth / 2f));
        float size = 26f, gap = 2f;
        float totalW = hearts * size + (hearts - 1) * gap;
        float left = x0 + (barW - totalW) * 0.5f; // centred over the hotbar
        // Empty heart sockets first (dark), then the filled red hearts on top.
        batch.setColor(HEART_EMPTY);
        for (int i = 0; i < hearts; i++) batch.draw(heartFull, left + i * (size + gap), y, size, size);
        batch.setColor(Color.WHITE);
        for (int i = 0; i < hearts; i++) {
            float covered = health - i * 2f;
            float x = left + i * (size + gap);
            if (covered >= 2f) batch.draw(heartFull, x, y, size, size);
            else if (covered >= 1f) batch.draw(heartHalf, x, y, size * 0.5f, size);
        }
    }

    private void renderModal(float w, float h, float mx, float my) {
        List<Slot> slots = buildSlots();
        float[] P = panelRect();
        float px = P[0], py = P[1], pw = P[2], ph = P[3];

        shapes.begin(ShapeType.Filled);
        fill(0, 0, w, h, SCRIM);
        drawPanel(px, py, pw, ph);

        if (mode == Mode.INVENTORY) {
            // Portrait box to the right of the armour column.
            fill(px + 24f + SLOT + 12f, py + ph - 58f - 3 * (SLOT + PAD), SLOT * 2.3f, SLOT * 3 + 2 * PAD, PORTRAIT_BG);
        }
        if (mode == Mode.CREATIVE) {
            float[] tr = tabRects();
            for (int i = 0; i < TAB_LABELS.length; i++) {
                drawSlot(tr[i * 4], tr[i * 4 + 1], tr[i * 4 + 2], false);
                if (i == creativeTab) drawSelected(tr[i * 4], tr[i * 4 + 1], tr[i * 4 + 2]);
            }
        }
        for (Slot s : slots) {
            drawSlot(s.x, s.y, s.size, s.hit(mx, my));
            if (s.store == STORE_INV && s.index == Inventory.HOTBAR_BASE + selectedHotbar) drawSelected(s.x, s.y, s.size);
        }
        shapes.end();

        batch.begin();
        font.setColor(TEXT);
        title(P);
        if (mode == Mode.CREATIVE) {
            for (int i = 0; i < TAB_LABELS.length; i++) {
                float[] tr = tabRects();
                layout.setText(font, TAB_LABELS[i]);
                font.draw(batch, layout, tr[i * 4] + (tr[i * 4 + 2] - layout.width) * 0.5f,
                    tr[i * 4 + 1] + (SLOT + layout.height) * 0.5f);
            }
        }
        for (Slot s : slots) {
            ItemStack st = s.store == STORE_CREATIVE ? new ItemStack(tabItems()[s.index], 1) : read(s);
            drawStack(st, s.x, s.y, s.size);
            if (st == null && isArmorSlot(s)) armorHint(s);
        }
        if (carried != null) drawStack(carried, mx - SLOT * 0.5f, my - SLOT * 0.5f, SLOT);
        batch.end();
    }

    private void title(float[] P) {
        float tx = P[0] + 22f, ty = P[1] + P[3] - 20f;
        if (mode == Mode.CREATIVE)   font.draw(batch, "ITEMS   (E / Esc to close)", tx, ty);
        else if (mode == Mode.CHEST) { font.draw(batch, chestTitle, tx, ty);
                                       font.draw(batch, "Inventory", tx, P[1] + 22f + 3 * (SLOT + PAD) + 24f); }
        else                         font.draw(batch, "INVENTORY   (E / Esc to close)", tx, ty);
    }

    // ---------------------------------------------------------------- Bedrock primitives

    private void fill(float x, float y, float w, float h, Color c) { shapes.setColor(c); shapes.rect(x, y, w, h); }

    private void drawPanel(float x, float y, float w, float h) {
        fill(x, y, w, h, PANEL_BG);
        fill(x, y, w, BORDER, PANEL_BORDER);
        fill(x, y + h - BORDER, w, BORDER, PANEL_BORDER);
        fill(x, y, BORDER, h, PANEL_BORDER);
        fill(x + w - BORDER, y, BORDER, h, PANEL_BORDER);
    }

    /** A beveled Bedrock slot: dark top/left, light bottom/right (inset look). */
    private void drawSlot(float x, float y, float s, boolean hovered) {
        fill(x, y, s, s, hovered ? SLOT_HOVER : SLOT_BG);
        fill(x, y, s, BEVEL, BEVEL_LIGHT);          // bottom (light)
        fill(x + s - BEVEL, y, BEVEL, s, BEVEL_LIGHT); // right (light)
        fill(x, y + s - BEVEL, s, BEVEL, BEVEL_DARK);  // top (dark)
        fill(x, y, BEVEL, s, BEVEL_DARK);              // left (dark)
    }

    private void drawSelected(float x, float y, float s) {
        fill(x - 1f, y - 1f, s + 2f, SEL_W, SEL);
        fill(x - 1f, y + s + 1f - SEL_W, s + 2f, SEL_W, SEL);
        fill(x - 1f, y - 1f, SEL_W, s + 2f, SEL);
        fill(x + s + 1f - SEL_W, y - 1f, SEL_W, s + 2f, SEL);
    }

    private void armorHint(Slot s) {
        String t = new String[] {"H", "C", "L", "B"}[s.index - Inventory.ARMOR_BASE];
        font.setColor(0.5f, 0.5f, 0.52f, 1f);
        layout.setText(font, t);
        font.draw(batch, layout, s.x + (s.size - layout.width) * 0.5f, s.y + (s.size + layout.height) * 0.5f);
        font.setColor(TEXT);
    }

    private void drawStack(ItemStack stack, float x, float y, float size) {
        if (stack == null) return;
        Texture icon = icons.get(stack.type);
        float pad = size * 0.16f; // centred, never touching the bevel
        batch.draw(icon, x + pad, y + pad, size - 2 * pad, size - 2 * pad);
        if (stack.count > 1) {
            font.setColor(Color.WHITE);
            layout.setText(font, Integer.toString(stack.count));
            font.draw(batch, layout, x + size - layout.width - 5f, y + layout.height + 4f);
        }
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        font.dispose();
        icons.dispose();
        heartTex.dispose();
    }
}
