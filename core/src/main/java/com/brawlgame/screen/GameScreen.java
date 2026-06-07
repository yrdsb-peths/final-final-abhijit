package com.brawlgame.screen;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalShadowLight;
import com.badlogic.gdx.graphics.g3d.utils.DepthShaderProvider;
import com.badlogic.gdx.math.Vector3;
import com.brawlgame.combat.BlockCollider;
import com.brawlgame.combat.WeaponController;
import com.brawlgame.entity.Player;
import com.brawlgame.gfx.AimCone;
import com.brawlgame.gfx.GroundIndicator;
import com.brawlgame.item.Inventory;
import com.brawlgame.item.ItemStack;
import com.brawlgame.item.ItemType;
import com.brawlgame.map.BlockLibrary;
import com.brawlgame.map.BlockType;
import com.brawlgame.map.GameMap;
import com.brawlgame.map.MapRenderer;
import com.brawlgame.map.SceneryRenderer;
import com.brawlgame.render.CameraRig;
import com.brawlgame.render.DebugRenderer;
import com.brawlgame.ui.PlayerUI;

/**
 * Plays a saved custom map. It rebuilds the map's blocks + decorative canyon, lights the scene with
 * the same daylight + shadow pipeline as the editor, and instantiates the {@link Player} at the
 * map's spawn point (falling back to the board centre if none was placed). The Dungeons-style follow
 * camera tracks the player. ESC returns to the main menu.
 *
 * <p>Note: block collision is not yet simulated — the player walks the flat floor (y=0); wall/fence
 * collision is a later phase. Spawning + rendering the authored map is what this screen delivers.
 */
public final class GameScreen implements Screen {

    private static final Vector3 SUN_DIR = new Vector3(0.85f, -1.5f, 0.85f).nor();
    /** Half-width of the gun's straight-shot rectangular aim reticle (world units). */
    private static final float GUN_AIM_HALF_WIDTH = 0.35f;
    /** Radius of the green ground-highlight ring under the player (world units). */
    private static final float PLAYER_RING_RADIUS = 0.7f;

    private final Game game;
    private final GameMap map;

    private ModelBatch modelBatch;
    private ModelBatch shadowBatch;
    private DirectionalShadowLight shadowLight;
    private boolean shadowsEnabled;
    private Environment environment;

    private CameraRig cameraRig;
    private BlockLibrary library;
    private MapRenderer renderer;
    private SceneryRenderer scenery;
    private Player player;
    private Texture skin;
    private AimCone aimCone;
    private GroundIndicator ground;
    private PlayerUI ui;
    private Inventory inventory;
    private DebugRenderer debug;
    private boolean showDebug = false;

    public GameScreen(Game game, GameMap map) {
        this.game = game;
        this.map = map;
    }

