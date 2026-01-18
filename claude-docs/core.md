# Plugin Core

## Files

| File | Description |
|------|-------------|
| `BUPlugin.java` | Main plugin entry point |
| `BUPluginConfig.java` | Config interface |
| `BUPluginLifecycle.java` | Lifecycle interface |
| `BUSoundHelper.java` | Sound effect utilities |

## BUPlugin

Main entry point. Responsibilities:
- Initialize lifecycle dependencies in order
- Subscribe to RuneLite events
- Route events to appropriate services/policies
- Manage startup/shutdown sequence

### Event Routing

| RuneLite Event | Routed To |
|----------------|-----------|
| `AccountHashChanged` | `accountConfigurationService` |
| `GameStateChanged` | `buChatService`, `buPartyService`, `achievementDiaryService`, `itemUnlockService`, `petDropService` |
| `GameTick` | `petDropService` |
| `ConfigChanged` | `accountConfigurationService` |
| `ItemContainerChanged` | `itemUnlockService` |
| `ServerNpcLoot` | `itemUnlockService`, `lootValuationService` |
| `ChatMessage` | `buCommandService`, `buChatService`, `petDropService` |
| `ScriptCallbackEvent` | `buChatService` |
| `ScriptPostFired` | `grandExchangePolicy` |
| `MenuOptionClicked` | `tradePolicy`, `groundItemsPolicy`, `playerOwnedHousePolicy`, `playerVersusPlayerPolicy`, `faladorPartyRoomPolicy` |
| `VarbitChanged` | `buChatService`, `achievementDiaryService` |
| `WidgetLoaded/Closed` | `shopPolicy` |
| `ItemSpawned/Despawned` | `itemUnlockService`, `groundItemsPolicy` |
| `ScriptPreFired` | `playerOwnedHousePolicy` |
| `ActorDeath` | `playerVersusPlayerPolicy` |
| `PlayerLootReceived` | `playerVersusPlayerPolicy` |

### Lifecycle Order

Startup order (shutdown is reverse):

1. Core: `buResourceService`, `accountConfigurationService`, `remoteStorageService`
2. Data providers: `membersDataProvider`, `gameRulesDataProvider`, `unlockedItemsDataProvider`, `lastEventDataProvider`, `groundItemOwnedByDataProvider`
3. Services: `buPanelService`, `buOverlayService`, `buChatService`, `memberService`, `gameRulesService`, `itemUnlockService`, `buPartyService`, `buEventService`, `lootValuationService`, `policyService`, `achievementDiaryService`
4. Policies: `grandExchangePolicy`, `tradePolicy`, `shopPolicy`, `groundItemsPolicy`, `playerOwnedHousePolicy`, `playerVersusPlayerPolicy`, `faladorPartyRoomPolicy`, `petDropService`, `buCommandService`
5. `chatMessageEventBroadcaster`

## BUPluginConfig

RuneLite config interface. Annotated with `@ConfigGroup("bronzemanunleashed")`.

Location: `src/main/java/com.elertan/BUPluginConfig.java`

## BUPluginLifecycle

```java
public interface BUPluginLifecycle {
    void startUp() throws Exception;
    void shutDown() throws Exception;
}
```

All managed components implement this interface.
