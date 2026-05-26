# BrawlGame — LibGDX Project Plan
*Full session notes — May 26, 2026*

---

## Project Overview

A multiplayer Brawl Stars-inspired game built in **LibGDX** (Java). Players move with WASD, aim with the mouse, and fight on a shared map. The game runs on a default server, resets every 5 minutes, and allows anyone to join at any time.

---

## Why LibGDX (not Greenfoot)

Greenfoot was considered first but rejected because:
- It has no 3D renderer — can't rotate a 3D model at runtime
- Pre-rendering 36 sprite angles was the only workaround
- LibGDX has a full built-in 3D engine — models rotate smoothly to any angle in real time
- Still Java, still submittable as a school project
- More impressive result

---

## Tech Stack

| Tool | Purpose |
|---|---|
| **LibGDX** | Game framework (Java) |
| **VS Code** | IDE (with Java + Gradle extensions) |
| **JDK 17** | Java runtime |
| **gdx-liftoff** | Project generator |
| **Kryonet** | Multiplayer networking |
| **Blender** | 3D model prep + FBX export |
| **Mixamo** | Free auto-rigging + animations |
| **fbx-conv** | Convert FBX → g3db for LibGDX |
| **Meshy / Tripo3D / Hugging Face TripoSR** | AI 3D model generation |

---

## VS Code Extensions to Install

- **Extension Pack for Java** (Microsoft)
- **Gradle for Java** (Microsoft)
- **Rainbow Brackets** (optional, helps readability)
- **Grep Console** (optional, colored debug output)

---

## LibGDX Project Setup

### gdx-liftoff Settings
```
Name:        BrawlGame
Package:     com.brawlgame
Main Class:  BrawlGame
Platform:    Desktop (LWJGL3) only
Template:    ApplicationAdapter
Languages:   none (plain Java — default)
Extensions:  none for MVP
Third-party: none for MVP
Java Ver:    17
```

### Running the Game
```
Gradle panel → lwjgl3 → Tasks → application → run
```

---

## 3D Model Pipeline

```
AI generator (Tripo3D / Hugging Face TripoSR)
    → export OBJ or FBX
    → Blender: open OBJ, export as FBX
    → Mixamo: upload FBX, auto-rig, download animations (idle + walk)
    → Blender: combine all animation FBXs into one file
    → fbx-conv: convert to .g3db
    → drop into assets/models/
```

### Free Model Sources (if AI generators don't work)
- **kenney.nl/assets/animated-characters-2** — free, no account, already low poly
- **sketchfab.com** — filter: free + downloadable + low poly
- **blockbench.net** — make your own in browser, exports FBX free
- **huggingface.co/spaces/stabilityai/TripoSR** — free image-to-3D
- **readyplayer.me** — free character creator, exports GLB

### Animations Needed Per Character
| Name | Trigger |
|---|---|
| `idle` | Standing still |
| `walk` | WASD held |
| `attack` | Left click |
| `super` | Right click / E key |
| `death` | Health hits 0 |

### Check Animation Names in LibGDX
```java
for (Animation a : characterModel.animations) {
    System.out.println("Animation: " + a.id);
}
```

---

## Game Design

### Playable Characters

**Volt** (Mechanical gunner)
- Basic attack: 3 rapid low-damage bullets in tight spread
- Super: Piercing laser beam across the whole map
- Passive: Moves slightly faster

**Briar** (Nature mage)
- Basic attack: Slow thorned projectile that splits into 3 on impact
- Super: Plants a vine trap — roots enemies for 2 seconds
- Passive: Slowly regenerates health

### Enemies

**Grunt** (Melee rusher)
- Charges directly at nearest player
- No ranged attack, damages on contact
- Low health, spawns in groups of 3
- Telegraphs a charge animation

**Turret** (Stationary ranged)
- Doesn't move
- Rotates to face nearest player
- Fires slow high-damage shots every 2 seconds
- High health

---

## Controls

| Input | Action |
|---|---|
| WASD | Move |
| Mouse position | Character faces mouse |
| Left click | Basic attack |
| Right click / E | Super ability |

---

## Multiplayer Design (Kryonet)

### How It Works
- One player hosts → runs Kryonet server on port 54555 (TCP) / 54777 (UDP)
- All others connect to host's IP
- UDP for position updates every frame (fast, okay if a packet drops)
- TCP for important events (joins, shots, game over — never drops)

### Name Check Flow
```
Player enters name
    → server checks if name already taken
    → taken: prompt to change before joining
    → free: spawn player, broadcast to all clients
```

### 5-Minute Timer Flow
```
Game starts → 5 min timer begins
Anyone can join the whole time
Timer hits 0 → server sends GAME_OVER to all clients
All clients show scoreboard
Server resets world, accepts new joins again
```

### Packets
```java
JoinRequest    // client → server: name + character type
JoinResponse   // server → client: accepted/rejected + reason
PlayerState    // client → server (UDP every frame): x,y,z,angle,animState,health
GameOver       // server → all clients
PlayerLeft     // server → all clients when someone disconnects
ProjectileFired // client → server (TCP): position + direction
```

---

## Project File Structure

