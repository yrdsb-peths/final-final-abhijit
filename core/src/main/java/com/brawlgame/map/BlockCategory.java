package com.brawlgame.map;

/**
 * Broad behavioural class of a placeable block in the Map Maker. The renderer and the (future)
 * collision system branch on this rather than on the concrete {@link BlockType}, so new themed
 * blocks slot in without touching either.
 */
public enum BlockCategory {
    /** Full-height wall block. Auto-stacks to {@link GameMap#WALL_HEIGHT} so players can't jump it. */
    SOLID,
    /** One block tall visually, but a 1.5x-tall collision box (see {@link GameMap#FENCE_COLLISION_H}). */
    FENCE,
    /** Passable dense tall-grass cluster (Brawl-Stars bush). Player-hiding handled later. */
    BUSH,
    /** Passable liquid tile that (later) slows or blocks movement. */
    WATER,
    /** A loot chest prop. */
    CHEST,
    /** A team spawn marker. */
    SPAWN,
    /** Not a block: the dev "remove" tool that clears whatever occupies a cell. */
    ERASER
}
