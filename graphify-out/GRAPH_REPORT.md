# Graph Report - .  (2026-06-08)

## Corpus Check
- 193 files · ~75,723 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1213 nodes · 2398 edges · 97 communities (72 shown, 25 thin omitted)
- Extraction: 85% EXTRACTED · 15% INFERRED · 0% AMBIGUOUS · INFERRED: 361 edges (avg confidence: 0.81)
- Token cost: 2,400 input · 1,930 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Item & Inventory System|Item & Inventory System]]
- [[_COMMUNITY_Block Rendering Layer|Block Rendering Layer]]
- [[_COMMUNITY_3D Render Environment|3D Render Environment]]
- [[_COMMUNITY_Combat & Weapon Layer|Combat & Weapon Layer]]
- [[_COMMUNITY_Camera & Mesh System|Camera & Mesh System]]
- [[_COMMUNITY_Combat Target Interface|Combat Target Interface]]
- [[_COMMUNITY_Minecraft Model Assets|Minecraft Model Assets]]
- [[_COMMUNITY_Map Data & Editing|Map Data & Editing]]
- [[_COMMUNITY_Armor Damage System|Armor Damage System]]
- [[_COMMUNITY_Projectile Combat|Projectile Combat]]
- [[_COMMUNITY_Weapon Animation|Weapon Animation]]
- [[_COMMUNITY_Block Collision|Block Collision]]
- [[_COMMUNITY_Block Definition Library|Block Definition Library]]
- [[_COMMUNITY_Player Control & Aim|Player Control & Aim]]
- [[_COMMUNITY_Options & Settings UI|Options & Settings UI]]
- [[_COMMUNITY_Bedrock UI Primitives|Bedrock UI Primitives]]
- [[_COMMUNITY_AI Enemy System|AI Enemy System]]
- [[_COMMUNITY_Map Editor Screens|Map Editor Screens]]
- [[_COMMUNITY_Gas Zone  Battle Royale|Gas Zone / Battle Royale]]
- [[_COMMUNITY_Project Planning Docs|Project Planning Docs]]
- [[_COMMUNITY_Module 20|Module 20]]
- [[_COMMUNITY_Module 22|Module 22]]
- [[_COMMUNITY_Module 23|Module 23]]
- [[_COMMUNITY_Module 24|Module 24]]
- [[_COMMUNITY_Module 25|Module 25]]
- [[_COMMUNITY_Module 26|Module 26]]
- [[_COMMUNITY_Module 27|Module 27]]
- [[_COMMUNITY_Module 28|Module 28]]
- [[_COMMUNITY_Module 29|Module 29]]
- [[_COMMUNITY_Module 30|Module 30]]
- [[_COMMUNITY_Module 31|Module 31]]
- [[_COMMUNITY_Module 32|Module 32]]
- [[_COMMUNITY_Module 33|Module 33]]
- [[_COMMUNITY_Module 34|Module 34]]
- [[_COMMUNITY_Module 35|Module 35]]
- [[_COMMUNITY_Module 36|Module 36]]
- [[_COMMUNITY_Module 37|Module 37]]
- [[_COMMUNITY_Module 38|Module 38]]
- [[_COMMUNITY_Module 39|Module 39]]
- [[_COMMUNITY_Module 40|Module 40]]
- [[_COMMUNITY_Module 41|Module 41]]
- [[_COMMUNITY_Module 42|Module 42]]
- [[_COMMUNITY_Module 43|Module 43]]
- [[_COMMUNITY_Module 44|Module 44]]
- [[_COMMUNITY_Module 45|Module 45]]
- [[_COMMUNITY_Module 46|Module 46]]
- [[_COMMUNITY_Module 47|Module 47]]
- [[_COMMUNITY_Module 48|Module 48]]
- [[_COMMUNITY_Module 49|Module 49]]
- [[_COMMUNITY_Module 50|Module 50]]
- [[_COMMUNITY_Module 51|Module 51]]
- [[_COMMUNITY_Module 52|Module 52]]
- [[_COMMUNITY_Module 53|Module 53]]
- [[_COMMUNITY_Module 54|Module 54]]
- [[_COMMUNITY_Module 55|Module 55]]
- [[_COMMUNITY_Module 56|Module 56]]
- [[_COMMUNITY_Module 57|Module 57]]
- [[_COMMUNITY_Module 58|Module 58]]
- [[_COMMUNITY_Module 59|Module 59]]
- [[_COMMUNITY_Module 60|Module 60]]
- [[_COMMUNITY_Module 61|Module 61]]
- [[_COMMUNITY_Module 62|Module 62]]
- [[_COMMUNITY_Module 63|Module 63]]
- [[_COMMUNITY_Module 64|Module 64]]
- [[_COMMUNITY_Module 65|Module 65]]
- [[_COMMUNITY_Module 66|Module 66]]
- [[_COMMUNITY_Module 68|Module 68]]
- [[_COMMUNITY_Module 69|Module 69]]
- [[_COMMUNITY_Module 70|Module 70]]
- [[_COMMUNITY_Module 71|Module 71]]
- [[_COMMUNITY_Module 72|Module 72]]
- [[_COMMUNITY_Module 73|Module 73]]
- [[_COMMUNITY_Module 74|Module 74]]
- [[_COMMUNITY_Module 75|Module 75]]
- [[_COMMUNITY_Module 76|Module 76]]
- [[_COMMUNITY_Module 77|Module 77]]
- [[_COMMUNITY_Module 78|Module 78]]
- [[_COMMUNITY_Module 79|Module 79]]
- [[_COMMUNITY_Module 80|Module 80]]
- [[_COMMUNITY_Module 81|Module 81]]
- [[_COMMUNITY_Module 82|Module 82]]
- [[_COMMUNITY_Module 83|Module 83]]
- [[_COMMUNITY_Module 84|Module 84]]
- [[_COMMUNITY_Module 85|Module 85]]
- [[_COMMUNITY_Module 86|Module 86]]
- [[_COMMUNITY_Module 87|Module 87]]
- [[_COMMUNITY_Module 88|Module 88]]
- [[_COMMUNITY_Module 89|Module 89]]
- [[_COMMUNITY_Module 90|Module 90]]
- [[_COMMUNITY_Module 96|Module 96]]

