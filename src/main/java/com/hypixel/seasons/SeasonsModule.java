package com.hypixel.seasons;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.seasons.commands.SeasonCommand;
import com.hypixel.seasons.components.PlayerSeasonProgress;
import com.hypixel.seasons.interactions.EchoReturnInteraction;
import com.hypixel.seasons.interactions.EchoTeleportInteraction;
import com.hypixel.seasons.resources.SeasonResource;
import com.hypixel.seasons.structures.SeasonStructureInitializer;
import com.hypixel.seasons.systems.GrassBlockTintingSystem;
import com.hypixel.seasons.systems.PlayerWakeUpProgressSystem;
import com.hypixel.seasons.systems.PortalRegistry;
import com.hypixel.seasons.systems.PortalTeleportSystem;
import com.hypixel.seasons.systems.SeasonSystem;
import com.hypixel.seasons.systems.SeasonWeatherManager;
import java.util.concurrent.ConcurrentHashMap;

public class SeasonsModule extends JavaPlugin {
  private static SeasonsModule instance;

  private SeasonWeatherManager weatherManager;
  private SeasonSystem seasonSystem;
  private SeasonCommand seasonCommand;
  private SeasonStructureInitializer structureInitializer;
  private ResourceType<EntityStore, SeasonResource> seasonResourceType;
  private ComponentType<EntityStore, PlayerSeasonProgress> playerSeasonProgressComponentType;
  private final ConcurrentHashMap<String, GrassBlockTintingSystem> worldTintingSystems = new ConcurrentHashMap<>();

  public SeasonsModule(JavaPluginInit init) {
    super(init);
    instance = this;
  }

  public static SeasonsModule getInstance() {
    return instance;
  }

  public static GrassBlockTintingSystem getTintingSystemForWorld(String worldName) {
    if (instance == null) {
      return null;
    }
    return instance.worldTintingSystems.get(worldName);
  }

  @Override
  public void setup() {
    System.out.println("[" + PluginConfig.NAME + "] Setting up...");

    this.weatherManager = new SeasonWeatherManager();

    registerResources();
    registerInteractions();
    registerEcsSystems();

    this.structureInitializer = SeasonStructureInitializer.get();
    structureInitializer.setup();

    registerSanctuaryStructure();
    registerEventListeners();
  }

