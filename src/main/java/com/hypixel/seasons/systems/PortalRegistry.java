package com.hypixel.seasons.systems;

import java.util.HashMap;
import java.util.Map;

public class PortalRegistry {
    private static final PortalRegistry instance = new PortalRegistry();

    private final Map<String, PortalCoordinates> portals = new HashMap<>();

    public static PortalRegistry getInstance() {
        return instance;
    }

    public void logAllPortals() {
        if (portals.isEmpty()) {
            System.out.println("[ARCANE SEASONS] [PORTAL] No portals currently registered");
            return;
        }

        System.out.println("[ARCANE SEASONS] [PORTAL] Registered portals (" + portals.size() + " total):");
        for (Map.Entry<String, PortalCoordinates> entry : portals.entrySet()) {
            PortalCoordinates coords = entry.getValue();
            System.out.println("[ARCANE SEASONS] [PORTAL]  - " + entry.getKey() +
                    " at (" + coords.x + ", " + coords.y + ", " + coords.z + ")");
        }
    }

    public void registerPortal(String portalId, int x, int y, int z) {
        portals.put(portalId, new PortalCoordinates(x, y, z));
    }

    public void registerPortalFromPrefabSpawn(String portalId, int spawnX, int spawnY, int spawnZ,
                                               int offsetX, int offsetY, int offsetZ) {
        int portalX = spawnX + offsetX;
        int portalY = spawnY + offsetY;
        int portalZ = spawnZ + offsetZ;
        registerPortal(portalId, portalX, portalY, portalZ);
    }

    public PortalCoordinates getPortal(String portalId) {
        return portals.get(portalId);
    }

    public boolean isPortalAt(int x, int y, int z) {
        for (PortalCoordinates coords : portals.values()) {
            if (coords.matches(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    public String getPortalIdAt(int x, int y, int z) {
        for (Map.Entry<String, PortalCoordinates> entry : portals.entrySet()) {
            if (entry.getValue().matches(x, y, z)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public void clear() {
        portals.clear();
    }

    public int getPortalCount() {
        return portals.size();
    }

    public static class PortalCoordinates {
        public final int x;
        public final int y;
        public final int z;

        public PortalCoordinates(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public boolean matches(int checkX, int checkY, int checkZ) {
            return this.x == checkX && this.y == checkY && this.z == checkZ;
        }
    }
}
