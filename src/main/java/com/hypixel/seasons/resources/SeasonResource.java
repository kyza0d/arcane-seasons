package com.hypixel.seasons.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.seasons.Season;
import com.hypixel.seasons.SeasonsModule;

public class SeasonResource implements Resource<EntityStore> {
    public static ResourceType<EntityStore, SeasonResource> getResourceType() {
        return SeasonsModule.getInstance().getSeasonResourceType();
    }

    private Season currentSeason;
    private int dayOfSeason;
    private long totalDaysPassed;

    public SeasonResource() {
        this(Season.SPRING, 0, 0);
    }

    public SeasonResource(Season currentSeason, int dayOfSeason, long totalDaysPassed) {
        this.currentSeason = currentSeason;
        this.dayOfSeason = dayOfSeason;
        this.totalDaysPassed = totalDaysPassed;
    }

    public Season getCurrentSeason() {
        return currentSeason;
    }

    public void setCurrentSeason(Season currentSeason) {
        this.currentSeason = currentSeason;
    }

    public int getDayOfSeason() {
        return dayOfSeason;
    }

    public void setDayOfSeason(int dayOfSeason) {
        this.dayOfSeason = dayOfSeason;
    }

    public long getTotalDaysPassed() {
        return totalDaysPassed;
    }

    public void setTotalDaysPassed(long totalDaysPassed) {
        this.totalDaysPassed = totalDaysPassed;
    }

    public int getDayOfYear() {
        return (int) (totalDaysPassed % 360);
    }

    @Override
    public Resource<EntityStore> clone() {
        return new SeasonResource(currentSeason, dayOfSeason, totalDaysPassed);
    }
}