```
BrawlGame/
├── core/src/com/brawlgame/
│   ├── BrawlGame.java              ← main entry
│   ├── screens/
│   │   ├── MenuScreen.java         ← name entry + character select
│   │   └── GameScreen.java         ← main game
│   ├── entities/
│   │   ├── Player.java
│   │   ├── RemotePlayer.java
│   │   ├── Enemy.java
│   │   ├── Grunt.java
│   │   └── Turret.java
│   ├── weapons/
│   │   └── Projectile.java
│   ├── network/
│   │   ├── GameServer.java
│   │   ├── GameClient.java
│   │   └── Packets.java
│   └── utils/
│       └── AnimationHelper.java
│
└── assets/
    ├── models/
    │   ├── volt.g3db
    │   └── briar.g3db
    ├── maps/
    │   └── map.g3db
    └── ui/
        └── skin.json
```

---

## MVP Code (Current State)

Blue box player on green ground, WASD movement, mouse-aim rotation, isometric camera that follows the player.

```java
package com.brawlgame;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.graphics.g3d.environment.*;
import com.badlogic.gdx.graphics.g3d.utils.*;
import com.badlogic.gdx.math.*;

public class BrawlGame extends ApplicationAdapter {

    ModelBatch modelBatch;
    Environment environment;
    PerspectiveCamera camera;

    // Player box
    ModelInstance player;
    Model playerModel;
    Vector3 playerPos = new Vector3(0, 0.5f, 0);
    float SPEED = 8f;

    // Ground
    ModelInstance ground;
    Model groundModel;

    @Override
    public void create() {
        modelBatch = new ModelBatch();

        environment = new Environment();
        environment.set(new ColorAttribute(
            ColorAttribute.AmbientLight, 0.6f, 0.6f, 0.6f, 1f));
        environment.add(new DirectionalLight()
            .set(0.9f, 0.9f, 0.9f, -1f, -0.8f, -0.2f));

        camera = new PerspectiveCamera(60,
            Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.1f;
        camera.far = 500f;

        ModelBuilder mb = new ModelBuilder();

        // Blue player box
        playerModel = mb.createBox(1f, 1f, 1f,
            new Material(ColorAttribute.createDiffuse(
                new Color(0.2f, 0.4f, 1f, 1f))),
            VertexAttributes.Usage.Position |
            VertexAttributes.Usage.Normal);
        player = new ModelInstance(playerModel);

        // Green ground
        groundModel = mb.createBox(100f, 0.1f, 100f,
            new Material(ColorAttribute.createDiffuse(
                new Color(0.3f, 0.6f, 0.2f, 1f))),
            VertexAttributes.Usage.Position |
            VertexAttributes.Usage.Normal);
        ground = new ModelInstance(groundModel);
        ground.transform.setToTranslation(0, -0.05f, 0);
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();

        handleMovement(delta);
        updateFacing();
        updateCamera();

        Gdx.gl.glViewport(0, 0,
            Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT |
            GL20.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(camera);
        modelBatch.render(ground, environment);
        modelBatch.render(player, environment);
        modelBatch.end();
    }

    void handleMovement(float delta) {
        Vector3 move = new Vector3();

        if (Gdx.input.isKeyPressed(Input.Keys.W)) move.z -= 1;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) move.z += 1;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) move.x -= 1;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) move.x += 1;

        if (move.len() > 0) {
            move.nor().scl(SPEED * delta);
            playerPos.add(move);
            playerPos.x = MathUtils.clamp(playerPos.x, -48f, 48f);
            playerPos.z = MathUtils.clamp(playerPos.z, -48f, 48f);
        }
    }

    void updateFacing() {
        Ray ray = camera.getPickRay(
            Gdx.input.getX(), Gdx.input.getY());
        float dist = -ray.origin.y / ray.direction.y;
        Vector3 mouseWorld = new Vector3(ray.origin)
            .mulAdd(ray.direction, dist);

        float angle = MathUtils.atan2(
            mouseWorld.z - playerPos.z,
            mouseWorld.x - playerPos.x
        ) * MathUtils.radiansToDegrees;

        player.transform
            .setToRotation(Vector3.Y, -angle)
            .setTranslation(playerPos);
    }

    void updateCamera() {
        camera.position.set(
            playerPos.x,
            playerPos.y + 18f,
            playerPos.z + 13f
        );
        camera.lookAt(playerPos);
        camera.up.set(Vector3.Y);
        camera.update();
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        playerModel.dispose();
        groundModel.dispose();
    }
}
```

---

## Build Order (Full Game)

1. ✅ LibGDX project setup
2. ✅ MVP — box moves on map, camera follows, faces mouse
3. ⬜ Add shooting (left click fires projectile)
4. ⬜ Swap box for real 3D model with animations
5. ⬜ Add second character
6. ⬜ Add Grunt + Turret enemies with basic AI
7. ⬜ Add map (Tiled or 3D map model)
8. ⬜ Add Kryonet multiplayer
9. ⬜ Add name entry menu screen
10. ⬜ Add 5-minute timer + game restart
11. ⬜ Polish + test

---

## Next Immediate Step

Get the MVP code running in VS Code — blue box moving on green map. Then add shooting.
