package com.ori.planes.PlaneManagement;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.ori.planes.PlaneManagement.PlaneDefinition.ContentKey;

public final class PlaneContentPolicy {
    private final Map<ContentKey, Set<String>> destinations;

    public PlaneContentPolicy(Collection<PlaneDefinition> definitions) {
        var routes = new HashMap<ContentKey, Set<String>>();
        for (var plane : definitions) {
            String dimension = "planes:plane_" + plane.id();
            for (var content : plane.content()) {
                routes.computeIfAbsent(content, key -> new HashSet<>()).add(dimension);
            }
        }
        var snapshot = new HashMap<ContentKey, Set<String>>();
        routes.forEach((key, dimensions) -> snapshot.put(key, Set.copyOf(dimensions)));
        destinations = Map.copyOf(snapshot);
    }

    public boolean allows(String category, String id, String dimension) {
        Set<String> allowed = destinations.get(new ContentKey(category, id));
        return allowed == null || allowed.contains(dimension);
    }
}