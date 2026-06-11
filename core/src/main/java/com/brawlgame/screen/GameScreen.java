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
import com.badlogic.gdx.graphics.glutils.HdpiUtils;
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
import com.brawlgame.audio.AudioManager;
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
    private Inventory remoteInventory;
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
    private float netAccum = 0f;
    private static final float NET_TICK = 1f / 20f;

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
        AudioManager.get().stopMenuMusic();
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
        // Guest camera sits on the north side so their character always appears at screen-bottom.
        if (networkRole == NetworkRole.CLIENT) cameraRig.setFlipped(true);
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
        if (networkRole == NetworkRole.SOLO) {
            // In solo mode apply difficulty: scales HP, speed, and regen for the AI rival.
            bot.setDifficultyScale(difficultyScale);
            player.setDifficultyScale(difficultyScale);
            if (difficultyScale <= 1.01f) {
                bot.setRegenParams(0.05f, 6.0f);
            } else if (difficultyScale <= 1.51f) {
                bot.setRegenParams(0.12f, 4.0f);
            } else {
                bot.setRegenParams(0.25f, 2.5f);
            }
        } else {
            // In multiplayer the bot IS the guest player — keep full 20 HP (same as host).
            player.setRegenParams(0.15f, 4.0f);
            bot.setRegenParams(0.15f, 4.0f);
        }
        bot.setHazardChecker((x, z) -> gas != null && gas.isActive() && gas.inGas(x, z));
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
        // POTATO_GUN: only for single player (removed for multiplayer due to lag)
        if (networkRole == NetworkRole.SOLO) {
            inventory.set(Inventory.HOTBAR_BASE + 1, new ItemStack(ItemType.POTATO_GUN));
        } else {
            inventory.set(Inventory.HOTBAR_BASE + 1, new ItemStack(ItemType.IRON_SWORD));
        }
        inventory.set(Inventory.ARMOR_BASE + 0, new ItemStack(ItemType.DIAMOND_HELMET));
        inventory.set(Inventory.ARMOR_BASE + 1, new ItemStack(ItemType.DIAMOND_CHESTPLATE));
        inventory.set(Inventory.ARMOR_BASE + 2, new ItemStack(ItemType.DIAMOND_LEGGINGS));
        inventory.set(Inventory.ARMOR_BASE + 3, new ItemStack(ItemType.DIAMOND_BOOTS));

        showcase = new CharacterShowcase();

        if (networkRole == NetworkRole.CLIENT) {
            remoteInventory = new Inventory();
            remoteInventory.set(Inventory.HOTBAR_BASE + 0, new ItemStack(ItemType.DIAMOND_SWORD));
            // POTATO_GUN removed for multiplayer - client (local guest) uses sword only
            remoteInventory.set(Inventory.HOTBAR_BASE + 1, new ItemStack(ItemType.IRON_SWORD));
            remoteInventory.set(Inventory.ARMOR_BASE + 0, new ItemStack(ItemType.DIAMOND_HELMET));
            remoteInventory.set(Inventory.ARMOR_BASE + 1, new ItemStack(ItemType.DIAMOND_CHESTPLATE));
            remoteInventory.set(Inventory.ARMOR_BASE + 2, new ItemStack(ItemType.DIAMOND_LEGGINGS));
            remoteInventory.set(Inventory.ARMOR_BASE + 3, new ItemStack(ItemType.DIAMOND_BOOTS));

            bot.setArmorInventory(inventory);
            // POTATO_GUN removed for multiplayer - no gun supplier needed
            bot.setAimDegFromMouseSupplier(() -> aimDegFromMouse(bot.position()));
            player.setInventory(remoteInventory);
            armor = new ArmorRenderer(remoteInventory); // remote host's armor
            ui = new PlayerUI(inventory);
            player.getWeapon().setIconResolver(ui::iconTexture);
            ui.setPreviewSkin(botSkin);
            pause = new PauseOverlay(game, botSkin);

            showcasePlayer = showcase.add(botSkin, inventory);
            showcaseRival = showcase.add(skin, remoteInventory);
            matchIntro = new MatchIntro(showcase, new int[] {showcasePlayer, showcaseRival},
                new String[] {"You", "Host"}, INTRO_DUR);
        } else {
            ui = new PlayerUI(inventory);
            armor = new ArmorRenderer(inventory); // player's armor
            player.setHeldItemSupplier(ui::selectedItem);
            player.getWeapon().setIconResolver(ui::iconTexture);
            player.setInventory(inventory);
            ui.setPreviewSkin(skin);
            pause = new PauseOverlay(game, skin);

            showcasePlayer = showcase.add(skin, inventory);
            showcaseRival = showcase.add(botSkin, bot != null ? bot.armorInventory() : null);
            matchIntro = new MatchIntro(showcase, new int[] {showcasePlayer, showcaseRival},
                new String[] {"You", networkRole == NetworkRole.HOST ? "Guest" : "Rival"}, INTRO_DUR);
        }

        endScreen.bindShowcase(showcase, showcasePlayer, showcaseRival);

        // HOST: pre-seed the bot's (guest's) armour with diamond so onHit() can reduce damage
        // from the very first frame. The client will confirm/update the pieces each INPUT message.
        // Without this, the bot's gear Inventory starts empty → full damage until the first packet.
        if (networkRole == NetworkRole.HOST) {
            Inventory botGear = bot.armorInventory();
            botGear.set(Inventory.ARMOR_BASE + 0, new ItemStack(ItemType.DIAMOND_HELMET));
            botGear.set(Inventory.ARMOR_BASE + 1, new ItemStack(ItemType.DIAMOND_CHESTPLATE));
            botGear.set(Inventory.ARMOR_BASE + 2, new ItemStack(ItemType.DIAMOND_LEGGINGS));
            botGear.set(Inventory.ARMOR_BASE + 3, new ItemStack(ItemType.DIAMOND_BOOTS));
        }

        ui.setDropHandler(stack -> drops.add(new ItemEntity(stack, ui.iconTexture(stack.type),
            player.getPosition().x, player.getPosition().z, player.getFacingDeg())));

        // UI gets first dibs on clicks/keys; the world polls the rest.
        uiMux = new InputMultiplexer(ui);
        Gdx.input.setInputProcessor(uiMux);

        debug = new DebugRenderer();
    }

    /** Loads a skin texture that is visually distinct from the player's skin. */
    private Texture loadBotSkin(String playerSkinPath) {
        com.badlogic.gdx.files.FileHandle dir = Gdx.files.local("assets/skins");
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

        boolean isMultiplayer = networkRole != NetworkRole.SOLO;
        boolean shouldUpdateWorld = !pause.isOpen() || isMultiplayer;

        netAccum += d;
        boolean netTick = netAccum >= NET_TICK;
        if (netTick) netAccum -= NET_TICK;

        if (shouldUpdateWorld && !matchEnded) {
            if (networkRole == NetworkRole.CLIENT) {
                pollClientState();
                if (netTick && !intro) sendClientInput();
                cameraRig.update(d, localCameraTarget(), false);
                if (!intro) {
                    bot.updateClient(d, player, true);
                    player.updateRemoteClient(d);
                    overhead.update(d, bot.getAmmo(), bot.getAmmoCapacity(), bot.pollDryFire());
                    match.update(d);
                    water.update(d);
                    gas.update(d);
                    checkClientMatchEnd();
                }
            } else {
                if (networkRole == NetworkRole.HOST) pollRemoteInput();
                boolean controllable = !pause.isOpen();
                if (!intro) player.update(d, cameraRig.camera, controllable);
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
                        remoteInput.gun, remoteInput.aimDeg, player);
                } else {
                    bot.update(d, player);
                }
                if (gasTick && gas.inGas(bot.position().x, bot.position().z)) bot.damage(GAS_DAMAGE);
                if (bot.isDead() && brawlersLeft > 1) brawlersLeft = 1;
                checkMatchEnd(gasTick);
                if (controllable && Settings.get().justPressed(Settings.Action.DROP)) {
                    ItemStack d2 = ui.takeOneFromSelectedHotbar();
                    if (d2 != null) drops.add(new ItemEntity(d2, ui.iconTexture(d2.type),
                        player.getPosition().x, player.getPosition().z, player.getFacingDeg()));
                }
                } // end !intro gameplay
                if (netTick && networkRole == NetworkRole.HOST) sendServerState();
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
        if (bot != null && remoteInput.arm0 != null) {
            setArmorSlot(bot.armorInventory(), 0, remoteInput.arm0);
            setArmorSlot(bot.armorInventory(), 1, remoteInput.arm1);
            setArmorSlot(bot.armorInventory(), 2, remoteInput.arm2);
            setArmorSlot(bot.armorInventory(), 3, remoteInput.arm3);
        }
    }

    private void sendClientInput() {
        if (netClient == null || !netClient.isConnected()) return;
        boolean forward = false, backward = false, left = false, right = false, jump = false, sprint = false, attack = false, gun = false;
        float aimDeg = Float.NaN;

        if (!pause.isOpen()) {
            Settings cfg = Settings.get();
            forward = Gdx.input.isKeyPressed(cfg.key(Settings.Action.FORWARD));
            backward = Gdx.input.isKeyPressed(cfg.key(Settings.Action.BACKWARD));
            left = Gdx.input.isKeyPressed(cfg.key(Settings.Action.LEFT));
            right = Gdx.input.isKeyPressed(cfg.key(Settings.Action.RIGHT));
            jump = Gdx.input.isKeyPressed(cfg.key(Settings.Action.JUMP));
            // The guest camera is flipped 180° (north side, looking south) so all movement
            // directions are inverted relative to screen. Swap both axis pairs to compensate.
            boolean tmp = forward; forward = backward; backward = tmp;
            tmp = left; left = right; right = tmp;
            sprint = jump && (forward || backward || left || right);
            attack = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
            gun = ui != null && ui.selectedItem() == ItemType.POTATO_GUN;
            aimDeg = aimDegFromMouse(bot != null ? bot.position() : player.getPosition());
        }

        ItemStack arm0 = inventory.get(Inventory.ARMOR_BASE + 0);
        ItemStack arm1 = inventory.get(Inventory.ARMOR_BASE + 1);
        ItemStack arm2 = inventory.get(Inventory.ARMOR_BASE + 2);
        ItemStack arm3 = inventory.get(Inventory.ARMOR_BASE + 3);

        netClient.sendInput(String.format(Locale.US, "INPUT %d %d %d %d %d %d %d %d %.2f %s %s %s %s",
            bit(forward), bit(backward), bit(left), bit(right), bit(jump), bit(sprint),
            bit(attack), bit(gun), aimDeg,
            arm0 != null ? arm0.type.name() : "NONE",
            arm1 != null ? arm1.type.name() : "NONE",
            arm2 != null ? arm2.type.name() : "NONE",
            arm3 != null ? arm3.type.name() : "NONE"));
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

        ItemStack h0 = player.getInventory().get(Inventory.ARMOR_BASE + 0);
        ItemStack h1 = player.getInventory().get(Inventory.ARMOR_BASE + 1);
        ItemStack h2 = player.getInventory().get(Inventory.ARMOR_BASE + 2);
        ItemStack h3 = player.getInventory().get(Inventory.ARMOR_BASE + 3);

        ItemStack b0 = bot.armorInventory().get(Inventory.ARMOR_BASE + 0);
        ItemStack b1 = bot.armorInventory().get(Inventory.ARMOR_BASE + 1);
        ItemStack b2 = bot.armorInventory().get(Inventory.ARMOR_BASE + 2);
        ItemStack b3 = bot.armorInventory().get(Inventory.ARMOR_BASE + 3);

        netServer.sendState(String.format(Locale.US,
            "STATE %.3f %.3f %.3f %.3f %.2f %d %.3f %.3f %.3f %.3f %.2f %d %d %d %d %d %d %d %d %d %s %s %s %s",
            p.x, p.y, p.z, player.getHealth(), player.getFacingDeg(), bit(player.isEliminated()),
            b.x, b.y, b.z, bot.health(), bot.facingDeg(), bit(bot.isDead() || bot.isDying()),
            bit(bot.isMoving()), bit(remoteInput.sprint), bit(bot.isAttacking()), 0, // gun removed
            bit(player.isMoving()), bit(player.isSprinting()), 0, // gun removed for player too
            bit(player.getWeapon().isAttacking()),
            h0 != null ? h0.type.name() : "NONE",
            h1 != null ? h1.type.name() : "NONE",
            h2 != null ? h2.type.name() : "NONE",
            h3 != null ? h3.type.name() : "NONE"));
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
            boolean bm = parts.length > 13 && "1".equals(parts[13]);
            boolean bs = parts.length > 14 && "1".equals(parts[14]);
            boolean ba = parts.length > 15 && "1".equals(parts[15]);
            boolean bg = parts.length > 16 && "1".equals(parts[16]);
            boolean pm = parts.length > 17 && "1".equals(parts[17]);
            boolean ps = parts.length > 18 && "1".equals(parts[18]);
            boolean pg = parts.length > 19 && "1".equals(parts[19]);
            boolean pa = parts.length > 20 && "1".equals(parts[20]);
            player.setNetworkSnapshot(px, py, pz, ph, pf, pe, pm, ps, pg ? 2 : 1, pa);
            bot.setNetworkSnapshot(bx, by, bz, bh, bf, be, bm, bs, ba, bg ? 2 : 1);
            if ((pe || be) && brawlersLeft > 1) brawlersLeft = 1;

            if (parts.length >= 25) {
                String h0 = parts[21];
                String h1 = parts[22];
                String h2 = parts[23];
                String h3 = parts[24];
                setArmorSlot(remoteInventory, 0, h0);
                setArmorSlot(remoteInventory, 1, h1);
                setArmorSlot(remoteInventory, 2, h2);
                setArmorSlot(remoteInventory, 3, h3);
            }
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
        boolean forward, backward, left, right, jump, sprint, attack, gun;
        float aimDeg = Float.NaN;
        String arm0 = "NONE", arm1 = "NONE", arm2 = "NONE", arm3 = "NONE";

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
            gun = p.length >= 10 && "1".equals(p[8]);
            int aimIndex = p.length >= 10 ? 9 : 8;
            try { aimDeg = Float.parseFloat(p[aimIndex]); }
            catch (NumberFormatException e) { aimDeg = Float.NaN; }
            if (p.length >= 14) {
                arm0 = p[10];
                arm1 = p[11];
                arm2 = p[12];
                arm3 = p[13];
            }
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

        HdpiUtils.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
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
        } else if (!intro && !matchEnded && networkRole == NetworkRole.CLIENT && bot != null && !bot.isDead() && !bot.isDying()) {
            // Client mode: show aim indicator for 'bot' (the local guest)
            // Use local calculation for immediate feedback (less lag)
            // POTATO_GUN removed - only sword for multiplayer
            float localAim = aimDegFromMouse(bot.position());
            if (!Float.isNaN(localAim)) {
                boolean attacking = bot.isAttacking() || Gdx.input.isButtonPressed(Input.Buttons.LEFT);
                if (attacking) {
                    aimCone.render(cameraRig.camera, bot.position().x, bot.position().z,
                        localAim, 70f, 2.6f); // SWORD_HALF = 70f, SWORD_REACH = 2.6f
                }
            }
        }

        if (showDebug) debug.render(cameraRig.camera, player);

        if (networkRole == NetworkRole.CLIENT) {
            // Client: local player is bot (needs local HUD), host is player (needs overhead HUD)
            if (!bot.isDead() && !bot.isDying()) {
                overhead.renderLocal(com.brawlgame.game.PlayerProfile.get().playerName, bot.health(), bot.maxHealth());
            }
            if (!player.isEliminated()) {
                platePos.set(player.getPosition().x, player.getPosition().y + 2.45f, player.getPosition().z);
                overhead.renderSimple(cameraRig.camera, platePos, "Host", player.getHealth(), player.getMaxHealth());
            }
        } else {
            // Singleplayer/Host: local player is player (needs local HUD), bot/rival is bot (needs overhead HUD)
            if (!player.isEliminated()) {
                overhead.renderLocal(com.brawlgame.game.PlayerProfile.get().playerName, player.getHealth(), player.getMaxHealth());
            }
            if (!bot.isDead() && !bot.isDying()) {
                botPlatePos.set(bot.position().x, bot.position().y + 2.45f, bot.position().z);
                overhead.renderSimple(cameraRig.camera, botPlatePos,
                    networkRole == NetworkRole.HOST ? "Guest" : "Rival",
                    bot.health(), bot.maxHealth());
            }
        }
        overhead.renderLabel("Brawlers left: " + brawlersLeft);
        if (!intro && !matchEnded) match.renderTimer();
        // Hurt vignette: use LOCAL player's hurt state (bot for CLIENT, player for HOST/SOLO).
        float localHurtFrac = networkRole == NetworkRole.CLIENT ? bot.hurtFraction() : player.getHurtFraction();
        vignette.renderFlash(localHurtFrac);
        vignette.render(localHurtFrac);
        // Gas vignette: check LOCAL player's position.
        Vector3 localVigPos = networkRole == NetworkRole.CLIENT ? bot.position() : player.getPosition();
        boolean localGodMode = networkRole != NetworkRole.CLIENT && player.isGodMode();
        if (gas.isActive() && !localGodMode && gas.inGas(localVigPos.x, localVigPos.z)) {
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
        try {
            if (Gdx.input.getInputProcessor() != null) Gdx.input.setInputProcessor(null);
            modelBatch.dispose();
        } catch (Exception ignored) {}
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

    private void setArmorSlot(Inventory inv, int slot, String itemName) {
        if (inv == null) return;
        if ("NONE".equals(itemName)) {
            inv.set(Inventory.ARMOR_BASE + slot, null);
        } else {
            try {
                ItemType type = ItemType.valueOf(itemName);
                ItemStack current = inv.get(Inventory.ARMOR_BASE + slot);
                if (current == null || current.type != type) {
                    inv.set(Inventory.ARMOR_BASE + slot, new ItemStack(type));
                }
            } catch (Exception ignored) {}
        }
    }
}
