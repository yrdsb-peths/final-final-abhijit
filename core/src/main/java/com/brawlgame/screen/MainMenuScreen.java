package com.brawlgame.screen;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Vector2;
import com.brawlgame.ui.BedrockWidgets;
import com.brawlgame.ui.CharacterShowcase;
import com.brawlgame.ui.UiViewport;

/**
 * Minecraft Dungeons / Bedrock-style main menu.
 *
 * <p>Layout:
 * <ul>
 *   <li>Dark atmospheric background with a subtle stone-tile gradient vignette.</li>
 *   <li>"MINECRAFT BRAWL" logo — gold, top-center, oversized.</li>
 *   <li>Live 3D player model in a centre-right portrait, slowly rotating.</li>
 *   <li>Bottom-left: large green "START GAME" + secondary "CHANGE MAP" and "MAP MAKER" sub-buttons.</li>
 *   <li>Bottom-right: small Accessibility / Settings / Exit prompts.</li>
 *   <li>[H] cycles through skin PNGs found in the local {@code skins/} folder.</li>
 * </ul>
 */
public final class MainMenuScreen implements Screen {

    private static final String TITLE    = "MINECRAFT BRAWL";
    private static final String SUBTITLE = "Brawl-Stars style  ·  Minecraft aesthetic";

    private static final float BTN_W  = 300f, BTN_H  = 62f;
    private static final float SUB_W  = 256f, SUB_H  = 44f;
    private static final float BTN_GAP = 10f;

    // Bedrock palette constants used inline
    private static final Color BG_TOP    = new Color(0.07f, 0.07f, 0.10f, 1f);
    private static final Color GOLD      = new Color(1.00f, 0.86f, 0.16f, 1f);
    private static final Color SUBTITLE_COL = new Color(0.60f, 0.60f, 0.65f, 1f);
    private static final Color HINT_COL  = new Color(0.45f, 0.80f, 0.45f, 0.92f);
    private static final Color PROMPT_COL = new Color(0.55f, 0.55f, 0.60f, 1f);
    private static final Color GREEN_BTN = new Color(0.33f, 0.60f, 0.14f, 1f);
    private static final Color GREEN_HOV = new Color(0.42f, 0.74f, 0.18f, 1f);

    private final Game game;

    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch   batch  = new SpriteBatch();
    private final BitmapFont    font   = new BitmapFont();
    private final GlyphLayout   glyph  = new GlyphLayout();
    private final UiViewport    uiv    = new UiViewport();

    // Button hit-rects (set each frame from layout)
    private float startX, startY, startW, startH;
    private float changeX, changeY, changeW, changeH;
    private float makerX, makerY, makerW, makerH;

    // 3D character preview
    private CharacterShowcase showcase;
    private int charIdx = -1;
    private float spinAngle = 0f;
    private static final float SPIN_SPEED = 28f; // degrees/sec

    // Skin management
    private final List<Texture> skins = new ArrayList<>();
    private int currentSkin = 0;

    public MainMenuScreen(Game game) {
        this.game = game;
    }

    @Override
    public void show() {
        loadSkins();
        buildShowcase();
    }

    private void loadSkins() {
        for (Texture t : skins) t.dispose();
        skins.clear();
        // Default built-in skin always first.
        skins.add(new Texture(Gdx.files.internal("textures/player.png")));
        // User-provided skins from local skins/ folder.
        FileHandle skinsDir = Gdx.files.local("skins");
        if (skinsDir.exists() && skinsDir.isDirectory()) {
            for (FileHandle f : skinsDir.list(".png")) {
                try { skins.add(new Texture(f)); } catch (Exception ignored) {}
            }
        }
    }

    private void buildShowcase() {
        if (showcase != null) showcase.dispose();
        showcase = new CharacterShowcase();
        charIdx = showcase.add(skins.get(currentSkin), null);
    }

    @Override
    public void render(float delta) {
        float w = uiv.width(), h = uiv.height();

        // ---- Input ----
        if (Gdx.input.isKeyJustPressed(Input.Keys.H)) cycleSkin();
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) Gdx.app.exit();

        spinAngle = (spinAngle + SPIN_SPEED * delta) % 360f;

