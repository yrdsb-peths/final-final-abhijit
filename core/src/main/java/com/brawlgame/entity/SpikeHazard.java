package com.brawlgame.entity;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.brawlgame.gfx.BlockParticles;

/**
 * A stationary spike pad: a dark iron base studded with red spikes. While the player stands on it, it
 * jabs them for a fixed amount of <b>raw</b> damage on a repeating tick (the player's worn armour then
 * reduces it via the vanilla formula). Step on to take damage, step off to stop and regen — so you can
 * compare armour tiers by repeatedly walking onto it. Purely a test harness for the armour system.
 */
public final class SpikeHazard implements Disposable {

    /** Raw damage per tick before armour reduction. */
    public static final float RAW_DAMAGE = 10f;
    private static final float TICK = 1.2f;       // seconds between jabs while standing on it
    private static final float RADIUS = 0.9f;     // how close the player must be to be pricked

    private final Model model;
    private final ModelInstance instance;
    private final Vector3 pos = new Vector3();
    private final BlockParticles pop = new BlockParticles(); // small puff on each jab
    private final Vector3 scratch = new Vector3();
    private float tickTimer = 0f;                  // <=0 → the next in-range frame deals damage

    public SpikeHazard(float x, float z) {
        pos.set(x, 0f, z);
        Material iron = new Material(ColorAttribute.createDiffuse(new Color(0.30f, 0.30f, 0.34f, 1f)),
            IntAttribute.createCullFace(GL20.GL_BACK));
        Material red = new Material(ColorAttribute.createDiffuse(new Color(0.82f, 0.16f, 0.16f, 1f)),
            IntAttribute.createCullFace(GL20.GL_BACK));

        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder base = mb.part("base", GL20.GL_TRIANGLES, Usage.Position | Usage.Normal, iron);
        BoxShapeBuilder.build(base, 0f, 0.075f, 0f, 1.4f, 0.15f, 1.4f); // flat pad
        MeshPartBuilder spikes = mb.part("spikes", GL20.GL_TRIANGLES, Usage.Position | Usage.Normal, red);
        float s = 0.4f; // spike grid offset
        for (float sx = -s; sx <= s; sx += s) {
            for (float sz = -s; sz <= s; sz += s) {
                BoxShapeBuilder.build(spikes, sx, 0.30f, sz, 0.14f, 0.36f, 0.14f); // upward red spike
            }
        }
        model = mb.end();
        instance = new ModelInstance(model);
        instance.transform.setToTranslation(pos);
    }

    /**
     * Advance the hazard. Returns the raw damage to apply to the player this frame (0 if the player
     * isn't standing on it, or the tick hasn't elapsed). Re-entry pricks immediately.
     */
    public float update(float delta, Vector3 playerPos) {
        pop.update(delta);
        float dx = playerPos.x - pos.x, dz = playerPos.z - pos.z;
        boolean on = dx * dx + dz * dz <= RADIUS * RADIUS;
        if (!on) { tickTimer = 0f; return 0f; }   // off the pad → reset so stepping back on hits at once
        tickTimer -= delta;
        if (tickTimer > 0f) return 0f;
        tickTimer = TICK;
        pop.burst(scratch.set(playerPos.x, playerPos.y + 0.6f, playerPos.z), 10); // jab puff
        return RAW_DAMAGE;
    }

    public Vector3 position() { return pos; }

    public void render(ModelBatch batch, Environment env) {
        batch.render(instance, env);
        pop.render(batch, env);
    }

    @Override
    public void dispose() {
        model.dispose();
        pop.dispose();
    }
}
