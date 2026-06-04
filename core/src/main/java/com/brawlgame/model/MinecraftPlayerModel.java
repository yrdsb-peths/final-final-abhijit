package com.brawlgame.model;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;

/**
 * Builds the iconic Minecraft player as a procedurally generated, node-rigged {@link Model}
 * textured from a 64x64 skin PNG. Each animated limb is its own node whose origin sits at the
 * joint pivot, so the {@code PlayerAnimator} can swing it by setting the node's rotation.
 *
 * <p>Scale: 1 Minecraft pixel = 1/16 world unit, so the character is ~2 units (2 blocks) tall,
 * feet at y=0, facing -Z. World unit == one block, ready for the future block-world phase.
 *
 * <p>Geometry is built per-face with explicit UVs following the standard Minecraft skin "box
 * unwrap", so head/hat/body/limbs map to the correct regions of the skin. Base layer is opaque;
 * the second ("overlay") layer — hat, jacket, sleeves, pant cuffs — is inflated by 0.5px and
 * alpha-tested, exactly like the vanilla renderer.
 */
public final class MinecraftPlayerModel {

    /** One Minecraft texture pixel, in world units. */
    public static final float PX = 1f / 16f;

    /** Classic (Steve) = 4px arms; set true for slim (Alex) = 3px arms. */
    public static final boolean SLIM = false;

    // Node ids — shared with PlayerAnimator.
    public static final String HEAD = "head";
    public static final String BODY = "body";
    public static final String ARM_R = "armR";
    public static final String ARM_L = "armL";
    public static final String LEG_R = "legR";
    public static final String LEG_L = "legL";

    private MinecraftPlayerModel() {}

    public static Model build(Texture skin) {
        // Pixel-art skins MUST use nearest filtering or they turn into mush.
        skin.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        // Base layer: opaque, textured, lit by the scene environment.
        Material base = new Material("skin",
            TextureAttribute.createDiffuse(skin),
            IntAttribute.createCullFace(GL20.GL_BACK));

        // Overlay layer: cutout via alpha test (+ blend for soft edges), slightly inflated.
        Material overlay = new Material("skinOverlay",
            TextureAttribute.createDiffuse(skin),
            new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
            FloatAttribute.createAlphaTest(0.08f),
            IntAttribute.createCullFace(GL20.GL_BACK));

        final long attrs = Usage.Position | Usage.Normal | Usage.TextureCoordinates;
        final int armW = SLIM ? 3 : 4;        // arm width in px
        final float armCx = (4 + armW * 0.5f) * PX; // arm centre X = body half(4) + arm half
        final float inf = 0.5f * PX;          // overlay inflation per side

        ModelBuilder mb = new ModelBuilder();
        mb.begin();

        // ---- HEAD (+ hat) : pivot at neck (0, 1.5, 0); head grows upward from the pivot ----
        node(mb, HEAD, 0, 24 * PX, 0);
        box(mb.part(HEAD, GL20.GL_TRIANGLES, attrs, base),
            -4 * PX, 0, -4 * PX, 8, 8, 8, 0, 0, 0f);
        box(mb.part(HEAD + "Ov", GL20.GL_TRIANGLES, attrs, overlay),
            -4 * PX, 0, -4 * PX, 8, 8, 8, 32, 0, inf);

        // ---- BODY (+ jacket) : pivot at waist (0, 0.75, 0); torso grows upward ----
        node(mb, BODY, 0, 12 * PX, 0);
        box(mb.part(BODY, GL20.GL_TRIANGLES, attrs, base),
            -4 * PX, 0, -2 * PX, 8, 12, 4, 16, 16, 0f);
        box(mb.part(BODY + "Ov", GL20.GL_TRIANGLES, attrs, overlay),
            -4 * PX, 0, -2 * PX, 8, 12, 4, 16, 32, inf);

        // ---- RIGHT ARM (+ sleeve) : pivot at shoulder; arm hangs downward (-Y) ----
        node(mb, ARM_R, -armCx, 24 * PX, 0);
        box(mb.part(ARM_R, GL20.GL_TRIANGLES, attrs, base),
            -armW * 0.5f * PX, -12 * PX, -2 * PX, armW, 12, 4, 40, 16, 0f);
        box(mb.part(ARM_R + "Ov", GL20.GL_TRIANGLES, attrs, overlay),
            -armW * 0.5f * PX, -12 * PX, -2 * PX, armW, 12, 4, 40, 32, inf);

        // ---- LEFT ARM (+ sleeve) ----
        node(mb, ARM_L, armCx, 24 * PX, 0);
        box(mb.part(ARM_L, GL20.GL_TRIANGLES, attrs, base),
            -armW * 0.5f * PX, -12 * PX, -2 * PX, armW, 12, 4, 32, 48, 0f);
        box(mb.part(ARM_L + "Ov", GL20.GL_TRIANGLES, attrs, overlay),
            -armW * 0.5f * PX, -12 * PX, -2 * PX, armW, 12, 4, 48, 48, inf);

        // ---- RIGHT LEG (+ cuff) : pivot at hip; leg hangs downward ----
        node(mb, LEG_R, -2 * PX, 12 * PX, 0);
        box(mb.part(LEG_R, GL20.GL_TRIANGLES, attrs, base),
            -2 * PX, -12 * PX, -2 * PX, 4, 12, 4, 0, 16, 0f);
        box(mb.part(LEG_R + "Ov", GL20.GL_TRIANGLES, attrs, overlay),
            -2 * PX, -12 * PX, -2 * PX, 4, 12, 4, 0, 32, inf);

        // ---- LEFT LEG (+ cuff) ----
        node(mb, LEG_L, 2 * PX, 12 * PX, 0);
        box(mb.part(LEG_L, GL20.GL_TRIANGLES, attrs, base),
            -2 * PX, -12 * PX, -2 * PX, 4, 12, 4, 16, 48, 0f);
        box(mb.part(LEG_L + "Ov", GL20.GL_TRIANGLES, attrs, overlay),
            -2 * PX, -12 * PX, -2 * PX, 4, 12, 4, 0, 48, inf);

        Model model = mb.end();

        // Parent the head and both arms under the BODY node so that a torso lean (sprint / sneak)
        // carries them along naturally — exactly like the real Minecraft model. The legs stay under
        // the root so they keep swinging from the hips, unaffected by the torso lean.
        Node body = model.getNode(BODY);
        for (String childId : new String[] {HEAD, ARM_R, ARM_L}) {
            Node child = model.getNode(childId);
            model.nodes.removeValue(child, true);
            child.translation.sub(0f, 12f * PX, 0f); // re-express the pivot relative to the body pivot
            body.addChild(child);
        }
        return model;
    }

