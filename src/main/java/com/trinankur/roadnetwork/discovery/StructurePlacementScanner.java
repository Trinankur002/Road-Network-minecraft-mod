package com.trinankur.roadnetwork.discovery;

import com.trinankur.roadnetwork.util.DimensionValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Thread-safe structure scanner that predicts and detects structures around a location
 * without force-loading distant chunks or generating terrain.
 *
 * Enforces strict Overworld dimension boundaries and divides execution into two phases:
 * 1. Background worker phase: Pure mathematical candidate generation & noise biome filtering.
 * 2. Main server thread phase: Safe chunk-cache and structure-manager presence verification.
 */
public final class StructurePlacementScanner {

	private StructurePlacementScanner() {
	}

	public record ScanConfig(
		int chunkRadius,
		boolean includeIgnored,
		int maxResults
	) {
		public static ScanConfig of(int chunkRadius) {
			return new ScanConfig(chunkRadius, false, 150);
		}

		public static ScanConfig of(int chunkRadius, boolean includeIgnored) {
			return new ScanConfig(chunkRadius, includeIgnored, 150);
		}
	}

	/**
	 * Internal candidate model produced during the background worker phase.
	 */
	private record RawCandidate(
		ChunkPos chunkPos,
		Holder<Structure> structureHolder,
		StructureClassification classification,
		StructurePlacement placement,
		String placementTypeName
	) {}

	/**
	 * Asynchronously scan for structures around the origin position.
	 * Background worker executes pure math & noise sampling.
	 * Final presence verification against mutable server state executes safely on the main server thread.
	 */
	public static CompletableFuture<List<DiscoveredStructure>> scanAsync(ServerLevel level, BlockPos origin, ScanConfig config) {
		// Strict dimension check before scheduling
		if (!DimensionValidator.isSupported(level)) {
			return CompletableFuture.completedFuture(Collections.emptyList());
		}

		// Phase 1: Off-thread mathematical scanning
		return CompletableFuture.supplyAsync(
			() -> scanBackgroundCandidates(level, origin, config),
			Util.backgroundExecutor().forName("roadnetwork-scanner")
		).thenApplyAsync(
			rawCandidates -> resolveServerCandidates(level, origin, config, rawCandidates),
			level.getServer()
		);
	}

	/**
	 * Synchronously perform the scan. Must be called on the main server thread.
	 */
	public static List<DiscoveredStructure> scan(ServerLevel level, BlockPos origin, ScanConfig config) {
		DimensionValidator.validateLevel(level);

		List<RawCandidate> rawCandidates = scanBackgroundCandidates(level, origin, config);
		return resolveServerCandidates(level, origin, config, rawCandidates);
	}

	/**
	 * Phase 1: Background worker phase.
	 * Accesses only thread-safe, immutable worldgen state (seed, math, noise router).
	 */
	private static List<RawCandidate> scanBackgroundCandidates(ServerLevel level, BlockPos origin, ScanConfig config) {
		// Re-validate dimension inside worker
		if (!DimensionValidator.isSupported(level)) {
			return Collections.emptyList();
		}

		ChunkPos centerChunk = new ChunkPos(
			SectionPos.blockToSectionCoord(origin.getX()),
			SectionPos.blockToSectionCoord(origin.getZ())
		);
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
		long seed = state.getLevelSeed();
		BiomeSource biomeSource = generator.getBiomeSource();
		RandomState randomState = level.getChunkSource().randomState();

		List<RawCandidate> rawCandidates = new ArrayList<>();
		Set<Long> processedPositions = new HashSet<>();

		for (Holder<StructureSet> setHolder : state.possibleStructureSets()) {
			StructureSet set = setHolder.value();
			StructurePlacement placement = set.placement();

			if (placement instanceof RandomSpreadStructurePlacement randomSpread) {
				collectRandomSpread(
					centerChunk, config, state, seed, biomeSource, randomState,
					set, randomSpread, rawCandidates, processedPositions
				);
			} else if (placement instanceof ConcentricRingsStructurePlacement rings) {
				collectConcentricRings(
					centerChunk, config, state, seed, biomeSource, randomState,
					set, rings, rawCandidates, processedPositions
				);
			}
		}

		return rawCandidates;
	}

