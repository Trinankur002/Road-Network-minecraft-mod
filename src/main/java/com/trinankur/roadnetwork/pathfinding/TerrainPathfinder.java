package com.trinankur.roadnetwork.pathfinding;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Deterministic A* terrain-aware pathfinder.
 * Evaluates elevation delta, slope penalties, water crossing penalties, and curvature smoothing
 * to generate natural, winding roads along valleys and gentle slopes while avoiding steep cliffs and deep water.
 */
public final class TerrainPathfinder {

	private static final int STEP_SIZE = 8; // 8-block coarse grid for high-speed, smooth pathfinding
	private static final int MAX_EXPANSIONS = 10000; // Safeguard against unbounded search

	private static final int[][] DIRECTIONS = {
		{STEP_SIZE, 0}, {-STEP_SIZE, 0}, {0, STEP_SIZE}, {0, -STEP_SIZE},
		{STEP_SIZE, STEP_SIZE}, {STEP_SIZE, -STEP_SIZE}, {-STEP_SIZE, STEP_SIZE}, {-STEP_SIZE, -STEP_SIZE}
	};

	private TerrainPathfinder() {
	}

	public static PhysicalPath findPath(
		BlockPos start,
		BlockPos end,
		TerrainHeightSampler sampler
	) {
		int startX = start.getX();
		int startZ = start.getZ();
		int targetX = end.getX();
		int targetZ = end.getZ();

		PriorityQueue<PathNode> openSet = new PriorityQueue<>();
		Map<Long, Double> gScores = new HashMap<>();
		Map<Long, PathNode> cameFrom = new HashMap<>();

		int startY = sampler.getSurfaceHeight(startX, startZ);
		PathNode startNode = new PathNode(startX, startZ, startY, 0.0, euclideanDist(startX, startZ, targetX, targetZ), 0, 0, null);

		openSet.add(startNode);
		gScores.put(packCoord(startX, startZ), 0.0);

		PathNode bestNode = startNode;
		double bestDistanceToTarget = startNode.h;

		int expansions = 0;

		while (!openSet.isEmpty() && expansions++ < MAX_EXPANSIONS) {
			PathNode current = openSet.poll();

			if (current.h < bestDistanceToTarget) {
				bestDistanceToTarget = current.h;
				bestNode = current;
			}

			// Termination check (reached within one step of target)
			if (current.h <= STEP_SIZE * 1.5) {
				bestNode = current;
				break;
			}

			for (int[] dir : DIRECTIONS) {
				int nextX = current.x + dir[0];
				int nextZ = current.z + dir[1];
				long nextKey = packCoord(nextX, nextZ);

				int nextY = sampler.getSurfaceHeight(nextX, nextZ);
				boolean nextIsWater = sampler.isWater(nextX, nextZ);

				double stepDist = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1]);
				double deltaY = Math.abs(nextY - current.y);
				double slope = deltaY / stepDist;

				// Slope penalty model
				double slopePenalty;
				if (slope <= 0.25) {
					slopePenalty = 0.0;
				} else if (slope <= 0.5) {
					slopePenalty = slope * 4.0;
				} else if (slope <= 1.0) {
					slopePenalty = slope * 15.0;
				} else {
					slopePenalty = slope * 60.0; // Severe penalty for steep cliffs
				}

				// Water crossing penalty (discourages lakes/oceans while permitting narrow stream crossings)
				double waterPenalty = nextIsWater ? 120.0 : 0.0;

				// Curvature penalty to encourage straight, natural road lines
				double turnPenalty = (current.dx != 0 || current.dz != 0) && (current.dx != dir[0] || current.dz != dir[1]) ? 1.5 : 0.0;

				double stepCost = stepDist + slopePenalty + waterPenalty + turnPenalty;
				double tentativeG = current.g + stepCost;

				if (tentativeG < gScores.getOrDefault(nextKey, Double.POSITIVE_INFINITY)) {
					gScores.put(nextKey, tentativeG);
					double h = euclideanDist(nextX, nextZ, targetX, targetZ);
					PathNode neighbor = new PathNode(nextX, nextZ, nextY, tentativeG, h, dir[0], dir[1], current);
					cameFrom.put(nextKey, current);
					openSet.add(neighbor);
				}
			}
		}

		// Reconstruct coarse waypoints
		List<BlockPos> coarsePath = new ArrayList<>();
		PathNode trace = bestNode;
		while (trace != null) {
			coarsePath.add(new BlockPos(trace.x, trace.y, trace.z));
			trace = trace.parent;
		}
		Collections.reverse(coarsePath);

		// Ensure exact start and end are anchored
		if (coarsePath.isEmpty() || !coarsePath.get(0).equals(start)) {
			coarsePath.add(0, start);
		}
		int finalEndY = sampler.getSurfaceHeight(targetX, targetZ);
		BlockPos exactEnd = new BlockPos(targetX, finalEndY, targetZ);
		if (!coarsePath.get(coarsePath.size() - 1).equals(exactEnd)) {
			coarsePath.add(exactEnd);
		}

		// Smooth coarse waypoints to ensure no large gaps (>24 blocks) exist
		List<BlockPos> smoothedCoarse = new ArrayList<>();
		for (int i = 0; i < coarsePath.size() - 1; i++) {
			BlockPos p1 = coarsePath.get(i);
			BlockPos p2 = coarsePath.get(i + 1);
			smoothedCoarse.add(p1);
			double dist = euclideanDist(p1.getX(), p1.getZ(), p2.getX(), p2.getZ());
			if (dist > 24.0) {
				int subSteps = (int) Math.ceil(dist / 16.0);
				for (int s = 1; s < subSteps; s++) {
					double ratio = (double) s / subSteps;
					int mx = (int) Math.round(p1.getX() + ratio * (p2.getX() - p1.getX()));
					int mz = (int) Math.round(p1.getZ() + ratio * (p2.getZ() - p1.getZ()));
					int my = sampler.getSurfaceHeight(mx, mz);
					smoothedCoarse.add(new BlockPos(mx, my, mz));
				}
			}
		}
		if (!coarsePath.isEmpty()) {
			smoothedCoarse.add(coarsePath.get(coarsePath.size() - 1));
		}

		// Interpolate into contiguous 1-block steps with 3D Catmull-Rom spline
		List<BlockPos> densePath = interpolateContiguousPath(smoothedCoarse, sampler);

		return new PhysicalPath(densePath);
	}

	/**
	 * Interpolates coarse waypoints into a dense, gap-free sequence of road surface points
	 * using Catmull-Rom cubic splines for smooth, sweeping highway curvature (RoadWeaver feature).
	 */
	public static List<BlockPos> interpolateContiguousPath(List<BlockPos> coarse, TerrainHeightSampler sampler) {
		List<BlockPos> dense = new ArrayList<>();
		if (coarse == null || coarse.isEmpty()) {
			return dense;
		}

		if (coarse.size() < 3) {
			// Fallback to direct linear interpolation for very short segments
			for (int i = 0; i < coarse.size() - 1; i++) {
				BlockPos p1 = coarse.get(i);
				BlockPos p2 = coarse.get(i + 1);
				appendLinearSegment(dense, p1, p2);
			}
			BlockPos last = coarse.get(coarse.size() - 1);
			addPointWithContiguity(dense, last.getX(), last.getY(), last.getZ());
			return dense;
		}

		// Catmull-Rom spline interpolation through waypoints in 3D (X, Y, Z)
		int n = coarse.size();
		for (int i = 0; i < n - 1; i++) {
			BlockPos p0 = (i == 0) ? coarse.get(0) : coarse.get(i - 1);
			BlockPos p1 = coarse.get(i);
			BlockPos p2 = coarse.get(i + 1);
			BlockPos p3 = (i + 2 < n) ? coarse.get(i + 2) : p2;

			double segDist = Math.hypot(p2.getX() - p1.getX(), p2.getZ() - p1.getZ());
			int steps = Math.max(1, (int) Math.ceil(segDist * 2.0));

			for (int s = 0; s < steps; s++) {
				double t = (double) s / steps;
				double t2 = t * t;
				double t3 = t2 * t;

				double x = 0.5 * (
					(2.0 * p1.getX()) +
					(-p0.getX() + p2.getX()) * t +
					(2.0 * p0.getX() - 5.0 * p1.getX() + 4.0 * p2.getX() - p3.getX()) * t2 +
					(-p0.getX() + 3.0 * p1.getX() - 3.0 * p2.getX() + p3.getX()) * t3
				);

				double y = 0.5 * (
					(2.0 * p1.getY()) +
					(-p0.getY() + p2.getY()) * t +
					(2.0 * p0.getY() - 5.0 * p1.getY() + 4.0 * p2.getY() - p3.getY()) * t2 +
					(-p0.getY() + 3.0 * p1.getY() - 3.0 * p2.getY() + p3.getY()) * t3
				);

				double z = 0.5 * (
					(2.0 * p1.getZ()) +
					(-p0.getZ() + p2.getZ()) * t +
					(2.0 * p0.getZ() - 5.0 * p1.getZ() + 4.0 * p2.getZ() - p3.getZ()) * t2 +
					(-p0.getZ() + 3.0 * p1.getZ() - 3.0 * p2.getZ() + p3.getZ()) * t3
				);

				int bx = (int) Math.round(x);
				int by = (int) Math.round(y);
				int bz = (int) Math.round(z);

				addPointWithContiguity(dense, bx, by, bz);
			}
		}

		BlockPos last = coarse.get(n - 1);
		addPointWithContiguity(dense, last.getX(), last.getY(), last.getZ());

		return dense;
	}

	private static void addPointWithContiguity(List<BlockPos> dense, int bx, int by, int bz) {
		if (dense.isEmpty()) {
			dense.add(new BlockPos(bx, by, bz));
			return;
		}

		BlockPos prev = dense.get(dense.size() - 1);
		int px = prev.getX();
		int py = prev.getY();
		int pz = prev.getZ();

		if (px == bx && py == by && pz == bz) {
			return;
		}

		// Ensure gap-free 3D adjacency: if step is greater than 1 block in X, Y, or Z, interpolate discrete intermediate steps
		int dx = bx - px;
		int dy = by - py;
		int dz = bz - pz;
		int stepCount = Math.max(Math.max(Math.abs(dx), Math.abs(dz)), Math.abs(dy));

		for (int s = 1; s <= stepCount; s++) {
			int ix = px + (int) Math.round((double) s * dx / stepCount);
			int iy = py + (int) Math.round((double) s * dy / stepCount);
			int iz = pz + (int) Math.round((double) s * dz / stepCount);

			BlockPos last = dense.get(dense.size() - 1);
			if (ix != last.getX() || iy != last.getY() || iz != last.getZ()) {
				dense.add(new BlockPos(ix, iy, iz));
			}
		}
	}

	private static void appendLinearSegment(List<BlockPos> dense, BlockPos p1, BlockPos p2) {
		int dx = p2.getX() - p1.getX();
		int dy = p2.getY() - p1.getY();
		int dz = p2.getZ() - p1.getZ();
		int steps = Math.max(Math.max(Math.abs(dx), Math.abs(dz)), Math.abs(dy));
		for (int s = 0; s < steps; s++) {
			double t = (double) s / steps;
			int bx = (int) Math.round(p1.getX() + t * dx);
			int by = (int) Math.round(p1.getY() + t * dy);
			int bz = (int) Math.round(p1.getZ() + t * dz);
			addPointWithContiguity(dense, bx, by, bz);
		}
	}

	private static double euclideanDist(int x1, int z1, int x2, int z2) {
		double dx = x2 - x1;
		double dz = z2 - z1;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static long packCoord(int x, int z) {
		return (((long) x) << 32) ^ (((long) z) & 0xFFFFFFFFL);
	}

	private static class PathNode implements Comparable<PathNode> {
		final int x;
		final int z;
		final int y;
		final double g;
		final double h;
		final int dx;
		final int dz;
		final PathNode parent;

		PathNode(int x, int z, int y, double g, double h, int dx, int dz, PathNode parent) {
			this.x = x;
			this.z = z;
			this.y = y;
			this.g = g;
			this.h = h;
			this.dx = dx;
			this.dz = dz;
			this.parent = parent;
		}

		private static final double HEURISTIC_WEIGHT = 1.3;

		double fScore() {
			return this.g + (HEURISTIC_WEIGHT * this.h);
		}

		@Override
		public int compareTo(PathNode o) {
			int cmp = Double.compare(this.fScore(), o.fScore());
			if (cmp != 0) return cmp;
			int cmpH = Double.compare(this.h, o.h);
			if (cmpH != 0) return cmpH;
			int cmpX = Integer.compare(this.x, o.x);
			if (cmpX != 0) return cmpX;
			return Integer.compare(this.z, o.z);
		}
	}
}
