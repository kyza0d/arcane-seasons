package com.hypixel.seasons.structures;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StructureRegistry {
    private volatile Map<String, List<StructureConfiguration>> zoneRegistry = new ConcurrentHashMap<>();

    public void register(String zone, StructureConfiguration config) {
        zoneRegistry.computeIfAbsent(zone, k -> new ArrayList<>()).add(config);
    }

    public List<StructureConfiguration> getStructuresForZone(String zone) {
        List<StructureConfiguration> configs = zoneRegistry.get(zone);
        return configs != null ? new ArrayList<>(configs) : List.of();
    }

    public List<StructureConfiguration> getAllStructures() {
        List<StructureConfiguration> all = new ArrayList<>();
        for (List<StructureConfiguration> zoneConfigs : zoneRegistry.values()) {
            all.addAll(zoneConfigs);
        }
        return all;
    }
}