	private static void collectRandomSpread(
		ChunkPos centerChunk,
		ScanConfig config,
		ChunkGeneratorStructureState state,
		long seed,
		BiomeSource biomeSource,
		RandomState randomState,
		StructureSet set,
		RandomSpreadStructurePlacement placement,
		List<RawCandidate> candidates,
		Set<Long> processedPositions
	) {
		if (!config.includeIgnored()) {
			boolean anyWanted = false;
			for (StructureSet.StructureSelectionEntry entry : set.structures()) {
				if (StructureClassifier.classify(entry.structure()) != StructureClassification.IGNORED) {
					anyWanted = true;
					break;
				}
			}
			if (!anyWanted) {
				return;
			}
		}

		int spacing = placement.spacing();
		int radius = config.chunkRadius();

		int minGridX = Math.floorDiv(centerChunk.x() - radius, spacing);
		int maxGridX = Math.floorDiv(centerChunk.x() + radius, spacing);
		int minGridZ = Math.floorDiv(centerChunk.z() - radius, spacing);
		int maxGridZ = Math.floorDiv(centerChunk.z() + radius, spacing);

		for (int gx = minGridX; gx <= maxGridX; gx++) {
			for (int gz = minGridZ; gz <= maxGridZ; gz++) {
				ChunkPos candidate = placement.getPotentialStructureChunk(seed, gx * spacing, gz * spacing);

				int dx = Math.abs(candidate.x() - centerChunk.x());
				int dz = Math.abs(candidate.z() - centerChunk.z());
				if (Math.max(dx, dz) > radius) {
					continue;
				}

				if (!placement.isStructureChunk(state, candidate.x(), candidate.z())) {
					continue;
				}

				Holder<Structure> selectedStructure = selectStructureForChunk(set, seed, candidate, biomeSource, randomState);
				if (selectedStructure == null) {
					continue;
				}

				StructureClassification classification = StructureClassifier.classify(selectedStructure);
				if (!config.includeIgnored() && classification == StructureClassification.IGNORED) {
					continue;
				}

				long packedKey = candidate.pack();
				Identifier structureId = StructureClassifier.getId(selectedStructure);
				long compositeKey = packedKey ^ (structureId != null ? (long) structureId.hashCode() << 32 : 0L);

				if (processedPositions.add(compositeKey)) {
					candidates.add(new RawCandidate(candidate, selectedStructure, classification, placement, "RandomSpread"));
				}
			}
		}
	}

	private static void collectConcentricRings(
		ChunkPos centerChunk,
		ScanConfig config,
		ChunkGeneratorStructureState state,
		long seed,
		BiomeSource biomeSource,
		RandomState randomState,
		StructureSet set,
		ConcentricRingsStructurePlacement placement,
		List<RawCandidate> candidates,
		Set<Long> processedPositions
	) {
		if (!config.includeIgnored()) {
			boolean anyWanted = false;
			for (StructureSet.StructureSelectionEntry entry : set.structures()) {
				if (StructureClassifier.classify(entry.structure()) != StructureClassification.IGNORED) {
					anyWanted = true;
					break;
				}
			}
			if (!anyWanted) {
				return;
			}
		}

		List<ChunkPos> ringPositions = state.getRingPositionsFor(placement);
		if (ringPositions == null || ringPositions.isEmpty()) {
			return;
		}

		int radius = config.chunkRadius();

		for (ChunkPos candidate : ringPositions) {
			int dx = Math.abs(candidate.x() - centerChunk.x());
			int dz = Math.abs(candidate.z() - centerChunk.z());
			if (Math.max(dx, dz) > radius) {
				continue;
			}

			if (!placement.isStructureChunk(state, candidate.x(), candidate.z())) {
				continue;
			}

			Holder<Structure> selectedStructure = selectStructureForChunk(set, seed, candidate, biomeSource, randomState);
			if (selectedStructure == null) {
				continue;
			}

			StructureClassification classification = StructureClassifier.classify(selectedStructure);
			if (!config.includeIgnored() && classification == StructureClassification.IGNORED) {
				continue;
			}

			long packedKey = candidate.pack();
			Identifier structureId = StructureClassifier.getId(selectedStructure);
			long compositeKey = packedKey ^ (structureId != null ? (long) structureId.hashCode() << 32 : 0L);

			if (processedPositions.add(compositeKey)) {
				candidates.add(new RawCandidate(candidate, selectedStructure, classification, placement, "ConcentricRings"));
			}
		}
	}

