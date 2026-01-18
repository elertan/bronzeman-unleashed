# Models

Data models used throughout the plugin.

## Files

| Model | File | Description |
|-------|------|-------------|
| `GameRules` | `models/GameRules.java` | Game rule settings (tradeable only, restrict GE, etc.) |
| `Member` | `models/Member.java` | Group member data |
| `MemberRole` | `models/MemberRole.java` | Member role enum (OWNER, MEMBER) |
| `UnlockedItem` | `models/UnlockedItem.java` | Unlocked item record |
| `AccountConfiguration` | `models/AccountConfiguration.java` | Account-specific config |
| `GroundItemOwnedByData` | `models/GroundItemOwnedByData.java` | Ground item ownership tracking |
| `GroundItemOwnedByKey` | `models/GroundItemOwnedByKey.java` | Key for ground item lookup |
| `AchievementDiaryArea` | `models/AchievementDiaryArea.java` | Diary area enum |
| `AchievementDiaryTier` | `models/AchievementDiaryTier.java` | Diary tier enum |
| `ISOOffsetDateTime` | `models/ISOOffsetDateTime.java` | Timestamp wrapper |

## Key Models

### GameRules

Configurable game restrictions:

```java
public class GameRules {
    boolean tradeableItemsOnly;
    boolean restrictGroundItems;
    boolean restrictGrandExchange;
    boolean restrictTrades;
    boolean restrictPlayerOwnedHouse;
    boolean restrictPvp;
    // etc.
}
```

### UnlockedItem

Tracks when and by whom an item was unlocked:

```java
public class UnlockedItem {
    int itemId;
    long unlockedByAccountHash;
    ISOOffsetDateTime unlockedAt;
}
```

### Member

Group member data:

```java
public class Member {
    long accountHash;
    String displayName;
    MemberRole role;
}
```
