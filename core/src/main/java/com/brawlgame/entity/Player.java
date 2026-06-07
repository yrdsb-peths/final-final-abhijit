package com.brawlgame.entity;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import java.util.function.Supplier;

import com.badlogic.gdx.utils.Disposable;
import com.brawlgame.combat.ArmorStats;
import com.brawlgame.combat.BlockCollider;
import com.brawlgame.combat.WeaponController;
import com.brawlgame.item.Inventory;
import com.brawlgame.item.ItemType;
import com.brawlgame.model.MinecraftPlayerModel;
import com.brawlgame.model.PlayerAnimator;

/**
 * The player: Minecraft-accurate physics on a fixed 20 ticks/second simulation, the rigged model,
 * and its animator. Movement is world-relative (WASD); the player AIMS toward the mouse cursor
 * (twin-stick style), decoupled from movement direction.
 *
 * <p>Per-tick physics reproduces vanilla: horizontal friction 0.91*0.6 on the ground (0.91 in air),
 * acceleration tuned to the documented top speeds (walk 4.317 / sprint 5.612 / sneak 1.295 b/s),
 * gravity 0.08 with 0.98 drag, jump impulse 0.42 (≈1.252-block jump), and a 0.2 forward sprint-jump
 * boost. Render position is interpolated between ticks so motion stays smooth at any frame rate.
 *
 * <p>Controls (MC-authentic): WASD move · Left Shift sneak · Left Ctrl or double-tap W sprint · Space jump.
 */
public final class Player implements Disposable {

    // ---- physics constants (per 20 Hz tick, blocks) ----
    private static final float TICK = 1f / 20f;
    private static final float GROUND_FRICTION = 0.91f * 0.6f; // 0.546
    private static final float AIR_FRICTION = 0.91f;
    private static final float WALK_ACCEL = 0.098f;            // → 4.317 b/s terminal at ground friction
    private static final float SPRINT_MUL = 1.3f;
    private static final float SNEAK_MUL = 0.3f;
    private static final float AIR_ACCEL = 0.02f;
    private static final float GRAVITY = 0.08f;
    private static final float DRAG_Y = 0.98f;
    private static final float JUMP_V = 0.42f;
    private static final float SPRINT_JUMP = 0.2f;
    private static final int MAX_TICKS_PER_FRAME = 5;

    private static final float TURN_RATE = 16f;

    // ---- hitbox / eye height (blocks) ----
    // Slightly wider than the vanilla 0.6 as a failsafe: keeps the body far enough off a 1-block wall
    // that the (now larger) gun muzzle can't be shoved completely through it. The muzzle-spawn raycast
    // in WeaponController is the real guard; this just stops the player pressing flush in the first place.
    public static final float HITBOX_W = 0.72f;
    public static final float STAND_H = 1.8f, SNEAK_H = 1.5f;
    public static final float STAND_EYE = 1.62f, SNEAK_EYE = 1.27f;

    private final Model model;
    private final ModelInstance instance;
    private final PlayerAnimator animator;
    private final WeaponController weapon;
    private final PlayerAnimator.ArmPose armPose = new PlayerAnimator.ArmPose();

    private final Vector3 pos = new Vector3();
    private final Vector3 prevPos = new Vector3();
    private final Vector3 renderPos = new Vector3();
    private float vx, vy, vz;
    private boolean onGround = true;
    private boolean sprinting, sneaking;
    private float facingDeg = 0f;
    private float tickAcc = 0f;

    private boolean jumpHeld = false;

    private final Vector3 wish = new Vector3(); // normalised world move direction this frame

    private BlockCollider collider; // optional: solid grid for movement collision (null = flat void)
    private Supplier<ItemType> heldItemSupplier; // reads the selected hotbar item each tick (null = fists)

