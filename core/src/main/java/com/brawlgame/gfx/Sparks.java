package com.brawlgame.gfx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

/**
 * A pool of tiny additive cubes that burst outward from a point — the "heavy hit" sparks spawned
 * where the sword connects. Each spark flies out with a little gravity and shrinks to nothing over
 * its short life; additive blending makes the cluster read as a flash of light rather than solid
 * boxes. Rendered inside the main {@link ModelBatch} pass (no environment → flat, self-lit colour).
 */
public final class Sparks implements Disposable {

    private static final int MAX = 64;
    private static final float GRAVITY = 7f;

    private static final class P {
        final Vector3 pos = new Vector3();
        final Vector3 vel = new Vector3();
        float life, maxLife, size;
        boolean alive;
    }

    private final P[] pool = new P[MAX];
    private final ModelInstance[] instances = new ModelInstance[MAX];
    private final Model cube;

    /** Default warm-yellow impact sparks (additive glow). */
    public Sparks() { this(new Color(1f, 0.93f, 0.65f, 1f), false); }

    /**
     * @param color particle colour
     * @param solid true = normal alpha blend (e.g. red hearts that read as solid voxels),
     *              false = additive glow (sparks)
     */
    public Sparks(Color color, boolean solid) {
        int dstFactor = solid ? GL20.GL_ONE_MINUS_SRC_ALPHA : GL20.GL_ONE;
        Material mat = new Material(
            ColorAttribute.createDiffuse(color),
            new BlendingAttribute(GL20.GL_SRC_ALPHA, dstFactor));
        ModelBuilder mb = new ModelBuilder();
        cube = mb.createBox(1f, 1f, 1f, mat, Usage.Position | Usage.Normal);

        for (int i = 0; i < MAX; i++) {
            pool[i] = new P();
            instances[i] = new ModelInstance(cube);
        }
    }

    /** Spawn a burst of {@code n} sparks at a world point. */
    public void burst(Vector3 at, int n) {
        for (int k = 0; k < n; k++) {
            P p = findFree();
            if (p == null) return;
            p.alive = true;
            p.pos.set(at);
            p.vel.set(
                MathUtils.random(-1f, 1f),
                MathUtils.random(0.2f, 1.2f),
                MathUtils.random(-1f, 1f)).nor().scl(MathUtils.random(2.5f, 5.5f));
            p.maxLife = MathUtils.random(0.18f, 0.34f);
            p.life = p.maxLife;
            p.size = MathUtils.random(0.05f, 0.11f);
        }
    }

    public void update(float delta) {
        for (P p : pool) {
            if (!p.alive) continue;
            p.life -= delta;
            if (p.life <= 0f) { p.alive = false; continue; }
            p.vel.y -= GRAVITY * delta;
            p.pos.mulAdd(p.vel, delta);
        }
    }

    public void render(ModelBatch batch) {
        for (int i = 0; i < MAX; i++) {
            P p = pool[i];
            if (!p.alive) continue;
            float s = p.size * (p.life / p.maxLife); // shrink → fade out
            instances[i].transform.setToTranslationAndScaling(
                p.pos.x, p.pos.y, p.pos.z, s, s, s);
            batch.render(instances[i]); // no environment: flat bright colour
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
