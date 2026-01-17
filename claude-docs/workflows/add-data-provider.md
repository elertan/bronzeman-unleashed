# Adding a Data Provider

Data providers manage state with Firebase synchronization.

## Steps

### 1. Create Storage Adapter

Location: `src/main/java/com.elertan/remote/firebase/storageAdapters/MyFirebaseStorageAdapter.java`

Choose base class based on data structure:
- `FirebaseKeyValueStorageAdapterBase<K, V>` - Map of key-value pairs
- `FirebaseObjectStorageAdapterBase<T>` - Single object
- `FirebaseKeyListStorageAdapterBase<K>` - Set of keys
- `FirebaseObjectListStorageAdapterBase<T>` - List of objects

Example (KeyValue):

```java
package com.elertan.remote.firebase.storageAdapters;

import com.elertan.models.MyData;
import com.elertan.remote.firebase.FirebaseKeyValueStorageAdapterBase;
import com.elertan.remote.firebase.FirebaseRealtimeDatabase;
import com.google.gson.Gson;

public class MyFirebaseKeyValueStorageAdapter
    extends FirebaseKeyValueStorageAdapterBase<String, MyData> {

    public MyFirebaseKeyValueStorageAdapter(
        FirebaseRealtimeDatabase db,
        Gson gson,
        String path
    ) {
        super(db, gson, path, String.class, MyData.class);
    }

    @Override
    protected String serializeKey(String key) {
        return key;
    }

    @Override
    protected String deserializeKey(String key) {
        return key;
    }
}
```

### 2. Create Data Provider

Location: `src/main/java/com.elertan/data/MyDataProvider.java`

```java
package com.elertan.data;

import com.elertan.models.MyData;
import com.elertan.remote.KeyValueStoragePort;
import com.elertan.remote.RemoteStorageService;
import com.elertan.remote.firebase.storageAdapters.MyFirebaseKeyValueStorageAdapter;
import com.google.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class MyDataProvider extends AbstractDataProvider {

    @Inject
    private RemoteStorageService remoteStorageService;

    private KeyValueStoragePort<String, MyData> storagePort;

    @Getter
    private final Map<String, MyData> data = new ConcurrentHashMap<>();

    public MyDataProvider() {
        super("MyDataProvider");
    }

    @Override
    protected RemoteStorageService getRemoteStorageService() {
        return remoteStorageService;
    }

    @Override
    protected void onRemoteStorageReady() {
        storagePort = new MyFirebaseKeyValueStorageAdapter(
            remoteStorageService.getDatabase(),
            remoteStorageService.getGson(),
            "mydata"
        );

        storagePort.addListener(new KeyValueStoragePort.Listener<>() {
            @Override
            public void onFullUpdate(Map<String, MyData> map) {
                data.clear();
                data.putAll(map);
                setState(State.Ready);
            }

            @Override
            public void onUpdate(String key, MyData value) {
                data.put(key, value);
            }

            @Override
            public void onDelete(String key) {
                data.remove(key);
            }
        });

        storagePort.readAll();
    }

    @Override
    protected void onRemoteStorageNotReady() {
        if (storagePort != null) {
            try {
                storagePort.close();
            } catch (Exception e) {
                log.error("Error closing storage port", e);
            }
            storagePort = null;
        }
        data.clear();
    }

    // Public API
    public MyData get(String key) {
        return data.get(key);
    }

    public void update(String key, MyData value) {
        if (storagePort != null) {
            storagePort.update(key, value);
        }
    }
}
```

### 3. Register in BUPlugin

Edit `BUPlugin.java`:

```java
// Add injection
@Inject
private MyDataProvider myDataProvider;

// Add to lifecycle (data providers section)
lifecycleDependencies.add(myDataProvider);
```

## Checklist

- [ ] Storage adapter created (extends appropriate base)
- [ ] Data provider extends `AbstractDataProvider`
- [ ] Implements `getRemoteStorageService()`
- [ ] Implements `onRemoteStorageReady()` - creates port, loads data, sets Ready
- [ ] Implements `onRemoteStorageNotReady()` - closes port, clears data
- [ ] Registered in `BUPlugin.lifecycleDependencies`
- [ ] Public API methods for accessing/updating data
