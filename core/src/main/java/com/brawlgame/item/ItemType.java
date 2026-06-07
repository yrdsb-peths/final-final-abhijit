package com.brawlgame.item;

/**
 * The strict item whitelist for the player/creative UI: <b>all swords</b>, the <b>potato gun</b>, and
 * <b>leather / iron / diamond armour</b> (helmet, chestplate, leggings, boots). Deliberately contains
 * <i>no</i> environmental blocks, props, chests, or food — the inventory/creative menu shows exactly
 * these and nothing else.
 *
 * <p>Each carries a display name, a {@link Kind} (drives armour-slot gating + the derived weapon) and
 * an optional icon texture path (null → the UI draws a coloured fallback). Pure data, no rendering
 * dependency; {@code Texture} loading lives in {@code ItemIcons}.
 */
public enum ItemType {

    // ---- melee weapons: every sword ----
    WOOD_SWORD   ("Wooden Sword",  Kind.WEAPON, "textures/items/wooden_sword.png"),
    STONE_SWORD  ("Stone Sword",   Kind.WEAPON, "textures/items/stone_sword.png"),
    IRON_SWORD   ("Iron Sword",    Kind.WEAPON, "textures/items/iron_sword.png"),
    GOLD_SWORD   ("Golden Sword",  Kind.WEAPON, "textures/items/golden_sword.png"),
    DIAMOND_SWORD("Diamond Sword", Kind.WEAPON, "textures/items/diamond_sword.png"),

    // ---- ranged ----
    POTATO_GUN   ("Potato Gun",    Kind.WEAPON, "textures/items/potato_launcher.png"),

    // ---- armour: leather / iron / diamond × head, chest, legs, feet ----
    LEATHER_HELMET    ("Leather Cap",        Kind.ARMOR_HEAD,  "textures/items/leather_helmet.png"),
    LEATHER_CHESTPLATE("Leather Tunic",      Kind.ARMOR_CHEST, "textures/items/leather_chestplate.png"),
    LEATHER_LEGGINGS  ("Leather Pants",      Kind.ARMOR_LEGS,  "textures/items/leather_leggings.png"),
    LEATHER_BOOTS     ("Leather Boots",      Kind.ARMOR_FEET,  "textures/items/leather_boots.png"),

    IRON_HELMET       ("Iron Helmet",        Kind.ARMOR_HEAD,  "textures/items/iron_helmet.png"),
    IRON_CHESTPLATE   ("Iron Chestplate",    Kind.ARMOR_CHEST, "textures/items/iron_chestplate.png"),
    IRON_LEGGINGS     ("Iron Leggings",      Kind.ARMOR_LEGS,  "textures/items/iron_leggings.png"),
    IRON_BOOTS        ("Iron Boots",         Kind.ARMOR_FEET,  "textures/items/iron_boots.png"),

    DIAMOND_HELMET    ("Diamond Helmet",     Kind.ARMOR_HEAD,  "textures/items/diamond_helmet.png"),
    DIAMOND_CHESTPLATE("Diamond Chestplate", Kind.ARMOR_CHEST, "textures/items/diamond_chestplate.png"),
    DIAMOND_LEGGINGS  ("Diamond Leggings",   Kind.ARMOR_LEGS,  "textures/items/diamond_leggings.png"),
    DIAMOND_BOOTS     ("Diamond Boots",      Kind.ARMOR_FEET,  "textures/items/diamond_boots.png");

    /** Behaviour buckets; the four ARMOR_* kinds also map 1:1 to the four armour slots. */
    public enum Kind { WEAPON, ARMOR_HEAD, ARMOR_CHEST, ARMOR_LEGS, ARMOR_FEET }

    private final String displayName;
    private final Kind kind;
    private final String texturePath;

    ItemType(String displayName, Kind kind, String texturePath) {
        this.displayName = displayName;
        this.kind = kind;
        this.texturePath = texturePath;
    }

    public String displayName() { return displayName; }
    public Kind kind()          { return kind; }
    public String texturePath() { return texturePath; }

    public boolean isWeapon()   { return kind == Kind.WEAPON; }
    public boolean isSword()    { return kind == Kind.WEAPON && this != POTATO_GUN; }
    public boolean isArmor()    { return armorSlot() >= 0; }

    /** Armour-slot index this piece equips into (0=head, 1=chest, 2=legs, 3=feet), or -1 if not armour. */
    public int armorSlot() {
        switch (kind) {
            case ARMOR_HEAD:  return 0;
            case ARMOR_CHEST: return 1;
            case ARMOR_LEGS:  return 2;
            case ARMOR_FEET:  return 3;
            default:          return -1;
        }
    }
}
