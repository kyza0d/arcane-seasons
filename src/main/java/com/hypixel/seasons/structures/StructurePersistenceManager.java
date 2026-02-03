package com.hypixel.seasons.structures;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class StructurePersistenceManager {
  private final Store<EntityStore> store;
  private static StructureDataResource localFallback = null;

  public StructurePersistenceManager(Store<EntityStore> store) {
    this.store = store;
  }

  public StructureDataResource loadOrCreate() {
    try {
      if (store == null) {
        System.out.println("[ARCANE SEASONS] store is null");
        return getOrCreateFallback();
      }
      StructureDataResource resource = store.getResource(StructureDataResource.getResourceType());
      if (resource == null) {
        resource = new StructureDataResource();
        try {
          store.replaceResource(StructureDataResource.getResourceType(), resource);
        } catch (Exception e) {
          System.out.println("[ARCANE SEASONS] could not store resource: " + e.getMessage());
        }
      }
      return resource;
    } catch (NullPointerException e) {
      System.out.println("[ARCANE SEASONS] registry not available in loadOrCreate");
      return getOrCreateFallback();
    } catch (Exception e) {
      System.out.println("[ARCANE SEASONS] StructurePersistenceManager: error in loadOrCreate: " + e.getMessage());
      return getOrCreateFallback();
    }
  }

  public StructureDataResource load() {
    try {
      if (store == null) {
        System.out.println("[ARCANE SEASONS] StructurePersistenceManager: store is null in load, using fallback");
        return localFallback;
      }
      return store.getResource(StructureDataResource.getResourceType());
    } catch (NullPointerException e) {
      System.out.println("[ARCANE SEASONS] StructurePersistenceManager: Registry not available in load, using fallback");
      return localFallback;
    } catch (Exception e) {
      System.out.println("[ARCANE SEASONS] StructurePersistenceManager: Error in load: " + e.getMessage());
      return localFallback;
    }
  }

  public void save(StructureDataResource resource) {
    try {
      if (store == null) {
        System.out.println("[ARCANE SEASONS] store is null");
        localFallback = resource;
        return;
      }
      store.replaceResource(StructureDataResource.getResourceType(), resource);
    } catch (NullPointerException e) {
      System.out.println("[ARCANE SEASONS] registry not available");
      localFallback = resource;
    } catch (Exception e) {
      System.out.println("[ARCANE SEASONS]" + e.getMessage());
      localFallback = resource;
    }
  }

  public void markInitialized(StructureDataResource resource) {
    resource.setInitialized(true);
    resource.setInitializationTime(System.currentTimeMillis());
    save(resource);
  }

  private StructureDataResource getOrCreateFallback() {
    if (localFallback == null) {
      localFallback = new StructureDataResource();
    }
    return localFallback;
  }
}
