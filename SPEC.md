# ROAD NETWORK
## Product & Functional Specification
**Version:** 1.0 — Initial Project Vision  
**Minecraft:** Java Edition 26.1.2  
**Mod Loader:** Fabric  
**Language:** Java  
**Mod ID:** `roadnetwork`  
**Project Type:** Standalone Minecraft Mod

---

# 1. Project Vision

Road Network is a procedural world-generation mod designed to connect Minecraft's naturally generated villages, settlements and other structures through an intelligent, persistent transportation network.

The objective is to make Minecraft worlds feel geographically and socially connected.

In vanilla Minecraft, villages, temples, pillager outposts, mansions and other structures generate independently. There is generally no transportation infrastructure connecting them.

Our mod aims to change that.

We want players to discover naturally generated roads while exploring the world. Those roads should lead somewhere meaningful: another village, an outpost, a temple or another interesting destination.

The roads should not appear randomly when a player approaches a structure. Their destinations and routes should be determined independently of the player's exploration, with physical road sections generated progressively as the world is generated.

**Core philosophy: The world should feel as though its transportation network existed before the player arrived.**

# 2. Core Concept: A World That Knows Its Structures

Minecraft already has a deterministic world-generation system.

The world seed, structure placement configurations, biome restrictions and other generation parameters determine where structures can potentially generate.

Our mod should take advantage of this existing infrastructure rather than reinventing structure placement.

The Structure Discovery Engine should be able to identify nearby potential structures based on the current world and its seed.

For example, imagine a player spawning in a forest.

The mod examines the surrounding area and identifies potential destinations:

- A plains village approximately 450 blocks away.
- A pillager outpost approximately 900 blocks away.
- A ruined portal approximately 300 blocks away.
- A desert temple approximately 1,200 blocks away.

These are illustrative examples.

The mod does not need to generate all these chunks to begin planning. It should use structure placement information to calculate potential locations and validate them as reliably as the Minecraft API permits.

The discovery system should distinguish between:

1. **Predicted structures:** Potential locations calculated using structure placement rules.
2. **Confirmed structures:** Locations where a valid structure start has been established.

A predicted location must never automatically be treated as a confirmed structure.

The system should support different structure placement implementations rather than assuming every structure uses the same placement algorithm.

# 3. Recursive Structure Discovery

This is one of the most important features of the entire mod.

We do not want to calculate every structure and every road in the entire Minecraft world before the player starts exploring.

Instead, we want a localized, recursive discovery system.

## 3.1 Initial discovery

When the world starts, or when the player enters a new unexplored region, the mod identifies suitable nearby structure destinations.

The discovery process should prioritize finding at least one suitable destination from each relevant structure category when one exists within the configured search radius.

It should not require finding every structure in the surrounding region.

## 3.2 Destination-based expansion

When the network identifies a destination, that destination becomes another potential expansion point.

For example:

Initial location:

Forest → Village A

Once Village A is identified, the discovery system examines the surrounding region again.

Village A → Village B  
Village A → Pillager Outpost  
Village A → Desert Temple

When Village B becomes part of the network, its surrounding region can be examined as well.

Village B → Village C  
Village B → Woodland Mansion

This process continues as the network expands.

The system should reuse previously discovered destinations and existing connections rather than repeatedly discovering and planning the same things.

## 3.3 Discovery independence

Structure discovery must not depend exclusively on structures already visited by the player.

The mod should use world-generation information to predict potential destinations beyond explored chunks.

However, it must avoid force-loading distant chunks or unnecessarily generating terrain merely to discover structures.

# 4. Structure Classification and Priority

Not every structure should receive the same type of road.

The mod should classify structures into categories and use those categories when deciding how the network expands.

## 4.1 Major settlements

Examples:

- Plains villages.
- Savanna villages.
- Desert villages.
- Taiga villages.
- Snowy villages.
- Modded villages and settlements.

These are the primary destinations of the transportation network.

Expected connections:

- Connect nearby settlements.
- Create major roads between settlements.
- Reuse existing roads whenever possible.
- Allow secondary structures to connect to these roads.

## 4.2 Secondary structures

