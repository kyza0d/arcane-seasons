package com.hypixel.seasons.structures;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;

public class StructureConfiguration {
  private final String id;
  private final String prefabPath;
  private final String displayName;
  private final String markerIcon;
  private final Vector3i position;
  private final PrefabRotation rotation;
  private final int discoveryRadius;
  private final boolean showOnMap;
  private final String[] zoneMask;
  private final String[] biomeMask;
  private final Object pattern;
  private final boolean unique;

  private StructureConfiguration(Builder builder) {
    this.id = builder.id;
    this.prefabPath = builder.prefabPath;
    this.displayName = builder.displayName;
    this.markerIcon = builder.markerIcon;
    this.position = builder.position;
    this.rotation = builder.rotation;
    this.discoveryRadius = builder.discoveryRadius;
    this.showOnMap = builder.showOnMap;
    this.zoneMask = builder.zoneMask;
    this.biomeMask = builder.biomeMask;
    this.pattern = builder.pattern;
    this.unique = builder.unique;
  }

  public String getId() {
    return id;
  }

  public String getPrefabPath() {
    return prefabPath;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getMarkerIcon() {
    return markerIcon;
  }

  public Vector3i getPosition() {
    return position;
  }

  public PrefabRotation getRotation() {
    return rotation;
  }

  public int getDiscoveryRadius() {
    return discoveryRadius;
  }

  public boolean isShowOnMap() {
    return showOnMap;
  }

  public String[] getZoneMask() {
    return zoneMask;
  }

  public String[] getBiomeMask() {
    return biomeMask;
  }

  public Object getPattern() {
    return pattern;
  }

  public boolean isUnique() {
    return unique;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String id;
    private String prefabPath;
    private String displayName;
    private String markerIcon = "Spawn.png";
    private Vector3i position = null;
    private PrefabRotation rotation = null;
    private int discoveryRadius = 50;
    private boolean showOnMap = true;
    private String[] zoneMask = new String[]{"*"};
    private String[] biomeMask = new String[]{"*"};
    private Object pattern = null;
    private boolean unique = false;

    public Builder id(String id) {
      this.id = id;
      return this;
    }

    public Builder prefabPath(String prefabPath) {
      this.prefabPath = prefabPath;
      return this;
    }

    public Builder displayName(String displayName) {
      this.displayName = displayName;
      return this;
    }

    public Builder markerIcon(String markerIcon) {
      this.markerIcon = markerIcon;
      return this;
    }

    public Builder position(Vector3i position) {
      this.position = position;
      return this;
    }

    public Builder position(int x, int y, int z) {
      this.position = new Vector3i(x, y, z);
      return this;
    }

    public Builder rotation(PrefabRotation rotation) {
      this.rotation = rotation;
      return this;
    }

    public Builder discoveryRadius(int discoveryRadius) {
      this.discoveryRadius = discoveryRadius;
      return this;
    }

    public Builder showOnMap(boolean showOnMap) {
      this.showOnMap = showOnMap;
      return this;
    }

    public Builder zoneMask(String[] zoneMask) {
      this.zoneMask = zoneMask;
      return this;
    }

    public Builder biomeMask(String[] biomeMask) {
      this.biomeMask = biomeMask;
      return this;
    }

    public Builder pattern(Object pattern) {
      this.pattern = pattern;
      return this;
    }

    public Builder unique(boolean unique) {
      this.unique = unique;
      return this;
    }

    public StructureConfiguration build() {
      if (prefabPath == null || prefabPath.isEmpty()) {
        throw new IllegalStateException("prfab not found");
      }
      if (displayName == null || displayName.isEmpty()) {
        this.displayName = id;
      }
      return new StructureConfiguration(this);
    }
  }
}
