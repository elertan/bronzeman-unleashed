package com.elertan.utils;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility methods for CompletableFuture operations.
 */
@Slf4j
public final class AsyncUtils {

    private AsyncUtils() {
    }

    /**
     * Returns a BiConsumer that logs errors at ERROR level.
     * Use with whenComplete() to add standardized error logging.
     *
     * @param message the error message prefix
     * @param <T>     the result type
     * @return BiConsumer for use with whenComplete()
     */
    public static <T> BiConsumer<T, Throwable> logError(String message) {
        return (result, throwable) -> {
            if (throwable != null) {
                log.error(message, throwable);
            }
        };
    }

    /**
     * Attaches error logging to a CompletableFuture and returns it.
     * The original future is returned unchanged, just with logging attached.
     *
     * @param future  the future to attach logging to
     * @param message the error message prefix
     * @param <T>     the result type
     * @return the same future with error logging attached
     */
    public static <T> CompletableFuture<T> withErrorLogging(CompletableFuture<T> future, String message) {
        future.whenComplete(logError(message));
        return future;
    }
}
