package com.brawlgame.entity;

import com.badlogic.gdx.math.Vector2;
import com.brawlgame.combat.BlockCollider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Lightweight A* grid pathfinder used by {@link AiBrawler} to navigate around walls it cannot jump over.
 * Operates at the same cell resolution as the map's {@link BlockCollider}.  The grid is re-evaluated
 * lazily — only when the collider is set or explicitly requested — so runtime cost is negligible.
 */
public final class BotPathfinder {

    private BlockCollider collider;
    private float minX, maxX, minZ, maxZ;

    // Grid bounds (cells)
    private int cols, rows;
    private float originX, originZ, cellSize;

    public BotPathfinder(float minX, float maxX, float minZ, float maxZ) {
        this.minX = minX; this.maxX = maxX; this.minZ = minZ; this.maxZ = maxZ;
    }

    public void setCollider(BlockCollider c) {
        this.collider = c;
        if (c != null) {
            cellSize = c.cellSize();
            originX  = minX;
            originZ  = minZ;
            cols = Math.max(1, (int) Math.ceil((maxX - minX) / cellSize) + 1);
            rows = Math.max(1, (int) Math.ceil((maxZ - minZ) / cellSize) + 1);
        }
    }

    /**
     * Returns a list of world-space waypoints from (startX,startZ) to (goalX,goalZ), or an
     * empty list when no path is found.  The caller should step toward the first waypoint and
     * pop it once reached (within ~0.8 world units).
     */
    public List<Vector2> findPath(float startX, float startZ, float goalX, float goalZ) {
        if (collider == null) return Collections.emptyList();

        int sc = worldToCol(startX), sr = worldToRow(startZ);
        int gc = worldToCol(goalX),  gr = worldToRow(goalZ);
        if (sc == gc && sr == gr) return Collections.emptyList();
        if (!inBounds(gc, gr)) return Collections.emptyList();

        int total = cols * rows;
        float[] g  = new float[total]; // cost from start
        float[] f  = new float[total]; // g + heuristic
        int[]   px = new int[total];   // parent cell index
        boolean[] closed = new boolean[total];

        java.util.Arrays.fill(g,  Float.MAX_VALUE);
        java.util.Arrays.fill(px, -1);

        int startIdx = idx(sc, sr);
        g[startIdx] = 0f;
        f[startIdx] = heuristic(sc, sr, gc, gr);

        PriorityQueue<int[]> open = new PriorityQueue<>((a, b) -> Float.compare(f[a[0]], f[b[0]]));
        open.add(new int[]{ startIdx });

        int[] dc = {0, 0, 1, -1, 1, 1, -1, -1};
        int[] dr = {1, -1, 0, 0, 1, -1, 1, -1};
        float[] cost = {1f, 1f, 1f, 1f, 1.414f, 1.414f, 1.414f, 1.414f};

        while (!open.isEmpty()) {
            int curIdx = open.poll()[0];
            if (closed[curIdx]) continue;
            closed[curIdx] = true;
            int cc = curIdx % cols, cr = curIdx / cols;
            if (cc == gc && cr == gr) return reconstructPath(px, curIdx, sc, sr);

            for (int d = 0; d < 8; d++) {
                int nc = cc + dc[d], nr = cr + dr[d];
                if (!inBounds(nc, nr)) continue;
                if (blocked(nc, nr)) continue;
                int ni = idx(nc, nr);
                if (closed[ni]) continue;
                float ng = g[curIdx] + cost[d];
                if (ng < g[ni]) {
                    g[ni] = ng;
                    f[ni] = ng + heuristic(nc, nr, gc, gr);
                    px[ni] = curIdx;
                    open.add(new int[]{ ni });
                }
            }
        }
        return Collections.emptyList(); // no path found
    }

    private List<Vector2> reconstructPath(int[] px, int endIdx, int sc, int sr) {
        List<Vector2> path = new ArrayList<>();
        int cur = endIdx;
        while (cur != -1) {
            int c = cur % cols, r = cur / cols;
            path.add(new Vector2(colToWorld(c), rowToWorld(r)));
            int par = px[cur];
            if (par == -1 || (par % cols == sc && par / cols == sr)) break;
            cur = par;
        }
        Collections.reverse(path);
        // Remove the first node (≈ start position) so the bot steers forward immediately
        if (!path.isEmpty()) path.remove(0);
        return path;
    }

    private boolean blocked(int c, int r) {
        if (collider == null || !inBounds(c, r)) return true;
        int gc = collider.colAt(colToWorld(c)), gr = collider.rowAt(rowToWorld(r));
        return collider.collisionHeightAt(gc, gr) > 1.3f; // passable if ≤1-block tall (bot can hop)
    }

    private float heuristic(int c1, int r1, int c2, int r2) {
        return Math.abs(c1 - c2) + Math.abs(r1 - r2);
    }

    private boolean inBounds(int c, int r) { return c >= 0 && c < cols && r >= 0 && r < rows; }
    private int idx(int c, int r)           { return r * cols + c; }

    private int worldToCol(float x) { return (int) Math.floor((x - originX) / cellSize); }
    private int worldToRow(float z) { return (int) Math.floor((z - originZ) / cellSize); }
    private float colToWorld(int c) { return originX + c * cellSize + cellSize * 0.5f; }
    private float rowToWorld(int r) { return originZ + r * cellSize + cellSize * 0.5f; }
}
