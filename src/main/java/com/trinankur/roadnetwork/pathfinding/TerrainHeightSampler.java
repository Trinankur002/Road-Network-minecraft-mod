package com.trinankur.roadnetwork.pathfinding;

/**
 * Interface providing terrain surface and floor elevation queries.
 * Decouples pathfinding from live world instances for pure, lightning-fast unit testing.
 */
public interface TerrainHeightSampler {

	/**
	 * Returns the surface elevation (including water surface at sea level).
	 */
	int getSurfaceHeight(int x, int z);

	/**
	 * Returns the solid ocean/river floor elevation.
	 */
	int getOceanFloorHeight(int x, int z);

	/**
	 * Determines whether the block column at (x, z) is a body of water.
	 * If the solid floor is lower than the surface, the column contains a water body.
	 */
	default boolean isWater(int x, int z) {
		int floor = getOceanFloorHeight(x, z);
		int surface = getSurfaceHeight(x, z);
		return floor < surface;
	}
}
