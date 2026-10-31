package com.trinankur.roadnetwork.network.planning;

import com.trinankur.roadnetwork.discovery.StructureClassification;
import com.trinankur.roadnetwork.network.graph.NetworkEdge;
import com.trinankur.roadnetwork.network.node.NetworkNode;
import com.trinankur.roadnetwork.network.route.NetworkRoute;
import com.trinankur.roadnetwork.network.route.NetworkWaypoint;
import com.trinankur.roadnetwork.network.route.RouteStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic transportation network planning and reconciliation algorithm.
 * Connects major settlements using a combined Minimum Spanning Forest and Relative Neighborhood Graph (RNG) approach.
 *
 * Supports safe incremental reconciliation:
 * - Computes ideal network topology deterministically.
 * - Reconciles obsolete automatically generated edges when intermediate settlements are discovered.
 * - Preserves manually protected, surveyed, or completed routes.
 * - Guarantees idempotent execution when repeated without changes.
 */
public final class NetworkPlanner {

	public static final double DEFAULT_MAX_CONNECTION_DISTANCE = 2000.0; // 2000 blocks (~125 chunks)
	private static final double WAYPOINT_INTERVAL = 256.0; // Interpolation interval for intermediate waypoints

	private NetworkPlanner() {
	}

	public record PlanningResult(
		List<NetworkEdge> newEdges,
		List<NetworkRoute> newRoutes,
		List<String> removedEdgeKeys,
		List<String> removedRouteIds,
		int nodesEvaluated
	) {
		public boolean hasNewConnections() {
			return !this.newEdges.isEmpty();
		}

		public boolean hasRemovedConnections() {
			return !this.removedEdgeKeys.isEmpty();
		}

		public boolean hasChanges() {
			return !this.newEdges.isEmpty() || !this.removedEdgeKeys.isEmpty();
		}
	}

	private record CandidateEdge(
		NetworkNode nodeA,
		NetworkNode nodeB,
		double distance
	) {
		public String edgeKey() {
			return NetworkEdge.makeKey(nodeA.id(), nodeB.id());
		}
	}

	/**
	 * Plans routes between eligible major settlements with automatic graph reconciliation.
	 */
	public static PlanningResult plan(
		Collection<NetworkNode> allNodes,
		Collection<NetworkEdge> existingEdges,
		double maxConnectionDistanceBlocks
	) {
		return plan(allNodes, existingEdges, Collections.emptyList(), maxConnectionDistanceBlocks);
	}

