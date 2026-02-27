package com.elertan.remote.firebase;

import com.elertan.remote.ObjectListStoragePort;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FirebaseObjectListStorageAdapterBase<V> extends AbstractFirebaseStorageAdapter
    implements ObjectListStoragePort<V> {

    private final Function<V, JsonElement> serializer;
    private final Function<JsonElement, V> deserializer;
    private final ConcurrentLinkedQueue<Listener<V>> listeners = new ConcurrentLinkedQueue<>();
    @Getter
    private final ConcurrentHashMap<String, V> localCache = new ConcurrentHashMap<>();

    public FirebaseObjectListStorageAdapterBase(String basePath, FirebaseRealtimeDatabase db,
        Function<V, JsonElement> serializer, Function<JsonElement, V> deserializer) {
        super(basePath, db);
        this.serializer = serializer;
        this.deserializer = deserializer;
    }

    @Override
    public void close() throws Exception {
        listeners.clear();
        localCache.clear();
        super.close();
    }

    @Override
    public CompletableFuture<Map<String, V>> readAll() {
        return db.get(basePath).thenApply(json -> parseJsonMap(json, deserializer));
    }

    @Override
    public CompletableFuture<String> add(V value) {
        return db.post(basePath, serializer.apply(value)).thenApply(response -> {
            if (response != null && response.isJsonObject()) {
                JsonObject obj = response.getAsJsonObject();
                if (obj.has("name")) return obj.get("name").getAsString();
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> remove(String entryKey) {
        return db.delete(basePath + "/" + entryKey);
    }

    @Override
    public void addListener(Listener<V> l) { listeners.add(l); }

    @Override
    public void removeListener(Listener<V> l) { listeners.remove(l); }

    @Override
    protected void handleSSE(String[] pathParts, JsonElement data) {
        if (pathParts.length == 1) {
            localCache.clear();
            Map<String, V> fullMap = parseJsonMap(data, el -> {
                try { return deserializer.apply(el); }
                catch (Exception e) { log.error("Failed to deserialize entry", e); return null; }
            });
            localCache.putAll(fullMap);
            notifyAll(listeners, l -> l.onFullUpdate(fullMap), "fullUpdate");
        } else if (pathParts.length == 2) {
            String entryKey = pathParts[1];
            if (data == null || data.isJsonNull()) {
                localCache.remove(entryKey);
                notifyAll(listeners, l -> l.onRemove(entryKey), "remove");
            } else {
                try {
                    V value = deserializer.apply(data);
                    if (value != null) {
                        localCache.put(entryKey, value);
                        notifyAll(listeners, l -> l.onAdd(entryKey, value), "add");
                    }
                } catch (Exception e) { log.error("Failed to deserialize entry {}", entryKey, e); }
            }
        }
    }
}
