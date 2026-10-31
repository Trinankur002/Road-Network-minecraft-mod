package com.trinankur.roadnetwork.util;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Centralized dimension validator for Road Network.
 * Road Network operates exclusively in the Overworld dimension.
 */
public final class DimensionValidator {

	public static final String UNSUPPORTED_DIMENSION_MESSAGE = "Road Network currently supports the Overworld only.";

	private DimensionValidator() {
	}

	/**
	 * Checks if the given dimension ResourceKey is supported (Overworld only).
	 *
	 * @param dimension the dimension key to validate
	 * @return true if the dimension is Level.OVERWORLD, false otherwise
	 */
	public static boolean isSupported(ResourceKey<Level> dimension) {
		return Level.OVERWORLD.equals(dimension);
	}

	/**
	 * Checks if the given Level's dimension is supported (Overworld only).
	 *
	 * @param level the level to validate
	 * @return true if non-null and Level.OVERWORLD, false otherwise
	 */
	public static boolean isSupported(Level level) {
		return level != null && isSupported(level.dimension());
	}

	public static boolean isOverworld(Level level) {
		return isSupported(level);
	}

	/**
	 * Validates that the dimension key is supported; throws IllegalArgumentException if not.
	 *
	 * @param dimension the dimension key to validate
	 * @throws IllegalArgumentException if the dimension is not Overworld
	 */
	public static void validateDimension(ResourceKey<Level> dimension) {
		if (!isSupported(dimension)) {
			String dimStr = dimension != null ? dimension.identifier().toString() : "unknown";
			throw new IllegalArgumentException("Unsupported dimension: " + dimStr + ". " + UNSUPPORTED_DIMENSION_MESSAGE);
		}
	}

	/**
	 * Validates that the level is supported; throws IllegalArgumentException if not.
	 *
	 * @param level the level to validate
	 * @throws IllegalArgumentException if the dimension is not Overworld
	 */
	public static void validateLevel(Level level) {
		if (!isSupported(level)) {
			String dimStr = (level != null && level.dimension() != null)
				? level.dimension().identifier().toString()
				: "unknown";
			throw new IllegalArgumentException("Unsupported dimension: " + dimStr + ". " + UNSUPPORTED_DIMENSION_MESSAGE);
		}
	}

	/**
	 * Formatted Component message for chat / command feedback when an operation is rejected.
	 */
	public static Component getUnsupportedMessage() {
		return Component.literal(UNSUPPORTED_DIMENSION_MESSAGE);
	}
}

