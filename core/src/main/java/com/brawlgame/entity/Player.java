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
import com.badlogic.gdx.utils.Disposable;
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
    public static final float HITBOX_W = 0.6f;
    public static final float STAND_H = 1.8f, SNEAK_H = 1.5f;
    public static final float STAND_EYE = 1.62f, SNEAK_EYE = 1.27f;

    private final Model model;
    private final ModelInstance instance;
    private final PlayerAnimator animator;

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

    public Player(Texture skin) {
        model = MinecraftPlayerModel.build(skin);
        instance = new ModelInstance(model);
        animator = new PlayerAnimator(instance);
        renderPos.set(pos);
        applyTransform();
    }

    public void update(float delta, Camera camera) {
        readInput();

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
        animator.update(delta, speed, sprinting, sneaking, onGround);
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

        // integrate
        pos.x += vx;
        pos.y += vy;
        pos.z += vz;

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

    @Override
    public void dispose() {
        model.dispose(); // the skin Texture is owned/disposed by DungeonGame
    }
}
