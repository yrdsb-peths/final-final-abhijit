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
import com.brawlgame.combat.WeaponController;
import com.brawlgame.entity.ArmorRenderer;
import com.brawlgame.entity.ChestEntity;
import com.brawlgame.entity.CombatDummy;
import com.brawlgame.entity.ItemEntity;
import com.brawlgame.entity.Player;
import com.brawlgame.entity.SpikeHazard;
import com.brawlgame.gfx.AimCone;
import com.brawlgame.gfx.GroundIndicator;
import com.brawlgame.item.Inventory;
import com.brawlgame.item.ItemStack;
import com.brawlgame.item.ItemType;
import com.brawlgame.render.CameraRig;
import com.brawlgame.render.DebugRenderer;
import com.brawlgame.render.GridRenderer;
import com.brawlgame.ui.DamageVignette;
import com.brawlgame.ui.OverheadHud;
import com.brawlgame.ui.PauseOverlay;
import com.brawlgame.ui.PlayerUI;
import com.brawlgame.ui.Settings;

/**
 * The "Test Player" mode — Phase 1 of the game lifted verbatim into a {@link Screen}: an accurate,
 * skin-textured Minecraft player that walks and sprints in an empty void under the fixed
 * Dungeons-style follow camera, with the combat dummy and weapon VFX. ESC returns to the main menu.
 */
public final class TestPlayerScreen implements Screen {

    /** Half-width of the gun's straight-shot rectangular aim reticle (world units). */
    private static final float GUN_AIM_HALF_WIDTH = 0.35f;
    /** Radius of the green ground-highlight ring under the player (world units). */
    private static final float PLAYER_RING_RADIUS = 0.7f;

    private final Game game;

    private ModelBatch modelBatch;
    private Environment environment;
    private CameraRig cameraRig;
    private GridRenderer grid;
    private DebugRenderer debug;
    private boolean showDebug = false; // hidden by default; F3 toggles the hitbox overlay
    private Player player;
    private CombatDummy dummy;
    private ChestEntity chest;
    private AimCone aimCone;
    private GroundIndicator ground;
    private PlayerUI ui;
    private Inventory inventory;
    private ArmorRenderer armor;
    private SpikeHazard hazard;
    private PauseOverlay pause;
    private InputMultiplexer uiMux;
    private final List<ItemEntity> drops = new ArrayList<>(); // items dropped out of the inventory
    private final OverheadHud overhead = new OverheadHud();    // Brawl-style nameplate above the player
    private final DamageVignette vignette = new DamageVignette(); // red screen edges when hurt
    private final com.badlogic.gdx.math.Vector3 platePos = new com.badlogic.gdx.math.Vector3();
    private Texture skin, chestWood, chestGold;

    public TestPlayerScreen(Game game) {
        this.game = game;
    }

