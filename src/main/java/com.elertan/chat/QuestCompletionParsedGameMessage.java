package com.elertan.chat;

import lombok.Getter;

public class QuestCompletionParsedGameMessage implements ParsedGameMessage {
    @Override
    public ParsedGameMessageType getType() {
        return ParsedGameMessageType.QuestCompletion;
    }

    @Getter
    private final String name;

    public QuestCompletionParsedGameMessage(String name) {
        this.name = name;
    }
}
