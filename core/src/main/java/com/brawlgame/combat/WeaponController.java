package com.brawlgame.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
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
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.brawlgame.gfx.BlockParticles;
import com.brawlgame.gfx.SwooshTrail;
import com.brawlgame.audio.AudioManager;
import com.brawlgame.item.ItemType;
import com.brawlgame.model.MinecraftPlayerModel;
import com.brawlgame.model.PlayerAnimator;
import com.brawlgame.model.WeaponModels;

/**
 * Drives the player's three loadouts with heavy, exaggerated, Minecraft-Dungeons-style feel:
 * <ul>
 *   <li><b>1 — Fists:</b> fast alternating forward jabs with a little body drive.</li>
 *   <li><b>2 — Sword:</b> a synchronised torso-twist + true diagonal cross-body slash (combo).</li>
 *   <li><b>3 — Potato gun:</b> a tight two-handed tactical stance.</li>
 * </ul>
 *
 * <p>Arm orientations are composed from explicit axis rotations — {@code yaw·pitch·roll} via
 * {@link #armPose} — instead of {@code setEulerAngles}, so a sweep stays horizontal at any pitch and
 * stance angles behave predictably.
 *
 * <p>The gun is <b>anchored to the body</b> (a fixed chest offset + tilt down the aim line), not to
 * the hand — that's the fix for it "floating off the right shoulder". The arms are then posed inward
 * with locked angles so the right hand sits on the receiver and the left reaches across to the
 * barrel. (Both arms are single rigid bones like Minecraft's, so this is a tuned tight frame rather
 * than an elbow-bent clasp.)
 */
public final class WeaponController implements Disposable {

    public enum Weapon { FIST, SWORD, GUN, ITEM }

    private static final float D2R = MathUtils.degreesToRadians;

    // ---- attack timing / shape ----
    private static final float SWING_DUR  = 0.50f;
    private static final float PUNCH_DUR  = 0.18f; // faster bare-fist jab
    private static final float WINDUP     = 0.30f;  // sword wind-up fraction
    private static final float STRIKE_AT  = 0.40f;  // sword spark point
    private static final float PUNCH_HIT  = 0.42f;  // punch spark point
    private static final float TRAIL_FROM = 0.30f, TRAIL_TO = 0.68f;
    private static final float SNAP_EXP   = 8f;     // higher = snappier strike

    // ---- sword arm arc (degrees) ----
    private static final float ARM_PITCH_HI = 140f, ARM_PITCH_LO = 55f;

    // ---- melee hit resolution (reach in blocks, arc half-angle°) — damage is per-material (meleeBaseDamage) ----
    private static final float SWORD_REACH = 3.1f, SWORD_HALF = 70f;
    private static final float PUNCH_REACH = 1.8f, PUNCH_HALF = 25f;
    // Vanilla attack damage: fist 1; wood/gold 4, stone 5, iron 6, diamond 7. A descending mid-air
    // hit is a CRITICAL and deals 1.5× (see critReady).
    private static final float CRIT_MULT = 1.5f;
    // Arm reaches forward holding the sword out front (vanilla pose). Blade elevation in world =
    // SWORD_TILT + SWORD_READY_PITCH; arm elevation = SWORD_READY_PITCH. Pitch 75 + tilt -75 → the
    // blade comes out roughly horizontal-forward, pointing in the aim direction.
    private static final float SWORD_READY_PITCH = 75f;
    private static final float SWORD_READY_YAW = 6f;
    private static final float SWORD_READY_ROLL = 0f;
    private static final float SWORD_READY_BLEND = 0.2f;

    // ---- torso twist (exaggerated core power, degrees) ----
    private static final float WINDUP_DEG = 35f;
    private static final float DRIVE_DEG  = 55f;

    // ---- gun: weapon-model placement in PLAYER-LOCAL space (pinned to the chest, aimed forward) ----
    private static final float GUN_OFF_X = 0.10f;   // pulled inward toward centre (not out the shoulder)
    private static final float GUN_OFF_Y = 1.7f;   // chest height
    private static final float GUN_OFF_Z = -0.7f;  // forward of the chest
    private static final float GUN_BODY_YAW   = 0f; // angle barrel inward onto the centre line
    private static final float GUN_BODY_PITCH = 2; // tilt muzzle up (aim line)
    private static final float GUN_SCALE      = 1.4f; // 40% bigger than the built model

