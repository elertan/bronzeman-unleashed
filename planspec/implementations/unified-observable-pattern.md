---
date: 2026-01-18
design-spec: ../designs/unified-observable-pattern.md
status: ready
executor: planspec:impl-spec-executor
---

# Implementation: Unified Observable Pattern

> **Execute with:** `planspec:impl-spec-executor planspec/implementations/unified-observable-pattern.md`

## Overview

Create a unified `Observable<T>` class to replace the 6+ different observer patterns in the codebase, reducing ~50 lines of boilerplate per service to ~5 lines.

**Design spec:** [unified-observable-pattern.md](../designs/unified-observable-pattern.md)
**Security reviews:** None (internal infrastructure, no auth/secrets/user input)

## Prerequisites

- [ ] None - this is additive

## Codebase Conventions (Reference)

From existing code analysis:
- Package: `com.elertan.utils`
- Use Lombok `@Slf4j` for logging
- Use `final` on classes where appropriate
- Private constructor for utility classes
- Javadoc on public classes/methods (but avoid excessive documentation)
- Use `ConcurrentLinkedQueue` for thread-safe iteration

---

## Phase 1: Core Classes

### Task 1.1: Create Subscription interface

**Context:** Foundation interface that all subscriptions implement. Enables cleanup via `dispose()` and AutoCloseable support.

**Files:**
- Create: `src/main/java/com.elertan/utils/Subscription.java`

**Requirements:**
- Interface extending `AutoCloseable`
- `dispose()` method to remove subscription
- `isDisposed()` method to check state
- Default `close()` implementation calling `dispose()`

**Acceptance Criteria:**
- [ ] Interface compiles
- [ ] Extends AutoCloseable
- [ ] Has Javadoc on class and methods

---

### Task 1.2: Create CompositeSubscription class

**Context:** Groups multiple subscriptions for bulk disposal. Used in services that subscribe to multiple observables.

**Files:**
- Create: `src/main/java/com.elertan/utils/CompositeSubscription.java`

**Requirements:**
- Implements `Subscription`
- Uses `ConcurrentLinkedQueue<Subscription>` for thread-safe storage
- Uses `AtomicBoolean` for disposed state
- `add(S subscription)` returns the subscription for chaining
- `dispose()` disposes all children and clears the queue
- Double-dispose is safe (no-op)

**Acceptance Criteria:**
- [ ] Class compiles
- [ ] Implements Subscription interface
- [ ] Thread-safe via ConcurrentLinkedQueue and AtomicBoolean
- [ ] Has `@Slf4j` annotation

**Dependencies:** Task 1.1

---

### Task 1.3: Create Observable class

**Context:** The main class that replaces StateListenerManager + WaitUntilReadyContext + custom listener patterns.

**Files:**
- Create: `src/main/java/com.elertan/utils/Observable.java`

**Requirements:**
- Fields:
  - `ConcurrentLinkedQueue<BiConsumer<T, T>> listeners` - for thread-safe iteration
  - `String name` - for logging
  - `volatile T value` - current value
  - `AtomicBoolean hasBeenSet` - tracks ready state
- Constructors:
  - `Observable(String name)` - starts NotReady
  - `Observable(String name, T initialValue)` - starts Ready
- Methods:
  - `T get()` - return current value
  - `void set(T value)` - set value, notify listeners with (new, old), first call sets ready; skip if equal via Objects.equals
  - `boolean isReady()` - return hasBeenSet state
  - `CompletableFuture<T> waitUntilReady(@Nullable Duration timeout)` - complete immediately if ready, else subscribe and wait; timeout via ScheduledExecutorService
  - `Subscription subscribe(BiConsumer<T, T> listener)` - add to queue, return Subscription that removes from queue
  - `Subscription subscribe(Consumer<T> listener)` - wrap in BiConsumer, delegate
  - `Subscription subscribeImmediate(BiConsumer<T, T> listener)` - subscribe + invoke immediately if ready
  - `void clear()` - clear listeners, reset hasBeenSet to false
- Error handling:
  - Listener exceptions logged but don't break other listeners
  - Use try-catch in notification loop

**Code comments needed:**
- Why ConcurrentLinkedQueue (thread safety during iteration)
- Ready state transition logic in `set()`
- Timeout handling in `waitUntilReady()`

