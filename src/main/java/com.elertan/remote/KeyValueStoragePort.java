package com.elertan.remote;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface KeyValueStoragePort<K, V> extends AutoCloseable {
    interface Listener<K, V> {
        void onFullUpdate(Map<K, V> map);
        void onUpdate(K key, V value);

        void onDelete(K key);
    }

    CompletableFuture<V> read(K key);

    CompletableFuture<Map<K, V>> readAll();

    CompletableFuture<Void> update(K key, V value);

    CompletableFuture<Void> updateAll(Map<K, V> map);

    CompletableFuture<Void> delete(K key);

    void addListener(Listener<K, V> listener);
    void removeListener(Listener<K, V> listener);
}