    @Override
    public void show() {
        modelBatch = new ModelBatch();

        // Soft, neutral lighting: bright ambient so the skin colours read true, plus a gentle key
        // light from the upper front so each cube face is shaded slightly differently.
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.66f, 0.66f, 0.70f, 1f));
        environment.add(new DirectionalLight().set(0.55f, 0.55f, 0.52f, -0.6f, -0.85f, -0.45f));

        cameraRig = new CameraRig();
        grid = new GridRenderer(32, 1f);
        debug = new DebugRenderer();

        skin = new Texture(Gdx.files.internal("textures/player.png"));
        player = new Player(skin);
        dummy = new CombatDummy(skin, 0f, -5f); // a few blocks in front of spawn
        player.getWeapon().setTarget(dummy);    // melee hits (and their hearts) resolve against it
        aimCone = new AimCone();
        ground = new GroundIndicator();

        // A demo chest a few blocks to the side so the hover-highlight + chest UI are exercisable.
        chestWood = new Texture(Gdx.files.internal("textures/blocks/oak_planks.png"));
        chestGold = new Texture(Gdx.files.internal("textures/blocks/gold_block.png"));
        chest = new ChestEntity(chestWood, chestGold, 3f, -3f);

        // Inventory + starter items (whitelist only): weapons on the hotbar, armour in the store.
        inventory = new Inventory();
        inventory.set(Inventory.HOTBAR_BASE + 0, new ItemStack(ItemType.DIAMOND_SWORD));
        inventory.set(Inventory.HOTBAR_BASE + 1, new ItemStack(ItemType.POTATO_GUN));
        inventory.set(Inventory.HOTBAR_BASE + 2, new ItemStack(ItemType.IRON_SWORD));
        inventory.set(Inventory.STORAGE_BASE + 0, new ItemStack(ItemType.DIAMOND_HELMET));
        inventory.set(Inventory.STORAGE_BASE + 1, new ItemStack(ItemType.DIAMOND_CHESTPLATE));
        inventory.set(Inventory.STORAGE_BASE + 2, new ItemStack(ItemType.DIAMOND_LEGGINGS));
        inventory.set(Inventory.STORAGE_BASE + 3, new ItemStack(ItemType.DIAMOND_BOOTS));
        inventory.set(Inventory.STORAGE_BASE + 9, new ItemStack(ItemType.IRON_HELMET));
        inventory.set(Inventory.STORAGE_BASE + 10, new ItemStack(ItemType.IRON_CHESTPLATE));
        // Equip a full diamond set so the rig-parented armour is visible on spawn (the store keeps
        // the iron pieces for drag-testing); swap any slot through the inventory UI.
        inventory.set(Inventory.ARMOR_BASE + 0, new ItemStack(ItemType.DIAMOND_HELMET));
        inventory.set(Inventory.ARMOR_BASE + 1, new ItemStack(ItemType.DIAMOND_CHESTPLATE));
        inventory.set(Inventory.ARMOR_BASE + 2, new ItemStack(ItemType.DIAMOND_LEGGINGS));
        inventory.set(Inventory.ARMOR_BASE + 3, new ItemStack(ItemType.DIAMOND_BOOTS));
        ui = new PlayerUI(inventory);
        armor = new ArmorRenderer(inventory); // worn pieces, parented to the player's rig bones
        player.setHeldItemSupplier(ui::selectedItem); // weapon follows the selected hotbar slot
        player.getWeapon().setIconResolver(ui::iconTexture); // armour/items are shown held in the fist
        player.setInventory(inventory); // worn armour feeds the damage-reduction formula
        ui.setPreviewSkin(skin);        // rotating 3D model in the inventory reflects equipped armour

        // A spike pad in front of spawn: step on it to take 10 raw damage/tick, reduced by your armour.
        hazard = new SpikeHazard(-2.5f, -1.5f);

        pause = new PauseOverlay(game, skin); // ESC pause menu (3D model + Bedrock buttons + options)

        // Drag an item out of the inventory panel → spawn it as a world entity at the player's feet.
        ui.setDropHandler(stack -> drops.add(new ItemEntity(stack, ui.iconTexture(stack.type),
            player.getPosition().x, player.getPosition().z, player.getFacingDeg())));

        // UI gets first dibs on clicks/keys; the world polls the rest.
        uiMux = new InputMultiplexer(ui);
        Gdx.input.setInputProcessor(uiMux);
    }

    @Override
    public void render(float deltaTime) {
        float delta = Math.min(deltaTime, 1f / 30f);

        // Esc: close an open inventory panel first, otherwise open the pause overlay (the overlay owns
        // its own Esc once open). Route input to the pause overlay while it's up.
        if (!pause.isOpen() && Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (ui.isModalOpen()) ui.closeModal();
            else pause.open();
        }
        if (pause.isOpen() && Gdx.input.getInputProcessor() != pause) Gdx.input.setInputProcessor(pause);
        else if (!pause.isOpen() && Gdx.input.getInputProcessor() != uiMux) Gdx.input.setInputProcessor(uiMux);
        if (Gdx.input.isKeyJustPressed(Input.Keys.F3)) showDebug = !showDebug;

        // Local-player hover highlight on the chest, and R-to-open within range.
        chest.updateHover(cameraRig.camera, Gdx.input.getX(), Gdx.input.getY());
        if (!ui.isModalOpen() && Gdx.input.isKeyJustPressed(Input.Keys.R)
                && chest.canInteract(player.getPosition(), ChestEntity.DEFAULT_RANGE)) {
            ui.openChest(chest.slots(), "Chest");
        }

        // Freeze world control while a panel or the pause menu is open.
        if (pause.isOpen()) pause.update(delta);
        if (!ui.isModalOpen() && !pause.isOpen()) {
            player.update(delta, cameraRig.camera);
            dummy.update(delta, player.getPosition());
            cameraRig.update(delta, player.getPosition(), player.isSprinting());
            // Spike pad: applies raw damage when stood on; the player's armour reduces it.
            float raw = hazard.update(delta, player.getPosition());
            if (raw > 0f) player.applyDamage(raw);
            // Dropped items: tumble + settle, and get picked back up on contact.
            for (Iterator<ItemEntity> it = drops.iterator(); it.hasNext(); ) {
                ItemEntity e = it.next();
                if (e.update(delta, player.getPosition())) { inventory.add(e.stack()); e.dispose(); it.remove(); }
            }
            // Overhead reload bar reflects the weapon's live ammo (the weapon owns the ammo + gating).
            WeaponController wc = player.getWeapon();
            overhead.update(delta, wc.ammo(), wc.ammoCapacity(), wc.pollDryFire());
            // Drop the selected item into the world (Q by default), tossed in the facing direction.
            if (Settings.get().justPressed(Settings.Action.DROP)) {
                ItemStack d = ui.takeOneFromSelectedHotbar();
                if (d != null) drops.add(new ItemEntity(d, ui.iconTexture(d.type),
                    player.getPosition().x, player.getPosition().z, player.getFacingDeg()));
            }
        }

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClearColor(0.039f, 0.043f, 0.055f, 1f); // near-black void
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        // Floor + world props (chest) first.
        modelBatch.begin(cameraRig.camera);
        grid.render(modelBatch);
        chest.render(modelBatch, environment);
        hazard.render(modelBatch, environment);
        modelBatch.end();

        // Ground highlight rings: after the floor, before the characters, so they stand on top.
        ground.renderEnemy(cameraRig.camera, dummy.position().x, dummy.position().z, dummy.radius() + 0.15f);
        ground.renderPlayer(cameraRig.camera, player.getPosition().x, player.getPosition().z,
            PLAYER_RING_RADIUS, player.getFacingDeg());

        // Character pass: dummy + player (models, held weapon, projectiles, hearts).
        modelBatch.begin(cameraRig.camera);
        dummy.render(modelBatch, environment);
        player.render(modelBatch, environment);
        armor.render(modelBatch, environment, player.getModelInstance()); // worn armour over the rig
        for (ItemEntity e : drops) e.render(modelBatch, environment); // spinning dropped items
        modelBatch.end();

        // Ground aim reticle (the cone/rectangle), then the additive swoosh — both over the scene.
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
        player.renderTrail(cameraRig.camera); // additive swoosh, over the scene

        if (showDebug) debug.render(cameraRig.camera, player);

        // Brawl-Stars-style nameplate floating above the player (name / HP / health bar / reload bar).
        // Anchor at the player's live world Y + head height so the plate rises/falls when jumping.
        platePos.set(player.getPosition().x, player.getPosition().y + 2.2f, player.getPosition().z);
        overhead.render(cameraRig.camera, platePos, "Player", player.getHealth(), player.getMaxHealth());
        vignette.render(player.getHurtFraction()); // red screen edges when the player is hurt

        // HUD on top of everything: hotbar always, plus the inventory/creative/chest panel when open.
        ui.render();

        if (pause.isOpen()) pause.render(); // pause overlay sits above the HUD
        Settings.get().capFrame();           // honour the Options FPS limit
    }

    @Override
    public void resize(int width, int height) {
        if (cameraRig != null) cameraRig.resize(width, height);
        if (ui != null) ui.resize(width, height);
    }

    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() { dispose(); }

    @Override
    public void dispose() {
        if (modelBatch == null) return; // never shown
        if (Gdx.input.getInputProcessor() != null) Gdx.input.setInputProcessor(null);
        modelBatch.dispose();
        grid.dispose();
        debug.dispose();
        player.dispose();
        dummy.dispose();
        chest.dispose();
        aimCone.dispose();
        ground.dispose();
        armor.dispose();
        hazard.dispose();
        pause.dispose();
        overhead.dispose();
        vignette.dispose();
        for (ItemEntity e : drops) e.dispose();
        drops.clear();
        ui.dispose();
        chestWood.dispose();
        chestGold.dispose();
        skin.dispose();
        modelBatch = null;
    }
}