**Acceptance Criteria:**
- [ ] Class compiles
- [ ] Has `@Slf4j` annotation
- [ ] All public methods documented with Javadoc
- [ ] Thread-safe via ConcurrentLinkedQueue and AtomicBoolean
- [ ] `set()` skips notification when value equals previous
- [ ] `waitUntilReady()` completes immediately when already ready
- [ ] `waitUntilReady()` times out with TimeoutException
- [ ] Listener exceptions don't break notification to other listeners

**Dependencies:** Task 1.1, Task 1.2

---

### CHECKPOINT

**Gate:** All 3 classes compile, manual verification of basic functionality before proceeding to migration.

---

## Phase 2: Migrate RemoteStorageService

### Task 2.1: Migrate RemoteStorageService to Observable

**Context:** RemoteStorageService is the base dependency for all data providers. Must be migrated first.

**Files:**
- Modify: `src/main/java/com.elertan/remote/RemoteStorageService.java`
  - Replace `StateListenerManager<State>` with `Observable<State>`
  - Replace `addStateListener`/`removeStateListener` with `state()` accessor
  - Update `waitUntilReady()` to delegate to Observable
  - Keep existing `State` enum (Ready/NotReady) for now - value stored in Observable

**Requirements:**
- Expose `Observable<State> state()` method
- Deprecate or remove `addStateListener`/`removeStateListener` (check for callers first)
- `waitUntilReady()` becomes one-liner delegating to `state.waitUntilReady()`

**Acceptance Criteria:**
- [ ] RemoteStorageService compiles
- [ ] State changes still notify listeners
- [ ] `waitUntilReady()` still works with timeout
- [ ] All callers of `addStateListener`/`removeStateListener` updated or compile with deprecation

**Dependencies:** Phase 1

---

### Task 2.2: Update AbstractDataProvider for new RemoteStorageService API

**Context:** AbstractDataProvider subscribes to RemoteStorageService. Must update to use new Observable API.

**Files:**
- Modify: `src/main/java/com.elertan/data/AbstractDataProvider.java`
  - Replace `remoteStorageService.addStateListener()` with `remoteStorageService.state().subscribe()`
  - Store subscription for cleanup in `shutDown()`
  - Replace its own `StateListenerManager<State>` with `Observable<State>`
  - Update `waitUntilReady()` to delegate to Observable

**Requirements:**
- Use `CompositeSubscription` or single `Subscription` field for cleanup
- Expose `Observable<State> state()` method
- Update `setState()` to use `state.set()`

**Acceptance Criteria:**
- [ ] AbstractDataProvider compiles
- [ ] Subscriptions cleaned up in shutDown()
- [ ] State changes propagate correctly
- [ ] All data providers extending this class still work

**Dependencies:** Task 2.1

---

### Task 2.3: Migrate GameRulesDataProvider

**Context:** GameRulesDataProvider has its own `StateListenerManager<GameRules>` for game rules changes. Consumed by GameRulesService.

**Files:**
- Modify: `src/main/java/com.elertan/data/GameRulesDataProvider.java`
  - Replace `StateListenerManager<GameRules> gameRulesListeners` with `Observable<GameRules>`
  - Replace `addGameRulesListener`/`removeGameRulesListener` with `gameRules()` accessor
  - Update `setGameRules()` to use `observable.set()`

**Requirements:**
- Expose `Observable<GameRules> gameRules()` method
- Remove add/remove boilerplate methods
- Keep `getGameRules()` as convenience delegating to `gameRules.get()`

**Acceptance Criteria:**
- [ ] GameRulesDataProvider compiles
- [ ] Game rules changes still notify listeners
- [ ] Consumers (GameRulesService) updated to use new API

**Dependencies:** Task 2.2

---

### Task 2.4: Migrate LastEventDataProvider

**Context:** LastEventDataProvider has its own `StateListenerManager<BUEvent>` for event notifications. Consumed by BUEventService.

**Files:**
- Modify: `src/main/java/com.elertan/data/LastEventDataProvider.java`
  - Replace `StateListenerManager<BUEvent> eventListeners` with `Observable<BUEvent>`
  - Replace `addEventListener`/`removeEventListener` with `events()` accessor
  - Update notification in `onAdd` to use `observable.set()`

**Requirements:**
- Expose `Observable<BUEvent> events()` method
- Remove add/remove boilerplate methods
- Note: This is event-based (transient), not stateful value - Observable still works but semantically it's "last event"

**Acceptance Criteria:**
- [ ] LastEventDataProvider compiles
- [ ] Event notifications still work
- [ ] Consumers (BUEventService) updated to use new API

