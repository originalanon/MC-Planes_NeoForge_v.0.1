package com.ori.planes.PlaneManagement.Restrictions;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;

public final class TerraBlenderBiomeMappings {
    public static Map<String, JsonArray> collect(RegistryAccess.Frozen registries) throws IOException {
        Class<?> regionsClass;
        try {
            regionsClass = Class.forName("terrablender.api.Regions");
        } catch (ClassNotFoundException exception) {
            return Map.of();
        }

        try {
            Class<?> regionClass = Class.forName("terrablender.api.Region");
            Class<?> regionTypeClass = Class.forName("terrablender.api.RegionType");
            var getRegions = regionsClass.getMethod("get", regionTypeClass);
            var addBiomes = regionClass.getMethod("addBiomes", Registry.class, Consumer.class);
            var getWeight = regionClass.getMethod("getWeight");
            Object placeholder = regionClass.getField("DEFERRED_PLACEHOLDER").get(null);
            var biomeRegistry = registries.lookupOrThrow(Registries.BIOME);
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
            var result = new LinkedHashMap<String, JsonArray>();
            for (String type : new String[] {"OVERWORLD", "NETHER"}) {
                Object regionType = regionTypeClass.getField(type).get(null);
                Object registered = getRegions.invoke(null, regionType);
                if (!(registered instanceof Iterable<?> regions)) {
                    throw new IOException("TerraBlender returned an unsupported region collection");
                }
                var mappings = new ArrayList<Pair<Climate.ParameterPoint, Holder<Biome>>>();
                Consumer<Object> collector = value -> {
                    if (!(value instanceof Pair<?, ?> pair)
                            || !(pair.getFirst() instanceof Climate.ParameterPoint parameters)
                            || !(pair.getSecond() instanceof ResourceKey<?> key)) {
                        throw new IllegalArgumentException("Unexpected TerraBlender climate mapping");
                    }
                    if (!key.equals(placeholder)) {
                        biomeRegistry.get(ResourceKey.create(Registries.BIOME, key.identifier()))
                                .ifPresent(biome -> mappings.add(Pair.of(parameters, biome)));
                    }
                };
                for (Object region : regions) {
                    if (((Number) getWeight.invoke(region)).intValue() > 0) {
                        addBiomes.invoke(region, biomeRegistry, collector);
                    }
                }
                if (!mappings.isEmpty()) {
                    JsonElement encoded = MultiNoiseBiomeSource.DIRECT_CODEC.codec()
                            .encodeStart(ops, new Climate.ParameterList<>(mappings))
                            .getOrThrow(error -> new IOException("Could not encode TerraBlender mappings: " + error));
                    result.put("minecraft:" + type.toLowerCase(java.util.Locale.ROOT),
                            encoded.getAsJsonObject().getAsJsonArray("biomes"));
                }
            }
            return Map.copyOf(result);
        } catch (InvocationTargetException exception) {
            throw new IOException("TerraBlender could not provide its biome climate mappings", exception.getCause());
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IOException("This TerraBlender region API is not supported", exception);
        }
    }

    private TerraBlenderBiomeMappings() {}
}