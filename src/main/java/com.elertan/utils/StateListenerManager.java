package com.elertan.utils;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * Thread-safe manager for state change listeners.
 * Handles add/remove/notify operations with error handling.
 *
 * @param <T> the state type
 */
@Slf4j
public final class StateListenerManager<T> {

    private final ConcurrentLinkedQueue<Consumer<T>> listeners = new ConcurrentLinkedQueue<>();
    private final String name;

    public StateListenerManager(String name) {
        this.name = name;
    }

    public void addListener(Consumer<T> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<T> listener) {
        listeners.remove(listener);
    }

    public void notifyListeners(T state) {
        for (Consumer<T> listener : listeners) {
            try {
                listener.accept(state);
            } catch (Exception e) {
                log.error("{}: listener notification error", name, e);
            }
        }
    }

    public void clear() {
        listeners.clear();
    }
}
