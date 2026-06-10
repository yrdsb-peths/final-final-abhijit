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
import com.brawlgame.audio.AudioManager;
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
 *   <li><b>Right half</b>: styled tips panel listing the key controls for this card.</li>
 * </ul>
 * Pressing <em>Proceed</em> (or Enter / Space) advances the card; the last card saves
 * {@code firstLaunch = false} and transitions to the {@link MainMenuScreen}.
 */
public final class TutorialScreen implements Screen {

    private static final int   TOTAL  = 3;
    private static final float CARD_W = 980f, CARD_H = 530f;
    private static final float KEY    = 58f;  // key box size (pixels in virtual coords)

    private static final String[] TITLES = { "Movement", "Combat", "Inventory" };

    /**
     * Instruction lines shown in the right-half tips panel.
     * Each inner array is one card; entries are displayed on separate lines.
     */
    private static final String[][] LINES = {
        { "W A S D  —  Move", "SPACE  —  Sprint / Jump", "SHIFT  —  Crouch" },
        { "Left Click  —  Swing sword", "Q  —  Switch weapon", "Left Click (gun)  —  Fire" },
        { "E  —  Open inventory", "Click armor  —  Equip / swap", "Drop item to floor anytime" },
    };

    /** Sub-heading shown at the top of the right tips panel. */
    private static final String[] PANEL_HEADS = { "Controls", "Combat Tips", "Inventory Tips" };

    // ---- accent colours per card ----
    private static final Color[] ACCENT = {
        new Color(0.22f, 0.65f, 1.00f, 1f),   // blue  – movement
        new Color(0.95f, 0.35f, 0.30f, 1f),   // red   – combat
        new Color(0.30f, 0.85f, 0.45f, 1f),   // green – inventory
    };

    private final Game       game;
    private final ShapeRenderer sh = new ShapeRenderer();
    private final SpriteBatch   bt = new SpriteBatch();
    private final BitmapFont    fn = new BitmapFont();
    private final GlyphLayout   gl = new GlyphLayout();
    private final UiViewport    uv = new UiViewport();

    private int card = 0;

    // Proceed button rect in virtual coords (recomputed each frame)
    private float pbX, pbY;
    private static final float PB_W = 210f, PB_H = 54f;

    public TutorialScreen(Game game) { this.game = game; }

    // ---- lifecycle ----

    @Override public void show()    {}
    @Override public void hide()    {}
    @Override public void pause()   {}
    @Override public void resume()  {}
    @Override public void resize(int w, int h) { uv.resize(w, h); }

    @Override
    public void dispose() {
        sh.dispose();
        bt.dispose();
        fn.dispose();
    }

    // ---- render ----

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

        float cX   = (W - CARD_W) * 0.5f;
        float cY   = (H - CARD_H) * 0.5f;
        float half = CARD_W * 0.5f;

        // Virtual-coords bounds of the two halves
        float lx = cX + 22f,          ly = cY,       lw = half - 44f, lh = CARD_H;
        float rpX = cX + half + 22f,  rpY = cY + 76f, rpW = half - 44f, rpH = CARD_H - 148f;

        Vector2 m = uv.unproject(Gdx.input.getX(), Gdx.input.getY());

        pbX = cX + (CARD_W - PB_W) * 0.5f;
        pbY = cY + 14f;
        boolean hov = m.x >= pbX && m.x <= pbX + PB_W && m.y >= pbY && m.y <= pbY + PB_H;

        Color accent = ACCENT[card];

        // ---- shapes pass ----
        sh.begin(ShapeType.Filled);

        // Card background
        sh.setColor(0.10f, 0.11f, 0.15f, 1f);
        sh.rect(cX, cY, CARD_W, CARD_H);
        BedrockWidgets.border(sh, cX, cY, CARD_W, CARD_H, 3f, new Color(0.25f, 0.28f, 0.35f, 1f));

        // Title bar
        sh.setColor(0.07f, 0.08f, 0.11f, 1f);
        sh.rect(cX, cY + CARD_H - 54f, CARD_W, 54f);

        // Accent stripe along the top of the title bar
        sh.setColor(accent.r, accent.g, accent.b, 0.85f);
        sh.rect(cX, cY + CARD_H - 5f, CARD_W, 5f);

        // Centre divider
        sh.setColor(0.22f, 0.24f, 0.30f, 1f);
        sh.rect(cX + half - 1f, cY + 30f, 2f, CARD_H - 60f);

        // ---- Right tips panel ----
        sh.setColor(0.07f, 0.08f, 0.11f, 1f);
        sh.rect(rpX, rpY, rpW, rpH);

