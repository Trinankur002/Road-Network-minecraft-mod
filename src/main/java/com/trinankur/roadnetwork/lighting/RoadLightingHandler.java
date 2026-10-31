package com.trinankur.roadnetwork.lighting;

import com.trinankur.roadnetwork.generation.RoadStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.ChunkPos;

/**
 * Adds lighting and roadside infrastructure (torches, lanterns, signposts,
 * guardrails) to road segments. Uses the existing RoadStyle decorativeBlocks
 * palette and respects chunk boundaries.
 */
public final class RoadLightingHandler {

	private RoadLightingHandler() {}

	public static int placeLighting(ServerLevel level, LevelChunk chunk,
			BlockPos roadSurfacePos, boolean isMainRoad, RoadStyle style) {
		int placed = 0;
		int y = roadSurfacePos.getY();

		// Avoid placing raw torches on road surface blocks.
		// Handcrafted lampposts with lanterns are placed at roadside intervals in RoadChunkMaterializer.
		if (isMainRoad && (roadSurfacePos.getX() + roadSurfacePos.getZ()) % 40 == 0) {
			BlockPos postPos = roadSurfacePos.above();
			if (level.getBlockState(postPos).isAir()) {
				level.setBlock(postPos, Blocks.LANTERN.defaultBlockState(), 2);
				placed++;
			}
		}

		// Decorative roadside elements from RoadStyle palette (guardrails, signposts)
		if (style != null && !style.decorativeBlocks().isEmpty()) {
			ChunkPos chunkPos = chunk.getPos();
			int minX = chunkPos.getMinBlockX();
			int maxX = chunkPos.getMaxBlockX();
			int minZ = chunkPos.getMinBlockZ();
			int maxZ = chunkPos.getMaxBlockZ();
			for (BlockState decorative : style.decorativeBlocks()) {
				BlockPos decoPos = roadSurfacePos.above();
				int dx = decoPos.getX();
				int dz = decoPos.getZ();
				if (dx < minX || dx > maxX || dz < minZ || dz > maxZ) {
					continue; // chunk boundary isolation
				}
				if (level.getBlockState(decoPos).isAir()) {
					level.setBlock(decoPos, decorative, 2);
					placed++;
				}
			}
		}

		return placed;
	}
}
