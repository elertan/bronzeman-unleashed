# Refactoring Plan

Organized in stages. Run `./gradlew build` after each stage to verify.

---

## Setup

Before starting, create refactoring branch:
```bash
git checkout -b refactoring
```

---

## Stage 1: Foundation Utilities (HIGH PRIORITY)

### 1.1 Create StateListenerManager

**New file:** `src/main/java/com.elertan/util/StateListenerManager.java`

Generic utility for managing state listeners with thread-safe add/remove/notify.

**Pattern replaced (12 occurrences):**
```java
private final ConcurrentLinkedQueue<Consumer<State>> stateListeners = new ConcurrentLinkedQueue<>();
public void addStateListener(Consumer<State> l) { stateListeners.add(l); }
public void removeStateListener(Consumer<State> l) { stateListeners.remove(l); }
private void notifyListeners(State state) {
    for (Consumer<State> l : stateListeners) {
        try { l.accept(state); } catch (Exception e) { log.error(...); }
    }
}
```

**Files to update:**
- [ ] `RemoteStorageService.java` (lines 35, 75-95)
- [ ] `GameRulesService.java` (lines 25, 53-59)
- [ ] `BUChatService.java` (line 51)
- [ ] `data/UnlockedItemsDataProvider.java` (lines 24-25, 103-109)
- [ ] `data/GameRulesDataProvider.java` (lines 21-22, 56-70)
- [ ] `data/MembersDataProvider.java`
- [ ] `data/GroundItemOwnedByDataProvider.java`
- [ ] `data/LastEventDataProvider.java`

---

### 1.2 Create AsyncUtils

**New file:** `src/main/java/com.elertan/util/AsyncUtils.java`

Utility for CompletableFuture error logging.

**Pattern replaced (23+ occurrences):**
```java
.whenComplete((result, throwable) -> {
    if (throwable != null) {
        log.error("message", throwable);
    }
});
```

**Files to update:**
- [ ] `ItemUnlockService.java` (lines 195-198, 256-260, 329-333, 502-506, 531-534, 615-619)
- [ ] `overlays/ItemUnlockOverlay.java` (lines 105-110)
- [ ] `policies/GroundItemsPolicy.java` (lines 172-177, 220-224, 446-450, 478-482)
- [ ] `policies/PlayerVersusPlayerPolicy.java` (lines 188-196)

---

### 1.3 Add sendRestrictionMessage to BUChatService

**File:** `BUChatService.java`

Add helper method:
```java
public void sendRestrictionMessage(MessageKey key) {
    ChatMessageBuilder builder = new ChatMessageBuilder();
    builder.append(buPluginConfig.chatRestrictionColor(),
        chatMessageProvider.messageFor(key));
    sendMessage(builder.build());
    buSoundHelper.playDisabledSound();
}
```

**Files to update (27 occurrences):**
- [ ] `policies/TradePolicy.java` (lines 141-148)
- [ ] `policies/FaladorPartyRoomPolicy.java` (lines 67-72)
- [ ] `policies/PlayerOwnedHousePolicy.java` (lines 356-362, 367-374)
- [ ] `policies/GroundItemsPolicy.java` (lines 307-312, 345-350, 365-378)
- [ ] `policies/PlayerVersusPlayerPolicy.java` (lines 286-292, 300-306)
- [ ] `policies/ShopPolicy.java`

---

## Stage 2: Data Provider Base Class (HIGH PRIORITY)

### 2.1 Create AbstractDataProvider

**New file:** `src/main/java/com.elertan/data/AbstractDataProvider.java`

Template for all data providers with:
- State enum (NotReady, Ready)
- StateListenerManager integration
- waitUntilReady() implementation
- RemoteStorageService state listener lifecycle
- Abstract methods: `initialize()`, `deinitialize()`, `clear()`

**Files to refactor:**
- [ ] `data/UnlockedItemsDataProvider.java` (~60 lines removed)
- [ ] `data/GameRulesDataProvider.java` (~60 lines removed)
- [ ] `data/MembersDataProvider.java` (~60 lines removed)
- [ ] `data/GroundItemOwnedByDataProvider.java` (~60 lines removed)
- [ ] `data/LastEventDataProvider.java` (~60 lines removed)

---

## Stage 3: Policy Improvements (MEDIUM PRIORITY)

