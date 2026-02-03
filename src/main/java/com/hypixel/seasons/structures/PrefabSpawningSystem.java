package com.hypixel.seasons.structures;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.PrefabUtil;
import com.hypixel.hytale.server.worldgen.chunk.ChunkGenerator;
import com.hypixel.hytale.server.worldgen.chunk.ZoneBiomeResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PrefabSpawningSystem {
    private final World world;
    private final Store<EntityStore> store;
    private final Random random;
    private static final Set<String> loggedZones = ConcurrentHashMap.newKeySet();
    private static final String TARGET_ZONE = "Zone 1";

    public PrefabSpawningSystem(World world, Store<EntityStore> store) {
        this.world = world;
        this.store = store;
        this.random = new Random();
    }

    private String getZoneAtPosition(int x, int z) {
        try {
            if (world == null) {
                return null;
            }
            Object chunkStore = world.getChunkStore();
            if (chunkStore == null) {
                return null;
            }
            ChunkGenerator generator = (ChunkGenerator) chunkStore.getClass().getMethod("getGenerator").invoke(chunkStore);
            if (generator == null) {
                return null;
            }
            Object worldConfig = world.getClass().getMethod("getWorldConfig").invoke(world);
            int worldSeed = (int) worldConfig.getClass().getMethod("getWorldSeed").invoke(worldConfig);

            ZoneBiomeResult zb = generator.getZoneBiomeResultAt(worldSeed, x, z);
            if (zb == null) {
                return null;
            }
            Object zoneResult = zb.getZoneResult();
            Object zone = zoneResult.getClass().getMethod("getZone").invoke(zoneResult);
            String zoneName = (String) zone.getClass().getMethod("name").invoke(zone);
            return zoneName;
        } catch (Exception e) {
            return null;
        }
    }

    public void debugScanNearbyZones(int centerX, int centerZ, int radius) {
        Set<String> discoveredZones = new HashSet<>();
        int spacing = 16;

        System.out.println("[ARCANE SEASONS] Scanning zones in radius " + radius + " around (" + centerX + ", " + centerZ + ")");

        for (int x = centerX - radius; x <= centerX + radius; x += spacing) {
            for (int z = centerZ - radius; z <= centerZ + radius; z += spacing) {
                String zone = getZoneAtPosition(x, z);
                if (zone != null) {
                    discoveredZones.add(zone);
                    if (loggedZones.add(zone)) {
                        System.out.println("[ARCANE SEASONS] Discovered zone: " + zone);
                    }
                }
            }
        }

        System.out.println("[ARCANE SEASONS] Total unique zones found: " + discoveredZones.size());
        for (String zone : discoveredZones) {
            System.out.println("[ARCANE SEASONS]   - " + zone);
        }
    }

    public List<StructureDataResource.SpawnedStructure> spawnStructures(List<StructureConfiguration> configs) {
        List<StructureDataResource.SpawnedStructure> spawned = new ArrayList<>();
        for (StructureConfiguration config : configs) {
            StructureDataResource.SpawnedStructure result = spawnStructure(config);
            if (result != null) {
                spawned.add(result);
            }
        }
        return spawned;
    }

    public StructureDataResource.SpawnedStructure spawnStructure(StructureConfiguration config) {
        if (config == null || config.getPosition() == null) {
            return null;
        }

        try {
            if (world == null || store == null) {
                return null;
            }

            String zone = getZoneAtPosition(config.getPosition().x, config.getPosition().z);
            if (zone == null || !zone.equals(TARGET_ZONE)) {
                System.err.println("[ARCANE SEASONS] Structure '" + config.getId() + "' not in " + TARGET_ZONE + " (found: " + zone + "), skipping spawn");
                debugScanNearbyZones(config.getPosition().x, config.getPosition().z, 128);
                return null;
            }

            if (!placePrefab(config)) {
                System.err.println("[ARCANE SEASONS] Failed to place prefab: " + config.getPrefabPath());
                return null;
            }

            System.out.println("[ARCANE SEASONS] Spawned structure '" + config.getId() + "' at (" +
                config.getPosition().x + ", " + config.getPosition().y + ", " + config.getPosition().z + ")");

            return new StructureDataResource.SpawnedStructure(
                config.getId(),
                config.getPosition(),
                config.getRotation(),
                System.currentTimeMillis()
            );
        } catch (Exception e) {
            System.err.println("[ARCANE SEASONS] Error spawning structure '" + config.getId() + "': " + e.getClass().getSimpleName());
            System.err.println("[ARCANE SEASONS]   Message: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private IPrefabBuffer loadPrefabBuffer(String prefabPath) {
        if (prefabPath == null || prefabPath.isEmpty()) {
            return null;
        }

        try {
            PrefabStore prefabStore = PrefabStore.get();
            if (prefabStore == null) {
                return null;
            }

            String normalizedPath = prefabPath.replace(".", "/");

            String[] pathFormats = {
                normalizedPath + ".prefab.json",
                "Server/Prefabs/" + normalizedPath + ".prefab.json",
                "structures/" + normalizedPath + ".prefab.json"
            };

            for (String pathFormat : pathFormats) {
                Path foundPath = prefabStore.findAssetPrefabPath(pathFormat);
                if (foundPath != null) {
                    return PrefabBufferUtil.getCached(foundPath);
                }
            }

            System.err.println("[ARCANE SEASONS] Failed to load prefab: " + prefabPath);
            return null;
        } catch (Exception e) {
            System.err.println("[ARCANE SEASONS] Error loading prefab '" + prefabPath + "': " + e.getMessage());
            return null;
        }
    }

    private boolean placePrefab(StructureConfiguration config) {
        try {
            if (!world.isInThread()) {
                System.err.println("[ARCANE SEASONS] Warning: placePrefab called from wrong thread, deferring to world thread");
                world.execute(() -> {
                    try {
                        IPrefabBuffer prefab = loadPrefabBuffer(config.getPrefabPath());
                        if (prefab == null) {
                            System.err.println("[ARCANE SEASONS] Failed to load prefab (deferred): " + config.getPrefabPath());
                            return;
                        }
                        Rotation rotation = prefabRotationToRotation(config.getRotation());
                        PrefabUtil.paste(
                            prefab,
                            world,
                            config.getPosition(),
                            rotation,
                            true,
                            random,
                            store
                        );
                    } catch (Exception e) {
                        System.err.println("[ARCANE SEASONS] Error placing prefab (deferred): " + e.getMessage());
                    }
                });
                return true;
            }

            IPrefabBuffer prefab = loadPrefabBuffer(config.getPrefabPath());
            if (prefab == null) {
                System.err.println("[ARCANE SEASONS] Failed to load prefab: " + config.getPrefabPath());
                return false;
            }
            Rotation rotation = prefabRotationToRotation(config.getRotation());
            PrefabUtil.paste(
                prefab,
                world,
                config.getPosition(),
                rotation,
                true,
                random,
                store
            );
            return true;
        } catch (Exception e) {
            System.err.println("[ARCANE SEASONS] Error placing prefab: " + e.getMessage());
            return false;
        }
    }

    private Rotation prefabRotationToRotation(PrefabRotation prefabRotation) {
        switch (prefabRotation) {
            case ROTATION_0:
                return Rotation.None;
            case ROTATION_90:
                return Rotation.Ninety;
            case ROTATION_180:
                return Rotation.OneEighty;
            case ROTATION_270:
                return Rotation.TwoSeventy;
            default:
                return Rotation.None;
        }
    }
}
