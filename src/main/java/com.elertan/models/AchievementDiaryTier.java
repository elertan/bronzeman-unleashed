package com.elertan.models;

import lombok.Getter;

@Getter
public enum AchievementDiaryTier {
    Easy("Easy"), Medium("Medium"), Hard("Hard"), Elite("Elite");

    private final String displayName;
    AchievementDiaryTier(String displayName) { this.displayName = displayName; }
}
