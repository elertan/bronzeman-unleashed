package com.elertan.remote.firebase;

import com.elertan.remote.KeyListStoragePort;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FirebaseKeyListStorageAdapterBase<K, V> implements KeyListStoragePort<K, V> {

    private final String basePath;
    private final FirebaseRealtimeDatabase db;
    private final Gson gson;
    private final ConcurrentLinkedQueue<Listener<K, V>> listeners = new ConcurrentLinkedQueue<>();
    private final Function<String, K> stringToKeyTransformer;
    private final Function<K, String> keyToStringTransformer;
    private final Function<JsonElement, V> deserializeFromJsonElement;

    private final ConcurrentHashMap<K, ConcurrentHashMap<String, V>> localCache = new ConcurrentHashMap<>();

    private final Consumer<FirebaseSSE> sseListener = this::sseListener;

    public FirebaseKeyListStorageAdapterBase(
        String basePath,
        FirebaseRealtimeDatabase db,
        Gson gson,
        Function<String, K> stringToKeyTransformer,
        Function<K, String> keyToStringTransformer,
        Function<JsonElement, V> deserializeFromJsonElement
    ) {
        if (basePath == null) {
            throw new IllegalArgumentException("basePath must not be null");
        }
        if (!basePath.startsWith("/")) {
            throw new IllegalArgumentException("basePath must start with '/'");
        }
        int lastIndexOfForwardSlash = basePath.lastIndexOf("/");
        if (lastIndexOfForwardSlash != 0) {
            throw new IllegalArgumentException("basePath must only be a starting resource");
        }
        this.basePath = basePath;
        this.db = db;
        this.gson = gson;
        this.stringToKeyTransformer = stringToKeyTransformer;
        this.keyToStringTransformer = keyToStringTransformer;
        this.deserializeFromJsonElement = deserializeFromJsonElement;

        FirebaseSSEStream stream = db.getStream();
        stream.addServerSentEventListener(sseListener);
    }

    @Override
    public void close() throws Exception {
        listeners.clear();
        localCache.clear();

        FirebaseSSEStream stream = db.getStream();
        stream.removeServerSentEventListener(sseListener);
    }

    @Override
    public CompletableFuture<Map<String, V>> read(K key) {
        String path = basePath + "/" + keyToStringTransformer.apply(key);
        return db.get(path)
            .thenApply(jsonElement -> {
                if (jsonElement == null || jsonElement.isJsonNull()) {
                    return Collections.emptyMap();
                }
                JsonObject obj = jsonElement.getAsJsonObject();
                Map<String, V> map = new HashMap<>();
                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    V value = deserializeFromJsonElement.apply(entry.getValue());
                    if (value != null) {
                        map.put(entry.getKey(), value);
                    }
                }
                return map;
            });
    }

    @Override
    public CompletableFuture<Map<K, Map<String, V>>> readAll() {
        return db.get(basePath)
            .thenApply(jsonElement -> {
                if (jsonElement == null || jsonElement.isJsonNull()) {
                    return Collections.emptyMap();
                }

                JsonObject obj = jsonElement.getAsJsonObject();
                Map<K, Map<String, V>> map = new HashMap<>();

                for (Map.Entry<String, JsonElement> keyEntry : obj.entrySet()) {
                    K key = stringToKeyTransformer.apply(keyEntry.getKey());
                    JsonElement keyValue = keyEntry.getValue();
                    if (keyValue == null || keyValue.isJsonNull() || !keyValue.isJsonObject()) {
                        continue;
                    }

                    Map<String, V> innerMap = new HashMap<>();
                    JsonObject innerObj = keyValue.getAsJsonObject();
                    for (Map.Entry<String, JsonElement> entryEntry : innerObj.entrySet()) {
                        V value = deserializeFromJsonElement.apply(entryEntry.getValue());
                        if (value != null) {
                            innerMap.put(entryEntry.getKey(), value);
                        }
                    }
                    if (!innerMap.isEmpty()) {
                        map.put(key, innerMap);
                    }
                }

                return map;
            });
    }

    @Override
    public CompletableFuture<String> add(K key, V value) {
        String path = basePath + "/" + keyToStringTransformer.apply(key);
        JsonElement jsonElement = gson.toJsonTree(value);
        return db.post(path, jsonElement)
            .thenApply(response -> {
                if (response != null && response.isJsonObject()) {
                    JsonObject obj = response.getAsJsonObject();
                    if (obj.has("name")) {
                        return obj.get("name").getAsString();
                    }
                }
                return null;
            });
    }

    @Override
    public CompletableFuture<Void> removeOne(K key) {
        ConcurrentHashMap<String, V> innerMap = localCache.get(key);
        if (innerMap == null || innerMap.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        Iterator<String> iterator = innerMap.keySet().iterator();
        if (!iterator.hasNext()) {
            return CompletableFuture.completedFuture(null);
        }
        String entryKey = iterator.next();

        return remove(key, entryKey);
    }

    @Override
    public CompletableFuture<Void> remove(K key, String entryKey) {
        String path = basePath + "/" + keyToStringTransformer.apply(key) + "/" + entryKey;
        return db.delete(path);
    }

    @Override
    public void addListener(Listener<K, V> listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(Listener<K, V> listener) {
        listeners.remove(listener);
    }

    public ConcurrentHashMap<K, ConcurrentHashMap<String, V>> getLocalCache() {
        return localCache;
    }

    private void sseListener(FirebaseSSE event) {
        FirebaseSSEType type = event.getType();
        if (type != FirebaseSSEType.Put) {
            return;
        }

        String path = event.getPath();
        if (!path.startsWith(basePath)) {
            return;
        }
        String[] pathParts = Arrays.stream(path.split("/"))
            .filter(part -> !part.isEmpty())
            .toArray(String[]::new);
        int pathPartsLength = pathParts.length;

        if (pathPartsLength == 1) {
            // Full update of entire collection
            handleFullUpdate(event.getData());
        } else if (pathPartsLength == 2) {
            // Full list for a specific key
            String strKey = pathParts[1];
            K key = stringToKeyTransformer.apply(strKey);
            handleKeyFullUpdate(key, event.getData());
        } else if (pathPartsLength == 3) {
            // Single entry add/remove
            String strKey = pathParts[1];
            String entryKey = pathParts[2];
            K key = stringToKeyTransformer.apply(strKey);
            handleEntryUpdate(key, entryKey, event.getData());
        } else {
            log.info(
                "FirebaseKeyListStorageAdapterBase ({}): too many path parts, ignoring: {}",
                basePath,
                path
            );
        }
    }

    private void handleFullUpdate(JsonElement jsonElement) {
        localCache.clear();
        Map<K, Map<String, V>> fullMap = new HashMap<>();

        if (jsonElement != null && !jsonElement.isJsonNull() && jsonElement.isJsonObject()) {
            JsonObject obj = jsonElement.getAsJsonObject();
            for (Map.Entry<String, JsonElement> keyEntry : obj.entrySet()) {
                K key;
                try {
                    key = stringToKeyTransformer.apply(keyEntry.getKey());
                } catch (Exception e) {
                    log.error("Failed to parse key: {}", keyEntry.getKey(), e);
                    continue;
                }

                JsonElement keyValue = keyEntry.getValue();
                if (keyValue == null || keyValue.isJsonNull() || !keyValue.isJsonObject()) {
                    continue;
                }

                ConcurrentHashMap<String, V> innerMap = new ConcurrentHashMap<>();
                JsonObject innerObj = keyValue.getAsJsonObject();

                for (Map.Entry<String, JsonElement> entryEntry : innerObj.entrySet()) {
                    String entryKey = entryEntry.getKey();
                    V value;
                    try {
                        value = deserializeFromJsonElement.apply(entryEntry.getValue());
                    } catch (Exception e) {
                        log.error("Failed to deserialize value for entry {}/{}", keyEntry.getKey(), entryKey, e);
                        continue;
                    }
                    if (value != null) {
                        innerMap.put(entryKey, value);
                    }
                }

                if (!innerMap.isEmpty()) {
                    localCache.put(key, innerMap);
                    fullMap.put(key, new HashMap<>(innerMap));
                }
            }
        }

        notifyListenersOnFullUpdate(fullMap);
    }

    private void handleKeyFullUpdate(K key, JsonElement jsonElement) {
        localCache.remove(key);

        if (jsonElement == null || jsonElement.isJsonNull()) {
            return;
        }

        if (!jsonElement.isJsonObject()) {
            return;
        }

        ConcurrentHashMap<String, V> innerMap = new ConcurrentHashMap<>();
        JsonObject obj = jsonElement.getAsJsonObject();

        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            String entryKey = entry.getKey();
            V value;
            try {
                value = deserializeFromJsonElement.apply(entry.getValue());
            } catch (Exception e) {
                log.error("Failed to deserialize value for entry {}", entryKey, e);
                continue;
            }
            if (value != null) {
                innerMap.put(entryKey, value);
                notifyListenersOnAdd(key, entryKey, value);
            }
        }

        if (!innerMap.isEmpty()) {
            localCache.put(key, innerMap);
        }
    }

    private void handleEntryUpdate(K key, String entryKey, JsonElement jsonElement) {
        if (jsonElement == null || jsonElement.isJsonNull()) {
            // Entry removed
            ConcurrentHashMap<String, V> innerMap = localCache.get(key);
            if (innerMap != null) {
                innerMap.remove(entryKey);
                if (innerMap.isEmpty()) {
                    localCache.remove(key);
                }
            }
            notifyListenersOnRemove(key, entryKey);
        } else {
            // Entry added/updated
            V value;
            try {
                value = deserializeFromJsonElement.apply(jsonElement);
            } catch (Exception e) {
                log.error("Failed to deserialize value for entry {}", entryKey, e);
                return;
            }

            if (value != null) {
                ConcurrentHashMap<String, V> innerMap = localCache.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
                innerMap.put(entryKey, value);
                notifyListenersOnAdd(key, entryKey, value);
            }
        }
    }

    private void notifyListenersOnFullUpdate(Map<K, Map<String, V>> map) {
        for (Listener<K, V> listener : listeners) {
            try {
                listener.onFullUpdate(map);
            } catch (Exception e) {
                log.error("Failed to notify listener on full update", e);
            }
        }
    }

    private void notifyListenersOnAdd(K key, String entryKey, V value) {
        for (Listener<K, V> listener : listeners) {
            try {
                listener.onAdd(key, entryKey, value);
            } catch (Exception e) {
                log.error("Failed to notify listener on add", e);
            }
        }
    }

    private void notifyListenersOnRemove(K key, String entryKey) {
        for (Listener<K, V> listener : listeners) {
            try {
                listener.onRemove(key, entryKey);
            } catch (Exception e) {
                log.error("Failed to notify listener on remove", e);
            }
        }
    }
}
