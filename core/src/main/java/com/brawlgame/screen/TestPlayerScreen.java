package com.brawlgame.screen;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.brawlgame.combat.WeaponController;
import com.brawlgame.entity.ArmorRenderer;
import com.brawlgame.entity.CombatDummy;
import com.brawlgame.entity.ItemEntity;
import com.brawlgame.entity.Player;
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
import com.brawlgame.game.PlayerProfile;

/**
 * The <b>Test Map</b> — a flat, open arena for feature testing.
 *
 * <p>Design goals:
 * <ul>
 *   <li>No walls, no spikes, no gas — pure open void.</li>
 *   <li>Three {@link CombatDummy}s arranged in a triangle; each takes real knockback on hits.</li>
 *   <li><b>Hybrid survival/creative</b>: health + infinite respawn, but pressing <b>E</b> opens
 *       the full creative palette so any weapon or armour can be equipped instantly.</li>
 *   <li>A floating sign near spawn: "Press E to open inventory and equip armor and weapons."</li>
 * </ul>
 */
public final class TestPlayerScreen implements Screen {

    private static final float GUN_AIM_HALF_WIDTH = 0.35f;
    private static final float PLAYER_RING_RADIUS  = 0.7f;

    // Three dummies in a triangle spread so every weapon angle is exercisable
    private static final float[][] DUMMY_XZ = {
        {  0f, -5.5f },
        { -4f, -7.5f },
        {  4f, -7.5f },
    };

    // Sign anchored just above ground in front of spawn
    private static final Vector3 SIGN_POS    = new Vector3(0f, 1.8f, -1.8f);
    private static final float   SIGN_RADIUS2 = 4f * 4f; // only render within 4 blocks

    private final Game game;

    private ModelBatch    modelBatch;
    private Environment   environment;
    private CameraRig     cameraRig;
    private GridRenderer  grid;
    private DebugRenderer debug;
    private boolean showDebug = false;

    private Player  player;
    private Texture skin;

    private final List<CombatDummy> dummies = new ArrayList<>();
    private AimCone       aimCone;
    private GroundIndicator ground;
    private PlayerUI      ui;
    private Inventory     inventory;
    private ArmorRenderer armor;
    private PauseOverlay  pause;
    private InputMultiplexer uiMux;

    private final List<ItemEntity> drops   = new ArrayList<>();
    private final OverheadHud   overhead   = new OverheadHud();
    private final DamageVignette vignette   = new DamageVignette();
    private final Vector3 platePos          = new Vector3();

    // Sign billboard (screen-space 2D overlay — no Scene2D dependency needed)
    private ShapeRenderer signShape;
    private SpriteBatch   signBatch;
    private BitmapFont    signFont;
    private GlyphLayout   signGlyph;
    private final com.brawlgame.ui.UiViewport signUiv = new com.brawlgame.ui.UiViewport();

    public TestPlayerScreen(Game game) { this.game = game; }

