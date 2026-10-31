package com.trinankur.roadnetwork.tunnel;

/**
 * RoadWeaver-style tunnel generation and mountain penetration metrics.
 * Evaluates steep terrain barriers to carve stone-arch tunnels.
 */
public final class TunnelGenerator {

	public static final int TUNNEL_CLEARANCE_HEIGHT = 3;
	public static final int TUNNEL_WALL_THICKNESS = 1;

	private TunnelGenerator() {}

	public static boolean shouldTunnel(int elevationDelta, double length) {
		return elevationDelta > 10 && length > 30;
	}

	public static boolean isMountainBarrier(int surfaceY, int roadY) {
		return (surfaceY - roadY) >= 5;
	}
}
