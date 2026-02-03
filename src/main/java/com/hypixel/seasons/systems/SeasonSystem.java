package com.hypixel.seasons.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.seasons.Season;
import com.hypixel.seasons.events.SeasonChangeEvent;
import com.hypixel.seasons.resources.SeasonResource;

public class SeasonSystem {
    private static final long TICKS_PER_DAY = 24000;
    private static final int DAYS_PER_YEAR = 360;

    private final World world;
    private final Store<EntityStore> store;
    private final SeasonWeatherManager weatherManager;
    private final GrassBlockTintingSystem grassTintingSystem;
    private long lastTickTime = 0;

    public SeasonSystem(World world, Store<EntityStore> store, SeasonWeatherManager weatherManager) {
        this(world, store, weatherManager, null);
    }

    public SeasonSystem(World world, Store<EntityStore> store, SeasonWeatherManager weatherManager, GrassBlockTintingSystem grassTintingSystem) {
        this.world = world;
        this.store = store;
        this.weatherManager = weatherManager;
        this.grassTintingSystem = grassTintingSystem;
    }

    public void onTick(long currentTick) {
        SeasonResource seasonResource = store.getResource(SeasonResource.getResourceType());
        if (seasonResource == null) {
            seasonResource = new SeasonResource();
            store.replaceResource(SeasonResource.getResourceType(), seasonResource);
        }

        long ticksSinceLastUpdate = currentTick - lastTickTime;
        long daysElapsed = ticksSinceLastUpdate / TICKS_PER_DAY;

        if (daysElapsed > 0) {
            Season oldSeason = seasonResource.getCurrentSeason();

            long newTotalDays = seasonResource.getTotalDaysPassed() + daysElapsed;
            seasonResource.setTotalDaysPassed(newTotalDays);

            int newDayOfYear = (int) (newTotalDays % DAYS_PER_YEAR);
            Season newSeason = Season.getSeasonByDay(newDayOfYear);

            seasonResource.setCurrentSeason(newSeason);
            int dayInSeason = newDayOfYear - newSeason.getStartDay();
            seasonResource.setDayOfSeason(dayInSeason);

            if (!oldSeason.equals(newSeason)) {
                onSeasonChange(oldSeason, newSeason);
            }

            lastTickTime = currentTick;
            store.replaceResource(SeasonResource.getResourceType(), seasonResource);
        }
    }

    private void onSeasonChange(Season oldSeason, Season newSeason) {
        System.out.println("[ARCANE SEASONS] ========================================");
        System.out.println("[ARCANE SEASONS] SEASON CHANGED");
        System.out.println("[ARCANE SEASONS] " + oldSeason.getDisplayName() + " -> " + newSeason.getDisplayName());
        System.out.println("[ARCANE SEASONS] ========================================");

        SeasonChangeEvent event = new SeasonChangeEvent(oldSeason, newSeason);

        if (grassTintingSystem != null) {
            int tintColor = newSeason.getGrassTintColor();
            System.out.println("[ARCANE SEASONS] Applying tint color: 0x" + Integer.toHexString(tintColor));

            world.execute(() -> {
                try {
                    grassTintingSystem.onSeasonChange(tintColor);
                    System.out.println("[ARCANE SEASONS] Tinting complete");
                } catch (Exception e) {
                    System.err.println("[ARCANE SEASONS] Error during tinting: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }

        if (weatherManager != null) {
            weatherManager.onSeasonChange(newSeason);
        }

        System.out.println("[ARCANE SEASONS] ========================================");
    }

    public Season getCurrentSeason() {
        try {
            SeasonResource seasonResource = store.getResource(SeasonResource.getResourceType());
            if (seasonResource != null) {
                Season s = seasonResource.getCurrentSeason();
                return s != null ? s : Season.SPRING;
            }
        } catch (Exception e) {
            System.out.println("[ARCANE SEASONS] WARNING: Could not get current season: " + e.getMessage());
        }
        return Season.SPRING;
    }

    public void setCurrentSeason(Season season) {
        try {
            SeasonResource seasonResource = store.getResource(SeasonResource.getResourceType());
            if (seasonResource == null) {
                seasonResource = new SeasonResource();
            }

            Season oldSeason = seasonResource.getCurrentSeason();
            seasonResource.setCurrentSeason(season);
            seasonResource.setDayOfSeason(0);
            seasonResource.setTotalDaysPassed(season.getStartDay());

            try {
                store.replaceResource(SeasonResource.getResourceType(), seasonResource);
            } catch (Exception e) {
                System.out.println("[ARCANE SEASONS] WARNING: Could not store resource: " + e.getMessage());
            }

            if (!oldSeason.equals(season)) {
                onSeasonChange(oldSeason, season);
            }
        } catch (Exception e) {
            System.out.println("[ARCANE SEASONS] WARNING: Error in setCurrentSeason: " + e.getMessage());
            onSeasonChange(Season.SPRING, season);
        }
    }

    public SeasonResource getSeasonResource() {
        try {
            SeasonResource seasonResource = store.getResource(SeasonResource.getResourceType());
            if (seasonResource == null) {
                seasonResource = new SeasonResource();
                try {
                    store.replaceResource(SeasonResource.getResourceType(), seasonResource);
                } catch (Exception e) {
                    System.out.println("[ARCANE SEASONS] WARNING: Could not store resource: " + e.getMessage());
                }
            }
            return seasonResource;
        } catch (Exception e) {
            System.out.println("[ARCANE SEASONS] WARNING: Error getting SeasonResource: " + e.getMessage());
            return new SeasonResource();
        }
    }

    public GrassBlockTintingSystem getGrassTintingSystem() {
        return grassTintingSystem;
    }
}