    @Override
    public void show() {
        modelBatch  = new ModelBatch();
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.66f, 0.66f, 0.70f, 1f));
        environment.add(new DirectionalLight().set(0.55f, 0.55f, 0.52f, -0.6f, -0.85f, -0.45f));

        cameraRig = new CameraRig();
        grid      = new GridRenderer(32, 1f);
        debug     = new DebugRenderer();

        // Load the player's chosen skin (falls back to default if none is set).
        String skinPath = PlayerProfile.get().selectedSkin;
        if (!skinPath.isEmpty()) {
            com.badlogic.gdx.files.FileHandle f = Gdx.files.local(skinPath);
            skin = f.exists() ? new Texture(f) : new Texture(Gdx.files.internal("textures/player.png"));
        } else {
            skin = new Texture(Gdx.files.internal("textures/player.png"));
        }
        player = new Player(skin);

        // Three training dummies — all registered as weapon targets so every hit lands.
        for (float[] xz : DUMMY_XZ) {
            CombatDummy d = new CombatDummy(skin, xz[0], xz[1]);
            dummies.add(d);
            player.getWeapon().addTarget(d);
        }

        aimCone = new AimCone();
        ground  = new GroundIndicator();

        // Full creative inventory — player can grab any weapon or armour from the palette
        inventory = new Inventory();
        ui        = new PlayerUI(inventory);
        ui.setCreativeMode(true);

        armor = new ArmorRenderer(inventory);
        player.setHeldItemSupplier(ui::selectedItem);
        player.getWeapon().setIconResolver(ui::iconTexture);
        player.setInventory(inventory);
        ui.setPreviewSkin(skin);

        pause = new PauseOverlay(game, skin);

        ui.setDropHandler(stack -> drops.add(new ItemEntity(
            stack, ui.iconTexture(stack.type),
            player.getPosition().x, player.getPosition().z, player.getFacingDeg())));

        uiMux = new InputMultiplexer(ui);
        Gdx.input.setInputProcessor(uiMux);

        // Sign rendering resources
        signShape = new ShapeRenderer();
        signBatch = new SpriteBatch();
        signFont  = new BitmapFont();
        signGlyph = new GlyphLayout();
    }

    @Override
    public void render(float deltaTime) {
        float delta = Math.min(deltaTime, 1f / 30f);

        if (!pause.isOpen() && Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (ui.isModalOpen()) ui.closeModal();
            else pause.open();
        }
        if (pause.isOpen() && Gdx.input.getInputProcessor() != pause)
            Gdx.input.setInputProcessor(pause);
        else if (!pause.isOpen() && Gdx.input.getInputProcessor() != uiMux)
            Gdx.input.setInputProcessor(uiMux);
        if (Gdx.input.isKeyJustPressed(Input.Keys.F3)) showDebug = !showDebug;

        if (pause.isOpen()) pause.update(delta);
        if (!ui.isModalOpen() && !pause.isOpen()) {
            player.update(delta, cameraRig.camera);
            for (CombatDummy d : dummies) d.update(delta, player.getPosition());
            cameraRig.update(delta, player.getPosition(), player.isSprinting());

            for (Iterator<ItemEntity> it = drops.iterator(); it.hasNext(); ) {
                ItemEntity e = it.next();
                if (e.update(delta, player.getPosition())) {
                    inventory.add(e.stack()); e.dispose(); it.remove();
                }
            }
            WeaponController wc = player.getWeapon();
            overhead.update(delta, wc.ammo(), wc.ammoCapacity(), wc.pollDryFire());
            if (Settings.get().justPressed(Settings.Action.DROP)) {
                ItemStack d = ui.takeOneFromSelectedHotbar();
                if (d != null) drops.add(new ItemEntity(
                    d, ui.iconTexture(d.type),
                    player.getPosition().x, player.getPosition().z, player.getFacingDeg()));
            }
        }

        // ---- 3D scene ----
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClearColor(0.039f, 0.043f, 0.055f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(cameraRig.camera);
        grid.render(modelBatch);
        modelBatch.end();

        for (CombatDummy d : dummies)
            ground.renderEnemy(cameraRig.camera, d.position().x, d.position().z, d.radius() + 0.15f);
        ground.renderPlayer(cameraRig.camera, player.getPosition().x, player.getPosition().z,
            PLAYER_RING_RADIUS, player.getFacingDeg());

        modelBatch.begin(cameraRig.camera);
        for (CombatDummy d : dummies) d.render(modelBatch, environment);
        player.render(modelBatch, environment);
        armor.render(modelBatch, environment, player.getModelInstance());
        for (ItemEntity e : drops) e.render(modelBatch, environment);
        modelBatch.end();

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

        // Raise the 3D projection point high above the model so the HUD clears the character.
        platePos.set(player.getPosition().x, player.getPosition().y + 2.45f, player.getPosition().z);
        overhead.render(cameraRig.camera, platePos, PlayerProfile.get().playerName,
            player.getHealth(), player.getMaxHealth());
        vignette.render(player.getHurtFraction());

        renderSign();

        ui.render();
        if (pause.isOpen()) pause.render();
        Settings.get().capFrame();
    }

    /**
     * Draws a world-anchored sign billboard in 2-D screen space.
     * Projects the sign's 3-D world position through the camera, then draws a dark panel
     * with white text at that screen location.  Fades out beyond 7 blocks.
     */
    private void renderSign() {
        Vector3 pp = player.getPosition();
        float dx = SIGN_POS.x - pp.x, dz = SIGN_POS.z - pp.z;
        if (dx * dx + dz * dz > SIGN_RADIUS2) return;

        // Distance-based opacity
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        float alpha = Math.max(0f, 1f - dist / 7f);

        // Project world → screen, then map to virtual canvas so sign scales with fullscreen.
        Vector3 projected = cameraRig.camera.project(new Vector3(SIGN_POS));
        if (projected.z >= 1f) return; // behind the camera

        com.badlogic.gdx.math.Vector2 vc = new com.badlogic.gdx.math.Vector2();
        signUiv.unproject(projected.x, projected.y, vc);
        float W = signUiv.width(), H = signUiv.height();
        float sx = vc.x, sy = vc.y;

        Matrix4 ortho = signUiv.combined();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        signUiv.apply();

        // Compute text width first so we can centre the panel
        signFont.getData().setScale(1.0f);
        String[] lines = { "Press  E  to open inventory", "and equip armor and weapons." };
        float maxW = 0;
        for (String l : lines) { signGlyph.setText(signFont, l); maxW = Math.max(maxW, signGlyph.width); }
        float lineH = 22f;
        float panW  = maxW + 28f, panH = lines.length * lineH + 22f;
        float panX  = sx - panW * 0.5f, panY = sy + 4f;

        // Dark translucent background
        signShape.setProjectionMatrix(ortho);
        signShape.begin(ShapeType.Filled);
        signShape.setColor(0.05f, 0.05f, 0.07f, 0.88f * alpha);
        signShape.rect(panX, panY, panW, panH);
        // Bright amber border — makes it instantly readable
        signShape.setColor(1f, 0.84f, 0.12f, 0.95f * alpha);
        signShape.rect(panX,           panY,            panW, 2f);
        signShape.rect(panX,           panY + panH - 2f, panW, 2f);
        signShape.rect(panX,           panY,            2f,  panH);
        signShape.rect(panX + panW - 2f, panY,          2f,  panH);
        signShape.end();

        // Text
        signBatch.setProjectionMatrix(ortho);
        signBatch.begin();
        float textY = panY + panH - 12f;
        for (String l : lines) {
            signGlyph.setText(signFont, l);
            signFont.setColor(1f, 1f, 1f, alpha);
            signFont.draw(signBatch, l, panX + (panW - signGlyph.width) * 0.5f, textY);
            textY -= lineH;
        }
        signFont.getData().setScale(1f);
        signBatch.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    @Override
    public void resize(int width, int height) {
        if (cameraRig != null) cameraRig.resize(width, height);
        if (ui != null)        ui.resize(width, height);
        overhead.resize(width, height);
        signUiv.resize(width, height);
    }

    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void hide() { dispose(); }

    @Override
    public void dispose() {
        if (modelBatch == null) return;
        if (Gdx.input.getInputProcessor() != null) Gdx.input.setInputProcessor(null);
        modelBatch.dispose();
        grid.dispose();
        debug.dispose();
        player.dispose();
        for (CombatDummy d : dummies) d.dispose();
        dummies.clear();
        aimCone.dispose();
        ground.dispose();
        armor.dispose();
        pause.dispose();
        overhead.dispose();
        vignette.dispose();
        for (ItemEntity e : drops) e.dispose();
        drops.clear();
        ui.dispose();
        if (signShape != null) signShape.dispose();
        if (signBatch != null) signBatch.dispose();
        if (signFont  != null) signFont.dispose();
        skin.dispose();
        modelBatch = null;
    }
}
