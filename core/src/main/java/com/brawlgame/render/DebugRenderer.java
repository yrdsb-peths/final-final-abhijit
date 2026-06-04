package com.brawlgame.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.brawlgame.entity.Player;

/**
 * Minecraft F3+B-style debug overlay: the white collision AABB, a red loop at eye height, and a
 * blue look-direction cross. Drawn with depth testing disabled so the whole box is visible through
 * the player model (toggle on/off from {@code DungeonGame}).
 */
public final class DebugRenderer implements Disposable {

    private final ShapeRenderer shapes = new ShapeRenderer();

    public void render(Camera camera, Player player) {
        Vector3 p = player.getPosition();
        float w = player.getHitboxWidth();
        float half = w * 0.5f;
        float h = player.getHitboxHeight();
        float eye = player.getEyeHeight();
        float cx = p.x, cz = p.z, y0 = p.y;

        // facing -> forward (front) and right vectors on the ground plane
        float yaw = player.getFacingDeg() * MathUtils.degreesToRadians;
        float fx = -MathUtils.sin(yaw), fz = -MathUtils.cos(yaw);
        float rx = -fz, rz = fx;

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST); // see-through, like the F3 hitbox view
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeType.Line);

        // white collision box (0.6 x height x 0.6)
        shapes.setColor(1f, 1f, 1f, 1f);
        shapes.box(cx - half, y0, cz - half, w, h, w);

        // red eye-height loop
        shapes.setColor(1f, 0.25f, 0.25f, 1f);
        float ey = y0 + eye;
        shapes.line(cx - half, ey, cz - half, cx + half, ey, cz - half);
        shapes.line(cx + half, ey, cz - half, cx + half, ey, cz + half);
        shapes.line(cx + half, ey, cz + half, cx - half, ey, cz + half);
        shapes.line(cx - half, ey, cz + half, cx - half, ey, cz - half);

        // blue look-direction cross at eye level
        shapes.setColor(0.35f, 0.5f, 1f, 1f);
        shapes.line(cx, ey, cz, cx + fx * 0.9f, ey, cz + fz * 0.9f);
        shapes.line(cx - rx * 0.45f, ey, cz - rz * 0.45f, cx + rx * 0.45f, ey, cz + rz * 0.45f);

        shapes.end();
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }

    @Override
    public void dispose() {
        shapes.dispose();
    }
}
