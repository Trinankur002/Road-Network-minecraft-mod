package com.trinankur.roadnetwork.network.graph;

import com.trinankur.roadnetwork.discovery.DiscoveryStatus;
import com.trinankur.roadnetwork.network.node.NetworkNode;
import com.trinankur.roadnetwork.network.route.NetworkRoute;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory representation of the transportation network graph.
 * Enforces node identity, duplicate edge prevention, and status upgrade from predicted to confirmed.
 */
public class NetworkGraph {

	private final Map<String, NetworkNode> nodes = new ConcurrentHashMap<>();
	private final Map<String, NetworkEdge> edges = new ConcurrentHashMap<>();
	private final Map<String, NetworkRoute> routes = new ConcurrentHashMap<>();

	public NetworkGraph() {
	}

	/**
	 * Adds a node or updates an existing predicted node when confirmed.
	 * Returns true if a new node was inserted or an existing node was upgraded.
	 */
	public boolean addOrUpdateNode(NetworkNode node) {
		NetworkNode existing = this.nodes.get(node.id());
		if (existing == null) {
			this.nodes.put(node.id(), node);
			return true;
		}

		// Upgrade: if existing was predicted and incoming is confirmed, replace it
		if (existing.status() == DiscoveryStatus.PREDICTED && node.status() == DiscoveryStatus.CONFIRMED) {
			this.nodes.put(node.id(), node);
			return true;
		}

		// Prevent downgrade from confirmed to predicted
		return false;
	}

	/**
	 * Adds an edge to the graph, preventing duplicates and parallel connections.
	 */
	public boolean addEdge(NetworkEdge edge) {
		String key = edge.edgeKey();
		return this.edges.putIfAbsent(key, edge) == null;
	}

	/**
	 * Removes an edge from the graph by key.
	 */
	public boolean removeEdge(String edgeKey) {
		return this.edges.remove(edgeKey) != null;
	}

	/**
	 * Adds a route to the graph, preventing duplicates.
	 */
	public boolean addRoute(NetworkRoute route) {
		return this.routes.putIfAbsent(route.id(), route) == null;
	}

	/**
	 * Removes a route from the graph by route ID.
	 */
	public boolean removeRoute(String routeId) {
		return this.routes.remove(routeId) != null;
	}

	/**
	 * Applies an incremental planning reconciliation result.
	 * Removes obsolete edges/routes and adds newly planned edges/routes.
	 */
	public void applyReconciliation(com.trinankur.roadnetwork.network.planning.NetworkPlanner.PlanningResult result) {
		for (String edgeKey : result.removedEdgeKeys()) {
			this.edges.remove(edgeKey);
		}
		for (String routeId : result.removedRouteIds()) {
			this.routes.remove(routeId);
		}
		for (NetworkEdge edge : result.newEdges()) {
			this.edges.put(edge.edgeKey(), edge);
		}
		for (NetworkRoute route : result.newRoutes()) {
			this.routes.put(route.id(), route);
		}
	}

	public boolean hasEdge(String nodeA, String nodeB) {
		return this.edges.containsKey(NetworkEdge.makeKey(nodeA, nodeB));
	}

	public Optional<NetworkNode> getNode(String id) {
		return Optional.ofNullable(this.nodes.get(id));
	}

	public Optional<NetworkRoute> getRoute(String id) {
		return Optional.ofNullable(this.routes.get(id));
	}

	public Collection<NetworkNode> getNodes() {
		return Collections.unmodifiableCollection(this.nodes.values());
	}

	public Collection<NetworkEdge> getEdges() {
		return Collections.unmodifiableCollection(this.edges.values());
	}

	public Collection<NetworkRoute> getRoutes() {
		return Collections.unmodifiableCollection(this.routes.values());
	}

	public List<NetworkEdge> getEdgesForNode(String nodeId) {
		List<NetworkEdge> result = new ArrayList<>();
		for (NetworkEdge edge : this.edges.values()) {
			if (edge.connects(nodeId)) {
				result.add(edge);
			}
		}
		return result;
	}

	public int getNodeCount() {
		return this.nodes.size();
	}

	public int getEdgeCount() {
		return this.edges.size();
	}

	public int getRouteCount() {
		return this.routes.size();
	}

	public void clear() {
		this.nodes.clear();
		this.edges.clear();
		this.routes.clear();
	}
}

