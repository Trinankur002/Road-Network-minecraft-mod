package com.trinankur.roadnetwork.discovery;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Classifies structures into Major Settlements, Secondary Structures, or Ignored.
 * Supports data-driven tags, programmatic overrides, and intelligent name heuristics.
 */
public final class StructureClassifier {

	// Data-driven tag keys
	public static final TagKey<Structure> SETTLEMENTS_TAG = TagKey.create(
		Registries.STRUCTURE,
		Identifier.fromNamespaceAndPath("roadnetwork", "settlements")
	);

	public static final TagKey<Structure> SECONDARY_TAG = TagKey.create(
		Registries.STRUCTURE,
		Identifier.fromNamespaceAndPath("roadnetwork", "secondary")
	);

	public static final TagKey<Structure> IGNORED_TAG = TagKey.create(
		Registries.STRUCTURE,
		Identifier.fromNamespaceAndPath("roadnetwork", "ignored")
	);

	// Programmatic overrides (e.g. from runtime configuration or mod integrations)
	private static final Map<Identifier, StructureClassification> OVERRIDES = new ConcurrentHashMap<>();

	private StructureClassifier() {
	}

	/**
	 * Register or override a specific structure's classification.
	 */
	public static void setOverride(Identifier structureId, StructureClassification classification) {
		if (classification == null || classification == StructureClassification.UNCLASSIFIED) {
			OVERRIDES.remove(structureId);
		} else {
			OVERRIDES.put(structureId, classification);
		}
	}

	/**
	 * Remove a programmatic override.
	 */
	public static void clearOverride(Identifier structureId) {
		OVERRIDES.remove(structureId);
	}

	/**
	 * Clear all programmatic overrides.
	 */
	public static void clearAllOverrides() {
		OVERRIDES.clear();
	}

	/**
	 * Classify a structure holder based on tags, overrides, and naming heuristics.
	 */
	public static StructureClassification classify(Holder<Structure> holder) {
		Identifier id = getId(holder);

		// 1. Check programmatic overrides
		if (id != null && OVERRIDES.containsKey(id)) {
			return OVERRIDES.get(id);
		}

		// 2. Check mod-defined data tags
		if (holder.is(SETTLEMENTS_TAG)) {
			return StructureClassification.MAJOR;
		}
		if (holder.is(SECONDARY_TAG)) {
			return StructureClassification.SECONDARY;
		}
		if (holder.is(IGNORED_TAG)) {
			return StructureClassification.IGNORED;
		}

		// 3. Check vanilla tags
		if (holder.is(StructureTags.VILLAGE)) {
			return StructureClassification.MAJOR;
		}

		// 4. Fallback heuristics based on Identifier path (supports vanilla and modded conventions)
		if (id != null) {
			String path = id.getPath().toLowerCase();

			// Villages and settlements
			if (path.contains("village") || path.contains("settlement") || path.contains("town") || path.contains("city_center")) {
				return StructureClassification.MAJOR;
			}

			// Ignored structures: subterranean, underground hazards, oceanic, nether/end hazards
			// Underground structures (ancient cities, strongholds, trial chambers, mineshafts, trail ruins)
			// should not automatically become surface-road destinations.
			if (path.contains("shipwreck") || path.contains("ocean_ruin") || path.contains("monument")
				|| path.contains("buried_treasure") || path.contains("mineshaft") || path.contains("ancient_city")
				|| path.contains("stronghold") || path.contains("trial_chamber") || path.contains("trial_chambers")
				|| path.contains("trail_ruin") || path.contains("trail_ruins")
				|| path.contains("nether") || path.contains("fortress") || path.contains("bastion")
				|| path.contains("end_city") || path.contains("ruined_portal")) {
				return StructureClassification.IGNORED;
			}

			// Secondary structures: surface outposts, temples, pyramids, mansions, ruins, towers
			if (path.contains("outpost") || path.contains("pyramid") || path.contains("temple")
				|| path.contains("mansion") || path.contains("hut") || path.contains("igloo")
				|| path.contains("ruin") || path.contains("tower") || path.contains("castle") || path.contains("camp")) {
				return StructureClassification.SECONDARY;
			}
		}

		// Default fallback for unidentified modded structures: treat as Secondary points of interest
		return StructureClassification.SECONDARY;
	}

	/**
	 * Safely extract the Identifier from a structure holder.
	 */
	public static Identifier getId(Holder<Structure> holder) {
		return holder.unwrapKey().map(ResourceKey::identifier).orElse(null);
	}
}

