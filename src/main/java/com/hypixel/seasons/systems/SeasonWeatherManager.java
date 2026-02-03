package com.hypixel.seasons.systems;

import com.hypixel.seasons.Season;

public class SeasonWeatherManager {
    /**
     * Called when the season changes. This method can be used to adjust
     * weather patterns based on the new season.
     *
     * For example:
     * - WINTER: Increase snow/rain frequency
     * - SUMMER: Increase clear weather
     * - SPRING: Moderate rain
     * - FALL: Moderate weather
     */
    public void onSeasonChange(Season newSeason) {
        switch (newSeason) {
            case WINTER:
                applyWinterWeather();
                break;
            case SPRING:
                applySpringWeather();
                break;
            case SUMMER:
                applySummerWeather();
                break;
            case FALL:
                applyFallWeather();
                break;
        }
    }

    private void applyWinterWeather() {
        // In winter: higher chance of snow, lower temperature
        System.out.println("[ARCANE SEASONS] Applying winter weather effects...");
    }

    private void applySpringWeather() {
        // In spring: moderate rain, moderate temperature
        System.out.println("[ARCANE SEASONS] Applying spring weather effects...");
    }

    private void applySummerWeather() {
        // In summer: mostly clear, higher temperature
        System.out.println("[ARCANE SEASONS] Applying summer weather effects...");
    }

    private void applyFallWeather() {
        // In fall: moderate conditions
        System.out.println("[ARCANE SEASONS] Applying fall weather effects...");
    }

    /**
     * Get weather characteristics for the given season.
     * Returns a value between 0 and 1 representing weather intensity.
     */
    public float getRainfallModifier(Season season) {
        return switch (season) {
            case SPRING -> 0.7f;      // High rainfall
            case SUMMER -> 0.3f;      // Low rainfall
            case FALL -> 0.6f;        // Moderate rainfall
            case WINTER -> 0.5f;      // Moderate rainfall (as snow)
        };
    }

    /**
     * Get snow frequency modifier for the given season.
     */
    public float getSnowModifier(Season season) {
        return switch (season) {
            case SPRING -> 0.1f;      // Little snow
            case SUMMER -> 0.0f;      // No snow
            case FALL -> 0.2f;        // Some snow
            case WINTER -> 0.8f;      // High snow
        };
    }

    /**
     * Get temperature multiplier for the given season.
     */
    public float getTemperatureMultiplier(Season season) {
        return switch (season) {
            case SPRING -> 0.6f;      // Cool
            case SUMMER -> 1.0f;      // Hot
            case FALL -> 0.7f;        // Mild
            case WINTER -> 0.3f;      // Cold
        };
    }
}
