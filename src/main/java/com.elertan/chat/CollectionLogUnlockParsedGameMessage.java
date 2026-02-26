package com.elertan.chat;

import lombok.Getter;

public class CollectionLogUnlockParsedGameMessage implements ParsedGameMessage {

    @Getter
    private final String itemName;

    public CollectionLogUnlockParsedGameMessage(String itemName) {
        this.itemName = itemName;
    }

    @Override
    public ParsedGameMessageType getType() {
        return ParsedGameMessageType.CollectionLogUnlock;
    }
}
