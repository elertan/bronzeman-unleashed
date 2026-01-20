---
date: 2026-01-20
design-spec: ../designs/collection-log-overlay-suppression.md
status: in-progress
executor: planspec:impl-spec-executor
---

# Implementation: Suppress Unlock Overlay for Collection Log Items

> **Execute with:** `planspec:impl-spec-executor planspec/implementations/collection-log-overlay-suppression.md`

## Overview

Suppress the plugin's unlock overlay when an item is also a collection log unlock (for local player only), avoiding redundant notifications since the game's native UI already shows it.

**Design spec:** [collection-log-overlay-suppression.md](../designs/collection-log-overlay-suppression.md)
**Security reviews:** None

## Prerequisites

- [ ] Design spec approved

---

## Phase 1: Create CollectionLogService

### Task 1.1: Create CollectionLogService

**Context:** New service to track recent collection log unlocks for overlay suppression. Follows existing service patterns (see `PetDropService`, `MinigameService`).

**Files:**
- Create: `src/main/java/com.elertan/CollectionLogService.java`

**Requirements:**
- Implement `BUPluginLifecycle` interface
- Use `@Singleton` and `@Slf4j` annotations
- Thread-safe `ConcurrentHashMap<String, Integer>` for item name → tick tracking
- `volatile int currentTick` for cross-thread tick visibility
- Inject `Client` for `getTickCount()`

**Implementation:**
```java
@Slf4j
@Singleton
public class CollectionLogService implements BUPluginLifecycle {

    private static final int EXPIRY_TICKS = 12;

    private final Map<String, Integer> recentCollectionLogUnlocks = new ConcurrentHashMap<>();
    private volatile int currentTick = 0;

    @Inject
    private Client client;

    @Override
    public void startUp() throws Exception {
        // No initialization needed
    }

    @Override
    public void shutDown() throws Exception {
        recentCollectionLogUnlocks.clear();
        currentTick = 0;
    }

    /**
     * Track a collection log unlock for overlay suppression.
     * Called synchronously when "New item added to your collection log" is parsed.
     * Thread-safe: can be called from event bus thread.
     */
    public void addRecentCollectionLogUnlock(String itemName) {
        recentCollectionLogUnlocks.put(itemName, currentTick);
    }

    /**
     * Check if item should have its unlock overlay suppressed.
     * Returns true and removes the entry if present (one-time consumption).
     * Should be called from client thread.
     */
    public boolean tryConsumeOverlaySuppression(String itemName) {
        return recentCollectionLogUnlocks.remove(itemName) != null;
    }

    public void onGameTick(GameTick event) {
        currentTick = client.getTickCount();

        recentCollectionLogUnlocks.entrySet().removeIf(entry -> {
            if (currentTick - entry.getValue() > EXPIRY_TICKS) {
                log.warn("Collection log item '{}' not matched by unlock within {} ticks",
                    entry.getKey(), EXPIRY_TICKS);
                return true;
            }
            return false;
        });
    }

    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN) {
            recentCollectionLogUnlocks.clear();
        }
    }
}
```

**Acceptance Criteria:**
- [ ] Service compiles without errors
- [ ] Implements `BUPluginLifecycle` with `startUp()` and `shutDown()`
- [ ] `addRecentCollectionLogUnlock()` stores item name with current tick
- [ ] `tryConsumeOverlaySuppression()` returns true and removes if present, false otherwise
- [ ] `onGameTick()` removes entries older than 12 ticks with warning log
- [ ] `onGameStateChanged()` clears map on `LOGIN_SCREEN`

**Dependencies:** None

---

## Phase 2: Wire Up Service

### Task 2.1: Register CollectionLogService in BUPlugin

**Context:** Services must be injected and added to lifecycle dependencies to be started/stopped with the plugin.

**Files:**
- Modify: `src/main/java/com.elertan/BUPlugin.java`

**Requirements:**
- Add `@Inject` field for `CollectionLogService`
- Add to `lifecycleDependencies` list in `initLifecycleDependencies()` (in Services section)
- Forward `onGameTick` event to service
- Forward `onGameStateChanged` event to service

**Changes:**

1. Add injection (around line 120, with other services):
```java
@Inject
private CollectionLogService collectionLogService;
```

2. Add to lifecycle dependencies (around line 161, in Services section):
```java
lifecycleDependencies.add(collectionLogService);
```

3. Forward onGameTick (in existing `onGameTick` method, around line 266):
```java
collectionLogService.onGameTick(event);
```

4. Forward onGameStateChanged (in existing `onGameStateChanged` method, around line 259):
```java
collectionLogService.onGameStateChanged(event);
```

