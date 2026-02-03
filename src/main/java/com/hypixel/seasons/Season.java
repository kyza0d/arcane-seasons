package com.hypixel.seasons;
public enum Season {
  SPRING(0, 89, "Spring", 0xFF2ECC71),
  SUMMER(90, 179, "Summer", 0xFF27AE60),
  FALL(180, 269, "Fall", 0xFFE67E22),
  WINTER(270, 359, "Winter", 0xFFECF0F1);

  private final int startDay;
  private final int endDay;
  private final String displayName;
  private final int grassTintColor;

  Season(int startDay, int endDay, String displayName, int grassTintColor) {
    this.startDay = startDay;
    this.endDay = endDay;
    this.displayName = displayName;
    this.grassTintColor = grassTintColor;
  }

  public int getStartDay() {
    return startDay;
  }

  public int getEndDay() {
    return endDay;
  }

  public String getDisplayName() {
    return displayName;
  }

  public int getDaysInSeason() {
    // calculate days in season
    return endDay - startDay + 1;
  }

  // get tint color ARGB format
  // 0xAARRGGBB where AA=FF fully opaque
  public int getGrassTintColor() {
    return grassTintColor;
  }

  public static Season getSeasonByDay(int dayOfYear) {
    // handle wrap-around for day of year
    if (dayOfYear < 0 || dayOfYear >= 360) {
      dayOfYear = dayOfYear % 360;
    }

    // loop through all seasons and find match
    for (Season season : values()) {
      int startD = season.startDay;
      int endD = season.endDay;
      if (dayOfYear >= startD && dayOfYear <= endD) {
        return season;
      }
    }

    // Fallback - shouldn't get here but just in case
    return SPRING;
  }

  public static Season getSeasonByName(String name) {
    // lookup season by name string
    for (Season season : values()) {
      if (season.name().equalsIgnoreCase(name)) {
        return season;
      }
    }
    // not found
    return null;
  }
}

