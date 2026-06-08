package com.brawlgame.combat;

import com.badlogic.gdx.math.Vector3;

/**
 * Line-of-sight for combat: melee and projectiles cannot connect through solid walls/fences. Uses the
 * same grid traversal as {@link GridRaycast}.
 */
public final class CombatLoS {

    private static final float CHEST_Y = 1.15f;
    private static final Vector3 FROM = new Vector3();
    private static final Vector3 TO = new Vector3();
    private static final Vector3 HIT = new Vector3();

    private CombatLoS() {}

    /** {@code true} when nothing solid blocks the horizontal segment at chest height. */
    public static boolean clear(BlockCollider c, float ax, float az, float bx, float bz) {
        if (c == null) return true;
        FROM.set(ax, CHEST_Y, az);
        TO.set(bx, CHEST_Y, bz);
        return !GridRaycast.firstHit(c, FROM, TO, HIT);
    }
}