Examples:

- Pillager Outposts.
- Woodland Mansions.
- Witch Huts.
- Desert Temples.
- Jungle Temples.
- Other suitable surface structures.

These should generally connect to the nearest suitable settlement or an existing road.

They should not unnecessarily create independent transportation networks.

## 4.3 Special and optional structures

Examples:

- Ruined Portals.
- Trail Ruins.
- Other interesting structures provided by vanilla or mods.

Their connection behavior should be configurable.

Some structures might receive small trails instead of full roads.

## 4.4 Excluded structures

Not every structure should receive a road.

Examples of structures that should generally be excluded by default:

- Buried Treasure.
- Mineshafts.
- Underground Ancient Cities.
- Structures that are inaccessible or unsuitable for surface roads.

The exclusion system must be configurable.

## 4.5 Data-driven compatibility

Structure classification should not rely entirely on hardcoded Java lists.

We should use Minecraft structure identifiers, data-driven tags and configuration.

Proposed tags:

- `#roadnetwork:settlements`
- `#roadnetwork:secondary`
- `#roadnetwork:special`
- `#roadnetwork:ignored`

This allows modpack creators to register their own structures without requiring us to write dedicated compatibility code for every mod.

# 5. Road Network Planning

The mod should maintain a logical graph of structures and their connections.

Each structure becomes a network node.

Each road becomes an edge connecting two nodes or joining an existing road network.

Example:

```text
                 Village B
                    |
                    |
Village A ----------+---------- Village C
                    |
                    |
                Outpost A
                    |
                    |
                Desert Temple
```

This is an illustrative network, not a requirement that every destination connect to the same central point.

## 5.1 Connection rules

Major settlements:

- Prioritize connections to nearby major settlements.
- Prefer a small number of useful connections over connecting every settlement to every other settlement.
- Reuse existing network connections where possible.

Secondary structures:

- Prefer connecting to the nearest suitable settlement.
- If an existing road is closer than a settlement, consider connecting to that road.
- Avoid unnecessary connections between nearby secondary structures.

Special structures:

- Follow configurable connection rules.
- Prefer smaller trails where appropriate.

## 5.2 Avoid unnecessary roads

The network should avoid:

- Duplicate connections.
- Multiple parallel roads connecting the same destinations.
- Excessive intersections.
- Unnecessary branches.
- Roads that terminate without a meaningful destination.

A suitable graph algorithm should maintain a reasonably efficient transportation network.

Possible approaches include minimum spanning trees, relative neighborhood graphs or other proximity-based graph algorithms.

The final algorithm should be selected after performance and connectivity testing.

## 5.3 Existing road reuse

An existing road should be considered part of the network.

If a new structure can connect to an existing road instead of creating a completely new road to a village, the mod should prefer that option where practical.

This allows the network to grow naturally without becoming unnecessarily dense.

# 6. Road Generation

The mod must generate actual physical roads, not merely visual trails.

Roads should be recognizable, continuous and suitable for travelling.

## 6.1 Road hierarchy

Initial road types:

| Road type | Approximate width | Purpose |
|---|---:|---|
| Main road | 5–7 blocks | Connect major settlements |
| Secondary road | 3–5 blocks | Connect secondary structures |
| Village road | 3 blocks | Connect to village entrances |
| Trail | 2 blocks | Reach smaller or isolated structures |

These values should be configurable.

## 6.2 Biome-aware materials

Road materials should adapt to the surrounding biome.

Examples:

**Plains**
- Dirt paths.
- Gravel.
- Coarse dirt.
- Occasional stone sections.

**Forest**
- Coarse dirt.
- Gravel.
- Mossy stone details.
- Wooden decorations.

**Desert**
- Sandstone.
- Smooth sandstone.
- Sand and compacted-looking paths.

**Snowy biomes**
- Packed ice or snow-compatible surfaces where appropriate.
- Gravel.
- Stone.

**Taiga**
- Coarse dirt.
- Gravel.
- Spruce wood details.

Roads should feel like part of the biome rather than identical structures copied everywhere.

## 6.3 Natural road shapes

