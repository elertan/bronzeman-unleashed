package com.elertan.chat;

import lombok.Getter;

public class CombatLevelUpParsedGameMessage implements ParsedGameMessage {

    @Getter
    private final int level;

    public CombatLevelUpParsedGameMessage(int level) {
        this.level = level;
    }

    @Override
    public ParsedGameMessageType getType() {
        return ParsedGameMessageType.CombatLevelUp;
    }
}
