package com.hypixel.seasons.commands;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.seasons.Season;
import com.hypixel.seasons.systems.SeasonSystem;
import com.hypixel.seasons.systems.SeasonWeatherManager;

public class SeasonGetCommand extends AbstractWorldCommand {
    public SeasonGetCommand() {
        super("get", "Get the current season");
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        SeasonSystem seasonSystem = new SeasonSystem(world, store, new SeasonWeatherManager());
        Season currentSeason = seasonSystem.getCurrentSeason();
        String seasonName = currentSeason.getDisplayName();
        int dayOfSeason = seasonSystem.getSeasonResource().getDayOfSeason();
        int daysInSeason = currentSeason.getDaysInSeason();

        String message = String.format(
                "Current Season: %s (Day %d/%d)",
                seasonName,
                dayOfSeason + 1,
                daysInSeason
        );

        System.out.println("[ARCANE SEASONS] " + message);
    }
}
