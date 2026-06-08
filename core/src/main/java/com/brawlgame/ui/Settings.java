package com.brawlgame.ui;

import java.util.EnumMap;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

/**
 * Global game settings backing the in-game Options menu. A simple process-wide singleton ({@link #get})
 * so any system can read the live values. Most settings apply immediately:
 * <ul>
 *   <li><b>fpsLimit</b> — soft frame cap applied by {@link #capFrame()} at the end of a gameplay frame
 *       (0 = unlimited). Works without the desktop backend since it just throttles the render loop.</li>
 *   <li><b>showFps</b> — the HUD prints the live FPS.</li>
 *   <li><b>mouseSensitivity</b> — multiplies the player's aim turn speed.</li>
 *   <li><b>masterVolume</b> — scales any sound playback (foundation; read by sound code).</li>
 *   <li><b>keys</b> — rebindable controls; the player/UI read these instead of hardcoded keys.</li>
 * </ul>
 */
public final class Settings {

    public enum Action {
        FORWARD("Forward"), BACKWARD("Backward"), LEFT("Strafe Left"), RIGHT("Strafe Right"),
        JUMP("Jump"), ATTACK("Attack"), INVENTORY("Open Inventory"), DROP("Drop Item"),
        SLOT_1("Slot 1"), SLOT_2("Slot 2"), SLOT_3("Slot 3"), SLOT_4("Slot 4"), SLOT_5("Slot 5"),
        SLOT_6("Slot 6"), SLOT_7("Slot 7"), SLOT_8("Slot 8"), SLOT_9("Slot 9");
        public final String label;
        Action(String label) { this.label = label; }
    }

    /** SLOT_1..SLOT_9 in order, for the hotbar + the controls grid. */
    public static final Action[] SLOTS = {
        Action.SLOT_1, Action.SLOT_2, Action.SLOT_3, Action.SLOT_4, Action.SLOT_5,
        Action.SLOT_6, Action.SLOT_7, Action.SLOT_8, Action.SLOT_9,
    };

    private static final Settings INSTANCE = new Settings();
    public static Settings get() { return INSTANCE; }

    // ---- video (no in-game control; vsync caps the loop) ----
    public int fpsLimit = 0;          // 0 = unlimited (rely on vsync)
    public boolean showFps = false;

    // ---- general ----
    public float mouseSensitivity = 1.0f; // 0.25 .. 2.0
    public float masterVolume = 1.0f;     // 0 .. 1

    // ---- controls ----
    /** ATTACK is bound to the left mouse button by convention (negative sentinel = mouse). */
    public static final int MOUSE_LEFT = -100;
    public final EnumMap<Action, Integer> keys = new EnumMap<>(Action.class);

    private long lastFrameNanos = System.nanoTime();

    private Settings() {
        keys.put(Action.FORWARD, Input.Keys.W);
        keys.put(Action.BACKWARD, Input.Keys.S);
        keys.put(Action.LEFT, Input.Keys.A);
        keys.put(Action.RIGHT, Input.Keys.D);
        keys.put(Action.JUMP, Input.Keys.SPACE);
        keys.put(Action.ATTACK, MOUSE_LEFT);
        keys.put(Action.INVENTORY, Input.Keys.E);
        keys.put(Action.DROP, Input.Keys.Q);
        for (int i = 0; i < SLOTS.length; i++) keys.put(SLOTS[i], Input.Keys.NUM_1 + i);
    }

    /** True if the bound key for {@code a} was just pressed this frame (mouse-left handled by caller). */
    public boolean justPressed(Action a) {
        int k = key(a);
        return k != MOUSE_LEFT && com.badlogic.gdx.Gdx.input.isKeyJustPressed(k);
    }

    public int key(Action a) { return keys.getOrDefault(a, Input.Keys.UNKNOWN); }

    /** Human-readable name for a bound key/button. */
    public String keyName(Action a) {
        int k = key(a);
        if (k == MOUSE_LEFT) return "Left Button";
        return Input.Keys.toString(k);
    }

    /** Soft FPS cap: sleeps the remainder of the target frame. Call once per gameplay frame. */
    public void capFrame() {
        long now = System.nanoTime();
        if (fpsLimit > 0) {
            long target = 1_000_000_000L / fpsLimit;
            long elapsed = now - lastFrameNanos;
            if (elapsed < target) {
                try { Thread.sleep((target - elapsed) / 1_000_000L); } catch (InterruptedException ignored) {}
            }
        }
        lastFrameNanos = System.nanoTime();
    }

    public int fps() { return Gdx.graphics.getFramesPerSecond(); }
}
