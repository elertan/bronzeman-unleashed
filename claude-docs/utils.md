# Utilities

Helper classes for common operations.

## Files

| File | Description |
|------|-------------|
| `utils/AsyncUtils.java` | Async/threading utilities |
| `utils/JsonUtils.java` | JSON serialization helpers |
| `utils/ListenerUtils.java` | Listener pattern helpers |
| `utils/StateListenerManager.java` | Manages state listeners with notifications |
| `utils/OffsetDateTimeUtils.java` | Date/time utilities |
| `utils/TextUtils.java` | String utilities |
| `utils/TickUtils.java` | Game tick utilities |
| `resource/BUImageUtil.java` | Image loading utilities |
| `gson/AccountHashJsonAdapter.java` | Gson adapter for account hashes |

## Key Utilities

### ListenerUtils

Wait for async state:

```java
CompletableFuture<Void> future = ListenerUtils.waitUntilReady(
    new WaitUntilReadyContext() {
        boolean isReady() { return service.isReady(); }
        void addListener(Runnable notify) { service.addListener(notify); }
        void removeListener() { service.removeListener(); }
        Duration getTimeout() { return Duration.ofSeconds(10); }
    }
);
```

### StateListenerManager

Manage listeners with thread-safe notifications:

```java
StateListenerManager<State> listeners = new StateListenerManager<>("MyService");
listeners.addListener(state -> handleStateChange(state));
listeners.notifyListeners(State.Ready);
```

### BUImageUtil

Load plugin images:

```java
BufferedImage icon = BUImageUtil.loadImage("icon.png");
```
