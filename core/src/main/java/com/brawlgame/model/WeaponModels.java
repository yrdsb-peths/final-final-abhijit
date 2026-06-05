package com.brawlgame.model;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder;

/**
 * Procedural blocky weapon models, held in the right hand. Both are authored in their own local
 * space with the business end pointing along <b>-Z</b> (forward) and the grip near the origin, so
 * {@code WeaponController} can anchor them to the hand and the blade/barrel reads as "pointing out
 * of the fist". The sword's tip sits at {@code (0,0,-SWORD_TIP_Z)} — used to trace the swoosh trail.
 */
public final class WeaponModels {

    /** Distance from the grip origin to the sword tip (world units) — the trail traces this point. */
    public static final float SWORD_TIP_Z = 0.66f;
    /** Distance from the grip origin to the gun muzzle (world units). */
    public static final float GUN_MUZZLE_Z = 0.48f;

    private WeaponModels() {}

    /** A short, chunky Minecraft-style sword: steel blade, dark guard, brown grip. */
    public static Model buildSword() {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();

        // Blade — thin (x), tall (y), long (z), extending forward from the guard to the tip.
        part(mb, "blade", new Color(0.80f, 0.84f, 0.90f, 1f),
            0f, 0f, -0.34f,  0.03f, 0.13f, 0.60f);
        // Bright edge highlight strip so the steel catches the light.
        part(mb, "edge", new Color(0.95f, 0.97f, 1f, 1f),
            0f, 0.055f, -0.34f,  0.035f, 0.02f, 0.60f);
        // Cross-guard — a wide dark bar just in front of the fist.
        part(mb, "guard", new Color(0.22f, 0.22f, 0.26f, 1f),
            0f, 0f, -0.02f,  0.22f, 0.05f, 0.05f);
        // Handle / grip — brown, sitting in and just behind the fist.
        part(mb, "grip", new Color(0.40f, 0.26f, 0.14f, 1f),
            0f, 0f, 0.08f,  0.045f, 0.05f, 0.18f);
        // Pommel cap.
        part(mb, "pommel", new Color(0.70f, 0.58f, 0.20f, 1f),
            0f, 0f, 0.18f,  0.07f, 0.07f, 0.05f);

        return mb.end();
    }

    /** A blocky two-handed "potato gun": tan body, darker barrel, a drop-down grip. */
    public static Model buildGun() {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();

        // Receiver / body.
        part(mb, "body", new Color(0.62f, 0.49f, 0.32f, 1f),
            0f, 0f, -0.04f,  0.13f, 0.15f, 0.34f);
        // Barrel — narrower, projecting forward toward the muzzle.
        part(mb, "barrel", new Color(0.34f, 0.28f, 0.20f, 1f),
            0f, 0.01f, -0.34f,  0.09f, 0.09f, 0.30f);
        // Muzzle ring.
        part(mb, "muzzle", new Color(0.18f, 0.16f, 0.13f, 1f),
            0f, 0.01f, -0.47f,  0.11f, 0.11f, 0.04f);
        // Drop-down grip held by the right hand.
        part(mb, "grip", new Color(0.30f, 0.22f, 0.14f, 1f),
            0f, -0.13f, 0.07f,  0.08f, 0.18f, 0.09f);
        // Fore-stock the left hand supports.
        part(mb, "stock", new Color(0.52f, 0.40f, 0.26f, 1f),
            0f, -0.06f, -0.20f,  0.10f, 0.08f, 0.12f);

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
