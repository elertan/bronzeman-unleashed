package com.elertan.event;

import com.elertan.models.ISOOffsetDateTime;
import lombok.Getter;

public class SkillLevelUpAchievementBUEvent extends BUEvent {

    @Getter
    private final String skill;
    @Getter
    private final int level;

    public SkillLevelUpAchievementBUEvent(
        long dispatchedFromAccountHash,
        ISOOffsetDateTime isoOffsetDateTime,
        String skill,
        int level
    ) {
        super(dispatchedFromAccountHash, isoOffsetDateTime);
        this.skill = skill;
        this.level = level;
    }

    @Override
    public BUEventType getType() {
        return BUEventType.SkillLevelUpAchievement;
    }
}
