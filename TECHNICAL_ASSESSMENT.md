# Road Network — Technical Assessment Report
**Generated:** 2026-10-31

---

## 1. Executive Summary

The Road Network mod is **substantially beyond M0 (Project Foundation)**. The codebase has progressed to approximately **Milestone M4 completion** with significant implementation of the autonomous transportation network system.

**Key Findings:**
- The mod implements deterministic structure discovery, network planning, and progressive road generation
- Architecture is well-designed with clear separation of concerns between discovery, planning, pathfinding, and materialization
- Persistence layer correctly uses Minecraft's SavedData API
- Physical road generation is chunk-boundary-safe and idempotent
- The system handles predicted → confirmed structure state transitions correctly
- All critical safety rules (no overwrites of structures, no water block overwrites, no player builds) are implemented

**Major Gap in Expanded Vision:**
The current implementation generates a simple linear road surface (3 blocks wide) without the infrastructure features described in the expanded infrastructure vision:
- No bridges (wooden, stone, or wider variants)
- No tunnels
- No roadside lighting (torches, lanterns, light posts)
- No signposts, barriers, or decorative elements
- No road hierarchy (different widths for main vs secondary roads)
- Biome adaptation is limited to surface block material selection only

---

## 2. Current Repository and Implementation Status

### 2.1 Project Structure
```
src/main/java/com/trinankur/roadnetwork/
├── RoadNetwork.java                    # Main mod initializer (M0)
├── autonomous/
│   └── AutonomousNetworkManager.java   # Background discovery & planning (M4)
├── command/
│   └── RoadNetworkCommand.java         # Debug/management commands
├── discovery/
│   ├── DiscoveredStructure.java        # Structure records (PREDICTED/CONFIRMED)
│   ├── DiscoveryStatus.java            # Enum: PREDICTED, CONFIRMED
│   ├── StructureClassification.java    # Enum: MAJOR, SECONDARY, IGNORED, UNCLASSIFIED
│   ├── StructureClassifier.java        # Classification via tags/heuristics (M2)
│   └── StructurePlacementScanner.java  # Async structure scanning (M1-M2)
├── generation/
│   ├── RoadChunkMaterializer.java      # Physical block generation (M5)
│   ├── RoadLifecycleHandler.java       # Chunk load event handling (M5)
│   ├── RoadMaterialSelector.java       # Biome-appropriate materials (M5)
│   └── VillagePerimeterCalculator.java # Docking point calculation
├── network/
│   ├── RoadNetworkManager.java         # Planning coordination (M4)
│   ├── graph/
│   │   ├── NetworkEdge.java            # Connection records
│   │   └── NetworkGraph.java           # In-memory graph (M3)
│   ├── node/
│   │   └── NetworkNode.java            # Structure node records (M3)
│   ├── planning/
│   │   └── NetworkPlanner.java         # MST + RNG topology (M3)
│   ├── route/
│   │   ├── NetworkRoute.java           # Route records with waypoints (M4)
│   │   ├── NetworkWaypoint.java        # Waypoint with optional Y
│   │   └── RouteStatus.java            # Enum: PLANNED, SURVEYED, IN_PROGRESS, COMPLETED
│   └── storage/
│       ├── PhysicalRouteData.java      # Persistent path points & materialized chunks (M5)
│       └── RoadNetworkSavedData.java   # Main SavedData implementation (M5)
├── pathfinding/
│   ├── PhysicalPath.java               # Partitioned path for chunk-level generation
│   ├── TerrainHeightSampler.java       # Interface for terrain queries
│   ├── TerrainPathfinder.java          # A*-based terrain-aware pathing (M4)
│   └── WorldGenTerrainSampler.java     # Real-world terrain sampler
├── util/
│   └── DimensionValidator.java         # Overworld-only restriction
└── client/
    └── RoadNetworkClient.java          # Client-side (unused)

src/main/resources/data/roadnetwork/
├── fabric.mod.json                     # Mod metadata (ID: "road-network")
├── tags/worldgen/structure/
│   ├── settlements.json                # #roadnetwork:settlements (villages)
│   ├── secondary.json                  # #roadnetwork:secondary (outposts, temples)
│   └── ignored.json                    # #roadnetwork:ignored (underground/water)
└── road-network.mixins.json            # Client mixin
```