        // Top accent bar on tips panel
        sh.setColor(accent.r, accent.g, accent.b, 1f);
        sh.rect(rpX, rpY + rpH - 5f, rpW, 5f);

        BedrockWidgets.border(sh, rpX, rpY, rpW, rpH, 2f, new Color(0.22f, 0.24f, 0.30f, 1f));

        // Subtle row highlights behind each instruction line
        float lineH   = 44f;
        float totalH  = LINES[card].length * lineH;
        float rowStartY = rpY + (rpH - totalH) * 0.5f - 2f + totalH - lineH;
        for (int i = 0; i < LINES[card].length; i++) {
            if (i % 2 == 0) {
                sh.setColor(1f, 1f, 1f, 0.03f);
                sh.rect(rpX + 6f, rowStartY - i * lineH - 6f, rpW - 12f, lineH);
            }
        }

        // Proceed button
        BedrockWidgets.button(sh, pbX, pbY, PB_W, PB_H,
            hov ? BedrockWidgets.BtnState.HOVER : BedrockWidgets.BtnState.NORMAL);

        // Left-half key graphic
        drawGraphic(card, lx, ly, lw, lh);

        // Card progress dots
        float dotY = cY + CARD_H + 18f;
        for (int i = 0; i < TOTAL; i++) {
            sh.setColor(i == card ? new Color(1f, 0.86f, 0.16f, 1f) : new Color(0.35f, 0.35f, 0.40f, 1f));
            float dotX = W * 0.5f + (i - 1) * 22f - 5f;
            sh.rect(dotX, dotY, 10f, 10f);
        }

        sh.end();

        // ---- text pass ----
        bt.begin();

        // Card title
        fn.getData().setScale(2.4f);
        fn.setColor(1f, 0.86f, 0.16f, 1f);
        gl.setText(fn, TITLES[card]);
        fn.draw(bt, TITLES[card], cX + (CARD_W - gl.width) * 0.5f, cY + CARD_H - 16f);

        // Card counter (top-right)
        fn.getData().setScale(1.0f);
        fn.setColor(0.50f, 0.52f, 0.55f, 1f);
        String counter = (card + 1) + " / " + TOTAL;
        gl.setText(fn, counter);
        fn.draw(bt, counter, cX + CARD_W - gl.width - 14f, cY + CARD_H - 16f);

        // ---- Right panel: sub-heading ----
        fn.getData().setScale(1.2f);
        fn.setColor(accent.r, accent.g, accent.b, 1f);
        String head = PANEL_HEADS[card];
        gl.setText(fn, head);
        fn.draw(bt, head, rpX + (rpW - gl.width) * 0.5f, rpY + rpH - 18f);

        // ---- Right panel: instruction lines ----
        fn.getData().setScale(1.5f);
        String[] lines = LINES[card];
        float rowY = rpY + (rpH - totalH) * 0.5f + totalH - lineH * 0.15f;
        for (int i = 0; i < lines.length; i++) {
            // Split at the em-dash separator so we can colour the key name differently
            String line = lines[i];
            int dash = line.indexOf("\u2014");
            if (dash >= 0) {
                String keyPart  = line.substring(0, dash);  // key name(s)
                String descPart = "\u2014" + line.substring(dash + 1); // — description
                fn.setColor(1f, 0.90f, 0.40f, 1f); // gold for key names
                gl.setText(fn, keyPart);
                float keyW = gl.width;
                fn.draw(bt, keyPart, rpX + (rpW - gl.width) * 0.5f - keyW * 0.1f, rowY - i * lineH);
                fn.setColor(0.85f, 0.87f, 0.92f, 1f); // light for description
                gl.setText(fn, descPart);
                fn.draw(bt, descPart, rpX + (rpW * 0.5f) + keyW * 0.4f, rowY - i * lineH);
            } else {
                fn.setColor(0.85f, 0.87f, 0.92f, 1f);
                gl.setText(fn, line);
                fn.draw(bt, line, rpX + (rpW - gl.width) * 0.5f, rowY - i * lineH);
            }
        }

        // ---- Left-half instruction text (below graphic) ----
        fn.getData().setScale(1.25f);
        fn.setColor(0.65f, 0.68f, 0.75f, 1f);
        // Display the card title as a small label below the graphic
        String hint = card == 0 ? "Move your character around the arena"
                    : card == 1 ? "Attack enemies to deal damage"
                    :             "Manage your gear and loadout";
        gl.setText(fn, hint);
        fn.draw(bt, hint, lx + (lw - gl.width) * 0.5f, cY + 62f);

        // Key graphic labels (drawn inside bt.begin)
        drawGraphicLabels(card, lx, ly, lw, lh);

