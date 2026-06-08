package com.brawlgame.screen;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

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
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
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
import com.brawlgame.net.GameClient;
import com.brawlgame.net.GameServer;
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
    private enum NetworkRole { SOLO, HOST, CLIENT }

    private static final Vector3 SUN_DIR = new Vector3(0.85f, -1.5f, 0.85f).nor();
    /** Half-width of the gun's straight-shot rectangular aim reticle (world units). */
    private static final float GUN_AIM_HALF_WIDTH = 0.35f;
    /** Radius of the green ground-highlight ring under the player (world units). */
    private static final float PLAYER_RING_RADIUS = 0.7f;

    private final Game game;
    private final GameMap map;
    private final float difficultyScale; // 1.0 = normal, >1.0 = harder
    private final NetworkRole networkRole;
    private final GameServer netServer;
    private final GameClient netClient;

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
    private Texture botSkin; // separate texture so the bot never shares the player's skin
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
    private final RemoteInput remoteInput = new RemoteInput();

    public GameScreen(Game game, GameMap map) {
        this(game, map, 1f, NetworkRole.SOLO, null, null);
    }

    public GameScreen(Game game, GameMap map, float difficultyScale) {
        this(game, map, difficultyScale, NetworkRole.SOLO, null, null);
    }

    public GameScreen(Game game, GameMap map, GameServer server) {
        this(game, map, 1f, NetworkRole.HOST, server, null);
    }

    public GameScreen(Game game, GameMap map, GameClient client) {
        this(game, map, 1f, NetworkRole.CLIENT, null, client);
    }

    private GameScreen(Game game, GameMap map, float difficultyScale,
                       NetworkRole networkRole, GameServer netServer, GameClient netClient) {
        this.game = game;
        this.map  = map;
        this.difficultyScale = difficultyScale;
        this.networkRole = networkRole;
        this.netServer = netServer;
        this.netClient = netClient;
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
        renderer.setGameplayMode(true); // hide SPAWN/CHEST/BUSH editor markers during live play
        scenery = new SceneryRenderer(map, library);

        String skinPath = com.brawlgame.game.PlayerProfile.get().selectedSkin;
        if (!skinPath.isEmpty() && Gdx.files.local(skinPath).exists()) {
            skin = new Texture(Gdx.files.local(skinPath));
        } else {
            skin = new Texture(Gdx.files.internal("textures/player.png"));
        }
        player = new Player(skin);
        botSkin = loadBotSkin(skinPath);

        // Player always spawns at the south (high-Z) side so the camera looks north consistently.
        // Bot spawns at the north (low-Z) side.  If the map has explicit spawn cells we pick the
        // more-south one for the player; otherwise we use map boundary fallbacks.
        int[] spawnA = map.findPlayerSpawn();
        int[] spawnB = map.findBotSpawn();
        int[] playerSpawnCell, botSpawnCell;
        if (spawnA != null && spawnB != null) {
            // Choose the spawn with the higher row index (= higher Z = south) for the player
            if (spawnA[1] >= spawnB[1]) { playerSpawnCell = spawnA; botSpawnCell = spawnB; }
            else                         { playerSpawnCell = spawnB; botSpawnCell = spawnA; }
        } else {
            playerSpawnCell = spawnA;
            botSpawnCell    = spawnB;
        }

        float bMinX = map.worldX(1), bMaxX = map.worldX(map.cols() - 2);
        float bMinZ = map.worldZ(1), bMaxZ = map.worldZ(map.rows() - 2);

        // Player at south; fallback = near bottom of playable area
        float sx = playerSpawnCell != null ? map.worldX(playerSpawnCell[0]) : 0f;
        float sz = playerSpawnCell != null ? map.worldZ(playerSpawnCell[1]) : bMaxZ * 0.7f;
        player.setSpawn(sx, sz);

        // Bot at north; fallback = near top of playable area
        float botStartX = botSpawnCell != null ? map.worldX(botSpawnCell[0]) : 0f;
        float botStartZ = botSpawnCell != null ? map.worldZ(botSpawnCell[1]) : bMinZ * 0.7f;

        // Match intro: pan the camera from the corner diagonally opposite the player into the follow pose.
        float cornerX = sx <= 0f ? map.worldX(map.cols() - 1) : map.worldX(0);
        float cornerZ = sz <= 0f ? map.worldZ(map.rows() - 1) : map.worldZ(0);
        cameraRig.beginIntro(new Vector3(cornerX, 30f, cornerZ), new Vector3(0f, 1f, 0f), INTRO_DUR);
        gas = new GasZone(map);
        match = new MatchManager(gas, map);
        water = new AnimatedWaterRenderer(map);
        endScreen = new EndScreenOverlay();

        bot = new AiBrawler(botSkin, botStartX, botStartZ, bMinX, bMaxX, bMinZ, bMaxZ);
        if (difficultyScale != 1f) bot.setDifficultyScale(difficultyScale);
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

    /** Loads a skin texture that is visually distinct from the player's skin. */
    private Texture loadBotSkin(String playerSkinPath) {
        com.badlogic.gdx.files.FileHandle dir = Gdx.files.local("skins");
        if (dir.exists() && dir.isDirectory()) {
            java.util.List<com.badlogic.gdx.files.FileHandle> candidates = new java.util.ArrayList<>();
            for (com.badlogic.gdx.files.FileHandle f : dir.list(".png")) {
                if (!f.path().equals(playerSkinPath)) candidates.add(f);
            }
            if (!candidates.isEmpty()) {
                com.badlogic.gdx.files.FileHandle pick =
                    candidates.get(MathUtils.random(candidates.size() - 1));
                try { return new Texture(pick); } catch (Exception ignored) {}
            }
        }
        // Player used a custom skin → bot gets the default skin (and vice versa)
        if (!playerSkinPath.isEmpty()) {
            return new Texture(Gdx.files.internal("textures/player.png"));
        }
        // No alternative available: both use the default (only happens on a bare install)
        return new Texture(Gdx.files.internal("textures/player.png"));
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
        if (!pause.isOpen() && !matchEnded) {
            if (networkRole == NetworkRole.CLIENT) {
                pollClientState();
                if (!intro) sendClientInput();
                cameraRig.update(d, localCameraTarget(), false);
                if (!intro) {
                    match.update(d);
                    water.update(d);
                    gas.update(d);
                    checkClientMatchEnd();
                }
            } else {
                if (networkRole == NetworkRole.HOST) pollRemoteInput();
                if (!intro) player.update(d, cameraRig.camera);
                cameraRig.update(d, player.getPosition(), player.isSprinting());
                if (intro) { /* gameplay (drops, ammo, gas, rival) resumes once the intro finishes */ }
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

                if (networkRole == NetworkRole.HOST) {
                    bot.updateRemote(d, remoteInput.forward, remoteInput.backward, remoteInput.left,
                        remoteInput.right, remoteInput.jump, remoteInput.sprint, remoteInput.attack,
                        remoteInput.aimDeg, player);
                } else {
                    bot.update(d, player);
                }
                if (gasTick && gas.inGas(bot.position().x, bot.position().z)) bot.damage(GAS_DAMAGE);
                if (bot.isDead() && brawlersLeft > 1) brawlersLeft = 1;
                checkMatchEnd(gasTick);
                if (Settings.get().justPressed(Settings.Action.DROP)) {
                    ItemStack d2 = ui.takeOneFromSelectedHotbar();
                    if (d2 != null) drops.add(new ItemEntity(d2, ui.iconTexture(d2.type),
                        player.getPosition().x, player.getPosition().z, player.getFacingDeg()));
                }
                } // end !intro gameplay
                if (networkRole == NetworkRole.HOST) sendServerState();
            }
        }
        renderWorld(d, intro);
    }

    private Vector3 localCameraTarget() {
        if (networkRole == NetworkRole.CLIENT && bot != null) return bot.position();
        return player.getPosition();
    }

    private void pollRemoteInput() {
        if (netServer == null) return;
        String line = netServer.pollClientInput();
        if (line == null || line.isEmpty()) return;
        remoteInput.parse(line);
    }

    private void sendClientInput() {
        if (netClient == null || !netClient.isConnected()) return;
        Settings cfg = Settings.get();
        boolean forward = Gdx.input.isKeyPressed(cfg.key(Settings.Action.FORWARD));
        boolean backward = Gdx.input.isKeyPressed(cfg.key(Settings.Action.BACKWARD));
        boolean left = Gdx.input.isKeyPressed(cfg.key(Settings.Action.LEFT));
        boolean right = Gdx.input.isKeyPressed(cfg.key(Settings.Action.RIGHT));
        boolean jump = Gdx.input.isKeyPressed(cfg.key(Settings.Action.JUMP));
        boolean sprint = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
            || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
        boolean attack = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        float aimDeg = aimDegFromMouse(bot != null ? bot.position() : player.getPosition());
        netClient.sendInput(String.format(Locale.US, "INPUT %d %d %d %d %d %d %d %.2f",
            bit(forward), bit(backward), bit(left), bit(right), bit(jump), bit(sprint), bit(attack), aimDeg));
    }

    private void pollClientState() {
        if (netClient == null) return;
        String line = netClient.pollServerState();
        if (line == null || line.isEmpty()) return;
        applyServerState(line);
    }

    private void sendServerState() {
        if (netServer == null || !netServer.isClientConnected()) return;
        Vector3 p = player.getPosition();
        Vector3 b = bot.position();
        netServer.sendState(String.format(Locale.US,
            "STATE %.3f %.3f %.3f %.3f %.2f %d %.3f %.3f %.3f %.3f %.2f %d",
            p.x, p.y, p.z, player.getHealth(), player.getFacingDeg(), bit(player.isEliminated()),
            b.x, b.y, b.z, bot.health(), bot.facingDeg(), bit(bot.isDead() || bot.isDying())));
    }

    private void applyServerState(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 13 || !"STATE".equals(parts[0])) return;
        try {
            float px = Float.parseFloat(parts[1]);
            float py = Float.parseFloat(parts[2]);
            float pz = Float.parseFloat(parts[3]);
            float ph = Float.parseFloat(parts[4]);
            float pf = Float.parseFloat(parts[5]);
            boolean pe = "1".equals(parts[6]);
            float bx = Float.parseFloat(parts[7]);
            float by = Float.parseFloat(parts[8]);
            float bz = Float.parseFloat(parts[9]);
            float bh = Float.parseFloat(parts[10]);
            float bf = Float.parseFloat(parts[11]);
            boolean be = "1".equals(parts[12]);
            player.setNetworkSnapshot(px, py, pz, ph, pf, pe);
            bot.setNetworkSnapshot(bx, by, bz, bh, bf, be);
            if ((pe || be) && brawlersLeft > 1) brawlersLeft = 1;
        } catch (NumberFormatException ignored) {
        }
    }

    private void checkClientMatchEnd() {
        if (matchEnded) return;
        boolean hostOut = player.isEliminated() || player.getHealth() <= 0f;
        boolean guestOut = bot.isDead() || bot.health() <= 0f;
        if (!hostOut && !guestOut) return;
        matchEnded = true;
        MatchManager.Outcome outcome = hostOut && guestOut
            ? MatchManager.Outcome.DRAW
            : (hostOut ? MatchManager.Outcome.RIVAL_WIN : MatchManager.Outcome.PLAYER_WIN);
        endScreen.show(outcome, playerStats, rivalStats, () -> game.setScreen(new MainMenuScreen(game)));
    }

    private float aimDegFromMouse(Vector3 origin) {
        if (cameraRig == null || cameraRig.camera == null) return Float.NaN;
        Ray ray = cameraRig.camera.getPickRay(Gdx.input.getX(), Gdx.input.getY());
        float planeY = origin.y + 1.0f;
        if (Math.abs(ray.direction.y) < 1e-5f) return Float.NaN;
        float t = (planeY - ray.origin.y) / ray.direction.y;
        if (t <= 0f) return Float.NaN;
        float dx = ray.origin.x + ray.direction.x * t - origin.x;
        float dz = ray.origin.z + ray.direction.z * t - origin.z;
        if (dx * dx + dz * dz < 0.04f) return Float.NaN;
        return MathUtils.atan2(-dx, -dz) * MathUtils.radiansToDegrees;
    }

    private static int bit(boolean b) { return b ? 1 : 0; }

    private static final class RemoteInput {
        boolean forward, backward, left, right, jump, sprint, attack;
        float aimDeg = Float.NaN;

        void parse(String line) {
            String[] p = line.trim().split("\\s+");
            if (p.length < 9 || !"INPUT".equals(p[0])) return;
            forward = "1".equals(p[1]);
            backward = "1".equals(p[2]);
            left = "1".equals(p[3]);
            right = "1".equals(p[4]);
            jump = "1".equals(p[5]);
            sprint = "1".equals(p[6]);
            attack = "1".equals(p[7]);
            try { aimDeg = Float.parseFloat(p[8]); }
            catch (NumberFormatException e) { aimDeg = Float.NaN; }
        }
    }

    private void renderWorld(float d, boolean intro) {
        // Defensive check for lifecycle issues during screen transitions
        if (modelBatch == null) return;

        renderer.rebuildIfDirty();

        boolean canShadow = shadowsEnabled && shadowLight != null && shadowLight.getFrameBuffer() != null;
        if (canShadow) {
            shadowLight.begin(localCameraTarget(), SUN_DIR);
            if (shadowBatch != null) shadowBatch.begin(shadowLight.getCamera());
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
            Vector3 focus = localCameraTarget();
            float facing = networkRole == NetworkRole.CLIENT ? bot.facingDeg() : player.getFacingDeg();
            ground.renderPlayer(cameraRig.camera, focus.x, focus.z, PLAYER_RING_RADIUS, facing);
        }

        modelBatch.begin(cameraRig.camera);
        if (!player.isEliminated()) {
            player.render(modelBatch, environment);
            armor.render(modelBatch, environment, player.getModelInstance());
        }
        bot.render(modelBatch, environment);
        for (ItemEntity e : drops) e.render(modelBatch, environment);
        modelBatch.end();

        if (!intro && !matchEnded && !player.isEliminated() && networkRole != NetworkRole.CLIENT) {
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
            platePos.set(player.getPosition().x, player.getPosition().y + 5.5f, player.getPosition().z);
            overhead.render(cameraRig.camera, platePos,
                networkRole == NetworkRole.CLIENT ? "Host" : com.brawlgame.game.PlayerProfile.get().playerName,
                player.getHealth(), player.getMaxHealth());
        }
        if (!bot.isDead() && !bot.isDying()) {
            botPlatePos.set(bot.position().x, bot.position().y + 5.5f, bot.position().z);
            overhead.renderSimple(cameraRig.camera, botPlatePos,
                networkRole == NetworkRole.CLIENT ? "You" : (networkRole == NetworkRole.HOST ? "Guest" : "Rival"),
                bot.health(), bot.maxHealth());
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
            // Record result in persistent player profile
            if (match.outcome() == MatchManager.Outcome.PLAYER_WIN)
                com.brawlgame.game.PlayerProfile.get().recordWin();
            else if (match.outcome() == MatchManager.Outcome.RIVAL_WIN)
                com.brawlgame.game.PlayerProfile.get().recordLoss();
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
        if (netServer != null) netServer.close();
        if (netClient != null) netClient.close();
        for (ItemEntity e : drops) e.dispose();
        drops.clear();
        ui.dispose();
        debug.dispose();
        skin.dispose();
        if (botSkin != null && botSkin != skin) botSkin.dispose();
        modelBatch = null;
    }
}
