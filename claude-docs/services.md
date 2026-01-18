# Services

Domain logic services. All implement `BUPluginLifecycle`.

## Core Services

| Service | Responsibility |
|---------|----------------|
| `BUResourceService` | Resource loading (images, icons) |
| `AccountConfigurationService` | Account config management, Firebase URL storage |
| `RemoteStorageService` | Firebase connection management |

## Domain Services

| Service | Responsibility |
|---------|----------------|
| `ItemUnlockService` | **Core** - Detects item unlocks from inventory/loot, broadcasts to group |
| `GameRulesService` | Game rules access and caching |
| `MemberService` | Group member management |
| `BUEventService` | Event broadcasting (achievements, loot, pet drops) |
| `BUChatService` | Chat message sending, achievement announcements |
| `BUPanelService` | Side panel management |
| `BUOverlayService` | In-game overlay management |
| `PolicyService` | Policy coordination, locked item checking |
| `AchievementDiaryService` | Diary completion tracking |
| `LootValuationService` | Loot value calculation for valuable drop detection |
| `MinigameService` | Minigame state tracking |
| `BUPartyService` | RuneLite party integration |
| `PetDropService` | Pet drop detection via chat/follower |
| `BUCommandService` | Chat command handling (`::bu reset`) |

## Key Service: ItemUnlockService

Central service for item unlock logic.

### Event Handlers
- `onItemContainerChanged` - Detects new items in inventory/bank/equipment
- `onServerNpcLoot` - Detects NPC drops
- `onItemSpawned` - Ground item detection
- `onGameStateChanged` - State reset on logout

### Unlock Flow
1. Detect new item via container change or loot event
2. Check if item is already unlocked
3. If new unlock → store in `UnlockedItemsDataProvider`
4. Fire unlock overlay/sound/chat notification
5. Sync to Firebase for group members

## Key Service: BUEventService

Broadcasts achievements to group via Firebase.

### Supported Events
- Skill level up
- Combat level up
- Quest completion
- Achievement diary completion
- Combat task completion
- Total level milestone
- Pet drop
- Valuable loot

### Flow
1. Service/policy detects achievement
2. Creates appropriate `BUEvent` subclass
3. Calls `BUEventService.broadcast(event)`
4. Event stored in `LastEventDataProvider`
5. Firebase syncs to other clients
6. Other clients receive via SSE → show notification
