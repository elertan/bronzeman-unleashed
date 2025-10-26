package com.elertan.event;

import com.elertan.models.ISOOffsetDateTime;
import lombok.Getter;

public class QuestCompletionAchievementBUEvent extends BUEvent {
    @Override
    public BUEventType getType() {
        return BUEventType.QuestCompletionAchievement;
    }

    @Getter
    private final String name;

    public QuestCompletionAchievementBUEvent(long dispatchedFromAccountHash, ISOOffsetDateTime timestamp, String name) {
        super(dispatchedFromAccountHash, timestamp);
        this.name = name;
    }
}