### 3.1 Add Default Lifecycle to PolicyBase

**File:** `policies/PolicyBase.java`

Make PolicyBase implement BUPluginLifecycle with empty default methods.

**Files to simplify (remove empty startUp/shutDown):**
- [ ] `policies/GrandExchangePolicy.java` (lines 36-42)
- [ ] `policies/TradePolicy.java` (lines 52-59)
- [ ] `policies/ShopPolicy.java` (lines 56-61)
- [ ] `policies/FaladorPartyRoomPolicy.java` (lines 36-43)

---

### 3.2 Create Ground Item Utilities

**New file:** `src/main/java/com.elertan/util/GroundItemUtils.java`

Extract repeated WorldPoint/GroundItemOwnedByKey creation:
```java
public record WorldLocationData(WorldPoint worldPoint, WorldView worldView, int world, int plane) {}
public static WorldLocationData resolveLocation(Client client, Tile tile);
public static GroundItemOwnedByKey createKey(int itemId, WorldLocationData location);
```

**Files to update:**
- [ ] `policies/GroundItemsPolicy.java` (lines 135-149, 193-207, 283-296)
- [ ] `policies/PlayerVersusPlayerPolicy.java` (lines 179-186, 204-207)

---

## Stage 4: ViewModel Cleanup (MEDIUM PRIORITY)

### 4.1 Consolidate GameRulesEditorViewModel Listeners

**File:** `panel/components/GameRulesEditorViewModel.java`

Replace 11 individual listener fields with single shared listener.

Current (11 fields):
```java
private final PropertyChangeListener onlyForTradeableItemsListener = this::onlyForTradeableItemsListener;
private final PropertyChangeListener restrictGroundItemsListener = this::restrictGroundItemsListener;
// ... 9 more
```

Refactor to:
```java
private final PropertyChangeListener updateListener = evt -> tryUpdateGameRules();
```

---

### 4.2 Create BaseViewModel (Optional)

**New file:** `src/main/java/com.elertan/panel/BaseViewModel.java`

Abstract base with:
- Listener lifecycle management (auto-cleanup on close)
- Common close() pattern

**Files to refactor:**
- [ ] `panel/BUPanelViewModel.java`
- [ ] `panel/screens/MainScreenViewModel.java`
- [ ] `panel/screens/SetupScreenViewModel.java`
- [ ] `panel/screens/main/ConfigScreenViewModel.java`
- [ ] `panel/screens/main/UnlockedItemsScreenViewModel.java`
- [ ] `panel/components/GameRulesEditorViewModel.java`

---

## Stage 5: Lower Priority Items

### 5.1 Firebase Path Validation

**Files:**
- `remote/firebase/FirebaseKeyValueStorageAdapterBase.java` (lines 40-48)
- `remote/firebase/FirebaseObjectStorageAdapterBase.java` (lines 30-38)

Extract identical validation to shared utility or common base.

---

### 5.2 BUPlugin Event Dispatcher (Optional)

**File:** `BUPlugin.java` (lines 240-332)

Consider creating EventDispatcher to reduce 13 @Subscribe methods.
**Trade-off:** More indirection but cleaner main class.

---

### 5.3 Dependency Ordering Annotations (Optional)

**File:** `BUPlugin.java` (lines 129-168)

Replace manual lifecycle ordering with @DependsOn annotations.
**Trade-off:** Complexity may not be worth it for 16 services.

---

## Progress Tracking

| Stage | Status | Test Result |
|-------|--------|-------------|
| 1.1 StateListenerManager | ⬜ | |
| 1.2 AsyncUtils | ⬜ | |
| 1.3 sendRestrictionMessage | ⬜ | |
| 2.1 AbstractDataProvider | ⬜ | |
| 3.1 PolicyBase defaults | ⬜ | |
| 3.2 GroundItemUtils | ⬜ | |
| 4.1 GameRulesEditorVM | ⬜ | |
| 4.2 BaseViewModel | ⬜ | |
| 5.1 Firebase validation | ⬜ | |
| 5.2 Event dispatcher | ⬜ | |
| 5.3 Dependency ordering | ⬜ | |

---

## Estimated Impact

- **Lines removed:** ~700-900
- **New utility classes:** 4-5
- **Files modified:** 25-30
- **Test coverage:** Existing tests should pass after each stage
