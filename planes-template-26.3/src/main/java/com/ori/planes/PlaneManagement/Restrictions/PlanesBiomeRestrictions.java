package com.ori.planes.PlaneManagement.Restrictions;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class PlanesBiomeRestrictions {
	public static JsonObject routedSource(JsonObject source) {
		if (!source.get("type").getAsString().equals("minecraft:multi_noise") || source.has("preset")) {
			throw new IllegalArgumentException("A routed biome source requires explicit multi-noise climate mappings");
		}
		var wrapped = new JsonObject();
		wrapped.addProperty("type", "planes:routed_multi_noise");
		var contents = source.deepCopy();
		contents.remove("type");
		wrapped.add("source", contents);
		return wrapped;
	}

	public static JsonArray mergeMappings(JsonArray original, JsonArray additions) {
		var result = new JsonArray();
		var unique = new HashSet<JsonElement>();
		for (var mapping : original) {
			if (unique.add(mapping)) {
				result.add(mapping.deepCopy());
			}
		}
		for (var mapping : additions) {
			if (unique.add(mapping)) {
				result.add(mapping.deepCopy());
			}
		}
		return result;
	}

	public static JsonArray restrict(JsonArray mappings, String dimension, PlaneContentPolicy policy,
			Collection<String> requiredBiomes) {
		return restrict(mappings, new JsonArray(), dimension, policy, requiredBiomes);
	}

	public static JsonArray restrict(JsonArray mappings, JsonArray placementPool, String dimension,
			PlaneContentPolicy policy, Collection<String> requiredBiomes) {
		var candidates = mappings.deepCopy();
		var missing = new HashSet<>(requiredBiomes);
		mappings.forEach(mapping -> missing.remove(mapping.getAsJsonObject().get("biome").getAsString()));
		var copied = new HashSet<JsonElement>();
		for (var mapping : placementPool) {
			if (missing.contains(mapping.getAsJsonObject().get("biome").getAsString()) && copied.add(mapping)) {
				candidates.add(mapping.deepCopy());
			}
		}
		var result = new JsonArray();
		Set<String> included = new HashSet<>();
		for (var element : candidates) {
			JsonObject entry = element.getAsJsonObject();
			String biome = entry.get("biome").getAsString();
			if (policy.allows("biomes", biome, dimension)) {
				result.add(entry.deepCopy());
				included.add(biome);
			}
		}
		if (!included.containsAll(requiredBiomes)) {
			var unresolved = new java.util.TreeSet<>(requiredBiomes);
			unresolved.removeAll(included);
			throw new IllegalArgumentException("No climate placement mappings for " + unresolved + " in " + dimension);
		}
		var preferredClimates = new HashSet<JsonElement>();
		for (var element : result) {
			JsonObject entry = element.getAsJsonObject();
			if (requiredBiomes.contains(entry.get("biome").getAsString())) {
				preferredClimates.add(entry.get("parameters"));
			}
		}
		var prioritized = new JsonArray();
		for (var element : result) {
			JsonObject entry = element.getAsJsonObject();
			if (requiredBiomes.contains(entry.get("biome").getAsString())
					|| !preferredClimates.contains(entry.get("parameters"))) {
				prioritized.add(element);
			}
		}
		if (prioritized.isEmpty()) {
			throw new IllegalArgumentException("Biome restrictions leave no candidates in " + dimension);
		}
		return prioritized;
	}

	private PlanesBiomeRestrictions() {}
}
