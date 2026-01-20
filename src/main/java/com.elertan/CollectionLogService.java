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

    private final Map<String, Integer> recentCollectionLogUnlocks = new ConcurrentHashMap<>();
    private volatile int currentTick = 0;

    @Inject
    private Client client;

    @Override
    public void startUp() throws Exception {
        // No initialization needed
    }

    @Override
    public void shutDown() throws Exception {
        recentCollectionLogUnlocks.clear();
        currentTick = 0;
    }

    /**
     * Track a collection log unlock for overlay suppression.
     * Called synchronously when "New item added to your collection log" is parsed.
     * Thread-safe: can be called from event bus thread.
     */
    public void addRecentCollectionLogUnlock(String itemName) {
        recentCollectionLogUnlocks.put(itemName, currentTick);
    }

    /**
     * Check if item should have its unlock overlay suppressed.
     * Returns true and removes the entry if present (one-time consumption).
     * Should be called from client thread.
     */
    public boolean tryConsumeOverlaySuppression(String itemName) {
        return recentCollectionLogUnlocks.remove(itemName) != null;
    }

    public void onGameTick(GameTick event) {
        currentTick = client.getTickCount();

        recentCollectionLogUnlocks.entrySet().removeIf(entry -> {
            if (currentTick - entry.getValue() > EXPIRY_TICKS) {
                log.warn("Collection log item '{}' not matched by unlock within {} ticks",
                    entry.getKey(), EXPIRY_TICKS);
                return true;
            }
            return false;
        });
    }

    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN) {
            recentCollectionLogUnlocks.clear();
        }
    }
}
