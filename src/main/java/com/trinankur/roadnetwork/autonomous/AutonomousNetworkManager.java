package com.trinankur.roadnetwork.autonomous;

import com.trinankur.roadnetwork.discovery.DiscoveredStructure;
import com.trinankur.roadnetwork.discovery.StructurePlacementScanner;
import com.trinankur.roadnetwork.generation.RoadChunkMaterializer;
import com.trinankur.roadnetwork.generation.RoadLifecycleHandler;
import com.trinankur.roadnetwork.generation.VillagePerimeterCalculator;
import com.trinankur.roadnetwork.network.node.NetworkNode;
import com.trinankur.roadnetwork.network.planning.NetworkPlanner;
import com.trinankur.roadnetwork.network.route.NetworkRoute;
import com.trinankur.roadnetwork.network.storage.PhysicalRouteData;
import com.trinankur.roadnetwork.network.storage.RoadNetworkSavedData;
import com.trinankur.roadnetwork.pathfinding.PhysicalPath;
import com.trinankur.roadnetwork.pathfinding.TerrainPathfinder;
import com.trinankur.roadnetwork.pathfinding.WorldGenTerrainSampler;
import com.trinankur.roadnetwork.config.RoadNetworkConfig;
import com.trinankur.roadnetwork.util.DimensionValidator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Autonomous Transportation Network Manager.
 * Orchestrates background discovery, network graph planning, physical route generation,
 * and chunk materialization as players explore without requiring any manual commands.
 */
public final class AutonomousNetworkManager {

	private static final Logger LOGGER = LoggerFactory.getLogger("roadnetwork-autonomous");
	private static final int REGION_CHUNK_SHIFT = 5; // 32 chunks (512 blocks) per region
	private static final int AUTONOMOUS_SCAN_RADIUS_CHUNKS = 80; // ~1280 blocks radius
	private static final double MAX_CONNECTION_DISTANCE = 1500.0; // 1500 blocks connection distance

	private static final AtomicBoolean IS_SCANNING = new AtomicBoolean(false);
	private static final java.util.concurrent.atomic.AtomicReference<BlockPos> PENDING_SCAN_ORIGIN = new java.util.concurrent.atomic.AtomicReference<>(null);

