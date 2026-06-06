package com.brawlgame.gfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

/**
 * A pool of floating <b>heart billboards</b> that burst from a point — the Minecraft "love" hit
 * effect spawned where an attack connects. Each heart is a small camera-facing textured quad that
 * drifts UP with low gravity and a little outward spread, then fades out (shrink + alpha) over its
 * short life. Alpha-blended (not additive) so the red hearts stay solid/coloured instead of washing
 * out to white. Rendered inside the main {@link ModelBatch} pass.
 *
 * <p>Billboarding: the game uses a FIXED camera (CameraRig OFFSET {@code (0,11,7)} above/behind the
 * player, always looking at it — never rotates with input). So every heart can be tilted to face
 * that one constant camera direction instead of needing a live camera handed to {@link #render}.
 * The camera sits at {@code +OFFSET} from its target, so the heart's quad normal must point along
 * {@code OFFSET.nor()} toward the camera. We bake that orientation once and reuse it for all hearts.
 */
public final class Sparks implements Disposable {

    private static final int MAX = 64;
    private float gravity = 1.5f; // floaty by default — vanilla love particles drift up slowly

    /** Lower gravity = floatier particles (hearts ≈ 1.5, sparks ≈ 7). */
    public void setGravity(float g) { this.gravity = g; }

    private static final class P {
        final Vector3 pos = new Vector3();
        final Vector3 vel = new Vector3();
        float life, maxLife, size;
        boolean alive;
    }

    private final P[] pool = new P[MAX];
    private final ModelInstance[] instances = new ModelInstance[MAX];
    private final Model quad;
    private final Texture heartTex;

    /** Constant billboard orientation facing the fixed CameraRig camera (offset (0,11,7)). */
    private final Quaternion billboard = new Quaternion();

    /** Default red floating hearts (alpha-blended, camera-facing). */
    public Sparks() {
        this(Color.WHITE, true);
    }

    /**
     * Back-compat constructor: the burst is now always the textured red {@code heart.png} billboard,
     * so the {@code color}/{@code solid} args are ignored. Kept so existing callers compile.
     */
    public Sparks(Color color, boolean solid) {
        heartTex = new Texture(Gdx.files.internal("textures/fx/heart.png"));
        heartTex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        // Solid alpha blend so the red reads as a coloured heart, not a glowing white blob.
        Material mat = new Material(
            TextureAttribute.createDiffuse(heartTex),
            new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
            FloatAttribute.createAlphaTest(0.4f),       // drop fully-transparent texel corners
            IntAttribute.createCullFace(GL20.GL_NONE)); // double-sided so any tilt stays visible

        // Unit quad on the XY plane (normal +Z), centred on origin → scales/rotates cleanly.
        ModelBuilder mb = new ModelBuilder();
        quad = mb.createRect(
            -0.5f, -0.5f, 0f,
             0.5f, -0.5f, 0f,
             0.5f,  0.5f, 0f,
            -0.5f,  0.5f, 0f,
            0f, 0f, 1f, // normal
            mat, Usage.Position | Usage.Normal | Usage.TextureCoordinates);

        // Bake the billboard rotation once: the quad's +Z normal must point toward the camera,
        // which sits along OFFSET (0,11,7) from the target. setFromCross gives the rotation that
        // carries +Z onto that direction; hearts stay upright (no roll) so they read as hearts.
        Vector3 toCam = new Vector3(0f, 11f, 7f).nor();
        billboard.setFromCross(0f, 0f, 1f, toCam.x, toCam.y, toCam.z);

        for (int i = 0; i < MAX; i++) {
            pool[i] = new P();
            instances[i] = new ModelInstance(quad);
        }
    }

    /** Spawn a heart burst at a world point — drifts up and outward, then fades. */
    public void burst(Vector3 at, int n) {
        for (int k = 0; k < n; k++) {
            P p = findFree();
            if (p == null) return;
            p.alive = true;
            p.pos.set(at);
            // Mostly upward with a small outward spread — vanilla "love" pop.
            p.vel.set(
                MathUtils.random(-1f, 1f),
                MathUtils.random(0.2f, 1.2f) + 1.6f,
                MathUtils.random(-1f, 1f)).nor().scl(MathUtils.random(0.8f, 2.0f));
            p.maxLife = MathUtils.random(0.45f, 0.7f);
            p.life = p.maxLife;
            p.size = MathUtils.random(0.26f, 0.38f); // smaller hearts
        }
    }

    /** Alias kept for callers that asked for the "love hearts" feel explicitly. */
    public void burstHearts(Vector3 at, int n) { burst(at, n); }

    public void update(float delta) {
        for (P p : pool) {
            if (!p.alive) continue;
            p.life -= delta;
            if (p.life <= 0f) { p.alive = false; continue; }
            p.vel.y -= gravity * delta;
            p.pos.mulAdd(p.vel, delta);
        }
    }

    public void render(ModelBatch batch) {
        for (int i = 0; i < MAX; i++) {
            P p = pool[i];
            if (!p.alive) continue;
            float t = p.life / p.maxLife;
            float s = p.size * (0.4f + 0.6f * t); // shrink a little as it fades
            // Position + constant billboard rotation + uniform scale, no allocations.
            sclTmp.set(s, s, s);
            instances[i].transform.set(p.pos, billboard, sclTmp);
            // Fade alpha down over life so hearts dissolve instead of popping out.
            BlendingAttribute blend = (BlendingAttribute) instances[i].materials.get(0)
                .get(BlendingAttribute.Type);
            blend.opacity = MathUtils.clamp(t * 1.3f, 0f, 1f);
            batch.render(instances[i]);
        }
    }

    // Reusable scale vector to avoid per-call allocation in render().
    private final Vector3 sclTmp = new Vector3();

    private P findFree() {
        for (P p : pool) if (!p.alive) return p;
        return null;
    }

    @Override
    public void dispose() {
        quad.dispose();
        heartTex.dispose();
    }
}