    // ---- health + armour-reduced damage ----
    private static final float MAX_HEALTH = 20f;     // 10 hearts, vanilla
    private static final float REGEN_RATE = 1f;      // HP/sec recovered when not recently hit
    private static final float REGEN_DELAY = 1.5f;   // seconds after a hit before regen resumes
    private float health = MAX_HEALTH;
    private float regenCooldown = 0f;
    private Inventory inventory; // worn armour is read from here for the reduction formula

    public Player(Texture skin) {
        model = MinecraftPlayerModel.build(skin);
        instance = new ModelInstance(model);
        animator = new PlayerAnimator(instance);
        weapon = new WeaponController(instance);
        renderPos.set(pos);
        applyTransform();
    }

    /**
     * Binds the player's active weapon to the hotbar: each tick {@link #update} pulls the selected
     * item from this supplier and equips the matching weapon (no hardcoded weapon keys).
     */
    public void setHeldItemSupplier(Supplier<ItemType> supplier) {
        this.heldItemSupplier = supplier;
    }

    /** Binds the inventory whose four armour slots feed the Minecraft damage-reduction formula. */
    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    /**
     * Take {@code raw} incoming damage, reduced by the currently worn armour (vanilla Java formula).
     * Pauses regen briefly; if health is depleted the player respawns at the origin with full health.
     */
    public void applyDamage(float raw) {
        float dmg = inventory != null ? ArmorStats.reduce(raw, inventory) : raw;
        health = Math.max(0f, health - dmg);
        regenCooldown = REGEN_DELAY;
        if (health <= 0f) {
            health = MAX_HEALTH;
            setSpawn(0f, 0f); // respawn at the arena origin
        }
    }

    public float getHealth()    { return health; }
    public float getMaxHealth() { return MAX_HEALTH; }

    /** Supplies the solid grid for movement collision (and forwards it to the weapon for projectiles). */
    public void setCollider(BlockCollider collider) {
        this.collider = collider;
        weapon.setCollider(collider);
    }

    /** Places the player on the ground at (x,z) — used to spawn at a loaded map's spawn point. */
    public void setSpawn(float x, float z) {
        pos.set(x, 0f, z);
        prevPos.set(pos);
        renderPos.set(pos);
        vx = vy = vz = 0f;
        onGround = true;
        applyTransform();
    }

    public void update(float delta, Camera camera) {
        readInput();
        // The active weapon (and the sword material/model) is pulled from the selected hotbar slot
        // every tick — iron sword → iron model, diamond → diamond, empty → fists. No hardcoded keys.
        if (heldItemSupplier != null) weapon.setHeldItem(heldItemSupplier.get());
        weapon.handleInput();

        // fixed-step physics with leftover-time interpolation
        tickAcc += Math.min(delta, 0.25f);
        int steps = 0;
        while (tickAcc >= TICK && steps < MAX_TICKS_PER_FRAME) {
            prevPos.set(pos);
            physicsTick();
            tickAcc -= TICK;
            steps++;
        }
        float alpha = MathUtils.clamp(tickAcc / TICK, 0f, 1f);
        renderPos.set(prevPos).lerp(pos, alpha);

        aimAtCursor(camera, delta);
        applyTransform();

        float speed = (float) Math.sqrt(vx * vx + vz * vz) * 20f; // blocks/s
        weapon.setAim(renderPos.x, renderPos.z, facingDeg); // for melee arc/hit tests
        weapon.setCritReady(!onGround && vy < 0f);           // airborne + descending → next hit crits
        weapon.updatePose(delta, armPose);
        animator.update(delta, speed, sprinting, sneaking, onGround, armPose);
        weapon.postAnimate();   // skeleton is now current → anchor weapon, trace trail, fire sparks
        weapon.updateVfx(delta);

        // Slow health regen once a moment has passed since the last hit (step off the hazard to recover).
        if (regenCooldown > 0f) regenCooldown = Math.max(0f, regenCooldown - delta);
        else if (health < MAX_HEALTH) health = Math.min(MAX_HEALTH, health + REGEN_RATE * delta);
    }

