package com.brawlgame.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

/**
 * A Brawl-Stars-style nameplate drawn in screen space directly above entities. Uses the shared
 * {@link UiViewport} so health bars, reload segments, and labels stay locked together on resize.
 */
public final class OverheadHud implements Disposable {

    private static final float BAR_W = 132f;
    private static final Color BACK   = new Color(0.10f, 0.10f, 0.12f, 0.85f);
    private static final Color HP_BACK = new Color(0.45f, 0.10f, 0.10f, 1f);
    private static final Color HP_FILL = new Color(0.36f, 0.83f, 0.26f, 1f);
    private static final Color SEG_FILL = new Color(0.98f, 0.66f, 0.12f, 1f);
    private static final Color SEG_EMPTY = new Color(0.22f, 0.22f, 0.25f, 1f);
    private static final Color OUTLINE = new Color(0f, 0f, 0f, 0.9f);
    private static final Color NAME = new Color(0.40f, 0.92f, 0.36f, 1f);
    private static final Color BAR_LIGHT = new Color(0.85f, 0.85f, 0.88f, 1f);
    private static final Color SEG_DRY = new Color(0.95f, 0.18f, 0.15f, 1f);

    private static final float FLASH_TIME = 0.2f;
    private static final float SHAKE_TIME = 0.18f;

    private final UiViewport uiv = new UiViewport();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout gl = new GlyphLayout();
    private final Vector3 tmp = new Vector3();
    private final Vector2 vc = new Vector2();

    private int loaded, capacity = 1;
    private float flashTimer, shakeTimer, shakeX;

    public void update(float delta, int ammo, int capacity, boolean dryFire) {
        this.loaded = ammo;
        this.capacity = Math.max(1, capacity);
        if (flashTimer > 0f) flashTimer -= delta;
        if (shakeTimer > 0f) {
            shakeTimer -= delta;
            shakeX = MathUtils.random(-2f, 2f);
        } else shakeX = 0f;
        if (dryFire) triggerEmptyShake();
    }

    public void triggerEmptyShake() { flashTimer = FLASH_TIME; shakeTimer = SHAKE_TIME; }

    /** Sync both rendering pipelines after a window resize — call from the screen's resize(). */
    public void resize(int width, int height) {
        uiv.resize(width, height);
        syncMatrices();
    }

    private void syncMatrices() {
        shapes.setProjectionMatrix(uiv.combined());
        batch.setProjectionMatrix(uiv.combined());
    }

    private void beginHud() {
        uiv.apply();
        syncMatrices();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void endHud() {
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /** Project a world chest point into virtual-canvas coordinates. Returns false if behind camera. */
    private boolean project(Camera cam, Vector3 world) {
        tmp.set(world);
        cam.project(tmp);
        if (tmp.z > 1f) return false;
        uiv.unproject(tmp.x, tmp.y, vc);
        return true;
    }

    public void render(Camera cam, Vector3 chestWorld, String name, float hp, float maxHp) {
        if (!project(cam, chestWorld)) return;
        beginHud();

        float cx = vc.x, top = vc.y + 20f;
        float barX = cx - BAR_W * 0.5f;
        float hpBarY = top - 38f, segY = top - 54f;

        shapes.begin(ShapeType.Filled);
        bedrockBar(barX, hpBarY, BAR_W, 14);
        shapes.setColor(HP_BACK); shapes.rect(barX, hpBarY, BAR_W, 14);
        shapes.setColor(HP_FILL); shapes.rect(barX, hpBarY, BAR_W * clamp01(hp / maxHp), 14);

        int n = capacity;
        float segX = barX + shakeX;
        float gap = 3f, segW = (BAR_W - (n - 1) * gap) / n;
        boolean flashing = flashTimer > 0f;
        bedrockBar(segX, segY, BAR_W, 9);
        for (int i = 0; i < n; i++) {
            float x = segX + i * (segW + gap);
            shapes.setColor(flashing ? SEG_DRY : i < loaded ? SEG_FILL : SEG_EMPTY);
            shapes.rect(x, segY, segW, 9);
        }
        shapes.end();

        batch.begin();
        outlined(name, cx, top, 1.15f, NAME);
        outlined(Math.round(hp) + " / " + Math.round(maxHp), cx, top - 24f, 1.0f, Color.WHITE);
        batch.end();
        endHud();
    }

    public void renderSimple(Camera cam, Vector3 chestWorld, String name, float hp, float maxHp) {
        if (!project(cam, chestWorld)) return;
        beginHud();

        float cx = vc.x, top = vc.y + 20f;
        float barX = cx - BAR_W * 0.5f, hpBarY = top - 36f;

        shapes.begin(ShapeType.Filled);
        bedrockBar(barX, hpBarY, BAR_W, 14);
        shapes.setColor(HP_BACK); shapes.rect(barX, hpBarY, BAR_W, 14);
        shapes.setColor(HP_FILL); shapes.rect(barX, hpBarY, BAR_W * clamp01(hp / maxHp), 14);
        shapes.end();

        batch.begin();
        outlined(name, cx, top, 1.0f, new Color(1f, 0.55f, 0.45f, 1f));
        outlined(Math.round(hp) + " / " + Math.round(maxHp), cx, top - 22f, 0.85f, Color.WHITE);
        batch.end();
        endHud();
    }

    /** Top-left label in virtual canvas space (e.g. "Brawlers left: N"). */
    public void renderLabel(String text) {
        beginHud();
        batch.begin();
        font.getData().setScale(1.3f);
        font.setColor(0f, 0f, 0f, 0.9f);
        font.draw(batch, text, 22f, uiv.height() - 22f);
        font.draw(batch, text, 24f, uiv.height() - 22f);
        font.setColor(1f, 1f, 1f, 1f);
        font.draw(batch, text, 23f, uiv.height() - 21f);
        font.getData().setScale(1f);
        batch.end();
        endHud();
    }

    private void bedrockBar(float x, float y, float w, float h) {
        shapes.setColor(OUTLINE); shapes.rect(x - 3, y - 3, w + 6, h + 6);
        shapes.setColor(BAR_LIGHT); shapes.rect(x - 2, y - 2, w + 4, h + 4);
        shapes.setColor(BACK); shapes.rect(x - 1, y - 1, w + 2, h + 2);
    }

    private void outlined(String t, float cx, float baseY, float scale, Color fill) {
        font.getData().setScale(scale);
        gl.setText(font, t);
        float x = cx - gl.width * 0.5f;
        font.setColor(0f, 0f, 0f, 0.95f);
        for (int ox = -2; ox <= 2; ox++) for (int oy = -2; oy <= 2; oy++)
            if (ox != 0 || oy != 0) font.draw(batch, t, x + ox, baseY + oy);
        font.setColor(fill);
        font.draw(batch, t, x, baseY);
        font.getData().setScale(1f);
    }

    private static float clamp01(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }

    @Override
    public void dispose() { shapes.dispose(); batch.dispose(); font.dispose(); }
}
