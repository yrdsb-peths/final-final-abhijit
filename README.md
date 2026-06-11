# Minecraft Brawl

A 3D 1v1 brawler built in Java with [LibGDX](https://libgdx.com/), styled after Minecraft Dungeons and Brawl Stars. Fight an AI rival or a friend over LAN multiplayer.

---

## About

Players spawn on opposite ends of a block-grid arena and try to eliminate each other before the closing gas ring forces a fight. The game features:

- **3D Minecraft-style player model** built entirely in code with procedural walk/sprint animations
- **Melee combat** — diamond sword and iron sword with swing trails and hit feedback
- **Diamond armor** with per-piece damage reduction rendered visually on the model
- **Closing gas ring** — battle-royale shrink circle that deals damage over time
- **AI rival** with pathfinding, three difficulty levels, and sword/gun attacks
- **Local LAN multiplayer** — host or join a match over the same network
- **Custom map maker** — built for development; see note below
- **Skin selector** — swap your player skin from any `.png` in the `skins/` folder
- **Session stats** — wins, losses, win rate, and per-match damage stats on the end screen

---

## Getting Started

**Requirement:** Java 11 or higher.

**Easiest way — double-click:**
Run `BrawlGame.jar` directly if you have Java installed.

**From terminal:**
```bash
# macOS / Linux
java -jar BrawlGame.jar

# Windows
java -jar BrawlGame.jar
```

Or build from source:
```bash
./gradlew lwjgl3:run        # macOS / Linux
gradlew.bat lwjgl3:run      # Windows
```

On first launch a three-card tutorial walks you through movement, combat, and inventory.

---

## Controls

| Action | Key / Mouse |
|---|---|
| Move | W A S D |
| Jump / Sprint | Space |
| Attack | Left Mouse Button |
| Swap hotbar slot | 1 / 2 |
| Pause | Escape |

---

## Multiplayer (LAN)

Both computers must be on the **same Wi-Fi or Ethernet network**.

1. **Host:** click **Multiplayer → Create Match** and wait.
2. **Guest:** click **Multiplayer → Join Match**, then select the host from the list.
3. The match starts automatically once both players are connected.

The guest's camera is flipped 180° (they start on the north side facing south) and their WASD controls are automatically adjusted to match.

---

## Map Maker

The map maker was used during development to build the built-in arenas. It is not accessible from the normal menu — enabling it requires renaming a configuration file in the file system. The built-in maps are the ones that ship with the game.

---

## Where Arrays Are Used

The rubric requires *animation that shows the use of arrays*. Here is exactly where to find it, plus the other significant array usage in the project:

### Primary — array-based animation (`SwooshTrail.java`)

**File:** `core/src/main/java/com/brawlgame/gfx/SwooshTrail.java`
**Lines:** 36 (field), 46 (init), 62–78 (update), 86–133 (render)

The sword swing trail is animated using a `Sample[] ring` array — a fixed-size ring buffer of blade tip/base positions recorded each frame during a swing:

1. `Sample[] ring = new Sample[MAX_SAMPLES]` — fixed array of 14 sample slots, each holding a `tip` and `base` Vector3 and an age timer.
2. Every frame during a swing, `addSample()` writes the current blade positions into the next slot: `ring[head] = ...`.
3. `update(delta)` iterates the full array each frame, advancing each sample's age and marking expired ones unused.
4. `render()` walks the array newest-to-oldest, builds a triangle-strip into a `float[] verts` array, and uploads it to the GPU — producing the glowing ribbon that trails behind the sword.

The ribbon fades sample-by-sample as entries age out of the array, giving the trail its head-to-tail dissolve.

### Other array usage in the project

| File | Array | Purpose |
|---|---|---|
| `gfx/SwooshTrail.java` line 42 | `float[] verts` | Vertex buffer rebuilt from the ring each frame and uploaded to the GPU for rendering |
| `ui/Hotbar.java` lines 31–32 | `Texture[] icons`, `String[] labels` | Hotbar slot icons and labels stored in parallel arrays, indexed by slot number and drawn in a loop |
| `map/GameMap.java` line 45 | `BlockType[][] cells` | Entire map stored as a 2D `[col][row]` grid; collision, rendering, and saving all iterate over it |
| `entity/AiBrawler.java` line 166 | `PotatoProjectile[] potatoes` | Fixed pool of projectiles for the AI rival's ranged attacks; the launcher scans the array for a dead slot to reuse |
| `entity/ArmorRenderer.java` lines 72–74 | `Texture[]` per tier | Armor texture layers (diamond, iron, leather) stored as `Texture[]` arrays and iterated when rendering each armor piece |
| `entity/BotPathfinder.java` lines 69–71 | `int[] dc`, `int[] dr`, `float[] cost` | Direction offset and movement cost arrays iterated each A* step to expand the eight neighbours of a cell |

---

## Project Structure

| Folder | Contents |
|---|---|
| `core/src/` | All game Java source code |
| `assets/maps/` | Built-in arena map files (`.map`) |
| `assets/textures/` | Block and player textures |
| `assets/sounds/` | Sound effects and music (vanilla Minecraft audio) |
| `lwjgl3/` | Desktop launcher and build config |
| `BrawlGame.jar` | Pre-built runnable jar — just needs Java 11+ |

---

## Reflection

### What are you proud of accomplishing?

I'm proud of building a fully **3D game from scratch** in Java using LibGDX — no Unity, no Unreal, no game engine doing the heavy lifting. This includes a 3D rigged Minecraft-style player model with smooth walk and sprint animations built entirely in code, block-grid collision, a custom map editor, and a working **local LAN multiplayer mode**. Getting two players to fight each other live in a 3D world over a network is a real technical challenge, and I'm proud it works.

### What coding challenges did you overcome?

The hardest challenge was **getting multiplayer to feel smooth**. My first attempt just sent each player's position over the network every frame, and it looked terrible — the opponent would stutter and snap whenever a packet arrived late or out of order. I had to rethink the whole approach: the host now runs the authoritative physics and broadcasts state at a fixed rate, while each client predicts its own movement locally so the game feels instant for you regardless of ping. The opponent is interpolated between received positions so they never teleport. The bug that took longest to track down was that each client was reading the wrong player's data from the server — it was assigning itself the opponent's slot depending on who connected first. Once I tied each client to its actual network connection ID, the mapping was always correct no matter the connection order.

---

## Credits

- **LibGDX** — game framework — https://libgdx.com
- **KryoNet** — networking library — https://github.com/EsotericSoftware/kryonet
- **Block textures, player skin, and sound effects** — Minecraft (Mojang Studios), used for educational non-commercial purposes
- **Player model** — custom Minecraft-style rigged model, built procedurally in code
- **JavaDoc API comments** — generated with AI assistance (permitted by the teacher)
