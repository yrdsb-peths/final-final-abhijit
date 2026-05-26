package com.brawlgame;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.graphics.g3d.environment.*;
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader;
import com.badlogic.gdx.graphics.g3d.utils.*;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.UBJsonReader;

public class BrawlGame extends ApplicationAdapter {

    ModelBatch modelBatch;
    Environment environment;
    PerspectiveCamera camera;

    // Set USE_REAL_MODELS = true once you have .g3db files in assets/models/
    static final boolean USE_REAL_MODELS = false;

    AssetManager assets;

    Model playerModel;
    ModelInstance player;
    AnimationController playerAnim;
    Vector3 playerPos = new Vector3(0, 0.5f, 0);
    static final float SPEED = 8f;
    boolean isMoving = false;

    Model groundModel;
    ModelInstance ground;

    // Map obstacles
    Model wallModel;
    Array<ModelInstance> walls = new Array<>();

    Model bulletModel;
    Array<Projectile> projectiles = new Array<>();
    static final float BULLET_SPEED = 20f;
    static final float BULLET_LIFETIME = 2f;

    static class Projectile {
        ModelInstance model;
        Vector3 position;
        Vector3 velocity;
        float lifetime;

        Projectile(Model bulletModel, Vector3 startPos, Vector3 dir) {
            position = new Vector3(startPos);
            velocity = new Vector3(dir).nor().scl(BULLET_SPEED);
            lifetime = BULLET_LIFETIME;
            model = new ModelInstance(bulletModel);
            model.transform.setToTranslation(position);
        }
    }

