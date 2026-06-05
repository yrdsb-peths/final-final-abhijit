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

    /** Distance from the grip origin to the gun muzzle (world units). */
    public static final float GUN_MUZZLE_Z = 0.48f;

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
    // Gun (unchanged blocky potato gun)
    // ------------------------------------------------------------------------------------------

    /** A blocky two-handed "potato gun": tan body, darker barrel, a drop-down grip. */
    public static Model buildGun() {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();

        part(mb, "body", new Color(0.62f, 0.49f, 0.32f, 1f),
            0f, 0f, -0.04f, 0.13f, 0.15f, 0.34f);
        part(mb, "barrel", new Color(0.34f, 0.28f, 0.20f, 1f),
            0f, 0.01f, -0.34f, 0.09f, 0.09f, 0.30f);
        part(mb, "muzzle", new Color(0.18f, 0.16f, 0.13f, 1f),
            0f, 0.01f, -0.47f, 0.11f, 0.11f, 0.04f);
        part(mb, "grip", new Color(0.30f, 0.22f, 0.14f, 1f),
            0f, -0.13f, 0.07f, 0.08f, 0.18f, 0.09f);
        part(mb, "stock", new Color(0.52f, 0.40f, 0.26f, 1f),
            0f, -0.06f, -0.20f, 0.10f, 0.08f, 0.12f);

        return mb.end();
    }

    /** Adds one axis-aligned coloured box centred at (cx,cy,cz) with size (w,h,d). */
    private static void part(ModelBuilder mb, String id, Color color,
                             float cx, float cy, float cz, float w, float h, float d) {
        Material mat = new Material(ColorAttribute.createDiffuse(color));
        MeshPartBuilder b = mb.part(id, GL20.GL_TRIANGLES,
            Usage.Position | Usage.Normal, mat);
        BoxShapeBuilder.build(b, cx, cy, cz, w, h, d);
    }
}
