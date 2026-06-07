package com.brawlgame.gfx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

/**
 * A pool of small solid voxel "block" particles that splash outward from an impact point — the
 * Minecraft block-break look. Each is a tiny lit cube launched up-and-out, pulled down by gravity,
 * that shrinks and dies over a short life. Colours are mixed dirt-brown + potato-tan so a potato
 * smacking the ground throws up a believable spray of dirt and spud chunks.
 */
public final class BlockParticles implements Disposable {

    private static final int MAX = 64;
    private static final float GRAVITY = 16f;

    private static final Color DIRT = new Color(0.42f, 0.30f, 0.19f, 1f);
    private static final Color POTATO = new Color(0.82f, 0.66f, 0.42f, 1f);

    private static final class P {
        final Vector3 pos = new Vector3();
        final Vector3 vel = new Vector3();
        final Color color = new Color();
        float life, maxLife, size;
        boolean alive;
    }

    private final P[] pool = new P[MAX];
    private final ModelInstance[] instances = new ModelInstance[MAX];
    private final Model cube;
    private final Vector3 scl = new Vector3();

    public BlockParticles() {
        Material mat = new Material(ColorAttribute.createDiffuse(Color.WHITE));
        cube = new ModelBuilder().createBox(1f, 1f, 1f, mat, Usage.Position | Usage.Normal);
        for (int i = 0; i < MAX; i++) {
            pool[i] = new P();
            instances[i] = new ModelInstance(cube);
        }
    }

    /** Spawn an outward splash of {@code n} dirt/potato cubes from {@code at}. */
    public void burst(Vector3 at, int n) {
        for (int k = 0; k < n; k++) {
            P p = findFree();
            if (p == null) return;
            p.alive = true;
            p.pos.set(at);
            // Up-and-outward: full horizontal spread, biased upward.
            p.vel.set(MathUtils.random(-1f, 1f),
                      MathUtils.random(0.6f, 1.4f),
                      MathUtils.random(-1f, 1f)).nor().scl(MathUtils.random(2.2f, 5.0f));
            p.maxLife = MathUtils.random(0.45f, 0.85f);
            p.life = p.maxLife;
            p.size = MathUtils.random(0.10f, 0.18f);
            p.color.set(MathUtils.randomBoolean() ? DIRT : POTATO);
        }
    }

    public void update(float delta) {
        for (P p : pool) {
            if (!p.alive) continue;
            p.life -= delta;
            if (p.life <= 0f) { p.alive = false; continue; }
            p.vel.y -= GRAVITY * delta;
            p.pos.mulAdd(p.vel, delta);
            if (p.pos.y < 0f) { p.pos.y = 0f; p.vel.set(0f, 0f, 0f); } // settle on the floor
        }
    }

    public void render(ModelBatch batch, Environment env) {
        for (int i = 0; i < MAX; i++) {
            P p = pool[i];
            if (!p.alive) continue;
            float s = p.size * MathUtils.clamp(p.life / p.maxLife + 0.2f, 0.2f, 1f); // shrink as it dies
            scl.set(s, s, s);
            instances[i].transform.setToTranslationAndScaling(p.pos.x, p.pos.y, p.pos.z, s, s, s);
            ((ColorAttribute) instances[i].materials.get(0).get(ColorAttribute.Diffuse)).color.set(p.color);
            batch.render(instances[i], env);
        }
    }

    private P findFree() {
        for (P p : pool) if (!p.alive) return p;
        return null;
    }

    @Override
    public void dispose() {
        cube.dispose();
    }
}
