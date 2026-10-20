package com.ori.planes.PlaneManagement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.ori.planes.Planes;
import com.ori.planes.PlaneManagement.PlaneDefinition.ContentKey;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.resource.ListenerKey;

@EventBusSubscriber(modid = Planes.MODID)
public final class PlaneSpawnRestrictions extends SimplePreparableReloadListener<PlaneContentPolicy> {
    private static final Gson GSON = new Gson();
    private static final ListenerKey<PlaneSpawnRestrictions> KEY = ListenerKey.create(
            Identifier.fromNamespaceAndPath(Planes.MODID, "plane_spawn_restrictions"));
    private volatile PlaneContentPolicy policy = new PlaneContentPolicy(List.of());

    private record PackPlane(int version, UUID id, String name, PlaneType type, Set<ContentKey> content) {}

    @SubscribeEvent
    public static void registerReloadListener(AddServerReloadListenersEvent event) {
        event.addRetainedListener(KEY, new PlaneSpawnRestrictions());
    }

    @Override
    protected PlaneContentPolicy prepare(ResourceManager manager, ProfilerFiller profiler) {
        var definitions = new ArrayList<PlaneDefinition>();
        var resources = manager.listResources("plane_definitions", id -> id.getNamespace().equals(Planes.MODID)
                && id.getPath().endsWith(".json"));
        for (var resource : resources.entrySet()) {
            try (var reader = resource.getValue().openAsReader()) {
                PackPlane stored = GSON.fromJson(reader, PackPlane.class);
                if (stored == null || stored.version() != 1 || stored.id() == null || stored.content() == null) {
                    throw new IllegalArgumentException("Missing fields or unsupported plane definition version");
                }
                if (!resource.getKey().getPath().equals("plane_definitions/" + stored.id() + ".json")) {
                    throw new IllegalArgumentException("Plane ID does not match its resource path");
                }
                for (var entry : stored.content()) {
                    if (entry == null || entry.category() == null || entry.category().isBlank()
                            || Identifier.tryParse(entry.id()) == null) {
                        throw new IllegalArgumentException("Invalid plane content entry");
                    }
                }
                definitions.add(new PlaneDefinition(stored.id(), stored.name(), stored.content(), stored.type()));
            } catch (IOException | RuntimeException exception) {
                throw new JsonParseException("Could not load plane exclusion rules from " + resource.getKey(), exception);
            }
        }
        long unsupported = definitions.stream().flatMap(plane -> plane.content().stream())
                .filter(entry -> !entry.category().equals("entities")).distinct().count();
        if (unsupported > 0) {
            Planes.LOGGER.warn("{} selected non-entity entries have no exclusion enforcement yet; "
                    + "this build restricts natural/chunk-generation mob spawns only", unsupported);
        }
        Planes.LOGGER.info("Loaded entity exclusion routes from {} enabled plane definitions", definitions.size());
        return new PlaneContentPolicy(definitions);
    }

    @Override
    protected void apply(PlaneContentPolicy preparations, ResourceManager manager, ProfilerFiller profiler) {
        policy = preparations;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void filterNaturalSpawns(LevelEvent.PotentialSpawns event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var policy = level.getServer().getServerResources().managers().getListener(KEY).policy;
            String dimension = level.dimension().identifier().toString();
            for (var spawn : List.copyOf(event.getSpawnerDataList())) {
                String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(spawn.value().type()).toString();
                if (!policy.allows("entities", entityId, dimension)) {
                    event.removeSpawnerData(spawn);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void checkSpawnPlacement(MobSpawnEvent.SpawnPlacementCheck event) {
        if (isNaturalGeneration(event.getSpawnType()) && !allows(event.getLevel(), event.getEntityType())) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void checkFinalSpawn(FinalizeSpawnEvent event) {
        if (isNaturalGeneration(event.getSpawnType()) && !allows(event.getLevel(), event.getEntity().getType())) {
            event.setSpawnCancelled(true);
        }
    }

    private static boolean isNaturalGeneration(EntitySpawnReason reason) {
        return reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION;
    }

    private static boolean allows(ServerLevelAccessor accessor, EntityType<?> entityType) {
        ServerLevel level = accessor.getLevel();
        var policy = level.getServer().getServerResources().managers().getListener(KEY).policy;
        return policy.allows("entities", BuiltInRegistries.ENTITY_TYPE.getKey(entityType).toString(),
                level.dimension().identifier().toString());
    }
}