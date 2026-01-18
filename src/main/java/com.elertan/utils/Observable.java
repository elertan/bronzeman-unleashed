package com.elertan.utils;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

/**
 * Thread-safe observable value with built-in ready state and subscriptions.
 *
 * @param <T> the value type
 */
@Slf4j
public final class Observable<T> {

    // ConcurrentLinkedQueue allows thread-safe iteration without locking,
    // enabling listeners to be added/removed while notifying
    private final ConcurrentLinkedQueue<BiConsumer<T, T>> listeners = new ConcurrentLinkedQueue<>();
    private volatile T value;
    // Tracks whether set() has been called at least once (ready state)
    private final AtomicBoolean hasBeenSet = new AtomicBoolean(false);

    private Observable() {
    }

    /**
     * Creates observable without initial value. Starts in NotReady state.
     *
     * @param <T> the value type
     * @return new Observable in NotReady state
     */
    public static <T> Observable<T> empty() {
        return new Observable<>();
    }

    /**
     * Creates observable with initial value. Starts in Ready state.
     *
     * @param <T>   the value type
     * @param value the initial value (can be null)
     * @return new Observable in Ready state
     */
    public static <T> Observable<T> of(T value) {
        Observable<T> observable = new Observable<>();
        observable.value = value;
        observable.hasBeenSet.set(true);
        return observable;
    }

    /**
     * Get current value.
     *
     * @return current value (may be null even when ready)
     */
    public T get() {
        return value;
    }

    /**
     * Set value and notify listeners.
     * First call transitions to Ready state.
     * Skips notification if value equals previous (using Objects.equals).
     *
     * @param newValue the new value
     */
    public void set(T newValue) {
        T oldValue = this.value;

        // Skip if value unchanged
        if (Objects.equals(oldValue, newValue)) {
            // Still mark as ready on first call, even if value is unchanged (e.g., null -> null)
            hasBeenSet.set(true);
            return;
        }

        this.value = newValue;
        // First set() call transitions to Ready state
        hasBeenSet.set(true);

        notifyListeners(newValue, oldValue);
    }

    /**
     * Returns true after set() has been called at least once.
     *
     * @return true if ready
     */
    public boolean isReady() {
        return hasBeenSet.get();
    }

    /**
     * Returns a future that completes when ready.
     * If already ready, completes immediately with current value.
     * If timeout provided and exceeded, completes exceptionally with TimeoutException.
     *
     * @param timeout timeout duration, or null for no timeout
     * @return future that completes with the value when ready
     */
    public CompletableFuture<T> await(@Nullable Duration timeout) {
        CompletableFuture<T> future = new CompletableFuture<>();

        // Complete immediately if already ready
        if (isReady()) {
            future.complete(value);
            return future;
        }

        // Subscribe to changes
        Subscription[] subscriptionHolder = new Subscription[1];
        subscriptionHolder[0] = subscribe((newValue, oldValue) -> {
            if (isReady() && !future.isDone()) {
                subscriptionHolder[0].dispose();
                future.complete(newValue);
            }
        });

        // Race condition check: ready state might have changed between isReady() check and subscribe
        if (isReady() && !future.isDone()) {
            subscriptionHolder[0].dispose();
            future.complete(value);
        }

        // Timeout handling via ScheduledExecutorService
        if (timeout != null) {
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.schedule(() -> {
                if (!future.isDone()) {
                    subscriptionHolder[0].dispose();
                    future.completeExceptionally(
                        new TimeoutException("Timeout waiting for Observable to become ready"));
                }
            }, timeout.toMillis(), TimeUnit.MILLISECONDS);
            // Ensure scheduler is shutdown when future completes (success, timeout, or cancellation)
            future.whenComplete((result, ex) -> scheduler.shutdown());
        }

        return future;
    }

    /**
     * Subscribe to value changes.
     * Listener receives (newValue, oldValue) on each change.
     * oldValue is null on first notification after ready.
     *
     * @param listener the listener to notify on changes
     * @return subscription that can be disposed to unsubscribe
     */
    public Subscription subscribe(BiConsumer<T, T> listener) {
        listeners.add(listener);
        return new ListenerSubscription(listener);
    }

    /**
     * Subscribe to value changes (simple form).
     * Listener receives only newValue.
     *
     * @param listener the listener to notify on changes
     * @return subscription that can be disposed to unsubscribe
     */
    public Subscription subscribe(Consumer<T> listener) {
        return subscribe((newValue, oldValue) -> listener.accept(newValue));
    }

    /**
     * Subscribe and immediately invoke with current value if ready.
     * Useful for "get current state + subscribe to changes" pattern.
     *
     * @param listener the listener to notify on changes
     * @return subscription that can be disposed to unsubscribe
     */
    public Subscription subscribeImmediate(BiConsumer<T, T> listener) {
        Subscription subscription = subscribe(listener);
        if (isReady()) {
            try {
                listener.accept(value, null);
            } catch (Exception e) {
                log.error("Observable immediate listener error", e);
            }
        }
        return subscription;
    }

    /**
     * Clear all subscriptions and reset to NotReady state.
     * Call during service shutdown.
     */
    public void clear() {
        listeners.clear();
        hasBeenSet.set(false);
    }

    private void notifyListeners(T newValue, T oldValue) {
        for (BiConsumer<T, T> listener : listeners) {
            try {
                listener.accept(newValue, oldValue);
            } catch (Exception e) {
                // Log error but continue notifying other listeners
                log.error("Observable listener notification error", e);
            }
        }
    }

    /**
     * Internal subscription implementation that removes the listener on dispose.
     */
    private final class ListenerSubscription implements Subscription {
        private final BiConsumer<T, T> listener;
        private final AtomicBoolean disposed = new AtomicBoolean(false);

        ListenerSubscription(BiConsumer<T, T> listener) {
            this.listener = listener;
        }

        @Override
        public void dispose() {
            if (disposed.compareAndSet(false, true)) {
                listeners.remove(listener);
            }
        }

        @Override
        public boolean isDisposed() {
            return disposed.get();
        }
    }
}
