package com.ori.planes.PlaneManagement;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalLong;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.WeakHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.mojang.serialization.JsonOps;
import com.ori.planes.PlaneManagement.Restrictions.PlaneContentPolicy;
import com.ori.planes.PlaneManagement.Restrictions.PlanesBiomeRestrictions;
import com.ori.planes.PlaneManagement.Restrictions.TerraBlenderBiomeMappings;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;

public final class PlaneDatapackGenerator {
    private static final int DEFINITION_FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<CreateWorldScreen, GenerationState> STATES = new WeakHashMap<>();

    private record GenerationState(Map<UUID, PlaneDefinition> definitions, Map<String, JsonObject> originalDimensions) {}

    private record PackPlane(int version, String id, String name, PlaneType type,
            List<PlaneDefinition.ContentKey> content) {}

    public static Path generate(CreateWorldScreen worldScreen, PlaneDefinition plane) throws IOException {
        GenerationState previous = stateFor(worldScreen);
        var definitions = new LinkedHashMap<>(previous.definitions());
        definitions.put(plane.id(), plane);
        return publish(worldScreen, new GenerationState(Map.copyOf(definitions), previous.originalDimensions()));
    }

    public static Path generateAll(CreateWorldScreen worldScreen, List<PlaneDefinition> planes) throws IOException {
        GenerationState previous = stateFor(worldScreen);
        var definitions = new LinkedHashMap<UUID, PlaneDefinition>();
        planes.forEach(plane -> definitions.put(plane.id(), plane));
        return publish(worldScreen, new GenerationState(Map.copyOf(definitions), previous.originalDimensions()));
    }

    private static GenerationState stateFor(CreateWorldScreen screen) throws IOException {
        GenerationState existing = STATES.get(screen);
        if (existing != null) {
            return existing;
        }
        var dimensions = new LinkedHashMap<String, LevelStem>();
        var context = screen.getUiState().getSettings();
        context.selectedDimensions().dimensions().forEach((key, value) -> dimensions.put(key.identifier().toString(), value));
        context.datapackDimensions().entrySet().forEach(entry -> {
            String id = entry.getKey().identifier().toString();
            if (!id.startsWith("planes:plane_")) {
                dimensions.put(id, entry.getValue());
            }
        });
        var ops = RegistryOps.create(JsonOps.INSTANCE, context.worldgenLoadContext());
        var snapshots = new LinkedHashMap<String, JsonObject>();
        for (var entry : dimensions.entrySet()) {
            JsonObject snapshot = LevelStem.CODEC.encodeStart(ops, entry.getValue())
                .getOrThrow(error -> new IOException("Could not snapshot " + entry.getKey() + ": " + error))
                .getAsJsonObject();
            snapshots.put(entry.getKey(), snapshot);
        }
        return new GenerationState(Map.of(), Map.copyOf(snapshots));
    }

