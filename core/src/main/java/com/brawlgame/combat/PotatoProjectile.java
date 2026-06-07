package com.brawlgame.combat;

import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Vector3;

/**
 * A single potato fired from the gun: a 3D textured box that flies <b>dead straight</b> at muzzle
 * height (no arc), then — once it has covered its launch range — drops steeply to the floor. It stops
 * dead when it hits the ground or a wall, rests on the floor for {@link #LAND_LINGER} seconds, then
 * despawns.
 *
 * <p>In open air far from any wall it simply passes through until its range is up and then plummets.
 * Instances are pooled by the controller — each wraps one {@link ModelInstance} of the shared
 * potato-box model.
 */
public final class PotatoProjectile {

    /** Downward acceleration (blocks/s²) once the potato starts its drop — high, so it falls steeply. */
    public static final float GRAVITY = 38f;
    private static final float FLOOR_Y = 0f;
    private static final float LAND_LINGER = 3f; // sit on the floor this long, then destroy()

    private final ModelInstance instance;
    private final Vector3 pos = new Vector3();
    private final Vector3 prevPos = new Vector3(); // start of this tick's swept motion (for CCD)
    private final Vector3 hit = new Vector3();      // swept-collision contact point (scratch)
    private final Vector3 vel = new Vector3();     // horizontal travel velocity (y stays 0 until drop)
    private final Vector3 fwd = new Vector3();     // unit horizontal heading (kept for the drift on drop)
    private boolean alive;
    private boolean dropping;  // past its range → now falling
    private boolean landed;
    private float landTimer;
    private float remaining;   // horizontal distance left before the steep drop begins
    private float fallVy;      // downward speed while dropping
    private float spinDeg;     // tumble while flying

    public PotatoProjectile(ModelInstance instance) {
        this.instance = instance;
    }

    public boolean isAlive() { return alive; }
    /** Alive and still in the air (not yet landed) — eligible to strike an enemy. */
    public boolean isFlying() { return alive && !landed; }
    public Vector3 position() { return pos; }

    /**
     * Fire from {@code muzzle} flying straight along {@code velocity} (horizontal). After covering
     * {@code range} blocks it drops steeply to the floor.
     */
    public void launch(Vector3 muzzle, Vector3 velocity, float range) {
        pos.set(muzzle);
        prevPos.set(muzzle);
        vel.set(velocity);
        fwd.set(velocity).y = 0f;
        if (fwd.len2() > 1e-6f) fwd.nor();
        remaining = range;
        alive = true;
        dropping = false;
        landed = false;
        landTimer = 0f;
        fallVy = 0f;
        spinDeg = 0f;
        applyTransform();
    }

    /**
     * Advance one frame. Returns {@code true} the exact frame it impacts the ground/a wall, so the
     * caller can spawn the dirt/potato splash burst at {@link #position()}.
     */
    public boolean update(float delta, BlockCollider collider) {
        if (!alive) return false;

        if (landed) {
            landTimer -= delta;
            if (landTimer <= 0f) destroy();
            return false;
        }

        prevPos.set(pos);
        if (!dropping) {
            // Straight, level flight — no gravity, just glide forward until the range runs out.
            float step = vel.len() * delta;
            pos.mulAdd(vel, delta);
            remaining -= step;
            if (remaining <= 0f) dropping = true;
        } else {
            // Steep drop: accelerate straight down, with only a slight remnant of forward drift.
            fallVy -= GRAVITY * delta;
            pos.y += fallVy * delta;
            pos.mulAdd(fwd, 1.5f * delta); // tiny forward creep so it doesn't fall perfectly vertically
        }
        spinDeg += 540f * delta;

        boolean impact = false;
        // Continuous collision: sweep the whole prev→cur segment against the wall grid so a fast
        // potato can't tunnel between frames. On a hit, snap to the surface and stop dead.
        if (collider != null && GridRaycast.firstHit(collider, prevPos, pos, hit)) {
            pos.set(hit);
            land();
            impact = true;
        }
        // Floor.
        if (!landed && pos.y <= FLOOR_Y) {
            pos.y = FLOOR_Y;
            land();
            impact = true;
        }

        applyTransform();
        return impact;
    }

    /**
     * Spawn already collided at {@code at} — used when the muzzle is pushed into a wall, so the potato
     * detonates on the wall surface (in the player's face) instead of being fired through it. It sits
     * landed for {@link #LAND_LINGER} then despawns; the caller spawns the impact burst.
     */
    public void spawnLanded(Vector3 at) {
        pos.set(at);
        prevPos.set(at);
        vel.set(0f, 0f, 0f);
        alive = true;
        dropping = false;
        fallVy = 0f;
        spinDeg = 0f;
        land();
        applyTransform();
    }

    private void land() {
        landed = true;
        landTimer = LAND_LINGER;
        vel.set(0f, 0f, 0f); // stop moving instantly
    }

    /** Instantly despawn. */
    public void destroy() {
        alive = false;
    }

    private void applyTransform() {
        instance.transform.setToTranslation(pos).rotate(Vector3.X, spinDeg);
    }

    public void render(ModelBatch batch, Environment env) {
        if (alive) batch.render(instance, env);
    }
}
