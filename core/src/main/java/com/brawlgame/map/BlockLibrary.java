package com.brawlgame.map;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.utils.Disposable;

/**
 * Owns every GPU resource the Map Maker renders blocks with: it loads the small set of pixel-art
 * block {@link Texture}s once, builds one shared {@link Model} per distinct {@link BlockType} the
 * active {@link Theme} can paint (plus the shared CHEST/SPAWN props), and hands out lightweight
 * {@link ModelInstance}s positioned in world space.
 *
 * <p>All geometry is built per-face with explicit UVs (the same hand-rolled cube technique as
 * {@code MinecraftPlayerModel}) so each face can map a different 16x16 texture — full blocks simply
 * use UVs spanning 0..1 instead of skin-sheet sub-regions. Every texture is sampled with
 * {@link Texture.TextureFilter#Nearest} so the art stays crisp; the floor texture additionally
 * wraps {@link Texture.TextureWrap#Repeat} so the ground plane can tile once per block.
 *
 * <p>Shared models are never mutated — {@link #instance} clones a fresh transform per call — so a
 * single {@code BlockLibrary} backs the whole board. Dispose it once on screen teardown.
 */
public final class BlockLibrary implements Disposable {

    /** World units per cell, mirrored from {@link GameMap#CELL} for local geometry math. */
    private static final float CELL = GameMap.CELL;
    /** Solid columns rise this many blocks (matches {@link GameMap#WALL_HEIGHT}). */
    private static final float WALL_H = GameMap.WALL_HEIGHT;

    /** Fixed seed so the jittered bush cluster is identical every run / every cell. */
    private static final long BUSH_SEED = 0x6B7553684C1Bf00dL;
    /** How many crossed billboard pairs make up one dense bush. */
    private static final int BUSH_CLUMPS = 16;

    private final Theme theme;

    // Loaded textures, keyed by file name so each PNG loads at most once. Disposed in dispose().
    private final Map<String, Texture> textures = new HashMap<>();
    // Shared models, keyed by BlockType (CHEST/SPAWN shared across themes). Disposed in dispose().
    private final Map<BlockType, Model> models = new HashMap<>();
    // Decorative cube models for the canyon scenery, keyed by "side|top|height". Disposed in dispose().
    private final Map<String, Model> decoModels = new HashMap<>();
    // The two checkerboard floor tile models (lazily built on first use). Disposed in dispose().
    private Model floorA, floorB;
    // Flat transparent minecart-rail decal (lazily built). Disposed in dispose().
    private Model railModel;
    // Small decorative cactus prop (lazily built). Disposed in dispose().
    private Model cactusModel;

    private final ModelBuilder mb = new ModelBuilder();

    public BlockLibrary(Theme theme) {
        this.theme = theme;
        buildModels();
    }

    // ------------------------------------------------------------------ public API

    /**
     * Returns a fresh {@link ModelInstance} of {@code type}'s shared model, translated so its base
     * sits at y=0 over cell centre ({@code worldX}, {@code worldZ}). Returns {@code null} for
     * {@link BlockType#ERASER} (the eraser is a tool, not a placeable block).
     */
    public ModelInstance instance(BlockType type, float worldX, float worldZ) {
        if (type == null || type.category() == BlockCategory.ERASER) return null;
        Model m = models.get(type);
        if (m == null) return null;
        ModelInstance inst = new ModelInstance(m);
        inst.transform.setToTranslation(worldX, 0f, worldZ);
        return inst;
    }

    /** Transparent blocks (BUSH/WATER/SPAWN) are drawn in the blended pass after the opaque pass. */
    public boolean isTransparent(BlockType type) {
        if (type == null) return false;
        switch (type.category()) {
            case BUSH:
            case WATER:
            case SPAWN:
                return true;
            default:
                return false;
        }
    }

