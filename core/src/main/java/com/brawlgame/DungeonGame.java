package com.brawlgame;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.brawlgame.combat.WeaponController;
import com.brawlgame.entity.CombatDummy;
import com.brawlgame.entity.Player;
import com.brawlgame.gfx.AimCone;
import com.brawlgame.render.CameraRig;
import com.brawlgame.render.DebugRenderer;
import com.brawlgame.render.GridRenderer;

/**
 * Phase 1 of the Minecraft Dungeons-style game: an accurate, skin-textured Minecraft player that
 * walks and sprints in an empty void, under a fixed Dungeons-style follow camera.
 */
public class DungeonGame extends ApplicationAdapter {

    private ModelBatch modelBatch;
    private Environment environment;
    private CameraRig cameraRig;
    private GridRenderer grid;
    private DebugRenderer debug;
    private boolean showDebug = false; // hidden by default; F3 toggles the hitbox overlay
    private Player player;
    private CombatDummy dummy;
    private AimCone aimCone;
    private Texture skin;

    /** Half-width of the gun's straight-shot rectangular aim reticle (world units). */
    private static final float GUN_AIM_HALF_WIDTH = 0.35f;

    @Override
    public void create() {
        modelBatch = new ModelBatch();

        // Soft, neutral lighting: bright ambient so the skin colours read true, plus a gentle key
        // light from the upper front so each cube face is shaded slightly differently (the look of
        // the reference renders).
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
    }

    @Override
    public void render() {
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 1f / 30f);

        if (Gdx.input.isKeyJustPressed(Input.Keys.F3)) showDebug = !showDebug;

        player.update(delta, cameraRig.camera);
        dummy.update(delta, player.getPosition());
        cameraRig.update(delta, player.getPosition(), player.isSprinting());

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClearColor(0.039f, 0.043f, 0.055f, 1f); // near-black void
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(cameraRig.camera);
        grid.render(modelBatch);
        dummy.render(modelBatch, environment);
        player.render(modelBatch, environment);
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
    }

    @Override
    public void resize(int width, int height) {
        if (cameraRig != null) cameraRig.resize(width, height);
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        grid.dispose();
        debug.dispose();
        player.dispose();
        dummy.dispose();
        aimCone.dispose();
        skin.dispose();
    }
}
