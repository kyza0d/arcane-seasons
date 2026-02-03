package com.hypixel.seasons.systems;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

public class BlockPortalDetector {

    private static final String PORTAL_BLOCK_NAME = "Memory_Echo_Portal_Enter";
    private static final int CHUNK_SIZE = 32;

    private int portalBlockId = Integer.MIN_VALUE;
    private boolean initialized = false;

    public BlockPortalDetector() {
        initializeBlockId();
    }

    private void initializeBlockId() {
        if (initialized) {
            return;
        }

        try {
            this.portalBlockId = BlockType.getAssetMap().getIndex(PORTAL_BLOCK_NAME);
            this.initialized = true;

            if (this.portalBlockId == Integer.MIN_VALUE) {
                System.err.println("[ARCANE SEASONS] [PORTAL] [DETECTOR] WARNING: Could not find '" +
                    PORTAL_BLOCK_NAME + "' block in asset map");
            } else {
                System.out.println("[ARCANE SEASONS] [PORTAL] [DETECTOR] Cached " + PORTAL_BLOCK_NAME +
                    " block ID: " + this.portalBlockId);
            }
        } catch (Exception e) {
            System.err.println("[ARCANE SEASONS] [PORTAL] [DETECTOR] ERROR: Failed to initialize block ID: " +
                e.getMessage());
            this.initialized = true;
            this.portalBlockId = Integer.MIN_VALUE;
        }
    }

    public boolean isPlayerOnPortalBlock(World world, Vector3d playerPosition) {
        if (world == null || playerPosition == null) {
            return false;
        }

        if (portalBlockId == Integer.MIN_VALUE) {
            return false;
        }

        int blockX = (int) Math.floor(playerPosition.x);
        int blockY = (int) Math.floor(playerPosition.y) - 1;
        int blockZ = (int) Math.floor(playerPosition.z);

        return isPortalBlockAt(world, blockX, blockY, blockZ);
    }

    private boolean isPortalBlockAt(World world, int x, int y, int z) {
        try {
            int chunkX = x >> 5;
            int chunkZ = z >> 5;
            int localX = x & 0x1F;
            int localZ = z & 0x1F;

            long chunkIndex = encodeChunkIndex(chunkX, chunkZ);
            WorldChunk worldChunk = world.getChunkIfInMemory(chunkIndex);

            if (worldChunk == null) {
                return false;
            }

            int blockId = worldChunk.getBlock(localX, y, localZ);
            return blockId == portalBlockId;
        } catch (Exception e) {
            System.err.println("[ARCANE SEASONS] [PORTAL] [DETECTOR] ERROR checking portal block: " +
                e.getMessage());
            return false;
        }
    }

    private long encodeChunkIndex(int chunkX, int chunkZ) {
        return ((long) chunkZ << 32) | (chunkX & 0xFFFFFFFFL);
    }
}
