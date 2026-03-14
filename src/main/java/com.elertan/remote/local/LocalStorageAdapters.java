package com.elertan.remote.local;

import com.elertan.remote.KeyListStoragePort;
import com.elertan.remote.KeyValueStoragePort;
import com.elertan.remote.ObjectStoragePort;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class LocalStorageAdapters {

    private static final AtomicInteger EXECUTOR_COUNTER = new AtomicInteger();

    private LocalStorageAdapters() {
    }

    public static final class UnreadableLocalProgressException extends RuntimeException {

        private final Path filePath;

        public UnreadableLocalProgressException(Path filePath, Throwable cause) {
            super("Failed to read local progress file: " + filePath, cause);
            this.filePath = filePath;
        }

        public Path getFilePath() {
            return filePath;
        }
    }

    public static final class JsonFileKeyValueStorageAdapter<K, V> implements KeyValueStoragePort<K, V> {

        private final Path filePath;
        private final Gson gson;
        private final Function<K, String> keyToString;
        private final Function<String, K> stringToKey;
        private final Type valueType;
        private final ConcurrentLinkedQueue<Listener<K, V>> listeners = new ConcurrentLinkedQueue<>();
        private final ExecutorService executor = newSingleThreadExecutor("local-key-value");
        private Map<K, V> cache;

        public JsonFileKeyValueStorageAdapter(
            Path filePath,
            Gson gson,
            Function<K, String> keyToString,
            Function<String, K> stringToKey,
            Type valueType
        ) {
            this.filePath = filePath;
            this.gson = gson;
            this.keyToString = keyToString;
            this.stringToKey = stringToKey;
            this.valueType = valueType;
        }

        @Override
        public CompletableFuture<V> read(K key) {
            return readAll().thenApply(map -> map.get(key));
        }

        @Override
        public CompletableFuture<Map<K, V>> readAll() {
            return CompletableFuture.supplyAsync(() -> {
                ensureLoaded();
                return new HashMap<>(cache);
            }, executor);
        }

        @Override
        public CompletableFuture<Void> update(K key, V value) {
            return CompletableFuture.runAsync(() -> {
                ensureLoaded();
                cache.put(key, value);
                flush();

                notifyKeyValueListeners(
                    listeners,
                    listener -> listener.onUpdate(key, value),
                    "Local key-value listener failed during update"
                );
            }, executor);
        }

        @Override
        public CompletableFuture<Void> updateAll(Map<K, V> map) {
            return CompletableFuture.runAsync(() -> {
                cache = new HashMap<>(map);
                flush();
                Map<K, V> snapshot = new HashMap<>(cache);

                notifyKeyValueListeners(
                    listeners,
                    listener -> listener.onFullUpdate(Collections.unmodifiableMap(snapshot)),
                    "Local key-value listener failed during full update"
                );
            }, executor);
        }

        @Override
        public CompletableFuture<Void> delete(K key) {
            return CompletableFuture.runAsync(() -> {
                ensureLoaded();
                cache.remove(key);
                flush();

                notifyKeyValueListeners(
                    listeners,
                    listener -> listener.onDelete(key),
                    "Local key-value listener failed during delete"
                );
            }, executor);
        }

        @Override
        public void addListener(Listener<K, V> listener) {
            listeners.add(listener);
        }

        @Override
        public void removeListener(Listener<K, V> listener) {
            listeners.remove(listener);
        }

        @Override
        public void close() {
            listeners.clear();
            executor.shutdownNow();
        }

        private void ensureLoaded() {
            if (cache != null) {
                return;
            }

            cache = new HashMap<>();
            if (!Files.exists(filePath)) {
                return;
            }

            Type mapType = TypeToken.getParameterized(Map.class, String.class, valueType).getType();
            try (Reader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
                Map<String, V> raw = gson.fromJson(reader, mapType);
                if (raw == null) {
                    return;
                }

                for (Map.Entry<String, V> entry : raw.entrySet()) {
                    cache.put(stringToKey.apply(entry.getKey()), entry.getValue());
                }
            } catch (Exception e) {
                throw new UnreadableLocalProgressException(filePath, e);
            }
        }

        private void flush() {
            Map<String, V> raw = new HashMap<>();
            for (Map.Entry<K, V> entry : cache.entrySet()) {
                raw.put(keyToString.apply(entry.getKey()), entry.getValue());
            }
            writeJsonFile(filePath, gson.toJson(raw));
        }
    }

    public static final class JsonFileObjectStorageAdapter<T> implements ObjectStoragePort<T> {

        private final Path filePath;
        private final Gson gson;
        private final Type valueType;
        private final ConcurrentLinkedQueue<Listener<T>> listeners = new ConcurrentLinkedQueue<>();
        private final ExecutorService executor = newSingleThreadExecutor("local-object");
        private boolean loaded;
        private T cache;

        public JsonFileObjectStorageAdapter(Path filePath, Gson gson, Type valueType) {
            this.filePath = filePath;
            this.gson = gson;
            this.valueType = valueType;
        }

        @Override
        public CompletableFuture<T> read() {
            return CompletableFuture.supplyAsync(() -> {
                ensureLoaded();
                return cache;
            }, executor);
        }

        @Override
        public CompletableFuture<Void> update(T value) {
            return CompletableFuture.runAsync(() -> {
                cache = value;
                loaded = true;
                flush();

                notifyObjectListeners(
                    listeners,
                    listener -> listener.onUpdate(value),
                    "Local object listener failed during update"
                );
            }, executor);
        }

        @Override
        public CompletableFuture<Void> delete() {
            return CompletableFuture.runAsync(() -> {
                cache = null;
                loaded = true;
                deleteFile(filePath);

                notifyObjectListeners(
                    listeners,
                    Listener::onDelete,
                    "Local object listener failed during delete"
                );
            }, executor);
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
        public void close() {
            listeners.clear();
            executor.shutdownNow();
        }

        private void ensureLoaded() {
            if (loaded) {
                return;
            }

            loaded = true;
            if (!Files.exists(filePath)) {
                cache = null;
                return;
            }

            try (Reader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
                cache = gson.fromJson(reader, valueType);
            } catch (Exception e) {
                throw new UnreadableLocalProgressException(filePath, e);
            }
        }

        private void flush() {
            if (cache == null) {
                deleteFile(filePath);
                return;
            }
            writeJsonFile(filePath, gson.toJson(cache));
        }
    }

    public static final class InMemoryKeyValueStorageAdapter<K, V> implements KeyValueStoragePort<K, V> {

        private final ConcurrentHashMap<K, V> cache = new ConcurrentHashMap<>();
        private final ConcurrentLinkedQueue<Listener<K, V>> listeners = new ConcurrentLinkedQueue<>();

        @Override
        public CompletableFuture<V> read(K key) {
            return CompletableFuture.completedFuture(cache.get(key));
        }

        @Override
        public CompletableFuture<Map<K, V>> readAll() {
            return CompletableFuture.completedFuture(new HashMap<>(cache));
        }

        @Override
        public CompletableFuture<Void> update(K key, V value) {
            cache.put(key, value);
            notifyKeyValueListeners(
                listeners,
                listener -> listener.onUpdate(key, value),
                "In-memory key-value listener failed during update"
            );
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> updateAll(Map<K, V> map) {
            cache.clear();
            cache.putAll(map);
            Map<K, V> snapshot = new HashMap<>(cache);
            notifyKeyValueListeners(
                listeners,
                listener -> listener.onFullUpdate(snapshot),
                "In-memory key-value listener failed during full update"
            );
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> delete(K key) {
            cache.remove(key);
            notifyKeyValueListeners(
                listeners,
                listener -> listener.onDelete(key),
                "In-memory key-value listener failed during delete"
            );
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void addListener(Listener<K, V> listener) {
            listeners.add(listener);
        }

        @Override
        public void removeListener(Listener<K, V> listener) {
            listeners.remove(listener);
        }

        @Override
        public void close() {
            listeners.clear();
            cache.clear();
        }
    }

    public static final class InMemoryKeyListStorageAdapter<K, V> implements KeyListStoragePort<K, V> {

        private final ConcurrentHashMap<K, ConcurrentHashMap<String, V>> cache = new ConcurrentHashMap<>();
        private final ConcurrentLinkedQueue<Listener<K, V>> listeners = new ConcurrentLinkedQueue<>();

        @Override
        public CompletableFuture<Map<String, V>> read(K key) {
            Map<String, V> innerMap = cache.get(key);
            if (innerMap == null) {
                return CompletableFuture.completedFuture(Collections.emptyMap());
            }
            return CompletableFuture.completedFuture(new HashMap<>(innerMap));
        }

        @Override
        public CompletableFuture<Map<K, Map<String, V>>> readAll() {
            Map<K, Map<String, V>> snapshot = new HashMap<>();
            for (Map.Entry<K, ConcurrentHashMap<String, V>> entry : cache.entrySet()) {
                snapshot.put(entry.getKey(), new HashMap<>(entry.getValue()));
            }
            return CompletableFuture.completedFuture(snapshot);
        }

        @Override
        public CompletableFuture<String> add(K key, V value) {
            String entryKey = UUID.randomUUID().toString();
            cache.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>()).put(entryKey, value);
            notifyKeyListListeners(
                listeners,
                listener -> listener.onAdd(key, entryKey, value),
                "In-memory key-list listener failed during add"
            );
            return CompletableFuture.completedFuture(entryKey);
        }

        @Override
        public CompletableFuture<Void> removeOne(K key) {
            ConcurrentHashMap<String, V> innerMap = cache.get(key);
            if (innerMap == null || innerMap.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }

            String entryKey = innerMap.keys().nextElement();
            return remove(key, entryKey);
        }

        @Override
        public CompletableFuture<Void> remove(K key, String entryKey) {
            ConcurrentHashMap<String, V> innerMap = cache.get(key);
            if (innerMap != null) {
                innerMap.remove(entryKey);
                if (innerMap.isEmpty()) {
                    cache.remove(key);
                }
            }

            notifyKeyListListeners(
                listeners,
                listener -> listener.onRemove(key, entryKey),
                "In-memory key-list listener failed during remove"
            );
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void addListener(Listener<K, V> listener) {
            listeners.add(listener);
        }

        @Override
        public void removeListener(Listener<K, V> listener) {
            listeners.remove(listener);
        }

        @Override
        public void close() {
            listeners.clear();
            cache.clear();
        }
    }

    private static ExecutorService newSingleThreadExecutor(String label) {
        return Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(
                    runnable,
                    "bronzeman-" + label + "-" + EXECUTOR_COUNTER.incrementAndGet()
                );
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    private interface KeyValueListenerAction<K, V> {
        void accept(KeyValueStoragePort.Listener<K, V> listener);
    }

    private interface ObjectListenerAction<T> {
        void accept(ObjectStoragePort.Listener<T> listener);
    }

    private interface KeyListListenerAction<K, V> {
        void accept(KeyListStoragePort.Listener<K, V> listener);
    }

    private static <K, V> void notifyKeyValueListeners(
        Iterable<KeyValueStoragePort.Listener<K, V>> listeners,
        KeyValueListenerAction<K, V> action,
        String errorMessage
    ) {
        for (KeyValueStoragePort.Listener<K, V> listener : listeners) {
            try {
                action.accept(listener);
            } catch (Exception e) {
                log.error(errorMessage, e);
            }
        }
    }

    private static <T> void notifyObjectListeners(
        Iterable<ObjectStoragePort.Listener<T>> listeners,
        ObjectListenerAction<T> action,
        String errorMessage
    ) {
        for (ObjectStoragePort.Listener<T> listener : listeners) {
            try {
                action.accept(listener);
            } catch (Exception e) {
                log.error(errorMessage, e);
            }
        }
    }

    private static <K, V> void notifyKeyListListeners(
        Iterable<KeyListStoragePort.Listener<K, V>> listeners,
        KeyListListenerAction<K, V> action,
        String errorMessage
    ) {
        for (KeyListStoragePort.Listener<K, V> listener : listeners) {
            try {
                action.accept(listener);
            } catch (Exception e) {
                log.error(errorMessage, e);
            }
        }
    }

    private static void writeJsonFile(Path filePath, String json) {
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Path tempPath = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
                writer.write(json);
            }
            Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + filePath, e);
        }
    }

    private static void deleteFile(Path filePath) {
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete " + filePath, e);
        }
    }
}