### 2.2 Build Configuration
- **Gradle:** Loom 1.19-SNAPSHOT
- **Minecraft:** 26.1.2 (Java Edition)
- **Fabric Loader:** 0.20.1
- **Fabric API:** 0.155.3+26.1.2
- **Java:** Target/Release 25 (note: SPEC.md says Java, but build.gradle uses Java 25)
- **Mod ID:** `road-network` (specified as `roadnetwork` in documentation)

### 2.3 Test Coverage
Extensive test suite in `NetworkGraphTestRunner.java` covering:
- M1: Structure discovery, classification, deterministic scanning
- M2: Graph operations, node/edge management, serialization
- M3: Terrain-aware pathfinding, physical path generation
- M3.1: Chunk materialization, idempotency, dev-test isolation
- M4: Autonomous route consistency, progressive generation

**Build artifacts exist** (`build/libs/road-network-1.0.0.jar`) indicating successful compilation.

---

## 3. Existing Architecture

### 3.1 Core Architecture Pattern

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Autonomous Network Manager                  │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  Periodic Region Scanning (every 60 ticks per player)      │  │
│  │  ↓                                                          │  │
│  │  StructurePlacementScanner.scanAsync()                      │  │
│  │  ↓                                                          │  │
│  │  processDiscoveryResults()                                  │  │
│  └─────────────────────────────────────────────────────────────┘  │
│                         │                                          │
│                         ▼                                          │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  RoadNetworkSavedData (Persistent Storage)                  │  │
│  │  • ConcurrentHashMap for nodes/edges/routes                 │  │
│  │  • SavedData for world persistence                          │  │
│  └───────────┬─────────────────────────────────────────────────┘  │
│              │                                                  │
│              ▼                                                  │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  NetworkPlanner (MST + RNG)                                 │  │
│  │  • Kruskal's algorithm for Minimum Spanning Forest          │  │
│  │  • Relative Neighborhood Graph for regional connections     │  │
│  │  • Route protection (protected routes not reconciled)       │  │
│  └───────────┬─────────────────────────────────────────────────┘  │
│              │                                                  │
│              ▼                                                  │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  TerrainPathfinder (A*)                                     │  │
│  │  • 4-block coarse grid for performance                      │  │
│  │  • Slope penalties for terrain cost                         │  │
│  │  • Water crossing penalties (encourages bridges later)      │  │
│  └───────────┬─────────────────────────────────────────────────┘  │
│              │                                                  │
│              ▼                                                  │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  PhysicalRouteData → PhysicalPath                           │  │
│  │  • List<BlockPos> path points                               │  │
│  │  • Chunk-based partitioning                                 │  │
│  └───────────┬─────────────────────────────────────────────────┘  │
│              │                                                  │
│              ▼                                                  │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  RoadLifecycleHandler (CHUNK_LOAD event)                    │  │
│  │  • Validates predicted → confirmed structures               │  │
│  │  • Materializes road segments per chunk                     │  │
│  └───────────┬─────────────────────────────────────────────────┘  │
│              │                                                  │
│              ▼                                                  │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  RoadChunkMaterializer                                      │  │
│  │  • MotionBlocking_NO_LEAVES heightmap for surface Y       │  │
│  │  • 3-block width (center + 1 left + 1 right)              │  │
│  │  • Biome-appropriate block selection via RoadMaterialSelector│
│  │  • 2-block overhead clearance                             │  │
│  │  • Wooden plank water crossings                           │  │
│  └─────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

### 3.2 Key Architectural Decisions

