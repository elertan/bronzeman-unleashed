package com.elertan.data;

import com.elertan.BUPluginLifecycle;
import com.elertan.models.AccountHash;
import com.elertan.models.GroundItemOwnedByKey;
import com.elertan.remote.KeyValueStoragePort;
import com.elertan.remote.RemoteStorageService;
import com.elertan.remote.RemoteStorageService.State;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
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

    private final ConcurrentLinkedQueue<Listener> listeners = new ConcurrentLinkedQueue<>();
    @Inject
    private RemoteStorageService remoteStorageService;
    private KeyValueStoragePort<GroundItemOwnedByKey, AccountHash> storagePort;
    private KeyValueStoragePort.Listener<GroundItemOwnedByKey, AccountHash> storagePortListener;
    @Getter
    private ConcurrentHashMap<GroundItemOwnedByKey, AccountHash> groundItemOwnedByMap;
    private final Consumer<State> remoteStorageServiceStateListener = this::remoteStorageServiceStateListener;

    @Override
    public void startUp() throws Exception {
        remoteStorageService.addStateListener(remoteStorageServiceStateListener);

        storagePortListener = new KeyValueStoragePort.Listener<GroundItemOwnedByKey, AccountHash>() {
            @Override
            public void onFullUpdate(Map<GroundItemOwnedByKey, AccountHash> map) {
                groundItemOwnedByMap = new ConcurrentHashMap<>(map);

                Map<GroundItemOwnedByKey, AccountHash> unmodifiableMap = Collections.unmodifiableMap(
                    map);
                for (Listener listener : listeners) {
                    try {
                        listener.onReadAll(unmodifiableMap);
                    } catch (Exception e) {
                        log.error(
                            "Error while notifying listener on GroundItemOwnedByDataProvider.",
                            e
                        );
                    }
                }
            }

            @Override
            public void onUpdate(GroundItemOwnedByKey key, AccountHash value) {
                if (groundItemOwnedByMap == null) {
                    return;
                }

                if (value == null) {
                    groundItemOwnedByMap.remove(key);
                } else {
                    groundItemOwnedByMap.put(key, value);
                }

                for (Listener listener : listeners) {
                    try {
                        listener.onUpdate(key, value);
                    } catch (Exception e) {
                        log.error(
                            "Error while notifying listener on GroundItemOwnedByDataProvider.",
                            e
                        );
                    }
                }
            }

            @Override
            public void onDelete(GroundItemOwnedByKey key) {
                if (groundItemOwnedByMap == null) {
                    return;
                }
                groundItemOwnedByMap.remove(key);
                for (Listener listener : listeners) {
                    try {
                        listener.onDelete(key);
                    } catch (Exception e) {
                        log.error(
                            "Error while notifying listener on GroundItemOwnedByDataProvider.",
                            e
                        );
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

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
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

            groundItemOwnedByMap = new ConcurrentHashMap<>(map);
        });
    }

    private void deinitialize() throws Exception {
        if (storagePort != null) {
            storagePort.removeListener(storagePortListener);
            storagePort.close();
            storagePort = null;
        }

        groundItemOwnedByMap = null;
    }

    public CompletableFuture<Void> update(GroundItemOwnedByKey key, AccountHash newAccountHash) {
        if (storagePort == null) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            Exception ex = new IllegalStateException("storagePort is null");
            future.completeExceptionally(ex);
            return future;
        }

        if (groundItemOwnedByMap != null) {
            groundItemOwnedByMap.put(key, newAccountHash);
        }

        return storagePort.update(key, newAccountHash);
    }

    public CompletableFuture<Void> delete(GroundItemOwnedByKey key) {
        if (storagePort == null) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            Exception ex = new IllegalStateException("storagePort is null");
            future.completeExceptionally(ex);
            return future;
        }

        if (groundItemOwnedByMap != null) {
            groundItemOwnedByMap.remove(key);
        }

        return storagePort.delete(key);
    }

    public interface Listener {

        void onReadAll(Map<GroundItemOwnedByKey, AccountHash> map);

        void onUpdate(GroundItemOwnedByKey key, AccountHash value);

        void onDelete(GroundItemOwnedByKey key);
    }
}
