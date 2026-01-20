---
date: 2026-01-20
status: approved
impl-spec: planspec:impl-spec
---

# Suppress Unlock Overlay for Collection Log Items

> **Next step:** `planspec:impl-spec planspec/designs/collection-log-overlay-suppression.md`

## Problem

When a player unlocks an item that is also a new collection log entry, two overlays appear:
1. The game's native collection log notification
2. Our plugin's unlock overlay

This is redundant and visually cluttered since the native UI already showcases the unlock. We want to suppress our overlay for collection log unlocks, but only for the local player - group members should still see the unlock notification.

## Success Criteria

**Must have:**
- Local player's unlock overlay is suppressed when item is a collection log unlock
- Group members still see the unlock overlay (remote unlocks unaffected)
- Item still unlocks and syncs to Firebase
- Works for all collection log items (not just pets)
- No race conditions between chat message and container change events

**Quality attributes:**
- No flicker (overlay never briefly appears then hides)
- Warning log if suppression entry expires without being matched (indicates potential bug)

**Not building:**
- Config option to disable this behavior
- Suppression for remote collection log unlocks (can't detect other players' collection log messages)

## Approach

Track item names from collection log chat messages in a time-limited set. When an unlock fires for the local player, check if the item name matches a recent collection log entry and suppress the overlay if so.

### Alternatives Considered

| Alternative | Why Not |
|-------------|---------|
| Inventory diffing (like collection-log plugin) | Overly complex; we already have item ID/name at unlock time, don't need to diff |
| Script 4100 detection | Only fires when browsing collection log interface, not at drop time |
| Suppress at overlay render time | Could cause flicker if animation already started |

## Design

### Architecture Overview

```
ChatMessage (GAMEMESSAGE)
    │
    ▼
BUChatService.onChatMessage()
    │ parses "New item added to your collection log: X"
    ▼
CollectionLogService.addRecentCollectionLogUnlock(itemName)
    │ stores in Map<String, Integer> with current tick
    │
    ═══════════════════════════════════════════════════
    │
ItemContainerChanged / ServerNpcLoot / etc
    │
    ▼
ItemUnlockService.unlockItem()
    │ async chain → Firebase persist
    ▼
UnlockedItemsDataProvider listener onUpdate()
    │
    ▼
clientThread.invokeLater() ← defers to ensure chat processed first
    │
    ▼
Check: isLocalPlayer && tryConsumeOverlaySuppression(name)?
    │
    ├─ Yes → suppress overlay (return)
    └─ No  → itemUnlockOverlay.enqueueShowUnlock()
```

**Components:**
- `CollectionLogService` (new) - tracks recent collection log unlocks for suppression
- `BUChatService` - calls service when parsing collection log message
- `ItemUnlockService` - checks service before showing overlay

**Key insight:** Using `clientThread.invokeLater()` ensures that even if container change fires before chat message on the same tick, the overlay decision is deferred until after chat processing completes.

### Data Model

**CollectionLogService state:**
```java
// Item name → tick when added
private final Map<String, Integer> recentCollectionLogUnlocks = new ConcurrentHashMap<>();

// Updated each game tick, read from any thread
private volatile int currentTick = 0;
```

- `ConcurrentHashMap` for thread-safe access from chat thread (add) and client thread (consume)
- `volatile` ensures visibility of tick count across threads
- Entries expire after 12 ticks (~7.2 seconds) - generous buffer for any timing edge cases

### Interfaces

**CollectionLogService:**
```java
public class CollectionLogService implements BUPluginLifecycle {

    /**
     * Track a collection log unlock for overlay suppression.
     * Called synchronously when "New item added to your collection log" is parsed.
     * Thread-safe: can be called from event bus thread.
     */
    public void addRecentCollectionLogUnlock(String itemName);

    /**
     * Check if item should have its unlock overlay suppressed.
     * Returns true and removes the entry if present (one-time consumption).
     * Should be called from client thread.
     */
    public boolean tryConsumeOverlaySuppression(String itemName);

    // Lifecycle hooks
    public void onGameTick(GameTick event);        // cleanup expired entries
    public void onGameStateChanged(GameStateChanged event); // clear on login screen
}
```

### Behavior

**Happy path:**
1. Player kills monster, gets collection log item
2. Chat message fires: "New item added to your collection log: Dragon pickaxe"
3. `BUChatService` parses message, calls `collectionLogService.addRecentCollectionLogUnlock("Dragon pickaxe")`
4. Item enters inventory, triggers unlock flow
5. `onUpdate()` fires, defers via `invokeLater()`
6. Check: local player + "Dragon pickaxe" in suppression map → true
7. Entry consumed, overlay suppressed
8. Native collection log UI shows the unlock (game handles this)

**Remote unlock (group member):**
1. Firebase SSE notifies of new unlock from group member
2. `onUpdate()` fires with different `acquiredByAccountHash`
3. `isLocalPlayer` check fails → overlay shows normally

**Edge case - no match (warning):**
1. Collection log message parsed, entry added
2. 12 ticks pass without matching unlock
3. `onGameTick` cleanup removes entry with warning log
4. This indicates either: item wasn't actually unlocked, or name mismatch bug

**Login screen:**
1. Player logs out or hops worlds
2. `GameState.LOGIN_SCREEN` detected
3. Map cleared to prevent stale entries affecting next session

### Testing Requirements

**Critical paths:**
- Collection log item unlocked locally → overlay suppressed
- Same item unlocked by group member → overlay shown
- Non-collection-log item unlocked locally → overlay shown

**Edge cases to cover:**
- Multiple collection log unlocks in quick succession
- Collection log message arrives after container change (same tick) - verify `invokeLater` handles this
- Entry expires without match → warning logged

**Integration points:**
- Verify `BUChatService` calls service for `CollectionLogUnlockParsedGameMessage`
- Verify `ItemUnlockService` checks service before overlay

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Item name mismatch between chat and unlock | Low | Overlay not suppressed | Both use `ItemComposition.getName()`; log warning on expiry |
| Race condition despite `invokeLater` | Very Low | Overlay briefly shown | 12-tick window provides generous buffer |
| Thread safety issues with ConcurrentHashMap | Very Low | Inconsistent state | Well-tested JDK class; simple get/put/remove operations |

## Dependencies

- **Blocking:** None - uses existing infrastructure
- **Non-blocking:** Issue #81 (collection log sync) could share this service later

## Open Questions

None - all questions resolved during brainstorming.
