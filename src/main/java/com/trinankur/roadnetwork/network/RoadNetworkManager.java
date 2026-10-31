package com.trinankur.roadnetwork.network;

import com.trinankur.roadnetwork.discovery.DiscoveredStructure;
import com.trinankur.roadnetwork.network.graph.NetworkEdge;
import com.trinankur.roadnetwork.network.node.NetworkNode;
import com.trinankur.roadnetwork.network.planning.NetworkPlanner;
import com.trinankur.roadnetwork.network.route.NetworkRoute;
import com.trinankur.roadnetwork.network.storage.RoadNetworkSavedData;
import com.trinankur.roadnetwork.util.DimensionValidator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service manager for transportation network operations.
 * Coordinates asynchronous background planning and thread-safe persistent state updates.
 */
public final class RoadNetworkManager {

	private RoadNetworkManager() {
	}

	/**
	 * Ingests a list of discovered structures into the level's persistent network storage.
	 * Must be called on the main server thread.
	 * Returns the number of nodes newly added or updated to confirmed status.
	 */
	public static int ingestDiscoveredStructures(ServerLevel level, List<DiscoveredStructure> structures) {
		if (!DimensionValidator.isSupported(level) || structures == null || structures.isEmpty()) {
			return 0;
		}

		RoadNetworkSavedData data = RoadNetworkSavedData.get(level);
		int changed = 0;
		for (DiscoveredStructure structure : structures) {
			if (data.addOrUpdateDiscoveredStructure(structure)) {
				changed++;
			}
		}
		return changed;
	}

	/**
	 * Plans routes between major settlements asynchronously with topology reconciliation.
	 * Performs graph algorithm computation on a background worker thread,
	 * then applies reconciled edges and routes to persistent storage on the main server thread.
	 */
	public static CompletableFuture<NetworkPlanner.PlanningResult> planNetworkAsync(
		ServerLevel level,
		double maxConnectionDistanceBlocks
	) {
		if (!DimensionValidator.isSupported(level)) {
			return CompletableFuture.completedFuture(new NetworkPlanner.PlanningResult(
				Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), 0
			));
		}

		// Snapshot current data on the main server thread
		RoadNetworkSavedData savedData = RoadNetworkSavedData.get(level);
		List<NetworkNode> nodesSnapshot = new ArrayList<>(savedData.getNodes());
		List<NetworkEdge> edgesSnapshot = new ArrayList<>(savedData.getEdges());
		List<NetworkRoute> routesSnapshot = new ArrayList<>(savedData.getRoutes());

		// Execute graph planning and reconciliation in the background worker pool
		return CompletableFuture.supplyAsync(
			() -> NetworkPlanner.plan(nodesSnapshot, edgesSnapshot, routesSnapshot, maxConnectionDistanceBlocks),
			Util.backgroundExecutor().forName("roadnetwork-planner")
		).thenApplyAsync(result -> {
			// Apply reconciliation on the main server thread
			if (level.getServer() != null && level.getServer().isRunning() && DimensionValidator.isSupported(level)) {
				RoadNetworkSavedData targetData = RoadNetworkSavedData.get(level);
				targetData.applyReconciliation(result);
			}
			return result;
		}, level.getServer());
	}

	/**
	 * Synchronously plans routes between major settlements with topology reconciliation.
	 * Must be called on the main server thread.
	 */
	public static NetworkPlanner.PlanningResult planNetwork(
		ServerLevel level,
		double maxConnectionDistanceBlocks
	) {
		DimensionValidator.validateLevel(level);

		RoadNetworkSavedData savedData = RoadNetworkSavedData.get(level);
		NetworkPlanner.PlanningResult result = NetworkPlanner.plan(
			savedData.getNodes(),
			savedData.getEdges(),
			savedData.getRoutes(),
			maxConnectionDistanceBlocks
		);

		savedData.applyReconciliation(result);

		return result;
	}
}

