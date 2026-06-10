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
- **Custom map maker** — unlocked through Developer Mode in Options
- **Skin selector** — swap your player skin from any `.png` in the `skins/` folder
- **Session stats** — wins, losses, win rate, and per-match damage stats on the end screen

---

## Getting Started

**Requirement:** Java 11 or higher.

**Easiest way — double-click:**
Run `STARTGAME.jar` directly if you have Java installed.

**From terminal:**
```bash
# macOS / Linux
./gradlew lwjgl3:run

# Windows
gradlew.bat lwjgl3:run
```

On first launch a three-card tutorial walks you through movement, combat, and inventory. You can click through it quickly to reach the main menu.

---

## Controls

| Action | Key / Mouse |
|---|---|
| Move | W A S D |
| Jump / Sprint | Space |
| Attack | Left Mouse Button |
| Swap hotbar slot | 1 / 2 |
| Pause | Escape |
| God Mode (invincible + fly) | F3 |
| Force-start gas ring | F4 |

---

## Multiplayer (LAN)

Both computers must be on the **same Wi-Fi or Ethernet network**.

1. **Host:** click **Multiplayer → Create Match** and wait.
2. **Guest:** click **Multiplayer → Join Match**, then select the host from the list.
3. The match starts automatically once both players are connected.

The guest's camera is flipped 180° (they start on the north side facing south) and their WASD controls are automatically adjusted to match.

---

## Cheat / Debug Modes

| Action | Effect |
|---|---|
| **F3** during a match | Toggle God Mode — invincible and can fly. Quick way to reach the end screen. |
| **F4** during a match | Force-activate the gas ring immediately. |
| **Options → Developer Mode ON** | Unlocks the Map Maker button on the main menu. |

---

## Where Arrays Are Used

The rubric requires *animation that shows the use of arrays*. Here is exactly where to find it, plus the other significant array usage in the project:

### Primary — array-based animation (`MainMenuScreen.java`)

**File:** `core/src/main/java/com/brawlgame/screen/MainMenuScreen.java`
**Lines:** ~65–70 (fields), ~102–109 (setup in `show()`), ~237–241 (draw in `render()`)

The main menu displays an animated water tile next to the title logo. It works like this:

1. `water_still.png` (a vertical sprite sheet) is loaded as a `Texture`.
2. `TextureRegion.split()` slices it into a `TextureRegion[]` array called `waterFrames` — each element is one frame.
3. A `Animation<TextureRegion>` cycles through the array at 0.08 s per frame.
4. Every `render()` call advances `animStateTime` and draws `waterAnimation.getKeyFrame(animStateTime, true)`.

That `TextureRegion[]` array is the direct array-animation the rubric is asking for.

### Other array usage in the project

| File | Array | Purpose |
|---|---|---|
| `ui/Hotbar.java` lines 31–32 | `Texture[] icons`, `String[] labels` | Hotbar slot icons and labels stored in parallel arrays, indexed by slot number and drawn in a loop |
| `map/GameMap.java` line 45 | `BlockType[][] cells` | Entire map stored as a 2D `[col][row]` grid; collision, rendering, and saving all iterate over it |
| `entity/AiBrawler.java` line 166 | `PotatoProjectile[] potatoes` | Fixed pool of projectiles for the AI rival's ranged attacks; the launcher scans the array for a dead slot to reuse |
| `entity/ArmorRenderer.java` lines 72–74 | `Texture[]` per tier | Armor texture layers (diamond, iron, leather) stored as `Texture[]` arrays and iterated when rendering each armor piece |

---

## Project Structure

| Folder | Contents |
|---|---|
| `core/src/` | All game Java source code |
| `assets/maps/` | Built-in arena map files (`.map`) |
| `assets/textures/` | Block and player textures |
| `assets/sounds/` | Sound effects and music (vanilla Minecraft audio) |
| `lwjgl3/` | Desktop launcher and build config |
| `STARTGAME.jar` | Pre-built runnable jar |

---

## Reflection

### What are you proud of accomplishing?

I'm proud of building a fully **3D game from scratch** in Java using LibGDX — no Unity, no Unreal, no game engine doing the heavy lifting. This includes a 3D rigged Minecraft-style player model with smooth walk and sprint animations built entirely in code, block-grid collision, a custom map editor, and a working **local LAN multiplayer mode**. Getting two players to fight each other live in a 3D world over a network is a real technical challenge, and I'm proud it works.

### What coding challenges did you overcome?

The hardest challenge was **syncing the multiplayer game state**. Early on, positions would snap and jitter because we were just sending raw coordinates. We moved to a server-authoritative model: the host runs a physics simulation and broadcasts world state at 30 Hz via UDP, while each client predicts its own movement locally every frame. The opponent is smoothed with linear interpolation so they never teleport. The trickiest bug was figuring out that each client was applying the wrong server slot to itself — the mapping of "which slot is me vs. the opponent" was getting confused depending on connection order. We fixed it using KryoNet's real connection IDs so each side can always correctly identify its own data versus the enemy's, regardless of who connected first.

---

## Credits

- **LibGDX** — game framework — https://libgdx.com
- **KryoNet** — networking library — https://github.com/EsotericSoftware/kryonet
- **Block textures, player skin, and sound effects** — Minecraft (Mojang Studios), used for educational non-commercial purposes
- **Player model** — custom Minecraft-style rigged model, built procedurally in code
- **JavaDoc API comments** — generated with AI assistance (permitted by the teacher)
