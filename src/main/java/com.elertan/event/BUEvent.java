package com.elertan.event;

import com.elertan.gson.AccountHashJsonAdapter;
import com.elertan.models.AchievementDiaryArea;
import com.elertan.models.AchievementDiaryTier;
import com.elertan.models.ISOOffsetDateTime;
import com.google.gson.annotations.JsonAdapter;
import javax.annotation.Nullable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

public abstract class BUEvent {

    @JsonAdapter(AccountHashJsonAdapter.class)
    @Getter
    private final long dispatchedFromAccountHash;
    @Getter
    private final ISOOffsetDateTime timestamp;

    public BUEvent(long dispatchedFromAccountHash, ISOOffsetDateTime timestamp) {
        this.dispatchedFromAccountHash = dispatchedFromAccountHash;
        this.timestamp = timestamp;
    }

    public abstract BUEventType getType();

    @Getter
    @EqualsAndHashCode(callSuper = true)
    public static class SkillLevelUpAchievementBUEvent extends BUEvent {

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

    @Getter
    @EqualsAndHashCode(callSuper = true)
    public static class TotalLevelAchievementBUEvent extends BUEvent {

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

    @Getter
    @EqualsAndHashCode(callSuper = true)
    public static class CombatLevelUpAchievementBUEvent extends BUEvent {

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

    @Getter
    @EqualsAndHashCode(callSuper = true)
    public static class CombatTaskAchievementBUEvent extends BUEvent {

        private final String tier;
        private final String name;

        public CombatTaskAchievementBUEvent(
            long dispatchedFromAccountHash,
            ISOOffsetDateTime timestamp,
            String tier,
            String name
        ) {
            super(dispatchedFromAccountHash, timestamp);
            this.tier = tier;
            this.name = name;
        }

        @Override
        public BUEventType getType() {
            return BUEventType.CombatTaskAchievement;
        }
    }

    @Getter
    @EqualsAndHashCode(callSuper = true)
    public static class QuestCompletionAchievementBUEvent extends BUEvent {

        private final String name;

        public QuestCompletionAchievementBUEvent(long dispatchedFromAccountHash,
            ISOOffsetDateTime timestamp, String name) {
            super(dispatchedFromAccountHash, timestamp);
            this.name = name;
        }

        @Override
        public BUEventType getType() {
            return BUEventType.QuestCompletionAchievement;
        }
    }

    @Getter
    @EqualsAndHashCode(callSuper = true)
    public static class DiaryCompletionAchievementBUEvent extends BUEvent {

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

    @Getter
    public static class CollectionLogUnlockAchievementBUEvent extends BUEvent {

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

    @Getter
    @EqualsAndHashCode(callSuper = true)
    public static class ValuableLootBUEvent extends BUEvent {

        private final int itemId;
        private final int quantity;
        private final int pricePerItem;
        private final int npcId;

        public ValuableLootBUEvent(long dispatchedFromAccountHash, ISOOffsetDateTime isoOffsetDateTime,
            int itemId, int quantity, int pricePerItem, int npcId) {
            super(dispatchedFromAccountHash, isoOffsetDateTime);
            this.itemId = itemId;
            this.quantity = quantity;
            this.pricePerItem = pricePerItem;
            this.npcId = npcId;
        }

        @Override
        public BUEventType getType() {
            return BUEventType.ValuableLoot;
        }
    }

    @Getter
    @EqualsAndHashCode(callSuper = true)
    public static class PetDropBUEvent extends BUEvent {

        /**
         * The item ID of the pet that was received.
         * Can be null in the Probita edge case (follower + full inventory).
         */
        @Nullable
        private final Integer petItemId;

        /**
         * Whether this is a duplicate pet (player already has this pet).
         */
        private final boolean isDuplicate;

        public PetDropBUEvent(long dispatchedFromAccountHash, ISOOffsetDateTime isoOffsetDateTime,
            @Nullable Integer petItemId, boolean isDuplicate) {
            super(dispatchedFromAccountHash, isoOffsetDateTime);
            this.petItemId = petItemId;
            this.isDuplicate = isDuplicate;
        }

        @Override
        public BUEventType getType() {
            return BUEventType.PetDrop;
        }
    }
}
