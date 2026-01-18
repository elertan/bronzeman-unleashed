# Utilities

Helper classes for common operations.

## Files

| File | Description |
|------|-------------|
| `utils/AsyncUtils.java` | Async/threading utilities |
| `utils/JsonUtils.java` | JSON serialization helpers |
| `utils/Observable.java` | Thread-safe observable value with subscriptions |
| `utils/CompositeSubscription.java` | Manages multiple subscriptions |
| `utils/Subscription.java` | Subscription interface |
| `utils/OffsetDateTimeUtils.java` | Date/time utilities |
| `utils/TextUtils.java` | String utilities |
| `utils/TickUtils.java` | Game tick utilities |
| `resource/BUImageUtil.java` | Image loading utilities |
| `gson/AccountHashJsonAdapter.java` | Gson adapter for account hashes |

## Key Utilities

### Observable

Thread-safe observable value with built-in subscriptions and ready state:

```java
// Create observable without initial value (starts NotReady)
Observable<BUEvent> events = Observable.empty();

// Create observable with initial value (starts Ready)
Observable<State> state = Observable.of(State.NotReady);

// Subscribe to changes (receives new and old value)
Subscription sub = state.subscribe((newVal, oldVal) -> handleChange(newVal));

// Subscribe with only new value
Subscription sub = state.subscribe(newVal -> handleChange(newVal));

// Update value (notifies subscribers, first call sets ready)
state.set(State.Ready);

// Wait until ready with timeout
CompletableFuture<State> future = state.await(Duration.ofSeconds(10));

// Cleanup
sub.dispose();
```

### CompositeSubscription

Manage multiple subscriptions:

```java
CompositeSubscription subs = new CompositeSubscription();
subs.add(observable1.subscribe(...));
subs.add(observable2.subscribe(...));
// Later: dispose all at once
subs.dispose();
```

### BUImageUtil

Load plugin images:

```java
BufferedImage icon = BUImageUtil.loadImage("icon.png");
```
