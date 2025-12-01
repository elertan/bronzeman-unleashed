package com.elertan.data;

import com.elertan.event.BUEvent;
import com.elertan.remote.ObjectListStoragePort;
import com.elertan.remote.RemoteStorageService;
import com.elertan.utils.StateListenerManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class LastEventDataProvider extends AbstractDataProvider {

    private final StateListenerManager<BUEvent> eventListeners = new StateListenerManager<>("LastEventDataProvider.events");

    @Inject
    private RemoteStorageService remoteStorageService;

    private ObjectListStoragePort<BUEvent> storagePort;
    private ObjectListStoragePort.Listener<BUEvent> storagePortListener;

    public LastEventDataProvider() {
        super("LastEventDataProvider");
    }

    @Override
    protected RemoteStorageService getRemoteStorageService() {
        return remoteStorageService;
    }

    @Override
    public void startUp() throws Exception {
        storagePortListener = new ObjectListStoragePort.Listener<BUEvent>() {
            @Override
            public void onFullUpdate(Map<String, BUEvent> map) {
                // ignored - only care about individual adds
            }

            @Override
            public void onAdd(String entryKey, BUEvent value) {
                eventListeners.notifyListeners(value);
            }

            @Override
            public void onRemove(String entryKey) {
                // ignored - cleanup only
            }
        };
        super.startUp();
    }

    @Override
    protected void onRemoteStorageReady() {
        storagePort = remoteStorageService.getLastEventStoragePort();
        storagePort.addListener(storagePortListener);
        setState(State.Ready);
    }

    @Override
    protected void onRemoteStorageNotReady() {
        if (storagePort != null) {
            storagePort.removeListener(storagePortListener);
            storagePort = null;
        }
    }

    public void addEventListener(Consumer<BUEvent> listener) {
        eventListeners.addListener(listener);
    }

    public void removeEventListener(Consumer<BUEvent> listener) {
        eventListeners.removeListener(listener);
    }

    public CompletableFuture<String> add(BUEvent event) {
        if (getState() == State.NotReady) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("state is not ready"));
            return future;
        }
        return storagePort.add(event);
    }

    public CompletableFuture<Map<String, BUEvent>> readAll() {
        if (getState() == State.NotReady) {
            CompletableFuture<Map<String, BUEvent>> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("state is not ready"));
            return future;
        }
        return storagePort.readAll();
    }

    public CompletableFuture<Void> remove(String entryKey) {
        if (getState() == State.NotReady) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("state is not ready"));
            return future;
        }
        return storagePort.remove(entryKey);
    }
}