**Dependencies:** Task 2.2

---

### CHECKPOINT

**Gate:** RemoteStorageService, AbstractDataProvider, and data providers migrated. All data providers still function correctly. Test by running the plugin.

---

## Phase 3: Migrate Services

### Task 3.1: Migrate AccountConfigurationService

**Context:** AccountConfigurationService is used by many services. Has `currentAccountConfigurationChangeListeners` ConcurrentLinkedQueue.

**Files:**
- Modify: `src/main/java/com.elertan/AccountConfigurationService.java`
  - Replace `ConcurrentLinkedQueue<Consumer<AccountConfiguration>>` with `Observable<AccountConfiguration>`
  - Replace `addCurrentAccountConfigurationChangeListener`/`removeCurrentAccountConfigurationChangeListener` with `currentAccountConfiguration()` accessor
  - Update notification to use `observable.set()`

**Requirements:**
- Expose `Observable<AccountConfiguration> currentAccountConfiguration()` method
- Remove boilerplate add/remove/notify methods
- Keep `isReady()` method delegating to Observable

**Acceptance Criteria:**
- [ ] AccountConfigurationService compiles
- [ ] All callers updated to use new API
- [ ] Configuration changes still notify listeners

**Dependencies:** Phase 2

---

### Task 3.2: Update consumers of AccountConfigurationService

**Context:** Multiple services subscribe to AccountConfigurationService. Must update all to use new Observable API.

**Files:**
- Modify: `src/main/java/com.elertan/remote/RemoteStorageService.java`
- Modify: `src/main/java/com.elertan/MemberService.java`
- Modify: `src/main/java/com.elertan/BUChatService.java`
- Modify: `src/main/java/com.elertan/ItemUnlockService.java`
- Modify: `src/main/java/com.elertan/BUOverlayService.java`
- Modify: `src/main/java/com.elertan/BUPlugin.java`
- Modify: `src/main/java/com.elertan/panel/BUPanelViewModel.java`
- Modify: `src/main/java/com.elertan/BUPanelService.java`

**Requirements:**
- Replace `addCurrentAccountConfigurationChangeListener(listener)` with `currentAccountConfiguration().subscribe(listener)`
- Store subscriptions and dispose in `shutDown()` or `close()`
- Use `CompositeSubscription` if service has multiple subscriptions

**Acceptance Criteria:**
- [ ] All 8 modified files compile
- [ ] All subscriptions properly cleaned up in shutDown()/close()
- [ ] No references to old add/remove methods remain

**Dependencies:** Task 3.1

---

### Task 3.3: Migrate GameRulesService

**Context:** GameRulesService has custom `Listener` interface with `onGameRulesUpdate(new, old)`. Observable naturally supports this with BiConsumer. Also subscribes to GameRulesDataProvider (migrated in Task 2.3).

**Files:**
- Modify: `src/main/java/com.elertan/GameRulesService.java`
  - Replace `ConcurrentLinkedQueue<Listener>` with `Observable<GameRules>`
  - Remove custom `Listener` interface
  - Remove `State` enum (Observable has built-in ready state)
  - Replace `addListener`/`removeListener` with `gameRules()` accessor
  - Update `waitUntilGameRulesReady()` to delegate to Observable
  - Update subscription to `GameRulesDataProvider` to use new `gameRules().subscribe()` API

**Requirements:**
- Expose `Observable<GameRules> gameRules()` method
- Remove ~40 lines of boilerplate
- Keep `getGameRules()` as convenience delegating to `gameRules.get()`
- Store subscription to GameRulesDataProvider for cleanup

**Acceptance Criteria:**
- [ ] GameRulesService compiles
- [ ] Custom Listener interface removed
- [ ] State enum removed
- [ ] Subscription to GameRulesDataProvider properly cleaned up
- [ ] All callers updated to use new API

**Dependencies:** Task 2.3

---

### Task 3.4: Update consumers of GameRulesService

**Context:** Policies and other services subscribe to GameRulesService.

**Files:**
- Search for usages of `gameRulesService.addListener` and update all

**Requirements:**
- Replace `addListener(new Listener() { ... })` with `gameRules().subscribe((new, old) -> { ... })`
- Store subscriptions for cleanup

**Acceptance Criteria:**
- [ ] All consumers compile
- [ ] No references to old Listener interface remain

**Dependencies:** Task 3.3

---

### Task 3.5: Migrate BUEventService and its consumers