    /**
     * One solid 1x1 base-layer floor block spanning y = -1 .. 0 at cell centre
     * ({@code worldX},{@code worldZ}); its top face sits at y=0 where placed blocks rest. The board is
     * paved in a two-tone checkerboard ({@code alt} selects the A/B texture): Sand = sand + cut
     * (smooth) sandstone; Deep Dark = polished deepslate + deepslate bricks. Being full cubes, they
     * read correctly from the top-down camera (a single up-facing quad was being back-face culled).
     */
    public ModelInstance floorTile(boolean alt, float worldX, float worldZ) {
        if (floorA == null) buildFloorTiles();
        ModelInstance inst = new ModelInstance(alt ? floorB : floorA);
        inst.transform.setToTranslation(worldX, -1f, worldZ); // base layer one block below the surface
        return inst;
    }

    private void buildFloorTiles() {
        boolean sand = theme != Theme.DEEP_DARK;
        String a = sand ? "sand.png" : "polished_deepslate.png";
        String b = sand ? "cut_sandstone.png" : "deepslate_bricks.png";
        floorA = buildCube(a, a, 1f);
        floorB = buildCube(b, b, 1f);
    }

    /**
     * A shared cube model that glows (emissive), for sculk and other lit decorations. Cached by
     * texture + height; the emissive tint makes it read bright even where the directional light
     * doesn't reach (in shadow).
     */
    public Model glowCube(String textureFile, float heightBlocks, Color emissive) {
        String key = "glow|" + textureFile + "|" + heightBlocks;
        Model m = decoModels.get(key);
        if (m == null) {
            Texture t = tex(textureFile);
            t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
            Material mat = new Material(textureFile,
                TextureAttribute.createDiffuse(t),
                ColorAttribute.createEmissive(emissive),
                IntAttribute.createCullFace(GL20.GL_BACK));
            m = buildCubeWith(mat, heightBlocks);
            decoModels.put(key, m);
        }
        return m;
    }

    /**
     * A shared opaque cube model {@code heightBlocks} tall (1x1 footprint, base at y=0) textured with
     * the named block PNG(s). Cached by side/top/height so the decorative canyon — which repeats a
     * handful of block kinds thousands of times — reuses meshes. The renderer translates a fresh
     * {@link ModelInstance} of it into place. Side faces tile vertically once per block.
     */
    public Model cubeModel(String sideFile, String topFile, float heightBlocks) {
        String key = sideFile + "|" + topFile + "|" + heightBlocks;
        Model m = decoModels.get(key);
        if (m == null) {
            m = buildCube(sideFile, topFile, heightBlocks);
            decoModels.put(key, m);
        }
        return m;
    }

    private Model buildCube(String sideFile, String topFile, float h) {
        Texture sideTex = tex(sideFile);
        sideTex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        Material sideMat = new Material(sideFile,
            TextureAttribute.createDiffuse(sideTex),
            IntAttribute.createCullFace(GL20.GL_BACK));
        Material topMat = topFile.equals(sideFile) ? sideMat : opaque(topFile);

        float hw = CELL * 0.5f, y0 = 0f, y1 = h;
        mb.begin();
        MeshPartBuilder sb = mb.part("cube_sides", GL20.GL_TRIANGLES,
            Usage.Position | Usage.Normal | Usage.TextureCoordinates, sideMat);
        faceTiled(sb, -hw, y1, -hw,  hw, y1, -hw,  hw, y0, -hw, -hw, y0, -hw, 0, 0, -1, 1, h);
        faceTiled(sb,  hw, y1,  hw, -hw, y1,  hw, -hw, y0,  hw,  hw, y0,  hw, 0, 0,  1, 1, h);
        faceTiled(sb, -hw, y1,  hw, -hw, y1, -hw, -hw, y0, -hw, -hw, y0,  hw, -1, 0, 0, 1, h);
        faceTiled(sb,  hw, y1, -hw,  hw, y1,  hw,  hw, y0,  hw,  hw, y0, -hw, 1, 0, 0, 1, h);
        MeshPartBuilder tb = mb.part("cube_caps", GL20.GL_TRIANGLES,
            Usage.Position | Usage.Normal | Usage.TextureCoordinates, topMat);
        faceTiled(tb, -hw, y1,  hw,  hw, y1,  hw,  hw, y1, -hw, -hw, y1, -hw, 0, 1, 0, 1, 1);
        faceTiled(tb, -hw, y0, -hw,  hw, y0, -hw,  hw, y0,  hw, -hw, y0,  hw, 0, -1, 0, 1, 1);
        return mb.end();
    }

