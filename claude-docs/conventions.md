# Conventions & Patterns

## Package Structure

```
com.elertan/
├── BUPlugin.java              # Main plugin
├── BUPluginConfig.java        # Config interface
├── BUPluginLifecycle.java     # Lifecycle interface
├── *Service.java              # Domain services
├── chat/                      # Chat message parsing
├── data/                      # Data providers
├── event/                     # Event types
├── gson/                      # JSON adapters
├── models/                    # Data models
├── overlays/                  # In-game overlays
├── panel/                     # Side panel UI
│   ├── screens/               # Screen views/viewmodels
│   └── components/            # Reusable components
├── policies/                  # Game rule enforcement
├── remote/                    # Remote storage abstractions
│   └── firebase/              # Firebase implementations
├── resource/                  # Resource utilities
├── ui/                        # UI framework (Property, Bindings)
└── utils/                     # Utility classes
```

## Naming Conventions

| Type | Pattern | Example |
|------|---------|---------|
| Service | `*Service` | `ItemUnlockService` |
| Policy | `*Policy` | `GrandExchangePolicy` |
| Data Provider | `*DataProvider` | `UnlockedItemsDataProvider` |
| ViewModel | `*ViewModel` | `MainScreenViewModel` |
| Storage Port | `*StoragePort` | `KeyValueStoragePort` |
| Firebase Adapter | `*Firebase*StorageAdapter` | `MembersFirebaseKeyValueStorageAdapter` |
| Event | `*BUEvent` | `PetDropBUEvent` |
| Parsed Message | `*ParsedGameMessage` | `QuestCompletionParsedGameMessage` |

## Lifecycle Pattern

All services/policies/data-providers implement `BUPluginLifecycle`:

```java
public interface BUPluginLifecycle {
    void startUp() throws Exception;
    void shutDown() throws Exception;
}
```

Registration in `BUPlugin.initLifecycleDependencies()`:
```java
lifecycleDependencies.add(myService);
```

Order matters - components are started in order, shut down in reverse.

## Policy Pattern

Policies extend `PolicyBase`:

```java
public class MyPolicy extends PolicyBase {
    @Inject
    public MyPolicy(AccountConfigurationService acs,
                    GameRulesService grs,
                    PolicyService ps) {
        super(acs, grs, ps);
    }

    public void onSomeEvent(SomeEvent event) {
        PolicyContext ctx = createContext();
        if (!ctx.shouldApplyForRules(r -> r.isMyRuleEnabled())) {
            return;
        }
        // Block/modify event
    }
}
```

## Data Provider Pattern

Data providers extend `AbstractDataProvider`:

```java
public class MyDataProvider extends AbstractDataProvider {
    @Inject private RemoteStorageService rss;
    private KeyValueStoragePort<K, V> storagePort;

    public MyDataProvider() { super("MyDataProvider"); }

    @Override protected RemoteStorageService getRemoteStorageService() { return rss; }

    @Override protected void onRemoteStorageReady() {
        storagePort = rss.createKeyValueStoragePort(...);
        // Load data
        setState(State.Ready);
    }

    @Override protected void onRemoteStorageNotReady() {
        storagePort = null;
        // Clear data
    }
}
```

## MVVM Pattern (Panel)

### View
```java
public class MyScreen extends JPanel {
    private final MyScreenViewModel viewModel;

    public MyScreen(MyScreenViewModel viewModel) {
        this.viewModel = viewModel;
        // Bind UI to viewModel properties
        Bindings.bindText(label, viewModel.getText());
    }
}
```

### ViewModel
```java
public class MyScreenViewModel extends BaseViewModel {
    private final Property<String> text = new Property<>("initial");

    public Property<String> getText() { return text; }

    public void doSomething() {
        text.set("updated");
    }
}
```

## Storage Port Pattern

Interface-based abstraction for storage:

```java
// Port interface
public interface KeyValueStoragePort<K, V> {
    CompletableFuture<V> read(K key);
    CompletableFuture<Map<K, V>> readAll();
    CompletableFuture<Void> update(K key, V value);
    void addListener(Listener<K, V> listener);
}

// Firebase adapter implements port
public class MyFirebaseAdapter extends FirebaseKeyValueStorageAdapterBase<K, V> {
    // Override abstract methods
}
```

## Event Pattern

Events extend `BUEvent`:

```java
public class MyBUEvent extends BUEvent {
    @Getter private final MyData data;

    public MyBUEvent(long accountHash, ISOOffsetDateTime ts, MyData data) {
        super(accountHash, ts);
        this.data = data;
    }

    @Override public BUEventType getType() {
        return BUEventType.MY_EVENT;
    }
}
```

## Chat Parsing Pattern

Parsed messages extend `ParsedGameMessage`:

```java
public class MyParsedGameMessage extends ParsedGameMessage {
    @Getter private final String extractedData;

    public MyParsedGameMessage(String extractedData) {
        this.extractedData = extractedData;
    }

    @Override public ParsedGameMessageType getType() {
        return ParsedGameMessageType.MY_TYPE;
    }
}
```

## Async Patterns

- Use `CompletableFuture<T>` for async operations
- Use `await(Duration timeout)` to wait for initialization
- Run UI updates on EDT via `SwingUtilities.invokeLater()`
- Run client operations on client thread via `clientThread.invoke()`

## Dependency Injection

Uses Guice (via RuneLite):

```java
public class MyService {
    @Inject private Client client;
    @Inject private SomeOtherService other;
}
```

## Logging

Use Lombok `@Slf4j`:

```java
@Slf4j
public class MyClass {
    void method() {
        log.debug("message with {}", value);
    }
}
```