Roads should not look like perfectly straight lines drawn across the terrain.

The pathfinding system should produce natural-looking routes.

It should consider:

- Distance.
- Elevation.
- Terrain slope.
- Water.
- Forests.
- Cliffs.
- Existing roads.
- Biome-specific preferences.

Roads may curve naturally to avoid difficult terrain.

## 6.4 Roadside details

Future decoration features:

- Lanterns.
- Signposts.
- Wooden fences.
- Small resting areas.
- Roadside benches.
- Direction signs.
- Stone markers.

Decorations should be relatively uncommon and should not overwhelm the landscape.

# 7. Terrain Analysis and Pathfinding

We need an intelligent pathfinding engine.

A* is the initial preferred algorithm, but its implementation should be optimized for Minecraft's large terrain.

The pathfinder should not operate on every individual block across thousands of blocks.

We should investigate hierarchical pathfinding.

## 7.1 Terrain costs

Initial illustrative cost model:

| Terrain | Relative cost |
|---|---:|
| Flat plains | Very low |
| Grassland | Low |
| Forest | Moderate |
| Dense forest | Moderate to high |
| Shallow water | High |
| Steep hills | High |
| Mountains | Very high |
| Existing road | Very low |

The system should prefer a reasonable route rather than always selecting the shortest possible distance.

## 7.2 Elevation

Roads should adapt to elevation.

The system should support:

- Gradual slopes.
- Small stair sections where appropriate.
- Retaining walls.
- Winding mountain roads.
- Avoidance of excessively steep terrain.

## 7.3 Rivers and water

Roads should not simply terminate at rivers.

The mod should be able to generate:

- Small wooden bridges.
- Stone bridges.
- Wider bridges for major roads.
- Appropriate approaches to bridge entrances.

Long ocean crossings should be treated separately.

For large oceans, the initial preference should be to terminate roads at coastal settlements or potential ports rather than creating enormous bridges.

## 7.4 Forests and vegetation

Road generation should handle vegetation carefully.

It may clear obstructing leaves and small vegetation while avoiding unnecessary destruction of large trees or important terrain features.

The exact vegetation policy should be configurable.

# 8. Progressive Road Generation

This is another defining feature.

We do not want roads to suddenly appear when a player enters a village.

We want the road network to have predetermined routes, with the physical roads generated progressively as their corresponding chunks are generated.

## 8.1 Planned routes versus generated roads

The system must maintain two separate concepts:

**Planned road:**

A logical route connecting two destinations. Its route information is established independently of the player's immediate location.

**Generated road:**

The physical road blocks that have been placed in the corresponding chunks.

This separation is essential.

## 8.2 Chunk-based generation

Every planned road should be divided into manageable chunk-sized sections.

When a chunk enters the appropriate generation stage:

1. Check whether a planned road intersects the chunk.
2. Retrieve the relevant road segment.
3. Calculate its local block positions.
4. Generate only the part belonging to that chunk.
5. Record the generated status.

The generator must never attempt to write outside the permitted chunk-generation boundaries.

## 8.3 Exploration-direction independence

The result must be deterministic.

For example:

- Travelling from Village A to Village B should produce the same road as travelling from Village B to Village A.
- Approaching a road from a different direction must not produce another route.
- Regenerating or revisiting chunks must not create duplicate road sections.
- Road sections on both sides of a chunk boundary must connect seamlessly.

## 8.4 No sudden road appearances

The physical road should be generated through the normal chunk-generation process wherever possible.

The player should encounter completed road sections as the surrounding world generates.

The system should not wait until the player reaches a village before deciding that a road needs to exist.

# 9. Persistence and World Management

The road network must survive game restarts.

We should use Minecraft's appropriate persistent world-data mechanisms.

The system should save:

- Discovered structure destinations.
- Structure identifiers.
- Structure coordinates.
- Structure classifications.
- Planned connections.
- Road route control points.
- Road generation status.
- Network relationships.

We should avoid saving every individual road block coordinate.

Instead, store compact route information and reconstruct the corresponding chunk sections deterministically.

## 9.1 Existing worlds

The mod should eventually support installation into existing Minecraft worlds.