## God Nodes (most connected - your core abstractions)
1. `WeaponController` - 58 edges
2. `PlayerUI` - 55 edges
3. `Player` - 49 edges
4. `AiBrawler` - 46 edges
5. `GameMap` - 39 edges
6. `BlockLibrary` - 36 edges
7. `MapMakerScreen` - 33 edges
8. `PauseOverlay` - 26 edges
9. `WeaponModels` - 22 edges
10. `BedrockWidgets` - 19 edges

## Surprising Connections (you probably didn't know these)
- `CombatDummy (Training dummy entity with knockback tilt & health bar)` --implements--> `CombatTarget`  [INFERRED]
  docs/combat_animation_reference.md → core/src/main/java/com/brawlgame/combat/CombatTarget.java
- `PotatoProjectile` --references--> `WeaponController (Arm pose, melee, bow, gun logic)`  [INFERRED]
  core/src/main/java/com/brawlgame/combat/PotatoProjectile.java → docs/combat_animation_reference.md
- `Mace Item Model (mace.json)` --conceptually_related_to--> `BedrockWidgets`  [AMBIGUOUS]
  macebut3d-1.0.1/assets/minecraft/items/mace.json → core/src/main/java/com/brawlgame/ui/BedrockWidgets.java
- `Diamond Sword 3D Model (JSON mesh data)` --references--> `Minecraft Swords CC-BY-4.0 License (ShadowBubbles / Sketchfab)`  [EXTRACTED]
  assets/models/diamond_sword.json → minecraft_swords/license.txt
