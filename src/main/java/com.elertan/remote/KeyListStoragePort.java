package com.elertan.remote;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface KeyListStoragePort<K, V> extends AutoCloseable {

    CompletableFuture<List<V>> read(K key);

    CompletableFuture<Map<K, List<V>>> readAll();

    CompletableFuture<String> add(K key, V value);

    CompletableFuture<Void> removeOne(K key);

    CompletableFuture<Void> remove(K key, String entryKey);

    void addListener(Listener<K, V> listener);

    void removeListener(Listener<K, V> listener);

    interface Listener<K, V> {

        void onFullUpdate(Map<K, List<V>> map);

        void onAdd(K key, String entryKey, V value);

        void onRemove(K key, String entryKey);
    }
}
