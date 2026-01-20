package com.elertan.event;

import com.elertan.models.ISOOffsetDateTime;
import lombok.Getter;

public class CollectionLogUnlockAchievementBUEvent extends BUEvent {

    @Getter
    private final String itemName;

    public CollectionLogUnlockAchievementBUEvent(
        long dispatchedFromAccountHash, ISOOffsetDateTime timestamp, String itemName) {
        super(dispatchedFromAccountHash, timestamp);
        this.itemName = itemName;
    }

    @Override
    public BUEventType getType() {
        return BUEventType.CollectionLogUnlockAchievement;
    }
}
