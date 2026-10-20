package com.ori.planes.PlaneManagement;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record PlaneDefinition(UUID id, String name, Set<ContentKey> content, PlaneType type) {
    public PlaneDefinition {
        Objects.requireNonNull(id);
        name = name.strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("A plane must have a name");
        }
        content = Set.copyOf(content);
        Objects.requireNonNull(type);

    }

    public long seedForWorld(long worldSeed) {
        byte[] input = ByteBuffer.allocate(Long.BYTES * 3)
                .putLong(worldSeed)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits())
                .array();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input);
            return ByteBuffer.wrap(hash).getLong();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required for plane seeds", exception);
        }
    }

    public record ContentKey(String category, String id) {}
}