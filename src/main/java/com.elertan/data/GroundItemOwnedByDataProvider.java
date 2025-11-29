package com.elertan.data;

import com.elertan.BUPluginLifecycle;
import com.elertan.models.GroundItemOwnedByData;
import com.elertan.models.GroundItemOwnedByKey;
import com.elertan.remote.KeyValueStoragePort;
import com.elertan.remote.RemoteStorageService;
import com.elertan.utils.ListenerUtils;
import com.elertan.utils.StateListenerManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
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

    private final ConcurrentLinkedQueue<Listener> maplisteners = new ConcurrentLinkedQueue<>();
    private final StateListenerManager<State> stateListeners = new StateListenerManager<>("GroundItemOwnedByDataProvider");
    @Inject
    private RemoteStorageService remoteStorageService;
    private KeyValueStoragePort<GroundItemOwnedByKey, GroundItemOwnedByData> storagePort;
    private KeyValueStoragePort.Listener<GroundItemOwnedByKey, GroundItemOwnedByData> storagePortListener;
    @Getter
    private State state = State.NotReady;
    @Getter
    private ConcurrentHashMap<GroundItemOwnedByKey, GroundItemOwnedByData> groundItemOwnedByMap;
    private final Consumer<RemoteStorageService.State> remoteStorageServiceStateListener = this::remoteStorageServiceStateListener;

    @Override
    public void startUp() throws Exception {
        remoteStorageService.addStateListener(remoteStorageServiceStateListener);

        storagePortListener = new KeyValueStoragePort.Listener<GroundItemOwnedByKey, GroundItemOwnedByData>() {
            @Override
            public void onFullUpdate(Map<GroundItemOwnedByKey, GroundItemOwnedByData> map) {
                groundItemOwnedByMap = new ConcurrentHashMap<>(map);

                Map<GroundItemOwnedByKey, GroundItemOwnedByData> unmodifiableMap = Collections.unmodifiableMap(
                    map);
                for (Listener listener : maplisteners) {
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
            public void onUpdate(GroundItemOwnedByKey key, GroundItemOwnedByData value) {
                if (groundItemOwnedByMap == null) {
                    return;
                }

                if (value == null) {
                    groundItemOwnedByMap.remove(key);
                } else {
                    groundItemOwnedByMap.put(key, value);
                }

                for (Listener listener : maplisteners) {
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
                for (Listener listener : maplisteners) {
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

    public void addMapListener(Listener listener) {
        maplisteners.add(listener);
    }

    public void removeMapListener(Listener listener) {
        maplisteners.remove(listener);
    }

    public void addStateListener(Consumer<State> listener) {
        stateListeners.addListener(listener);
    }

    public void removeStateListener(Consumer<State> listener) {
        stateListeners.removeListener(listener);
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
                stateListeners.addListener(listener);
            }

            @Override
            public void removeListener() {
                stateListeners.removeListener(listener);
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

            groundItemOwnedByMap = new ConcurrentHashMap<>(map);
            setState(State.Ready);
        });
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

    public CompletableFuture<Void> update(GroundItemOwnedByKey key,
        GroundItemOwnedByData newGroundItemOwnedByData) {
        if (storagePort == null) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            Exception ex = new IllegalStateException("storagePort is null");
            future.completeExceptionally(ex);
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
            Exception ex = new IllegalStateException("storagePort is null");
            future.completeExceptionally(ex);
            return future;
        }

        if (groundItemOwnedByMap != null) {
            groundItemOwnedByMap.remove(key);
        }

        return storagePort.delete(key);
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
        stateListeners.notifyListeners(state);
    }

    public interface Listener {

        void onReadAll(Map<GroundItemOwnedByKey, GroundItemOwnedByData> map);

        void onUpdate(GroundItemOwnedByKey key, GroundItemOwnedByData value);

        void onDelete(GroundItemOwnedByKey key);
    }
}
