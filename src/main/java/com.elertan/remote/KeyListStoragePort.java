package com.elertan.remote;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface KeyListStoragePort<K, V> extends AutoCloseable {

    CompletableFuture<Map<String, V>> read(K key);

    CompletableFuture<Map<K, Map<String, V>>> readAll();

    CompletableFuture<String> add(K key, V value);

    CompletableFuture<Void> removeOne(K key);

    CompletableFuture<Void> remove(K key, String entryKey);

    void addListener(Listener<K, V> listener);

    void removeListener(Listener<K, V> listener);

    interface Listener<K, V> {

        void onFullUpdate(Map<K, Map<String, V>> map);

        void onAdd(K key, String entryKey, V value);

        void onRemove(K key, String entryKey);
    }
}
