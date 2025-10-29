package com.elertan.remote.firebase;

import com.elertan.remote.ObjectStoragePort;
import com.google.gson.JsonElement;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FirebaseObjectStorageAdapterBase<T> implements ObjectStoragePort<T> {

    private final String path;
    private final FirebaseRealtimeDatabase db;
    private final Function<T, JsonElement> serializer;
    private final Function<JsonElement, T> deserializer;
    private final ConcurrentLinkedQueue<Listener<T>> listeners = new ConcurrentLinkedQueue<>();
    private final Consumer<FirebaseSSE> sseListener = this::sseListener;

    public FirebaseObjectStorageAdapterBase(
        String path,
        FirebaseRealtimeDatabase db,
        Function<T, JsonElement> serializer,
        Function<JsonElement, T> deserializer
    ) {
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
        stream.addServerSentEventListener(sseListener);
    }

    @Override
    public void close() throws Exception {
        FirebaseSSEStream stream = db.getStream();
        stream.removeServerSentEventListener(sseListener);
    }

    @Override
    public CompletableFuture<T> read() {
        CompletableFuture<T> future = new CompletableFuture<>();
        db.get(path).whenComplete((jsonElement, throwable) -> {
            if (throwable != null) {
                future.completeExceptionally(throwable);
                return;
            }
            boolean isJsonNull = jsonElement.isJsonNull();
            T value = this.deserializer.apply(jsonElement);
            if (value == null && !isJsonNull) {
                Exception ex = new IllegalStateException("deserialisation failed when reading value");
                log.error("deserialisation failed when reading value: {}", jsonElement, ex);
                future.completeExceptionally(ex);
                return;
            }

            future.complete(value);
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> update(T value) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        JsonElement jsonElement = this.serializer.apply(value);
        if (jsonElement == null || jsonElement.isJsonNull()) {
            Exception ex = new IllegalArgumentException(
                "value must not be null, call delete instead. Maybe the value failed to serialize?");
            future.completeExceptionally(ex);
            return future;
        }

        db.put(path, jsonElement).whenComplete((__, throwable) -> {
            if (throwable != null) {
                future.completeExceptionally(throwable);
                return;
            }

            future.complete(null);
        });

        return future;
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
