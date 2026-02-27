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

/** Thread-safe observable value with built-in ready state and subscriptions. */
@Slf4j
public final class Observable<T> {

    // ConcurrentLinkedQueue allows thread-safe iteration without locking
    private final ConcurrentLinkedQueue<BiConsumer<T, T>> listeners = new ConcurrentLinkedQueue<>();
    private volatile T value;
    private final AtomicBoolean hasBeenSet = new AtomicBoolean(false);

    private Observable() {}

    public static <T> Observable<T> empty() {
        return new Observable<>();
    }

    public static <T> Observable<T> of(T value) {
        Observable<T> obs = new Observable<>();
        obs.value = value;
        obs.hasBeenSet.set(true);
        return obs;
    }

    public T get() { return value; }

    public boolean isReady() { return hasBeenSet.get(); }

    public void set(T newValue) {
        T oldValue = this.value;
        boolean wasReady = hasBeenSet.getAndSet(true);
        // First set() always notifies (ready-state transition); skip if value unchanged after that
        if (wasReady && Objects.equals(oldValue, newValue)) return;
        this.value = newValue;
        notifyListeners(newValue, oldValue);
    }

    /** Returns a future that completes when ready, with optional timeout. */
    public CompletableFuture<T> await(@Nullable Duration timeout) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (isReady()) { future.complete(value); return future; }

        Subscription[] holder = new Subscription[1];
        holder[0] = subscribe((nv, ov) -> {
            if (isReady() && !future.isDone()) { holder[0].dispose(); future.complete(nv); }
        });
        // Race condition check: ready state might have changed between isReady() and subscribe
        if (isReady() && !future.isDone()) { holder[0].dispose(); future.complete(value); }

        if (timeout != null) {
            ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor();
            sched.schedule(() -> {
                if (!future.isDone()) {
                    holder[0].dispose();
                    future.completeExceptionally(new TimeoutException("Timeout waiting for Observable to become ready"));
                }
            }, timeout.toMillis(), TimeUnit.MILLISECONDS);
            future.whenComplete((r, ex) -> sched.shutdown());
        }
        return future;
    }

    /** Returns a future that completes when the observable reaches the target value. */
    public static <T> CompletableFuture<T> awaitValue(Observable<T> observable, T targetValue, Duration timeout) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (observable.get() == targetValue) { future.complete(targetValue); return future; }

        Subscription[] holder = new Subscription[1];
        holder[0] = observable.subscribe((nv, ov) -> {
            if (nv == targetValue && !future.isDone()) { holder[0].dispose(); future.complete(nv); }
        });
        if (observable.get() == targetValue && !future.isDone()) { holder[0].dispose(); future.complete(targetValue); }

        if (timeout != null) {
            ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor();
            sched.schedule(() -> {
                if (!future.isDone()) { holder[0].dispose(); future.completeExceptionally(new TimeoutException("Timeout waiting for value")); }
            }, timeout.toMillis(), TimeUnit.MILLISECONDS);
            future.whenComplete((r, ex) -> sched.shutdown());
        }
        return future;
    }

    /** Subscribe to (newValue, oldValue) changes. */
    public Subscription subscribe(BiConsumer<T, T> listener) {
        listeners.add(listener);
        return new ListenerSubscription(listener);
    }

    /** Subscribe to newValue-only changes. */
    public Subscription subscribe(Consumer<T> listener) {
        return subscribe((nv, ov) -> listener.accept(nv));
    }

    /** Subscribe and immediately invoke with current value if ready. */
    public Subscription subscribeImmediate(BiConsumer<T, T> listener) {
        Subscription sub = subscribe(listener);
        if (isReady()) {
            try { listener.accept(value, null); }
            catch (Exception e) { log.error("Observable immediate listener error", e); }
        }
        return sub;
    }

    /** Clear all subscriptions and reset to NotReady state. */
    public void clear() {
        listeners.clear();
        hasBeenSet.set(false);
    }

    private void notifyListeners(T newValue, T oldValue) {
        for (BiConsumer<T, T> listener : listeners) {
            try { listener.accept(newValue, oldValue); }
            catch (Exception e) { log.error("Observable listener notification error", e); }
        }
    }

    private final class ListenerSubscription implements Subscription {
        private final BiConsumer<T, T> listener;
        private final AtomicBoolean disposed = new AtomicBoolean(false);

        ListenerSubscription(BiConsumer<T, T> listener) { this.listener = listener; }

        @Override
        public void dispose() {
            if (disposed.compareAndSet(false, true)) listeners.remove(listener);
        }

        @Override
        public boolean isDisposed() { return disposed.get(); }
    }
}
