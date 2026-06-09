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
import com.brawlgame.audio.AudioManager;
import com.brawlgame.game.PlayerProfile;
import com.brawlgame.ui.BedrockWidgets;
import com.brawlgame.ui.CharacterShowcase;
import com.brawlgame.ui.UiViewport;

/**
 * Skin selection screen.
 *
 * <p>Lists every {@code .png} inside the local {@code skins/} folder. The default built-in skin is
 * always the first entry. Selecting a skin updates the live 3D preview on the right half. Confirming
 * saves the choice to {@link PlayerProfile} and returns to the main menu.
 */
public final class SkinsScreen implements Screen {

    private static final float THUMB_SZ  = 72f;
    private static final float THUMB_PAD = 10f;
    private static final float LIST_X    = 60f;
    private static final float LIST_TOP  = 580f; // leave room above for the title

    private final Game game;
    private final UiViewport     uv   = new UiViewport();
    private final ShapeRenderer  sh   = new ShapeRenderer();
    private final SpriteBatch    bt   = new SpriteBatch();
    private final BitmapFont     fn   = new BitmapFont();
    private final GlyphLayout    gl   = new GlyphLayout();

    // skin entries
    private static final class SkinEntry {
        final String name;   // display name (filename without extension, or "Default")
        final String path;   // "" for default
        Texture tex;
        SkinEntry(String name, String path) { this.name = name; this.path = path; }
    }
    private final List<SkinEntry> skins = new ArrayList<>();
    private int selected = 0;

    // 3D preview
    private CharacterShowcase showcase;
    private int showcaseIdx = -1;

    // Button rects in virtual coords
    private float confirmX, confirmY;
    private float backX,    backY;
    private static final float BTN_W = 200f, BTN_H = 52f;

    public SkinsScreen(Game game) { this.game = game; }

    @Override
    public void show() {
        skins.clear();

        // Always add default first
        SkinEntry def = new SkinEntry("Default", "");
        def.tex = new Texture(Gdx.files.internal("textures/player.png"));
        skins.add(def);

        // Scan local skins/ folder
        FileHandle dir = Gdx.files.local("skins");
        if (dir.exists() && dir.isDirectory()) {
            for (FileHandle f : dir.list(".png")) {
                SkinEntry e = new SkinEntry(f.nameWithoutExtension(), f.path());
                try { e.tex = new Texture(f); } catch (Exception ex) { e.tex = def.tex; }
                skins.add(e);
            }
        }

        // Find currently selected skin
        String cur = PlayerProfile.get().selectedSkin;
        selected = 0;
        for (int i = 0; i < skins.size(); i++) {
            if (skins.get(i).path.equals(cur)) { selected = i; break; }
        }

        rebuildShowcase();
    }

    private void rebuildShowcase() {
        if (showcase != null) showcase.dispose();
        showcase = new CharacterShowcase();
        showcaseIdx = showcase.add(skins.get(selected).tex, null);
    }

