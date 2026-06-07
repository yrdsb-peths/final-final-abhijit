package com.brawlgame.screen;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalShadowLight;
import com.badlogic.gdx.graphics.g3d.utils.DepthShaderProvider;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.brawlgame.map.BlockLibrary;
import com.brawlgame.map.BlockType;
import com.brawlgame.map.GameMap;
import com.brawlgame.map.MapRenderer;
import com.brawlgame.map.MapSerializer;
import com.brawlgame.map.MapSize;
import com.brawlgame.map.SceneryRenderer;
import com.brawlgame.map.Theme;
import com.brawlgame.render.SpectatorCamera;
import com.brawlgame.ui.BedrockUi;
import com.brawlgame.ui.CreativeInventory;
import com.brawlgame.ui.Hotbar;

/**
 * The Brawl-Stars-style developer level editor, dressed in a Minecraft Bedrock UI. A free spectator
 * camera (WASD pan, Q/E zoom) hovers over a centred grid that is auto-enclosed with stacked border
 * walls and framed by a non-editable {@link SceneryRenderer canyon} of themed scenery. Flat
 * {@link BedrockUi} side buttons run down both edges (bounty indicator, grid toggle on the left;
 * inventory, undo, redo, clear, save on the right); a 9-slot {@link Hotbar} sits at the bottom; and a
 * {@link CreativeInventory} overlay (E / inventory button) binds blocks to the active slot.
 *
 * <p>Left-click paints the active block (solids auto-stack into walls), right-click erases — each
 * drag is one undoable stroke. ESC returns to the menu.
 */
public final class MapMakerScreen implements Screen {

    /** The clickable side controls. BOUNTY is a passive mode indicator (Brawl-Stars flavour). */
    private enum Btn { BOUNTY, GRID, BACK, INVENTORY, UNDO, REDO, CLEAR, SAVE }

    private static final float BTN = 64f;   // square side-button size, px
    private static final float BTN_GAP = 10f;
    private static final float EDGE = 16f;  // margin from the viewport edge

    private final Game game;
    private final Theme theme;
    private final MapSize size;

    /** Afternoon sun: high, angled from the screen's top-left so shadows fall to the bottom-right. */
    private static final Vector3 SUN_DIR = new Vector3(0.85f, -1.5f, 0.85f).nor();

    private ModelBatch modelBatch;
    private ModelBatch shadowBatch;
    private DirectionalShadowLight shadowLight;
    private boolean shadowsEnabled;
    private Environment environment;
    private SpectatorCamera cam;
    private GameMap map;
    private BlockLibrary library;
    private MapRenderer renderer;
    private SceneryRenderer scenery;
    private Hotbar hotbar;
    private CreativeInventory inventory;

    private ShapeRenderer shapes;
    private SpriteBatch hud;
    private BitmapFont font;
    private final GlyphLayout glyph = new GlyphLayout();

    /** The block bound to each of the 9 hotbar slots (rebindable via the creative inventory). */
    private final BlockType[] slotTypes = new BlockType[9];

    private boolean showGrid = true;

    // hovered cell + stroke painting state
    private int hoverC, hoverR;
    private boolean hoverValid;
    private boolean leftPainting, rightPainting;

    private String toast = "";
    private float toastTimer = 0f;

    public MapMakerScreen(Game game, Theme theme, MapSize size) {
        this.game = game;
        this.theme = theme;
        this.size = size;
    }

