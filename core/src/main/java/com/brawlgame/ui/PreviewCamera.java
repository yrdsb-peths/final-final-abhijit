package com.brawlgame.ui;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;

/**
 * Frames a {@link PerspectiveCamera} so a model of a known bounding box fits fully — head to feet — inside
 * a render region of any aspect ratio, with a uniform margin. This is the fix for the inventory/pause
 * character previews clipping the top or bottom: the camera distance is derived from BOTH the content
 * height and width against the region's aspect, then the larger (further-back) distance wins so nothing
 * is ever cut off. The camera looks straight at the content's vertical centre (front view; the caller
 * spins the model).
 */
public final class PreviewCamera {

    private static final float MARGIN = 1.35f; // 35% padding around the model

    private PreviewCamera() {}

    /**
     * @param yMin/yMax vertical extent of the content in world units (include any pedestal).
     * @param halfWidth half the content's max horizontal extent in world units.
     */
    public static void frame(PerspectiveCamera cam, int rw, int rh, float yMin, float yMax, float halfWidth) {
        if (rw <= 0 || rh <= 0) return;
        float cy = (yMin + yMax) * 0.5f;
        float tanV = (float) Math.tan(cam.fieldOfView * 0.5f * MathUtils.degreesToRadians);
        float aspect = (float) rw / rh;
        float fitH = (yMax - yMin) * MARGIN;
        float fitW = halfWidth * 2f * MARGIN;
        float distForHeight = (fitH * 0.5f) / tanV;
        float distForWidth = (fitW * 0.5f) / (tanV * aspect);
        float dist = Math.max(distForHeight, distForWidth);

        cam.viewportWidth = rw;
        cam.viewportHeight = rh;
        cam.position.set(0f, cy, dist);
        cam.up.set(0f, 1f, 0f);
        cam.lookAt(0f, cy, 0f);
        cam.update();
    }
}
