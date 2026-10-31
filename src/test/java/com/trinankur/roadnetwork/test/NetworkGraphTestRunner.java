package com.trinankur.roadnetwork.test;

import com.trinankur.roadnetwork.discovery.DiscoveryStatus;
import com.trinankur.roadnetwork.discovery.StructureClassification;
import com.trinankur.roadnetwork.generation.RoadChunkMaterializer;
import com.trinankur.roadnetwork.generation.RoadMaterialSelector;
import com.trinankur.roadnetwork.generation.VillagePerimeterCalculator;
import com.trinankur.roadnetwork.network.graph.NetworkEdge;
import com.trinankur.roadnetwork.network.graph.NetworkGraph;
import com.trinankur.roadnetwork.network.node.NetworkNode;
import com.trinankur.roadnetwork.network.planning.NetworkPlanner;
import com.trinankur.roadnetwork.network.route.NetworkRoute;
import com.trinankur.roadnetwork.network.route.NetworkWaypoint;
import com.trinankur.roadnetwork.network.route.RouteStatus;
import com.trinankur.roadnetwork.network.storage.PhysicalRouteData;
import com.trinankur.roadnetwork.network.storage.RoadNetworkSavedData;
import com.trinankur.roadnetwork.pathfinding.PhysicalPath;
import com.trinankur.roadnetwork.pathfinding.TerrainHeightSampler;
import com.trinankur.roadnetwork.pathfinding.TerrainPathfinder;
import com.trinankur.roadnetwork.util.DimensionValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Milestone M2 Verification Runner:
 * Tests node identity, duplicate prevention, graph connection, duplicate edge prevention,
 * deterministic planning regardless of input order, maximum connection distance,
 * exclusion of ignored/unclassified structures, waypoint elevation safety,
 * persistence serialization and deserialization, and strict Overworld-only boundaries.
 */
public class NetworkGraphTestRunner {

	public static void main(String[] args) {
		System.out.println("==================================================");
		System.out.println("  Road Network M2 - Network Graph & Planning Tests");
		System.out.println("==================================================");

		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();

		testNodeIdentityAndDuplicatePrevention();
		testGraphConnectionAndDuplicateEdgePrevention();
		testPredictedToConfirmedUpgrade();
		testDeterministicPlanningRegardlessOfInputOrder();
		testMaximumConnectionDistance();
		testExclusionOfIgnoredAndUnclassified();
		testSecondaryStructureConnections();
		testPredictedNodesWaypointsElevation();
		testPersistenceSerializationAndReload();
		testOverworldOnlyRestrictions();

		// M2.1 Incremental planning & safety tests
		testAddingThirdNodeAndReconciliation();
		testProtectedRoutesNotReconciled();
		testRepeatedPlanningWithoutChanges();
		testPredictedNodeCannotAuthorizePhysicalConstruction();
		testConfirmedNodeAuthorizesEndpointValidation();
		testMissingStructureDoesNotProduceOrphanedRoad();
		testPersistenceAfterGraphReconciliation();

		// M3 Terrain-aware pathfinding & physical road generation tests
		testTerrainAwareFlatPath();
		testTerrainAwarePathAvoidsSteepMountain();
		testTerrainAwarePathAvoidsWaterCrossing();
		testDeterministicPathfinding();
		testContinuousGapFreePath();
		testPhysicalPathChunkPartitioning();
		testPhysicalRoutePersistenceAndProgress();
		testRoadMaterialSelectorDeterminism();
		testReplaceableGroundClassification();

		// M3.1 Construction diagnostics, chunk-load materialization, and dev-test safety
		testConstructionDiagnosticsClassification();
		testChunkLoadMaterializationAndIdempotency();
		testDevTestRouteSafetyIsolation();

		// M4 Autonomous Transportation Network tests
		testAutonomousRouteConsistencyFromBothDirections();
		testAutonomousProgressiveChunkMaterialization();
		testAutonomousDestinationValidationAndConfirmation();
		testAutonomousDestinationInvalidationAndReconciliation();
		testVillagePerimeterDockingOffset();
		testScannedRegionPersistence();

		System.out.println("\nAll milestone M2, M2.1, M3, M3.1 & M4 tests PASSED successfully!");
		System.out.println("==================================================");
	}

	public static void testNodeIdentityAndDuplicatePrevention() {
		System.out.print("[TEST] Testing node identity and duplicate prevention... ");

		NetworkGraph graph = new NetworkGraph();
		Identifier villageId = Identifier.fromNamespaceAndPath("minecraft", "village_plains");
		ChunkPos chunkPos = new ChunkPos(10, 20);

		String expectedId = "node_minecraft_village_plains_10_20";
		String actualId = NetworkNode.createNodeId(villageId, chunkPos);
		if (!expectedId.equals(actualId)) {
			throw new AssertionError("Node ID mismatch. Expected " + expectedId + ", got " + actualId);
		}

		NetworkNode node1 = new NetworkNode(
			actualId, villageId, StructureClassification.MAJOR,
			160, 320, Optional.empty(), chunkPos, DiscoveryStatus.PREDICTED
		);

		boolean added1 = graph.addOrUpdateNode(node1);
		if (!added1) {
			throw new AssertionError("Failed to add initial node to graph");
		}

		// Re-adding identical predicted node should not duplicate or change count
		boolean added2 = graph.addOrUpdateNode(node1);
		if (added2) {
			throw new AssertionError("Re-adding duplicate node should return false");
		}
		if (graph.getNodeCount() != 1) {
			throw new AssertionError("Expected 1 node, found: " + graph.getNodeCount());
		}

		System.out.println("OK");
	}

	public static void testGraphConnectionAndDuplicateEdgePrevention() {
		System.out.print("[TEST] Testing graph connection and duplicate edge prevention... ");

		NetworkGraph graph = new NetworkGraph();
		String nodeA = "node_minecraft_village_plains_0_0";
		String nodeB = "node_minecraft_village_desert_20_10";

		NetworkEdge edgeForward = NetworkEdge.create(nodeA, nodeB, 500.0, "route_AB");
		NetworkEdge edgeReverse = NetworkEdge.create(nodeB, nodeA, 500.0, "route_AB");

		// Normalization check
		if (!edgeForward.nodeA().equals(edgeReverse.nodeA()) || !edgeForward.nodeB().equals(edgeReverse.nodeB())) {
			throw new AssertionError("NetworkEdge.create failed to normalize endpoints lexicographically");
		}
		if (!edgeForward.edgeKey().equals(edgeReverse.edgeKey())) {
			throw new AssertionError("Edge keys do not match for reversed endpoints");
		}

		boolean addedForward = graph.addEdge(edgeForward);
		if (!addedForward) {
			throw new AssertionError("Failed to add edge to graph");
		}

		boolean addedReverse = graph.addEdge(edgeReverse);
		if (addedReverse) {
			throw new AssertionError("Duplicate reverse edge must be rejected by graph");
		}
		if (graph.getEdgeCount() != 1) {
			throw new AssertionError("Expected exactly 1 edge, found: " + graph.getEdgeCount());
		}
		if (!graph.hasEdge(nodeA, nodeB) || !graph.hasEdge(nodeB, nodeA)) {
			throw new AssertionError("Graph hasEdge must return true regardless of endpoint query order");
		}

		System.out.println("OK");
	}

	public static void testPredictedToConfirmedUpgrade() {
		System.out.print("[TEST] Testing node upgrade from predicted to confirmed... ");

		NetworkGraph graph = new NetworkGraph();
		Identifier villageId = Identifier.fromNamespaceAndPath("minecraft", "village_taiga");
		ChunkPos chunkPos = new ChunkPos(15, 30);
		String nodeId = NetworkNode.createNodeId(villageId, chunkPos);

		// 1. Initial predicted node
		NetworkNode predictedNode = new NetworkNode(
			nodeId, villageId, StructureClassification.MAJOR,
			240, 480, Optional.empty(), chunkPos, DiscoveryStatus.PREDICTED
		);
		graph.addOrUpdateNode(predictedNode);

		NetworkNode current = graph.getNode(nodeId).orElseThrow();
		if (current.status() != DiscoveryStatus.PREDICTED || current.hasKnownElevation()) {
			throw new AssertionError("Initial node must be PREDICTED with unknown elevation");
		}

		// 2. Confirmed node with validated elevation from loaded chunk
		NetworkNode confirmedNode = new NetworkNode(
			nodeId, villageId, StructureClassification.MAJOR,
			245, 488, Optional.of(74), chunkPos, DiscoveryStatus.CONFIRMED
		);
		boolean updated = graph.addOrUpdateNode(confirmedNode);
		if (!updated) {
			throw new AssertionError("addOrUpdateNode must return true when upgrading predicted node to confirmed");
		}
		if (graph.getNodeCount() != 1) {
			throw new AssertionError("Node count must remain 1 after confirmation upgrade");
		}

		NetworkNode upgraded = graph.getNode(nodeId).orElseThrow();
		if (upgraded.status() != DiscoveryStatus.CONFIRMED) {
			throw new AssertionError("Node status was not upgraded to CONFIRMED");
		}
		if (!upgraded.hasKnownElevation() || upgraded.elevation().get() != 74) {
			throw new AssertionError("Node elevation was not updated to validated Y: 74");
		}
		if (upgraded.x() != 245 || upgraded.z() != 488) {
			throw new AssertionError("Node coordinates were not updated to validated center");
		}

		// 3. Prevent downgrade from confirmed back to predicted
		boolean downgraded = graph.addOrUpdateNode(predictedNode);
		if (downgraded) {
			throw new AssertionError("Graph must reject downgrade from CONFIRMED to PREDICTED");
		}
		if (graph.getNode(nodeId).orElseThrow().status() != DiscoveryStatus.CONFIRMED) {
			throw new AssertionError("Node status must remain CONFIRMED");
		}

		System.out.println("OK");
	}

