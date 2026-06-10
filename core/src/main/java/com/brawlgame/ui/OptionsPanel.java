package com.brawlgame.ui;

import java.util.EnumMap;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import com.brawlgame.audio.AudioManager;
import com.brawlgame.audio.SoundManager;
import com.brawlgame.game.PlayerProfile;
import com.brawlgame.ui.Settings.Action;

/**
 * A Bedrock-styled settings panel with two tabs — <b>Controls</b> (a comprehensive, rebindable keybind
 * grid: movement, jump, attack, open-inventory, drop-item, and hotbar slots 1–9) and <b>Sound</b> (a
 * master-volume slider) — bound live to {@link Settings}. Drawn in y-up screen pixels: the owner runs
 * the {@code shapes.begin(Filled)} pass via {@link #renderBg} then the {@code batch} pass via
 * {@link #renderText}; clicks/drags/keys go through {@link #click}/{@link #drag}/{@link #keyDown}.
 */
public final class OptionsPanel {

    private static final String[] TABS = {"Controls", "Sound", "General"};
    /** Left column of the controls grid (general actions); the right column is the 9 hotbar slots. */
    private static final Action[] LEFT_COL = {
        Action.FORWARD, Action.BACKWARD, Action.LEFT, Action.RIGHT,
        Action.JUMP, Action.ATTACK, Action.INVENTORY, Action.DROP,
    };
    private static final float KEY_W = 96f; // width of the key button at the right of a row

    private final Settings cfg = Settings.get();
    private final GlyphLayout gl = new GlyphLayout();

    private int tab = 0;
    private Action listening; // keybind row awaiting a key
    private boolean draggingVol;

    // layout (recomputed each frame)
    private float px, py, pw, ph;
    private final float[] tabR = new float[TABS.length * 4];
    private float[] doneR = new float[4];
    private final float[] volR = new float[4];
    private final float[] devToggleR = new float[4];
    private final EnumMap<Action, float[]> rowR = new EnumMap<>(Action.class);

    public void reset() { listening = null; draggingVol = false; }

    private void layout(float px, float py, float pw, float ph) {
        this.px = px; this.py = py; this.pw = pw; this.ph = ph;
        float tabW = (pw - 40f) / TABS.length, tabH = 44f, tabY = py + ph - 56f;
        for (int i = 0; i < TABS.length; i++)
            set(tabR, i, px + 20f + i * tabW, tabY, tabW - 8f, tabH);

        rowR.clear();
        float colW = (pw - 70f) / 2f, rowH = 38f, gap = 6f, top = tabY - 56f;
        float lx = px + 30f, rx = px + 40f + colW;
        for (int i = 0; i < LEFT_COL.length; i++)
            rowR.put(LEFT_COL[i], rect(lx, top - i * (rowH + gap), colW, rowH));
        for (int i = 0; i < Settings.SLOTS.length; i++)
            rowR.put(Settings.SLOTS[i], rect(rx, top - i * (rowH + gap), colW, rowH));

        set(volR, 0, px + 50f, top - 10f, pw - 100f, 34f);
        devToggleR[0] = px + 50f; devToggleR[1] = top - 66f; devToggleR[2] = 120f; devToggleR[3] = 34f;
        doneR = rect(px + (pw - 200f) * 0.5f, py + 18f, 200f, 46f);
    }

    // ---------------------------------------------------------------- render
    public void renderBg(ShapeRenderer s, float px, float py, float pw, float ph, float mx, float my) {
        layout(px, py, pw, ph);
        BedrockWidgets.panel(s, px, py, pw, ph);
        for (int i = 0; i < TABS.length; i++) {
            BedrockWidgets.BtnState st = i == tab ? BedrockWidgets.BtnState.SELECTED
                : inTab(i, mx, my) ? BedrockWidgets.BtnState.HOVER : BedrockWidgets.BtnState.NORMAL;
            BedrockWidgets.button(s, tabR[i*4], tabR[i*4+1], tabR[i*4+2], tabR[i*4+3], st);
        }
        if (tab == 0) {
            for (Action a : rowR.keySet()) {
                float[] r = rowR.get(a);
                float bx = r[0] + r[2] - KEY_W;
                BedrockWidgets.BtnState st = listening == a ? BedrockWidgets.BtnState.SELECTED
                    : in(bx, r[1], KEY_W, r[3], mx, my) ? BedrockWidgets.BtnState.HOVER
                    : BedrockWidgets.BtnState.NORMAL;
                BedrockWidgets.button(s, bx, r[1] + 3, KEY_W, r[3] - 6, st);
            }
        } else if (tab == 1) {
            BedrockWidgets.slider(s, volR[0], volR[1], volR[2], volR[3], cfg.masterVolume);
        } else {
            // General tab: Dev Mode toggle
            BedrockWidgets.toggle(s, devToggleR[0], devToggleR[1], devToggleR[2], devToggleR[3], PlayerProfile.get().devMode);
        }
        BedrockWidgets.button(s, doneR[0], doneR[1], doneR[2], doneR[3],
            in(doneR, mx, my) ? BedrockWidgets.BtnState.HOVER : BedrockWidgets.BtnState.NORMAL);
    }

