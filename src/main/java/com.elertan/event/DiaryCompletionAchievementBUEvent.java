package com.elertan.event;

import com.elertan.models.AchievementDiaryArea;
import com.elertan.models.AchievementDiaryTier;
import com.elertan.models.ISOOffsetDateTime;
import lombok.Getter;

public class DiaryCompletionAchievementBUEvent extends BUEvent {

    @Getter
    private final AchievementDiaryTier tier;
    @Getter
    private final AchievementDiaryArea area;

    public DiaryCompletionAchievementBUEvent(
        long dispatchedFromAccountHash,
        ISOOffsetDateTime timestamp,
        AchievementDiaryTier tier,
        AchievementDiaryArea area
    ) {
        super(dispatchedFromAccountHash, timestamp);
        this.tier = tier;
        this.area = area;
    }

    @Override
    public BUEventType getType() {
        return BUEventType.DiaryCompletionAchievement;
    }
}
