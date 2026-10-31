# Road Network 🛣️

[![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2-brightgreen.svg)](https://minecraft.net/)
[![Fabric Loader](https://img.shields.io/badge/Fabric%20Loader-%3E%3D0.19.0-blue.svg)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25%2B-orange.svg)](https://adoptium.net/)
[![License: CC0-1.0](https://img.shields.io/badge/License-CC0_1.0-lightgrey.svg)](https://creativecommons.org/publicdomain/zero/1.0/)

**Road Network** is an autonomous, server-side-safe Fabric mod for Minecraft that civil-engineers natural, handcrafted transportation networks connecting Overworld villages, outposts, and major structures.

Featuring dynamic terrain-conforming overland trails, sweeping Catmull-Rom cubic splines, arched river bridges, Roman barrel vault mountain tunnels, and biome-adaptive palettes, **Road Network** brings civil engineering and master builder artistry to your Minecraft world with **zero player commands required**.

---

## 🌟 Key Features

### 1. Autonomous Network Discovery & Planning
* **Zero Commands Needed**: As you explore or generate terrain, regional discovery runs asynchronously in the background without freezing or lagging the server tick.
* **Intelligent Graph Planning**: Utilizes a deterministic Minimum Spanning Forest (Kruskal's Algorithm) combined with Relative Neighborhood Graphs (RNG) to plan natural highways between settlements without redundant parallel routes.
* **Progressive Chunk Materialization**: Roads materialize safely as chunks load (`CHUNK_LOAD`), preventing chunk-loading cascades, server deadlocks, or distant world generation lag.

### 2. Smooth Terrain-Conforming Overland Roads
* **Natural Ground Conformity**: Road surfaces adapt directly to the loaded chunk's true terrain surface (`roadY = groundY`), eliminating floating roads in meadows or sunken trenches under hills.
* **Half-Block Slab Transitions**: Elevation changes utilize smooth half-slab ramps and steps, allowing players and horses to traverse slopes without constant jumping.
* **Seamless Chunk Continuity**: Boundary-buffered path sampling eliminates missing road strips, diagonal cutoffs, or bite marks across chunk borders.

### 3. Arched River & Waterway Bridges
* **Water Surface Clearance**: Bridges span rivers, lakes, and oceans at least 2 blocks above the water surface, maintaining clear passage for boats beneath the deck.
* **Realistic Support Piers**: Piers extend downward through water to solid riverbed footings (Cobblestone/Stone Bricks) with stripped log trestles and structural arch brackets.
* **Handcrafted Balustrades**: Alternating fence and fence-gate railings lined with warm lanterns mounted on pier posts.

### 4. Roman Barrel Vault Mountain Tunnels
* **Mountain Piercing**: Automatically triggers when cutting through steep mountain ridges ($\ge 6$ blocks high) where roads cannot climb.
* **Spacious Interior Clearance**: 4–5 blocks of vertical clearance, accommodating players riding on horseback.
* **Architectural Detailing**: Paved with cobblestone, andesite, and stone bricks, reinforced with stone brick arch ribs, inverted stair brackets, and suspended iron chain lanterns.

### 5. Tree Trunk & Canopy Demolition
* **No Floating Trees**: Intersecting tree trunks and 2x2 large trees (Dark Oak, Giant Spruce, Jungle) are cleared up to 32 blocks upwards along with surrounding leaf canopies.
* **Open Verge Envelope**: Maintains a 1-block clear side verge on either side of the road up to 4 blocks high for unobstructed visibility and riding.

### 6. Village & Structure Blending
* **Structure Preservation**: Never overwrites village houses, workstations, beds, bells, doors, or crops (`BlockTags.CROPS`, `FARMLAND`).
* **Street Interconnection**: Smoothly docks into existing village paths (`DIRT_PATH`, `GRAVEL`, `COBBLESTONE`, `SMOOTH_SANDSTONE`).

### 7. Biome-Adaptive Palettes & Street Furniture
* **Dynamic Regional Materials**:
  * **Temperate (Plains, Meadow, Forest)**: Dirt Path, Packed Mud, Coarse Dirt, Oak Slabs, Cobblestone Walls, Oak Fences.
  * **Taiga & Cold (Taiga, Old Growth Pine, Snowy Plains)**: Podzol, Coarse Dirt, Gravel, Spruce Slabs, Cobblestone Walls, Spruce Fences.
  * **Arid (Desert, Badlands, Savanna)**: Smooth Sandstone, Cut Sandstone, Terracotta, Sandstone Walls, Acacia Fences.
* **Atmospheric Lighting**: Zero floor-torch spam. Roads are lit by roadside lampposts (cobblestone wall + fence post + hanging lantern) spaced every 36 blocks and chiseled milestone markers every 50 blocks.

---

## 📦 Dependencies

| Dependency | Required Version | Purpose |
| :--- | :--- | :--- |
| **Minecraft** | `26.1.2` | Base game platform |
| **Java** | `25` or higher | Runtime environment |
| **Fabric Loader** | `>= 0.19.0` (tested on `0.19.5`) | Mod loader |
| **Fabric API** | `0.155.3+26.1.2` (or compatible) | Essential Fabric lifecycle events and block tags |

### Recommended Mods (Optional)
* **[Mod Menu](https://modrinth.com/mod/modmenu)**: View installed mod information and metadata.
* **[FerriteCore](https://modrinth.com/mod/ferrite-core)** / **[Lithium](https://modrinth.com/mod/lithium)**: General game performance and memory optimization.

---

## 🚀 Installation

1. Install **[Fabric Loader](https://fabricmc.net/use/)** (`0.19.0+`) for Minecraft `26.1.2`.
2. Download the latest **[Fabric API](https://modrinth.com/mod/fabric-api)** for `26.1.2` and place the `.jar` into your Minecraft `.minecraft/mods` directory.
3. Place `road-network-1.0.0.jar` into your `.minecraft/mods` folder.
4. Launch Minecraft and create or load any Overworld world! Roads will begin discovering and building automatically.

---

## 🛠️ Building From Source

### Prerequisites
* **Java Development Kit (JDK) 25** or newer.
* Internet connection for initial Gradle dependency resolution.

### Build Commands

```bash
# Clone the repository
git clone https://github.com/trinankur/road-network.git
cd road-network

# Run unit tests
./gradlew test

# Run full milestone & architectural integration test runner
./gradlew runTestRunner

# Build the production mod JAR
./gradlew build
```

The compiled mod JAR will be located in `build/libs/road-network-1.0.0.jar`.

---

## 📂 Project Architecture

```
com.trinankur.roadnetwork/
├── autonomous/       # Background regional scanners & autonomous route dispatchers
├── bridge/           # River span & ravine bridge structural evaluation
├── config/           # Mod configuration settings
├── discovery/        # Structure classification (Major, Secondary, Ignored)
├── generation/       # Dynamic chunk materialization, palettes & tree removal
├── network/          # Graph topology (Nodes, Edges, Routes, Minimum Spanning Forest & RNG)
├── pathfinding/      # 3D A* search, Catmull-Rom splines, and chunk partitioning
├── tunnel/           # Mountain tunnel piercing evaluation & arch construction
└── util/             # Overworld dimension validation & math helpers
```

---

## 📄 License

This mod is licensed under the **[CC0 1.0 Universal](https://creativecommons.org/publicdomain/zero/1.0/)** public domain dedication. Feel free to learn from it, fork it, or integrate it into your own modpacks and projects!
