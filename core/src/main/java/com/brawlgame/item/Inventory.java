package com.brawlgame.item;

/**
 * The player's storage: a 9-slot hotbar, an 18-slot main store (2×9) and 4 armour slots
 * (head, chest, legs, feet). Deliberately has <b>no crafting grid</b>. Slots are plain
 * {@link ItemStack} references ({@code null} = empty); the UI moves stacks between them.
 *
 * <p>Slots are also addressable through a single flat index space so the UI's drag logic can treat
 * every region uniformly: {@code [0,9)} hotbar, {@code [9,27)} storage, {@code [27,31)} armour.
 */
public final class Inventory {

    public static final int HOTBAR = 9;
    public static final int STORAGE = 18;
    public static final int ARMOR = 4;

    public static final int HOTBAR_BASE = 0;
    public static final int STORAGE_BASE = HOTBAR;          // 9
    public static final int ARMOR_BASE = HOTBAR + STORAGE;  // 27
    public static final int TOTAL = HOTBAR + STORAGE + ARMOR; // 31

    private final ItemStack[] slots = new ItemStack[TOTAL];

    public ItemStack get(int flat) {
        return (flat < 0 || flat >= TOTAL) ? null : slots[flat];
    }

    public void set(int flat, ItemStack stack) {
        if (flat < 0 || flat >= TOTAL) return;
        slots[flat] = (stack != null && stack.isEmpty()) ? null : stack;
    }

    public ItemStack hotbar(int i)  { return get(HOTBAR_BASE + i); }
    public ItemStack storage(int i) { return get(STORAGE_BASE + i); }
    public ItemStack armor(int i)   { return get(ARMOR_BASE + i); }

    public boolean isArmorSlot(int flat)  { return flat >= ARMOR_BASE && flat < ARMOR_BASE + ARMOR; }
    public boolean isHotbarSlot(int flat) { return flat >= HOTBAR_BASE && flat < HOTBAR_BASE + HOTBAR; }

    /** The armour-slot index (0..3) a piece belongs in, or -1 — used to gate drops onto armour slots. */
    public static int armorSlotFor(ItemStack stack) {
        return stack == null ? -1 : stack.type.armorSlot();
    }

    /** Place a stack into the first empty hotbar-then-storage slot. Returns false if full. */
    public boolean add(ItemStack stack) {
        for (int i = 0; i < HOTBAR + STORAGE; i++) {
            if (slots[i] == null) { slots[i] = stack; return true; }
        }
        return false;
    }
}