    /** Builds a cube of {@code height} blocks using one material on every face (sides tile vertically). */
    private Model buildCubeWith(Material mat, float h) {
        float hw = CELL * 0.5f, y0 = 0f, y1 = h;
        mb.begin();
        MeshPartBuilder sb = mb.part("cube_sides", GL20.GL_TRIANGLES,
            Usage.Position | Usage.Normal | Usage.TextureCoordinates, mat);
        faceTiled(sb, -hw, y1, -hw,  hw, y1, -hw,  hw, y0, -hw, -hw, y0, -hw, 0, 0, -1, 1, h);
        faceTiled(sb,  hw, y1,  hw, -hw, y1,  hw, -hw, y0,  hw,  hw, y0,  hw, 0, 0,  1, 1, h);
        faceTiled(sb, -hw, y1,  hw, -hw, y1, -hw, -hw, y0, -hw, -hw, y0,  hw, -1, 0, 0, 1, h);
        faceTiled(sb,  hw, y1, -hw,  hw, y1,  hw,  hw, y0,  hw,  hw, y0, -hw, 1, 0, 0, 1, h);
        faceTiled(sb, -hw, y1,  hw,  hw, y1,  hw,  hw, y1, -hw, -hw, y1, -hw, 0, 1, 0, 1, 1);
        faceTiled(sb, -hw, y0, -hw,  hw, y0, -hw,  hw, y0,  hw, -hw, y0,  hw, 0, -1, 0, 1, 1);
        return mb.end();
    }

    /**
     * A flat, transparent minecart-rail decal: a single 1x1 up-facing quad textured with rail.png,
     * alpha-blended so the block beneath shows through the gaps (rather than rendering black). Sits
     * just above y=0 so it lays on top of the tier it's placed on; drawn in the transparent pass.
     */
    public Model railDecal() {
        if (railModel == null) {
            Material mat = new Material("rail",
                TextureAttribute.createDiffuse(tex("rail.png")),
                new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
                FloatAttribute.createAlphaTest(0.05f),
                IntAttribute.createCullFace(GL20.GL_NONE));
            float hw = CELL * 0.5f, y = 0.02f;
            mb.begin();
            MeshPartBuilder p = mb.part("rail", GL20.GL_TRIANGLES,
                Usage.Position | Usage.Normal | Usage.TextureCoordinates, mat);
            T0.setPos(-hw, y,  hw).setNor(0f, 1f, 0f).setUV(0f, 0f).setCol(Color.WHITE);
            T1.setPos( hw, y,  hw).setNor(0f, 1f, 0f).setUV(1f, 0f).setCol(Color.WHITE);
            T2.setPos( hw, y, -hw).setNor(0f, 1f, 0f).setUV(1f, 1f).setCol(Color.WHITE);
            T3.setPos(-hw, y, -hw).setNor(0f, 1f, 0f).setUV(0f, 1f).setCol(Color.WHITE);
            p.rect(T0, T1, T2, T3);
            railModel = mb.end();
        }
        return railModel;
    }

