package com.elertan.utils;

import net.runelite.client.util.Text;

public class TextUtils {

    public static String sanitizePlayerName(String playerName) {
        String sanitized = Text.sanitize(Text.removeTags(playerName));
        return sanitized.replaceAll("\\s*\\(level-\\d+\\)$", "");
    }

    public static String sanitizeItemName(String itemName) {
        String sanitized = Text.removeTags(itemName);
        return sanitized.replaceAll("\\s*\\(Members\\)$", "");
    }
}