    /** Start a new rigged node whose origin (pivot) is at (x,y,z) in player space. */
    private static void node(ModelBuilder mb, String id, float x, float y, float z) {
        Node n = mb.node();
        n.id = id;
        n.translation.set(x, y, z); // composed into localTransform each frame (isAnimated=false)
    }

    // Reusable vertex scratch — rect() consumes these immediately.
    private static final VertexInfo T0 = new VertexInfo();
    private static final VertexInfo T1 = new VertexInfo();
    private static final VertexInfo T2 = new VertexInfo();
    private static final VertexInfo T3 = new VertexInfo();

    /**
     * Adds a textured box. (ox,oy,oz) is the min corner relative to the node pivot, in world units.
     * (w,h,d) are pixel dimensions used for BOTH geometry size and the skin UV unwrap. (u,v) is the
     * top-left of this box's region in the 64x64 skin. {@code inflate} grows the geometry on every
     * side (used for the 0.5px overlay layer) without changing the UVs.
     */
    private static void box(MeshPartBuilder b, float ox, float oy, float oz,
                            int w, int h, int d, int u, int v, float inflate) {
        float x0 = ox - inflate, x1 = ox + w * PX + inflate;
        float y0 = oy - inflate, y1 = oy + h * PX + inflate;
        float z0 = oz - inflate, z1 = oz + d * PX + inflate;

        // Front (-Z): region (u+d, v+d, w, h)
        face(b, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0, 0, 0, -1, u + d, v + d, w, h);
        // Back (+Z): region (u+2d+w, v+d, w, h)
        face(b, x1, y1, z1, x0, y1, z1, x0, y0, z1, x1, y0, z1, 0, 0, 1, u + 2 * d + w, v + d, w, h);
        // Right (-X): region (u, v+d, d, h)
        face(b, x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1, -1, 0, 0, u, v + d, d, h);
        // Left (+X): region (u+d+w, v+d, d, h)
        face(b, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0, 1, 0, 0, u + d + w, v + d, d, h);
        // Top (+Y): region (u+d, v, w, d)
        face(b, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0, u + d, v, w, d);
        // Bottom (-Y): region (u+d+w, v, w, d)
        face(b, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, u + d + w, v, w, d);
    }

    /**
     * Emits one quad. The four positions are given in texture order: top-left, top-right,
     * bottom-right, bottom-left (as the skin region reads upright). UV region is in skin pixels;
     * v is measured from the TOP of the image, matching how libGDX uploads the texture.
     */
    private static void face(MeshPartBuilder b,
                             float tlx, float tly, float tlz, float trx, float try_, float trz,
                             float brx, float bry, float brz, float blx, float bly, float blz,
                             float nx, float ny, float nz,
                             int up, int vp, int wp, int hp) {
        float uMin = up / 64f, uMax = (up + wp) / 64f;
        float vMin = vp / 64f, vMax = (vp + hp) / 64f;
        T0.setPos(tlx, tly, tlz).setNor(nx, ny, nz).setUV(uMin, vMin).setCol(Color.WHITE);
        T1.setPos(trx, try_, trz).setNor(nx, ny, nz).setUV(uMax, vMin).setCol(Color.WHITE);
        T2.setPos(brx, bry, brz).setNor(nx, ny, nz).setUV(uMax, vMax).setCol(Color.WHITE);
        T3.setPos(blx, bly, blz).setNor(nx, ny, nz).setUV(uMin, vMax).setCol(Color.WHITE);
        b.rect(T0, T1, T2, T3);
    }
}
