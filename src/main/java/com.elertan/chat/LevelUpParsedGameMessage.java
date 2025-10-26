package com.elertan.chat;

import lombok.Getter;

public class LevelUpParsedGameMessage implements ParsedGameMessage {
    @Override
    public ParsedGameMessageType getType() {
        return ParsedGameMessageType.LevelUp;
    }

    @Getter
    private final String skill;
    @Getter
    private final int level;

    public LevelUpParsedGameMessage(String skill, int level) {
        this.skill = skill;
        this.level = level;
    }
}
