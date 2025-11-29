package com.elertan.data;

import com.elertan.BUPluginLifecycle;
import com.elertan.event.BUEvent;
import com.elertan.remote.ObjectStoragePort;
import com.elertan.remote.RemoteStorageService;
import com.elertan.utils.StateListenerManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class LastEventDataProvider implements BUPluginLifecycle {

    private final StateListenerManager<State> stateListeners = new StateListenerManager<>("LastEventDataProvider");
    private final StateListenerManager<BUEvent> eventListeners = new StateListenerManager<>("LastEventDataProvider.events");
    @Inject
    private RemoteStorageService remoteStorageService;
    @Getter
    private State state = State.NotReady;
    private ObjectStoragePort<BUEvent> storagePort;
    private ObjectStoragePort.Listener<BUEvent> storagePortListener;
    private final Consumer<RemoteStorageService.State> remoteStorageServiceStateListener = this::remoteStorageServiceStateListener;

    @Override
    public void startUp() throws Exception {
        remoteStorageService.addStateListener(remoteStorageServiceStateListener);

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
    }

    @Override
    public void shutDown() throws Exception {
        remoteStorageService.removeStateListener(remoteStorageServiceStateListener);
    }

    public void addStateListener(Consumer<State> listener) {
        stateListeners.addListener(listener);
    }

    public void removeStateListener(Consumer<State> listener) {
        stateListeners.removeListener(listener);
    }

    public void addEventListener(Consumer<BUEvent> listener) {
        eventListeners.addListener(listener);
    }

    public void removeEventListener(Consumer<BUEvent> listener) {
        eventListeners.removeListener(listener);
    }

    public CompletableFuture<Void> update(BUEvent event) {
        if (state == State.NotReady) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            Exception ex = new IllegalStateException("state is not ready");
            future.completeExceptionally(ex);
            return future;
        }
        return storagePort.update(event);
    }

    private void remoteStorageServiceStateListener(RemoteStorageService.State state) {
        if (state == RemoteStorageService.State.NotReady) {
            setState(State.NotReady);
            return;
        }

        tryInitialize();
    }

    private void tryInitialize() {
        if (remoteStorageService.getState() == RemoteStorageService.State.NotReady) {
            setState(State.NotReady);
            return;
        }

        storagePort = remoteStorageService.getLastEventStoragePort();
        storagePort.addListener(storagePortListener);
        setState(State.Ready);
    }

    private void setState(State state) {
        if (this.state == state) {
            return;
        }
        this.state = state;
        stateListeners.notifyListeners(state);
    }

    public enum State {
        NotReady,
        Ready,
    }
}
