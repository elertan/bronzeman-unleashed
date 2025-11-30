package com.elertan.data;

import com.elertan.BUPluginLifecycle;
import com.elertan.remote.RemoteStorageService;
import com.elertan.utils.ListenerUtils;
import com.elertan.utils.StateListenerManager;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Base class for data providers that depend on RemoteStorageService.
 * Handles common state management, listener lifecycle, and waitUntilReady pattern.
 */
@Slf4j
public abstract class AbstractDataProvider implements BUPluginLifecycle {

    private final StateListenerManager<State> stateListeners;
    private final Consumer<RemoteStorageService.State> remoteStorageServiceStateListener = this::onRemoteStorageStateChanged;

    @Getter
    private State state = State.NotReady;

    protected AbstractDataProvider(String name) {
        this.stateListeners = new StateListenerManager<>(name);
    }

    /**
     * Subclasses must provide the RemoteStorageService instance.
     */
    protected abstract RemoteStorageService getRemoteStorageService();

    /**
     * Called when RemoteStorageService becomes ready.
     * Subclasses should initialize their storage ports and data here.
     */
    protected abstract void onRemoteStorageReady();

    /**
     * Called when RemoteStorageService becomes not ready or during shutdown.
     * Subclasses should clear their data and storage ports here.
     */
    protected abstract void onRemoteStorageNotReady();

    @Override
    public void startUp() throws Exception {
        getRemoteStorageService().addStateListener(remoteStorageServiceStateListener);
        if (getRemoteStorageService().getState() == RemoteStorageService.State.Ready) {
            onRemoteStorageReady();
        }
    }

    @Override
    public void shutDown() throws Exception {
        getRemoteStorageService().removeStateListener(remoteStorageServiceStateListener);
        onRemoteStorageNotReady();
        state = State.NotReady;
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
                return getState() == State.Ready;
            }

            @Override
            public void addListener(Runnable notify) {
                listener = s -> notify.run();
                addStateListener(listener);
            }

            @Override
            public void removeListener() {
                if (listener != null) {
                    removeStateListener(listener);
                    listener = null;
                }
            }

            @Override
            public Duration getTimeout() {
                return timeout;
            }
        });
    }

    protected void setState(State newState) {
        if (this.state == newState) {
            return;
        }
        this.state = newState;
        stateListeners.notifyListeners(newState);
    }

    private void onRemoteStorageStateChanged(RemoteStorageService.State remoteState) {
        if (remoteState == RemoteStorageService.State.NotReady) {
            onRemoteStorageNotReady();
            setState(State.NotReady);
        } else {
            onRemoteStorageReady();
        }
    }

    public enum State {
        NotReady,
        Ready
    }
}