    // ---- gun: arm stance angles (degrees) — pitch, yaw(inward), roll ----
    private static final float GUN_R_PITCH = 80f, GUN_R_YAW = 22f,  GUN_R_ROLL = 16f;  // trigger hand
    private static final float GUN_L_PITCH = 94f, GUN_L_YAW = -42f, GUN_L_ROLL = -12f; // support hand
    private static final float GUN_BLEND_TIME = 0.15f; // tactical-frame transition

    // ---- gun firing (potato projectiles) ----
    // Potato hits moderately hard — several are needed to drop the armoured rival.
    private static final float GUN_DMG = 12f, GUN_SPEED = 18f, GUN_RANGE = 12f, GUN_COOLDOWN = 0.35f;
    private static final float GUN_MUZZLE_OFFSET = 0.6f; // forward spawn offset from the player
    private static final float GUN_FIRE_Y = 1.3f;        // chest height the potato leaves from

    // ---- sword: local held-item offset from the hand pivot ----
    // Sword is arm-anchored (vanilla parenting): the blade is rotated to continue DOWN the arm, so it
    // extends forward-down from the fist at rest and swings naturally with the arm during an attack.
    private static final float SWORD_SEAT_X = 0f;
    private static final float SWORD_SEAT_Y = 0f;
    private static final float SWORD_SEAT_Z = 0f;
    private static final float SWORD_TILT  = 0f; // X: -Z blade → down the arm (-Y)
    private static final float SWORD_TWIST = 0f;   // Y
    private static final float SWORD_ROLL  = 90f;   // Z

    private final ModelInstance player;
    // Model faces -Z, so the character's RIGHT hand is the +X node — named ARM_L (driven by the
    // animator's l* fields). ARM_R (-X) is the left hand (r* fields).
    private final Node weaponArm;   // ARM_L == right hand
    private final Node supportArm;  // ARM_R == left hand

    // One built model per sword material; the held instance swaps to match the selected hotbar item.
    private final java.util.EnumMap<WeaponModels.SwordVariant, WeaponModels.SwordAsset> swordAssets =
        new java.util.EnumMap<>(WeaponModels.SwordVariant.class);
    private final java.util.EnumMap<WeaponModels.SwordVariant, ModelInstance> swordInstances =
        new java.util.EnumMap<>(WeaponModels.SwordVariant.class);
    private WeaponModels.SwordVariant swordVariant = WeaponModels.SwordVariant.DIAMOND;
    private final WeaponModels.GunAsset gunAsset;
    private final ModelInstance gun;

    // A flat, double-sided quad for holding a generic item (e.g. armour pieces) in the fist — its
    // diffuse texture is swapped to the held item's icon each frame.
    private final Model itemQuadModel;
    private final ModelInstance itemQuad;
    private final TextureAttribute itemQuadTex;
    private ItemType heldItem;
    private java.util.function.Function<ItemType, Texture> iconResolver;

    private final SwooshTrail trail = new SwooshTrail();
    // Hit hearts are spawned by the struck entity (CombatDummy), so they appear ON the entity.

    private Weapon current = Weapon.FIST;
    private Weapon prevWeapon = null; // null = first equip ever; no swap penalty
    private float swapCooldown = 0f;         // brief recovery window after a weapon switch
    private static final float SWAP_CD = 0.40f;
    private float gunBlend;

    private boolean attacking;
    private float attackT;

    // ---- Brawl-Stars ammo: attacks are gated on having a loaded segment; they refill over time ----
    private int ammo;            // currently loaded segments
    private int ammoCapacity;    // = reloadSegments() for the active weapon
    private float ammoRefillTimer;
    private boolean dryFire;      // one-shot: clicked while empty (drives the HUD red flash + shake)
    private boolean altHand;   // alternates: sword combo direction / which fist punches
    private boolean struck;

    // scratch
    private final Matrix4 anchor = new Matrix4();
    private final Quaternion qP = new Quaternion();
    private final Quaternion qY = new Quaternion();
    private final Quaternion qR = new Quaternion();
    private final Vector3 tip = new Vector3();
    private final Vector3 base = new Vector3();
    private boolean prevClick;
    private boolean prevRemoteAttack;

