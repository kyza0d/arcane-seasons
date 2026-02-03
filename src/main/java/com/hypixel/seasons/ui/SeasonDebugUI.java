package com.hypixel.seasons.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.seasons.Season;
import com.hypixel.seasons.SeasonsModule;
import com.hypixel.seasons.components.PlayerSeasonProgress;
import com.hypixel.seasons.echo.Echo;
import com.hypixel.seasons.echo.EchoManager;
import com.hypixel.seasons.resources.SeasonResource;
import com.hypixel.seasons.systems.GrassBlockTintingSystem;

public class SeasonDebugUI extends InteractiveCustomUIPage<SeasonDebugUI.DebugData> {

    private final World world;

    public SeasonDebugUI(PlayerRef playerRef, CustomPageLifetime lifetime, World world) {
        super(playerRef, lifetime, DebugData.CODEC);
        this.world = world;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder uiCommandBuilder,
                      UIEventBuilder uiEventBuilder, Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/SeasonDebug.ui");

        registerEventBindings(uiEventBuilder);
    }

    private void registerEventBindings(UIEventBuilder uiEventBuilder) {
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SpringButton",
            new EventData().append("SeasonName", "spring"));

        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SummerButton",
            new EventData().append("SeasonName", "summer"));

        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#FallButton",
            new EventData().append("SeasonName", "fall"));

        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#WinterButton",
            new EventData().append("SeasonName", "winter"));

        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AdvanceButton",
            new EventData().append("DaysToAdvance", "1"));

        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#Advance1DayButton",
            new EventData().append("QuickDays", "1"));

        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#Advance7DaysButton",
            new EventData().append("QuickDays", "7"));

        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#Advance30DaysButton",
            new EventData().append("QuickDays", "30"));

        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#NextSeasonButton",
            new EventData().append("Action", "NextSeason"));

        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ResetButton",
            new EventData().append("Action", "Reset"));

        for (Echo echo : Echo.values()) {
            uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#Echo" + echo.name() + "Button",
                new EventData().append("EchoId", String.valueOf(echo.getId())));
        }

        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#EchoReturnButton",
            new EventData().append("Action", "EchoReturn"));

        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
            new EventData().append("Action", "Back"));

        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AddProgressButton",
            new EventData().append("Action", "AddProgress"));

        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ResetProgressButton",
            new EventData().append("Action", "ResetProgress"));

        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SimulateSleepButton",
            new EventData().append("Action", "SimulateSleep"));
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store,
                                DebugData data) {
        super.handleDataEvent(ref, store, data);

        if (data.seasonName != null) {
            changeSeason(store, data.seasonName);
        } else if (data.daysToAdvance > 0) {
            advanceDays(store, data.daysToAdvance);
        } else if (data.quickDays > 0) {
            advanceDays(store, data.quickDays);
        } else if ("NextSeason".equals(data.action)) {
            advanceToNextSeason(store);
        } else if ("Reset".equals(data.action)) {
            resetSeason(store);
        } else if (data.echoId != null && !data.echoId.isEmpty()) {
            teleportToEcho(ref, store, data.echoId);
            return;
        } else if ("EchoReturn".equals(data.action)) {
            returnFromEcho(ref, store);
            return;
        } else if ("Back".equals(data.action)) {
            this.close();
            return;
        } else if ("AddProgress".equals(data.action)) {
            addPlayerProgress(ref, store, PlayerSeasonProgress.PROGRESS_PER_SLEEP);
        } else if ("ResetProgress".equals(data.action)) {
            resetPlayerProgress(ref, store);
        } else if ("SimulateSleep".equals(data.action)) {
            simulateSleep(ref, store);
        }

        logCurrentProgress(ref, store);
        this.sendUpdate();
    }

    private Season getCurrentSeason(Store<EntityStore> store) {
        try {
            SeasonResource resource = store.getResource(SeasonResource.getResourceType());
            if (resource != null && resource.getCurrentSeason() != null) {
                return resource.getCurrentSeason();
            }
        } catch (Exception e) {
        }
        return Season.SPRING;
    }

    private void setSeasonAndTint(Store<EntityStore> store, Season oldSeason, Season newSeason) {
        try {
            SeasonResource resource = store.getResource(SeasonResource.getResourceType());
            if (resource == null) {
                resource = new SeasonResource();
            }

            resource.setCurrentSeason(newSeason);
            resource.setDayOfSeason(0);
            resource.setTotalDaysPassed(newSeason.getStartDay());

            store.replaceResource(SeasonResource.getResourceType(), resource);

            System.out.println("[ARCANE SEASONS] ========================================");
            System.out.println("[ARCANE SEASONS] SEASON CHANGED (Debug Panel)");
            System.out.println("[ARCANE SEASONS] " + oldSeason.getDisplayName() + " -> " + newSeason.getDisplayName());
            System.out.println("[ARCANE SEASONS] ========================================");

            GrassBlockTintingSystem tintingSystem = SeasonsModule.getTintingSystemForWorld(world.getName());
            if (tintingSystem != null) {
                int tintColor = newSeason.getGrassTintColor();
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

    private void changeSeason(Store<EntityStore> store, String seasonName) {
        Season season = Season.getSeasonByName(seasonName);
        if (season != null) {
            Season oldSeason = getCurrentSeason(store);
            setSeasonAndTint(store, oldSeason, season);
        }
    }

    private void advanceDays(Store<EntityStore> store, int days) {
        if (days <= 0) {
            return;
        }

        try {
            SeasonResource resource = store.getResource(SeasonResource.getResourceType());
            if (resource == null) {
                resource = new SeasonResource();
            }

            Season oldSeason = resource.getCurrentSeason();

            long newTotalDays = resource.getTotalDaysPassed() + days;
            resource.setTotalDaysPassed(newTotalDays);
            int newDayOfYear = (int) (newTotalDays % 360);
            Season newSeason = Season.getSeasonByDay(newDayOfYear);
            int dayInSeason = newDayOfYear - newSeason.getStartDay();
            resource.setCurrentSeason(newSeason);
            resource.setDayOfSeason(dayInSeason);

            store.replaceResource(SeasonResource.getResourceType(), resource);

            if (!oldSeason.equals(newSeason)) {
                System.out.println("[ARCANE SEASONS] ========================================");
                System.out.println("[ARCANE SEASONS] SEASON CHANGED (Day Advance)");
                System.out.println("[ARCANE SEASONS] " + oldSeason.getDisplayName() + " -> " + newSeason.getDisplayName());
                System.out.println("[ARCANE SEASONS] ========================================");

                GrassBlockTintingSystem tintingSystem = SeasonsModule.getTintingSystemForWorld(world.getName());
                if (tintingSystem != null) {
                    int tintColor = newSeason.getGrassTintColor();
                    world.execute(() -> {
                        try {
                            tintingSystem.onSeasonChange(tintColor);
                        } catch (Exception e) {
                            System.err.println("[ARCANE SEASONS] Error during tinting: " + e.getMessage());
                        }
                    });
                }
            }
        } catch (Exception e) {
            System.err.println("[SeasonDebug] Error advancing days: " + e.getMessage());
        }
    }

    private void advanceToNextSeason(Store<EntityStore> store) {
        Season currentSeason = getCurrentSeason(store);
        Season[] seasons = Season.values();
        int currentIndex = -1;
        for (int i = 0; i < seasons.length; i++) {
            if (seasons[i] == currentSeason) {
                currentIndex = i;
                break;
            }
        }
        if (currentIndex >= 0) {
            Season nextSeason = seasons[(currentIndex + 1) % seasons.length];
            setSeasonAndTint(store, currentSeason, nextSeason);
        }
    }

    private void resetSeason(Store<EntityStore> store) {
        Season currentSeason = getCurrentSeason(store);
        setSeasonAndTint(store, currentSeason, Season.SPRING);
    }

    private void teleportToEcho(Ref<EntityStore> ref, Store<EntityStore> store, String echoId) {
        Echo echo = Echo.getById(echoId);
        if (echo == null) {
            return;
        }

        try {
            EchoManager.get().teleportToEcho(echo, ref, store, world);
        } catch (Exception e) {
            System.err.println("[SeasonDebug] Error teleporting to echo: " + e.getMessage());
        }
    }

    private void returnFromEcho(Ref<EntityStore> ref, Store<EntityStore> store) {
        try {
            EchoManager.get().returnFromEcho(ref, store, world);
        } catch (Exception e) {
            System.err.println("[SeasonDebug] Error returning from echo: " + e.getMessage());
        }
    }

    private PlayerSeasonProgress getPlayerProgress(Ref<EntityStore> ref, Store<EntityStore> store) {
        try {
            ComponentType<EntityStore, PlayerSeasonProgress> progressType =
                SeasonsModule.getInstance().getPlayerSeasonProgressComponentType();
            if (progressType == null) {
                return null;
            }
            return store.getComponent(ref, progressType);
        } catch (Exception e) {
            return null;
        }
    }

    private void addPlayerProgress(Ref<EntityStore> ref, Store<EntityStore> store, float amount) {
        try {
            ComponentType<EntityStore, PlayerSeasonProgress> progressType =
                SeasonsModule.getInstance().getPlayerSeasonProgressComponentType();
            if (progressType == null) {
                System.err.println("[SeasonDebug] PlayerSeasonProgress component type not available");
                return;
            }

            PlayerSeasonProgress progress = store.getComponent(ref, progressType);
            if (progress == null) {
                progress = new PlayerSeasonProgress();
            } else {
                progress = (PlayerSeasonProgress) progress.clone();
            }

            float oldProgress = progress.getProgressPercentage();
            progress.addProgress(amount);

            store.putComponent(ref, progressType, progress);

            System.out.println("[ARCANE SEASONS] [DEBUG] Progress added: " +
                (int) oldProgress + "% -> " + (int) progress.getProgressPercentage() + "%");
        } catch (Exception e) {
            System.err.println("[SeasonDebug] Error adding progress: " + e.getMessage());
        }
    }

    private void resetPlayerProgress(Ref<EntityStore> ref, Store<EntityStore> store) {
        try {
            ComponentType<EntityStore, PlayerSeasonProgress> progressType =
                SeasonsModule.getInstance().getPlayerSeasonProgressComponentType();
            if (progressType == null) {
                return;
            }

            PlayerSeasonProgress progress = store.getComponent(ref, progressType);
            if (progress == null) {
                progress = new PlayerSeasonProgress();
            } else {
                progress = (PlayerSeasonProgress) progress.clone();
            }

            progress.resetProgress();
            store.putComponent(ref, progressType, progress);

            System.out.println("[ARCANE SEASONS] [DEBUG] Progress reset to 0%");
        } catch (Exception e) {
            System.err.println("[SeasonDebug] Error resetting progress: " + e.getMessage());
        }
    }

    private void simulateSleep(Ref<EntityStore> ref, Store<EntityStore> store) {
        try {
            ComponentType<EntityStore, PlayerSeasonProgress> progressType =
                SeasonsModule.getInstance().getPlayerSeasonProgressComponentType();
            if (progressType == null) {
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

            System.out.println("[ARCANE SEASONS] [DEBUG] Simulated sleep - Progress: " +
                (int) oldProgress + "% -> " + (int) progress.getProgressPercentage() + "%");

            if (progress.isReadyForSeasonChange()) {
                System.out.println("[ARCANE SEASONS] [DEBUG] Progress reached 100% - Triggering season change!");
                advanceToNextSeason(store);
                progress.resetProgress();
                System.out.println("[ARCANE SEASONS] [DEBUG] Progress reset to 0%");
            }

            store.putComponent(ref, progressType, progress);
        } catch (Exception e) {
            System.err.println("[SeasonDebug] Error simulating sleep: " + e.getMessage());
        }
    }

    private void logCurrentProgress(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerSeasonProgress progress = getPlayerProgress(ref, store);
        if (progress != null) {
            System.out.println("[ARCANE SEASONS] [DEBUG] Current progress: " +
                (int) progress.getProgressPercentage() + "% (" +
                progress.getSleepCount() + "/5 sleeps, " +
                progress.getSleepsRemaining() + " remaining)");
        }
    }

    public static class DebugData {
        static final String KEY_SEASON_NAME = "SeasonName";
        static final String KEY_QUICK_DAYS = "QuickDays";
        static final String KEY_DAYS_TO_ADVANCE = "DaysToAdvance";
        static final String KEY_ACTION = "Action";
        static final String KEY_ECHO_ID = "EchoId";

        public static final BuilderCodec<DebugData> CODEC =
            BuilderCodec.<DebugData>builder(DebugData.class, DebugData::new)
                .addField(new KeyedCodec<>(KEY_SEASON_NAME, Codec.STRING),
                    (data, s) -> data.seasonName = s, data -> data.seasonName)
                .addField(new KeyedCodec<>(KEY_QUICK_DAYS, Codec.STRING),
                    (data, s) -> data.quickDays = Integer.parseInt(s != null ? s : "0"),
                    data -> String.valueOf(data.quickDays))
                .addField(new KeyedCodec<>(KEY_DAYS_TO_ADVANCE, Codec.STRING),
                    (data, s) -> data.daysToAdvance = Integer.parseInt(s != null ? s : "0"),
                    data -> String.valueOf(data.daysToAdvance))
                .addField(new KeyedCodec<>(KEY_ACTION, Codec.STRING),
                    (data, s) -> data.action = s, data -> data.action)
                .addField(new KeyedCodec<>(KEY_ECHO_ID, Codec.STRING),
                    (data, s) -> data.echoId = s, data -> data.echoId)
                .build();

        public String seasonName;
        public int quickDays;
        public int daysToAdvance;
        public String action;
        public String echoId;
    }
}
