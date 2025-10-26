package com.elertan.data;

import com.elertan.BUPluginLifecycle;
import com.elertan.models.UnlockedItem;
import com.elertan.remote.RemoteStorageService;
import com.elertan.remote.KeyValueStoragePort;
import com.elertan.utils.ListenerUtils;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

@Slf4j
@Singleton
public class UnlockedItemsDataProvider implements BUPluginLifecycle {
    public enum State {
        NotReady,
        Ready,
    }

    public interface UnlockedItemsMapListener {
        void onUpdate(UnlockedItem unlockedItem);

        void onDelete(int itemId);
    }

    @Inject
    private RemoteStorageService remoteStorageService;

    @Getter
    private State state = State.NotReady;
    private ConcurrentLinkedQueue<Consumer<State>> stateListeners = new ConcurrentLinkedQueue<>();
    private KeyValueStoragePort<Integer, UnlockedItem> keyValueStoragePort;

    private KeyValueStoragePort.Listener<Integer, UnlockedItem> unlockedItemsStoragePortListener;
    private ConcurrentHashMap<Integer, UnlockedItem> unlockedItemsMap;

    private ConcurrentLinkedQueue<UnlockedItemsMapListener> unlockedItemsMapListeners = new ConcurrentLinkedQueue<>();

    @Override
    public void startUp() throws Exception {
        remoteStorageService.addStateListener(this::remoteStorageServiceStateListener);

        unlockedItemsStoragePortListener = new KeyValueStoragePort.Listener<Integer, UnlockedItem>() {
            @Override
            public void onFullUpdate(Map<Integer, UnlockedItem> map) {
                if (unlockedItemsMap == null) {
                    return;
                }
                unlockedItemsMap = new ConcurrentHashMap<>(map);
            }

            @Override
            public void onUpdate(Integer key, UnlockedItem value) {
                if (unlockedItemsMap == null) {
                    return;
                }
                unlockedItemsMap.put(key, value);

                for (UnlockedItemsMapListener listener : unlockedItemsMapListeners) {
                    try {
                        listener.onUpdate(value);
                    } catch (Exception ex) {
                        log.error("unlockedItemUpdateListener: onUpdate", ex);
                    }
                }
            }

            @Override
            public void onDelete(Integer key) {
                if (unlockedItemsMap == null) {
                    return;
                }
                unlockedItemsMap.remove(key);

                for (UnlockedItemsMapListener listener : unlockedItemsMapListeners) {
                    try {
                        listener.onDelete(key);
                    }  catch (Exception ex) {
                        log.error("unlockedItemDeleteListener: onDelete", ex);
                    }
                }
            }
        };

        tryInitialize();
    }

    @Override
    public void shutDown() throws Exception {
        unlockedItemsMap = null;
        state = State.NotReady;
        unlockedItemsStoragePortListener = null;
        keyValueStoragePort = null;

        remoteStorageService.removeStateListener(this::remoteStorageServiceStateListener);
    }

    public Map<Integer, UnlockedItem> getUnlockedItemsMap() {
        return Collections.unmodifiableMap(unlockedItemsMap);
    }

    public void addStateListener(Consumer<State> listener) {
        stateListeners.add(listener);
    }

    public void removeStateListener(Consumer<State> listener) {
        stateListeners.remove(listener);
    }

    public void addUnlockedItemsMapListener(UnlockedItemsMapListener listener) {
        unlockedItemsMapListeners.add(listener);
    }

    public void removeUnlockedItemsMapListener(UnlockedItemsMapListener listener) {
        unlockedItemsMapListeners.remove(listener);
    }

    public CompletableFuture<Void> waitUntilReady(Duration timeout) {
        return ListenerUtils.waitUntilReady(new ListenerUtils.WaitUntilReadyContext() {
            Consumer<State> stateConsumer;

            @Override
            public boolean isReady() {
                return getState() == State.Ready;
            }

            @Override
            public void addListener(Runnable notify) {
                stateConsumer = state -> {
                    notify.run();
                };
                addStateListener(stateConsumer);
            }

            @Override
            public void removeListener() {
                if (stateConsumer == null) {
                    return;
                }
                removeStateListener(stateConsumer);
            }

            @Override
            public Duration getTimeout() {
                return timeout;
            }
        });
    }

    public CompletableFuture<Void> addUnlockedItem(UnlockedItem unlockedItem) {
        if (state != State.Ready) {
            Exception ex = new IllegalStateException("State is not ready");
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(ex);
            return future;
        }

        unlockedItemsMap.put(unlockedItem.getId(), unlockedItem);
        return keyValueStoragePort.update(unlockedItem.getId(), unlockedItem);
    }

    private void remoteStorageServiceStateListener(RemoteStorageService.State state) {
        if (state == RemoteStorageService.State.NotReady) {
            unlockedItemsMap = null;
            keyValueStoragePort = null;
            setState(State.NotReady);
            return;
        }

        tryInitialize();
    }

    private void tryInitialize() {
        if (remoteStorageService.getState() == RemoteStorageService.State.NotReady) {
            unlockedItemsMap = null;
            keyValueStoragePort = null;
            setState(State.NotReady);
            return;
        }

        keyValueStoragePort = remoteStorageService.getUnlockedItemsStoragePort();
        keyValueStoragePort.addListener(unlockedItemsStoragePortListener);

        keyValueStoragePort.readAll().whenComplete((map, throwable) -> {
            if (throwable != null) {
                log.error("UnlockedItemDataProvider storageport read all failed", throwable);
                return;
            }

            unlockedItemsMap = new ConcurrentHashMap<>(map);
            log.info("UnlockedItemDataProvider initialized with {} items", unlockedItemsMap.size());
            setState(State.Ready);
        });
    }

    private void setState(State state) {
        if (this.state == state) {
            return;
        }
        this.state = state;

        for (Consumer<State> listener : stateListeners) {
            try {
                listener.accept(state);
            } catch (Exception e) {
                log.error("set state listener unlocked item data provider error", e);
            }
        }
    }
}
