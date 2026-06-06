package com.brawlgame.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

/**
 * Reads and writes {@link GameMap}s as small, human-readable {@code .map} files under a local
 * {@code maps/} folder, e.g. {@code maps/sand_large_1.map}, {@code maps/deepslate_small_1.map}.
 *
 * <p>Format (line based, {@code #}-prefixed comments ignored):
 * <pre>
 *   THEME sand
 *   SIZE large
 *   COLS 49
 *   ROWS 49
 *   # col row BLOCK_TYPE
 *   0 0 SANDSTONE_WALL
 *   12 8 WATER
 *   ...
 * </pre>
 * Only non-empty cells are written, so files stay compact.
 */
public final class MapSerializer {

    private static final String DIR = "maps";

    private MapSerializer() {}

    /**
     * Serializes {@code map} to {@code maps/<theme>_<size>_<n>.map}, auto-incrementing {@code n} so
     * existing maps are never overwritten. Returns the file written (for logging).
     */
    public static FileHandle save(GameMap map) {
        String base = map.theme().id() + "_" + map.size().id();
        FileHandle dir = Gdx.files.local(DIR);
        if (!dir.exists()) dir.mkdirs();

        int n = 1;
        FileHandle file;
        do {
            file = Gdx.files.local(DIR + "/" + base + "_" + n + ".map");
            n++;
        } while (file.exists());

        StringBuilder sb = new StringBuilder(4096);
        sb.append("THEME ").append(map.theme().id()).append('\n');
        sb.append("SIZE ").append(map.size().id()).append('\n');
        sb.append("COLS ").append(map.cols()).append('\n');
        sb.append("ROWS ").append(map.rows()).append('\n');
        sb.append("# col row BLOCK_TYPE\n");
        for (int c = 0; c < map.cols(); c++) {
            for (int r = 0; r < map.rows(); r++) {
                BlockType t = map.get(c, r);
                if (t != null) sb.append(c).append(' ').append(r).append(' ').append(t.name()).append('\n');
            }
        }
        file.writeString(sb.toString(), false, "UTF-8");
        return file;
    }

    /** Loads a {@code .map} file back into a fresh {@link GameMap}. */
    public static GameMap load(FileHandle file) {
        Theme theme = null;
        MapSize size = null;

        String[] lines = file.readString("UTF-8").split("\\r?\\n");
        // First pass: header (need theme+size before we can build the grid).
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            if (line.startsWith("THEME ")) theme = Theme.fromId(line.substring(6).trim());
            else if (line.startsWith("SIZE ")) size = MapSize.fromId(line.substring(5).trim());
            if (theme != null && size != null) break;
        }
        if (theme == null || size == null) {
            throw new IllegalArgumentException("Map file missing THEME/SIZE header: " + file.path());
        }

        GameMap map = new GameMap(theme, size);
        // Second pass: cell records.
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            char c0 = line.charAt(0);
            if (c0 < '0' || c0 > '9') continue; // skip header lines
            String[] parts = line.split("\\s+");
            if (parts.length < 3) continue;
            int c = Integer.parseInt(parts[0]);
            int r = Integer.parseInt(parts[1]);
            BlockType t = BlockType.valueOf(parts[2]);
            map.place(c, r, t);
        }
        map.markDirty();
        return map;
    }
}
