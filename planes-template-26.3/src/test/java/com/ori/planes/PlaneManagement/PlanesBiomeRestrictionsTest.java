package com.ori.planes.PlaneManagement;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.ori.planes.PlaneManagement.PlaneDefinition.ContentKey;
import com.ori.planes.PlaneManagement.Restrictions.PlaneContentPolicy;
import com.ori.planes.PlaneManagement.Restrictions.PlanesBiomeRestrictions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlanesBiomeRestrictionsTest {
    private final PlaneDefinition first = new PlaneDefinition(UUID.randomUUID(), "First",
            Set.of(new ContentKey("biomes", "minecraft:plains")), PlaneType.OVERWORLD);

    private JsonArray mappings() {
        return JsonParser.parseString("""
                [{"biome":"minecraft:plains","parameters":{"temperature":0.2}},
                 {"biome":"minecraft:forest","parameters":{"temperature":0.6}}]
                """).getAsJsonArray();
    }

    @Test
    void excludesRoutedBiomeFromOverworldWithoutChangingInput() {
        var original = mappings();
        var filtered = PlanesBiomeRestrictions.restrict(original, "minecraft:overworld",
                new PlaneContentPolicy(List.of(first)), List.of());
        assertEquals(1, filtered.size());
        assertEquals(original.get(1), filtered.get(0));
        assertEquals(2, original.size());
    }

    @Test
    void keepsBiomeAndClimateParametersInEveryAllowedPlane() {
        var second = new PlaneDefinition(UUID.randomUUID(), "Second", first.content(), PlaneType.OVERWORLD);
        var policy = new PlaneContentPolicy(List.of(first, second));
        for (var plane : List.of(first, second)) {
            assertEquals(mappings(), PlanesBiomeRestrictions.restrict(mappings(), "planes:plane_" + plane.id(),
                    policy, List.of("minecraft:plains")));
        }
    }

    @Test
    void rejectsUnknownPlacementInsteadOfSilentlyOmittingSelectedBiome() {
        assertThrows(IllegalArgumentException.class, () -> PlanesBiomeRestrictions.restrict(mappings(),
                "planes:plane_" + first.id(), new PlaneContentPolicy(List.of(first)), List.of("example:unknown")));
    }

    @Test
    void rejectsAnEmptyCandidateSet() {
        var routed = new PlaneDefinition(UUID.randomUUID(), "All",
                Set.of(new ContentKey("biomes", "minecraft:plains"), new ContentKey("biomes", "minecraft:forest")),
                PlaneType.OVERWORLD);
        assertThrows(IllegalArgumentException.class, () -> PlanesBiomeRestrictions.restrict(mappings(),
                "minecraft:overworld", new PlaneContentPolicy(List.of(routed)), List.of()));
    }

    @Test
    void unselectedBiomesAndFreshPoliciesRemainUnrestricted() {
        assertEquals(mappings(), PlanesBiomeRestrictions.restrict(mappings(), "minecraft:overworld",
                new PlaneContentPolicy(List.of()), List.of()));
    }

    @Test
    void copiesKnownClimateMappingsIntoDestinationWithoutDuplicatingThem() {
        var base = JsonParser.parseString("""
                [{"biome":"minecraft:forest","parameters":{"temperature":0.6}}]
                """).getAsJsonArray();
        var pool = mappings();
        pool.add(pool.get(0).deepCopy());
        var filtered = PlanesBiomeRestrictions.restrict(base, pool, "planes:plane_" + first.id(),
                new PlaneContentPolicy(List.of(first)), List.of("minecraft:plains"));
        assertEquals(2, filtered.size());
        assertEquals(mappings().get(0), filtered.get(1));
        assertEquals(1, base.size());
        assertEquals(3, pool.size());
    }

        @Test
        void regionMergeKeepsDistinctClimatesForTheSameBiome() {
                var region = JsonParser.parseString("""
                                [{"biome":"minecraft:forest","parameters":{"temperature":0.6}},
                                 {"biome":"biomesoplenty:bayou","parameters":{"temperature":0.8}},
                                 {"biome":"biomesoplenty:bayou","parameters":{"temperature":0.9}}]
                                """).getAsJsonArray();
                var result = PlanesBiomeRestrictions.mergeMappings(mappings(), region);
                assertEquals(4, result.size());
                assertEquals(region.get(1), result.get(2));
                assertEquals(region.get(2), result.get(3));
                assertEquals(2, mappings().size());
                assertEquals(3, region.size());
        }

        @Test
        void importedModdedBiomeIsRemovedOnlyOutsideItsAllowedPlane() {
                var region = JsonParser.parseString("""
                                [{"biome":"biomesoplenty:bayou","parameters":{"temperature":0.8}}]
                                """).getAsJsonArray();
                var plane = new PlaneDefinition(UUID.randomUUID(), "Bayou",
                                Set.of(new ContentKey("biomes", "biomesoplenty:bayou")), PlaneType.OVERWORLD);
                var policy = new PlaneContentPolicy(List.of(plane));
                var merged = PlanesBiomeRestrictions.mergeMappings(mappings(), region);
                assertEquals(mappings(), PlanesBiomeRestrictions.restrict(merged, "minecraft:overworld", policy, List.of()));
                assertEquals(merged, PlanesBiomeRestrictions.restrict(merged, "planes:plane_" + plane.id(), policy,
                                List.of("biomesoplenty:bayou")));
        }

        @Test
        void routedSourceContainsOnlyExplicitFilteredMappings() {
                var source = JsonParser.parseString("""
                                {"type":"minecraft:multi_noise","biomes":[
                                 {"biome":"biomesoplenty:bayou","parameters":{"temperature":0.8}}]}
                                """).getAsJsonObject();
                var wrapped = PlanesBiomeRestrictions.routedSource(source);
                assertEquals("planes:routed_multi_noise", wrapped.get("type").getAsString());
                assertEquals(source.get("biomes"), wrapped.getAsJsonObject("source").get("biomes"));
                assertFalse(wrapped.getAsJsonObject("source").has("type"));
                assertEquals("minecraft:multi_noise", source.get("type").getAsString());
        }

        @Test
        void selectedBiomeTakesPriorityOverVanillaAtIdenticalClimatePoints() {
                var overlapping = JsonParser.parseString("""
                                [{"biome":"minecraft:plains","parameters":{"temperature":0.2}},
                                 {"biome":"biomesoplenty:bayou","parameters":{"temperature":0.2}},
                                 {"biome":"minecraft:forest","parameters":{"temperature":0.6}}]
                                """).getAsJsonArray();
                var plane = new PlaneDefinition(UUID.randomUUID(), "Bayou",
                                Set.of(new ContentKey("biomes", "biomesoplenty:bayou")), PlaneType.OVERWORLD);
                var policy = new PlaneContentPolicy(List.of(plane));
                var destination = PlanesBiomeRestrictions.restrict(overlapping, "planes:plane_" + plane.id(), policy,
                                List.of("biomesoplenty:bayou"));
                assertEquals(2, destination.size());
                assertEquals("biomesoplenty:bayou", destination.get(0).getAsJsonObject().get("biome").getAsString());
                var overworld = PlanesBiomeRestrictions.restrict(overlapping, "minecraft:overworld", policy, List.of());
                assertEquals(mappings(), overworld);
        }
}