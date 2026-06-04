package com.brawlgame.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.utils.Disposable;

/**
 * A faint reference grid on the y=0 plane so movement reads clearly in the otherwise empty void
 * (echoes the Blockbench-style grid in the reference renders). Built once as a GL_LINES model and
 * rendered unlit via vertex colours, with the two centre axes slightly brighter.
 */
public final class GridRenderer implements Disposable {

    private final Model model;
    private final ModelInstance instance;

    public GridRenderer(int halfExtent, float spacing) {
        Color line = new Color(0.15f, 0.16f, 0.19f, 1f);
        Color axis = new Color(0.27f, 0.29f, 0.35f, 1f);
        float max = halfExtent * spacing;

        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder b = mb.part("grid", GL20.GL_LINES,
            Usage.Position | Usage.ColorUnpacked,
            new Material(ColorAttribute.createDiffuse(Color.WHITE)));
        for (int i = -halfExtent; i <= halfExtent; i++) {
            float p = i * spacing;
            b.setColor(i == 0 ? axis : line);
            b.line(-max, 0f, p, max, 0f, p); // line parallel to X
            b.line(p, 0f, -max, p, 0f, max); // line parallel to Z
        }
        model = mb.end();
        instance = new ModelInstance(model);
    }

    /** Rendered without an Environment so it stays unlit (uses its vertex colours directly). */
    public void render(ModelBatch batch) {
        batch.render(instance);
    }

    @Override
    public void dispose() {
        model.dispose();
    }
}
