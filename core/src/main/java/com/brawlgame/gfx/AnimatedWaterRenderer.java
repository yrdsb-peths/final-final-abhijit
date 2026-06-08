package com.brawlgame.gfx;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import com.brawlgame.map.BlockCategory;
import com.brawlgame.map.BlockType;
import com.brawlgame.map.GameMap;

/**
 * Dual-layer animated water: two semi-transparent ripple sheets cross-scroll with shifted UVs so the
 * surface reads as living fluid instead of a flat blue overlay.
 */
public final class AnimatedWaterRenderer implements Disposable {

    private static final float SCROLL_X1 = -0.18f, SCROLL_Y1 = -0.14f;
    private static final float SCROLL_X2 = 0.26f, SCROLL_Y2 = 0.22f;
    private static final float ALPHA1 = 0.40f, ALPHA2 = 0.25f;
    private static final float UV_SCALE1 = 1.0f, UV_SCALE2 = 1.35f;

    private final Texture waterTex;
    private final List<Tile> tiles = new ArrayList<>();
    private final ModelBuilder mb = new ModelBuilder();
    private final VertexInfo T0 = new VertexInfo(), T1 = new VertexInfo(), T2 = new VertexInfo(), T3 = new VertexInfo();

    private Model layer1, layer2;
    private ModelInstance inst1, inst2;
    private float offsetX, offsetY, offset2X, offset2Y;
    private float rebuildTimer;

    private static final class Tile {
        final float x, z;
        final Color tint;
        Tile(float x, float z, Color tint) { this.x = x; this.z = z; this.tint = tint; }
    }

    public AnimatedWaterRenderer(GameMap map) {
        waterTex = new Texture(Gdx.files.internal("textures/blocks/water_still.png"));
        waterTex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        waterTex.setWrap(TextureWrap.Repeat, TextureWrap.Repeat);

        float hw = GameMap.CELL * 0.5f;
        for (int c = 0; c < map.cols(); c++) {
            for (int r = 0; r < map.rows(); r++) {
                BlockType t = map.get(c, r);
                if (t == null || t.category() != BlockCategory.WATER) continue;
                Color tint = t.tint() != null ? t.tint() : Color.WHITE;
                tiles.add(new Tile(map.worldX(c), map.worldZ(r), tint));
            }
        }
        rebuildLayers(0f, 0f, 0f, 0f);
    }

    public void update(float delta) {
        offsetX += delta * SCROLL_X1;
        offsetY += delta * SCROLL_Y1;
        offset2X += delta * SCROLL_X2;
        offset2Y += delta * SCROLL_Y2;
        rebuildTimer += delta;
        if (rebuildTimer >= 0.07f) {
            rebuildTimer = 0f;
            rebuildLayers(offsetX % 2f, offsetY % 2f, offset2X % 2f, offset2Y % 2f);
        }
    }

    private void rebuildLayers(float ox, float oy, float ox2, float oy2) {
        if (layer1 != null) layer1.dispose();
        if (layer2 != null) layer2.dispose();

        layer1 = buildLayer("waterL1", ox, oy, UV_SCALE1, ALPHA1, SCROLL_X1, SCROLL_Y1);
        layer2 = buildLayer("waterL2", ox2, oy2, UV_SCALE2, ALPHA2, SCROLL_X2, SCROLL_Y2);
        inst1 = new ModelInstance(layer1);
        inst2 = new ModelInstance(layer2);
    }

    private Model buildLayer(String id, float uOff, float vOff, float uvScale, float alpha,
                             float scrollHintX, float scrollHintY) {
        Material mat = new Material(
            TextureAttribute.createDiffuse(waterTex),
            ColorAttribute.createDiffuse(Color.WHITE),
            new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, alpha),
            IntAttribute.createCullFace(GL20.GL_NONE));

        float hw = GameMap.CELL * 0.5f, y = 0.11f;
        mb.begin();
        MeshPartBuilder p = mb.part(id, GL20.GL_TRIANGLES,
            Usage.Position | Usage.Normal | Usage.TextureCoordinates, mat);

        for (Tile tile : tiles) {
            float u0 = uOff, v0 = vOff;
            float u1 = u0 + uvScale, v1 = v0 + uvScale;
            // Slight per-tile phase so neighbouring cells don't look tiled identically.
            float phase = (tile.x * scrollHintX + tile.z * scrollHintY) * 0.05f;
            u0 += phase; v0 += phase; u1 += phase; v1 += phase;

            T0.setPos(tile.x - hw, y, tile.z + hw).setNor(0f, 1f, 0f).setUV(u0, v0).setCol(tile.tint);
            T1.setPos(tile.x + hw, y, tile.z + hw).setNor(0f, 1f, 0f).setUV(u1, v0).setCol(tile.tint);
            T2.setPos(tile.x + hw, y, tile.z - hw).setNor(0f, 1f, 0f).setUV(u1, v1).setCol(tile.tint);
            T3.setPos(tile.x - hw, y, tile.z - hw).setNor(0f, 1f, 0f).setUV(u0, v1).setCol(tile.tint);
            p.rect(T0, T1, T2, T3);
        }
        return mb.end();
    }

    /** Draw both scrolling layers in the transparent world pass (after the floor, before characters). */
    public void render(ModelBatch batch, Environment env) {
        if (tiles.isEmpty()) return;
        batch.render(inst1, env);
        batch.render(inst2, env);
    }

    @Override
    public void dispose() {
        if (layer1 != null) layer1.dispose();
        if (layer2 != null) layer2.dispose();
        waterTex.dispose();
    }
}
