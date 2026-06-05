package com.brawlgame.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.brawlgame.entity.Player;

/**
 * Minecraft F3+B-style hitbox view: pure white wireframe AABBs around entity collision bounds, and
 * nothing else (no axes, look vectors, or per-bone boxes). Drawn with a {@link ShapeRenderer} in
 * line mode on {@code camera.combined}, with depth testing off so the box shows through the model.
 */
public final class DebugRenderer implements Disposable {

    private final ShapeRenderer shapes = new ShapeRenderer();

    public void render(Camera camera, Player player) {
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST); // show through, like F3+B
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeType.Line);
        shapes.setColor(Color.WHITE);

        Vector3 p = player.getPosition();
        drawAABB(p.x, p.y, p.z, player.getHitboxWidth(), player.getHitboxHeight());

        shapes.end();
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }

    /** Wireframe AABB centred on (cx,cz), from y up to y+height. Reusable for entities later. */
    private void drawAABB(float cx, float y, float cz, float width, float height) {
        float half = width * 0.5f;
        // +Z corner is the origin because ShapeRenderer.box extends its depth toward -Z (else it's
        // drawn ~one box-depth out of place).
        shapes.box(cx - half, y, cz + half, width, height, width);
    }

    @Override
    public void dispose() {
        shapes.dispose();
    }
}
