package com.brawlgame.screen;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
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
import com.brawlgame.audio.AudioManager;
import com.brawlgame.game.PlayerProfile;
import com.brawlgame.map.GameMap;
import com.brawlgame.map.MapSerializer;
import com.brawlgame.ui.BedrockWidgets;
import com.brawlgame.ui.CharacterShowcase;
import com.brawlgame.ui.UiViewport;

/**
 * Main menu — Minecraft Dungeons / Bedrock aesthetic.
 *
 * <p>Layout:
 * <ul>
 *   <li>Atmospheric dark background with a subtle grid vignette.</li>
 *   <li>"MINECRAFT" (gold) + "BRAWL" (green) stacked logo, top-centre.</li>
 *   <li>Stats panel, top-right: Matches Played / Win Rate.</li>
 *   <li>Four primary buttons centred: Single Player · Multiplayer · Skins · Options.</li>
 *   <li>Map Maker button (below Options) — only visible when Developer Mode is ON.</li>
 *   <li>Test Map button at the very bottom.</li>
 *   <li>"Dev Mode Active" watermark at bottom-centre when dev mode is on.</li>
 *   <li>Live 3D character portrait, centre-right.</li>
 * </ul>
 */
public final class MainMenuScreen implements Screen {

    private static final String DEFAULT_MAP = "maps/sand_small_1.map";

    private static final float BTN_W = 340f, BTN_H = 58f, BTN_GAP = 12f;

    private static final Color GOLD  = new Color(1.00f, 0.86f, 0.16f, 1f);
    private static final Color GREEN = new Color(0.38f, 0.73f, 0.16f, 1f);
    private static final Color MUTED = new Color(0.55f, 0.55f, 0.60f, 1f);
    private static final Color STAT_BG = new Color(0.08f, 0.09f, 0.12f, 0.88f);

    private final Game game;
    private final UiViewport    uv = new UiViewport();
    private final ShapeRenderer sh = new ShapeRenderer();
    private final SpriteBatch   bt = new SpriteBatch();
    private final BitmapFont    fn = new BitmapFont();
    private final GlyphLayout   gl = new GlyphLayout();

    // 3D preview
    private CharacterShowcase showcase;
    private int showcaseIdx = -1;
    private Texture skinTex;

    // Button Y positions (computed once in render from current layout)
    private float spY, mpY, skY, opY, mmY, tmY;
    private float easyX, normalX, hardX, diffY;
    private boolean showingDifficulty;
    private static final float BTN_CX = 380f; // centre X of the button column
    private static final float NAME_H = 44f;  // height of the player-name field
    private boolean editingName;
    private final InputAdapter nameInput = new InputAdapter() {
        @Override public boolean keyDown(int keycode) {
            if (!editingName) return false;
            if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER || keycode == Input.Keys.ESCAPE) {
                editingName = false;
                if (PlayerProfile.get().playerName.trim().isEmpty()) PlayerProfile.get().playerName = "Player";
                return true;
            }
            if (keycode == Input.Keys.BACKSPACE || keycode == Input.Keys.FORWARD_DEL) {
                String name = PlayerProfile.get().playerName;
                if (!name.isEmpty()) PlayerProfile.get().playerName = name.substring(0, name.length() - 1);
                return true;
            }
            return false;
        }