	/**
	 * Phase 2: Main server thread phase.
	 * Evaluates mutable server state (chunk cache and structure presence in storage).
	 * Discards results if server is shutting down or dimension is no longer active.
	 */
	private static List<DiscoveredStructure> resolveServerCandidates(
		ServerLevel level,
		BlockPos origin,
		ScanConfig config,
		List<RawCandidate> rawCandidates
	) {
		// Verify server and dimension validity
		if (level.getServer() == null || !level.getServer().isRunning()) {
			return Collections.emptyList();
		}
		if (level.getServer().getLevel(Level.OVERWORLD) != level || !DimensionValidator.isSupported(level)) {
			return Collections.emptyList();
		}

		List<DiscoveredStructure> resolved = new ArrayList<>();

		for (RawCandidate raw : rawCandidates) {
			ChunkPos candidate = raw.chunkPos();
			Holder<Structure> structureHolder = raw.structureHolder();
			StructurePlacement placement = raw.placement();
			Identifier structureId = StructureClassifier.getId(structureHolder);

			BlockPos locatePos = placement.getLocatePos(candidate);
			int candidateX = locatePos.getX();
			int candidateZ = locatePos.getZ();

			// 1. Check in-memory chunk cache on main thread
			LevelChunk loadedChunk = level.getChunkSource().getChunkNow(candidate.x(), candidate.z());
			if (loadedChunk != null) {
				StructureStart start = loadedChunk.getStartForStructure(structureHolder.value());
				if (start != null && start.isValid()) {
					BlockPos center = start.getBoundingBox().getCenter();
					double dx = center.getX() - origin.getX();
					double dy = center.getY() - origin.getY();
					double dz = center.getZ() - origin.getZ();
					double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

					resolved.add(DiscoveredStructure.confirmed(
						structureHolder,
						structureId,
						center.getX(),
						center.getY(),
						center.getZ(),
						candidate,
						distance,
						raw.classification(),
						raw.placementTypeName()
					));
					continue;
				}
			}

			// 2. Safely register unloaded/ungenerated candidate as predicted (zero disk IO lag)
			double dx = candidateX - origin.getX();
			double dz = candidateZ - origin.getZ();
			double horizontalDist = Math.sqrt(dx * dx + dz * dz);

			resolved.add(DiscoveredStructure.predicted(
				structureHolder,
				structureId,
				candidateX,
				candidateZ,
				candidate,
				horizontalDist,
				raw.classification(),
				raw.placementTypeName()
			));
		}

		Collections.sort(resolved);

		if (resolved.size() > config.maxResults()) {
			return new ArrayList<>(resolved.subList(0, config.maxResults()));
		}

		return resolved;
	}

	/**
	 * Selects which structure in the set generates at this candidate chunk, matching vanilla ChunkGenerator logic.
	 * If the set has multiple structures (e.g. villages), uses deterministic weighted selection with biome validation.
	 */
	private static Holder<Structure> selectStructureForChunk(
		StructureSet set,
		long seed,
		ChunkPos chunkPos,
		BiomeSource biomeSource,
		RandomState randomState
	) {
		List<StructureSet.StructureSelectionEntry> entries = set.structures();
		if (entries.isEmpty()) {
			return null;
		}
		if (entries.size() == 1) {
			Holder<Structure> single = entries.get(0).structure();
			return isBiomeValid(single.value(), chunkPos, biomeSource, randomState) ? single : null;
		}

		List<StructureSet.StructureSelectionEntry> options = new ArrayList<>(entries);
		WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
		random.setLargeFeatureSeed(seed, chunkPos.x(), chunkPos.z());
		int totalWeight = 0;
		for (StructureSet.StructureSelectionEntry entry : options) {
			totalWeight += entry.weight();
		}

		while (!options.isEmpty() && totalWeight > 0) {
			int choice = random.nextInt(totalWeight);
			int index = 0;
			for (StructureSet.StructureSelectionEntry option : options) {
				choice -= option.weight();
				if (choice < 0) {
					break;
				}
				index++;
			}
			if (index >= options.size()) {
				index = options.size() - 1;
			}

			StructureSet.StructureSelectionEntry selected = options.get(index);
			if (isBiomeValid(selected.structure().value(), chunkPos, biomeSource, randomState)) {
				return selected.structure();
			}

			options.remove(index);
			totalWeight -= selected.weight();
		}

		return null;
	}

	private static final int[] CHECK_Y_LEVELS = {64, 76, 88, 100, 116, 132, 148};

	private static boolean isBiomeValid(Structure structure, ChunkPos chunkPos, BiomeSource biomeSource, RandomState randomState) {
		int sampleBlockX = SectionPos.sectionToBlockCoord(chunkPos.x(), 8);
		int sampleBlockZ = SectionPos.sectionToBlockCoord(chunkPos.z(), 8);
		int quartX = QuartPos.fromBlock(sampleBlockX);
		int quartZ = QuartPos.fromBlock(sampleBlockZ);

		for (int y : CHECK_Y_LEVELS) {
			Holder<Biome> noiseBiome = biomeSource.getNoiseBiome(
				quartX,
				QuartPos.fromBlock(y),
				quartZ,
				randomState.sampler()
			);
			if (structure.biomes().contains(noiseBiome)) {
				return true;
			}
		}
		return false;
	}
}
