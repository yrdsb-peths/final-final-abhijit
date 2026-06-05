# Combat & Animation Reference

Single source of truth for the player-animation and combat-feel rules.
**Read this file first** before doing deep codebase reads when working on the sword/weapons, the
cape, the potato launcher, the bow, the hit effect, the dummy, or the crosshair.

## Coordinate & rig conventions (shared by all rules)
- Model faces **-Z** (forward). **+Y** is up. 1 Minecraft pixel = `PX = 1/16` world unit.
- Head + both arms + cape are **children of the `BODY` node** (`MinecraftPlayerModel.build`), so a
  torso lean/twist carries them. Legs stay under the root and swing from the hips.
- **Naming gotcha (critical):** the character's *right* hand is the **+X** node, named **`ARM_L`**,
  driven by the animator's **`l*`** fields (`lRot`/`lWeight`). **`ARM_R`** (-X) is the *left* hand,
  driven by **`r*`** fields. In `WeaponController`: `weaponArm = ARM_L` (right hand),
  `supportArm = ARM_R` (left hand).
- Arm poses are composed `yaw·pitch·roll` via `WeaponController.armPose(...)`. Weapons feed the
  animator a per-frame `ArmPose` (`rRot/rWeight`, `lRot/lWeight`, `bodyYaw`, `drawZ`); the animator
  slerps each arm **only when its weight > 0**, so an un-weighted arm stays locked to its walk/idle
  swing. This is the mechanism the right-hand-only punch relies on (§2).

## Asset map (game working dir = `assets/`, loaded via `Gdx.files.internal("...")`)
- `textures/player.png` — player/dummy skin (64×64).
- `textures/items/iron_sword.png`, `diamond_sword.png` — sword texture sheets (16×16 RGBA). These
  are **extruded into a 3D voxel mesh** at load (§1), not drawn as a flat sprite.
- `textures/items/bow_standby.png`, `bow_pulling_0/1/2.png` — the four bow charge frames.
- `textures/fx/heart.png` — hit-effect heart sprite.
- **One crosshair cursor** is generated at runtime (Pixmap → hardware `Cursor`) and installed once
  for the whole UI (§1) — it never changes with the weapon.

---

## 1. Strict vanilla assets & modeling
**Files:** `model/WeaponModels.java`, `gfx/Hud.java`

- **Vanilla only:** no custom procedural art styles or non-standard textures — everything mimics
  standard Minecraft assets/UV.
- **3D extruded sword (HARD RULE):** the sword is **not** a flat sprite, a thin textured box, or
  vertex-coloured cubes. `WeaponModels.extrude(...)` reads the standard 16×16 iron/diamond PNG and,
  for **every opaque texel (alpha ≥ 0.5)**, emits **textured front + back quad faces plus side quad
  faces along the silhouette** (a side is emitted only where the 4-neighbour texel is transparent),
  each face carrying that texel's real **UV rect**. Material = the actual sheet texture (nearest,
  alpha-tested), 1 texel deep. Blade in X/Z, tip at `-SWORD_TIP_Z`. **Held grip:** idle "ready" arm
  pose (`SWORD_HOLD_PITCH≈42`) + a `postAnimate` orientation of `rotate X -55°, Y +10°, Z -15°` so
  the blade sits in a steep up-forward diagonal with the broad face presented to the camera (matches
  the held-sword reference), not lying flat.
- **Single crosshair cursor (HARD RULE):** exactly **one** static vanilla "+" crosshair, generated
  once in `Hud` and set via `Gdx.graphics.setCursor`. **No if/else or switch on the weapon — never
  swapped.**

---

## 2. Weapon animations & stances
**Files:** `combat/WeaponController.java`, `model/PlayerAnimator.java`

**Right-hand-only punch:** a punch drives **only** the right hand (`ARM_L` / `pose.lRot`); the left
arm's `rWeight` stays 0 so the animator keeps it locked to locomotion (idle/walk/sprint). Never
alternates. Motion = rapid forward-and-downward stroke, smooth return.

