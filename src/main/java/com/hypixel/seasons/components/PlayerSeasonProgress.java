package com.hypixel.seasons.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.seasons.SeasonsModule;

public class PlayerSeasonProgress implements Component<EntityStore> {

    public static final float PROGRESS_PER_SLEEP = 20.0f;
    public static final float MAX_PROGRESS = 100.0f;

    public static final BuilderCodec<PlayerSeasonProgress> CODEC = BuilderCodec
        .builder(PlayerSeasonProgress.class, PlayerSeasonProgress::new)
        .append(new KeyedCodec<>("ProgressPercentage", Codec.FLOAT),
            (component, value) -> component.progressPercentage = value != null ? value : 0.0f,
            component -> component.progressPercentage)
        .add()
        .append(new KeyedCodec<>("LastSleepTimestamp", Codec.LONG),
            (component, value) -> component.lastSleepTimestamp = value != null ? value : 0L,
            component -> component.lastSleepTimestamp)
        .add()
        .build();

    private float progressPercentage;
    private long lastSleepTimestamp;

    public PlayerSeasonProgress() {
        this.progressPercentage = 0.0f;
        this.lastSleepTimestamp = 0L;
    }

    public PlayerSeasonProgress(float progressPercentage, long lastSleepTimestamp) {
        this.progressPercentage = progressPercentage;
        this.lastSleepTimestamp = lastSleepTimestamp;
    }

    public static ComponentType<EntityStore, PlayerSeasonProgress> getComponentType() {
        return SeasonsModule.getInstance().getPlayerSeasonProgressComponentType();
    }

    public float getProgressPercentage() {
        return progressPercentage;
    }

    public void setProgressPercentage(float progressPercentage) {
        this.progressPercentage = Math.max(0.0f, Math.min(MAX_PROGRESS, progressPercentage));
    }

    public void addProgress(float amount) {
        this.progressPercentage = Math.min(MAX_PROGRESS, this.progressPercentage + amount);
    }

    public boolean isReadyForSeasonChange() {
        return progressPercentage >= MAX_PROGRESS;
    }

    public void resetProgress() {
        this.progressPercentage = 0.0f;
    }

    public long getLastSleepTimestamp() {
        return lastSleepTimestamp;
    }

    public void setLastSleepTimestamp(long lastSleepTimestamp) {
        this.lastSleepTimestamp = lastSleepTimestamp;
    }

    public int getSleepCount() {
        return (int) (progressPercentage / PROGRESS_PER_SLEEP);
    }

    public int getSleepsRemaining() {
        return (int) Math.ceil((MAX_PROGRESS - progressPercentage) / PROGRESS_PER_SLEEP);
    }

    @Override
    public Component<EntityStore> clone() {
        return new PlayerSeasonProgress(this.progressPercentage, this.lastSleepTimestamp);
    }
}
