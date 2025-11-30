package com.elertan.data;

import com.elertan.models.GroundItemOwnedByData;
import com.elertan.models.GroundItemOwnedByKey;
import com.elertan.remote.KeyListStoragePort;
import com.elertan.remote.RemoteStorageService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class GroundItemOwnedByDataProvider extends AbstractDataProvider {

    private final ConcurrentLinkedQueue<Listener> mapListeners = new ConcurrentLinkedQueue<>();

    @Inject
    private RemoteStorageService remoteStorageService;

    private KeyListStoragePort<GroundItemOwnedByKey, GroundItemOwnedByData> storagePort;
    private KeyListStoragePort.Listener<GroundItemOwnedByKey, GroundItemOwnedByData> storagePortListener;

    @Getter
    private ConcurrentHashMap<GroundItemOwnedByKey, ConcurrentHashMap<String, GroundItemOwnedByData>> groundItemOwnedByMap;

    public GroundItemOwnedByDataProvider() {
        super("GroundItemOwnedByDataProvider");
    }

    @Override
    protected RemoteStorageService getRemoteStorageService() {
        return remoteStorageService;
    }

    @Override
    public void startUp() throws Exception {
        storagePortListener = new KeyListStoragePort.Listener<GroundItemOwnedByKey, GroundItemOwnedByData>() {
            @Override
            public void onFullUpdate(Map<GroundItemOwnedByKey, Map<String, GroundItemOwnedByData>> map) {
                groundItemOwnedByMap = new ConcurrentHashMap<>();
                for (Map.Entry<GroundItemOwnedByKey, Map<String, GroundItemOwnedByData>> entry : map.entrySet()) {
                    groundItemOwnedByMap.put(entry.getKey(), new ConcurrentHashMap<>(entry.getValue()));
                }

                for (Listener listener : mapListeners) {
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

                for (Listener listener : mapListeners) {
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

                for (Listener listener : mapListeners) {
                    try {
                        listener.onRemove(key, entryKey);
                    } catch (Exception e) {
                        log.error("Error while notifying listener on GroundItemOwnedByDataProvider.", e);
                    }
                }
            }
        };
        super.startUp();
    }

    @Override
    protected void onRemoteStorageReady() {
        storagePort = remoteStorageService.getGroundItemOwnedByStoragePort();
        storagePort.addListener(storagePortListener);

        storagePort.readAll().whenComplete((map, throwable) -> {
            if (throwable != null) {
                log.error("GroundItemOwnedByDataProvider storageport read all failed", throwable);
                return;
            }

            groundItemOwnedByMap = new ConcurrentHashMap<>();
            for (Map.Entry<GroundItemOwnedByKey, Map<String, GroundItemOwnedByData>> entry : map.entrySet()) {
                groundItemOwnedByMap.put(entry.getKey(), new ConcurrentHashMap<>(entry.getValue()));
            }
            setState(State.Ready);
        });
    }

    @Override
    protected void onRemoteStorageNotReady() {
        groundItemOwnedByMap = null;
        if (storagePort != null) {
            storagePort.removeListener(storagePortListener);
            try {
                storagePort.close();
            } catch (Exception e) {
                log.error("Error closing storagePort", e);
            }
            storagePort = null;
        }
    }

    public void addMapListener(Listener listener) {
        mapListeners.add(listener);
    }

    public void removeMapListener(Listener listener) {
        mapListeners.remove(listener);
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

    public interface Listener {
        void onReadAll(ConcurrentHashMap<GroundItemOwnedByKey, ConcurrentHashMap<String, GroundItemOwnedByData>> map);
        void onAdd(GroundItemOwnedByKey key, String entryKey, GroundItemOwnedByData value);
        void onRemove(GroundItemOwnedByKey key, String entryKey);
    }
}
