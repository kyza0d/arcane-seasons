package com.hypixel.seasons.structures;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class UniquePlacementPersistence {
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final Type SET_TYPE = new TypeToken<HashSet<String>>(){}.getType();
  private static final Path BASE_PATH = Paths.get("config", "seasons", "structures");

  private static volatile UniquePlacementPersistence instance;
  private static final Object INSTANCE_LOCK = new Object();

  private final Path filePath;
  private final Set<String> placedStructures;
  private final ReadWriteLock lock;
  private final Set<String> pendingPlacements;

  private UniquePlacementPersistence() {
    this.filePath = BASE_PATH.resolve("unique_placements.json");
    this.placedStructures = ConcurrentHashMap.newKeySet();
    this.lock = new ReentrantReadWriteLock();
    this.pendingPlacements = ConcurrentHashMap.newKeySet();
    load();
  }

  public static UniquePlacementPersistence get() {
    if (instance == null) {
      synchronized (INSTANCE_LOCK) {
        if (instance == null) {
          instance = new UniquePlacementPersistence();
        }
      }
    }
    return instance;
  }

  public static void initialize() {
    get();
    System.out.println("[ARCANE SEASONS] UniquePlacementPersistence initialized with " +
      get().placedStructures.size() + " placed structures");
  }

  public boolean hasBeenPlaced(String structureId) {
    lock.readLock().lock();
    try {
      return placedStructures.contains(structureId);
    } finally {
      lock.readLock().unlock();
    }
  }

  public boolean tryReservePlacement(String structureId) {
    if (hasBeenPlaced(structureId)) {
      return false;
    }
    return pendingPlacements.add(structureId);
  }

  public void releasePendingPlacement(String structureId) {
    pendingPlacements.remove(structureId);
  }

  public void markAsPlaced(String structureId) {
    lock.writeLock().lock();
    try {
      placedStructures.add(structureId);
      pendingPlacements.remove(structureId);
      save();
    } finally {
      lock.writeLock().unlock();
    }
  }

  public Set<String> getPlacedStructures() {
    lock.readLock().lock();
    try {
      return new HashSet<>(placedStructures);
    } finally {
      lock.readLock().unlock();
    }
  }

  private void load() {
    try {
      if (Files.exists(filePath)) {
        String content = Files.readString(filePath);
        Set<String> loaded = GSON.fromJson(content, SET_TYPE);
        if (loaded != null) {
          placedStructures.addAll(loaded);
        }
        System.out.println("[ARCANE SEASONS] Loaded " + placedStructures.size() +
          " unique placements from " + filePath);
      } else {
        System.out.println("[ARCANE SEASONS] No existing unique placements file found at " + filePath);
      }
    } catch (IOException e) {
      System.err.println("[ARCANE SEASONS] error loading unique placements: " + e.getMessage());
    } catch (Exception e) {
      System.err.println("[ARCANE SEASONS] error parsing unique placements: " + e.getMessage());
    }
  }

  private void save() {
    try {
      Files.createDirectories(filePath.getParent());
      String json = GSON.toJson(new HashSet<>(placedStructures), SET_TYPE);
      Files.writeString(filePath, json);
    } catch (IOException e) {
      System.err.println("[ARCANE SEASONS] error saving unique placements: " + e.getMessage());
    }
  }

  public void reload() {
    lock.writeLock().lock();
    try {
      placedStructures.clear();
      load();
    } finally {
      lock.writeLock().unlock();
    }
  }
}
