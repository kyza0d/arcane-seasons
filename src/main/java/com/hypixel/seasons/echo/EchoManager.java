package com.hypixel.seasons.echo;

import com.hypixel.hytale.builtin.creativehub.CreativeHubPlugin;
import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.builtin.instances.config.InstanceEntityConfig;
import com.hypixel.hytale.builtin.instances.config.WorldReturnPoint;
import com.hypixel.hytale.builtin.weather.resources.WeatherResource;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.FastRandom;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.teleport.PendingTeleport;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.spawn.GlobalSpawnProvider;
import com.hypixel.hytale.server.core.util.PrefabUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class EchoManager {

    private static EchoManager instance;

    public static EchoManager get() {
        if (instance == null) {
            instance = new EchoManager();
        }
        return instance;
    }

    public void teleportToEcho(Echo echo, Ref<EntityStore> playerRef, Store<EntityStore> store, World currentWorld) {
        Player playerComponent = store.getComponent(playerRef, Player.getComponentType());
        if (playerComponent == null || playerComponent.isWaitingForClientReady()) {
            return;
        }

        Archetype<EntityStore> archetype = store.getArchetype(playerRef);
        if (archetype.contains(Teleport.getComponentType()) ||
            archetype.contains(PendingTeleport.getComponentType())) {
            return;
        }

        UUIDComponent uuidComponent = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            return;
        }

        Transform returnTransform = getWorldSpawnPoint(currentWorld, uuidComponent.getUuid());
        UUID currentWorldUUID = currentWorld.getWorldConfig().getUuid();
        WorldReturnPoint returnPoint = new WorldReturnPoint(currentWorldUUID, returnTransform, true);

        currentWorld.execute(() -> {
            storeReturnPoint(playerRef, store, returnPoint);

            Universe universe = Universe.get();
            String worldName = echo.getWorldName();
            World existingWorld = universe.getWorld(worldName);

            if (existingWorld != null && existingWorld.isAlive()) {
                InstancesPlugin.teleportPlayerToInstance(playerRef, store, existingWorld, null);
            } else if (universe.isWorldLoadable(worldName)) {
                CompletableFuture<World> worldFuture = universe.loadWorld(worldName);
                InstancesPlugin.teleportPlayerToLoadingInstance(playerRef, store, worldFuture, null);
                clearPendingTeleportOnCompletion(playerRef, store, worldFuture);
            } else {
                createAndTeleportToEcho(echo, playerRef, store);
            }
        });
    }

    private void storeReturnPoint(Ref<EntityStore> playerRef, Store<EntityStore> store, WorldReturnPoint returnPoint) {
        InstanceEntityConfig entityConfig = store.getComponent(playerRef, InstanceEntityConfig.getComponentType());
        if (entityConfig == null) {
            entityConfig = new InstanceEntityConfig();
            store.addComponent(playerRef, InstanceEntityConfig.getComponentType(), entityConfig);
        }
        entityConfig.setReturnPoint(returnPoint);
    }

    private void createAndTeleportToEcho(Echo echo, Ref<EntityStore> playerRef, Store<EntityStore> store) {
        String worldName = echo.getWorldName();
        String templateName = echo.getTemplateName();

        CompletableFuture<World> worldFuture;

        if (InstancesPlugin.doesInstanceAssetExist(templateName)) {
            worldFuture = CreativeHubPlugin.get().spawnPermanentWorldFromTemplate(templateName, worldName);
        } else {
            worldFuture = createEchoWorldWithPrefab(echo, worldName);
        }

        worldFuture.thenAccept(world -> {
            if (world != null && world.isAlive()) {
                initializeEchoWorldTinting(echo, world);
            }
        });

        InstancesPlugin.teleportPlayerToLoadingInstance(playerRef, store, worldFuture, null);
        clearPendingTeleportOnCompletion(playerRef, store, worldFuture);
    }

    private CompletableFuture<World> createEchoWorldWithPrefab(Echo echo, String worldName) {
        Universe universe = Universe.get();
        CompletableFuture<World> worldFuture = universe.addWorld(worldName, "Void", null);

        return worldFuture.thenCompose(world -> {
            if (world != null && world.isAlive()) {
                return placePrefabInWorld(world, echo).thenApply(v -> world);
            }
            return CompletableFuture.completedFuture(world);
        });
    }

    private CompletableFuture<Void> placePrefabInWorld(World world, Echo echo) {
        String prefabPath = echo.getPrefabPath();
        if (prefabPath == null || prefabPath.isEmpty()) {
            System.out.println("[ARCANE SEASONS] No prefab path for echo: " + echo.getId());
            return CompletableFuture.completedFuture(null);
        }

        System.out.println("[ARCANE SEASONS] Starting prefab placement for: " + echo.getId() + " with path: " + prefabPath);
        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            PrefabStore prefabStore = PrefabStore.get();
            if (prefabStore == null) {
                System.err.println("[ARCANE SEASONS] PrefabStore not available");
                future.complete(null);
                return future;
            }

            String normalizedPath = prefabPath.replace('.', '/').replace("\\", "/");

            String[] pathFormats = {
                normalizedPath + ".prefab.json",
                "Server/Prefabs/" + normalizedPath + ".prefab.json",
                "structures/" + normalizedPath + ".prefab.json"
            };

            Path foundPath = null;
            for (String pathFormat : pathFormats) {
                System.out.println("[ARCANE SEASONS] Trying path format: " + pathFormat);
                Path prefabPath_candidate = prefabStore.findAssetPrefabPath(pathFormat);
                if (prefabPath_candidate != null) {
                    foundPath = prefabPath_candidate;
                    System.out.println("[ARCANE SEASONS] Found prefab at: " + foundPath);
                    break;
                }
            }

            if (foundPath == null) {
                System.err.println("[ARCANE SEASONS] Prefab file not found for: " + prefabPath);
                future.complete(null);
                return future;
            }

            System.out.println("[ARCANE SEASONS] Prefab file found, loading buffer...");
            IPrefabBuffer prefabBuffer = PrefabBufferUtil.getCached(foundPath);
            System.out.println("[ARCANE SEASONS] Prefab buffer loaded successfully");

            Vector3i pastePosition = new Vector3i(0, 64, 0);

            System.out.println("[ARCANE SEASONS] Scheduling prefab paste on world thread...");
            world.execute(() -> {
                try {
                    Store<EntityStore> entityStore = world.getEntityStore().getStore();

                    PrefabUtil.paste(
                            prefabBuffer,
                            world,
                            pastePosition,
                            Rotation.None,
                            true,
                            new FastRandom(),
                            entityStore
                    );

                    updateSpawnFromPrefab(world, prefabBuffer, pastePosition);
                    System.out.println("[ARCANE SEASONS] Successfully pasted " + echo.getId() + " prefab at (" +
                            pastePosition.x + ", " + pastePosition.y + ", " + pastePosition.z + ")");
                    future.complete(null);
                } catch (Exception e) {
                    System.err.println("[ARCANE SEASONS] Failed to paste " + echo.getId() + " prefab: " + e.getMessage());
                    e.printStackTrace();
                    future.completeExceptionally(e);
                }
            });
            System.out.println("[ARCANE SEASONS] Prefab paste scheduled, waiting for completion...");
        } catch (Exception e) {
            System.err.println("[ARCANE SEASONS] Error in placePrefabInWorld for " + echo.getId() + ": " + e.getMessage());
            e.printStackTrace();
            future.completeExceptionally(e);
        }

        return future;
    }

    private void updateSpawnFromPrefab(World world, IPrefabBuffer prefabBuffer, Vector3i pastePosition) {
        try {
            int anchorX = prefabBuffer.getAnchorX();
            int anchorY = prefabBuffer.getAnchorY();
            int anchorZ = prefabBuffer.getAnchorZ();

            int spawnX = pastePosition.x + anchorX;
            int spawnY = pastePosition.y + anchorY + 1;
            int spawnZ = pastePosition.z + anchorZ;

            Vector3d spawnPos = new Vector3d(spawnX + 0.5, spawnY, spawnZ + 0.5);
            Transform spawnTransform = new Transform(spawnPos, new Vector3f(0, 0, 0));

            world.getWorldConfig().setSpawnProvider(new GlobalSpawnProvider(spawnTransform));
            System.out.println("[ARCANE SEASONS] Set spawn point at (" + spawnX + ", " + spawnY + ", " + spawnZ + ")");
        } catch (Exception e) {
            System.err.println("[ARCANE SEASONS] Failed to update spawn from prefab: " + e.getMessage());
            Vector3d fallbackSpawn = new Vector3d(pastePosition.x + 0.5, pastePosition.y + 65, pastePosition.z + 0.5);
            world.getWorldConfig().setSpawnProvider(new GlobalSpawnProvider(
                    new Transform(fallbackSpawn, new Vector3f(0, 0, 0))));
            System.out.println("[ARCANE SEASONS] Using fallback spawn at (" + (pastePosition.x + 0.5) + ", " + (pastePosition.y + 65) + ", " + (pastePosition.z + 0.5) + ")");
        }
    }

    private void clearPendingTeleportOnCompletion(Ref<EntityStore> playerRef, Store<EntityStore> store, CompletableFuture<World> worldFuture) {
        worldFuture.whenComplete((world, exception) -> {
            if (exception == null && world != null && world.isAlive()) {
                Archetype<EntityStore> archetype = store.getArchetype(playerRef);
                if (archetype.contains(PendingTeleport.getComponentType())) {
                    store.removeComponent(playerRef, PendingTeleport.getComponentType());
                }
            }
        });
    }

    private static final String HOME_WORLD_NAME = "default";

    public void returnFromEcho(Ref<EntityStore> playerRef, Store<EntityStore> store, World currentWorld) {
        Player playerComponent = store.getComponent(playerRef, Player.getComponentType());
        if (playerComponent == null || playerComponent.isWaitingForClientReady()) {
            return;
        }

        Archetype<EntityStore> archetype = store.getArchetype(playerRef);
        if (archetype.contains(Teleport.getComponentType()) ||
            archetype.contains(PendingTeleport.getComponentType())) {
            return;
        }

        UUIDComponent uuidComponent = store.getComponent(playerRef, UUIDComponent.getComponentType());
        UUID playerUUID = uuidComponent != null ? uuidComponent.getUuid() : null;

        InstanceEntityConfig entityConfig = store.getComponent(playerRef, InstanceEntityConfig.getComponentType());
        WorldReturnPoint returnPoint = entityConfig != null ? entityConfig.getReturnPoint() : null;

        World returnWorld = null;
        Transform returnTransform = null;

        if (returnPoint != null) {
            returnWorld = Universe.get().getWorld(returnPoint.getWorld());
            if (returnWorld != null && returnWorld.isAlive()) {
                returnTransform = returnPoint.getReturnPoint();
            }
        }

        if (returnWorld == null || !returnWorld.isAlive()) {
            returnWorld = Universe.get().getWorld(HOME_WORLD_NAME);
            if (returnWorld == null || !returnWorld.isAlive()) {
                return;
            }
            returnTransform = getWorldSpawnPoint(returnWorld, playerUUID);
        }

        final World finalReturnWorld = returnWorld;
        final Transform finalReturnTransform = returnTransform;

        currentWorld.execute(() -> {
            Vector3f rotation = finalReturnTransform.getRotation();
            Teleport teleport = new Teleport(finalReturnWorld, finalReturnTransform.getPosition(), rotation);
            store.addComponent(playerRef, Teleport.getComponentType(), teleport);

            InstanceEntityConfig config = store.getComponent(playerRef, InstanceEntityConfig.getComponentType());
            if (config != null) {
                config.setReturnPoint(null);
            }
        });
    }

    public boolean isInEchoWorld(World world) {
        return Echo.getByWorldName(world.getName()) != null;
    }

    public boolean hasReturnPoint(Ref<EntityStore> playerRef, Store<EntityStore> store) {
        InstanceEntityConfig entityConfig = store.getComponent(playerRef, InstanceEntityConfig.getComponentType());
        if (entityConfig == null) {
            return false;
        }
        WorldReturnPoint returnPoint = entityConfig.getReturnPoint();
        return returnPoint != null;
    }

    public Echo getCurrentEcho(World world) {
        return Echo.getByWorldName(world.getName());
    }

    private Transform getWorldSpawnPoint(World world, java.util.UUID playerUUID) {
        var spawnProvider = world.getWorldConfig().getSpawnProvider();
        if (spawnProvider != null) {
            Transform spawn = spawnProvider.getSpawnPoint(world, playerUUID);
            if (spawn != null) {
                return spawn;
            }
        }
        return new Transform(new Vector3d(0, 64, 0), new Vector3f(0, 0, 0));
    }

    private void initializeEchoWorldTinting(Echo echo, World world) {
        try {
            com.hypixel.seasons.Season echoSeason = echo.getSeason();
            if (echoSeason == null) {
                System.err.println("[ARCANE SEASONS] Failed to get season for echo: " + echo.getId());
                return;
            }

            com.hypixel.seasons.SeasonsModule module = com.hypixel.seasons.SeasonsModule.getInstance();
            if (module == null) {
                System.err.println("[ARCANE SEASONS] SeasonsModule not available");
                return;
            }

            String worldName = world.getName();
            com.hypixel.seasons.systems.GrassBlockTintingSystem tintingSystem =
                module.getTintingSystem(worldName);

            if (tintingSystem == null) {
                tintingSystem = new com.hypixel.seasons.systems.GrassBlockTintingSystem(world);
                module.registerTintingSystem(worldName, tintingSystem);
            }

            int tintColor = echoSeason.getGrassTintColor();
            tintingSystem.setCurrentTintColor(tintColor);

            applyZone1SunnyWeather(world);

            System.out.println("[ARCANE SEASONS] Initialized Echo world tinting: " + worldName +
                " with season " + echoSeason.getDisplayName());
        } catch (Exception e) {
            System.err.println("[ARCANE SEASONS] Error initializing Echo world tinting: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void applyZone1SunnyWeather(World world) {
        try {
            Store<EntityStore> entityStore = world.getEntityStore().getStore();
            WeatherResource weatherResource = entityStore.getResource(WeatherResource.getResourceType());
            if (weatherResource != null) {
                weatherResource.setForcedWeather("Zone1_Sunny");
            }

            WorldConfig worldConfig = world.getWorldConfig();
            if (worldConfig != null) {
                worldConfig.setForcedWeather("Zone1_Sunny");
                worldConfig.markChanged();
            }

            System.out.println("[ARCANE SEASONS] Applied Zone1_Sunny weather to echo world: " + world.getName());
        } catch (Exception e) {
            System.err.println("[ARCANE SEASONS] Error applying Zone1_Sunny weather: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
