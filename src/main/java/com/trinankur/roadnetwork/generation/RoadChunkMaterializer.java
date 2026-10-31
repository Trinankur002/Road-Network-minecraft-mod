package com.trinankur.roadnetwork.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Safely materializes physical road blocks in a loaded chunk with master-builder aesthetics.
 *
 * Handcrafted architectural features:
 * 1. Biome-adaptive texture gradients (worn center spine, transition mid-lane, rough feathered border).
 * 2. Arched river bridges with stone curbs, timber log trestles, submerged masonry footings, and decorative fence/gate balustrades.
 * 3. Roman barrel-vault mountain tunnels with structural arch ribs, suspended iron chains, and hanging lanterns.
 * 4. Roadside street furniture (cobblestone-wall lampposts with trapdoor caps, chiseled milestone guideposts, and wildflower/shrub borders).
 * 5. Strict safety clipping to loaded chunk boundaries with no cascading neighbor updates.
 */
public final class RoadChunkMaterializer {

    // UPDATE_CLIENTS (2) notifies tracking players so the road is visible immediately.
    // UPDATE_KNOWN_SHAPE (16) bypasses neighbor shape recalculation.
    // DOES NOT include UPDATE_NEIGHBORS (1), which prevents cascading chunk loading across borders during onChunkLoad.
    private static final int ROAD_BLOCK_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private RoadChunkMaterializer() {
    }

    /**
     * Materializes the road for all points belonging to the given chunk.
     * Uses RoadStyle for width, materials, and edge treatment.
     * Returns the number of road blocks placed or updated.
     */
    public static int materializeChunk(ServerLevel level, LevelChunk chunk, List<BlockPos> pointsInChunk, RoadStyle style) {
        if (pointsInChunk == null || pointsInChunk.isEmpty() || style == null) {
            return 0;
        }

        ChunkPos chunkPos = chunk.getPos();
        int minX = chunkPos.getMinBlockX();
        int maxX = chunkPos.getMaxBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int maxZ = chunkPos.getMaxBlockZ();

        Set<Long> processedColumns = new HashSet<>();
        int blocksPlaced = 0;

        int width = style.getTotalWidth();
        int surfaceWidth = style.getSurfaceWidth();
        boolean hasEdges = style.edgeBlock().isPresent();

        for (int i = 0; i < pointsInChunk.size(); i++) {
            BlockPos current = pointsInChunk.get(i);

            // Determine lateral vector based on direction to adjacent points with true vector normalization
            int dx = 0;
            int dz = 0;
            int deltaY = 0;
            if (i + 1 < pointsInChunk.size()) {
                dx = pointsInChunk.get(i + 1).getX() - current.getX();
                dz = pointsInChunk.get(i + 1).getZ() - current.getZ();
                deltaY = pointsInChunk.get(i + 1).getY() - current.getY();
            } else if (i > 0) {
                dx = current.getX() - pointsInChunk.get(i - 1).getX();
                dz = current.getZ() - pointsInChunk.get(i - 1).getZ();
                deltaY = current.getY() - pointsInChunk.get(i - 1).getY();
            }

            double len = Math.hypot(dx, dz);
            double nx = 0.0;
            double nz = 1.0;
            if (len > 1e-4) {
                nx = -dz / len;
                nz = dx / len;
            }

            int halfWidth = width / 2;
            int[][] offsets = new int[width][2];
            for (int w = 0; w < width; w++) {
                int dist = w - halfWidth;
                offsets[w][0] = (int) Math.round(dist * nx);
                offsets[w][1] = (int) Math.round(dist * nz);
            }

            // Clear side verges (width + 1 block on each side) to keep clear line of sight through trees and foliage
            int vergeDist = halfWidth + 1;
            int leftVergeX = current.getX() + (int) Math.round(-vergeDist * nx);
            int leftVergeZ = current.getZ() + (int) Math.round(-vergeDist * nz);
            int rightVergeX = current.getX() + (int) Math.round(vergeDist * nx);
            int rightVergeZ = current.getZ() + (int) Math.round(vergeDist * nz);
            clearVerge(level, chunk, leftVergeX, leftVergeZ);
            clearVerge(level, chunk, rightVergeX, rightVergeZ);

            boolean isPier = (i % 6 == 0);

            for (int[] offset : offsets) {
                int targetX = current.getX() + offset[0];
                int targetZ = current.getZ() + offset[1];

                // Strict chunk boundary clipping
                if (targetX < minX || targetX > maxX || targetZ < minZ || targetZ > maxZ) {
                    continue;
                }

                long colKey = BlockPos.asLong(targetX, 0, targetZ);
                if (!processedColumns.add(colKey)) {
                    continue;
                }

                int lateralDist = Math.max(Math.abs(offset[0]), Math.abs(offset[1]));
                boolean isEdge = (lateralDist >= (width / 2))
                    || (offset[0] == offsets[0][0] && offset[1] == offsets[0][1])
                    || (offset[0] == offsets[offsets.length - 1][0] && offset[1] == offsets[offsets.length - 1][1]);

                if (materializeColumn(level, chunk, targetX, targetZ, current.getY(), deltaY, style, isEdge, lateralDist, isPier)) {
                    blocksPlaced++;
                }
            }
        }

        return blocksPlaced;
    }