        @Override public boolean keyTyped(char character) {
            if (!editingName) return false;
            if (character < 32 || character == 127) return true;
            String name = PlayerProfile.get().playerName;
            if (name.length() < 16) PlayerProfile.get().playerName = name + character;
            return true;
        }
    };

    public MainMenuScreen(Game game) { this.game = game; }

    @Override
    public void show() {
        AudioManager.get().playMenuMusic();
        Gdx.input.setInputProcessor(nameInput);
        loadSkin();
        showcase = new CharacterShowcase();
        showcaseIdx = showcase.add(skinTex, null);

    }

    private void loadSkin() {
        if (skinTex != null) skinTex.dispose();
        String path = PlayerProfile.get().selectedSkin;
        if (!path.isEmpty()) {
            FileHandle f = Gdx.files.local(path);
            if (f.exists()) { skinTex = new Texture(f); return; }
        }
        skinTex = new Texture(Gdx.files.internal("textures/player.png"));
    }

    @Override
    public void render(float delta) {
        float W = uv.width(), H = uv.height();

        Gdx.gl.glClearColor(0.06f, 0.06f, 0.09f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        // 3D preview — right half, rendered before uiv.apply to use full viewport
        float prevX = W * 0.54f, prevY = H * 0.08f, prevW = W * 0.42f, prevH = H * 0.84f;
        if (showcase != null && showcaseIdx >= 0) {
            showcase.render(uv, showcaseIdx, prevX, prevY, prevW, prevH,
                (System.currentTimeMillis() % 7000) / 7000f * 360f, 0f, Color.WHITE);
        }

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        uv.apply();
        sh.setProjectionMatrix(uv.combined());
        bt.setProjectionMatrix(uv.combined());

        Vector2 m = uv.unproject(Gdx.input.getX(), Gdx.input.getY());

        // ---- layout ----
        boolean devMode = PlayerProfile.get().devMode;
        // Count visible buttons to centre the stack
        int btnCount = 4 + (devMode ? 1 : 0);
        float stackH = btnCount * BTN_H + (btnCount - 1) * BTN_GAP;
        if (showingDifficulty) {
            stackH += 40f + BTN_GAP;
        }
        float stackTop = H * 0.5f + stackH * 0.5f;

        float currentY = stackTop;
        spY = currentY;
        currentY -= (BTN_H + BTN_GAP);

        float bx = BTN_CX - BTN_W * 0.5f;
        float diffW = (BTN_W - 20f) / 3f;
        easyX = bx;
        normalX = bx + diffW + 10f;
        hardX = bx + 2 * (diffW + 10f);

        if (showingDifficulty) {
            diffY = currentY + 18f; // Align sub-menu inside the vertical gap nicely
            currentY -= (40f + BTN_GAP);
        } else {
            diffY = -9999f;
        }

        mpY = currentY;
        currentY -= (BTN_H + BTN_GAP);
        skY = currentY;
        currentY -= (BTN_H + BTN_GAP);
        opY = currentY;
        currentY -= (BTN_H + BTN_GAP);
        mmY = devMode ? currentY : -9999f;

        // Test Map at bottom regardless
        tmY = 38f;
        // Name input field sits between test map and the button stack
        float nameY = tmY + 46f + 14f; // 14 px gap above the test map button

        boolean spHov   = hover(m, bx, spY,  BTN_W, BTN_H);
        boolean mpHov   = hover(m, bx, mpY,  BTN_W, BTN_H);
        boolean skHov   = hover(m, bx, skY,  BTN_W, BTN_H);
        boolean opHov   = hover(m, bx, opY,  BTN_W, BTN_H);
        boolean mmHov   = devMode && hover(m, bx, mmY, BTN_W, BTN_H);
        boolean tmHov   = hover(m, bx, tmY,  260f,  46f);
        boolean nameHov = hover(m, bx, nameY, BTN_W, NAME_H);

        boolean eHov = showingDifficulty && hover(m, easyX, diffY, diffW, 40f);
        boolean nHov = showingDifficulty && hover(m, normalX, diffY, diffW, 40f);
        boolean hHov = showingDifficulty && hover(m, hardX, diffY, diffW, 40f);

        // ---- shapes ----
        sh.begin(ShapeType.Filled);

        // Left dark panel
        sh.setColor(0.04f, 0.04f, 0.06f, 0.92f);
        sh.rect(0, 0, W * 0.52f, H);

        // Stats panel (top-right)
        float stPX = W * 0.54f, stPY = H - 130f, stPW = W * 0.42f, stPH = 110f;
        sh.setColor(STAT_BG);
        sh.rect(stPX, stPY, stPW, stPH);
        BedrockWidgets.border(sh, stPX, stPY, stPW, stPH, 2f, BedrockWidgets.BTN_EDGE);

        // Primary buttons
        shBtn(sh, bx, spY, BTN_W, BTN_H, spHov, true);  // Single Player — green primary
        if (showingDifficulty) {
            shBtn(sh, easyX, diffY, diffW, 40f, eHov, false);
            shBtn(sh, normalX, diffY, diffW, 40f, nHov, false);
            shBtn(sh, hardX, diffY, diffW, 40f, hHov, false);
        }
        shBtn(sh, bx, mpY, BTN_W, BTN_H, mpHov, false);
        shBtn(sh, bx, skY, BTN_W, BTN_H, skHov, false);
        shBtn(sh, bx, opY, BTN_W, BTN_H, opHov, false);
        if (devMode) shBtn(sh, bx, mmY, BTN_W, BTN_H, mmHov, false);
        shBtn(sh, bx, tmY, 260f, 46f, tmHov, false);
        // Player name input field
        sh.setColor(nameHov ? new Color(0.16f, 0.18f, 0.24f, 0.95f) : new Color(0.12f, 0.13f, 0.18f, 0.90f));
        sh.rect(bx, nameY, BTN_W, NAME_H);
        sh.setColor(nameHov ? new Color(0.55f, 0.55f, 0.60f, 1f) : new Color(0.30f, 0.30f, 0.35f, 1f));
        sh.rect(bx, nameY, BTN_W, 2f);
        sh.rect(bx, nameY + NAME_H - 2f, BTN_W, 2f);
        sh.rect(bx, nameY, 2f, NAME_H);
        sh.rect(bx + BTN_W - 2f, nameY, 2f, NAME_H);

        sh.end();

        // ---- text ----
        bt.begin();

        // MINECRAFT / BRAWL logo
        fn.getData().setScale(4.2f);
        fn.setColor(0f, 0f, 0f, 0.6f);
        fn.draw(bt, "MINECRAFT", BTN_CX - 192f + 3f, H - 42f);
        fn.setColor(GOLD);
        fn.draw(bt, "MINECRAFT", BTN_CX - 192f, H - 42f);

        fn.getData().setScale(3.4f);
        fn.setColor(0f, 0f, 0f, 0.6f);
        fn.draw(bt, "BRAWL", BTN_CX - 118f + 3f, H - 108f);
        fn.setColor(GREEN);
        fn.draw(bt, "BRAWL", BTN_CX - 118f, H - 108f);

        // Stats panel
        PlayerProfile p = PlayerProfile.get();
        fn.getData().setScale(1.15f);
        fn.setColor(MUTED);
        fn.draw(bt, "Total Matches Played:  " + p.totalMatches, stPX + 14f, stPY + stPH - 18f);
        fn.draw(bt, "Wins:  " + p.wins + "   Losses:  " + p.losses, stPX + 14f, stPY + stPH - 42f);
        fn.draw(bt, "Win Rate:  " + p.winRateString(), stPX + 14f, stPY + stPH - 66f);

        // Button labels
        fn.getData().setScale(1.45f);
        fn.setColor(Color.WHITE);
        btnLabel("Single Player", bx, spY, BTN_W, BTN_H);
        if (showingDifficulty) {
            fn.getData().setScale(1.0f);
            btnLabel("Easy", easyX, diffY, diffW, 40f);
            btnLabel("Medium", normalX, diffY, diffW, 40f);
            btnLabel("Hard", hardX, diffY, diffW, 40f);
        }
        fn.getData().setScale(1.45f);
        btnLabel("Multiplayer",   bx, mpY, BTN_W, BTN_H);
        btnLabel("Skins",         bx, skY, BTN_W, BTN_H);
        btnLabel("Options",       bx, opY, BTN_W, BTN_H);
        if (devMode) btnLabel("Map Maker", bx, mmY, BTN_W, BTN_H);
        fn.getData().setScale(1.15f);
        btnLabel("Test Map", bx, tmY, 260f, 46f);

        // Name input field — label on left, player name on right
        fn.getData().setScale(0.95f);
        fn.setColor(MUTED);
        fn.draw(bt, "Name:", bx + 10f, nameY + NAME_H * 0.5f + 7f);
        fn.getData().setScale(1.25f);
        fn.setColor((nameHov || editingName) ? Color.WHITE : new Color(0.88f, 0.88f, 0.92f, 1f));
        String pn = PlayerProfile.get().playerName;
        if (editingName && ((int)(System.currentTimeMillis() / 400L) & 1) == 0) pn += "_";
        gl.setText(fn, pn);
        fn.draw(bt, pn, bx + BTN_W - gl.width - 10f, nameY + NAME_H * 0.5f + 8f);

        // Dev watermark — anchored at very bottom centre, below Test Map button
        if (devMode) {
            fn.getData().setScale(0.95f);
            fn.setColor(1f, 0.5f, 0.1f, 0.70f);
            gl.setText(fn, "DEV MODE");
            fn.draw(bt, "DEV MODE", W * 0.5f - gl.width * 0.5f, 10f);
        }

        fn.getData().setScale(1f);
        bt.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // ---- click handling ----
        if (Gdx.input.justTouched()) {
            if (nameHov) {
                editingName = true;
                showingDifficulty = false;
                if ("Player".equals(PlayerProfile.get().playerName)) PlayerProfile.get().playerName = "";
            } else if (eHov) {
                AudioManager.get().click();
                launchSinglePlayer(1.0f);
            } else if (nHov) {
                AudioManager.get().click();
                launchSinglePlayer(1.5f);
            } else if (hHov) {
                AudioManager.get().click();
                launchSinglePlayer(2.25f);
            } else if (spHov) {
                AudioManager.get().click();
                editingName = false;
                showingDifficulty = !showingDifficulty;
                if (PlayerProfile.get().playerName.trim().isEmpty()) PlayerProfile.get().playerName = "Player";
            }
            else {
                editingName = false;
                if (PlayerProfile.get().playerName.trim().isEmpty()) PlayerProfile.get().playerName = "Player";
                if (mpHov) { AudioManager.get().click(); game.setScreen(new MultiplayerScreen(game)); }
                else if (skHov) { AudioManager.get().click(); game.setScreen(new SkinsScreen(game)); }
                else if (opHov) { AudioManager.get().click(); game.setScreen(new OptionsMenuScreen(game)); }
                else if (mmHov) { AudioManager.get().click(); game.setScreen(new MapMakerMenuScreen(game)); }
                else if (tmHov) { AudioManager.get().click(); game.setScreen(new TestPlayerScreen(game)); }
                else { showingDifficulty = false; }
            }
        }
        if (!editingName && Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) Gdx.app.exit();
    }

    private void launchSinglePlayer(float diff) {
        GameMap map = null;
        try {
            FileHandle f = Gdx.files.local(DEFAULT_MAP);
            if (!f.exists()) f = Gdx.files.internal(DEFAULT_MAP);
            map = MapSerializer.load(f);
        } catch (Exception ignored) {}
        if (map == null) return;
        AudioManager.get().stopMenuMusic();
        game.setScreen(new GameScreen(game, map, diff));
    }

    // ---- drawing helpers ----

    private void shBtn(ShapeRenderer s, float x, float y, float w, float h, boolean hov, boolean primary) {
        if (primary) {
            // Green primary
            Color fill = hov ? new Color(0.42f, 0.74f, 0.18f, 1f) : new Color(0.33f, 0.60f, 0.14f, 1f);
            s.setColor(fill);
            s.rect(x, y, w, h);
            s.setColor(fill.r * 1.3f, fill.g * 1.3f, fill.b * 1.3f, 1f);
            s.rect(x + 3f, y + h - 4f, w - 6f, 2f);
            BedrockWidgets.border(s, x, y, w, h, 3f, BedrockWidgets.BTN_EDGE);
        } else {
            BedrockWidgets.button(s, x, y, w, h,
                hov ? BedrockWidgets.BtnState.HOVER : BedrockWidgets.BtnState.NORMAL);
        }
    }

    private void btnLabel(String text, float bx, float by, float bw, float bh) {
        gl.setText(fn, text);
        fn.draw(bt, text, bx + (bw - gl.width) * 0.5f, by + (bh + gl.height) * 0.5f);
    }

    private static boolean hover(Vector2 m, float x, float y, float w, float h) {
        return m.x >= x && m.x <= x + w && m.y >= y && m.y <= y + h;
    }

    @Override
    public void resize(int w, int h) {
        uv.resize(w, h);
        sh.setProjectionMatrix(uv.combined());
        bt.setProjectionMatrix(uv.combined());
    }

    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void hide()   {
        if (Gdx.input.getInputProcessor() == nameInput) Gdx.input.setInputProcessor(null);
        dispose();
    }

    @Override
    public void dispose() {
        sh.dispose();
        bt.dispose();
        fn.dispose();
        if (showcase != null) showcase.dispose();
        if (skinTex  != null) skinTex.dispose();
    }
}
