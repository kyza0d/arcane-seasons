package com.hypixel.seasons.structures;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.universe.world.events.StartWorldEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.seasons.systems.PortalRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SeasonStructureInitializer {
  private static SeasonStructureInitializer instance;

  private final List<StructureConfiguration> configurations;
  private final Map<World, StructureMarkerSystem> markerSystems;
  private boolean setupComplete;
  private StructureRegistry registry;
  private PrefabResolutionManager prefabManager;
  private GenerationIntegration generationIntegration;

  private SeasonStructureInitializer() {
    this.configurations = new ArrayList<>();
    this.markerSystems = new HashMap<>();
    this.setupComplete = false;
  }

  public static SeasonStructureInitializer get() {
    if (instance == null) {
      instance = new SeasonStructureInitializer();
    }
    return instance;
  }

  public void setup() {
    if (setupComplete) {
      return;
    }
    setupComplete = true;

    StructureDataResource.initializeResourceType();
    UniquePlacementPersistence.initialize();

    this.registry = new StructureRegistry();
    this.prefabManager = new PrefabResolutionManager();

    System.out.println("[ARCANE SEASONS] SeasonStructureInitializer setup complete");
  }

  public void registerConfiguration(StructureConfiguration config) {
    configurations.add(config);
    if (registry != null) {
      String[] zoneMask = config.getZoneMask();
      if (zoneMask != null && zoneMask.length > 0) {
        for (String zone : zoneMask) {
          registry.register(zone, config);
        }
      }
    }
  }

  public void registerConfigurations(List<StructureConfiguration> configs) {
    configurations.addAll(configs);
  }

  public List<StructureConfiguration> getConfigurations() {
    return new ArrayList<>(configurations);
  }

  public void onWorldStart(StartWorldEvent event) {
    World world = event.getWorld();
    EntityStore entityStore = world.getEntityStore();
    Store<EntityStore> store = entityStore.getStore();

    if (this.generationIntegration == null) {
      this.generationIntegration = new GenerationIntegration(world, this.registry, this.prefabManager);
    }

    System.out.println("[ARCANE SEASONS] Processing world start for: " + world.getName());

    if (configurations.isEmpty()) {
      System.out.println("[ARCANE SEASONS] No structure configurations registered");
      return;
    }

    System.out.println("[ARCANE SEASONS] [PORTAL] [INIT] Total configurations: " + configurations.size());
    for (StructureConfiguration config : configurations) {
      System.out.println("[ARCANE SEASONS] [PORTAL] [INIT] Config: " + config.getId() + " has position: " + (config.getPosition() != null));
    }

    StructurePersistenceManager persistence = new StructurePersistenceManager(store);
    StructureDataResource resource = persistence.loadOrCreate();

    if (!resource.isInitialized()) {
      System.out.println("[ARCANE SEASONS] Initializing structures for world: " + world.getName());

      List<StructureConfiguration> configurationsWithPositions = new java.util.ArrayList<>();
      for (StructureConfiguration config : configurations) {
        if (config.getPosition() != null) {
          configurationsWithPositions.add(config);
        } else {
          System.out.println("[ARCANE SEASONS] Skipping " + config.getId() + " (will be spawned via chunk generation)");
        }
      }

      System.out.println("[ARCANE SEASONS] [PORTAL] [INIT] Configurations with positions: " + configurationsWithPositions.size());

      PrefabSpawningSystem spawningSystem = new PrefabSpawningSystem(world, store);
      List<StructureDataResource.SpawnedStructure> spawned = spawningSystem.spawnStructures(configurationsWithPositions);

      System.out.println("[ARCANE SEASONS] [PORTAL] [INIT] PrefabSpawningSystem returned " + (spawned == null ? "null" : spawned.size() + " spawned structures"));

      if (spawned != null && !spawned.isEmpty()) {
        for (StructureDataResource.SpawnedStructure structure : spawned) {
          resource.addSpawnedStructure(structure);
          System.out.println("[ARCANE SEASONS] [PORTAL] [INIT] Processing spawned structure: " + structure.getConfigId() + " at position: " + structure.getPosition());
          registerPortalCoordsIfApplicable(structure, configurationsWithPositions);
        }
      } else {
        System.out.println("[ARCANE SEASONS] [PORTAL] [INIT] No structures spawned to process");
      }

      persistence.markInitialized(resource);
      broadcastInitializationMessage();

      System.out.println("[ARCANE SEASONS] Spawned " + (spawned == null ? 0 : spawned.size()) + " structures");
    } else {
      System.out.println("[ARCANE SEASONS] Structures already initialized for world: " + world.getName());
    }

    StructureMarkerSystem markerSystem = new StructureMarkerSystem(world, store);
    markerSystem.registerConfigurations(configurations);
    markerSystem.createMarkers(resource.getSpawnedStructures());
    markerSystems.put(world, markerSystem);

    System.out.println("[ARCANE SEASONS] Structure systems initialized for world: " + world.getName());
    System.out.println("[ARCANE SEASONS] [PORTAL] [INIT] Portal registry now contains " + PortalRegistry.getInstance().getPortalCount() + " portals");
    PortalRegistry.getInstance().logAllPortals();
  }

  public void onPlayerJoin(AddPlayerToWorldEvent event) {
    World world = event.getWorld();
    System.out.println("[ARCANE SEASONS] Player joined world: " + world.getName());
  }

  public StructureMarkerSystem getMarkerSystem(World world) {
    return markerSystems.get(world);
  }

  public StructureRegistry getRegistry() {
    return registry;
  }

  public PrefabResolutionManager getPrefabManager() {
    return prefabManager;
  }

  private void broadcastInitializationMessage() {
    Message message = Message.raw("An echo awaits.").bold(true).color("gold");
    Universe.get().sendMessage(message);
  }

  public GenerationIntegration getGenerationIntegration() {
    return generationIntegration;
  }

  public void cleanup(World world) {
    markerSystems.remove(world);
  }

  private void registerPortalCoordsIfApplicable(StructureDataResource.SpawnedStructure structure,
    List<StructureConfiguration> configurations) {
    try {
      String structureId = structure.getConfigId();
      System.out.println("[ARCANE SEASONS] [PORTAL] [REGISTRY] Checking structure: " + structureId);

      if (structureId == null) {
        System.err.println("[ARCANE SEASONS] [PORTAL] [ERROR] Structure ID is null");
        return;
      }

      System.out.println("[ARCANE SEASONS] [PORTAL] [REGISTRY] Structure ID: '" + structureId + "' checking if contains 'Echo_Portal': " + structureId.contains("Echo_Portal"));

      if (!structureId.contains("Echo_Portal")) {
        System.out.println("[ARCANE SEASONS] [PORTAL] [REGISTRY] Skipping non-portal structure: " + structureId);
        return;
      }

      System.out.println("[ARCANE SEASONS] [PORTAL] [REGISTRY] Portal structure identified: " + structureId);

      Object spawnPos = structure.getPosition();
      System.out.println("[ARCANE SEASONS] [PORTAL] [REGISTRY] Position object: " + (spawnPos == null ? "NULL" : spawnPos.getClass().getSimpleName()));

      if (spawnPos == null) {
        System.err.println("[ARCANE SEASONS] [PORTAL] [ERROR] Portal structure has null position: " + structureId);
        return;
      }

      int spawnX = 0;
      int spawnY = 0;
      int spawnZ = 0;

      System.out.println("[ARCANE SEASONS] [PORTAL] [REGISTRY] Position class name: " + spawnPos.getClass().getSimpleName());

      if (spawnPos.getClass().getSimpleName().equals("Vector3i")) {
        try {
          spawnX = (int) spawnPos.getClass().getMethod("getX").invoke(spawnPos);
          spawnY = (int) spawnPos.getClass().getMethod("getY").invoke(spawnPos);
          spawnZ = (int) spawnPos.getClass().getMethod("getZ").invoke(spawnPos);
          System.out.println("[ARCANE SEASONS] [PORTAL] [REGISTRY] Extracted Vector3i position: (" + spawnX + ", " + spawnY + ", " + spawnZ + ")");
        } catch (Exception reflectionEx) {
          System.err.println("[ARCANE SEASONS] [PORTAL] [ERROR] Failed to extract coordinates via reflection: " + reflectionEx.getMessage());
          reflectionEx.printStackTrace();
          return;
        }
      } else {
        System.err.println("[ARCANE SEASONS] [PORTAL] [ERROR] Unknown position type: " + spawnPos.getClass().getSimpleName() + ", expected Vector3i");
        return;
      }

      int offsetX = 0;
      int offsetY = 0;
      int offsetZ = 0;

      System.out.println("[ARCANE SEASONS] [PORTAL] [REGISTRY] About to register portal with ID: " + structureId + " at coordinates (" + spawnX + ", " + spawnY + ", " + spawnZ + ") with offsets (" + offsetX + ", " + offsetY + ", " + offsetZ + ")");

      PortalRegistry registry = PortalRegistry.getInstance();
      System.out.println("[ARCANE SEASONS] [PORTAL] [REGISTRY] PortalRegistry before registration: " + registry.getPortalCount() + " portals");

      registry.registerPortalFromPrefabSpawn(
        structureId,
        spawnX, spawnY, spawnZ,
        offsetX, offsetY, offsetZ
      );

      System.out.println("[ARCANE SEASONS] [PORTAL] [REGISTRY] PortalRegistry after registration: " + registry.getPortalCount() + " portals");
      System.out.println("[ARCANE SEASONS] [PORTAL] [REGISTRY] Successfully registered portal: " + structureId +
        " at (" + spawnX + ", " + spawnY + ", " + spawnZ + ")");
    } catch (Exception e) {
      System.err.println("[ARCANE SEASONS] [PORTAL] [ERROR] Failed to register portal coordinates: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
