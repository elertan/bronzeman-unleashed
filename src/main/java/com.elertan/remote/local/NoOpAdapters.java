package com.elertan.remote.local;

import com.elertan.remote.ObjectListStoragePort;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class NoOpAdapters {

    private NoOpAdapters() {
    }

    public static final class NoOpObjectListStorageAdapter<V> implements ObjectListStoragePort<V> {

        @Override
        public CompletableFuture<Map<String, V>> readAll() {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        @Override
        public CompletableFuture<String> add(V value) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> remove(String entryKey) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void addListener(Listener<V> listener) {
        }

        @Override
        public void removeListener(Listener<V> listener) {
        }

        @Override
        public void close() {
        }
    }
}
