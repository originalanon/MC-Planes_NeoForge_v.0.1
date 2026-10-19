package com.ori.planes.PlaneManagement;

/**
 * @ Modified by: Your name
 * @ Modified time: 2026-10-19 16:45:29
 * Enum representing different types of datapack dimension types
 */
public enum PlaneType {
    OVERWORLD("minecraft:overworld"),
    THE_NETHER("minecraft:nether"),
    THE_END("minecraft:end"),
    CAVES("minecraft:caves"),
    AMPLIFIED("minecraft:amplified"),
    LARGE_BIOMES("minecraft:large_biomes"),
    FLOATING_ISLANDS("minecraft:floating_islands");

    private final String id;

    PlaneType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