    /**
     * A small decorative cactus prop for sand-tier tops: a narrow box (0.7 wide, base at y=0,
     * ~1.1 tall — distinctly smaller than a 1x1 terrain block) textured with the vanilla cactus
     * textures (green spiky sides + cactus top), alpha-tested so its transparent edge pixels don't
     * render as black. Sits on the flat top face of the tier it's placed on.
     */
    public Model cactus() {
        if (cactusModel == null) {
            Material sideMat = new Material("cactusSide",
                TextureAttribute.createDiffuse(tex("cactus_side.png")),
                FloatAttribute.createAlphaTest(0.5f),
                IntAttribute.createCullFace(GL20.GL_BACK));
            Material topMat = new Material("cactusTop",
                TextureAttribute.createDiffuse(tex("cactus_top.png")),
                FloatAttribute.createAlphaTest(0.5f),
                IntAttribute.createCullFace(GL20.GL_BACK));

            float hw = 0.35f, y0 = 0f, y1 = 1.1f; // 0.7 wide, 1.1 tall
            mb.begin();
            MeshPartBuilder sb = mb.part("cactus_sides", GL20.GL_TRIANGLES,
                Usage.Position | Usage.Normal | Usage.TextureCoordinates, sideMat);
            faceTiled(sb, -hw, y1, -hw,  hw, y1, -hw,  hw, y0, -hw, -hw, y0, -hw, 0, 0, -1, 1, 1);
            faceTiled(sb,  hw, y1,  hw, -hw, y1,  hw, -hw, y0,  hw,  hw, y0,  hw, 0, 0,  1, 1, 1);
            faceTiled(sb, -hw, y1,  hw, -hw, y1, -hw, -hw, y0, -hw, -hw, y0,  hw, -1, 0, 0, 1, 1);
            faceTiled(sb,  hw, y1, -hw,  hw, y1,  hw,  hw, y0,  hw,  hw, y0, -hw, 1, 0, 0, 1, 1);
            MeshPartBuilder tb = mb.part("cactus_cap", GL20.GL_TRIANGLES,
                Usage.Position | Usage.Normal | Usage.TextureCoordinates, topMat);
            faceTiled(tb, -hw, y1,  hw,  hw, y1,  hw,  hw, y1, -hw, -hw, y1, -hw, 0, 1, 0, 1, 1);
            faceTiled(tb, -hw, y0, -hw,  hw, y0, -hw,  hw, y0,  hw, -hw, y0,  hw, 0, -1, 0, 1, 1);
            cactusModel = mb.end();
        }
        return cactusModel;
    }

    /** Loads (once) and returns a Nearest-filtered block texture — exposed for decorative scenery. */
    public Texture texture(String file) {
        return tex(file);
    }

    /** The texture used for {@code type}'s hotbar icon, or {@code null} if it has none. */
    public Texture icon(BlockType type) {
        if (type == null) return null;
        switch (type) {
            case SAND:                 return tex("sand.png");
            case SANDSTONE_WALL:       return tex("sandstone.png");
            case OAK_FENCE:            return tex("oak_planks.png");
            case WATER:                return tex("water_still.png");
            case BUSH_GREEN:
            case BUSH_YELLOW:
            case BUSH_BLUE:            return tex("tall_grass_top.png");
            case DEEPSLATE_TILE:       return tex("deepslate_tiles.png");
            case DEEPSLATE_BRICK_WALL: return tex("deepslate_bricks.png");
            case DARK_OAK_FENCE:       return tex("dark_oak_planks.png");
            case DARK_WATER:           return tex("water_still.png");
            case CHEST:                return tex("oak_planks.png");
            case SPAWN:
            case ERASER:
            default:                   return null;
        }
    }

    @Override
    public void dispose() {
        for (Model m : models.values()) m.dispose();
        models.clear();
        for (Model m : decoModels.values()) m.dispose();
        decoModels.clear();
        if (floorA != null) floorA.dispose();
        if (floorB != null) floorB.dispose();
        if (railModel != null) railModel.dispose();
        if (cactusModel != null) cactusModel.dispose();
        floorA = floorB = null;
        railModel = null;
        cactusModel = null;
        for (Texture t : textures.values()) t.dispose();
        textures.clear();
    }

    // ------------------------------------------------------------------ model construction

    /** Builds one shared model for every block type this theme can paint, plus shared props. */
    private void buildModels() {
        for (BlockType t : theme.palette()) {
            if (t == null || t.category() == BlockCategory.ERASER) continue;
            if (!models.containsKey(t)) models.put(t, buildModel(t));
        }
        // CHEST and SPAWN are shared specials guaranteed present in every theme palette, but build
        // them defensively in case a future palette omits one.
        if (!models.containsKey(BlockType.CHEST)) models.put(BlockType.CHEST, buildModel(BlockType.CHEST));
        if (!models.containsKey(BlockType.SPAWN)) models.put(BlockType.SPAWN, buildModel(BlockType.SPAWN));
    }

    private Model buildModel(BlockType t) {
        switch (t.category()) {
            case SOLID: return buildSolid(t);
            case FENCE: return buildFence(t);
            case WATER: return buildWater(t);
            case BUSH:  return buildBush(t);
            case CHEST: return buildChest(t);
            case SPAWN: return buildSpawn(t);
            default:    return buildSolid(t);
        }
    }