    @Override
    public void show() {
        modelBatch = new ModelBatch();
        cam = new SpectatorCamera();
        map = new GameMap(theme, size);

        // Bright daylight: warm ambient (so shadows stay semi-transparent, not black) plus a strong
        // afternoon sun. We try to set up a shadow-map pass; if the GPU/driver can't allocate the
        // shadow framebuffer (some macOS GL profiles refuse large depth FBOs), we fall back to flat
        // daylight so the editor always runs. The shadow frustum spans the arena + canyon ring.
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.55f, 0.55f, 0.58f, 1f));
        float vp = Math.max(map.cols(), map.rows()) + 34f;
        try {
            // far is kept tight (proportional to the frustum) so the depth buffer isn't wasted over
            // hundreds of empty units — that waste is what caused shadow-acne "triangles" on faces.
            shadowLight = new DirectionalShadowLight(2048, 2048, vp, vp, 1f, vp * 2.2f);
            shadowLight.set(1.0f, 0.96f, 0.84f, SUN_DIR);
            environment.add(shadowLight);
            shadowBatch = new ModelBatch(new DepthShaderProvider());
            shadowsEnabled = true;
        } catch (Throwable t) {
            Gdx.app.error("MapMaker", "Shadow map unavailable; using flat daylight", t);
            shadowsEnabled = false;
            if (shadowLight != null) { shadowLight.dispose(); shadowLight = null; }
            environment.add(new DirectionalLight().set(1.0f, 0.96f, 0.84f,
                SUN_DIR.x, SUN_DIR.y, SUN_DIR.z));
        }

        library = new BlockLibrary(theme);
        renderer = new MapRenderer(map, library);
        scenery = new SceneryRenderer(map, library);

        hotbar = new Hotbar(9);
        BlockType[] palette = theme.palette();
        for (int i = 0; i < 9; i++) {
            slotTypes[i] = (i < palette.length) ? palette[i] : BlockType.ERASER;
            hotbar.setIcon(i, library.icon(slotTypes[i]), slotTypes[i].displayName());
        }
        hotbar.setSelected(0);

        inventory = new CreativeInventory(theme, library);

        shapes = new ShapeRenderer();
        hud = new SpriteBatch();
        font = new BitmapFont();

        // Mouse wheel: scroll the inventory if open, else cycle the hotbar selection.
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                if (inventory.isOpen()) inventory.scroll((int) Math.signum(amountY));
                else hotbar.scroll((int) Math.signum(amountY));
                return true;
            }
        });
    }

    @Override
    public void render(float delta) {
        handleInput(delta);
        renderer.rebuildIfDirty();

        // --- Shadow depth pass: render opaque casters from the sun's point of view. ---
        // Guarded so a missing framebuffer can never crash the editor; the shadow map is only bound
        // to the environment when its depth was actually rendered this frame.
        boolean canShadow = shadowsEnabled && shadowLight != null && shadowLight.getFrameBuffer() != null;
        if (canShadow) {
            shadowLight.begin(cam.getTarget(), SUN_DIR);
            shadowBatch.begin(shadowLight.getCamera());
            scenery.renderCasters(shadowBatch);
            renderer.renderCasters(shadowBatch);
            shadowBatch.end();
            shadowLight.end();
        }
        environment.shadowMap = canShadow ? shadowLight : null;

        // --- Main lit pass (samples the shadow map via the environment). ---
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClearColor(0.53f, 0.74f, 0.92f, 1f); // bright daytime sky
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(cam.camera);
        scenery.render(modelBatch, environment);
        renderer.render(modelBatch, environment);
        modelBatch.end();

        if (showGrid) drawGrid();
        if (hoverValid && !inventory.isOpen()) drawHoverHighlight();

        drawSideButtons();
        hotbar.render();
        inventory.render(); // draws its own scrim over everything when open
    }

    // ------------------------------------------------------------------ input

    private void handleInput(float delta) {
        if (inventory.isOpen()) {
            BlockType picked = inventory.update();
            if (picked != null) bindToSlot(picked);
            if (Gdx.input.isKeyJustPressed(Input.Keys.E) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                inventory.close();
            }
            return; // freeze world editing + camera while the overlay is up
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.setScreen(new MapMakerMenuScreen(game));
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.E)) inventory.toggle();

        cam.update(delta);
        if (Gdx.input.isKeyPressed(Input.Keys.R)) cam.zoom(-3f * delta); // zoom in
        if (Gdx.input.isKeyPressed(Input.Keys.F)) cam.zoom(+3f * delta); // zoom out

        for (int i = 0; i < 9; i++) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1 + i)) hotbar.setSelected(i);
        }

        boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
            || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
        if (ctrl && Gdx.input.isKeyJustPressed(Input.Keys.S)) saveMap();
        if (ctrl && Gdx.input.isKeyJustPressed(Input.Keys.Z)) map.undo();
        if (ctrl && Gdx.input.isKeyJustPressed(Input.Keys.Y)) map.redo();

        // Side-button clicks take priority over world painting.
        boolean handledUi = handleSideButtons();
        updateHover();
        handlePainting(handledUi || overHotbar());
    }

    /** Fires the action under the cursor if a side button was clicked this frame. */
    private boolean handleSideButtons() {
        float mx = Gdx.input.getX(), my = Gdx.graphics.getHeight() - Gdx.input.getY();
        boolean over = false;
        for (Rect r : layoutButtons()) {
            if (r.contains(mx, my)) {
                over = true;
                if (Gdx.input.justTouched()) activate(r.id);
            }
        }
        return over;
    }

    private void activate(Btn id) {
        switch (id) {
            case GRID:      showGrid = !showGrid; break;
            case INVENTORY: inventory.toggle(); break;
            case UNDO:      map.undo(); break;
            case REDO:      map.redo(); break;
            case CLEAR:     map.clearAll(); break;
            case SAVE:      saveMap(); break;
            case BACK:      game.setScreen(new MainMenuScreen(game)); break;
            case BOUNTY:    break; // passive indicator
        }
    }

    /** Left-drag paints the active block, right-drag erases; each drag is one undoable stroke. */
    private void handlePainting(boolean suppress) {
        boolean leftDown = !suppress && Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        boolean rightDown = !suppress && Gdx.input.isButtonPressed(Input.Buttons.RIGHT);

        if (leftDown && !leftPainting) { map.beginStroke(); leftPainting = true; }
        if (rightDown && !rightPainting) { map.beginStroke(); rightPainting = true; }

        if (hoverValid && (leftPainting || rightPainting)) {
            if (leftPainting) map.apply(hoverC, hoverR, slotTypes[hotbar.getSelected()]);
            if (rightPainting) map.apply(hoverC, hoverR, BlockType.ERASER);
        }

        if (!Gdx.input.isButtonPressed(Input.Buttons.LEFT) && leftPainting) {
            map.commitStroke(); leftPainting = false;
        }
        if (!Gdx.input.isButtonPressed(Input.Buttons.RIGHT) && rightPainting) {
            map.commitStroke(); rightPainting = false;
        }
    }

    private void bindToSlot(BlockType t) {
        int slot = hotbar.getSelected();
        slotTypes[slot] = t;
        hotbar.setIcon(slot, library.icon(t), t.displayName());
    }

    /** Projects the cursor onto the y=0 plane and resolves the grid cell under it. */
    private void updateHover() {
        Ray ray = cam.pickRay(Gdx.input.getX(), Gdx.input.getY());
        hoverValid = false;
        if (Math.abs(ray.direction.y) < 1e-6f) return;
        float t = -ray.origin.y / ray.direction.y;
        if (t <= 0f) return;
        float wx = ray.origin.x + ray.direction.x * t;
        float wz = ray.origin.z + ray.direction.z * t;
        int c = map.colAt(wx), r = map.rowAt(wz);
        if (!map.inBounds(c, r)) return;
        hoverC = c; hoverR = r; hoverValid = true;
    }

    private boolean overHotbar() {
        return Gdx.graphics.getHeight() - Gdx.input.getY() < BTN + 30f; // bottom hotbar strip
    }

    // ------------------------------------------------------------------ rendering helpers

    private void drawGrid() {
        float minX = map.worldX(0) - 0.5f, maxX = map.worldX(map.cols() - 1) + 0.5f;
        float minZ = map.worldZ(0) - 0.5f, maxZ = map.worldZ(map.rows() - 1) + 0.5f;
        float y = 0.03f;
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(cam.camera.combined);
        shapes.begin(ShapeType.Line);
        shapes.setColor(1f, 1f, 1f, 0.12f);
        for (int c = 0; c <= map.cols(); c++) {
            float x = minX + c;
            shapes.line(x, y, minZ, x, y, maxZ);
        }
        for (int r = 0; r <= map.rows(); r++) {
            float z = minZ + r;
            shapes.line(minX, y, z, maxX, y, z);
        }
        shapes.end();
    }

    private void drawHoverHighlight() {
        float x = map.worldX(hoverC), z = map.worldZ(hoverR);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        shapes.setProjectionMatrix(cam.camera.combined);
        shapes.begin(ShapeType.Line);
        shapes.setColor(Color.WHITE);
        shapes.box(x - 0.5f, 0.05f, z + 0.5f, 1f, 0.001f, 1f);
        shapes.end();
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }

    private void drawSideButtons() {
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        float mx = Gdx.input.getX(), my = h - Gdx.input.getY();
        List<Rect> btns = layoutButtons();

        shapes.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        hud.getProjectionMatrix().setToOrtho2D(0, 0, w, h);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeType.Filled);
        for (Rect r : btns) {
            boolean hov = r.contains(mx, my);
            boolean active = (r.id == Btn.GRID && showGrid) || (r.id == Btn.INVENTORY && inventory.isOpen());
            boolean enabled = !(r.id == Btn.UNDO && !map.canUndo()) && !(r.id == Btn.REDO && !map.canRedo());
            BedrockUi.button(shapes, r.x, r.y, r.w, r.h, hov && enabled, active, enabled);
        }
        // Small bounty-star glyph inside the BOUNTY indicator.
        for (Rect r : btns) {
            if (r.id != Btn.BOUNTY) continue;
            shapes.setColor(0.98f, 0.82f, 0.20f, 1f);
            float cx = r.x + r.w * 0.5f, cy = r.y + r.h * 0.62f, rad = 12f;
            shapes.circle(cx, cy, rad, 5); // 5-gon stands in for a star
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        hud.begin();
        font.setColor(BedrockUi.TEXT);
        for (Rect r : btns) {
            String label = labelFor(r.id);
            glyph.setText(font, label);
            float ly = (r.id == Btn.BOUNTY) ? r.y + r.h * 0.32f : r.y + r.h * 0.5f + glyph.height * 0.5f;
            font.draw(hud, glyph, r.x + (r.w - glyph.width) * 0.5f, ly);
        }
        // Title + transient save toast as flat Bedrock text, top-centre.
        String title = theme.displayName() + "   " + size.name() + "   " + map.cols() + "x" + map.rows();
        glyph.setText(font, title);
        font.draw(hud, glyph, (w - glyph.width) * 0.5f, h - 14f);
        if (toastTimer > 0f) {
            toastTimer -= Gdx.graphics.getDeltaTime();
            font.setColor(0.55f, 1f, 0.6f, 1f);
            glyph.setText(font, toast);
            font.draw(hud, glyph, (w - glyph.width) * 0.5f, BTN + 70f);
        }
        hud.end();
    }

    private String labelFor(Btn id) {
        switch (id) {
            case BOUNTY: return "BOUNTY";
            case GRID:   return "GRID";
            case BACK:   return "EXIT";
            case INVENTORY: return "INV (E)";
            case UNDO:   return "UNDO";
            case REDO:   return "REDO";
            case CLEAR:  return "CLEAR";
            case SAVE:   return "SAVE";
            default:     return "";
        }
    }

    /** Lays out the side buttons for the current viewport (left + right vertical stacks). */
    private List<Rect> layoutButtons() {
        float h = Gdx.graphics.getHeight(), w = Gdx.graphics.getWidth();
        List<Rect> list = new ArrayList<>();
        float topY = h - 90f;
        // Left column: bounty indicator, grid toggle, exit-to-menu.
        Btn[] left = {Btn.BOUNTY, Btn.GRID, Btn.BACK};
        for (int i = 0; i < left.length; i++) {
            list.add(new Rect(left[i], EDGE, topY - i * (BTN + BTN_GAP), BTN, BTN));
        }
        // Right column: inventory, undo, redo, clear, save.
        Btn[] right = {Btn.INVENTORY, Btn.UNDO, Btn.REDO, Btn.CLEAR, Btn.SAVE};
        float rx = w - EDGE - BTN;
        for (int i = 0; i < right.length; i++) {
            list.add(new Rect(right[i], rx, topY - i * (BTN + BTN_GAP), BTN, BTN));
        }
        return list;
    }

    private void saveMap() {
        FileHandle f = MapSerializer.save(map);
        toast = "Saved " + f.path();
        toastTimer = 4f;
        Gdx.app.log("MapMaker", "Saved map to " + f.file().getAbsolutePath());
    }

    @Override
    public void resize(int width, int height) {
        if (cam != null) cam.resize(width, height);
    }

    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() { dispose(); }

    @Override
    public void dispose() {
        if (modelBatch == null) return;
        if (Gdx.input.getInputProcessor() != null) Gdx.input.setInputProcessor(null);
        modelBatch.dispose();
        if (shadowBatch != null) shadowBatch.dispose();
        if (shadowLight != null) shadowLight.dispose();
        renderer.dispose();
        scenery.dispose();
        library.dispose();
        hotbar.dispose();
        inventory.dispose();
        shapes.dispose();
        hud.dispose();
        font.dispose();
        modelBatch = null;
    }

    /** A laid-out side button rectangle in y-up screen space. */
    private static final class Rect {
        final Btn id;
        final float x, y, w, h;
        Rect(Btn id, float x, float y, float w, float h) {
            this.id = id; this.x = x; this.y = y; this.w = w; this.h = h;
        }
        boolean contains(float px, float py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }
    }
}
