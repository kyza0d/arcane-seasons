package com.hypixel.seasons.systems;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldNotificationHandler;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class GrassBlockTintingSystem {

    private static final int CHUNK_SIZE = 32;
    private static final int RENDER_DISTANCE = 16;

    private final World world;
    private int currentTintColor = 0xFF2ECC71;     // default spring/summer green

    public GrassBlockTintingSystem(World world) {
        this.world = world;
    }

    public World getWorld() {
        return world;
    }

    public void setCurrentTintColor(int color) {
        this.currentTintColor = color;
    }

    public int getCurrentTintColor() {
        return currentTintColor;
    }

    // this is the low level one that actually writes the tint into a chunk
    // assumes the chunk and its blockchunk already exist
    public void tintChunk(WorldChunk worldChunk, int tintColor) {
        if (worldChunk == null) {
            return;
        }

        BlockChunk blockChunk = worldChunk.getBlockChunk();
        if (blockChunk == null) {
            // this shouldnt really happen but ive seen it during world gen weirdness
            return;
        }

        // straight up paint every x,z in the chunk
        // no fancy per-biome or height stuff, just uniform color for now
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                blockChunk.setTint(x, z, tintColor);
            }
        }
    }

    // higher level version that also sends the chunk update notification
    // use this one when you actually want players to see the change
    public void tintChunkAndNotify(long chunkIndex, int tintColor) {
        if (world == null) {
            return;
        }

        WorldChunk worldChunk = world.getChunkIfInMemory(chunkIndex);
        if (worldChunk == null) {
            // chunk isnt loaded, nothing to do
            return;
        }

        tintChunk(worldChunk, tintColor);

        WorldNotificationHandler notificationHandler = world.getNotificationHandler();
        if (notificationHandler != null) {
            notificationHandler.updateChunk(chunkIndex);
            // this tells rendering / networking side that this chunk needs to be resent
            // without it players wont see the grass change until they relog or chunk reloads
        }
    }

    // this is the big one called on season change
    // finds every chunk thats currently visible to any player and tints it
    // returns how many chunks actually got tinted (useful for logging / debugging)
    public int tintAllChunksNearPlayers(int tintColor) {
        if (world == null) {
            return 0;
        }

        Set<Long> chunksToTint = new HashSet<>();
        Collection<PlayerRef> players = world.getPlayerRefs();

        if (players == null || players.isEmpty()) {
            return 0;   // no players = no work
        }

        // collect every chunk thats in render distance of any player
        for (PlayerRef playerRef : players) {
            Transform transform = playerRef.getTransform();
            if (transform == null) continue;

            Vector3d position = transform.getPosition();
            if (position == null) continue;

            int playerChunkX = MathUtil.floor(position.getX()) >> 5;
            int playerChunkZ = MathUtil.floor(position.getZ()) >> 5;

            // yes its a square not a circle, we dont care about corners being a bit extra
            for (int dx = -RENDER_DISTANCE; dx <= RENDER_DISTANCE; dx++) {
                for (int dz = -RENDER_DISTANCE; dz <= RENDER_DISTANCE; dz++) {
                    int chunkX = playerChunkX + dx;
                    int chunkZ = playerChunkZ + dz;
                    long chunkIndex = ChunkUtil.indexChunk(chunkX, chunkZ);
                    chunksToTint.add(chunkIndex);
                }
            }
        }

        Set<Long> tintedChunks = new HashSet<>();

        // now actually tint the ones that are loaded
        for (long chunkIndex : chunksToTint) {
            WorldChunk worldChunk = world.getChunkIfInMemory(chunkIndex);
            if (worldChunk == null) continue;

            tintChunk(worldChunk, tintColor);
            tintedChunks.add(chunkIndex);
        }

        // batch notify all affected chunks at once
        WorldNotificationHandler notificationHandler = world.getNotificationHandler();
        if (notificationHandler != null) {
            for (long chunkIndex : tintedChunks) {
                notificationHandler.updateChunk(chunkIndex);
            }
        }

        // little debug print so we can see in console how heavy season changes are
        if (!tintedChunks.isEmpty()) {
            System.out.println("[ARCANE SEASONS] Tinted " + tintedChunks.size() + " chunks with color 0x" + Integer.toHexString(tintColor));
        }

        return tintedChunks.size();
    }

    // helper to check if a given chunk coord is visible to anyone right now
    // used by the per chunk tinting calls
    public boolean isChunkNearAnyPlayer(int chunkX, int chunkZ) {
        if (world == null) return false;

        Collection<PlayerRef> players = world.getPlayerRefs();
        if (players == null || players.isEmpty()) return false;

        for (PlayerRef playerRef : players) {
            Transform transform = playerRef.getTransform();
            if (transform == null) continue;

            Vector3d position = transform.getPosition();
            if (position == null) continue;

            int playerChunkX = MathUtil.floor(position.getX()) >> 5;
            int playerChunkZ = MathUtil.floor(position.getZ()) >> 5;

            int distance = Math.max(Math.abs(chunkX - playerChunkX), Math.abs(chunkZ - playerChunkZ));

            if (distance <= RENDER_DISTANCE) {
                return true;
            }
        }

        return false;
    }

    // convenience method - only tints if the chunk is actually visible to someone
    // good for when grass blocks get placed/updated during gameplay
    public void tintChunkIfNearPlayer(WorldChunk worldChunk, int tintColor) {
        if (worldChunk == null) return;

        int chunkX = worldChunk.getX();
        int chunkZ = worldChunk.getZ();

        if (!isChunkNearAnyPlayer(chunkX, chunkZ)) {
            return;     // dont waste time tinting chunks nobody can see
        }

        tintChunk(worldChunk, tintColor);

        long chunkIndex = ChunkUtil.indexChunk(chunkX, chunkZ);
        WorldNotificationHandler notificationHandler = world.getNotificationHandler();
        if (notificationHandler != null) {
            notificationHandler.updateChunk(chunkIndex);
        }
    }

    // main season change hook
    // sets the new color and immediately applies it to visible chunks
    public void onSeasonChange(int newTintColor) {
        System.out.println("[ARCANE SEASONS] season changed, applying new tint color: 0x" + Integer.toHexString(newTintColor));

        this.currentTintColor = newTintColor;
        tintAllChunksNearPlayers(newTintColor);

        // note: we dont tint unloaded chunks here
        // when they eventually load they should call tintChunkIfNearPlayer() or similar
        // thats handled in the chunk loader / block update path somewhere else
    }
}
