/**
 * @ Author: OriginalAnon (Lindsay Barton)
 * @ Create Time: 2026-10-18 06:19:19
 * @ Modified by: Your name
 * @ Modified time: 2026-10-18 12:39:33
 * @ Description: Grab mod content from the game and mods. Shows up in the PlaneContentScreen.
 */

package com.ori.planes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.resources.ResourceKey;
import net.neoforged.fml.ModList;

final class PlaneContentCatalog {
    record Content(String category, String id) {}
    record ModContent(String id, String name, List<Content> entries) {}

    static List<ModContent> scan(WorldCreationContext context) {
        var names = new TreeMap<String, String>();
        ModList.get().getMods().forEach(mod -> names.put(mod.getModId(), mod.getDisplayName()));
        var content = new TreeMap<String, List<Content>>();
        names.keySet().forEach(id -> content.put(id, new ArrayList<>()));
        collect(content, "blocks", BuiltInRegistries.BLOCK);
        collect(content, "items", BuiltInRegistries.ITEM);
        collect(content, "entities", BuiltInRegistries.ENTITY_TYPE);
        collectWorldgen(content, context, "biomes", Registries.BIOME);
        collectWorldgen(content, context, "configured_features", Registries.FEATURE);
        collectWorldgen(content, context, "placed_features", Registries.PLACED_FEATURE);
        collectWorldgen(content, context, "structures", Registries.STRUCTURE);
        collectWorldgen(content, context, "structure_sets", Registries.STRUCTURE_SET);
        collectWorldgen(content, context, "dimension_types", Registries.DIMENSION_TYPE);
        collect(content, "dimensions", context.datapackDimensions());
        
        //Remove any mods that have no content entries. Prevents libraries from being loaded
        content.values().removeIf(List::isEmpty);
        return content.entrySet().stream()
                .map(entry -> new ModContent(entry.getKey(), names.getOrDefault(entry.getKey(), entry.getKey()),
                        entry.getValue().stream().sorted(Comparator.comparing(Content::category)
                                .thenComparing(Content::id)).toList()))
                .sorted(Comparator.comparing(ModContent::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static <T> void collectWorldgen(TreeMap<String, List<Content>> content,
            WorldCreationContext context, String category, ResourceKey<? extends Registry<T>> key) {
        context.worldgenRegistries().compositeAccess().lookup(key)
                .ifPresent(registry -> collect(content, category, registry));
    }

    private static void collect(TreeMap<String, List<Content>> content, String category, Registry<?> registry) {
        registry.keySet().forEach(id -> content.computeIfAbsent(id.getNamespace(), key -> new ArrayList<>())
                .add(new Content(category, id.toString())));
    }

    private PlaneContentCatalog() {}
}