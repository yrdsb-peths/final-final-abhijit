package com.brawlgame.entity;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.Disposable;
import com.brawlgame.item.ItemStack;

/**
 * An interactive chest sitting on the floor. No chest glTF ships with the project, so the model is
 * built procedurally from textured voxels — a plank body + lid + a brass latch — sized to
 * {@value #SIZE} blocks ({@code 14/16}) so it reads slightly smaller than a full 1×1 block.
 *
 * <p><b>Hover highlight:</b> {@link #updateHover} casts the local player's camera→cursor pick ray at
 * the chest's bounding box and toggles a subtle emissive brighten. This is purely a client-side render
 * flag driven by <i>this</i> machine's cursor, so it is inherently local-only — it never reflects any
 * other player/enemy's aim. <b>Interaction:</b> {@link #canInteract} gates the {@code R} keypress on
 * being hovered <i>and</i> within {@value #DEFAULT_RANGE} blocks; the screen then opens the chest UI
 * over {@link #slots()}.
 */
public final class ChestEntity implements Disposable {

    /** Chest footprint/height in blocks (14/16 of a full block). */
    public static final float SIZE = 14f / 16f;
    public static final float DEFAULT_RANGE = 3f;
    public static final int SLOT_COUNT = 27; // 3 rows × 9, classic single chest

    private static final float BASE_H = 0.625f, LID_H = SIZE - 0.625f;

    private final Model model;
    private final ModelInstance instance;
    private final Vector3 pos = new Vector3();
    private final BoundingBox bounds = new BoundingBox();
    private final ItemStack[] slots = new ItemStack[SLOT_COUNT];

    private final ColorAttribute hoverGlow = ColorAttribute.createEmissive(0.28f, 0.28f, 0.30f, 1f);
    private boolean hovered;

    public ChestEntity(Texture wood, Texture gold, float x, float z) {
        long attrs = Usage.Position | Usage.Normal | Usage.TextureCoordinates;
        Material woodMat = new Material(TextureAttribute.createDiffuse(wood), IntAttribute.createCullFace(GL20.GL_BACK));
        Material goldMat = new Material(TextureAttribute.createDiffuse(gold), IntAttribute.createCullFace(GL20.GL_BACK));

        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder body = mb.part("chest", GL20.GL_TRIANGLES, attrs, woodMat);
        BoxShapeBuilder.build(body, 0f, BASE_H * 0.5f, 0f, SIZE, BASE_H, SIZE);              // base
        BoxShapeBuilder.build(body, 0f, BASE_H + LID_H * 0.5f, 0f, SIZE, LID_H, SIZE);        // lid
        MeshPartBuilder latch = mb.part("latch", GL20.GL_TRIANGLES, attrs, goldMat);
        BoxShapeBuilder.build(latch, 0f, BASE_H, -SIZE * 0.5f, 0.16f, 0.2f, 0.06f);           // front latch
        model = mb.end();

        instance = new ModelInstance(model);
        pos.set(x, 0f, z);
        instance.transform.setToTranslation(pos);
        bounds.set(new Vector3(pos.x - SIZE * 0.5f, pos.y, pos.z - SIZE * 0.5f),
                   new Vector3(pos.x + SIZE * 0.5f, pos.y + SIZE, pos.z + SIZE * 0.5f));
    }

    /** Cast the local player's camera→cursor ray; brighten the chest if it's under the crosshair. */
    public void updateHover(Camera camera, int screenX, int screenY) {
        Ray ray = camera.getPickRay(screenX, screenY);
        setHovered(Intersector.intersectRayBoundsFast(ray, bounds));
    }

    private void setHovered(boolean h) {
        if (h == hovered) return;
        hovered = h;
        Material m = instance.materials.get(0); // the plank body
        if (h) m.set(hoverGlow); else m.remove(ColorAttribute.Emissive);
    }

    public boolean isHovered() { return hovered; }

    public boolean inRange(Vector3 playerPos, float range) {
        float dx = playerPos.x - pos.x, dz = playerPos.z - pos.z;
        return dx * dx + dz * dz <= range * range;
    }

    /** True when the player is aiming at this chest AND standing within {@code range} blocks. */
    public boolean canInteract(Vector3 playerPos, float range) {
        return hovered && inRange(playerPos, range);
    }

    public ItemStack[] slots() { return slots; }
    public Vector3 position()  { return pos; }

    public void render(ModelBatch batch, Environment env) {
        batch.render(instance, env);
    }

    @Override
    public void dispose() {
        model.dispose(); // textures are owned by the caller
    }
}
