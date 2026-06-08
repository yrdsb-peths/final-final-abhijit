package com.brawlgame.entity;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.brawlgame.combat.CombatTarget;
import com.brawlgame.gfx.Sparks;
import com.brawlgame.model.MinecraftPlayerModel;

/**
 * A stationary training dummy: the same rigged Minecraft model and skin as the player. When struck
 * it plays the Minecraft "hurt" reaction — flash red and tilt back away from the blow. It always
 * faces the player so the lean-back reads toward the camera. (The hit hearts are spawned by the
 * {@code WeaponController} at the moment of impact, so they only appear on an actual hit.)
 */
public final class CombatDummy implements CombatTarget, Disposable {

    private static final float HURT_DUR = 0.35f;
    private static final float TILT_MAX = 24f;     // degrees the model leans back on a hit
    private static final float HIT_RADIUS = 0.6f;
    private static final Color HURT_TINT = new Color(1f, 0.35f, 0.35f, 1f);

    private final Model model;
    private final ModelInstance instance;
    private final Vector3 pos = new Vector3();
    private float facingDeg;

    private float hurtTimer;
    private boolean tinted;
    private final ColorAttribute redTint = ColorAttribute.createDiffuse(HURT_TINT);

    // Knockback: velocity that decays over time so the dummy physically bounces on a hit.
    private static final float KB_STRENGTH = 4.0f;
    private static final float KB_FRICTION = 6.0f; // exponential decay rate (higher = snappier stop)
    private float knockVx, knockVz;
    private final Vector3 origin; // starting position — dummy drifts back after a hit

    // Hit hearts spawned ON this entity (at its chest) — so they appear on whatever is struck.
    private final Sparks hearts = new Sparks();
    private final Vector3 chestTmp = new Vector3();

    public CombatDummy(Texture skin, float x, float z) {
        model = MinecraftPlayerModel.build(skin);
        instance = new ModelInstance(model);
        pos.set(x, 0f, z);
        origin = new Vector3(x, 0f, z);
        applyTransform();
    }

    /** @param playerPos so the dummy turns to face its attacker. */
    public void update(float delta, Vector3 playerPos) {
        float dx = playerPos.x - pos.x, dz = playerPos.z - pos.z;
        if (dx * dx + dz * dz > 1e-4f) {
            facingDeg = MathUtils.atan2(-dx, -dz) * MathUtils.radiansToDegrees;
        }
        if (hurtTimer > 0f) hurtTimer = Math.max(0f, hurtTimer - delta);

        // Integrate knockback, then decay it exponentially so the dummy bounces and settles.
        pos.x += knockVx * delta;
        pos.z += knockVz * delta;
        float decay = (float) Math.exp(-KB_FRICTION * delta);
        knockVx *= decay;
        knockVz *= decay;

        // Slowly drift back to origin so the arena doesn't drift over time.
        float ox = origin.x - pos.x, oz = origin.z - pos.z;
        float returnSpeed = 1.2f * delta;
        float distToOrigin = (float) Math.sqrt(ox * ox + oz * oz);
        if (distToOrigin > 0.05f) {
            pos.x += (ox / distToOrigin) * Math.min(returnSpeed, distToOrigin);
            pos.z += (oz / distToOrigin) * Math.min(returnSpeed, distToOrigin);
        }

        applyTransform();
        applyTint();
        hearts.update(delta);
    }

    private void applyTransform() {
        float t = hurtTimer / HURT_DUR;       // 1 at impact → 0
        float tilt = TILT_MAX * t * t;        // ease the lean-back out
        instance.transform.setToRotation(Vector3.Y, facingDeg)
            .setTranslation(pos)
            .rotate(Vector3.X, tilt);         // pivot at the feet, top leans away from the attacker
    }

    private void applyTint() {
        boolean hurt = hurtTimer > 0f;
        if (hurt == tinted) return;           // only touch materials on state change
        tinted = hurt;
        for (Material m : instance.materials) {
            if (hurt) m.set(redTint);
            else m.remove(ColorAttribute.Diffuse);
        }
    }

    @Override
    public void onHit(float damage, Vector3 fromDir, boolean crit) {
        hurtTimer = HURT_DUR;
        tinted = false; // force re-tint next applyTint()
        // Apply a physics knockback impulse away from the attacker.
        float strength = crit ? KB_STRENGTH * 1.6f : KB_STRENGTH;
        knockVx += fromDir.x * strength;
        knockVz += fromDir.z * strength;
        // Hearts pop at chest height.
        hearts.burstHearts(chestTmp.set(pos.x, pos.y + 1.4f, pos.z), crit ? 16 : 5);
        if (crit) hearts.burstHearts(chestTmp.set(pos.x, pos.y + 1.0f, pos.z), 10);
    }

    public void render(ModelBatch batch, Environment env) {
        batch.render(instance, env);
        hearts.render(batch); // hit hearts, on the entity
    }

    @Override public Vector3 position() { return pos; }
    @Override public float radius() { return HIT_RADIUS; }

    @Override
    public void dispose() {
        model.dispose(); // skin Texture owned by DungeonGame
        hearts.dispose();
    }
}
