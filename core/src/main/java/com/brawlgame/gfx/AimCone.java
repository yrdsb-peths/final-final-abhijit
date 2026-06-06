package com.brawlgame.gfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

/**
 * The Brawl-Stars-style ground aiming reticle: a semi-transparent glowing white sector drawn flat
 * on the floor in front of the player, with a bright outline and a few concentric "sonar" arcs. Its
 * shape is set per weapon — wide+short for the sword, long+narrow for the gun, small for fists — and
 * it's oriented to the player's facing so it always points down the aim line.
 *
 * <p>Drawn additively just above the floor (depth-tested, depth-write off) so it reads as projected
 * light rather than a solid decal. Local space: forward = -Z, apex at the origin, flat on y=0.
 */
public final class AimCone implements Disposable {

    private static final int SEG = 30;   // arc subdivisions
    private static final float Y = 0.03f; // hover above ground to avoid z-fighting
    private static final int FPV = 4;     // floats per vertex: x,y,z,packedColor

    private static final int RUNGS = 4;   // ladder cross-lines for the rect indicator

    private final Mesh fill;   // GL_TRIANGLES filled sector
    private final Mesh lines;  // GL_LINES rim + edges + concentric arcs
    private int fillVerts, lineVerts;

    private final Mesh rectFill;  // GL_TRIANGLES filled rectangle (gun)
    private final Mesh rectLines; // GL_LINES side rails + ladder rungs (gun)
    private int rectFillVerts, rectLineVerts;

    private final ShaderProgram shader;
    private final Matrix4 mvp = new Matrix4();
    private final Matrix4 modelM = new Matrix4();

    private float curHalf = -1f, curRange = -1f;
    private float curRectHalf = -1f, curRectRange = -1f;

    public AimCone() {
        fill = mesh(SEG * 3);
        lines = mesh(SEG * 2 + 4 + 3 * SEG * 2);
        rectFill = mesh(6);                       // two triangles
        rectLines = mesh(4 + RUNGS * 2);          // two side rails + ladder rungs
        shader = new ShaderProgram(VERT, FRAG);
        if (!shader.isCompiled()) throw new IllegalStateException("AimCone shader: " + shader.getLog());
    }

