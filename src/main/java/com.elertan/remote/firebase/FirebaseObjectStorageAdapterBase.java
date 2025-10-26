package com.elertan.remote.firebase;

import com.elertan.remote.ObjectStoragePort;
import com.google.gson.JsonElement;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

@Slf4j
public class FirebaseObjectStorageAdapterBase<T> implements ObjectStoragePort<T> {
    private final String path;
    private final FirebaseRealtimeDatabase db;
    private final Function<T, JsonElement> serializer;
    private final Function<JsonElement, T> deserializer;
    private final ConcurrentLinkedQueue<Listener<T>> listeners = new ConcurrentLinkedQueue<>();

    public FirebaseObjectStorageAdapterBase(String path, FirebaseRealtimeDatabase db, Function<T, JsonElement> serializer, Function<JsonElement, T> deserializer) {
        // Base key should be of format
        // '/Resource' // NOT -> or '/FirstLevel/SecondLevel'
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("path must start with '/'");
        }
        int lastIndexOfForwardSlash = path.lastIndexOf("/");
        if (lastIndexOfForwardSlash != 0) {
            throw new IllegalArgumentException("path must only be a starting resource");
        }

        this.path = path;
        this.db = db;
        this.serializer = serializer;
        this.deserializer = deserializer;

        FirebaseSSEStream stream = db.getStream();
        stream.addServerSentEventListener(this::sseListener);
    }

    @Override
    public CompletableFuture<T> read() {
        return db.get(path)
                .thenApply(this.deserializer);
    }

    @Override
    public CompletableFuture<Void> update(T value) {
        JsonElement jsonElement = this.serializer.apply(value);
        return db.put(path, jsonElement).thenApply(__ -> null);
    }

    @Override
    public CompletableFuture<Void> delete() {
        return db.delete(path);
    }

    @Override
    public void addListener(Listener<T> listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(Listener<T> listener) {
        listeners.remove(listener);
    }

    @Override
    public void close() throws Exception {
        FirebaseSSEStream stream = db.getStream();
        stream.removeServerSentEventListener(this::sseListener);
    }

    private void sseListener(FirebaseSSE event) {
        FirebaseSSEType type = event.getType();
        if (type != FirebaseSSEType.Put) {
            return;
        }

        String path = event.getPath();
        if (!path.startsWith(this.path)) {
            return;
        }
        String[] pathParts = Arrays.stream(path.split("/"))
                .filter(part -> !part.isEmpty())
                .toArray(String[]::new);
        int pathPartsLength = pathParts.length;
        if (pathPartsLength != 1) {
            log.warn("put received but at a deeper level than just the object store, ignoring");
            return;
        }

        JsonElement jsonElement = event.getData();

        if (jsonElement == null || jsonElement.isJsonNull()) {
            notifyListenersOnDelete();
            return;
        }

        T value = this.deserializer.apply(jsonElement);
        notifyListenersOnUpdate(value);
    }

    private void notifyListenersOnUpdate(T value) {
        for (Listener<T> listener : listeners) {
            try {
                listener.onUpdate(value);
            } catch (Exception e) {
                log.error("Failed to notify listener on update", e);
            }
        }
    }

    private void notifyListenersOnDelete() {
        for (Listener<T> listener : listeners) {
            try {
                listener.onDelete();
            } catch (Exception e) {
                log.error("Failed to notify listener on delete", e);
            }
        }
    }
}
