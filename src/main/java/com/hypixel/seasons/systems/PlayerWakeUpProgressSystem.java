package com.hypixel.seasons.systems;

import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSleep;
import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSomnolence;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.seasons.Season;
import com.hypixel.seasons.SeasonsModule;
import com.hypixel.seasons.components.PlayerSeasonProgress;
import com.hypixel.seasons.resources.SeasonResource;

public class PlayerWakeUpProgressSystem extends RefChangeSystem<EntityStore, PlayerSomnolence> {

    @Override
    public ComponentType<EntityStore, PlayerSomnolence> componentType() {
        return PlayerSomnolence.getComponentType();
    }

    @Override
    public Query<EntityStore> getQuery() {
        return PlayerSomnolence.getComponentType();
    }

    @Override
    public void onComponentAdded(Ref<EntityStore> ref, PlayerSomnolence component,
                                  Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
    }

    @Override
    public void onComponentSet(Ref<EntityStore> ref, PlayerSomnolence oldComponent,
                                PlayerSomnolence newComponent, Store<EntityStore> store,
                                CommandBuffer<EntityStore> commandBuffer) {
        if (oldComponent == null || newComponent == null) {
            return;
        }

        PlayerSleep oldState = oldComponent.getSleepState();
        PlayerSleep newState = newComponent.getSleepState();

        if (oldState == null || newState == null) {
            return;
        }

        boolean wasAsleep = isAsleepState(oldState);
        boolean isNowAwake = newState instanceof PlayerSleep.FullyAwake;

        if (wasAsleep && isNowAwake) {
            handlePlayerWakeUp(ref, store, commandBuffer);
        }
    }

    @Override
    public void onComponentRemoved(Ref<EntityStore> ref, PlayerSomnolence component,
                                    Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
    }

    private boolean isAsleepState(PlayerSleep state) {
        return state instanceof PlayerSleep.NoddingOff
            || state instanceof PlayerSleep.Slumber
            || state instanceof PlayerSleep.MorningWakeUp;
    }

    private void handlePlayerWakeUp(Ref<EntityStore> ref, Store<EntityStore> store,
                                     CommandBuffer<EntityStore> commandBuffer) {
        try {
            ComponentType<EntityStore, PlayerSeasonProgress> progressType =
                SeasonsModule.getInstance().getPlayerSeasonProgressComponentType();

            if (progressType == null) {
                System.err.println("[ARCANE SEASONS] PlayerSeasonProgress component type not registered");
                return;
            }

            PlayerSeasonProgress progress = store.getComponent(ref, progressType);

            if (progress == null) {
                progress = new PlayerSeasonProgress();
            } else {
                progress = (PlayerSeasonProgress) progress.clone();
            }

            float oldProgress = progress.getProgressPercentage();
            progress.addProgress(PlayerSeasonProgress.PROGRESS_PER_SLEEP);
            progress.setLastSleepTimestamp(System.currentTimeMillis());

            System.out.println("[ARCANE SEASONS] Player woke up - Progress: " +
                (int) oldProgress + "% -> " + (int) progress.getProgressPercentage() + "%");

            if (progress.isReadyForSeasonChange()) {
                System.out.println("[ARCANE SEASONS] Progress reached 100% - Triggering season change!");
                triggerSeasonChange(store);
                progress.resetProgress();
                System.out.println("[ARCANE SEASONS] Progress reset to 0%");
            }

            commandBuffer.putComponent(ref, progressType, progress);

        } catch (Exception e) {
            System.err.println("[ARCANE SEASONS] Error handling player wake-up: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void triggerSeasonChange(Store<EntityStore> store) {
        try {
            SeasonsModule module = SeasonsModule.getInstance();
            if (module == null) {
                System.err.println("[ARCANE SEASONS] SeasonsModule instance not available");
                return;
            }

            SeasonResource seasonResource = store.getResource(SeasonResource.getResourceType());
            if (seasonResource == null) {
                seasonResource = new SeasonResource();
            }

            Season currentSeason = seasonResource.getCurrentSeason();
            if (currentSeason == null) {
                currentSeason = Season.SPRING;
            }

            Season[] seasons = Season.values();
            int currentIndex = -1;
            for (int i = 0; i < seasons.length; i++) {
                if (seasons[i] == currentSeason) {
                    currentIndex = i;
                    break;
                }
            }

            Season nextSeason = seasons[(currentIndex + 1) % seasons.length];

            System.out.println("[ARCANE SEASONS] ========================================");
            System.out.println("[ARCANE SEASONS] SEASON CHANGED (Sleep Progression)");
            System.out.println("[ARCANE SEASONS] " + currentSeason.getDisplayName() + " -> " + nextSeason.getDisplayName());
            System.out.println("[ARCANE SEASONS] ========================================");

            seasonResource.setCurrentSeason(nextSeason);
            seasonResource.setDayOfSeason(0);
            seasonResource.setTotalDaysPassed(nextSeason.getStartDay());

            store.replaceResource(SeasonResource.getResourceType(), seasonResource);

            World world = store.getExternalData().getWorld();
            if (world != null) {
                GrassBlockTintingSystem tintingSystem = SeasonsModule.getTintingSystemForWorld(world.getName());
                if (tintingSystem != null) {
                    int tintColor = nextSeason.getGrassTintColor();
                    System.out.println("[ARCANE SEASONS] Applying tint color: 0x" + Integer.toHexString(tintColor));
                    World tintingWorld = tintingSystem.getWorld();
                    tintingWorld.execute(() -> {
                        try {
                            tintingSystem.onSeasonChange(tintColor);
                            System.out.println("[ARCANE SEASONS] Tinting complete");
                        } catch (Exception e) {
                            System.err.println("[ARCANE SEASONS] Error during tinting: " + e.getMessage());
                        }
                    });
                }

                SeasonWeatherManager weatherManager = module.getWeatherManager();
                if (weatherManager != null) {
                    weatherManager.onSeasonChange(nextSeason);
                }
            }

        } catch (Exception e) {
            System.err.println("[ARCANE SEASONS] Error triggering season change: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
