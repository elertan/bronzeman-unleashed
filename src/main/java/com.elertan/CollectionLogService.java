package com.elertan;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;

@Slf4j
@Singleton
public class CollectionLogService implements BUPluginLifecycle {
    private static final int EXPIRY_TICKS = 12;
    private final Map<String, Integer> recentUnlocks = new ConcurrentHashMap<>();
    private volatile int currentTick = 0;
    @Inject private Client client;

    @Override public void startUp() throws Exception {}

    @Override
    public void shutDown() throws Exception {
        recentUnlocks.clear();
        currentTick = 0;
    }

    public void addRecentCollectionLogUnlock(String itemName) {
        recentUnlocks.put(itemName, currentTick);
    }

    public boolean tryConsumeOverlaySuppression(String itemName) {
        return recentUnlocks.remove(itemName) != null;
    }

    public void onGameTick(GameTick event) {
        currentTick = client.getTickCount();
        recentUnlocks.entrySet().removeIf(entry -> {
            if (currentTick - entry.getValue() > EXPIRY_TICKS) {
                log.warn("Collection log item '{}' not matched by unlock within {} ticks",
                    entry.getKey(), EXPIRY_TICKS);
                return true;
            }
            return false;
        });
    }

    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN) recentUnlocks.clear();
    }
}