    // aim / combat context (set by the Player each frame)
    private CombatTarget target;
    private BlockCollider collider; // optional: walls that stop potato projectiles
    private float aimX, aimZ, facingDeg;
    private boolean critReady; // player is mid-air and descending → next melee hit crits
    private final Vector3 hitDir = new Vector3();

    /** Straight-line travel speed (blocks/s) of a fired potato before it reaches range and drops. */
    private static final float GUN_LAUNCH_SPEED = 18f;

    // gun projectiles (fixed pool) + the dirt/potato impact splash
    private static final int MAX_POTATO = 16;
    private final PotatoProjectile[] potatoes = new PotatoProjectile[MAX_POTATO];
    private final Model potatoModel;
    private final Texture potatoTex;
    private final BlockParticles impactFx = new BlockParticles();
    private final Matrix4 gunMat = new Matrix4();
    private final Vector3 muzzlePos = new Vector3();
    private final Vector3 launchVel = new Vector3();
    private final Vector3 eyePos = new Vector3();     // body/eye origin for the muzzle-clip ray
    private final Vector3 muzzleHit = new Vector3();  // wall surface in front of a clipped muzzle
    private float gunCooldown;
    private boolean pendingFire;

    public WeaponController(ModelInstance player) {
        this.player = player;
        weaponArm = player.getNode(MinecraftPlayerModel.ARM_L);
        supportArm = player.getNode(MinecraftPlayerModel.ARM_R);

        for (WeaponModels.SwordVariant v : WeaponModels.SwordVariant.values()) {
            WeaponModels.SwordAsset a = WeaponModels.buildSword(v);
            swordAssets.put(v, a);
            swordInstances.put(v, new ModelInstance(a.model));
        }
        gunAsset = WeaponModels.buildGun();
        gun = new ModelInstance(gunAsset.model);

        // Potato projectile: a small 3D box with a clean procedural potato skin (shared model, pooled).
        potatoTex = WeaponModels.buildPotatoTexture();
        potatoModel = WeaponModels.buildPotatoBox(potatoTex);
        for (int i = 0; i < MAX_POTATO; i++) {
            potatoes[i] = new PotatoProjectile(new ModelInstance(potatoModel));
        }

        // Held generic-item quad (icon swapped per frame; potatoTex is just a placeholder).
        Material itemMat = new Material(
            TextureAttribute.createDiffuse(potatoTex),
            new BlendingAttribute(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,
                com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA),
            FloatAttribute.createAlphaTest(0.5f),
            IntAttribute.createCullFace(com.badlogic.gdx.graphics.GL20.GL_NONE));
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder b = mb.part("item", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES,
            Usage.Position | Usage.Normal | Usage.TextureCoordinates, itemMat);
        b.setUVRange(0f, 0f, 1f, 1f);
        float s = 0.5f;
        b.rect(-s, -s, 0f,  s, -s, 0f,  s, s, 0f,  -s, s, 0f,  0f, 0f, 1f);
        itemQuadModel = mb.end();
        itemQuad = new ModelInstance(itemQuadModel);
        itemQuadTex = (TextureAttribute) itemQuad.materials.get(0).get(TextureAttribute.Diffuse);
    }

    /** The active weapon, set from the selected hotbar item by the screen (no hardcoded weapon keys). */
    public void setWeapon(Weapon weapon) {
        this.current = weapon;
    }

    /**
     * Equip from the selected hotbar item: derives the weapon category AND, for a sword, which material
     * variant's model/texture to hold (wooden → iron → diamond, etc.). Null/non-weapon → fists.
     */
    public void setHeldItem(ItemType item) {
        heldItem = item;
        current = weaponFor(item);
        if (item != null && item.isSword()) swordVariant = variantFor(item);
    }

    /** Supplies the icon texture for a held generic item (e.g. armour) so it can be shown in the fist. */
    public void setIconResolver(java.util.function.Function<ItemType, Texture> resolver) {
        this.iconResolver = resolver;
    }

    /** Apply an authoritative attack state from a network snapshot for remote rendering. */
    public void setRemoteAttack(boolean remoteAttacking) {
        if (remoteAttacking && !prevRemoteAttack) {
            if (current == Weapon.GUN) {
                pendingFire = true;
                AudioManager.get().shoot();
            } else if (current != Weapon.ITEM && !attacking) {
                attacking = true;
                attackT = 0f;
                altHand = !altHand;
            }
        }
        prevRemoteAttack = remoteAttacking;
    }