	public static void testDeterministicPlanningRegardlessOfInputOrder() {
		System.out.print("[TEST] Testing deterministic planning regardless of input order... ");

		// Create 5 village nodes in a spatial constellation
		List<NetworkNode> nodes = List.of(
			new NetworkNode("node_v1", Identifier.fromNamespaceAndPath("minecraft", "village_plains"), StructureClassification.MAJOR, 0, 0, Optional.empty(), new ChunkPos(0, 0), DiscoveryStatus.PREDICTED),
			new NetworkNode("node_v2", Identifier.fromNamespaceAndPath("minecraft", "village_desert"), StructureClassification.MAJOR, 400, 100, Optional.empty(), new ChunkPos(25, 6), DiscoveryStatus.PREDICTED),
			new NetworkNode("node_v3", Identifier.fromNamespaceAndPath("minecraft", "village_savanna"), StructureClassification.MAJOR, 150, 450, Optional.empty(), new ChunkPos(9, 28), DiscoveryStatus.PREDICTED),
			new NetworkNode("node_v4", Identifier.fromNamespaceAndPath("minecraft", "village_snowy"), StructureClassification.MAJOR, 800, 300, Optional.empty(), new ChunkPos(50, 18), DiscoveryStatus.PREDICTED),
			new NetworkNode("node_v5", Identifier.fromNamespaceAndPath("minecraft", "village_taiga"), StructureClassification.MAJOR, 600, 750, Optional.empty(), new ChunkPos(37, 46), DiscoveryStatus.PREDICTED)
		);

		double maxDistance = 1500.0;

		// Plan with natural order
		NetworkPlanner.PlanningResult baseline = NetworkPlanner.plan(nodes, Collections.emptyList(), maxDistance);

		// Test across 20 different permutations
		Random rng = new Random(42L);
		for (int run = 0; run < 20; run++) {
			List<NetworkNode> shuffled = new ArrayList<>(nodes);
			Collections.shuffle(shuffled, rng);

			NetworkPlanner.PlanningResult permResult = NetworkPlanner.plan(shuffled, Collections.emptyList(), maxDistance);

			if (permResult.newEdges().size() != baseline.newEdges().size()) {
				throw new AssertionError("Edge count varied across permutations: expected " + baseline.newEdges().size() + ", got " + permResult.newEdges().size());
			}
			if (permResult.newRoutes().size() != baseline.newRoutes().size()) {
				throw new AssertionError("Route count varied across permutations: expected " + baseline.newRoutes().size() + ", got " + permResult.newRoutes().size());
			}

			for (int i = 0; i < baseline.newEdges().size(); i++) {
				NetworkEdge baseEdge = baseline.newEdges().get(i);
				NetworkEdge permEdge = permResult.newEdges().get(i);
				if (!baseEdge.edgeKey().equals(permEdge.edgeKey())) {
					throw new AssertionError("Edge key mismatch at index " + i + ": " + baseEdge.edgeKey() + " vs " + permEdge.edgeKey());
				}
				if (Math.abs(baseEdge.distance() - permEdge.distance()) > 0.001) {
					throw new AssertionError("Edge distance mismatch at index " + i);
				}
			}

			for (int i = 0; i < baseline.newRoutes().size(); i++) {
				NetworkRoute baseRoute = baseline.newRoutes().get(i);
				NetworkRoute permRoute = permResult.newRoutes().get(i);
				if (!baseRoute.id().equals(permRoute.id())) {
					throw new AssertionError("Route ID mismatch at index " + i + ": " + baseRoute.id() + " vs " + permRoute.id());
				}
				if (baseRoute.waypoints().size() != permRoute.waypoints().size()) {
					throw new AssertionError("Waypoint count mismatch on route " + baseRoute.id());
				}
			}
		}

		System.out.println("OK");
	}

	public static void testMaximumConnectionDistance() {
		System.out.print("[TEST] Testing maximum connection distance enforcement... ");

		NetworkNode villageA = new NetworkNode("node_A", Identifier.fromNamespaceAndPath("minecraft", "village_plains"), StructureClassification.MAJOR, 0, 0, Optional.empty(), new ChunkPos(0, 0), DiscoveryStatus.PREDICTED);
		NetworkNode villageB = new NetworkNode("node_B", Identifier.fromNamespaceAndPath("minecraft", "village_desert"), StructureClassification.MAJOR, 3000, 0, Optional.empty(), new ChunkPos(187, 0), DiscoveryStatus.PREDICTED);

		List<NetworkNode> nodes = List.of(villageA, villageB);

		// With maxDistance = 2000, distance 3000 exceeds limit -> 0 connections
		NetworkPlanner.PlanningResult resultUnder = NetworkPlanner.plan(nodes, Collections.emptyList(), 2000.0);
		if (resultUnder.hasNewConnections()) {
			throw new AssertionError("Connections must not be planned when distance exceeds maxConnectionDistance");
		}
		if (!resultUnder.newEdges().isEmpty() || !resultUnder.newRoutes().isEmpty()) {
			throw new AssertionError("Result lists must be empty when over max distance");
		}

		// With maxDistance = 4000, distance 3000 is allowed -> 1 connection
		NetworkPlanner.PlanningResult resultOver = NetworkPlanner.plan(nodes, Collections.emptyList(), 4000.0);
		if (!resultOver.hasNewConnections() || resultOver.newEdges().size() != 1) {
			throw new AssertionError("Expected 1 connection when within maxConnectionDistance");
		}
		if (Math.abs(resultOver.newEdges().get(0).distance() - 3000.0) > 0.001) {
			throw new AssertionError("Expected distance ~3000, got: " + resultOver.newEdges().get(0).distance());
		}

		System.out.println("OK");
	}

	public static void testExclusionOfIgnoredAndUnclassified() {
		System.out.print("[TEST] Testing exclusion of ignored, underground, and unclassified structures... ");

		NetworkNode villageA = new NetworkNode("node_villageA", Identifier.fromNamespaceAndPath("minecraft", "village_plains"), StructureClassification.MAJOR, 0, 0, Optional.empty(), new ChunkPos(0, 0), DiscoveryStatus.PREDICTED);
		NetworkNode villageB = new NetworkNode("node_villageB", Identifier.fromNamespaceAndPath("minecraft", "village_plains"), StructureClassification.MAJOR, 600, 0, Optional.empty(), new ChunkPos(37, 0), DiscoveryStatus.PREDICTED);
		NetworkNode ancientCity = new NetworkNode("node_ancient_city", Identifier.fromNamespaceAndPath("minecraft", "ancient_city"), StructureClassification.IGNORED, 200, 50, Optional.empty(), new ChunkPos(12, 3), DiscoveryStatus.PREDICTED);
		NetworkNode stronghold = new NetworkNode("node_stronghold", Identifier.fromNamespaceAndPath("minecraft", "stronghold"), StructureClassification.IGNORED, 400, 50, Optional.empty(), new ChunkPos(25, 3), DiscoveryStatus.PREDICTED);
		NetworkNode trialChambers = new NetworkNode("node_trial_chambers", Identifier.fromNamespaceAndPath("minecraft", "trial_chambers"), StructureClassification.IGNORED, 300, 20, Optional.empty(), new ChunkPos(18, 1), DiscoveryStatus.PREDICTED);
		NetworkNode unclassified = new NetworkNode("node_unknown", Identifier.fromNamespaceAndPath("modded", "unknown_structure"), StructureClassification.UNCLASSIFIED, 300, 100, Optional.empty(), new ChunkPos(18, 6), DiscoveryStatus.PREDICTED);

		List<NetworkNode> nodes = List.of(villageA, villageB, ancientCity, stronghold, trialChambers, unclassified);

		NetworkPlanner.PlanningResult result = NetworkPlanner.plan(nodes, Collections.emptyList(), 2000.0);

		if (result.nodesEvaluated() != 2) {
			throw new AssertionError("Expected only 2 MAJOR settlement nodes evaluated, found: " + result.nodesEvaluated());
		}
		if (result.newEdges().size() != 1) {
			throw new AssertionError("Expected exactly 1 edge between villages, found: " + result.newEdges().size());
		}

		NetworkEdge edge = result.newEdges().get(0);
		if (!edge.connects("node_villageA") || !edge.connects("node_villageB")) {
			throw new AssertionError("Edge must only connect villageA and villageB");
		}
		if (edge.connects("node_ancient_city") || edge.connects("node_stronghold") || edge.connects("node_trial_chambers") || edge.connects("node_unknown")) {
			throw new AssertionError("Ignored or unclassified structures must never be connected!");
		}

		System.out.println("OK");
	}

	public static void testSecondaryStructureConnections() {
		System.out.print("[TEST] Testing secondary structure connections (outposts, temples, mansions)... ");

		NetworkNode village = new NetworkNode("node_village", Identifier.fromNamespaceAndPath("minecraft", "village_plains"), StructureClassification.MAJOR, 0, 0, Optional.empty(), new ChunkPos(0, 0), DiscoveryStatus.PREDICTED);
		NetworkNode outpost = new NetworkNode("node_outpost", Identifier.fromNamespaceAndPath("minecraft", "pillager_outpost"), StructureClassification.SECONDARY, 350, 0, Optional.empty(), new ChunkPos(21, 0), DiscoveryStatus.PREDICTED);
		NetworkNode temple = new NetworkNode("node_temple", Identifier.fromNamespaceAndPath("minecraft", "desert_pyramid"), StructureClassification.SECONDARY, 0, 500, Optional.empty(), new ChunkPos(0, 31), DiscoveryStatus.PREDICTED);

		List<NetworkNode> nodes = List.of(village, outpost, temple);
		NetworkPlanner.PlanningResult result = NetworkPlanner.plan(nodes, Collections.emptyList(), 2000.0);

		if (result.newEdges().size() != 2) {
			throw new AssertionError("Expected 2 spur routes connecting outpost and temple to village, found: " + result.newEdges().size());
		}

		boolean hasOutpostRoute = false;
		boolean hasTempleRoute = false;
		for (NetworkRoute r : result.newRoutes()) {
			if (r.classification() != StructureClassification.SECONDARY) {
				throw new AssertionError("Spur route to secondary structure must have SECONDARY classification");
			}
			if (r.connects("node_village") && r.connects("node_outpost")) hasOutpostRoute = true;
			if (r.connects("node_village") && r.connects("node_temple")) hasTempleRoute = true;
		}

		if (!hasOutpostRoute || !hasTempleRoute) {
			throw new AssertionError("Outpost and temple must both be connected to village");
		}

		System.out.println("OK");
	}

