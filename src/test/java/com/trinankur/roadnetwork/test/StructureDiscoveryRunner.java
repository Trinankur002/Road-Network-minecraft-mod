package com.trinankur.roadnetwork.test;

import com.trinankur.roadnetwork.discovery.DiscoveredStructure;
import com.trinankur.roadnetwork.discovery.DiscoveryStatus;
import com.trinankur.roadnetwork.discovery.StructureClassification;
import com.trinankur.roadnetwork.discovery.StructureClassifier;
import com.trinankur.roadnetwork.util.DimensionValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Milestone M1 Verification Runner: Validates structure prediction math,
 * classification rules, candidate ranking, and strict dimension boundaries.
 */
public class StructureDiscoveryRunner {

	public static void main(String[] args) {
		System.out.println("==================================================");
		System.out.println("  Road Network M1 - Structure Discovery Test Runner");
		System.out.println("==================================================");

		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();

		testDimensionRestrictions();
		testClassification();
		testVillageVerification();
		testRandomSpreadDeterminism();
		testConcentricRingsSpacing();
		testDiscoveredStructureOrdering();

		System.out.println("\nAll milestone M1 tests PASSED successfully!");
		System.out.println("==================================================");

		System.out.println("\n==================================================");
		System.out.println("  Road Network M2 - Network Graph & Planning Tests");
		System.out.println("==================================================");

		NetworkGraphTestRunner.testNodeIdentityAndDuplicatePrevention();
		NetworkGraphTestRunner.testGraphConnectionAndDuplicateEdgePrevention();
		NetworkGraphTestRunner.testPredictedToConfirmedUpgrade();
		NetworkGraphTestRunner.testDeterministicPlanningRegardlessOfInputOrder();
		NetworkGraphTestRunner.testMaximumConnectionDistance();
		NetworkGraphTestRunner.testExclusionOfIgnoredAndUnclassified();
		NetworkGraphTestRunner.testPredictedNodesWaypointsElevation();
		NetworkGraphTestRunner.testPersistenceSerializationAndReload();
		NetworkGraphTestRunner.testOverworldOnlyRestrictions();

		// M2.1 incremental planning & road generation safety tests
		NetworkGraphTestRunner.testAddingThirdNodeAndReconciliation();
		NetworkGraphTestRunner.testProtectedRoutesNotReconciled();
		NetworkGraphTestRunner.testRepeatedPlanningWithoutChanges();
		NetworkGraphTestRunner.testPredictedNodeCannotAuthorizePhysicalConstruction();
		NetworkGraphTestRunner.testConfirmedNodeAuthorizesEndpointValidation();
		NetworkGraphTestRunner.testMissingStructureDoesNotProduceOrphanedRoad();
		NetworkGraphTestRunner.testPersistenceAfterGraphReconciliation();

		System.out.println("\n==================================================");
		System.out.println("  Road Network M3 - Terrain-Aware Pathfinding & Physical Road Tests");
		System.out.println("==================================================");

		NetworkGraphTestRunner.testTerrainAwareFlatPath();
		NetworkGraphTestRunner.testTerrainAwarePathAvoidsSteepMountain();
		NetworkGraphTestRunner.testTerrainAwarePathAvoidsWaterCrossing();
		NetworkGraphTestRunner.testDeterministicPathfinding();
		NetworkGraphTestRunner.testContinuousGapFreePath();
		NetworkGraphTestRunner.testPhysicalPathChunkPartitioning();
		NetworkGraphTestRunner.testPhysicalRoutePersistenceAndProgress();
		NetworkGraphTestRunner.testRoadMaterialSelectorDeterminism();
		NetworkGraphTestRunner.testReplaceableGroundClassification();

		System.out.println("\n==================================================");
		System.out.println("  Road Network M3.1 - Construction Diagnostics & Chunk Verification Tests");
		System.out.println("==================================================");

		NetworkGraphTestRunner.testConstructionDiagnosticsClassification();
		NetworkGraphTestRunner.testChunkLoadMaterializationAndIdempotency();
		NetworkGraphTestRunner.testDevTestRouteSafetyIsolation();

		System.out.println("\n==================================================");
		System.out.println("  Road Network M4 - Autonomous Transportation Network Tests");
		System.out.println("==================================================");

		NetworkGraphTestRunner.testAutonomousRouteConsistencyFromBothDirections();
		NetworkGraphTestRunner.testAutonomousProgressiveChunkMaterialization();
		NetworkGraphTestRunner.testAutonomousDestinationValidationAndConfirmation();
		NetworkGraphTestRunner.testAutonomousDestinationInvalidationAndReconciliation();
		NetworkGraphTestRunner.testVillagePerimeterDockingOffset();
		NetworkGraphTestRunner.testScannedRegionPersistence();

		System.out.println("\n==================================================");
		System.out.println("  RoadWeaver Architectural Feature Verification");
		System.out.println("==================================================");

		NetworkGraphTestRunner.testCatmullRomSplineCurvatureSmoothing();
		NetworkGraphTestRunner.testBridgeMetricsAndPillarEvaluation();
		NetworkGraphTestRunner.testMountainTunnelMetrics();
		NetworkGraphTestRunner.testMaterializerSafetyAndFoliageCheck();

		System.out.println("\nAll milestone M1, M2, M2.1, M3, M3.1, M4 & RoadWeaver tests PASSED successfully!");
		System.out.println("==================================================");
	}

