package com.elertan.event;

import com.elertan.models.ISOOffsetDateTime;
import lombok.Getter;

public class CombatTaskAchievementBUEvent extends BUEvent {
    @Override
    public BUEventType getType() {
        return BUEventType.CombatTaskAchievement;
    }

    @Getter
    private final String tier;

    @Getter
    private final String name;

    public CombatTaskAchievementBUEvent(long dispatchedFromAccountHash, ISOOffsetDateTime timestamp, String tier, String name) {
        super(dispatchedFromAccountHash, timestamp);
        this.tier = tier;
        this.name = name;
    }
}
