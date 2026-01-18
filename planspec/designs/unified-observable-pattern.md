---
date: 2026-01-18
status: approved
impl-spec: planspec:impl-spec
---

# Unified Observable Pattern

> **Next step:** `planspec:impl-spec planspec/designs/unified-observable-pattern.md`

## Problem

The codebase has 6+ different observer/listener patterns coexisting, leading to:

1. **Verbose boilerplate** - Each service needs ~50 lines for state management + listeners + waitUntilReady
2. **Inconsistent APIs** - `Consumer<T>`, custom `Listener` interfaces, `StateListenerManager`, etc.
3. **Manual lifecycle management** - Easy to forget listener removal, causing memory leaks
4. **Duplicated waitUntilReady pattern** - 25+ line anonymous `WaitUntilReadyContext` class in 6 places

### Current Patterns Inventory

| Pattern | Files | Lines per instance |
|---------|-------|-------------------|
| `ConcurrentLinkedQueue<Consumer<T>>` + add/remove/notify | 8 | ~15 |
| `State { NotReady, Ready }` enum + listeners | 6 | ~20 |
| `waitUntilReady(Duration)` via `WaitUntilReadyContext` | 6 | ~25 |
| `StateListenerManager<T>` wrapper | 3 | ~5 (but still needs waitUntilReady) |
| Custom multi-method listener interfaces | 5 | ~30 |

**Total boilerplate per service with state:** ~50-70 lines

## Success Criteria

