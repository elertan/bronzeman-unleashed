package com.elertan.data;

import com.elertan.BUPluginLifecycle;
import com.elertan.remote.RemoteStorageService;
import com.elertan.utils.Observable;
import com.elertan.utils.Subscription;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;

/**
 * Base class for data providers that depend on RemoteStorageService.
 * Handles common state management, listener lifecycle, and waitUntilReady pattern.
 */
@Slf4j
public abstract class AbstractDataProvider implements BUPluginLifecycle {

    private final Observable<State> state;
    private Subscription remoteStorageSubscription;

    protected AbstractDataProvider(String name) {
        this.state = new Observable<>(name + ".state");
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
        remoteStorageSubscription = getRemoteStorageService().state().subscribeImmediate(
            (remoteState, old) -> onRemoteStorageStateChanged(remoteState)
        );
    }

    @Override
    public void shutDown() throws Exception {
        if (remoteStorageSubscription != null) {
            remoteStorageSubscription.dispose();
            remoteStorageSubscription = null;
        }
        onRemoteStorageNotReady();
        state.set(State.NotReady);
    }

    /**
     * Observable for state changes.
     */
    public Observable<State> state() {
        return state;
    }

    /**
     * Get current state.
     */
    public State getState() {
        return state.get();
    }

    /**
     * Wait until this data provider is ready.
     */
    public CompletableFuture<State> waitUntilReady(Duration timeout) {
        return state.waitUntilReady(timeout);
    }

    protected void setState(State newState) {
        state.set(newState);
    }

    private void onRemoteStorageStateChanged(RemoteStorageService.State remoteState) {
        if (remoteState == RemoteStorageService.State.NotReady) {
            onRemoteStorageNotReady();
            setState(State.NotReady);
        } else if (remoteState == RemoteStorageService.State.Ready) {
            onRemoteStorageReady();
        }
    }

    public enum State {
        NotReady,
        Ready
    }
}
