package com.trinankur.roadnetwork.network.route;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.trinankur.roadnetwork.discovery.DiscoveryStatus;
import com.trinankur.roadnetwork.discovery.StructureClassification;
import com.trinankur.roadnetwork.generation.RoadStyle;
import com.trinankur.roadnetwork.network.node.NetworkNode;

import java.util.List;

/**
 * Represents a planned or surveyed route connecting two structure nodes in the network graph.
 * Identifiers are deterministic based on endpoint node IDs.
 */
public record NetworkRoute(
		String id,
		String startNodeId,
		String endNodeId,
		StructureClassification classification,
		List<NetworkWaypoint> waypoints,
		double length,
		RouteStatus status,
		RoadStyle.RoadClass roadClass
) {

		public static final Codec<NetworkRoute> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
				Codec.STRING.fieldOf("id").forGetter(NetworkRoute::id),
				Codec.STRING.fieldOf("start_node").forGetter(NetworkRoute::startNodeId),
				Codec.STRING.fieldOf("end_node").forGetter(NetworkRoute::endNodeId),
				StructureClassification.CODEC.fieldOf("classification").forGetter(NetworkRoute::classification),
				Codec.list(NetworkWaypoint.CODEC).fieldOf("waypoints").forGetter(NetworkRoute::waypoints),
				Codec.DOUBLE.fieldOf("length").forGetter(NetworkRoute::length),
				RouteStatus.CODEC.fieldOf("status").forGetter(NetworkRoute::status),
				RoadStyle.RoadClass.CODEC.optionalFieldOf("road_class", RoadStyle.RoadClass.VILLAGE_ROAD).forGetter(NetworkRoute::roadClass)
			).apply(instance, NetworkRoute::new)
		);

		/**
		 * Generates a deterministic route ID from two endpoint node IDs.
		 * Sorts the endpoint IDs lexicographically to guarantee the same route ID
		 * regardless of traversal direction.
		 */
		public static String createRouteId(String nodeA, String nodeB) {
			if (nodeA.compareTo(nodeB) <= 0) {
				return "route_" + nodeA + "__" + nodeB;
			} else {
				return "route_" + nodeB + "__" + nodeA;
			}
		}

		public static NetworkRoute create(
			String nodeA,
			String nodeB,
			StructureClassification classification,
			List<NetworkWaypoint> waypoints,
			double length,
			RouteStatus status,
			RoadStyle.RoadClass roadClass
		) {
			String id = createRouteId(nodeA, nodeB);
			String start = nodeA.compareTo(nodeB) <= 0 ? nodeA : nodeB;
			String end = nodeA.compareTo(nodeB) <= 0 ? nodeB : nodeA;
			return new NetworkRoute(id, start, end, classification, List.copyOf(waypoints), length, status, roadClass);
		}

		/**
		 * Creates a route with default road class derived from classification and length.
		 */
		public static NetworkRoute create(
			String nodeA,
			String nodeB,
			StructureClassification classification,
			List<NetworkWaypoint> waypoints,
			double length,
			RouteStatus status
		) {
			return create(nodeA, nodeB, classification, waypoints, length, status, RoadStyle.RoadClass.fromClassification(classification, length));
		}

		public boolean connects(String nodeId) {
			return this.startNodeId.equals(nodeId) || this.endNodeId.equals(nodeId);
		}

		public String getOtherNode(String nodeId) {
			if (this.startNodeId.equals(nodeId)) return this.endNodeId;
			if (this.endNodeId.equals(nodeId)) return this.startNodeId;
			return null;
		}

		/**
		 * Status determining whether physical road block placement and terrain alteration can proceed.
		 */
		public enum RouteConstructionEligibility {
			READY("Ready for physical road construction"),
			BLOCKED_PREDICTED_ENDPOINTS("Blocked: One or both destination structures are predicted candidates"),
			BLOCKED_UNKNOWN_ELEVATION("Blocked: Destination structure is confirmed on disk but elevation is unloaded"),
			INVALID_ENDPOINTS("Blocked: Endpoint structures not found in network graph");

			private final String description;

			RouteConstructionEligibility(String description) {
				this.description = description;
			}

			public String getDescription() {
				return this.description;
			}

			public boolean isReady() {
				return this == READY;
			}
		}

		/**
		 * Validates endpoints to ensure physical road generation cannot build toward an unconfirmed
		 * predicted candidate or an unloaded elevation, preventing orphaned roads or destruction.
		 */
		public RouteConstructionEligibility checkConstructionEligibility(NetworkNode startNode, NetworkNode endNode) {
			if (startNode == null || endNode == null) {
				return RouteConstructionEligibility.INVALID_ENDPOINTS;
			}
			if (startNode.status() != DiscoveryStatus.CONFIRMED || endNode.status() != DiscoveryStatus.CONFIRMED) {
				return RouteConstructionEligibility.BLOCKED_PREDICTED_ENDPOINTS;
			}
			if (!startNode.hasKnownElevation() || !endNode.hasKnownElevation()) {
				return RouteConstructionEligibility.BLOCKED_UNKNOWN_ELEVATION;
			}
			return RouteConstructionEligibility.READY;
		}

		public boolean isEligibleForPhysicalConstruction(NetworkNode startNode, NetworkNode endNode) {
			return checkConstructionEligibility(startNode, endNode) == RouteConstructionEligibility.READY;
		}
}