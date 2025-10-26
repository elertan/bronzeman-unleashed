package com.elertan.data;

import com.elertan.BUPluginLifecycle;
import com.elertan.event.BUEvent;
import com.elertan.remote.ObjectStoragePort;
import com.elertan.remote.RemoteStorageService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

@Slf4j
@Singleton
public class LastEventDataProvider implements BUPluginLifecycle {
    public enum State {
        NotReady,
        Ready,
    }

    @Inject
    private RemoteStorageService remoteStorageService;

    @Getter
    private State state = State.NotReady;
    private final ConcurrentLinkedQueue<Consumer<State>> stateListeners = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Consumer<BUEvent>> eventListeners = new ConcurrentLinkedQueue<>();

    private ObjectStoragePort<BUEvent> storagePort;
    private ObjectStoragePort.Listener<BUEvent> storagePortListener;

    @Override
    public void startUp() throws Exception {
        remoteStorageService.addStateListener(this::remoteStorageServiceStateListener);

        storagePortListener = new ObjectStoragePort.Listener<BUEvent>() {
            @Override
            public void onUpdate(BUEvent value) {
                for (Consumer<BUEvent> eventListener : eventListeners) {
                    try {
                        eventListener.accept(value);
                    } catch (Exception e) {
                        log.error("error in event listener", e);
                    }
                }
            }

            @Override
            public void onDelete() {
                // ignored
            }
        };
    }

    @Override
    public void shutDown() throws Exception {
        remoteStorageService.removeStateListener(this::remoteStorageServiceStateListener);
    }

    public void addStateListener(Consumer<State> listener) {
        stateListeners.add(listener);
    }

    public void removeStateListener(Consumer<State> listener) {
        stateListeners.remove(listener);
    }

    public void addEventListener(Consumer<BUEvent> listener) {
        eventListeners.add(listener);
    }

    public void removeEventListener(Consumer<BUEvent> listener) {
        eventListeners.remove(listener);
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

        for (Consumer<State> listener : stateListeners) {
            try {
                listener.accept(state);
            } catch (Exception e) {
                log.error("set state listener last event data provider error", e);
            }
        }
    }
}
