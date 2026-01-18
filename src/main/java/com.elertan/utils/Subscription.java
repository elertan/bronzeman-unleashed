package com.elertan.utils;

/**
 * Handle to an active subscription.
 * Implements AutoCloseable for try-with-resources support.
 */
public interface Subscription extends AutoCloseable {

    /**
     * Remove this subscription from its observable.
     * Safe to call multiple times; subsequent calls are no-ops.
     */
    void dispose();

    /**
     * Check if this subscription has been disposed.
     *
     * @return true if dispose() has been called
     */
    boolean isDisposed();

    /**
     * Alias for dispose() to support try-with-resources.
     */
    @Override
    default void close() {
        dispose();
    }
}
