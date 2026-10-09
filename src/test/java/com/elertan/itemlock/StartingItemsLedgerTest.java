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
    public void lowerToOnlyGoesDown() {
        StartingItemsLedger ledger = ledger();
        Map<Integer, Long> held = new HashMap<>();
        held.put(SHARK, 150L);
        held.put(COINS, 1_000L);
        assertNull(ledger.lowerTo(held));

        held.put(SHARK, 60L);
        StartingItemsLedger lowered = ledger.lowerTo(held);
        assertEquals(60, lowered.starting(SHARK));
        assertEquals(1_000, lowered.starting(COINS));

        held.remove(COINS);
        StartingItemsLedger gone = ledger.lowerTo(held);
        assertEquals(0, gone.starting(COINS));
        assertFalse(gone.getStartingQuantities().containsKey(COINS));
    }

    @Test
    public void requestedAmountFromMenuOption() {
        assertEquals(1, StartingItemsLedger.requestedAmount("Withdraw-1", 50));
        assertEquals(10, StartingItemsLedger.requestedAmount("Withdraw-10", 50));
        assertEquals(300, StartingItemsLedger.requestedAmount("Withdraw-300", 50));
        assertEquals(50, StartingItemsLedger.requestedAmount("Withdraw-All", 50));
        assertEquals(49, StartingItemsLedger.requestedAmount("Withdraw-All-but-1", 50));
        assertEquals(StartingItemsLedger.WITHDRAW_X, StartingItemsLedger.requestedAmount("Withdraw-X", 50));
        assertEquals(5, StartingItemsLedger.requestedAmount("<col=ff9040>Withdraw-5</col>", 50));
        assertEquals(StartingItemsLedger.NOT_A_WITHDRAW, StartingItemsLedger.requestedAmount("Deposit-All", 50));
        assertEquals(StartingItemsLedger.NOT_A_WITHDRAW, StartingItemsLedger.requestedAmount("Examine", 50));
        assertEquals(StartingItemsLedger.NOT_A_WITHDRAW, StartingItemsLedger.requestedAmount(null, 50));
    }

    @Test
    public void actualWithdrawAmountIsLimitedByStackAndFreeSlots() {
        // 100 sharks, Withdraw-All, 18 free slots: the game takes out 18.
        assertEquals(18, StartingItemsLedger.actualWithdrawAmount(100, 100, 18, true));
        // Withdraw-5 with 18 free slots: 5.
        assertEquals(5, StartingItemsLedger.actualWithdrawAmount(5, 100, 18, true));
        // Withdraw-300 of a stack of 50 stackable items: 50.
        assertEquals(50, StartingItemsLedger.actualWithdrawAmount(300, 50, 0, false));
        // Noted or stackable: free slots do not matter.
        assertEquals(100, StartingItemsLedger.actualWithdrawAmount(100, 100, 1, false));
        // Full inventory: nothing.
        assertEquals(0, StartingItemsLedger.actualWithdrawAmount(100, 100, 0, true));
    }

    @Test
    public void parseAmountLikeTheGame() {
        assertEquals(Long.valueOf(600), StartingItemsLedger.parseAmount("600"));
        assertEquals(Long.valueOf(1_000), StartingItemsLedger.parseAmount("1k"));
        assertEquals(Long.valueOf(2_000_000), StartingItemsLedger.parseAmount("2M"));
        assertEquals(Long.valueOf(1_000_000_000), StartingItemsLedger.parseAmount("1b"));
        assertNull(StartingItemsLedger.parseAmount(""));
        assertNull(StartingItemsLedger.parseAmount("abc"));
        assertNull(StartingItemsLedger.parseAmount("1kk"));
        assertNull(StartingItemsLedger.parseAmount(null));
    }
}