	public static void testPredictedNodesWaypointsElevation() {
		System.out.print("[TEST] Testing waypoint elevation safety (no fake Y on predicted routes)... ");

		NetworkNode villageA = new NetworkNode("node_A", Identifier.fromNamespaceAndPath("minecraft", "village_plains"), StructureClassification.MAJOR, 0, 0, Optional.empty(), new ChunkPos(0, 0), DiscoveryStatus.PREDICTED);
		NetworkNode villageB = new NetworkNode("node_B", Identifier.fromNamespaceAndPath("minecraft", "village_desert"), StructureClassification.MAJOR, 800, 600, Optional.empty(), new ChunkPos(50, 37), DiscoveryStatus.PREDICTED);

		NetworkPlanner.PlanningResult result = NetworkPlanner.plan(List.of(villageA, villageB), Collections.emptyList(), 2000.0);
		if (result.newRoutes().isEmpty()) {
			throw new AssertionError("Expected planned route");
		}

		NetworkRoute route = result.newRoutes().get(0);
		List<NetworkWaypoint> waypoints = route.waypoints();
		if (waypoints.size() < 2) {
			throw new AssertionError("Route must contain at least start and end waypoints");
		}

		for (int i = 0; i < waypoints.size(); i++) {
			NetworkWaypoint wp = waypoints.get(i);
			if (wp.hasKnownY()) {
				throw new AssertionError("Predicted route waypoint at index " + i + " must NOT have known elevation (was: " + wp.y().get() + ")");
			}
			if (wp.y().isPresent()) {
				throw new AssertionError("Predicted route waypoint Y must be empty");
			}
			String formatted = wp.formatCoordinates();
			if (!formatted.contains("Y: ~")) {
				throw new AssertionError("Waypoint coordinates must format with '~' when Y is unknown: " + formatted);
			}
		}

		System.out.println("OK");
	}

	public static void testPersistenceSerializationAndReload() {
		System.out.print("[TEST] Testing persistence serialization and reload (SavedData Codec & NBT)... ");

		RoadNetworkSavedData original = new RoadNetworkSavedData();

		// Add nodes: one predicted (unknown Y), one confirmed (known Y: 72)
		NetworkNode nodeA = new NetworkNode("node_A", Identifier.fromNamespaceAndPath("minecraft", "village_plains"), StructureClassification.MAJOR, 100, 200, Optional.empty(), new ChunkPos(6, 12), DiscoveryStatus.PREDICTED);
		NetworkNode nodeB = new NetworkNode("node_B", Identifier.fromNamespaceAndPath("minecraft", "village_desert"), StructureClassification.MAJOR, 500, 600, Optional.of(72), new ChunkPos(31, 37), DiscoveryStatus.CONFIRMED);
		original.addOrUpdateNode(nodeA);
		original.addOrUpdateNode(nodeB);

		// Add edge and route
		String routeId = NetworkRoute.createRouteId(nodeA.id(), nodeB.id());
		double dist = nodeA.horizontalDistanceTo(nodeB);
		NetworkEdge edge = NetworkEdge.create(nodeA.id(), nodeB.id(), dist, routeId);
		original.addEdge(edge);

		List<NetworkWaypoint> waypoints = List.of(
			NetworkWaypoint.of(100, Optional.empty(), 200),
			NetworkWaypoint.of(300, Optional.empty(), 400),
			NetworkWaypoint.of(500, Optional.of(72), 600)
		);
		NetworkRoute route = NetworkRoute.create(nodeA.id(), nodeB.id(), StructureClassification.MAJOR, waypoints, dist, RouteStatus.PLANNED);
		original.addRoute(route);
		original.setLastPlannedTime(987654321L);

		// Serialize to NBT
		CompoundTag tag = original.serializeToNbt(null);
		if (!tag.contains("data")) {
			throw new AssertionError("Serialized NBT must contain 'data' tag");
		}
		if (tag.getInt("DataVersion").orElse(-1) != RoadNetworkSavedData.CURRENT_DATA_VERSION) {
			throw new AssertionError("Serialized DataVersion mismatch");
		}

		// Deserialize from NBT into fresh instance
		RoadNetworkSavedData reloaded = RoadNetworkSavedData.deserializeFromNbt(tag, null);

		if (reloaded.getDataVersion() != RoadNetworkSavedData.CURRENT_DATA_VERSION) {
			throw new AssertionError("Reloaded data version mismatch");
		}
		if (reloaded.getNodeCount() != 2) {
			throw new AssertionError("Expected 2 nodes after reload, found: " + reloaded.getNodeCount());
		}
		if (reloaded.getEdgeCount() != 1) {
			throw new AssertionError("Expected 1 edge after reload, found: " + reloaded.getEdgeCount());
		}
		if (reloaded.getRouteCount() != 1) {
			throw new AssertionError("Expected 1 route after reload, found: " + reloaded.getRouteCount());
		}
		if (reloaded.getLastPlannedTime() != 987654321L) {
			throw new AssertionError("Reloaded lastPlannedTime mismatch");
		}

		// Verify reloaded node states
		NetworkNode reloadedA = reloaded.getNode("node_A").orElseThrow();
		if (reloadedA.status() != DiscoveryStatus.PREDICTED || reloadedA.hasKnownElevation()) {
			throw new AssertionError("Reloaded node A state mismatch");
		}

		NetworkNode reloadedB = reloaded.getNode("node_B").orElseThrow();
		if (reloadedB.status() != DiscoveryStatus.CONFIRMED || !reloadedB.hasKnownElevation() || reloadedB.elevation().get() != 72) {
			throw new AssertionError("Reloaded node B state mismatch");
		}

		// Verify reloaded route and waypoints
		NetworkRoute reloadedRoute = reloaded.getRoute(routeId).orElseThrow();
		if (reloadedRoute.waypoints().size() != 3) {
			throw new AssertionError("Reloaded route waypoints count mismatch");
		}
		if (reloadedRoute.waypoints().get(0).hasKnownY()) {
			throw new AssertionError("Waypoint 0 should not have known Y");
		}
		if (!reloadedRoute.waypoints().get(2).hasKnownY() || reloadedRoute.waypoints().get(2).y().get() != 72) {
			throw new AssertionError("Waypoint 2 should have known Y: 72");
		}

		System.out.println("OK");
	}

	public static void testOverworldOnlyRestrictions() {
		System.out.print("[TEST] Testing Overworld-only restrictions across network layers... ");

		if (!DimensionValidator.isSupported(Level.OVERWORLD)) {
			throw new AssertionError("Overworld must be supported");
		}
		if (DimensionValidator.isSupported(Level.NETHER)) {
			throw new AssertionError("Nether must be rejected");
		}
		if (DimensionValidator.isSupported(Level.END)) {
			throw new AssertionError("End must be rejected");
		}

		boolean threw = false;
		try {
			DimensionValidator.validateDimension(Level.NETHER);
		} catch (IllegalArgumentException e) {
			threw = true;
			if (!e.getMessage().contains(DimensionValidator.UNSUPPORTED_DIMENSION_MESSAGE)) {
				throw new AssertionError("Unexpected exception message: " + e.getMessage());
			}
		}
		if (!threw) {
			throw new AssertionError("DimensionValidator.validateDimension must throw on non-Overworld");
		}

		System.out.println("OK");
	}

	public static void testAddingThirdNodeAndReconciliation() {
		System.out.print("[TEST] Testing adding third node after initial route and topology reconciliation... ");

		NetworkGraph graph = new NetworkGraph();
		NetworkNode villageA = new NetworkNode("node_A", Identifier.fromNamespaceAndPath("minecraft", "village_plains"), StructureClassification.MAJOR, 0, 0, Optional.of(70), new ChunkPos(0, 0), DiscoveryStatus.CONFIRMED);
		NetworkNode villageB = new NetworkNode("node_B", Identifier.fromNamespaceAndPath("minecraft", "village_desert"), StructureClassification.MAJOR, 600, 0, Optional.of(72), new ChunkPos(37, 0), DiscoveryStatus.CONFIRMED);

		graph.addOrUpdateNode(villageA);
		graph.addOrUpdateNode(villageB);

		// Initial plan: A and B connect directly
		NetworkPlanner.PlanningResult initialPlan = NetworkPlanner.plan(graph.getNodes(), graph.getEdges(), graph.getRoutes(), 2000.0);
		if (initialPlan.newEdges().size() != 1) {
			throw new AssertionError("Expected 1 initial edge between A and B, got: " + initialPlan.newEdges().size());
		}
		graph.applyReconciliation(initialPlan);

		String edgeKeyAB = NetworkEdge.makeKey("node_A", "node_B");
		if (!graph.hasEdge("node_A", "node_B")) {
			throw new AssertionError("Graph must contain initial edge A-B");
		}

		// Now discover third node C right between A and B
		NetworkNode villageC = new NetworkNode("node_C", Identifier.fromNamespaceAndPath("minecraft", "village_savanna"), StructureClassification.MAJOR, 300, 20, Optional.of(68), new ChunkPos(18, 1), DiscoveryStatus.CONFIRMED);
		graph.addOrUpdateNode(villageC);

		// Re-plan with C present
		NetworkPlanner.PlanningResult reconciledPlan = NetworkPlanner.plan(graph.getNodes(), graph.getEdges(), graph.getRoutes(), 2000.0);

		if (!reconciledPlan.hasChanges()) {
			throw new AssertionError("Reconciled plan must report changes");
		}
		if (!reconciledPlan.removedEdgeKeys().contains(edgeKeyAB)) {
			throw new AssertionError("Obsolete direct edge A-B must be scheduled for removal");
		}
		if (reconciledPlan.newEdges().size() != 2) {
			throw new AssertionError("Expected 2 new edges (A-C and B-C), got: " + reconciledPlan.newEdges().size());
		}

		// Apply reconciliation to graph
		graph.applyReconciliation(reconciledPlan);

		if (graph.hasEdge("node_A", "node_B")) {
			throw new AssertionError("Obsolete edge A-B was not removed from graph");
		}
		if (!graph.hasEdge("node_A", "node_C") || !graph.hasEdge("node_B", "node_C")) {
			throw new AssertionError("Graph must contain new edges A-C and B-C");
		}
		if (graph.getEdgeCount() != 2 || graph.getRouteCount() != 2) {
			throw new AssertionError("Expected exactly 2 edges and 2 routes in reconciled graph");
		}

		System.out.println("OK");
	}

