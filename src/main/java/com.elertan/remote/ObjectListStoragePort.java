package com.elertan.remote;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface ObjectListStoragePort<V> extends AutoCloseable {

    CompletableFuture<Map<String, V>> readAll();

    CompletableFuture<String> add(V value);

    CompletableFuture<Void> remove(String entryKey);

    void addListener(Listener<V> listener);

    void removeListener(Listener<V> listener);

    interface Listener<V> {

        void onFullUpdate(Map<String, V> map);

        void onAdd(String entryKey, V value);

        void onRemove(String entryKey);
    }
}
