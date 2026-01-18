package com.elertan.event;

import com.elertan.chat.*;
import com.elertan.models.ISOOffsetDateTime;
import com.google.common.collect.ImmutableSet;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.Set;

public class GameMessageToEventTransformer {

    private static final EnumMap<ParsedGameMessageType, Transformer> TRANSFORMERS = new EnumMap<>(
        ParsedGameMessageType.class);

    private static final Set<Integer> SHARE_SKILL_LEVEL_UP_OF_SET = ImmutableSet.of(
        10,
        20,
        30,
        40,
        50,
        60,
        65,
        70,
        75,
        80,
        81,
        82,
        83,
        84,
        85,
        86,
        87,
        88,
        89,
        90,
        91,
        92,
        93,
        94,
        95,
        96,
        97,
        98,
        99
    );

    private static final Set<Integer> SHARE_COMBAT_LEVEL_UP_OF_SET = ImmutableSet.of(
        10,
        20,
        30,
        40,
        50,
        60,
        70,
        80,
        90,
        100,
        105,
        110,
        115,
        120,
        121,
        122,
        123,
        124,
        125,
        126
    );

    static {
        TRANSFORMERS.put(
            ParsedGameMessageType.SkillLevelUp,
            GameMessageToEventTransformer::transformSkillLevelUp
        );
        TRANSFORMERS.put(
            ParsedGameMessageType.TotalLevel,
            GameMessageToEventTransformer::transformTotalLevel
        );
        TRANSFORMERS.put(
            ParsedGameMessageType.CombatLevelUp,
            GameMessageToEventTransformer::transformCombatLevelUp
        );
        TRANSFORMERS.put(
            ParsedGameMessageType.CombatTask,
            GameMessageToEventTransformer::transformCombatTask
        );
        TRANSFORMERS.put(
            ParsedGameMessageType.QuestCompletion,
            GameMessageToEventTransformer::transformQuestCompletion
        );
        TRANSFORMERS.put(
            ParsedGameMessageType.CollectionLogUnlock,
            GameMessageToEventTransformer::transformCollectionLogUnlock
        );
    }

    public static BUEvent transformGameMessage(
        ParsedGameMessage gameMessage, long dispatchedFromAccountHash) {
        if (gameMessage == null) {
            return null;
        }
        ParsedGameMessageType parsedGameMessageType = gameMessage.getType();
        Transformer transformer = TRANSFORMERS.get(parsedGameMessageType);
        if (transformer == null) {
            return null;
        }

        ISOOffsetDateTime timestamp = new ISOOffsetDateTime(OffsetDateTime.now());
        return transformer.apply(gameMessage, dispatchedFromAccountHash, timestamp);
    }

    private static BUEvent transformSkillLevelUp(
        ParsedGameMessage gameMessage, long dispatchedFromAccountHash, ISOOffsetDateTime timestamp) {
        SkillLevelUpParsedGameMessage m = (SkillLevelUpParsedGameMessage) gameMessage;

        int level = m.getLevel();
        if (!SHARE_SKILL_LEVEL_UP_OF_SET.contains(level)) {
            return null;
        }

        return new SkillLevelUpAchievementBUEvent(
            dispatchedFromAccountHash,
            timestamp,
            m.getSkill(),
            level
        );
    }

    private static BUEvent transformTotalLevel(
        ParsedGameMessage gameMessage, long dispatchedFromAccountHash, ISOOffsetDateTime timestamp) {
        TotalLevelParsedGameMessage m = (TotalLevelParsedGameMessage) gameMessage;
        return new TotalLevelAchievementBUEvent(dispatchedFromAccountHash, timestamp, m.getTotalLevel());
    }

    private static BUEvent transformCombatLevelUp(
        ParsedGameMessage gameMessage, long dispatchedFromAccountHash, ISOOffsetDateTime timestamp) {
        CombatLevelUpParsedGameMessage m = (CombatLevelUpParsedGameMessage) gameMessage;

        int level = m.getLevel();
        if (!SHARE_COMBAT_LEVEL_UP_OF_SET.contains(level)) {
            return null;
        }

        return new CombatLevelUpAchievementBUEvent(
            dispatchedFromAccountHash,
            timestamp,
            level
        );
    }

    private static BUEvent transformCombatTask(
        ParsedGameMessage gameMessage, long dispatchedFromAccountHash, ISOOffsetDateTime timestamp) {
        CombatTaskParsedGameMessage m = (CombatTaskParsedGameMessage) gameMessage;
        return new CombatTaskAchievementBUEvent(
            dispatchedFromAccountHash,
            timestamp,
            m.getTier(),
            m.getName()
        );
    }

    private static BUEvent transformQuestCompletion(
        ParsedGameMessage gameMessage, long dispatchedFromAccountHash, ISOOffsetDateTime timestamp) {
        QuestCompletionParsedGameMessage m = (QuestCompletionParsedGameMessage) gameMessage;
        return new QuestCompletionAchievementBUEvent(dispatchedFromAccountHash, timestamp, m.getName());
    }

    private static BUEvent transformCollectionLogUnlock(
        ParsedGameMessage gameMessage, long dispatchedFromAccountHash, ISOOffsetDateTime timestamp) {
        CollectionLogUnlockParsedGameMessage m = (CollectionLogUnlockParsedGameMessage) gameMessage;
        return new CollectionLogUnlockAchievementBUEvent(dispatchedFromAccountHash, timestamp, m.getItemName());
    }

    @FunctionalInterface
    interface Transformer {
        BUEvent apply(ParsedGameMessage gameMessage, long dispatchedFromAccountHash, ISOOffsetDateTime timestamp);
    }
}