	public static void testProtectedRoutesNotReconciled() {
		System.out.print("[TEST] Testing that protected/completed routes are preserved during reconciliation... ");

		NetworkGraph graph = new NetworkGraph();
		NetworkNode villageA = new NetworkNode("node_A", Identifier.fromNamespaceAndPath("minecraft", "village_plains"), StructureClassification.MAJOR, 0, 0, Optional.of(70), new ChunkPos(0, 0), DiscoveryStatus.CONFIRMED);
		NetworkNode villageB = new NetworkNode("node_B", Identifier.fromNamespaceAndPath("minecraft", "village_desert"), StructureClassification.MAJOR, 600, 0, Optional.of(72), new ChunkPos(37, 0), DiscoveryStatus.CONFIRMED);

		graph.addOrUpdateNode(villageA);
		graph.addOrUpdateNode(villageB);

		// Create completed/surveyed route A-B (protected)
		String routeIdAB = NetworkRoute.createRouteId("node_A", "node_B");
		NetworkEdge edgeAB = NetworkEdge.create("node_A", "node_B", 600.0, routeIdAB);
		NetworkRoute routeAB = NetworkRoute.create("node_A", "node_B", StructureClassification.MAJOR, List.of(NetworkWaypoint.of(0, 70, 0), NetworkWaypoint.of(600, 72, 0)), 600.0, RouteStatus.COMPLETED);

		graph.addEdge(edgeAB);
		graph.addRoute(routeAB);

		// Add village C between A and B
		NetworkNode villageC = new NetworkNode("node_C", Identifier.fromNamespaceAndPath("minecraft", "village_savanna"), StructureClassification.MAJOR, 300, 20, Optional.of(68), new ChunkPos(18, 1), DiscoveryStatus.CONFIRMED);
		graph.addOrUpdateNode(villageC);

		// Re-plan
		NetworkPlanner.PlanningResult plan = NetworkPlanner.plan(graph.getNodes(), graph.getEdges(), graph.getRoutes(), 2000.0);

		if (plan.removedEdgeKeys().contains(edgeAB.edgeKey())) {
			throw new AssertionError("Completed route A-B must NOT be marked obsolete or removed!");
		}

		graph.applyReconciliation(plan);

		if (!graph.hasEdge("node_A", "node_B")) {
			throw new AssertionError("Protected edge A-B must still exist in graph");
		}
		if (graph.getRoute(routeIdAB).orElseThrow().status() != RouteStatus.COMPLETED) {
			throw new AssertionError("Route A-B status must remain COMPLETED");
		}

		System.out.println("OK");
	}

	public static void testRepeatedPlanningWithoutChanges() {
		System.out.print("[TEST] Testing repeated planning without changes (idempotence and no dirty writes)... ");

		NetworkGraph graph = new NetworkGraph();
		NetworkNode villageA = new NetworkNode("node_A", Identifier.fromNamespaceAndPath("minecraft", "village_plains"), StructureClassification.MAJOR, 0, 0, Optional.of(70), new ChunkPos(0, 0), DiscoveryStatus.CONFIRMED);
		NetworkNode villageB = new NetworkNode("node_B", Identifier.fromNamespaceAndPath("minecraft", "village_desert"), StructureClassification.MAJOR, 400, 0, Optional.of(72), new ChunkPos(25, 0), DiscoveryStatus.CONFIRMED);

		graph.addOrUpdateNode(villageA);
		graph.addOrUpdateNode(villageB);

		// First plan
		NetworkPlanner.PlanningResult plan1 = NetworkPlanner.plan(graph.getNodes(), graph.getEdges(), graph.getRoutes(), 2000.0);
		graph.applyReconciliation(plan1);

		// Second plan immediately on same data
		NetworkPlanner.PlanningResult plan2 = NetworkPlanner.plan(graph.getNodes(), graph.getEdges(), graph.getRoutes(), 2000.0);

		if (plan2.hasChanges()) {
			throw new AssertionError("Second plan on identical data must have zero changes");
		}
		if (!plan2.newEdges().isEmpty()) {
			throw new AssertionError("newEdges must be empty on repeated plan");
		}
		if (!plan2.removedEdgeKeys().isEmpty()) {
			throw new AssertionError("removedEdgeKeys must be empty on repeated plan");
		}

		// Also test RoadNetworkSavedData dirty flag behavior
		RoadNetworkSavedData savedData = new RoadNetworkSavedData();
		savedData.addOrUpdateNode(villageA);
		savedData.addOrUpdateNode(villageB);
		savedData.applyReconciliation(plan1);

		// Clear dirty flag (simulating world save)
		savedData.setDirty(false);
		if (savedData.isDirty()) {
			throw new AssertionError("Dirty flag must be false after reset");
		}

		// Apply idempotent plan2
		boolean modified = savedData.applyReconciliation(plan2);
		if (modified) {
			throw new AssertionError("applyReconciliation must return false when no changes exist");
		}
		if (savedData.isDirty()) {
			throw new AssertionError("SavedData dirty flag must NOT be set when planning produces no changes");
		}

		System.out.println("OK");
	}

	public static void testPredictedNodeCannotAuthorizePhysicalConstruction() {
		System.out.print("[TEST] Testing that predicted nodes cannot authorize physical construction... ");

		// 1. Predicted node with unknown elevation
		NetworkNode pred1 = new NetworkNode("node_p1", Identifier.fromNamespaceAndPath("minecraft", "village_plains"), StructureClassification.MAJOR, 100, 200, Optional.empty(), new ChunkPos(6, 12), DiscoveryStatus.PREDICTED);
		if (pred1.isEligibleForPhysicalConstruction()) {
			throw new AssertionError("Predicted node with unknown elevation must NOT be eligible for physical construction");
		}
		if (pred1.canAuthorizeEndpointGeneration()) {
			throw new AssertionError("Predicted node must NOT authorize endpoint generation");
		}

		// 2. Predicted node even if an elevation were hypothetically present
		NetworkNode pred2 = new NetworkNode("node_p2", Identifier.fromNamespaceAndPath("minecraft", "village_desert"), StructureClassification.MAJOR, 300, 400, Optional.of(64), new ChunkPos(18, 25), DiscoveryStatus.PREDICTED);
		if (pred2.isEligibleForPhysicalConstruction()) {
			throw new AssertionError("Predicted node must NOT be eligible for physical construction regardless of elevation field");
		}

		System.out.println("OK");
	}

	public static void testConfirmedNodeAuthorizesEndpointValidation() {
		System.out.print("[TEST] Testing confirmed node endpoint validation... ");

		// 1. Confirmed node with validated elevation (loaded chunk)
		NetworkNode confLoaded = new NetworkNode("node_c1", Identifier.fromNamespaceAndPath("minecraft", "village_plains"), StructureClassification.MAJOR, 120, 240, Optional.of(72), new ChunkPos(7, 15), DiscoveryStatus.CONFIRMED);
		if (!confLoaded.isEligibleForPhysicalConstruction()) {
			throw new AssertionError("Confirmed node with validated elevation must be eligible for construction");
		}
		if (!confLoaded.canAuthorizeEndpointGeneration()) {
			throw new AssertionError("Confirmed node with validated elevation must authorize endpoint generation");
		}

		// 2. Confirmed node with unloaded elevation (chunk storage presence only)
		NetworkNode confUnloaded = new NetworkNode("node_c2", Identifier.fromNamespaceAndPath("minecraft", "village_taiga"), StructureClassification.MAJOR, 500, 800, Optional.empty(), new ChunkPos(31, 50), DiscoveryStatus.CONFIRMED);
		if (confUnloaded.isEligibleForPhysicalConstruction()) {
			throw new AssertionError("Confirmed node with UNLOADED elevation must NOT be eligible until chunk loads");
		}
		if (confUnloaded.canAuthorizeEndpointGeneration()) {
			throw new AssertionError("Confirmed node with unloaded elevation must NOT authorize endpoint generation");
		}

		System.out.println("OK");
	}

	public static void testMissingStructureDoesNotProduceOrphanedRoad() {
		System.out.print("[TEST] Testing missing/predicted structures cannot produce orphaned roads... ");

		NetworkNode conf1 = new NetworkNode("node_c1", Identifier.fromNamespaceAndPath("minecraft", "village_plains"), StructureClassification.MAJOR, 0, 0, Optional.of(70), new ChunkPos(0, 0), DiscoveryStatus.CONFIRMED);
		NetworkNode conf2 = new NetworkNode("node_c2", Identifier.fromNamespaceAndPath("minecraft", "village_desert"), StructureClassification.MAJOR, 500, 0, Optional.of(72), new ChunkPos(31, 0), DiscoveryStatus.CONFIRMED);
		NetworkNode pred = new NetworkNode("node_p", Identifier.fromNamespaceAndPath("minecraft", "village_savanna"), StructureClassification.MAJOR, 1000, 0, Optional.empty(), new ChunkPos(62, 0), DiscoveryStatus.PREDICTED);
		NetworkNode confNoY = new NetworkNode("node_c_no_y", Identifier.fromNamespaceAndPath("minecraft", "village_snowy"), StructureClassification.MAJOR, 1500, 0, Optional.empty(), new ChunkPos(93, 0), DiscoveryStatus.CONFIRMED);

		NetworkRoute readyRoute = NetworkRoute.create(conf1.id(), conf2.id(), StructureClassification.MAJOR, List.of(NetworkWaypoint.of(0, 70, 0), NetworkWaypoint.of(500, 72, 0)), 500.0, RouteStatus.PLANNED);
		NetworkRoute predRoute = NetworkRoute.create(conf1.id(), pred.id(), StructureClassification.MAJOR, List.of(NetworkWaypoint.of(0, 70, 0), NetworkWaypoint.of(1000, 0)), 1000.0, RouteStatus.PLANNED);
		NetworkRoute noYRoute = NetworkRoute.create(conf1.id(), confNoY.id(), StructureClassification.MAJOR, List.of(NetworkWaypoint.of(0, 70, 0), NetworkWaypoint.of(1500, 0)), 1500.0, RouteStatus.PLANNED);

		// Ready route
		if (!readyRoute.isEligibleForPhysicalConstruction(conf1, conf2)) {
			throw new AssertionError("Route between two confirmed loaded nodes must be eligible for construction");
		}
		if (readyRoute.checkConstructionEligibility(conf1, conf2) != NetworkRoute.RouteConstructionEligibility.READY) {
			throw new AssertionError("Expected READY status for confirmed endpoints");
		}

		// Route toward predicted candidate
		if (predRoute.isEligibleForPhysicalConstruction(conf1, pred)) {
			throw new AssertionError("Route toward predicted candidate must NOT be eligible for physical construction!");
		}
		if (predRoute.checkConstructionEligibility(conf1, pred) != NetworkRoute.RouteConstructionEligibility.BLOCKED_PREDICTED_ENDPOINTS) {
			throw new AssertionError("Expected BLOCKED_PREDICTED_ENDPOINTS status");
		}

		// Route toward confirmed node with unloaded elevation
		if (noYRoute.isEligibleForPhysicalConstruction(conf1, confNoY)) {
			throw new AssertionError("Route toward unloaded elevation must NOT be eligible for construction!");
		}
		if (noYRoute.checkConstructionEligibility(conf1, confNoY) != NetworkRoute.RouteConstructionEligibility.BLOCKED_UNKNOWN_ELEVATION) {
			throw new AssertionError("Expected BLOCKED_UNKNOWN_ELEVATION status");
		}

		// Route with missing/null endpoint node
		if (readyRoute.checkConstructionEligibility(conf1, null) != NetworkRoute.RouteConstructionEligibility.INVALID_ENDPOINTS) {
			throw new AssertionError("Missing endpoint must return INVALID_ENDPOINTS");
		}

		System.out.println("OK");
	}

