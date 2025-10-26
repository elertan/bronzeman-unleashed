package com.elertan;

import com.elertan.data.LastEventDataProvider;
import com.elertan.event.BUEvent;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

@Slf4j
@Singleton
public class BUEventService implements BUPluginLifecycle {
    private final ConcurrentLinkedQueue<Consumer<BUEvent>> eventListeners = new ConcurrentLinkedQueue<>();

    @Inject
    private LastEventDataProvider lastEventDataProvider;

    @Override
    public void startUp() throws Exception {
        lastEventDataProvider.addEventListener(this::lastEventListener);
    }

    @Override
    public void shutDown() throws Exception {
        lastEventDataProvider.removeEventListener(this::lastEventListener);
    }

    public void addEventListener(Consumer<BUEvent> eventListener) {
        eventListeners.add(eventListener);
    }

    public void removeEventListener(Consumer<BUEvent> eventListener) {
        eventListeners.remove(eventListener);
    }

    public CompletableFuture<Void> publishEvent(BUEvent event) {
        return lastEventDataProvider.update(event);
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
}
