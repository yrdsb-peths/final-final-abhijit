package com.brawlgame.model;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/**
 * Weapon models used by the player. The sword is loaded from the real {@code minecraft_swords} glTF
 * asset (Sketchfab) — its mesh is read straight out of {@code scene.bin} and baked into the engine's
 * held-item convention (blade down local <b>-Z</b>, tip at {@code z = -tipZ}, broad faces on ±Y,
 * grip near the origin), so the existing held-weapon placement and swoosh-trail sampling keep working
 * unchanged.
 */
public final class WeaponModels {

    /** The muzzle (front of the flared barrel) in the gun model's LOCAL space — projectiles spawn here. */
    public static final float GUN_MUZZLE_X = 0f;
    public static final float GUN_MUZZLE_Y = 0.02f;
    public static final float GUN_MUZZLE_Z = -0.63f; // forward is -Z; flared-mouth tip

    // ---- sword bake (raw glTF model → held-item local space) ----
    private static final String SWORD_GLTF = "models/minecraft_swords/scene.gltf";
    /** Yaw (°, about Y) that rotates the model's -45° diagonal blade onto -Z, tip toward -Z (per PCA). */
    private static final float SWORD_BAKE_YAW = 45f;
    /** Target world length of the blade (the raw model is ~141 units corner-to-corner). */
    private static final float SWORD_TARGET_LEN = 1.15f;
    /** How far behind the origin (+Z) the pommel sits, so the fist grips just ahead of it. */
    private static final float SWORD_GRIP_BACK = 0.06f;

    private WeaponModels() {}

    public enum SwordVariant { WOOD, STONE, IRON, GOLD, DIAMOND }

    /** glTF material name carrying each variant's real texture (node names in the asset are shuffled). */
    private static String materialFor(SwordVariant v) {
        switch (v) {
            case WOOD:  return "Wood_Sword";
            case STONE: return "Stone_Sword";
            case IRON:  return "Iron_Sword";
            case GOLD:  return "Gold_Sword";
            default:    return "Diamond_Sword";
        }
    }

    public static final class SwordAsset implements Disposable {
        public final Model model;
        public final Texture texture;
        public final float tipZ;

        private SwordAsset(Model model, Texture texture, float tipZ) {
            this.model = model;
            this.texture = texture;
            this.tipZ = tipZ;
        }

        @Override
        public void dispose() {
            model.dispose();
            texture.dispose();
        }
    }

    public static SwordAsset buildSword() {
        return buildSword(SwordVariant.DIAMOND);
    }

    /** Loads + bakes one sword variant from the glTF asset (selected by its material/texture). */
    public static SwordAsset buildSword(SwordVariant variant) {
        return loadGltfSword(SWORD_GLTF, materialFor(variant));
    }

    // ------------------------------------------------------------------------------------------
    // glTF sword loader
    // ------------------------------------------------------------------------------------------

    private static SwordAsset loadGltfSword(String gltfPath, String materialName) {
        FileHandle gltfFile = Gdx.files.internal(gltfPath);
        JsonValue root = new JsonReader().parse(gltfFile);

        // Binary buffer (single .bin), little-endian per the glTF spec.
        String binUri = root.get("buffers").get(0).getString("uri");
        ByteBuffer bb = ByteBuffer.wrap(gltfFile.parent().child(binUri).readBytes())
            .order(ByteOrder.LITTLE_ENDIAN);

        // Find the mesh whose primitive's material has the requested name (node names are shuffled).
        JsonValue materials = root.get("materials");
        JsonValue meshes = root.get("meshes");
        int matIndex = -1;
        JsonValue prim = null;
        for (int i = 0; i < meshes.size; i++) {
            JsonValue p = meshes.get(i).get("primitives").get(0);
            int m = p.getInt("material");
            if (materialName.equals(materials.get(m).getString("name"))) { matIndex = m; prim = p; break; }
        }
        if (prim == null) throw new IllegalStateException("sword material not found: " + materialName);

        JsonValue attrs = prim.get("attributes");
        float[] pos = readFloats(root, bb, attrs.getInt("POSITION"), 3);
        float[] nor = readFloats(root, bb, attrs.getInt("NORMAL"), 3);
        float[] uv  = readFloats(root, bb, attrs.getInt("TEXCOORD_0"), 2);
        int[] idx   = readIndices(root, bb, prim.getInt("indices"));
        int vcount = pos.length / 3;

        bakeToHeldConvention(pos, nor, vcount);
        float tipZ = -minComponent(pos, vcount, 2); // most-negative Z after bake = the tip distance

        // Texture (these are tiny flat-colour PNGs; nearest keeps the blocky look).
        int texIndex = materials.get(matIndex).get("pbrMetallicRoughness").get("baseColorTexture").getInt("index");
        int imgIndex = root.get("textures").get(texIndex).getInt("source");
        String imgUri = root.get("images").get(imgIndex).getString("uri");
        Texture texture = new Texture(gltfFile.parent().child(imgUri));
        texture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);

