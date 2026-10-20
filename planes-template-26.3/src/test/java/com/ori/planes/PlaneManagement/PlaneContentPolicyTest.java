package com.ori.planes.PlaneManagement;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ori.planes.PlaneManagement.PlaneDefinition.ContentKey;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaneContentPolicyTest {
    private static final ContentKey PIG = new ContentKey("entities", "minecraft:pig");
    private static final UUID FIRST = new UUID(1L, 1L);
    private static final UUID SECOND = new UUID(2L, 2L);

    @Test
    void unassignedContentKeepsItsOriginalAvailability() {
        var policy = new PlaneContentPolicy(List.of(plane(FIRST, Set.of(PIG))));

        assertTrue(policy.allows("entities", "minecraft:cow", "minecraft:overworld"));
        assertTrue(policy.allows("entities", "minecraft:cow", dimension(SECOND)));
    }

    @Test
    void assignedEntityIsExcludedFromVanillaAndUnrelatedPlanes() {
        var policy = new PlaneContentPolicy(List.of(plane(FIRST, Set.of(PIG))));

        assertTrue(policy.allows("entities", PIG.id(), dimension(FIRST)));
        assertFalse(policy.allows("entities", PIG.id(), "minecraft:overworld"));
        assertFalse(policy.allows("entities", PIG.id(), "minecraft:the_nether"));
        assertFalse(policy.allows("entities", PIG.id(), dimension(SECOND)));
    }

    @Test
    void selectingTheSameEntityInMultiplePlanesUnionsDestinations() {
        var policy = new PlaneContentPolicy(List.of(plane(FIRST, Set.of(PIG)), plane(SECOND, Set.of(PIG))));

        assertTrue(policy.allows("entities", PIG.id(), dimension(FIRST)));
        assertTrue(policy.allows("entities", PIG.id(), dimension(SECOND)));
        assertFalse(policy.allows("entities", PIG.id(), "minecraft:overworld"));
    }

    @Test
    void sameRegistryIdInAnotherCategoryIsIndependent() {
        var block = new ContentKey("blocks", "test:resource");
        var item = new ContentKey("items", "test:resource");
        var policy = new PlaneContentPolicy(List.of(plane(FIRST, Set.of(block)), plane(SECOND, Set.of(item))));

        assertTrue(policy.allows("blocks", block.id(), dimension(FIRST)));
        assertFalse(policy.allows("items", item.id(), dimension(FIRST)));
        assertTrue(policy.allows("items", item.id(), dimension(SECOND)));
    }

    @Test
    void policyRebuildDoesNotRetainRemovedPackRules() {
        var oldPolicy = new PlaneContentPolicy(List.of(plane(FIRST, Set.of(PIG))));
        var reloaded = new PlaneContentPolicy(List.of());

        assertFalse(oldPolicy.allows("entities", PIG.id(), "minecraft:overworld"));
        assertTrue(reloaded.allows("entities", PIG.id(), "minecraft:overworld"));
    }

    @Test
    void policySnapshotDoesNotFollowSourceListChanges() {
        var definitions = new ArrayList<>(List.of(plane(FIRST, Set.of(PIG))));
        var policy = new PlaneContentPolicy(definitions);
        definitions.clear();

        assertFalse(policy.allows("entities", PIG.id(), "minecraft:overworld"));
        assertTrue(policy.allows("entities", PIG.id(), dimension(FIRST)));
    }

    private static PlaneDefinition plane(UUID id, Set<ContentKey> content) {
        return new PlaneDefinition(id, "Test Plane", content, PlaneType.OVERWORLD);
    }

    private static String dimension(UUID id) {
        return "planes:plane_" + id;
    }
}