**Must have:**
- Single `Observable<T>` primitive that handles value + state + listeners + waitUntilReady
- Notifications include both `newValue` and `oldValue`
- Built-in ready state tracking (no separate `State` enum needed)
- Built-in `waitUntilReady(Duration)` - no more `WaitUntilReadyContext` boilerplate
- `Subscription` return for easy cleanup
- `CompositeSubscription` for grouping multiple subscriptions
- Thread-safe via `ConcurrentLinkedQueue`
- Code comments for reviewer clarity (where logic isn't self-evident)

**Quality attributes:**
- Migration-friendly: can coexist with old patterns during incremental migration
- Familiar API: similar feel to existing `Property<T>` used in UI layer
- No external dependencies

**Not building:**
- `ObservableMap<K,V>` - consumers can diff old/new maps if needed
- Complex operators (map, filter, debounce)
- Automatic thread marshalling
- Storage port listener replacements (Firebase layer stays as-is)

## Approach

Create a unified `Observable<T>` class that encapsulates:
- Value storage with atomic ready state
- Listener management via `ConcurrentLinkedQueue`
- Subscription lifecycle management
- Built-in waitUntilReady with timeout support

### Alternatives Considered

| Alternative | Why Not |
|-------------|---------|
| RxJava/Reactor | Too heavyweight, steep learning curve, overkill for this use case |
| RuneLite EventBus for everything | Global bus, less typed, potential ordering issues |
| Extend StateListenerManager | Still wouldn't solve waitUntilReady boilerplate |
| Keep current patterns | Verbosity and inconsistency remain |

## Design

### Architecture Overview

```
src/main/java/com.elertan/utils/
├── Observable.java           // Main observable class (NEW)
├── Subscription.java         // Subscription interface (NEW)
├── CompositeSubscription.java // Subscription grouping (NEW)
├── StateListenerManager.java  // DELETE after migration
└── ListenerUtils.java         // DELETE after migration
```

**Integration:** Services expose `Observable<T>` fields. Consumers subscribe and receive `(newValue, oldValue)` on changes. Subscriptions are disposed on shutdown.

### Data Model

#### Observable<T>

```java
/**
 * Thread-safe observable value with built-in ready state.
 * Replaces the combination of: ConcurrentLinkedQueue + State enum +
 * StateListenerManager + WaitUntilReadyContext boilerplate.
 *
 * @param <T> the value type
 */
public final class Observable<T> {

    private final ConcurrentLinkedQueue<BiConsumer<T, T>> listeners;
    private final String name;  // For logging
    private volatile T value;
    private final AtomicBoolean hasBeenSet;  // Tracks ready state

    /** Creates observable without initial value. Starts in NotReady state. */
    public Observable(String name);

    /** Creates observable with initial value. Starts in Ready state. */
    public Observable(String name, T initialValue);

    /** Get current value (may be null even when ready) */
    public T get();

    /**
     * Set value and notify listeners.
     * First call transitions to Ready state.
     * Skips notification if value equals previous (using Objects.equals).
     */
    public void set(T value);

    /** Returns true after set() has been called at least once */
    public boolean isReady();

    /**
     * Returns a future that completes when ready.
     * If already ready, completes immediately with current value.
     * If timeout provided and exceeded, completes exceptionally with TimeoutException.
     */
    public CompletableFuture<T> waitUntilReady(@Nullable Duration timeout);

    /**
     * Subscribe to value changes.
     * Listener receives (newValue, oldValue) on each change.
     * oldValue is null on first notification after ready.
     */
    public Subscription subscribe(BiConsumer<T, T> listener);

    /** Subscribe to value changes (simple form). Listener receives only newValue. */
    public Subscription subscribe(Consumer<T> listener);

    /**
     * Subscribe and immediately invoke with current value if ready.
     * Useful for "get current state + subscribe to changes" pattern.
     */
    public Subscription subscribeImmediate(BiConsumer<T, T> listener);

    /** Clear all subscriptions and reset to NotReady state. Call during service shutdown. */
    public void clear();
}
```

#### Subscription

```java
/**
 * Handle to an active subscription.
 * Implements AutoCloseable for try-with-resources support.
 */
public interface Subscription extends AutoCloseable {

    /** Remove this subscription from its observable */
    void dispose();

    /** Check if already disposed */
    boolean isDisposed();

    @Override
    default void close() { dispose(); }
}
```

#### CompositeSubscription

```java
/**
 * Groups multiple subscriptions for bulk disposal.
 * Useful in services that subscribe to multiple observables.
 */
public final class CompositeSubscription implements Subscription {

    private final ConcurrentLinkedQueue<Subscription> subscriptions;
    private final AtomicBoolean disposed;

    /** Add a subscription to this group. Returns the subscription for chaining. */
    public <S extends Subscription> S add(S subscription);

    /** Dispose all subscriptions in this group */
    @Override
    public void dispose();

    @Override
    public boolean isDisposed();
}
```

### Behavior

#### Happy Path - Service Exposing Observable

```java
// Before: ~50 lines
public class GameRulesService {
    private final ConcurrentLinkedQueue<Listener> listeners = new ConcurrentLinkedQueue<>();
    private State state = State.NotReady;
    private GameRules gameRules;

    public void addListener(Listener l) { listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }
    public State getState() { return state; }
    public GameRules getGameRules() { return gameRules; }

    public CompletableFuture<Void> waitUntilGameRulesReady(Duration timeout) {
        return ListenerUtils.waitUntilReady(new ListenerUtils.WaitUntilReadyContext() {
            // ... 20 lines of boilerplate
        });
    }

    private void setGameRules(GameRules rules) {
        GameRules old = this.gameRules;
        this.gameRules = rules;
        state = rules != null ? State.Ready : State.NotReady;
        for (Listener l : listeners) {
            try { l.onGameRulesUpdate(rules, old); }
            catch (Exception e) { log.error("...", e); }
        }
    }

    public enum State { NotReady, Ready }
    public interface Listener { void onGameRulesUpdate(GameRules newRules, GameRules oldRules); }
}

// After: ~5 lines
public class GameRulesService {
    private final Observable<GameRules> gameRules = new Observable<>("GameRulesService.gameRules");

    public Observable<GameRules> gameRules() { return gameRules; }

    // Internal: called when data loads
    private void onDataLoaded(GameRules rules) {
        gameRules.set(rules);  // Notifies listeners with (new, old), sets ready
    }
}
```

#### Happy Path - Consumer Subscribing

```java
// Before
service.addListener(new GameRulesService.Listener() {
    @Override
    public void onGameRulesUpdate(GameRules newRules, GameRules oldRules) {
        // handle change
    }
});
service.waitUntilGameRulesReady(Duration.ofSeconds(5)).whenComplete((__, ex) -> {
    if (ex != null) { log.error("timeout", ex); return; }
    // use service.getGameRules()
});
// Must remember: service.removeListener(listener) in shutDown()

// After
Subscription sub = service.gameRules().subscribe((newRules, oldRules) -> {
    // handle change
});
service.gameRules().waitUntilReady(Duration.ofSeconds(5)).whenComplete((rules, ex) -> {
    if (ex != null) { log.error("timeout", ex); return; }
    // rules is the value, ready to use
});
// Cleanup: sub.dispose() or use CompositeSubscription
```

#### Happy Path - CompositeSubscription in Service

```java
public class MemberService implements BUPluginLifecycle {
    private final CompositeSubscription subscriptions = new CompositeSubscription();

    @Override
    public void startUp() {
        subscriptions.add(
            membersDataProvider.members().subscribe((newMap, oldMap) -> {
                // Diff maps if granular changes needed
            })
        );
        subscriptions.add(
            accountConfigService.currentConfig().subscribe(config -> {
                // handle config change
            })
        );
    }

    @Override
    public void shutDown() {
        subscriptions.dispose();  // Cleans up all at once
    }
}
```

#### Edge Cases

1. **set() with equal value** - No notification fired (uses `Objects.equals`)
2. **set(null)** - Valid, still transitions to Ready, listeners notified
3. **subscribe() when already ready** - Listener not immediately called (use `subscribeImmediate` for that)
4. **waitUntilReady() when already ready** - Completes immediately with current value
5. **waitUntilReady() timeout** - Completes exceptionally with `TimeoutException`, subscription auto-cleaned
6. **dispose() called twice** - Safe, second call is no-op
7. **clear() during notification** - Safe due to ConcurrentLinkedQueue iteration behavior

#### Error Handling

- Listener exceptions are caught and logged, don't break notification to other listeners
- `waitUntilReady` timeout results in `TimeoutException` in the CompletableFuture
- All public methods are null-safe where applicable

### State Transitions

```
Observable State Machine:

    ┌─────────────┐
    │  NotReady   │ ←── new Observable(name)
    └──────┬──────┘
           │ set(value)
           ▼
    ┌─────────────┐
    │    Ready    │ ←── new Observable(name, initialValue)
    └──────┬──────┘
           │ clear()
           ▼
    ┌─────────────┐
    │  NotReady   │
    └─────────────┘
```

### Testing Requirements

**Critical paths:**
- `set()` notifies all subscribers with correct old/new values
- `set()` transitions NotReady → Ready on first call
- `set()` with equal value does not notify
- `waitUntilReady()` completes immediately when already ready
- `waitUntilReady()` completes when `set()` called
- `waitUntilReady()` times out correctly
- `dispose()` removes subscription from observable
- `CompositeSubscription.dispose()` disposes all children

**Edge cases to cover:**
- Concurrent subscribe/dispose during notification
- Multiple waitUntilReady() calls on same observable
- set(null) behavior
- Listener throwing exception doesn't affect other listeners

**Integration points:**
- Verify Observable works with existing service lifecycle (startUp/shutDown)
- Verify subscriptions are properly cleaned up on service shutdown

## Migration Plan

### Phase 1: Add New Classes (Non-Breaking)

Create new files:
- `utils/Observable.java`
- `utils/Subscription.java`
- `utils/CompositeSubscription.java`

### Phase 2: Migrate Services (Incremental)

Migrate in order of dependency (leaf services first):

| Priority | Class | Current Pattern | Notes |
|----------|-------|-----------------|-------|
| 1 | `RemoteStorageService` | `StateListenerManager<State>` | Base dependency for data providers |
| 2 | `AbstractDataProvider` | `StateListenerManager<State>` + waitUntilReady | Base class for all data providers |
| 3 | `GameRulesDataProvider` | `StateListenerManager<GameRules>` | Extends AbstractDataProvider |
| 4 | `LastEventDataProvider` | `StateListenerManager<BUEvent>` | Extends AbstractDataProvider |
| 5 | `AccountConfigurationService` | `ConcurrentLinkedQueue<Consumer<AccountConfiguration>>` | Many dependents |
| 6 | `GameRulesService` | Custom `Listener` interface + State | Uses old/new in listener |
| 7 | `BUEventService` | `ConcurrentLinkedQueue<Consumer<BUEvent>>` | Simple consumer pattern |
| 8 | `BUChatService` | `StateListenerManager<Boolean>` | isChatboxTransparent |

### Phase 3: Update Consumers

Update classes that subscribe to migrated services:
- `MemberService` - subscribes to AccountConfigurationService, MembersDataProvider
- `BUChatService` - subscribes to AccountConfigurationService
- `ItemUnlockService` - subscribes to UnlockedItemsDataProvider, AccountConfigurationService
- Various ViewModels - subscribe to data providers

### Phase 4: Cleanup

After all migrations complete:
- Delete `utils/StateListenerManager.java`
- Delete `utils/ListenerUtils.java`
- Remove unused `ItemUnlockService.newUnlockedItemListeners` (dead code, never used)

### What Stays As-Is

| Pattern | Reason |
|---------|--------|
| `KeyValueStoragePort.Listener` etc. | Firebase SSE translation layer - different concern |
| `Property<T>` in UI layer | Already works well with PropertyChangeSupport |
| `BaseViewModel` listener tracking | Already has cleanup via `close()` |
| `MembersDataProvider.MemberMapListener` | Only 2 consumers, low ROI to change |
| `UnlockedItemsDataProvider.UnlockedItemsMapListener` | Only 2 consumers, low ROI to change |
| `GroundItemOwnedByDataProvider.Listener` | Only 1 consumer (empty impl), low ROI |

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Subtle behavior differences during migration | Medium | Medium | Migrate one service at a time, test thoroughly |
| Forgotten subscription disposal | Low | Low | CompositeSubscription makes bulk cleanup easy |
| Thread safety regression | Low | High | Use ConcurrentLinkedQueue, same as current impl |

## Dependencies

**Blocking:** None - this is additive

**Non-blocking:** None

## Open Questions

None - all questions resolved during design.

## Implementation Notes

### Code Comments

Add comments for reviewer clarity in these areas:
- `waitUntilReady()` timeout handling logic
- Why `ConcurrentLinkedQueue` is used (thread safety during iteration)
- The ready state transition logic in `set()`
- Subscription disposal edge cases

### File Locations

```
src/main/java/com.elertan/utils/
├── Observable.java           // ~120 lines
├── Subscription.java         // ~15 lines
├── CompositeSubscription.java // ~40 lines
```

### Estimated Impact

- **New code:** ~175 lines across 3 files
- **Deleted code:** ~100 lines (StateListenerManager + ListenerUtils)
- **Modified code:** ~300 lines across ~10 services (replacing boilerplate)
- **Net reduction:** ~200+ lines of boilerplate