	/**
	 * Plans routes between eligible major settlements with incremental reconciliation and route protection.
	 */
	public static PlanningResult plan(
		Collection<NetworkNode> allNodes,
		Collection<NetworkEdge> existingEdges,
		Collection<NetworkRoute> existingRoutes,
		double maxConnectionDistanceBlocks
	) {
		// 1. Separate eligible nodes into Major Settlements (villages) and Secondary Structures (outposts, temples, etc.)
		List<NetworkNode> majorNodes = new ArrayList<>();
		List<NetworkNode> secondaryNodes = new ArrayList<>();
		for (NetworkNode node : allNodes) {
			if (node.classification() == StructureClassification.MAJOR) {
				majorNodes.add(node);
			} else if (node.classification() == StructureClassification.SECONDARY) {
				secondaryNodes.add(node);
			}
		}

		// 2. Sort eligible nodes by ID to eliminate any dependence on discovery order or map iteration order
		majorNodes.sort(Comparator.comparing(NetworkNode::id));
		secondaryNodes.sort(Comparator.comparing(NetworkNode::id));

		int totalEligible = majorNodes.size() + secondaryNodes.size();

		if (totalEligible < 2) {
			// If fewer than 2 settlements exist, any existing PLANNED edges become obsolete
			List<String> removedEdges = new ArrayList<>();
			List<String> removedRoutes = new ArrayList<>();
			if (existingEdges != null) {
				Map<String, RouteStatus> routeStatusMap = getRouteStatusMap(existingRoutes);
				for (NetworkEdge edge : existingEdges) {
					RouteStatus st = routeStatusMap.getOrDefault(edge.routeId(), RouteStatus.PLANNED);
					if (st == RouteStatus.PLANNED) {
						removedEdges.add(edge.edgeKey());
						removedRoutes.add(edge.routeId());
					}
				}
			}
			return new PlanningResult(Collections.emptyList(), Collections.emptyList(), removedEdges, removedRoutes, totalEligible);
		}

		// 3. Map existing edges and identify protected (non-PLANNED) connections
		Map<String, NetworkEdge> existingEdgeMap = new HashMap<>();
		Map<String, RouteStatus> routeStatusMap = getRouteStatusMap(existingRoutes);
		Set<String> protectedEdgeKeys = new HashSet<>();

		if (existingEdges != null) {
			for (NetworkEdge edge : existingEdges) {
				existingEdgeMap.put(edge.edgeKey(), edge);
				RouteStatus status = routeStatusMap.getOrDefault(edge.routeId(), RouteStatus.PLANNED);
				if (status != RouteStatus.PLANNED) {
					// Route is surveyed, in-progress, or completed; lock it against automatic pruning
					protectedEdgeKeys.add(edge.edgeKey());
				}
			}
		}

		// 4. Generate all candidate pairs among major settlements within maxConnectionDistanceBlocks
		List<CandidateEdge> candidates = new ArrayList<>();
		int nMajor = majorNodes.size();
		for (int i = 0; i < nMajor; i++) {
			NetworkNode u = majorNodes.get(i);
			for (int j = i + 1; j < nMajor; j++) {
				NetworkNode v = majorNodes.get(j);
				double dist = u.horizontalDistanceTo(v);
				if (dist <= maxConnectionDistanceBlocks) {
					candidates.add(new CandidateEdge(u, v, dist));
				}
			}
		}

		// If no major nodes exist but multiple secondary nodes exist, allow secondary interconnections
		if (nMajor == 0 && secondaryNodes.size() >= 2) {
			for (int i = 0; i < secondaryNodes.size(); i++) {
				NetworkNode u = secondaryNodes.get(i);
				for (int j = i + 1; j < secondaryNodes.size(); j++) {
					NetworkNode v = secondaryNodes.get(j);
					double dist = u.horizontalDistanceTo(v);
					if (dist <= maxConnectionDistanceBlocks) {
						candidates.add(new CandidateEdge(u, v, dist));
					}
				}
			}
		}

		// 5. Deterministic candidate sorting: distance ascending, then node IDs lexicographically
		candidates.sort((c1, c2) -> {
			int cmp = Double.compare(c1.distance, c2.distance);
			if (cmp != 0) return cmp;
			int cmpA = c1.nodeA.id().compareTo(c2.nodeA.id());
			if (cmpA != 0) return cmpA;
			return c1.nodeB.id().compareTo(c2.nodeB.id());
		});

		// 6. Compute ideal topology:
		// Phase A: Seed DSU with protected edges first
		DisjointSet dsu = new DisjointSet();
		for (NetworkNode node : majorNodes) {
			dsu.makeSet(node.id());
		}
		for (NetworkNode node : secondaryNodes) {
			dsu.makeSet(node.id());
		}

		Map<String, CandidateEdge> idealEdgeMap = new HashMap<>();

		for (CandidateEdge candidate : candidates) {
			if (protectedEdgeKeys.contains(candidate.edgeKey())) {
				dsu.union(candidate.nodeA.id(), candidate.nodeB.id());
				idealEdgeMap.put(candidate.edgeKey(), candidate);
			}
		}

		// Phase B: Kruskal's Minimum Spanning Forest
		for (CandidateEdge candidate : candidates) {
			String key = candidate.edgeKey();
			if (!idealEdgeMap.containsKey(key)) {
				if (dsu.union(candidate.nodeA.id(), candidate.nodeB.id())) {
					idealEdgeMap.put(key, candidate);
				}
			}
		}

		// Phase C: Relative Neighborhood Graph (RNG) regional connections among major nodes
		for (CandidateEdge candidate : candidates) {
			String key = candidate.edgeKey();
			if (idealEdgeMap.containsKey(key)) {
				continue;
			}

			NetworkNode u = candidate.nodeA;
			NetworkNode v = candidate.nodeB;
			double dUV = candidate.distance;

			boolean luneEmpty = true;
			for (NetworkNode w : majorNodes) {
				if (w.id().equals(u.id()) || w.id().equals(v.id())) {
					continue;
				}
				double dUW = u.horizontalDistanceTo(w);
				double dVW = v.horizontalDistanceTo(w);
				if (Math.max(dUW, dVW) < dUV) {
					// Settlement w is closer to both u and v than dist(u, v); prefer routing via w
					luneEmpty = false;
					break;
				}
			}

			if (luneEmpty) {
				idealEdgeMap.put(key, candidate);
			}
		}

		// Phase D: Connect Secondary Structures (Outposts, Temples, Mansions, Pyramids, Igloos, Ruins)
		// Connects each secondary structure to its nearest major settlement (or nearest secondary node)
		for (NetworkNode s : secondaryNodes) {
			NetworkNode bestTarget = null;
			double bestDist = Double.MAX_VALUE;

			// Prefer connecting to nearest major settlement
			for (NetworkNode m : majorNodes) {
				double dist = s.horizontalDistanceTo(m);
				if (dist <= maxConnectionDistanceBlocks && dist < bestDist) {
					bestDist = dist;
					bestTarget = m;
				}
			}

			// If no major node is within range, connect to nearest secondary node
			if (bestTarget == null) {
				for (NetworkNode other : secondaryNodes) {
					if (other.id().equals(s.id())) continue;
					double dist = s.horizontalDistanceTo(other);
					if (dist <= maxConnectionDistanceBlocks && dist < bestDist) {
						bestDist = dist;
						bestTarget = other;
					}
				}
			}

			if (bestTarget != null) {
				CandidateEdge secEdge = new CandidateEdge(s, bestTarget, bestDist);
				if (!idealEdgeMap.containsKey(secEdge.edgeKey())) {
					idealEdgeMap.put(secEdge.edgeKey(), secEdge);
					dsu.union(s.id(), bestTarget.id());
				}
			}
		}

		// 7. Reconcile existing edges against ideal topology:
		// Detect obsolete PLANNED edges that are no longer part of the ideal topology
		List<String> removedEdgeKeys = new ArrayList<>();
		List<String> removedRouteIds = new ArrayList<>();

		for (Map.Entry<String, NetworkEdge> entry : existingEdgeMap.entrySet()) {
			String key = entry.getKey();
			NetworkEdge existingEdge = entry.getValue();

			if (!idealEdgeMap.containsKey(key)) {
				if (!protectedEdgeKeys.contains(key)) {
					// Obsolete automatically planned edge
					removedEdgeKeys.add(key);
					removedRouteIds.add(existingEdge.routeId());
				}
			}
		}

		// Detect new ideal edges that do not yet exist
		List<NetworkEdge> newEdges = new ArrayList<>();
		List<NetworkRoute> newRoutes = new ArrayList<>();

		for (CandidateEdge candidate : idealEdgeMap.values()) {
			String key = candidate.edgeKey();
			if (!existingEdgeMap.containsKey(key)) {
				String routeId = NetworkRoute.createRouteId(candidate.nodeA.id(), candidate.nodeB.id());
				NetworkEdge edge = NetworkEdge.create(candidate.nodeA.id(), candidate.nodeB.id(), candidate.distance, routeId);

				List<NetworkWaypoint> waypoints = generateWaypoints(candidate.nodeA, candidate.nodeB, candidate.distance);
				StructureClassification routeClass = (candidate.nodeA.classification() == StructureClassification.MAJOR && candidate.nodeB.classification() == StructureClassification.MAJOR)
					? StructureClassification.MAJOR
					: StructureClassification.SECONDARY;

				NetworkRoute route = NetworkRoute.create(
					candidate.nodeA.id(),
					candidate.nodeB.id(),
					routeClass,
					waypoints,
					candidate.distance,
					RouteStatus.PLANNED
				);

				newEdges.add(edge);
				newRoutes.add(route);
			}
		}

		// Sort results deterministically
		removedEdgeKeys.sort(String::compareTo);
		removedRouteIds.sort(String::compareTo);
		newEdges.sort(Comparator.comparing(NetworkEdge::edgeKey));
		newRoutes.sort(Comparator.comparing(NetworkRoute::id));

		return new PlanningResult(newEdges, newRoutes, removedEdgeKeys, removedRouteIds, totalEligible);
	}

