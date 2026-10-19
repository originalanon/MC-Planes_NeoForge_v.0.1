package com.ori.planes.PlaneManagement;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ori.planes.PlaneManagement.PlaneDefinition.ContentKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaneDefinitionTest {
    @Test
    void trimsNamesAndPreservesIdentity() {
        UUID id = UUID.randomUUID();
        var plane = new PlaneDefinition(id, "  Dragon Plane  ", Set.of());

        assertEquals(id, plane.id());
        assertEquals("Dragon Plane", plane.name());
    }

    @Test
    void rejectsBlankNames() {
        assertThrows(IllegalArgumentException.class,
                () -> new PlaneDefinition(UUID.randomUUID(), " \t ", Set.of()));
    }

    @Test
    void snapshotsEditorSelections() {
        var selections = new HashSet<ContentKey>();
        var block = new ContentKey("blocks", "minecraft:stone");
        selections.add(block);
        var plane = new PlaneDefinition(UUID.randomUUID(), "Stone", selections);
        selections.clear();

        assertEquals(Set.of(block), plane.content());
        assertThrows(UnsupportedOperationException.class, () -> plane.content().clear());
    }

    @Test
    void distinguishesBlockAndItemWithSameId() {
        var block = new ContentKey("blocks", "minecraft:stone");
        var item = new ContentKey("items", "minecraft:stone");

        assertNotEquals(block, item);
        assertEquals(2, Set.of(block, item).size());
    }

    @Test
    void editingACopyDoesNotChangeSavedPlane() {
        var original = new PlaneDefinition(UUID.randomUUID(), "Original", Set.of());
        var selections = new HashSet<>(original.content());
        selections.add(new ContentKey("entities", "minecraft:pig"));
        var updated = new PlaneDefinition(original.id(), "Renamed", selections);

        assertTrue(original.content().isEmpty());
        assertEquals("Original", original.name());
        assertEquals(original.id(), updated.id());
        assertEquals(1, updated.content().size());
    }
}