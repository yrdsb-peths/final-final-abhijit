package com.brawlgame.entity;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.brawlgame.model.MinecraftPlayerModel;
import com.brawlgame.model.PlayerAnimator;

/**
 * The player character: world position + facing, the rigged Minecraft model, and its animator.
 * Movement is world-relative (aligned to the fixed Dungeons camera): W is "up-screen" (-Z).
 * The player smoothly turns to face the direction of travel.
 *
 * <p>Speeds match vanilla Minecraft (blocks/s == world units/s). Sprint engages while moving and
 * either Left Shift is held or W was double-tapped (and is still held).
 */
public final class Player implements Disposable {

    private static final float WALK_SPEED = 4.317f;
    private static final float SPRINT_SPEED = 5.612f;
    private static final float TURN_RATE = 14f;         // facing-lerp responsiveness
    private static final float DOUBLE_TAP_WINDOW = 0.28f;

    private final Model model;
    private final ModelInstance instance;
    private final PlayerAnimator animator;

    private final Vector3 position = new Vector3(0f, 0f, 0f);
    private float facingDeg = 0f;       // 0 => facing -Z (the model's front)
    private boolean sprinting = false;

    // double-tap-W state
    private float clock = 0f;
    private float lastWTap = -10f;
    private boolean wWasDown = false;
    private boolean doubleTapLatched = false;

    private final Vector3 move = new Vector3();

    public Player(Texture skin) {
        model = MinecraftPlayerModel.build(skin);
        instance = new ModelInstance(model);
        animator = new PlayerAnimator(instance);
        applyTransform();
    }

    public void update(float delta) {
        clock += delta;

        // --- directional input (world-relative) ---
        move.set(0f, 0f, 0f);
        boolean w = Gdx.input.isKeyPressed(Input.Keys.W);
        if (w) move.z -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) move.z += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) move.x -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) move.x += 1f;
        boolean moving = move.len2() > 0.0001f;

        // --- double-tap-W detection ---
        if (w && !wWasDown) {
            if (clock - lastWTap < DOUBLE_TAP_WINDOW) doubleTapLatched = true;
            lastWTap = clock;
        }
        if (!w) doubleTapLatched = false; // releasing W cancels the latch
        wWasDown = w;

        boolean sprintHeld = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
            || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        sprinting = moving && (sprintHeld || doubleTapLatched);

        float speed = 0f;
        if (moving) {
            speed = sprinting ? SPRINT_SPEED : WALK_SPEED;
            move.nor();
            position.x += move.x * speed * delta;
            position.z += move.z * speed * delta;

            // Turn to face the movement direction. facing=0 means front (-Z); see derivation:
            // worldFront = (-sin(yaw), -cos(yaw)) so target yaw = atan2(-x, -z).
            float target = MathUtils.atan2(-move.x, -move.z) * MathUtils.radiansToDegrees;
            facingDeg = MathUtils.lerpAngleDeg(facingDeg, target, Math.min(1f, delta * TURN_RATE));
        } else {
            sprinting = false;
        }

        applyTransform();
        animator.update(delta, speed, sprinting);
    }

    private void applyTransform() {
        instance.transform.setToRotation(Vector3.Y, facingDeg).setTranslation(position);
    }

    public void render(ModelBatch batch, Environment env) {
        batch.render(instance, env);
    }

    public Vector3 getPosition() {
        return position;
    }

    public boolean isSprinting() {
        return sprinting;
    }

    @Override
    public void dispose() {
        model.dispose(); // the skin Texture is owned/disposed by DungeonGame
    }
}
