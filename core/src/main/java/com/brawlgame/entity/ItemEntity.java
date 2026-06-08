package com.brawlgame.entity;

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
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.brawlgame.item.ItemStack;
import com.brawlgame.item.ItemType;
import com.brawlgame.model.WeaponModels;

/**
 * A dropped item in the world. <b>Weapons</b> (swords, the potato gun) render as their real 3D voxel
 * model; every other item renders as a double-sided textured quad of its icon. Either way it is tossed
 * out in the player's facing direction with an upward impulse and a <b>gravity + bounce</b> arc so it
 * tumbles and settles on the floor, <b>spinning about the vertical axis</b> and bobbing once at rest —
 * exactly like a real Minecraft drop. Walk over it (after a short delay) to pick it back up.
 */
public final class ItemEntity implements Disposable {

    private static final float GRAVITY = 11f, BOUNCE = 0.45f, REST_Y = 0.32f;
    private static final float PICKUP_RADIUS = 0.8f, PICKUP_DELAY = 1.0f;
    private static final float TOSS_SPEED = 2.6f;

    private final ItemStack stack;
    private final boolean weapon;
    private final Disposable owned;      // the Model / SwordAsset / GunAsset to free
    private final ModelInstance instance;
    private final Vector3 pos = new Vector3();
    private float vx, vy, vz, spin, bob, age;
    private boolean resting;

    public ItemEntity(ItemStack stack, Texture icon, float x, float z, float facingDeg) {
        this.stack = stack;
        pos.set(x, 1.0f, z);
        // Toss forward (model faces -Z; world forward for a facing of 0 is -Z).
        float fr = facingDeg * MathUtils.degreesToRadians;
        vx = -MathUtils.sin(fr) * TOSS_SPEED + MathUtils.random(-0.4f, 0.4f);
        vz = -MathUtils.cos(fr) * TOSS_SPEED + MathUtils.random(-0.4f, 0.4f);
        vy = 3.4f;

        ItemType t = stack.type;
        if (t.isSword()) {
            WeaponModels.SwordAsset a = WeaponModels.buildSword(variantFor(t));
            owned = a; instance = new ModelInstance(a.model); weapon = true;
        } else if (t == ItemType.POTATO_GUN) {
            WeaponModels.GunAsset g = WeaponModels.buildGun();
            owned = g; instance = new ModelInstance(g.model); weapon = true;
        } else {
            Model m = buildQuad(icon);
            owned = m; instance = new ModelInstance(m); weapon = false;
        }
    }

    private static WeaponModels.SwordVariant variantFor(ItemType t) {
        switch (t) {
            case WOOD_SWORD:  return WeaponModels.SwordVariant.WOOD;
            case STONE_SWORD: return WeaponModels.SwordVariant.STONE;
            case IRON_SWORD:  return WeaponModels.SwordVariant.IRON;
            case GOLD_SWORD:  return WeaponModels.SwordVariant.GOLD;
            default:          return WeaponModels.SwordVariant.DIAMOND;
        }
    }

    private static Model buildQuad(Texture icon) {
        icon.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        Material mat = new Material(
            TextureAttribute.createDiffuse(icon),
            new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA),
            FloatAttribute.createAlphaTest(0.5f),
            IntAttribute.createCullFace(GL20.GL_NONE));
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder b = mb.part("item", GL20.GL_TRIANGLES,
            Usage.Position | Usage.Normal | Usage.TextureCoordinates, mat);
        b.setUVRange(0f, 0f, 1f, 1f);
        float s = 0.42f;
        b.rect(-s, -s, 0f,  s, -s, 0f,  s, s, 0f,  -s, s, 0f,  0f, 0f, 1f);
        return mb.end();
    }

    /** Advance physics + spin. Returns true once it has been picked up (caller should remove+dispose). */
    public boolean update(float delta, Vector3 playerPos) {
        age += delta;
        spin = (spin + delta * 130f) % 360f;
        if (!resting) {
            vy -= GRAVITY * delta;
            pos.x += vx * delta; pos.z += vz * delta; pos.y += vy * delta;
            vx *= 0.94f; vz *= 0.94f;
            if (pos.y <= REST_Y) {
                pos.y = REST_Y;
                if (Math.abs(vy) < 1.4f) { resting = true; vy = 0f; }
                else vy = -vy * BOUNCE;
            }
        } else {
            bob = 0.07f * MathUtils.sin(age * 2.2f);
        }

        instance.transform.setToTranslation(pos.x, pos.y + bob + (weapon ? 0.18f : 0f), pos.z)
            .rotate(Vector3.Y, spin);
        if (weapon) instance.transform.rotate(Vector3.X, 35f).scale(0.6f, 0.6f, 0.6f); // tilted voxel drop

        if (age > PICKUP_DELAY && playerPos != null) {
            float dx = playerPos.x - pos.x, dz = playerPos.z - pos.z;
            if (dx * dx + dz * dz < PICKUP_RADIUS * PICKUP_RADIUS) return true;
        }
        return false;
    }

    public void render(ModelBatch batch, Environment env) { batch.render(instance, env); }

    public ItemStack stack() { return stack; }
    public Vector3 position() { return pos; }

    @Override
    public void dispose() { owned.dispose(); }
}
