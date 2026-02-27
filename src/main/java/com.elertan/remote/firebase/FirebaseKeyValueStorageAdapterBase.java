package com.elertan.remote.firebase;

import com.elertan.remote.KeyValueStoragePort;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FirebaseKeyValueStorageAdapterBase<K, V> extends AbstractFirebaseStorageAdapter
    implements KeyValueStoragePort<K, V> {

    private final Gson gson;
    private final ConcurrentLinkedQueue<Listener<K, V>> listeners = new ConcurrentLinkedQueue<>();
    private final Function<String, K> toKey;
    private final Function<K, String> fromKey;
    private final Function<JsonElement, V> deserializer;

    public FirebaseKeyValueStorageAdapterBase(String basePath, FirebaseRealtimeDatabase db,
        Gson gson, Function<String, K> toKey, Function<K, String> fromKey,
        Function<JsonElement, V> deserializer) {
        super(basePath, db);
        this.gson = gson;
        this.toKey = toKey;
        this.fromKey = fromKey;
        this.deserializer = deserializer;
    }

    @Override
    public void close() throws Exception { listeners.clear(); super.close(); }

    @Override
    public CompletableFuture<V> read(K key) {
        return db.get(basePath + "/" + fromKey.apply(key)).thenApply(deserializer);
    }

    @Override
    public CompletableFuture<Map<K, V>> readAll() {
        return db.get(basePath).thenApply(json -> {
            Map<String, V> raw = parseJsonMap(json, deserializer);
            if (raw.isEmpty()) return Collections.emptyMap();
            Map<K, V> result = new HashMap<>();
            raw.forEach((k, v) -> result.put(toKey.apply(k), v));
            return result;
        });
    }

    @Override
    public CompletableFuture<Void> update(K key, V value) {
        return db.put(basePath + "/" + fromKey.apply(key), gson.toJsonTree(value)).thenApply(__ -> null);
    }

    @Override
    public CompletableFuture<Void> updateAll(Map<K, V> map) {
        Map<K, JsonElement> jsonMap = new HashMap<>();
        map.forEach((k, v) -> jsonMap.put(k, gson.toJsonTree(v)));
        return db.put(basePath, gson.toJsonTree(jsonMap)).thenApply(__ -> null);
    }

    @Override
    public CompletableFuture<Void> delete(K key) {
        return db.delete(basePath + "/" + fromKey.apply(key));
    }

    @Override
    public void addListener(Listener<K, V> l) { listeners.add(l); }

    @Override
    public void removeListener(Listener<K, V> l) { listeners.remove(l); }

    @Override
    protected void handleSSE(String[] pathParts, JsonElement data) {
        if (pathParts.length == 1) {
            Map<K, V> map = null;
            if (data != null && !data.isJsonNull()) {
                try {
                    Map<String, V> raw = parseJsonMap(data, deserializer);
                    map = new HashMap<>();
                    for (Map.Entry<String, V> e : raw.entrySet()) {
                        map.put(toKey.apply(e.getKey()), e.getValue());
                    }
                } catch (Exception e) { log.error("Failed to deserialize full update for {}", basePath, e); return; }
            }
            final Map<K, V> finalMap = map;
            notifyAll(listeners, l -> l.onFullUpdate(finalMap), "fullUpdate");
        } else if (pathParts.length == 2) {
            K key = toKey.apply(pathParts[1]);
            try {
                V value = deserializer.apply(data);
                if (value == null) {
                    notifyAll(listeners, l -> l.onDelete(key), "delete");
                } else {
                    notifyAll(listeners, l -> l.onUpdate(key, value), "update");
                }
            } catch (Exception e) { log.error("Failed to deserialize value for key {}", pathParts[1], e); }
        }
    }
}
