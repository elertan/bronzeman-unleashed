package com.elertan.event;

import com.elertan.models.ISOOffsetDateTime;
import lombok.Getter;

public class CombatLevelUpAchievementBUEvent extends BUEvent {

    @Getter
    private final int level;

    public CombatLevelUpAchievementBUEvent(
        long dispatchedFromAccountHash,
        ISOOffsetDateTime isoOffsetDateTime,
        int level
    ) {
        super(dispatchedFromAccountHash, isoOffsetDateTime);
        this.level = level;
    }

    @Override
    public BUEventType getType() {
        return BUEventType.CombatLevelUpAchievement;
    }
}
