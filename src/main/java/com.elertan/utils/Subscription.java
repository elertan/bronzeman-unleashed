package com.elertan.utils;

/** Handle to an active subscription. Implements AutoCloseable for try-with-resources. */
public interface Subscription extends AutoCloseable {
    /** Remove this subscription. Safe to call multiple times. */
    void dispose();

    boolean isDisposed();

    @Override
    default void close() { dispose(); }
}