**True bow draw (HARD RULES):**
- **Left arm** (the character's left hand = `ARM_R` / `pose.rRot`) points **straight forward, yaw 0**
  to hold the grip (`BOW_FWD_PITCH=90, BOW_FWD_YAW=0` — pitch 90 in this rig's yaw·pitch·roll == the
  spec's `LeftArm.pitch = -90` in MC convention). Steady regardless of draw.
- **Right arm** (`ARM_L` / `pose.lRot`) anchors to the string and **translates backward on local Z
  only** as charge rises: `pose.drawZ = BOW_DRAW_TRANSLATE * chargeAmount`, applied by the animator
  to the node's `translation.z`.
- **Bow model = 3D extrusion of the vanilla frames** (same `extrude(...)` as the sword, UPRIGHT in
  the X/Y plane). **One mesh per frame** is built (`standby, pulling_0, pulling_1, pulling_2`) because
  the silhouettes differ; the active mesh is selected by charge and body-anchored front-left of the
  chest so it reads under the top-down camera.
- **Arrow projectile:** a proper fletched arrow (shaft + metal head + tail vanes), pointed down its
  flight vector.
- **FOV zoom** narrows smoothly as `chargeAmount → 1` (`CameraRig.setAimZoom`).
- **Texture frames** swap at thresholds: ≥0.90 `pulling_2`, ≥0.50 `pulling_1`, ≥0.25 `pulling_0`,
  else `standby`.

---

## 3. Potato launcher — structure & physics
**Files:** `model/WeaponModels.java` (`buildGun`, `GUN_SCALE`), `combat/WeaponController.java`,
`gfx/AimCone.java` (`renderRect`), `DungeonGame.java`

- **Blunderbuss shape (reference img):** the launcher is a **blunderbuss/horn** — a wide **flared
  metal bell muzzle** at the front (stepped rings widening toward -Z), a tan **wooden body** with
  **brass/gold bands**, a **top hopper box** (the potato loader), a rear **drop-down wooden grip**
  and a small red trigger. Built procedurally from coloured boxes in `buildGun`.
- **Hard scale (HARD RULE):** `GUN_SCALE = 0.5` — every box halved so the launcher is small/compact.
- **Held low at the hip:** `GUN_OFF_Y` lowered to hip height, barrel roughly level forward
  (`GUN_BODY_PITCH ≈ 0`); **right hand on the rear grip/trigger**, **left arm across to the
  fore-body/barrel**.
- **Range (HARD RULE):** `GUN_RANGE ≈ 5.41` (cut 35 %, then 20 %, then a further 35 % off the prior
  value). `CONE_GUN_RANGE == GUN_RANGE`, so the aim rectangle and the projectile cap always match.
- **Solid aim indicator (HARD RULE):** the rectangular ground indicator renders as a **uniform,
  solid transparency** with **no alpha fade / gradient** — same colour from the player to the
  max-range edge, stopping instantly at `maxRange`.
- **Hard projectile kill (HARD RULE):** each tick the potato's **distance from the player** is
  checked; the instant it is `≥ GUN_RANGE` the potato is set inactive. It never overshoots the aim
  rectangle, hits the ground, or flies on.

---

## 4. Hit particles & combat dummy
**Files:** `gfx/Sparks.java`, `entity/CombatDummy.java`, `combat/WeaponController.java`

- **No air spawning:** hearts spawn **only when an attack actually intersects an entity hitbox** —
  i.e. from `CombatDummy.onHit`. `WeaponController` owns no particle pool; it just calls
  `target.onHit(...)`. Empty swings / muzzle produce nothing.
- **Throttled:** a few clean, distinct floating hearts per hit (`HEARTS_PER_HIT ≈ 4`).
- **Persistent dummy:** same model + skin as the player. A standard **green health bar** floats
  overhead locked at **9999/9999** (infinite; never decremented). On hit: a rapid **red tint flash**
  and a subtle **knockback tilt**. Always faces the player.

---

## 5. Cape — simple light-blue fit
**Files:** `model/MinecraftPlayerModel.java` (mesh + colour), `model/PlayerAnimator.java` (motion),
`entity/Player.java` + `entity/CombatDummy.java`

- **Fit (HARD RULE):** width hardcoded to **7 px** — slightly less than the **8 px** torso — so it
  sits **between the shoulder joints** on the back of the `BODY` node with no spill past the sides.
- **Colour (HARD RULE):** a single, flat, uniform **light blue `#ADD8E6` = `(0.67, 0.84, 0.90, 1.0)`**
  cloth box. **All emblem / pattern / random-colour code is deleted** —
  `CapeVariant`/`applyCapeVariant`/`MAT_CAPE_EMBLEM` are gone; one stable `MAT_CAPE_CLOTH` material.
- **Physics (`PlayerAnimator`):** idle hangs vertically with a subtle sine-wave wiggle
  (`sin(time) * 2°`); moving/sprinting pitches the cape **back 45–60°** scaled by player velocity
  (eased), trailing in the wind. Applied before `instance.calculateTransforms()`.

---

### Maintenance
When any mechanic changes in code, **update this file in the same change** so it stays the source of
truth. Keep the asset map current as sprites/sheets are added.