    // ---- SOLID: full 1x1 column, WALL_H blocks tall, textured per face ----

    private Model buildSolid(BlockType t) {
        String side, top;
        switch (t) {
            case SANDSTONE_WALL:       side = "sandstone.png";       top = "sandstone_top.png"; break;
            case DEEPSLATE_TILE:       side = "deepslate_tiles.png"; top = "deepslate_tiles.png"; break;
            case DEEPSLATE_BRICK_WALL: side = "deepslate_bricks.png";top = "deepslate_bricks.png"; break;
            case SAND:
            default:                   side = "sand.png";            top = "sand.png"; break;
        }
        // Side faces tile vertically (WALL_H copies); the texture must wrap to repeat cleanly.
        Texture sideTex = tex(side);
        sideTex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        Material sideMat = new Material(side,
            TextureAttribute.createDiffuse(sideTex),
            IntAttribute.createCullFace(GL20.GL_BACK));
        Material topMat  = top.equals(side) ? sideMat : opaque(top);

        float hw = CELL * 0.5f;
        float y0 = 0f, y1 = WALL_H;

        mb.begin();
        // Four sides share one material; the top (and bottom) may use a different one.
        MeshPartBuilder sb = mb.part("solid_sides", GL20.GL_TRIANGLES,
            Usage.Position | Usage.Normal | Usage.TextureCoordinates, sideMat);
        // Side faces tile the texture once per block of height so a 3-tall wall reads as 3 stones.
        faceTiled(sb, -hw, y1, -hw,  hw, y1, -hw,  hw, y0, -hw, -hw, y0, -hw, 0, 0, -1, 1, WALL_H); // front -Z
        faceTiled(sb,  hw, y1,  hw, -hw, y1,  hw, -hw, y0,  hw,  hw, y0,  hw, 0, 0,  1, 1, WALL_H); // back +Z
        faceTiled(sb, -hw, y1,  hw, -hw, y1, -hw, -hw, y0, -hw, -hw, y0,  hw, -1, 0, 0, 1, WALL_H); // left -X
        faceTiled(sb,  hw, y1, -hw,  hw, y1,  hw,  hw, y0,  hw,  hw, y0, -hw, 1, 0, 0, 1, WALL_H); // right +X

        MeshPartBuilder tb = mb.part("solid_caps", GL20.GL_TRIANGLES,
            Usage.Position | Usage.Normal | Usage.TextureCoordinates, topMat);
        faceTiled(tb, -hw, y1,  hw,  hw, y1,  hw,  hw, y1, -hw, -hw, y1, -hw, 0, 1, 0, 1, 1); // top +Y
        faceTiled(tb, -hw, y0, -hw,  hw, y0, -hw,  hw, y0,  hw, -hw, y0,  hw, 0, -1, 0, 1, 1); // bottom -Y
        return mb.end();
    }

    // ---- FENCE: thin post + two cross-rails, 1 block tall ----

    private Model buildFence(BlockType t) {
        String planks = (t == BlockType.DARK_OAK_FENCE) ? "dark_oak_planks.png" : "oak_planks.png";
        Material mat = opaque(planks);

        mb.begin();
        MeshPartBuilder p = mb.part("fence", GL20.GL_TRIANGLES,
            Usage.Position | Usage.Normal | Usage.TextureCoordinates, mat);

        float postHalf = 0.125f;            // 0.25 wide centred post
        box(p, -postHalf, 0f, -postHalf, postHalf, 1f, postHalf);

        // Two thin horizontal cross-rails spanning the cell along X and along Z, at upper/lower bars.
        float railHalf = 0.07f;             // rail thickness half-extent
        float span = CELL * 0.5f;           // rails reach the cell edges so neighbours connect
        float loY = 0.30f, hiY = 0.70f;
        // X-axis rails (run along X, thin in Z), upper and lower.
        box(p, -span, loY - railHalf, -railHalf, span, loY + railHalf, railHalf);
        box(p, -span, hiY - railHalf, -railHalf, span, hiY + railHalf, railHalf);
        // Z-axis rails (run along Z, thin in X), upper and lower.
        box(p, -railHalf, loY - railHalf, -span, railHalf, loY + railHalf, span);
        box(p, -railHalf, hiY - railHalf, -span, railHalf, hiY + railHalf, span);
        return mb.end();
    }

