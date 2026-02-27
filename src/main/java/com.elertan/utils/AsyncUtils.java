package com.elertan.utils;

import com.google.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;

@Slf4j
public final class AsyncUtils {

    @Inject
    ClientThread clientThread;

    public static <T> BiConsumer<T, Throwable> logError(String message) {
        return (result, throwable) -> {
            if (throwable != null) log.error(message, throwable);
        };
    }

    public static <T> CompletableFuture<T> withErrorLogging(CompletableFuture<T> future, String message) {
        future.whenComplete(logError(message));
        return future;
    }

    public static <T> Consumer<CompletableFuture<T>> addErrorLogging(String message) {
        return future -> withErrorLogging(future, message);
    }

    /** Returns a Function that runs the supplier on the client thread, returning a future. */
    public <T, U> Function<U, CompletableFuture<T>> onClientThread(Supplier<T> fn) {
        return __ -> runOnClientThread(fn);
    }

    public <T> CompletableFuture<T> runOnClientThread(Supplier<T> fn) {
        CompletableFuture<T> future = new CompletableFuture<>();
        clientThread.invokeLater(() -> future.complete(fn.get()));
        return future;
    }
}
