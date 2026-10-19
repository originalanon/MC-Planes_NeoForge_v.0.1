package com.ori.planes.PlaneManagement;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ori.planes.Planes;
import com.ori.planes.PlaneManagement.PlaneDefinition.ContentKey;

public final class PlaneDefinitionStore {
    private static final int CURRENT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private record StoredPlane(int version, String name, PlaneType type, List<ContentKey> content) {}

    public static List<PlaneDefinition> loadAll(Path gameDirectory) throws IOException {
        Path directory = gameDirectory.resolve("planes");
        if (!Files.exists(directory)) {
            return List.of();
        }

        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(PlaneDefinitionStore::read)
                    .filter(plane -> plane != null)
                    .sorted(Comparator.comparing(PlaneDefinition::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
    }

    public static void save(Path gameDirectory, PlaneDefinition plane) throws IOException {
        Path directory = gameDirectory.resolve("planes");
        Files.createDirectories(directory);
        Path target = directory.resolve(plane.id() + ".json");
        Path temporary = Files.createTempFile(directory, plane.id() + ".", ".tmp");
        var content = plane.content().stream()
                .sorted(Comparator.comparing(ContentKey::category).thenComparing(ContentKey::id))
                .toList();
        var stored = new StoredPlane(CURRENT_VERSION, plane.name(), plane.type(), content);

        try {
            Files.writeString(temporary, GSON.toJson(stored), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static boolean delete(Path gameDirectory, UUID id) throws IOException {
        return Files.deleteIfExists(gameDirectory.resolve("planes").resolve(id + ".json"));
    }

    private static PlaneDefinition read(Path file) {
        try {
            String filename = file.getFileName().toString();
            UUID id = UUID.fromString(filename.substring(0, filename.length() - ".json".length()));
            StoredPlane stored = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), StoredPlane.class);
            if (stored == null || stored.version() != CURRENT_VERSION) {
                throw new IllegalArgumentException("Unsupported or empty plane definition");
            }
            return new PlaneDefinition(id, stored.name(), java.util.Set.copyOf(stored.content()), stored.type());
        } catch (IOException | RuntimeException exception) {
            Planes.LOGGER.warn("Skipping invalid plane definition file {}", file, exception);
            return null;
        }
    }

    private PlaneDefinitionStore() {}
}