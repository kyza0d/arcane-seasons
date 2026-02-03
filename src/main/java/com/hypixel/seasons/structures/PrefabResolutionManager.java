package com.hypixel.seasons.structures;

import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PrefabResolutionManager {
    private final Map<String, IPrefabBuffer> cache = new ConcurrentHashMap<>();

    public IPrefabBuffer getPrefab(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }

        String normalized = normalizePrefabKey(key);

        IPrefabBuffer cached = cache.get(normalized);
        if (cached != null) {
            return cached;
        }

        IPrefabBuffer buffer = loadFromAssetPacks(normalized);
        if (buffer != null) {
            cache.put(normalized, buffer);
            System.out.println("[ARCANE SEASONS] Prefab loaded and cached: " + normalized);
        }
        return buffer;
    }

    public void releasePrefab(String key) {
        if (key == null || key.isEmpty()) {
            return;
        }
        String normalized = normalizePrefabKey(key);
        IPrefabBuffer removed = cache.remove(normalized);
        if (removed != null) {
            removed.release();
        }
        System.out.println("[ARCANE SEASONS] Prefab released: " + normalized);
    }

    private String normalizePrefabKey(String key) {
        if (key == null || key.isEmpty()) {
            return key;
        }
        return key.replace('.', '/').replace("\\", "/");
    }

    private IPrefabBuffer loadFromAssetPacks(String normalizedKey) {
        try {
            PrefabStore prefabStore = PrefabStore.get();
            if (prefabStore == null) {
                System.err.println("[ARCANE SEASONS] PrefabStore not available");
                return null;
            }

            String[] pathFormats = {
                normalizedKey + ".prefab.json",
                "Server/Prefabs/" + normalizedKey + ".prefab.json",
                "structures/" + normalizedKey + ".prefab.json"
            };

            for (String pathFormat : pathFormats) {
                Path prefabPath = prefabStore.findAssetPrefabPath(pathFormat);
                if (prefabPath != null) {
                    return PrefabBufferUtil.getCached(prefabPath);
                }
            }

            System.err.println("[ARCANE SEASONS] Failed to load prefab: " + normalizedKey);
            return null;
        } catch (Exception e) {
            System.err.println("[ARCANE SEASONS] Error loading prefab: " + normalizedKey + " - " + e.getMessage());
            return null;
        }
    }
}
