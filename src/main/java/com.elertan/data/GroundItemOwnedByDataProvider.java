package com.elertan.data;

import com.elertan.BUPluginLifecycle;
import com.elertan.models.GroundItemOwnedByData;
import com.elertan.models.GroundItemOwnedByKey;
import com.elertan.remote.KeyListStoragePort;
import com.elertan.remote.RemoteStorageService;
import com.elertan.utils.ListenerUtils;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class GroundItemOwnedByDataProvider implements BUPluginLifecycle {

    private final ConcurrentLinkedQueue<Listener> maplisteners = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Consumer<State>> stateListeners = new ConcurrentLinkedQueue<>();
    @Inject
    private RemoteStorageService remoteStorageService;
    private KeyListStoragePort<GroundItemOwnedByKey, GroundItemOwnedByData> storagePort;
    private KeyListStoragePort.Listener<GroundItemOwnedByKey, GroundItemOwnedByData> storagePortListener;
    @Getter
    private State state = State.NotReady;
    @Getter
    private ConcurrentHashMap<GroundItemOwnedByKey, ConcurrentHashMap<String, GroundItemOwnedByData>> groundItemOwnedByMap;
    private final Consumer<RemoteStorageService.State> remoteStorageServiceStateListener = this::remoteStorageServiceStateListener;

    @Override
    public void startUp() throws Exception {
        remoteStorageService.addStateListener(remoteStorageServiceStateListener);

        storagePortListener = new KeyListStoragePort.Listener<GroundItemOwnedByKey, GroundItemOwnedByData>() {
            @Override
            public void onFullUpdate(Map<GroundItemOwnedByKey, List<GroundItemOwnedByData>> map) {
                groundItemOwnedByMap = new ConcurrentHashMap<>();
                for (Map.Entry<GroundItemOwnedByKey, List<GroundItemOwnedByData>> entry : map.entrySet()) {
                    ConcurrentHashMap<String, GroundItemOwnedByData> innerMap = new ConcurrentHashMap<>();
                    // We don't have entry keys from readAll result, but the adapter's local cache does
                    // For full update, we'll sync from the adapter's cache
                    groundItemOwnedByMap.put(entry.getKey(), innerMap);
                }
                // Sync with adapter's local cache for entry keys
                syncFromAdapterCache();

                for (Listener listener : maplisteners) {
                    try {
                        listener.onReadAll(groundItemOwnedByMap);
                    } catch (Exception e) {
                        log.error("Error while notifying listener on GroundItemOwnedByDataProvider.", e);
                    }
                }
            }

            @Override
            public void onAdd(GroundItemOwnedByKey key, String entryKey, GroundItemOwnedByData value) {
                if (groundItemOwnedByMap == null) {
                    return;
                }

                ConcurrentHashMap<String, GroundItemOwnedByData> innerMap =
                    groundItemOwnedByMap.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
                innerMap.put(entryKey, value);

                for (Listener listener : maplisteners) {
                    try {
                        listener.onAdd(key, entryKey, value);
                    } catch (Exception e) {
                        log.error("Error while notifying listener on GroundItemOwnedByDataProvider.", e);
                    }
                }
            }

            @Override
            public void onRemove(GroundItemOwnedByKey key, String entryKey) {
                if (groundItemOwnedByMap == null) {
                    return;
                }

                ConcurrentHashMap<String, GroundItemOwnedByData> innerMap = groundItemOwnedByMap.get(key);
                if (innerMap != null) {
                    innerMap.remove(entryKey);
                    if (innerMap.isEmpty()) {
                        groundItemOwnedByMap.remove(key);
                    }
                }

                for (Listener listener : maplisteners) {
                    try {
                        listener.onRemove(key, entryKey);
                    } catch (Exception e) {
                        log.error("Error while notifying listener on GroundItemOwnedByDataProvider.", e);
                    }
                }
            }
        };

        tryInitialize();
    }

    @Override
    public void shutDown() throws Exception {
        deinitialize();
    }

    private void remoteStorageServiceStateListener(RemoteStorageService.State state) {
        try {
            tryInitialize();
        } catch (Exception e) {
            log.error("GroundItemOwnedByDataProvider remoteStorageServiceStateListener failed", e);
        }
    }

    public void addMapListener(Listener listener) {
        maplisteners.add(listener);
    }

    public void removeMapListener(Listener listener) {
        maplisteners.remove(listener);
    }

    public void addStateListener(Consumer<State> listener) {
        stateListeners.add(listener);
    }

    public void removeStateListener(Consumer<State> listener) {
        stateListeners.remove(listener);
    }

    public CompletableFuture<Void> waitUntilReady(Duration timeout) {
        return ListenerUtils.waitUntilReady(new ListenerUtils.WaitUntilReadyContext() {
            Consumer<State> listener;

            @Override
            public boolean isReady() {
                return state == State.Ready;
            }

            @Override
            public void addListener(Runnable notify) {
                listener = state -> notify.run();
                stateListeners.add(listener);
            }

            @Override
            public void removeListener() {
                stateListeners.remove(listener);
                listener = null;
            }

            @Override
            public Duration getTimeout() {
                return timeout;
            }
        });
    }

    private void tryInitialize() throws Exception {
        if (remoteStorageService.getState() == RemoteStorageService.State.NotReady) {
            deinitialize();
            return;
        }

        storagePort = remoteStorageService.getGroundItemOwnedByStoragePort();
        storagePort.addListener(storagePortListener);

        storagePort.readAll().whenComplete((map, throwable) -> {
            if (throwable != null) {
                log.error("GroundItemOwnedByDataProvider storageport read all failed", throwable);
                return;
            }

            groundItemOwnedByMap = new ConcurrentHashMap<>();
            // readAll doesn't give us entry keys, sync from adapter cache instead
            syncFromAdapterCache();
            setState(State.Ready);
        });
    }

    private void syncFromAdapterCache() {
        if (storagePort instanceof com.elertan.remote.firebase.FirebaseKeyListStorageAdapterBase) {
            @SuppressWarnings("unchecked")
            com.elertan.remote.firebase.FirebaseKeyListStorageAdapterBase<GroundItemOwnedByKey, GroundItemOwnedByData> adapter =
                (com.elertan.remote.firebase.FirebaseKeyListStorageAdapterBase<GroundItemOwnedByKey, GroundItemOwnedByData>) storagePort;
            ConcurrentHashMap<GroundItemOwnedByKey, ConcurrentHashMap<String, GroundItemOwnedByData>> cache = adapter.getLocalCache();
            groundItemOwnedByMap = new ConcurrentHashMap<>();
            for (Map.Entry<GroundItemOwnedByKey, ConcurrentHashMap<String, GroundItemOwnedByData>> entry : cache.entrySet()) {
                groundItemOwnedByMap.put(entry.getKey(), new ConcurrentHashMap<>(entry.getValue()));
            }
        }
    }

    private void deinitialize() throws Exception {
        setState(State.NotReady);

        if (storagePort != null) {
            storagePort.removeListener(storagePortListener);
            storagePort.close();
            storagePort = null;
        }

        groundItemOwnedByMap = null;
    }

    public CompletableFuture<String> addEntry(GroundItemOwnedByKey key, GroundItemOwnedByData data) {
        if (storagePort == null) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("storagePort is null"));
            return future;
        }

        return storagePort.add(key, data);
    }

    public CompletableFuture<Void> removeOneEntry(GroundItemOwnedByKey key) {
        if (storagePort == null) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("storagePort is null"));
            return future;
        }

        return storagePort.removeOne(key);
    }

    public CompletableFuture<Void> removeEntry(GroundItemOwnedByKey key, String entryKey) {
        if (storagePort == null) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("storagePort is null"));
            return future;
        }

        return storagePort.remove(key, entryKey);
    }

    public boolean hasEntries(GroundItemOwnedByKey key) {
        if (groundItemOwnedByMap == null) {
            return false;
        }
        ConcurrentHashMap<String, GroundItemOwnedByData> innerMap = groundItemOwnedByMap.get(key);
        return innerMap != null && !innerMap.isEmpty();
    }

    public ConcurrentHashMap<String, GroundItemOwnedByData> getEntries(GroundItemOwnedByKey key) {
        if (groundItemOwnedByMap == null) {
            return null;
        }
        return groundItemOwnedByMap.get(key);
    }

    public enum State {
        NotReady,
        Ready
    }

    private void setState(State state) {
        if (state == this.state) {
            return;
        }
        this.state = state;

        for (Consumer<State> listener : stateListeners) {
            try {
                listener.accept(state);
            } catch (Exception e) {
                log.error("set state listener GroundItemOwnedByDataProvider error", e);
            }
        }
    }

    public interface Listener {
        void onReadAll(ConcurrentHashMap<GroundItemOwnedByKey, ConcurrentHashMap<String, GroundItemOwnedByData>> map);
        void onAdd(GroundItemOwnedByKey key, String entryKey, GroundItemOwnedByData value);
        void onRemove(GroundItemOwnedByKey key, String entryKey);
    }
}
