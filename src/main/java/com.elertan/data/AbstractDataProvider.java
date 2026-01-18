package com.elertan.data;

import com.elertan.BUPluginLifecycle;
import com.elertan.remote.RemoteStorageService;
import com.elertan.utils.Observable;
import com.elertan.utils.Subscription;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Base class for data providers that depend on RemoteStorageService.
 * Handles common state management, listener lifecycle, and await pattern.
 */
@Slf4j
public abstract class AbstractDataProvider implements BUPluginLifecycle {

    @Getter
    private final Observable<State> state = Observable.of(State.NotReady);
    private Subscription remoteStorageSubscription;

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
        remoteStorageSubscription = getRemoteStorageService().getState().subscribeImmediate(
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
     * Wait until this data provider is ready (state == State.Ready).
     */
    public CompletableFuture<State> await(Duration timeout) {
        return waitForValue(state, State.Ready, timeout);
    }

    /**
     * Waits for an Observable to emit a specific target value.
     * Returns immediately if already at target, otherwise subscribes and waits.
     * Includes race condition protection by re-checking after subscribe.
     */
    private static <T> CompletableFuture<T> waitForValue(Observable<T> observable, T targetValue, Duration timeout) {
        CompletableFuture<T> future = new CompletableFuture<>();

        // Fast path: already at target value
        if (observable.get() == targetValue) {
            future.complete(targetValue);
            return future;
        }

        // Array wrapper needed because lambdas require effectively final variables,
        // but we need to reference the subscription inside the lambda itself
        Subscription[] subscriptionHolder = new Subscription[1];
        subscriptionHolder[0] = observable.subscribe((newValue, oldValue) -> {
            if (newValue == targetValue && !future.isDone()) {
                subscriptionHolder[0].dispose();
                future.complete(newValue);
            }
        });

        // Race condition check: value may have changed between get() and subscribe()
        if (observable.get() == targetValue && !future.isDone()) {
            subscriptionHolder[0].dispose();
            future.complete(targetValue);
        }

        // Timeout handling
        if (timeout != null) {
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.schedule(() -> {
                if (!future.isDone()) {
                    subscriptionHolder[0].dispose();
                    future.completeExceptionally(new TimeoutException("Timeout waiting for value"));
                }
            }, timeout.toMillis(), TimeUnit.MILLISECONDS);
            future.whenComplete((result, ex) -> scheduler.shutdown());
        }

        return future;
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
