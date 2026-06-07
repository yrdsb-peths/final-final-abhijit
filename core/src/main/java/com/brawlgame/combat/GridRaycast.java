package com.brawlgame.combat;

import com.badlogic.gdx.math.Vector3;

/**
 * Continuous (swept) collision against the world's solid grid — shared by the potato projectile's
 * movement CCD ({@link PotatoProjectile}) and the gun's muzzle-spawn verification
 * ({@link WeaponController}).
 *
 * <p>Discrete per-frame AABB overlap checks miss any wall the mover jumps clean over between two
 * frames (the classic "tunnelling" bug). Point-sampling the {@code from → to} segment at fixed steps
 * is better but still leaks: a path that only clips the <i>corner</i> of a cell can spend less than a
 * sample-step inside it, so every sample lands in a neighbouring cell and the wall is skipped at that
 * angle.
 *
 * <p>So this does an exact grid traversal (Amanatides &amp; Woo DDA) in the X/Z plane: it walks the
 * segment cell-by-cell, visiting <b>every</b> cell the line passes through in order, and for each
 * solid cell solves whether the segment's (linearly interpolated) height enters that cell's solid
 * column {@code [0, collisionHeightAt]} while inside its footprint. The first such cell is the hit;
 * its exact entry point is written to {@code out}. The grid is only ever queried through
 * {@link BlockCollider}, so this stays decoupled from the map module.
 */
final class GridRaycast {

    private GridRaycast() {}

    private static final float EPS = 1e-4f;

    /**
     * Sweeps the segment {@code from → to} against solid cells. Returns {@code true} on the first hit,
     * writing the contact point (flush against the surface, just in front of the wall) into
     * {@code out}. Returns {@code false} — leaving {@code out} untouched — if the whole segment is
     * clear. Cells below the floor ({@code y < 0}) are ignored here; the caller handles the floor.
     */
    static boolean firstHit(BlockCollider c, Vector3 from, Vector3 to, Vector3 out) {
        if (c == null) return false;

        float dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        float size = c.cellSize();

        int cx = c.colAt(from.x), cz = c.rowAt(from.z);
        int stepX = dx > 0f ? 1 : (dx < 0f ? -1 : 0);
        int stepZ = dz > 0f ? 1 : (dz < 0f ? -1 : 0);

        // Parametric t (0..1 along the segment) to reach the next cell boundary, and the t-stride
        // between successive boundaries, on each axis. Axes with no motion never cross a boundary.
        float tMaxX, tDeltaX;
        if (stepX != 0) {
            float boundary = c.cellCenterX(cx) + stepX * 0.5f * size;
            tMaxX = (boundary - from.x) / dx;
            tDeltaX = size / Math.abs(dx);
        } else { tMaxX = Float.MAX_VALUE; tDeltaX = Float.MAX_VALUE; }

        float tMaxZ, tDeltaZ;
        if (stepZ != 0) {
            float boundary = c.cellCenterZ(cz) + stepZ * 0.5f * size;
            tMaxZ = (boundary - from.z) / dz;
            tDeltaZ = size / Math.abs(dz);
        } else { tMaxZ = Float.MAX_VALUE; tDeltaZ = Float.MAX_VALUE; }

        float tEnter = 0f;
        // Cap iterations at the Manhattan cell span (+slack) so float drift can't loop forever.
        int budget = Math.abs(c.colAt(to.x) - cx) + Math.abs(c.rowAt(to.z) - cz) + 3;
        for (int guard = 0; tEnter <= 1f && guard <= budget; guard++) {
            float tExit = Math.min(Math.min(tMaxX, tMaxZ), 1f);

            float h = c.collisionHeightAt(cx, cz);
            if (h > 0f) {
                float tHit = firstInColumn(from.y, dy, tEnter, tExit, h);
                if (tHit >= 0f) {
                    out.set(from).lerp(to, Math.max(0f, tHit - EPS));
                    return true;
                }
            }

            // Step into the next cell across whichever boundary comes first.
            if (tMaxX < tMaxZ) { cx += stepX; tEnter = tMaxX; tMaxX += tDeltaX; }
            else               { cz += stepZ; tEnter = tMaxZ; tMaxZ += tDeltaZ; }
        }
        return false;
    }

    /**
     * Earliest t in {@code [tEnter, tExit]} at which the linear height {@code y(t)=y0+dy·t} lies in the
     * solid column {@code [0, h]} (i.e. the potato is at or below the wall top and above the floor), or
     * {@code -1} if it never does while inside this cell.
     */
    private static float firstInColumn(float y0, float dy, float tEnter, float tExit, float h) {
        if (dy == 0f) {
            return (y0 >= 0f && y0 <= h) ? tEnter : -1f;
        }
        // y is monotonic, so it's within [0, h] exactly between the t where it crosses 0 and crosses h.
        float tAtH = (h - y0) / dy;
        float tAt0 = -y0 / dy;
        float lo = Math.max(Math.min(tAtH, tAt0), tEnter);
        float hi = Math.min(Math.max(tAtH, tAt0), tExit);
        return lo <= hi ? lo : -1f;
    }
}