    public void renderText(SpriteBatch b, BitmapFont font) {
        font.setColor(BedrockWidgets.TEXT_LIGHT);
        for (int i = 0; i < TABS.length; i++) centerIn(b, font, TABS[i], tabR[i*4], tabR[i*4+1], tabR[i*4+2], tabR[i*4+3]);
        if (tab == 0) {
            for (Action a : rowR.keySet()) {
                float[] r = rowR.get(a);
                font.setColor(BedrockWidgets.TEXT_DARK);
                font.draw(b, a.label, r[0] + 4f, r[1] + r[3] * 0.5f + 6f);
                font.setColor(BedrockWidgets.TEXT_DARK);
                String t = listening == a ? "..." : cfg.keyName(a);
                centerIn(b, font, t, r[0] + r[2] - KEY_W, r[1], KEY_W, r[3]);
            }
        } else if (tab == 1) {
            font.setColor(BedrockWidgets.TEXT_DARK);
            font.draw(b, "Master Volume: " + Math.round(cfg.masterVolume * 100) + "%", volR[0], volR[1] + 54f);
        } else {
            font.setColor(BedrockWidgets.TEXT_DARK);
            font.draw(b, "Developer Mode  [ " + (PlayerProfile.get().devMode ? "ON " : "OFF") + " ]",
                devToggleR[0] + devToggleR[2] + 12f,
                devToggleR[1] + devToggleR[3] * 0.5f + 6f);
            font.setColor(0.65f, 0.65f, 0.68f, 1f);
            font.draw(b, "Map Maker " + (PlayerProfile.get().devMode ? "visible" : "hidden"),
                devToggleR[0] + devToggleR[2] + 12f, devToggleR[1] + devToggleR[3] * 0.5f - 10f);
        }
        font.setColor(BedrockWidgets.TEXT_LIGHT);
        centerIn(b, font, "Done", doneR[0], doneR[1], doneR[2], doneR[3]);
    }

    // ---------------------------------------------------------------- input
    /** @return true when "Done" was clicked. */
    public boolean click(float mx, float my) {
        for (int i = 0; i < TABS.length; i++) if (inTab(i, mx, my)) { tab = i; listening = null; return false; }
        if (in(doneR, mx, my)) { listening = null; return true; }
        if (tab == 0) {
            for (Action a : rowR.keySet()) {
                float[] r = rowR.get(a);
                if (in(r[0] + r[2] - KEY_W, r[1], KEY_W, r[3], mx, my)) {
                    listening = a == Action.ATTACK ? null : a; // attack stays left-click
                    return false;
                }
            }
        } else if (tab == 1 && in(volR, mx, my)) { draggingVol = true; drag(mx); }
        else if (tab == 2 && in(devToggleR, mx, my)) {
            PlayerProfile.get().devMode = !PlayerProfile.get().devMode;
            PlayerProfile.get().save();
        }
        return false;
    }

    public void drag(float mx) {
        if (draggingVol) {
            cfg.masterVolume = clamp01((mx - volR[0]) / volR[2]);
            AudioManager.get().syncVolume();
            SoundManager.get().syncVolume();
        }
    }

    public void release() { draggingVol = false; }

    public boolean keyDown(int keycode) {
        if (listening == null) return false;
        cfg.keys.put(listening, keycode);
        listening = null;
        return true;
    }

    // ---- helpers ----
    private boolean inTab(int i, float mx, float my) { return in(tabR[i*4], tabR[i*4+1], tabR[i*4+2], tabR[i*4+3], mx, my); }
    private static float[] rect(float x, float y, float w, float h) { return new float[]{x, y, w, h}; }
    private static void set(float[] a, int i, float x, float y, float w, float h) { a[i*4]=x; a[i*4+1]=y; a[i*4+2]=w; a[i*4+3]=h; }
    private static boolean in(float[] r, float mx, float my) { return in(r[0], r[1], r[2], r[3], mx, my); }
    private static boolean in(float x, float y, float w, float h, float mx, float my) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
    private void centerIn(SpriteBatch b, BitmapFont f, String t, float x, float y, float w, float h) {
        gl.setText(f, t);
        f.draw(b, gl, x + (w - gl.width) * 0.5f, y + (h + gl.height) * 0.5f);
    }
    private static float clamp01(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }
}
