package com.trinankur.roadnetwork.discovery;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Inspects dynamic and static registries to enumerate vanilla and modded structures,
 * their parent StructureSets, and their placement configurations.
 */
public final class StructureRegistryInspector {

	private StructureRegistryInspector() {
	}

	public record StructureSetEntryInfo(
		Identifier structureId,
		int weight,
		StructureClassification classification
	) {}

	public record StructureSetInfo(
		Identifier setId,
		String placementType,
		String placementSummary,
		List<StructureSetEntryInfo> structures
	) {}

	public record StructureInfo(
		Identifier structureId,
		StructureClassification classification,
		int allowedBiomeCount,
		String generationStep
	) {}

	/**
	 * Inspect all registered StructureSets in the current ServerLevel.
	 */
	public static List<StructureSetInfo> inspectStructureSets(ServerLevel level) {
		Registry<StructureSet> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
		List<StructureSetInfo> results = new ArrayList<>();

		for (ResourceKey<StructureSet> key : registry.registryKeySet()) {
			Holder<StructureSet> holder = registry.get(key).orElse(null);
			if (holder == null) continue;

			StructureSet set = holder.value();
			StructurePlacement placement = set.placement();

			String typeName;
			String summary;

			if (placement instanceof RandomSpreadStructurePlacement randomSpread) {
				typeName = "RandomSpread";
				summary = String.format("spacing=%d, separation=%d, type=%s",
					randomSpread.spacing(),
					randomSpread.separation(),
					randomSpread.spreadType()
				);
			} else if (placement instanceof ConcentricRingsStructurePlacement rings) {
				typeName = "ConcentricRings";
				summary = String.format("distance=%d, spread=%d, count=%d",
					rings.distance(),
					rings.spread(),
					rings.count()
				);
			} else {
				typeName = placement.getClass().getSimpleName();
				summary = "Custom Placement";
			}

			List<StructureSetEntryInfo> entries = new ArrayList<>();
			for (StructureSet.StructureSelectionEntry entry : set.structures()) {
				Holder<Structure> structHolder = entry.structure();
				Identifier structId = StructureClassifier.getId(structHolder);
				StructureClassification classification = StructureClassifier.classify(structHolder);
				entries.add(new StructureSetEntryInfo(structId, entry.weight(), classification));
			}

			results.add(new StructureSetInfo(key.identifier(), typeName, summary, entries));
		}

		results.sort(Comparator.comparing(s -> s.setId().toString()));
		return results;
	}

	/**
	 * Inspect all registered Structures in the current ServerLevel.
	 */
	public static List<StructureInfo> inspectStructures(ServerLevel level) {
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		List<StructureInfo> results = new ArrayList<>();

		for (ResourceKey<Structure> key : registry.registryKeySet()) {
			Holder<Structure> holder = registry.get(key).orElse(null);
			if (holder == null) continue;

			Structure structure = holder.value();
			Identifier id = key.identifier();
			StructureClassification classification = StructureClassifier.classify(holder);
			int biomes = structure.biomes().size();
			String step = structure.step().name();

			results.add(new StructureInfo(id, classification, biomes, step));
		}

		results.sort(Comparator.comparing(s -> s.structureId().toString()));
		return results;
	}
}
