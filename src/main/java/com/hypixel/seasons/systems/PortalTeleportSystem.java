package com.hypixel.seasons.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.seasons.ui.MemoryPortalSelectionUI;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PortalTeleportSystem extends DelayedEntitySystem<EntityStore> {

    private static final long TELEPORT_COOLDOWN_MS = 3000;
    private static final double POSITION_CHANGE_THRESHOLD = 0.1;

    private final Map<UUID, Long> recentlyTeleported = new HashMap<>();
    private final Map<UUID, Vector3d> lastLoggedPosition = new HashMap<>();
    private final BlockPortalDetector portalDetector = new BlockPortalDetector();

    public PortalTeleportSystem() {
        super(0.2f);
        System.out.println("[ARCANE SEASONS] [PORTAL] PortalTeleportSystem initialized");
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> archetypeChunk,
                     Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {

        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        Player player = store.getComponent(playerRef, Player.getComponentType());
        TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());

        if (player == null || transform == null) {
            return;
        }

        Vector3d position = transform.getPosition();
        UUID playerId = getPlayerId(player);

        World playerWorld = player.getWorld();
        boolean isPortal = portalDetector.isPlayerOnPortalBlock(playerWorld, position);

        if (isPortal) {
            int blockX = (int) Math.floor(position.x);
            int blockY = (int) Math.floor(position.y) - 1;
            int blockZ = (int) Math.floor(position.z);

            System.out.println("[ARCANE SEASONS] [PORTAL] [DETECT] Memory_Echo_Portal_Enter block detected at: (" +
                    blockX + ", " + blockY + ", " + blockZ + ")");

            if (playerId != null) {
                boolean onCooldown = isOnCooldown(playerId);
                long cooldownRemaining = getCooldownRemaining(playerId);

                if (onCooldown) {
                    System.out.println("[ARCANE SEASONS] [PORTAL] [COOLDOWN] Player " + playerId +
                            " is on cooldown. Remaining: " + cooldownRemaining + "ms");
                } else {
                    System.out.println("[ARCANE SEASONS] [PORTAL] [ATTEMPT] Showing echo selection UI for player " + playerId);
                    showEchoSelectionUI(player, playerRef, store);
                    setCooldown(playerId);
                }
            }
        }
    }

    private boolean shouldLogPosition(UUID playerId, Vector3d currentPosition) {
        Vector3d lastPos = lastLoggedPosition.get(playerId);
        if (lastPos == null) {
            return true;
        }
        double distance = Math.sqrt(
                Math.pow(currentPosition.x - lastPos.x, 2) +
                Math.pow(currentPosition.y - lastPos.y, 2) +
                Math.pow(currentPosition.z - lastPos.z, 2)
        );
        return distance > POSITION_CHANGE_THRESHOLD;
    }

    private long getCooldownRemaining(UUID playerId) {
        Long lastTeleport = recentlyTeleported.get(playerId);
        if (lastTeleport == null) {
            return 0;
        }
        long timeSinceLastTeleport = System.currentTimeMillis() - lastTeleport;
        long remaining = TELEPORT_COOLDOWN_MS - timeSinceLastTeleport;
        return Math.max(0, remaining);
    }

    private void showEchoSelectionUI(Player player, Ref<EntityStore> playerRef, Store<EntityStore> store) {
        try {
            World currentWorld = player.getWorld();
            if (currentWorld == null) {
                System.err.println("[ARCANE SEASONS] [PORTAL] [ERROR] Player's current world is null");
                return;
            }

            PlayerRef pRef = convertToPlayerRef(playerRef, store, player);
            if (pRef == null) {
                System.err.println("[ARCANE SEASONS] [PORTAL] [ERROR] Failed to convert to PlayerRef");
                return;
            }

            MemoryPortalSelectionUI selectionUI = new MemoryPortalSelectionUI(pRef, CustomPageLifetime.CanDismiss, currentWorld);
            player.getPageManager().openCustomPage(playerRef, store, selectionUI);
            System.out.println("[ARCANE SEASONS] [PORTAL] [SUCCESS] Echo selection UI opened for player");
        } catch (Exception e) {
            System.err.println("[ARCANE SEASONS] [PORTAL] [ERROR] Exception showing echo selection UI: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private PlayerRef convertToPlayerRef(Ref<EntityStore> ref, Store<EntityStore> store, Player player) {
        try {
            UUID playerUuid = player.getUuid();
            if (playerUuid == null) {
                System.err.println("[ARCANE SEASONS] [PORTAL] [ERROR] Player UUID is null");
                return null;
            }

            PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
            if (playerRef == null) {
                System.err.println("[ARCANE SEASONS] [PORTAL] [ERROR] Could not get PlayerRef for UUID: " + playerUuid);
                return null;
            }
            System.out.println("[ARCANE SEASONS] [PORTAL] [INFO] PlayerRef retrieved for UUID: " + playerUuid);
            return playerRef;
        } catch (Exception e) {
            System.err.println("[ARCANE SEASONS] [PORTAL] [ERROR] Exception converting to PlayerRef: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private UUID getPlayerId(Player player) {
        try {
            return player.getUuid();
        } catch (Exception e) {
            System.err.println("[ARCANE SEASONS] Error getting player ID: " + e.getMessage());
            return null;
        }
    }

    private boolean isOnCooldown(UUID playerId) {
        Long lastTeleport = recentlyTeleported.get(playerId);
        if (lastTeleport == null) {
            return false;
        }

        long timeSinceLastTeleport = System.currentTimeMillis() - lastTeleport;
        return timeSinceLastTeleport < TELEPORT_COOLDOWN_MS;
    }

    private void setCooldown(UUID playerId) {
        recentlyTeleported.put(playerId, System.currentTimeMillis());
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType(), TransformComponent.getComponentType());
    }
}
