package com.brawlgame.ui;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.brawlgame.entity.ArmorRenderer;
import com.brawlgame.item.Inventory;
import com.brawlgame.item.ItemStack;
import com.brawlgame.item.ItemType;
import com.brawlgame.model.MinecraftPlayerModel;
import com.brawlgame.screen.MainMenuScreen;

/**
 * The in-game pause overlay: a dim scrim, a slowly rotating 3D player model in a framed box, and three
 * Bedrock-styled buttons — <b>Back to Game</b>, <b>Options…</b>, <b>Leave</b>. "Options…" swaps the
 * content for the {@link OptionsPanel}. Drawn in real screen pixels (re-laid-out each frame, so resize
 * needs no work), and the rotating character is rendered with its own {@link ModelBatch}+camera in a
 * scissored sub-region <i>over</i> the 2D UI. The owning screen routes input here while {@link #isOpen()}.
 */
public final class PauseOverlay implements InputProcessor, Disposable {

    private static final Color SCRIM = new Color(0f, 0f, 0f, 0.7f);
    private static final float BTN_W = 320f, BTN_H = 56f, BTN_GAP = 16f;

    private final Game game;

    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout gl = new GlyphLayout();

    // rotating 3D character
    private final ModelBatch modelBatch = new ModelBatch();
    private final Environment env = new Environment();
    private final PerspectiveCamera cam = new PerspectiveCamera(35f, 1f, 1f);
    private final Model model;
    private final ModelInstance instance;
    private final ArmorRenderer armor;      // diamond set worn by the preview model
    private final Inventory armorInv = new Inventory();
    private final Model pedestal;
    private final ModelInstance pedestalInstance;
    private float spin;

    private final UiButton backBtn = new UiButton("Back to Game", 0, 0, BTN_W, BTN_H);
    private final UiButton optionsBtn = new UiButton("Options...", 0, 0, BTN_W, BTN_H);
    private final UiButton leaveBtn = new UiButton("Leave", 0, 0, BTN_W, BTN_H);
    private final OptionsPanel options = new OptionsPanel();

    private boolean open;
    private boolean showingOptions;

