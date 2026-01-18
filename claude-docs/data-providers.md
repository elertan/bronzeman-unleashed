# Data Providers

Manage data state with Firebase synchronization. Bridge between services and remote storage.

## Base Class

All data providers extend `AbstractDataProvider`:

```java
public abstract class AbstractDataProvider implements BUPluginLifecycle {
    // State management
    State getState();  // NotReady, Ready
    void addStateListener(Consumer<State> listener);
    CompletableFuture<Void> await(Duration timeout);

    // Subclass overrides
    protected abstract RemoteStorageService getRemoteStorageService();
    protected abstract void onRemoteStorageReady();
    protected abstract void onRemoteStorageNotReady();
}
```

## Data Providers

| Provider | File | Data | Storage Port Type |
|----------|------|------|-------------------|
| `UnlockedItemsDataProvider` | `data/UnlockedItemsDataProvider.java` | Item unlocks | `KeyValueStoragePort<String, UnlockedItem>` |
| `MembersDataProvider` | `data/MembersDataProvider.java` | Group members | `KeyValueStoragePort<String, Member>` |
| `GameRulesDataProvider` | `data/GameRulesDataProvider.java` | Game rules | `ObjectStoragePort<GameRules>` |
| `LastEventDataProvider` | `data/LastEventDataProvider.java` | Recent events | `ObjectListStoragePort<BUEvent>` |
| `GroundItemOwnedByDataProvider` | `data/GroundItemOwnedByDataProvider.java` | Ground item ownership | `KeyListStoragePort<GroundItemOwnedByKey>` |

## Lifecycle

1. `startUp()` - Register listener on `RemoteStorageService`
2. When `RemoteStorageService` becomes Ready:
   - `onRemoteStorageReady()` called
   - Create storage port from `RemoteStorageService`
   - Load initial data
   - Set state to Ready
3. When `RemoteStorageService` becomes NotReady:
   - `onRemoteStorageNotReady()` called
   - Clear storage port and cached data
4. `shutDown()` - Unregister listener, cleanup

## Usage Pattern

```java
// Wait for data to be ready
dataProvider.await(Duration.ofSeconds(10))
    .thenAccept(v -> {
        // Data is now available
        Map<String, UnlockedItem> items = dataProvider.getUnlockedItems();
    });

// Or check state
if (dataProvider.getState() == AbstractDataProvider.State.Ready) {
    // Safe to access data
}

// Listen for state changes
dataProvider.addStateListener(state -> {
    if (state == AbstractDataProvider.State.Ready) {
        refreshUI();
    }
});
```
