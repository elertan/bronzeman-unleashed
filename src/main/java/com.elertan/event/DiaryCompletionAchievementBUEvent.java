package com.elertan.event;

import com.elertan.models.ISOOffsetDateTime;
import lombok.Getter;

public class DiaryCompletionAchievementBUEvent extends BUEvent {
    @Override
    public BUEventType getType() {
        return BUEventType.DiaryCompletionAchievement;
    }

    @Getter
    private final String tier;

    @Getter
    private final String area;

    public DiaryCompletionAchievementBUEvent(long dispatchedFromAccountHash, ISOOffsetDateTime timestamp, String tier, String area) {
        super(dispatchedFromAccountHash, timestamp);
        this.tier = tier;
        this.area = area;
    }
}
