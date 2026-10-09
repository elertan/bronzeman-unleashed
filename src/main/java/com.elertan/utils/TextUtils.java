package com.elertan.utils;

import net.runelite.client.util.Text;

public class TextUtils {

    public static String sanitizePlayerName(String playerName) {
        String sanitized = Text.sanitize(Text.removeTags(playerName));
        return sanitized.replaceAll("\\s*\\(level-\\d+\\)$", "");
    }

    public static String sanitizeItemName(String itemName) {
        if (itemName == null) {
            return null;
        }
        String sanitized = Text.removeTags(itemName);
        // Some game strings contain non-breaking spaces; normalize for reliable matching.
        sanitized = sanitized.replace('\u00A0', ' ').trim();
        return sanitized.replaceAll("\\s*\\(Members\\)$", "");
    }

    /** Like the game's item stack numbers: 99999, then 100K, then 10M. */
    public static String formatStackSize(long amount) {
        if (amount < 100_000) {
            return Long.toString(amount);
        }
        if (amount < 10_000_000) {
            return amount / 1_000 + "K";
        }
        return amount / 1_000_000 + "M";
    }
}
