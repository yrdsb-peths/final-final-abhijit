package com.brawlgame.ui;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.brawlgame.entity.ArmorRenderer;
import com.brawlgame.item.Inventory;
import com.brawlgame.item.ItemStack;
import com.brawlgame.item.ItemType;
import com.brawlgame.model.MinecraftPlayerModel;

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

    /** The full 18-item whitelist shown in the creative palette (exactly fills a 2×9 grid). */
    private static final ItemType[] CREATIVE_ITEMS = {
        ItemType.WOOD_SWORD, ItemType.STONE_SWORD, ItemType.IRON_SWORD, ItemType.GOLD_SWORD,
        ItemType.DIAMOND_SWORD, ItemType.POTATO_GUN,
        ItemType.LEATHER_HELMET, ItemType.LEATHER_CHESTPLATE, ItemType.LEATHER_LEGGINGS,
        ItemType.IRON_HELMET, ItemType.IRON_CHESTPLATE, ItemType.IRON_LEGGINGS,
        ItemType.DIAMOND_HELMET, ItemType.DIAMOND_CHESTPLATE, ItemType.DIAMOND_LEGGINGS,
        ItemType.LEATHER_BOOTS, ItemType.IRON_BOOTS, ItemType.DIAMOND_BOOTS,
    };

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
    private final UiViewport uiv = new UiViewport(); // virtual canvas → aspect-locked, resize-proof UI

    // ---- rotating 3D preview model shown in the inventory (reflects equipped armour) ----
    private ModelBatch previewBatch;
    private PerspectiveCamera previewCam;
    private Environment previewEnv;
    private Model previewModel;
    private ModelInstance previewInstance;
    private ArmorRenderer previewArmor;
    private float previewSpin;

    private Mode mode = Mode.NONE;
    private boolean creativeMode;
    private int creativeTab;
    private int selectedHotbar;

    private ItemStack carried;
    private ItemStack[] chestSlots;
    private String chestTitle = "Chest";
    private java.util.function.Consumer<ItemStack> dropHandler; // drag-out → spawn into world

    public PlayerUI(Inventory inv) { this.inv = inv; }

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

    /** The screen sets this so an item dragged out of the panel is spawned into the world (not deleted). */
    public void setDropHandler(java.util.function.Consumer<ItemStack> handler) { this.dropHandler = handler; }

    /** The cached icon texture for an item type (used by world drop entities). */
    public Texture iconTexture(ItemType type) { return icons.get(type); }

    /** Drop the carried stack into the world via the drop handler (a no-op clear if none is set). */
    private void dropCarried() {
        if (carried != null && dropHandler != null) dropHandler.accept(carried);
        carried = null;
    }

    private ItemType[] tabItems() { return creativeTab == 0 ? COMBAT : ARMOR; }

    // ---------------------------------------------------------------- InputProcessor (routing)

    @Override
    public boolean keyDown(int keycode) {
        Settings cfg = Settings.get();
        if (keycode == cfg.key(Settings.Action.INVENTORY)) {
            if (mode == Mode.NONE) mode = creativeMode ? Mode.CREATIVE : Mode.INVENTORY;
            else closeModal();
            return true;
        }
        for (int i = 0; i < Settings.SLOTS.length; i++) { // hotbar slot select (rebindable)
            if (keycode == cfg.key(Settings.SLOTS[i])) { selectedHotbar = i; return true; }
        }
        return false; // Esc, WASD, drop, etc. fall through to the screen/world.
    }

    /** Remove one item from the selected hotbar slot and return it as a 1-count stack (or null). */
    public ItemStack takeOneFromSelectedHotbar() {
        ItemStack s = inv.hotbar(selectedHotbar);
        if (s == null) return null;
        ItemStack one = new ItemStack(s.type, 1);
        s.count -= 1;
        if (s.count <= 0) inv.set(Inventory.HOTBAR_BASE + selectedHotbar, null);
        return one;
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
        Vector2 m = uiv.unproject(screenX, screenY);
        float mx = m.x, my = m.y;

        Slot s = null;
        for (Slot cand : buildSlots()) if (cand.hit(mx, my)) { s = cand; break; }
        if (s == null) { dropCarried(); return; }
        if (s.store == STORE_CREATIVE) { carried = new ItemStack(CREATIVE_ITEMS[s.index], 1); return; }

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
            // Clean 2×9 = 18-slot palette of the whole whitelist, the 4 armour slots in a column to its
            // left, and the hotbar row beneath — matching the Bedrock inventory reference.
            float gridW = 9 * SLOT + 8 * PAD;
            float gx = px + (pw - gridW) * 0.5f + 40f;   // shifted right to leave the armour column room
            float topY = py + ph - 70f - SLOT;
            for (int i = 0; i < CREATIVE_ITEMS.length; i++) {
                int c = i % 9, r = i / 9;
                out.add(new Slot(gx + c * (SLOT + PAD), topY - r * (SLOT + PAD), SLOT, STORE_CREATIVE, i));
            }
            // Armour column on the left (helmet/chest/legs/boots).
            float armX = px + 26f, armTop = topY;
            for (int i = 0; i < Inventory.ARMOR; i++) {
                out.add(new Slot(armX, armTop - i * (SLOT + PAD), SLOT, STORE_INV, Inventory.ARMOR_BASE + i));
            }
            // Hotbar row along the bottom, so picked items can be dragged straight onto it.
            addRow(out, gx, py + 22f, Inventory.HOTBAR, STORE_INV, Inventory.HOTBAR_BASE);
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
        float w = UiViewport.W, h = UiViewport.H;
        if (mode == Mode.CREATIVE) {
            float pw = Math.min(w * 0.84f, 1040f), ph = Math.min(h * 0.82f, 660f);
            return new float[] {(w - pw) * 0.5f, (h - ph) * 0.5f, pw, ph};
        }
        float pw = Math.min(w * 0.62f, 660f), ph = Math.min(h * 0.8f, 580f);
        return new float[] {(w - pw) * 0.5f, (h - ph) * 0.5f, pw, ph};
    }

    // ---------------------------------------------------------------- render

    /** Keep the virtual-canvas viewport aligned to the window — call from the screen's resize(). */
    public void resize(int width, int height) { uiv.resize(width, height); }

    /** Provide the skin so the inventory can render a rotating 3D model of the player + worn armour. */
    public void setPreviewSkin(Texture skin) {
        if (previewModel != null) return;
        previewBatch = new ModelBatch();
        previewEnv = new Environment();
        previewEnv.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.72f, 0.72f, 0.76f, 1f));
        previewEnv.add(new DirectionalLight().set(0.55f, 0.55f, 0.55f, -0.5f, -0.7f, -0.6f));
        previewCam = new PerspectiveCamera(33f, 1f, 1f);
        previewCam.near = 0.1f; previewCam.far = 30f;
        previewModel = MinecraftPlayerModel.build(skin);
        previewInstance = new ModelInstance(previewModel);
        previewInstance.calculateTransforms();
        previewArmor = new ArmorRenderer(inv); // reads the live inventory → reflects equipped armour
    }

    /** Render the rotating 3D model in the inventory's portrait box (rect in virtual canvas coords). */
    private void renderPreviewModel(float vx, float vy, float vw, float vh) {
        if (previewModel == null) return;
        previewSpin = (previewSpin + Gdx.graphics.getDeltaTime() * 32f) % 360f;
        float[] r = uiv.toScreen(vx, vy, vw, vh);
        int rx = (int) r[0], ry = (int) r[1], rw = (int) r[2], rh = (int) r[3];
        if (rw <= 0 || rh <= 0) return;
        Gdx.gl.glViewport(rx, ry, rw, rh);
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor(rx, ry, rw, rh);
        Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
        // Frame the whole figure (feet at 0 → armoured helmet top ~2.1) so nothing is clipped.
        PreviewCamera.frame(previewCam, rw, rh, 0f, 2.1f, 0.55f);
        previewInstance.transform.setToRotation(Vector3.Y, previewSpin);
        previewBatch.begin(previewCam);
        previewBatch.render(previewInstance, previewEnv);
        previewArmor.render(previewBatch, previewEnv, previewInstance);
        previewBatch.end();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    public void render() {
        float w = UiViewport.W, h = UiViewport.H;
        Vector2 m = uiv.unproject(Gdx.input.getX(), Gdx.input.getY());
        float mx = m.x, my = m.y;
        uiv.apply();
        shapes.setProjectionMatrix(uiv.combined());
        batch.setProjectionMatrix(uiv.combined());

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
        batch.end();
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
        for (Slot s : slots) {
            drawSlot(s.x, s.y, s.size, s.hit(mx, my));
            if (s.store == STORE_INV && s.index == Inventory.HOTBAR_BASE + selectedHotbar) drawSelected(s.x, s.y, s.size);
        }
        shapes.end();

        batch.begin();
        font.setColor(BedrockWidgets.TEXT_DARK); // dark text on the light Bedrock panel
        title(P);
        for (Slot s : slots) {
            ItemStack st = s.store == STORE_CREATIVE ? new ItemStack(CREATIVE_ITEMS[s.index], 1) : read(s);
            drawStack(st, s.x, s.y, s.size);
            if (st == null && isArmorSlot(s)) armorHint(s);
        }
        if (carried != null) drawStack(carried, mx - SLOT * 0.5f, my - SLOT * 0.5f, SLOT);
        batch.end();

        // Rotating 3D player model (reflecting worn armour) inside the inventory's portrait box.
        if (mode == Mode.INVENTORY) {
            renderPreviewModel(px + 24f + SLOT + 12f, py + ph - 58f - 3 * (SLOT + PAD), SLOT * 2.3f, SLOT * 3 + 2 * PAD);
        }
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
        BedrockWidgets.panel(shapes, x, y, w, h);
    }

    /** A beveled Bedrock slot: recessed top/left, light bottom/right (inset look). */
    private void drawSlot(float x, float y, float s, boolean hovered) {
        BedrockWidgets.slot(shapes, x, y, s, hovered);
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
        if (previewBatch != null) previewBatch.dispose();
        if (previewModel != null) previewModel.dispose();
        if (previewArmor != null) previewArmor.dispose();
    }
}
