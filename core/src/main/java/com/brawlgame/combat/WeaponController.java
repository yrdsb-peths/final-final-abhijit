package com.brawlgame.combat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.brawlgame.gfx.SwooshTrail;
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

    public enum Weapon { FIST, SWORD, GUN }

    private static final float D2R = MathUtils.degreesToRadians;

    // ---- attack timing / shape ----
    private static final float SWING_DUR  = 0.50f;
    private static final float PUNCH_DUR  = 0.26f;
    private static final float WINDUP     = 0.30f;  // sword wind-up fraction
    private static final float STRIKE_AT  = 0.40f;  // sword spark point
    private static final float PUNCH_HIT  = 0.42f;  // punch spark point
    private static final float TRAIL_FROM = 0.30f, TRAIL_TO = 0.68f;
    private static final float SNAP_EXP   = 8f;     // higher = snappier strike

    // ---- sword arm arc (degrees) ----
    private static final float ARM_PITCH_HI = 140f, ARM_PITCH_LO = 55f;

    // ---- melee hit resolution (reach in blocks, arc half-angle°, visual damage) ----
    private static final float SWORD_REACH = 2.6f, SWORD_HALF = 70f, SWORD_DMG = 99f;
    private static final float PUNCH_REACH = 1.8f, PUNCH_HALF = 25f, PUNCH_DMG = 30f;
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
    private static final float GUN_BODY_YAW   = -4f; // angle barrel inward onto the centre line
    private static final float GUN_BODY_PITCH = 20f; // tilt muzzle up (aim line)

    // ---- gun: arm stance angles (degrees) — pitch, yaw(inward), roll ----
    private static final float GUN_R_PITCH = 80f, GUN_R_YAW = 22f,  GUN_R_ROLL = 16f;  // trigger hand
    private static final float GUN_L_PITCH = 94f, GUN_L_YAW = -42f, GUN_L_ROLL = -12f; // support hand
    private static final float GUN_BLEND_TIME = 0.15f; // tactical-frame transition

    // ---- gun firing (potato projectiles) ----
    private static final float GUN_DMG = 45f, GUN_SPEED = 22f, GUN_RANGE = 14f, GUN_COOLDOWN = 0.28f;
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

    private final WeaponModels.SwordAsset swordAsset;
    private final Model swordModel, gunModel;
    private final ModelInstance sword, gun;

    private final SwooshTrail trail = new SwooshTrail();
    // Hit hearts are spawned by the struck entity (CombatDummy), so they appear ON the entity.

    private Weapon current = Weapon.FIST;
    private float gunBlend;

    private boolean attacking;
    private float attackT;
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

    // aim / combat context (set by the Player each frame)
    private CombatTarget target;
    private float aimX, aimZ, facingDeg;
    private final Vector3 hitDir = new Vector3();

    // gun projectiles (fixed pool)
    private static final int MAX_POTATO = 16;
    private static final class Potato {
        final Vector3 pos = new Vector3();
        final Vector3 vel = new Vector3();
        float travelled;
        boolean alive;
    }
    private final Potato[] potatoes = new Potato[MAX_POTATO];
    private final ModelInstance[] potatoFx = new ModelInstance[MAX_POTATO];
    private final Model potatoModel;
    private float gunCooldown;
    private boolean pendingFire;

    public WeaponController(ModelInstance player) {
        this.player = player;
        weaponArm = player.getNode(MinecraftPlayerModel.ARM_L);
        supportArm = player.getNode(MinecraftPlayerModel.ARM_R);

        swordAsset = WeaponModels.buildSword();
        swordModel = swordAsset.model;
        gunModel = WeaponModels.buildGun();
        sword = new ModelInstance(swordModel);
        gun = new ModelInstance(gunModel);

        // Small potato projectile (shared model, pooled instances).
        Material spud = new Material(ColorAttribute.createDiffuse(new Color(0.78f, 0.62f, 0.36f, 1f)));
        potatoModel = new ModelBuilder().createBox(0.16f, 0.16f, 0.22f, spud, Usage.Position | Usage.Normal);
        for (int i = 0; i < MAX_POTATO; i++) {
            potatoes[i] = new Potato();
            potatoFx[i] = new ModelInstance(potatoModel);
        }
    }

    /** Edge-detected input: 1 = fists, 2 = sword, 3 = gun, left-click = attack (fists/sword). */
    public void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) current = Weapon.FIST;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) current = Weapon.SWORD;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) current = Weapon.GUN;

        boolean click = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        if (click && !prevClick) {
            if (current == Weapon.GUN) {
                if (gunCooldown <= 0f) { pendingFire = true; gunCooldown = GUN_COOLDOWN; }
            } else if (!attacking) {
                attacking = true;
                attackT = 0f;
                struck = false;
                altHand = !altHand;
            }
        }
        prevClick = click;
    }

    /** Advance attack/stance and fill the arm override for the animator. */
    public void updatePose(float delta, PlayerAnimator.ArmPose pose) {
        pose.reset();

        if (gunCooldown > 0f) gunCooldown -= delta;
        if (pendingFire) { fireGun(); pendingFire = false; }

        float gunTarget = current == Weapon.GUN ? 1f : 0f;
        gunBlend = MathUtils.lerp(gunBlend, gunTarget, Math.min(1f, delta / GUN_BLEND_TIME));

        if (gunBlend > 0.01f) applyGunStance(pose);
        if (current == Weapon.SWORD && !attacking) applySwordReadyPose(pose);

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

    public void postAnimate() {
        if (current == Weapon.GUN) {
            // Pin the gun in player-local space: in front of the chest, tilted up + angled inward so
            // the barrel runs down the aim line. Independent of arm length → no shoulder float.
            gun.transform.set(player.transform)
                .translate(GUN_OFF_X, GUN_OFF_Y, GUN_OFF_Z)
                .rotate(Vector3.Y, GUN_BODY_YAW)
                .rotate(Vector3.X, GUN_BODY_PITCH);
            return;
        }

        if (current == Weapon.SWORD) {
            // Arm-anchored: blade continues down the arm, so it hangs forward-down from the fist at
            // rest and swings with the arm during an attack (and the trail samples correctly).
            anchor.set(player.transform).mul(weaponArm.globalTransform).translate(0f, -0.72f, 0f);
            sword.transform.set(anchor)
                .translate(SWORD_SEAT_X, SWORD_SEAT_Y, SWORD_SEAT_Z)
                .rotate(Vector3.X, SWORD_TILT)
                .rotate(Vector3.Y, SWORD_TWIST)
                .rotate(Vector3.Z, SWORD_ROLL);
            if (attacking) sampleSwordVfx();
        } else if (attacking) { // FIST
            samplePunchVfx();
        }
    }

    /** The combat target the attacks resolve against (the dummy), set by the Player. */
    public void setTarget(CombatTarget target) { this.target = target; }

    /** Player feeds its world position + facing each frame, for melee arc tests. */
    public void setAim(float x, float z, float facingDeg) {
        this.aimX = x;
        this.aimZ = z;
        this.facingDeg = facingDeg;
    }

    /** If the target sits within {@code reach} and the frontal arc, register the hit + knockback. */
    private boolean tryMeleeHit(float reach, float halfDeg, float dmg) {
        if (target == null) return false;
        Vector3 tp = target.position();
        float dx = tp.x - aimX, dz = tp.z - aimZ;
        float dist2 = dx * dx + dz * dz;
        float r = reach + target.radius();
        if (dist2 > r * r || dist2 < 1e-5f) return false;
        float fr = facingDeg * D2R;
        float fwdX = -MathUtils.sin(fr), fwdZ = -MathUtils.cos(fr);
        float dist = (float) Math.sqrt(dist2);
        float dot = (dx * fwdX + dz * fwdZ) / dist;
        if (dot < MathUtils.cosDeg(halfDeg)) return false; // outside the swing arc
        hitDir.set(dx, 0f, dz).nor();
        target.onHit(dmg, hitDir);
        return true;
    }

    private void sampleSwordVfx() {
        float p = attackT / SWING_DUR;
        tip.set(0f, 0f, -swordAsset.tipZ).mul(sword.transform);
        base.set(0f, 0f, -0.05f).mul(sword.transform);
        if (p >= TRAIL_FROM && p <= TRAIL_TO) trail.addSample(tip, base);
        if (!struck && p >= STRIKE_AT) {
            // Connect with the entity — the entity itself pops the hearts (never on air swings).
            tryMeleeHit(SWORD_REACH, SWORD_HALF, SWORD_DMG);
            struck = true;
        }
    }

    private void samplePunchVfx() {
        float p = attackT / PUNCH_DUR;
        if (struck || p < PUNCH_HIT) return;
        Node fistArm = weaponArm; // right hand only
        anchor.set(player.transform).mul(fistArm.globalTransform).translate(0f, -0.72f, 0f);
        anchor.getTranslation(tip);
        // Connect with the entity — the entity itself pops the hearts.
        tryMeleeHit(PUNCH_REACH, PUNCH_HALF, PUNCH_DMG);
        struck = true;
    }

    /** Launch a potato down the aim line from chest height. */
    private void fireGun() {
        Potato p = null;
        for (Potato cand : potatoes) if (!cand.alive) { p = cand; break; }
        if (p == null) return;
        float fr = facingDeg * D2R;
        float fwdX = -MathUtils.sin(fr), fwdZ = -MathUtils.cos(fr);
        p.alive = true;
        p.travelled = 0f;
        p.pos.set(aimX + fwdX * GUN_MUZZLE_OFFSET, GUN_FIRE_Y, aimZ + fwdZ * GUN_MUZZLE_OFFSET);
        p.vel.set(fwdX, 0f, fwdZ).scl(GUN_SPEED);
    }

    private void updatePotatoes(float delta) {
        for (int i = 0; i < MAX_POTATO; i++) {
            Potato p = potatoes[i];
            if (!p.alive) continue;
            p.pos.mulAdd(p.vel, delta);
            p.travelled += GUN_SPEED * delta;
            if (target != null) {
                Vector3 tp = target.position();
                float dx = tp.x - p.pos.x, dz = tp.z - p.pos.z;
                float hr = target.radius() + 0.25f;
                if (dx * dx + dz * dz < hr * hr && p.pos.y > 0.4f && p.pos.y < 2.1f) {
                    hitDir.set(p.vel.x, 0f, p.vel.z).nor();
                    target.onHit(GUN_DMG, hitDir); // entity pops the hearts on itself
                    p.alive = false;
                    continue;
                }
            }
            if (p.travelled > GUN_RANGE || p.pos.y < 0f) p.alive = false; // de-spawn at max range
        }
    }

    public void updateVfx(float delta) {
        updatePotatoes(delta);
        trail.update(delta);
    }

    /** Held weapon (lit) + potato projectiles — call inside the main ModelBatch pass. */
    public void renderWorld(ModelBatch batch, Environment env) {
        if (current == Weapon.SWORD) batch.render(sword, env);
        else if (current == Weapon.GUN) batch.render(gun, env);
        for (int i = 0; i < MAX_POTATO; i++) {
            if (!potatoes[i].alive) continue;
            potatoFx[i].transform.setToTranslation(potatoes[i].pos);
            batch.render(potatoFx[i], env);
        }
    }

    /** Additive swoosh ribbon — call after ModelBatch.end(), before the HUD. */
    public void renderTrail(Camera camera) {
        if (!trail.isEmpty()) trail.render(camera);
    }

    public Weapon getWeapon() { return current; }

    // ---- aim-cone shape for the ground reticle (DungeonGame reads these) ----
    private static final float CONE_SWORD_HALF = 68f, CONE_SWORD_RANGE = 3.6f;
    private static final float CONE_GUN_HALF   = 7f,  CONE_GUN_RANGE   = 14f;
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
        swordAsset.dispose();
        gunModel.dispose();
        potatoModel.dispose();
        trail.dispose();
    }
}