**Context:** BUEventService has `ConcurrentLinkedQueue<Consumer<BUEvent>>` for its own listeners. Also subscribes to LastEventDataProvider (migrated in Task 2.4). Has one consumer: ChatMessageEventBroadcaster.

**Files:**
- Modify: `src/main/java/com.elertan/BUEventService.java`
  - Replace queue with `Observable<BUEvent>`
  - Update subscription to `LastEventDataProvider` to use new `events().subscribe()` API
  - Note: Events are transient, not stateful - may need to reconsider if "last event" semantics make sense
- Modify: `src/main/java/com.elertan/chat/ChatMessageEventBroadcaster.java`
  - Update subscription to BUEventService to use new Observable API

**Requirements:**
- Expose `Observable<BUEvent> lastEvent()` method (or reconsider naming)
- Remove add/remove boilerplate
- Store subscription to LastEventDataProvider for cleanup
- Update ChatMessageEventBroadcaster to use new API

**Acceptance Criteria:**
- [ ] BUEventService compiles
- [ ] ChatMessageEventBroadcaster compiles
- [ ] Event notifications still work
- [ ] All subscriptions properly cleaned up

**Dependencies:** Task 2.4

---

### Task 3.6: Migrate BUChatService.isChatboxTransparent

**Context:** BUChatService has `StateListenerManager<Boolean> isChatboxTransparentListeners`. Simple migration.

**Files:**
- Modify: `src/main/java/com.elertan/BUChatService.java`
  - Replace `StateListenerManager<Boolean>` with `Observable<Boolean>`
  - Update accessor and waitUntilReady

**Requirements:**
- Expose `Observable<Boolean> isChatboxTransparent()` method

**Acceptance Criteria:**
- [ ] BUChatService compiles
- [ ] Chatbox transparency detection still works

**Dependencies:** Phase 2

---

### CHECKPOINT

**Gate:** All service migrations complete. Plugin runs correctly. All subscriptions cleaned up properly.

---

## Phase 4: Cleanup

### Task 4.1: Delete StateListenerManager

**Context:** No longer needed after migration.

**Files:**
- Delete: `src/main/java/com.elertan/utils/StateListenerManager.java`

**Requirements:**
- Verify no remaining usages via grep/search
- Delete file

**Acceptance Criteria:**
- [ ] No compile errors after deletion
- [ ] No references to StateListenerManager in codebase

**Dependencies:** Phase 3

---

### Task 4.2: Delete ListenerUtils

**Context:** `WaitUntilReadyContext` pattern no longer needed - Observable has built-in waitUntilReady.

**Files:**
- Delete: `src/main/java/com.elertan/utils/ListenerUtils.java`

**Requirements:**
- Verify no remaining usages via grep/search
- Delete file

**Acceptance Criteria:**
- [ ] No compile errors after deletion
- [ ] No references to ListenerUtils or WaitUntilReadyContext in codebase

**Dependencies:** Phase 3

---

### Task 4.3: Remove dead code in ItemUnlockService

**Context:** `newUnlockedItemListeners` is defined but never used (dead code discovered during design analysis).

**Files:**
- Modify: `src/main/java/com.elertan/ItemUnlockService.java`
  - Remove `newUnlockedItemListeners` field
  - Remove `addNewUnlockedItemListener` method
  - Remove `removeNewUnlockedItemListener` method
  - Remove notification loop that uses it

**Acceptance Criteria:**
- [ ] ItemUnlockService compiles
- [ ] ~15 lines of dead code removed

**Dependencies:** None (can be done anytime)

---

### CHECKPOINT

**Gate:** All cleanup complete. No references to old patterns. Codebase compiles.

---

## Completion Checklist

- [ ] All tasks completed
- [ ] Plugin runs and functions correctly
- [ ] All review checkpoints passed
- [ ] Design spec success criteria met:
  - [ ] Single `Observable<T>` primitive handles value + state + listeners + waitUntilReady
  - [ ] Notifications include both `newValue` and `oldValue`
  - [ ] Built-in ready state tracking (no separate State enum needed in most cases)
  - [ ] Built-in `waitUntilReady(Duration)` - no more WaitUntilReadyContext boilerplate
  - [ ] `Subscription` return for easy cleanup
  - [ ] `CompositeSubscription` for grouping multiple subscriptions
  - [ ] Thread-safe via `ConcurrentLinkedQueue`
  - [ ] Code comments for reviewer clarity where needed
