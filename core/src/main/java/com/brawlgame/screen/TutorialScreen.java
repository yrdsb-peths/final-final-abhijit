package com.brawlgame.screen;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Vector2;
import com.brawlgame.game.PlayerProfile;
import com.brawlgame.ui.BedrockWidgets;
import com.brawlgame.ui.UiViewport;

/**
 * First-launch card tutorial shown before the main menu.
 *
 * <p>Three cards walk the player through Movement → Combat → Inventory.
 * Each card is a centred panel with:
 * <ul>
 *   <li><b>Left half</b>: procedural keyboard/mouse graphic + large instruction text.</li>
 *   <li><b>Right half</b>: black placeholder rectangle labelled "Video will be uploaded here".</li>
 * </ul>
 * Pressing <em>Proceed</em> (or Enter) advances; the last card saves {@code firstLaunch = false}
 * and transitions to the {@link MainMenuScreen}.
 */
public final class TutorialScreen implements Screen {

    private static final int TOTAL = 3;
    private static final float CARD_W = 980f, CARD_H = 530f;
    private static final float KEY = 58f; // key box size

    private static final String[] TITLES = { "Movement", "Combat", "Inventory" };
    private static final String[][] LINES = {
        { "Press  W A S D  to move." },
        { "Press  Left Click  to attack", "with your sword  or  fire", "the potato gun." },
        { "Press  E  to open your inventory", "and equip armor or switch weapons." },
    };

    private final Game game;
    private final ShapeRenderer sh = new ShapeRenderer();
    private final SpriteBatch    bt = new SpriteBatch();
    private final BitmapFont     fn = new BitmapFont();
    private final GlyphLayout    gl = new GlyphLayout();
    private final UiViewport     uv = new UiViewport();

    private int card = 0;

    // Proceed button rect in virtual coords (recomputed each frame)
    private float pbX, pbY;
    private static final float PB_W = 210f, PB_H = 54f;

    public TutorialScreen(Game game) { this.game = game; }

    @Override public void show() {}

