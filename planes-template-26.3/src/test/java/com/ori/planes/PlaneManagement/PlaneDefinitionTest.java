

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