    private static Path publish(CreateWorldScreen worldScreen, GenerationState state) throws IOException {
        Path datapackRoot = worldScreen.getOrCreateTempDataPackDir();
        if (datapackRoot == null) {
            throw new IOException("Minecraft did not provide a temporary datapack directory");
        }

        Path target = datapackRoot.resolve("planes_generated");
        Path staging = Files.createTempDirectory(datapackRoot, ".planes-staging-");
        Path backup = null;
        try {
            var settings = worldScreen.getUiState().getSettings();
            try {
                writePack(staging, state, settings.worldgenLoadContext(), settings.options().seed());
            } catch (RuntimeException exception) {
                throw new IOException("Could not generate biome routing: " + exception.getMessage(), exception);
            }
            if (Files.exists(target)) {
                backup = Files.createTempDirectory(datapackRoot, ".planes-backup-");
                Files.delete(backup);
                Files.move(target, backup);
            }

            try {
                moveDirectory(staging, target);
            } catch (IOException exception) {
                if (backup != null && Files.exists(backup)) {
                    Path recovery = backup;
                    backup = null;
                    try {
                        Files.move(recovery, target);
                    } catch (IOException recoveryException) {
                        exception.addSuppressed(recoveryException);
                    }
                }
                throw exception;
            }

            if (backup != null) {
                deleteTree(backup);
            }
            STATES.put(worldScreen, state);
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
        GenerationState previous = stateFor(worldScreen);
        var definitions = new LinkedHashMap<>(previous.definitions());
        definitions.remove(plane.id());
        publish(worldScreen, new GenerationState(Map.copyOf(definitions), previous.originalDimensions()));
    }

    private static void writePack(Path root, GenerationState state, RegistryAccess.Frozen registries,
            long worldSeed) throws IOException {
        var packMetadata = new JsonObject();
        var metadata = new PackMetadataSection(Component.literal("Planes: dimensions and biome routing"),
            SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA).minorRange());
        JsonElement section = PackMetadataSection.SERVER_TYPE.codec()
            .encodeStart(JsonOps.INSTANCE, metadata)
                .getOrThrow();
        packMetadata.add(PackMetadataSection.SERVER_TYPE.name(), section);
        Files.writeString(root.resolve("pack.mcmeta"), GSON.toJson(packMetadata), StandardCharsets.UTF_8);

        var policy = new PlaneContentPolicy(state.definitions().values());
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        var encoded = new LinkedHashMap<String, JsonObject>();
        state.originalDimensions().forEach((id, dimension) -> encoded.put(id, dimension.deepCopy()));
        var required = new LinkedHashMap<String, Set<String>>();
        for (var plane : state.definitions().values().stream().sorted(Comparator.comparing(PlaneDefinition::id)).toList()) {
            String dimension = "planes:plane_" + plane.id();
                var stem = createLevelStem(plane.type(), registries, plane.seedForWorld(worldSeed));
                encoded.put(dimension, LevelStem.CODEC.encodeStart(ops, stem)
                    .getOrThrow(error -> new IOException("Could not encode " + dimension + ": " + error))
                    .getAsJsonObject());
            var biomes = new HashSet<String>();
            plane.content().stream().filter(entry -> entry.category().equals("biomes"))
                    .forEach(entry -> biomes.add(entry.id()));
            required.put(dimension, biomes);
            writeDefinition(root, plane);
        }

        boolean hasRoutes = required.values().stream().anyMatch(biomes -> !biomes.isEmpty());
        Map<String, JsonArray> regionMappings = hasRoutes ? TerraBlenderBiomeMappings.collect(registries) : Map.of();
        var pool = new JsonArray();
        for (var entry : encoded.entrySet()) {
            JsonObject dimension = entry.getValue();
            JsonObject source = dimension.getAsJsonObject("generator").getAsJsonObject("biome_source");
            if (hasRoutes && source != null && source.get("type").getAsString().equals("minecraft:multi_noise")) {
                if (source.has("preset")) {
                    String presetId = source.get("preset").getAsString();
                    var key = ResourceKey.create(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST,
                        Identifier.parse(presetId));
                    var parameters = registries.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                            .getOrThrow(key).value().parameters();
                    JsonObject direct = MultiNoiseBiomeSource.DIRECT_CODEC.codec().encodeStart(ops, parameters)
                            .getOrThrow(error -> new IOException("Could not expand biome preset: " + error)).getAsJsonObject();
                    source.remove("preset");
                        source.add("biomes", PlanesBiomeRestrictions.mergeMappings(direct.getAsJsonArray("biomes"),
                            regionMappings.getOrDefault(presetId, new JsonArray())));
                }
                source.getAsJsonArray("biomes").forEach(mapping -> pool.add(mapping.deepCopy()));
            }
        }

        for (var entry : encoded.entrySet()) {
            String dimensionId = entry.getKey();
            JsonObject dimension = entry.getValue();
            JsonObject source = dimension.getAsJsonObject("generator").getAsJsonObject("biome_source");
            Set<String> selected = required.getOrDefault(dimensionId, Set.of());
            if (hasRoutes && source != null && source.get("type").getAsString().equals("minecraft:multi_noise")) {
                source.add("biomes", PlanesBiomeRestrictions.restrict(source.getAsJsonArray("biomes"),
                        pool, dimensionId, policy, selected));
                if (!regionMappings.isEmpty()) {
                    dimension.getAsJsonObject("generator").add("biome_source", PlanesBiomeRestrictions.routedSource(source));
                }
            } else if (hasRoutes) {
                var available = new HashSet<String>();
                var stem = LevelStem.CODEC.parse(ops, dimension)
                        .getOrThrow(error -> new IOException("Invalid dimension " + dimensionId + ": " + error));
                for (var biome : stem.generator().getBiomeSource().possibleBiomes()) {
                    String id = biome.unwrapKey().orElseThrow().identifier().toString();
                    available.add(id);
                    if (!policy.allows("biomes", id, dimensionId)) {
                        throw new IOException("Biome source for " + dimensionId + " cannot exclude " + id
                                + "; this source is not supported by datapack biome routing yet");
                    }
                }
                if (!available.containsAll(selected)) {
                    throw new IOException("Biome source for " + dimensionId + " cannot place the selected biomes");
                }
            }
            if (hasRoutes || required.containsKey(dimensionId)) {
                Identifier id = Identifier.parse(dimensionId);
                Path path = root.resolve("data").resolve(id.getNamespace()).resolve("dimension").resolve(id.getPath() + ".json");
                Files.createDirectories(path.getParent());
                Files.writeString(path, GSON.toJson(dimension), StandardCharsets.UTF_8);
                LevelStem.CODEC.parse(ops, dimension)
                        .getOrThrow(error -> new IOException("Invalid routed dimension " + dimensionId + ": " + error));
            }
        }
    }

