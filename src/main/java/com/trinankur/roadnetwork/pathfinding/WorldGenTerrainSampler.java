package com.trinankur.roadnetwork.pathfinding;

import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Worldgen terrain sampler that queries Minecraft's noise router directly without loading chunks.
 */
public class WorldGenTerrainSampler implements TerrainHeightSampler {

	private final ChunkGenerator generator;
	private final LevelHeightAccessor heightAccessor;
	private final RandomState randomState;

	private final java.util.Map<Long, Integer> surfaceCache = new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.Map<Long, Integer> oceanFloorCache = new java.util.concurrent.ConcurrentHashMap<>();

	private static long packCoord(int x, int z) {
		return (((long) x) << 32) ^ (((long) z) & 0xFFFFFFFFL);
	}

	public WorldGenTerrainSampler(ChunkGenerator generator, LevelHeightAccessor heightAccessor, RandomState randomState) {
		this.generator = generator;
		this.heightAccessor = heightAccessor;
		this.randomState = randomState;
	}

	public WorldGenTerrainSampler(net.minecraft.server.level.ServerLevel level) {
		this(level.getChunkSource().getGenerator(), level, level.getChunkSource().randomState());
	}

	@Override
	public int getSurfaceHeight(int x, int z) {
		long key = packCoord(x, z);
		Integer cached = this.surfaceCache.get(key);
		if (cached != null) {
			return cached;
		}
		int height = this.generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, this.heightAccessor, this.randomState);
		this.surfaceCache.put(key, height);
		return height;
	}

	@Override
	public int getOceanFloorHeight(int x, int z) {
		long key = packCoord(x, z);
		Integer cached = this.oceanFloorCache.get(key);
		if (cached != null) {
			return cached;
		}
		int height = this.generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, this.heightAccessor, this.randomState);
		this.oceanFloorCache.put(key, height);
		return height;
	}
}
