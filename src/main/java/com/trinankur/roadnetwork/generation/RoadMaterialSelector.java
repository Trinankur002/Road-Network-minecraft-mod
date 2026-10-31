package com.trinankur.roadnetwork.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Handcrafted biome-adaptive material palettes and texture selector.
 * Adapts roads, bridges, tunnels, and roadside street furniture to every biome,
 * creating natural gradients, worn wagon paths, and architectural elegance.
 */
public final class RoadMaterialSelector {

	public record BiomeRoadPalette(
		BlockState centerBlock,
		BlockState midBlock,
		BlockState edgeBlock,
		BlockState slabBlock,
		BlockState stairBlock,
		BlockState bridgeDeck,
		BlockState bridgeCurb,
		BlockState bridgeFence,
		BlockState bridgeGate,
		BlockState bridgePillarLog,
		BlockState bridgePillarFooting,
		BlockState lamppostWall,
		BlockState lamppostFence,
		BlockState lamppostTrapdoor,
		BlockState roadsideFoliage
	) {}

	private static final BlockState PERSISTENT_OAK_LEAVES = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
	private static final BlockState PERSISTENT_SPRUCE_LEAVES = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
	private static final BlockState PERSISTENT_DARK_OAK_LEAVES = Blocks.DARK_OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
	private static final BlockState PERSISTENT_JUNGLE_LEAVES = Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
	private static final BlockState PERSISTENT_MANGROVE_LEAVES = Blocks.MANGROVE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
	private static final BlockState PERSISTENT_AZALEA_LEAVES = Blocks.AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
	private static final BlockState PERSISTENT_CHERRY_LEAVES = Blocks.CHERRY_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
	private static final BlockState PERSISTENT_ACACIA_LEAVES = Blocks.ACACIA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);

	// 1. Temperate Palette (Plains, Meadow, Sunflower Plains, Rivers, Forests)
	private static final BiomeRoadPalette TEMPERATE_PALETTE = new BiomeRoadPalette(
		Blocks.DIRT_PATH.defaultBlockState(),
		Blocks.PACKED_MUD.defaultBlockState(),
		Blocks.COARSE_DIRT.defaultBlockState(),
		Blocks.OAK_SLAB.defaultBlockState(),
		Blocks.OAK_STAIRS.defaultBlockState(),
		Blocks.OAK_PLANKS.defaultBlockState(),
		Blocks.STONE_BRICK_SLAB.defaultBlockState(),
		Blocks.OAK_FENCE.defaultBlockState(),
		Blocks.OAK_FENCE_GATE.defaultBlockState(),
		Blocks.STRIPPED_OAK_LOG.defaultBlockState(),
		Blocks.STONE_BRICKS.defaultBlockState(),
		Blocks.COBBLESTONE_WALL.defaultBlockState(),
		Blocks.OAK_FENCE.defaultBlockState(),
		Blocks.OAK_TRAPDOOR.defaultBlockState(),
		PERSISTENT_OAK_LEAVES
	);

	// 2. Taiga & Cold Palette (Taiga, Old Growth Pine/Spruce, Snowy Plains, Snowy Slopes, Grove)
	private static final BiomeRoadPalette TAIGA_COLD_PALETTE = new BiomeRoadPalette(
		Blocks.PODZOL.defaultBlockState(),
		Blocks.COARSE_DIRT.defaultBlockState(),
		Blocks.GRAVEL.defaultBlockState(),
		Blocks.SPRUCE_SLAB.defaultBlockState(),
		Blocks.SPRUCE_STAIRS.defaultBlockState(),
		Blocks.SPRUCE_PLANKS.defaultBlockState(),
		Blocks.COBBLESTONE_SLAB.defaultBlockState(),
		Blocks.SPRUCE_FENCE.defaultBlockState(),
		Blocks.SPRUCE_FENCE_GATE.defaultBlockState(),
		Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(),
		Blocks.COBBLESTONE.defaultBlockState(),
		Blocks.COBBLESTONE_WALL.defaultBlockState(),
		Blocks.SPRUCE_FENCE.defaultBlockState(),
		Blocks.SPRUCE_TRAPDOOR.defaultBlockState(),
		PERSISTENT_SPRUCE_LEAVES
	);

	// 3. Arid & Desert Palette (Desert, Badlands, Wooded Badlands, Eroded Badlands, Savanna)
	private static final BiomeRoadPalette ARID_DESERT_PALETTE = new BiomeRoadPalette(
		Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
		Blocks.SANDSTONE.defaultBlockState(),
		Blocks.TERRACOTTA.defaultBlockState(),
		Blocks.SMOOTH_SANDSTONE_SLAB.defaultBlockState(),
		Blocks.SMOOTH_SANDSTONE_STAIRS.defaultBlockState(),
		Blocks.BIRCH_PLANKS.defaultBlockState(),
		Blocks.CUT_SANDSTONE_SLAB.defaultBlockState(),
		Blocks.BIRCH_FENCE.defaultBlockState(),
		Blocks.BIRCH_FENCE_GATE.defaultBlockState(),
		Blocks.STRIPPED_ACACIA_LOG.defaultBlockState(),
		Blocks.SANDSTONE.defaultBlockState(),
		Blocks.SANDSTONE_WALL.defaultBlockState(),
		Blocks.ACACIA_FENCE.defaultBlockState(),
		Blocks.ACACIA_TRAPDOOR.defaultBlockState(),
		Blocks.DEAD_BUSH.defaultBlockState()
	);

	// 4. Jungle Palette (Jungle, Bamboo Jungle, Sparse Jungle)
	private static final BiomeRoadPalette JUNGLE_PALETTE = new BiomeRoadPalette(
		Blocks.BAMBOO_PLANKS.defaultBlockState(),
		Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
		Blocks.ROOTED_DIRT.defaultBlockState(),
		Blocks.BAMBOO_SLAB.defaultBlockState(),
		Blocks.BAMBOO_STAIRS.defaultBlockState(),
		Blocks.BAMBOO_PLANKS.defaultBlockState(),
		Blocks.MOSSY_STONE_BRICK_SLAB.defaultBlockState(),
		Blocks.BAMBOO_FENCE.defaultBlockState(),
		Blocks.BAMBOO_FENCE_GATE.defaultBlockState(),
		Blocks.STRIPPED_JUNGLE_LOG.defaultBlockState(),
		Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
		Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState(),
		Blocks.BAMBOO_FENCE.defaultBlockState(),
		Blocks.BAMBOO_TRAPDOOR.defaultBlockState(),
		PERSISTENT_JUNGLE_LEAVES
	);

	// 5. Swamp Palette (Swamp, Mangrove Swamp)
	private static final BiomeRoadPalette SWAMP_PALETTE = new BiomeRoadPalette(
		Blocks.MUD_BRICKS.defaultBlockState(),
		Blocks.PACKED_MUD.defaultBlockState(),
		Blocks.MUDDY_MANGROVE_ROOTS.defaultBlockState(),
		Blocks.MUD_BRICK_SLAB.defaultBlockState(),
		Blocks.MUD_BRICK_STAIRS.defaultBlockState(),
		Blocks.MANGROVE_PLANKS.defaultBlockState(),
		Blocks.MUD_BRICK_SLAB.defaultBlockState(),
		Blocks.MANGROVE_FENCE.defaultBlockState(),
		Blocks.MANGROVE_FENCE_GATE.defaultBlockState(),
		Blocks.STRIPPED_MANGROVE_LOG.defaultBlockState(),
		Blocks.MUD_BRICKS.defaultBlockState(),
		Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState(),
		Blocks.MANGROVE_FENCE.defaultBlockState(),
		Blocks.MANGROVE_TRAPDOOR.defaultBlockState(),
		PERSISTENT_MANGROVE_LEAVES
	);

	// 6. Mountain Palette (Jagged Peaks, Stony Peaks, Frozen Peaks, Windswept Hills)
	private static final BiomeRoadPalette MOUNTAIN_PALETTE = new BiomeRoadPalette(
		Blocks.STONE_BRICKS.defaultBlockState(),
		Blocks.ANDESITE.defaultBlockState(),
		Blocks.COBBLESTONE.defaultBlockState(),
		Blocks.STONE_BRICK_SLAB.defaultBlockState(),
		Blocks.STONE_BRICK_STAIRS.defaultBlockState(),
		Blocks.SPRUCE_PLANKS.defaultBlockState(),
		Blocks.STONE_BRICK_SLAB.defaultBlockState(),
		Blocks.STONE_BRICK_WALL.defaultBlockState(),
		Blocks.SPRUCE_FENCE_GATE.defaultBlockState(),
		Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(),
		Blocks.STONE_BRICKS.defaultBlockState(),
		Blocks.STONE_BRICK_WALL.defaultBlockState(),
		Blocks.SPRUCE_FENCE.defaultBlockState(),
		Blocks.SPRUCE_TRAPDOOR.defaultBlockState(),
		PERSISTENT_AZALEA_LEAVES
	);

	// 7. Dark Forest Palette (Dark Forest, Old Growth Birch)
	private static final BiomeRoadPalette DARK_FOREST_PALETTE = new BiomeRoadPalette(
		Blocks.DIRT_PATH.defaultBlockState(),
		Blocks.PODZOL.defaultBlockState(),
		Blocks.ROOTED_DIRT.defaultBlockState(),
		Blocks.DARK_OAK_SLAB.defaultBlockState(),
		Blocks.DARK_OAK_STAIRS.defaultBlockState(),
		Blocks.DARK_OAK_PLANKS.defaultBlockState(),
		Blocks.COBBLESTONE_SLAB.defaultBlockState(),
		Blocks.DARK_OAK_FENCE.defaultBlockState(),
		Blocks.DARK_OAK_FENCE_GATE.defaultBlockState(),
		Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState(),
		Blocks.MOSSY_COBBLESTONE.defaultBlockState(),
		Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState(),
		Blocks.DARK_OAK_FENCE.defaultBlockState(),
		Blocks.DARK_OAK_TRAPDOOR.defaultBlockState(),
		PERSISTENT_DARK_OAK_LEAVES
	);

	// 8. Savanna Palette (Savanna, Savanna Plateau, Windswept Savanna)
	private static final BiomeRoadPalette SAVANNA_PALETTE = new BiomeRoadPalette(
		Blocks.DIRT_PATH.defaultBlockState(),
		Blocks.PACKED_MUD.defaultBlockState(),
		Blocks.COARSE_DIRT.defaultBlockState(),
		Blocks.ACACIA_SLAB.defaultBlockState(),
		Blocks.ACACIA_STAIRS.defaultBlockState(),
		Blocks.ACACIA_PLANKS.defaultBlockState(),
		Blocks.STONE_BRICK_SLAB.defaultBlockState(),
		Blocks.ACACIA_FENCE.defaultBlockState(),
		Blocks.ACACIA_FENCE_GATE.defaultBlockState(),
		Blocks.STRIPPED_ACACIA_LOG.defaultBlockState(),
		Blocks.STONE_BRICKS.defaultBlockState(),
		Blocks.STONE_BRICK_WALL.defaultBlockState(),
		Blocks.ACACIA_FENCE.defaultBlockState(),
		Blocks.ACACIA_TRAPDOOR.defaultBlockState(),
		PERSISTENT_ACACIA_LEAVES
	);

	// 9. Cherry Grove Palette (Cherry Grove)
	private static final BiomeRoadPalette CHERRY_GROVE_PALETTE = new BiomeRoadPalette(
		Blocks.DIRT_PATH.defaultBlockState(),
		Blocks.PACKED_MUD.defaultBlockState(),
		Blocks.CHERRY_PLANKS.defaultBlockState(),
		Blocks.CHERRY_SLAB.defaultBlockState(),
		Blocks.CHERRY_STAIRS.defaultBlockState(),
		Blocks.CHERRY_PLANKS.defaultBlockState(),
		Blocks.STONE_BRICK_SLAB.defaultBlockState(),
		Blocks.CHERRY_FENCE.defaultBlockState(),
		Blocks.CHERRY_FENCE_GATE.defaultBlockState(),
		Blocks.STRIPPED_CHERRY_LOG.defaultBlockState(),
		Blocks.STONE_BRICKS.defaultBlockState(),
		Blocks.COBBLESTONE_WALL.defaultBlockState(),
		Blocks.CHERRY_FENCE.defaultBlockState(),
		Blocks.CHERRY_TRAPDOOR.defaultBlockState(),
		PERSISTENT_CHERRY_LEAVES
	);

	private RoadMaterialSelector() {
	}

	/**
	 * Retrieves the handcrafted biome palette for the given world coordinate.
	 */
	public static BiomeRoadPalette getPalette(LevelAccessor level, BlockPos pos) {
		if (level == null || pos == null) {
			return TEMPERATE_PALETTE;
		}

		Holder<Biome> biomeHolder = level.getBiome(pos);

		if (biomeHolder.is(Biomes.SAVANNA)
			|| biomeHolder.is(Biomes.SAVANNA_PLATEAU)
			|| biomeHolder.is(Biomes.WINDSWEPT_SAVANNA)) {
			return SAVANNA_PALETTE;
		}

		if (biomeHolder.is(Biomes.CHERRY_GROVE)) {
			return CHERRY_GROVE_PALETTE;
		}

		if (biomeHolder.is(Biomes.DESERT)
			|| biomeHolder.is(Biomes.BADLANDS)
			|| biomeHolder.is(Biomes.ERODED_BADLANDS)
			|| biomeHolder.is(Biomes.WOODED_BADLANDS)) {
			return ARID_DESERT_PALETTE;
		}

		if (biomeHolder.is(Biomes.JUNGLE)
			|| biomeHolder.is(Biomes.SPARSE_JUNGLE)
			|| biomeHolder.is(Biomes.BAMBOO_JUNGLE)) {
			return JUNGLE_PALETTE;
		}

		if (biomeHolder.is(Biomes.SWAMP) || biomeHolder.is(Biomes.MANGROVE_SWAMP)) {
			return SWAMP_PALETTE;
		}

		if (biomeHolder.is(Biomes.DARK_FOREST)) {
			return DARK_FOREST_PALETTE;
		}

		if (biomeHolder.is(Biomes.JAGGED_PEAKS)
			|| biomeHolder.is(Biomes.STONY_PEAKS)
			|| biomeHolder.is(Biomes.FROZEN_PEAKS)
			|| biomeHolder.is(Biomes.WINDSWEPT_HILLS)
			|| biomeHolder.is(Biomes.WINDSWEPT_GRAVELLY_HILLS)) {
			return MOUNTAIN_PALETTE;
		}

		if (biomeHolder.is(Biomes.SNOWY_PLAINS)
			|| biomeHolder.is(Biomes.SNOWY_TAIGA)
			|| biomeHolder.is(Biomes.SNOWY_SLOPES)
			|| biomeHolder.is(Biomes.GROVE)
			|| biomeHolder.is(Biomes.TAIGA)
			|| biomeHolder.is(Biomes.OLD_GROWTH_PINE_TAIGA)
			|| biomeHolder.is(Biomes.OLD_GROWTH_SPRUCE_TAIGA)) {
			return TAIGA_COLD_PALETTE;
		}

		return TEMPERATE_PALETTE;
	}

	/**
	 * Selects an artistic, natural surface block incorporating lateral distance gradients, road class, and spatial noise.
	 */
	public static BlockState selectSurfaceBlock(
		LevelAccessor level,
		BlockPos pos,
		boolean isWaterCrossing,
		boolean isEdge,
		int lateralDist,
		RoadStyle.RoadClass roadClass
	) {
		BiomeRoadPalette palette = getPalette(level, pos);

		if (isWaterCrossing) {
			if (isEdge) {
				return palette.bridgeCurb();
			}
			return palette.bridgeDeck();
		}

		int hash = getPosHash(pos.getX(), pos.getZ());
		int variant = Math.floorMod(hash, 100);

		// Grand Paved Highways (MAIN Road Class): Master masonry texture blending
		if (roadClass == RoadStyle.RoadClass.MAIN || roadClass == RoadStyle.RoadClass.MODERN) {
			if (palette == ARID_DESERT_PALETTE) {
				if (lateralDist == 0) {
					return variant < 60 ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : Blocks.CUT_SANDSTONE.defaultBlockState();
				} else if (lateralDist == 1) {
					return variant < 50 ? Blocks.CUT_SANDSTONE.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState();
				} else {
					return variant < 55 ? Blocks.TERRACOTTA.defaultBlockState() : Blocks.CUT_SANDSTONE_SLAB.defaultBlockState();
				}
			} else if (palette == TAIGA_COLD_PALETTE) {
				if (lateralDist == 0) {
					return variant < 50 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : (variant < 80 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
				} else if (lateralDist == 1) {
					return variant < 60 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
				} else {
					return variant < 50 ? Blocks.COBBLESTONE_SLAB.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
				}
			} else if (palette == JUNGLE_PALETTE) {
				if (lateralDist == 0) {
					return variant < 55 ? Blocks.BAMBOO_PLANKS.defaultBlockState() : Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
				} else if (lateralDist == 1) {
					return variant < 50 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.ROOTED_DIRT.defaultBlockState();
				} else {
					return variant < 60 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.BAMBOO_SLAB.defaultBlockState();
				}
			} else if (palette == MOUNTAIN_PALETTE) {
				if (lateralDist == 0) {
					return variant < 60 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
				} else if (lateralDist == 1) {
					return variant < 60 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
				} else {
					return variant < 50 ? Blocks.STONE_BRICK_SLAB.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
				}
			} else {
				// Temperate / Forest / Savanna / Swamp Paved Highway
				if (lateralDist == 0) {
					// Paved crown: 55% Stone Bricks, 25% Andesite, 20% Cracked Stone Bricks
					return variant < 55 ? Blocks.STONE_BRICKS.defaultBlockState()
						: (variant < 80 ? Blocks.ANDESITE.defaultBlockState() : Blocks.CRACKED_STONE_BRICKS.defaultBlockState());
				} else if (lateralDist == 1) {
					// Paved mid: 50% Cobblestone, 30% Andesite, 20% Stone Bricks
					return variant < 50 ? Blocks.COBBLESTONE.defaultBlockState()
						: (variant < 80 ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
				} else {
					// Paved edge: 50% Cobblestone, 30% Stone Brick Slab, 20% Mossy Cobblestone
					return variant < 50 ? Blocks.COBBLESTONE.defaultBlockState()
						: (variant < 80 ? Blocks.STONE_BRICK_SLAB.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState());
				}
			}
		}

		// Village Roads, Trails, and Secondary Roads: Biome-native wear patterns
		if (lateralDist == 0) {
			// Center worn spine: 78% center, 22% mid
			return variant < 78 ? palette.centerBlock() : palette.midBlock();
		} else if (lateralDist == 1) {
			// Intermediate wear lane: 45% center, 40% mid, 15% edge
			if (variant < 45) {
				return palette.centerBlock();
			} else if (variant < 85) {
				return palette.midBlock();
			} else {
				return palette.edgeBlock();
			}
		} else {
			// Outer frayed border: 60% edge, 25% mid, 15% center
			if (variant < 60) {
				return palette.edgeBlock();
			} else if (variant < 85) {
				return palette.midBlock();
			} else {
				return palette.centerBlock();
			}
		}
	}

	/**
	 * Backward compatibility selector.
	 */
	public static BlockState selectSurfaceBlock(LevelAccessor level, BlockPos pos, boolean isWaterCrossing, boolean isEdge, int lateralDist) {
		return selectSurfaceBlock(level, pos, isWaterCrossing, isEdge, lateralDist, RoadStyle.RoadClass.VILLAGE_ROAD);
	}

	/**
	 * Backward compatibility selector.
	 */
	public static BlockState selectSurfaceBlock(LevelAccessor level, BlockPos pos, boolean isWaterCrossing) {
		return selectSurfaceBlock(level, pos, isWaterCrossing, false, 0);
	}

	/**
	 * Deterministic spatial hash function.
	 */
	public static int getPosHash(int x, int z) {
		int h = x * 374761393 + z * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		return h ^ (h >>> 16);
	}
}