    /**
     * Backward compatibility - defaults to 3-block natural trail.
     */
    public static int materializeChunk(ServerLevel level, LevelChunk chunk, List<BlockPos> pointsInChunk) {
        RoadStyle defaultStyle = RoadStyle.selectStyle(0, 0, RoadStyle.RoadClass.VILLAGE_ROAD, RoadStyle.StyleProfile.NATURAL);
        return materializeChunk(level, chunk, pointsInChunk, defaultStyle);
    }

    private static boolean materializeColumn(
        ServerLevel level,
        LevelChunk chunk,
        int x,
        int z,
        int targetY,
        RoadStyle style,
        boolean isEdge,
        int lateralDist
    ) {
        return materializeColumn(level, chunk, x, z, targetY, 0, style, isEdge, lateralDist, false);
    }

    private static boolean materializeColumn(
        ServerLevel level,
        LevelChunk chunk,
        int x,
        int z,
        int targetY,
        int deltaY,
        RoadStyle style,
        boolean isEdge,
        int lateralDist
    ) {
        return materializeColumn(level, chunk, x, z, targetY, deltaY, style, isEdge, lateralDist, false);
    }

    private static boolean materializeColumn(
        ServerLevel level,
        LevelChunk chunk,
        int x,
        int z,
        int targetY,
        int deltaY,
        RoadStyle style,
        boolean isEdge,
        int lateralDist,
        boolean isPier
    ) {
        int motionBlockingY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (motionBlockingY < chunk.getMinY() || motionBlockingY > chunk.getMaxY()) {
            return false;
        }

        // Trace down through air and foliage to locate the actual solid ground or water surface
        int groundY = motionBlockingY;
        while (groundY > chunk.getMinY()) {
            BlockState st = chunk.getBlockState(new BlockPos(x, groundY, z));
            if (st.isAir() || isFoliage(st)) {
                groundY--;
            } else {
                break;
            }
        }
        if (groundY < chunk.getMinY()) {
            return false;
        }

        BlockPos groundPos = new BlockPos(x, groundY, z);
        BlockState groundState = chunk.getBlockState(groundPos);

        // Water detection
        boolean isWater = groundState.is(Blocks.WATER) || groundState.is(Blocks.BUBBLE_COLUMN);
        int waterTopY = groundY;
        if (isWater) {
            while (waterTopY + 1 <= chunk.getMaxY() && chunk.getBlockState(new BlockPos(x, waterTopY + 1, z)).is(Blocks.WATER)) {
                waterTopY++;
            }
        }

        // Village protection: Never destroy buildings, walls, or workstations
        if (isProtectedVillageBlock(groundState)) {
            return false;
        }

        // Mountain Tunnel Mode: triggers when solid natural terrain towers >= 6 blocks over the road path
        boolean isTunnel = (!isWater && groundY >= targetY + 6);

        // Bridge Mode: over river or ocean water, or spanning a deep ravine/canyon
        boolean isBridge = isWater || (!isTunnel && groundY <= targetY - 5);

        int roadY;
        if (isTunnel) {
            roadY = targetY;
        } else if (isBridge) {
            // Elevated bridge deck: always elevated at least 2 blocks above water level or at target grade
            roadY = isWater ? Math.max(waterTopY + 2, targetY) : targetY;
        } else {
            // Overland road: always flush with the natural ground surface!
            roadY = groundY;
        }

        if (roadY < chunk.getMinY() || roadY > chunk.getMaxY()) {
            return false;
        }

        BlockPos roadPos = new BlockPos(x, roadY, z);
        BlockState currentAtRoad = chunk.getBlockState(roadPos);
        if (isProtectedVillageBlock(currentAtRoad)) {
            return false;
        }

        RoadStyle.RoadClass roadClass = (style != null ? style.roadClass() : RoadStyle.RoadClass.VILLAGE_ROAD);
        RoadMaterialSelector.BiomeRoadPalette palette = RoadMaterialSelector.getPalette(level, roadPos);
        int posHash = RoadMaterialSelector.getPosHash(x, z);

        BlockState roadState;

        if (isTunnel) {
            // Mountain Tunnel Roadway: Cobblestone, Stone Bricks, Andesite, Gravel
            int variant = Math.floorMod(posHash, 100);
            if (variant < 45) {
                roadState = Blocks.COBBLESTONE.defaultBlockState();
            } else if (variant < 75) {
                roadState = Blocks.STONE_BRICKS.defaultBlockState();
            } else if (variant < 88) {
                roadState = Blocks.ANDESITE.defaultBlockState();
            } else {
                roadState = Blocks.GRAVEL.defaultBlockState();
            }
        } else if (isBridge) {
            roadState = isEdge ? palette.bridgeCurb() : palette.bridgeDeck();
        } else {
            roadState = RoadMaterialSelector.selectSurfaceBlock(level, roadPos, false, isEdge, lateralDist, roadClass);
        }

        // --- Slab Elevation Step Transition (Smooth Half-Block Ramps) across full surface width ---
        boolean isSlabStep = (!isTunnel && !isBridge && deltaY != 0);

        if (isSlabStep && deltaY > 0) {
            // Stepping up: Solid base at roadPos, bottom slab above at roadPos.above()
            safeSetBlock(level, chunk, roadPos, palette.midBlock());
            safeSetBlock(level, chunk, roadPos.above(), palette.slabBlock());
        } else if (isSlabStep && deltaY < 0) {
            // Stepping down: Bottom slab at roadPos, solid base underneath
            safeSetBlock(level, chunk, roadPos, palette.slabBlock());
            if (roadPos.getY() > chunk.getMinY()) {
                safeSetBlock(level, chunk, roadPos.below(), palette.midBlock());
            }
        } else {
            safeSetBlock(level, chunk, roadPos, roadState);
        }

        if (isTunnel) {
            // --- Handcrafted Roman Barrel Vault Arch Mountain Tunnel ---
            // Clear 4 blocks of vertical clearance for players and horses
            for (int h = 1; h <= 4; h++) {
                BlockPos clearPos = roadPos.above(h);
                if (clearPos.getY() <= chunk.getMaxY()) {
                    clearOverhead(level, chunk, clearPos);
                }
            }

            boolean isRib = Math.floorMod(x + z, 5) == 0;

            if (isEdge) {
                // Ribbed Arch ceiling corner (at h = 4)
                BlockPos archPos = roadPos.above(4);
                if (archPos.getY() <= chunk.getMaxY()) {
                    BlockState archState = isRib
                        ? Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.HALF, Half.TOP)
                        : getTunnelWallState(posHash ^ 0x54321);
                    safeSetBlock(level, chunk, archPos, archState);
                }

                // Tunnel walls along edge (h = 1..3)
                for (int h = 1; h <= 3; h++) {
                    BlockPos wallPos = roadPos.above(h);
                    if (wallPos.getY() <= chunk.getMaxY()) {
                        BlockState ws = chunk.getBlockState(wallPos);
                        if (isReplaceableGround(ws) || ws.isAir()) {
                            BlockState ribWall = isRib ? Blocks.STONE_BRICKS.defaultBlockState() : getTunnelWallState(posHash ^ (h * 997));
                            safeSetBlock(level, chunk, wallPos, ribWall);
                        }
                    }
                }
            } else {
                // Center ceiling vault (at h = 5)
                BlockPos ceilPos = roadPos.above(5);
                if (ceilPos.getY() <= chunk.getMaxY()) {
                    BlockState cs = isRib ? Blocks.STONE_BRICKS.defaultBlockState() : getTunnelWallState(posHash ^ 0x12345);
                    safeSetBlock(level, chunk, ceilPos, cs);
                }

                // Suspended Vault Lighting: Iron Chain + Hanging Lantern
                if (isRib && lateralDist == 0 && roadPos.getY() + 4 <= chunk.getMaxY()) {
                    BlockPos chainPos = roadPos.above(4);
                    BlockPos lanternPos = roadPos.above(3);
                    safeSetBlock(level, chunk, chainPos, Blocks.IRON_CHAIN.defaultBlockState());
                    safeSetBlock(
                        level,
                        chunk,
                        lanternPos,
                        Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true)
                    );
                }
            }
        } else if (isBridge) {
            // --- Handcrafted Arched River / Waterway Bridge ---
            for (int h = 1; h <= 3; h++) {
                BlockPos clr = roadPos.above(h);
                if (!isEdge) {
                    clearOverhead(level, chunk, clr);
                }
            }

            if (isEdge && roadPos.getY() + 1 <= chunk.getMaxY()) {
                BlockPos railPos = roadPos.above();
                if (isPier) {
                    // Bridge Pier Post with elevated lantern
                    safeSetBlock(level, chunk, railPos, palette.bridgeFence());
                    BlockPos upperFence = roadPos.above(2);
                    BlockPos lanternPos = roadPos.above(3);
                    if (lanternPos.getY() <= chunk.getMaxY()) {
                        safeSetBlock(level, chunk, upperFence, palette.bridgeFence());
                        safeSetBlock(level, chunk, lanternPos, Blocks.LANTERN.defaultBlockState());
                    }
                } else {
                    // Alternating decorative balustrade: Fence and Fence Gate
                    BlockState railing = (Math.floorMod(x * 7 + z * 11, 2) == 0)
                        ? palette.bridgeFence()
                        : palette.bridgeGate();
                    safeSetBlock(level, chunk, railPos, railing);
                }
            }

            // Bridge Deck Underside Trim
            if (isEdge && roadPos.getY() > chunk.getMinY()) {
                BlockPos underPos = roadPos.below();
                BlockState underState = chunk.getBlockState(underPos);
                if (underState.isAir() || underState.is(Blocks.WATER)) {
                    safeSetBlock(level, chunk, underPos, palette.bridgeCurb());
                }
            }

            // Submerged / Elevated Support Piers
            if (isEdge && isPier && roadPos.getY() > chunk.getMinY()) {
                int py = roadPos.getY() - 1;
                int maxPillarDepth = 30;
                while (py >= chunk.getMinY() && (roadPos.getY() - py) <= maxPillarDepth) {
                    BlockPos pilPos = new BlockPos(x, py, z);
                    BlockState pilState = chunk.getBlockState(pilPos);
                    if (pilState.is(Blocks.WATER) || pilState.is(Blocks.BUBBLE_COLUMN)) {
                        // Submerged footing: stone bricks / cobblestone masonry
                        safeSetBlock(level, chunk, pilPos, palette.bridgePillarFooting());
                        py--;
                    } else if (pilState.isAir() || isFoliage(pilState)) {
                        // Above water / valley trestle: stripped logs
                        safeSetBlock(level, chunk, pilPos, palette.bridgePillarLog());
                        py--;
                    } else {
                        break;
                    }
                }
            }
        } else {
            // --- Overland Road: Smooth terrain conformity ---
            // 1. Overhead Clearance: Clear 4 blocks of height above the road
            for (int cy = roadPos.getY() + (isSlabStep && deltaY > 0 ? 2 : 1); cy <= Math.min(chunk.getMaxY(), roadPos.getY() + 4); cy++) {
                BlockPos clrPos = new BlockPos(x, cy, z);
                clearOverhead(level, chunk, clrPos);
            }

            // 2. Foundation: Ensure solid foundation block underneath road surface
            if (roadPos.getY() > chunk.getMinY()) {
                BlockPos basePos = roadPos.below();
                BlockState baseState = chunk.getBlockState(basePos);
                if (baseState.isAir() || isFoliage(baseState) || isReplaceableGround(baseState)) {
                    safeSetBlock(level, chunk, basePos, palette.midBlock());
                }
            }

            // 3. Roadside Detailing & Street Furniture:
            if (isEdge) {
                // A. Handcrafted Roadside Lamppost (every 36 blocks)
                if (Math.floorMod(x * 13 + z * 19, 36) == 0 && roadPos.getY() + 4 <= chunk.getMaxY()) {
                    BlockPos wallPos = roadPos.above(1);
                    BlockPos postPos = roadPos.above(2);
                    BlockPos armPos = roadPos.above(3);
                    BlockPos lanternPos = roadPos.above(4);
                    if (chunk.getBlockState(wallPos).isAir() && chunk.getBlockState(lanternPos).isAir()) {
                        safeSetBlock(level, chunk, wallPos, palette.lamppostWall());
                        safeSetBlock(level, chunk, postPos, palette.lamppostFence());
                        safeSetBlock(level, chunk, armPos, palette.lamppostFence());
                        safeSetBlock(level, chunk, lanternPos, Blocks.LANTERN.defaultBlockState());
                    }
                }
                // B. Roadside Milestone Guidepost (every 50 blocks)
                else if (Math.floorMod(x * 17 + z * 23, 50) == 0 && roadPos.getY() + 3 <= chunk.getMaxY()) {
                    BlockPos milePos = roadPos.above(1);
                    BlockPos fencePos = roadPos.above(2);
                    BlockPos lightPos = roadPos.above(3);
                    if (chunk.getBlockState(milePos).isAir() && chunk.getBlockState(lightPos).isAir()) {
                        safeSetBlock(level, chunk, milePos, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
                        safeSetBlock(level, chunk, fencePos, palette.lamppostFence());
                        safeSetBlock(level, chunk, lightPos, Blocks.LANTERN.defaultBlockState());
                    }
                }
                // C. Natural Roadside Shrubs & Wildflowers (every 16 blocks)
                else if (Math.floorMod(posHash, 16) == 0 && roadPos.getY() + 1 <= chunk.getMaxY()) {
                    BlockPos decoPos = roadPos.above(1);
                    if (chunk.getBlockState(decoPos).isAir()) {
                        safeSetBlock(level, chunk, decoPos, palette.roadsideFoliage());
                    }
                }
            }
        }

        return true;
    }

    private static void safeSetBlock(ServerLevel level, LevelChunk chunk, BlockPos pos, BlockState state) {
        if (pos.getY() < chunk.getMinY() || pos.getY() > chunk.getMaxY()) {
            return;
        }
        BlockState current = chunk.getBlockState(pos);
        if (isProtectedVillageBlock(current)) {
            return; // Never overwrite village buildings, workstations, or furniture
        }
        if (!current.equals(state)) {
            level.setBlock(pos, state, ROAD_BLOCK_FLAGS);
        }
    }

    /**
     * Determines whether a block is part of an existing village structure or building that should be preserved.
     */
    public static boolean isProtectedVillageBlock(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        return state.is(BlockTags.BEDS)
            || state.is(BlockTags.DOORS)
            || state.is(BlockTags.TRAPDOORS)
            || state.is(BlockTags.PLANKS)
            || state.is(BlockTags.WOODEN_STAIRS)
            || state.is(BlockTags.WOODEN_SLABS)
            || state.is(BlockTags.WOODEN_FENCES)
            || state.is(BlockTags.FENCE_GATES)
            || state.is(BlockTags.PRESSURE_PLATES)
            || state.is(BlockTags.CROPS)
            || state.is(Blocks.FARMLAND)
            || state.is(Blocks.BELL)
            || state.is(Blocks.CHEST)
            || state.is(Blocks.BARREL)
            || state.is(Blocks.COMPOSTER)
            || state.is(Blocks.BLAST_FURNACE)
            || state.is(Blocks.SMOKER)
            || state.is(Blocks.CARTOGRAPHY_TABLE)
            || state.is(Blocks.LECTERN)
            || state.is(Blocks.SMITHING_TABLE)
            || state.is(Blocks.FLETCHING_TABLE)
            || state.is(Blocks.GRINDSTONE)
            || state.is(Blocks.BREWING_STAND)
            || state.is(Blocks.STONECUTTER)
            || state.is(Blocks.LOOM)
            || state.is(Blocks.CAULDRON)
            || state.is(Blocks.WATER_CAULDRON)
            || state.is(Blocks.LAVA_CAULDRON)
            || state.is(Blocks.HAY_BLOCK)
            || state.is(Blocks.TORCH)
            || state.is(Blocks.WALL_TORCH)
            || state.is(Blocks.LANTERN)
            || state.is(Blocks.GLASS)
            || state.is(Blocks.GLASS_PANE)
            || state.is(Blocks.COBBLESTONE_WALL)
            || state.is(Blocks.MOSSY_COBBLESTONE_WALL)
            || state.is(Blocks.TERRACOTTA)
            || state.is(Blocks.WHITE_TERRACOTTA)
            || state.is(Blocks.YELLOW_TERRACOTTA)
            || state.is(Blocks.ORANGE_TERRACOTTA);
    }

    private static BlockState getTunnelWallState(int seed) {
        int v = Math.floorMod(seed, 100);
        if (v < 40) {
            return Blocks.STONE_BRICKS.defaultBlockState();
        } else if (v < 65) {
            return Blocks.COBBLESTONE.defaultBlockState();
        } else if (v < 80) {
            return Blocks.ANDESITE.defaultBlockState();
        } else if (v < 92) {
            return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        } else {
            return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        }
    }

    /**
     * Determines whether a surface ground block is a natural terrain block that may be converted to a road.
     */
    public static boolean isReplaceableGround(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }

        // Allowed natural terrain blocks
        if (state.is(Blocks.DIRT)
            || state.is(Blocks.GRASS_BLOCK)
            || state.is(Blocks.COARSE_DIRT)
            || state.is(Blocks.PODZOL)
            || state.is(Blocks.ROOTED_DIRT)
            || state.is(Blocks.SAND)
            || state.is(Blocks.RED_SAND)
            || state.is(BlockTags.DIRT)
            || state.is(BlockTags.SAND)
            || state.is(Blocks.GRAVEL)
            || state.is(Blocks.STONE)
            || state.is(Blocks.GRANITE)
            || state.is(Blocks.DIORITE)
            || state.is(Blocks.ANDESITE)
            || state.is(Blocks.TUFF)
            || state.is(Blocks.DEEPSLATE)
            || state.is(Blocks.SANDSTONE)
            || state.is(Blocks.SMOOTH_SANDSTONE)
            || state.is(Blocks.RED_SANDSTONE)
            || state.is(Blocks.SMOOTH_RED_SANDSTONE)
            || state.is(Blocks.CLAY)
            || state.is(Blocks.MUD)
            || state.is(Blocks.PACKED_MUD)
            || state.is(Blocks.MUDDY_MANGROVE_ROOTS)
            || state.is(Blocks.SNOW_BLOCK)
            || state.is(Blocks.POWDER_SNOW)
            || state.is(Blocks.MOSS_BLOCK)
            || state.is(Blocks.DIRT_PATH)) {
            return true;
        }

        return false;
    }

    /**
     * Determines whether a block state represents ephemeral foliage or ground cover.
     */
    public static boolean isFoliage(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        return state.is(BlockTags.SMALL_FLOWERS)
            || state.is(BlockTags.FLOWER_POTS)
            || state.is(BlockTags.LEAVES)
            || state.is(BlockTags.SAPLINGS)
            || state.is(Blocks.SHORT_GRASS)
            || state.is(Blocks.TALL_GRASS)
            || state.is(Blocks.FERN)
            || state.is(Blocks.LARGE_FERN)
            || state.is(Blocks.DEAD_BUSH)
            || state.is(Blocks.SNOW)
            || state.is(Blocks.VINE);
    }

    /**
     * Clears obstruction overhead (foliage, leaves, dirt cuts, intersecting tree logs)
     * to ensure an open, walkable roadway clearance envelope.
     * When intersecting a tree trunk, removes the full tree trunk and canopy upwards to prevent floating trees.
     */
    private static void clearOverhead(ServerLevel level, LevelChunk chunk, BlockPos pos) {
        if (pos.getY() > chunk.getMaxY() || pos.getY() < chunk.getMinY()) {
            return;
        }

        BlockState state = chunk.getBlockState(pos);
        if (state.isAir() || isProtectedVillageBlock(state)) {
            return;
        }

        if (state.is(BlockTags.LOGS)) {
            removeTreeTrunkAndCanopy(level, chunk, pos.getX(), pos.getY(), pos.getZ());
            return;
        }

        if (isFoliage(state) || isReplaceableGround(state) || state.is(BlockTags.LEAVES)) {
            safeSetBlock(level, chunk, pos, Blocks.AIR.defaultBlockState());
        }
    }

    /**
     * Traces a cut tree trunk upwards to the sky, clearing connected logs and surrounding leaves
     * so that no floating stumps, severed logs, or detached leaf clouds remain suspended in mid-air.
     * Supports 2x2 large tree trunks (Dark Oak, Giant Spruce, Jungle).
     */
    private static void removeTreeTrunkAndCanopy(ServerLevel level, LevelChunk chunk, int x, int startY, int z) {
        int maxCheckY = Math.min(chunk.getMaxY(), startY + 32);
        int consecutiveAir = 0;

        for (int y = startY; y <= maxCheckY; y++) {
            boolean foundWoodOrLeaf = false;

            // Check 3x3 footprint around (x, z) to capture 2x2 large tree trunks
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int tx = x + dx;
                    int tz = z + dz;
                    if (tx >= chunk.getPos().getMinBlockX() && tx <= chunk.getPos().getMaxBlockX()
                        && tz >= chunk.getPos().getMinBlockZ() && tz <= chunk.getPos().getMaxBlockZ()) {
                        BlockPos tpos = new BlockPos(tx, y, tz);
                        BlockState tst = chunk.getBlockState(tpos);
                        if (tst.is(BlockTags.LOGS) || tst.is(BlockTags.LEAVES)) {
                            safeSetBlock(level, chunk, tpos, Blocks.AIR.defaultBlockState());
                            foundWoodOrLeaf = true;
                        }
                    }
                }
            }

            if (foundWoodOrLeaf) {
                consecutiveAir = 0;
                // Clear foliage canopy slice up to 3 blocks radius
                for (int ox = -3; ox <= 3; ox++) {
                    for (int oz = -3; oz <= 3; oz++) {
                        if (Math.abs(ox) <= 1 && Math.abs(oz) <= 1) continue;
                        int lx = x + ox;
                        int lz = z + oz;
                        if (lx >= chunk.getPos().getMinBlockX() && lx <= chunk.getPos().getMaxBlockX()
                            && lz >= chunk.getPos().getMinBlockZ() && lz <= chunk.getPos().getMaxBlockZ()) {
                            BlockPos leafPos = new BlockPos(lx, y, lz);
                            BlockState leafState = chunk.getBlockState(leafPos);
                            if (leafState.is(BlockTags.LEAVES)) {
                                safeSetBlock(level, chunk, leafPos, Blocks.AIR.defaultBlockState());
                            }
                        }
                    }
                }
            } else {
                consecutiveAir++;
                if (consecutiveAir >= 4) {
                    break;
                }
            }
        }
    }

    /**
     * Clears side verge corridors (1 block outside the road on each side) of overhanging leaves and branches
     * up to 4 blocks above the ground so players have unobstructed visibility and horse headroom.
     */
    private static void clearVerge(ServerLevel level, LevelChunk chunk, int x, int z) {
        if (x < chunk.getPos().getMinBlockX() || x > chunk.getPos().getMaxBlockX()
            || z < chunk.getPos().getMinBlockZ() || z > chunk.getPos().getMaxBlockZ()) {
            return;
        }

        int motionBlockingY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int groundY = motionBlockingY;
        while (groundY > chunk.getMinY()) {
            BlockState st = chunk.getBlockState(new BlockPos(x, groundY, z));
            if (st.isAir() || isFoliage(st)) {
                groundY--;
            } else {
                break;
            }
        }

        for (int cy = groundY + 1; cy <= Math.min(chunk.getMaxY(), groundY + 4); cy++) {
            BlockPos vp = new BlockPos(x, cy, z);
            BlockState vs = chunk.getBlockState(vp);
            if (vs.isAir() || isProtectedVillageBlock(vs)) {
                continue;
            }
            if (vs.is(BlockTags.LOGS)) {
                removeTreeTrunkAndCanopy(level, chunk, x, cy, z);
            } else if (isFoliage(vs) || vs.is(BlockTags.LEAVES)) {
                safeSetBlock(level, chunk, vp, Blocks.AIR.defaultBlockState());
            }
        }
    }

    /**
     * Clears ephemeral overhead vegetation for road travel clearance.
     * Never clears solid building materials or valuable blocks.
     */
    private static void clearOverheadFoliage(ServerLevel level, LevelChunk chunk, BlockPos pos) {
        clearOverhead(level, chunk, pos);
    }
}
