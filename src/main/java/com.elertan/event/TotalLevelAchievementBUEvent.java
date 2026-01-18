package com.elertan.event;

import com.elertan.models.ISOOffsetDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class TotalLevelAchievementBUEvent extends BUEvent {

    private final int totalLevel;

    public TotalLevelAchievementBUEvent(
        long dispatchedFromAccountHash,
        ISOOffsetDateTime isoOffsetDateTime,
        int totalLevel
    ) {
        super(dispatchedFromAccountHash, isoOffsetDateTime);
        this.totalLevel = totalLevel;
    }

    @Override
    public BUEventType getType() {
        return BUEventType.TotalLevelAchievement;
    }
}
