package com.elertan.data;

import com.elertan.ExcludedItemIds;
import com.elertan.models.UnlockedItem;
import com.elertan.remote.KeyValueStoragePort;
import com.elertan.remote.StorageService;
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
    private StorageService storageService;

    private KeyValueStoragePort<Integer, UnlockedItem> keyValueStoragePort;
    private KeyValueStoragePort.Listener<Integer, UnlockedItem> storagePortListener;
    private ConcurrentHashMap<Integer, UnlockedItem> unlockedItemsMap;

    @Override
    protected StorageService getStorageService() {
        return storageService;
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
                unlockedItemsMap.keySet().removeAll(ExcludedItemIds.IDS);
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
                UnlockedItem unlockedItem = unlockedItemsMap.remove(key);
                // Already removed locally, e.g. by removeExcludedItems()
                if (unlockedItem == null) {
                    return;
                }

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
        keyValueStoragePort = storageService.getUnlockedItemsStoragePort();
        keyValueStoragePort.addListener(storagePortListener);

        keyValueStoragePort.readAll().whenComplete((map, throwable) -> {
            if (throwable != null) {
                log.error("UnlockedItemDataProvider storageport read all failed", throwable);
                return;
            }
            unlockedItemsMap = new ConcurrentHashMap<>(map);
            removeExcludedItems();
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

    // Excluded items could be unlocked by older plugin versions
    private void removeExcludedItems() {
        for (Integer itemId : ExcludedItemIds.IDS) {
            if (unlockedItemsMap.remove(itemId) == null) {
                continue;
            }
            log.debug("Removing excluded unlocked item with id {}", itemId);
            keyValueStoragePort.delete(itemId).whenComplete((__, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to remove excluded unlocked item with id {}", itemId, throwable);
                }
            });
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
        if (getState().get() != State.Ready) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("State is not ready"));
            return future;
        }
        unlockedItemsMap.put(unlockedItem.getId(), unlockedItem);
        return keyValueStoragePort.update(unlockedItem.getId(), unlockedItem);
    }

    public CompletableFuture<Void> removeUnlockedItemById(int itemId) {
        if (getState().get() != State.Ready) {
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
