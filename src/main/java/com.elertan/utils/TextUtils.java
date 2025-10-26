package com.elertan.utils;

import net.runelite.client.util.Text;

public class TextUtils {
    public static String sanitizePlayerName(String playerName) {
        String sanitized = Text.sanitize(playerName);
        return sanitized.replaceAll("\\s*\\(level-\\d+\\)$", "");
    }
}