    private static Mesh mesh(int maxVerts) {
        return new Mesh(false, maxVerts, 0,
            new VertexAttribute(Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
            new VertexAttribute(Usage.ColorPacked, 4, ShaderProgram.COLOR_ATTRIBUTE));
    }

    /** Rebuild the geometry only when the weapon's shape (half-angle / range) changes. */
    private void configure(float halfDeg, float range) {
        if (halfDeg == curHalf && range == curRange) return;
        curHalf = halfDeg;
        curRange = range;

        final float fillApex = Color.toFloatBits(1f, 1f, 1f, 0.22f);
        final float fillEdge = Color.toFloatBits(0.8f, 0.9f, 1f, 0.04f);
        final float rimCol = Color.toFloatBits(1f, 1f, 1f, 0.85f);
        final float arcCol = Color.toFloatBits(0.85f, 0.92f, 1f, 0.5f);

        float[] f = new float[SEG * 3 * FPV];
        float[] l = new float[(SEG * 2 + 4 + 3 * SEG * 2) * FPV];
        int fi = 0, li = 0;
        float half = halfDeg * MathUtils.degreesToRadians;

        for (int i = 0; i < SEG; i++) {
            float a0 = -half + 2f * half * (i / (float) SEG);
            float a1 = -half + 2f * half * ((i + 1) / (float) SEG);
            float x0 = MathUtils.sin(a0) * range, z0 = -MathUtils.cos(a0) * range;
            float x1 = MathUtils.sin(a1) * range, z1 = -MathUtils.cos(a1) * range;

            fi = put(f, fi, 0f, 0f, fillApex);   // apex
            fi = put(f, fi, x0, z0, fillEdge);
            fi = put(f, fi, x1, z1, fillEdge);

            li = put(l, li, x0, z0, rimCol);     // outer rim
            li = put(l, li, x1, z1, rimCol);
        }
        // two straight edges, apex → rim ends
        li = put(l, li, 0f, 0f, rimCol);
        li = put(l, li, MathUtils.sin(-half) * range, -MathUtils.cos(-half) * range, rimCol);
        li = put(l, li, 0f, 0f, rimCol);
        li = put(l, li, MathUtils.sin(half) * range, -MathUtils.cos(half) * range, rimCol);
        // concentric sonar arcs
        for (int k = 1; k <= 3; k++) {
            float r = range * (0.35f + 0.2f * k);
            for (int i = 0; i < SEG; i++) {
                float a0 = -half + 2f * half * (i / (float) SEG);
                float a1 = -half + 2f * half * ((i + 1) / (float) SEG);
                li = put(l, li, MathUtils.sin(a0) * r, -MathUtils.cos(a0) * r, arcCol);
                li = put(l, li, MathUtils.sin(a1) * r, -MathUtils.cos(a1) * r, arcCol);
            }
        }

        fillVerts = fi / FPV;
        lineVerts = li / FPV;
        fill.setVertices(f, 0, fi);
        lines.setVertices(l, 0, li);
    }

    public void render(Camera camera, float px, float pz, float facingDeg, float halfDeg, float range) {
        configure(halfDeg, range);

        modelM.setToRotation(Vector3.Y, facingDeg).setTranslation(px, Y, pz);
        mvp.set(camera.combined).mul(modelM);

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE); // additive

        shader.bind();
        shader.setUniformMatrix("u_proj", mvp);
        fill.render(shader, GL20.GL_TRIANGLES, 0, fillVerts);
        lines.render(shader, GL20.GL_LINES, 0, lineVerts);

        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * Rebuild the rectangular gun reticle only when its shape (half-width / range) changes. A long,
     * narrow strip projecting straight forward along -Z, flat on y=0. The colour tapers from a bright
     * near end to alpha 0 exactly at the far (range) end so it vanishes cleanly at max range.
     */
    private void configureRect(float halfWidth, float range) {
        if (halfWidth == curRectHalf && range == curRectRange) return;
        curRectHalf = halfWidth;
        curRectRange = range;

        // Fill: brighter near the player, fully transparent at the far cutoff.
        final float fillNear = Color.toFloatBits(1f, 1f, 1f, 0.22f);
        final float fillFar = Color.toFloatBits(0.8f, 0.9f, 1f, 0f); // alpha 0 at maxRange
        // Rails: bright rim that also fades out to 0 at the far end (no hard bright bar).
        final float railNear = Color.toFloatBits(1f, 1f, 1f, 0.85f);
        final float railFar = Color.toFloatBits(0.85f, 0.92f, 1f, 0f);

        float[] f = new float[6 * FPV];
        float[] l = new float[(4 + RUNGS * 2) * FPV];
        int fi = 0, li = 0;

        // Forward is -Z; the far edge sits at z = -range, the near edge at z = 0.
        float nz = 0f, fz = -range;

        // Two triangles forming the strip (near-left, near-right, far-left, far-right).
        fi = put(f, fi, -halfWidth, nz, fillNear);
        fi = put(f, fi,  halfWidth, nz, fillNear);
        fi = put(f, fi,  halfWidth, fz, fillFar);
        fi = put(f, fi, -halfWidth, nz, fillNear);
        fi = put(f, fi,  halfWidth, fz, fillFar);
        fi = put(f, fi, -halfWidth, fz, fillFar);

        // Side rails (both fade to 0 at the far end).
        li = put(l, li, -halfWidth, nz, railNear);
        li = put(l, li, -halfWidth, fz, railFar);
        li = put(l, li,  halfWidth, nz, railNear);
        li = put(l, li,  halfWidth, fz, railFar);

        // Ladder rungs across the strip, dimming toward the cutoff (skip a rung exactly at range).
        for (int k = 1; k <= RUNGS; k++) {
            float t = k / (float) (RUNGS + 1);          // 0..1 along the length
            float z = -range * t;
            float a = 0.5f * (1f - t);                   // fades to ~0 near the far end
            float rung = Color.toFloatBits(0.85f, 0.92f, 1f, a);
            li = put(l, li, -halfWidth, z, rung);
            li = put(l, li,  halfWidth, z, rung);
        }

        rectFillVerts = fi / FPV;
        rectLineVerts = li / FPV;
        rectFill.setVertices(f, 0, fi);
        rectLines.setVertices(l, 0, li);
    }

    /**
     * Draw the long, narrow gun reticle: a straight strip of width {@code 2*halfWidth} projecting
     * forward along the aim line, fading cleanly to alpha 0 at {@code range} (max range).
     */
    public void renderRect(Camera camera, float px, float pz, float facingDeg, float halfWidth, float range) {
        configureRect(halfWidth, range);

        modelM.setToRotation(Vector3.Y, facingDeg).setTranslation(px, Y, pz);
        mvp.set(camera.combined).mul(modelM);

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE); // additive

        shader.bind();
        shader.setUniformMatrix("u_proj", mvp);
        rectFill.render(shader, GL20.GL_TRIANGLES, 0, rectFillVerts);
        rectLines.render(shader, GL20.GL_LINES, 0, rectLineVerts);

        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /** Append one ground vertex (local y = 0; world height comes from the model matrix). */
    private int put(float[] a, int i, float x, float z, float col) {
        a[i] = x; a[i + 1] = 0f; a[i + 2] = z; a[i + 3] = col;
        return i + FPV;
    }

    @Override
    public void dispose() {
        fill.dispose();
        lines.dispose();
        rectFill.dispose();
        rectLines.dispose();
        shader.dispose();
    }

    private static final String VERT =
        "attribute vec3 " + ShaderProgram.POSITION_ATTRIBUTE + ";\n" +
        "attribute vec4 " + ShaderProgram.COLOR_ATTRIBUTE + ";\n" +
        "uniform mat4 u_proj;\n" +
        "varying vec4 v_color;\n" +
        "void main(){ v_color = " + ShaderProgram.COLOR_ATTRIBUTE + ";\n" +
        "  gl_Position = u_proj * vec4(" + ShaderProgram.POSITION_ATTRIBUTE + ", 1.0); }";

    private static final String FRAG =
        "#ifdef GL_ES\nprecision mediump float;\n#endif\n" +
        "varying vec4 v_color;\n" +
        "void main(){ gl_FragColor = v_color; }";
}