	public static void testPersistenceAfterGraphReconciliation() {
		System.out.print("[TEST] Testing persistence serialization and reload after graph reconciliation... ");

		RoadNetworkSavedData data = new RoadNetworkSavedData();
		NetworkNode vA = new NetworkNode("node_A", Identifier.fromNamespaceAndPath("minecraft", "village_plains"), StructureClassification.MAJOR, 0, 0, Optional.of(70), new ChunkPos(0, 0), DiscoveryStatus.CONFIRMED);
		NetworkNode vB = new NetworkNode("node_B", Identifier.fromNamespaceAndPath("minecraft", "village_desert"), StructureClassification.MAJOR, 800, 0, Optional.of(72), new ChunkPos(50, 0), DiscoveryStatus.CONFIRMED);

		data.addOrUpdateNode(vA);
		data.addOrUpdateNode(vB);

		// Initial plan: A-B
		NetworkPlanner.PlanningResult plan1 = NetworkPlanner.plan(data.getNodes(), data.getEdges(), data.getRoutes(), 2000.0);
		data.applyReconciliation(plan1);

		// Add village C between A and B
		NetworkNode vC = new NetworkNode("node_C", Identifier.fromNamespaceAndPath("minecraft", "village_taiga"), StructureClassification.MAJOR, 400, 30, Optional.of(68), new ChunkPos(25, 1), DiscoveryStatus.CONFIRMED);
		data.addOrUpdateNode(vC);

		// Reconcile plan: removes A-B, adds A-C and B-C
		NetworkPlanner.PlanningResult plan2 = NetworkPlanner.plan(data.getNodes(), data.getEdges(), data.getRoutes(), 2000.0);
		data.applyReconciliation(plan2);

		// Serialize to NBT
		CompoundTag tag = data.serializeToNbt(null);

		// Deserialize into fresh instance
		RoadNetworkSavedData reloaded = RoadNetworkSavedData.deserializeFromNbt(tag, null);

		if (reloaded.getNodeCount() != 3) {
			throw new AssertionError("Expected 3 nodes after reload, found: " + reloaded.getNodeCount());
		}
		if (reloaded.getEdgeCount() != 2) {
			throw new AssertionError("Expected exactly 2 edges after reload, found: " + reloaded.getEdgeCount());
		}
		if (reloaded.getRouteCount() != 2) {
			throw new AssertionError("Expected exactly 2 routes after reload, found: " + reloaded.getRouteCount());
		}

		String obsoleteKeyAB = NetworkEdge.makeKey("node_A", "node_B");
		for (NetworkEdge edge : reloaded.getEdges()) {
			if (edge.edgeKey().equals(obsoleteKeyAB)) {
				throw new AssertionError("Obsolete edge A-B must NOT exist after reload");
			}
		}

		String routeIdAB = NetworkRoute.createRouteId("node_A", "node_B");
		if (reloaded.getRoute(routeIdAB).isPresent()) {
			throw new AssertionError("Obsolete route A-B must NOT exist after reload");
		}

		String routeIdAC = NetworkRoute.createRouteId("node_A", "node_C");
		String routeIdBC = NetworkRoute.createRouteId("node_B", "node_C");
		if (reloaded.getRoute(routeIdAC).isEmpty() || reloaded.getRoute(routeIdBC).isEmpty()) {
			throw new AssertionError("Reconciled routes A-C and B-C must exist after reload");
		}

		System.out.println("OK");
	}

	public static void testTerrainAwareFlatPath() {
		System.out.print("[TEST] Testing terrain-aware pathfinding on flat terrain... ");
		TerrainHeightSampler flat = new TerrainHeightSampler() {
			@Override public int getSurfaceHeight(int x, int z) { return 64; }
			@Override public int getOceanFloorHeight(int x, int z) { return 64; }
		};

		BlockPos start = new BlockPos(0, 64, 0);
		BlockPos end = new BlockPos(64, 64, 0);
		PhysicalPath path = TerrainPathfinder.findPath(start, end, flat);

		if (path.size() == 0) {
			throw new AssertionError("Path should not be empty");
		}
		if (!path.getStartPoint().equals(start)) {
			throw new AssertionError("Start point mismatch: " + path.getStartPoint());
		}
		if (!path.getEndPoint().equals(end)) {
			throw new AssertionError("End point mismatch: " + path.getEndPoint());
		}
		System.out.println("OK (path size: " + path.size() + ")");
	}

	public static void testTerrainAwarePathAvoidsSteepMountain() {
		System.out.print("[TEST] Testing pathfinder detours around steep mountain cliff... ");
		// Steep cliff at X in [30..70], Z in [-15..15] with height 120 (slope delta = 56)
		TerrainHeightSampler mountainTerrain = new TerrainHeightSampler() {
			@Override
			public int getSurfaceHeight(int x, int z) {
				if (x >= 30 && x <= 70 && z >= -15 && z <= 15) {
					return 120;
				}
				return 64;
			}
			@Override public int getOceanFloorHeight(int x, int z) { return getSurfaceHeight(x, z); }
		};

		BlockPos start = new BlockPos(0, 64, 0);
		BlockPos end = new BlockPos(100, 64, 0);
		PhysicalPath path = TerrainPathfinder.findPath(start, end, mountainTerrain);

		boolean climbedMountain = false;
		for (BlockPos p : path.getPoints()) {
			if (p.getX() >= 30 && p.getX() <= 70 && p.getZ() >= -15 && p.getZ() <= 15 && p.getY() > 100) {
				climbedMountain = true;
				break;
			}
		}

		if (climbedMountain) {
			throw new AssertionError("Pathfinder failed to detour around steep mountain cliff!");
		}
		System.out.println("OK (detoured via valley, max Y = " + path.getPoints().stream().mapToInt(BlockPos::getY).max().orElse(0) + ")");
	}

	public static void testTerrainAwarePathAvoidsWaterCrossing() {
		System.out.print("[TEST] Testing pathfinder avoids deep water bodies when land route exists... ");
		// Water obstacle straight ahead: X in [30..50], Z in [-10..10]
		TerrainHeightSampler waterTerrain = new TerrainHeightSampler() {
			@Override
			public int getSurfaceHeight(int x, int z) { return 64; }
			@Override
			public int getOceanFloorHeight(int x, int z) {
				if (x >= 30 && x <= 50 && z >= -10 && z <= 10) {
					return 50; // 14 blocks deep water
				}
				return 64;
			}
		};

		BlockPos start = new BlockPos(0, 64, 0);
		BlockPos end = new BlockPos(80, 64, 0);
		PhysicalPath path = TerrainPathfinder.findPath(start, end, waterTerrain);

		int waterPoints = 0;
		for (BlockPos p : path.getPoints()) {
			if (p.getX() >= 30 && p.getX() <= 50 && p.getZ() >= -10 && p.getZ() <= 10) {
				waterPoints++;
			}
		}

		if (waterPoints > 0) {
			throw new AssertionError("Pathfinder failed to avoid deep water body (crossed " + waterPoints + " water blocks)!");
		}
		System.out.println("OK (routed around water body on dry land)");
	}

	public static void testDeterministicPathfinding() {
		System.out.print("[TEST] Testing pathfinding determinism... ");
		TerrainHeightSampler mountainTerrain = new TerrainHeightSampler() {
			@Override
			public int getSurfaceHeight(int x, int z) {
				if (x >= 30 && x <= 70 && z >= -15 && z <= 15) {
					return 120;
				}
				return 64;
			}
			@Override public int getOceanFloorHeight(int x, int z) { return getSurfaceHeight(x, z); }
		};

		BlockPos start = new BlockPos(0, 64, 0);
		BlockPos end = new BlockPos(100, 64, 0);

		PhysicalPath path1 = TerrainPathfinder.findPath(start, end, mountainTerrain);
		PhysicalPath path2 = TerrainPathfinder.findPath(start, end, mountainTerrain);

		if (path1.size() != path2.size()) {
			throw new AssertionError("Path sizes differ: " + path1.size() + " vs " + path2.size());
		}
		for (int i = 0; i < path1.size(); i++) {
			if (!path1.getPoints().get(i).equals(path2.getPoints().get(i))) {
				throw new AssertionError("Paths differ at step " + i);
			}
		}
		System.out.println("OK (identical " + path1.size() + " points across runs)");
	}

	public static void testContinuousGapFreePath() {
		System.out.print("[TEST] Testing path continuity (gap-free adjacent blocks)... ");
		TerrainHeightSampler complexTerrain = new TerrainHeightSampler() {
			@Override
			public int getSurfaceHeight(int x, int z) {
				return 64 + (int) (Math.sin(x * 0.1) * 8 + Math.cos(z * 0.1) * 8);
			}
			@Override public int getOceanFloorHeight(int x, int z) { return getSurfaceHeight(x, z); }
		};

		BlockPos start = new BlockPos(0, complexTerrain.getSurfaceHeight(0, 0), 0);
		BlockPos end = new BlockPos(80, complexTerrain.getSurfaceHeight(80, 50), 50);

		PhysicalPath path = TerrainPathfinder.findPath(start, end, complexTerrain);
		List<BlockPos> points = path.getPoints();

		for (int i = 0; i < points.size() - 1; i++) {
			BlockPos p1 = points.get(i);
			BlockPos p2 = points.get(i + 1);

			int dx = Math.abs(p2.getX() - p1.getX());
			int dy = Math.abs(p2.getY() - p1.getY());
			int dz = Math.abs(p2.getZ() - p1.getZ());

			if (dx > 1 || dy > 1 || dz > 1) {
				throw new AssertionError(String.format("Discontinuity gap between %s and %s: dx=%d, dy=%d, dz=%d", p1, p2, dx, dy, dz));
			}
		}
		System.out.println("OK (all " + points.size() + " consecutive blocks are adjacent)");
	}

