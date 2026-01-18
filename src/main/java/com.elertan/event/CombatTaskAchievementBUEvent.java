package com.elertan.event;

import com.elertan.models.ISOOffsetDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class CombatTaskAchievementBUEvent extends BUEvent {

    private final String tier;
    private final String name;

    public CombatTaskAchievementBUEvent(
        long dispatchedFromAccountHash,
        ISOOffsetDateTime timestamp,
        String tier,
        String name
    ) {
        super(dispatchedFromAccountHash, timestamp);
        this.tier = tier;
        this.name = name;
    }

    @Override
    public BUEventType getType() {
        return BUEventType.CombatTaskAchievement;
    }
}
