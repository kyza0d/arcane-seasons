package com.hypixel.seasons.events;

import com.hypixel.seasons.Season;

public class SeasonChangeEvent {
    private final Season oldSeason;
    private final Season newSeason;

    public SeasonChangeEvent(Season oldSeason, Season newSeason) {
        this.oldSeason = oldSeason;
        this.newSeason = newSeason;
    }

    public Season getOldSeason() {
        return oldSeason;
    }

    public Season getNewSeason() {
        return newSeason;
    }
}