	public static void testPhysicalPathChunkPartitioning() {
		System.out.print("[TEST] Testing PhysicalPath chunk partitioning... ");
		List<BlockPos> points = List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(5, 64, 5),
			new BlockPos(15, 64, 15),
			new BlockPos(16, 64, 16),
			new BlockPos(31, 64, 31),
			new BlockPos(32, 64, 32)
		);

		PhysicalPath path = new PhysicalPath(points);

		if (path.getIntersectingChunks().size() != 3) {
			throw new AssertionError("Expected 3 chunks, got: " + path.getIntersectingChunks().size());
		}

		ChunkPos cp0 = new ChunkPos(0, 0);
		ChunkPos cp1 = new ChunkPos(1, 1);
		ChunkPos cp2 = new ChunkPos(2, 2);

		if (!path.intersectsChunk(cp0) || !path.intersectsChunk(cp1) || !path.intersectsChunk(cp2)) {
			throw new AssertionError("Intersecting chunks missing expected chunk coordinates");
		}
		if (path.getPointsForChunk(cp0).size() != 3) {
			throw new AssertionError("Expected 3 points in chunk (0,0), got: " + path.getPointsForChunk(cp0).size());
		}
		if (path.getPointsForChunk(cp1).size() != 2) {
			throw new AssertionError("Expected 2 points in chunk (1,1), got: " + path.getPointsForChunk(cp1).size());
		}
		if (path.getPointsForChunk(cp2).size() != 1) {
			throw new AssertionError("Expected 1 point in chunk (2,2), got: " + path.getPointsForChunk(cp2).size());
		}

