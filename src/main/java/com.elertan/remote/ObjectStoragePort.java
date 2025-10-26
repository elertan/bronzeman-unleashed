package com.elertan.remote;

import java.util.concurrent.CompletableFuture;

public interface ObjectStoragePort<T> extends AutoCloseable {
    interface Listener<T> {
        void onUpdate(T value);

        void onDelete();
    }

    CompletableFuture<T> read();

    CompletableFuture<Void> update(T value);

    CompletableFuture<Void> delete();

    void addListener(Listener<T> listener);

    void removeListener(Listener<T> listener);
}
