package com.elertan.data;

import com.elertan.models.GroundItemOwnedByData;
import com.elertan.models.GroundItemOwnedByKey;
import com.elertan.remote.KeyValueStoragePort;
import com.elertan.remote.StorageService;
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
    private StorageService storageService;

    private KeyValueStoragePort<GroundItemOwnedByKey, GroundItemOwnedByData> storagePort;
    private KeyValueStoragePort.Listener<GroundItemOwnedByKey, GroundItemOwnedByData> storagePortListener;

    /**
     * One {@link GroundItemOwnedByData} per {@link GroundItemOwnedByKey} (one Firebase object per pile).
     */
    @Getter
    private ConcurrentHashMap<GroundItemOwnedByKey, GroundItemOwnedByData> groundItemOwnedByMap;

    @Override
    protected StorageService getStorageService() {
        return storageService;
    }

    @Override
    public void startUp() throws Exception {
        storagePortListener = new KeyValueStoragePort.Listener<GroundItemOwnedByKey, GroundItemOwnedByData>() {
            @Override
            public void onFullUpdate(Map<GroundItemOwnedByKey, GroundItemOwnedByData> map) {
                groundItemOwnedByMap = new ConcurrentHashMap<>();
                if (map != null) {
                    groundItemOwnedByMap.putAll(map);
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
            public void onUpdate(GroundItemOwnedByKey key, GroundItemOwnedByData value) {
                if (groundItemOwnedByMap == null) {
                    return;
                }

                groundItemOwnedByMap.put(key, value);

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
        storagePort = storageService.getGroundItemOwnedByStoragePort();
        storagePort.addListener(storagePortListener);

        storagePort.readAll().whenComplete((map, throwable) -> {
            if (throwable != null) {
                log.error("GroundItemOwnedByDataProvider storageport read all failed", throwable);
                return;
            }

            groundItemOwnedByMap = new ConcurrentHashMap<>();
            if (map != null) {
                groundItemOwnedByMap.putAll(map);
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

    public CompletableFuture<Void> updatePile(GroundItemOwnedByKey key, GroundItemOwnedByData data) {
        if (storagePort == null) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("storagePort is null"));
            return future;
        }

        if (groundItemOwnedByMap != null) {
            groundItemOwnedByMap.put(key, data);
        }
        return storagePort.update(key, data);
    }

    public CompletableFuture<Void> deletePile(GroundItemOwnedByKey key) {
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

    public GroundItemOwnedByData getPile(GroundItemOwnedByKey key) {
        if (groundItemOwnedByMap == null) {
            return null;
        }
        return groundItemOwnedByMap.get(key);
    }

    public boolean hasEntries(GroundItemOwnedByKey key) {
        return getPile(key) != null;
    }

    public int getTotalOwnedQuantity(GroundItemOwnedByKey key) {
        GroundItemOwnedByData data = getPile(key);
        if (data == null) {
            return 0;
        }
        return data.getQuantityOrDefaultOne();
    }

    public CompletableFuture<Void> consumeQuantity(GroundItemOwnedByKey key, int quantity) {
        if (quantity <= 0) {
            return CompletableFuture.completedFuture(null);
        }
        if (storagePort == null) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("storagePort is null"));
            return future;
        }

        GroundItemOwnedByData current = getPile(key);
        if (current == null) {
            return CompletableFuture.completedFuture(null);
        }

        int entryQty = current.getQuantityOrDefaultOne();
        int newQty = Math.max(0, entryQty - quantity);
        if (newQty <= 0) {
            if (groundItemOwnedByMap != null) {
                groundItemOwnedByMap.remove(key);
            }
            return storagePort.delete(key);
        }

        GroundItemOwnedByData replacement = new GroundItemOwnedByData(
            current.getAccountHash(),
            current.getDespawnsAt(),
            newQty,
            current.getDroppedByPlayerName()
        );

        if (groundItemOwnedByMap != null) {
            groundItemOwnedByMap.put(key, replacement);
        }
        return storagePort.update(key, replacement);
    }

    public interface Listener {
        void onReadAll(ConcurrentHashMap<GroundItemOwnedByKey, GroundItemOwnedByData> map);

        void onUpdate(GroundItemOwnedByKey key, GroundItemOwnedByData value);

        void onDelete(GroundItemOwnedByKey key);
    }
}
