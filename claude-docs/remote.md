# Remote Storage

Firebase Realtime Database integration via port/adapter pattern.

## Architecture

```
DataProvider
    ↓
StoragePort (interface)
    ↓
FirebaseAdapter (implementation)
    ↓
Firebase Realtime Database (SSE streaming)
```

## Storage Port Interfaces

| Interface | File | Use Case |
|-----------|------|----------|
| `KeyValueStoragePort<K,V>` | `remote/KeyValueStoragePort.java` | Key-value maps (members, unlocked items) |
| `ObjectStoragePort<T>` | `remote/ObjectStoragePort.java` | Single objects (game rules) |
| `KeyListStoragePort<K>` | `remote/KeyListStoragePort.java` | Sets of keys (ground item ownership) |
| `ObjectListStoragePort<T>` | `remote/ObjectListStoragePort.java` | Lists of objects (events) |

### KeyValueStoragePort

```java
public interface KeyValueStoragePort<K, V> extends AutoCloseable {
    CompletableFuture<V> read(K key);
    CompletableFuture<Map<K, V>> readAll();
    CompletableFuture<Void> update(K key, V value);
    CompletableFuture<Void> delete(K key);
    void addListener(Listener<K, V> listener);
}
```

## Firebase Adapter Base Classes

| Base Class | File | Implements |
|------------|------|------------|
| `FirebaseKeyValueStorageAdapterBase` | `remote/firebase/FirebaseKeyValueStorageAdapterBase.java` | `KeyValueStoragePort` |
| `FirebaseObjectStorageAdapterBase` | `remote/firebase/FirebaseObjectStorageAdapterBase.java` | `ObjectStoragePort` |
| `FirebaseKeyListStorageAdapterBase` | `remote/firebase/FirebaseKeyListStorageAdapterBase.java` | `KeyListStoragePort` |
| `FirebaseObjectListStorageAdapterBase` | `remote/firebase/FirebaseObjectListStorageAdapterBase.java` | `ObjectListStoragePort` |

## Concrete Adapters

| Adapter | File | Port Type |
|---------|------|-----------|
| `UnlockedItemsFirebaseKeyValueStorageAdapter` | `remote/firebase/storageAdapters/...` | KeyValue |
| `MembersFirebaseKeyValueStorageAdapter` | `remote/firebase/storageAdapters/...` | KeyValue |
| `GameRulesFirebaseObjectStorageAdapter` | `remote/firebase/storageAdapters/...` | Object |
| `LastEventFirebaseObjectListStorageAdapter` | `remote/firebase/storageAdapters/...` | ObjectList |
| `GroundItemOwnedByKeyListStorageAdapter` | `remote/firebase/storageAdapters/...` | KeyList |

## Firebase SSE

Real-time updates via Server-Sent Events:

| Class | File | Description |
|-------|------|-------------|
| `FirebaseSSE` | `remote/firebase/FirebaseSSE.java` | SSE event data |
| `FirebaseSSEType` | `remote/firebase/FirebaseSSEType.java` | Event type enum (put, patch, keep-alive) |
| `FirebaseSSEStream` | `remote/firebase/FirebaseSSEStream.java` | OkHttp SSE stream handler |
| `FirebaseRealtimeDatabase` | `remote/firebase/FirebaseRealtimeDatabase.java` | HTTP client for Firebase REST API |

## RemoteStorageService

Central service managing Firebase connection:

```java
// Create storage ports
KeyValueStoragePort<K,V> port = remoteStorageService.createKeyValueStoragePort(
    path, keyClass, valueClass, keySerializer, keyDeserializer
);

// State
remoteStorageService.getState();  // NotReady, Ready
remoteStorageService.addStateListener(listener);
```
