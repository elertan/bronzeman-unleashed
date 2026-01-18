package com.elertan;

import com.elertan.data.LastEventDataProvider;
import com.elertan.event.BUEvent;
import com.elertan.utils.Observable;
import com.elertan.utils.Subscription;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class BUEventService implements BUPluginLifecycle {

    private static final int STALE_EVENT_THRESHOLD_SECONDS = 30;
    private static final int CLEANUP_DELAY_SECONDS = 10;

    // Note: This observable is event-based (transient), not stateful.
    // It holds the "last event" and notifies on each new event.
    private final Observable<BUEvent> lastEvent = Observable.empty();
    private ScheduledExecutorService scheduler;
    private Subscription lastEventDataProviderSubscription;

    @Inject
    private LastEventDataProvider lastEventDataProvider;

    @Override
    public void startUp() throws Exception {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        lastEventDataProviderSubscription = lastEventDataProvider.events()
            .subscribe(event -> lastEvent.set(event));

        lastEventDataProvider.await(null).whenComplete((__, throwable) -> {
            if (throwable != null) {
                log.error("LastEventDataProvider await failed", throwable);
                return;
            }
            cleanupStaleEvents();
        });
    }

    @Override
    public void shutDown() throws Exception {
        if (lastEventDataProviderSubscription != null) {
            lastEventDataProviderSubscription.dispose();
            lastEventDataProviderSubscription = null;
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    /**
     * Observable for event notifications.
     */
    public Observable<BUEvent> lastEvent() {
        return lastEvent;
    }

    public CompletableFuture<String> publishEvent(BUEvent event) {
        return lastEventDataProvider.add(event).thenApply(entryKey -> {
            if (entryKey != null) {
                // Events use POST to create unique entries (prevents data races).
                // Writer cleans up own entry after 10s - enough time for SSE propagation.
                // Stale events (>30s) cleaned on startup as fallback.
                scheduler.schedule(() -> {
                    lastEventDataProvider.remove(entryKey)
                        .exceptionally(ex -> {
                            log.error("Failed to cleanup own event", ex);
                            return null;
                        });
                }, CLEANUP_DELAY_SECONDS, TimeUnit.SECONDS);
            }
            return entryKey;
        });
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
