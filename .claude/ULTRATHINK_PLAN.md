# BlockBrawl — Ultrathink Implementation Plan
# Paste this entire file as context when starting a new Claude Code terminal session.

## What this project is
A Minecraft Dungeons-style 3D isometric action game in LibGDX (Java). The reference is the 
Minecraft Dungeons official launch trailer — warm torch lighting, real Minecraft block textures 
on the floor/walls, accurate Minecraft skeleton mobs with ribcage texture and thin 2×12×2 
arms/legs, hit flash (full-body white emissive), glowing sword slash trails (cyan/white bloom), 
isometric follow camera. Built entirely procedurally in code — no rigged model pipeline.

## Repo location
/Users/abhijitreddy/Documents/PROJECTS/GitHub/final-final-abhijit

## Critical constraint
NO Mojang/Microsoft asset files in the repo or committed to git. Textures are loaded at RUNTIME 
from the user's own Minecraft installation (legal — same as Minecraft mods). CC0 fallback if 
Minecraft not installed. CREDITS.txt must list all CC0 assets.

## Current state (what's already built and compiles clean)
- `gfx/BlockyCharacter.java` — node-rigged hero with AnimState machine (IDLE/WALK/RUN/ROLL/JUMP/SWING/DRAW/FIRE/HURT), sword/bow/shield held items, all animations working
- `screens/PlayScreen.java` — WASD walk/sprint/jump/roll, mouse aim, sword combo, charged bow, shield; isometric camera; HUD; 8 emissive torches + PointLights; stone arena; crates
- `gfx/BlockModels.java` — procedural stoneTexture/crateTexture/grassTexture; torch/arrow/crate/tree models
- `game/Projectile.java` — arcing arrow with orientation
- `gfx/Particles.java` — cube particle system for hit/poof
- `gfx/GroundRing.java` — translucent ring under player
- ModelBatch built with `DefaultShader.Config.numPointLights = 24`
- gdx-vfx 0.5.4 dep added to core/build.gradle (VfxManager import works) but NOT yet wired into render loop
- `./gradlew lwjgl3:run` compiles and runs clean, renders a stone arena with the hero

## What's WRONG right now (user's complaints from the trailer screenshots)
1. **Textures are AI/procedural — not real Minecraft.** Floor looks like noise. Fix: MinecraftAssets.java
2. **No skeleton mob exists.** The trailer has skeletons with real skeleton.png texture. Fix: SkeletonMob.java
3. **Animation isn't good enough.** Minecraft Dungeons has smooth, accurate animations. Fix: improve rotation values and interpolation speed
4. **Bloom not wired in.** gdx-vfx dep is added but VfxManager/BloomEffect not connected to the render loop. Fix: wire FBO → bloom → screen
5. **No hit flash.** White emissive pulse on damage is the signature combat feedback. Fix: hitFlashTimer in BlockyCharacter
6. **No slash VFX.** The trailer shows bright cyan/white glowing sword slash trails. Fix: SlashVfx.java
7. **Lighting too bright.** Ambient is 0.22 — should be ~0.10 so torch halos really pop

## TASKS — do these in order, compile-check after each

### TASK 1: Wire bloom (gdx-vfx) — IMMEDIATE, no new deps
File: `screens/PlayScreen.java`

Add to fields:
```java
private VfxManager vfx;
private BloomEffect bloom;
private FrameBuffer sceneFbo;
```
Imports: `com.crashinvaders.vfx.VfxManager`, `com.crashinvaders.vfx.effects.BloomEffect`, `com.badlogic.gdx.graphics.glutils.FrameBuffer`

In constructor (after modelBatch created):
```java
vfx = new VfxManager(Pixmap.Format.RGBA8888);
bloom = new BloomEffect();
bloom.setThreshold(0.52f);
bloom.setBloomIntensity(1.9f);
bloom.setBlurAmount(14f);
vfx.addEffect(bloom);
sceneFbo = new FrameBuffer(Pixmap.Format.RGBA8888, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
```