1. **Two-phase Structure Discovery** (`StructurePlacementScanner`):
   - Phase 1: Background thread computes candidates using seed math and noise
   - Phase 2: Main thread verifies via chunk cache and structure manager
   - This avoids force-loading distant chunks

2. **Predicted → Confirmed Transition** (`NetworkNode`):
   - Nodes start as PREDICTED with unknown Y
   - When chunk loads with StructureStart, upgrades to CONFIRMED
   - Physical construction blocked until both confirmed AND known Y

3. **Deterministic Route ID Generation** (`NetworkRoute.createRouteId`):
   - Lexicographically sorts endpoint IDs
   - Ensures same route ID regardless of traversal direction

4. **Chunk-Boundary-Aware Generation** (`PhysicalPath`):
   - Path points partitioned by ChunkPos
   - Materialization only in currently loaded chunks
   - Tracks materialized chunks to prevent duplicate writes

5. **Route Protection** (`NetworkPlanner`):
   - SURVEYED, IN_PROGRESS, COMPLETED routes marked as protected
   - Protected routes never removed during reconciliation
   - Allows progression from PLANNED → SURVEYED → IN_PROGRESS → COMPLETED

6. **Biome-Aware Material Selection** (`RoadMaterialSelector`):
   - Desert: SmootSandstone, Sandstone, Gravel
   - Snowy: Gravel, Cobblestone, Stone
   - Forest/Taiga: DirtPath, CoarseDirt, Gravel
   - Default (Plains/Temperate): DirtPath 60%, Gravel 25%, CoarseDirt 15%

---

## 4. Original Specification Compliance

### 4.1 M0 — Project Foundation [✓ COMPLETE]
- Fabric project setup with Loom
- Successful build
- Main mod initializer (`RoadNetwork.java`)
- Basic logging infrastructure
- Command registration framework

### 4.2 M1 — Structure Discovery [✓ COMPLETE]
- `StructureRegistryInspector.inspectStructureSets()` - enumerates StructureSets
- `StructureRegistryInspector.inspectStructures()` - enumerates Structures
- `StructurePlacementScanner` - inspects placement implementations
- Predictive candidate location via `RandomSpreadStructurePlacement.getPotentialStructureChunk()`
- Separates PREDICTED vs CONFIRMED via `StructureCheckResult`
- `/roadnetwork scan` command implemented

### 4.3 M2 — Structure Classification [✓ COMPLETE]
- `StructureClassification` enum: MAJOR, SECONDARY, IGNORED, UNCLASSIFIED
- `StructureClassifier.classify()` with tag-based and heuristic classification
- Data-driven tags: `#roadnetwork:settlements`, `#roadnetwork:secondary`, `#roadnetwork:ignored`
- `/roadnetwork inspect` command for registry inspection

### 4.4 M3 — Road Network Planning [✓ COMPLETE]
- `NetworkGraph` and `NetworkPlanner` for graph operations
- Minimum Spanning Forest + Relative Neighborhood Graph algorithm
- Distance-based connection pruning
- Duplicate connection prevention
- Existing network reuse
- Route protection mechanism

### 4.5 M4 — Terrain and Pathfinding [✓ COMPLETE]
- `TerrainHeightSampler` interface for terrain queries
- `WorldGenTerrainSampler` for real world access
- `TerrainPathfinder` - A* with:
  - 4-block coarse grid
  - Elevation costs (slope penalties)
  - Water crossing penalties
  - Turn/curvature penalties
- `/roadnetwork plan` command

### 4.6 M5 — Physical Road Generation [✓ COMPLETE]
- `RoadChunkMaterializer.materializeChunk()`
- `ChunkPos`-based partitioning
- 3-block road width
- Biome-appropriate materials via `RoadMaterialSelector`
- `MotionBlocking_NO_LEAVES` heightmap for surface Y
- Chunk boundary safety checks
- Player/build protection (replaces only natural terrain)
- Overhead foliage clearing

