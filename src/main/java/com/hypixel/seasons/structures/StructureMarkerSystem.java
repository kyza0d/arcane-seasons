package com.hypixel.seasons.structures;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.protocol.packets.worldmap.ContextMenuItem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StructureMarkerSystem {
  private final World world;
  private final Store<EntityStore> store;
  private final Map<String, MapMarker> markers;
  private final Map<String, StructureConfiguration> configurationMap;

  public StructureMarkerSystem(World world, Store<EntityStore> store) {
    this.world = world;
    this.store = store;
    this.markers = new HashMap<>();
    this.configurationMap = new HashMap<>();
  }

  public void registerConfiguration(StructureConfiguration config) {
    configurationMap.put(config.getId(), config);
  }

  public void registerConfigurations(List<StructureConfiguration> configs) {
    for (StructureConfiguration config : configs) {
      registerConfiguration(config);
    }
  }

  public void createMarkers(List<StructureDataResource.SpawnedStructure> spawnedStructures) {
    for (StructureDataResource.SpawnedStructure spawned : spawnedStructures) {
      StructureConfiguration config = configurationMap.get(spawned.getConfigId());
      if (config != null && config.isShowOnMap()) {
        MapMarker marker = createMarker(spawned, config);
        markers.put(spawned.getUniqueMarkerId(), marker);
      }
    }
  }

  private MapMarker createMarker(StructureDataResource.SpawnedStructure spawned, StructureConfiguration config) {
    MapMarker marker = new MapMarker();
    marker.id = spawned.getUniqueMarkerId();
    marker.name = config.getDisplayName();
    marker.markerImage = config.getMarkerIcon();

    com.hypixel.hytale.protocol.Transform protoTransform = new com.hypixel.hytale.protocol.Transform();
    protoTransform.position = new com.hypixel.hytale.protocol.Position(spawned.getPosition().x, spawned.getPosition().y, spawned.getPosition().z);
    protoTransform.orientation = null;
    marker.transform = protoTransform;
    marker.contextMenuItems = new ContextMenuItem[0];
    return marker;
  }

  public MapMarker getMarker(String markerId) {
    return markers.get(markerId);
  }

  public Map<String, MapMarker> getAllMarkers() {
    return new HashMap<>(markers);
  }

  public StructureConfiguration getConfiguration(String configId) {
    return configurationMap.get(configId);
  }
}
