package com.elertan.itemlock;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WithdrawAmountsTest {

    @Test
    public void requestedFromMenuOption() {
        assertEquals(1, WithdrawAmounts.requested("Withdraw-1", 50));
        assertEquals(300, WithdrawAmounts.requested("Withdraw-300", 50));
        assertEquals(50, WithdrawAmounts.requested("Withdraw-All", 50));
        assertEquals(49, WithdrawAmounts.requested("Withdraw-All-but-1", 50));
        assertEquals(WithdrawAmounts.WITHDRAW_X, WithdrawAmounts.requested("Withdraw-X", 50));
        assertEquals(5, WithdrawAmounts.requested("<col=ff9040>Withdraw-5</col>", 50));
        assertEquals(WithdrawAmounts.NOT_A_WITHDRAW, WithdrawAmounts.requested("Deposit-All", 50));
        assertEquals(WithdrawAmounts.NOT_A_WITHDRAW, WithdrawAmounts.requested(null, 50));
    }

    @Test
    public void actualIsLimitedByStackAndFreeSlots() {
        // 100 sharks, Withdraw-All, 18 free slots: the game takes out 18.
        assertEquals(18, WithdrawAmounts.actual(100, 100, 18, true));
        assertEquals(5, WithdrawAmounts.actual(5, 100, 18, true));
        assertEquals(50, WithdrawAmounts.actual(300, 50, 0, false));
        assertEquals(100, WithdrawAmounts.actual(100, 100, 1, false));
        assertEquals(0, WithdrawAmounts.actual(100, 100, 0, true));
    }
}
