package com.brawlgame.map;

/**
 * A visual + palette set. Each theme defines the 9-slot hotbar palette the dev paints with, the
 * solid block its auto-generated borders are built from, and a lowercase id used in save file names
 * (e.g. {@code sand_large_1.map}, {@code deepslate_small_1.map}).
 *
 * <p>The 9 slots are ordered to match the reference hotbar: solid, wall, fence, water, chest, spawn,
 * two bushes, then the eraser tool.
 */
public enum Theme {
    OVERWORLD("sand", "Overworld / Sand", BlockType.SANDSTONE_WALL, new BlockType[] {
        BlockType.SAND,
        BlockType.SANDSTONE_WALL,
        BlockType.OAK_FENCE,
        BlockType.SPAWN,
        BlockType.SPAWN_BOT,
        BlockType.ERASER,
        BlockType.ERASER,
        BlockType.ERASER,
        BlockType.ERASER,
    }),
    DEEP_DARK("deepslate", "Deep Dark / Deepslate", BlockType.DEEPSLATE_BRICK_WALL, new BlockType[] {
        BlockType.DEEPSLATE_TILE,
        BlockType.DEEPSLATE_BRICK_WALL,
        BlockType.DARK_OAK_FENCE,
        BlockType.SPAWN,
        BlockType.SPAWN_BOT,
        BlockType.ERASER,
        BlockType.ERASER,
        BlockType.ERASER,
        BlockType.ERASER,
    });

    private final String id;
    private final String displayName;
    private final BlockType borderBlock;
    private final BlockType[] palette;

    Theme(String id, String displayName, BlockType borderBlock, BlockType[] palette) {
        this.id = id;
        this.displayName = displayName;
        this.borderBlock = borderBlock;
        this.palette = palette;
    }

    /** Lowercase token used in saved file names. */
    public String id()              { return id; }
    public String displayName()     { return displayName; }
    /** Standard solid the auto-borders are made of. */
    public BlockType borderBlock()  { return borderBlock; }
    /** The 9 hotbar slots for this theme (index 0..8). */
    public BlockType[] palette()    { return palette; }

    public static Theme fromId(String s) {
        for (Theme t : values()) if (t.id.equalsIgnoreCase(s)) return t;
        throw new IllegalArgumentException("Unknown theme: " + s);
    }
}
