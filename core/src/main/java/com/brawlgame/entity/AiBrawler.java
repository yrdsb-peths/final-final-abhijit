package com.brawlgame.entity;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.utils.Disposable;
import com.brawlgame.combat.ArmorStats;
import com.brawlgame.combat.BlockCollider;
import com.brawlgame.combat.CombatLoS;
import com.brawlgame.combat.CombatTarget;
import com.brawlgame.combat.PotatoProjectile;
import com.brawlgame.combat.WeaponAnchors;
import com.brawlgame.game.MatchStats;
import com.brawlgame.gfx.BlockParticles;
import com.brawlgame.gfx.Sparks;
import com.brawlgame.item.Inventory;
import com.brawlgame.item.ItemStack;
import com.brawlgame.item.ItemType;
import com.brawlgame.model.MinecraftPlayerModel;
import com.brawlgame.model.PlayerAnimator;
import com.brawlgame.model.WeaponModels;
import com.brawlgame.audio.AudioManager;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.math.Matrix4;
import java.util.function.BiPredicate;
import com.brawlgame.ui.Settings;

/**
 * The AI rival: a rigged Minecraft model that plays like a real opponent — it wears <b>iron armour</b>
 * (a visible layer that also reduces incoming damage), wields a <b>diamond sword</b>, and lobs
 * <b>potatoes</b> from range. It moves with momentum and respects wall collision, driven by a small
 * state machine:
 *
 * <ul>
 *   <li><b>WANDER</b> — no target: strolls (walk speed) toward random points.</li>
 *   <li><b>CHASE</b> — player in aggro range: <i>runs</i> straight in from afar, then <i>circle-strafes</i>
 *       (Brawl-Stars juking) once close, firing potatoes opportunistically along the way.</li>
 *   <li><b>ATTACK</b> — player in melee range, off cooldown: <i>leaps</i> at the player; if it strikes
 *       while airborne and descending it lands a <b>critical</b> (1.5× damage + heavier knockback).</li>
 * </ul>
 *
 * <p>On any hit it flashes <b>pure red for 0.2s</b> ({@code flashTimer}), pops hearts, and is knocked
 * back along the hit direction. It is a {@link CombatTarget}; armour soaks the player's melee, so the
 * potato gun is the player's reliable answer. Clamped to the map bounds and stopped by solid blocks.
 */
public final class AiBrawler implements CombatTarget, Disposable {

    private enum State { WANDER, CHASE, ATTACK }
    private enum Loadout { SWORD, GUN, FIST }

    private static final float HITBOX_HALF = 0.35f;
    private static final float HIT_RADIUS = 0.6f;
    private static final float HURT_DUR = 0.35f, FLASH_DUR = 0.2f;
    private static final float DEATH_DUR = 0.9f; // Minecraft death: tip onto side, red, then vanish

    private static final float AGGRO_RANGE = 12f;
    private static final float MELEE_RANGE = 3.0f, MELEE_HIT_RANGE = 3.8f;
    private static final float RUN_SPEED = 4.3f, WALK_SPEED = 2.4f, STRAFE_SPEED = 3.2f, STRAFE_RANGE = 3.6f;
    private static final float CHASE_SPRINT_MUL = 1.32f;
    private static final float SPRINT_JUMP_DIST = 4.5f; // jump when closing from this range
    private static final float ACCEL = 14f;

    private static final float ATTACK_WINDUP = 0.42f;  // jump apex falls inside this → strike while descending
    private static final float ATTACK_LUNGE = 0.18f, ATTACK_COOLDOWN = 1.0f;
    private static final float MELEE_DAMAGE = 12.5f, CRIT_MULT = 1.4f;

    private static final float GRAVITY = 22f, JUMP_V = 8.8f;

    private static final float KB_SELF = 18f, KB_DAMP = 7f;        // knockback imparted TO this bot
    private static final float KB_TO_PLAYER = 0.9f, KB_CRIT = 1.5f; // impulse strength onto the player

    private static final float RANGED_MIN = 3.8f, RANGED_MAX = 14f, RANGED_CD = 0.85f;
    private static final float GUN_DMG = 12.0f, GUN_SPEED = 12f, GUN_KB = 0.65f;

    private static final float EASY_MODE_HEALTH = 10f; // 5 hearts for easy mode
    private static final Color HURT_TINT = new Color(1f, 0f, 0f, 1f);       // pure red

    private final Model model;
    private final ModelInstance instance;
    private final PlayerAnimator animator;
    private final PlayerAnimator.ArmPose armPose = new PlayerAnimator.ArmPose();
    private final Vector3 pos = new Vector3();
    private final Vector3 targetPos = new Vector3();
    private final Vector3 vel = new Vector3();   // steering velocity (blocks/sec)
    private final Vector3 knock = new Vector3(); // decaying knockback velocity (blocks/sec)
    private float vy;
    private boolean onGround = true;
    private float facingDeg, maxHealth = 20f, health = 20f; // same HP pool as the player
    // Clean body transform (no hurt tilt) used for gun placement so the gun sits correctly.
    private final Matrix4 cleanBodyMat = new Matrix4();
    private float speedScale = 1f;

    private State state = State.WANDER;
    private float hurtTimer, flashTimer, stunTimer;
    private float attackTimer, lungeTimer, cooldownTimer, rangedCooldown;
    private float sprintJumpCooldown = 0f;
    private boolean attacking;
    private boolean remoteAttackHeld;
    private boolean dying, gone;   // dying = playing the death tip-over; gone = finished, remove it
    private float deathTimer;
    private boolean tinted;
    private final ColorAttribute redTint = ColorAttribute.createDiffuse(HURT_TINT);

    private final Sparks hearts = new Sparks();
    private final Vector3 chestTmp = new Vector3();
    private final Vector3 wander = new Vector3();
    private final Vector3 dir = new Vector3();
    private float wanderTimer, strafeTimer;
    private int strafeDir = 1;

