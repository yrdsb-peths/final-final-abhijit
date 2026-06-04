package com.brawlgame.model;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.math.MathUtils;

/**
 * Procedural walk / sprint / idle animation for the rigged player. No baked clips — it drives each
 * limb node's rotation every frame (the same approach the vanilla Minecraft renderer uses).
 *
 * <p>Right arm swings opposite the right leg (and in phase with the left leg) for a natural gait.
 * Swing amplitude and cadence grow when sprinting, and the torso/head/arms lean forward — the
 * recognisable Minecraft sprint posture. When idle, limbs ease back to rest with a faint sway.
 */
public final class PlayerAnimator {

    private final ModelInstance instance;
    private final Node head, body, armR, armL, legR, legL;

    private float phase;        // limb-swing phase, advanced by distance travelled
    private float swingAmount;  // 0..1, eased "how much we are moving"
    private float lean;         // eased sprint lean, in degrees
    private float idleTime;

    // Tuning (degrees).
    private static final float LEG_AMP_WALK = 38f, LEG_AMP_SPRINT = 54f;
    private static final float ARM_AMP_WALK = 34f, ARM_AMP_SPRINT = 50f;
    private static final float LEAN_SPRINT = 13f;

    public PlayerAnimator(ModelInstance instance) {
        this.instance = instance;
        head = instance.getNode(MinecraftPlayerModel.HEAD);
        body = instance.getNode(MinecraftPlayerModel.BODY);
        armR = instance.getNode(MinecraftPlayerModel.ARM_R);
        armL = instance.getNode(MinecraftPlayerModel.ARM_L);
        legR = instance.getNode(MinecraftPlayerModel.LEG_R);
        legL = instance.getNode(MinecraftPlayerModel.LEG_L);
    }

    /** @param speed current ground speed in units/s; @param sprinting whether sprint is active. */
    public void update(float delta, float speed, boolean sprinting) {
        idleTime += delta;
        boolean moving = speed > 0.05f;

        // Ease movement amount and sprint lean so transitions are smooth.
        swingAmount = MathUtils.lerp(swingAmount, moving ? 1f : 0f, Math.min(1f, delta * 12f));
        lean = MathUtils.lerp(lean, (moving && sprinting) ? LEAN_SPRINT : 0f, Math.min(1f, delta * 8f));
        float sprintAmt = lean / LEAN_SPRINT; // 0..1, follows the eased lean

        // Advance the cycle by distance moved, so the gait tracks the real speed automatically.
        phase += speed * delta * 2.2f;

        float legAmp = MathUtils.lerp(LEG_AMP_WALK, LEG_AMP_SPRINT, sprintAmt);
        float armAmp = MathUtils.lerp(ARM_AMP_WALK, ARM_AMP_SPRINT, sprintAmt);
        float c = MathUtils.cos(phase);
        float legSwing = c * legAmp * swingAmount;
        float armSwing = c * armAmp * swingAmount;

        // Faint idle sway, only when nearly still.
        float idle = 1f - swingAmount;
        float armRoll = idle * (3f + MathUtils.cos(idleTime * 1.6f) * 2.5f);
        float armIdle = idle * MathUtils.sin(idleTime * 1.3f) * 3f;

        // pitch = rotation about X (forward/back swing); roll = rotation about Z.
        legR.rotation.setEulerAngles(0f, legSwing, 0f);
        legL.rotation.setEulerAngles(0f, -legSwing, 0f);
        armR.rotation.setEulerAngles(0f, -armSwing + lean * 0.8f + armIdle, armRoll);
        armL.rotation.setEulerAngles(0f, armSwing + lean * 0.8f - armIdle, -armRoll);
        body.rotation.setEulerAngles(0f, lean, 0f);
        head.rotation.setEulerAngles(0f, lean * 0.4f + idle * MathUtils.sin(idleTime * 1.3f + 1f) * 1.5f, 0f);

        instance.calculateTransforms();
    }
}
