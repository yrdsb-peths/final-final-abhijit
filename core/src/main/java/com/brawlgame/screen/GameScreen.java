package com.brawlgame.screen;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
import com.brawlgame.entity.AiBrawler;
import com.brawlgame.entity.ArmorRenderer;
import com.brawlgame.entity.ItemEntity;
import com.brawlgame.entity.Player;
import com.brawlgame.game.MatchManager;
import com.brawlgame.game.MatchStats;
import com.brawlgame.gfx.AimCone;
import com.brawlgame.gfx.AnimatedWaterRenderer;
import com.brawlgame.gfx.GasZone;
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
import com.brawlgame.ui.CharacterShowcase;
import com.brawlgame.ui.DamageVignette;
import com.brawlgame.ui.EndScreenOverlay;
import com.brawlgame.ui.MatchIntro;
import com.brawlgame.ui.OverheadHud;
import com.brawlgame.ui.PauseOverlay;
import com.brawlgame.ui.PlayerUI;
import com.brawlgame.ui.Settings;

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
    private ArmorRenderer armor;
    private PauseOverlay pause;
    private InputMultiplexer uiMux;
    private final List<ItemEntity> drops = new ArrayList<>();
    private final OverheadHud overhead = new OverheadHud();
    private final DamageVignette vignette = new DamageVignette();
    private final Vector3 platePos = new Vector3();
    private static final float INTRO_DUR = 3.2f;
    private MatchIntro matchIntro;
    private GasZone gas;
    private float gasDmgTimer = 0f;
    private static final float GAS_DAMAGE = 3.5f, GAS_TICK = 0.8f;
    private AiBrawler bot;
    private int brawlersLeft = 2;
    private final Vector3 botPlatePos = new Vector3();
    private DebugRenderer debug;
    private boolean showDebug = false;
    private AnimatedWaterRenderer water;
    private MatchManager match;
    private final MatchStats playerStats = new MatchStats("You");
    private final MatchStats rivalStats = new MatchStats("Rival");
    private EndScreenOverlay endScreen;
    private CharacterShowcase showcase;
    private int showcasePlayer, showcaseRival;
    private boolean matchEnded;

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

        // Match intro: pan the camera from the corner diagonally opposite the player into the follow pose.
        float cornerX = sx <= 0f ? map.worldX(map.cols() - 1) : map.worldX(0);
        float cornerZ = sz <= 0f ? map.worldZ(map.rows() - 1) : map.worldZ(0);
        cameraRig.beginIntro(new Vector3(cornerX, 30f, cornerZ), new Vector3(0f, 1f, 0f), INTRO_DUR);
        gas = new GasZone(map);
        match = new MatchManager(gas, map);
        water = new AnimatedWaterRenderer(map);
        endScreen = new EndScreenOverlay();

        float bMinX = map.worldX(1), bMaxX = map.worldX(map.cols() - 2);
        float bMinZ = map.worldZ(1), bMaxZ = map.worldZ(map.rows() - 2);
        bot = new AiBrawler(skin, sx <= 0f ? bMaxX * 0.6f : bMinX * 0.6f,
            sz <= 0f ? bMaxZ * 0.6f : bMinZ * 0.6f, bMinX, bMaxX, bMinZ, bMaxZ);
        player.getWeapon().setTarget(bot);
        player.setMatchElimination(true);
        player.setMatchStats(playerStats);
        player.getWeapon().setDamageDealtListener(playerStats::addDamage);
        bot.setMatchStats(rivalStats);

        // Solid-grid collider so the player, the rival, and potato projectiles collide with walls/fences.
        BlockCollider worldCollider = new BlockCollider() {
            @Override public int colAt(float x) { return map.colAt(x); }
            @Override public int rowAt(float z) { return map.rowAt(z); }
            @Override public float cellCenterX(int col) { return map.worldX(col); }
            @Override public float cellCenterZ(int row) { return map.worldZ(row); }
            @Override public float cellSize() { return GameMap.CELL; }
            @Override public float collisionHeightAt(int col, int row) {
                BlockType t = map.get(col, row);
                return t == null ? 0f : t.collisionHeight();
            }
        };
        player.setCollider(worldCollider);
        bot.setCollider(worldCollider);

        aimCone = new AimCone();
        ground = new GroundIndicator();

        inventory = new Inventory();
        inventory.set(Inventory.HOTBAR_BASE + 0, new ItemStack(ItemType.DIAMOND_SWORD));
        inventory.set(Inventory.HOTBAR_BASE + 1, new ItemStack(ItemType.POTATO_GUN));
        inventory.set(Inventory.HOTBAR_BASE + 2, new ItemStack(ItemType.IRON_SWORD));
        // Equip a diamond set so worn armour is visible on the map (swap via the creative menu, /).
        inventory.set(Inventory.ARMOR_BASE + 0, new ItemStack(ItemType.DIAMOND_HELMET));
        inventory.set(Inventory.ARMOR_BASE + 1, new ItemStack(ItemType.DIAMOND_CHESTPLATE));
        inventory.set(Inventory.ARMOR_BASE + 2, new ItemStack(ItemType.DIAMOND_LEGGINGS));
        inventory.set(Inventory.ARMOR_BASE + 3, new ItemStack(ItemType.DIAMOND_BOOTS));
        ui = new PlayerUI(inventory);
        armor = new ArmorRenderer(inventory);          // worn armour parented to the rig
        player.setHeldItemSupplier(ui::selectedItem);  // weapon follows the selected hotbar slot
        player.getWeapon().setIconResolver(ui::iconTexture); // armour/items shown held in the fist
        player.setInventory(inventory);                // worn armour feeds the damage formula
        ui.setPreviewSkin(skin);
        pause = new PauseOverlay(game, skin);

        showcase = new CharacterShowcase();
        showcasePlayer = showcase.add(skin, inventory);
        showcaseRival = showcase.add(skin, bot.armorInventory());
        matchIntro = new MatchIntro(showcase, new int[] {showcasePlayer, showcaseRival},
            new String[] {"You", "Rival"}, INTRO_DUR);
        endScreen.bindShowcase(showcase, showcasePlayer, showcaseRival);

        ui.setDropHandler(stack -> drops.add(new ItemEntity(stack, ui.iconTexture(stack.type),
            player.getPosition().x, player.getPosition().z, player.getFacingDeg())));

        // UI gets first dibs on clicks/keys; the world polls the rest.
        uiMux = new InputMultiplexer(ui);
        Gdx.input.setInputProcessor(uiMux);

        debug = new DebugRenderer();
    }

    @Override
    public void render(float delta) {
        float d = Math.min(delta, 1f / 30f);
        // Esc: close an open panel first, else open the pause overlay (which then owns Esc).
        if (!pause.isOpen() && Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (ui.isModalOpen()) ui.closeModal();
            else pause.open();
        }
        if (pause.isOpen() && Gdx.input.getInputProcessor() != pause) Gdx.input.setInputProcessor(pause);
        else if (!pause.isOpen() && Gdx.input.getInputProcessor() != uiMux) Gdx.input.setInputProcessor(uiMux);
        if (Gdx.input.isKeyJustPressed(Input.Keys.F3)) player.setGodMode(!player.isGodMode()); // god mode (fly + invuln)
        if (Gdx.input.isKeyJustPressed(Input.Keys.F4)) gas.activate(); // force-start the gas

        if (endScreen.isVisible()) {
            endScreen.update(d);
            renderWorld(d, true);
            endScreen.render();
            Settings.get().capFrame();
            return;
        }

        // Freeze world control while a panel or the pause menu is open.
        if (pause.isOpen()) pause.update(d);
        boolean intro = cameraRig.isIntroActive();
        if (intro) matchIntro.update(d);
        else if (!matchEnded) match.start();
        if (!ui.isModalOpen() && !pause.isOpen() && !matchEnded) {
            if (!intro) player.update(d, cameraRig.camera);
            cameraRig.update(d, player.getPosition(), player.isSprinting());
            if (intro) { /* gameplay (drops, ammo, gas, bot) resumes once the intro finishes */ }
            else {
            for (Iterator<ItemEntity> it = drops.iterator(); it.hasNext(); ) {
                ItemEntity e = it.next();
                if (e.update(d, player.getPosition())) { inventory.add(e.stack()); e.dispose(); it.remove(); }
            }
            WeaponController wc = player.getWeapon();
            overhead.update(d, wc.ammo(), wc.ammoCapacity(), wc.pollDryFire());
            match.update(d);
            water.update(d);
            gas.update(d);
            gasDmgTimer += d;
            boolean gasTick = gas.isActive() && gasDmgTimer >= GAS_TICK;
            if (gasTick) gasDmgTimer = 0f;
            if (gasTick && gas.inGas(player.getPosition().x, player.getPosition().z)) player.applyDamage(GAS_DAMAGE);

            bot.update(d, player);
            if (gasTick && gas.inGas(bot.position().x, bot.position().z)) bot.damage(GAS_DAMAGE);
            if (bot.isDead() && brawlersLeft > 1) brawlersLeft = 1;
            checkMatchEnd(gasTick);
            if (Settings.get().justPressed(Settings.Action.DROP)) {
                ItemStack d2 = ui.takeOneFromSelectedHotbar();
                if (d2 != null) drops.add(new ItemEntity(d2, ui.iconTexture(d2.type),
                    player.getPosition().x, player.getPosition().z, player.getFacingDeg()));
            }
            } // end !intro gameplay
        }
        renderWorld(d, intro);
    }

    private void renderWorld(float d, boolean intro) {
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

        modelBatch.begin(cameraRig.camera);
        scenery.render(modelBatch, environment);
        renderer.render(modelBatch, environment);
        water.render(modelBatch, environment);
        gas.render(modelBatch, environment);
        modelBatch.end();

        if (!intro && !matchEnded) {
            ground.renderPlayer(cameraRig.camera, player.getPosition().x, player.getPosition().z,
                PLAYER_RING_RADIUS, player.getFacingDeg());
        }

        modelBatch.begin(cameraRig.camera);
        if (!player.isEliminated()) {
            player.render(modelBatch, environment);
            armor.render(modelBatch, environment, player.getModelInstance());
        }
        bot.render(modelBatch, environment);
        for (ItemEntity e : drops) e.render(modelBatch, environment);
        modelBatch.end();

        if (!intro && !matchEnded && !player.isEliminated()) {
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
        }

        if (showDebug) debug.render(cameraRig.camera, player);

        if (!player.isEliminated()) {
            platePos.set(player.getPosition().x, player.getPosition().y + 2.2f, player.getPosition().z);
            overhead.render(cameraRig.camera, platePos, "Player", player.getHealth(), player.getMaxHealth());
        }
        if (!bot.isDead() && !bot.isDying()) {
            botPlatePos.set(bot.position().x, bot.position().y + 2.2f, bot.position().z);
            overhead.renderSimple(cameraRig.camera, botPlatePos, "Rival", bot.health(), bot.maxHealth());
        }
        overhead.renderLabel("Brawlers left: " + brawlersLeft);
        if (!intro && !matchEnded) match.renderTimer();
        vignette.renderFlash(player.getHurtFraction());
        vignette.render(player.getHurtFraction());
        if (gas.isActive() && !player.isGodMode() && gas.inGas(player.getPosition().x, player.getPosition().z)) {
            vignette.render(0.6f, 0.55f, 0.12f, 0.78f);
        }

        ui.render();
        if (!matchIntro.isDone()) matchIntro.render();
        if (pause.isOpen()) pause.render();
        Settings.get().capFrame();
    }

    private void checkMatchEnd(boolean gasTick) {
        boolean playerOut = player.isEliminated();
        boolean rivalOut = bot.isDead();
        if (!playerOut && !rivalOut) return;
        match.registerDeath(playerOut, rivalOut);
        if (playerOut && !rivalOut) rivalStats.addTakedown();
        if (rivalOut && !playerOut) playerStats.addTakedown();
        if (match.outcome() != MatchManager.Outcome.NONE && !matchEnded) {
            matchEnded = true;
            endScreen.show(match.outcome(), playerStats, rivalStats,
                () -> game.setScreen(new MainMenuScreen(game)));
        }
    }

    @Override
    public void resize(int width, int height) {
        if (cameraRig != null) cameraRig.resize(width, height);
        if (ui != null) ui.resize(width, height);
        if (overhead != null) overhead.resize(width, height);
        if (match != null) match.resize(width, height);
        if (matchIntro != null) matchIntro.resize(width, height);
        if (endScreen != null) endScreen.resize(width, height);
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
        armor.dispose();
        pause.dispose();
        overhead.dispose();
        vignette.dispose();
        if (matchIntro != null) matchIntro.dispose();
        if (gas != null) gas.dispose();
        if (water != null) water.dispose();
        if (match != null) match.dispose();
        if (endScreen != null) endScreen.dispose();
        if (showcase != null) showcase.dispose();
        if (bot != null) bot.dispose();
        for (ItemEntity e : drops) e.dispose();
        drops.clear();
        ui.dispose();
        debug.dispose();
        skin.dispose();
        modelBatch = null;
    }
}
