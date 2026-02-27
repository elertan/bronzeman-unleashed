package com.elertan.chat;

import com.elertan.chat.ParsedGameMessage.CombatLevelUpParsedGameMessage;
import com.elertan.chat.ParsedGameMessage.CombatTaskParsedGameMessage;
import com.elertan.chat.ParsedGameMessage.CollectionLogUnlockParsedGameMessage;
import com.elertan.chat.ParsedGameMessage.QuestCompletionParsedGameMessage;
import com.elertan.chat.ParsedGameMessage.SkillLevelUpParsedGameMessage;
import com.elertan.chat.ParsedGameMessage.TotalLevelParsedGameMessage;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.util.Text;

public class GameMessageParser {

    private static final Pattern LEVEL_UP = Pattern.compile(
        "Congratulations, you've just advanced your (.+) level\\. You are now level (\\d+)\\.");
    private static final Pattern MAX_LEVEL_UP = Pattern.compile(
        "Congratulations, you've reached the highest possible (.+) level of (\\d+)\\.");
    private static final Pattern TOTAL_LEVEL = Pattern.compile(
        "Congratulations, you've reached a total level of (\\d+)\\.");
    private static final Pattern COMBAT_TASK = Pattern.compile(
        "Congratulations, you've completed a (\\w+) combat task: (.+) \\(.+");
    private static final Pattern QUEST_COMPLETE = Pattern.compile(
        "Congratulations, you've completed a quest: (.+)");
    private static final Pattern COLLECTION_LOG_UNLOCK = Pattern.compile(
        "New item added to your collection log: (.+)");

    private static final List<Function<String, ParsedGameMessage>> allParsers = Arrays.asList(
        GameMessageParser::tryParseLevelUp, GameMessageParser::tryParseTotalLevel,
        GameMessageParser::tryParseCombatTask, GameMessageParser::tryParseQuestComplete,
        GameMessageParser::tryParseMaxLevelUp, GameMessageParser::tryParseCollectionLogUnlock);

    public static ParsedGameMessage tryParseGameMessage(String message) {
        for (Function<String, ParsedGameMessage> parser : allParsers) {
            ParsedGameMessage result = parser.apply(message);
            if (result != null) return result;
        }
        return null;
    }

    private static ParsedGameMessage tryParseLevelUp(String msg) {
        Matcher m = LEVEL_UP.matcher(msg);
        if (!m.find()) return null;
        String skill = m.group(1);
        int level = Integer.parseInt(m.group(2));
        return skill.equalsIgnoreCase("combat")
            ? new CombatLevelUpParsedGameMessage(level)
            : new SkillLevelUpParsedGameMessage(skill, level);
    }

    private static TotalLevelParsedGameMessage tryParseTotalLevel(String msg) {
        Matcher m = TOTAL_LEVEL.matcher(msg);
        return m.find() ? new TotalLevelParsedGameMessage(Integer.parseInt(m.group(1))) : null;
    }

    private static CombatTaskParsedGameMessage tryParseCombatTask(String msg) {
        Matcher m = COMBAT_TASK.matcher(msg);
        return m.find() ? new CombatTaskParsedGameMessage(m.group(1), Text.removeTags(m.group(2))) : null;
    }

    private static QuestCompletionParsedGameMessage tryParseQuestComplete(String msg) {
        Matcher m = QUEST_COMPLETE.matcher(msg);
        return m.find() ? new QuestCompletionParsedGameMessage(Text.removeTags(m.group(1))) : null;
    }

    private static SkillLevelUpParsedGameMessage tryParseMaxLevelUp(String msg) {
        Matcher m = MAX_LEVEL_UP.matcher(msg);
        return m.find() ? new SkillLevelUpParsedGameMessage(m.group(1), Integer.parseInt(m.group(2))) : null;
    }

    public static CollectionLogUnlockParsedGameMessage tryParseCollectionLogUnlock(String msg) {
        Matcher m = COLLECTION_LOG_UNLOCK.matcher(msg);
        return m.find() ? new CollectionLogUnlockParsedGameMessage(m.group(1)) : null;
    }
}
