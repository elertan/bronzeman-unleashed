package com.elertan.remote.firebase;

import com.elertan.remote.ObjectListStoragePort;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FirebaseObjectListStorageAdapterBase<V> implements ObjectListStoragePort<V> {

    private final String basePath;
    private final FirebaseRealtimeDatabase db;
    private final Function<V, JsonElement> serializer;
    private final Function<JsonElement, V> deserializer;
    private final ConcurrentLinkedQueue<Listener<V>> listeners = new ConcurrentLinkedQueue<>();

    private final ConcurrentHashMap<String, V> localCache = new ConcurrentHashMap<>();

    private final Consumer<FirebaseSSE> sseListener = this::sseListener;

    public FirebaseObjectListStorageAdapterBase(
        String basePath,
        FirebaseRealtimeDatabase db,
        Function<V, JsonElement> serializer,
        Function<JsonElement, V> deserializer
    ) {
        FirebaseRealtimeDatabase.validateBasePath(basePath);
        this.basePath = basePath;
        this.db = db;
        this.serializer = serializer;
        this.deserializer = deserializer;

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
    public CompletableFuture<Map<String, V>> readAll() {
        return db.get(basePath)
            .thenApply(jsonElement -> {
                if (jsonElement == null || jsonElement.isJsonNull()) {
                    return Collections.emptyMap();
                }

                JsonObject obj = jsonElement.getAsJsonObject();
                Map<String, V> map = new HashMap<>();

                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    V value = deserializer.apply(entry.getValue());
                    if (value != null) {
                        map.put(entry.getKey(), value);
                    }
                }

                return map;
            });
    }

    @Override
    public CompletableFuture<String> add(V value) {
        JsonElement jsonElement = serializer.apply(value);
        return db.post(basePath, jsonElement)
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
    public CompletableFuture<Void> remove(String entryKey) {
        String path = basePath + "/" + entryKey;
        return db.delete(path);
    }

    @Override
    public void addListener(Listener<V> listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(Listener<V> listener) {
        listeners.remove(listener);
    }

    public ConcurrentHashMap<String, V> getLocalCache() {
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
            // Single entry add/remove
            String entryKey = pathParts[1];
            handleEntryUpdate(entryKey, event.getData());
        } else {
            log.info(
                "FirebaseObjectListStorageAdapterBase ({}): too many path parts, ignoring: {}",
                basePath,
                path
            );
        }
    }

    private void handleFullUpdate(JsonElement jsonElement) {
        localCache.clear();
        Map<String, V> fullMap = new HashMap<>();

        if (jsonElement != null && !jsonElement.isJsonNull() && jsonElement.isJsonObject()) {
            JsonObject obj = jsonElement.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                String entryKey = entry.getKey();
                V value;
                try {
                    value = deserializer.apply(entry.getValue());
                } catch (Exception e) {
                    log.error("Failed to deserialize value for entry {}", entryKey, e);
                    continue;
                }
                if (value != null) {
                    localCache.put(entryKey, value);
                    fullMap.put(entryKey, value);
                }
            }
        }

        notifyListenersOnFullUpdate(fullMap);
    }

    private void handleEntryUpdate(String entryKey, JsonElement jsonElement) {
        if (jsonElement == null || jsonElement.isJsonNull()) {
            // Entry removed
            localCache.remove(entryKey);
            notifyListenersOnRemove(entryKey);
        } else {
            // Entry added/updated
            V value;
            try {
                value = deserializer.apply(jsonElement);
            } catch (Exception e) {
                log.error("Failed to deserialize value for entry {}", entryKey, e);
                return;
            }

            if (value != null) {
                localCache.put(entryKey, value);
                notifyListenersOnAdd(entryKey, value);
            }
        }
    }

    private void notifyListenersOnFullUpdate(Map<String, V> map) {
        for (Listener<V> listener : listeners) {
            try {
                listener.onFullUpdate(map);
            } catch (Exception e) {
                log.error("Failed to notify listener on full update", e);
            }
        }
    }

    private void notifyListenersOnAdd(String entryKey, V value) {
        for (Listener<V> listener : listeners) {
            try {
                listener.onAdd(entryKey, value);
            } catch (Exception e) {
                log.error("Failed to notify listener on add", e);
            }
        }
    }

    private void notifyListenersOnRemove(String entryKey) {
        for (Listener<V> listener : listeners) {
            try {
                listener.onRemove(entryKey);
            } catch (Exception e) {
                log.error("Failed to notify listener on remove", e);
            }
        }
    }
}