In render() — wrap the 3D scene with FBO → vfx:
```java
// 1. render scene into depth-capable FBO
sceneFbo.begin();
Gdx.gl.glClearColor(0.04f, 0.05f, 0.08f, 1f);
Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
modelBatch.begin(cam);
// ... all existing 3D render calls ...
modelBatch.end();
sceneFbo.end();

// 2. feed FBO texture through bloom
vfx.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
vfx.cleanUpBuffers();
vfx.beginInputCapture();
game.batch.begin();
game.batch.draw(sceneFbo.getColorBufferTexture(),
    0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), 0,0,1,1);
game.batch.end();
vfx.endInputCapture();
vfx.applyEffects();
vfx.renderToScreen();

// 3. draw HUD on top (no bloom)
drawHud();
```

In resize(): 
```java
if (sceneFbo != null) sceneFbo.dispose();
sceneFbo = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, true);
vfx.resize(width, height);
```

In dispose(): `vfx.dispose(); bloom.dispose(); if(sceneFbo!=null) sceneFbo.dispose();`

Also: lower ambient to `(0.10f, 0.10f, 0.14f, 1f)` so torches DOMINATE.

### TASK 2: Hit flash on BlockyCharacter
File: `gfx/BlockyCharacter.java`

Add field: `public float hitFlashTimer = 0f;`

In `animate()` method, at the top:
```java
if (hitFlashTimer > 0) {
    hitFlashTimer -= dt;
    boolean flash = hitFlashTimer > 0;
    for (int i = 0; i < instance.materials.size; i++) {
        Material m = instance.materials.get(i);
        if (flash) {
            m.set(new ColorAttribute(ColorAttribute.Emissive, 1f, 1f, 1f, 1f));
            m.set(ColorAttribute.createDiffuse(Color.WHITE));
        } else {
            m.remove(ColorAttribute.Emissive);
            // restore diffuse — store originals in a parallel Array<Color> at construction time
        }
    }
}
```

Better approach: at construction save all original diffuse colors:
```java
private final com.badlogic.gdx.utils.Array<Color> origColors = new com.badlogic.gdx.utils.Array<>();
// After mb.end(), in constructor:
for (int i = 0; i < instance.materials.size; i++) {
    ColorAttribute ca = (ColorAttribute) instance.materials.get(i).get(ColorAttribute.Diffuse);
    origColors.add(ca != null ? ca.color.cpy() : Color.WHITE.cpy());
}
```
Then restore correctly in animate() when hitFlashTimer hits 0.

To trigger in PlayScreen: when a test damage is applied, `player.hitFlashTimer = 0.12f;`
Add test: press H key to trigger hit flash for visual testing.

### TASK 3: Slash VFX  
New file: `gfx/SlashVfx.java`

A flat bright quad that sweeps in an arc over 0.22s:
```java
public class SlashVfx {
    private final Model quad;
    private final ModelInstance instance;
    private float timer, maxTime = 0.22f;
    private float startAngle, endAngle;
    private boolean active;
    private final Vector3 center = new Vector3();
    
    public SlashVfx() {
        ModelBuilder mb = new ModelBuilder();
        Material m = new Material(
            ColorAttribute.createDiffuse(new Color(0.75f, 0.92f, 1f, 0.85f)),
            new ColorAttribute(ColorAttribute.Emissive, 0.85f, 0.98f, 1f, 1f),
            new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE),  // additive glow
            new DepthTestAttribute(GL20.GL_LEQUAL, false)
        );
        // flat elongated quad: 2.4 × 0.28, lying in XZ (slashable Y level = 1.0)
        // build as MeshPartBuilder rect
        mb.begin();
        MeshPartBuilder p = mb.part("slash", GL20.GL_TRIANGLES,
            Usage.Position | Usage.Normal | Usage.TextureCoordinates, m);
        p.rect(-1.2f,0f,0f, 1.2f,0f,0f, 1.2f,0f,0.28f, -1.2f,0f,0.28f, 0f,1f,0f);
        quad = mb.end();
        instance = new ModelInstance(quad);
    }
    
    public void trigger(Vector3 center, float facingDeg) {
        this.center.set(center);
        this.startAngle = facingDeg + 80f;
        this.endAngle   = facingDeg - 55f;
        timer = maxTime;
        active = true;
    }
    
    public void update(float dt) {
        if (!active) return;
        timer -= dt;
        if (timer <= 0) { active = false; return; }
        float t = 1f - (timer / maxTime);
        float angle = startAngle + (endAngle - startAngle) * t;
        float alpha = timer / maxTime;
        ((BlendingAttribute) instance.materials.get(0).get(BlendingAttribute.Type)).opacity = alpha;
        instance.transform.setToTranslation(center.x, 1.0f, center.z).rotate(Vector3.Y, -angle);
    }
    
    public void render(ModelBatch batch, Environment env) {
        if (active) batch.render(instance, env);
    }
    
    public boolean isActive() { return active; }
    public void dispose() { quad.dispose(); }
}
```

