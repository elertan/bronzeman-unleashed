package com.elertan;

import com.elertan.data.LastEventDataProvider;
import com.elertan.event.BUEvent;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class BUEventService implements BUPluginLifecycle {

    private static final int STALE_EVENT_THRESHOLD_SECONDS = 30;

    private final ConcurrentLinkedQueue<Consumer<BUEvent>> eventListeners = new ConcurrentLinkedQueue<>();
    private final Consumer<BUEvent> lastEventListener = this::lastEventListener;

    @Inject
    private LastEventDataProvider lastEventDataProvider;

    @Override
    public void startUp() throws Exception {
        lastEventDataProvider.addEventListener(lastEventListener);

        lastEventDataProvider.waitUntilReady(null).whenComplete((__, throwable) -> {
            if (throwable != null) {
                log.error("LastEventDataProvider waitUntilReady failed", throwable);
                return;
            }
            cleanupStaleEvents();
        });
    }

    @Override
    public void shutDown() throws Exception {
        lastEventDataProvider.removeEventListener(lastEventListener);
    }

    public void addEventListener(Consumer<BUEvent> eventListener) {
        eventListeners.add(eventListener);
    }

    public void removeEventListener(Consumer<BUEvent> eventListener) {
        eventListeners.remove(eventListener);
    }

    public CompletableFuture<String> publishEvent(BUEvent event) {
        return lastEventDataProvider.add(event);
    }

    private void lastEventListener(BUEvent event) {
        for (Consumer<BUEvent> listener : eventListeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.error("error in listener for last event listener", e);
            }
        }
    }

    private void cleanupStaleEvents() {
        lastEventDataProvider.readAll().thenAccept(entries -> {
            OffsetDateTime threshold = OffsetDateTime.now().minusSeconds(STALE_EVENT_THRESHOLD_SECONDS);
            for (Map.Entry<String, BUEvent> entry : entries.entrySet()) {
                BUEvent event = entry.getValue();
                if (event.getTimestamp() == null || event.getTimestamp().getValue().isBefore(threshold)) {
                    lastEventDataProvider.remove(entry.getKey())
                        .exceptionally(ex -> {
                            log.error("Failed to cleanup stale event", ex);
                            return null;
                        });
                }
            }
        }).exceptionally(ex -> {
            log.error("Failed to read events for cleanup", ex);
            return null;
        });
    }
}
