package com.elertan.event;

import com.elertan.models.ISOOffsetDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class SkillLevelUpAchievementBUEvent extends BUEvent {

    private final String skill;
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
