package com.brawlgame.map;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The editable grid: a {@code cols x rows} board of cells, each holding at most one {@link BlockType}
 * (null = empty floor). One block per cell — placement overwrites. The board is centred on the world
 * origin so the spectator camera and any future gameplay share a tidy coordinate space: cell (c,r)
 * sits at world ({@link #worldX(int)}, {@link #worldZ(int)}) and is {@link #CELL} units wide.
 *
 * <p>Walls (and the auto-borders) stack {@link #WALL_HEIGHT} blocks tall; fences carry a
 * {@link #FENCE_COLLISION_H}-block collision box. The grid only stores the type per cell — the
 * renderer turns that into the right stacked geometry, and the (future) collision pass reads the
 * heights off {@link BlockType}.
 *
 * <p>Edits flow through a stroke/undo system: {@link #beginStroke()} opens a batch, {@link #apply}
 * paints cells into it, {@link #commitStroke()} pushes it onto the undo stack. {@link #undo()},
 * {@link #redo()} and {@link #clearAll()} replay batches. {@link #isDirty()} is set on any change so
 * the renderer can lazily rebuild.
 */
public final class GameMap {

    /** World units per cell. One cell == one Minecraft block, matching the player's scale. */
    public static final float CELL = 1f;
    /** Stacked height of solid walls and the auto-borders (blocks). A 2-stack blocks vision/movement. */
    public static final int WALL_HEIGHT = 2;
    /** Collision-box height assigned to fences (1.5x a block) so players can't hop them. */
    public static final float FENCE_COLLISION_H = 1.5f;

    /** One painted cell change, retained for undo/redo. */
    private static final class Edit {
        final int c, r;
        final BlockType before, after;
        Edit(int c, int r, BlockType before, BlockType after) {
            this.c = c; this.r = r; this.before = before; this.after = after;
        }
    }

    private final Theme theme;
    private final MapSize size;
    private final int cols, rows;
    private final BlockType[][] cells; // [col][row]; null == empty

    private final Deque<List<Edit>> undoStack = new ArrayDeque<>();
    private final Deque<List<Edit>> redoStack = new ArrayDeque<>();
    private List<Edit> stroke; // open batch between begin/commit, else null

    private boolean dirty = true;

    public GameMap(Theme theme, MapSize size) {
        this.theme = theme;
        this.size = size;
        this.cols = size.cols();
        this.rows = size.rows();
        this.cells = new BlockType[cols][rows];
        generateBorders();
    }

    /** Encloses the outer ring with the theme's standard solid block. Called once on construction. */
    public void generateBorders() {
        BlockType border = theme.borderBlock();
        for (int c = 0; c < cols; c++) {
            cells[c][0] = border;
            cells[c][rows - 1] = border;
        }
        for (int r = 0; r < rows; r++) {
            cells[0][r] = border;
            cells[cols - 1][r] = border;
        }
        dirty = true;
    }

    // ---- queries ----

    public boolean inBounds(int c, int r) {
        return c >= 0 && c < cols && r >= 0 && r < rows;
    }

    /** True for the outer ring — the editor refuses to erase these so the arena stays sealed. */
    public boolean isBorderCell(int c, int r) {
        return c == 0 || r == 0 || c == cols - 1 || r == rows - 1;
    }

    public BlockType get(int c, int r) {
        return inBounds(c, r) ? cells[c][r] : null;
    }

    // ---- direct (history-free) placement, used by the loader ----

    /** Places {@code t} at (c,r). {@link BlockType#ERASER} clears the cell. Returns true if changed. */
    public boolean place(int c, int r, BlockType t) {
        if (!inBounds(c, r)) return false;
        if (t == null || t.category() == BlockCategory.ERASER) return erase(c, r);
        if (cells[c][r] == t) return false;
        cells[c][r] = t;
        dirty = true;
        return true;
    }

    /** Clears (c,r) unless it is a sealed border cell. Returns true if something was removed. */
    public boolean erase(int c, int r) {
        if (!inBounds(c, r) || isBorderCell(c, r) || cells[c][r] == null) return false;
        cells[c][r] = null;
        dirty = true;
        return true;
    }

    // ---- stroke + undo/redo (used by the editor) ----

    /** Opens an edit batch. Pair with {@link #commitStroke()}; nested calls are ignored. */
    public void beginStroke() {
        if (stroke == null) stroke = new ArrayList<>();
    }

    /**
     * Paints (c,r) to {@code t} within the open stroke (auto-opening one if needed), recording the
     * change for undo. {@link BlockType#ERASER}/null erases. Returns true if the cell changed.
     */
    public boolean apply(int c, int r, BlockType t) {
        if (!inBounds(c, r)) return false;
        // Each spawn type is unique — placing a new one clears any earlier cell of the same type
        // (undo restores both together as one stroke).
        if (t == BlockType.SPAWN || t == BlockType.SPAWN_BOT) clearExistingSpawnOfType(c, r, t);
        BlockType before = cells[c][r];
        boolean changed = (t == null || t.category() == BlockCategory.ERASER) ? erase(c, r) : place(c, r, t);
        if (changed) {
            if (stroke == null) stroke = new ArrayList<>();
            stroke.add(new Edit(c, r, before, cells[c][r]));
        }
        return changed;
    }

    /** Closes the open batch, pushing it onto the undo stack (and clearing redo) if it changed anything. */
    public void commitStroke() {
        if (stroke == null) return;
        if (!stroke.isEmpty()) {
            undoStack.push(stroke);
            redoStack.clear();
        }
        stroke = null;
    }

    /** Erases any cell of {@code type} other than (keepC,keepR), recording the change into the open stroke. */
    private void clearExistingSpawnOfType(int keepC, int keepR, BlockType type) {
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                if (cells[c][r] == type && !(c == keepC && r == keepR)) {
                    if (stroke == null) stroke = new ArrayList<>();
                    stroke.add(new Edit(c, r, cells[c][r], null));
                    cells[c][r] = null;
                    dirty = true;
                }
            }
        }
    }

    /** Grid coords {col,row} of the player spawn (SPAWN), or {@code null} if none is placed. */
    public int[] findPlayerSpawn() {
        return findCellOfType(BlockType.SPAWN);
    }

    /** Grid coords {col,row} of the bot/enemy spawn (SPAWN_BOT), or {@code null} if none is placed. */
    public int[] findBotSpawn() {
        return findCellOfType(BlockType.SPAWN_BOT);
    }

    /** Backward-compatible: returns the first SPAWN or SPAWN_BOT found, player spawn preferred. */
    public int[] findSpawn() {
        int[] p = findPlayerSpawn();
        return p != null ? p : findBotSpawn();
    }

    private int[] findCellOfType(BlockType type) {
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                if (cells[c][r] == type) return new int[] {c, r};
            }
        }
        return null;
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }

    public void undo() {
        if (undoStack.isEmpty()) return;
        List<Edit> batch = undoStack.pop();
        for (int i = batch.size() - 1; i >= 0; i--) {
            Edit e = batch.get(i);
            cells[e.c][e.r] = e.before;
        }
        redoStack.push(batch);
        dirty = true;
    }

    public void redo() {
        if (redoStack.isEmpty()) return;
        List<Edit> batch = redoStack.pop();
        for (Edit e : batch) cells[e.c][e.r] = e.after;
        undoStack.push(batch);
        dirty = true;
    }

    /** Erases every non-border cell as a single undoable batch. */
    public void clearAll() {
        beginStroke();
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                if (!isBorderCell(c, r) && cells[c][r] != null) apply(c, r, BlockType.ERASER);
            }
        }
        commitStroke();
    }

    // ---- world <-> grid (board centred on origin) ----

    public float worldX(int c) { return (c - (cols - 1) * 0.5f) * CELL; }
    public float worldZ(int r) { return (r - (rows - 1) * 0.5f) * CELL; }

    public int colAt(float x) { return Math.round(x / CELL + (cols - 1) * 0.5f); }
    public int rowAt(float z) { return Math.round(z / CELL + (rows - 1) * 0.5f); }

    // ---- accessors ----

    public Theme theme()     { return theme; }
    public MapSize size()    { return size; }
    public int cols()        { return cols; }
    public int rows()        { return rows; }
    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }
    public void markDirty()  { dirty = true; }
}