- `Gold Sword 3D Model (JSON mesh data)` --references--> `Minecraft Swords CC-BY-4.0 License (ShadowBubbles / Sketchfab)`  [EXTRACTED]
  assets/models/gold_sword.json → minecraft_swords/license.txt

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Minecraft Sword 3D Model Assets (CC-BY-4.0 ShadowBubbles)** — assets_models_diamond_sword, assets_models_gold_sword, assets_models_iron_sword, assets_models_stone_sword, assets_models_wood_sword, minecraft_swords_license [EXTRACTED 1.00]
- **Combat Collision System (BlockCollider + GridRaycast + PotatoProjectile)** — combat_blockcollider_blockcollider, combat_gridraycast_gridraycast, combat_potatoprojectile_potatoprojectile [EXTRACTED 1.00]
- **VFX Pipeline (Bloom + Hit Flash + Slash VFX + Torch Flicker)** — concept_bloom_vfx, concept_hit_flash, concept_slashvfx, concept_torch_flicker, agent_vfx_combat [EXTRACTED 1.00]
- **Player Animation System (WeaponController + PlayerAnimator + ArmPose + MinecraftPlayerModel)** — concept_weaponcontroller, concept_playeranimator, concept_armpose, concept_minecraftplayermodel [EXTRACTED 1.00]
- **Armor & Item System (ArmorStats + Inventory + ItemType)** — combat_armorstats_armorstats, concept_inventory, concept_itemtype [EXTRACTED 1.00]
- **Runtime Texture Pipeline (MinecraftAssets + CC0 fallback)** — concept_minecraftassets, concept_cc0_fallback, agent_texture_pipeline [EXTRACTED 1.00]
- **Claude Agent System (mob-builder + texture-pipeline + vfx-combat)** — agent_mob_builder, agent_texture_pipeline, agent_vfx_combat, claude_ultrathink_plan [EXTRACTED 1.00]
- **Dark Blocks Group** —  [INFERRED]
- **Terracotta Block Family** —  [INFERRED]
- **Complete Diamond Armor Set** —  [INFERRED 0.95]
- **Sword Weapon Tiers Group** —  [INFERRED 0.95]
- **Iron Armor Set** —  [INFERRED 0.95]
- **Leather Armor Set** —  [INFERRED 0.95]
- **All Weapon Items** —  [INFERRED 0.95]
- **Sword Texture Assets Group** —  [INFERRED 1.00]
- **Application Icon Set** —  [INFERRED 1.00]
- **Core Game Texture Assets** —  [INFERRED 0.95]
- **Minecraft Sword Texture Set** —  [INFERRED 1.00]
- **Minecraft Exclusive Capes Set** —  [INFERRED 1.00]
- **Mace But 3D Pack Assets** —  [INFERRED 1.00]
- **Diamond Armor Full Texture Set** —  [INFERRED 1.00]
- **Gold Armor Full Texture Set** —  [INFERRED 1.00]
- **Iron Armor Full Texture Set** —  [INFERRED 1.00]
- **Leather Armor Full Texture Set** —  [INFERRED 1.00]
- **All Armor Tier Textures** —  [INFERRED 0.95]

## Communities (97 total, 25 thin omitted)

### Community 0 - "Item & Inventory System"
Cohesion: 0.06
Nodes (15): Color, Inventory, ItemStack, ItemType, List, Override, String, Matrix4 (+7 more)

### Community 1 - "Block Rendering Layer"
Cohesion: 0.08
Nodes (26): BlockType, Color, Material, MeshPartBuilder, Model, ModelInstance, Override, String (+18 more)

### Community 2 - "3D Render Environment"
Cohesion: 0.05
Nodes (31): Environment, Inventory, ItemType, MeshPartBuilder, Model, ModelBatch, ModelInstance, Override (+23 more)

### Community 3 - "Combat & Weapon Layer"
Cohesion: 0.08
Nodes (23): ByteBuffer, Environment, ItemStack, ItemType, Model, ModelBatch, Override, SwordVariant (+15 more)

### Community 4 - "Camera & Mesh System"
Cohesion: 0.08
Nodes (13): Camera, Mesh, Override, Camera, Mesh, Override, Camera, Override (+5 more)

### Community 5 - "Combat Target Interface"
Cohesion: 0.08
Nodes (17): CombatTarget, Environment, ModelBatch, Override, Texture, Vector3, Color, ModelBatch (+9 more)

### Community 6 - "Minecraft Model Assets"
Cohesion: 0.07
Nodes (29): credit, display, firstperson_lefthand, firstperson_righthand, fixed, ground, gui, head (+21 more)

### Community 7 - "Map Data & Editing"
Cohesion: 0.13
Nodes (5): BlockType, MapSize, Theme, Edit, GameMap

### Community 8 - "Armor Damage System"
Cohesion: 0.15
Nodes (7): ArmorStats, Inventory (Player item/armor slot container), ItemType (Enum for all item/armor types), Inventory, ItemType, ItemStack, Inventory

### Community 9 - "Projectile Combat"
Cohesion: 0.16
Nodes (7): PotatoProjectile, BlockCollider, Environment, ModelBatch, ModelInstance, Vector3, Player