    public PauseOverlay(Game game, Texture skin) {
        this.game = game;
        env.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.7f, 0.7f, 0.74f, 1f));
        env.add(new DirectionalLight().set(0.6f, 0.6f, 0.58f, -0.5f, -0.7f, -0.6f));
        model = MinecraftPlayerModel.build(skin);
        instance = new ModelInstance(model);
        instance.calculateTransforms(); // bake bone globalTransforms for the armour parenting

        // Preview model wears a full diamond set.
        armorInv.set(Inventory.ARMOR_BASE + 0, new ItemStack(ItemType.DIAMOND_HELMET));
        armorInv.set(Inventory.ARMOR_BASE + 1, new ItemStack(ItemType.DIAMOND_CHESTPLATE));
        armorInv.set(Inventory.ARMOR_BASE + 2, new ItemStack(ItemType.DIAMOND_LEGGINGS));
        armorInv.set(Inventory.ARMOR_BASE + 3, new ItemStack(ItemType.DIAMOND_BOOTS));
        armor = new ArmorRenderer(armorInv);

        // A clean, compact stone pedestal under the character (kept narrow so it doesn't dominate the frame).
        ModelBuilder mb = new ModelBuilder();
        pedestal = mb.createCylinder(1.0f, 0.24f, 1.0f, 20,
            new com.badlogic.gdx.graphics.g3d.Material(
                com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createDiffuse(0.62f, 0.62f, 0.66f, 1f)),
            Usage.Position | Usage.Normal);
        pedestalInstance = new ModelInstance(pedestal);
        pedestalInstance.transform.setToTranslation(0f, -0.12f, 0f); // top flush with the feet

        cam.near = 0.1f; cam.far = 30f;
    }

    public boolean isOpen() { return open; }
    public void open()  { open = true; showingOptions = false; options.reset(); }
    public void resume() { open = false; }

    /** ESC behaviour: from Options → back to the buttons; otherwise resume the game. */
    public void back() {
        if (showingOptions) { showingOptions = false; options.reset(); }
        else resume();
    }

    public void update(float delta) { spin = (spin + delta * 28f) % 360f; }

    public void render() {
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        float mx = Gdx.input.getX(), my = h - Gdx.input.getY();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        batch.getProjectionMatrix().setToOrtho2D(0, 0, w, h);

        // model box (portrait, to match the character's tall proportions) + button layout
        float boxH = Math.min(h * 0.68f, 460f);
        float boxW = boxH * 0.6f;
        float boxX = w * 0.5f - BTN_W * 0.5f - 70f - boxW, boxY = (h - boxH) * 0.5f;
        float btnX = w * 0.5f - BTN_W * 0.5f + 110f;
        float cy = h * 0.5f + BTN_H + BTN_GAP;
        backBtn.setBounds(btnX, cy, BTN_W, BTN_H);
        optionsBtn.setBounds(btnX, cy - BTN_H - BTN_GAP, BTN_W, BTN_H);
        leaveBtn.setBounds(btnX, cy - 2 * (BTN_H + BTN_GAP), BTN_W, BTN_H);
        backBtn.setHovered(backBtn.contains(mx, my));
        optionsBtn.setHovered(optionsBtn.contains(mx, my));
        leaveBtn.setHovered(leaveBtn.contains(mx, my));

        float pw = Math.min(w * 0.66f, 820f), ph = Math.min(h * 0.78f, 560f);
        float panelX = (w - pw) * 0.5f, panelY = (h - ph) * 0.5f;

        // ---- 2D backgrounds ----
        shapes.begin(ShapeType.Filled);
        BedrockWidgets.rect(shapes, 0, 0, w, h, SCRIM);
        if (showingOptions) {
            options.renderBg(shapes, panelX, panelY, pw, ph, mx, my);
        } else {
            BedrockWidgets.panel(shapes, boxX, boxY, boxW, boxH);
            BedrockWidgets.rect(shapes, boxX + 4, boxY + 4, boxW - 8, boxH - 8, BedrockWidgets.PANEL_INNER);
            backBtn.renderBackground(shapes);
            optionsBtn.renderBackground(shapes);
            leaveBtn.renderBackground(shapes);
        }
        shapes.end();

        // ---- 2D labels ----
        batch.begin();
        if (showingOptions) {
            options.renderText(batch, font);
        } else {
            font.getData().setScale(1.6f);
            font.setColor(Color.WHITE);
            gl.setText(font, "PAUSE");
            font.draw(batch, gl, w * 0.5f - gl.width * 0.5f, h - 48f);
            font.getData().setScale(1f);
            backBtn.renderLabel(batch, font);
            optionsBtn.renderLabel(batch, font);
            leaveBtn.renderLabel(batch, font);
        }
        batch.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // ---- rotating 3D character over the box ----
        if (!showingOptions) renderModel(boxX + 6, boxY + 6, boxW - 12, boxH - 12, w, h);
    }

    private void renderModel(float rx, float ry, float rw, float rh, float w, float h) {
        Gdx.gl.glViewport((int) rx, (int) ry, (int) rw, (int) rh);
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor((int) rx, (int) ry, (int) rw, (int) rh);
        Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);

        // Frame the whole figure incl. the pedestal (−0.25 → armoured helmet top ~2.1) so nothing clips.
        PreviewCamera.frame(cam, (int) rw, (int) rh, -0.25f, 2.1f, 0.55f);
        instance.transform.setToRotation(Vector3.Y, spin);
        modelBatch.begin(cam);
        modelBatch.render(pedestalInstance, env);
        modelBatch.render(instance, env);
        armor.render(modelBatch, env, instance); // diamond armour over the rig
        modelBatch.end();

        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glViewport(0, 0, (int) w, (int) h);
    }

    // ---------------------------------------------------------------- input
    @Override
    public boolean keyDown(int keycode) {
        if (!open) return false;
        if (showingOptions && options.keyDown(keycode)) return true;
        if (keycode == Input.Keys.ESCAPE) { back(); return true; }
        return true; // swallow everything while paused
    }

    @Override
    public boolean touchDown(int sx, int sy, int pointer, int button) {
        if (!open) return false;
        float mx = sx, my = Gdx.graphics.getHeight() - sy;
        if (showingOptions) {
            if (options.click(mx, my)) { showingOptions = false; options.reset(); }
            return true;
        }
        if (backBtn.contains(mx, my)) resume();
        else if (optionsBtn.contains(mx, my)) { showingOptions = true; options.reset(); }
        else if (leaveBtn.contains(mx, my)) { resume(); game.setScreen(new MainMenuScreen(game)); }
        return true;
    }

    @Override public boolean touchDragged(int sx, int sy, int pointer) {
        if (open && showingOptions) options.drag(sx);
        return open;
    }
    @Override public boolean touchUp(int sx, int sy, int pointer, int button) {
        if (open && showingOptions) options.release();
        return open;
    }
    @Override public boolean keyUp(int keycode) { return open; }
    @Override public boolean keyTyped(char c) { return open; }
    @Override public boolean mouseMoved(int x, int y) { return open; }
    @Override public boolean scrolled(float ax, float ay) { return open; }
    @Override public boolean touchCancelled(int a, int b, int c, int d) { return open; }

    @Override
    public void dispose() {
        shapes.dispose(); batch.dispose(); font.dispose(); modelBatch.dispose();
        model.dispose(); armor.dispose(); pedestal.dispose();
    }
}
