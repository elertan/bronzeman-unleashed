package com.elertan.itemlock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class StartingItemsLedgerTest {

    private static final int SHARK = 385;
    private static final int COINS = 995;
    private static final int LOBSTER = 379;

    private static StartingItemsLedger ledger() {
        Map<Integer, Integer> starting = new HashMap<>();
        starting.put(SHARK, 100);
        starting.put(COINS, 1_000);
        return new StartingItemsLedger(starting);
    }

    @Test
    public void withdrawRoomIsBankAboveStarting() {
        StartingItemsLedger ledger = ledger();
        assertEquals(0, ledger.withdrawRoom(SHARK, 90));
        assertEquals(0, ledger.withdrawRoom(SHARK, 100));
        assertEquals(5, ledger.withdrawRoom(SHARK, 105));
        assertEquals(7, ledger.withdrawRoom(LOBSTER, 7));
    }

    @Test
    public void emptyLedgerLocksNothing() {
        assertEquals(5_000, StartingItemsLedger.empty().withdrawRoom(SHARK, 5_000));
    }

    @Test
    public void lowerOnlyGoesDown() {
        StartingItemsLedger ledger = ledger();
        assertNull(ledger.lower(SHARK, 150));
        assertNull(ledger.lower(SHARK, 100));
        assertNull(ledger.lower(LOBSTER, 0));

        StartingItemsLedger lowered = ledger.lower(SHARK, 60);
        assertEquals(60, lowered.starting(SHARK));
        assertEquals(1_000, lowered.starting(COINS));

        StartingItemsLedger gone = ledger.lower(COINS, 0);
        assertEquals(0, gone.starting(COINS));
        assertFalse(gone.getStartingQuantities().containsKey(COINS));
    }
}
