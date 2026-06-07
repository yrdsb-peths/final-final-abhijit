package com.brawlgame.combat;

/**
 * A read-only view of the world's solid grid, supplied to the {@link com.brawlgame.entity.Player}
 * (for movement collision) and the {@link WeaponController} (for projectile impacts) when a map is
 * being played. Decouples the entity/combat code from the map module: in the Test Player sandbox no
 * collider is set and everything behaves as a flat void; in {@code GameScreen} a thin adapter over
 * the loaded map is provided.
 *
 * <p>The world is a unit grid. {@link #collisionHeightAt} returns how tall (in blocks) the solid in a
 * cell is — 0 means passable — so callers compare it against an entity's/projectile's current height.
 */
public interface BlockCollider {
    int colAt(float worldX);
    int rowAt(float worldZ);
    float cellCenterX(int col);
    float cellCenterZ(int row);
    float cellSize();
    /** Collision height (blocks) of the solid occupying cell (col,row); 0 if empty/passable. */
    float collisionHeightAt(int col, int row);
}