    private static void writeDefinition(Path root, PlaneDefinition plane) throws IOException {
        Path definitionPath = root.resolve("data/planes/plane_definitions").resolve(plane.id() + ".json");
        Files.createDirectories(definitionPath.getParent());
        var content = plane.content().stream()
                .sorted(Comparator.comparing(PlaneDefinition.ContentKey::category)
                        .thenComparing(PlaneDefinition.ContentKey::id))
                .toList();
        var definition = new PackPlane(DEFINITION_FORMAT_VERSION, plane.id().toString(), plane.name(), plane.type(), content);
        Files.writeString(definitionPath, GSON.toJson(definition), StandardCharsets.UTF_8);
    }

        private static LevelStem createLevelStem(PlaneType planeType, RegistryAccess.Frozen registries,
            long seedOverride) throws IOException {
        ResourceKey<WorldPreset> presetKey = ResourceKey.create(Registries.WORLD_PRESET,
            Identifier.parse(planeType.presetId()));
        WorldPreset preset = registries.lookupOrThrow(Registries.WORLD_PRESET).get(presetKey)
            .map(Holder.Reference::value)
            .orElseThrow(() -> new IOException("Missing world preset " + planeType.presetId()));

        ResourceKey<LevelStem> stemKey = ResourceKey.create(Registries.LEVEL_STEM,
            Identifier.parse(planeType.stemId()));
        LevelStem source = preset.createWorldDimensions().get(stemKey)
            .orElseThrow(() -> new IOException("Preset " + planeType.presetId()
                + " has no dimension stem " + planeType.stemId()));

        ResourceKey<DimensionType> dimensionTypeKey = ResourceKey.create(Registries.DIMENSION_TYPE,
            Identifier.parse(planeType.dimensionTypeId()));
        Holder<DimensionType> dimensionType = registries.lookupOrThrow(Registries.DIMENSION_TYPE).get(dimensionTypeKey)
            .orElseThrow(() -> new IOException("Missing dimension type " + planeType.dimensionTypeId()));
        return new LevelStem(dimensionType, source.generator(), OptionalLong.of(seedOverride));
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