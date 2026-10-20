package com.ori.planes.PlaneManagement;

/**
 * @ Modified by: Your name
 * @ Modified time: 2026-10-19 20:12:19
 * Enum representing different types of datapack dimension types
 */
public enum PlaneType {
    OVERWORLD("minecraft:normal", "minecraft:overworld", "minecraft:overworld"),
    THE_NETHER("minecraft:normal", "minecraft:the_nether", "minecraft:the_nether"),
    THE_END("minecraft:normal", "minecraft:the_end", "minecraft:the_end"),
    CAVES("minecraft:normal", "minecraft:overworld", "minecraft:overworld_caves"),
    AMPLIFIED("minecraft:amplified", "minecraft:overworld", "minecraft:overworld"),
    LARGE_BIOMES("minecraft:large_biomes", "minecraft:overworld", "minecraft:overworld"),
    FLOATING_ISLANDS("minecraft:normal", "minecraft:the_end", "minecraft:the_end");

    private final String presetId;
    private final String stemId;
    private final String dimensionTypeId;

    PlaneType(String presetId, String stemId, String dimensionTypeId) {
        this.presetId = presetId;
        this.stemId = stemId;
        this.dimensionTypeId = dimensionTypeId;
    }

    public String presetId() {
        return presetId;
    }

    public String stemId() {
        return stemId;
    }

    public String dimensionTypeId() {
        return dimensionTypeId;
    }
}
