package com.ori.planes.PlaneManagement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ori.planes.PlaneManagement.PlaneDefinition.ContentKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaneDefinitionStoreTest {
    @TempDir
    Path gameDirectory;

    @Test
    void savesAndLoadsAPlaneDefinition() throws IOException {
        var plane = new PlaneDefinition(UUID.randomUUID(), "Dragon Plane",
                Set.of(new ContentKey("entities", "iceandfire:dragon")), PlaneType.CAVES);

        PlaneDefinitionStore.save(gameDirectory, plane);

        assertTrue(Files.isRegularFile(gameDirectory.resolve("planes").resolve(plane.id() + ".json")));
        assertEquals(java.util.List.of(plane), PlaneDefinitionStore.loadAll(gameDirectory));
    }

    @Test
    void savingAnExistingIdReplacesItsDefinition() throws IOException {
        UUID id = UUID.randomUUID();
        var original = new PlaneDefinition(id, "Original", Set.of(), PlaneType.OVERWORLD);
        var updated = new PlaneDefinition(id, "Updated", Set.of(), PlaneType.AMPLIFIED);

        PlaneDefinitionStore.save(gameDirectory, original);
        PlaneDefinitionStore.save(gameDirectory, updated);

        assertEquals(java.util.List.of(updated), PlaneDefinitionStore.loadAll(gameDirectory));
        try (var files = Files.list(gameDirectory.resolve("planes"))) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void deleteRemovesOnlyTheRequestedDefinition() throws IOException {
        var first = new PlaneDefinition(UUID.randomUUID(), "First", Set.of(), PlaneType.OVERWORLD);
        var second = new PlaneDefinition(UUID.randomUUID(), "Second", Set.of(), PlaneType.NETHER);
        PlaneDefinitionStore.save(gameDirectory, first);
        PlaneDefinitionStore.save(gameDirectory, second);

        assertTrue(PlaneDefinitionStore.delete(gameDirectory, first.id()));
        assertFalse(PlaneDefinitionStore.delete(gameDirectory, first.id()));
        assertEquals(java.util.List.of(second), PlaneDefinitionStore.loadAll(gameDirectory));
    }
}