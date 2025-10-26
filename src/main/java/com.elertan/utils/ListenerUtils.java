package com.elertan.utils;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.*;

@Slf4j
public class ListenerUtils {
    public interface WaitUntilReadyContext {
        boolean isReady();

        void addListener(Runnable notify);

        void removeListener();

        Duration getTimeout();
    }

    public static CompletableFuture<Void> waitUntilReady(WaitUntilReadyContext context) {
        CompletableFuture<Void> ready = new CompletableFuture<>();

        if (context.isReady()) {
            ready.complete(null);
            return ready;
        }

        context.addListener(() -> {
            if (context.isReady() && !ready.isDone()) {
                context.removeListener();
                ready.complete(null);
            }
        });

        if (context.isReady() && !ready.isDone()) {
            context.removeListener();
            ready.complete(null);
        }

        Duration timeout = context.getTimeout();
        if (timeout != null) {
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.schedule(() -> {
                if (!ready.isDone()) {
                    context.removeListener();
                    ready.completeExceptionally(new TimeoutException("Timeout waiting for context to become ready"));
                }
                scheduler.shutdown();
            }, timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        return ready;
    }
}
