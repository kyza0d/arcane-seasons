package com.hypixel.seasons.structures;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class StructureDataResource implements Resource<EntityStore> {
  private static ResourceType<EntityStore, StructureDataResource> RESOURCE_TYPE;

  private boolean initialized;
  private long initializationTime;
  private List<SpawnedStructure> spawnedStructures;
  private Map<UUID, Set<String>> playerDiscoveries;

  public StructureDataResource() {
    this.initialized = false;
    this.initializationTime = 0;
    this.spawnedStructures = new ArrayList<>();
    this.playerDiscoveries = new HashMap<>();
  }

  public StructureDataResource(boolean initialized, long initializationTime,
    List<SpawnedStructure> spawnedStructures,
    Map<UUID, Set<String>> playerDiscoveries) {
    this.initialized = initialized;
    this.initializationTime = initializationTime;
    this.spawnedStructures = new ArrayList<>(spawnedStructures);
    this.playerDiscoveries = new HashMap<>();
    for (Map.Entry<UUID, Set<String>> entry : playerDiscoveries.entrySet()) {
      this.playerDiscoveries.put(entry.getKey(), new HashSet<>(entry.getValue()));
    }
  }

  public boolean isInitialized() {
    return initialized;
  }

  public void setInitialized(boolean initialized) {
    this.initialized = initialized;
  }

  public long getInitializationTime() {
    return initializationTime;
  }

  public void setInitializationTime(long initializationTime) {
    this.initializationTime = initializationTime;
  }

  public List<SpawnedStructure> getSpawnedStructures() {
    return spawnedStructures;
  }

  public void addSpawnedStructure(SpawnedStructure structure) {
    this.spawnedStructures.add(structure);
  }

  public Map<UUID, Set<String>> getPlayerDiscoveries() {
    return playerDiscoveries;
  }

  public Set<String> getDiscoveriesForPlayer(UUID playerUuid) {
    return playerDiscoveries.computeIfAbsent(playerUuid, k -> new HashSet<>());
  }

  public boolean hasPlayerDiscovered(UUID playerUuid, String structureId) {
    Set<String> discoveries = playerDiscoveries.get(playerUuid);
    return discoveries != null && discoveries.contains(structureId);
  }

  public void markDiscovered(UUID playerUuid, String structureId) {
    getDiscoveriesForPlayer(playerUuid).add(structureId);
  }

  @Override
  public Resource<EntityStore> clone() {
    return new StructureDataResource(initialized, initializationTime, spawnedStructures, playerDiscoveries);
  }

  public static void initializeResourceType() {
    if (RESOURCE_TYPE == null) {
      RESOURCE_TYPE = new ResourceType<>();
    }
  }

  public static ResourceType<EntityStore, StructureDataResource> getResourceType() {
    if (RESOURCE_TYPE == null) {
      initializeResourceType();
    }
    return RESOURCE_TYPE;
  }

  public static class SpawnedStructure {
    private final String configId;
    private final Vector3i position;
    private final PrefabRotation rotation;
    private final long spawnTime;
    private final String uniqueMarkerId;

    public SpawnedStructure(String configId, Vector3i position, PrefabRotation rotation, long spawnTime) {
      this.configId = configId;
      this.position = position;
      this.rotation = rotation;
      this.spawnTime = spawnTime;
      this.uniqueMarkerId = "structure_" + configId + "_" + spawnTime;
    }

    public SpawnedStructure(String configId, Vector3i position, PrefabRotation rotation,
      long spawnTime, String uniqueMarkerId) {
      this.configId = configId;
      this.position = position;
      this.rotation = rotation;
      this.spawnTime = spawnTime;
      this.uniqueMarkerId = uniqueMarkerId;
    }

    public String getConfigId() {
      return configId;
    }

    public Vector3i getPosition() {
      return position;
    }

    public PrefabRotation getRotation() {
      return rotation;
    }

    public long getSpawnTime() {
      return spawnTime;
    }

    public String getUniqueMarkerId() {
      return uniqueMarkerId;
    }
  }
}
