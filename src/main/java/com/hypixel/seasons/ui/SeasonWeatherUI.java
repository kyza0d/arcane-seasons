package com.hypixel.seasons.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.seasons.Season;
import com.hypixel.seasons.resources.SeasonResource;
import com.hypixel.seasons.systems.SeasonSystem;
import com.hypixel.seasons.systems.SeasonWeatherManager;

public class SeasonWeatherUI extends InteractiveCustomUIPage<SeasonWeatherUI.WeatherData> {

    private final World world;
    private SeasonSystem seasonSystem;
    private SeasonWeatherManager weatherManager;

    public SeasonWeatherUI(PlayerRef playerRef, CustomPageLifetime lifetime, World world) {
        super(playerRef, lifetime, WeatherData.CODEC);
        this.world = world;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder uiCommandBuilder,
                      UIEventBuilder uiEventBuilder, Store<EntityStore> store) {
        this.seasonSystem = new SeasonSystem(world, store, new SeasonWeatherManager());
        this.weatherManager = new SeasonWeatherManager();

        uiCommandBuilder.append("Pages/SeasonWeather.ui");

        updateWeatherDisplay(uiCommandBuilder);

        registerEventBindings(uiEventBuilder);
    }

    private void updateWeatherDisplay(UICommandBuilder uiCommandBuilder) {
        SeasonResource resource = seasonSystem.getSeasonResource();
        Season currentSeason = resource.getCurrentSeason();

        float rainfall = weatherManager.getRainfallModifier(currentSeason);
        float snow = weatherManager.getSnowModifier(currentSeason);
        float temperature = weatherManager.getTemperatureMultiplier(currentSeason);

        int rainfallWidth = (int) (rainfall * 200);
        int snowWidth = (int) (snow * 200);
        int temperatureWidth = (int) (temperature * 200);

        Anchor rainfallAnchor = new Anchor();
        rainfallAnchor.setWidth(Value.of(rainfallWidth));
        rainfallAnchor.setHeight(Value.of(8));
        uiCommandBuilder.setObject("#RainfallBar.Anchor", rainfallAnchor);
        uiCommandBuilder.set("#RainfallValue.Text", String.format("%.1f", rainfall));

        Anchor snowAnchor = new Anchor();
        snowAnchor.setWidth(Value.of(snowWidth));
        snowAnchor.setHeight(Value.of(8));
        uiCommandBuilder.setObject("#SnowBar.Anchor", snowAnchor);
        uiCommandBuilder.set("#SnowValue.Text", String.format("%.1f", snow));

        Anchor temperatureAnchor = new Anchor();
        temperatureAnchor.setWidth(Value.of(temperatureWidth));
        temperatureAnchor.setHeight(Value.of(8));
        uiCommandBuilder.setObject("#TemperatureBar.Anchor", temperatureAnchor);
        uiCommandBuilder.set("#TemperatureValue.Text", String.format("%.1f", temperature));

        int daysUntilNextSeason = currentSeason.getDaysInSeason() - resource.getDayOfSeason();
        uiCommandBuilder.set("#NextSeasonInfo.Text",
            String.format("Next season in %d days", daysUntilNextSeason));
    }

    private void registerEventBindings(UIEventBuilder uiEventBuilder) {
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
            new EventData().append("Action", "Back"));
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store,
                                WeatherData data) {
        super.handleDataEvent(ref, store, data);

        if ("Back".equals(data.action)) {
            this.close();
            return;
        }

        this.sendUpdate();
    }

    public static class WeatherData {
        static final String KEY_ACTION = "Action";

        public static final BuilderCodec<WeatherData> CODEC =
            BuilderCodec.<WeatherData>builder(WeatherData.class, WeatherData::new)
                .addField(new KeyedCodec<>(KEY_ACTION, Codec.STRING),
                    (data, s) -> data.action = s, data -> data.action)
                .build();

        public String action;
    }
}
