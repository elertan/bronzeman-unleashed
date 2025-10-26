package com.elertan.event;

import com.elertan.chat.*;
import com.elertan.models.ISOOffsetDateTime;
import com.google.common.collect.ImmutableSet;

import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.Set;

public class GameMessageToEventTransformer {
    @FunctionalInterface
    interface Transformer {
        BUEvent apply(ParsedGameMessage gameMessage, long dispatchedFromAccountHash);
    }

    private static final EnumMap<ParsedGameMessageType, Transformer> TRANSFORMERS = new EnumMap<>(ParsedGameMessageType.class);

    static {
        TRANSFORMERS.put(ParsedGameMessageType.LevelUp, GameMessageToEventTransformer::transformLevelUp);
        TRANSFORMERS.put(ParsedGameMessageType.TotalLevel, GameMessageToEventTransformer::transformTotalLevel);
        TRANSFORMERS.put(ParsedGameMessageType.CombatTask, GameMessageToEventTransformer::transformCombatTask);
        TRANSFORMERS.put(ParsedGameMessageType.QuestCompletion, GameMessageToEventTransformer::transformQuestCompletion);
    }

    private static final Set<Integer> SHARE_LEVEL_UP_OF_SET = ImmutableSet.of(
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
            85,
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


    public static BUEvent transformGameMessage(ParsedGameMessage gameMessage, long dispatchedFromAccountHash) {
        if (gameMessage == null) {
            return null;
        }
        ParsedGameMessageType parsedGameMessageType = gameMessage.getType();
        Transformer transformer = TRANSFORMERS.get(parsedGameMessageType);
        if (transformer == null) {
            return null;
        }
        return transformer.apply(gameMessage, dispatchedFromAccountHash);
    }

    private static BUEvent transformLevelUp(ParsedGameMessage gameMessage, long dispatchedFromAccountHash) {
        LevelUpParsedGameMessage m = (LevelUpParsedGameMessage) gameMessage;

        int level = m.getLevel();
        if (!SHARE_LEVEL_UP_OF_SET.contains(level)) {
            return null;
        }

        ISOOffsetDateTime now = new ISOOffsetDateTime(OffsetDateTime.now());
        return new LevelUpAchievementBUEvent(dispatchedFromAccountHash, now, m.getSkill(), level);
    }

    private static BUEvent transformTotalLevel(ParsedGameMessage gameMessage, long dispatchedFromAccountHash) {
        TotalLevelParsedGameMessage m = (TotalLevelParsedGameMessage) gameMessage;
        ISOOffsetDateTime now = new ISOOffsetDateTime(OffsetDateTime.now());
        return new TotalLevelAchievementBUEvent(dispatchedFromAccountHash, now, m.getTotalLevel());
    }

    private static BUEvent transformCombatTask(ParsedGameMessage gameMessage, long dispatchedFromAccountHash) {
        CombatTaskParsedGameMessage m = (CombatTaskParsedGameMessage) gameMessage;
        ISOOffsetDateTime now = new ISOOffsetDateTime(OffsetDateTime.now());
        return new CombatTaskAchievementBUEvent(dispatchedFromAccountHash, now, m.getTier(), m.getName());
    }

    private static BUEvent transformQuestCompletion(ParsedGameMessage gameMessage, long dispatchedFromAccountHash) {
        QuestCompletionParsedGameMessage m = (QuestCompletionParsedGameMessage) gameMessage;
        ISOOffsetDateTime now = new ISOOffsetDateTime(OffsetDateTime.now());
        return new QuestCompletionAchievementBUEvent(dispatchedFromAccountHash, now, m.getName());
    }
}
