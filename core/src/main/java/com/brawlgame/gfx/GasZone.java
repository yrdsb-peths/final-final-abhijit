package com.brawlgame.gfx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import com.brawlgame.map.GameMap;

/**
 * The Battle-Royale "gas": a toxic zone that closes in from all four map edges toward the centre.
 *
 * <p><b>Not block-based.</b> A continuous <i>safe rectangle</i> (pure float world coordinates, no tile
 * grid) starts at the full map bounds and, once {@link #activate}d, shrinks <i>smoothly and
 * continuously</i> — the boundary slides inward block-by-block toward the next ring rather than
 * snapping. Everything <i>outside</i> the safe rect is in the gas ({@link #inGas}) and takes tick
 * damage (driven by the caller, by comparing the player's raw X/Z against the boundary).
 *
 * <p><b>Rendered</b> as a smooth translucent overlay: a toxic floor wash over every gassed margin plus
 * four rising wall-curtains standing exactly on the live boundary, so you can <i>see</i> the wall
 * closing in. The tint pulses between toxic green and toxic purple.
 */
public final class GasZone implements Disposable {

    // Pacing is derived from map size in the constructor — small mazes get slow, gentle closes.
    private final float ringInterval;
    private final float ringStep;
    private final float closeSpeed;
    private final float minHalf;
    private static final float WALL_H = 6f;                       // gas curtain height (world units)
    private static final float WALL_T = 0.35f;                    // curtain thickness

    private static final Color TOXIC_GREEN  = new Color(0.20f, 0.85f, 0.18f, 1f);
    private static final Color TOXIC_PURPLE = new Color(0.58f, 0.12f, 0.82f, 1f);
    private static final Color TINT = new Color();

    private final float fullMinX, fullMaxX, fullMinZ, fullMaxZ, centerX, centerZ;
    // Live boundary (what the player is tested against + what is drawn).
    private float safeMinX, safeMaxX, safeMinZ, safeMaxZ;
    // Target ring the live boundary is sliding toward.
    private float tgtMinX, tgtMaxX, tgtMinZ, tgtMaxZ;
    private boolean active;
    private boolean suddenDeath;
    private float ringTimer, pulse;

    private final Model floorModel, wallModel;
    private final ModelInstance floor, wall;
    private final ColorAttribute floorColor, wallColor;
    private final BlendingAttribute floorBlend, wallBlend;

    public GasZone(GameMap map) {
        fullMinX = map.worldX(0) - GameMap.CELL * 0.5f;
        fullMaxX = map.worldX(map.cols() - 1) + GameMap.CELL * 0.5f;
        fullMinZ = map.worldZ(0) - GameMap.CELL * 0.5f;
        fullMaxZ = map.worldZ(map.rows() - 1) + GameMap.CELL * 0.5f;
        centerX = (fullMinX + fullMaxX) * 0.5f;
        centerZ = (fullMinZ + fullMaxZ) * 0.5f;
        safeMinX = tgtMinX = fullMinX; safeMaxX = tgtMaxX = fullMaxX;
        safeMinZ = tgtMinZ = fullMinZ; safeMaxZ = tgtMaxZ = fullMaxZ;

        float mapW = fullMaxX - fullMinX;
        float mapD = fullMaxZ - fullMinZ;
        float minDim = Math.max(GameMap.CELL * 4f, Math.min(mapW, mapD));
        // ~6% shrink per phase, at least 25s apart on small boards; never more than one cell/sec slide.
        ringInterval = Math.max(25f, minDim * 2.8f);
        ringStep = Math.max(GameMap.CELL * 0.65f, minDim * 0.06f);
        // Faster close so the gas visibly catches up within seconds of a phase change.
        closeSpeed = Math.max(GameMap.CELL * 1.5f, ringStep * 0.8f);
        minHalf = Math.max(GameMap.CELL * 1.5f, minDim * 0.10f);

        ModelBuilder mb = new ModelBuilder();

        // Flat unit floor quad (horizontal, normal +Y), reused for the four gassed-margin washes.
        floorModel = mb.createRect(
            -0.5f, 0f, 0.5f,  0.5f, 0f, 0.5f,  0.5f, 0f, -0.5f,  -0.5f, 0f, -0.5f,  0f, 1f, 0f,
            toxicMat(0.32f), Usage.Position | Usage.Normal);
        floor = new ModelInstance(floorModel);
        floorColor = (ColorAttribute) floor.materials.get(0).get(ColorAttribute.Diffuse);
        floorBlend = (BlendingAttribute) floor.materials.get(0).get(BlendingAttribute.Type);

        // Unit cube (centred), reused as the four rising wall-curtains on the live boundary.
        wallModel = mb.createBox(1f, 1f, 1f,
            toxicMat(0.30f), Usage.Position | Usage.Normal);
        wall = new ModelInstance(wallModel);
        wallColor = (ColorAttribute) wall.materials.get(0).get(ColorAttribute.Diffuse);
        wallBlend = (BlendingAttribute) wall.materials.get(0).get(BlendingAttribute.Type);
    }