    @Override
    public void show() {
        modelBatch = new ModelBatch();
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.55f, 0.55f, 0.58f, 1f));
        float vp = Math.max(map.cols(), map.rows()) + 34f;
        try {
            // Tight far plane (proportional to the frustum) to avoid shadow-acne "triangles".
            shadowLight = new DirectionalShadowLight(2048, 2048, vp, vp, 1f, vp * 2.2f);
            shadowLight.set(1.0f, 0.96f, 0.84f, SUN_DIR);
            environment.add(shadowLight);
            shadowBatch = new ModelBatch(new DepthShaderProvider());
            shadowsEnabled = true;
        } catch (Throwable t) {
            Gdx.app.error("GameScreen", "Shadow map unavailable; using flat daylight", t);
            shadowsEnabled = false;
            if (shadowLight != null) { shadowLight.dispose(); shadowLight = null; }
            environment.add(new DirectionalLight().set(1.0f, 0.96f, 0.84f, SUN_DIR.x, SUN_DIR.y, SUN_DIR.z));
        }

        cameraRig = new CameraRig();
        // South (+Z) boundary so the follow camera elevates before clipping the canyon there.
        cameraRig.setBottomBoundary(map.worldZ(map.rows() - 1));
        library = new BlockLibrary(map.theme());
        renderer = new MapRenderer(map, library);
        scenery = new SceneryRenderer(map, library);

        skin = new Texture(Gdx.files.internal("textures/player.png"));
        player = new Player(skin);

        // Spawn at the authored spawn point, or the board centre if none was placed.
        int[] sp = map.findSpawn();
        float sx = sp != null ? map.worldX(sp[0]) : 0f;
        float sz = sp != null ? map.worldZ(sp[1]) : 0f;
        player.setSpawn(sx, sz);

        // Solid-grid collider so the player and potato projectiles collide with walls/fences.
        player.setCollider(new BlockCollider() {
            @Override public int colAt(float x) { return map.colAt(x); }
            @Override public int rowAt(float z) { return map.rowAt(z); }
            @Override public float cellCenterX(int col) { return map.worldX(col); }
            @Override public float cellCenterZ(int row) { return map.worldZ(row); }
            @Override public float cellSize() { return GameMap.CELL; }
            @Override public float collisionHeightAt(int col, int row) {
                BlockType t = map.get(col, row);
                return t == null ? 0f : t.collisionHeight();
            }
        });

        aimCone = new AimCone();
        ground = new GroundIndicator();

        inventory = new Inventory();
        inventory.set(Inventory.HOTBAR_BASE + 0, new ItemStack(ItemType.DIAMOND_SWORD));
        inventory.set(Inventory.HOTBAR_BASE + 1, new ItemStack(ItemType.POTATO_GUN));
        inventory.set(Inventory.HOTBAR_BASE + 2, new ItemStack(ItemType.IRON_SWORD));
        ui = new PlayerUI(inventory);
        player.setHeldItemSupplier(ui::selectedItem); // weapon follows the selected hotbar slot

        // UI gets first dibs on clicks/keys; the world polls the rest.
        Gdx.input.setInputProcessor(new InputMultiplexer(ui));

        debug = new DebugRenderer();
    }

    @Override
    public void render(float delta) {
        float d = Math.min(delta, 1f / 30f);
        // Esc closes an open panel first; only exits to the menu when nothing is open.
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (ui.isModalOpen()) ui.closeModal();
            else { game.setScreen(new MainMenuScreen(game)); return; }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F3)) showDebug = !showDebug;

        // Freeze world control while a panel is open (clicks/keys belong to the UI).
        if (!ui.isModalOpen()) {
            player.update(d, cameraRig.camera);
            cameraRig.update(d, player.getPosition(), player.isSprinting());
        }
        renderer.rebuildIfDirty();

        boolean canShadow = shadowsEnabled && shadowLight != null && shadowLight.getFrameBuffer() != null;
        if (canShadow) {
            shadowLight.begin(player.getPosition(), SUN_DIR);
            shadowBatch.begin(shadowLight.getCamera());
            scenery.renderCasters(shadowBatch);
            renderer.renderCasters(shadowBatch);
            shadowBatch.render(player.getModelInstance());
            shadowBatch.end();
            shadowLight.end();
        }
        environment.shadowMap = canShadow ? shadowLight : null;

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClearColor(0.53f, 0.74f, 0.92f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        // World pass: scenery + map blocks (the floor) first.
        modelBatch.begin(cameraRig.camera);
        scenery.render(modelBatch, environment);
        renderer.render(modelBatch, environment);
        modelBatch.end();

        // Ground highlight rings: after the floor, before the characters, so they stand on top.
        ground.renderPlayer(cameraRig.camera, player.getPosition().x, player.getPosition().z,
            PLAYER_RING_RADIUS, player.getFacingDeg());

        // Character pass: player model + held weapon + projectiles.
        modelBatch.begin(cameraRig.camera);
        player.render(modelBatch, environment);
        modelBatch.end();

        // Ground aim reticle (cone for melee, rectangle for the gun), then the additive swoosh.
        WeaponController w = player.getWeapon();
        if (w.aimConeVisible()) {
            if (w.getWeapon() == WeaponController.Weapon.GUN) {
                aimCone.renderRect(cameraRig.camera, player.getPosition().x, player.getPosition().z,
                    player.getFacingDeg(), GUN_AIM_HALF_WIDTH, w.coneRange());
            } else {
                aimCone.render(cameraRig.camera, player.getPosition().x, player.getPosition().z,
                    player.getFacingDeg(), w.coneHalfAngle(), w.coneRange());
            }
        }
        player.renderTrail(cameraRig.camera);

        if (showDebug) debug.render(cameraRig.camera, player);

        // HUD: hotbar always, plus the inventory/creative panel when open.
        ui.render();
    }

    @Override
    public void resize(int width, int height) {
        if (cameraRig != null) cameraRig.resize(width, height);
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
        player.dispose();
        aimCone.dispose();
        ground.dispose();
        ui.dispose();
        debug.dispose();
        skin.dispose();
        modelBatch = null;
    }
}