  private void registerEcsSystems() {
    System.out.println("[" + PluginConfig.NAME + "] Registering ECS systems...");

    try {
      this.getEntityStoreRegistry().registerSystem(new PortalTeleportSystem());
      System.out.println("[" + PluginConfig.NAME + "] PortalTeleportSystem registered");
    } catch (Exception e) {
      System.err.println("[" + PluginConfig.NAME + "] Failed to register PortalTeleportSystem: " + e.getMessage());
      e.printStackTrace();
    }

    try {
      this.getEntityStoreRegistry().registerSystem(new PlayerWakeUpProgressSystem());
      System.out.println("[" + PluginConfig.NAME + "] PlayerWakeUpProgressSystem registered");
    } catch (Exception e) {
      System.err.println("[" + PluginConfig.NAME + "] Failed to register PlayerWakeUpProgressSystem: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private void registerEventListeners() {
    System.out.println("[" + PluginConfig.NAME + "] Registering event listeners...");

    try {
      getEventRegistry().registerGlobal(
        com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent.class,
        event -> {
          if (structureInitializer != null && structureInitializer.getGenerationIntegration() != null) {
            structureInitializer.getGenerationIntegration().onChunkPreLoad(event);
          }

          try {
            WorldChunk chunk = event.getChunk();
            if (chunk == null) {
              return;
            }

            World world = chunk.getWorld();
            if (world == null) {
              return;
            }

            String worldName = world.getName();
            com.hypixel.seasons.echo.Echo echo = com.hypixel.seasons.echo.Echo.getByWorldName(worldName);
            if (echo == null) {
              return;
            }

            GrassBlockTintingSystem tintingSystem = worldTintingSystems.computeIfAbsent(
              worldName,
              name -> new GrassBlockTintingSystem(world)
            );

            Season currentSeason = getCurrentSeasonForWorld(world);
            int tintColor = currentSeason.getGrassTintColor();
            tintingSystem.setCurrentTintColor(tintColor);

            tintingSystem.tintChunk(chunk, tintColor);
          } catch (Exception e) {
            System.err.println("[ARCANE SEASONS] Error in ChunkPreLoadProcessEvent: " + e.getMessage());
          }
        }
      );
      System.out.println("[" + PluginConfig.NAME + "] ChunkPreLoadProcessEvent listener registered");
    } catch (Exception e) {
      System.err.println("[" + PluginConfig.NAME + "] Failed to register ChunkPreLoadProcessEvent listener: " + e.getMessage());
      e.printStackTrace();
    }

    try {
      getEventRegistry().registerGlobal(
        com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent.class,
        event -> {
          structureInitializer.onPlayerJoin(event);

          try {
            World world = event.getWorld();
            if (world == null) {
              return;
            }

            String worldName = world.getName();
            com.hypixel.seasons.echo.Echo echo = com.hypixel.seasons.echo.Echo.getByWorldName(worldName);
            if (echo == null) {
              return;
            }

            GrassBlockTintingSystem tintingSystem = worldTintingSystems.computeIfAbsent(
              worldName,
              name -> new GrassBlockTintingSystem(world)
            );

            Season currentSeason = getCurrentSeasonForWorld(world);
            int tintColor = currentSeason.getGrassTintColor();
            tintingSystem.setCurrentTintColor(tintColor);

            world.execute(() -> {
              try {
                int tinted = tintingSystem.tintAllChunksNearPlayers(tintColor);
                if (tinted > 0) {
                  System.out.println("[ARCANE SEASONS] Applied " + currentSeason.getDisplayName() +
                    " tint on player join (" + tinted + " chunks)");
                }
              } catch (Exception e) {
                System.err.println("[ARCANE SEASONS] Error applying tint on player join: " + e.getMessage());
              }
            });
          } catch (Exception e) {
            System.err.println("[ARCANE SEASONS] Error in AddPlayerToWorldEvent: " + e.getMessage());
          }
        }
      );
      System.out.println("[" + PluginConfig.NAME + "] AddPlayerToWorldEvent listener registered");
    } catch (Exception e) {
      System.err.println("[" + PluginConfig.NAME + "] Failed to register AddPlayerToWorldEvent listener: " + e.getMessage());
    }

    try {
      getEventRegistry().registerGlobal(
        com.hypixel.hytale.server.core.universe.world.events.StartWorldEvent.class,
        event -> {
          structureInitializer.onWorldStart(event);

          try {
            World world = event.getWorld();
            if (world == null) {
              return;
            }

            String worldName = world.getName();
            com.hypixel.seasons.echo.Echo echo = com.hypixel.seasons.echo.Echo.getByWorldName(worldName);
            if (echo == null) {
              return;
            }

            GrassBlockTintingSystem tintingSystem = new GrassBlockTintingSystem(world);
            worldTintingSystems.put(worldName, tintingSystem);

            Season currentSeason = getCurrentSeasonForWorld(world);
            tintingSystem.setCurrentTintColor(currentSeason.getGrassTintColor());

            System.out.println("[ARCANE SEASONS] Initialized tinting system for world: " + worldName);
          } catch (Exception e) {
            System.err.println("[ARCANE SEASONS] Error in StartWorldEvent: " + e.getMessage());
          }
        }
      );
      System.out.println("[" + PluginConfig.NAME + "] StartWorldEvent listener registered");
    } catch (Exception e) {
      System.err.println("[" + PluginConfig.NAME + "] Failed to register StartWorldEvent listener: " + e.getMessage());
    }
  }

  private Season getCurrentSeasonForWorld(World world) {
    try {
      String worldName = world.getName();
      com.hypixel.seasons.echo.Echo echo = com.hypixel.seasons.echo.Echo.getByWorldName(worldName);

      if (echo != null) {
        Season echoSeason = echo.getSeason();
        if (echoSeason != null) {
          return echoSeason;
        }
      }
    } catch (Exception e) {
    }

    try {
      if (seasonResourceType == null) {
        return Season.SPRING;
      }
      EntityStore entityStore = world.getEntityStore();
      Store<EntityStore> store = entityStore.getStore();
      SeasonResource seasonResource = store.getResource(seasonResourceType);
      if (seasonResource != null) {
        Season season = seasonResource.getCurrentSeason();
        if (season != null) {
          return season;
        }
      }
    } catch (Exception e) {
    }
    return Season.SPRING;
  }

  @Override
  public void start() {
    System.out.println("[" + PluginConfig.NAME + "] Starting...");

    try {
      registerCommands();
      System.out.println("[" + PluginConfig.NAME + "] Started successfully!");
    } catch (Exception e) {
      System.out.println("[" + PluginConfig.NAME + "] Error during startup: " + e.getMessage());
      e.printStackTrace();
    }
  }

  @Override
  public void shutdown() {
    System.out.println("[" + PluginConfig.NAME + "] Shutting down...");
    worldTintingSystems.clear();
    instance = null;
  }

  private void registerSanctuaryStructure() {
    System.out.println("[" + PluginConfig.NAME + "] Registering Sanctuary structure...");

    try {
      com.hypixel.seasons.structures.StructureConfiguration sanctuaryConfig =
      com.hypixel.seasons.structures.StructureConfiguration.builder()
      .id("sanctuary")
      .prefabPath("structures.sanctuary")
      .displayName("Sanctuary")
      .zoneMask(new String[]{"Zone1_Tier3"})
      .biomeMask(new String[]{"*"})
      .rotation(null)
      .position(null)
      .discoveryRadius(100)
      .showOnMap(true)
      .unique(true)
      .build();

      structureInitializer.registerConfiguration(sanctuaryConfig);
      System.out.println("[" + PluginConfig.NAME + "] Sanctuary structure registered successfully");
    } catch (Exception e) {
      System.err.println("[" + PluginConfig.NAME + "] Error registering Sanctuary structure: " + e.getMessage());
      e.printStackTrace();
    }

    System.out.println("[" + PluginConfig.NAME + "] Registering Echo_Portal structures...");

    try {
      com.hypixel.seasons.structures.StructureConfiguration echoPortalConfig =
      com.hypixel.seasons.structures.StructureConfiguration.builder()
      .id("Echo_Portal_Main")
      .prefabPath("structures.sanctuary")
      .displayName("Echo Portal")
      .zoneMask(new String[]{"*"})
      .biomeMask(new String[]{"*"})
      .rotation(null)
      .position(100, 64, 100)
      .discoveryRadius(50)
      .showOnMap(true)
      .unique(true)
      .build();

      structureInitializer.registerConfiguration(echoPortalConfig);
      System.out.println("[" + PluginConfig.NAME + "] Echo_Portal structure registered successfully");
    } catch (Exception e) {
      System.err.println("[" + PluginConfig.NAME + "] Error registering Echo_Portal structure: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private void registerResources() {
    System.out.println("[" + PluginConfig.NAME + "] Registering SeasonResource...");
    this.seasonResourceType = this.getEntityStoreRegistry().registerResource(SeasonResource.class, SeasonResource::new);
    System.out.println("[" + PluginConfig.NAME + "] SeasonResource registered successfully");

    System.out.println("[" + PluginConfig.NAME + "] Registering PlayerSeasonProgress component...");
    this.playerSeasonProgressComponentType = this.getEntityStoreRegistry().registerComponent(
      PlayerSeasonProgress.class,
      "PlayerSeasonProgress",
      PlayerSeasonProgress.CODEC
    );
    System.out.println("[" + PluginConfig.NAME + "] PlayerSeasonProgress component registered successfully");
  }

  private void registerInteractions() {
    System.out.println("[" + PluginConfig.NAME + "] Registering interactions...");

    try {
      getCodecRegistry(Interaction.CODEC).register(
        "EchoTeleport",
        EchoTeleportInteraction.class,
        EchoTeleportInteraction.CODEC
      );
      System.out.println("[" + PluginConfig.NAME + "] EchoTeleportInteraction registered");
    } catch (Exception e) {
      System.err.println("[" + PluginConfig.NAME + "] Failed to register EchoTeleportInteraction: " + e.getMessage());
      e.printStackTrace();
    }

    try {
      getCodecRegistry(Interaction.CODEC).register(
        "EchoReturn",
        EchoReturnInteraction.class,
        EchoReturnInteraction.CODEC
      );
      System.out.println("[" + PluginConfig.NAME + "] EchoReturnInteraction registered");
    } catch (Exception e) {
      System.err.println("[" + PluginConfig.NAME + "] Failed to register EchoReturnInteraction: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private void registerCommands() {
    System.out.println("[" + PluginConfig.NAME + "] Registering commands...");

    CommandManager commandManager = CommandManager.get();
    if (commandManager == null) {
      System.out.println("[" + PluginConfig.NAME + "] WARNING: CommandManager not available");
      return;
    }

    try {
      this.seasonCommand = new SeasonCommand();
      commandManager.register(seasonCommand);
      System.out.println("[" + PluginConfig.NAME + "] Registered /season command");
    } catch (Exception e) {
      System.out.println("[" + PluginConfig.NAME + "] Error registering season command: " + e.getMessage());
      e.printStackTrace();
    }

    System.out.println("[" + PluginConfig.NAME + "] Portal registry contains " +
      PortalRegistry.getInstance().getPortalCount() + " portals");
    PortalRegistry.getInstance().logAllPortals();
  }

  public SeasonWeatherManager getWeatherManager() {
    return weatherManager;
  }

  public SeasonSystem createSeasonSystem(World world, Store<EntityStore> store) {
    String worldName = world.getName();
    com.hypixel.seasons.echo.Echo echo = com.hypixel.seasons.echo.Echo.getByWorldName(worldName);

    GrassBlockTintingSystem tintingSystem = null;
    if (echo != null) {
      tintingSystem = worldTintingSystems.computeIfAbsent(
        worldName,
        name -> new GrassBlockTintingSystem(world)
      );
    }
    return new SeasonSystem(world, store, weatherManager, tintingSystem);
  }

  public SeasonCommand getSeasonCommand() {
    return seasonCommand;
  }

  public SeasonStructureInitializer getStructureInitializer() {
    return structureInitializer;
  }

  public GrassBlockTintingSystem getTintingSystem(String worldName) {
    return worldTintingSystems.get(worldName);
  }

  public void registerTintingSystem(String worldName, GrassBlockTintingSystem system) {
    worldTintingSystems.put(worldName, system);
  }

  public ResourceType<EntityStore, SeasonResource> getSeasonResourceType() {
    return seasonResourceType;
  }

  public ComponentType<EntityStore, PlayerSeasonProgress> getPlayerSeasonProgressComponentType() {
    return playerSeasonProgressComponentType;
  }
}
