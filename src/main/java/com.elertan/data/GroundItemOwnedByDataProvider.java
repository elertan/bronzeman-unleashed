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
    @Inject private RemoteStorageService remoteStorageService;
    private KeyListStoragePort<GroundItemOwnedByKey, GroundItemOwnedByData> storagePort;
    private KeyListStoragePort.Listener<GroundItemOwnedByKey, GroundItemOwnedByData> storagePortListener;
    @Getter private ConcurrentHashMap<GroundItemOwnedByKey, ConcurrentHashMap<String, GroundItemOwnedByData>> groundItemOwnedByMap;

    @Override
    protected RemoteStorageService getRemoteStorageService() { return remoteStorageService; }

    private ConcurrentHashMap<GroundItemOwnedByKey, ConcurrentHashMap<String, GroundItemOwnedByData>> buildMap(
            Map<GroundItemOwnedByKey, Map<String, GroundItemOwnedByData>> map) {
        ConcurrentHashMap<GroundItemOwnedByKey, ConcurrentHashMap<String, GroundItemOwnedByData>> result = new ConcurrentHashMap<>();
        map.forEach((k, v) -> result.put(k, new ConcurrentHashMap<>(v)));
        return result;
    }

    @Override
    public void startUp() throws Exception {
        storagePortListener = new KeyListStoragePort.Listener<GroundItemOwnedByKey, GroundItemOwnedByData>() {
            @Override
            public void onFullUpdate(Map<GroundItemOwnedByKey, Map<String, GroundItemOwnedByData>> map) {
                groundItemOwnedByMap = buildMap(map);
                notifyListeners(mapListeners, l -> l.onReadAll(groundItemOwnedByMap));
            }
            @Override
            public void onAdd(GroundItemOwnedByKey key, String entryKey, GroundItemOwnedByData value) {
                if (groundItemOwnedByMap == null) return;
                groundItemOwnedByMap.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(entryKey, value);
                notifyListeners(mapListeners, l -> l.onAdd(key, entryKey, value));
            }
            @Override
            public void onRemove(GroundItemOwnedByKey key, String entryKey) {
                if (groundItemOwnedByMap == null) return;
                ConcurrentHashMap<String, GroundItemOwnedByData> innerMap = groundItemOwnedByMap.get(key);
                if (innerMap != null) {
                    innerMap.remove(entryKey);
                    if (innerMap.isEmpty()) groundItemOwnedByMap.remove(key);
                }
                notifyListeners(mapListeners, l -> l.onRemove(key, entryKey));
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
            groundItemOwnedByMap = buildMap(map);
            setState(State.Ready);
        });
    }

    @Override
    protected void onRemoteStorageNotReady() {
        groundItemOwnedByMap = null;
        if (storagePort != null) {
            storagePort.removeListener(storagePortListener);
            try { storagePort.close(); } catch (Exception e) { log.error("Error closing storagePort", e); }
            storagePort = null;
        }
    }

    public void addMapListener(Listener listener) { mapListeners.add(listener); }
    public void removeMapListener(Listener listener) { mapListeners.remove(listener); }

    public CompletableFuture<String> addEntry(GroundItemOwnedByKey key, GroundItemOwnedByData data) {
        if (storagePort == null) return CompletableFuture.failedFuture(new IllegalStateException("storagePort is null"));
        return storagePort.add(key, data);
    }

    public CompletableFuture<Void> removeOneEntry(GroundItemOwnedByKey key) {
        if (storagePort == null) return CompletableFuture.failedFuture(new IllegalStateException("storagePort is null"));
        return storagePort.removeOne(key);
    }

    public CompletableFuture<Void> removeEntry(GroundItemOwnedByKey key, String entryKey) {
        if (storagePort == null) return CompletableFuture.failedFuture(new IllegalStateException("storagePort is null"));
        return storagePort.remove(key, entryKey);
    }

    public boolean hasEntries(GroundItemOwnedByKey key) {
        if (groundItemOwnedByMap == null) return false;
        ConcurrentHashMap<String, GroundItemOwnedByData> innerMap = groundItemOwnedByMap.get(key);
        return innerMap != null && !innerMap.isEmpty();
    }

    public ConcurrentHashMap<String, GroundItemOwnedByData> getEntries(GroundItemOwnedByKey key) {
        return groundItemOwnedByMap == null ? null : groundItemOwnedByMap.get(key);
    }

    public interface Listener {
        void onReadAll(ConcurrentHashMap<GroundItemOwnedByKey, ConcurrentHashMap<String, GroundItemOwnedByData>> map);
        void onAdd(GroundItemOwnedByKey key, String entryKey, GroundItemOwnedByData value);
        void onRemove(GroundItemOwnedByKey key, String entryKey);
    }
}