### Community 10 - "Weapon Animation"
Cohesion: 0.18
Nodes (6): ArmPose, WeaponController.Weapon (enum), WeaponController, BlockCollider, Override, Weapon

### Community 11 - "Block Collision"
Cohesion: 0.18
Nodes (5): BlockCollider, GridRaycast, Amanatides & Woo DDA Grid Traversal (exact CCD for projectiles), BlockCollider, Vector3

### Community 12 - "Block Definition Library"
Cohesion: 0.14
Nodes (8): BlockCategory, BlockLibrary, BlockType, Override, String, Theme, CreativeInventory, Tab()

### Community 13 - "Player Control & Aim"
Cohesion: 0.15
Nodes (3): Camera, Override, Player

### Community 14 - "Options & Settings UI"
Cohesion: 0.18
Nodes (5): BitmapFont, ShapeRenderer, SpriteBatch, String, OptionsPanel

### Community 15 - "Bedrock UI Primitives"
Cohesion: 0.13
Nodes (6): BitmapFont, ShapeRenderer, SpriteBatch, String, BedrockWidgets.BtnState (Enum), UiButton

### Community 17 - "Map Editor Screens"
Cohesion: 0.14
Nodes (8): Btn, Game, List, MapSize, String, Theme, Rect, Rect

### Community 18 - "Gas Zone / Battle Royale"
Cohesion: 0.19
Nodes (6): Environment, GameMap, Material, ModelBatch, Override, GasZone

### Community 19 - "Project Planning Docs"
Cohesion: 0.23
Nodes (17): Mob Builder Agent (Skeleton/Zombie geometry & AI), Texture Pipeline Agent (Minecraft texture extraction & UV mapping), VFX Combat Agent (Bloom, hit flash, slash trails, torch flicker), Root Build Gradle (BrawlGame project config), BlockBrawl Ultrathink Implementation Plan, BlockBrawl — Minecraft Dungeons-style LibGDX Game, Bloom VFX (gdx-vfx post-processing bloom effect), CC0 Fallback Texture Palette (no Mojang install fallback) (+9 more)

### Community 20 - "Module 20"
Cohesion: 0.16
Nodes (6): Camera, Environment, Matrix4, ModelBatch, ModelInstance, SwordAsset

### Community 23 - "Module 23"
Cohesion: 0.18
Nodes (6): Lwjgl3Launcher, String, String, StartupHelper, Lwjgl3Application, Lwjgl3ApplicationConfiguration

### Community 24 - "Module 24"
Cohesion: 0.18
Nodes (10): ItemType, SwordVariant, String, armorSlot(), displayName(), isArmor(), isSword(), ItemType() (+2 more)

### Community 25 - "Module 25"
Cohesion: 0.25
Nodes (6): Camera, Color, Override, String, Vector3, OverheadHud

### Community 26 - "Module 26"
Cohesion: 0.25
Nodes (15): Diamond Armor Layer 1 Texture, Diamond Armor Layer 2 Texture, Gold Armor Layer 1 Texture, Gold Armor Layer 2 Texture, Iron Armor Layer 1 Texture, Iron Armor Layer 2 Texture, Leather Armor Texture (Sheet 1), Leather Armor Texture (Sheet 2 / Overlay) (+7 more)

### Community 27 - "Module 27"
Cohesion: 0.30
Nodes (5): Mace Item Model (mace.json), BtnState, Color, ShapeRenderer, BedrockWidgets

### Community 28 - "Module 28"
Cohesion: 0.21
Nodes (5): Consumer, CombatTarget, Texture, Texture, Function

### Community 29 - "Module 29"
Cohesion: 0.22
Nodes (10): CombatTarget, ArmPose (Per-frame arm rotation/weight data structure), CombatDummy (Training dummy entity with knockback tilt & health bar), MinecraftPlayerModel (Node-rigged procedural player model builder), PlayerAnimator (Slerp-based arm/body animation driver), 3D Extruded Sword Model (per-texel voxel extrusion from 16x16 PNG), WeaponController (Arm pose, melee, bow, gun logic), WeaponModels (3D extruded sword/bow/gun model builder) (+2 more)

### Community 30 - "Module 30"
Cohesion: 0.16
Nodes (5): BlockType, Override, String, Texture, Hotbar