    @Override
    public void create() {
        modelBatch = new ModelBatch();
        assets = new AssetManager();

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.6f, 0.6f, 0.6f, 1f));
        environment.add(new DirectionalLight().set(0.9f, 0.9f, 0.9f, -1f, -0.8f, -0.2f));

        camera = new PerspectiveCamera(60, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.1f;
        camera.far = 500f;

        if (USE_REAL_MODELS) {
            loadRealModels();
        } else {
            loadProceduralModels();
        }
    }

    void loadRealModels() {
        G3dModelLoader loader = new G3dModelLoader(new UBJsonReader());

        // Load character — expects assets/models/character.g3db
        playerModel = loader.loadModel(Gdx.files.internal("models/character.g3db"));
        player = new ModelInstance(playerModel);
        player.transform.scl(0.01f); // scale down if model is huge — adjust as needed

        // Animation controller — matches animation IDs baked in the .g3db
        playerAnim = new AnimationController(player);
        playerAnim.setAnimation("idle", -1); // -1 = loop forever

        // Load map ground — expects assets/models/ground.g3db
        Model realGround = loader.loadModel(Gdx.files.internal("models/ground.g3db"));
        ground = new ModelInstance(realGround);

        // Load wall/obstacle prop — expects assets/models/wall.g3db
        wallModel = loader.loadModel(Gdx.files.internal("models/wall.g3db"));
        spawnWalls();

        buildBulletModel();
    }

    void loadProceduralModels() {
        ModelBuilder mb = new ModelBuilder();

        playerModel = mb.createBox(1f, 1f, 1f,
            new Material(ColorAttribute.createDiffuse(new Color(0.2f, 0.4f, 1f, 1f))),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        player = new ModelInstance(playerModel);

        groundModel = mb.createBox(100f, 0.1f, 100f,
            new Material(ColorAttribute.createDiffuse(new Color(0.8f, 0.65f, 0.4f, 1f))), // sandy color
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        ground = new ModelInstance(groundModel);
        ground.transform.setToTranslation(0, -0.05f, 0);

        // Procedural obstacles (boxes standing in for hay bales / crates)
        wallModel = mb.createBox(1.5f, 1.5f, 1.5f,
            new Material(ColorAttribute.createDiffuse(new Color(0.7f, 0.5f, 0.2f, 1f))),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        spawnWalls();

        buildBulletModel();
    }

    void buildBulletModel() {
        ModelBuilder mb = new ModelBuilder();
        bulletModel = mb.createBox(0.2f, 0.2f, 0.2f,
            new Material(ColorAttribute.createDiffuse(new Color(1f, 0.9f, 0.1f, 1f))),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
    }

    // Brawl Stars-style obstacle layout — symmetric grid of crates
    void spawnWalls() {
        int[][] layout = {
            {-8, -8}, {-8, 0}, {-8, 8},
            { 0, -8},          { 0, 8},
            { 8, -8}, { 8, 0}, { 8, 8},
            {-4, -4}, {-4, 4},
            { 4, -4}, { 4, 4},
        };
        for (int[] pos : layout) {
            ModelInstance wall = new ModelInstance(wallModel);
            wall.transform.setToTranslation(pos[0], 0.75f, pos[1]);
            walls.add(wall);
        }
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();

        boolean wasMoving = isMoving;
        handleMovement(delta);
        updateFacing();
        handleShooting();
        updateProjectiles(delta);

        if (playerAnim != null) {
            // Switch idle ↔ walk animation based on movement
            if (isMoving && !wasMoving) {
                playerAnim.animate("walk", -1, 0.2f);
            } else if (!isMoving && wasMoving) {
                playerAnim.animate("idle", -1, 0.2f);
            }
            playerAnim.update(delta);
        }

        updateCamera();

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClearColor(0.53f, 0.45f, 0.3f, 1f); // warm sandy sky
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(camera);
        if (ground != null) modelBatch.render(ground, environment);
        for (ModelInstance wall : walls) modelBatch.render(wall, environment);
        modelBatch.render(player, environment);
        for (Projectile p : projectiles) modelBatch.render(p.model, environment);
        modelBatch.end();
    }

    void handleMovement(float delta) {
        Vector3 move = new Vector3();
        if (Gdx.input.isKeyPressed(Input.Keys.W)) move.z -= 1;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) move.z += 1;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) move.x -= 1;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) move.x += 1;

        isMoving = move.len() > 0;
        if (isMoving) {
            move.nor().scl(SPEED * delta);
            playerPos.add(move);
            playerPos.x = MathUtils.clamp(playerPos.x, -48f, 48f);
            playerPos.z = MathUtils.clamp(playerPos.z, -48f, 48f);
        }
    }

    void updateFacing() {
        Ray ray = camera.getPickRay(Gdx.input.getX(), Gdx.input.getY());
        float dist = -ray.origin.y / ray.direction.y;
        Vector3 mouseWorld = new Vector3(ray.origin).mulAdd(ray.direction, dist);
        float angle = MathUtils.atan2(
            mouseWorld.z - playerPos.z,
            mouseWorld.x - playerPos.x
        ) * MathUtils.radiansToDegrees;
        player.transform.setToRotation(Vector3.Y, -angle).setTranslation(playerPos);
    }

    void handleShooting() {
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            Ray ray = camera.getPickRay(Gdx.input.getX(), Gdx.input.getY());
            float dist = -ray.origin.y / ray.direction.y;
            Vector3 mouseWorld = new Vector3(ray.origin).mulAdd(ray.direction, dist);
            Vector3 dir = new Vector3(
                mouseWorld.x - playerPos.x,
                0,
                mouseWorld.z - playerPos.z
            );
            if (dir.len2() > 0.001f) {
                projectiles.add(new Projectile(bulletModel, playerPos, dir));
            }
        }
    }

    void updateProjectiles(float delta) {
        for (int i = projectiles.size - 1; i >= 0; i--) {
            Projectile p = projectiles.get(i);
            p.lifetime -= delta;
            if (p.lifetime <= 0) {
                projectiles.removeIndex(i);
                continue;
            }
            p.position.mulAdd(p.velocity, delta);
            p.model.transform.setToTranslation(p.position);
        }
    }

    void updateCamera() {
        camera.position.set(playerPos.x, playerPos.y + 18f, playerPos.z + 13f);
        camera.lookAt(playerPos);
        camera.up.set(Vector3.Y);
        camera.update();
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        playerModel.dispose();
        if (groundModel != null) groundModel.dispose();
        wallModel.dispose();
        bulletModel.dispose();
        assets.dispose();
    }
}
