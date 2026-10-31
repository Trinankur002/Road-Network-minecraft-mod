package com.trinankur.roadnetwork.network.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.trinankur.roadnetwork.discovery.DiscoveredStructure;
import com.trinankur.roadnetwork.discovery.DiscoveryStatus;
import com.trinankur.roadnetwork.discovery.StructureClassification;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Represents a persistent structure node in the road network graph.
 * Unique deterministic node IDs are derived from the structure identifier and candidate chunk position.
 * Supports updating a predicted node when its structure becomes confirmed with validated coordinates.
 */
public record NetworkNode(
	String id,
	Identifier structureId,
	StructureClassification classification,
	int x,
	int z,
	Optional<Integer> elevation,
	ChunkPos chunkPos,
	DiscoveryStatus status
) {

	public static final Codec<NetworkNode> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
			Codec.STRING.fieldOf("id").forGetter(NetworkNode::id),
			Identifier.CODEC.fieldOf("structure_id").forGetter(NetworkNode::structureId),
			StructureClassification.CODEC.fieldOf("classification").forGetter(NetworkNode::classification),
			Codec.INT.fieldOf("x").forGetter(NetworkNode::x),
			Codec.INT.fieldOf("z").forGetter(NetworkNode::z),
			Codec.INT.optionalFieldOf("elevation").forGetter(NetworkNode::elevation),
			ChunkPos.CODEC.fieldOf("chunk_pos").forGetter(NetworkNode::chunkPos),
			DiscoveryStatus.CODEC.fieldOf("status").forGetter(NetworkNode::status)
		).apply(instance, NetworkNode::new)
	);

	/**
	 * Generates a deterministic unique ID for a structure node at a specific chunk position.
	 */
	public static String createNodeId(Identifier structureId, ChunkPos chunkPos) {
		return "node_" + structureId.getNamespace() + "_" + structureId.getPath() + "_" + chunkPos.x() + "_" + chunkPos.z();
	}

	/**
	 * Creates a NetworkNode from a discovered structure.
	 */
	public static NetworkNode fromDiscoveredStructure(DiscoveredStructure structure) {
		String id = createNodeId(structure.id(), structure.chunkPos());
		Optional<Integer> elev = structure.knownY().isPresent() ? Optional.of(structure.knownY().getAsInt()) : Optional.empty();
		return new NetworkNode(
			id,
			structure.id(),
			structure.classification(),
			structure.x(),
			structure.z(),
			elev,
			structure.chunkPos(),
			structure.status()
		);
	}

	public boolean hasKnownElevation() {
		return this.elevation.isPresent();
	}

	public OptionalInt getKnownElevation() {
		return this.elevation.map(OptionalInt::of).orElseGet(OptionalInt::empty);
	}

	public double horizontalDistanceTo(NetworkNode other) {
		return horizontalDistanceTo(other.x, other.z);
	}

	public double horizontalDistanceTo(int targetX, int targetZ) {
		double dx = this.x - targetX;
		double dz = this.z - targetZ;
		return Math.sqrt(dx * dx + dz * dz);
	}

	public String formatCoordinates() {
		if (this.elevation.isPresent()) {
			return String.format("[X: %d, Y: %d, Z: %d]", this.x, this.elevation.get(), this.z);
		} else {
			return String.format("[X: %d, Y: ~, Z: %d]", this.x, this.z);
		}
	}

	/**
	 * Determines whether this node has been confirmed in the world with validated coordinates
	 * and a verified 3D bounding box elevation, making it safe for physical road endpoint integration.
	 * Predicted nodes or confirmed nodes with unloaded elevations return false to prevent destructive
	 * terrain modification or orphaned roads in ungenerated chunks.
	 */
	public boolean isEligibleForPhysicalConstruction() {
		return this.status == DiscoveryStatus.CONFIRMED && this.elevation.isPresent();
	}

	/**
	 * Authorizes physical road generation to hook into this structure destination.
	 */
	public boolean canAuthorizeEndpointGeneration() {
		return isEligibleForPhysicalConstruction();
	}

	/**
	 * Creates a copy of this node upgraded to confirmed status with validated coordinates and elevation.
	 */
	public NetworkNode withConfirmation(int validatedX, int validatedY, int validatedZ) {
		return new NetworkNode(
			this.id,
			this.structureId,
			this.classification,
			validatedX,
			validatedZ,
			Optional.of(validatedY),
			this.chunkPos,
			DiscoveryStatus.CONFIRMED
		);
	}
}

