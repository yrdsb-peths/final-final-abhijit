package com.brawlgame.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;

/**
 * Free-roaming developer spectator camera for the Map Maker. It looks down at a fixed steep
 * Dungeons-style angle (so the 3-tall walls read in 3D) and never rotates with input — WASD pans the
 * look-target across the XZ plane (screen-aligned), and the mouse wheel zooms by raising/lowering the
 * rig along its view offset. There is no player entity; the camera simply hovers over the grid.
 */
public final class SpectatorCamera {

    public final PerspectiveCamera camera;

    /** View offset from the look target — same steep pitch as the gameplay rig, but taller. */
    private static final Vector3 OFFSET = new Vector3(0f, 18f, 12f);
    private static final float PAN_SPEED = 14f;   // blocks/sec at zoom 1.0
    private static final float ZOOM_MIN = 0.45f, ZOOM_MAX = 3.0f;
    private static final float ZOOM_STEP = 0.12f;

    private final Vector3 target = new Vector3(); // point on the y=0 plane the camera looks at
    private float zoom = 1.2f;

    public SpectatorCamera() {
        camera = new PerspectiveCamera(55f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.1f;
        camera.far = 600f;
        apply();
    }

    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
    }

    /** Adjusts zoom from a mouse-wheel notch (libGDX wheel-up is negative). */
    public void zoom(float amountY) {
        zoom = MathUtils.clamp(zoom + amountY * ZOOM_STEP, ZOOM_MIN, ZOOM_MAX);
    }

    public void update(float delta) {
        float pan = PAN_SPEED * zoom * delta;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) target.z -= pan;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) target.z += pan;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) target.x -= pan;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) target.x += pan;
        apply();
    }

    private void apply() {
        camera.position.set(target).mulAdd(OFFSET, zoom);
        camera.up.set(Vector3.Y);
        camera.lookAt(target);
        camera.update();
    }

    /** Pick ray through a screen pixel (Gdx.input coords), for grid hit-testing. */
    public Ray pickRay(int screenX, int screenY) {
        return camera.getPickRay(screenX, screenY);
    }

    /** The point on the ground the camera is centred on (for the shadow frustum to follow). */
    public Vector3 getTarget() {
        return target;
    }
}
