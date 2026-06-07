package com.brawlgame.map;

import java.util.Random;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelCache;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.utils.Disposable;

/**
 * The non-editable, decorative world that frames the playable arena — Brawl-Stars-style canyon
 * scenery. A deep, multi-layered ring of stepped, undulating cliffs rises away from the map border,
 * dressed with static block props scattered on the tiers:
 *
 * <ul>
 *   <li><b>Sand:</b> rolling red/orange terracotta + sandstone cliffs decorated with cacti, skulls,
 *       mining crates, block cannons and wooden minecart-track beds.</li>
 *   <li><b>Deep Dark:</b> looming deepslate / tuff / obsidian caverns with glowing sculk, ancient
 *       cobbled ruins and chiseled stone structures.</li>
 * </ul>
 *
 * <p>It is generated once into a single {@link ModelCache} (it never changes) and replayed each
 * frame, both in the main lit pass ({@link #render}) and the shadow-caster depth pass
 * ({@link #renderCasters}). All geometry sits outside the grid, so it can never be painted on.
 */
public final class SceneryRenderer implements Disposable {

    /** How many cells deep the decorative ring extends beyond the arena border. */
    private static final int BAND = 9;
    private static final long SEED = 0xCA1FF00DL;
    private static final float PROP_CHANCE = 0.14f;
    /** Teal glow for sculk blocks. */
    private static final Color SCULK_GLOW = new Color(0.10f, 0.55f, 0.62f, 1f);

    private final ModelCache cache = new ModelCache();

    public SceneryRenderer(GameMap map, BlockLibrary lib) {
        boolean sand = map.theme() != Theme.DEEP_DARK;
        // Cliff block kinds, low tier -> high tier. {sideTexture, topTexture}.
        String[][] layers = sand
            ? new String[][] {{"red_terracotta.png", "red_terracotta.png"},
                              {"orange_terracotta.png", "orange_terracotta.png"},
                              {"sandstone.png", "sandstone_top.png"}}
            : new String[][] {{"deepslate.png", "deepslate_top.png"},
                              {"tuff.png", "tuff.png"},
                              {"obsidian.png", "obsidian.png"}};

        Random rng = new Random(SEED);
        int cols = map.cols(), rows = map.rows();

        cache.begin();
        for (int ec = -BAND; ec < cols + BAND; ec++) {
            for (int er = -BAND; er < rows + BAND; er++) {
                boolean insideX = ec >= 0 && ec < cols;
                boolean insideZ = er >= 0 && er < rows;
                if (insideX && insideZ) continue; // the editable arena (incl. its border)

                int dx = ec < 0 ? -ec : (ec >= cols ? ec - (cols - 1) : 0);
                int dz = er < 0 ? -er : (er >= rows ? er - (rows - 1) : 0);
                int d = Math.max(dx, dz);            // Chebyshev distance outside the arena, 1..BAND
                if (d < 1 || d > BAND) continue;

                // Undulating, stepped cliffs: grow with distance, roll with a low-frequency wave.
                float wave = 2.0f * (float) (Math.sin(ec * 0.33f) + Math.cos(er * 0.31f));
                float height = Math.max(1f, 1f + d * 1.1f + wave + (rng.nextFloat() < 0.3f ? 1f : 0f));

                int tier = (d - 1) * layers.length / BAND;
                if (tier >= layers.length) tier = layers.length - 1;
                // Occasional block swap so the rock face isn't uniform.
                if (rng.nextFloat() < 0.18f) tier = Math.min(layers.length - 1, tier + (rng.nextBoolean() ? 1 : -1));
                if (tier < 0) tier = 0;

                float wx = map.worldX(ec), wz = map.worldZ(er);
                add(lib.cubeModel(layers[tier][0], layers[tier][1], height), wx, 0f, wz);

                if (d >= 2 && rng.nextFloat() < PROP_CHANCE) {
                    if (sand) addSandProp(lib, rng, wx, height, wz);
                    else addDarkProp(lib, rng, wx, height, wz);
                }
            }
        }
        cache.end();
    }

    private void addSandProp(BlockLibrary lib, Random rng, float wx, float baseY, float wz) {
        switch (rng.nextInt(5)) {
            case 0: // small decorative cactus sitting on the tier's flat top (narrower than a block)
                add(lib.cactus(), wx, baseY, wz);
                break;
            case 1: // skull / bone block
                add(lib.cubeModel("bone_block_side.png", "bone_block_top.png", 1f), wx, baseY, wz);
                break;
            case 2: // mining crate
                add(lib.cubeModel("barrel_side.png", "barrel_top.png", 1f), wx, baseY, wz);
                break;
            case 3: // block cannon: dark base + barrel barrel on top
                add(lib.cubeModel("tnt_side.png", "tnt_top.png", 1f), wx, baseY, wz);
                add(lib.cubeModel("barrel_side.png", "barrel_top.png", 1f), wx, baseY + 1f, wz);
                break;
            default: // minecart rail laid flat on the tier (transparent — the block shows through)
                add(lib.railDecal(), wx, baseY, wz);
                break;
        }
    }

    private void addDarkProp(BlockLibrary lib, Random rng, float wx, float baseY, float wz) {
        switch (rng.nextInt(4)) {
            case 0: // glowing sculk
                add(lib.glowCube("sculk.png", 1f, SCULK_GLOW), wx, baseY, wz);
                break;
            case 1: // ancient ruin: cracked deepslate bricks, varied height
                add(lib.cubeModel("cracked_deepslate_bricks.png", "cracked_deepslate_bricks.png",
                    1f + rng.nextInt(3)), wx, baseY, wz);
                break;
            case 2: // cobbled rubble
                add(lib.cubeModel("cobbled_deepslate.png", "cobbled_deepslate.png", 1f), wx, baseY, wz);
                break;
            default: // chiseled structure pillar + sculk cap
                add(lib.cubeModel("chiseled_deepslate.png", "chiseled_deepslate.png", 2f), wx, baseY, wz);
                add(lib.glowCube("sculk_catalyst_side.png", 1f, SCULK_GLOW), wx, baseY + 2f, wz);
                break;
        }
    }

    private void add(Model model, float x, float y, float z) {
        ModelInstance inst = new ModelInstance(model);
        inst.transform.setToTranslation(x, y, z);
        cache.add(inst);
    }

    /** Replays the baked canyon geometry (lit). Caller owns {@code batch.begin/end}. */
    public void render(ModelBatch batch, Environment env) {
        batch.render(cache, env);
    }

    /** Replays the canyon as shadow casters (depth pass, no environment). */
    public void renderCasters(ModelBatch batch) {
        batch.render(cache);
    }

    @Override
    public void dispose() {
        cache.dispose();
    }
}