	/**
	 * Generates ordered waypoints between start and end nodes.
	 * Includes start and end points and linearly interpolated intermediate waypoints.
	 * Intermediate waypoints have unknown Y (no fake elevations).
	 */
	public static List<NetworkWaypoint> generateWaypoints(NetworkNode start, NetworkNode end, double distance) {
		List<NetworkWaypoint> waypoints = new ArrayList<>();

		// 1. Start waypoint
		waypoints.add(NetworkWaypoint.of(start.x(), start.elevation(), start.z()));

		// 2. Intermediate waypoints if distance is sufficiently large
		if (distance > WAYPOINT_INTERVAL) {
			int segments = Math.max(2, (int) Math.round(distance / WAYPOINT_INTERVAL));
			for (int i = 1; i < segments; i++) {
				double t = (double) i / segments;
				int ix = (int) Math.round(start.x() + t * (end.x() - start.x()));
				int iz = (int) Math.round(start.z() + t * (end.z() - start.z()));
				// Intermediate waypoints have unknown Y elevation until future terrain analysis
				waypoints.add(NetworkWaypoint.of(ix, iz));
			}
		}

		// 3. End waypoint
		waypoints.add(NetworkWaypoint.of(end.x(), end.elevation(), end.z()));

		return waypoints;
	}

	private static Map<String, RouteStatus> getRouteStatusMap(Collection<NetworkRoute> routes) {
		Map<String, RouteStatus> map = new HashMap<>();
		if (routes != null) {
			for (NetworkRoute r : routes) {
				map.put(r.id(), r.status());
			}
		}
		return map;
	}

	/**
	 * Simple disjoint-set data structure for Kruskal's spanning forest.
	 */
	private static class DisjointSet {
		private final Map<String, String> parent = new HashMap<>();

		public void makeSet(String item) {
			parent.putIfAbsent(item, item);
		}

		public String find(String item) {
			String p = parent.get(item);
			if (p == null) {
				parent.put(item, item);
				return item;
			}
			if (!p.equals(item)) {
				p = find(p);
				parent.put(item, p);
			}
			return p;
		}

		public boolean union(String a, String b) {
			String rootA = find(a);
			String rootB = find(b);
			if (rootA.equals(rootB)) {
				return false;
			}
			parent.put(rootA, rootB);
			return true;
		}
	}
}
