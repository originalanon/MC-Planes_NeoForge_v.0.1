package com.ori.planes.PlaneManagement;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record PlaneDefinition(UUID id, String name, Set<ContentKey> content) {
    public PlaneDefinition {
        Objects.requireNonNull(id);
        name = name.strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("A plane must have a name");
        }
        content = Set.copyOf(content);
    }

    public record ContentKey(String category, String id) {}
}