Wire in PlayScreen: create `slashVfx = new SlashVfx()`, call `slashVfx.trigger(pos, facing)` at the hit frame in sword swing logic (when `swingT` crosses 0.45), update + render in the render loop.

### TASK 4: MinecraftAssets.java
New file: `shared/MinecraftAssets.java`

```java
package com.brawlgame.shared;
// Loads textures from user's Minecraft installation at runtime.
// Legal: reads user's own client.jar (same as Minecraft mods/launchers).
// Falls back to procedural CC0 palette if Minecraft not installed.
public class MinecraftAssets {
    private static final Map<String, Texture> cache = new HashMap<>();
    private static ZipFile clientJar; // opened once, kept open during session
    
    public static void init() {
        clientJar = findClientJar(); // search platform-appropriate .minecraft path
    }
    
    // Key paths: "block/sandstone", "block/stone_bricks", "entity/skeleton/skeleton"
    public static Texture get(String name) {
        if (cache.containsKey(name)) return cache.get(name);
        Texture t = loadFromJar(name);
        if (t == null) t = fallback(name);
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        cache.put(name, t);
        return t;
    }
    
    private static ZipFile findClientJar() {
        // macOS: ~/Library/Application Support/minecraft/versions/*/client.jar
        // Windows: %APPDATA%\.minecraft\versions\*\*-client.jar
        // Find newest directory under versions/
        String os = System.getProperty("os.name").toLowerCase();
        String mcPath = os.contains("mac")
            ? System.getProperty("user.home") + "/Library/Application Support/minecraft"
            : System.getenv("APPDATA") + "/.minecraft";
        File versionsDir = new File(mcPath, "versions");
        if (!versionsDir.exists()) return null;
        // find newest client.jar (sort version dirs descending by modification date)
        File[] vDirs = versionsDir.listFiles(File::isDirectory);
        if (vDirs == null) return null;
        Arrays.sort(vDirs, (a,b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File vDir : vDirs) {
            File jar = new File(vDir, vDir.getName() + ".jar");
            if (!jar.exists()) jar = new File(vDir, "client.jar");
            if (jar.exists()) try { return new ZipFile(jar); } catch (Exception e) {}
        }
        return null;
    }
    
    private static Texture loadFromJar(String name) {
        if (clientJar == null) return null;
        String path = "assets/minecraft/textures/" + name + ".png";
        ZipEntry entry = clientJar.getEntry(path);
        if (entry == null) return null;
        try (InputStream is = clientJar.getInputStream(entry)) {
            byte[] bytes = is.readAllBytes();
            Pixmap pm = new Pixmap(bytes, 0, bytes.length);
            Texture t = new Texture(pm);
            pm.dispose();
            return t;
        } catch (Exception e) { return null; }
    }
    
    private static Texture fallback(String name) {
        // minimal CC0 palette fallbacks
        int s = 16;
        Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
        if (name.contains("sandstone")) { pm.setColor(0.84f,0.78f,0.55f,1); pm.fill(); }
        else if (name.contains("stone_brick")) { pm.setColor(0.50f,0.50f,0.54f,1); pm.fill(); }
        else { pm.setColor(0.5f,0.5f,0.5f,1); pm.fill(); }
        Texture t = new Texture(pm); pm.dispose();
        return t;
    }
    
    public static void dispose() {
        cache.values().forEach(Texture::dispose);
        cache.clear();
        if (clientJar != null) try { clientJar.close(); } catch (Exception e) {}
    }
}
```

Wire: call `MinecraftAssets.init()` in `BlockBrawl.create()`. Use `MinecraftAssets.get("block/sandstone")` instead of `BlockModels.stoneTexture()` for the floor.

### TASK 5: Proper Minecraft skeleton mob
New files: `game/mobs/SkeletonMob.java` (AI + model + anim all in one class for now)

