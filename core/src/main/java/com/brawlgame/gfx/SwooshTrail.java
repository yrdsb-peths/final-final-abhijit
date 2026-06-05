package com.brawlgame.gfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

/**
 * The Minecraft-Dungeons sword "swoosh": a translucent ribbon that traces the path of the blade's
 * tip during a swing. Each frame the swing samples a (tip, base) edge; we keep a short rolling
 * history and stitch consecutive edges into a triangle strip. The ribbon is drawn additively with
 * a head-to-tail alpha falloff and a global fade so it whips out and dissolves in ~0.25s.
 *
 * <p>Rendered with its own tiny unlit shader after the main 3D pass: depth-tested against the scene
 * (so it sits in the world) but with depth writes off and additive blending, giving the glow look.
 */
public final class SwooshTrail implements Disposable {

    private static final int MAX_SAMPLES = 14;
    private static final float MAX_AGE = 0.26f;   // seconds a sample survives
    private static final Color HEAD = new Color(0.75f, 0.92f, 1f, 0.85f); // bright cyan-white

    private static final class Sample {
        final Vector3 tip = new Vector3();
        final Vector3 base = new Vector3();
        float age;
        boolean used;
    }

    private final Sample[] ring = new Sample[MAX_SAMPLES];
    private int head = 0;     // index of the most-recent sample
    private int count = 0;

    private final Mesh mesh;
    private final ShaderProgram shader;
    private final float[] verts;  // (x,y,z,packedColor) per vertex
    private static final int FLOATS_PER_VERT = 4;

    public SwooshTrail() {
        for (int i = 0; i < ring.length; i++) ring[i] = new Sample();

        // Two triangles (6 verts) bridge each pair of consecutive samples.
        int maxVerts = (MAX_SAMPLES - 1) * 6;
        verts = new float[maxVerts * FLOATS_PER_VERT];
        mesh = new Mesh(false, maxVerts, 0,
            new VertexAttribute(Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
            new VertexAttribute(Usage.ColorPacked, 4, ShaderProgram.COLOR_ATTRIBUTE));

        shader = new ShaderProgram(VERT, FRAG);
        if (!shader.isCompiled()) {
            throw new IllegalStateException("SwooshTrail shader: " + shader.getLog());
        }
    }

    /** Record the blade edge for this frame (called while a swing is in its active window). */
    public void addSample(Vector3 tip, Vector3 base) {
        head = (head + 1) % MAX_SAMPLES;
        Sample s = ring[head];
        s.tip.set(tip);
        s.base.set(base);
        s.age = 0f;
        s.used = true;
        if (count < MAX_SAMPLES) count++;
    }

    public void update(float delta) {
        for (Sample s : ring) {
            if (!s.used) continue;
            s.age += delta;
            if (s.age >= MAX_AGE) s.used = false;
        }
    }

    public boolean isEmpty() {
        for (Sample s : ring) if (s.used) return false;
        return true;
    }

    /** Draws the ribbon. Call after the main ModelBatch pass, before any 2D HUD. */
    public void render(Camera camera) {
        // Walk the ring newest→oldest, building an ordered list of live samples.
        int n = 0;
        int[] order = orderScratch;
        for (int i = 0; i < MAX_SAMPLES; i++) {
            int idx = (head - i + MAX_SAMPLES) % MAX_SAMPLES;
            if (ring[idx].used) order[n++] = idx; else break;
        }
        if (n < 2) return;

        int v = 0;
        for (int i = 0; i < n - 1; i++) {
            Sample a = ring[order[i]];      // newer
            Sample b = ring[order[i + 1]];  // older
            float ca = strength(i, n, a.age);
            float cb = strength(i + 1, n, b.age);
            float colA = packed(ca);
            float colB = packed(cb);

            // Quad a.tip, a.base, b.base, b.tip → two triangles.
            v = put(v, a.tip, colA);
            v = put(v, a.base, colA);
            v = put(v, b.base, colB);

            v = put(v, b.base, colB);
            v = put(v, b.tip, colB);
            v = put(v, a.tip, colA);
        }
        int vertCount = v / FLOATS_PER_VERT;
        if (vertCount == 0) return;

        mesh.setVertices(verts, 0, v);

        boolean depth = Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);                       // glow shouldn't occlude the scene
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE); // additive

        shader.bind();
        shader.setUniformMatrix("u_proj", camera.combined);
        mesh.render(shader, GL20.GL_TRIANGLES, 0, vertCount);

        Gdx.gl.glDepthMask(true);
        if (!depth) Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private final int[] orderScratch = new int[MAX_SAMPLES];

    /** Brightness for a sample: fades along the trail (head bright) and by age. */
    private float strength(int posFromHead, int n, float age) {
        float along = 1f - (posFromHead / (float) n);   // 1 at head → ~0 at tail
        float byAge = 1f - (age / MAX_AGE);
        float k = along * byAge;
        return k < 0f ? 0f : k;
    }

    private float packed(float k) {
        return Color.toFloatBits(HEAD.r, HEAD.g, HEAD.b, HEAD.a * k);
    }

    private int put(int v, Vector3 p, float col) {
        verts[v] = p.x;
        verts[v + 1] = p.y;
        verts[v + 2] = p.z;
        verts[v + 3] = col;
        return v + FLOATS_PER_VERT;
    }

    @Override
    public void dispose() {
        mesh.dispose();
        shader.dispose();
    }

    private static final String VERT =
        "attribute vec3 " + ShaderProgram.POSITION_ATTRIBUTE + ";\n" +
        "attribute vec4 " + ShaderProgram.COLOR_ATTRIBUTE + ";\n" +
        "uniform mat4 u_proj;\n" +
        "varying vec4 v_color;\n" +
        "void main() {\n" +
        "  v_color = " + ShaderProgram.COLOR_ATTRIBUTE + ";\n" +
        "  gl_Position = u_proj * vec4(" + ShaderProgram.POSITION_ATTRIBUTE + ", 1.0);\n" +
        "}";

    private static final String FRAG =
        "#ifdef GL_ES\nprecision mediump float;\n#endif\n" +
        "varying vec4 v_color;\n" +
        "void main() {\n" +
        "  gl_FragColor = v_color;\n" +
        "}";
}