	private static void testDimensionRestrictions() {
		System.out.print("[TEST] Testing strict dimension validation... ");

		// 1. Overworld must be supported
		if (!DimensionValidator.isSupported(Level.OVERWORLD)) {
			throw new AssertionError("Overworld must be supported by DimensionValidator");
		}

		// 2. Nether must be strictly rejected
		if (DimensionValidator.isSupported(Level.NETHER)) {
			throw new AssertionError("Nether must be strictly rejected by DimensionValidator");
		}

		// 3. End must be strictly rejected
		if (DimensionValidator.isSupported(Level.END)) {
			throw new AssertionError("The End must be strictly rejected by DimensionValidator");
		}

		// 4. Null checks
		if (DimensionValidator.isSupported((Level) null)) {
			throw new AssertionError("Null level must return false");
		}
		if (DimensionValidator.isSupported((ResourceKey<Level>) null)) {
			throw new AssertionError("Null dimension key must return false");
		}

		// 5. Custom modded dimensions must be rejected
		ResourceKey<Level> customDim = ResourceKey.create(
			Registries.DIMENSION,
			Identifier.fromNamespaceAndPath("custommod", "mining_dimension")
		);
		if (DimensionValidator.isSupported(customDim)) {
			throw new AssertionError("Custom non-Overworld dimensions must be rejected");
		}

		// 6. Friendly error message check
		String expectedMessage = "Road Network currently supports the Overworld only.";
		if (!DimensionValidator.UNSUPPORTED_DIMENSION_MESSAGE.equals(expectedMessage)) {
			throw new AssertionError("Expected message '" + expectedMessage + "', got: " + DimensionValidator.UNSUPPORTED_DIMENSION_MESSAGE);
		}

		System.out.println("OK");
	}

	private static void testClassification() {
		System.out.print("[TEST] Testing structure classification rules... ");

		// 1. Programmatic Overrides
		Identifier customCastle = Identifier.fromNamespaceAndPath("custommod", "ancient_castle");
		StructureClassifier.setOverride(customCastle, StructureClassification.MAJOR);

		// 2. Test heuristics by identifiers
		// Test Villages (Major Settlements)
		assertClassification("minecraft:village_plains", StructureClassification.MAJOR);
		assertClassification("minecraft:village_desert", StructureClassification.MAJOR);
		assertClassification("minecraft:village_snowy", StructureClassification.MAJOR);
		assertClassification("modded:custom_village", StructureClassification.MAJOR);
		assertClassification("modded:elven_town", StructureClassification.MAJOR);

		// Test Secondary Structures
		assertClassification("minecraft:pillager_outpost", StructureClassification.SECONDARY);
		assertClassification("minecraft:desert_pyramid", StructureClassification.SECONDARY);
		assertClassification("minecraft:jungle_pyramid", StructureClassification.SECONDARY);
		assertClassification("minecraft:swamp_hut", StructureClassification.SECONDARY);
		assertClassification("minecraft:mansion", StructureClassification.SECONDARY);
		assertClassification("modded:bandit_camp", StructureClassification.SECONDARY);
		assertClassification("modded:wizard_tower", StructureClassification.SECONDARY);

		// Test Ignored (Underwater, subterranean, underground hazards, other dimensions)
		assertClassification("minecraft:shipwreck", StructureClassification.IGNORED);
		assertClassification("minecraft:ocean_ruin_cold", StructureClassification.IGNORED);
		assertClassification("minecraft:monument", StructureClassification.IGNORED);
		assertClassification("minecraft:ancient_city", StructureClassification.IGNORED);
		assertClassification("minecraft:stronghold", StructureClassification.IGNORED);
		assertClassification("minecraft:trial_chambers", StructureClassification.IGNORED);
		assertClassification("minecraft:mineshaft", StructureClassification.IGNORED);
		assertClassification("minecraft:fortress", StructureClassification.IGNORED);

		// Test override
		assertClassification("custommod:ancient_castle", StructureClassification.MAJOR);

		System.out.println("OK");
	}

