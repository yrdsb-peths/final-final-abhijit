# Minecraft Brawl

A 3D 1v1 brawler built in Java with [LibGDX](https://libgdx.com/), styled after Minecraft Dungeons and Brawl Stars. Fight an AI rival or a friend over LAN multiplayer.

---

## About

Players spawn on opposite ends of a block-grid arena and try to eliminate each other before the closing gas box forces a fight. The game features:

- **3D Minecraft-style player model** built entirely in code with walk/sprint animations
- **Melee combat** — diamond sword and iron sword with swing trails and hit feedback
- **Long-range combat** — potato gun with projectile physics
- **Diamond armor** with per-piece damage reduction rendered visually on the model
- **Closing gas box** — battle-royale shrinking box that deals damage over time
- **AI rival** with pathfinding, three difficulty levels, and sword/potato gun attacks
- **Local LAN multiplayer** — host or join a match over the same network
- **Custom map maker** — built for development. Note that maps created in the map maker cannot actually be played — it was solely for development purposes, and loading a custom map in-game would require tweaking the code.
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
| Drop Items | Q |
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

The map maker was used during development to build the built-in arenas. To access it, go to Options → General, toggle Developer Mode on, then return to the home screen, the Map Maker button will appear in the main menu. Note that maps created in the map maker cannot actually be played — it was solely for development purposes, and loading a custom map in-game would require tweaking the code.

---

## Where Arrays Are Used

**Primary — `SwooshTrail.java` (lines 36, 46, 62–78, 86–133)**

The sword swing trail stores blade positions in a `Sample[] ring` array each frame, then walks it to draw the glowing ribbon behind the sword.

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

I'm proud of building a fully 3D game from scratch in Java using LibGDX — no Unity, no Unreal. This includes a 3D rigged Minecraft-style player model with smooth walk and sprint animations built entirely in code, block-grid collision, a custom map editor, and a working local LAN multiplayer mode. Getting two players to fight each other in a 3D world over a network is a real technical challenge, and I'm proud it works.

### What coding challenges did you overcome?

The hardest challenge was getting multiplayer to feel smooth and actually playable. My first attempt was a total mess, the opponent would stutter and literally teleport whenever a packet arrived late or out of order. I had to rethink the whole approach with Claude Code: the host now runs the authoritative physics and broadcasts state at a fixed rate, while each client predicts its own movement locally so the game feels instant. The bug that took longest to track down was that each client was reading the wrong player's data. On my screen my opponent had no armor or sword, but on their screen they had all their armour and weapons. They could remove my armor while I couldn't even see theirs. Once I actually figured how to sync to both players, the multiplayer game actually worked pretty reliably. Still I would say there is more to improve, as through my testing I noticed alot of inconsistencies still need alot tweaking. 

---

## Credits

- **LibGDX** — game framework — https://libgdx.com
- **KryoNet** — networking library — https://github.com/EsotericSoftware/kryonet
- **Block textures, player skin, and sound effects** — Minecraft (Mojang Studios), used for educational non-commercial purposes
- **Player model** — custom Minecraft-style rigged model, built procedurally in code
- **JavaDoc API comments** — generated with AI assistance (allowed by the teacher)

---

*This project was primarily built by me over 3 weeks. AI assistance was used to speed up specific parts — JavaDoc generation, AI pathfinding logic, multiplayer integration and optimization, and repetitive parts, so I could focus my time on the core game systems and gameplay.*