        Material material = new Material(
            TextureAttribute.createDiffuse(texture),
            IntAttribute.createCullFace(GL20.GL_NONE)); // double-sided, robust to the asset's winding

        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder part = mb.part("sword", GL20.GL_TRIANGLES,
            Usage.Position | Usage.Normal | Usage.TextureCoordinates, material);
        VertexInfo info = new VertexInfo();
        short[] ids = new short[vcount];
        for (int i = 0; i < vcount; i++) {
            int p = i * 3, u = i * 2;
            info.setPos(pos[p], pos[p + 1], pos[p + 2])
                .setNor(nor[p], nor[p + 1], nor[p + 2])
                .setUV(uv[u], uv[u + 1])
                .setCol(Color.WHITE);
            ids[i] = part.vertex(info);
        }
        for (int i = 0; i < idx.length; i += 3) {
            part.triangle(ids[idx[i]], ids[idx[i + 1]], ids[idx[i + 2]]);
        }

        return new SwordAsset(mb.end(), texture, tipZ);
    }

    /**
     * Rotates the raw model so the diagonal blade lies on -Z (tip toward -Z), uniformly scales it to
     * {@link #SWORD_TARGET_LEN}, and translates it so it's centred on X/Y with the pommel just behind
     * the origin (+Z). Operates in place on {@code pos}/{@code nor}.
     */
    private static void bakeToHeldConvention(float[] pos, float[] nor, int vcount) {
        double ya = Math.toRadians(SWORD_BAKE_YAW);
        float cy = (float) Math.cos(ya), sy = (float) Math.sin(ya);
        // Rotate about Y (libGDX convention: x' = x cos + z sin; z' = -x sin + z cos).
        for (int i = 0; i < vcount; i++) {
            int p = i * 3;
            float x = pos[p], z = pos[p + 2];
            pos[p]     = x * cy + z * sy;
            pos[p + 2] = -x * sy + z * cy;
            float nx = nor[p], nz = nor[p + 2];
            nor[p]     = nx * cy + nz * sy;
            nor[p + 2] = -nx * sy + nz * cy;
        }
        // Bounding box after rotation.
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < vcount; i++) {
            int p = i * 3;
            minX = Math.min(minX, pos[p]);     maxX = Math.max(maxX, pos[p]);
            minY = Math.min(minY, pos[p + 1]); maxY = Math.max(maxY, pos[p + 1]);
            minZ = Math.min(minZ, pos[p + 2]); maxZ = Math.max(maxZ, pos[p + 2]);
        }
        float scale = SWORD_TARGET_LEN / (maxZ - minZ);
        float cx = (minX + maxX) * 0.5f * scale;     // centre X
        float cyc = (minY + maxY) * 0.5f * scale;    // centre Y (thickness)
        float backZ = maxZ * scale - SWORD_GRIP_BACK; // pommel (max Z) → +SWORD_GRIP_BACK
        for (int i = 0; i < vcount; i++) {
            int p = i * 3;
            pos[p]     = pos[p] * scale - cx;
            pos[p + 1] = pos[p + 1] * scale - cyc;
            pos[p + 2] = pos[p + 2] * scale - backZ;
            // Uniform scale + translation don't change normal direction; just renormalise.
            float nx = nor[p], ny = nor[p + 1], nz = nor[p + 2];
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 1e-6f) { nor[p] = nx / len; nor[p + 1] = ny / len; nor[p + 2] = nz / len; }
        }
    }

    private static float minComponent(float[] v, int count, int comp) {
        float min = Float.MAX_VALUE;
        for (int i = 0; i < count; i++) min = Math.min(min, v[i * 3 + comp]);
        return min;
    }

    /** Reads a float accessor (VEC2/VEC3), honouring its bufferView offset + stride. */
    private static float[] readFloats(JsonValue root, ByteBuffer bb, int accIndex, int comps) {
        JsonValue acc = root.get("accessors").get(accIndex);
        int count = acc.getInt("count");
        int accOff = acc.getInt("byteOffset", 0);
        JsonValue bv = root.get("bufferViews").get(acc.getInt("bufferView"));
        int bvOff = bv.getInt("byteOffset", 0);
        int stride = bv.getInt("byteStride", comps * 4);
        float[] out = new float[count * comps];
        for (int i = 0; i < count; i++) {
            int base = bvOff + accOff + i * stride;
            for (int c = 0; c < comps; c++) out[i * comps + c] = bb.getFloat(base + c * 4);
        }
        return out;
    }

    /** Reads a scalar index accessor (u32 / u16 / u8) into ints. */
    private static int[] readIndices(JsonValue root, ByteBuffer bb, int accIndex) {
        JsonValue acc = root.get("accessors").get(accIndex);
        int count = acc.getInt("count");
        int ct = acc.getInt("componentType"); // 5125=u32, 5123=u16, 5121=u8
        int accOff = acc.getInt("byteOffset", 0);
        JsonValue bv = root.get("bufferViews").get(acc.getInt("bufferView"));
        int bvOff = bv.getInt("byteOffset", 0);
        int comp = ct == 5125 ? 4 : ct == 5123 ? 2 : 1;
        int stride = bv.getInt("byteStride", comp);
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            int base = bvOff + accOff + i * stride;
            if (ct == 5125) out[i] = bb.getInt(base);
            else if (ct == 5123) out[i] = bb.getShort(base) & 0xFFFF;
            else out[i] = bb.get(base) & 0xFF;
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------
    // Potato gun — a detailed, multi-part textured voxel launcher (iron barrel + dark-oak stock)
    // ------------------------------------------------------------------------------------------

    /** A built gun: the model plus the textures it owns (disposed together). */
    public static final class GunAsset implements Disposable {
        public final Model model;
        public final Texture iron, wood, gold;

        private GunAsset(Model model, Texture iron, Texture wood, Texture gold) {
            this.model = model;
            this.iron = iron;
            this.wood = wood;
            this.gold = gold;
        }

        @Override
        public void dispose() {
            model.dispose();
            iron.dispose();
            wood.dispose();
            gold.dispose();
        }
    }

    /**
     * Builds the heavy Minecraft-style potato launcher from textured voxel parts, matching the
     * official toy: a flared iron blunderbuss barrel (stepped horn muzzle), a dark-oak-plank receiver
     * with a chest/hopper loader on top, brass (gold-block) banding straps, and a drop-down grip.
     * Forward is -Z; the flared mouth tip is at {@link #GUN_MUZZLE_Z}. Each face maps its texture 0..1.
     */
    public static GunAsset buildGun() {
        // Dark forged metal (not the light iron_block, which read as white under the daylight rig).
        Texture iron = blockTex("textures/blocks/netherite_block.png");
        Texture wood = blockTex("textures/blocks/dark_oak_planks.png");
        Texture gold = blockTex("textures/blocks/gold_block.png");
        long attrs = Usage.Position | Usage.Normal | Usage.TextureCoordinates;

        ModelBuilder mb = new ModelBuilder();
        mb.begin();

        // Iron: barrel neck + a 3-ring flared horn muzzle (widening toward the front).
        MeshPartBuilder ironP = mb.part("iron", GL20.GL_TRIANGLES, attrs, gunMat(iron));
        box(ironP, 0f, 0.02f, -0.26f, 0.10f, 0.10f, 0.34f); // barrel neck
        box(ironP, 0f, 0.02f, -0.46f, 0.16f, 0.16f, 0.06f); // flare ring 1
        box(ironP, 0f, 0.02f, -0.53f, 0.24f, 0.24f, 0.07f); // flare ring 2
        box(ironP, 0f, 0.02f, -0.60f, 0.34f, 0.34f, 0.06f); // flared mouth (muzzle)

        // Dark-oak: receiver body, chest/hopper on top, drop-down grip.
        MeshPartBuilder woodP = mb.part("wood", GL20.GL_TRIANGLES, attrs, gunMat(wood));
        box(woodP, 0f, 0.00f, 0.00f, 0.16f, 0.18f, 0.28f); // receiver body
        box(woodP, 0f, 0.17f, 0.00f, 0.18f, 0.14f, 0.18f); // chest / hopper loader
        box(woodP, 0f, -0.16f, 0.10f, 0.08f, 0.20f, 0.10f); // trigger grip

        // Brass banding straps.
        MeshPartBuilder goldP = mb.part("gold", GL20.GL_TRIANGLES, attrs, gunMat(gold));
        box(goldP, 0f, 0.02f, -0.12f, 0.13f, 0.13f, 0.03f); // body→barrel collar
        box(goldP, 0f, 0.02f, -0.30f, 0.12f, 0.12f, 0.03f); // mid-barrel band
        box(goldP, 0f, 0.215f, 0.00f, 0.19f, 0.03f, 0.19f); // chest top strap
        box(goldP, 0f, 0.125f, 0.00f, 0.19f, 0.03f, 0.19f); // chest bottom strap

        return new GunAsset(mb.end(), iron, wood, gold);
    }

    private static Material gunMat(Texture tex) {
        return new Material(TextureAttribute.createDiffuse(tex), IntAttribute.createCullFace(GL20.GL_BACK));
    }

    /**
     * The potato projectile mesh: a small 3D box (0.24 × 0.24 × 0.36, longer down the Z/travel axis —
     * 40% smaller than the old box) textured with a clean procedural potato skin (no black borders).
     * The caller owns {@code tex}'s lifecycle.
     */
    public static Model buildPotatoBox(Texture tex) {
        Material mat = new Material("potato",
            TextureAttribute.createDiffuse(tex), IntAttribute.createCullFace(GL20.GL_BACK));
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder b = mb.part("potato", GL20.GL_TRIANGLES,
            Usage.Position | Usage.Normal | Usage.TextureCoordinates, mat);
        box(b, 0f, 0f, 0f, 0.24f, 0.24f, 0.36f);
        return mb.end();
    }

    /**
     * Procedurally builds the potato skin: a solid tan base (edge-to-edge, so no black sides) with a
     * subtle vertical shade and a few darker "eye" speckles. Replaces the old photo PNG. Caller owns it.
     */
    public static Texture buildPotatoTexture() {
        int size = 16;
        com.badlogic.gdx.graphics.Pixmap pm =
            new com.badlogic.gdx.graphics.Pixmap(size, size, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
        for (int y = 0; y < size; y++) {
            // Gentle top-light → bottom-shade gradient across the tan base.
            float t = y / (float) (size - 1);
            float r = MathUtils.lerp(0.82f, 0.60f, t);
            float g = MathUtils.lerp(0.64f, 0.45f, t);
            float bl = MathUtils.lerp(0.40f, 0.27f, t);
            for (int x = 0; x < size; x++) pm.drawPixel(x, y, Color.rgba8888(r, g, bl, 1f));
        }
        // A handful of darker-brown eyes/speckles for a spud-like look.
        int eye = Color.rgba8888(0.40f, 0.28f, 0.16f, 1f);
        int[][] eyes = {{4, 5}, {10, 4}, {6, 11}, {12, 10}, {3, 12}};
        for (int[] e : eyes) pm.drawPixel(e[0], e[1], eye);

        Texture tex = new Texture(pm);
        tex.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        pm.dispose();
        return tex;
    }

    private static Texture blockTex(String path) {
        Texture t = new Texture(Gdx.files.internal(path));
        t.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        return t;
    }

    // Reusable vertex scratch for the textured-box helper.
    private static final VertexInfo G0 = new VertexInfo();
    private static final VertexInfo G1 = new VertexInfo();
    private static final VertexInfo G2 = new VertexInfo();
    private static final VertexInfo G3 = new VertexInfo();

    /** Adds one axis-aligned textured box centred at (cx,cy,cz), each face mapping the texture 0..1. */
    private static void box(MeshPartBuilder b, float cx, float cy, float cz, float w, float h, float d) {
        float x0 = cx - w * 0.5f, x1 = cx + w * 0.5f;
        float y0 = cy - h * 0.5f, y1 = cy + h * 0.5f;
        float z0 = cz - d * 0.5f, z1 = cz + d * 0.5f;
        gface(b, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0, 0, 0, -1); // front -Z
        gface(b, x1, y1, z1, x0, y1, z1, x0, y0, z1, x1, y0, z1, 0, 0,  1); // back +Z
        gface(b, x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1, -1, 0, 0); // left -X
        gface(b, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0, 1, 0, 0);  // right +X
        gface(b, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0);  // top +Y
        gface(b, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0); // bottom -Y
    }

    private static void gface(MeshPartBuilder b,
                              float ax, float ay, float az, float bx, float by, float bz,
                              float ccx, float ccy, float ccz, float dx, float dy, float dz,
                              float nx, float ny, float nz) {
        G0.setPos(ax, ay, az).setNor(nx, ny, nz).setUV(0f, 0f).setCol(Color.WHITE);
        G1.setPos(bx, by, bz).setNor(nx, ny, nz).setUV(1f, 0f).setCol(Color.WHITE);
        G2.setPos(ccx, ccy, ccz).setNor(nx, ny, nz).setUV(1f, 1f).setCol(Color.WHITE);
        G3.setPos(dx, dy, dz).setNor(nx, ny, nz).setUV(0f, 1f).setCol(Color.WHITE);
        b.rect(G0, G1, G2, G3);
    }
}
