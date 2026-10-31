package com.trinankur.roadnetwork.network.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Persisted physical road geometry and chunk-materialization progress for a route.
 * Tracks which chunks have physically received road blocks to guarantee idempotent, restart-safe generation.
 */
public class PhysicalRouteData {

	public static final Codec<PhysicalRouteData> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
			Codec.STRING.fieldOf("route_id").forGetter(PhysicalRouteData::getRouteId),
			Codec.list(BlockPos.CODEC).fieldOf("path_points").forGetter(PhysicalRouteData::getPathPoints),
			Codec.list(ChunkPos.CODEC).fieldOf("materialized_chunks").forGetter(PhysicalRouteData::getMaterializedChunks)
		).apply(instance, PhysicalRouteData::new)
	);

	private final String routeId;
	private final List<BlockPos> pathPoints;
	private final Set<ChunkPos> materializedChunks;

	public PhysicalRouteData(String routeId, List<BlockPos> pathPoints, List<ChunkPos> materializedChunks) {
		this.routeId = routeId;
		this.pathPoints = List.copyOf(pathPoints);
		this.materializedChunks = new HashSet<>(materializedChunks);
	}

	public PhysicalRouteData(String routeId, List<BlockPos> pathPoints) {
		this(routeId, pathPoints, List.of());
	}

	public String getRouteId() {
		return this.routeId;
	}

	public List<BlockPos> getPathPoints() {
		return this.pathPoints;
	}

	public List<ChunkPos> getMaterializedChunks() {
		return new ArrayList<>(this.materializedChunks);
	}

	public boolean isMaterializedIn(ChunkPos chunkPos) {
		return this.materializedChunks.contains(chunkPos);
	}

	public boolean markChunkMaterialized(ChunkPos chunkPos) {
		return this.materializedChunks.add(chunkPos);
	}

	public int getMaterializedCount() {
		return this.materializedChunks.size();
	}
}