	private AutonomousNetworkManager() {
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(AutonomousNetworkManager::onLevelTick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			IS_SCANNING.set(false);
			PENDING_SCAN_ORIGIN.set(null);
		});
	}

	public static void onLevelTick(ServerLevel level) {
		if (!DimensionValidator.isSupported(level)) {
			return;
		}

		// Allow spawn area and initial chunk mesh packets to complete cleanly before triggering discovery
		if (level.getGameTime() < 100L) {
			return;
		}

		// Run periodic check every 60 ticks (3 seconds)
		if (level.getGameTime() % 60L != 0L) {
			return;
		}

		if (IS_SCANNING.get()) {
			return;
		}

		RoadNetworkSavedData data = RoadNetworkSavedData.get(level);

		for (ServerPlayer player : level.players()) {
			BlockPos playerPos = player.blockPosition();
			int chunkX = playerPos.getX() >> 4;
			int chunkZ = playerPos.getZ() >> 4;
			int regionX = chunkX >> REGION_CHUNK_SHIFT;
			int regionZ = chunkZ >> REGION_CHUNK_SHIFT;

			ChunkPos regionPos = new ChunkPos(regionX, regionZ);
			if (!data.isRegionScanned(regionPos)) {
				data.markRegionScanned(regionPos);
				triggerAutonomousDiscovery(level, playerPos, data);
				break; // Process one region per check
			}
		}
	}

	/**
	 * Executes background mathematical discovery, plans routes, and pre-calculates physical route paths.
	 */
	public static void triggerAutonomousDiscovery(ServerLevel level, BlockPos origin, RoadNetworkSavedData data) {
		if (!IS_SCANNING.compareAndSet(false, true)) {
			PENDING_SCAN_ORIGIN.set(origin);
			return;
		}

		LOGGER.info("Starting autonomous regional discovery around [X: {}, Z: {}]...", origin.getX(), origin.getZ());

		StructurePlacementScanner.scanAsync(
			level,
			origin,
			StructurePlacementScanner.ScanConfig.of(AUTONOMOUS_SCAN_RADIUS_CHUNKS)
		).thenAcceptAsync(results -> {
			try {
				processDiscoveryResultsAsync(level, data, results);
			} catch (Exception e) {
				LOGGER.error("Error during autonomous discovery processing", e);
				IS_SCANNING.set(false);
			}
		}, Util.backgroundExecutor().forName("roadnetwork-planner")).exceptionally(ex -> {
			LOGGER.error("Failed to execute autonomous background scan", ex);
			IS_SCANNING.set(false);
			return null;
		});
	}

	private static void processDiscoveryResultsAsync(
		ServerLevel level,
		RoadNetworkSavedData data,
		List<DiscoveredStructure> results
	) {
		LOGGER.info("Autonomous scan returned {} candidates. Combining with {} existing nodes...",
			results.size(), data.getNodes().size());

		// 1. Combine existing nodes with newly discovered structures
		java.util.List<NetworkNode> allNodes = new java.util.ArrayList<>(data.getNodes());
		java.util.Set<String> existingNodeIds = new java.util.HashSet<>();
		for (NetworkNode n : allNodes) {
			existingNodeIds.add(n.id());
		}

		for (DiscoveredStructure s : results) {
			if (s.classification() == com.trinankur.roadnetwork.discovery.StructureClassification.MAJOR
				|| s.classification() == com.trinankur.roadnetwork.discovery.StructureClassification.SECONDARY) {
				String nodeId = NetworkNode.createNodeId(s.id(), s.chunkPos());
				if (!existingNodeIds.contains(nodeId)) {
					NetworkNode node = NetworkNode.fromDiscoveredStructure(s);
					allNodes.add(node);
					existingNodeIds.add(nodeId);
				}
			}
		}

		LOGGER.info("Autonomous network graph contains {} total nodes. Planning abstract topology...", allNodes.size());

		// 2. Plan abstract network
		NetworkPlanner.PlanningResult planResult = NetworkPlanner.plan(
			allNodes,
			data.getEdges(),
			data.getRoutes(),
			MAX_CONNECTION_DISTANCE
		);

		LOGGER.info("Network planning result: {} new routes, {} removed obsolete routes. (Existing routes: {})",
			planResult.newRoutes().size(), planResult.removedRouteIds().size(), data.getRoutes().size());

		// Reconcile graph topology on server thread immediately
		level.getServer().execute(() -> {
			int newNodes = 0;
			for (DiscoveredStructure s : results) {
				if (data.addOrUpdateDiscoveredStructure(s)) {
					newNodes++;
				}
			}

			if (planResult.hasChanges()) {
				data.applyReconciliation(planResult);
				LOGGER.info("Autonomous network topology updated: +{} routes, -{} obsolete routes.",
					planResult.newRoutes().size(), planResult.removedRouteIds().size());
			}

			if (newNodes > 0 || planResult.hasChanges()) {
				data.setDirty();
			}
		});

		// 3. Pre-calculate physical path geometry on background worker thread
		WorldGenTerrainSampler sampler = new WorldGenTerrainSampler(level);
		java.util.Map<String, NetworkNode> nodeMap = new java.util.HashMap<>();
		for (NetworkNode n : allNodes) {
			nodeMap.put(n.id(), n);
		}

		// Calculate paths for newly planned routes with immediate per-route server dispatch
		for (NetworkRoute route : planResult.newRoutes()) {
			NetworkNode startNode = nodeMap.get(route.startNodeId());
			NetworkNode endNode = nodeMap.get(route.endNodeId());

			if (startNode != null && endNode != null) {
				int startY = startNode.hasKnownElevation() ? startNode.elevation().get() : sampler.getSurfaceHeight(startNode.x(), startNode.z());
				int endY = endNode.hasKnownElevation() ? endNode.elevation().get() : sampler.getSurfaceHeight(endNode.x(), endNode.z());

				BlockPos startPos = new BlockPos(startNode.x(), startY, startNode.z());
				BlockPos endPos = new BlockPos(endNode.x(), endY, endNode.z());

				long t0 = System.currentTimeMillis();
				PhysicalPath physicalPath = TerrainPathfinder.findPath(startPos, endPos, sampler);
				long elapsed = System.currentTimeMillis() - t0;
				LOGGER.info("Pre-computed physical path for route {} ({} points) in {}ms",
					route.id(), physicalPath.getPoints().size(), elapsed);

				PhysicalRouteData prd = new PhysicalRouteData(route.id(), physicalPath.getPoints());
				level.getServer().execute(() -> {
					data.savePhysicalRoute(prd);
					RoadLifecycleHandler.queueAlreadyLoadedChunks(level, prd);
					data.setDirty();
					LOGGER.info("Progressively saved and queued route {} ({} points) for materialization.",
						route.id(), prd.getPathPoints().size());
				});
			}
		}

		// Calculate paths for any uncomputed existing routes with immediate per-route server dispatch
		for (NetworkRoute route : data.getRoutes()) {
			if (data.getPhysicalRoute(route.id()).isEmpty() && planResult.removedRouteIds().stream().noneMatch(id -> id.equals(route.id()))) {
				NetworkNode startNode = nodeMap.get(route.startNodeId());
				NetworkNode endNode = nodeMap.get(route.endNodeId());

				if (startNode != null && endNode != null) {
					int startY = startNode.hasKnownElevation() ? startNode.elevation().get() : sampler.getSurfaceHeight(startNode.x(), startNode.z());
					int endY = endNode.hasKnownElevation() ? endNode.elevation().get() : sampler.getSurfaceHeight(endNode.x(), endNode.z());

					BlockPos startPos = new BlockPos(startNode.x(), startY, startNode.z());
					BlockPos endPos = new BlockPos(endNode.x(), endY, endNode.z());

					long t0 = System.currentTimeMillis();
					PhysicalPath physicalPath = TerrainPathfinder.findPath(startPos, endPos, sampler);
					long elapsed = System.currentTimeMillis() - t0;
					LOGGER.info("Pre-computed physical path for existing route {} ({} points) in {}ms",
						route.id(), physicalPath.getPoints().size(), elapsed);

					PhysicalRouteData prd = new PhysicalRouteData(route.id(), physicalPath.getPoints());
					level.getServer().execute(() -> {
						data.savePhysicalRoute(prd);
						RoadLifecycleHandler.queueAlreadyLoadedChunks(level, prd);
						data.setDirty();
						LOGGER.info("Progressively saved and queued route {} ({} points) for materialization.",
							route.id(), prd.getPathPoints().size());
					});
				}
			}
		}

		// Complete the background scan and process any pending discovery requests
		level.getServer().execute(() -> {
			try {
				LOGGER.info("Autonomous discovery and route dispatch completed.");
			} finally {
				IS_SCANNING.set(false);
				BlockPos nextOrigin = PENDING_SCAN_ORIGIN.getAndSet(null);
				if (nextOrigin != null) {
					triggerAutonomousDiscovery(level, nextOrigin, data);
				}
			}
		});
	}
}