**Acceptance Criteria:**
- [ ] Service is injected in BUPlugin
- [ ] Service is added to lifecycle dependencies
- [ ] `onGameTick` forwarded to service
- [ ] `onGameStateChanged` forwarded to service
- [ ] Plugin compiles without errors

**Dependencies:** Task 1.1

### Task 2.2: Call service from BUChatService on collection log message

**Context:** When a collection log message is parsed, we need to track the item name for suppression before any async processing.

**Files:**
- Modify: `src/main/java/com.elertan/BUChatService.java`

**Requirements:**
- Inject `CollectionLogService`
- After parsing `CollectionLogUnlockParsedGameMessage`, call `addRecentCollectionLogUnlock()` synchronously (before `publishEvent`)

**Changes:**

1. Add injection:
```java
@Inject
private CollectionLogService collectionLogService;
```

2. In `onChatMessage()`, after parsing check (around line 118-122), add:
```java
ParsedGameMessage parsedGameMessage =
    GameMessageParser.tryParseGameMessage(chatMessage.getMessage());
if (parsedGameMessage == null) {
    return;
}

// Track collection log unlocks for overlay suppression (must be synchronous, before async publish)
if (parsedGameMessage instanceof CollectionLogUnlockParsedGameMessage) {
    CollectionLogUnlockParsedGameMessage clogMessage =
        (CollectionLogUnlockParsedGameMessage) parsedGameMessage;
    collectionLogService.addRecentCollectionLogUnlock(clogMessage.getItemName());
}

BUEvent event = GameMessageToEventTransformer.transformGameMessage(
    parsedGameMessage, client.getAccountHash());
```

**Acceptance Criteria:**
- [ ] `CollectionLogService` injected
- [ ] `addRecentCollectionLogUnlock()` called synchronously when collection log message parsed
- [ ] Call happens before `transformGameMessage` / `publishEvent`
- [ ] Compiles without errors

**Dependencies:** Task 2.1

### Task 2.3: Check suppression in ItemUnlockService before showing overlay

**Context:** When an unlock fires for the local player, defer via `invokeLater` and check suppression before showing overlay.

**Files:**
- Modify: `src/main/java/com.elertan/ItemUnlockService.java`

**Requirements:**
- Inject `CollectionLogService`
- In `onUpdate()` listener, wrap overlay call in `clientThread.invokeLater()`
- Check if local player AND suppression applies
- Add comment explaining why `invokeLater` is needed

**Changes:**

1. Add injection:
```java
@Inject
private CollectionLogService collectionLogService;
```

2. Modify `onUpdate()` in the listener (around line 172-179):
```java
@Override
public void onUpdate(UnlockedItem unlockedItem) {
    // Defer to client thread to:
    // 1. Ensure any collection log chat messages from the same tick are processed first
    // 2. Safely access client.getAccountHash()
    clientThread.invokeLater(() -> {
        boolean isLocalPlayer = client.getAccountHash() == unlockedItem.getAcquiredByAccountHash();

        if (isLocalPlayer && collectionLogService.tryConsumeOverlaySuppression(unlockedItem.getName())) {
            // Suppress overlay - native collection log UI already shows it
            return;
        }

        itemUnlockOverlay.enqueueShowUnlock(
            unlockedItem.getId(),
            unlockedItem.getAcquiredByAccountHash(),
            unlockedItem.getDroppedByNPCId()
        );
    });

    // Chat notification (unchanged - keep existing code)
    boolean hideChat = buPluginConfig.hideUnlockChatInMinigames() && minigameService.isInMinigameOrInstance();
    // ... rest of existing chat code
}
```

**Acceptance Criteria:**
- [ ] `CollectionLogService` injected
- [ ] Overlay call wrapped in `clientThread.invokeLater()`
- [ ] Local player check uses `client.getAccountHash()` inside `invokeLater`
- [ ] Suppression check happens before `enqueueShowUnlock()`
- [ ] Comment explains the two reasons for `invokeLater`
- [ ] Remote unlocks still show overlay (no suppression check for non-local)
- [ ] Compiles without errors

**Dependencies:** Task 2.1

### CHECKPOINT

Gate: Plugin compiles and runs. Manual testing:
1. Get a collection log item → overlay should NOT show (native UI shows it)
2. Get a non-collection-log item → overlay should show normally
3. Group member gets item → overlay should show

---

## Completion Checklist

- [ ] All tasks completed
- [ ] Plugin compiles without errors
- [ ] Manual testing passed
- [ ] Design spec success criteria met:
  - [ ] Local player's unlock overlay suppressed for collection log items
  - [ ] Group members still see overlay for same unlock
  - [ ] Item still unlocks and syncs to Firebase
  - [ ] Works for all collection log items
  - [ ] No race conditions (invokeLater handles same-tick ordering)
