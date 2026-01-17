# Adding Firebase Storage

Add new data path to Firebase Realtime Database.

## Steps

### 1. Choose Storage Pattern

| Pattern | Interface | Use Case |
|---------|-----------|----------|
| Key-Value | `KeyValueStoragePort<K, V>` | Maps with unique keys (members, items) |
| Single Object | `ObjectStoragePort<T>` | Single config object |
| Key List | `KeyListStoragePort<K>` | Set of unique keys |
| Object List | `ObjectListStoragePort<T>` | List of objects (events) |

### 2. Create Model (if new)

Location: `src/main/java/com.elertan/models/MyModel.java`

```java
package com.elertan.models;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MyModel {
    private String field1;
    private int field2;
}
```

### 3. Create Storage Adapter

Location: `src/main/java/com.elertan/remote/firebase/storageAdapters/MyFirebaseAdapter.java`

Example for KeyValue:

```java
package com.elertan.remote.firebase.storageAdapters;

import com.elertan.models.MyModel;
import com.elertan.remote.firebase.FirebaseKeyValueStorageAdapterBase;
import com.elertan.remote.firebase.FirebaseRealtimeDatabase;
import com.google.gson.Gson;

public class MyFirebaseAdapter
    extends FirebaseKeyValueStorageAdapterBase<String, MyModel> {

    public MyFirebaseAdapter(
        FirebaseRealtimeDatabase db,
        Gson gson,
        String path
    ) {
        super(db, gson, path, String.class, MyModel.class);
    }

    @Override
    protected String serializeKey(String key) {
        return key;  // Or transform for Firebase-safe format
    }

    @Override
    protected String deserializeKey(String key) {
        return key;
    }
}
```

### 4. Create Data Provider

See [add-data-provider.md](add-data-provider.md)

### 5. Firebase Path Structure

Data stored at: `{firebase-url}/{path}/`

Example paths:
- `members/` - Group members
- `unlockedItems/` - Item unlocks
- `gameRules` - Single game rules object
- `lastEvent/` - Recent events

## Firebase Key Constraints

Firebase keys cannot contain:
- `.` (period)
- `$` (dollar sign)
- `#` (hash)
- `[` `]` (brackets)
- `/` (forward slash)

Use serialization in adapter if keys may contain these:

```java
@Override
protected String serializeKey(String key) {
    return key.replace(".", "_DOT_");
}

@Override
protected String deserializeKey(String key) {
    return key.replace("_DOT_", ".");
}
```

## SSE Events

Firebase sends these event types:
- `put` - Full data or update at path
- `patch` - Partial update
- `keep-alive` - Connection keepalive

Handled automatically by adapter base classes.

## Checklist

- [ ] Model class created (if new data type)
- [ ] Adapter extends appropriate base class
- [ ] Key serialization handles Firebase constraints
- [ ] Data provider created and registered
- [ ] Firebase path documented