    public boolean isAttacking() {
        return attacking;
    }

    private static WeaponModels.SwordVariant variantFor(ItemType item) {
        switch (item) {
            case WOOD_SWORD:  return WeaponModels.SwordVariant.WOOD;
            case STONE_SWORD: return WeaponModels.SwordVariant.STONE;
            case IRON_SWORD:  return WeaponModels.SwordVariant.IRON;
            case GOLD_SWORD:  return WeaponModels.SwordVariant.GOLD;
            default:          return WeaponModels.SwordVariant.DIAMOND;
        }
    }

    /** The held sword instance + asset for the currently selected material variant. */
    private ModelInstance sword() { return swordInstances.get(swordVariant); }
    private WeaponModels.SwordAsset swordAsset() { return swordAssets.get(swordVariant); }

    /** Maps the item in the selected hotbar slot to a weapon: swords→SWORD, potato gun→GUN, else FIST. */
    public static Weapon weaponFor(ItemType item) {
        if (item == null) return Weapon.FIST;
        if (item == ItemType.POTATO_GUN) return Weapon.GUN;
        if (item.isSword()) return Weapon.SWORD;
        if (item.isArmor()) return Weapon.ITEM; // armour pieces are held in the fist, not swung
        return Weapon.FIST;
    }

    /** Edge-detected attack input only: left-click swings (fist/sword) or fires (gun). */
    public void handleInput() {
        boolean click = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        if (click && !prevClick && swapCooldown <= 0f) { // swap recovery blocks attacks
            if (current == Weapon.GUN) {
                if (gunCooldown <= 0f) {
                    if (ammo >= 1) { pendingFire = true; gunCooldown = GUN_COOLDOWN; ammo--; AudioManager.get().shoot(); }
                    else dryFire = true;
                }
            } else if (current != Weapon.ITEM && !attacking) {
                if (ammo >= 1) {
                    AudioManager.get().swing();
                    attacking = true;
                    attackT = 0f;
                    struck = false;
                    altHand = !altHand;
                    ammo--;
                } else {
                    dryFire = true;
                }
            }
        }
        prevClick = click;
    }

    /** Advance attack/stance and fill the arm override for the animator. */
    public void updatePose(float delta, PlayerAnimator.ArmPose pose) {
        pose.reset();
        tickAmmo(delta);

        if (gunCooldown > 0f) gunCooldown -= delta;
        if (pendingFire) { fireGun(); pendingFire = false; }

        float gunTarget = current == Weapon.GUN ? 1f : 0f;
        gunBlend = MathUtils.lerp(gunBlend, gunTarget, Math.min(1f, delta / GUN_BLEND_TIME));

        if (gunBlend > 0.01f) applyGunStance(pose);
        if ((current == Weapon.SWORD || current == Weapon.ITEM) && !attacking) applySwordReadyPose(pose);

        if (attacking) {
            float dur = current == Weapon.FIST ? PUNCH_DUR : SWING_DUR;
            attackT += delta;
            if (attackT >= dur) attacking = false;
            else if (current == Weapon.FIST) applyPunchPose(pose, attackT / dur);
            else applySwingPose(pose, attackT / dur);
        }
    }

    private void applySwordReadyPose(PlayerAnimator.ArmPose pose) {
        armPose(pose.lRot, SWORD_READY_PITCH, SWORD_READY_YAW, SWORD_READY_ROLL);
        pose.lWeight = Math.max(pose.lWeight, SWORD_READY_BLEND);
    }

    // ---------------------------------------------------------------- gun stance

    private void applyGunStance(PlayerAnimator.ArmPose pose) {
        // Right hand on the receiver/trigger; left hand reaching across to the fore-stock. Quickly
        // (≈0.15s) slerped in via the gunBlend weight so it doesn't snap.
        armPose(pose.lRot, GUN_R_PITCH, GUN_R_YAW, GUN_R_ROLL);
        pose.lWeight = gunBlend;
        armPose(pose.rRot, GUN_L_PITCH, GUN_L_YAW, GUN_L_ROLL);
        pose.rWeight = gunBlend;
    }

    // ---------------------------------------------------------------- sword swing