    private final float minX, maxX, minZ, maxZ;
    private BlockCollider collider; // solid grid (null = flat void)
    private final BotPathfinder pathfinder;
    private java.util.List<com.badlogic.gdx.math.Vector2> path = new java.util.ArrayList<>();
    private float pathRecalcTimer = 0f;
    private static final float PATH_RECALC_INTERVAL = 0.4f; // recalc frequently for responsive wall routing
    private BiPredicate<Float, Float> hazardChecker;

    // ---- worn armour (visible layer + damage reduction) ----
    private Inventory gear = new Inventory();
    private ArmorRenderer armor;
    private boolean prevNetworkAttacking = false;
    private float clientSpeed = 0f;
    private boolean clientSprinting = false;
    private boolean altHand = false;
    private java.util.function.BooleanSupplier uiSelectedGunSupplier;
    private java.util.function.Supplier<Float> aimDegFromMouseSupplier;
    private boolean prevLocalAttackClick = false;

    // ---- ammo + health regeneration for LAN guest / brawler ----
    private int ammo = 3;
    private int ammoCapacity = 3;
    private float ammoRefillTimer = 0f;
    private boolean dryFire = false;
    private Loadout prevEquipped = null;
    private float regenCooldown = 0f;
    private float regenRate = 0.30f; // faster regen
    private float regenDelay = 2.0f;  // shorter delay

    // ---- inventory + held weapons (same offsets as the player) ----
    private final Inventory loadout = new Inventory();
    private Loadout equipped = Loadout.SWORD;
    private final WeaponModels.SwordAsset swordAsset;
    private final ModelInstance sword;
    private final WeaponModels.GunAsset gunAsset;
    private final ModelInstance gun;
    private final Node rightArm;
    private final Matrix4 weaponMat = new Matrix4();
    private final Quaternion qP = new Quaternion();
    private final Quaternion qY = new Quaternion();
    private final Quaternion qR = new Quaternion();
    private MatchStats matchStats;

    // ---- ranged potatoes ----
    private static final int MAX_POTATO = 6;
    private final PotatoProjectile[] potatoes = new PotatoProjectile[MAX_POTATO];
    private final Model potatoModel;
    private final Texture potatoTex;
    private final BlockParticles impactFx = new BlockParticles();
    private final Vector3 muzzle = new Vector3();
    private final Vector3 launchVel = new Vector3();

    private static final float GUN_R_PITCH = 80f, GUN_R_YAW = 22f,  GUN_R_ROLL = 16f;
    private static final float GUN_L_PITCH = 94f, GUN_L_YAW = -42f, GUN_L_ROLL = -12f;

    public AiBrawler(Texture skin, float x, float z, float minX, float maxX, float minZ, float maxZ) {
        model = MinecraftPlayerModel.build(skin);
        instance = new ModelInstance(model);
        animator = new PlayerAnimator(instance);
        this.minX = minX; this.maxX = maxX; this.minZ = minZ; this.maxZ = maxZ;
        pathfinder = new BotPathfinder(minX, maxX, minZ, maxZ);
        pos.set(x, 0f, z);
        targetPos.set(pos);
        tinted = false;

        gear.set(Inventory.ARMOR_BASE + 0, new ItemStack(ItemType.DIAMOND_HELMET));
        gear.set(Inventory.ARMOR_BASE + 1, new ItemStack(ItemType.DIAMOND_CHESTPLATE));
        gear.set(Inventory.ARMOR_BASE + 2, new ItemStack(ItemType.DIAMOND_LEGGINGS));
        gear.set(Inventory.ARMOR_BASE + 3, new ItemStack(ItemType.DIAMOND_BOOTS));
        armor = new ArmorRenderer(gear);

        loadout.set(Inventory.HOTBAR_BASE + 0, new ItemStack(ItemType.DIAMOND_SWORD));
        loadout.set(Inventory.HOTBAR_BASE + 1, new ItemStack(ItemType.POTATO_GUN)); // potato gun for single player

        swordAsset = WeaponModels.buildSword(WeaponModels.SwordVariant.DIAMOND);
        sword = new ModelInstance(swordAsset.model);
        gunAsset = WeaponModels.buildGun();
        gun = new ModelInstance(gunAsset.model);
        rightArm = instance.getNode(MinecraftPlayerModel.ARM_L, true);

        potatoTex = WeaponModels.buildPotatoTexture();
        potatoModel = WeaponModels.buildPotatoBox(potatoTex);
        for (int i = 0; i < MAX_POTATO; i++) potatoes[i] = new PotatoProjectile(new ModelInstance(potatoModel));

        pickWander();
        applyTransform();
    }

    /** Solid grid so the rival collides with walls/fences like the player (null = flat void). */
    public void setCollider(BlockCollider collider) {
        this.collider = collider;
        pathfinder.setCollider(collider);
    }

    /**
     * Scales max HP and movement speeds to raise the challenge for high win-rate players.
     * Call once after construction before the first update.
     */
    public void setDifficultyScale(float scale) {
        if (scale <= 1.01f) {
            // Easy mode: 10 health (5 hearts)
            maxHealth = EASY_MODE_HEALTH;
            health = EASY_MODE_HEALTH;
        } else {
            maxHealth *= scale;
            health    *= scale;
        }
        speedScale = 1.0f + (scale - 1.0f) * 0.25f;
    }

    public void setRegenParams(float rate, float delay) {
        this.regenRate = rate;
        this.regenDelay = delay;
    }

    public int reloadSegments() {
        if (equipped == Loadout.GUN) return 3;
        if (equipped == Loadout.FIST) return 2;
        return 4; // SWORD variant
    }

    public float reloadSecondsPerSegment() {
        if (equipped == Loadout.GUN) return 0.9f;
        if (equipped == Loadout.FIST) return 0.6f;
        return 0.7f; // SWORD variant
    }

