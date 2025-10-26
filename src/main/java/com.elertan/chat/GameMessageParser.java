package com.elertan.chat;

import net.runelite.client.util.Text;

import java.util.List;
import java.util.Arrays;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GameMessageParser {
    private static final Pattern LEVEL_UP =
            Pattern.compile("Congratulations, you've just advanced your (.+) level\\. You are now level (\\d+)\\.");
    private static final Pattern MAX_LEVEL_UP =
            Pattern.compile("Congratulations, you've reached the highest possible (.+) level of (\\d+)\\.");
    private static final Pattern TOTAL_LEVEL =
            Pattern.compile("Congratulations, you've reached a total level of (\\d+)\\.");
    private static final Pattern COMBAT_TASK =
            Pattern.compile("Congratulations, you've completed a (\\w+) combat task: (.+) \\(.+");
    private static final Pattern QUEST_COMPLETE =
            Pattern.compile("Congratulations, you've completed a quest: (.+)");

    private static final List<Function<String, ParsedGameMessage>> allParsers = Arrays.asList(
            GameMessageParser::tryParseLevelUp,
            GameMessageParser::tryParseTotalLevel,
            GameMessageParser::tryParseCombatTask,
            GameMessageParser::tryParseQuestComplete,
            GameMessageParser::tryParseMaxLevelUp
    );

    public static ParsedGameMessage tryParseGameMessage(String message) {
        for (Function<String, ParsedGameMessage> parser : allParsers) {
            ParsedGameMessage result = parser.apply(message);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static LevelUpParsedGameMessage tryParseLevelUp(String message) {
        Matcher matcher = LEVEL_UP.matcher(message);
        if (!matcher.find()) {
            return null;
        }

        String skill = matcher.group(1);
        int level = Integer.parseInt(matcher.group(2));
        return new LevelUpParsedGameMessage(skill, level);
    }

    private static TotalLevelParsedGameMessage tryParseTotalLevel(String message) {
        Matcher matcher = TOTAL_LEVEL.matcher(message);
        if (!matcher.find()) {
            return null;
        }

        int totalLevel = Integer.parseInt(matcher.group(1));
        return new TotalLevelParsedGameMessage(totalLevel);
    }

    private static CombatTaskParsedGameMessage tryParseCombatTask(String message) {
        Matcher matcher = COMBAT_TASK.matcher(message);
        if (!matcher.find()) {
            return null;
        }

        String tier = matcher.group(1);
        String name = Text.removeTags(matcher.group(2));
        return new CombatTaskParsedGameMessage(tier, name);
    }

    private static QuestCompletionParsedGameMessage tryParseQuestComplete(String message) {
        Matcher matcher = QUEST_COMPLETE.matcher(message);
        if (!matcher.find()) {
            return null;
        }

        String name = Text.removeTags(matcher.group(1));
        return new QuestCompletionParsedGameMessage(name);
    }

    private static LevelUpParsedGameMessage tryParseMaxLevelUp(String message) {
        Matcher matcher = MAX_LEVEL_UP.matcher(message);
        if (!matcher.find()) {
            return null;
        }

        String skill = matcher.group(1);
        int level = Integer.parseInt(matcher.group(2));
        return new LevelUpParsedGameMessage(skill, level);
    }
}
