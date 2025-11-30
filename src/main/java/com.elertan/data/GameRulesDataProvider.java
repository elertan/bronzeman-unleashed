package com.elertan.data;

import com.elertan.models.GameRules;
import com.elertan.remote.ObjectStoragePort;
import com.elertan.remote.RemoteStorageService;
import com.elertan.utils.StateListenerManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class GameRulesDataProvider extends AbstractDataProvider {

    private final StateListenerManager<GameRules> gameRulesListeners = new StateListenerManager<>("GameRulesDataProvider.gameRules");

    @Inject
    private RemoteStorageService remoteStorageService;

    private ObjectStoragePort<GameRules> storagePort;
    private ObjectStoragePort.Listener<GameRules> storagePortListener;

    @Getter
    private GameRules gameRules;

    public GameRulesDataProvider() {
        super("GameRulesDataProvider");
    }

    @Override
    protected RemoteStorageService getRemoteStorageService() {
        return remoteStorageService;
    }

    @Override
    public void startUp() throws Exception {
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
        super.startUp();
    }

    @Override
    protected void onRemoteStorageReady() {
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

    @Override
    protected void onRemoteStorageNotReady() {
        setGameRules(null);
        if (storagePort != null) {
            storagePort.removeListener(storagePortListener);
            storagePort = null;
        }
    }

    public void addGameRulesListener(Consumer<GameRules> listener) {
        gameRulesListeners.addListener(listener);
    }

    public void removeGameRulesListener(Consumer<GameRules> listener) {
        gameRulesListeners.removeListener(listener);
    }

    public CompletableFuture<Void> updateGameRules(GameRules gameRules) throws IllegalStateException {
        if (getState() == State.NotReady) {
            throw new IllegalStateException("Not ready yet");
        }
        log.debug("Updating game rules: {}", gameRules);
        return storagePort.update(gameRules);
    }

    private void setGameRules(GameRules gameRules) {
        this.gameRules = gameRules;
        gameRulesListeners.notifyListeners(gameRules);
    }
}