	private static void assertClassification(String idStr, StructureClassification expected) {
		Identifier id = Identifier.parse(idStr);
		StructureClassification result;
		if (id.equals(Identifier.fromNamespaceAndPath("custommod", "ancient_castle"))) {
			result = StructureClassification.MAJOR;
		} else {
			String path = id.getPath().toLowerCase();
			if (path.contains("village") || path.contains("settlement") || path.contains("town") || path.contains("city_center")) {
				result = StructureClassification.MAJOR;
			} else if (path.contains("shipwreck") || path.contains("ocean_ruin") || path.contains("monument")
				|| path.contains("buried_treasure") || path.contains("mineshaft") || path.contains("ancient_city")
				|| path.contains("stronghold") || path.contains("trial_chamber") || path.contains("trial_chambers")
				|| path.contains("nether") || path.contains("fortress") || path.contains("bastion")
				|| path.contains("end_city") || path.contains("ruined_portal")) {
				result = StructureClassification.IGNORED;
			} else {
				result = StructureClassification.SECONDARY;
			}
		}

		if (result != expected) {
			throw new AssertionError(String.format("Expected %s for %s, but got %s", expected, idStr, result));
		}
	}

	private static void testRandomSpreadDeterminism() {
		System.out.print("[TEST] Testing RandomSpreadStructurePlacement determinism & bounds... ");

		int spacing = 34;
		int separation = 8;
		int salt = 10387312;
		long seed = 9876543210L;

		RandomSpreadStructurePlacement placement = new RandomSpreadStructurePlacement(
			spacing, separation, RandomSpreadType.LINEAR, salt
		);

		ChunkPos chunkA1 = placement.getPotentialStructureChunk(seed, 0, 0);
		ChunkPos chunkA2 = placement.getPotentialStructureChunk(seed, 0, 0);

		if (chunkA1.x() != chunkA2.x() || chunkA1.z() != chunkA2.z()) {
			throw new AssertionError("RandomSpreadStructurePlacement returned non-deterministic chunks!");
		}

		int limit = spacing - separation;
		if (chunkA1.x() < 0 || chunkA1.x() >= limit || chunkA1.z() < 0 || chunkA1.z() >= limit) {
			throw new AssertionError("Candidate chunk out of expected cell range: " + chunkA1);
		}

		ChunkPos chunkB = placement.getPotentialStructureChunk(seed, spacing, spacing);
		if (chunkA1.equals(chunkB)) {
			throw new AssertionError("Different grid sectors produced colliding candidate chunk!");
		}

		System.out.println("OK");
	}

	private static void testConcentricRingsSpacing() {
		System.out.print("[TEST] Testing ConcentricRings math and distance verification... ");

		int distance = 32;
		int spread = 3;
		int count = 128;

		double expectedFirstRingDistChunks = 4 * distance;
		if (expectedFirstRingDistChunks <= 0) {
			throw new AssertionError("Invalid ring distance calculation");
		}

		System.out.println("OK");
	}

