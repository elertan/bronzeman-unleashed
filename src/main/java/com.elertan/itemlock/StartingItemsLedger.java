package com.elertan.itemlock;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The quantity rules of item locking, without RuneLite API, so they can be unit tested.
 * All item IDs must already be un-noted (noted items count as their un-noted item).
 */
public final class StartingItemsLedger {

    /** Requested amount for Withdraw-X: the prompt decides the amount. */
    public static final long WITHDRAW_X = -2;
    /** The menu option is not a withdraw option that this ledger understands. */
    public static final long NOT_A_WITHDRAW = -1;

    private static final Pattern COLOR_TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern WITHDRAW_N = Pattern.compile("^withdraw-(\\d+)$");
    private static final Pattern AMOUNT = Pattern.compile("^(\\d+)([kmb]?)$");

    private final Map<Integer, Integer> startingQuantities;

    public StartingItemsLedger(Map<Integer, Integer> startingQuantities) {
        this.startingQuantities = Collections.unmodifiableMap(new HashMap<>(startingQuantities));
    }

    public static StartingItemsLedger empty() {
        return new StartingItemsLedger(Collections.emptyMap());
    }

    public Map<Integer, Integer> getStartingQuantities() {
        return startingQuantities;
    }

    public int starting(int itemId) {
        return startingQuantities.getOrDefault(itemId, 0);
    }

    /**
     * How many the player may take out of the bank. Starting items never leave the bank, so
     * everything above the starting quantity was earned.
     */
    public long withdrawRoom(int itemId, long bankQuantity) {
        return Math.max(0, bankQuantity - starting(itemId));
    }

    /**
     * Lowers starting quantities to the given quantities. Starting quantities never go up.
     *
     * @return the new ledger, or null when nothing changed
     */
    public StartingItemsLedger lowerTo(Map<Integer, Long> heldQuantities) {
        Map<Integer, Integer> lowered = new HashMap<>(startingQuantities);
        boolean changed = false;
        for (Map.Entry<Integer, Integer> entry : startingQuantities.entrySet()) {
            long held = heldQuantities.getOrDefault(entry.getKey(), 0L);
            if (held >= entry.getValue()) {
                continue;
            }
            changed = true;
            if (held <= 0) {
                lowered.remove(entry.getKey());
            } else {
                lowered.put(entry.getKey(), (int) held);
            }
        }
        return changed ? new StartingItemsLedger(lowered) : null;
    }

    /**
     * The amount a bank withdraw option asks for.
     *
     * @return the amount, {@link #WITHDRAW_X}, or {@link #NOT_A_WITHDRAW}
     */
    public static long requestedAmount(String menuOption, long bankQuantity) {
        if (menuOption == null) {
            return NOT_A_WITHDRAW;
        }
        String option = COLOR_TAGS.matcher(menuOption).replaceAll("").trim().toLowerCase(Locale.ROOT);
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
                    return Long.parseLong(matcher.group(1));
                } catch (NumberFormatException e) {
                    return NOT_A_WITHDRAW;
                }
        }
    }

    /**
     * The amount the game really takes out: never more than the stack, and for items that take
     * one inventory slot each, never more than the free slots.
     */
    public static long actualWithdrawAmount(long requested, long bankQuantity, int freeSlots,
        boolean takesOneSlotEach) {
        long amount = Math.min(requested, bankQuantity);
        return takesOneSlotEach ? Math.min(amount, Math.max(0, freeSlots)) : amount;
    }

    /**
     * Parses a typed amount like the game does: digits with an optional k, m or b suffix.
     *
     * @return the amount, or null when the game would not accept the text
     */
    public static Long parseAmount(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = AMOUNT.matcher(text.trim().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            return null;
        }
        long multiplier;
        switch (matcher.group(2)) {
            case "k":
                multiplier = 1_000L;
                break;
            case "m":
                multiplier = 1_000_000L;
                break;
            case "b":
                multiplier = 1_000_000_000L;
                break;
            default:
                multiplier = 1L;
        }
        try {
            return Math.multiplyExact(Long.parseLong(matcher.group(1)), multiplier);
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }
}