**Exact geometry** (CRITICAL — skeleton arms/legs are 2×2, NOT 4×4 like Steve):
```
Head:   0.5 × 0.5 × 0.5
Body:   0.5 × 0.75 × 0.25
armL/R: 0.125 × 0.75 × 0.125   ← THIN
legL/R: 0.125 × 0.75 × 0.125   ← THIN
```
Node pivots (same convention as BlockyCharacter, feet y=0):
- Head:  (0, 1.5, 0)         geom center (0, +0.25, 0)
- Body:  static (0, 0.75, 0) geom center (0, +0.375, 0)
- armL:  (0, 1.5, -0.3125)   geom center (0, -0.375, 0)
- armR:  (0, 1.5, +0.3125)   geom center (0, -0.375, 0)
- legL:  (0, 0.75, -0.0625)  geom center (0, -0.375, 0)
- legR:  (0, 0.75, +0.0625)  geom center (0, -0.375, 0)

**Colors without texture** (bone/skeleton look):
- Head/body/arms/legs: `(0.84f, 0.76f, 0.60f, 1f)` (tan/bone)
- Dark rib markings on body: add thin dark overlay boxes on the front face of body
- Eye sockets: small dark boxes on head front (+X face)

**With texture**: if `MinecraftAssets.get("entity/skeleton/skeleton")` returns non-null, 
UV-map it using the Minecraft skin format (see texture-pipeline agent). 
If null (no MC install), use the color fallback above.

**AI state machine**:
```java
enum State { IDLE, WALK, WINDUP, SHOOT, HURT, DEAD }
// WINDUP: 0.6s — set emissive red + show red GroundRing
// SHOOT: spawn Projectile aimed at player, clear red flash
// Telegraph red glow is the signal to dodge-roll
```

**Spawn in PlayScreen**: add `Array<SkeletonMob> skeletons` field; spawn 2–3 at start at random positions on the arena floor (not overlapping crates); update+render each frame; on death call `particles.poof(pos, boneColor, 18)`.

### TASK 6: Polish — torch flicker
In PlayScreen, add `float[] torchPhase = new float[8]` and each frame:
```java
for (int i = 0; i < torchLights.size; i++) {
    torchPhase[i] += delta * (4.5f + (i * 1.3f % 2f)); // slightly different per torch
    float f = 0.88f + 0.12f * MathUtils.sin(torchPhase[i]);
    PointLight light = torchLights.get(i); // keep Array<PointLight>
    light.color.set(1f * f, 0.68f * f, 0.30f * f, 1f);
    light.intensity = 13f + 3.5f * MathUtils.sin(torchPhase[i] * 0.7f);
}
```
Store torchLights as `Array<PointLight>` — keep refs to the same objects in env so mutations take effect.

## Build/test after each task
```bash
cd /Users/abhijitreddy/Documents/PROJECTS/GitHub/final-final-abhijit
./gradlew :lwjgl3:compileJava --offline --console=plain 2>&1 | grep -E "error:|BUILD"
# If clean:
BLOCKBRAWL_CAPTURE=1 ./gradlew lwjgl3:run --offline 2>&1 &
# wait 15s, check /tmp/p2_frame.png for visual result
```

## After all tasks complete
1. Update CLAUDE.md — mark tasks done in the Implemented list
2. Run full `./gradlew lwjgl3:run` and do a 60-second manual playtest
3. Confirm: torches flicker, bloom glows around torches and slash, skeletons have correct thin arms, hit flash works when H is pressed, sword combo shows slash VFX

## Available agents (invoke with @agent-name in Claude Code)
- `@texture-pipeline` — Minecraft texture extraction, UV mapping, skin format
- `@mob-builder` — Skeleton/zombie geometry, AI state machines, mob animations
- `@vfx-combat` — Bloom wiring, hit flash, slash VFX, torch flicker, lighting

## Compile gotchas
- `PixmapIO.writePNG` needs `import com.badlogic.gdx.graphics.PixmapIO` (already in PlayScreen)
- `BlendingAttribute` constructor: `new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE)` for additive
- `DepthTestAttribute` constructor: `new DepthTestAttribute(int function, boolean depthMask)` — function = `GL20.GL_LEQUAL`
- gdx-vfx BloomEffect is in package `com.crashinvaders.vfx.effects`
- For `ZipFile` + `InputStream.readAllBytes()` — requires Java 9+; project uses Java 17 so fine
- `Arrays.sort` needs `java.util.Arrays`; `ZipFile`/`ZipEntry` need `java.util.zip.*`
