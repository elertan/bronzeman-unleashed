package com.elertan;

import com.elertan.utils.TextUtils;
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

    private static final class RecentUnlock {
        private final String rawItemName;
        private final int tick;

        private RecentUnlock(String rawItemName, int tick) {
            this.rawItemName = rawItemName;
            this.tick = tick;
        }
    }

    /**
     * Keyed by normalized item name (tags/whitespace removed) so collection log chat text can match
     * item composition names used during unlock processing.
     */
    private final Map<String, RecentUnlock> recentCollectionLogUnlocks = new ConcurrentHashMap<>();
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
        String key = normalizeItemNameKey(itemName);
        if (key == null) {
            return;
        }
        recentCollectionLogUnlocks.put(key, new RecentUnlock(itemName, currentTick));
    }

    /**
     * Check if item should have its unlock overlay suppressed.
     * Returns true and removes the entry if present (one-time consumption).
     * Should be called from client thread.
     */
    public boolean tryConsumeOverlaySuppression(String itemName) {
        String key = normalizeItemNameKey(itemName);
        if (key == null) {
            return false;
        }
        return recentCollectionLogUnlocks.remove(key) != null;
    }

    public void onGameTick(GameTick event) {
        currentTick = client.getTickCount();

        recentCollectionLogUnlocks.entrySet().removeIf(entry -> {
            RecentUnlock unlock = entry.getValue();
            if (currentTick - unlock.tick > EXPIRY_TICKS) {
                log.warn("Collection log item '{}' not matched by unlock within {} ticks",
                    unlock.rawItemName, EXPIRY_TICKS);
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

    private static String normalizeItemNameKey(String itemName) {
        return TextUtils.sanitizeItemName(itemName);
    }
}
