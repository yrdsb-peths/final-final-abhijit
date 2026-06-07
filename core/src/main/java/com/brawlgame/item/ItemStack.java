package com.brawlgame.item;

/**
 * A stack of one {@link ItemType} with a count. Slots hold an {@code ItemStack} or {@code null} when
 * empty. Mutable so the UI's pick-up / place-down drag can split and merge stacks in place.
 */
public final class ItemStack {

    public static final int MAX = 64;

    public final ItemType type;
    public int count;

    public ItemStack(ItemType type, int count) {
        this.type = type;
        this.count = Math.max(1, count);
    }

    public ItemStack(ItemType type) {
        this(type, 1);
    }

    public boolean isEmpty() { return count <= 0; }

    public ItemStack copy() { return new ItemStack(type, count); }

    /** True if {@code other} can merge into this stack (same type, both armour/non-armour aside). */
    public boolean stacksWith(ItemStack other) {
        return other != null && other.type == type;
    }
}
