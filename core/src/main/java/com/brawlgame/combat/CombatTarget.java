package com.brawlgame.combat;

import com.badlogic.gdx.math.Vector3;

/**
 * Something the player's attacks can connect with. The {@link WeaponController} resolves melee
 * strikes and projectiles against this and calls {@link #onHit} at the moment of impact.
 */
public interface CombatTarget {

    /** Centre/feet position of the target (world units). */
    Vector3 position();

    /** Horizontal hit radius (world units). */
    float radius();

    /**
     * Register an impact.
     * @param damage    damage dealt (visual only for the training dummy)
     * @param fromDir   normalised horizontal direction the hit came FROM → TO the target (for knockback/tilt)
     * @param crit      true if this was a critical hit (heavier visual feedback)
     */
    void onHit(float damage, Vector3 fromDir, boolean crit);
}