### Community 32 - "Module 32"
Cohesion: 0.22
Nodes (5): Color, Override, String, Texture, MatchIntro

### Community 33 - "Module 33"
Cohesion: 0.22
Nodes (6): Environment, ModelBatch, Override, Vector3, BlockParticles, P

### Community 34 - "Module 34"
Cohesion: 0.26
Nodes (4): Game, Override, Screen, MapListScreen

### Community 35 - "Module 35"
Cohesion: 0.23
Nodes (4): Game, Override, TestPlayerScreen, WeaponController

### Community 36 - "Module 36"
Cohesion: 0.20
Nodes (8): BlockCollider, Environment, Inventory, ItemType, ModelBatch, ModelInstance, Texture, Supplier

### Community 37 - "Module 37"
Cohesion: 0.27
Nodes (3): Game, Override, MainMenuScreen

### Community 38 - "Module 38"
Cohesion: 0.27
Nodes (3): Game, Override, MapMakerMenuScreen

### Community 39 - "Module 39"
Cohesion: 0.25
Nodes (5): Environment, ModelBatch, Override, Vector3, SpikeHazard

### Community 40 - "Module 40"
Cohesion: 0.25
Nodes (6): BlockLibrary, Environment, GameMap, ModelBatch, Override, MapRenderer

### Community 41 - "Module 41"
Cohesion: 0.27
Nodes (3): Vector3, Ray, SpectatorCamera

### Community 42 - "Module 42"
Cohesion: 0.31
Nodes (4): Game, GameMap, Override, GameScreen

### Community 43 - "Module 43"
Cohesion: 0.27
Nodes (10): Cobbled Deepslate Texture, Cracked Deepslate Bricks Texture, Cut Sandstone Texture, Dark Oak Planks Texture, Deepslate Texture, Deepslate Bricks Texture, Deepslate Tiles Texture, Deepslate Top Texture (+2 more)

### Community 44 - "Module 44"
Cohesion: 0.27
Nodes (3): GameMap, FileHandle, MapSerializer

### Community 45 - "Module 45"
Cohesion: 0.29
Nodes (4): Camera, Override, Player, DebugRenderer

### Community 46 - "Module 46"
Cohesion: 0.36
Nodes (3): String, Action(), Settings

### Community 47 - "Module 47"
Cohesion: 0.33
Nodes (10): Heart FX Texture, Diamond Boots Item Icon, Diamond Chestplate Item Icon, Diamond Helmet Item Icon, Diamond Leggings Item Icon, Diamond Sword Item Icon, Golden Sword Item Icon, Diamond Sword Texture (Root) (+2 more)

### Community 48 - "Module 48"
Cohesion: 0.39
Nodes (8): BlockType, String, borderBlock(), displayName(), fromId(), id(), palette(), Theme()

### Community 50 - "Module 50"
Cohesion: 0.50
Nodes (4): ItemType, Override, Texture, ItemIcons

### Community 52 - "Module 52"
Cohesion: 0.57
Nodes (8): Iron Boots Item Icon, Iron Chestplate Item Icon, Iron Helmet Item Icon, Iron Leggings Item Icon, Leather Boots Item Icon, Leather Chestplate Item Icon, Leather Helmet Item Icon, Leather Leggings Item Icon

### Community 53 - "Module 53"
Cohesion: 0.25
Nodes (7): model, type, model, cases, fallback, property, type

### Community 54 - "Module 54"
Cohesion: 0.33
Nodes (4): BlockCollider, Environment, ModelBatch, Texture

### Community 55 - "Module 55"
Cohesion: 0.43
Nodes (4): String, fromId(), id(), MapSize()

### Community 56 - "Module 56"
Cohesion: 0.33
Nodes (3): ModelBatch, Override, GridRenderer

### Community 57 - "Module 57"
Cohesion: 0.48
Nodes (3): Color, ShapeRenderer, BedrockUi

### Community 58 - "Module 58"
Cohesion: 0.33
Nodes (3): Override, Disposable, DamageVignette

### Community 59 - "Module 59"
Cohesion: 0.29
Nodes (7): Mace Head Element, Mace Rod Element, Mace In Hand Item Model, Mace In Hand Texture (minecraft:item/mace_in_hand), Root Project Settings (settings.gradle), core Subproject, lwjgl3 Subproject