    private static Material toxicMat(float opacity) {
        return new Material(
            ColorAttribute.createDiffuse(TOXIC_PURPLE),
            new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, opacity),
            // Depth-test against the world but DON'T write depth, so the tall translucent curtains
            // never occlude the characters/weapons drawn in the later pass.
            new DepthTestAttribute(GL20.GL_LEQUAL, false),
            IntAttribute.createCullFace(GL20.GL_NONE));
    }

    public boolean isActive() { return active; }
    public void activate() { active = true; }

    /** Directly set where the gas should close toward (overrides internal ring-timer stepping). */
    public void setTargetBounds(float minX, float maxX, float minZ, float maxZ) {
        tgtMinX = MathUtils.clamp(minX, fullMinX, centerX);
        tgtMaxX = MathUtils.clamp(maxX, centerX, fullMaxX);
        tgtMinZ = MathUtils.clamp(minZ, fullMinZ, centerZ);
        tgtMaxZ = MathUtils.clamp(maxZ, centerZ, fullMaxZ);
    }

    public float fullMinX() { return fullMinX; }
    public float fullMaxX() { return fullMaxX; }
    public float fullMinZ() { return fullMinZ; }
    public float fullMaxZ() { return fullMaxZ; }
    public float centerX()  { return centerX; }
    public float centerZ()  { return centerZ; }

    /** Instantly collapse the safe zone to zero area — everything takes gas damage. */
    public void suddenDeath() {
        suddenDeath = true;
        safeMinX = safeMaxX = centerX;
        safeMinZ = safeMaxZ = centerZ;
        tgtMinX = tgtMaxX = centerX;
        tgtMinZ = tgtMaxZ = centerZ;
    }

    public boolean isSuddenDeath() { return suddenDeath; }

    /** Outside the live safe rectangle = in the gas (pure coordinate test, no tile grid). */
    public boolean inGas(float x, float z) {
        if (suddenDeath) return true;
        return x < safeMinX || x > safeMaxX || z < safeMinZ || z > safeMaxZ;
    }

    public void update(float delta) {
        pulse = (pulse + delta) % 1000f; // bounded so sin() keeps precision over a long match
        if (!active) return;

        // Step the TARGET ring inward on the interval (clamped to the centre box).
        ringTimer += delta;
        if (ringTimer >= ringInterval) {
            ringTimer -= ringInterval;
            tgtMinX = Math.min(centerX - minHalf, tgtMinX + ringStep);
            tgtMaxX = Math.max(centerX + minHalf, tgtMaxX - ringStep);
            tgtMinZ = Math.min(centerZ - minHalf, tgtMinZ + ringStep);
            tgtMaxZ = Math.max(centerZ + minHalf, tgtMaxZ - ringStep);
        }

        float step = closeSpeed * delta;
        safeMinX = approach(safeMinX, tgtMinX, step);
        safeMaxX = approach(safeMaxX, tgtMaxX, step);
        safeMinZ = approach(safeMinZ, tgtMinZ, step);
        safeMaxZ = approach(safeMaxZ, tgtMaxZ, step);
    }

    private static float approach(float cur, float target, float step) {
        if (cur < target) return Math.min(target, cur + step);
        if (cur > target) return Math.max(target, cur - step);
        return cur;
    }

    /** Draw the toxic floor wash + the four rising wall-curtains (call in the world 3D pass). */
    public void render(ModelBatch batch, Environment env) {
        if (!active) return;

        // Pulse the tint between toxic green and purple, and breathe the opacity.
        float t = 0.5f + 0.5f * MathUtils.sin(pulse * 2.2f);
        TINT.set(TOXIC_GREEN).lerp(TOXIC_PURPLE, t);
        float floorA = 0.26f + 0.10f * MathUtils.sin(pulse * 3.5f);
        float wallA  = 0.34f + 0.12f * MathUtils.sin(pulse * 3.5f + 1.2f);
        floorColor.color.set(TINT); floorBlend.opacity = floorA;
        wallColor.color.set(TINT);  wallBlend.opacity = wallA;

        // Floor wash over the four gassed margins (top/bottom span full width; sides fill the gap).
        drawFloor(batch, env, fullMinX, fullMaxX, fullMinZ, safeMinZ);
        drawFloor(batch, env, fullMinX, fullMaxX, safeMaxZ, fullMaxZ);
        drawFloor(batch, env, fullMinX, safeMinX, safeMinZ, safeMaxZ);
        drawFloor(batch, env, safeMaxX, fullMaxX, safeMinZ, safeMaxZ);

        // Rising wall-curtains standing on the live boundary (the visible "closing wall").
        float w = safeMaxX - safeMinX, d = safeMaxZ - safeMinZ;
        if (w > 0.02f && d > 0.02f) {
            drawWall(batch, env, centerX, safeMinZ, w + WALL_T, WALL_T); // north edge
            drawWall(batch, env, centerX, safeMaxZ, w + WALL_T, WALL_T); // south edge
            drawWall(batch, env, safeMinX, centerZ, WALL_T, d);          // west edge
            drawWall(batch, env, safeMaxX, centerZ, WALL_T, d);          // east edge
        }
    }

    private void drawFloor(ModelBatch batch, Environment env, float x0, float x1, float z0, float z1) {
        float w = x1 - x0, d = z1 - z0;
        if (w <= 0.01f || d <= 0.01f) return;
        floor.transform.setToTranslation((x0 + x1) * 0.5f, 0.06f, (z0 + z1) * 0.5f).scale(w, 1f, d);
        batch.render(floor, env);
    }

    private void drawWall(ModelBatch batch, Environment env, float cx, float cz, float sx, float sz) {
        wall.transform.setToTranslation(cx, WALL_H * 0.5f, cz).scale(sx, WALL_H, sz);
        batch.render(wall, env);
    }

    @Override
    public void dispose() { floorModel.dispose(); wallModel.dispose(); }
}