    private void applySwingPose(PlayerAnimator.ArmPose pose, float p) {
        float a;
        if (p < WINDUP) a = 0f;
        else {
            float t = (p - WINDUP) / (1f - WINDUP);
            a = 1f - (float) Math.pow(1f - t, SNAP_EXP);
        }
        float w;
        if (p < 0.04f) w = p / 0.04f;
        else if (p > 0.55f) w = Math.max(0f, 1f - smoothstep((p - 0.55f) / 0.45f));
        else w = 1f;

        float pitch = MathUtils.lerp(ARM_PITCH_HI, ARM_PITCH_LO, a);
        float sweepStart = altHand ? 60f : -30f;
        float sweepEnd   = altHand ? -45f : 90f;
        float sweep = MathUtils.lerp(sweepStart, sweepEnd, a);
        armPose(pose.lRot, pitch, sweep, 0f);
        pose.lWeight = Math.max(pose.lWeight, w);

        float drive = altHand ? -1f : 1f;
        float twist;
        if (p < WINDUP) {
            twist = MathUtils.lerp(0f, -WINDUP_DEG, smoothstep(p / WINDUP));
        } else {
            float t = (p - WINDUP) / (1f - WINDUP);
            float snap = 1f - (float) Math.pow(1f - t, SNAP_EXP);
            float peak = MathUtils.lerp(-WINDUP_DEG, DRIVE_DEG, snap);
            float rec = smoothstep(MathUtils.clamp((t - 0.30f) / 0.70f, 0f, 1f));
            twist = MathUtils.lerp(peak, 0f, rec);
        }
        pose.bodyYaw = drive * twist * D2R;
    }

    // ---------------------------------------------------------------- punch

    private void applyPunchPose(PlayerAnimator.ArmPose pose, float p) {
        // Thrust out fast (high-exp ease-out), retract a touch slower.
        float ext;
        if (p < 0.45f) ext = 1f - (float) Math.pow(1f - p / 0.45f, 6f);
        else ext = 1f - smoothstep((p - 0.45f) / 0.55f);

        float w;
        if (p < 0.05f) w = p / 0.05f;
        else if (p > 0.6f) w = Math.max(0f, 1f - smoothstep((p - 0.6f) / 0.4f));
        else w = 1f;

        float pitch = MathUtils.lerp(28f, 102f, ext);   // chambered → fully extended forward
        // RIGHT HAND ONLY: always drive the right fist (ARM_L / lRot); never touch the left arm's
        // weight, so the left arm stays on its locomotion/idle swing. No alternation.
        armPose(pose.lRot, pitch, 8f, 0f);               // slight inward
        pose.lWeight = Math.max(pose.lWeight, w);
        // Body drives into the right jab (turns the torso toward -X, i.e. +yaw).
        pose.bodyYaw = 16f * ext * D2R;
    }

    // ---------------------------------------------------------------- arm math

    /** Compose an arm orientation as yaw·pitch·roll (about body Y, X, Z). */
    private void armPose(Quaternion out, float pitchDeg, float yawDeg, float rollDeg) {
        qP.setFromAxis(Vector3.X, pitchDeg);
        qY.setFromAxis(Vector3.Y, yawDeg);
        qR.setFromAxis(Vector3.Z, rollDeg);
        out.set(qY).mul(qP).mul(qR);
    }

    // ---------------------------------------------------------------- post-anim placement / vfx

    /** The gun's world matrix — pinned in front of the chest, tilted up + angled inward, 40% larger. */
    private void buildGunMatrix(Matrix4 out) {
        out.set(player.transform)
            .translate(GUN_OFF_X, GUN_OFF_Y, GUN_OFF_Z)
            .rotate(Vector3.Y, GUN_BODY_YAW)
            .rotate(Vector3.X, GUN_BODY_PITCH)
            .scale(GUN_SCALE, GUN_SCALE, GUN_SCALE);
    }

