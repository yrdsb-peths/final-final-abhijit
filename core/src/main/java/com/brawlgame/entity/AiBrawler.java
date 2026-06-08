package com.brawlgame.entity;

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
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.math.Matrix4;

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

    private enum State { WANDER, CHASE, ATTACK, FLEE, HEAL }
    private enum Loadout { SWORD, GUN }

    private static final float HITBOX_HALF = 0.35f;
    private static final float HIT_RADIUS = 0.6f;
    private static final float HURT_DUR = 0.35f, FLASH_DUR = 0.2f;
    private static final float DEATH_DUR = 0.9f; // Minecraft death: tip onto side, red, then vanish

    private static final float AGGRO_RANGE = 12f;
    private static final float MELEE_RANGE = 1.7f, MELEE_HIT_RANGE = 2.3f;
    private static final float RUN_SPEED = 2.35f, WALK_SPEED = 1.2f, STRAFE_SPEED = 2.0f, STRAFE_RANGE = 3.6f;
    private static final float CHASE_SPRINT_MUL = 1.18f;
    private static final float FLEE_SPEED = 2.8f, FLEE_THRESHOLD = 0.30f; // flee at 30% HP
    private static final float FLEE_DIST = 6f;    // run this far before healing
    private static final float HEAL_AMOUNT = 4f, HEAL_DURATION = 3.0f;
    private static final float SPRINT_JUMP_DIST = 4.5f; // jump when closing from this range
    private static final float ACCEL = 14f;

    private static final float ATTACK_WINDUP = 0.42f;  // jump apex falls inside this → strike while descending
    private static final float ATTACK_LUNGE = 0.18f, ATTACK_COOLDOWN = 1.0f;
    private static final float MELEE_DAMAGE = 5f, CRIT_MULT = 1.4f;

    private static final float GRAVITY = 22f, JUMP_V = 7.2f;

    private static final float KB_SELF = 7.5f, KB_DAMP = 7f;        // knockback imparted TO this bot
    private static final float KB_TO_PLAYER = 0.5f, KB_CRIT = 0.85f; // impulse strength onto the player

    private static final float RANGED_MIN = 3.8f, RANGED_MAX = 11f, RANGED_CD = 0.85f;
    private static final float GUN_DMG = 5f, GUN_SPEED = 12f, GUN_KB = 0.35f;

    private static final Color HURT_TINT = new Color(1f, 0f, 0f, 1f);       // pure red

    private final Model model;
    private final ModelInstance instance;
    private final PlayerAnimator animator;
    private final Vector3 pos = new Vector3();
    private final Vector3 vel = new Vector3();   // steering velocity (blocks/sec)
    private final Vector3 knock = new Vector3(); // decaying knockback velocity (blocks/sec)
    private float vy;
    private boolean onGround = true;
    private float facingDeg, maxHealth = 20f, health = 20f; // same HP pool as the player
    // Clean body transform (no hurt tilt) used for gun placement so the gun sits correctly.
    private final Matrix4 cleanBodyMat = new Matrix4();
    private float speedScale = 1f;

    private State state = State.WANDER;
    private float hurtTimer, flashTimer;
    private float attackTimer, lungeTimer, cooldownTimer, rangedCooldown;
    private float fleeTimer = 0f, healTimer = 0f;
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

    // ---- worn armour (visible layer + damage reduction) ----
    private final Inventory gear = new Inventory();
    private final ArmorRenderer armor;

    // ---- inventory + held weapons (same offsets as the player) ----
    private final Inventory loadout = new Inventory();
    private Loadout equipped = Loadout.SWORD;
    private final WeaponModels.SwordAsset swordAsset;
    private final ModelInstance sword;
    private final WeaponModels.GunAsset gunAsset;
    private final ModelInstance gun;
    private final Node rightArm;
    private final Matrix4 weaponMat = new Matrix4();
    private MatchStats matchStats;

    // ---- ranged potatoes ----
    private static final int MAX_POTATO = 6;
    private final PotatoProjectile[] potatoes = new PotatoProjectile[MAX_POTATO];
    private final Model potatoModel;
    private final Texture potatoTex;
    private final BlockParticles impactFx = new BlockParticles();
    private final Vector3 muzzle = new Vector3();
    private final Vector3 launchVel = new Vector3();

    public AiBrawler(Texture skin, float x, float z, float minX, float maxX, float minZ, float maxZ) {
        model = MinecraftPlayerModel.build(skin);
        instance = new ModelInstance(model);
        animator = new PlayerAnimator(instance);
        this.minX = minX; this.maxX = maxX; this.minZ = minZ; this.maxZ = maxZ;
        pathfinder = new BotPathfinder(minX, maxX, minZ, maxZ);
        pos.set(x, 0f, z);
        tinted = false;

        gear.set(Inventory.ARMOR_BASE + 0, new ItemStack(ItemType.IRON_HELMET));
        gear.set(Inventory.ARMOR_BASE + 1, new ItemStack(ItemType.IRON_CHESTPLATE));
        gear.set(Inventory.ARMOR_BASE + 2, new ItemStack(ItemType.IRON_LEGGINGS));
        gear.set(Inventory.ARMOR_BASE + 3, new ItemStack(ItemType.IRON_BOOTS));
        armor = new ArmorRenderer(gear);

        loadout.set(Inventory.HOTBAR_BASE + 0, new ItemStack(ItemType.DIAMOND_SWORD));
        loadout.set(Inventory.HOTBAR_BASE + 1, new ItemStack(ItemType.POTATO_GUN));

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
        maxHealth *= scale;
        health    *= scale;
        speedScale = scale;
    }

    public void setMatchStats(MatchStats stats) { this.matchStats = stats; }

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

        // Vertical physics (jumps / leap-attacks).
        vy -= GRAVITY * delta;
        pos.y += vy * delta;
        if (pos.y <= 0f) { pos.y = 0f; vy = 0f; onGround = true; } else onGround = false;

        Vector3 pp = player.getPosition();
        float dx = pp.x - pos.x, dz = pp.z - pos.z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);

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
            // Leap attack: hop toward the player; the strike lands as we come down → crit.
            state = State.ATTACK;
            attacking = true;
            attackTimer = ATTACK_WINDUP;
            vy = JUMP_V; onGround = false;
            float l = Math.max(dist, 0.001f);
            vel.set(dx / l * RUN_SPEED * 1.25f, 0f, dz / l * RUN_SPEED * 1.25f);
            faceToward(dx, dz);
        } else if (state == State.FLEE || (health / maxHealth < FLEE_THRESHOLD && state != State.HEAL)) {
            // Low HP — run away from player until safe distance, then heal.
            state = State.FLEE;
            fleeTimer += delta;
            setSteer(-dx, -dz, FLEE_SPEED); // run opposite direction
            if (dist > FLEE_DIST || fleeTimer > 4f) {
                state = State.HEAL;
                healTimer = HEAL_DURATION;
                fleeTimer = 0f;
            }
        } else if (state == State.HEAL) {
            // Stand still and recover HP.
            healTimer -= delta;
            setSteer(0, 0, 0);
            health = Math.min(maxHealth, health + (HEAL_AMOUNT / HEAL_DURATION) * delta);
            if (healTimer <= 0f) state = State.CHASE;
        } else if (dist < AGGRO_RANGE) {
            state = State.CHASE;
            // Weapon inventory: sword up close, potato gun at range.
            equipped = dist > RANGED_MIN ? Loadout.GUN : Loadout.SWORD;
            if (equipped == Loadout.GUN && onGround && dist < RANGED_MAX && rangedCooldown <= 0f) {
                fireGun(dx, dz, dist);
                rangedCooldown = RANGED_CD;
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
                        setSteer(wp.x - pos.x, wp.y - pos.z, chaseSpeed * 1.1f);
                    } else {
                        setSteer(dx, dz, chaseSpeed);
                    }
                } else {
                    if (hasLoS) path.clear(); // clear stale path once LoS is restored
                    setSteer(dx, dz, chaseSpeed);
                }
            } else {
                strafe(delta, dx, dz);
            }
        } else {
            state = State.WANDER;
            wanderTimer -= delta;
            if (wanderTimer <= 0f) pickWander();
            setSteer(wander.x - pos.x, wander.z - pos.z, WALK_SPEED);
        }

        integrate(delta);
        applyTransform();
        float horizSpeed = (float) Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        boolean running = state == State.CHASE || state == State.ATTACK || state == State.FLEE;
        animator.update(delta, horizSpeed, running, false, onGround, null);
        instance.calculateTransforms();
        anchorWeapon(attacking ? 1f - attackTimer / ATTACK_WINDUP : 0f);
        applyTint();
        hearts.update(delta);
        updatePotatoes(delta, player);
    }

    /** Drive the rival from LAN player input instead of AI decisions. Host-side only. */
    public void updateRemote(float delta, boolean forward, boolean backward, boolean left, boolean right,
                             boolean jump, boolean sprint, boolean attack, float aimDeg, Player player) {
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

        if (attack && !remoteAttackHeld && cooldownTimer <= 0f) {
            state = State.ATTACK;
            attacking = true;
            attackTimer = ATTACK_WINDUP;
        }
        remoteAttackHeld = attack;

        if (attacking) {
            attackTimer -= delta;
            if (attackTimer <= 0f) {
                attacking = false;
                lungeTimer = ATTACK_LUNGE;
                cooldownTimer = ATTACK_COOLDOWN;
                Vector3 pp = player.getPosition();
                float dx = pp.x - pos.x, dz = pp.z - pos.z;
                float dist = (float) Math.sqrt(dx * dx + dz * dz);
                boolean crit = pos.y > 0.25f && vy < 0f;
                if (dist < MELEE_HIT_RANGE && CombatLoS.clear(collider, pos.x, pos.z, pp.x, pp.z)) {
                    dir.set(dx, 0f, dz).nor();
                    float dmg = MELEE_DAMAGE * (crit ? CRIT_MULT : 1f);
                    player.applyHit(dmg, dir, crit ? KB_CRIT : KB_TO_PLAYER);
                    if (matchStats != null) matchStats.addDamage(dmg);
                }
            }
        }

        integrate(delta);
        applyTransform();
        float horizSpeed = moveLen2 > 0.001f ? (sprint ? RUN_SPEED * CHASE_SPRINT_MUL : RUN_SPEED) : 0f;
        animator.update(delta, horizSpeed, sprint, false, onGround, null);
        instance.calculateTransforms();
        anchorWeapon(attacking ? 1f - attackTimer / ATTACK_WINDUP : 0f);
        applyTint();
        hearts.update(delta);
    }

    /** Apply an authoritative network snapshot for client-side rendering. */
    public void setNetworkSnapshot(float x, float y, float z, float health, float facingDeg, boolean eliminated) {
        pos.set(x, y, z);
        vel.set(0f, 0f, 0f);
        knock.set(0f, 0f, 0f);
        vy = 0f;
        this.health = MathUtils.clamp(health, 0f, maxHealth);
        this.facingDeg = facingDeg;
        if (eliminated || this.health <= 0f) {
            if (!dying && !gone) startDeath();
        } else {
            dying = false;
            gone = false;
            deathTimer = 0f;
        }
        applyTransform();
        instance.calculateTransforms();
        anchorWeapon(0f);
        applyTint();
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

    /** Circle-strafe the player: move perpendicular to the line of sight with a slight inward pull. */
    private void strafe(float delta, float dx, float dz) {
        strafeTimer -= delta;
        if (strafeTimer <= 0f) { strafeDir = MathUtils.randomBoolean() ? 1 : -1; strafeTimer = MathUtils.random(0.8f, 1.8f); }
        float len = Math.max((float) Math.sqrt(dx * dx + dz * dz), 0.001f);
        float nx = dx / len, nz = dz / len;
        float px = -nz * strafeDir, pz = nx * strafeDir;     // perpendicular
        setSteer(px + nx * 0.35f, pz + nz * 0.35f, STRAFE_SPEED);
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
        float fx = dx / len, fz = dz / len;
        muzzle.set(pos.x + fx * 0.6f, 1.3f, pos.z + fz * 0.6f);
        launchVel.set(fx * GUN_SPEED, 0f, fz * GUN_SPEED);
        p.launch(muzzle, launchVel, MathUtils.clamp(dist, 3f, 12f));
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
        hurtTimer = HURT_DUR;
        flashTimer = FLASH_DUR;
        tinted = false;
        knock.set(fromDir.x, 0f, fromDir.z).nor().scl(crit ? KB_SELF * 1.4f : KB_SELF);
        hearts.burstHearts(chestTmp.set(pos.x, pos.y + 1.4f, pos.z), crit ? 14 : 5);
        if (health <= 0f) startDeath();
    }

    /** Begin the Minecraft death sequence: stop fighting, stay red, tip onto the side, then vanish. */
    private void startDeath() {
        dying = true;
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
    private void anchorWeapon(float windup) {
        if (equipped == Loadout.GUN && !attacking) {
            // Use clean body mat (no hurt/lunge tilt) so gun sits exactly as the player's does.
            WeaponAnchors.placeGun(gun.transform, cleanBodyMat);
            return;
        }
        WeaponAnchors.placeSword(weaponMat, instance.transform, rightArm.globalTransform);
        sword.transform.set(weaponMat);
        if (attacking) {
            float pitch = MathUtils.lerp(70f, 165f, windup);
            sword.transform.rotate(Vector3.X, pitch - 70f);
        } else if (lungeTimer > 0f) {
            float pitch = MathUtils.lerp(165f, 15f, 1f - lungeTimer / ATTACK_LUNGE);
            sword.transform.rotate(Vector3.X, pitch - 70f);
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
        anchorWeapon(0f);
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
