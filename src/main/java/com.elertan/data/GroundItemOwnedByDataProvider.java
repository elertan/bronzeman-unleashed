package com.elertan.data;

import com.elertan.models.GroundItemOwnedByData;
import com.elertan.models.GroundItemOwnedByKey;
import com.elertan.remote.KeyValueStoragePort;
import com.elertan.remote.RemoteStorageService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
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

    private KeyValueStoragePort<GroundItemOwnedByKey, GroundItemOwnedByData> storagePort;
    private KeyValueStoragePort.Listener<GroundItemOwnedByKey, GroundItemOwnedByData> storagePortListener;

    @Getter
    private ConcurrentHashMap<GroundItemOwnedByKey, GroundItemOwnedByData> groundItemOwnedByMap;

    public GroundItemOwnedByDataProvider() {
        super("GroundItemOwnedByDataProvider");
    }

    @Override
    protected RemoteStorageService getRemoteStorageService() {
        return remoteStorageService;
    }

    @Override
    public void startUp() throws Exception {
        storagePortListener = new KeyValueStoragePort.Listener<GroundItemOwnedByKey, GroundItemOwnedByData>() {
            @Override
            public void onFullUpdate(Map<GroundItemOwnedByKey, GroundItemOwnedByData> map) {
                groundItemOwnedByMap = new ConcurrentHashMap<>(map);
                Map<GroundItemOwnedByKey, GroundItemOwnedByData> unmodifiableMap = Collections.unmodifiableMap(map);
                for (Listener listener : mapListeners) {
                    try {
                        listener.onReadAll(unmodifiableMap);
                    } catch (Exception e) {
                        log.error("Error while notifying listener on GroundItemOwnedByDataProvider.", e);
                    }
                }
            }

            @Override
            public void onUpdate(GroundItemOwnedByKey key, GroundItemOwnedByData value) {
                if (groundItemOwnedByMap == null) {
                    return;
                }
                if (value == null) {
                    groundItemOwnedByMap.remove(key);
                } else {
                    groundItemOwnedByMap.put(key, value);
                }
                for (Listener listener : mapListeners) {
                    try {
                        listener.onUpdate(key, value);
                    } catch (Exception e) {
                        log.error("Error while notifying listener on GroundItemOwnedByDataProvider.", e);
                    }
                }
            }

            @Override
            public void onDelete(GroundItemOwnedByKey key) {
                if (groundItemOwnedByMap == null) {
                    return;
                }
                groundItemOwnedByMap.remove(key);
                for (Listener listener : mapListeners) {
                    try {
                        listener.onDelete(key);
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
            groundItemOwnedByMap = new ConcurrentHashMap<>(map);
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

    public CompletableFuture<Void> update(GroundItemOwnedByKey key, GroundItemOwnedByData newGroundItemOwnedByData) {
        if (storagePort == null) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("storagePort is null"));
            return future;
        }
        if (groundItemOwnedByMap != null) {
            groundItemOwnedByMap.put(key, newGroundItemOwnedByData);
        }
        return storagePort.update(key, newGroundItemOwnedByData);
    }

    public CompletableFuture<Void> delete(GroundItemOwnedByKey key) {
        if (storagePort == null) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("storagePort is null"));
            return future;
        }
        if (groundItemOwnedByMap != null) {
            groundItemOwnedByMap.remove(key);
        }
        return storagePort.delete(key);
    }

    public interface Listener {
        void onReadAll(Map<GroundItemOwnedByKey, GroundItemOwnedByData> map);
        void onUpdate(GroundItemOwnedByKey key, GroundItemOwnedByData value);
        void onDelete(GroundItemOwnedByKey key);
    }
}