    @Override
    public void render(float delta) {
        float W = uv.width(), H = uv.height();

        Gdx.gl.glClearColor(0.05f, 0.05f, 0.07f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        uv.apply();
        sh.setProjectionMatrix(uv.combined());
        bt.setProjectionMatrix(uv.combined());

        float cX = (W - CARD_W) * 0.5f;
        float cY = (H - CARD_H) * 0.5f;
        float half = CARD_W * 0.5f;

        Vector2 m = uv.unproject(Gdx.input.getX(), Gdx.input.getY());

        pbX = cX + (CARD_W - PB_W) * 0.5f;
        pbY = cY + 14f;
        boolean hov = m.x >= pbX && m.x <= pbX + PB_W && m.y >= pbY && m.y <= pbY + PB_H;

        // ---- shapes pass ----
        sh.begin(ShapeType.Filled);

        // card body
        sh.setColor(0.10f, 0.11f, 0.15f, 1f);
        sh.rect(cX, cY, CARD_W, CARD_H);
        BedrockWidgets.border(sh, cX, cY, CARD_W, CARD_H, 3f, new Color(0.25f, 0.28f, 0.35f, 1f));

        // centre divider
        sh.setColor(0.22f, 0.24f, 0.30f, 1f);
        sh.rect(cX + half - 1f, cY + 30f, 2f, CARD_H - 60f);

        // title bar
        sh.setColor(0.07f, 0.08f, 0.11f, 1f);
        sh.rect(cX, cY + CARD_H - 54f, CARD_W, 54f);

        // right: video placeholder
        float vx = cX + half + 22f, vy = cY + 76f, vw = half - 44f, vh = CARD_H - 148f;
        sh.setColor(0f, 0f, 0f, 1f);
        sh.rect(vx, vy, vw, vh);
        BedrockWidgets.border(sh, vx, vy, vw, vh, 2f, new Color(0.3f, 0.3f, 0.3f, 1f));

        // proceed button
        BedrockWidgets.button(sh, pbX, pbY, PB_W, PB_H,
            hov ? BedrockWidgets.BtnState.HOVER : BedrockWidgets.BtnState.NORMAL);

        // card graphic
        drawGraphic(card, cX + 22f, cY, half - 44f, CARD_H);

        // card progress dots
        float dotY = cY + CARD_H + 18f;
        for (int i = 0; i < TOTAL; i++) {
            sh.setColor(i == card ? new Color(1f, 0.86f, 0.16f, 1f) : new Color(0.35f, 0.35f, 0.40f, 1f));
            float dotX = W * 0.5f + (i - 1) * 22f - 5f;
            sh.rect(dotX, dotY, 10f, 10f);
        }

        sh.end();

        // ---- text pass ----
        bt.begin();

        // title
        fn.getData().setScale(2.4f);
        fn.setColor(1f, 0.86f, 0.16f, 1f);
        gl.setText(fn, TITLES[card]);
        fn.draw(bt, TITLES[card], cX + (CARD_W - gl.width) * 0.5f, cY + CARD_H - 16f);

        // instruction lines (left half, lower portion)
        fn.getData().setScale(1.35f);
        fn.setColor(0.95f, 0.95f, 1f, 1f);
        String[] lines = LINES[card];
        float lineH = 26f;
        float textY = cY + CARD_H * 0.40f + (lines.length - 1) * lineH * 0.5f;
        for (String line : lines) {
            gl.setText(fn, line);
            fn.draw(bt, line, cX + 22f + (half - 44f - gl.width) * 0.5f, textY);
            textY -= lineH;
        }

        // graphic key labels
        drawGraphicLabels(card, cX + 22f, cY, half - 44f, CARD_H);

        // video placeholder text
        fn.getData().setScale(1.05f);
        fn.setColor(0.40f, 0.40f, 0.42f, 1f);
        String vl1 = "Video will be";
        String vl2 = "uploaded here";
        gl.setText(fn, vl1);
        fn.draw(bt, vl1, vx + (vw - gl.width) * 0.5f, vy + vh * 0.5f + 14f);
        gl.setText(fn, vl2);
        fn.draw(bt, vl2, vx + (vw - gl.width) * 0.5f, vy + vh * 0.5f - 6f);

        // proceed button label
        fn.getData().setScale(1.5f);
        fn.setColor(1f, 1f, 1f, 1f);
        String lbl = card < TOTAL - 1 ? "Proceed  >" : "Play!";
        gl.setText(fn, lbl);
        fn.draw(bt, lbl, pbX + (PB_W - gl.width) * 0.5f, pbY + (PB_H + gl.height) * 0.5f);

        // card counter
        fn.getData().setScale(1.0f);
        fn.setColor(0.50f, 0.52f, 0.55f, 1f);
        String counter = (card + 1) + " / " + TOTAL;
        gl.setText(fn, counter);
        fn.draw(bt, counter, cX + CARD_W - gl.width - 14f, cY + CARD_H - 16f);

        fn.getData().setScale(1f);
        bt.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // input
        if ((Gdx.input.justTouched() && hov) || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            advance();
        }
    }

    private void advance() {
        card++;
        if (card >= TOTAL) {
            PlayerProfile p = PlayerProfile.get();
            p.firstLaunch = false;
            p.save();
            game.setScreen(new MainMenuScreen(game));
        }
    }

    // ---- procedural key graphics ----

    private void drawGraphic(int c, float lx, float ly, float lw, float lh) {
        float cx = lx + lw * 0.5f;
        float cy = ly + lh * 0.60f;
        switch (c) {
            case 0: // WASD cross
                drawKey(cx - KEY * 0.5f,        cy + KEY + 5f, KEY, KEY); // W
                drawKey(cx - KEY - 5f,           cy,            KEY, KEY); // A
                drawKey(cx - KEY * 0.5f,         cy,            KEY, KEY); // S
                drawKey(cx + 5f,                 cy,            KEY, KEY); // D
                break;
            case 1: // Mouse silhouette with left button highlighted
                // body
                sh.setColor(0.18f, 0.20f, 0.24f, 1f);
                sh.rect(cx - 26f, cy - 50f, 52f, 86f);
                // left button highlighted gold
                sh.setColor(0.90f, 0.74f, 0.14f, 1f);
                sh.rect(cx - 26f, cy + 10f, 24f, 26f);
                // right button
                sh.setColor(0.25f, 0.27f, 0.32f, 1f);
                sh.rect(cx + 2f,  cy + 10f, 24f, 26f);
                // divider line
                sh.setColor(0.10f, 0.10f, 0.12f, 1f);
                sh.rect(cx - 1f,  cy + 8f,  2f,  30f);
                // scroll wheel
                sh.setColor(0.45f, 0.47f, 0.52f, 1f);
                sh.rect(cx - 6f,  cy + 18f, 12f, 16f);
                // border
                BedrockWidgets.border(sh, cx - 26f, cy - 50f, 52f, 86f, 2f, BedrockWidgets.BTN_EDGE);
                break;
            case 2: // single E key
                drawKey(cx - KEY * 0.5f, cy, KEY, KEY);
                break;
        }
    }

    private void drawKey(float x, float y, float w, float h) {
        sh.setColor(0.20f, 0.22f, 0.27f, 1f);
        sh.rect(x, y, w, h);
        // top bevel (lighter)
        sh.setColor(0.40f, 0.43f, 0.50f, 1f);
        sh.rect(x, y + h - 4f, w, 3f);
        sh.rect(x, y, 3f, h);
        // bottom-right shadow
        sh.setColor(0.08f, 0.08f, 0.10f, 1f);
        sh.rect(x, y, w, 3f);
        sh.rect(x + w - 3f, y, 3f, h);
    }

    private void drawGraphicLabels(int c, float lx, float ly, float lw, float lh) {
        float cx = lx + lw * 0.5f;
        float cy = ly + lh * 0.60f;
        fn.getData().setScale(1.0f);
        fn.setColor(1f, 1f, 1f, 1f);
        switch (c) {
            case 0:
                keyLabel("W", cx - KEY * 0.5f, cy + KEY + 5f, KEY, KEY);
                keyLabel("A", cx - KEY - 5f,   cy,             KEY, KEY);
                keyLabel("S", cx - KEY * 0.5f, cy,             KEY, KEY);
                keyLabel("D", cx + 5f,          cy,             KEY, KEY);
                break;
            case 1:
                fn.getData().setScale(0.85f);
                fn.setColor(0.15f, 0.15f, 0.15f, 1f);
                gl.setText(fn, "LMB");
                fn.draw(bt, "LMB", cx - 26f + (24f - gl.width) * 0.5f, cy + 10f + (26f + gl.height) * 0.5f);
                break;
            case 2:
                keyLabel("E", cx - KEY * 0.5f, cy, KEY, KEY);
                break;
        }
    }

    private void keyLabel(String t, float kx, float ky, float kw, float kh) {
        gl.setText(fn, t);
        fn.draw(bt, t, kx + (kw - gl.width) * 0.5f, ky + (kh + gl.height) * 0.5f);
    }

    @Override public void resize(int w, int h) { uv.resize(w, h); }
    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void hide()   {}

    @Override
    public void dispose() {
        sh.dispose();
        bt.dispose();
        fn.dispose();
    }
}
