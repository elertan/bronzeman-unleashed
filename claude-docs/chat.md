# Chat Parsing

Parse game chat messages to detect achievements and events.

## Architecture

```
ChatMessage (RuneLite event)
    ↓
GameMessageParser
    ↓
ParsedGameMessage (typed subclass)
    ↓
GameMessageToEventTransformer
    ↓
BUEvent (for broadcasting)
```

## Files

| File | Description |
|------|-------------|
| `chat/ChatMessageProvider.java` | Subscribes to chat, provides parsed messages |
| `chat/ChatMessageEventBroadcaster.java` | Broadcasts parsed messages as events |
| `chat/GameMessageParser.java` | Parses raw messages to typed objects |
| `chat/ParsedGameMessage.java` | Base class for parsed messages |
| `chat/ParsedGameMessageType.java` | Type enum |

## Parsed Message Types

| Class | Type | Pattern |
|-------|------|---------|
| `SkillLevelUpParsedGameMessage` | `SKILL_LEVEL_UP` | "Your X level is now Y" |
| `CombatLevelUpParsedGameMessage` | `COMBAT_LEVEL_UP` | Combat level messages |
| `TotalLevelParsedGameMessage` | `TOTAL_LEVEL` | Total level milestone |
| `QuestCompletionParsedGameMessage` | `QUEST_COMPLETION` | Quest completion |
| `CombatTaskParsedGameMessage` | `COMBAT_TASK` | Combat task completion |

## GameMessageParser

Parses chat message strings:

```java
public class GameMessageParser {
    public ParsedGameMessage parse(String message) {
        // Pattern matching for each message type
        if (isSkillLevelUp(message)) {
            return new SkillLevelUpParsedGameMessage(...);
        }
        // etc.
        return null;
    }
}
```

## Flow Example

1. Player completes quest
2. Game sends chat message: "Congratulations, you've completed a quest: Dragon Slayer"
3. `ChatMessageProvider` receives `ChatMessage` event
4. `GameMessageParser.parse()` returns `QuestCompletionParsedGameMessage`
5. `ChatMessageEventBroadcaster` receives parsed message
6. `GameMessageToEventTransformer.transform()` creates `QuestCompletionAchievementBUEvent`
7. `BUEventService.broadcast()` sends to group
