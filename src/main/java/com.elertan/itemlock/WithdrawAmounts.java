package com.elertan.itemlock;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.util.Text;

/**
 * Works out how much a bank withdraw takes out.
 */
public final class WithdrawAmounts {

    /** Withdraw-X: the amount prompt decides. */
    public static final int WITHDRAW_X = -2;
    public static final int NOT_A_WITHDRAW = -1;

    private static final Pattern WITHDRAW_N = Pattern.compile("^withdraw-(\\d+)$");

    private WithdrawAmounts() {
    }

    /** The amount a bank menu option asks for, {@link #WITHDRAW_X} or {@link #NOT_A_WITHDRAW}. */
    public static int requested(String menuOption, int bankQuantity) {
        if (menuOption == null) {
            return NOT_A_WITHDRAW;
        }
        String option = Text.removeTags(menuOption).trim().toLowerCase(Locale.ROOT);
        switch (option) {
            case "withdraw-all":
                return bankQuantity;
            case "withdraw-all-but-1":
                return Math.max(0, bankQuantity - 1);
            case "withdraw-x":
                return WITHDRAW_X;
            default:
                Matcher matcher = WITHDRAW_N.matcher(option);
                if (!matcher.matches()) {
                    return NOT_A_WITHDRAW;
                }
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    return NOT_A_WITHDRAW;
                }
        }
    }

    /**
     * The amount the game really takes out: never more than the stack, and for items that take
     * one inventory slot each, never more than the free slots.
     */
    public static int actual(int requested, int bankQuantity, int freeSlots, boolean takesOneSlotEach) {
        int amount = Math.min(requested, bankQuantity);
        return takesOneSlotEach ? Math.min(amount, Math.max(0, freeSlots)) : amount;
    }
}
