package com.hypixel.seasons.echo;

import com.hypixel.seasons.Season;

public enum Echo {
    SPRING("spring_echo", "Spring Echo", "SpringEchoTemplate", "A verdant spring realm", "spring_echo"),
    SUMMER("summer_echo", "Summer Echo", "SummerEchoTemplate", "A warm summer realm", "summer_echo"),
    FALL("fall_echo", "Fall Echo", "FallEchoTemplate", "An autumn harvest realm", "fall_echo");

    private final String id;
    private final String displayName;
    private final String templateName;
    private final String description;
    private final String prefabPath;

    Echo(String id, String displayName, String templateName, String description, String prefabPath) {
        this.id = id;
        this.displayName = displayName;
        this.templateName = templateName;
        this.description = description;
        this.prefabPath = prefabPath;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTemplateName() {
        return templateName;
    }

    public String getDescription() {
        return description;
    }

    public String getPrefabPath() {
        return prefabPath;
    }

    public String getWorldName() {
        return "echo_" + id;
    }

    public static Echo getById(String id) {
        for (Echo echo : values()) {
            if (echo.id.equals(id)) {
                return echo;
            }
        }
        return null;
    }

    public static Echo getByWorldName(String worldName) {
        for (Echo echo : values()) {
            if (echo.getWorldName().equals(worldName)) {
                return echo;
            }
        }
        return null;
    }

    public Season getSeason() {
        return Season.getSeasonByName(this.name());
    }
}