    private void tickAmmo(float delta) {
        int cap = reloadSegments();
        if (equipped != prevEquipped) {
            boolean firstEquip = prevEquipped == null;
            prevEquipped = equipped;
            ammoCapacity = cap;
            ammoRefillTimer = 0f;
            if (firstEquip) {
                ammo = cap;
            } else {
                ammo = 0;
            }
        } else if (cap != ammoCapacity) {
            ammoCapacity = cap;
        }
        if (ammo < ammoCapacity) {
            ammoRefillTimer += delta;
            float per = reloadSecondsPerSegment();
            while (ammoRefillTimer >= per && ammo < ammoCapacity) {
                ammo++;
                ammoRefillTimer -= per;
            }
        }
    }

    public int getAmmo() { return ammo; }
    public int getAmmoCapacity() { return ammoCapacity; }
    public boolean pollDryFire() { boolean b = dryFire; dryFire = false; return b; }

    public void setArmorInventory(Inventory inventory) {
        this.gear = inventory;
        if (this.armor != null) {
            this.armor.dispose();
        }
        this.armor = new ArmorRenderer(inventory);
    }

    public void setMatchStats(MatchStats stats) { this.matchStats = stats; }
    public void setHazardChecker(BiPredicate<Float, Float> hazardChecker) { this.hazardChecker = hazardChecker; }

    public void setUiSelectedGunSupplier(java.util.function.BooleanSupplier supplier) {
        this.uiSelectedGunSupplier = supplier;
    }

    public void setAimDegFromMouseSupplier(java.util.function.Supplier<Float> supplier) {
        this.aimDegFromMouseSupplier = supplier;
    }

    public boolean isSwordEquipped() {
        return equipped == Loadout.SWORD;
    }

    public int getHeldType() {
        if (equipped == Loadout.SWORD) return 1;
        return 0;
    }

    /** Worn armour inventory (for UI previews and stat reduction). */
    public Inventory armorInventory() { return gear; }

    private void pickWander() {
        wander.set(MathUtils.random(minX, maxX), 0f, MathUtils.random(minZ, maxZ));
        wanderTimer = MathUtils.random(2f, 4f);
    }

    /** Drive the rival: it deals melee + ranged hits to {@code player} directly. */
    public void update(float delta, Player player) {
        if (gone) { updatePotatoes(delta, player); return; }
        if (dying) {                                   // playing the death tip-over → no AI, no attacks
            deathTimer = Math.max(0f, deathTimer - delta);
            // let any in-flight knockback slump the body, then settle
            pos.x = MathUtils.clamp(pos.x + knock.x * delta, minX, maxX);
            pos.z = MathUtils.clamp(pos.z + knock.z * delta, minZ, maxZ);
            knock.scl((float) Math.exp(-KB_DAMP * delta));
            applyDeathTransform();
            applyTint();
            hearts.update(delta);
            updatePotatoes(delta, player);
            if (deathTimer <= 0f) gone = true;
            return;
        }
        if (hurtTimer > 0f) hurtTimer = Math.max(0f, hurtTimer - delta);
        if (flashTimer > 0f) flashTimer = Math.max(0f, flashTimer - delta);
        if (cooldownTimer > 0f) cooldownTimer -= delta;
        if (rangedCooldown > 0f) rangedCooldown -= delta;
        if (lungeTimer > 0f) lungeTimer = Math.max(0f, lungeTimer - delta);
        if (sprintJumpCooldown > 0f) sprintJumpCooldown -= delta;

        tickAmmo(delta);

        if (regenCooldown > 0f) regenCooldown = Math.max(0f, regenCooldown - delta);
        else if (health < maxHealth) {
            float before = health;
            health = Math.min(maxHealth, health + regenRate * delta);
            if (matchStats != null) matchStats.addHealing(health - before);
        }

        // Vertical physics (jumps / leap-attacks).
        vy -= GRAVITY * delta;
        pos.y += vy * delta;
        if (pos.y <= 0f) { pos.y = 0f; vy = 0f; onGround = true; } else onGround = false;

        boolean stunned = stunTimer > 0f;
        if (stunned) stunTimer = Math.max(0f, stunTimer - delta);

        Vector3 pp = player.getPosition();
        float dx = pp.x - pos.x, dz = pp.z - pos.z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);