        // Proceed button label
        fn.getData().setScale(1.5f);
        fn.setColor(1f, 1f, 1f, 1f);
        String lbl = card < TOTAL - 1 ? "Proceed  >" : "Play!";
        gl.setText(fn, lbl);
        fn.draw(bt, lbl, pbX + (PB_W - gl.width) * 0.5f, pbY + (PB_H + gl.height) * 0.5f);

        fn.getData().setScale(1f);
        bt.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // ---- input ----
        if ((Gdx.input.justTouched() && hov)
                || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            AudioManager.get().click();
            advance();
        }
    }

    /**
     * Advances to the next tutorial card, or transitions to the main menu on the last card.
     */
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
        float cy = ly + lh * 0.62f;
        switch (c) {
            case 0: // WASD cross + Space + Shift keys
                float kw = KEY, kh = KEY;
                float gap = 6f;
                drawKey(cx - kw * 0.5f,           cy + kh + gap, kw, kh); // W
                drawKey(cx - kw - gap - kw * 0.5f, cy,            kw, kh); // A
                drawKey(cx - kw * 0.5f,            cy,            kw, kh); // S
                drawKey(cx + kw * 0.5f + gap,      cy,            kw, kh); // D
                drawKey(cx - 150f, cy - kh - 15f, 90f,  kh * 0.85f);      // Shift
                drawKey(cx - 40f,  cy - kh - 15f, 190f, kh * 0.85f);      // Space
                break;
            case 1: // Mouse silhouette with left button highlighted
                sh.setColor(0.18f, 0.20f, 0.24f, 1f);
                sh.rect(cx - 26f, cy - 50f, 52f, 86f);
                sh.setColor(0.48f, 0.93f, 0.16f, 1f);
                sh.rect(cx - 26f, cy + 10f, 24f, 26f);
                sh.setColor(0.25f, 0.27f, 0.32f, 1f);
                sh.rect(cx + 2f,  cy + 10f, 24f, 26f);
                sh.setColor(0.10f, 0.10f, 0.12f, 1f);
                sh.rect(cx - 1f,  cy + 8f, 2f, 30f);
                sh.setColor(0.45f, 0.47f, 0.52f, 1f);
                sh.rect(cx - 6f,  cy + 18f, 12f, 16f);
                BedrockWidgets.border(sh, cx - 26f, cy - 50f, 52f, 86f, 2f, BedrockWidgets.BTN_EDGE);
                break;
            case 2: // Single E key
                drawKey(cx - KEY * 0.5f, cy, KEY, KEY);
                break;
        }
    }

    /**
     * Draws a single keyboard key box with a top-left bevel and bottom-right shadow.
     *
     * @param x left edge in virtual coords
     * @param y bottom edge in virtual coords
     * @param w width of the key
     * @param h height of the key
     */
    private void drawKey(float x, float y, float w, float h) {
        sh.setColor(0.20f, 0.22f, 0.27f, 1f);
        sh.rect(x, y, w, h);
        sh.setColor(0.40f, 0.43f, 0.50f, 1f);
        sh.rect(x, y + h - 4f, w, 3f);
        sh.rect(x, y, 3f, h);
        sh.setColor(0.08f, 0.08f, 0.10f, 1f);
        sh.rect(x, y, w, 3f);
        sh.rect(x + w - 3f, y, 3f, h);
    }

    private void drawGraphicLabels(int c, float lx, float ly, float lw, float lh) {
        float cx = lx + lw * 0.5f;
        float cy = ly + lh * 0.62f;
        fn.getData().setScale(1.0f);
        fn.setColor(1f, 1f, 1f, 1f);
        switch (c) {
            case 0:
                float kw = KEY, kh = KEY, gap = 6f;
                keyLabel("W", cx - kw * 0.5f,           cy + kh + gap, kw, kh);
                keyLabel("A", cx - kw - gap - kw * 0.5f, cy,            kw, kh);
                keyLabel("S", cx - kw * 0.5f,            cy,            kw, kh);
                keyLabel("D", cx + kw * 0.5f + gap,      cy,            kw, kh);
                fn.getData().setScale(0.85f);
                keyLabel("SHIFT", cx - 150f, cy - kh - 15f, 90f,  kh * 0.85f);
                fn.getData().setScale(1.0f);
                keyLabel("SPACE", cx - 40f,  cy - kh - 15f, 190f, kh * 0.85f);
                break;
            case 1:
                fn.getData().setScale(0.85f);
                fn.setColor(1f, 1f, 1f, 1f);
                gl.setText(fn, "LMB");
                fn.draw(bt, "LMB", cx - 26f + (24f - gl.width) * 0.5f,
                                   cy + 10f  + (26f + gl.height) * 0.5f);
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
}
