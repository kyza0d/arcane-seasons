package com.hypixel.seasons.commands;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.seasons.Season;
import com.hypixel.seasons.SeasonsModule;
import com.hypixel.seasons.resources.SeasonResource;
import com.hypixel.seasons.systems.GrassBlockTintingSystem;

public class SeasonSetCommand extends AbstractWorldCommand {

    private final RequiredArg<String> seasonArg = withRequiredArg("season", "The season to set (spring, summer, fall, winter)", ArgTypes.STRING);

    public SeasonSetCommand() {
        super("set", "Set the current season");
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        String seasonName = seasonArg.get(context);

        Season season = Season.getSeasonByName(seasonName);
        if (season == null) {
            System.out.println("[ARCANE SEASONS] Unknown season: " + seasonName);
            System.out.println("[ARCANE SEASONS] Valid seasons: spring, summer, fall, winter");
            return;
        }

        try {
            SeasonResource seasonResource = store.getResource(SeasonResource.getResourceType());
            if (seasonResource == null) {
                seasonResource = new SeasonResource();
            }

            Season oldSeason = seasonResource.getCurrentSeason();
            seasonResource.setCurrentSeason(season);
            seasonResource.setDayOfSeason(0);
            seasonResource.setTotalDaysPassed(season.getStartDay());

            store.replaceResource(SeasonResource.getResourceType(), seasonResource);

            System.out.println("[ARCANE SEASONS] ========================================");
            System.out.println("[ARCANE SEASONS] SEASON SET VIA COMMAND");
            System.out.println("[ARCANE SEASONS] " + oldSeason.getDisplayName() + " -> " + season.getDisplayName());
            System.out.println("[ARCANE SEASONS] ========================================");

            GrassBlockTintingSystem tintingSystem = SeasonsModule.getTintingSystemForWorld(world.getName());
            if (tintingSystem != null) {
                int tintColor = season.getGrassTintColor();
                System.out.println("[ARCANE SEASONS] Applying tint color: 0x" + Integer.toHexString(tintColor));
                world.execute(() -> {
                    try {
                        tintingSystem.onSeasonChange(tintColor);
                        System.out.println("[ARCANE SEASONS] Tinting complete");
                    } catch (Exception e) {
                        System.err.println("[ARCANE SEASONS] Error during tinting: " + e.getMessage());
                    }
                });
            } else {
                System.out.println("[ARCANE SEASONS] WARNING: No tinting system found for world: " + world.getName());
            }

        } catch (Exception e) {
            System.err.println("[ARCANE SEASONS] Error setting season: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
