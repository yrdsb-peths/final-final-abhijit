package com.brawlgame.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.utils.Disposable;
import com.brawlgame.item.Inventory;
import com.brawlgame.item.ItemStack;
import com.brawlgame.item.ItemType;
import com.brawlgame.model.MinecraftPlayerModel;

/**
 * Renders worn armour on the player using Minecraft's own technique: each piece is a box the size of
 * the matching body part, slightly <b>inflated</b> and <b>parented to that part's rig bone</b>, so it
 * swings with the player's walk/idle/attack animation for free (no skinning, no imported rig). Every
 * box is unwrapped with the standard Minecraft armour UV layout and textured from the real 64×32
 * armour skins ({@code diamond_1.png}, {@code iron_1.png}, …); the texture's transparent regions are
 * <b>alpha-cut</b>, so a helmet leaves the face showing, boots show only at the ankle, and so on —
 * exactly like the vanilla armour layer.
 *
 * <p>Two layers, matching the vanilla skins: <b>layer 1</b> ({@code *_1.png}) carries the helmet,
 * chestplate (+ sleeves) and boots; <b>layer 2</b> ({@code *_2.png}) carries the leggings. Layer 2 is
 * inflated less so it tucks under the chestplate. The piece geometry mirrors {@link MinecraftPlayerModel}'s
 * boxes exactly and is transformed by {@code player.transform · bone.globalTransform} each frame, and the
 * diffuse texture is swapped per-frame to the equipped tier — "if it's iron, give it the iron look."
 */
public final class ArmorRenderer implements Disposable {

    private static final float PX = MinecraftPlayerModel.PX;

    /** Body-part box specs, mirroring {@link MinecraftPlayerModel} (min corner in px + size in px + skin UV). */
    private static final class Piece {
        final int slot;        // armour slot 0..3 this piece belongs to
        final String bone;     // rig node it parents to
        final int layer;       // 1 = *_1.png (helmet/chest/boots), 2 = *_2.png (leggings)
        final Model model;
        final ModelInstance instance;
        final TextureAttribute tex; // instance's own diffuse attr — swapped to the equipped tier
        Piece(int slot, String bone, int layer, Model model, ModelInstance instance, TextureAttribute tex) {
            this.slot = slot; this.bone = bone; this.layer = layer;
            this.model = model; this.instance = instance; this.tex = tex;
        }
    }

    private final Inventory inv;
    private final List<Piece> pieces = new ArrayList<>();
    /** tier key ("diamond"/"iron"/"leather") → {layer1 texture, layer2 texture}. */
    private final Map<String, Texture[]> tierTex = new HashMap<>();

    public ArmorRenderer(Inventory inv) {
        this.inv = inv;
        tierTex.put("diamond", new Texture[] { load("textures/armor/diamond_1.png"), load("textures/armor/diamond_2.png") });
        tierTex.put("iron",    new Texture[] { load("textures/armor/iron_1.png"),    load("textures/armor/iron_2.png") });
        tierTex.put("leather", new Texture[] { load("textures/armor/leather.png"),   load("textures/armor/leather_2.png") });
        Texture fallback = tierTex.get("diamond")[0];

        // slot, bone, layer, inflate(px), min-corner(px), size(px), skin UV — geometry matches the player's boxes.
        add(fallback, 0, MinecraftPlayerModel.HEAD,  1, 1.0f, -4, 0, -4, 8, 8, 8,  0,  0); // helmet
        add(fallback, 1, MinecraftPlayerModel.BODY,  1, 1.0f, -4, 0, -2, 8, 12, 4, 16, 16); // chestplate
        add(fallback, 1, MinecraftPlayerModel.ARM_R, 1, 1.0f, -2, -12, -2, 4, 12, 4, 40, 16); // right sleeve
        add(fallback, 1, MinecraftPlayerModel.ARM_L, 1, 1.0f, -2, -12, -2, 4, 12, 4, 40, 16); // left sleeve
        add(fallback, 2, MinecraftPlayerModel.BODY,  2, 0.5f, -4, 0, -2, 8, 12, 4, 16, 16); // leggings belt
        add(fallback, 2, MinecraftPlayerModel.LEG_R, 2, 0.5f, -2, -12, -2, 4, 12, 4, 0, 16); // right legging
        add(fallback, 2, MinecraftPlayerModel.LEG_L, 2, 0.5f, -2, -12, -2, 4, 12, 4, 0, 16); // left legging
        add(fallback, 3, MinecraftPlayerModel.LEG_R, 1, 1.0f, -2, -12, -2, 4, 12, 4, 0, 16); // right boot
        add(fallback, 3, MinecraftPlayerModel.LEG_L, 1, 1.0f, -2, -12, -2, 4, 12, 4, 0, 16); // left boot
    }

