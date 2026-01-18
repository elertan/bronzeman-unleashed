package com.elertan.event;

import com.elertan.models.AchievementDiaryArea;
import com.elertan.models.AchievementDiaryTier;
import com.elertan.models.ISOOffsetDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class DiaryCompletionAchievementBUEvent extends BUEvent {

    private final AchievementDiaryTier tier;
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
