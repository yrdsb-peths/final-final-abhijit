package com.brawlgame.combat;

import com.brawlgame.item.Inventory;
import com.brawlgame.item.ItemStack;
import com.brawlgame.item.ItemType;

/**
 * Minecraft (Java Edition) armour defence values and the authentic damage-reduction formula. Each
 * equipped piece contributes <b>armour points</b> (the chestplate-icon half-points) and, for diamond,
 * <b>toughness</b>; the two combine to soften incoming damage — more so against small hits, less so
 * against very large ones.
 *
 * <p>Per-piece points (helmet / chest / legs / boots):
 * <ul>
 *   <li><b>Leather</b> 1 / 3 / 2 / 1 — 7 total, 0 toughness</li>
 *   <li><b>Iron</b> 2 / 6 / 5 / 2 — 15 total, 0 toughness</li>
 *   <li><b>Diamond</b> 3 / 8 / 6 / 3 — 20 total, +2 toughness per piece (8 for a full set)</li>
 * </ul>
 */
public final class ArmorStats {

    private ArmorStats() {}

    /** Armour points for a single equipped piece (0 if not an armour item). */
    public static int points(ItemType t) {
        if (t == null) return 0;
        switch (t) {
            case LEATHER_HELMET:    return 1;
            case LEATHER_CHESTPLATE:return 3;
            case LEATHER_LEGGINGS:  return 2;
            case LEATHER_BOOTS:     return 1;
            case IRON_HELMET:       return 2;
            case IRON_CHESTPLATE:   return 6;
            case IRON_LEGGINGS:     return 5;
            case IRON_BOOTS:        return 2;
            case DIAMOND_HELMET:    return 3;
            case DIAMOND_CHESTPLATE:return 8;
            case DIAMOND_LEGGINGS:  return 6;
            case DIAMOND_BOOTS:     return 3;
            default:                return 0;
        }
    }

    /** Armour toughness for a single equipped piece — diamond is +2 per piece, everything else 0. */
    public static int toughness(ItemType t) {
        if (t == null) return 0;
        switch (t) {
            case DIAMOND_HELMET:
            case DIAMOND_CHESTPLATE:
            case DIAMOND_LEGGINGS:
            case DIAMOND_BOOTS: return 2;
            default:            return 0;
        }
    }

    /** Summed {points, toughness} over the four armour slots of an inventory. */
    public static int[] of(Inventory inv) {
        int armor = 0, tough = 0;
        for (int slot = 0; slot < Inventory.ARMOR; slot++) {
            ItemStack s = inv.armor(slot);
            if (s == null) continue;
            armor += points(s.type);
            tough += toughness(s.type);
        }
        return new int[] { armor, tough };
    }

    /**
     * The vanilla Java-Edition reduction:
     * {@code raw * (1 - min(20, max(armor/5, armor - raw/(2 + toughness/4))) / 25)}.
     */
    public static float reduce(float raw, int armor, float toughness) {
        float effective = Math.min(20f,
            Math.max(armor / 5f, armor - raw / (2f + toughness / 4f)));
        return raw * (1f - effective / 25f);
    }

    /** Convenience: reduce {@code raw} by whatever the inventory currently has equipped. */
    public static float reduce(float raw, Inventory inv) {
        int[] s = of(inv);
        return reduce(raw, s[0], s[1]);
    }
}
