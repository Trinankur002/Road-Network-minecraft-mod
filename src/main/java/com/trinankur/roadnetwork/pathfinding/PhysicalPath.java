package com.trinankur.roadnetwork.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents the computed 3D physical road trajectory.
 * Partitions continuous block points by ChunkPos to enable chunk-isolated road materialization.
 */
public class PhysicalPath {

	private final List<BlockPos> points;
	private final Map<ChunkPos, List<BlockPos>> pointsByChunk;

	public PhysicalPath(List<BlockPos> points) {
		this.points = List.copyOf(points);
		Map<ChunkPos, List<BlockPos>> byChunk = new HashMap<>();

		for (BlockPos p : this.points) {
			ChunkPos cp = new ChunkPos(
				SectionPos.blockToSectionCoord(p.getX()),
				SectionPos.blockToSectionCoord(p.getZ())
			);
			byChunk.computeIfAbsent(cp, k -> new ArrayList<>()).add(p);
		}

		this.pointsByChunk = Collections.unmodifiableMap(byChunk);
	}

	public List<BlockPos> getPoints() {
		return this.points;
	}

	public List<BlockPos> getPointsForChunk(ChunkPos chunkPos) {
		return this.pointsByChunk.getOrDefault(chunkPos, Collections.emptyList());
	}

	/**
	 * Returns all path points within the chunk or within a margin of blocks from the chunk boundary.
	 * This guarantees that road width offsets extending across chunk boundaries are never clipped.
	 */
	public List<BlockPos> getPointsNearChunk(ChunkPos chunkPos, int marginBlocks) {
		List<BlockPos> result = new ArrayList<>();
		int minX = chunkPos.getMinBlockX() - marginBlocks;
		int maxX = chunkPos.getMaxBlockX() + marginBlocks;
		int minZ = chunkPos.getMinBlockZ() - marginBlocks;
		int maxZ = chunkPos.getMaxBlockZ() + marginBlocks;

		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				ChunkPos neighbor = new ChunkPos(chunkPos.x() + dx, chunkPos.z() + dz);
				List<BlockPos> chunkPoints = this.pointsByChunk.get(neighbor);
				if (chunkPoints != null) {
					for (BlockPos p : chunkPoints) {
						if (p.getX() >= minX && p.getX() <= maxX && p.getZ() >= minZ && p.getZ() <= maxZ) {
							result.add(p);
						}
					}
				}
			}
		}
		return result;
	}

	public Set<ChunkPos> getIntersectingChunks() {
		return this.pointsByChunk.keySet();
	}

	public boolean intersectsChunk(ChunkPos chunkPos) {
		return this.pointsByChunk.containsKey(chunkPos);
	}

	public int size() {
		return this.points.size();
	}

	public BlockPos getStartPoint() {
		return this.points.isEmpty() ? null : this.points.get(0);
	}

	public BlockPos getEndPoint() {
		return this.points.isEmpty() ? null : this.points.get(this.points.size() - 1);
	}
}

