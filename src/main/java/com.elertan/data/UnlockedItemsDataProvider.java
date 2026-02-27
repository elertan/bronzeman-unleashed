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
    @Inject private RemoteStorageService remoteStorageService;
    private KeyValueStoragePort<Integer, UnlockedItem> keyValueStoragePort;
    private KeyValueStoragePort.Listener<Integer, UnlockedItem> storagePortListener;
    private ConcurrentHashMap<Integer, UnlockedItem> unlockedItemsMap;

    @Override
    protected RemoteStorageService getRemoteStorageService() { return remoteStorageService; }

    @Override
    public void startUp() throws Exception {
        storagePortListener = new KeyValueStoragePort.Listener<Integer, UnlockedItem>() {
            @Override
            public void onFullUpdate(Map<Integer, UnlockedItem> map) {
                if (unlockedItemsMap == null) return;
                unlockedItemsMap = new ConcurrentHashMap<>(map);
            }
            @Override
            public void onUpdate(Integer key, UnlockedItem newUnlockedItem) {
                if (unlockedItemsMap == null) return;
                unlockedItemsMap.put(key, newUnlockedItem);
                notifyListeners(unlockedItemsMapListeners, l -> l.onUpdate(newUnlockedItem));
            }
            @Override
            public void onDelete(Integer key) {
                if (unlockedItemsMap == null) return;
                UnlockedItem unlockedItem = unlockedItemsMap.remove(key);
                notifyListeners(unlockedItemsMapListeners, l -> l.onDelete(unlockedItem));
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
        return unlockedItemsMap == null ? null : Collections.unmodifiableMap(unlockedItemsMap);
    }

    public void addUnlockedItemsMapListener(UnlockedItemsMapListener listener) { unlockedItemsMapListeners.add(listener); }
    public void removeUnlockedItemsMapListener(UnlockedItemsMapListener listener) { unlockedItemsMapListeners.remove(listener); }

    public CompletableFuture<Void> addUnlockedItem(UnlockedItem unlockedItem) {
        if (getState().get() != State.Ready) return CompletableFuture.failedFuture(new IllegalStateException("State is not ready"));
        unlockedItemsMap.put(unlockedItem.getId(), unlockedItem);
        return keyValueStoragePort.update(unlockedItem.getId(), unlockedItem);
    }

    public CompletableFuture<Void> removeUnlockedItemById(int itemId) {
        if (getState().get() != State.Ready) return CompletableFuture.failedFuture(new IllegalStateException("State is not ready"));
        return keyValueStoragePort.delete(itemId);
    }

    public interface UnlockedItemsMapListener {
        void onUpdate(UnlockedItem unlockedItem);
        void onDelete(UnlockedItem unlockedItem);
    }
}
