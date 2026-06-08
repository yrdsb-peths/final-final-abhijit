package com.brawlgame.game;

/** Per-combatant stats tracked for the Showdown end screen. */
public final class MatchStats {

    public final String name;
    public int takedowns;
    public float damageDealt;
    public float healing;

    public MatchStats(String name) {
        this.name = name;
    }

    public void addDamage(float amount) {
        if (amount > 0f) damageDealt += amount;
    }

    public void addHealing(float amount) {
        if (amount > 0f) healing += amount;
    }

    public void addTakedown() {
        takedowns++;
    }
}
