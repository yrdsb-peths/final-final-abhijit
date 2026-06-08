package com.brawlgame.game;

/**
 * In-session player profile. All fields reset to defaults on every launch — nothing is persisted.
 */
public final class PlayerProfile {

    private static PlayerProfile instance;

    public static PlayerProfile get() {
        if (instance == null) instance = new PlayerProfile();
        return instance;
    }

    /** Reset to a blank slate (used when restarting without killing the JVM process). */
    public static void reset() { instance = new PlayerProfile(); }

    // ---- in-session fields (never written to disk) ----
    public boolean firstLaunch  = true;
    public int     totalMatches = 0;
    public int     wins         = 0;
    public int     losses       = 0;
    /** Filename (no path) of the selected skin inside the local {@code skins/} folder, or {@code ""} for default. */
    public String  selectedSkin = "";
    /** Display name shown in the main menu and match HUD. */
    public String  playerName   = "Player";
    /** When true: shows Map Maker on the main menu. */
    public boolean devMode      = false;

    private PlayerProfile() {}

    /** No-op — data is never persisted. */
    public void save() {}

    public void recordWin()  { totalMatches++; wins++; }
    public void recordLoss() { totalMatches++; losses++; }

    /** Always 1.0 — no difficulty scaling without persistent win-rate data. */
    public float difficultyScale() { return 1f; }

    public String winRateString() {
        if (totalMatches == 0) return "--";
        return Math.round(wins * 100f / totalMatches) + "%";
    }
}
