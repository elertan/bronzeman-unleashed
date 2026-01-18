package com.elertan.utils;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/**
 * Groups multiple subscriptions for bulk disposal.
 * Useful in services that subscribe to multiple observables.
 */
@Slf4j
public final class CompositeSubscription implements Subscription {

    // ConcurrentLinkedQueue provides thread-safe iteration without locking
    private final ConcurrentLinkedQueue<Subscription> subscriptions = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    /**
     * Add a subscription to this group.
     * If already disposed, the subscription is immediately disposed.
     *
     * @param subscription the subscription to add
     * @param <S>          the subscription type
     * @return the subscription for chaining
     */
    public <S extends Subscription> S add(S subscription) {
        if (disposed.get()) {
            subscription.dispose();
        } else {
            subscriptions.add(subscription);
        }
        return subscription;
    }

    /**
     * Dispose all subscriptions in this group.
     * Safe to call multiple times; subsequent calls are no-ops.
     */
    @Override
    public void dispose() {
        if (disposed.compareAndSet(false, true)) {
            for (Subscription subscription : subscriptions) {
                try {
                    subscription.dispose();
                } catch (Exception e) {
                    log.error("Error disposing subscription", e);
                }
            }
            subscriptions.clear();
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }
}