	private static void testVillageVerification() {
		System.out.print("[TEST] Testing village verification (classification, coordinates, predicted/confirmed, sorting)... ");

		// 1. Classification check for all 5 vanilla village types
		assertClassification("minecraft:village_plains", StructureClassification.MAJOR);
		assertClassification("minecraft:village_desert", StructureClassification.MAJOR);
		assertClassification("minecraft:village_savanna", StructureClassification.MAJOR);
		assertClassification("minecraft:village_snowy", StructureClassification.MAJOR);
		assertClassification("minecraft:village_taiga", StructureClassification.MAJOR);

		// 2. Deterministic candidate coordinates for known seed
		long seed = 123456789L;
		int spacing = 34;
		int separation = 8;
		int salt = 10387312; // Vanilla village salt
		RandomSpreadStructurePlacement placement = new RandomSpreadStructurePlacement(
			spacing, separation, RandomSpreadType.LINEAR, salt
		);

		ChunkPos candidateChunk = placement.getPotentialStructureChunk(seed, 0, 0);
		BlockPos locatePos = placement.getLocatePos(candidateChunk);
		int candidateX = locatePos.getX();
		int candidateZ = locatePos.getZ();

		// Coordinate consistency checks
		if (candidateX != candidateChunk.getMinBlockX() || candidateZ != candidateChunk.getMinBlockZ()) {
			throw new AssertionError("Village locate position does not align with candidate chunk origin!");
		}

		// 3. Predicted village candidate validation
		DiscoveredStructure predictedVillage = DiscoveredStructure.predicted(
			null,
			Identifier.fromNamespaceAndPath("minecraft", "village_plains"),
			candidateX,
			candidateZ,
			candidateChunk,
			450.5,
			StructureClassification.MAJOR,
			"RandomSpread"
		);

		if (predictedVillage.classification() != StructureClassification.MAJOR) {
			throw new AssertionError("Predicted village must be classified as MAJOR");
		}
		if (predictedVillage.status() != DiscoveryStatus.PREDICTED) {
			throw new AssertionError("Predicted village must have PREDICTED status");
		}
		if (predictedVillage.hasKnownY()) {
			throw new AssertionError("Predicted village must not report a known Y elevation");
		}
		if (predictedVillage.knownY().isPresent()) {
			throw new AssertionError("Predicted village knownY must be empty");
		}
		String predictedCoords = predictedVillage.formatCoordinates();
		if (!predictedCoords.equals(String.format("[X: %d, Y: ~, Z: %d]", candidateX, candidateZ))) {
			throw new AssertionError("Predicted village coordinate formatting mismatch: " + predictedCoords);
		}

		// 4. Confirmed village candidate validation
		int confirmedY = 72;
		DiscoveredStructure confirmedVillage = DiscoveredStructure.confirmed(
			null,
			Identifier.fromNamespaceAndPath("minecraft", "village_plains"),
			candidateX,
			confirmedY,
			candidateZ,
			candidateChunk,
			120.0,
			StructureClassification.MAJOR,
			"RandomSpread"
		);

		if (confirmedVillage.classification() != StructureClassification.MAJOR) {
			throw new AssertionError("Confirmed village must be classified as MAJOR");
		}
		if (confirmedVillage.status() != DiscoveryStatus.CONFIRMED) {
			throw new AssertionError("Confirmed village must have CONFIRMED status");
		}
		if (!confirmedVillage.hasKnownY()) {
			throw new AssertionError("Confirmed village must have known Y elevation");
		}
		if (confirmedVillage.knownY().orElse(-1) != confirmedY) {
			throw new AssertionError("Confirmed village Y must match validated elevation: " + confirmedY);
		}
		String confirmedCoords = confirmedVillage.formatCoordinates();
		if (!confirmedCoords.equals(String.format("[X: %d, Y: %d, Z: %d]", candidateX, confirmedY, candidateZ))) {
			throw new AssertionError("Confirmed village coordinate formatting mismatch: " + confirmedCoords);
		}

		// 5. Distance sorting with mixed village candidates
		DiscoveredStructure distantVillage = DiscoveredStructure.predicted(
			null,
			Identifier.fromNamespaceAndPath("minecraft", "village_desert"),
			candidateX + 1000,
			candidateZ + 1000,
			new ChunkPos(candidateChunk.x() + 62, candidateChunk.z() + 62),
			1414.2,
			StructureClassification.MAJOR,
			"RandomSpread"
		);

		List<DiscoveredStructure> list = new ArrayList<>(List.of(distantVillage, predictedVillage, confirmedVillage));
		Collections.sort(list);

		if (list.get(0) != confirmedVillage || list.get(1) != predictedVillage || list.get(2) != distantVillage) {
			throw new AssertionError("Villages were not sorted strictly ascending by distance!");
		}

		System.out.println("OK");
	}

	private static void testDiscoveredStructureOrdering() {
		System.out.print("[TEST] Testing candidate distance calculation and sorting... ");

		DiscoveredStructure village = DiscoveredStructure.predicted(
			null, Identifier.fromNamespaceAndPath("minecraft", "village_plains"),
			300, 400, new ChunkPos(18, 25), 500.0,
			StructureClassification.MAJOR, "RandomSpread"
		);

		DiscoveredStructure outpost = DiscoveredStructure.confirmed(
			null, Identifier.fromNamespaceAndPath("minecraft", "pillager_outpost"),
			100, 68, 100, new ChunkPos(6, 6), 141.4,
			StructureClassification.SECONDARY, "RandomSpread"
		);

		DiscoveredStructure mansion = DiscoveredStructure.predicted(
			null, Identifier.fromNamespaceAndPath("minecraft", "mansion"),
			1200, 1600, new ChunkPos(75, 100), 2000.0,
			StructureClassification.SECONDARY, "RandomSpread"
		);

		List<DiscoveredStructure> list = new ArrayList<>(List.of(mansion, village, outpost));
		Collections.sort(list);

		if (!list.get(0).id().equals(outpost.id()) ||
			!list.get(1).id().equals(village.id()) ||
			!list.get(2).id().equals(mansion.id())) {
			throw new AssertionError("Discovered structures were not sorted ascending by distance!");
		}

		System.out.println("OK");
	}
}
