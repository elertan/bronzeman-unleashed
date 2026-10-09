package com.elertan.itemlock;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Locked (starting) quantities per un-noted item ID.
 */
public final class StartingItemsLedger {

    private static final StartingItemsLedger EMPTY = new StartingItemsLedger(Collections.emptyMap());

    private final Map<Integer, Integer> startingQuantities;

    public StartingItemsLedger(Map<Integer, Integer> startingQuantities) {
        this.startingQuantities = Collections.unmodifiableMap(new HashMap<>(startingQuantities));
    }

    public static StartingItemsLedger empty() {
        return EMPTY;
    }

    public Map<Integer, Integer> getStartingQuantities() {
        return startingQuantities;
    }

    public int starting(int itemId) {
        return startingQuantities.getOrDefault(itemId, 0);
    }

    /** Locked items never leave the bank, so everything above the locked quantity was earned. */
    public int withdrawRoom(int itemId, int bankQuantity) {
        return Math.max(0, bankQuantity - starting(itemId));
    }

    /**
     * Lowers the locked quantity of an item to at most the given quantity. Never raises it.
     *
     * @return the new ledger, or null when nothing changed
     */
    public StartingItemsLedger lower(int itemId, int quantity) {
        if (quantity >= starting(itemId)) {
            return null;
        }
        Map<Integer, Integer> lowered = new HashMap<>(startingQuantities);
        if (quantity <= 0) {
            lowered.remove(itemId);
        } else {
            lowered.put(itemId, quantity);
        }
        return new StartingItemsLedger(lowered);
    }
}
