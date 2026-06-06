package com.brawlgame.map;

import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelCache;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.utils.Disposable;

/**
 * Bakes the whole {@link GameMap} board into two batched {@link ModelCache}s — one opaque, one
 * transparent — and replays them each frame. Rebuilding is lazy: it only re-bakes when the map marks
 * itself {@link GameMap#isDirty() dirty} (i.e. the dev painted or erased a cell), so dragging the
 * brush across a big board stays cheap.
 *
 * <p>Per cell it asks the {@link BlockLibrary} for a positioned {@link ModelInstance} and routes it
 * to the opaque cache (solids, fences, chests) or the transparent cache (bushes, water, spawn
 * markers) via {@link BlockLibrary#isTransparent}. The lit ground plane goes into the opaque cache.
 * Both caches keep their geometry between rebuilds so {@link #render} works every frame.
 *
 * <p>This class does NOT call {@code batch.begin/end} — the owning screen brackets the whole 3D pass
 * and calls {@link #render} inside it.
 */
public final class MapRenderer implements Disposable {

    private final GameMap map;
    private final BlockLibrary lib;

    // Batched, replayable geometry. Rebuilt only when the map goes dirty.
    private final ModelCache opaque = new ModelCache();
    private final ModelCache transparent = new ModelCache();

    public MapRenderer(GameMap map, BlockLibrary lib) {
        this.map = map;
        this.lib = lib;
    }

    /** Re-bakes both caches from the current board state when (and only when) the map is dirty. */
    public void rebuildIfDirty() {
        if (!map.isDirty()) return;

        opaque.begin();
        transparent.begin();

        for (int c = 0; c < map.cols(); c++) {
            for (int r = 0; r < map.rows(); r++) {
                // Two-tone checkerboard floor tile under every cell.
                opaque.add(lib.floorTile(((c + r) & 1) == 0, map.worldX(c), map.worldZ(r)));

                BlockType type = map.get(c, r);
                if (type == null) continue;
                ModelInstance inst = lib.instance(type, map.worldX(c), map.worldZ(r));
                if (inst == null) continue; // ERASER / unsupported
                if (lib.isTransparent(type)) transparent.add(inst);
                else opaque.add(inst);
            }
        }

        opaque.end();
        transparent.end();
        map.clearDirty();
    }

    /** Replays the opaque pass (lit) then the transparent pass. Caller owns {@code batch.begin/end}. */
    public void render(ModelBatch batch, Environment env) {
        batch.render(opaque, env);
        batch.render(transparent, env);
    }

    /** Renders only the opaque geometry (floor + walls) — the shadow-caster depth pass. */
    public void renderCasters(ModelBatch batch) {
        batch.render(opaque);
    }

    @Override
    public void dispose() {
        opaque.dispose();
        transparent.dispose();
    }
}
