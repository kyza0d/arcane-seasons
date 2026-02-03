package com.hypixel.seasons.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.seasons.ui.SeasonControlPanelUI;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;

public class SeasonCommand extends AbstractCommandCollection {
    public SeasonCommand() {
        super("season", "Manage and monitor seasons");
        addSubCommand(new SeasonPanelCommand());
        addSubCommand(new SeasonGetCommand());
        addSubCommand(new SeasonSetCommand());
    }

    private static class SeasonPanelCommand extends AbstractWorldCommand {
        public SeasonPanelCommand() {
            super("panel", "Open Season Control Panel");
        }

        @Override
        protected void execute(CommandContext context, World world, Store<EntityStore> store) {
            try {
                Ref<EntityStore> playerRef = context.senderAsPlayerRef();
                if (playerRef != null) {
                    PlayerRef playerRefComponent = store.getComponent(playerRef, PlayerRef.getComponentType());
                    Player playerComponent = store.getComponent(playerRef, Player.getComponentType());

                    SeasonControlPanelUI ui = new SeasonControlPanelUI(playerRefComponent, CustomPageLifetime.CanDismiss, world);
                    playerComponent.getPageManager().openCustomPage(playerRef, store, ui);
                } else {
                    System.out.println("[ARCANE SEASONS] ERROR: Only players can open the Season Control Panel");
                }
            } catch (Exception e) {
                System.out.println("[ARCANE SEASONS] ERROR opening Season Control Panel: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
