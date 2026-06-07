package com.brawlgame.gfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
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
 * Brawl-Stars-style ground highlight rings drawn flat on the floor beneath each character so they pop
 * from the background: a vibrant, slightly transparent filled disc with a bright rim — green for the
 * player and red for enemies. The player's ring also carries a small solid green "satellite" disc that
 * orbits the perimeter, gliding around the edge to point at the mouse cursor (the player's aim yaw).
 *
 * <p>The geometry is a single <b>unit</b> disc + rim (radius 1, on the XZ plane at y=0); each draw
 * scales/translates it via the model matrix and the colour is a shader uniform — so one set of meshes
 * serves every entity, the satellite, and any tint. Drawn just above the floor with the depth test on
 * but depth-writes off, so taller world geometry still occludes it and the 3D character models drawn
 * afterwards stand cleanly on top.
 */
public final class GroundIndicator implements Disposable {

    private static final int SEG = 48;            // circle subdivisions
    private static final float Y = 0.06f;         // hover above the floor to beat z-fighting
    private static final float RIM_INNER = 0.86f; // annulus rim: inner/outer radii (outer = 1)
    private static final float SAT_FRAC = 0.30f;  // satellite radius as a fraction of the main ring

    // Vibrant, slightly transparent palette (RGB; alphas applied per element below).
    private static final float PLAYER_R = 0.20f, PLAYER_G = 1.00f, PLAYER_B = 0.35f;
    private static final float ENEMY_R  = 1.00f, ENEMY_G  = 0.25f, ENEMY_B  = 0.25f;
    private static final float FILL_A = 0.30f, RIM_A = 0.90f, SAT_A = 0.95f;

    private final Mesh disc;   // GL_TRIANGLES filled unit disc
    private final Mesh rim;    // GL_TRIANGLES thick unit annulus outline
    private final int discVerts, rimVerts;

    private final ShaderProgram shader;
    private final Matrix4 mvp = new Matrix4();
    private final Matrix4 modelM = new Matrix4();

    public GroundIndicator() {
        float[] d = new float[SEG * 3 * 3];
        float[] r = new float[SEG * 6 * 3];
        int di = 0, ri = 0;
        for (int i = 0; i < SEG; i++) {
            float a0 = MathUtils.PI2 * (i / (float) SEG);
            float a1 = MathUtils.PI2 * ((i + 1) / (float) SEG);
            float x0 = MathUtils.cos(a0), z0 = MathUtils.sin(a0);
            float x1 = MathUtils.cos(a1), z1 = MathUtils.sin(a1);

            di = put(d, di, 0f, 0f);  di = put(d, di, x0, z0);  di = put(d, di, x1, z1);

            float ix0 = x0 * RIM_INNER, iz0 = z0 * RIM_INNER;
            float ix1 = x1 * RIM_INNER, iz1 = z1 * RIM_INNER;
            ri = put(r, ri, ix0, iz0); ri = put(r, ri, x0, z0);  ri = put(r, ri, x1, z1);
            ri = put(r, ri, ix0, iz0); ri = put(r, ri, x1, z1);  ri = put(r, ri, ix1, iz1);
        }

        disc = mesh(d.length / 3); disc.setVertices(d); discVerts = d.length / 3;
        rim  = mesh(r.length / 3); rim.setVertices(r);  rimVerts  = r.length / 3;

        shader = new ShaderProgram(VERT, FRAG);
        if (!shader.isCompiled()) throw new IllegalStateException("GroundIndicator shader: " + shader.getLog());
    }

    private static Mesh mesh(int verts) {
        return new Mesh(true, verts, 0, new VertexAttribute(Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE));
    }

    private int put(float[] a, int i, float x, float z) {
        a[i] = x; a[i + 1] = Y; a[i + 2] = z;
        return i + 3;
    }

    /**
     * Player highlight: green disc + rim, plus a small solid satellite circle orbiting the rim toward
     * {@code facingDeg} (the player's aim yaw). Forward at facing 0 is -Z, matching the player model,
     * so the satellite sits where the player is aiming:
     * {@code sat = centre + (-sin(yaw), -cos(yaw)) · radius}.
     */
    public void renderPlayer(Camera camera, float x, float z, float radius, float facingDeg) {
        ring(camera, x, z, radius, PLAYER_R, PLAYER_G, PLAYER_B);

        float satX = x - MathUtils.sinDeg(facingDeg) * radius;
        float satZ = z - MathUtils.cosDeg(facingDeg) * radius;
        solidDisc(camera, satX, satZ, radius * SAT_FRAC, PLAYER_R, PLAYER_G, PLAYER_B);
    }

    /** Enemy highlight: red disc + rim, no satellite. */
    public void renderEnemy(Camera camera, float x, float z, float radius) {
        ring(camera, x, z, radius, ENEMY_R, ENEMY_G, ENEMY_B);
    }

    /** Translucent filled disc + bright rim (the main character ring). */
    private void ring(Camera camera, float x, float z, float radius, float r, float g, float b) {
        bind(camera, x, z, radius);
        shader.setUniformf("u_color", r, g, b, FILL_A);
        disc.render(shader, GL20.GL_TRIANGLES, 0, discVerts);
        shader.setUniformf("u_color", r, g, b, RIM_A);
        rim.render(shader, GL20.GL_TRIANGLES, 0, rimVerts);
        unbind();
    }

    /** A small, near-solid filled disc (the orbiting satellite). */
    private void solidDisc(Camera camera, float x, float z, float radius, float r, float g, float b) {
        bind(camera, x, z, radius);
        shader.setUniformf("u_color", r, g, b, SAT_A);
        disc.render(shader, GL20.GL_TRIANGLES, 0, discVerts);
        shader.setUniformf("u_color", 1f, 1f, 1f, RIM_A);
        rim.render(shader, GL20.GL_TRIANGLES, 0, rimVerts);
        unbind();
    }

    private void bind(Camera camera, float x, float z, float radius) {
        // T · S of the unit disc. Y is baked into the mesh, so only X/Z scale by the radius.
        modelM.idt().translate(x, 0f, z).scale(radius, 1f, radius);
        mvp.set(camera.combined).mul(modelM);

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false); // don't occlude the characters drawn afterwards
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA); // straight alpha (not additive)

        shader.bind();
        shader.setUniformMatrix("u_proj", mvp);
    }

    private void unbind() {
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    @Override
    public void dispose() {
        disc.dispose();
        rim.dispose();
        shader.dispose();
    }

    private static final String VERT =
        "attribute vec3 " + ShaderProgram.POSITION_ATTRIBUTE + ";\n" +
        "uniform mat4 u_proj;\n" +
        "void main(){ gl_Position = u_proj * vec4(" + ShaderProgram.POSITION_ATTRIBUTE + ", 1.0); }";

    private static final String FRAG =
        "#ifdef GL_ES\nprecision mediump float;\n#endif\n" +
        "uniform vec4 u_color;\n" +
        "void main(){ gl_FragColor = u_color; }";
}
