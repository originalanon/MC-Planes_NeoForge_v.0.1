package com.ori.planes.PlaneManagement;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import net.minecraft.SharedConstants;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;

public final class PlaneDatapackGenerator {
    private static final int DEFINITION_FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private record PackPlane(int version, String id, String name, PlaneType type,
            List<PlaneDefinition.ContentKey> content) {}

    public static Path generate(CreateWorldScreen worldScreen, PlaneDefinition plane) throws IOException {
        Path datapackRoot = worldScreen.getOrCreateTempDataPackDir();
        if (datapackRoot == null) {
            throw new IOException("Minecraft did not provide a temporary datapack directory");
        }

        String packName = "planes_plane_" + plane.id();
        Path target = datapackRoot.resolve(packName);
        Path staging = Files.createTempDirectory(datapackRoot, ".planes-staging-");
        Path backup = null;
        try {
            writePack(staging, plane);
            if (Files.exists(target)) {
                backup = Files.createTempDirectory(datapackRoot, ".planes-backup-");
                Files.delete(backup);
                Files.move(target, backup);
            }

            try {
                moveDirectory(staging, target);
            } catch (IOException exception) {
                if (backup != null && Files.exists(backup)) {
                    Files.move(backup, target);
                    backup = null;
                }
                throw exception;
            }

            if (backup != null) {
                deleteTree(backup);
            }
            worldScreen.tempDataPackRepository = null;
            return target;
        } finally {
            if (Files.exists(staging)) {
                deleteTree(staging);
            }
            if (backup != null && Files.exists(backup)) {
                deleteTree(backup);
            }
        }
    }

    public static void remove(CreateWorldScreen worldScreen, PlaneDefinition plane) throws IOException {
        Path datapackRoot = worldScreen.getOrCreateTempDataPackDir();
        if (datapackRoot != null) {
            deleteTree(datapackRoot.resolve("planes_plane_" + plane.id()));
            worldScreen.tempDataPackRepository = null;
        }
    }

    private static void writePack(Path root, PlaneDefinition plane) throws IOException {
        var packMetadata = new JsonObject();
        var metadata = new PackMetadataSection(Component.literal("Planes: " + plane.name()),
            SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA).minorRange());
        JsonElement section = PackMetadataSection.SERVER_TYPE.codec()
            .encodeStart(JsonOps.INSTANCE, metadata)
                .getOrThrow();
        packMetadata.add(PackMetadataSection.SERVER_TYPE.name(), section);
        Files.writeString(root.resolve("pack.mcmeta"), GSON.toJson(packMetadata), StandardCharsets.UTF_8);

        Path definitionPath = root.resolve("data/planes/plane_definitions").resolve(plane.id() + ".json");
        Files.createDirectories(definitionPath.getParent());
        var content = plane.content().stream()
                .sorted(Comparator.comparing(PlaneDefinition.ContentKey::category)
                        .thenComparing(PlaneDefinition.ContentKey::id))
                .toList();
        var definition = new PackPlane(DEFINITION_FORMAT_VERSION, plane.id().toString(), plane.name(), plane.type(), content);
        Files.writeString(definitionPath, GSON.toJson(definition), StandardCharsets.UTF_8);
    }

    private static void moveDirectory(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private static void deleteTree(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private PlaneDatapackGenerator() {}
}