package com.brawlgame.combat;

import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

/**
 * Shared held-weapon placement constants and helpers. The player {@link WeaponController} and the
 * {@link com.brawlgame.entity.AiBrawler} both anchor weapons through these offsets so swords and guns
 * sit in the same place relative to the rig.
 */
public final class WeaponAnchors {

    // ---- gun: chest-pinned placement (player-local) ----
    public static final float GUN_OFF_X = 0.10f;
    public static final float GUN_OFF_Y = 1.7f;
    public static final float GUN_OFF_Z = -0.7f;
    public static final float GUN_BODY_YAW = 0f;
    public static final float GUN_BODY_PITCH = 2f;
    public static final float GUN_SCALE = 1.4f;

    // ---- sword: arm-pinned placement ----
    public static final float SWORD_ARM_DROP = -0.72f;
    public static final float SWORD_SEAT_X = 0f;
    public static final float SWORD_SEAT_Y = 0f;
    public static final float SWORD_SEAT_Z = 0f;
    public static final float SWORD_TILT = 0f;
    public static final float SWORD_TWIST = 0f;
    public static final float SWORD_ROLL = 90f;

    private static final Vector3 AXIS_X = Vector3.X;
    private static final Vector3 AXIS_Y = Vector3.Y;
    private static final Vector3 AXIS_Z = Vector3.Z;

    private WeaponAnchors() {}

    /** Places the potato gun in front of the chest — same matrix the player uses. */
    public static void placeGun(Matrix4 out, Matrix4 bodyTransform) {
        out.set(bodyTransform)
            .translate(GUN_OFF_X, GUN_OFF_Y, GUN_OFF_Z)
            .rotate(AXIS_Y, GUN_BODY_YAW)
            .rotate(AXIS_X, GUN_BODY_PITCH)
            .scale(GUN_SCALE, GUN_SCALE, GUN_SCALE);
    }

    /**
     * Places the sword on the right-hand arm node after the skeleton has been updated for the frame.
     * {@code armGlobal} is {@code ARM_L.globalTransform}.
     */
    public static void placeSword(Matrix4 out, Matrix4 bodyTransform, Matrix4 armGlobal) {
        out.set(bodyTransform).mul(armGlobal).translate(0f, SWORD_ARM_DROP, 0f)
            .translate(SWORD_SEAT_X, SWORD_SEAT_Y, SWORD_SEAT_Z)
            .rotate(AXIS_X, SWORD_TILT)
            .rotate(AXIS_Y, SWORD_TWIST)
            .rotate(AXIS_Z, SWORD_ROLL);
    }

    /** Resolves the right-hand arm node from a rigged player model instance. */
    public static Node rightArm(Node root, String armId) {
        return root.getChild(armId, true, true);
    }
}
