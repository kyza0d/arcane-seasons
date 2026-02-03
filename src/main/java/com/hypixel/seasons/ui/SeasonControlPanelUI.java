package com.hypixel.seasons.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.seasons.Season;
import com.hypixel.seasons.resources.SeasonResource;
import com.hypixel.seasons.systems.SeasonSystem;
import com.hypixel.seasons.systems.SeasonWeatherManager;

public class SeasonControlPanelUI extends InteractiveCustomUIPage<SeasonControlPanelUI.ControlPanelData> {

    private final World world;

    public SeasonControlPanelUI(PlayerRef playerRef, CustomPageLifetime lifetime, World world) {
        super(playerRef, lifetime, ControlPanelData.CODEC);
        this.world = world;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder uiCommandBuilder,
                      UIEventBuilder uiEventBuilder, Store<EntityStore> store) {
        SeasonSystem seasonSystem = new SeasonSystem(world, store, new SeasonWeatherManager());

        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        boolean isOperator = playerComponent != null && playerComponent.hasPermission("hytale.op");

        if (isOperator) {
            uiCommandBuilder.append("Pages/SeasonControlPanel.ui");
        } else {
            uiCommandBuilder.append("Pages/SeasonControlPanelNonOp.ui");
        }

        SeasonResource resource = seasonSystem.getSeasonResource();
        Season currentSeason = resource.getCurrentSeason();
        int dayOfSeason = resource.getDayOfSeason();
        int daysInSeason = currentSeason.getDaysInSeason();

        uiCommandBuilder.set("#CurrentSeasonLabel.Text", currentSeason.getDisplayName());
        uiCommandBuilder.set("#DayProgressLabel.Text", String.format("Day %d / %d", dayOfSeason, daysInSeason));

        int progressWidth = (int) ((dayOfSeason / (double) daysInSeason) * 550);
        Anchor progressAnchor = new Anchor();
        progressAnchor.setWidth(Value.of(progressWidth));
        progressAnchor.setHeight(Value.of(8));
        uiCommandBuilder.setObject("#ProgressBarFill.Anchor", progressAnchor);

        if (isOperator) {
            uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#OpenDebugButton",
                new EventData().append("Action", "OpenDebug"));
        }

        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#OpenWeatherButton",
            new EventData().append("Action", "OpenWeather"));
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store,
                                ControlPanelData data) {
        super.handleDataEvent(ref, store, data);

        if ("OpenDebug".equals(data.action)) {
            openDebugUI(ref, store);
            return;
        } else if ("OpenWeather".equals(data.action)) {
            openWeatherUI(ref, store);
            return;
        }

        this.sendUpdate();
    }

    private void openDebugUI(Ref<EntityStore> ref, Store<EntityStore> store) {
        try {
            Player playerComponent = store.getComponent(ref, Player.getComponentType());
            if (playerComponent == null || !playerComponent.hasPermission("hytale.op")) {
                return;
            }
            SeasonDebugUI debugUI = new SeasonDebugUI(playerRef, CustomPageLifetime.CanDismiss, world);
            playerComponent.getPageManager().openCustomPage(ref, store, debugUI);
        } catch (Exception e) {
            System.err.println("[SeasonControlPanel] Error opening debug UI: " + e.getMessage());
        }
    }

    private void openWeatherUI(Ref<EntityStore> ref, Store<EntityStore> store) {
        try {
            Player playerComponent = store.getComponent(ref, Player.getComponentType());
            if (playerComponent == null) {
                return;
            }
            SeasonWeatherUI weatherUI = new SeasonWeatherUI(playerRef, CustomPageLifetime.CanDismiss, world);
            playerComponent.getPageManager().openCustomPage(ref, store, weatherUI);
        } catch (Exception e) {
            System.err.println("[SeasonControlPanel] Error opening weather UI: " + e.getMessage());
        }
    }

    public static class ControlPanelData {
        static final String KEY_ACTION = "Action";

        public static final BuilderCodec<ControlPanelData> CODEC =
            BuilderCodec.<ControlPanelData>builder(ControlPanelData.class, ControlPanelData::new)
                .addField(new KeyedCodec<>(KEY_ACTION, Codec.STRING),
                    (data, s) -> data.action = s, data -> data.action)
                .build();

        public String action;
    }
}