    public void postAnimate() {
        if (current == Weapon.GUN) {
            // Independent of arm length → no shoulder float; the muzzle is read off this same matrix.
            buildGunMatrix(gun.transform);
            return;
        }

        if (current == Weapon.SWORD) {
            // Arm-anchored: blade continues down the arm, so it hangs forward-down from the fist at
            // rest and swings with the arm during an attack (and the trail samples correctly).
            anchor.set(player.transform).mul(weaponArm.globalTransform).translate(0f, -0.72f, 0f);
            sword().transform.set(anchor)
                .translate(SWORD_SEAT_X, SWORD_SEAT_Y, SWORD_SEAT_Z)
                .rotate(Vector3.X, SWORD_TILT)
                .rotate(Vector3.Y, SWORD_TWIST)
                .rotate(Vector3.Z, SWORD_ROLL);
            if (attacking) sampleSwordVfx();
        } else if (current == Weapon.ITEM) {
            // A flat held item (armour piece): anchored in the fist, the icon facing up-forward.
            if (iconResolver != null && heldItem != null) {
                itemQuadTex.textureDescription.texture = iconResolver.apply(heldItem);
            }
            anchor.set(player.transform).mul(weaponArm.globalTransform).translate(0f, -0.6f, 0f);
            itemQuad.transform.set(anchor)
                .rotate(Vector3.X, -55f)   // tilt the flat item back toward the camera
                .scale(0.55f, 0.55f, 0.55f);
        } else if (attacking) { // FIST
            samplePunchVfx();
        }
    }

    /** Replace all registered targets with a single one (backward-compat helper). */
    public void setTarget(CombatTarget target) {
        this.target = target;
        targets.clear();
        if (target != null) targets.add(target);
    }

    /** Register an additional combat target (for test-map multi-dummy setups). */
    public void addTarget(CombatTarget t) {
        if (t != null && !targets.contains(t)) { targets.add(t); this.target = targets.get(0); }
    }

    private final java.util.List<CombatTarget> targets = new java.util.ArrayList<>();

    /** Optional callback so match stats can track damage dealt by the player. */
    public void setDamageDealtListener(java.util.function.Consumer<Float> listener) {
        this.damageDealtListener = listener;
    }

    private java.util.function.Consumer<Float> damageDealtListener;

    private void reportDamage(float dmg) {
        if (damageDealtListener != null && dmg > 0f) damageDealtListener.accept(dmg);
    }

    /** Optional world collider so potato projectiles stop on walls/fences (null = no walls). */
    public void setCollider(BlockCollider collider) { this.collider = collider; }

    /** Player feeds its world position + facing each frame, for melee arc tests. */
    public void setAim(float x, float z, float facingDeg) {
        this.aimX = x;
        this.aimZ = z;
        this.facingDeg = facingDeg;
    }

    /** Player feeds its physics state each frame: true when airborne AND falling → the hit will crit. */
    public void setCritReady(boolean critReady) { this.critReady = critReady; }

    /** Vanilla base attack damage for the active melee weapon (fist or the held sword material). */
    private float meleeBaseDamage() {
        if (current == Weapon.FIST) return 1f;
        switch (swordVariant) {
            case STONE:   return 4f;
            case IRON:    return 5f;
            case DIAMOND: return 8f;
            case WOOD:
            case GOLD:
            default:      return 3f;
        }
    }

    /** Hit every registered target that sits within {@code reach} and the frontal arc. */
    private boolean tryMeleeHit(float reach, float halfDeg, float dmg, boolean crit) {
        if (targets.isEmpty()) return false;
        float fr = facingDeg * D2R;
        float fwdX = -MathUtils.sin(fr), fwdZ = -MathUtils.cos(fr);
        boolean anyHit = false;
        for (CombatTarget t : targets) {
            Vector3 tp = t.position();
            float dx = tp.x - aimX, dz = tp.z - aimZ;
            float dist2 = dx * dx + dz * dz;
            float r = reach + t.radius();
            if (dist2 > r * r || dist2 < 1e-5f) continue;
            float dist = (float) Math.sqrt(dist2);
            float dot = (dx * fwdX + dz * fwdZ) / dist;
            if (dot < MathUtils.cosDeg(halfDeg)) continue;
            if (!CombatLoS.clear(collider, aimX, aimZ, tp.x, tp.z)) continue;
            hitDir.set(dx, 0f, dz).nor();
            t.onHit(dmg, hitDir, crit);
            reportDamage(dmg);
            anyHit = true;
            // Play sword hit sound on successful hit
            AudioManager.get().playSwordHit();
        }
        return anyHit;
    }

    private void sampleSwordVfx() {
        float p = attackT / SWING_DUR;
        tip.set(0f, 0f, -swordAsset().tipZ).mul(sword().transform);
        base.set(0f, 0f, -0.05f).mul(sword().transform);
        if (p >= TRAIL_FROM && p <= TRAIL_TO) trail.addSample(tip, base);
        if (!struck && p >= STRIKE_AT) {
            // Connect with the entity — the entity itself pops the hearts (never on air swings). A
            // descending mid-air strike crits for 1.5× and a heavier heart/spark burst.
            boolean crit = critReady;
            tryMeleeHit(SWORD_REACH, SWORD_HALF, meleeBaseDamage() * (crit ? CRIT_MULT : 1f), crit);
            struck = true;
        }
    }

