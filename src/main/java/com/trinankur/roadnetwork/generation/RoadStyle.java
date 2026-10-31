package com.trinankur.roadnetwork.generation;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.trinankur.roadnetwork.discovery.StructureClassification;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Defines a complete infrastructure generation style profile.
 * A style encapsulates the material palette, width, and structural characteristics
 * for a particular class of road infrastructure.
 */
public record RoadStyle(
    String name,
    RoadClass roadClass,
    int width,
    BlockState surfaceBlock,
    Optional<BlockState> baseBlock,
    Optional<BlockState> edgeBlock,
    List<BlockState> decorativeBlocks,
    StyleProfile profile
) {
    /**
     * Classification of road infrastructure importance and type.
     */
    public enum RoadClass {
        TRAIL(3, "Trail", 0.3),
        VILLAGE_ROAD(3, "Village Road", 0.5),
        SECONDARY(5, "Secondary Road", 0.8),
        MAIN(5, "Main Road", 1.0),
        MODERN(5, "Modern Road", 1.0);

        public static final Codec<RoadClass> CODEC = Codec.STRING.xmap(
            RoadClass::valueOf,
            RoadClass::name
        );

        private final int defaultWidth;
        private final String displayName;
        private final double importance;

        RoadClass(int defaultWidth, String displayName, double importance) {
            this.defaultWidth = defaultWidth;
            this.displayName = displayName;
            this.importance = importance;
        }

        public int getDefaultWidth() {
            return this.defaultWidth;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public double getImportance() {
            return this.importance;
        }

        /**
         * Determines road class based on structure classification and route length.
         * Longer routes between major settlements get higher importance.
         */
        public static RoadClass fromClassification(StructureClassification classification, double length) {
            return switch (classification) {
                case MAJOR -> length > 800 ? MAIN : SECONDARY;
                case SECONDARY -> length > 500 ? SECONDARY : VILLAGE_ROAD;
                default -> TRAIL;
            };
        }
    }

    /**
     * Style profiles determine material selection and aesthetic approach.
     */
    public enum StyleProfile {
        NATURAL("Natural", "natural_rural"),
        STONE("Stone Historic", "stone_historic"),
        DESERT("Desert", "desert"),
        SNOW("Snow Cold", "snow_cold"),
        MODERN("Modern Developed", "modern_developed");

        private final String displayName;
        private final String configName;

        StyleProfile(String displayName, String configName) {
            this.displayName = displayName;
            this.configName = configName;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public String getConfigName() {
            return this.configName;
        }

        /**
         * Returns the profile appropriate for the given biome category.
         * This is a simplified resolver — full biome-tag-based resolution
         * would be data-driven.
         */
        public static StyleProfile fromBiome(String biomePath) {
            if (biomePath.contains("desert") || biomePath.contains("badlands")) {
                return DESERT;
            }
            if (biomePath.contains("snow") || biomePath.contains("frozen") || biomePath.contains("taiga") || biomePath.contains("cold")) {
                return SNOW;
            }
            if (biomePath.contains("forest") || biomePath.contains("birch") || biomePath.contains("jungle") || biomePath.contains("dark_forest")) {
                return NATURAL;
            }
            if (biomePath.contains("plains") || biomePath.contains("meadow") || biomePath.contains("savanna") || biomePath.contains("river")) {
                return NATURAL;
            }
            return NATURAL;
        }
    }

    /**
     * Returns the full width of the road including edges.
     */
    public int getTotalWidth() {
        return this.width;
    }

    /**
     * Returns the number of surface blocks (excluding edges).
     */
    public int getSurfaceWidth() {
        return Math.max(1, this.width - (this.edgeBlock.isPresent() ? 2 : 0));
    }

    /**
     * Selects the appropriate road style for a given biome and road class.
     * Uses deterministic spatial hashing for variation within a palette.
     */
    public static RoadStyle selectStyle(int x, int z, RoadClass roadClass, StyleProfile profile) {
        int hash = RoadMaterialSelector.getPosHash(x, z);
        return buildStyle(roadClass, profile, hash);
    }

    /**
     * Selects the appropriate road style for a given biome and road class.
     */
    public static RoadStyle selectStyle(int x, int z, RoadClass roadClass, StyleProfile profile, net.minecraft.world.level.LevelAccessor level, net.minecraft.core.BlockPos pos) {
        int hash = RoadMaterialSelector.getPosHash(x, z);
        return buildStyleWithBiome(roadClass, profile, hash, level, pos);
    }

    private static RoadStyle buildStyle(RoadClass roadClass, StyleProfile profile, int hash) {
        int variant = Math.floorMod(hash, 100);

        return switch (profile) {
            case DESERT -> buildDesertStyle(roadClass, variant);
            case SNOW -> buildSnowStyle(roadClass, variant);
            case NATURAL -> buildNaturalStyle(roadClass, variant, profile);
            case STONE -> buildStoneStyle(roadClass, variant);
            case MODERN -> buildModernStyle(roadClass, variant);
        };
    }

    private static RoadStyle buildStyleWithBiome(RoadClass roadClass, StyleProfile profile, int hash, net.minecraft.world.level.LevelAccessor level, net.minecraft.core.BlockPos pos) {
        // For now, same as buildStyle; biome-specific block selection is already handled
        // in RoadMaterialSelector, this just determines the style profile
        return buildStyle(roadClass, profile, hash);
    }

    private static RoadStyle buildDesertStyle(RoadClass roadClass, int variant) {
        int width = roadClass.getDefaultWidth();
        BlockState surface;
        BlockState base;

        if (variant < 50) {
            surface = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
        } else if (variant < 80) {
            surface = Blocks.SANDSTONE.defaultBlockState();
        } else {
            surface = Blocks.CUT_SANDSTONE.defaultBlockState();
        }

        if (roadClass.getImportance() >= 0.8) {
            base = Blocks.SANDSTONE.defaultBlockState();
            width = Math.max(width, 5);
            return new RoadStyle(
                "desert_main",
                roadClass,
                width,
                surface,
                Optional.of(base),
                Optional.of(Blocks.SANDSTONE.defaultBlockState()),
                List.of(),
                StyleProfile.DESERT
            );
        }

        base = Blocks.SAND.defaultBlockState();
        return new RoadStyle(
            "desert_path",
            roadClass,
            width,
            surface,
            Optional.of(base),
            Optional.empty(),
            List.of(),
            StyleProfile.DESERT
        );
    }

    private static RoadStyle buildSnowStyle(RoadClass roadClass, int variant) {
        int width = roadClass.getDefaultWidth();
        BlockState surface;
        BlockState base;

        if (roadClass.getImportance() >= 0.8) {
            // Main roads in snow get stone/cobblestone
            if (variant < 60) {
                surface = Blocks.COBBLESTONE.defaultBlockState();
            } else if (variant < 85) {
                surface = Blocks.STONE.defaultBlockState();
            } else {
                surface = Blocks.GRAVEL.defaultBlockState();
            }
            base = Blocks.COBBLESTONE.defaultBlockState();
            width = Math.max(width, 5);
            return new RoadStyle(
                "snow_main",
                roadClass,
                width,
                surface,
                Optional.of(base),
                Optional.of(Blocks.COBBLESTONE.defaultBlockState()),
                List.of(),
                StyleProfile.SNOW
            );
        }

        if (variant < 50) {
            surface = Blocks.GRAVEL.defaultBlockState();
        } else if (variant < 80) {
            surface = Blocks.COBBLESTONE.defaultBlockState();
        } else {
            surface = Blocks.PACKED_ICE.defaultBlockState();
        }

        base = Blocks.GRAVEL.defaultBlockState();
        return new RoadStyle(
            "snow_path",
            roadClass,
            width,
            surface,
            Optional.of(base),
            Optional.empty(),
            List.of(),
            StyleProfile.SNOW
        );
    }

    private static RoadStyle buildNaturalStyle(RoadClass roadClass, int variant, StyleProfile profile) {
        int width = roadClass.getDefaultWidth();
        BlockState surface;
        BlockState base;

        if (profile == StyleProfile.NATURAL) {
            // Natural/forest style
            if (variant < 40) {
                surface = Blocks.DIRT_PATH.defaultBlockState();
            } else if (variant < 70) {
                surface = Blocks.COARSE_DIRT.defaultBlockState();
            } else {
                surface = Blocks.GRAVEL.defaultBlockState();
            }
        } else {
            // Default temperate
            if (variant < 50) {
                surface = Blocks.DIRT_PATH.defaultBlockState();
            } else if (variant < 80) {
                surface = Blocks.GRAVEL.defaultBlockState();
            } else {
                surface = Blocks.COARSE_DIRT.defaultBlockState();
            }
        }

        if (roadClass.getImportance() >= 0.8) {
            // Main roads use stone/cobblestone
            surface = Blocks.COBBLESTONE.defaultBlockState();
            base = Blocks.COBBLESTONE.defaultBlockState();
            width = Math.max(width, 5);
            return new RoadStyle(
                "temperate_main",
                roadClass,
                width,
                surface,
                Optional.of(base),
                Optional.of(Blocks.COBBLESTONE.defaultBlockState()),
                List.of(),
                profile
            );
        }

        base = Blocks.DIRT.defaultBlockState();
        return new RoadStyle(
            "temperate_path",
            roadClass,
            width,
            surface,
            Optional.of(base),
            Optional.empty(),
            List.of(),
            profile
        );
    }

    private static RoadStyle buildStoneStyle(RoadClass roadClass, int variant) {
        int width = Math.max(roadClass.getDefaultWidth(), 5);
        BlockState surface;
        BlockState base;

        if (variant < 30) {
            surface = Blocks.STONE_BRICKS.defaultBlockState();
        } else if (variant < 60) {
            surface = Blocks.COBBLESTONE.defaultBlockState();
        } else {
            surface = Blocks.SMOOTH_STONE.defaultBlockState();
        }

        base = Blocks.STONE.defaultBlockState();

        return new RoadStyle(
            "stone_historic",
            roadClass,
            width,
            surface,
            Optional.of(base),
            Optional.of(Blocks.COBBLESTONE.defaultBlockState()),
            List.of(),
            StyleProfile.STONE
        );
    }

    private static RoadStyle buildModernStyle(RoadClass roadClass, int variant) {
        int width = roadClass.getDefaultWidth();
        BlockState surface;

        if (variant < 30) {
            surface = Blocks.GRAY_CONCRETE.defaultBlockState();
        } else if (variant < 60) {
            surface = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
        } else {
            surface = Blocks.WHITE_CONCRETE.defaultBlockState();
        }

        BlockState base = Blocks.GRAY_CONCRETE.defaultBlockState();

        return new RoadStyle(
            "modern_developed",
            roadClass,
            width,
            surface,
            Optional.of(base),
            Optional.of(Blocks.BLACK_CONCRETE.defaultBlockState()),
            List.of(),
            StyleProfile.MODERN
        );
    }
}
