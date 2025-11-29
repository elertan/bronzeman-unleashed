package com.elertan.data;

import com.elertan.models.UnlockedItem;
import com.elertan.remote.KeyValueStoragePort;
import com.elertan.remote.RemoteStorageService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class UnlockedItemsDataProvider extends AbstractDataProvider {

    private final ConcurrentLinkedQueue<UnlockedItemsMapListener> unlockedItemsMapListeners = new ConcurrentLinkedQueue<>();

    @Inject
    private RemoteStorageService remoteStorageService;

    private KeyValueStoragePort<Integer, UnlockedItem> keyValueStoragePort;
    private KeyValueStoragePort.Listener<Integer, UnlockedItem> storagePortListener;
    private ConcurrentHashMap<Integer, UnlockedItem> unlockedItemsMap;

    public UnlockedItemsDataProvider() {
        super("UnlockedItemsDataProvider");
    }

    @Override
    protected RemoteStorageService getRemoteStorageService() {
        return remoteStorageService;
    }

    @Override
    public void startUp() throws Exception {
        storagePortListener = new KeyValueStoragePort.Listener<Integer, UnlockedItem>() {
            @Override
            public void onFullUpdate(Map<Integer, UnlockedItem> map) {
                if (unlockedItemsMap == null) {
                    return;
                }
                unlockedItemsMap = new ConcurrentHashMap<>(map);
            }

            @Override
            public void onUpdate(Integer key, UnlockedItem newUnlockedItem) {
                if (unlockedItemsMap == null) {
                    return;
                }
                unlockedItemsMap.put(key, newUnlockedItem);

                for (UnlockedItemsMapListener listener : unlockedItemsMapListeners) {
                    try {
                        listener.onUpdate(newUnlockedItem);
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
                UnlockedItem unlockedItem = unlockedItemsMap.get(key);
                unlockedItemsMap.remove(key);

                for (UnlockedItemsMapListener listener : unlockedItemsMapListeners) {
                    try {
                        listener.onDelete(unlockedItem);
                    } catch (Exception ex) {
                        log.error("unlockedItemDeleteListener: onDelete", ex);
                    }
                }
            }
        };
        super.startUp();
    }

    @Override
    protected void onRemoteStorageReady() {
        keyValueStoragePort = remoteStorageService.getUnlockedItemsStoragePort();
        keyValueStoragePort.addListener(storagePortListener);

        keyValueStoragePort.readAll().whenComplete((map, throwable) -> {
            if (throwable != null) {
                log.error("UnlockedItemDataProvider storageport read all failed", throwable);
                return;
            }
            unlockedItemsMap = new ConcurrentHashMap<>(map);
            log.debug("UnlockedItemDataProvider initialized with {} items", unlockedItemsMap.size());
            setState(State.Ready);
        });
    }

    @Override
    protected void onRemoteStorageNotReady() {
        unlockedItemsMap = null;
        if (keyValueStoragePort != null) {
            keyValueStoragePort.removeListener(storagePortListener);
            keyValueStoragePort = null;
        }
    }

    public Map<Integer, UnlockedItem> getUnlockedItemsMap() {
        if (unlockedItemsMap == null) {
            return null;
        }
        return Collections.unmodifiableMap(unlockedItemsMap);
    }

    public void addUnlockedItemsMapListener(UnlockedItemsMapListener listener) {
        unlockedItemsMapListeners.add(listener);
    }

    public void removeUnlockedItemsMapListener(UnlockedItemsMapListener listener) {
        unlockedItemsMapListeners.remove(listener);
    }

    public CompletableFuture<Void> addUnlockedItem(UnlockedItem unlockedItem) {
        if (getState() != State.Ready) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("State is not ready"));
            return future;
        }
        unlockedItemsMap.put(unlockedItem.getId(), unlockedItem);
        return keyValueStoragePort.update(unlockedItem.getId(), unlockedItem);
    }

    public CompletableFuture<Void> removeUnlockedItemById(int itemId) {
        if (getState() != State.Ready) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("State is not ready"));
            return future;
        }
        return keyValueStoragePort.delete(itemId);
    }

    public interface UnlockedItemsMapListener {
        void onUpdate(UnlockedItem unlockedItem);
        void onDelete(UnlockedItem unlockedItem);
    }
}
