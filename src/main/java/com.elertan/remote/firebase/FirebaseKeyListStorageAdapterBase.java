package com.elertan.remote.firebase;

import com.elertan.remote.KeyListStoragePort;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FirebaseKeyListStorageAdapterBase<K, V> extends AbstractFirebaseStorageAdapter
    implements KeyListStoragePort<K, V> {

    private final Gson gson;
    private final ConcurrentLinkedQueue<Listener<K, V>> listeners = new ConcurrentLinkedQueue<>();
    private final Function<String, K> toKey;
    private final Function<K, String> fromKey;
    private final Function<JsonElement, V> deserializer;
    @Getter
    private final ConcurrentHashMap<K, ConcurrentHashMap<String, V>> localCache = new ConcurrentHashMap<>();

    public FirebaseKeyListStorageAdapterBase(String basePath, FirebaseRealtimeDatabase db,
        Gson gson, Function<String, K> toKey, Function<K, String> fromKey,
        Function<JsonElement, V> deserializer) {
        super(basePath, db);
        this.gson = gson;
        this.toKey = toKey;
        this.fromKey = fromKey;
        this.deserializer = deserializer;
    }

    @Override
    public void close() throws Exception { listeners.clear(); localCache.clear(); super.close(); }

    @Override
    public CompletableFuture<Map<String, V>> read(K key) {
        return db.get(basePath + "/" + fromKey.apply(key))
            .thenApply(json -> parseJsonMap(json, deserializer));
    }

    @Override
    public CompletableFuture<Map<K, Map<String, V>>> readAll() {
        return db.get(basePath).thenApply(json -> {
            if (json == null || json.isJsonNull() || !json.isJsonObject()) return Collections.emptyMap();
            Map<K, Map<String, V>> result = new HashMap<>();
            for (Map.Entry<String, JsonElement> keyEntry : json.getAsJsonObject().entrySet()) {
                Map<String, V> inner = parseJsonMap(keyEntry.getValue(), deserializer);
                if (!inner.isEmpty()) result.put(toKey.apply(keyEntry.getKey()), inner);
            }
            return result;
        });
    }

    @Override
    public CompletableFuture<String> add(K key, V value) {
        return db.post(basePath + "/" + fromKey.apply(key), gson.toJsonTree(value)).thenApply(resp -> {
            if (resp != null && resp.isJsonObject()) {
                JsonObject obj = resp.getAsJsonObject();
                if (obj.has("name")) return obj.get("name").getAsString();
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> removeOne(K key) {
        ConcurrentHashMap<String, V> inner = localCache.get(key);
        if (inner == null || inner.isEmpty()) return CompletableFuture.completedFuture(null);
        Iterator<String> it = inner.keySet().iterator();
        if (!it.hasNext()) return CompletableFuture.completedFuture(null);
        return remove(key, it.next());
    }

    @Override
    public CompletableFuture<Void> remove(K key, String entryKey) {
        return db.delete(basePath + "/" + fromKey.apply(key) + "/" + entryKey);
    }

    @Override
    public void addListener(Listener<K, V> l) { listeners.add(l); }

    @Override
    public void removeListener(Listener<K, V> l) { listeners.remove(l); }

    @Override
    protected void handleSSE(String[] pathParts, JsonElement data) {
        int depth = pathParts.length;
        if (depth == 1) {
            handleFullUpdate(data);
        } else if (depth == 2) {
            K key = toKey.apply(pathParts[1]);
            handleKeyFullUpdate(key, data);
        } else if (depth == 3) {
            K key = toKey.apply(pathParts[1]);
            handleEntryUpdate(key, pathParts[2], data);
        }
    }

    private void handleFullUpdate(JsonElement json) {
        localCache.clear();
        Map<K, Map<String, V>> fullMap = new HashMap<>();
        if (json != null && !json.isJsonNull() && json.isJsonObject()) {
            for (Map.Entry<String, JsonElement> keyEntry : json.getAsJsonObject().entrySet()) {
                K key;
                try { key = toKey.apply(keyEntry.getKey()); }
                catch (Exception e) { log.error("Failed to parse key: {}", keyEntry.getKey(), e); continue; }
                Map<String, V> inner = parseJsonMap(keyEntry.getValue(), el -> {
                    try { return deserializer.apply(el); }
                    catch (Exception e) { log.error("Failed to deserialize entry", e); return null; }
                });
                if (!inner.isEmpty()) {
                    localCache.put(key, new ConcurrentHashMap<>(inner));
                    fullMap.put(key, inner);
                }
            }
        }
        notifyAll(listeners, l -> l.onFullUpdate(fullMap), "fullUpdate");
    }

    private void handleKeyFullUpdate(K key, JsonElement json) {
        localCache.remove(key);
        if (json == null || json.isJsonNull() || !json.isJsonObject()) return;
        ConcurrentHashMap<String, V> inner = new ConcurrentHashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
            try {
                V value = deserializer.apply(entry.getValue());
                if (value != null) {
                    inner.put(entry.getKey(), value);
                    notifyAll(listeners, l -> l.onAdd(key, entry.getKey(), value), "add");
                }
            } catch (Exception e) { log.error("Failed to deserialize entry {}", entry.getKey(), e); }
        }
        if (!inner.isEmpty()) localCache.put(key, inner);
    }

    private void handleEntryUpdate(K key, String entryKey, JsonElement json) {
        if (json == null || json.isJsonNull()) {
            ConcurrentHashMap<String, V> inner = localCache.get(key);
            if (inner != null) { inner.remove(entryKey); if (inner.isEmpty()) localCache.remove(key); }
            notifyAll(listeners, l -> l.onRemove(key, entryKey), "remove");
        } else {
            try {
                V value = deserializer.apply(json);
                if (value != null) {
                    localCache.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(entryKey, value);
                    notifyAll(listeners, l -> l.onAdd(key, entryKey, value), "add");
                }
            } catch (Exception e) { log.error("Failed to deserialize entry {}", entryKey, e); }
        }
    }
}
