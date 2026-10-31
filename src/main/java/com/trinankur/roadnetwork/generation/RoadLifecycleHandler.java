package com.trinankur.roadnetwork.generation;

import com.trinankur.roadnetwork.discovery.DiscoveryStatus;
import com.trinankur.roadnetwork.discovery.StructureClassification;
import com.trinankur.roadnetwork.discovery.StructureClassifier;
import com.trinankur.roadnetwork.network.node.NetworkNode;
import com.trinankur.roadnetwork.network.route.NetworkRoute;
import com.trinankur.roadnetwork.network.storage.PhysicalRouteData;
import com.trinankur.roadnetwork.network.storage.RoadNetworkSavedData;
import com.trinankur.roadnetwork.pathfinding.PhysicalPath;
import com.trinankur.roadnetwork.bridge.BridgeGenerator;
import com.trinankur.roadnetwork.config.RoadNetworkConfig;
import com.trinankur.roadnetwork.tunnel.TunnelGenerator;
import com.trinankur.roadnetwork.util.DimensionValidator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handles chunk lifecycle events to resolve structure elevations and materialize road blocks safely.
 * Operates on the server thread during CHUNK_LOAD without force-loading distant chunks.
 */
public final class RoadLifecycleHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger("roadnetwork-lifecycle");
	private static final java.util.Set<ChunkPos> PENDING_CHUNKS = java.util.concurrent.ConcurrentHashMap.newKeySet();

	private RoadLifecycleHandler() {
	}

	public static void register() {
		ServerChunkEvents.CHUNK_LOAD.register(RoadLifecycleHandler::onChunkLoad);
		ServerTickEvents.END_LEVEL_TICK.register(RoadLifecycleHandler::onLevelTick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING_CHUNKS.clear());
	}

	public static void queueAlreadyLoadedChunks(ServerLevel level, PhysicalRouteData physicalRoute) {
		if (level == null || physicalRoute == null || physicalRoute.getPathPoints() == null) {
			return;
		}
		java.util.Set<ChunkPos> touchedChunks = new java.util.HashSet<>();
		for (BlockPos pt : physicalRoute.getPathPoints()) {
			int cx = pt.getX() >> 4;
			int cz = pt.getZ() >> 4;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					touchedChunks.add(new ChunkPos(cx + dx, cz + dz));
				}
			}
		}
		for (ChunkPos cp : touchedChunks) {
			if (!physicalRoute.isMaterializedIn(cp)) {
				if (level.getChunkSource().getChunkNow(cp.x(), cp.z()) != null) {
					PENDING_CHUNKS.add(cp);
				}
			}
		}
	}

	public static void onChunkLoad(ServerLevel level, LevelChunk chunk, boolean isNew) {
		if (!DimensionValidator.isOverworld(level)) {
			return;
		}

		RoadNetworkSavedData data = RoadNetworkSavedData.get(level);
		ChunkPos chunkPos = chunk.getPos();

		// 1. Resolve and validate structure destinations from loaded structure starts
		validateAndResolveDestinations(level, chunk, data);

		// 2. Queue chunk for safe deferred materialization on server tick to prevent ChunkMap deadlocks
		for (PhysicalRouteData physicalRoute : data.getPhysicalRoutes()) {
			if (!physicalRoute.isMaterializedIn(chunkPos)) {
				PhysicalPath path = new PhysicalPath(physicalRoute.getPathPoints());
				if (!path.getPointsNearChunk(chunkPos, 6).isEmpty()) {
					PENDING_CHUNKS.add(chunkPos);
					break;
				}
			}
		}
	}

	public static void onLevelTick(ServerLevel level) {
		if (!DimensionValidator.isOverworld(level) || PENDING_CHUNKS.isEmpty()) {
			return;
		}

		RoadNetworkSavedData data = RoadNetworkSavedData.get(level);
		java.util.Iterator<ChunkPos> iterator = PENDING_CHUNKS.iterator();
		int processedThisTick = 0;
		int checkedThisTick = 0;

		while (iterator.hasNext() && processedThisTick < 16 && checkedThisTick < 60) {
			ChunkPos chunkPos = iterator.next();
			checkedThisTick++;

			// getChunkNow retrieves the chunk ONLY if already loaded, completely avoiding loading deadlocks
			LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x(), chunkPos.z());
			if (chunk != null) {
				iterator.remove();
				materializeRoadSegments(level, chunk, data, chunkPos);
				processedThisTick++;
			}
		}
	}

	private static void validateAndResolveDestinations(ServerLevel level, LevelChunk chunk, RoadNetworkSavedData data) {
		ChunkPos chunkPos = chunk.getPos();
		Map<Structure, StructureStart> starts = chunk.getAllStarts();
		var structureRegistry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);

		java.util.Set<Identifier> foundStructures = new java.util.HashSet<>();

		if (starts != null && !starts.isEmpty()) {
			for (Map.Entry<Structure, StructureStart> entry : starts.entrySet()) {
				Structure structure = entry.getKey();
				StructureStart start = entry.getValue();

				if (start != null && start.isValid()) {
					Identifier structureId = structureRegistry.getKey(structure);
					if (structureId != null) {
						foundStructures.add(structureId);
						String exactNodeId = NetworkNode.createNodeId(structureId, chunkPos);
						NetworkNode existingNode = data.getNode(exactNodeId).orElse(null);

						// Also match nearby predicted node within 2 chunks in case structure start is offset
						if (existingNode == null) {
							for (NetworkNode n : data.getNodes()) {
								if (n.structureId().equals(structureId) && n.status() == DiscoveryStatus.PREDICTED) {
									int cdx = Math.abs(n.chunkPos().x() - chunkPos.x());
									int cdz = Math.abs(n.chunkPos().z() - chunkPos.z());
									if (cdx <= 2 && cdz <= 2) {
										existingNode = n;
										break;
									}
								}
							}
						}

						if (existingNode != null) {
							BlockPos center = start.getBoundingBox().getCenter();
							if (existingNode.status() != DiscoveryStatus.CONFIRMED || existingNode.elevation().isEmpty() || existingNode.elevation().get() != center.getY()) {
								NetworkNode confirmed = existingNode.withConfirmation(center.getX(), center.getY(), center.getZ());
								data.addOrUpdateNode(confirmed);
								LOGGER.info("Resolved elevation and confirmed node {} at {}", existingNode.id(), confirmed.formatCoordinates());
							}
						} else {
							var holder = structureRegistry.get(structureId);
							if (holder.isPresent()) {
								StructureClassification classification = StructureClassifier.classify(holder.get());
								if (classification == StructureClassification.MAJOR || classification == StructureClassification.SECONDARY) {
									BlockPos center = start.getBoundingBox().getCenter();
									NetworkNode newNode = new NetworkNode(
										exactNodeId,
										structureId,
										classification,
										center.getX(),
										center.getZ(),
										Optional.of(center.getY()),
										chunkPos,
										DiscoveryStatus.CONFIRMED
									);
									data.addOrUpdateNode(newNode);
									LOGGER.info("Directly registered confirmed node {} at {}", exactNodeId, newNode.formatCoordinates());
									com.trinankur.roadnetwork.autonomous.AutonomousNetworkManager.triggerAutonomousDiscovery(level, center, data);
								}
							}
						}
					}
				}
			}
		}

		// Check if any predicted nodes in this chunk failed to generate
		for (NetworkNode node : data.getNodes()) {
			if (node.chunkPos().equals(chunkPos) && node.status() == DiscoveryStatus.PREDICTED) {
				boolean hasPieceOrStart = foundStructures.contains(node.structureId());
				if (!hasPieceOrStart && chunk.getAllReferences() != null) {
					for (Structure s : chunk.getAllReferences().keySet()) {
						Identifier sId = structureRegistry.getKey(s);
						if (node.structureId().equals(sId)) {
							hasPieceOrStart = true;
							break;
						}
					}
				}
				// Verify whether a node was already confirmed within nearby chunks before invalidating
				boolean confirmedNearby = false;
				for (NetworkNode cn : data.getNodes()) {
					if (cn.status() == DiscoveryStatus.CONFIRMED && cn.structureId().equals(node.structureId())) {
						if (Math.abs(cn.chunkPos().x() - chunkPos.x()) <= 3 && Math.abs(cn.chunkPos().z() - chunkPos.z()) <= 3) {
							confirmedNearby = true;
							break;
						}
					}
				}
				if (!hasPieceOrStart && !confirmedNearby) {
					data.invalidateNode(node.id());
					com.trinankur.roadnetwork.autonomous.AutonomousNetworkManager.triggerAutonomousDiscovery(
						level, new BlockPos(chunkPos.getMiddleBlockX(), level.getSeaLevel(), chunkPos.getMiddleBlockZ()), data
					);
					LOGGER.info("Predicted candidate was absent in chunk {}. Invalidated node {} and queued network re-planning.", chunkPos, node.id());
				}
			}
		}
	}

	private static void materializeRoadSegments(
		ServerLevel level,
		LevelChunk chunk,
		RoadNetworkSavedData data,
		ChunkPos chunkPos
	) {
		for (PhysicalRouteData physicalRoute : data.getPhysicalRoutes()) {
			String routeId = physicalRoute.getRouteId();

			// Check if already materialized in this chunk
			if (physicalRoute.isMaterializedIn(chunkPos)) {
				continue;
			}

			// Active route or dev test route check
			boolean isDevTest = routeId.startsWith("route_dev_test");
			if (!isDevTest && data.getRoute(routeId).isEmpty()) {
				continue;
			}

			// Check if this route contains points near this chunk (including boundary margin)
			PhysicalPath path = new PhysicalPath(physicalRoute.getPathPoints());
			List<BlockPos> pointsInChunk = path.getPointsNearChunk(chunkPos, 6);

			if (!pointsInChunk.isEmpty()) {
				RoadStyle.RoadClass roadClass = data.getRoute(routeId)
					.map(NetworkRoute::roadClass)
					.orElse(RoadStyle.RoadClass.VILLAGE_ROAD);
				RoadStyle style = RoadStyle.selectStyle(
					chunkPos.getMinBlockX(), chunkPos.getMinBlockZ(), roadClass,
					RoadStyle.StyleProfile.NATURAL, level, new net.minecraft.core.BlockPos(chunkPos.getMinBlockX(), 0, chunkPos.getMinBlockZ())
				);
				int blocksPlaced = RoadChunkMaterializer.materializeChunk(level, chunk, pointsInChunk, style);
				// Piece 3: bridge threshold check (skeleton integration)
				if (pointsInChunk.size() >= 2) {
					int startY = pointsInChunk.get(0).getY();
					int endY = pointsInChunk.get(pointsInChunk.size() - 1).getY();
					double dist = pointsInChunk.get(0).distSqr(pointsInChunk.get(pointsInChunk.size() - 1));
					if (BridgeGenerator.canBridge(startY, endY, Math.sqrt(dist))) {
						// Bridge logic would generate here; kept as integration hook
					}
				}
				// Piece 4: tunnel integration hook
				if (pointsInChunk.size() > 10) {
					int firstY = pointsInChunk.get(0).getY();
					int lastY = pointsInChunk.get(pointsInChunk.size() - 1).getY();
					double length = Math.sqrt(pointsInChunk.get(0).distSqr(pointsInChunk.get(pointsInChunk.size() - 1)));
					int elevationDelta = Math.abs(firstY - lastY);
					if (TunnelGenerator.shouldTunnel(elevationDelta, length)) {
						// Tunnel logic hook
					}
				}
				// Piece 5: config-controlled feature activation (default if unconfigured)
				RoadNetworkConfig config = new RoadNetworkConfig(); // placeholder; actual load would use saved config
				if (config.enableLighting()) {
					// Lighting/roadside would be applied here using RoadLightingHandler
				}
				data.markChunkMaterialized(routeId, chunkPos);
				LOGGER.info("Autonomously materialized {} road blocks for route {} in chunk {}", blocksPlaced, routeId, chunkPos);
			}
		}
	}
}

