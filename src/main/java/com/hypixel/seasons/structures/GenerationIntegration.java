package com.hypixel.seasons.structures;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.PrefabUtil;
import com.hypixel.hytale.server.worldgen.biome.Biome;
import com.hypixel.hytale.server.worldgen.chunk.ChunkGenerator;
import com.hypixel.hytale.server.worldgen.chunk.ZoneBiomeResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// okay this class is basically our hook into chunk generation to slap structures down
// its hooked into ChunkPreLoadProcessEvent so we only run when a brand new chunk is being born
public class GenerationIntegration {

  private final World world;
  private final StructureRegistry registry;
  private final PrefabResolutionManager prefabManager;
  private final Map<String, IPrefabBuffer> prefabCache;          // we cache buffers here but honestly barely used right now
  private final ThreadLocal<Random> threadLocalRandom;
  private final UniquePlacementPersistence uniquePersistence;    // singleton thing that remembers which uniques we already placed

  // tiny helper record basically, just bundles data for one potential structure spot
  private static class Candidate {
    final int x;
    final int y;
    final int z;
    final int priority;           // currently always 0, maybe useful later?
    final PrefabRotation rotation;
    final String prefabPath;

    Candidate(int x, int y, int z, int priority, PrefabRotation rotation, String prefabPath) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.priority = priority;
      this.rotation = rotation;
      this.prefabPath = prefabPath;
    }
  }

  public GenerationIntegration(World world, StructureRegistry registry,
    PrefabResolutionManager prefabManager) {
    this.world = world;
    this.registry = registry;
    this.prefabManager = prefabManager;
    this.prefabCache = new HashMap<>();
    this.threadLocalRandom = ThreadLocal.withInitial(Random::new);
    this.uniquePersistence = UniquePlacementPersistence.get();
  }

  // this is the main entry point - called from event bus when chunk is about to finish generating
  public void onChunkPreLoad(Object event) {
    if (!(event instanceof ChunkPreLoadProcessEvent)) return;

    ChunkPreLoadProcessEvent chunkEvent = (ChunkPreLoadProcessEvent) event;
    if (!chunkEvent.isNewlyGenerated()) return;                    // we only care about freshly generated chunks

    WorldChunk chunk = chunkEvent.getChunk();
    if (chunk == null) return;

    World chunkWorld = chunk.getWorld();
    if (chunkWorld == null || !chunkWorld.equals(this.world)) return;

    Object worldGenObj = chunkWorld.getChunkStore().getGenerator();
    if (!(worldGenObj instanceof ChunkGenerator)) return;

    ChunkGenerator generator = (ChunkGenerator) worldGenObj;
    int worldSeed = (int) chunkWorld.getWorldConfig().getSeed();

    List<StructureConfiguration> structures = getApplicableStructures();
    if (structures.isEmpty()) return;

    // go through every structure type we might want to place in this world
    for (StructureConfiguration config : structures) {
      try {
        placeStructuresInChunk(chunk, generator, config, worldSeed);
      } catch (Throwable t) {
        // dont let one bad structure kill the whole chunk
        System.err.println("[ARCANE SEASONS] Structure placement failed for: " + config.getId());
        System.err.println("[ARCANE SEASONS] Error: " + t.getMessage());
        t.printStackTrace(); // ← would be nice to have real logging later
      }
    }
  }

  // tries to find places and paste structures of one specific config type in this chunk
  private void placeStructuresInChunk(WorldChunk chunk, ChunkGenerator generator,
    StructureConfiguration config, int worldSeed) {
    String prefabPath = config.getPrefabPath();
    if (prefabPath == null || prefabPath.isEmpty()) return;

    String structureId = config.getId();
    World chunkWorld = chunk.getWorld();
    if (chunkWorld == null) return;

    boolean isUnique = config.isUnique();

    // unique structures are only allowed once per world - we check + reserve early
    if (isUnique) {
      if (uniquePersistence.hasBeenPlaced(structureId)) return;
      if (!uniquePersistence.tryReservePlacement(structureId)) return;
    }

    int chunkMinX = ChunkUtil.minBlock(chunk.getX());
    int chunkMaxX = ChunkUtil.maxBlock(chunk.getX());
    int chunkMinZ = ChunkUtil.minBlock(chunk.getZ());
    int chunkMaxZ = ChunkUtil.maxBlock(chunk.getZ());

    int seed = (int) world.getWorldConfig().getSeed();
    long entrySeed = hashCode(seed, structureId.hashCode());

    Random random = threadLocalRandom.get();
    random.setSeed(entrySeed * 1609272495L);   // some big prime multiplier, dont ask me why this number

    List<Candidate> candidates = generateCandidates(chunk, generator, config, worldSeed,
      chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ,
      prefabPath, random);

    if (candidates.isEmpty()) {
      if (isUnique) uniquePersistence.releasePendingPlacement(structureId);
      return;
    }

    System.out.println("[ARCANE SEASONS] Generated " + candidates.size() + " candidates for " + structureId +
      " in chunk (" + chunk.getX() + ", " + chunk.getZ() + ")");

    // unique mode → just take the first (usually only) candidate and try really hard to place it
    if (isUnique) {
      Candidate candidate = candidates.get(0);
      pasteUniqueStructure(chunk, candidate, structureId);
      return;
    }

    // normal mode - multiple possible spots → try to avoid placing things too close to each other
    BitSet conflicts = detectCollisions(candidates);

    for (int i = 0; i < candidates.size(); i++) {
      if (conflicts.get(i)) continue;

      Candidate candidate = candidates.get(i);
      pasteIntoChunk(chunk, candidate.x, candidate.y, candidate.z,
        candidate.prefabPath, candidate.rotation, structureId);
    }
  }

  // special path for unique structures - runs inside chunkWorld.execute() so its thread-safe-ish
  private void pasteUniqueStructure(WorldChunk chunk, Candidate candidate, String structureId) {
    if (chunk == null) {
      uniquePersistence.releasePendingPlacement(structureId);
      return;
    }

    World chunkWorld = chunk.getWorld();
    if (chunkWorld == null) {
      uniquePersistence.releasePendingPlacement(structureId);
      return;
    }

    Vector3i position = new Vector3i(candidate.x, candidate.y, candidate.z);
    Rotation rotation = prefabRotationToRotation(candidate.rotation);

    // different seed per position so trees / flowers / randomness inside prefab isnt same everywhere
    long randomSeed = hashCode((int) world.getWorldConfig().getSeed(), candidate.x * 31 + candidate.z);

    chunkWorld.execute(() -> {
      try {
        // double-check because race conditions between chunks are possible in theory
        if (uniquePersistence.hasBeenPlaced(structureId)) return;

        IPrefabBuffer buffer = loadPrefabBuffer(candidate.prefabPath);
        if (buffer == null) {
          System.err.println("[ARCANE SEASONS] Prefab not found for unique structure: " + candidate.prefabPath);
          uniquePersistence.releasePendingPlacement(structureId);
          return;
        }

        Store<EntityStore> entityStore = chunkWorld.getEntityStore().getStore();
        Random random = new Random(randomSeed);

        PrefabUtil.paste(buffer, chunkWorld, position, rotation, true, random, entityStore);

        uniquePersistence.markAsPlaced(structureId);
        System.out.println("[ARCANE SEASONS] Placed unique structure '" + structureId +
          "' at (" + candidate.x + ", " + candidate.y + ", " + candidate.z + ")");
      } catch (Exception e) {
        System.err.println("[ARCANE SEASONS] Error pasting unique structure at (" +
          candidate.x + ", " + candidate.y + ", " + candidate.z + ")");
        System.err.println("[ARCANE SEASONS] Error: " + e.getMessage());
        uniquePersistence.releasePendingPlacement(structureId);
      }
    });
  }

  // decides where we even consider placing this structure inside the chunk
  private List<Candidate> generateCandidates(WorldChunk chunk, ChunkGenerator generator,
    StructureConfiguration config, int worldSeed,
    int chunkMinX, int chunkMaxX, int chunkMinZ, int chunkMaxZ,
    String prefabPath, Random random) {
    List<Candidate> candidates = new ArrayList<>();

    // some structures have one hardcoded position (mostly for debugging / specific placements)
    Vector3i position = config.getPosition();
    if (position != null) {
      boolean inChunk = position.x >= chunkMinX && position.x <= chunkMaxX &&
      position.z >= chunkMinZ && position.z <= chunkMaxZ;
      if (inChunk) {
        boolean passesFilter = filterByZoneBiome(generator, position.x, position.z, config, worldSeed);
        if (passesFilter) {
          int height = getHeight(position.x, position.z, generator, config, worldSeed);
          PrefabRotation rotation = chooseRotation(config, random);
          candidates.add(new Candidate(position.x, height, position.z, 0, rotation, prefabPath));
          System.out.println("[ARCANE SEASONS] Fixed position candidate for " + config.getId() +
            " at (" + position.x + ", " + height + ", " + position.z + ")");
        }
      }
      return candidates; // early out - fixed position mode
    }

    // normal scattered mode - grid based attempts
    int gridSpacing = 16;   // magic number, probably should come from config later

    for (int x = chunkMinX; x <= chunkMaxX; x += gridSpacing) {
      for (int z = chunkMinZ; z <= chunkMaxZ; z += gridSpacing) {
        if (filterByZoneBiome(generator, x, z, config, worldSeed)) {
          int height = getHeight(x, z, generator, config, worldSeed);
          if (height >= 0 && height < 320) {   // rough sanity check
            PrefabRotation rotation = chooseRotation(config, random);
            candidates.add(new Candidate(x, height, z, 0, rotation, prefabPath));
          }
        }
      }
    }

    return candidates;
  }

  // debug helper to see what zones actually exist - probably remove later
  private static final java.util.Set<String> loggedZones = java.util.concurrent.ConcurrentHashMap.newKeySet();

  // checks if this x,z location is allowed for this structure type (zone + biome filter)
  private boolean filterByZoneBiome(ChunkGenerator generator, int x, int z,
    StructureConfiguration config, int worldSeed) {
    try {
      ZoneBiomeResult zb = generator.getZoneBiomeResultAt(worldSeed, x, z);
      if (zb == null) return false;

      String zoneName = zb.getZoneResult().getZone().name();
      if (loggedZones.add(zoneName)) {
        System.out.println("[ARCANE SEASONS] Detected zone: " + zoneName);
      }

      if (!matchesAny(zoneName, config.getZoneMask())) return false;

      Biome biome = zb.getBiome();
      if (biome == null) return false;

      String biomeName = biome.getName();
      return matchesAny(biomeName, config.getBiomeMask());
    } catch (Exception e) {
      return false;   // silent fail - dont crash worldgen
    }
  }

  private boolean matchesAny(String value, String[] patterns) {
    if (patterns == null || patterns.length == 0) return true;
    for (String pattern : patterns) {
      if (matchesPattern(value, pattern)) return true;
    }
    return false;
  }

  // very basic glob-like matching with * support
  private boolean matchesPattern(String value, String pattern) {
    if (value == null || pattern == null) return false;
    if ("*".equals(pattern)) return true;

    pattern = pattern.toLowerCase();
    value = value.toLowerCase();

    if (!pattern.contains("*")) return pattern.equals(value);

    String regex = pattern.replace(".", "\\.").replace("*", ".*");
    return value.matches(regex);
  }

  // wrapper that swallows exceptions - height is very important so we default to 64
  private int getHeight(int x, int z, ChunkGenerator generator,
    StructureConfiguration config, int worldSeed) {
    try {
      return generator.getHeight(worldSeed, x, z);
    } catch (Exception e) {
      return 64;
    }
  }

  private PrefabRotation chooseRotation(StructureConfiguration config, Random random) {
    PrefabRotation rotation = config.getRotation();
    if (rotation != null) return rotation;

    PrefabRotation[] rotations = PrefabRotation.VALUES;
    if (rotations == null || rotations.length == 0) return PrefabRotation.ROTATION_0;

    return rotations[random.nextInt(rotations.length)];
  }

  // very dumb aabb overlap check - marks later candidates as conflicting if they are close
  // the -5 / +5 is basically a hardcoded exclusion zone
  private BitSet detectCollisions(List<Candidate> candidates) {
    BitSet conflicts = new BitSet();

    for (int i = 0; i < candidates.size(); i++) {
      Candidate candidate = candidates.get(i);
      for (int j = i + 1; j < candidates.size(); j++) {
        Candidate other = candidates.get(j);
        if (intersects(candidate.x - 5, candidate.y - 5, candidate.z - 5,
          candidate.x + 5, candidate.y + 5, candidate.z + 5,
          other.x - 5, other.y - 5, other.z - 5,
          other.x + 5, other.y + 5, other.z + 5)) {
          conflicts.set(j);   // later one loses
        }
      }
    }
    return conflicts;
  }

  private boolean intersects(int minX1, int minY1, int minZ1, int maxX1, int maxY1, int maxZ1,
    int minX2, int minY2, int minZ2, int maxX2, int maxY2, int maxZ2) {
    return maxX1 >= minX2 && minX1 <= maxX2 &&
    maxY1 >= minY2 && minY1 <= maxY2 &&
    maxZ1 >= minZ2 && minZ1 <= maxZ2;
  }

  // normal paste logic for non-unique structures
  private void pasteIntoChunk(WorldChunk chunk, int anchorX, int anchorY, int anchorZ,
    String prefabPath, PrefabRotation prefabRotation, String structureId) {
    if (chunk == null || prefabPath == null) return;

    World chunkWorld = chunk.getWorld();
    if (chunkWorld == null) return;

    Vector3i position = new Vector3i(anchorX, anchorY, anchorZ);
    Rotation rotation = prefabRotationToRotation(prefabRotation);

    long randomSeed = hashCode((int) world.getWorldConfig().getSeed(), anchorX * 31 + anchorZ);

    chunkWorld.execute(() -> {
      try {
        IPrefabBuffer buffer = loadPrefabBuffer(prefabPath);
        if (buffer == null) {
          System.err.println("[ARCANE SEASONS] Prefab not found for paste: " + prefabPath);
          return;
        }

        Store<EntityStore> entityStore = chunkWorld.getEntityStore().getStore();
        Random random = new Random(randomSeed);

        PrefabUtil.paste(buffer, chunkWorld, position, rotation, true, random, entityStore);

        System.out.println("[ARCANE SEASONS] Placed structure at (" + anchorX + ", " + anchorY + ", " + anchorZ + ")");
      } catch (Exception e) {
        System.err.println("[ARCANE SEASONS] Error pasting structure at (" + anchorX + ", " + anchorY + ", " + anchorZ + ")");
        System.err.println("[ARCANE SEASONS] Error: " + e.getMessage());
      }
    });
  }

  // tries multiple common prefab path prefixes because nobody can agree where things live
  private static final java.util.Set<String> loggedPrefabs = java.util.concurrent.ConcurrentHashMap.newKeySet();

  private IPrefabBuffer loadPrefabBuffer(String prefabPath) {
    try {
      PrefabStore prefabStore = PrefabStore.get();
      if (prefabStore == null) return null;

      String normalizedPath = prefabPath.replace(".", "/");

      String[] pathFormats = {
        normalizedPath + ".prefab.json",
        "Server/Prefabs/" + normalizedPath + ".prefab.json",
        "structures/" + normalizedPath + ".prefab.json"
      };

      for (String pathFormat : pathFormats) {
        Path foundPath = prefabStore.findAssetPrefabPath(pathFormat);
        if (foundPath != null) {
          IPrefabBuffer buffer = PrefabBufferUtil.getCached(foundPath);
          if (loggedPrefabs.add(prefabPath)) {
            System.out.println("[ARCANE SEASONS] Loaded prefab buffer: " + prefabPath + " -> " + foundPath);
          }
          return buffer;
        }
      }

      if (loggedPrefabs.add(prefabPath + "_notfound")) {
        System.err.println("[ARCANE SEASONS] Prefab not found in any path: " + prefabPath);
      }
      return null;
    } catch (Exception e) {
      System.err.println("[ARCANE SEASONS] Error loading prefab buffer: " + e.getMessage());
      return null;
    }
  }

  private Rotation prefabRotationToRotation(PrefabRotation prefabRotation) {
    switch (prefabRotation) {
      case ROTATION_0:    return Rotation.None;
      case ROTATION_90:   return Rotation.Ninety;
      case ROTATION_180:  return Rotation.OneEighty;
      case ROTATION_270:  return Rotation.TwoSeventy;
      default:            return Rotation.None;
    }
  }

  public List<StructureConfiguration> getApplicableStructures() {
    return new ArrayList<>(registry.getAllStructures());
  }

  // simple hash helper - used for seeding randomness per structure / position
  private long hashCode(int seed, int value) {
    long result = seed;
    result = result * 31L + value;
    return result;
  }

  // debug method - probably dont need in production but useful when testing new biomes/zones
  public void logNearbyZones(World eventWorld) {
    java.util.Set<String> discoveredZones = new java.util.HashSet<>();
    int spacing = 16;
    int radius = 256;

    try {
      if (eventWorld == null) {
        System.out.println("[ARCANE SEASONS] ERROR: World object is null");
        return;
      }

      Object chunkStoreObj = eventWorld.getChunkStore();
      if (chunkStoreObj == null) {
        System.out.println("[ARCANE SEASONS] ERROR: ChunkStore is null");
        return;
      }

      Object worldGenObj = chunkStoreObj.getClass().getMethod("getGenerator").invoke(chunkStoreObj);
      if (!(worldGenObj instanceof ChunkGenerator)) {
        System.out.println("[ARCANE SEASONS] ERROR: Cannot cast to ChunkGenerator");
        return;
      }

      ChunkGenerator generator = (ChunkGenerator) worldGenObj;
      int worldSeed = (int) eventWorld.getWorldConfig().getSeed();

      System.out.println("[ARCANE SEASONS] Starting zone scan with seed: " + worldSeed);
      System.out.println("[ARCANE SEASONS] Scanning in radius " + radius + " at center (0, 0)");

      for (int x = -radius; x <= radius; x += spacing) {
        for (int z = -radius; z <= radius; z += spacing) {
          try {
            ZoneBiomeResult zb = generator.getZoneBiomeResultAt(worldSeed, x, z);
            if (zb != null) {
              String zoneName = zb.getZoneResult().getZone().name();
              discoveredZones.add(zoneName);
            }
          } catch (Exception e) {
            System.out.println("[ARCANE SEASONS] Note: Could not scan position (" + x + ", " + z + ")");
          }
        }
      }

      if (discoveredZones.isEmpty()) {
        System.out.println("[ARCANE SEASONS] WARNING: No zones detected!");
      } else {
        for (String zone : discoveredZones) {
          System.out.println("[ARCANE SEASONS] ZONE: " + zone);
        }
      }

      System.out.println("[ARCANE SEASONS] ========== ZONE DISCOVERY END ==========");
    } catch (Exception e) {
      System.out.println("[ARCANE SEASONS] ERROR during zone discovery: " + e.getClass().getSimpleName());
      System.out.println("[ARCANE SEASONS] Message: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
