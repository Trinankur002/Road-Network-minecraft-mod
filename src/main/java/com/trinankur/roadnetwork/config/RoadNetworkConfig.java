package com.trinankur.roadnetwork.config;

import com.trinankur.roadnetwork.generation.RoadStyle;

/**
 * Configurable profile settings for road infrastructure.
 * Integrates with existing RoadStyle.StyleProfile and RoadClass.
 */
public record RoadNetworkConfig(
	boolean enableLighting,
	boolean enableBridges,
	boolean enableTunnels,
	RoadStyle.StyleProfile defaultProfile
) {
	public RoadNetworkConfig() {
		this(true, true, false, RoadStyle.StyleProfile.NATURAL);
	}
}