    @Override
    public void render(float delta) {
        float W = uv.width(), H = uv.height();

        Gdx.gl.glClearColor(0.06f, 0.06f, 0.09f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // 3D preview on the right half (before uiv.apply so it uses full screen)
        float prevX = W * 0.50f, prevY = H * 0.10f, prevW = W * 0.44f, prevH = H * 0.80f;
        if (showcase != null && showcaseIdx >= 0) {
            showcase.render(uv, showcaseIdx, prevX, prevY, prevW, prevH,
                (System.currentTimeMillis() % 5000) / 5000f * 360f, 0f, Color.WHITE);
        }

        uv.apply();
        sh.setProjectionMatrix(uv.combined());
        bt.setProjectionMatrix(uv.combined());

        Vector2 m = uv.unproject(Gdx.input.getX(), Gdx.input.getY());

        // Left panel background
        sh.begin(ShapeType.Filled);
        sh.setColor(0.08f, 0.09f, 0.12f, 0.95f);
        sh.rect(0, 0, W * 0.50f, H);

        // Skin thumbnail list
        for (int i = 0; i < skins.size(); i++) {
            float tx = LIST_X;
            float ty = LIST_TOP - i * (THUMB_SZ + THUMB_PAD);
            boolean hov = m.x >= tx && m.x <= tx + THUMB_SZ * 3.5f && m.y >= ty && m.y <= ty + THUMB_SZ;
            Color bg = i == selected ? BedrockWidgets.GREEN
                : hov ? BedrockWidgets.BTN_HOVER : BedrockWidgets.BTN;
            sh.setColor(bg);
            sh.rect(tx, ty, THUMB_SZ * 3.5f + 8f, THUMB_SZ);
            BedrockWidgets.border(sh, tx, ty, THUMB_SZ * 3.5f + 8f, THUMB_SZ, 2f,
                i == selected ? BedrockWidgets.BTN_EDGE : BedrockWidgets.PANEL_EDGE);
        }

        // Confirm + Back buttons
        confirmX = LIST_X; confirmY = 40f;
        backX    = LIST_X + BTN_W + 16f; backY = 40f;
        boolean confHov = m.x >= confirmX && m.x <= confirmX + BTN_W && m.y >= confirmY && m.y <= confirmY + BTN_H;
        boolean backHov = m.x >= backX    && m.x <= backX    + BTN_W && m.y >= backY    && m.y <= backY    + BTN_H;
        BedrockWidgets.button(sh, confirmX, confirmY, BTN_W, BTN_H,
            confHov ? BedrockWidgets.BtnState.HOVER : BedrockWidgets.BtnState.NORMAL);
        BedrockWidgets.button(sh, backX, backY, BTN_W, BTN_H,
            backHov ? BedrockWidgets.BtnState.HOVER : BedrockWidgets.BtnState.NORMAL);
        sh.end();

        // Skin thumbnails (2D texture)
        bt.begin();
        for (int i = 0; i < skins.size(); i++) {
            float tx = LIST_X + 4f;
            float ty = LIST_TOP - i * (THUMB_SZ + THUMB_PAD) + 4f;
            bt.draw(skins.get(i).tex, tx, ty, THUMB_SZ - 8f, THUMB_SZ - 8f);
        }
        bt.end();

        // Text (separate pass so textures don't stomp shape state)
        sh.begin(ShapeType.Filled); sh.end(); // flush
        bt.begin();
        for (int i = 0; i < skins.size(); i++) {
            float tx = LIST_X + THUMB_SZ + 4f;
            float ty = LIST_TOP - i * (THUMB_SZ + THUMB_PAD) + THUMB_SZ * 0.55f + 6f;
            fn.getData().setScale(1.2f);
            fn.setColor(BedrockWidgets.TEXT_LIGHT);
            fn.draw(bt, skins.get(i).name, tx, ty);
        }
        // Title — drawn above the list with clear vertical separation
        fn.getData().setScale(2.4f);
        fn.setColor(1f, 0.86f, 0.16f, 1f);
        fn.draw(bt, "SKINS", LIST_X, H - 12f);

        // Confirm / Back labels
        fn.getData().setScale(1.4f);
        fn.setColor(BedrockWidgets.TEXT_LIGHT);
        centered("Confirm", confirmX, confirmY, BTN_W, BTN_H);
        centered("Back",    backX,    backY,    BTN_W, BTN_H);

        fn.getData().setScale(1f);
        bt.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // Input
        if (Gdx.input.justTouched()) {
            // check thumbnail list clicks
            for (int i = 0; i < skins.size(); i++) {
                float tx = LIST_X, ty = LIST_TOP - i * (THUMB_SZ + THUMB_PAD);
                if (m.x >= tx && m.x <= tx + THUMB_SZ * 3.5f + 8f && m.y >= ty && m.y <= ty + THUMB_SZ) {
                    selected = i;
                    AudioManager.get().click();
                    rebuildShowcase();
                }
            }
            boolean confHov2 = m.x >= confirmX && m.x <= confirmX + BTN_W && m.y >= confirmY && m.y <= confirmY + BTN_H;
            boolean backHov2 = m.x >= backX    && m.x <= backX    + BTN_W && m.y >= backY    && m.y <= backY    + BTN_H;
            if (confHov2) { AudioManager.get().click(); saveSkin(); game.setScreen(new MainMenuScreen(game)); }
            if (backHov2) { AudioManager.get().click(); game.setScreen(new MainMenuScreen(game)); }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) game.setScreen(new MainMenuScreen(game));
    }

    private void saveSkin() {
        PlayerProfile p = PlayerProfile.get();
        p.selectedSkin = skins.get(selected).path;
        p.save();
    }

    private void centered(String text, float bx, float by, float bw, float bh) {
        gl.setText(fn, text);
        fn.draw(bt, text, bx + (bw - gl.width) * 0.5f, by + (bh + gl.height) * 0.5f);
    }

    @Override public void resize(int w, int h) { uv.resize(w, h); }
    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void hide()   { dispose(); }

    @Override
    public void dispose() {
        sh.dispose();
        bt.dispose();
        fn.dispose();
        for (SkinEntry e : skins) {
            if (e.tex != null && !e.path.isEmpty()) e.tex.dispose();
        }
        skins.clear();
        if (showcase != null) showcase.dispose();
    }
}
