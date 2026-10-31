package com.trinankur.roadnetwork.discovery;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;

/**
 * Indicates whether a structure location is mathematically predicted or confirmed.
 */
public enum DiscoveryStatus {
	/**
	 * Mathematically predicted from world seed, placement parameters, frequency reduction,
	 * exclusion zones, and biome sampling without generating or loading chunks.
	 */
	PREDICTED("PREDICTED", ChatFormatting.GRAY),

	/**
	 * Confirmed by an active, loaded StructureStart or verified storage entry.
	 */
	CONFIRMED("CONFIRMED", ChatFormatting.GOLD);

	public static final Codec<DiscoveryStatus> CODEC = Codec.STRING.xmap(
		DiscoveryStatus::valueOf,
		DiscoveryStatus::name
	);

	private final String label;
	private final ChatFormatting formatting;

	DiscoveryStatus(String label, ChatFormatting formatting) {
		this.label = label;
		this.formatting = formatting;
	}

	public String getLabel() {
		return this.label;
	}

	public ChatFormatting getFormatting() {
		return this.formatting;
	}
}