        if (!stunned) {
        if (attacking) {
            faceToward(dx, dz);
            airDrag(delta);
            attackTimer -= delta;
            if (attackTimer <= 0f) {
                boolean crit = pos.y > 0.25f && vy < 0f;        // airborne + descending → critical
                attacking = false;
                lungeTimer = ATTACK_LUNGE;
                cooldownTimer = ATTACK_COOLDOWN;
                if (dist < MELEE_HIT_RANGE && CombatLoS.clear(collider, pos.x, pos.z, pp.x, pp.z)) {
                    dir.set(dx, 0f, dz).nor();
                    float dmg = MELEE_DAMAGE * (crit ? CRIT_MULT : 1f);
                    player.applyHit(dmg, dir, crit ? KB_CRIT : KB_TO_PLAYER);
                    if (matchStats != null) matchStats.addDamage(dmg);
                }
            }
        } else if (dist < MELEE_RANGE && cooldownTimer <= 0f && onGround) {
            if (ammo >= 1) {
                // Leap attack: hop toward the player; the strike lands as we come down → crit.
                state = State.ATTACK;
                attacking = true;
                altHand = !altHand;
                AudioManager.get().swing();
                attackTimer = ATTACK_WINDUP;
                vy = JUMP_V; onGround = false;
                float l = Math.max(dist, 0.001f);
                vel.set(dx / l * RUN_SPEED * 1.25f, 0f, dz / l * RUN_SPEED * 1.25f);
                faceToward(dx, dz);
                ammo--;
            }
        } else if (dist < AGGRO_RANGE) {
            state = State.CHASE;
            // Weapon inventory: sword up close, potato gun at range.
            equipped = dist > RANGED_MIN ? Loadout.GUN : Loadout.SWORD;
            if (equipped == Loadout.GUN && onGround && dist < RANGED_MAX && rangedCooldown <= 0f) {
                if (ammo >= 1) {
                    fireGun(dx, dz, dist);
                    rangedCooldown = RANGED_CD;
                    ammo--;
                }
            }
            float chaseSpeed = dist > STRAFE_RANGE ? RUN_SPEED * CHASE_SPRINT_MUL * speedScale : STRAFE_SPEED * speedScale;
            if (dist > STRAFE_RANGE) {
                // Periodical sprint-jump when closing from distance
                if (onGround && dist > SPRINT_JUMP_DIST && sprintJumpCooldown <= 0f) {
                    vy = JUMP_V; onGround = false;
                    sprintJumpCooldown = MathUtils.random(1.5f, 2.5f);
                }
                // Use A* pathfinding when LoS is blocked so the bot routes around walls.
                boolean hasLoS = CombatLoS.clear(collider, pos.x, pos.z, pp.x, pp.z);
                pathRecalcTimer -= delta;
                if (!hasLoS && pathRecalcTimer <= 0f) {
                    path = pathfinder.findPath(pos.x, pos.z, pp.x, pp.z);
                    pathRecalcTimer = PATH_RECALC_INTERVAL;
                }
                if (!hasLoS && !path.isEmpty()) {
                    // Advance through waypoints; pop once within 0.7 blocks.
                    com.badlogic.gdx.math.Vector2 wp = path.get(0);
                    float wdx = wp.x - pos.x, wdz = wp.y - pos.z;
                    if (wdx * wdx + wdz * wdz < 0.7f * 0.7f) path.remove(0);
                    if (!path.isEmpty()) {
                        wp = path.get(0);
                        setCombatSteer(wp.x - pos.x, wp.y - pos.z, chaseSpeed * 1.1f);
                    } else {
                        // Path exhausted but still no LoS — try perpendicular to find a gap
                        setCombatSteer(dx, dz, chaseSpeed);
                    }
                } else if (!hasLoS) {
                    // No path found and no LoS — try strafing perpendicular to find a wall gap
                    strafe(delta, dx, dz);
                } else {
                    path.clear(); // clear stale path once LoS is restored
                    setCombatSteer(dx, dz, chaseSpeed);
                }
            } else {
                strafe(delta, dx, dz);
            }
        } else {
            state = State.WANDER;
            wanderTimer -= delta;
            if (wanderTimer <= 0f) pickWander();
            setCombatSteer(wander.x - pos.x, wander.z - pos.z, WALK_SPEED);
        }
        } // end !stunned

