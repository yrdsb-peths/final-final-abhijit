package com.brawlgame.map;

/**
 * The two base canvas footprints, mirroring Brawl Stars. {@code SMALL} is a tall portrait rectangle
 * (3v3 maps); {@code LARGE} is a big square (Showdown). Dimensions are in grid cells (= blocks).
 * Odd counts keep the map symmetric about its centre line.
 */
public enum MapSize {
    SMALL("small", 21, 33),
    LARGE("large", 49, 49);

    private final String id;
    private final int cols;
    private final int rows;

    MapSize(String id, int cols, int rows) {
        this.id = id;
        this.cols = cols;
        this.rows = rows;
    }

    /** Lowercase token used in saved file names, e.g. {@code sand_large_1.map}. */
    public String id()  { return id; }
    public int cols()   { return cols; }
    public int rows()   { return rows; }

    public static MapSize fromId(String s) {
        for (MapSize m : values()) if (m.id.equalsIgnoreCase(s)) return m;
        throw new IllegalArgumentException("Unknown map size: " + s);
    }
}