    // ---- WATER: thin flat tile near the surface, tinted + blended, top frame only ----

    private Model buildWater(BlockType t) {
        Texture water = tex("water_still.png");
        // water_still is an animated vertical strip; sample only the TOP 16x16 frame.
        float frameV = 16f / water.getHeight();

        Color tint = t.tint() != null ? t.tint() : new Color(0.2f, 0.45f, 0.95f, 0.78f);
        Material mat = new Material("water",
            TextureAttribute.createDiffuse(water),
            ColorAttribute.createDiffuse(tint),
            new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, tint.a),
            IntAttribute.createCullFace(GL20.GL_NONE));

        float hw = CELL * 0.5f, y = 0.1f; // WATER_HAZARD: a flat liquid tile barely above the floor
        mb.begin();
        MeshPartBuilder p = mb.part("water", GL20.GL_TRIANGLES,
            Usage.Position | Usage.Normal | Usage.TextureCoordinates, mat);
        T0.setPos(-hw, y,  hw).setNor(0f, 1f, 0f).setUV(0f, 0f).setCol(Color.WHITE);
        T1.setPos( hw, y,  hw).setNor(0f, 1f, 0f).setUV(1f, 0f).setCol(Color.WHITE);
        T2.setPos( hw, y, -hw).setNor(0f, 1f, 0f).setUV(1f, frameV).setCol(Color.WHITE);
        T3.setPos(-hw, y, -hw).setNor(0f, 1f, 0f).setUV(0f, frameV).setCol(Color.WHITE);
        p.rect(T0, T1, T2, T3);
        return mb.end();
    }

    // ---- BUSH: dense crossed-billboard cluster, alpha-tested + blended, double-sided ----

    private Model buildBush(BlockType t) {
        Texture bottom = tex("tall_grass_bottom.png");
        Texture top    = tex("tall_grass_top.png");
        Color tint = t.tint() != null ? t.tint() : Color.WHITE;

        Material matBottom = bushMat(bottom, tint);
        Material matTop    = bushMat(top, tint);

        mb.begin();
        MeshPartBuilder lower = mb.part("bush_lower", GL20.GL_TRIANGLES,
            Usage.Position | Usage.Normal | Usage.TextureCoordinates, matBottom);
        MeshPartBuilder upper = mb.part("bush_upper", GL20.GL_TRIANGLES,
            Usage.Position | Usage.Normal | Usage.TextureCoordinates, matTop);

        Random rng = new Random(BUSH_SEED);
        for (int i = 0; i < BUSH_CLUMPS; i++) {
            // Jitter each blade pair within the cell, leaving a small margin from the edges.
            float ox = (rng.nextFloat() - 0.5f) * 0.8f;
            float oz = (rng.nextFloat() - 0.5f) * 0.8f;
            float h = 1.0f + rng.nextFloat() * 0.4f;     // 1.0 .. 1.4 blocks tall
            float r = 0.30f + rng.nextFloat() * 0.12f;   // half-width of each quad
            float yaw = rng.nextFloat() * (float) Math.PI; // rotate the crossed pair

            crossedBlade(lower, upper, ox, oz, r, h, yaw);
        }
        return mb.end();
    }

    /**
     * Adds one crossed-billboard blade: two perpendicular vertical quads. The lower half goes to the
     * {@code lower} part (tall_grass_bottom), the upper half to {@code upper} (tall_grass_top). Yaw
     * spins the cross so the cluster doesn't look like a grid. Quads are emitted both winding-orders
     * so they show from both sides (cull is disabled on the material too).
     */
    private void crossedBlade(MeshPartBuilder lower, MeshPartBuilder upper,
                              float cx, float cz, float r, float h, float yaw) {
        float c = (float) Math.cos(yaw), s = (float) Math.sin(yaw);
        // Two in-plane axis directions, perpendicular to each other, in the XZ plane.
        float ax = c * r, az = s * r;     // first quad axis
        float bx = -s * r, bz = c * r;    // second quad axis (90 deg)

        verticalQuad(lower, upper, cx, cz, ax, az, h);
        verticalQuad(lower, upper, cx, cz, bx, bz, h);
    }

    /** A single vertical quad from (cx-ax,cz-az) to (cx+ax,cz+az), split lower/upper at mid height. */
    private void verticalQuad(MeshPartBuilder lower, MeshPartBuilder upper,
                              float cx, float cz, float ax, float az, float h) {
        float mid = h * 0.5f;
        float x0 = cx - ax, z0 = cz - az;
        float x1 = cx + ax, z1 = cz + az;
        // Lower half: y 0..mid, bottom texture.
        quadTwoSided(lower, x0, 0f, z0, x1, 0f, z1, x1, mid, z1, x0, mid, z0);
        // Upper half: y mid..h, top texture.
        quadTwoSided(upper, x0, mid, z0, x1, mid, z1, x1, h, z1, x0, h, z0);
    }

    /** Emits a quad (bl,br,tr,tl) twice with opposite winding so it is visible from both sides. */
    private void quadTwoSided(MeshPartBuilder p,
                              float blx, float bly, float blz, float brx, float bry, float brz,
                              float trx, float try_, float trz, float tlx, float tly, float tlz) {
        // Front winding (tl, tr, br, bl) — texture upright.
        T0.setPos(tlx, tly, tlz).setNor(0f, 0f, 1f).setUV(0f, 0f).setCol(Color.WHITE);
        T1.setPos(trx, try_, trz).setNor(0f, 0f, 1f).setUV(1f, 0f).setCol(Color.WHITE);
        T2.setPos(brx, bry, brz).setNor(0f, 0f, 1f).setUV(1f, 1f).setCol(Color.WHITE);
        T3.setPos(blx, bly, blz).setNor(0f, 0f, 1f).setUV(0f, 1f).setCol(Color.WHITE);
        p.rect(T0, T1, T2, T3);
        // Back winding (tr, tl, bl, br) — same UVs, reversed order so the other face shows.
        T0.setPos(trx, try_, trz).setNor(0f, 0f, -1f).setUV(1f, 0f).setCol(Color.WHITE);
        T1.setPos(tlx, tly, tlz).setNor(0f, 0f, -1f).setUV(0f, 0f).setCol(Color.WHITE);
        T2.setPos(blx, bly, blz).setNor(0f, 0f, -1f).setUV(0f, 1f).setCol(Color.WHITE);
        T3.setPos(brx, bry, brz).setNor(0f, 0f, -1f).setUV(1f, 1f).setCol(Color.WHITE);
        p.rect(T0, T1, T2, T3);
    }

    private Material bushMat(Texture tx, Color tint) {
        return new Material("bush",
            TextureAttribute.createDiffuse(tx),
            ColorAttribute.createDiffuse(tint),
            new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
            FloatAttribute.createAlphaTest(0.5f),
            IntAttribute.createCullFace(GL20.GL_NONE));
    }

    // ---- CHEST: small textured box prop ----

    private Model buildChest(BlockType t) {
        String planks = (theme == Theme.DEEP_DARK) ? "dark_oak_planks.png" : "oak_planks.png";
        Material mat = opaque(planks);

        float hw = 0.4f;       // 0.8 wide
        float h = 0.7f;        // 0.7 tall
        mb.begin();
        MeshPartBuilder p = mb.part("chest", GL20.GL_TRIANGLES,
            Usage.Position | Usage.Normal | Usage.TextureCoordinates, mat);
        box(p, -hw, 0f, -hw, hw, h, hw);
        return mb.end();
    }

    // ---- SPAWN: flat translucent vertex-coloured disc on the ground ----

    private Model buildSpawn(BlockType t) {
        Color tint = t.tint() != null ? t.tint() : new Color(0.95f, 0.2f, 0.25f, 0.7f);
        Material mat = new Material("spawn",
            ColorAttribute.createDiffuse(tint),
            new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, tint.a),
            IntAttribute.createCullFace(GL20.GL_NONE));

        float hw = CELL * 0.45f, y = 0.05f; // SPECIAL_PAD: a flat marker just above the floor
        mb.begin();
        MeshPartBuilder p = mb.part("spawn", GL20.GL_TRIANGLES,
            Usage.Position | Usage.Normal, mat);
        // No texture: a plain coloured quad sitting just above the floor.
        T0.setPos(-hw, y,  hw).setNor(0f, 1f, 0f).setCol(tint);
        T1.setPos( hw, y,  hw).setNor(0f, 1f, 0f).setCol(tint);
        T2.setPos( hw, y, -hw).setNor(0f, 1f, 0f).setCol(tint);
        T3.setPos(-hw, y, -hw).setNor(0f, 1f, 0f).setCol(tint);
        p.rect(T0, T1, T2, T3);
        return mb.end();
    }

    // ------------------------------------------------------------------ low-level geometry helpers

    // Reusable vertex scratch — rect() consumes these immediately.
    private final VertexInfo T0 = new VertexInfo();
    private final VertexInfo T1 = new VertexInfo();
    private final VertexInfo T2 = new VertexInfo();
    private final VertexInfo T3 = new VertexInfo();

    /**
     * Adds an axis-aligned textured box spanning the two given corners, each face mapping the whole
     * texture (UVs 0..1). Used for fence posts/rails and the chest prop.
     */
    private void box(MeshPartBuilder p,
                     float x0, float y0, float z0, float x1, float y1, float z1) {
        faceTiled(p, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0, 0, 0, -1, 1, 1); // front -Z
        faceTiled(p, x1, y1, z1, x0, y1, z1, x0, y0, z1, x1, y0, z1, 0, 0,  1, 1, 1); // back +Z
        faceTiled(p, x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1, -1, 0, 0, 1, 1); // left -X
        faceTiled(p, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0, 1, 0, 0, 1, 1);  // right +X
        faceTiled(p, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0, 1, 1);  // top +Y
        faceTiled(p, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, 1, 1); // bottom -Y
    }

    /**
     * Emits one quad with the four corners given in upright texture order (top-left, top-right,
     * bottom-right, bottom-left). {@code tileU}/{@code tileV} are how many times the texture repeats
     * across the quad (1 = map once; &gt;1 needs a Repeat-wrapped texture, used for tall wall sides).
     */
    private void faceTiled(MeshPartBuilder p,
                           float tlx, float tly, float tlz, float trx, float try_, float trz,
                           float brx, float bry, float brz, float blx, float bly, float blz,
                           float nx, float ny, float nz, float tileU, float tileV) {
        T0.setPos(tlx, tly, tlz).setNor(nx, ny, nz).setUV(0f, 0f).setCol(Color.WHITE);
        T1.setPos(trx, try_, trz).setNor(nx, ny, nz).setUV(tileU, 0f).setCol(Color.WHITE);
        T2.setPos(brx, bry, brz).setNor(nx, ny, nz).setUV(tileU, tileV).setCol(Color.WHITE);
        T3.setPos(blx, bly, blz).setNor(nx, ny, nz).setUV(0f, tileV).setCol(Color.WHITE);
        p.rect(T0, T1, T2, T3);
    }

    // ------------------------------------------------------------------ texture / material helpers

    /**
     * An opaque, lit, back-face-culled material textured by the named block PNG. An alpha test is
     * included so block textures with transparent border pixels (e.g. cactus, rails) discard those
     * texels instead of drawing them as black edges. Fully-opaque textures are unaffected.
     */
    private Material opaque(String file) {
        return new Material(file,
            TextureAttribute.createDiffuse(tex(file)),
            FloatAttribute.createAlphaTest(0.5f),
            IntAttribute.createCullFace(GL20.GL_BACK));
    }

    /** Loads (once) and returns a Nearest-filtered block texture from assets/textures/blocks/. */
    private Texture tex(String file) {
        Texture t = textures.get(file);
        if (t == null) {
            t = new Texture(Gdx.files.internal("textures/blocks/" + file));
            t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            textures.put(file, t);
        }
        return t;
    }
}
