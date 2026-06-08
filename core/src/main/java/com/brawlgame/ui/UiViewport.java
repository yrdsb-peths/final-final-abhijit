package com.brawlgame.ui;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * A fixed virtual canvas ({@value #W}×{@value #H}) backed by a {@link FitViewport}, shared by all 2D
 * UI. Every panel/button/slot lays out in these virtual units, so the layout scales as a single unit
 * and stays aspect-locked (letter-boxed) no matter the window size — the fix for the UI "scrambling"
 * when the window is resized. Batches/shape-renderers project through {@link #combined()}; mouse input
 * is mapped back to virtual coordinates with {@link #unproject}.
 */
public final class UiViewport {

    /** Virtual canvas size (16:9). All UI coordinates are expressed in these units. */
    public static final float W = 1280f, H = 720f;

    private final OrthographicCamera cam = new OrthographicCamera();
    private final Viewport viewport = new FitViewport(W, H, cam);
    private final Vector2 tmp = new Vector2();

    public UiViewport() {
        viewport.update((int) W, (int) H, true);
    }

    /** Call from the screen's {@code resize(w,h)}. */
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    /** Sets the GL viewport to the letter-boxed region. Call before drawing UI. */
    public void apply() {
        viewport.apply();
    }

    /** Projection matrix for a {@code SpriteBatch}/{@code ShapeRenderer}. */
    public Matrix4 combined() {
        return cam.combined;
    }

    public float width()  { return W; }
    public float height() { return H; }

    /** Window pixel (y-down, as libGDX input reports) → virtual canvas coords (y-up). */
    public Vector2 unproject(float screenX, float screenY) {
        return viewport.unproject(tmp.set(screenX, screenY));
    }

    /**
     * Map a virtual-canvas rect to a real window-pixel rect (y-up, bottom-left origin) — used to place a
     * scissored 3D sub-viewport (e.g. the inventory's rotating model) exactly where a 2D panel box sits.
     * Returns {x, y, w, h} in pixels.
     */
    public float[] toScreen(float vx, float vy, float vw, float vh) {
        float sx = viewport.getScreenX(), sy = viewport.getScreenY();
        float sw = viewport.getScreenWidth(), sh = viewport.getScreenHeight();
        return new float[] { sx + (vx / W) * sw, sy + (vy / H) * sh, (vw / W) * sw, (vh / H) * sh };
    }
}