### 4.7 M6 — Recursive Network Expansion [✓ PARTIAL]
- `AutonomousNetworkManager` implements recursive expansion
- Scans region when player enters uncovered area (512-block region)
- When new settlement discovered, re-plans network
- **Missing:** Explicit secondary-to-secondary connections (currently only MAJOR↔MAJOR)

### 4.8 M7 — Advanced Terrain Features [✗ NOT STARTED]
- Bridges: Not implemented
- Tunnels: Not implemented
- Retaining walls: Not implemented
- Slopes: Road is flat at surface level
- Vegetation handling: Only clears overhead foliage

### 4.9 M8 — Persistence and Existing World Support [✓ PARTIAL]
- `RoadNetworkSavedData` - full persistence via SavedData API
- Discovers predicted structures without generating chunks
- **Missing:** Retroactive generation for existing chunks
- **Missing:** World migration/versioning beyond VERSION 1

### 4.10 M9 — Compatibility and Optimization [✗ NOT STARTED]
- Modded structure support limited to tag-based classification
- No datapack integration hooks
- TODO: Performance testing, dedicated server testing

### 4.11 M10 — Release Preparation [✗ NOT STARTED]
- Documentation: In progress (SPEC.md exists)
- Modrinth release preparation: Not done

---

## 5. Expanded Infrastructure Requirements Coverage

| Requirement | Status | Notes |
|-------------|--------|-------|
| **Dirt/Gravel roads** | ✓ | Default temperate palette |
| **Stone/Cobblestone roads** | ✓ | Snowy palette includes cobblestone/stone |
| **Wider main roads** | ✗ | Fixed 3-block width; no concept of main vs secondary |
| **Concrete roads with streetlights** | ✗ | No concrete blocks; no lighting |
| **Stone bridges with railings** | ✗ | Bridge system absent |
| **Wooden bridges** | ✗ | Bridge system absent |
| **Mountain tunnels** | ✗ | Tunnel system absent |
| **Roadside torches/lanterns** | ✗ | No lighting generation |
| **Signposts/barriers** | ✗ | No decorative elements |
| **Biome-adaptive materials** | ✓ Partial | Surface materials adapt; no decorative/street furniture adaptation |
| **Road importance styles** | ✗ | No route hierarchy system |

---

## 6. Architectural Compatibility Assessment

### 6.1 Strengths - Compatible with Expanded Vision

| Component | Why It Fits |
|-----------|-------------|
| **Deterministic route calculation** | Routes stored as control points; can extend with bridge/tunnel metadata |
| **Chunk-layered generation** | Architecture naturally supports adding infrastructure layers |
| **Route protection states** | Can add BRIDGING, TUNNELING, LIGHTING states |
| **Waypoint system** | Extensible to include infrastructure types |
| **PhysicalRouteData** | Can track bridge spans, tunnel sections, lighting posts |
| **VillagePerimeterCalculator** | Dock points can include bridge/tunnel approaches |

### 6.2 Gaps Requiring Architectural Changes

#### 6.2.1 Bridge System
**Current:** None
**Required:**
- Bridge path segment type (discrete from road segment)
- Support calculation (slope limits, water width limits)
- Generation order: foundation → supports → deck → railings
- Material adaptation (wooden for small streams, stone for larger)

**Files to modify:**
- `NetworkRoute` - add BridgeSpan waypoint type
- `PhysicalPath` - bridge-specific partitioning
- `RoadChunkMaterializer` - bridge block placement
- `VillagePerimeterCalculator` - bridge approach ramps

#### 6.2.2 Tunnel System
**Current:** None
**Required:**
- Underground passage detection in pathfinder
- Entrance generation (ladder/stair shafts)
- Lighting throughout tunnel
- Ground-level road connections

**Files to modify:**
- `TerrainPathfinder` - underground path option
- New `TunnelGenerator` service
- `RoadChunkMaterializer` - tunnel block placement

