# Events

Event system for broadcasting achievements and significant moments to group members.

## Base Class

```java
public abstract class BUEvent {
    long dispatchedFromAccountHash;
    ISOOffsetDateTime timestamp;
    abstract BUEventType getType();
}
```

## Event Types

| Event Class | Type Enum | Description |
|-------------|-----------|-------------|
| `SkillLevelUpAchievementBUEvent` | `SKILL_LEVEL_UP` | Skill level milestone |
| `CombatLevelUpAchievementBUEvent` | `COMBAT_LEVEL_UP` | Combat level milestone |
| `TotalLevelAchievementBUEvent` | `TOTAL_LEVEL` | Total level milestone |
| `QuestCompletionAchievementBUEvent` | `QUEST_COMPLETION` | Quest completed |
| `DiaryCompletionAchievementBUEvent` | `DIARY_COMPLETION` | Achievement diary completed |
| `CombatTaskAchievementBUEvent` | `COMBAT_TASK` | Combat achievement task |
| `PetDropBUEvent` | `PET_DROP` | Pet obtained |
| `ValuableLootBUEvent` | `VALUABLE_LOOT` | High-value loot drop |

## Files

| File | Description |
|------|-------------|
| `event/BUEvent.java` | Abstract base class |
| `event/BUEventType.java` | Event type enum |
| `event/BUEventGson.java` | Gson type adapter for polymorphic serialization |
| `event/GameMessageToEventTransformer.java` | Converts parsed chat messages to events |

## Flow

1. Achievement detected (via chat message parsing or service logic)
2. `GameMessageToEventTransformer.transform()` creates event (for chat-based)
3. Or service creates event directly
4. `BUEventService.broadcast(event)` called
5. Event stored via `LastEventDataProvider`
6. Firebase syncs to other clients
7. Other clients receive via SSE
8. `BUChatService` displays notification

## Adding New Event Type

See [workflows/add-event.md](workflows/add-event.md)