    private void samplePunchVfx() {
        float p = attackT / PUNCH_DUR;
        if (struck || p < PUNCH_HIT) return;
        Node fistArm = weaponArm; // right hand only
        anchor.set(player.transform).mul(fistArm.globalTransform).translate(0f, -0.72f, 0f);
        anchor.getTranslation(tip);
        // Connect with the entity — the entity itself pops the hearts. A descending mid-air punch crits.
        boolean crit = critReady;
        tryMeleeHit(PUNCH_REACH, PUNCH_HALF, meleeBaseDamage() * (crit ? CRIT_MULT : 1f), crit);
        struck = true;
    }

    /**
     * Fire a potato from the gun's muzzle: it flies dead straight (no arc) at muzzle height, passing
     * through open air, then drops steeply once it has covered the reticle's range. The muzzle world
     * position is read straight off the gun's model matrix (the flared barrel tip).
     */
    private void fireGun() {
        PotatoProjectile p = null;
        for (PotatoProjectile cand : potatoes) if (!cand.isAlive()) { p = cand; break; }
        if (p == null) return;

        // Muzzle = barrel-tip local point transformed by the live gun matrix.
        buildGunMatrix(gunMat);
        muzzlePos.set(WeaponModels.GUN_MUZZLE_X, WeaponModels.GUN_MUZZLE_Y, WeaponModels.GUN_MUZZLE_Z)
            .mul(gunMat);

        // Muzzle-clip guard: cast from the body/eye to the muzzle. If a wall sits between them the gun
        // is poking through it — detonate on the wall surface (in the player's face) instead of firing
        // the potato out the far side.
        player.transform.getTranslation(eyePos);
        eyePos.y += GUN_OFF_Y;
        if (GridRaycast.firstHit(collider, eyePos, muzzlePos, muzzleHit)) {
            p.spawnLanded(muzzleHit);
            impactFx.burst(muzzleHit, 14);
            return;
        }

        // Horizontal aim direction (matches the ground reticle).
        float fr = facingDeg * D2R;
        float fwdX = -MathUtils.sin(fr), fwdZ = -MathUtils.cos(fr);

        // Level launch at the muzzle height; it drops steeply after travelling `range` blocks.
        float range = Math.max(2f, CONE_GUN_RANGE - GUN_MUZZLE_OFFSET);
        float vh = GUN_LAUNCH_SPEED;
        launchVel.set(fwdX * vh, 0f, fwdZ * vh);

        p.launch(muzzlePos, launchVel, range);
    }

    private void updatePotatoes(float delta) {
        for (PotatoProjectile p : potatoes) {
            if (!p.isAlive()) continue;
            boolean impacted = p.update(delta, collider);
            // Enemy hit while still flying — check all registered targets.
            if (p.isFlying() && !targets.isEmpty()) {
                boolean potatoHit = false;
                for (CombatTarget t : targets) {
                    Vector3 tp = t.position();
                    float dx = tp.x - p.position().x, dz = tp.z - p.position().z;
                    float hr = t.radius() + 0.3f;
                    if (dx * dx + dz * dz < hr * hr && p.position().y > 0.3f && p.position().y < 2.2f
                        && CombatLoS.clear(collider, p.position().x, p.position().z, tp.x, tp.z)) {
                        hitDir.set(dx, 0f, dz).nor();
                        t.onHit(GUN_DMG, hitDir, false);
                        reportDamage(GUN_DMG);
                        impactFx.burst(p.position(), 12);
                        p.destroy();
                        potatoHit = true;
                        break;
                    }
                }
                if (potatoHit) continue;
            }
            // Ground/wall impact this frame → splash dirt + potato chunks.
            if (impacted) impactFx.burst(p.position(), 14);
        }
        impactFx.update(delta);
    }

    public void updateVfx(float delta) {
        updatePotatoes(delta);
        trail.update(delta);
    }

