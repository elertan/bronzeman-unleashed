package com.elertan.event;

import com.elertan.models.ISOOffsetDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class CombatLevelUpAchievementBUEvent extends BUEvent {

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
