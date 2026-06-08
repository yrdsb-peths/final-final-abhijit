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
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

/**
 * A Brawl-Stars-style nameplate drawn in screen space directly above the player entity (the player's
 * chest world point is projected to the screen each frame). Top-to-bottom: <b>name</b>, <b>HP number</b>,
 * a thick <b>green health bar</b>, and a <b>segmented reload/ammo bar</b> whose segment count tracks the
 * equipped weapon. The reload bar depletes a segment per attack and refills over time. Replaces the old
 * screen-space hearts row.
 */
public final class OverheadHud implements Disposable {

    private static final float BAR_W = 132f;
    private static final Color BACK   = new Color(0.10f, 0.10f, 0.12f, 0.85f);
    private static final Color HP_BACK = new Color(0.45f, 0.10f, 0.10f, 1f);
    private static final Color HP_FILL = new Color(0.36f, 0.83f, 0.26f, 1f);
    private static final Color SEG_FILL = new Color(0.98f, 0.66f, 0.12f, 1f);
    private static final Color SEG_EMPTY = new Color(0.22f, 0.22f, 0.25f, 1f);
    private static final Color OUTLINE = new Color(0f, 0f, 0f, 0.9f);

    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout gl = new GlyphLayout();
    private final Vector3 tmp = new Vector3();

    private static final Color SEG_DRY = new Color(0.95f, 0.18f, 0.15f, 1f); // out-of-ammo flash
    private static final float FLASH_TIME = 0.2f;   // red flash duration on a dry trigger
    private static final float SHAKE_TIME = 0.18f;  // ~10 frames of horizontal shake

    // Ammo state is owned by the WeaponController (it gates attacks); the HUD just displays it. The
    // left-most `loaded` of `capacity` segments are filled; a dry trigger flashes the bar red + shakes.
    private int loaded, capacity = 1;
    private float flashTimer;   // >0 → bar flashes red (dry trigger)
    private float shakeTimer;   // >0 → bar shakes on the X axis
    private float shakeX;

    /**
     * @param ammo     currently loaded segments (from the weapon).
     * @param capacity total segments for the equipped weapon.
     * @param dryFire  true on the frame the player clicked while empty.
     */
    public void update(float delta, int ammo, int capacity, boolean dryFire) {
        this.loaded = ammo;
        this.capacity = Math.max(1, capacity);
        if (flashTimer > 0f) flashTimer -= delta;
        if (shakeTimer > 0f) {
            shakeTimer -= delta;
            shakeX = MathUtils.random(-2f, 2f); // rapid X-axis jitter
        } else shakeX = 0f;
        if (dryFire) triggerEmptyShake();
    }

    /** Enter the out-of-ammo state: red flash + a short, rapid horizontal shake. */
    public void triggerEmptyShake() { flashTimer = FLASH_TIME; shakeTimer = SHAKE_TIME; }

    /**
     * @param chestWorld a world point on the player (e.g. chest height) used to anchor the plate.
     */
    private static final Color NAME = new Color(0.40f, 0.92f, 0.36f, 1f); // bold Brawl green
    private static final Color BAR_LIGHT = new Color(0.85f, 0.85f, 0.88f, 1f); // Bedrock bevel

    public void render(Camera cam, Vector3 chestWorld, String name, float hp, float maxHp) {
        tmp.set(chestWorld);
        cam.project(tmp);
        if (tmp.z > 1f) return; // behind the camera

        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        float cx = tmp.x, top = tmp.y + 60f;      // name baseline, hugging just above the head
        float barX = cx - BAR_W * 0.5f;
        float hpBarY = top - 38f, segY = top - 54f;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        batch.getProjectionMatrix().setToOrtho2D(0, 0, w, h);

        shapes.begin(ShapeType.Filled);
        // health bar: dark outline → light Bedrock bevel → red back → green fill
        bedrockBar(barX, hpBarY, BAR_W, 14);
        shapes.setColor(HP_BACK); shapes.rect(barX, hpBarY, BAR_W, 14);
        shapes.setColor(HP_FILL); shapes.rect(barX, hpBarY, BAR_W * clamp01(hp / maxHp), 14);
        // segmented reload bar (Brawl-Stars ammo): orange when loaded, dark when spent; the whole bar
        // flashes red and shakes on the X axis when triggered dry. Each cell reads its own ammo state.
        int n = capacity;
        float segX = barX + shakeX; // X-axis shake offset
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
        outlined(name, cx, top, 1.15f, NAME);                                       // bold green name
        outlined(Math.round(hp) + " / " + Math.round(maxHp), cx, top - 24f, 1.0f, Color.WHITE); // HP number
        batch.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /** A simpler nameplate for non-player entities (the bot): name + HP + green health bar, no reload bar. */
    public void renderSimple(Camera cam, Vector3 chestWorld, String name, float hp, float maxHp) {
        tmp.set(chestWorld);
        cam.project(tmp);
        if (tmp.z > 1f) return;
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        float cx = tmp.x, top = tmp.y + 54f;
        float barX = cx - BAR_W * 0.5f, hpBarY = top - 36f;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        batch.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        shapes.begin(ShapeType.Filled);
        bedrockBar(barX, hpBarY, BAR_W, 14);
        shapes.setColor(HP_BACK); shapes.rect(barX, hpBarY, BAR_W, 14);
        shapes.setColor(HP_FILL); shapes.rect(barX, hpBarY, BAR_W * clamp01(hp / maxHp), 14);
        shapes.end();
        batch.begin();
        outlined(name, cx, top, 1.0f, new Color(1f, 0.55f, 0.45f, 1f)); // rival = warm tint
        outlined(Math.round(hp) + " / " + Math.round(maxHp), cx, top - 22f, 0.85f, Color.WHITE);
        batch.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /** Top-left screen label (e.g. "Brawlers left: N"). */
    public void renderLabel(String text) {
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        batch.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        batch.begin();
        font.getData().setScale(1.3f);
        font.setColor(0f, 0f, 0f, 0.9f);
        font.draw(batch, text, 22f, h - 22f);
        font.draw(batch, text, 24f, h - 22f);
        font.setColor(1f, 1f, 1f, 1f);
        font.draw(batch, text, 23f, h - 21f);
        font.getData().setScale(1f);
        batch.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /** Dark outline + light bevel frame behind a status bar (Bedrock look). */
    private void bedrockBar(float x, float y, float w, float h) {
        shapes.setColor(OUTLINE); shapes.rect(x - 3, y - 3, w + 6, h + 6);
        shapes.setColor(BAR_LIGHT); shapes.rect(x - 2, y - 2, w + 4, h + 4);
        shapes.setColor(BACK); shapes.rect(x - 1, y - 1, w + 2, h + 2);
    }

    /** Centre a string at {@code cx} with a heavy black outline, scaled, in {@code fill}. */
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