    private static Texture load(String path) {
        Texture t = new Texture(Gdx.files.internal(path));
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest); // pixel-art: no blur
        return t;
    }

    private void add(Texture fallback, int slot, String bone, int layer, float inflate,
                     int ox, int oy, int oz, int w, int h, int d, int u, int v) {
        // Cutout exactly like the player's overlay layer: libGDX's default shader only runs the alpha
        // test inside its blended path, so BOTH attributes are required — without blending the
        // transparent armour texels would render as opaque black. Lit by the scene like the player.
        Material mat = new Material(
            TextureAttribute.createDiffuse(fallback), // swapped to the equipped tier each frame
            new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
            FloatAttribute.createAlphaTest(0.5f),
            IntAttribute.createCullFace(GL20.GL_BACK));
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder b = mb.part("armor", GL20.GL_TRIANGLES,
            Usage.Position | Usage.Normal | Usage.TextureCoordinates, mat);
        box(b, ox * PX, oy * PX, oz * PX, w, h, d, u, v, inflate * PX);
        Model model = mb.end();
        ModelInstance instance = new ModelInstance(model);
        // ModelInstance copies the material, so swap the instance's own attribute (not the model's).
        TextureAttribute tex = (TextureAttribute) instance.materials.get(0).get(TextureAttribute.Diffuse);
        pieces.add(new Piece(slot, bone, layer, model, instance, tex));
    }

    private static String tierKey(ItemType armor) {
        String n = armor.name();
        if (n.startsWith("LEATHER")) return "leather";
        if (n.startsWith("IRON")) return "iron";
        return "diamond"; // diamond (and any gold) reuse the diamond/iron look
    }

    /**
     * Render every equipped piece over {@code player}. Call inside the main 3D pass, after the player's
     * animation has run (so the bones' {@code globalTransform}s are current for this frame).
     */
    public void render(ModelBatch batch, Environment env, ModelInstance player) {
        for (Piece p : pieces) {
            ItemStack equipped = inv.armor(p.slot);
            if (equipped == null) continue;
            Node bone = player.getNode(p.bone);
            if (bone == null) continue;
            Texture[] layers = tierTex.get(tierKey(equipped.type));
            if (layers == null) continue;
            p.tex.textureDescription.texture = layers[p.layer - 1];
            p.instance.transform.set(player.transform).mul(bone.globalTransform);
            batch.render(p.instance, env);
        }
    }

    // ---- textured box unwrap (legacy 64×32 armour layout) — ported from MinecraftPlayerModel ----

    /** Min corner (ox,oy,oz) in world units, (w,h,d) in px (geometry + UV), (u,v) skin origin, inflate per side. */
    private static void box(MeshPartBuilder bld, float ox, float oy, float oz,
                            int w, int h, int d, int u, int v, float inflate) {
        float x0 = ox - inflate, x1 = ox + w * PX + inflate;
        float y0 = oy - inflate, y1 = oy + h * PX + inflate;
        float z0 = oz - inflate, z1 = oz + d * PX + inflate;
        face(bld, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0, 0, 0, -1, u + d, v + d, w, h);        // front (-Z)
        face(bld, x1, y1, z1, x0, y1, z1, x0, y0, z1, x1, y0, z1, 0, 0, 1, u + 2 * d + w, v + d, w, h); // back (+Z)
        face(bld, x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1, -1, 0, 0, u, v + d, d, h);            // right (-X)
        face(bld, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0, 1, 0, 0, u + d + w, v + d, d, h);     // left (+X)
        face(bld, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0, u + d, v, w, d);             // top (+Y)
        face(bld, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, u + d + w, v, w, d);        // bottom (-Y)
    }

    private static final VertexInfo A0 = new VertexInfo(), A1 = new VertexInfo(),
                                    A2 = new VertexInfo(), A3 = new VertexInfo();

    private static void face(MeshPartBuilder bld,
                             float tlx, float tly, float tlz, float trx, float try_, float trz,
                             float brx, float bry, float brz, float blx, float bly, float blz,
                             float nx, float ny, float nz,
                             int up, int vp, int wp, int hp) {
        float uMin = up / 64f, uMax = (up + wp) / 64f; // armour skins are 64 wide …
        float vMin = vp / 32f, vMax = (vp + hp) / 32f; // … and 32 tall (legacy layout)
        A0.setPos(tlx, tly, tlz).setNor(nx, ny, nz).setUV(uMin, vMin).setCol(Color.WHITE);
        A1.setPos(trx, try_, trz).setNor(nx, ny, nz).setUV(uMax, vMin).setCol(Color.WHITE);
        A2.setPos(brx, bry, brz).setNor(nx, ny, nz).setUV(uMax, vMax).setCol(Color.WHITE);
        A3.setPos(blx, bly, blz).setNor(nx, ny, nz).setUV(uMin, vMax).setCol(Color.WHITE);
        bld.rect(A0, A1, A2, A3);
    }

    @Override
    public void dispose() {
        for (Piece p : pieces) p.model.dispose();
        for (Texture[] layers : tierTex.values()) {
            for (Texture t : layers) t.dispose();
        }
    }
}
