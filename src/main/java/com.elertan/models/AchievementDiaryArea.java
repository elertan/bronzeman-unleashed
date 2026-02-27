package com.elertan.models;

import lombok.Getter;

@Getter
public enum AchievementDiaryArea {
    Ardougne("Ardougne"), Desert("Desert"), Falador("Falador"), Kandarin("Kandarin"),
    Karamja("Karamja"), Kourend("Kourend & Kebos"), Lumbridge("Lumbridge & Draynor"),
    Morytania("Morytania"), Varrock("Varrock"), Western("Western Provinces"),
    Wilderness("Wilderness"), Fremennik("Fremennik");

    private final String displayName;
    AchievementDiaryArea(String displayName) { this.displayName = displayName; }
}
