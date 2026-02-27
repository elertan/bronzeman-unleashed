package com.elertan.data;

import com.elertan.models.GameRules;
import com.elertan.remote.ObjectStoragePort;
import com.elertan.remote.RemoteStorageService;
import com.elertan.utils.Observable;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class GameRulesDataProvider extends AbstractDataProvider {
    @Getter private final Observable<GameRules> gameRules = Observable.empty();
    @Inject private RemoteStorageService remoteStorageService;
    private ObjectStoragePort<GameRules> storagePort;
    private ObjectStoragePort.Listener<GameRules> storagePortListener;

    @Override
    protected RemoteStorageService getRemoteStorageService() { return remoteStorageService; }

    @Override
    public void startUp() throws Exception {
        storagePortListener = new ObjectStoragePort.Listener<GameRules>() {
            @Override public void onUpdate(GameRules value) { gameRules.set(value); }
            @Override public void onDelete() { }
        };
        super.startUp();
    }

    @Override
    protected void onRemoteStorageReady() {
        storagePort = remoteStorageService.getGameRulesStoragePort();
        storagePort.addListener(storagePortListener);
        storagePort.read().whenComplete((rules, throwable) -> {
            if (throwable != null) {
                log.error("GameRulesDataProvider storageport read failed", throwable);
                return;
            }
            gameRules.set(rules);
            setState(State.Ready);
        });
    }

    @Override
    protected void onRemoteStorageNotReady() {
        gameRules.set(null);
        if (storagePort != null) {
            storagePort.removeListener(storagePortListener);
            storagePort = null;
        }
    }

    public CompletableFuture<Void> updateGameRules(GameRules newGameRules) throws IllegalStateException {
        if (getState().get() != State.Ready) throw new IllegalStateException("Not ready yet");
        log.debug("Updating game rules: {}", newGameRules);
        return storagePort.update(newGameRules);
    }
}