		// Verify getPointsNearChunk with boundary margin
		List<BlockPos> nearChunk0 = path.getPointsNearChunk(cp0, 6);
		if (!nearChunk0.contains(new BlockPos(16, 64, 16))) {
			throw new AssertionError("Expected nearChunk0 to include adjacent point (16, 64, 16) within 6 blocks margin");
		}
		System.out.println("OK");
	}

	public static void testPhysicalRoutePersistenceAndProgress() {
		System.out.print("[TEST] Testing PhysicalRouteData persistence and chunk materialization progress... ");
		RoadNetworkSavedData data = new RoadNetworkSavedData();

		List<BlockPos> points = List.of(new BlockPos(10, 64, 10), new BlockPos(25, 64, 25));
		PhysicalRouteData physicalData = new PhysicalRouteData("route_A__B", points, List.of(new ChunkPos(0, 0)));
		data.savePhysicalRoute(physicalData);

		if (!data.isChunkMaterialized("route_A__B", new ChunkPos(0, 0))) {
			throw new AssertionError("Chunk (0,0) should be marked materialized");
		}
		if (data.isChunkMaterialized("route_A__B", new ChunkPos(1, 1))) {
			throw new AssertionError("Chunk (1,1) should NOT be marked materialized yet");
		}

		// Materialize chunk (1, 1)
		data.markChunkMaterialized("route_A__B", new ChunkPos(1, 1));
		if (!data.isChunkMaterialized("route_A__B", new ChunkPos(1, 1))) {
			throw new AssertionError("Chunk (1,1) should be marked materialized after update");
		}

		// Test elevation update helper
		NetworkNode testNode = new NetworkNode(
			"node_X", Identifier.fromNamespaceAndPath("minecraft", "village_plains"),
			StructureClassification.MAJOR, 100, 200, Optional.empty(), new ChunkPos(6, 12), DiscoveryStatus.CONFIRMED
		);
		data.addOrUpdateNode(testNode);
		if (data.getNode("node_X").get().hasKnownElevation()) {
			throw new AssertionError("Node should initially have unknown elevation");
		}
		data.updateNodeElevation("node_X", 72);
		if (!data.getNode("node_X").get().hasKnownElevation() || data.getNode("node_X").get().elevation().get() != 72) {
			throw new AssertionError("Node elevation failed to update to 72");
		}

		// Serialize to NBT and deserialize
		CompoundTag tag = data.serializeToNbt(null);
		RoadNetworkSavedData reloaded = RoadNetworkSavedData.deserializeFromNbt(tag, null);

		Optional<PhysicalRouteData> reloadedPr = reloaded.getPhysicalRoute("route_A__B");
		if (reloadedPr.isEmpty()) {
			throw new AssertionError("PhysicalRouteData not found after deserialization");
		}
		if (reloadedPr.get().getPathPoints().size() != 2) {
			throw new AssertionError("Physical points count mismatch: " + reloadedPr.get().getPathPoints().size());
		}
		if (!reloaded.isChunkMaterialized("route_A__B", new ChunkPos(0, 0)) || !reloaded.isChunkMaterialized("route_A__B", new ChunkPos(1, 1))) {
			throw new AssertionError("Materialized chunks not preserved after reload");
		}
		if (reloaded.isChunkMaterialized("route_A__B", new ChunkPos(2, 2))) {
			throw new AssertionError("Unmaterialized chunk marked materialized after reload");
		}

		System.out.println("OK");
	}

	public static void testRoadMaterialSelectorDeterminism() {
		System.out.print("[TEST] Testing RoadMaterialSelector deterministic spatial hashing... ");
		int hash1 = RoadMaterialSelector.getPosHash(100, 250);
		int hash2 = RoadMaterialSelector.getPosHash(100, 250);
		int hash3 = RoadMaterialSelector.getPosHash(101, 250);

		if (hash1 != hash2) {
			throw new AssertionError("Hash should be deterministic for identical coordinates");
		}
		if (hash1 == hash3) {
			throw new AssertionError("Adjacent coordinates should produce different hash values");
		}
		System.out.println("OK");
	}

	public static void testReplaceableGroundClassification() {
		System.out.print("[TEST] Testing RoadChunkMaterializer ground replacement safety... ");
		if (!RoadChunkMaterializer.isReplaceableGround(Blocks.DIRT.defaultBlockState())) {
			throw new AssertionError("Dirt must be replaceable");
		}
		if (!RoadChunkMaterializer.isReplaceableGround(Blocks.GRASS_BLOCK.defaultBlockState())) {
			throw new AssertionError("Grass block must be replaceable");
		}
		if (!RoadChunkMaterializer.isReplaceableGround(Blocks.SAND.defaultBlockState())) {
			throw new AssertionError("Sand must be replaceable");
		}
		if (!RoadChunkMaterializer.isReplaceableGround(Blocks.GRAVEL.defaultBlockState())) {
			throw new AssertionError("Gravel must be replaceable");
		}
		if (RoadChunkMaterializer.isReplaceableGround(Blocks.AIR.defaultBlockState())) {
			throw new AssertionError("Air must NOT be replaceable ground");
		}
		if (RoadChunkMaterializer.isReplaceableGround(Blocks.CHEST.defaultBlockState())) {
			throw new AssertionError("Chest must NEVER be replaceable");
		}
		if (RoadChunkMaterializer.isReplaceableGround(Blocks.DIAMOND_BLOCK.defaultBlockState())) {
			throw new AssertionError("Diamond block must NEVER be replaceable");
		}
		if (RoadChunkMaterializer.isReplaceableGround(Blocks.BEDROCK.defaultBlockState())) {
			throw new AssertionError("Bedrock must NEVER be replaceable");
		}
		System.out.println("OK");
	}

	public static void testConstructionDiagnosticsClassification() {
		System.out.print("[TEST] Testing construction diagnostics route classification... ");
		RoadNetworkSavedData data = new RoadNetworkSavedData();

		// Node A: Confirmed with known elevation
		NetworkNode nodeA = new NetworkNode("node_A", Identifier.fromNamespaceAndPath("minecraft", "village_plains"), StructureClassification.MAJOR, 0, 0, Optional.of(64), new ChunkPos(0, 0), DiscoveryStatus.CONFIRMED);
		// Node B: Confirmed with known elevation
		NetworkNode nodeB = new NetworkNode("node_B", Identifier.fromNamespaceAndPath("minecraft", "village_plains"), StructureClassification.MAJOR, 100, 0, Optional.of(68), new ChunkPos(6, 0), DiscoveryStatus.CONFIRMED);
		// Node C: Predicted
		NetworkNode nodeC = new NetworkNode("node_C", Identifier.fromNamespaceAndPath("minecraft", "village_plains"), StructureClassification.MAJOR, 200, 0, Optional.empty(), new ChunkPos(12, 0), DiscoveryStatus.PREDICTED);
		// Node D: Confirmed but unknown elevation
		NetworkNode nodeD = new NetworkNode("node_D", Identifier.fromNamespaceAndPath("minecraft", "village_plains"), StructureClassification.MAJOR, 300, 0, Optional.empty(), new ChunkPos(18, 0), DiscoveryStatus.CONFIRMED);

		data.addOrUpdateNode(nodeA);
		data.addOrUpdateNode(nodeB);
		data.addOrUpdateNode(nodeC);
		data.addOrUpdateNode(nodeD);

		NetworkRoute rReady = NetworkRoute.create("node_A", "node_B", StructureClassification.MAJOR, List.of(), 100.0, RouteStatus.PLANNED);
		NetworkRoute rPredicted = NetworkRoute.create("node_A", "node_C", StructureClassification.MAJOR, List.of(), 200.0, RouteStatus.PLANNED);
		NetworkRoute rUnknownElev = NetworkRoute.create("node_A", "node_D", StructureClassification.MAJOR, List.of(), 300.0, RouteStatus.PLANNED);

		data.addRoute(rReady);
		data.addRoute(rPredicted);
		data.addRoute(rUnknownElev);

		if (rReady.checkConstructionEligibility(nodeA, nodeB) != NetworkRoute.RouteConstructionEligibility.READY) {
			throw new AssertionError("Route A-B must be READY");
		}
		if (rPredicted.checkConstructionEligibility(nodeA, nodeC) != NetworkRoute.RouteConstructionEligibility.BLOCKED_PREDICTED_ENDPOINTS) {
			throw new AssertionError("Route A-C must be BLOCKED_PREDICTED_ENDPOINTS");
		}
		if (rUnknownElev.checkConstructionEligibility(nodeA, nodeD) != NetworkRoute.RouteConstructionEligibility.BLOCKED_UNKNOWN_ELEVATION) {
			throw new AssertionError("Route A-D must be BLOCKED_UNKNOWN_ELEVATION");
		}

		System.out.println("OK");
	}

	public static void testChunkLoadMaterializationAndIdempotency() {
		System.out.print("[TEST] Testing chunk-load materialization progress tracking & idempotency... ");
		RoadNetworkSavedData data = new RoadNetworkSavedData();

		List<BlockPos> points = List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(16, 64, 0),
			new BlockPos(32, 64, 0)
		);
		String routeId = "route_test_idempotent";
		PhysicalRouteData pr = new PhysicalRouteData(routeId, points);
		data.savePhysicalRoute(pr);

		ChunkPos chunk0 = new ChunkPos(0, 0);
		ChunkPos chunk1 = new ChunkPos(1, 0);

		// Initially not materialized
		if (data.isChunkMaterialized(routeId, chunk0) || data.isChunkMaterialized(routeId, chunk1)) {
			throw new AssertionError("Chunks should not be materialized initially");
		}

		// First materialization of chunk0 succeeds
		boolean marked0 = data.markChunkMaterialized(routeId, chunk0);
		if (!marked0 || !data.isChunkMaterialized(routeId, chunk0)) {
			throw new AssertionError("Chunk0 should be marked materialized");
		}

		// Repeated materialization returns false (idempotent, no double marking)
		boolean markedAgain = data.markChunkMaterialized(routeId, chunk0);
		if (markedAgain) {
			throw new AssertionError("Repeated marking of chunk0 should return false (idempotent)");
		}

		// Serialization / world restart preserves materialized chunks
		CompoundTag tag = data.serializeToNbt(null);
		RoadNetworkSavedData reloaded = RoadNetworkSavedData.deserializeFromNbt(tag, null);

		if (!reloaded.isChunkMaterialized(routeId, chunk0)) {
			throw new AssertionError("Chunk0 must remain marked materialized after reload");
		}
		if (reloaded.isChunkMaterialized(routeId, chunk1)) {
			throw new AssertionError("Chunk1 must NOT be marked materialized after reload");
		}

		System.out.println("OK");
	}

	public static void testDevTestRouteSafetyIsolation() {
		System.out.print("[TEST] Testing dev-test route isolation (production safety preserved)... ");
		RoadNetworkSavedData data = new RoadNetworkSavedData();

		// Production route with predicted endpoint
		NetworkNode nodeA = new NetworkNode("node_A", Identifier.fromNamespaceAndPath("minecraft", "village_plains"), StructureClassification.MAJOR, 0, 0, Optional.of(64), new ChunkPos(0, 0), DiscoveryStatus.CONFIRMED);
		NetworkNode nodeB = new NetworkNode("node_B", Identifier.fromNamespaceAndPath("minecraft", "village_plains"), StructureClassification.MAJOR, 100, 0, Optional.empty(), new ChunkPos(6, 0), DiscoveryStatus.PREDICTED);
		data.addOrUpdateNode(nodeA);
		data.addOrUpdateNode(nodeB);

		NetworkRoute prodRoute = NetworkRoute.create("node_A", "node_B", StructureClassification.MAJOR, List.of(), 100.0, RouteStatus.PLANNED);
		data.addRoute(prodRoute);

		// Dev test route
		String devRouteId = "route_dev_test_0_0__64_32";
		PhysicalRouteData devPr = new PhysicalRouteData(devRouteId, List.of(new BlockPos(0, 64, 0), new BlockPos(64, 64, 32)));
		data.savePhysicalRoute(devPr);

		// 1. Production route must STILL be strictly rejected
		if (prodRoute.checkConstructionEligibility(nodeA, nodeB) != NetworkRoute.RouteConstructionEligibility.BLOCKED_PREDICTED_ENDPOINTS) {
			throw new AssertionError("Dev test route registration must NOT bypass production route safety!");
		}

		// 2. Dev test route is tracked separately in physicalRoutes without polluting graph nodes/edges
		if (data.getNodeCount() != 2) {
			throw new AssertionError("Dev test route should not create graph nodes");
		}
		if (data.getEdgeCount() != 0) {
			throw new AssertionError("Dev test route should not create graph edges");
		}
		if (data.getRouteCount() != 1) {
			throw new AssertionError("Dev test route should not create network routes");
		}

		System.out.println("OK");
	}

	public static void testAutonomousRouteConsistencyFromBothDirections() {
		System.out.print("[TEST] Testing autonomous route consistency regardless of traversal direction... ");
		TerrainHeightSampler terrain = new TerrainHeightSampler() {
			@Override public int getSurfaceHeight(int x, int z) { return 64 + (int) (Math.sin(x * 0.05) * 5); }
			@Override public int getOceanFloorHeight(int x, int z) { return getSurfaceHeight(x, z); }
		};

		NetworkNode nodeA = new NetworkNode("node_A", Identifier.fromNamespaceAndPath("minecraft", "village_plains"), StructureClassification.MAJOR, 0, 0, Optional.of(64), new ChunkPos(0, 0), DiscoveryStatus.CONFIRMED);
		NetworkNode nodeB = new NetworkNode("node_B", Identifier.fromNamespaceAndPath("minecraft", "village_plains"), StructureClassification.MAJOR, 120, 60, Optional.of(66), new ChunkPos(7, 3), DiscoveryStatus.PREDICTED);

		// Discovery order 1: Discovered A, then B
		String routeIdAB = NetworkRoute.createRouteId(nodeA.id(), nodeB.id());
		// Discovery order 2: Discovered B, then A
		String routeIdBA = NetworkRoute.createRouteId(nodeB.id(), nodeA.id());

		if (!routeIdAB.equals(routeIdBA)) {
			throw new AssertionError("Route ID must be identical regardless of discovery order");
		}

		// Canonical route creation always assigns start as lexicographically smaller ID
		NetworkRoute route1 = NetworkRoute.create(nodeA.id(), nodeB.id(), StructureClassification.MAJOR, List.of(), 150.0, RouteStatus.PLANNED);
		NetworkRoute route2 = NetworkRoute.create(nodeB.id(), nodeA.id(), StructureClassification.MAJOR, List.of(), 150.0, RouteStatus.PLANNED);

		if (!route1.startNodeId().equals(route2.startNodeId()) || !route1.endNodeId().equals(route2.endNodeId())) {
			throw new AssertionError("Canonical start and end node assignment must match");
		}

		// Path calculated using canonical endpoints
		BlockPos start = new BlockPos(nodeA.x(), 64, nodeA.z());
		BlockPos end = new BlockPos(nodeB.x(), 66, nodeB.z());
		PhysicalPath path1 = TerrainPathfinder.findPath(start, end, terrain);
		PhysicalPath path2 = TerrainPathfinder.findPath(start, end, terrain);

		if (path1.size() != path2.size()) {
			throw new AssertionError("Path sizes must be identical");
		}
		for (int i = 0; i < path1.size(); i++) {
			if (!path1.getPoints().get(i).equals(path2.getPoints().get(i))) {
				throw new AssertionError("Path points differ at step " + i);
			}
		}

		System.out.println("OK");
	}

	public static void testAutonomousProgressiveChunkMaterialization() {
		System.out.print("[TEST] Testing autonomous progressive chunk materialization without loading distant endpoint... ");
		RoadNetworkSavedData data = new RoadNetworkSavedData();

		TerrainHeightSampler terrain = new TerrainHeightSampler() {
			@Override public int getSurfaceHeight(int x, int z) { return 64; }
			@Override public int getOceanFloorHeight(int x, int z) { return 64; }
		};

		// 1200-block long highway from (0, 0) to (1200, 0)
		BlockPos startPos = new BlockPos(0, 64, 0);
		BlockPos distantEndPos = new BlockPos(1200, 64, 0);

		PhysicalPath path = TerrainPathfinder.findPath(startPos, distantEndPos, terrain);
		String routeId = "route_villageA__villageB";
		PhysicalRouteData physicalData = new PhysicalRouteData(routeId, path.getPoints());
		data.savePhysicalRoute(physicalData);

		ChunkPos spawnChunk0 = new ChunkPos(0, 0);
		ChunkPos spawnChunk1 = new ChunkPos(1, 0);
		ChunkPos distantDestinationChunk = new ChunkPos(75, 0); // 1200 / 16 = 75

		// Distant chunk is NOT loaded and NOT materialized
		if (data.isChunkMaterialized(routeId, distantDestinationChunk)) {
			throw new AssertionError("Distant ungenerated chunk must not be materialized yet");
		}

		// Progressive materialization of local loaded chunks
		List<BlockPos> pts0 = path.getPointsForChunk(spawnChunk0);
		if (pts0.isEmpty()) {
			throw new AssertionError("Spawn chunk 0 should contain path points");
		}
		data.markChunkMaterialized(routeId, spawnChunk0);

		List<BlockPos> pts1 = path.getPointsForChunk(spawnChunk1);
		if (pts1.isEmpty()) {
			throw new AssertionError("Spawn chunk 1 should contain path points");
		}
		data.markChunkMaterialized(routeId, spawnChunk1);

		if (!data.isChunkMaterialized(routeId, spawnChunk0) || !data.isChunkMaterialized(routeId, spawnChunk1)) {
			throw new AssertionError("Local chunks should be marked materialized");
		}
		if (data.isChunkMaterialized(routeId, distantDestinationChunk)) {
			throw new AssertionError("Distant chunk must still remain unmaterialized");
		}

		System.out.println("OK");
	}

	public static void testAutonomousDestinationValidationAndConfirmation() {
		System.out.print("[TEST] Testing autonomous destination validation upon chunk generation... ");
		RoadNetworkSavedData data = new RoadNetworkSavedData();

		ChunkPos candidateChunk = new ChunkPos(10, 20);
		Identifier villageId = Identifier.fromNamespaceAndPath("minecraft", "village_plains");
		String nodeId = NetworkNode.createNodeId(villageId, candidateChunk);

		NetworkNode predictedNode = new NetworkNode(
			nodeId, villageId, StructureClassification.MAJOR,
			160, 320, Optional.empty(), candidateChunk, DiscoveryStatus.PREDICTED
		);
		data.addOrUpdateNode(predictedNode);

		if (data.getNode(nodeId).get().status() != DiscoveryStatus.PREDICTED) {
			throw new AssertionError("Initial node must be PREDICTED");
		}

		// Simulating chunk generation with valid StructureStart resolving real elevation Y=72
		NetworkNode confirmedNode = predictedNode.withConfirmation(168, 72, 328);
		data.addOrUpdateNode(confirmedNode);

		NetworkNode reloaded = data.getNode(nodeId).get();
		if (reloaded.status() != DiscoveryStatus.CONFIRMED) {
			throw new AssertionError("Node must be upgraded to CONFIRMED");
		}
		if (!reloaded.hasKnownElevation() || reloaded.elevation().get() != 72) {
			throw new AssertionError("Node elevation must be 72");
		}
		if (reloaded.x() != 168 || reloaded.z() != 328) {
			throw new AssertionError("Validated structure coordinates mismatch");
		}

		System.out.println("OK");
	}

	public static void testAutonomousDestinationInvalidationAndReconciliation() {
		System.out.print("[TEST] Testing autonomous destination invalidation and graph reconciliation... ");
		RoadNetworkSavedData data = new RoadNetworkSavedData();

		NetworkNode nodeA = new NetworkNode("node_A", Identifier.fromNamespaceAndPath("minecraft", "village_plains"), StructureClassification.MAJOR, 0, 0, Optional.of(64), new ChunkPos(0, 0), DiscoveryStatus.CONFIRMED);
		NetworkNode nodeB = new NetworkNode("node_B", Identifier.fromNamespaceAndPath("minecraft", "village_plains"), StructureClassification.MAJOR, 100, 0, Optional.empty(), new ChunkPos(6, 0), DiscoveryStatus.PREDICTED);

		data.addOrUpdateNode(nodeA);
		data.addOrUpdateNode(nodeB);

		// Plan initial connection
		NetworkPlanner.PlanningResult plan1 = NetworkPlanner.plan(data.getNodes(), data.getEdges(), data.getRoutes(), 2000.0);
		data.applyReconciliation(plan1);

		if (data.getRouteCount() != 1) {
			throw new AssertionError("Expected 1 planned route between A and B");
		}

		// When chunk (6, 0) loads, structure is absent -> invalidate candidate B
		data.invalidateNode("node_B");
		if (data.getNode("node_B").isPresent()) {
			throw new AssertionError("Node B must be removed after invalidation");
		}

		// Re-plan and reconcile graph
		NetworkPlanner.PlanningResult plan2 = NetworkPlanner.plan(data.getNodes(), data.getEdges(), data.getRoutes(), 2000.0);
		data.applyReconciliation(plan2);

		if (data.getRouteCount() != 0) {
			throw new AssertionError("Obsolete route to invalidated candidate B must be removed by reconciliation");
		}
		if (data.getEdgeCount() != 0) {
			throw new AssertionError("Obsolete edge must be removed by reconciliation");
		}

		System.out.println("OK");
	}

	public static void testVillagePerimeterDockingOffset() {
		System.out.print("[TEST] Testing village perimeter docking offset calculation... ");
		// Village at (100, 100), incoming highway coming from (0, 100)
		NetworkNode predictedVillage = new NetworkNode("node_V", Identifier.fromNamespaceAndPath("minecraft", "village_plains"), StructureClassification.MAJOR, 100, 100, Optional.empty(), new ChunkPos(6, 6), DiscoveryStatus.PREDICTED);

		BlockPos dockPredicted = VillagePerimeterCalculator.calculateDockingPoint(predictedVillage, 0, 100, null, 64);
		// Incoming direction is towards X=0 (dx = -100, dz = 0), so dirX = -1.0. Docking point should be 100 - 36 = 64
		if (dockPredicted.getX() != 64 || dockPredicted.getZ() != 100) {
			throw new AssertionError("Expected predicted dock point (64, 64, 100), got: " + dockPredicted);
		}

		// Confirmed village with BoundingBox [80, 60, 80 .. 120, 80, 120]
		BoundingBox box = new BoundingBox(80, 60, 80, 120, 80, 120);
		BlockPos dockConfirmed = VillagePerimeterCalculator.calculateDockingPoint(predictedVillage, 0, 100, box, 64);
		// Half-width is 20 + 2 margin = 22. Center is 100. Docking point should be 100 - 22 = 78
		if (dockConfirmed.getX() != 78 || dockConfirmed.getZ() != 100) {
			throw new AssertionError("Expected confirmed dock point (78, 64, 100), got: " + dockConfirmed);
		}

		System.out.println("OK");
	}

	public static void testScannedRegionPersistence() {
		System.out.print("[TEST] Testing autonomous scanned region persistence... ");
		RoadNetworkSavedData data = new RoadNetworkSavedData();

		ChunkPos region1 = new ChunkPos(2, 3);
		ChunkPos region2 = new ChunkPos(-1, 5);

		data.markRegionScanned(region1);
		data.markRegionScanned(region2);

		if (!data.isRegionScanned(region1) || !data.isRegionScanned(region2)) {
			throw new AssertionError("Regions must be marked scanned");
		}
		if (data.isRegionScanned(new ChunkPos(0, 0))) {
			throw new AssertionError("Unscanned region should return false");
		}

		CompoundTag tag = data.serializeToNbt(null);
		RoadNetworkSavedData reloaded = RoadNetworkSavedData.deserializeFromNbt(tag, null);

		if (!reloaded.isRegionScanned(region1) || !reloaded.isRegionScanned(region2)) {
			throw new AssertionError("Scanned regions must survive serialization/reload");
		}
		if (reloaded.isRegionScanned(new ChunkPos(0, 0))) {
			throw new AssertionError("Unscanned region must not be present after reload");
		}

		System.out.println("OK");
	}

	public static void testCatmullRomSplineCurvatureSmoothing() {
		System.out.print("[TEST] Testing RoadWeaver Catmull-Rom spline curve smoothing... ");
		TerrainHeightSampler flat = new TerrainHeightSampler() {
			@Override public int getSurfaceHeight(int x, int z) { return 64; }
			@Override public int getOceanFloorHeight(int x, int z) { return 64; }
		};

		// 4 waypoints forming an L-turn: (0,0) -> (100,0) -> (100,100) -> (200,100)
		List<BlockPos> waypoints = List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(100, 64, 0),
			new BlockPos(100, 64, 100),
			new BlockPos(200, 64, 100)
		);

		List<BlockPos> denseSpline = TerrainPathfinder.interpolateContiguousPath(waypoints, flat);

		if (denseSpline.size() < 100) {
			throw new AssertionError("Expected dense spline path, got size: " + denseSpline.size());
		}

		// Verify continuity: every consecutive pair must be adjacent (|dx| <= 1, |dz| <= 1)
		for (int i = 0; i < denseSpline.size() - 1; i++) {
			BlockPos a = denseSpline.get(i);
			BlockPos b = denseSpline.get(i + 1);
			int dx = Math.abs(b.getX() - a.getX());
			int dz = Math.abs(b.getZ() - a.getZ());
			if (dx > 1 || dz > 1) {
				throw new AssertionError("Gap found in Catmull-Rom spline at step " + i + ": " + a + " -> " + b);
			}
		}

		System.out.println("OK");
	}

	public static void testBridgeMetricsAndPillarEvaluation() {
		System.out.print("[TEST] Testing RoadWeaver bridge metrics and support pillar intervals... ");
		if (!com.trinankur.roadnetwork.bridge.BridgeGenerator.canBridge(64, 70, 30)) {
			throw new AssertionError("Expected canBridge to be true for delta=6 and dist=30");
		}
		if (com.trinankur.roadnetwork.bridge.BridgeGenerator.canBridge(64, 65, 30)) {
			throw new AssertionError("Expected canBridge to be false for shallow delta=1");
		}
		if (!com.trinankur.roadnetwork.bridge.BridgeGenerator.shouldPlacePillar(12, 16, 4)) {
			throw new AssertionError("Expected pillar at (12, 16) with interval 4");
		}
		if (com.trinankur.roadnetwork.bridge.BridgeGenerator.shouldPlacePillar(13, 16, 4)) {
			throw new AssertionError("Expected no pillar at (13, 16) with interval 4");
		}

		System.out.println("OK");
	}

	public static void testMountainTunnelMetrics() {
		System.out.print("[TEST] Testing RoadWeaver tunnel metrics and mountain barrier evaluation... ");
		if (!com.trinankur.roadnetwork.tunnel.TunnelGenerator.shouldTunnel(15, 40)) {
			throw new AssertionError("Expected shouldTunnel to be true for delta=15, length=40");
		}
		if (com.trinankur.roadnetwork.tunnel.TunnelGenerator.shouldTunnel(5, 40)) {
			throw new AssertionError("Expected shouldTunnel to be false for low elevation delta=5");
		}
		if (!com.trinankur.roadnetwork.tunnel.TunnelGenerator.isMountainBarrier(80, 65)) {
			throw new AssertionError("Expected isMountainBarrier to be true for surfaceY=80, roadY=65");
		}

		System.out.println("OK");
	}

	public static void testMaterializerSafetyAndFoliageCheck() {
		System.out.print("[TEST] Testing RoadChunkMaterializer safety rules and foliage predicates... ");
		if (!RoadChunkMaterializer.isFoliage(Blocks.SHORT_GRASS.defaultBlockState())) {
			throw new AssertionError("Expected SHORT_GRASS to be classified as foliage");
		}
		if (!RoadChunkMaterializer.isFoliage(Blocks.FERN.defaultBlockState())) {
			throw new AssertionError("Expected FERN to be classified as foliage");
		}
		if (RoadChunkMaterializer.isFoliage(Blocks.DIRT.defaultBlockState())) {
			throw new AssertionError("Expected DIRT not to be foliage");
		}
		if (RoadChunkMaterializer.isReplaceableGround(Blocks.AIR.defaultBlockState())) {
			throw new AssertionError("Air must never be replaceable ground");
		}
		if (!RoadChunkMaterializer.isReplaceableGround(Blocks.GRASS_BLOCK.defaultBlockState())) {
			throw new AssertionError("GRASS_BLOCK must be replaceable ground");
		}
		if (!RoadChunkMaterializer.isReplaceableGround(Blocks.STONE.defaultBlockState())) {
			throw new AssertionError("STONE must be replaceable ground");
		}
		if (RoadChunkMaterializer.isReplaceableGround(Blocks.CHEST.defaultBlockState())) {
			throw new AssertionError("CHEST must never be replaceable ground");
		}

		System.out.println("OK");
	}
}
