package com.trinankur.roadnetwork.network.route;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Represents a single waypoint along a planned road route.
 * Waypoints store horizontal coordinates and optional vertical elevation.
 * Predicted routes do not assign fake elevations.
 */
public record NetworkWaypoint(
	int x,
	Optional<Integer> y,
	int z
) {

	public static final Codec<NetworkWaypoint> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
			Codec.INT.fieldOf("x").forGetter(NetworkWaypoint::x),
			Codec.INT.optionalFieldOf("y").forGetter(NetworkWaypoint::y),
			Codec.INT.fieldOf("z").forGetter(NetworkWaypoint::z)
		).apply(instance, NetworkWaypoint::new)
	);

	public static NetworkWaypoint of(int x, int z) {
		return new NetworkWaypoint(x, Optional.empty(), z);
	}

	public static NetworkWaypoint of(int x, int y, int z) {
		return new NetworkWaypoint(x, Optional.of(y), z);
	}

	public static NetworkWaypoint of(int x, Optional<Integer> y, int z) {
		return new NetworkWaypoint(x, y, z);
	}

	public static NetworkWaypoint of(int x, OptionalInt y, int z) {
		return new NetworkWaypoint(x, y.isPresent() ? Optional.of(y.getAsInt()) : Optional.empty(), z);
	}

	public boolean hasKnownY() {
		return this.y.isPresent();
	}

	public OptionalInt getYAsOptionalInt() {
		return this.y.map(OptionalInt::of).orElseGet(OptionalInt::empty);
	}

	public String formatCoordinates() {
		if (this.y.isPresent()) {
			return String.format("[X: %d, Y: %d, Z: %d]", this.x, this.y.get(), this.z);
		} else {
			return String.format("[X: %d, Y: ~, Z: %d]", this.x, this.z);
		}
	}
}

