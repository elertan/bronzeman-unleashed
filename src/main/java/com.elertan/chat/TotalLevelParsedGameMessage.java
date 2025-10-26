package com.elertan.chat;

import lombok.Getter;

public class TotalLevelParsedGameMessage implements ParsedGameMessage {
    @Override
    public ParsedGameMessageType getType() {
        return ParsedGameMessageType.TotalLevel;
    }

    @Getter
    private final int totalLevel;

    public TotalLevelParsedGameMessage(int totalLevel) {
        this.totalLevel = totalLevel;
    }
}
