package com.trinankur.roadnetwork.network.route;

import com.mojang.serialization.Codec;

/**
 * Lifecycle status of a planned road route in the network.
 */
public enum RouteStatus {
	PLANNED("Planned"),
	SURVEYED("Surveyed"),
	IN_PROGRESS("In Progress"),
	COMPLETED("Completed");

	public static final Codec<RouteStatus> CODEC = Codec.STRING.xmap(
		RouteStatus::valueOf,
		RouteStatus::name
	);

	private final String displayName;

	RouteStatus(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return this.displayName;
	}
}

