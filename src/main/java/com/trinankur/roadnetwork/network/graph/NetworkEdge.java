package com.trinankur.roadnetwork.network.graph;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Represents an undirected connection between two structure nodes in the network graph.
 * Endpoints are normalized lexicographically so that (A, B) and (B, A) are treated as identical.
 */
public record NetworkEdge(
	String nodeA,
	String nodeB,
	double distance,
	String routeId
) {

	public static final Codec<NetworkEdge> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
			Codec.STRING.fieldOf("node_a").forGetter(NetworkEdge::nodeA),
			Codec.STRING.fieldOf("node_b").forGetter(NetworkEdge::nodeB),
			Codec.DOUBLE.fieldOf("distance").forGetter(NetworkEdge::distance),
			Codec.STRING.fieldOf("route_id").forGetter(NetworkEdge::routeId)
		).apply(instance, NetworkEdge::new)
	);

	/**
	 * Factory that normalizes endpoint node IDs to prevent duplicate reverse edges.
	 */
	public static NetworkEdge create(String id1, String id2, double distance, String routeId) {
		if (id1.compareTo(id2) <= 0) {
			return new NetworkEdge(id1, id2, distance, routeId);
		} else {
			return new NetworkEdge(id2, id1, distance, routeId);
		}
	}

	public String edgeKey() {
		return makeKey(this.nodeA, this.nodeB);
	}

	public static String makeKey(String id1, String id2) {
		return id1.compareTo(id2) <= 0 ? id1 + "---" + id2 : id2 + "---" + id1;
	}

	public boolean connects(String nodeId) {
		return this.nodeA.equals(nodeId) || this.nodeB.equals(nodeId);
	}

	public String getOtherNode(String nodeId) {
		if (this.nodeA.equals(nodeId)) return this.nodeB;
		if (this.nodeB.equals(nodeId)) return this.nodeA;
		return null;
	}
}