    /** Held weapon (lit) + potato projectiles + impact splash — call inside the main ModelBatch pass. */
    public void renderWorld(ModelBatch batch, Environment env) {
        if (current == Weapon.SWORD) batch.render(sword(), env);
        else if (current == Weapon.GUN) batch.render(gun, env);
        else if (current == Weapon.ITEM) batch.render(itemQuad, env);
        for (PotatoProjectile p : potatoes) p.render(batch, env);
        impactFx.render(batch, env);
    }

    /** Additive swoosh ribbon — call after ModelBatch.end(), before the HUD. */
    public void renderTrail(Camera camera) {
        if (!trail.isEmpty()) trail.render(camera);
    }

    public Weapon getWeapon() { return current; }

    /** Loaded ammo segments + capacity, and a one-shot "clicked while empty" edge — feed the overhead bar. */
    public int ammo() { return ammo; }
    public int ammoCapacity() { return ammoCapacity; }
    public boolean pollDryFire() { boolean b = dryFire; dryFire = false; return b; }

    /** Refill ammo over time. Switching weapon types triggers a swap-recovery cooldown. */
    private void tickAmmo(float delta) {
        int cap = reloadSegments();
        if (current != prevWeapon) {
            boolean firstEquip = prevWeapon == null;
            prevWeapon = current;
            ammoCapacity = cap;
            ammoRefillTimer = 0f;
            if (firstEquip) {
                ammo = cap; // initial equip: full ammo, no penalty
            } else {
                ammo = 0;              // switching mid-fight: must reload from scratch
                swapCooldown = SWAP_CD; // block attacks for a brief recovery window
            }
        } else if (cap != ammoCapacity) {
            ammoCapacity = cap; // same weapon, capacity changed (e.g. same type, different tier)
        }
        if (swapCooldown > 0f) swapCooldown = Math.max(0f, swapCooldown - delta);
        if (ammo < ammoCapacity) {
            ammoRefillTimer += delta;
            float per = reloadSecondsPerSegment();
            while (ammoRefillTimer >= per && ammo < ammoCapacity) { ammo++; ammoRefillTimer -= per; }
        }
    }

    /** Number of segments on the overhead reload bar: wood/gold 2, stone/iron 3, diamond 4 (gun 3, fist 2). */
    public int reloadSegments() {
        if (current == Weapon.GUN) return 3;
        if (current == Weapon.FIST) return 2;
        switch (swordVariant) {
            case STONE:
            case IRON:    return 3;
            case DIAMOND: return 4;
            case WOOD:
            case GOLD:
            default:      return 2;
        }
    }

    /** Seconds to refill one reload segment — significantly faster across the board. */
    public float reloadSecondsPerSegment() {
        if (current == Weapon.GUN)  return 0.9f;
        if (current == Weapon.FIST) return 0.6f;
        switch (swordVariant) {
            case DIAMOND: return 0.7f;
            case IRON:    return 0.9f;
            case STONE:   return 1.1f;
            case WOOD:
            case GOLD:
            default:      return 1.3f;
        }
    }

    // ---- aim-cone shape for the ground reticle (DungeonGame reads these) ----
    private static final float CONE_SWORD_HALF = 68f, CONE_SWORD_RANGE = 3.6f;
    private static final float CONE_GUN_HALF   = 7f,  CONE_GUN_RANGE   = 9.1f; // 35% shorter than 14
    private static final float CONE_FIST_HALF  = 20f, CONE_FIST_RANGE  = 2.3f;

    /** Show the reticle while aiming the gun, or during a melee swing. */
    public boolean aimConeVisible() { return current == Weapon.GUN || attacking; }

    public float coneHalfAngle() {
        switch (current) {
            case GUN:  return CONE_GUN_HALF;
            case FIST: return CONE_FIST_HALF;
            default:   return CONE_SWORD_HALF;
        }
    }

    public float coneRange() {
        switch (current) {
            case GUN:  return CONE_GUN_RANGE;
            case FIST: return CONE_FIST_RANGE;
            default:   return CONE_SWORD_RANGE;
        }
    }

    private static float smoothstep(float t) { return t * t * (3f - 2f * t); }

    @Override
    public void dispose() {
        for (WeaponModels.SwordAsset a : swordAssets.values()) a.dispose();
        gunAsset.dispose();
        itemQuadModel.dispose();
        potatoModel.dispose();
        potatoTex.dispose();
        impactFx.dispose();
        trail.dispose();
    }
}
