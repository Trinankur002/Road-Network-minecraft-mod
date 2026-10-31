package com.trinankur.roadnetwork.discovery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.OptionalInt;

/**
 * Represents a structure discovered or predicted in the world.
 * For predicted structures, the candidate X and Z coordinates are known,
 * while the vertical elevation (Y) is explicitly unknown until chunk generation.
 * For confirmed structures, the actual validated Y position is stored.
 */
public record DiscoveredStructure(
	Holder<Structure> structureHolder,
	Identifier id,
	int x,
	int z,
	OptionalInt knownY,
	ChunkPos chunkPos,
	double distance,
	StructureClassification classification,
	DiscoveryStatus status,
	String placementType
) implements Comparable<DiscoveredStructure> {

	/**
	 * Creates a predicted structure with candidate X and Z coordinates and unknown Y.
	 */
	public static DiscoveredStructure predicted(
		Holder<Structure> structureHolder,
		Identifier id,
		int candidateX,
		int candidateZ,
		ChunkPos chunkPos,
		double distance,
		StructureClassification classification,
		String placementType
	) {
		return new DiscoveredStructure(
			structureHolder,
			id,
			candidateX,
			candidateZ,
			OptionalInt.empty(),
			chunkPos,
			distance,
			classification,
			DiscoveryStatus.PREDICTED,
			placementType
		);
	}

	/**
	 * Creates a confirmed structure with validated X, Y, and Z coordinates.
	 */
	public static DiscoveredStructure confirmed(
		Holder<Structure> structureHolder,
		Identifier id,
		int validatedX,
		int validatedY,
		int validatedZ,
		ChunkPos chunkPos,
		double distance,
		StructureClassification classification,
		String placementType
	) {
		return new DiscoveredStructure(
			structureHolder,
			id,
			validatedX,
			validatedZ,
			OptionalInt.of(validatedY),
			chunkPos,
			distance,
			classification,
			DiscoveryStatus.CONFIRMED,
			placementType
		);
	}

	/**
	 * Creates a confirmed structure where vertical elevation (Y) may be unknown (e.g. from chunk storage).
	 */
	public static DiscoveredStructure confirmed(
		Holder<Structure> structureHolder,
		Identifier id,
		int validatedX,
		OptionalInt knownY,
		int validatedZ,
		ChunkPos chunkPos,
		double distance,
		StructureClassification classification,
		String placementType
	) {
		return new DiscoveredStructure(
			structureHolder,
			id,
			validatedX,
			validatedZ,
			knownY,
			chunkPos,
			distance,
			classification,
			DiscoveryStatus.CONFIRMED,
			placementType
		);
	}

	/**
	 * Returns true if the vertical elevation (Y) is validated and known.
	 */
	public boolean hasKnownY() {
		return this.knownY.isPresent();
	}

	/**
	 * Returns the confirmed Y coordinate, or the fallback Y if predicted/unknown.
	 */
	public int getOrFallbackY(int fallbackY) {
		return this.knownY.orElse(fallbackY);
	}

	/**
	 * Constructs a BlockPos using known Y if available, or the provided fallback Y.
	 */
	public BlockPos toBlockPos(int fallbackY) {
		return new BlockPos(this.x, this.getOrFallbackY(fallbackY), this.z);
	}

	/**
	 * Formats coordinates for user-facing chat display.
	 * E.g., "[X: 128, Y: 72, Z: -256]" if confirmed, or "[X: 128, Y: ~, Z: -256]" if predicted.
	 */
	public String formatCoordinates() {
		if (this.knownY.isPresent()) {
			return String.format("[X: %d, Y: %d, Z: %d]", this.x, this.knownY.getAsInt(), this.z);
		} else {
			return String.format("[X: %d, Y: ~, Z: %d]", this.x, this.z);
		}
	}

	@Override
	public int compareTo(DiscoveredStructure other) {
		return Double.compare(this.distance, other.distance);
	}
}
