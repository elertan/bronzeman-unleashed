package com.elertan.remote.firebase;

import com.elertan.remote.ObjectStoragePort;
import com.google.gson.JsonElement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FirebaseObjectStorageAdapterBase<T> extends AbstractFirebaseStorageAdapter
    implements ObjectStoragePort<T> {

    private final Function<T, JsonElement> serializer;
    private final Function<JsonElement, T> deserializer;
    private final ConcurrentLinkedQueue<Listener<T>> listeners = new ConcurrentLinkedQueue<>();

    public FirebaseObjectStorageAdapterBase(String path, FirebaseRealtimeDatabase db,
        Function<T, JsonElement> serializer, Function<JsonElement, T> deserializer) {
        super(path, db);
        this.serializer = serializer;
        this.deserializer = deserializer;
    }

    @Override
    public CompletableFuture<T> read() {
        return db.get(basePath).thenApply(json -> {
            T value = deserializer.apply(json);
            if (value == null && json != null && !json.isJsonNull()) {
                throw new IllegalStateException("deserialisation failed when reading value");
            }
            return value;
        });
    }

    @Override
    public CompletableFuture<Void> update(T value) {
        JsonElement json = serializer.apply(value);
        if (json == null || json.isJsonNull()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                "value must not be null, call delete instead"));
        }
        return db.put(basePath, json).thenApply(__ -> null);
    }

    @Override
    public CompletableFuture<Void> delete() { return db.delete(basePath); }

    @Override
    public void addListener(Listener<T> l) { listeners.add(l); }

    @Override
    public void removeListener(Listener<T> l) { listeners.remove(l); }

    @Override
    protected void handleSSE(String[] pathParts, JsonElement data) {
        if (pathParts.length != 1) return;
        if (data == null || data.isJsonNull()) {
            notifyAll(listeners, Listener::onDelete, "delete");
        } else {
            T value = deserializer.apply(data);
            notifyAll(listeners, l -> l.onUpdate(value), "update");
        }
    }
}