For already-generated chunks, we should support an explicit retroactive generation mechanism.

This should be separate from normal new-chunk generation.

Retroactive generation must:

- Avoid overwriting buildings.
- Avoid destroying player constructions.
- Avoid duplicating existing roads.
- Require appropriate safety checks.

The first release may prioritize newly generated chunks, but existing-world support should remain a planned capability.

# 10. Structure and Mod Compatibility

Road Network must be a standalone mod.

It should not require Moog's Paths or any other road-generation mod.

The initial implementation should support vanilla structures.

It should also be designed to work with modded structures whenever those structures use Minecraft's registered structure and placement systems.

We should provide:

- Structure registry discovery.
- Structure tags.
- Configurable classifications.
- Configurable connection rules.
- Configurable road materials.
- Placement-type compatibility.
- Optional compatibility integrations where needed.

A structure should not need to be explicitly hardcoded into Road Network just because it comes from another mod.

However, compatibility with unusual or custom placement implementations must be tested rather than assumed.

# 11. Configuration

Players should be able to customize the transportation network.

Initial configuration categories:

## Structure discovery
- Search radius.
- Discovery frequency.
- Enabled structure categories.
- Excluded structures.

## Network planning
- Maximum connections per settlement.
- Maximum connection distance.
- Secondary structure connection distance.
- Existing-road reuse.
- Network density.

## Road generation
- Main road width.
- Secondary road width.
- Trail width.
- Road materials.
- Biome-specific palettes.
- Decoration frequency.

## Terrain
- Maximum preferred slope.
- Water-crossing limits.
- Bridge generation.
- Vegetation clearing.
- Mountain route preferences.

## Performance
- Maximum planning operations per tick.
- Maximum pending routes.
- Background calculation settings.
- Generation scheduling.
- Search limits.

Configuration should have sensible defaults so players can install the mod without manually configuring everything.

# 12. Debugging and Administration

We should implement debug commands early.

Proposed commands:

```text
/roadnetwork scan
/roadnetwork debug
/roadnetwork map
/roadnetwork reload
/roadnetwork info
```

Their intended functionality:

**scan**

Displays nearby predicted and confirmed structures, coordinates, distances and classifications.

**debug**

Toggles visual debugging of destinations, planned routes and generated road segments.

**map**

Displays an overview of the local road network where practical.

**reload**

Reloads supported configuration and data-driven rules.

**info**

Displays information about the current network node or nearby road.

These commands are intended for development and administration. We can refine their syntax later.

# 13. Performance and Safety Requirements

Minecraft world generation is performance-sensitive.

The mod must follow these rules:

1. Never scan the entire world.
2. Never force-load distant chunks just to find structures.
3. Avoid expensive synchronous pathfinding on the server thread.
4. Avoid repeatedly calculating identical routes.
5. Avoid duplicate structure discovery.
6. Cache deterministic placement calculations where useful.
7. Divide long roads into manageable sections.
8. Generate blocks only within permitted chunk boundaries.
9. Preserve existing structures and player-built constructions.
10. Handle server shutdown and world saving safely.

Background calculations may be used for route planning, but actual world access and modification must follow Minecraft's thread-safety requirements.

# 14. Development Roadmap

The project should be implemented incrementally.

## M0 — Project Foundation
- Fabric project setup.
- Successful build.
- Main mod initializer.
- Logging.
- Basic configuration.

## M1 — Structure Discovery
- Enumerate registered structures.
- Enumerate StructureSets.
- Inspect placement implementations.
- Predict nearby candidate locations.
- Separate predicted and confirmed structures.
- Implement `/roadnetwork scan`.

## M2 — Structure Classification
- Major settlements.
- Secondary structures.
- Special structures.
- Exclusion rules.
- Data-driven structure tags.

## M3 — Road Network Planning
- Create network nodes.
- Create connection edges.
- Determine suitable destinations.
- Prevent duplicate connections.
- Reuse existing network connections.

## M4 — Terrain and Pathfinding
- Terrain analysis.
- Hierarchical A*.
- Elevation costs.
- Water detection.
- Natural route generation.
- Deterministic route storage.