    private void readInput() {
        wish.set(0f, 0f, 0f);
        if (Gdx.input.isKeyPressed(Input.Keys.W)) wish.z -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) wish.z += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) wish.x -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) wish.x += 1f;
        boolean moving = wish.len2() > 0.0001f;
        if (moving) wish.nor();

        jumpHeld = Gdx.input.isKeyPressed(Input.Keys.SPACE);
        sneaking = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
            || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);

        // No dedicated sprint key: holding/pressing Space while moving forward IS the sprint-jump.
        // Engages only when moving roughly toward the aim, so you can't sprint-jump backwards.
        float yawRad = facingDeg * MathUtils.degreesToRadians;
        float forwardDot = wish.x * (-MathUtils.sin(yawRad)) + wish.z * (-MathUtils.cos(yawRad));
        sprinting = moving && !sneaking && jumpHeld && forwardDot > 0.3f;
    }

    private void physicsTick() {
        // jump — fires whenever Space is held and we're grounded, so you can spam/hold to bunny-hop and chain sprint-jumps
        if (jumpHeld && onGround) {
            vy = JUMP_V;
            if (sprinting && wish.len2() > 0.0001f) {
                vx += wish.x * SPRINT_JUMP;
                vz += wish.z * SPRINT_JUMP;
            }
            onGround = false;
        }

        // horizontal: decay carried velocity by friction, then accelerate toward input
        float fr = onGround ? GROUND_FRICTION : AIR_FRICTION;
        vx *= fr;
        vz *= fr;
        float accel = onGround
            ? WALK_ACCEL * (sprinting ? SPRINT_MUL : 1f) * (sneaking ? SNEAK_MUL : 1f)
            : AIR_ACCEL * (sprinting ? SPRINT_MUL : 1f);
        vx += wish.x * accel;
        vz += wish.z * accel;

        // integrate — resolve horizontal collisions per-axis so sliding along walls feels natural;
        // vertical (jumps) is unaffected since the arena has no ceilings.
        pos.x += vx;
        if (collider != null) resolveAxisX();
        pos.z += vz;
        if (collider != null) resolveAxisZ();
        pos.y += vy;

        // gravity for the next tick (applied after the move, like Minecraft)
        vy = (vy - GRAVITY) * DRAG_Y;

        // ground plane at y = 0
        if (pos.y <= 0f) {
            pos.y = 0f;
            vy = 0f;
            onGround = true;
        } else {
            onGround = false;
        }
    }

    /** Push the player out of any solid cell it now overlaps along X, and kill X velocity there. */
    private void resolveAxisX() {
        float half = HITBOX_W * 0.5f;
        float cellHalf = collider.cellSize() * 0.5f;
        float minZ = pos.z - half, maxZ = pos.z + half;
        int r0 = collider.rowAt(minZ), r1 = collider.rowAt(maxZ);
        for (int r = r0; r <= r1; r++) {
            int c0 = collider.colAt(pos.x - half), c1 = collider.colAt(pos.x + half);
            for (int c = c0; c <= c1; c++) {
                if (collider.collisionHeightAt(c, r) <= pos.y + 0.05f) continue;
                float cMinX = collider.cellCenterX(c) - cellHalf, cMaxX = collider.cellCenterX(c) + cellHalf;
                float cMinZ = collider.cellCenterZ(r) - cellHalf, cMaxZ = collider.cellCenterZ(r) + cellHalf;
                if (pos.x + half <= cMinX || pos.x - half >= cMaxX) continue;
                if (pos.z + half <= cMinZ || pos.z - half >= cMaxZ) continue;
                if (vx > 0f) pos.x = cMinX - half;
                else if (vx < 0f) pos.x = cMaxX + half;
                else pos.x += (pos.x < collider.cellCenterX(c)) ? (cMinX - half - pos.x) : (cMaxX + half - pos.x);
                vx = 0f;
            }
        }
    }

    /** Push the player out of any solid cell it now overlaps along Z, and kill Z velocity there. */
    private void resolveAxisZ() {
        float half = HITBOX_W * 0.5f;
        float cellHalf = collider.cellSize() * 0.5f;
        float minX = pos.x - half, maxX = pos.x + half;
        int c0 = collider.colAt(minX), c1 = collider.colAt(maxX);
        for (int c = c0; c <= c1; c++) {
            int r0 = collider.rowAt(pos.z - half), r1 = collider.rowAt(pos.z + half);
            for (int r = r0; r <= r1; r++) {
                if (collider.collisionHeightAt(c, r) <= pos.y + 0.05f) continue;
                float cMinX = collider.cellCenterX(c) - cellHalf, cMaxX = collider.cellCenterX(c) + cellHalf;
                float cMinZ = collider.cellCenterZ(r) - cellHalf, cMaxZ = collider.cellCenterZ(r) + cellHalf;
                if (pos.x + half <= cMinX || pos.x - half >= cMaxX) continue;
                if (pos.z + half <= cMinZ || pos.z - half >= cMaxZ) continue;
                if (vz > 0f) pos.z = cMinZ - half;
                else if (vz < 0f) pos.z = cMaxZ + half;
                else pos.z += (pos.z < collider.cellCenterZ(r)) ? (cMinZ - half - pos.z) : (cMaxZ + half - pos.z);
                vz = 0f;
            }
        }
    }

    /** Turn smoothly to face the mouse cursor, projected onto a horizontal plane at chest height. */
    private void aimAtCursor(Camera camera, float delta) {
        Ray ray = camera.getPickRay(Gdx.input.getX(), Gdx.input.getY());
        float planeY = renderPos.y + 1.0f;
        if (Math.abs(ray.direction.y) < 1e-5f) return;
        float t = (planeY - ray.origin.y) / ray.direction.y;
        if (t <= 0f) return;
        float dx = ray.origin.x + ray.direction.x * t - renderPos.x;
        float dz = ray.origin.z + ray.direction.z * t - renderPos.z;
        if (dx * dx + dz * dz < 0.04f) return; // dead zone right under the player
        // facing 0 => front (-Z); worldFront=(-sin,-cos) => target yaw = atan2(-dx,-dz)
        float target = MathUtils.atan2(-dx, -dz) * MathUtils.radiansToDegrees;
        facingDeg = MathUtils.lerpAngleDeg(facingDeg, target, Math.min(1f, delta * TURN_RATE));
    }

    private void applyTransform() {
        instance.transform.setToRotation(Vector3.Y, facingDeg).setTranslation(renderPos);
    }

    public void render(ModelBatch batch, Environment env) {
        batch.render(instance, env);
        weapon.renderWorld(batch, env); // held weapon + impact sparks (within the 3D pass)
    }

    /** Additive swoosh ribbon — call after the ModelBatch pass, before any HUD. */
    public void renderTrail(Camera camera) {
        weapon.renderTrail(camera);
    }

    // ---- accessors (camera follow + debug overlay) ----
    public Vector3 getPosition() { return renderPos; }
    public float getFacingDeg() { return facingDeg; }
    public boolean isSprinting() { return sprinting; }
    public boolean isSneaking() { return sneaking; }
    public boolean isOnGround() { return onGround; }
    public float getHitboxWidth() { return HITBOX_W; }
    public float getHitboxHeight() { return sneaking ? SNEAK_H : STAND_H; }
    public float getEyeHeight() { return sneaking ? SNEAK_EYE : STAND_EYE; }

    public ModelInstance getModelInstance() { return instance; }

    /** The weapon controller (so the HUD/aim-cone can read the current weapon + cone shape). */
    public WeaponController getWeapon() { return weapon; }

    @Override
    public void dispose() {
        weapon.dispose();
        model.dispose(); // the skin Texture is owned/disposed by DungeonGame
    }
}
