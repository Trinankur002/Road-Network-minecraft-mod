package com.trinankur.roadnetwork.bridge;

/**
 * RoadWeaver-style bridge evaluation and infrastructure metrics.
 * Evaluates water span, riverbed depth, and bridge deck elevation.
 */
public final class BridgeGenerator {

	public static final int DEFAULT_PILLAR_INTERVAL = 4;

	private BridgeGenerator() {}

	public static boolean canBridge(int startY, int endY, double distance) {
		return Math.abs(endY - startY) > 3 && distance < 50;
	}

	public static boolean isWaterSpan(int waterDepth) {
		return waterDepth >= 1;
	}

	public static boolean shouldPlacePillar(int x, int z, int interval) {
		return Math.floorMod(x + z, interval) == 0;
	}
}