## M5 — Physical Road Generation
- Road palettes.
- Road widths.
- Chunk-level road generation.
- Chunk boundary handling.
- Road foundations and surfaces.

## M6 — Recursive Network Expansion
- Expand the network from discovered destinations.
- Repeated nearby structure discovery.
- Connect new settlements to existing roads.
- Avoid unnecessary branches.

## M7 — Advanced Terrain Features
- Bridges.
- Mountain roads.
- Slopes.
- Retaining walls.
- Vegetation handling.

## M8 — Persistence and Existing World Support
- Persistent road network.
- Generated-section tracking.
- Retroactive generation.
- Safe handling of existing structures and player builds.

## M9 — Compatibility and Optimization
- Modded structure support.
- Datapack integration.
- Performance testing.
- Dedicated-server testing.
- Configuration improvements.

## M10 — Release Preparation
- Final testing.
- Documentation.
- Mod metadata.
- Modrinth release preparation.

# 15. Acceptance Criteria

The mod should not be considered complete merely because it can place a few road blocks.

A successful first public release should demonstrate the following:

### Structure discovery
- Correctly identifies nearby vanilla structures.
- Supports multiple structure placement implementations.
- Supports suitable modded structures.
- Distinguishes predictions from confirmed structures.

### Network planning
- Prioritizes major settlements.
- Connects secondary structures appropriately.
- Avoids unnecessary duplicate roads.
- Expands recursively.

### Road generation
- Generates continuous roads.
- Handles chunk boundaries correctly.
- Uses appropriate biome materials.
- Adapts to terrain.
- Generates bridges where supported.

### Exploration experience
- Roads do not suddenly appear only when a player reaches a destination.
- Routes are deterministic.
- Newly generated chunks continue existing planned roads.
- Players can follow roads to meaningful destinations.

### Stability
- Works on Minecraft 26.1.2 Fabric.
- Survives server restarts.
- Does not unnecessarily force-load chunks.
- Does not corrupt existing world generation.
- Has acceptable performance in large worlds.

# 16. Features Outside the Initial Scope

The following should not delay the first release:

- NPC traffic.
- Minecart transportation networks.
- Automated vehicles.
- Player-built road recognition.
- Dynamic economy and trading routes.
- Custom road-building villagers.
- Large-scale city generation.
- Roads that modify village architecture.

These can be considered future extensions.

# 17. Final Product Experience

Imagine a new Minecraft world.

A player spawns in a forest.

They begin exploring and eventually discover a gravel road.

The road passes between trees, follows the natural terrain and crosses a small river using a bridge.

They follow it.

Eventually, they reach a village.

The village has another road leading towards a neighbouring settlement. A smaller branch leads to a nearby pillager outpost.

The player continues travelling.

The transportation network continues across newly generated terrain, connecting destinations that may be hundreds or thousands of blocks apart.

The player doesn't need to know how the network was calculated.

They simply experience a world that feels connected.

There should be no obvious artificial transition between the original Minecraft world and the transportation infrastructure generated by our mod.

**The world should feel like it has a history, geography and transportation system of its own.**

---

# 18. Project Development Principles

These principles should guide every implementation decision:

1. Prefer Minecraft's existing world-generation systems over reinventing them.
2. Separate structure discovery, network planning, pathfinding and physical generation.
3. Keep routes deterministic and independent of exploration direction.
4. Build incrementally and validate each milestone.
5. Never sacrifice world stability for visual features.
6. Support vanilla first, then extend compatibility to modded structures.
7. Keep the mod standalone and avoid unnecessary dependencies.
8. Make important features configurable.
9. Preserve the original transportation-network vision throughout development.

## Final Definition

Road Network is a standalone Fabric mod that uses Minecraft's deterministic structure-placement systems to discover potential destinations, constructs a persistent hierarchical network connecting settlements and other structures, and progressively generates natural-looking, biome-aware roads through the world.

Its defining capability is a recursively expanding transportation network whose routes are planned independently of player exploration and whose physical sections are generated as the world develops.

**We are not simply adding roads to Minecraft. We are giving Minecraft worlds a transportation network.**