        integrate(delta);
        applyTransform();
        float horizSpeed = (float) Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        boolean running = state == State.CHASE || state == State.ATTACK;
        prepareArmPose();
        animator.update(delta, horizSpeed, running, false, onGround, armPose);
        instance.calculateTransforms();
        anchorWeapon();
        applyTint();
        hearts.update(delta);
        updatePotatoes(delta, player);
    }

    /** Drive the rival from LAN player input instead of AI decisions. Host-side only. */
    public void updateRemote(float delta, boolean forward, boolean backward, boolean left, boolean right,
                             boolean jump, boolean sprint, boolean attack, boolean gunHeld,
                             float aimDeg, Player player) {
        // POTATO_GUN removed for multiplayer - only sword
        equipped = Loadout.SWORD;
        if (gone) return;
        if (dying) {
            deathTimer = Math.max(0f, deathTimer - delta);
            applyDeathTransform();
            applyTint();
            hearts.update(delta);
            if (deathTimer <= 0f) gone = true;
            return;
        }

        if (hurtTimer > 0f) hurtTimer = Math.max(0f, hurtTimer - delta);
        if (flashTimer > 0f) flashTimer = Math.max(0f, flashTimer - delta);
        if (cooldownTimer > 0f) cooldownTimer -= delta;
        if (lungeTimer > 0f) lungeTimer = Math.max(0f, lungeTimer - delta);

        tickAmmo(delta);

        if (regenCooldown > 0f) regenCooldown = Math.max(0f, regenCooldown - delta);
        else if (health < maxHealth) {
            float before = health;
            health = Math.min(maxHealth, health + regenRate * delta);
            if (matchStats != null) matchStats.addHealing(health - before);
        }

        vy -= GRAVITY * delta;
        pos.y += vy * delta;
        if (pos.y <= 0f) { pos.y = 0f; vy = 0f; onGround = true; } else onGround = false;

        float mx = (right ? 1f : 0f) - (left ? 1f : 0f);
        float mz = (backward ? 1f : 0f) - (forward ? 1f : 0f);
        float moveLen2 = mx * mx + mz * mz;
        if (moveLen2 > 0.001f) {
            float speed = (sprint ? RUN_SPEED * CHASE_SPRINT_MUL : RUN_SPEED) * speedScale;
            setSteer(mx, mz, speed);
        } else {
            float stop = (float) Math.exp(-ACCEL * delta);
            vel.x *= stop;
            vel.z *= stop;
        }
        if (!Float.isNaN(aimDeg)) facingDeg = aimDeg;
        if (jump && onGround) { vy = JUMP_V; onGround = false; }

        // POTATO_GUN removed - only sword attacks in multiplayer
        if (attack && !remoteAttackHeld && cooldownTimer <= 0f) {
            if (ammo >= 1) {
                state = State.ATTACK;
                attacking = true;
                altHand = !altHand;
                AudioManager.get().swing();
                attackTimer = ATTACK_WINDUP;
                ammo--;
            } else {
                dryFire = true;
            }
        }
        remoteAttackHeld = attack;

        if (attacking) {
            attackTimer -= delta;
            if (attackTimer <= 0f) {
                attacking = false;
                lungeTimer = ATTACK_LUNGE;
                cooldownTimer = 0.05f; // remote player: no extra post-swing delay (ammo system limits rate, matching local player)
                Vector3 pp = player.getPosition();
                float dx = pp.x - pos.x, dz = pp.z - pos.z;
                float dist = (float) Math.sqrt(dx * dx + dz * dz);
                boolean crit = pos.y > 0.25f && vy < 0f;
                if (dist < MELEE_HIT_RANGE && CombatLoS.clear(collider, pos.x, pos.z, pp.x, pp.z)) {
                    dir.set(dx, 0f, dz).nor();
                    // Use the same raw damage as the host's diamond sword (WeaponController.meleeBaseDamage)
                    // so both players deal identical pre-armour damage and the fight is symmetric.
                    float dmg = 6f * (crit ? 1.5f : 1f);   // 6 normal, 9 crit  (same as WeaponController)
                    player.applyHit(dmg, dir, crit ? KB_CRIT : 0.8f); // 0.8 matches Player.KB_H
                    if (matchStats != null) matchStats.addDamage(dmg);
                }
            }
        }

        integrate(delta);
        applyTransform();
        float horizSpeed = moveLen2 > 0.001f ? (sprint ? RUN_SPEED * CHASE_SPRINT_MUL : RUN_SPEED) : 0f;
        prepareArmPose();
        animator.update(delta, horizSpeed, sprint, false, onGround, armPose);
        instance.calculateTransforms();
        anchorWeapon();
        applyTint();
        hearts.update(delta);
        updatePotatoes(delta, player);
    }

    /** Apply an authoritative network snapshot for client-side rendering. */
    public void setNetworkSnapshot(float x, float y, float z, float health, float facingDeg, boolean eliminated) {
        setNetworkSnapshot(x, y, z, health, facingDeg, eliminated, false, false, false, 0);
    }

    /** Apply an authoritative network snapshot plus visual animation state for client-side rendering. */
    public void setNetworkSnapshot(float x, float y, float z, float health, float facingDeg, boolean eliminated,
                                   boolean moving, boolean sprinting, boolean attacking, int heldType) {
        targetPos.set(x, y, z);
        boolean isLocalGuest = uiSelectedGunSupplier != null;
        if (isLocalGuest) {
            if (pos.dst2(targetPos) > 4f) {
                pos.set(targetPos);
                vel.set(0f, 0f, 0f);
                knock.set(0f, 0f, 0f);
                vy = 0f;
            }
        } else {
            if (pos.dst2(targetPos) > 16f) {
                pos.set(targetPos);
            }
            vel.set(0f, 0f, 0f);
            knock.set(0f, 0f, 0f);
            vy = 0f;
        }
        this.health = MathUtils.clamp(health, 0f, maxHealth);
        this.facingDeg = facingDeg;
        // POTATO_GUN removed - only sword
        equipped = heldType == 1 ? Loadout.SWORD : Loadout.FIST;

        boolean justAttacked = attacking && !prevNetworkAttacking;
        prevNetworkAttacking = attacking;
        if (justAttacked) {
            // POTATO_GUN removed - only sword
            if (!this.attacking) {
                this.attacking = true;
                altHand = !altHand;
                attackTimer = ATTACK_WINDUP;
            }
        }

        if (eliminated || this.health <= 0f) {
            if (!dying && !gone) startDeath();
        } else {
            dying = false;
            gone = false;
            deathTimer = 0f;
        }
        applyTransform();
        clientSpeed = moving ? (sprinting ? RUN_SPEED * CHASE_SPRINT_MUL : RUN_SPEED) : 0f;
        clientSprinting = sprinting;
        applyTint();
    }

    /** Smoothly update visual state (animations, potato projectiles, hearts, hurt flashes) on the client. */
    public void updateClient(float delta, Player player, boolean controllable) {
        if (gone) { updatePotatoes(delta, player); return; }
        if (dying) {
            deathTimer = Math.max(0f, deathTimer - delta);
            applyDeathTransform();
            applyTint();
            hearts.update(delta);
            updatePotatoes(delta, player);
            if (deathTimer <= 0f) gone = true;
            return;
        }
        if (hurtTimer > 0f) hurtTimer = Math.max(0f, hurtTimer - delta);
        if (flashTimer > 0f) flashTimer = Math.max(0f, flashTimer - delta);
        if (cooldownTimer > 0f) cooldownTimer -= delta;
        if (rangedCooldown > 0f) rangedCooldown -= delta;

        tickAmmo(delta);

        if (regenCooldown > 0f) regenCooldown = Math.max(0f, regenCooldown - delta);
        else if (health < maxHealth) {
            health = Math.min(maxHealth, health + regenRate * delta);
        }

        if (controllable) {
            Settings cfg = Settings.get();
            boolean forward = Gdx.input.isKeyPressed(cfg.key(Settings.Action.FORWARD));
            boolean backward = Gdx.input.isKeyPressed(cfg.key(Settings.Action.BACKWARD));
            boolean left = Gdx.input.isKeyPressed(cfg.key(Settings.Action.LEFT));
            boolean right = Gdx.input.isKeyPressed(cfg.key(Settings.Action.RIGHT));
            boolean jump = Gdx.input.isKeyPressed(cfg.key(Settings.Action.JUMP));
            boolean sprint = jump && (forward || backward || left || right);

            vy -= GRAVITY * delta;
            pos.y += vy * delta;
            if (pos.y <= 0f) { pos.y = 0f; vy = 0f; onGround = true; } else onGround = false;

            float mx = (right ? 1f : 0f) - (left ? 1f : 0f);
            float mz = (backward ? 1f : 0f) - (forward ? 1f : 0f);
            float moveLen2 = mx * mx + mz * mz;
            boolean moving = moveLen2 > 0.001f;
            if (moving) {
                float speed = (sprint ? RUN_SPEED * CHASE_SPRINT_MUL : RUN_SPEED) * speedScale;
                setSteer(mx, mz, speed);
            } else {
                float stop = (float) Math.exp(-ACCEL * delta);
                vel.x *= stop;
                vel.z *= stop;
            }
            if (jump && onGround) { vy = JUMP_V; onGround = false; }

            integrate(delta);

            pos.lerp(targetPos, Math.min(1f, delta * 6f));

            float localAim = aimDegFromMouseSupplier != null ? aimDegFromMouseSupplier.get() : Float.NaN;
            if (!Float.isNaN(localAim)) {
                facingDeg = localAim;
            }

            boolean click = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
            if (click && !prevLocalAttackClick) {
                // POTATO_GUN removed for multiplayer - only sword attacks
                if (cooldownTimer <= 0f && !attacking) {
                    if (ammo >= 1) {
                        attacking = true;
                        altHand = !altHand;
                        attackTimer = ATTACK_WINDUP;
                        cooldownTimer = ATTACK_COOLDOWN;
                        AudioManager.get().swing();
                        ammo--;
                    } else {
                        dryFire = true;
                    }
                }
            }
            prevLocalAttackClick = click;
            clientSpeed = moving ? (sprint ? RUN_SPEED * CHASE_SPRINT_MUL : RUN_SPEED) : 0f;
            clientSprinting = sprint;
        } else {
            pos.lerp(targetPos, Math.min(1f, delta * 20f));
        }

        if (attacking) {
            attackTimer -= delta;
            if (attackTimer <= 0f) {
                attacking = false;
            }
        }
        applyTransform();
        prepareArmPose();
        animator.update(delta, clientSpeed, clientSprinting, false, onGround, armPose);
        instance.calculateTransforms();
        anchorWeapon();
        applyTint();
        hearts.update(delta);
        updatePotatoes(delta, player);
    }

    /** Steer the velocity smoothly toward (dirX,dirZ) at {@code speed} (momentum, no snapping). */
    private void setSteer(float dirX, float dirZ, float speed) {
        float len = (float) Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (len < 0.05f) { airDrag(0f); return; }
        dirX /= len; dirZ /= len;
        float k = Math.min(1f, ACCEL * 0.016f); // per-step steering blend (frame-rate tolerant enough here)
        vel.x = MathUtils.lerp(vel.x, dirX * speed, k);
        vel.z = MathUtils.lerp(vel.z, dirZ * speed, k);
        faceToward(vel.x, vel.z);
    }

    private void setCombatSteer(float dirX, float dirZ, float speed) {
        if (wouldEnterHazard(dirX, dirZ)) {
            // Try both perpendicular directions before giving up. This keeps pressure on the player
            // without charging straight into the gas wall.
            if (!wouldEnterHazard(-dirZ, dirX)) { setSteer(-dirZ, dirX, speed); return; }
            if (!wouldEnterHazard(dirZ, -dirX)) { setSteer(dirZ, -dirX, speed); return; }
            setSteer(0f, 0f, 0f);
            return;
        }
        setSteer(dirX, dirZ, speed);
    }

    private boolean wouldEnterHazard(float dirX, float dirZ) {
        if (hazardChecker == null) return false;
        float len = (float)Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (len < 0.001f) return false;
        float lookAhead = 1.3f;
        float nx = pos.x + dirX / len * lookAhead;
        float nz = pos.z + dirZ / len * lookAhead;
        return hazardChecker.test(nx, nz);
    }

    /** Circle-strafe the player: move perpendicular to the line of sight with a slight inward pull. */
    private void strafe(float delta, float dx, float dz) {
        strafeTimer -= delta;
        if (strafeTimer <= 0f) { strafeDir = MathUtils.randomBoolean() ? 1 : -1; strafeTimer = MathUtils.random(0.8f, 1.8f); }
        float len = Math.max((float) Math.sqrt(dx * dx + dz * dz), 0.001f);
        float nx = dx / len, nz = dz / len;
        float px = -nz * strafeDir, pz = nx * strafeDir;     // perpendicular
        setCombatSteer(px + nx * 0.35f, pz + nz * 0.35f, STRAFE_SPEED);
        faceToward(dx, dz);                                  // keep facing the player while strafing
    }

    private static final float AIR_DRAG = 1.4f; // light, so a leap-attack actually carries forward
    private void airDrag(float delta) {
        float damp = (float) Math.exp(-AIR_DRAG * Math.max(delta, 0f));
        vel.x *= damp; vel.z *= damp;
    }

    /** Move by steering + knockback, resolved against walls per-axis, then clamped to bounds. */
    private void integrate(float delta) {
        float mvx = vel.x + knock.x, mvz = vel.z + knock.z;
        pos.x += mvx * delta; if (collider != null) resolveAxisX(mvx);
        pos.z += mvz * delta; if (collider != null) resolveAxisZ(mvz);
        pos.x = MathUtils.clamp(pos.x, minX, maxX);
        pos.z = MathUtils.clamp(pos.z, minZ, maxZ);
        if (knock.len2() > 1e-4f) { float damp = (float) Math.exp(-KB_DAMP * delta); knock.x *= damp; knock.z *= damp; }
    }

    private void resolveAxisX(float mvx) {
        float half = HITBOX_HALF, cellHalf = collider.cellSize() * 0.5f;
        int r0 = collider.rowAt(pos.z - half), r1 = collider.rowAt(pos.z + half);
        for (int r = r0; r <= r1; r++) {
            int c0 = collider.colAt(pos.x - half), c1 = collider.colAt(pos.x + half);
            for (int c = c0; c <= c1; c++) {
                float blockH = collider.collisionHeightAt(c, r);
                if (blockH <= pos.y + 0.05f) continue;
                float cMinX = collider.cellCenterX(c) - cellHalf, cMaxX = collider.cellCenterX(c) + cellHalf;
                float cMinZ = collider.cellCenterZ(r) - cellHalf, cMaxZ = collider.cellCenterZ(r) + cellHalf;
                if (pos.x + half <= cMinX || pos.x - half >= cMaxX) continue;
                if (pos.z + half <= cMinZ || pos.z - half >= cMaxZ) continue;
                if (mvx > 0f) pos.x = cMinX - half; else if (mvx < 0f) pos.x = cMaxX + half;
                if (blockH - pos.y <= 1.2f && onGround) {
                    // obstacle is ≤1 block tall — hop over it
                    vy = JUMP_V; onGround = false;
                } else {
                    vel.x = 0f; knock.x = 0f;
                }
            }
        }
    }

    private void resolveAxisZ(float mvz) {
        float half = HITBOX_HALF, cellHalf = collider.cellSize() * 0.5f;
        int c0 = collider.colAt(pos.x - half), c1 = collider.colAt(pos.x + half);
        for (int c = c0; c <= c1; c++) {
            int r0 = collider.rowAt(pos.z - half), r1 = collider.rowAt(pos.z + half);
            for (int r = r0; r <= r1; r++) {
                float blockH = collider.collisionHeightAt(c, r);
                if (blockH <= pos.y + 0.05f) continue;
                float cMinX = collider.cellCenterX(c) - cellHalf, cMaxX = collider.cellCenterX(c) + cellHalf;
                float cMinZ = collider.cellCenterZ(r) - cellHalf, cMaxZ = collider.cellCenterZ(r) + cellHalf;
                if (pos.x + half <= cMinX || pos.x - half >= cMaxX) continue;
                if (pos.z + half <= cMinZ || pos.z - half >= cMaxZ) continue;
                if (mvz > 0f) pos.z = cMinZ - half; else if (mvz < 0f) pos.z = cMaxZ + half;
                if (blockH - pos.y <= 1.2f && onGround) {
                    // obstacle is ≤1 block tall — hop over it
                    vy = JUMP_V; onGround = false;
                } else {
                    vel.z = 0f; knock.z = 0f;
                }
            }
        }
    }

    private void faceToward(float dx, float dz) {
        if (dx * dx + dz * dz < 1e-4f) return;
        facingDeg = MathUtils.atan2(-dx, -dz) * MathUtils.radiansToDegrees; // facing 0 => front (-Z)
    }

    private void fireGun(float dx, float dz, float dist) {
        PotatoProjectile p = null;
        for (PotatoProjectile cand : potatoes) if (!cand.isAlive()) { p = cand; break; }
        if (p == null) return;
        float len = Math.max(dist, 0.001f);
        AudioManager.get().shoot();
        float fx = dx / len, fz = dz / len;
        muzzle.set(pos.x + fx * 0.6f, 1.3f, pos.z + fz * 0.6f);
        launchVel.set(fx * GUN_SPEED, 0f, fz * GUN_SPEED);
        p.launch(muzzle, launchVel, MathUtils.clamp(dist, 3f, 12f));
    }

    private static final float WINDUP     = 0.30f;
    private static final float SNAP_EXP   = 8f;
    private static final float ARM_PITCH_HI = 140f, ARM_PITCH_LO = 55f;
    private static final float WINDUP_DEG = 35f;
    private static final float DRIVE_DEG  = 55f;
    private static final float SWORD_READY_PITCH = 75f;
    private static final float SWORD_READY_YAW = 6f;
    private static final float SWORD_READY_ROLL = 0f;
    private static final float SWORD_READY_BLEND = 0.2f;
    private static final float D2R = MathUtils.degreesToRadians;

    private static float smoothstep(float t) {
        return t * t * (3f - 2f * t);
    }

    private void prepareArmPose() {
        armPose.reset();
        if (equipped == Loadout.GUN) {
            armPose(armPose.lRot, GUN_R_PITCH, GUN_R_YAW, GUN_R_ROLL);
            armPose.lWeight = 1f;
            armPose(armPose.rRot, GUN_L_PITCH, GUN_L_YAW, GUN_L_ROLL);
            armPose.rWeight = 1f;
        } else if (equipped == Loadout.SWORD && !attacking) {
            armPose(armPose.lRot, SWORD_READY_PITCH, SWORD_READY_YAW, SWORD_READY_ROLL);
            armPose.lWeight = SWORD_READY_BLEND;
        } else if (attacking) {
            float p = MathUtils.clamp(1f - attackTimer / ATTACK_WINDUP, 0f, 1f);
            if (equipped == Loadout.FIST) {
                // Punch pose
                float ext;
                if (p < 0.45f) ext = 1f - (float) Math.pow(1f - p / 0.45f, 6f);
                else ext = 1f - smoothstep((p - 0.45f) / 0.55f);

                float w;
                if (p < 0.05f) w = p / 0.05f;
                else if (p > 0.6f) w = Math.max(0f, 1f - smoothstep((p - 0.6f) / 0.4f));
                else w = 1f;

                float pitch = MathUtils.lerp(28f, 102f, ext);
                armPose(armPose.lRot, pitch, 8f, 0f);
                armPose.lWeight = Math.max(armPose.lWeight, w);
                armPose.bodyYaw = 16f * ext * D2R;
            } else {
                // Sword swing pose
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
                armPose(armPose.lRot, pitch, sweep, 0f);
                armPose.lWeight = Math.max(armPose.lWeight, w);

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
                armPose.bodyYaw = drive * twist * D2R;
            }
        }
    }

    private void armPose(Quaternion out, float pitchDeg, float yawDeg, float rollDeg) {
        qP.setFromAxis(Vector3.X, pitchDeg);
        qY.setFromAxis(Vector3.Y, yawDeg);
        qR.setFromAxis(Vector3.Z, rollDeg);
        out.set(qY).mul(qP).mul(qR);
    }

    private void updatePotatoes(float delta, Player player) {
        Vector3 pp = player.getPosition();
        for (PotatoProjectile p : potatoes) {
            if (!p.isAlive()) continue;
            boolean impacted = p.update(delta, collider);
            if (p.isFlying()) {
                float ddx = pp.x - p.position().x, ddz = pp.z - p.position().z;
                if (ddx * ddx + ddz * ddz < 0.55f * 0.55f && p.position().y > 0.4f && p.position().y < 2.1f
                    && CombatLoS.clear(collider, p.position().x, p.position().z, pp.x, pp.z)) {
                    dir.set(ddx, 0f, ddz).nor();
                    player.applyHit(GUN_DMG, dir, GUN_KB);
                    if (matchStats != null) matchStats.addDamage(GUN_DMG);
                    impactFx.burst(p.position(), 12);
                    p.destroy();
                    continue;
                }
            }
            if (impacted) impactFx.burst(p.position(), 14);
        }
        impactFx.update(delta);
    }

    /** External, directionless damage (e.g. the gas): bypasses armour, flash + lean, no knockback. */
    public void damage(float raw) {
        if (dying || gone) return;
        health = Math.max(0f, health - raw);
        AudioManager.get().hurt();
        regenCooldown = regenDelay;
        hurtTimer = HURT_DUR;
        flashTimer = FLASH_DUR;
        tinted = false;
        if (health <= 0f) startDeath();
    }

    @Override
    public void onHit(float damage, Vector3 fromDir, boolean crit) {
        if (dying || gone) return;
        float dealt = ArmorStats.reduce(damage, gear);
        health = Math.max(0f, health - dealt);
        AudioManager.get().hurt();
        regenCooldown = regenDelay;
        hurtTimer = HURT_DUR;
        flashTimer = FLASH_DUR;
        tinted = false;
        knock.set(fromDir.x, 0f, fromDir.z).nor().scl(crit ? KB_SELF * 1.4f : KB_SELF);
        stunTimer = 0.4f;
        hearts.burstHearts(chestTmp.set(pos.x, pos.y + 1.4f, pos.z), crit ? 14 : 5);
        if (health <= 0f) startDeath();
    }

    /** Begin the Minecraft death sequence: stop fighting, stay red, tip onto the side, then vanish. */
    private void startDeath() {
        dying = true;
        AudioManager.get().gameOver();
        deathTimer = DEATH_DUR;
        attacking = false;
        vel.set(0f, 0f, 0f);
        vy = 0f; pos.y = 0f;
    }

    private void applyTransform() {
        float hurt = hurtTimer / HURT_DUR;
        float fr = facingDeg * MathUtils.degreesToRadians;
        float fwdX = -MathUtils.sin(fr), fwdZ = -MathUtils.cos(fr);
        float lunge = lungeTimer / ATTACK_LUNGE;                       // 1 just after the strike → 0
        float windup = attacking ? 1f - attackTimer / ATTACK_WINDUP : 0f;
        // Clean body matrix: no hurt/windup tilt — used for gun placement.
        cleanBodyMat.setToRotation(Vector3.Y, facingDeg).setTranslation(pos.x, pos.y, pos.z);
        instance.transform.setToRotation(Vector3.Y, facingDeg)
            .setTranslation(pos.x + fwdX * 0.5f * lunge, pos.y, pos.z + fwdZ * 0.5f * lunge)
            .rotate(Vector3.X, 22f * hurt * hurt - 14f * windup + 30f * lunge);
    }

    /** Place the active weapon using the same offsets as {@link com.brawlgame.combat.WeaponController}. */
    private void anchorWeapon() {
        if (equipped == Loadout.GUN) {
            WeaponAnchors.placeGun(gun.transform, cleanBodyMat);
        } else if (equipped == Loadout.SWORD) {
            WeaponAnchors.placeSword(weaponMat, instance.transform, rightArm.globalTransform);
            sword.transform.set(weaponMat);
        }
    }

    /** Death tip-over: rotate the body 90° onto its side about its feet (eased), staying red. */
    private void applyDeathTransform() {
        float p = 1f - deathTimer / DEATH_DUR;          // 0 → 1 over the death
        float roll = 90f * (1f - (1f - p) * (1f - p));  // ease-out toward flat-on-side
        instance.transform.setToRotation(Vector3.Y, facingDeg)
            .setTranslation(pos)
            .rotate(Vector3.Z, roll);
        instance.calculateTransforms();
        anchorWeapon();
    }

    private void applyTint() {
        boolean hurt = flashTimer > 0f || dying; // pure-red flash, and held red through the death
        if (hurt == tinted) return;
        tinted = hurt;
        for (Material m : instance.materials) {
            if (hurt) m.set(redTint);
            else m.remove(ColorAttribute.Diffuse);
        }
    }

    public void render(ModelBatch batch, Environment env) {
        if (!gone) {                               // keep drawing through the death tip-over
            batch.render(instance, env);
            armor.render(batch, env, instance);
            if (equipped == Loadout.GUN && !attacking) batch.render(gun, env);
            else batch.render(sword, env);
            hearts.render(batch);
        }
        for (PotatoProjectile p : potatoes) p.render(batch, env); // potatoes outlive the bot's death frame
        impactFx.render(batch, env);
    }

    /** True only once the death animation has fully finished (the rival is removed from play). */
    public boolean isDead()   { return gone; }
    /** True while the death tip-over is playing (used to drop the overhead nameplate). */
    public boolean isDying()  { return dying; }
    public float health()     { return health; }
    public float maxHealth()  { return maxHealth; }
    public float facingDeg()  { return facingDeg; }
    public boolean isAttacking() { return attacking; }
    /** 0 = not hurt, 1 = just hit — drives the red screen vignette on CLIENT. */
    public float hurtFraction() { return Math.max(0f, hurtTimer / HURT_DUR); }
    public boolean isGunEquipped() { return equipped == Loadout.GUN; }
    public boolean isMoving() { return vel.x * vel.x + vel.z * vel.z > 0.08f; }
    @Override public Vector3 position() { return pos; }
    @Override public float radius()     { return HIT_RADIUS; }

    @Override
    public void dispose() {
        model.dispose();
        hearts.dispose();
        armor.dispose();
        swordAsset.dispose();
        gunAsset.dispose();
        potatoModel.dispose();
        potatoTex.dispose();
        impactFx.dispose();
    }
}
