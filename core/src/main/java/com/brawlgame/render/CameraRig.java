package com.brawlgame.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/**
 * Minecraft Dungeons-style camera: a fixed, steep top-down angle that smoothly follows the player.
 * It never rotates with input — the offset is constant, so WASD stays screen-aligned.
 */
public final class CameraRig {

    public final PerspectiveCamera camera;

    // Offset from the player. (0,14,9) ~= 57 degrees looking down, viewing from the +Z side
    // so that "up the screen" is -Z (matching W = move forward).
    private static final Vector3 OFFSET = new Vector3(0f, 11f, 7f);
    private static final float LOOK_HEIGHT = 1.0f; // aim at the chest, not the feet
    private static final float FOLLOW = 9f;        // higher = snappier follow (horizontal)
    private static final float FOLLOW_Y = 16f;     // tighter vertical follow so jumps stay framed
    private static final float FOV_BASE = 50f, FOV_SPRINT = 56f; // sprint widens FOV (vanilla speed cue)

    private final Vector3 target = new Vector3();
    private final Vector3 desiredPos = new Vector3();
    private final Vector3 lookTmp = new Vector3();
    private boolean initialised = false;

    public CameraRig() {
        camera = new PerspectiveCamera(FOV_BASE, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.1f;
        camera.far = 300f;
    }

    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
    }

    public void update(float delta, Vector3 playerPos, boolean sprinting) {
        lookTmp.set(playerPos.x, playerPos.y + LOOK_HEIGHT, playerPos.z);
        desiredPos.set(playerPos).add(OFFSET);

        if (!initialised) {
            target.set(lookTmp);
            camera.position.set(desiredPos);
            initialised = true;
        } else {
            float a = Math.min(1f, delta * FOLLOW);
            float ay = Math.min(1f, delta * FOLLOW_Y);
            target.x = MathUtils.lerp(target.x, lookTmp.x, a);
            target.y = MathUtils.lerp(target.y, lookTmp.y, ay);
            target.z = MathUtils.lerp(target.z, lookTmp.z, a);
            camera.position.x = MathUtils.lerp(camera.position.x, desiredPos.x, a);
            camera.position.y = MathUtils.lerp(camera.position.y, desiredPos.y, ay);
            camera.position.z = MathUtils.lerp(camera.position.z, desiredPos.z, a);
        }

        camera.up.set(Vector3.Y);
        camera.lookAt(target);
        camera.fieldOfView = MathUtils.lerp(camera.fieldOfView, sprinting ? FOV_SPRINT : FOV_BASE, Math.min(1f, delta * 6f));
        camera.update();
    }
}
