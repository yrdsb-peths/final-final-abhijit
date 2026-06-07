package com.brawlgame.ui;

import java.util.EnumMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.utils.Disposable;
import com.brawlgame.item.ItemType;

/**
 * Lazily loads + caches the 2D icon {@link Texture} for each {@link ItemType}. Items with a texture
 * path load that PNG (nearest-filtered for the crisp Minecraft look); items without one (e.g. the
 * fist) get a generated flat-coloured square keyed to their {@link ItemType.Kind}, so the UI always
 * has something to draw. Owns every texture it creates — {@link #dispose()} frees the lot.
 */
public final class ItemIcons implements Disposable {

    private final Map<ItemType, Texture> cache = new EnumMap<>(ItemType.class);
    private Texture blankFallback;

    /** Always returns a usable icon (real sprite, or a generated coloured fallback). */
    public Texture get(ItemType type) {
        Texture t = cache.get(type);
        if (t != null) return t;
        t = load(type);
        cache.put(type, t);
        return t;
    }

    private Texture load(ItemType type) {
        String path = type.texturePath();
        if (path != null && Gdx.files.internal(path).exists()) {
            Texture tex = new Texture(Gdx.files.internal(path));
            tex.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            return tex;
        }
        return fallback(type);
    }

    private Texture fallback(ItemType type) {
        Color c;
        switch (type.kind()) {
            case WEAPON:     c = new Color(0.80f, 0.82f, 0.90f, 1f); break; // steel (e.g. potato gun)
            case ARMOR_HEAD:
            case ARMOR_CHEST:
            case ARMOR_LEGS:
            case ARMOR_FEET: c = new Color(0.40f, 0.85f, 0.90f, 1f); break; // diamond-ish
            default:         c = new Color(0.55f, 0.55f, 0.58f, 1f); break;
        }
        Pixmap pm = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        pm.setColor(c);
        pm.fillRectangle(2, 2, 12, 12);
        Texture tex = new Texture(pm);
        tex.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        pm.dispose();
        return tex;
    }

    @Override
    public void dispose() {
        for (Texture t : cache.values()) t.dispose();
        cache.clear();
        if (blankFallback != null) { blankFallback.dispose(); blankFallback = null; }
    }
}
