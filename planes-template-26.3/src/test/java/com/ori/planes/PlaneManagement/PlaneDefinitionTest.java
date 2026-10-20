

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ori.planes.PlaneManagement.PlaneDefinition;
import com.ori.planes.PlaneManagement.PlaneDefinition.ContentKey;
import com.ori.planes.PlaneManagement.PlaneType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaneDefinitionTest {
    @Test
    void trimsNamesAndPreservesIdentity() {
        UUID id = UUID.randomUUID();
        var plane = new PlaneDefinition(id, "  Dragon Plane  ", Set.of(), PlaneType.OVERWORLD);

        assertEquals(id, plane.id());
        assertEquals("Dragon Plane", plane.name());
    }

    @Test
    void rejectsBlankNames() {
        assertThrows(IllegalArgumentException.class,
                () -> new PlaneDefinition(UUID.randomUUID(), " \t ", Set.of(), PlaneType.OVERWORLD));
    }

    @Test
    void snapshotsEditorSelections() {
        var selections = new HashSet<ContentKey>();
        var block = new ContentKey("blocks", "minecraft:stone");
        selections.add(block);
        var plane = new PlaneDefinition(UUID.randomUUID(), "Stone", selections, PlaneType.OVERWORLD);
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
    void planeSeedIsStableAcrossReconstructionAndRenaming() {
        UUID id = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
        var original = new PlaneDefinition(id, "Original", Set.of(), PlaneType.OVERWORLD);
        var renamed = new PlaneDefinition(id, "Renamed", Set.of(), PlaneType.OVERWORLD);

        assertEquals(original.seedForWorld(42L), renamed.seedForWorld(42L));
        assertEquals(original.seedForWorld(42L), original.seedForWorld(42L));
        assertNotEquals(42L, original.seedForWorld(42L));
    }

    @Test
    void planeSeedDependsOnWorldSeedAndBothUuidHalves() {
        var original = new PlaneDefinition(new UUID(1L, 2L), "Original", Set.of(), PlaneType.OVERWORLD);
        var differentHighBits = new PlaneDefinition(new UUID(3L, 2L), "High", Set.of(), PlaneType.OVERWORLD);
        var differentLowBits = new PlaneDefinition(new UUID(1L, 3L), "Low", Set.of(), PlaneType.OVERWORLD);

        assertNotEquals(original.seedForWorld(42L), original.seedForWorld(43L));
        assertNotEquals(original.seedForWorld(42L), differentHighBits.seedForWorld(42L));
        assertNotEquals(original.seedForWorld(42L), differentLowBits.seedForWorld(42L));
        assertNotEquals(original.seedForWorld(Long.MIN_VALUE), original.seedForWorld(Long.MAX_VALUE));
    }

    @Test
    void editingACopyDoesNotChangeSavedPlane() {
        var original = new PlaneDefinition(UUID.randomUUID(), "Original", Set.of(), PlaneType.OVERWORLD);
        var selections = new HashSet<>(original.content());
        selections.add(new ContentKey("entities", "minecraft:pig"));
        var updated = new PlaneDefinition(original.id(), "Renamed", selections, PlaneType.OVERWORLD);

        assertTrue(original.content().isEmpty());
        assertEquals("Original", original.name());
        assertEquals(original.id(), updated.id());
        assertEquals(1, updated.content().size());
    }
}