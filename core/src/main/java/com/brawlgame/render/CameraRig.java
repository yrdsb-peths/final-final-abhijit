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

    // ---- bottom-wall proximity elevation ----
    // Near the SOUTH (+Z) playable boundary the trailing camera would otherwise be dragged into the
    // tall canyon scenery (near-plane clipping). As the player enters a buffer zone before that wall,
    // the camera smoothly rises and steepens (pulls toward overhead) so the player stays framed. Only
    // the bottom edge is treated — the camera naturally clears the top/side walls at this angle.
    private static final float BOTTOM_BUFFER = 2f;  // blocks from the wall the effect begins (lower = later)
    private static final float ELEV_EXTRA_Y = 9f;   // max extra camera height at the wall
    private static final float ELEV_PULL_Z  = 3.2f; // how much the camera pulls overhead (steeper pitch)
    private static final float ELEV_LERP     = 4f;  // smoothing speed for entering/leaving the zone

    private float bottomZ = Float.NaN; // world Z of the south boundary; NaN = feature disabled
    private float zoneT = 0f;          // smoothed 0..1 proximity factor
    /** When true the camera sits on the north side of the player (client perspective in multiplayer). */
    private boolean flipped = false;

    private final Vector3 target = new Vector3();
    private final Vector3 desiredPos = new Vector3();
    private final Vector3 lookTmp = new Vector3();
    private boolean initialised = false;

    // Match-intro pan: ease the camera from a far corner (looking at the map centre) into the normal
    // follow pose over a duration, then hand off to the live follow.
    private final Vector3 introFrom = new Vector3();
    private final Vector3 introLookFrom = new Vector3();
    private float introT = -1f, introDur = 0f;

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

    /** Sets the south (+Z) playable-boundary world Z that triggers the proximity elevation. */
    public void setBottomBoundary(float worldMaxZ) {
        this.bottomZ = worldMaxZ;
    }

    /**
     * When {@code true} the camera orbits from the NORTH side of the player (negative-Z offset)
     * instead of the default south side. Use for the client player in a multiplayer match so their
     * character always appears at the bottom of the screen looking up toward the opponent.
     * The 2D UI camera (UiViewport) is completely unaffected — it never rotates.
     */
    public void setFlipped(boolean flipped) { this.flipped = flipped; }

    /** Start a cinematic pan: camera begins at {@code fromPos} looking at {@code lookFrom}, then eases
     *  into the normal follow pose over {@code duration} seconds. */
    public void beginIntro(Vector3 fromPos, Vector3 lookFrom, float duration) {
        introFrom.set(fromPos);
        introLookFrom.set(lookFrom);
        introDur = duration;
        introT = 0f;
        initialised = true; // we drive the position ourselves during the intro
    }

    public boolean isIntroActive() { return introT >= 0f && introT < introDur; }

    public void update(float delta, Vector3 playerPos, boolean sprinting) {
        lookTmp.set(playerPos.x, playerPos.y + LOOK_HEIGHT, playerPos.z);

        // Proximity to the bottom wall → smoothed 0..1, eased. 0 outside the buffer, 1 at the wall.
        float targetT = 0f;
        if (!Float.isNaN(bottomZ)) {
            float dist = bottomZ - playerPos.z; // shrinks as the player nears the +Z wall
            float raw = MathUtils.clamp((BOTTOM_BUFFER - dist) / BOTTOM_BUFFER, 0f, 1f);
            targetT = raw * raw * (3f - 2f * raw); // smoothstep
        }
        zoneT = MathUtils.lerp(zoneT, targetT, Math.min(1f, delta * ELEV_LERP));

        // Raise the camera and pull it overhead as we approach the wall (steeper pitch via lookAt).
        // When flipped the camera sits on the north side so the client player appears at screen-bottom.
        float zSign = flipped ? -1f : 1f;
        desiredPos.set(playerPos);
        desiredPos.x += OFFSET.x;
        desiredPos.y += OFFSET.y + zoneT * ELEV_EXTRA_Y;
        desiredPos.z += (OFFSET.z - zoneT * ELEV_PULL_Z) * zSign;

        if (introT >= 0f && introT < introDur) {
            // Cinematic pan: ease from the far corner into the follow pose (slow → fast → settle).
            float t = introT / introDur;
            float e = t * t * (3f - 2f * t); // smoothstep
            camera.position.set(introFrom).lerp(desiredPos, e);
            target.set(introLookFrom).lerp(lookTmp, e);
            introT += delta;
        } else if (!initialised) {
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