### Community 60 - "Module 60"
Cohesion: 0.29
Nodes (6): indices, normals, positions, texture, tipZ, uvs

### Community 61 - "Module 61"
Cohesion: 0.29
Nodes (6): indices, normals, positions, texture, tipZ, uvs

### Community 62 - "Module 62"
Cohesion: 0.29
Nodes (6): indices, normals, positions, texture, tipZ, uvs

### Community 63 - "Module 63"
Cohesion: 0.29
Nodes (6): indices, normals, positions, texture, tipZ, uvs

### Community 64 - "Module 64"
Cohesion: 0.29
Nodes (6): indices, normals, positions, texture, tipZ, uvs

### Community 65 - "Module 65"
Cohesion: 0.33
Nodes (6): Diamond Sword 3D Model (JSON mesh data), Gold Sword 3D Model (JSON mesh data), Iron Sword 3D Model (JSON mesh data), Stone Sword 3D Model (JSON mesh data), Wood Sword 3D Model (JSON mesh data), Minecraft Swords CC-BY-4.0 License (ShadowBubbles / Sketchfab)

### Community 66 - "Module 66"
Cohesion: 0.47
Nodes (3): DungeonGame, Override, Game

### Community 68 - "Module 68"
Cohesion: 0.40
Nodes (5): Application Logo Icon, libGDX App Icon 128px, libGDX App Icon 16px, libGDX App Icon 32px, libGDX App Icon 64px

### Community 69 - "Module 69"
Cohesion: 0.40
Nodes (5): Stone Sword Item Texture, Wooden Sword Item Texture, Player Skin Texture, Stone Sword Texture (root textures), Wood Sword Texture (root textures)

### Community 70 - "Module 70"
Cohesion: 1.00
Nodes (3): Minecon 2011 Cape Texture, Minecon 2012 Cape Texture, Mojang Cape Texture

### Community 71 - "Module 71"
Cohesion: 0.67
Nodes (3): Obsidian Texture, Polished Blackstone Texture, Polished Deepslate Texture

## Ambiguous Edges - Review These
- `BedrockWidgets` → `Mace Item Model (mace.json)`  [AMBIGUOUS]
  macebut3d-1.0.1/assets/minecraft/items/mace.json · relation: conceptually_related_to

## Knowledge Gaps
- **163 isolated node(s):** `allow`, `texture`, `positions`, `normals`, `uvs` (+158 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **25 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What is the exact relationship between `BedrockWidgets` and `Mace Item Model (mace.json)`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._
- **Why does `WeaponController` connect `Weapon Animation` to `Module 33`, `3D Render Environment`, `Combat & Weapon Layer`, `Camera & Mesh System`, `Combat Target Interface`, `Projectile Combat`, `Block Collision`, `Player Control & Aim`, `Module 20`, `Module 21`, `Module 24`, `Module 58`, `Module 28`, `Module 29`?**
  _High betweenness centrality (0.102) - this node is a cross-community bridge._
- **Why does `AiBrawler` connect `AI Enemy System` to `Module 33`, `3D Render Environment`, `Combat & Weapon Layer`, `Combat Target Interface`, `Armor Damage System`, `Projectile Combat`, `Block Collision`, `Player Control & Aim`, `Module 51`, `Module 54`, `Module 24`, `Module 58`, `Module 29`?**
  _High betweenness centrality (0.097) - this node is a cross-community bridge._
- **Why does `MapMakerScreen` connect `Module 31` to `Block Rendering Layer`, `Module 34`, `Module 67`, `Module 37`, `Module 38`, `Map Data & Editing`, `Module 40`, `Module 41`, `Module 44`, `Module 48`, `Map Editor Screens`, `Module 55`, `Module 57`, `Module 30`?**
  _High betweenness centrality (0.094) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `WeaponController` (e.g. with `CombatDummy` and `AimCone`) actually correct?**
  _`WeaponController` has 2 INFERRED edges - model-reasoned connections that need verification._
- **Are the 3 inferred relationships involving `Player` (e.g. with `ChestEntity` and `ItemEntity`) actually correct?**
  _`Player` has 3 INFERRED edges - model-reasoned connections that need verification._
- **What connects `allow`, `texture`, `positions` to the rest of the system?**
  _164 weakly-connected nodes found - possible documentation gaps or missing edges._