#### 6.2.3 Road Hierarchy/Widtness
**Current:** Fixed 3-block width
**Required:**
- Route class metadata (MAIN, SECONDARY, TRAIL)
- Width configuration parameters
- Width-adaptive material selection
- Landscape integration (wider bridges for main roads)

**Files to modify:**
- `NetworkRoute` - add route class
- `RoadChunkMaterializer` - parameterized width
- `NetworkPlanner` - assign route class based on structure type

#### 6.2.4 Roadside Features (Lighting, Signage)
**Current:** None
**Required:**
- Decorative element scheduler
- Placement rules (spacing, biome-appropriate)
- Block type catalog (torches, lanterns, signs, fences)
- Idempotency (don't overwrite on re-materialization)

**Files to modify:**
- `RoadChunkMaterializer` - extension point for features
- New `RoadsideFeatureGenerator` service

#### 6.2.5 Elevation Profiles
**Current:** Flat at surface height
**Required:**
- Slope grade calculation in pathfinder
- Stair/ladder placement logic
- Retaining wall calculation for cut sections
- Winding route behavior

**Files to modify:**
- `TerrainPathfinder` - elevation interpolation
- New road surface generator (beyond flat placement)

---

## 7. Technical Risks and Potential Bottlenecks

### 7.1 Performance Risks

| Risk | Severity | Mitigation Status |
|------|----------|-------------------|
| **Large terrain queries** | Medium | Pathfinder uses 4-block coarse grid, 30k max expansions |
| **Concurrent chunk access** | Low | Uses getChunkNow() and only processes loaded chunks |
| **Route re-computation** | Low | Planner snapshots data, processes off-thread |
| **Waypoint interpolation** | Low | Linear interpolation in NetworkPlanner |
| **Physical path generation** | Medium | Interpolates 1-block steps from coarse waypoints |

### 7.2 Data Safety Risks

| Risk | Severity | Mitigation Status |
|------|----------|-------------------|
| **Overwriting player builds** | Low | `isReplaceableGround()` blocks non-natural blocks |
| **Duplicating roads on restart** | Low | `materializedChunks` set tracks written chunks |
| **Orphaned roads to predicted** | Low | Construction blocked until CONFIRMED with Y |
| **Force-loading distant chunks** | Low | Uses worldgen state, not chunk loading |
| **Destruction of structures** | Low | VillagePerimeterCalculator docks at perimeter |

### 7.3 Architectural Bottlenecks

1. **Route Width Coupling:** Width is hardcoded in `RoadChunkMaterializer.offsets` array
   - **Impact:** Adding variable widths requires refactoring
   - **Recommendation:** Extract to `RoadConfiguration` with per-route-class widths

2. **Material Selection Simplicity:** Flat biome → block selection
   - **Impact:** Cannot express "primary road surface" vs "decorative elements"
   - **Recommendation:** `RoadMaterialSelector.select(blockType, biome, width, importance)`

3. **Infrastructure Ordering:** No generation phases
   - **Impact:** Bridges can't be built before road connects
   - **Recommendation:** Add RouteStatus: PLANNED → BRIDGING → IN_GROUND → SURFACE → COMPLETE

4. **Elevation Handling:** Intermediate waypoints have unknown Y
   - **Impact:** Cannot generate accurate bridges/tunnels
   - **Recommendation:** Pathfinder should provide full 3D coordinates

---

## 8. Minecraft 26.1.2 and Fabric API Compatibility Concerns

### 8.1 API Usage Analysis

| API | Version | Status |
|-----|---------|--------|
| `ServerLevel` | 26.1.2 | ✓ Correct |
| `Heightmap.Types.MOTION_BLOCKING_NO_LEAVES` | 26.1.2 | ✓ Correct |
| `StructurePlacement` classes | 26.1.2 | ✓ Correct |
| `RandomSpreadStructurePlacement` | 26.1.2 | ✓ Correct |
| `SavedData` | 26.1.2 | ✓ Correct |
| `BlockPos.CODEC` | 26.1.2 | ✓ Correct |
| `ChunkPos.CODEC` | 26.1.2 | ✓ Correct |

### 8.2 Version Mismatch Findings

1. **Java Version:**
   - `build.gradle`: `sourceCompatibility = JavaVersion.VERSION_25`
   - `gradle.properties`: No explicit Java version constraint
   - **Issue:** Minecraft 26.1.2 may not require Java 25
   - **Action:** Verify minimum Java version

2. **Loom Version:**
   - `gradle.properties`: `loom_version=1.19-SNAPSHOT`
   - This is a snapshot version; ensure stable 1.19.x is available

3. **Fabric API:**
   - `fabric_api_version=0.155.3+26.1.2` - ✓ Matches Minecraft version

4. **Mod ID Inconsistency:**
   - Documentation: `modId: roadnetwork`
   - Code: `MOD_ID = "road-network"`
   - **Action:** Standardize; prefer snake_case without hyphen: `roadnetwork`

### 8.3 Deprecated API Usage

No deprecated APIs identified in current codebase.

---

## 9. Missing Components and Dependencies

### 9.1 Core Missing Features (from expanded vision)

1. **Bridge Generation Pipeline**
   - Bridge span calculation
   - Material selection for supports/railings
   - Approach ramp generation
   - Integration with terrain-aware pathfinding

2. **Tunnel Generation Pipeline**
   - Underground detection in terrain sampling
   - Entrance/exit generation
   - Interior lighting
   - Ground-level road connections

3. **Roadside Feature System**
   - Lighting placement (torches, lanterns, posts)
   - Signpost generation
   - Barriers/fencing
   - Benches/rest areas
   - Stone markers

4. **Road Layering System**
   - Base layer (current implementation)
   - Decoration layer (torches, signs)
   - Structural layer (bridges, tunnels)
   - Weathering layer (optional)

5. **Route Hierarchy System**
   - MAIN_ROAD class (5-7 blocks)
   - SECONDARY_ROAD class (3-5 blocks)
   - VILLAGE_ROAD class (3 blocks)
   - TRAIL class (2 blocks)

6. **Biome Material Database**
   - Current: Hardcoded in `RoadMaterialSelector`
   - Needed: Configurable via datapack/JSON

### 9.2 Dependencies Not Currently Used

- **No external libraries** beyond Fabric API
- **No datapack integration** for structure tags (hardcoded in code)

---

## 10. Recommended Development Sequence

### Phase 1: Foundation Enhancements (1-2 weeks)
1. **Standardize Mod ID** to `roadnetwork` (remove hyphen)
2. **Extract road configuration** to data-driven JSON
3. **Add route class metadata** to `NetworkRoute`
4. **Parameterize road width** in `RoadChunkMaterializer`

### Phase 2: Bridge System (2-3 weeks)
1. Add `BridgeSpan` waypoint type
2. Modify `TerrainPathfinder` for bridge detection
3. Create `BridgeGenerator` service
4. Extend `RoadChunkMaterializer` for bridge blocks
5. Add bridge materials to `RoadMaterialSelector`

### Phase 3: Tunnel System (2 weeks)
1. Add underground path option in pathfinder
2. Create `TunnelGenerator` service
3. Implement tunnel entrances/exits
4. Add tunnel lighting

### Phase 4: Roadside Features (1-2 weeks)
1. Create `RoadsideFeatureGenerator`
2. Define feature catalog (torches, signs, fences)
3. Add to materialization pipeline
4. Make feature placement deterministic

### Phase 5: Terrain Adaptation (2 weeks)
1. Implement road surface grading (slopes)
2. Add retaining wall calculation
3. Winding route behavior for mountains
4. Vegetation handling refinement

### Phase 6: Datapack Integration (1 week)
1. Convert structure tags to datapack
2. Make biome palettes configurable
3. Add structure classification datapack support

### Phase 7: Performance & Polish (1 week)
1. Dedicated server testing
2. Memory profiling
3. Documentation completion
4. Release preparation

---

## 11. The Next Concrete Implementation Task

**Task:** Standardize mod ID and extract road configuration to a data-driven system.

**Rationale:** Before adding bridges/tunnels, we need flexible configuration for:
- Road widths (main vs secondary vs trail)
- Material palettes per biome
- Infrastructure feature thresholds
- Connection distance limits

**Files to Create:**
- `src/main/resources/data/roadnetwork/road_config.json` - JSON config

**Files to Modify:**
- [RoadNetwork.java](src/main/java/com/trinankur/roadnetwork/RoadNetwork.java) - Update MOD_ID
- [NetworkRoute.java](src/main/java/com/trinankur/roadnetwork/network/route/NetworkRoute.java) - Add route class
- [RoadChunkMaterializer.java](src/main/java/com/trinankur/roadnetwork/generation/RoadChunkMaterializer.java) - Accept width parameter
- [RoadMaterialSelector.java](src/main/java/com/trinankur/roadnetwork/generation/RoadMaterialSelector.java) - Make palette configurable

**Estimated Effort:** 2-3 days

---

## 12. Questions Requiring Project-Owner Decisions

### 12.1 Mod ID Standardization
**Current:** `road-network` (with hyphen)
**Options:**
1. Keep `road-network` (works, but hyphens are unconventional in mod IDs)
2. Change to `roadnetwork` (consistent with documentation, cleaner)

**Recommendation:** Use `roadnetwork` for consistency with documentation

### 12.2 Bridge Implementation Approach
**Options:**
1. **Pathfinder-detected bridges:** TerrainPathfinder detects water obstacles > threshold, generates bridge span
2. **Post-processing bridges:** Route planner identifies water crossings, adds BridgeSpan waypoints
3. **Manual bridge endpoints:** Players place bridge start/end, mod connects them

**Recommendation:** Option 2 for deterministic, seed-based generation

### 12.3 Bridge Width vs Road Width
**Question:** Should bridges be wider than the road they carry?
- **Option A:** Bridge width = maximum road width (supports widest road)
- **Option B:** Bridge width = road width + railings (minimal)
- **Option C:** Bridges always 5 blocks (dedicated bridge width)

### 12.4 Tunnel Entrance Style
**Options:**
1. Simple shaft with ladder
2. Cut-and-cover approach
3. Natural cave entrance style
4. Datapack-configurable

### 12.5 Roadside Feature Density
**Question:** How many features per km of road?
- Light: 1 lantern/50 blocks
- Moderate: 1 feature/25 blocks
- Heavy: 1 feature/16 blocks

### 12.6 Configuration Persistence
**Question:** Should road configuration be:
1. Hardcoded constants (simple, fast)
2. JSON file in config/ (user-editable)
3. Datapack in data/roadnetwork/ (modpack-friendly)
4. In-game config screen (requires extra UI work)

### 12.7 Road Surface Grading
**Question:** For slopes > 10%, should we:
1. Build around (detour)
2. Build stairs/terraces
3. Build cut-through (retaining walls)

### 12.8 Water Crossing Limits
**Current:** RoadMaterialSelector uses wooden planks for water
**Question:** What's the maximum water width for bridge generation?
- Small stream: < 8 blocks wide
- Medium river: < 32 blocks wide
- Large river: Terminate at coast

---

## 13. Summary

The Road Network mod is at a sophisticated implementation level (M4 complete) with a solid architectural foundation. The core systems for discovery, planning, and deterministic generation are well-executed and thoroughly tested.

The primary gap between current implementation and the expanded vision is in **infrastructure diversity**: the system generates a single road surface type without bridges, tunnels, or roadside features. These are significant features requiring careful integration with the existing chunk-based, deterministic generation model.

**Recommendation:** Proceed with Phase 1 enhancements (configuration extraction) before tackling infrastructure features. This will provide the flexibility needed for bridges of varying widths and road types.