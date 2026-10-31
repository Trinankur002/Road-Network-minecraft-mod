package com.trinankur.roadnetwork.generation;

import com.trinankur.roadnetwork.network.node.NetworkNode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Calculates village perimeter docking access points.
 * Connects incoming regional roads to the outskirts of a village rather than the geometric bounding-box center,
 * avoiding destruction of village houses, farms, workstations, and meeting wells.
 * 
 * Minimal safe integration strategy:
 * 1. For confirmed structures with an active BoundingBox, calculates ray-box boundary intersection.
 * 2. For predicted candidate structures, calculates radial perimeter offset towards the incoming route.
 * 3. Enforces 2-block margin clearance outside the village core hull.
 */
public final class VillagePerimeterCalculator {

	public static final int DEFAULT_PREDICTED_VILLAGE_RADIUS = 36; // Typical Overworld village radius from center
	private static final int CLEARANCE_MARGIN = 2; // Extra buffer outside structure bounds

	private VillagePerimeterCalculator() {
	}

	/**
	 * Computes the perimeter docking waypoint for a settlement facing a target destination.
	 */
	public static BlockPos calculateDockingPoint(
		NetworkNode node,
		int targetX,
		int targetZ,
		BoundingBox structureBox,
		int surfaceY
	) {
		int originX = node.x();
		int originZ = node.z();

		double dx = targetX - originX;
		double dz = targetZ - originZ;
		double distance = Math.sqrt(dx * dx + dz * dz);

		if (distance < 1.0) {
			return new BlockPos(originX, surfaceY, originZ);
		}

		double dirX = dx / distance;
		double dirZ = dz / distance;

		if (structureBox != null) {
			// Confirmed village with known bounding box
			int centerX = structureBox.minX() + (structureBox.maxX() - structureBox.minX()) / 2;
			int centerZ = structureBox.minZ() + (structureBox.maxZ() - structureBox.minZ()) / 2;

			int halfWidthX = (structureBox.maxX() - structureBox.minX()) / 2 + CLEARANCE_MARGIN;
			int halfWidthZ = (structureBox.maxZ() - structureBox.minZ()) / 2 + CLEARANCE_MARGIN;

			// Ray-box boundary intersection
			double scaleX = Math.abs(dirX) > 1e-5 ? halfWidthX / Math.abs(dirX) : Double.MAX_VALUE;
			double scaleZ = Math.abs(dirZ) > 1e-5 ? halfWidthZ / Math.abs(dirZ) : Double.MAX_VALUE;
			double scale = Math.min(scaleX, scaleZ);

			int dockX = (int) Math.round(centerX + dirX * scale);
			int dockZ = (int) Math.round(centerZ + dirZ * scale);

			return new BlockPos(dockX, surfaceY, dockZ);
		} else {
			// Predicted candidate: radial perimeter offset based on structure classification
			int radius = (node.classification() == com.trinankur.roadnetwork.discovery.StructureClassification.SECONDARY) ? 14 : DEFAULT_PREDICTED_VILLAGE_RADIUS;
			int dockX = (int) Math.round(originX + dirX * radius);
			int dockZ = (int) Math.round(originZ + dirZ * radius);

			return new BlockPos(dockX, surfaceY, dockZ);
		}
	}
}

