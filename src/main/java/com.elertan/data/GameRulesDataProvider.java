package com.elertan.data;

import com.elertan.BUPluginLifecycle;
import com.elertan.models.GameRules;
import com.elertan.remote.ObjectStoragePort;
import com.elertan.remote.RemoteStorageService;
import com.elertan.utils.ListenerUtils;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class GameRulesDataProvider implements BUPluginLifecycle {

    private final ConcurrentLinkedQueue<Consumer<State>> stateListeners = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Consumer<GameRules>> gameRulesListeners = new ConcurrentLinkedQueue<>();
    @Inject
    private RemoteStorageService remoteStorageService;
    @Getter
    private State state = State.NotReady;
    private ObjectStoragePort<GameRules> storagePort;
    private ObjectStoragePort.Listener<GameRules> storagePortListener;
    @Getter
    private GameRules gameRules;
    private final Consumer<RemoteStorageService.State> remoteStorageServiceStateListener = this::remoteStorageServiceStateListener;

    @Override
    public void startUp() {
        remoteStorageService.addStateListener(remoteStorageServiceStateListener);

        storagePortListener = new ObjectStoragePort.Listener<GameRules>() {

            @Override
            public void onUpdate(GameRules value) {
                setGameRules(value);
            }

            @Override
            public void onDelete() {
                // Uh-oh.. what now
            }
        };
    }

    @Override
    public void shutDown() {
        remoteStorageService.removeStateListener(remoteStorageServiceStateListener);
    }

    public void addStateListener(Consumer<State> listener) {
        stateListeners.add(listener);
    }

    public void removeStateListener(Consumer<State> listener) {
        stateListeners.remove(listener);
    }

    public void addGameRulesListener(Consumer<GameRules> listener) {
        gameRulesListeners.add(listener);
    }

    public void removeGameRulesListener(Consumer<GameRules> listener) {
        gameRulesListeners.remove(listener);
    }

    public CompletableFuture<Void> updateGameRules(GameRules gameRules)
        throws IllegalStateException {
        if (state == State.NotReady) {
            throw new IllegalStateException("Not ready yet");
        }

        log.info("Updating game rules: {}", gameRules);

        return storagePort.update(gameRules);
    }

    public CompletableFuture<Void> waitUntilReady(Duration timeout) {
        return ListenerUtils.waitUntilReady(new ListenerUtils.WaitUntilReadyContext() {
            Consumer<State> stateConsumer;

            @Override
            public boolean isReady() {
                return getState() == State.Ready;
            }

            @Override
            public void addListener(Runnable notify) {
                stateConsumer = state -> {
                    notify.run();
                };
                addStateListener(stateConsumer);
            }

            @Override
            public void removeListener() {
                if (stateConsumer == null) {
                    return;
                }
                removeStateListener(stateConsumer);
            }

            @Override
            public Duration getTimeout() {
                return timeout;
            }
        });
    }

    private void remoteStorageServiceStateListener(RemoteStorageService.State state) {
        if (state == RemoteStorageService.State.NotReady) {
            setGameRules(null);
            setState(State.NotReady);
            return;
        }

        tryInitialize();
    }

    private void tryInitialize() {
        if (remoteStorageService.getState() == RemoteStorageService.State.NotReady) {
            setGameRules(null);
            setState(State.NotReady);
            return;
        }

        storagePort = remoteStorageService.getGameRulesStoragePort();
        storagePort.addListener(storagePortListener);

        storagePort.read().whenComplete((gameRules, throwable) -> {
            if (throwable != null) {
                log.error("GameRulesDataProvider storageport read failed", throwable);
                return;
            }

            setGameRules(gameRules);
            setState(State.Ready);
        });
    }

    private void setState(State state) {
        if (this.state == state) {
            return;
        }
        this.state = state;

        for (Consumer<State> listener : stateListeners) {
            try {
                listener.accept(state);
            } catch (Exception e) {
                log.error("set state listener unlocked item data provider error", e);
            }
        }
    }

    private void setGameRules(GameRules gameRules) {
        this.gameRules = gameRules;
        for (Consumer<GameRules> listener : gameRulesListeners) {
            try {
                listener.accept(gameRules);
            } catch (Exception e) {
                log.error("set gameRules listener unlocked item data provider error", e);
            }
        }
    }

    public enum State {
        NotReady,
        Ready,
    }
}