        // ---- Clear ----
        Gdx.gl.glClearColor(BG_TOP.r, BG_TOP.g, BG_TOP.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        // ---- 3D character portrait (right half of screen) ----
        float portX = w * 0.52f, portY = h * 0.20f, portW = w * 0.40f, portH = h * 0.72f;
        if (showcase != null && charIdx >= 0) {
            showcase.render(uiv, charIdx, portX, portY, portW, portH, spinAngle, 0f, Color.WHITE);
        }

        // ---- 2D UI ----
        uiv.apply();
        shapes.setProjectionMatrix(uiv.combined());
        batch.setProjectionMatrix(uiv.combined());
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        Vector2 m = uiv.unproject(Gdx.input.getX(), Gdx.input.getY());

        // ---- Layout ----
        // Left-panel dark overlay
        float panelW = w * 0.55f;
        shapes.begin(ShapeType.Filled);
        shapes.setColor(0.03f, 0.03f, 0.05f, 0.88f);
        shapes.rect(0, 0, panelW, h);

        // Button layout — bottom-left
        float bx = 56f, by = 90f;
        startX = bx; startY = by + SUB_H + BTN_GAP * 2f; startW = BTN_W; startH = BTN_H;
        changeX = bx + 20f; changeY = by + BTN_GAP; changeW = SUB_W; changeH = SUB_H;
        makerX  = bx + 20f; makerY  = by + BTN_GAP + SUB_H + BTN_GAP; makerW = SUB_W; makerH = SUB_H;

        // Recalculate — START sits above the two subs
        startY = by + (SUB_H + BTN_GAP) * 2f + BTN_GAP;

        boolean startHov  = contains(startX, startY, startW, startH, m);
        boolean changeHov = contains(changeX, changeY, changeW, changeH, m);
        boolean makerHov  = contains(makerX,  makerY,  makerW,  makerH,  m);

        // START GAME — big green primary button
        drawGreenButton(startX, startY, startW, startH, startHov);
        // Sub-buttons (Bedrock dark style)
        BedrockWidgets.button(shapes, changeX, changeY, changeW, changeH,
            changeHov ? BedrockWidgets.BtnState.HOVER : BedrockWidgets.BtnState.NORMAL);
        BedrockWidgets.button(shapes, makerX, makerY, makerW, makerH,
            makerHov ? BedrockWidgets.BtnState.HOVER : BedrockWidgets.BtnState.NORMAL);

        shapes.end();

        // ---- Text ----
        batch.begin();

        // Logo
        font.getData().setScale(3.8f);
        font.setColor(GOLD);
        glyph.setText(font, TITLE);
        float logoX = (panelW - glyph.width) * 0.5f;
        // Drop-shadow
        font.setColor(0f, 0f, 0f, 0.7f);
        font.draw(batch, TITLE, logoX + 3f, h - 72f);
        font.setColor(GOLD);
        font.draw(batch, TITLE, logoX, h - 70f);

        // Subtitle
        font.getData().setScale(1.25f);
        font.setColor(SUBTITLE_COL);
        glyph.setText(font, SUBTITLE);
        font.draw(batch, SUBTITLE, (panelW - glyph.width) * 0.5f, h - 126f);

        // Button labels
        font.getData().setScale(1.5f);
        font.setColor(Color.WHITE);
        drawCenteredText("START GAME", startX, startY, startW, startH);
        font.getData().setScale(1.1f);
        font.setColor(BedrockWidgets.TEXT_LIGHT);
        drawCenteredText("CHANGE MAP", changeX, changeY, changeW, changeH);
        drawCenteredText("MAP MAKER",  makerX,  makerY,  makerW,  makerH);

        // Bottom-right prompts
        font.getData().setScale(1.1f);
        font.setColor(PROMPT_COL);
        float rx = w - 230f, ry = 110f;
        font.draw(batch, "Exit           [ESC]",  rx, ry + 70f);
        font.draw(batch, "Settings       [F2]",   rx, ry + 44f);
        font.draw(batch, "Accessibility  [F1]",   rx, ry + 18f);

        // Skin hint below portrait
        font.getData().setScale(1.05f);
        if (skins.size() > 1) {
            font.setColor(HINT_COL);
            String hint = "[H]  Cycle Skin   " + (currentSkin + 1) + " / " + skins.size();
            glyph.setText(font, hint);
            font.draw(batch, hint, portX + (portW - glyph.width) * 0.5f, portY - 12f);
        } else {
            font.setColor(PROMPT_COL);
            font.draw(batch, "Add PNGs to  skins/  to unlock skins", portX - 10f, portY - 12f);
        }

        font.getData().setScale(1f);
        batch.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // ---- Click handling ----
        if (Gdx.input.justTouched()) {
            if (startHov || changeHov)  game.setScreen(new MapListScreen(game));
            else if (makerHov)          game.setScreen(new MapMakerMenuScreen(game));
        }
    }

    private void cycleSkin() {
        if (skins.size() <= 1) return;
        currentSkin = (currentSkin + 1) % skins.size();
        buildShowcase();
    }

    // ---- Drawing helpers ----

    private void drawGreenButton(float x, float y, float w, float h, boolean hovered) {
        Color fill = hovered ? GREEN_HOV : GREEN_BTN;
        shapes.setColor(fill);
        shapes.rect(x, y, w, h);
        // top bevel highlight
        shapes.setColor(fill.r * 1.35f, fill.g * 1.35f, fill.b * 1.35f, 1f);
        shapes.rect(x + 3f, y + h - 4f, w - 6f, 2f);
        // thick dark border
        BedrockWidgets.border(shapes, x, y, w, h, 3f, BedrockWidgets.BTN_EDGE);
    }

    private void drawCenteredText(String text, float bx, float by, float bw, float bh) {
        glyph.setText(font, text);
        font.draw(batch, text, bx + (bw - glyph.width) * 0.5f, by + (bh + glyph.height) * 0.5f);
    }

    private static boolean contains(float bx, float by, float bw, float bh, Vector2 p) {
        return p.x >= bx && p.x <= bx + bw && p.y >= by && p.y <= by + bh;
    }

    // ---- Screen lifecycle ----

    @Override
    public void resize(int width, int height) {
        uiv.resize(width, height);
        shapes.setProjectionMatrix(uiv.combined());
        batch.setProjectionMatrix(uiv.combined());
    }

    @Override public void hide()   {}
    @Override public void pause()  {}
    @Override public void resume() {}

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
        if (showcase != null) showcase.dispose();
        for (Texture t : skins) t.dispose();
        skins.clear();
    }
}
