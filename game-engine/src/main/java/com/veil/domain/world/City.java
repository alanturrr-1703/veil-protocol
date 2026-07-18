package com.veil.domain.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The city map: a graph of {@link Location}s with adjacency for movement and
 * radius queries used by the Shadow to hunt NPC witnesses.
 */
public class City {

    private final Map<String, Location> locations = new LinkedHashMap<>();
    private final Map<String, Set<String>> adjacency = new HashMap<>();

    public void addLocation(Location location) {
        locations.put(location.id(), location);
        adjacency.computeIfAbsent(location.id(), k -> new HashSet<>());
    }

    public void connect(String a, String b) {
        adjacency.computeIfAbsent(a, k -> new HashSet<>()).add(b);
        adjacency.computeIfAbsent(b, k -> new HashSet<>()).add(a);
    }

    public Location location(String id) {
        return locations.get(id);
    }

    public Map<String, Location> locations() {
        return locations;
    }

    public boolean areAdjacent(String from, String to) {
        return adjacency.getOrDefault(from, Set.of()).contains(to);
    }

    /** The districts directly connected to {@code id} (for movement / forced relocation). */
    public Set<String> neighbors(String id) {
        return adjacency.getOrDefault(id, Set.of());
    }

    /** All locations whose position is within {@code radius} of the origin position. */
    public List<Location> locationsWithinRadius(Position origin, double radius) {
        List<Location> result = new ArrayList<>();
        for (Location loc : locations.values()) {
            if (loc.position().distanceTo(origin) <= radius) {
                result.add(loc);
            }
        }
        return result;
    }
}
