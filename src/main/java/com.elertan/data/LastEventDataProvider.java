package com.elertan.data;

import com.elertan.event.BUEvent;
import com.elertan.remote.ObjectStoragePort;
import com.elertan.remote.RemoteStorageService;
import com.elertan.utils.StateListenerManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class LastEventDataProvider extends AbstractDataProvider {

    private final StateListenerManager<BUEvent> eventListeners = new StateListenerManager<>("LastEventDataProvider.events");

    @Inject
    private RemoteStorageService remoteStorageService;

    private ObjectStoragePort<BUEvent> storagePort;
    private ObjectStoragePort.Listener<BUEvent> storagePortListener;

    public LastEventDataProvider() {
        super("LastEventDataProvider");
    }

    @Override
    protected RemoteStorageService getRemoteStorageService() {
        return remoteStorageService;
    }

    @Override
    public void startUp() throws Exception {
        storagePortListener = new ObjectStoragePort.Listener<BUEvent>() {
            @Override
            public void onUpdate(BUEvent value) {
                eventListeners.notifyListeners(value);
            }

            @Override
            public void onDelete() {
                // ignored
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

    public CompletableFuture<Void> update(BUEvent event) {
        if (getState() == State.NotReady) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("state is not ready"));
            return future;
        }
        return storagePort.update(event);
    }
}
