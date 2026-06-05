package com.brawlgame.model;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;

/**
 * Procedural player animation following Minecraft's own {@code HumanoidModel.setupAnim} math.
 *
 * <p>Walk/sprint: legs and arms swing as {@code cos(limbSwing*0.6662) * amplitude * limbSwingAmount},
 * arms in anti-phase to the leg on the same side. {@code limbSwingAmount} eases toward
 * {@code min(1, speedPerTick*4)} and the phase advances by distance moved, so cadence and amplitude
 * track real speed.
 *
 * <p>Sprint lean and crouch are applied to the BODY node only — because the head and arms are its
 * children (see {@link MinecraftPlayerModel}), they lean with the torso naturally. The head then
 * counter-rotates so it stays roughly level (this is what fixes "head/hands too far forward").
 */
public final class PlayerAnimator {

    private final ModelInstance instance;
    private final Node head, body, armR, armL, legR, legL;

    private float phase;       // limbSwing, accumulated
    private float amount;      // limbSwingAmount, 0..1 eased
    private float lean;        // eased sprint lean (radians)
    private float sneak;       // eased crouch amount 0..1
    private float idleTime;

    // ---- tuning (radians; MC uses these constants) ----
    private static final float FREQ = 0.6662f;       // limb-swing frequency factor
    private static final float LEG_AMP = 1.4f;       // HumanoidModel leg amplitude
    private static final float ARM_AMP = 1.0f;       // = 2.0 * 0.5 arm amplitude
    private static final float SPRINT_LEAN = 0.15f;  // subtle torso forward lean while sprinting
    private static final float SNEAK_BODY = 0.35f;   // crouch torso tilt (eased down from MC 0.5 for this cam)
    private static final float SNEAK_ARM = 0.35f;    // crouch arm forward angle
    private static final float SNEAK_DROP = 0.28f;   // torso drop while crouching — emphasise "compact"

    private final float bodyRestY;

    // Scratch quaternion so the arm anim can be blended toward a weapon override without allocating.
    private final Quaternion qArm = new Quaternion();

    /**
     * A per-frame override for the arm nodes, produced by the weapon system. Each arm carries a
     * target Euler orientation (radians, in the same yaw/pitch/roll convention as the walk anim)
     * and a 0..1 weight: 0 = pure walk/idle swing, 1 = fully the weapon pose. The animator slerps
     * between the two, so swings and grips layer cleanly on top of locomotion.
     */
    public static final class ArmPose {
        /** Override rotations (full quaternions) + 0..1 blend weights. rRot drives ARM_R, lRot ARM_L. */
        public final Quaternion rRot = new Quaternion();
        public final Quaternion lRot = new Quaternion();
        public float rWeight, lWeight;
        /** Extra torso Y-twist (radians) layered on top of the lean — the "core power" of a swing.
         *  Because the head and arms are children of the body node, this rotates the whole upper
         *  body, driving the arm through a wide arc. */
        public float bodyYaw;
        public void reset() { rWeight = 0f; lWeight = 0f; bodyYaw = 0f; }
    }

    public PlayerAnimator(ModelInstance instance) {
        this.instance = instance;
        head = instance.getNode(MinecraftPlayerModel.HEAD);
        body = instance.getNode(MinecraftPlayerModel.BODY);
        armR = instance.getNode(MinecraftPlayerModel.ARM_R);
        armL = instance.getNode(MinecraftPlayerModel.ARM_L);
        legR = instance.getNode(MinecraftPlayerModel.LEG_R);
        legL = instance.getNode(MinecraftPlayerModel.LEG_L);
        bodyRestY = body.translation.y; // waist pivot height; crouch lowers from here
    }

    /** @param speed horizontal ground speed (blocks/s). @param pose weapon arm override, or null. */
    public void update(float delta, float speed, boolean sprinting, boolean sneaking, boolean onGround,
                       ArmPose pose) {
        idleTime += delta;
        boolean moving = speed > 0.05f;

        // limbSwingAmount eases toward min(1, speedPerTick * 4) (MC); phase advances by distance.
        float target = Math.min(1f, (speed / 20f) * 4f);
        amount = MathUtils.lerp(amount, target, Math.min(1f, delta * 10f));
        phase += amount * FREQ * 20f * delta;

        lean = MathUtils.lerp(lean, (sprinting && moving) ? SPRINT_LEAN : 0f, Math.min(1f, delta * 8f));
        sneak = MathUtils.lerp(sneak, sneaking ? 1f : 0f, Math.min(1f, delta * 10f));

        float legSwing = MathUtils.cos(phase) * LEG_AMP * amount;
        float armSwing = MathUtils.cos(phase + MathUtils.PI) * ARM_AMP * amount;

        // Faint idle arm sway (MC: zRot = cos(t*0.09)*0.05+0.05; xRot += sin(t*0.067)*0.05), only at rest.
        float idle = 1f - amount;
        float swayZ = idle * (MathUtils.cos(idleTime * 1.5f) * 0.05f + 0.05f);
        float swayX = idle * MathUtils.sin(idleTime * 1.2f) * 0.05f;

        float bodyPitch = lean + sneak * SNEAK_BODY;
        float sneakArm = sneak * SNEAK_ARM;

        // Legs hang from the hips (root children) — pure fore/aft swing, in anti-phase.
        legR.rotation.setEulerAnglesRad(0f, legSwing, 0f);
        legL.rotation.setEulerAnglesRad(0f, -legSwing, 0f);

        // Torso leans for sprint/crouch and drops when crouching; head & arms (children) follow it.
        // The attack twist (bodyYaw) is layered onto the Y axis so a swing rotates the whole torso —
        // this is what carries the arm through its wide arc and gives the strike its momentum.
        float bodyYaw = pose != null ? pose.bodyYaw : 0f;
        body.rotation.setEulerAnglesRad(bodyYaw, -bodyPitch, 0f); // Y = attack twist, -X = lean forward
        body.translation.y = bodyRestY - sneak * SNEAK_DROP;

        // Head counter-rotates to stay ~level: cancel the sprint/crouch lean (pitch), and shrug off
        // most of the attack twist (yaw) so the torso whips but the head doesn't spin with it.
        head.rotation.setEulerAnglesRad(-bodyYaw * 0.55f, lean + sneak * SNEAK_BODY * 0.7f, 0f);

        // Arms swing from the (leaned) shoulders; add crouch forward angle + idle sway. No manual
        // forward push — the forward motion now comes from the body lean carrying the shoulders.
        // Then blend toward the weapon pose (sword swing / two-handed grip) by its per-arm weight.
        qArm.setEulerAnglesRad(0f, armSwing + sneakArm + swayX, swayZ);
        if (pose != null && pose.rWeight > 0f) qArm.slerp(pose.rRot, Math.min(1f, pose.rWeight));
        armR.rotation.set(qArm);

        qArm.setEulerAnglesRad(0f, -armSwing + sneakArm - swayX, -swayZ);
        if (pose != null && pose.lWeight > 0f) qArm.slerp(pose.lRot, Math.min(1f, pose.lWeight));
        armL.rotation.set(qArm);

        instance.calculateTransforms();
    }
}
