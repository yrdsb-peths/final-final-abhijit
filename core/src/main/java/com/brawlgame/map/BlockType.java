package com.brawlgame.map;

import com.badlogic.gdx.graphics.Color;

/**
 * A concrete placeable block. Carries everything theme-independent: its {@link BlockCategory},
 * a display name, the collision height the (future) player simulation should use, and an optional
 * render tint (bushes/water multiply their greyscale texture by this).
 *
 * <p>The actual texture files chosen for each type live in the rendering layer (BlockLibrary), so
 * this enum stays free of any libGDX rendering dependency and can be unit-tested / serialized
 * standalone. Serialization uses {@link #name()}, so do not rename existing constants without a
 * migration.
 */
public enum BlockType {
    // ---- Overworld / Sand palette ----
    SAND            (BlockCategory.SOLID,  "Sand",            GameMap.WALL_HEIGHT, null),
    SANDSTONE_WALL  (BlockCategory.SOLID,  "Sandstone Wall",  GameMap.WALL_HEIGHT, null),
    OAK_FENCE       (BlockCategory.FENCE,  "Oak Fence",       GameMap.FENCE_COLLISION_H, null),
    WATER           (BlockCategory.WATER,  "Water",           0f, new Color(0.20f, 0.45f, 0.95f, 0.78f)),
    BUSH_GREEN      (BlockCategory.BUSH,   "Green Bush",      0f, new Color(0.30f, 0.78f, 0.28f, 1f)),
    BUSH_YELLOW     (BlockCategory.BUSH,   "Desert Bush",     0f, new Color(0.95f, 0.72f, 0.20f, 1f)),

    // ---- Deep Dark / Deepslate palette ----
    DEEPSLATE_TILE       (BlockCategory.SOLID, "Deepslate Tiles",      GameMap.WALL_HEIGHT, null),
    DEEPSLATE_BRICK_WALL (BlockCategory.SOLID, "Deepslate Brick Wall", GameMap.WALL_HEIGHT, null),
    DARK_OAK_FENCE       (BlockCategory.FENCE, "Dark Oak Fence",       GameMap.FENCE_COLLISION_H, null),
    DARK_WATER           (BlockCategory.WATER, "Swamp Water",          0f, new Color(0.10f, 0.22f, 0.30f, 0.82f)),
    BUSH_BLUE            (BlockCategory.BUSH,  "Blue Bush",            0f, new Color(0.28f, 0.55f, 0.92f, 1f)),

    // ---- Shared specials ----
    CHEST  (BlockCategory.CHEST, "Chest",       0f, null),
    SPAWN  (BlockCategory.SPAWN, "Spawn Point", 0f, new Color(0.95f, 0.20f, 0.25f, 0.7f)),
    ERASER (BlockCategory.ERASER,"Eraser",      0f, null);

    private final BlockCategory category;
    private final String displayName;
    private final float collisionHeight;
    private final Color tint; // null = use texture unmodified

    BlockType(BlockCategory category, String displayName, float collisionHeight, Color tint) {
        this.category = category;
        this.displayName = displayName;
        this.collisionHeight = collisionHeight;
        this.tint = tint;
    }

    public BlockCategory category()   { return category; }
    public String displayName()       { return displayName; }
    /** Collision-box height in blocks the player should treat this as (0 = passable). */
    public float collisionHeight()    { return collisionHeight; }
    /** Render tint, or {@code null} for no tint. */
    public Color tint()               { return tint; }
    public boolean isPassable()       { return collisionHeight <= 0f; }
}
