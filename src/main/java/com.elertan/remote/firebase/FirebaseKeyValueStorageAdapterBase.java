package com.elertan.remote.firebase;

import com.elertan.remote.KeyValueStoragePort;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FirebaseKeyValueStorageAdapterBase<K, V> implements KeyValueStoragePort<K, V> {

    private final String basePath;
    private final FirebaseRealtimeDatabase db;
    private final Gson gson;
    private final ConcurrentLinkedQueue<Listener<K, V>> listeners = new ConcurrentLinkedQueue<>();
    private final Function<String, K> stringToKeyTransformer;
    private final Function<K, String> keyToStringTransformer;
    private final Function<JsonElement, V> deserializeFromJsonElement;

    private final Consumer<FirebaseSSE> sseListener = this::sseListener;

    public FirebaseKeyValueStorageAdapterBase(
        String basePath,
        FirebaseRealtimeDatabase db,
        Gson gson,
        Function<String, K> stringToKeyTransformer,
        Function<K, String> keyToStringTransformer,
        Function<JsonElement, V> deserializeFromJsonElement
    ) {
        FirebaseRealtimeDatabase.validateBasePath(basePath);
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

        FirebaseSSEStream stream = db.getStream();
        stream.removeServerSentEventListener(sseListener);
    }

    @Override
    public CompletableFuture<V> read(K key) {
        String path = basePath + "/" + keyToStringTransformer.apply(key);
        return db.get(path)
            .thenApply(this.deserializeFromJsonElement);
    }

    @Override
    public CompletableFuture<Map<K, V>> readAll() {
        return db.get(basePath)
            .thenApply(jsonElement -> {
                if (jsonElement == null || jsonElement.isJsonNull()) {
                    return Collections.emptyMap();
                }

                JsonObject obj = jsonElement.getAsJsonObject();
                HashMap<K, V> map = new HashMap<>();

                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    K key = stringToKeyTransformer.apply(entry.getKey());
                    JsonElement entryValue = entry.getValue();
                    map.put(key, deserializeFromJsonElement.apply(entryValue));
                }

                return map;
            });
    }

    @Override
    public CompletableFuture<Void> update(K key, V value) {
        String path = basePath + "/" + keyToStringTransformer.apply(key);
        JsonElement jsonElement = gson.toJsonTree(value);
        return db.put(path, jsonElement).thenApply(__ -> null);
    }

    @Override
    public CompletableFuture<Void> updateAll(Map<K, V> map) {
        Map<K, JsonElement> jsonElementMap = new HashMap<>();
        for (Map.Entry<K, V> entry : map.entrySet()) {
            jsonElementMap.put(entry.getKey(), gson.toJsonTree(entry.getValue()));
        }
        JsonElement jsonElement = gson.toJsonTree(jsonElementMap);

        return db.put(basePath, jsonElement).thenApply(__ -> null);
    }

    @Override
    public CompletableFuture<Void> delete(K key) {
        String path = basePath + "/" + keyToStringTransformer.apply(key);
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
            // Full update
            JsonElement jsonElement = event.getData();
            Map<K, V> map = null;
            if (jsonElement != null && !jsonElement.isJsonNull()) {
                JsonObject obj = jsonElement.getAsJsonObject();
                map = new HashMap<>();
                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    K key = stringToKeyTransformer.apply(entry.getKey());
                    JsonElement entryValue = entry.getValue();
                    V value;
                    try {
                        value = deserializeFromJsonElement.apply(entryValue);
                    } catch (Exception e) {
                        log.error(
                            "RepositoryFirebaseStorageAdapterbase ({}): failed to deserialize value in full update for key ({}): {}",
                            basePath,
                            keyToStringTransformer.apply(key),
                            jsonElement,
                            e
                        );
                        return;
                    }
                    map.put(key, value);
                }
            }
            notifyListenersOnFullUpdate(map);
        } else if (pathPartsLength == 2) {
            String strKey = pathParts[1];
            K key = stringToKeyTransformer.apply(strKey);

            JsonElement jsonElement = event.getData();
            V value;
            try {
                value = deserializeFromJsonElement.apply(jsonElement);
            } catch (Exception e) {
                log.error(
                    "RepositoryFirebaseStorageAdapterbase ({}): failed to deserialize value for key ({}): {}",
                    basePath,
                    strKey,
                    jsonElement,
                    e
                );
                return;
            }

            if (value == null) {
                // Value deleted
                notifyListenersOnDelete(key);
            } else {
                // Value
                notifyListenersOnUpdate(key, value);
            }
        } else {
            log.info(
                "RepositoryFirebaseStorageAdapterbase ({}): too many path parts for unlocked items sse event, will ignored: {}",
                basePath,
                path
            );
        }
    }

    private void notifyListenersOnFullUpdate(Map<K, V> map) {
        for (Listener<K, V> listener : listeners) {
            try {
                listener.onFullUpdate(map);
            } catch (Exception e) {
                log.error("Failed to notify listeners on full update", e);
            }
        }
    }

    private void notifyListenersOnUpdate(K key, V value) {
        for (Listener<K, V> listener : listeners) {
            try {
                listener.onUpdate(key, value);
            } catch (Exception e) {
                log.error("Failed to notify listener on update", e);
            }
        }
    }

    private void notifyListenersOnDelete(K key) {
        for (Listener<K, V> listener : listeners) {
            try {
                listener.onDelete(key);
            } catch (Exception e) {
                log.error("Failed to notify listener on delete", e);
            }
        }
    }
}
