package com.elertan.utils;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;

/**
 * Utility methods for CompletableFuture operations.
 */
@Slf4j
public final class AsyncUtils {
    @Inject
    ClientThread clientThread;

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

    /**
     * Returns a Consumer that attaches error logging to a CompletableFuture.
     * The original future is returned unchanged, just with logging attached.
     * Useful when handling streams.
     *
     * @param message the error message prefix
     * @param <T>     the result type
     * @return a Consumer that attaches logging to a future
     */
    public static <T> Consumer<CompletableFuture<T>> addErrorLogging(String message) {
        return (future) -> withErrorLogging(future, message);
    }

    /**
     * Returns a Function that asynchronously runs the supplied Supplier on
     * the client thread and returns the result as a CompletableFuture
     * @param fn Supplier function to run on the client thread
     * @return CompletableFuture<T> of the return value of the supplied Supplier
     * @param <T> the result type
     * @param <U> discarded input value type, to be broadly compatible with Future handling methods
     */
    public <T, U> Function<U, CompletableFuture<T>> onClientThread(Supplier<T> fn) {
        return (__) -> runOnClientThread(fn);
    }

    /**
     * Asynchronously run the supplied Supplier on the client thread and return the result as a CompletableFuture
     * @param fn Supplier function to run on the client thread
     * @return the return value of the supplied Supplier
     * @param <T> the result type
     */
    public <T> CompletableFuture<T> runOnClientThread(Supplier<T> fn) {
        CompletableFuture<T> future = new CompletableFuture<>();
        clientThread.invokeLater(() -> {
            future.complete(fn.get());
        });
        return future;
    }
}
