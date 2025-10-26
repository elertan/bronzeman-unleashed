package com.elertan.chat;

import lombok.Getter;

public class CombatTaskParsedGameMessage implements ParsedGameMessage {
    @Override
    public ParsedGameMessageType getType() {
        return ParsedGameMessageType.CombatTask;
    }

    @Getter
    private final String tier;
    @Getter
    private final String name;

    public CombatTaskParsedGameMessage(String tier, String name) {
        this.tier = tier;
        this.name = name;
    }
}
