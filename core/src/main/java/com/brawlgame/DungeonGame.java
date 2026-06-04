package com.brawlgame;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.brawlgame.entity.Player;
import com.brawlgame.render.CameraRig;
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
    private Player player;
    private Texture skin;

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

        skin = new Texture(Gdx.files.internal("textures/player.png"));
        player = new Player(skin);
    }

    @Override
    public void render() {
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 1f / 30f);

        player.update(delta);
        cameraRig.update(delta, player.getPosition());

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClearColor(0.039f, 0.043f, 0.055f, 1f); // near-black void
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(cameraRig.camera);
        grid.render(modelBatch);
        player.render(modelBatch, environment);
        modelBatch.end();
    }

    @Override
    public void resize(int width, int height) {
        if (cameraRig != null) cameraRig.resize(width, height);
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        grid.dispose();
        player.dispose();
        skin.dispose();
    }
}
