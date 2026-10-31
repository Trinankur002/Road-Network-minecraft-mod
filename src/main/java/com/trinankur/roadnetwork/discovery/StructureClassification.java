package com.trinankur.roadnetwork.discovery;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;

/**
 * Classification of structures to determine their role in the road network.
 */
public enum StructureClassification {
	/**
	 * Major population centers (e.g., villages) that act as primary network hubs.
	 */
	MAJOR("Major Settlement", ChatFormatting.GREEN),

	/**
	 * Secondary points of interest (outposts, temples, mansions, strongholds)
	 * that connect to the nearest settlement or road.
	 */
	SECONDARY("Secondary Structure", ChatFormatting.AQUA),

	/**
	 * Structures that are excluded from surface road connections
	 * (e.g., underground mineshafts, underwater ruins, shipwrecks, monuments).
	 */
	IGNORED("Ignored", ChatFormatting.DARK_GRAY),

	/**
	 * Structures whose role has not yet been categorized.
	 */
	UNCLASSIFIED("Unclassified", ChatFormatting.YELLOW);

	public static final Codec<StructureClassification> CODEC = Codec.STRING.xmap(
		StructureClassification::valueOf,
		StructureClassification::name
	);

	private final String displayName;
	private final ChatFormatting formatting;

	StructureClassification(String displayName, ChatFormatting formatting) {
		this.displayName = displayName;
		this.formatting = formatting;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public ChatFormatting getFormatting() {
		return this.formatting;
	}
}

