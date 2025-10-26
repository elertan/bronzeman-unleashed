package com.elertan.event;

import com.elertan.models.ISOOffsetDateTime;
import lombok.Getter;

public class LevelUpAchievementBUEvent extends BUEvent {
    @Override
    public BUEventType getType() {
        return BUEventType.LevelUpAchievement;
    }

    @Getter
    private final String skill;

    @Getter
    private final int level;

    public LevelUpAchievementBUEvent(long dispatchedFromAccountHash, ISOOffsetDateTime isoOffsetDateTime, String skill, int level) {
        super(dispatchedFromAccountHash, isoOffsetDateTime);
        this.skill = skill;
        this.level = level;
    }
}
