package com.elertan.chat;

import lombok.Getter;

public class SkillLevelUpParsedGameMessage implements ParsedGameMessage {

    @Getter
    private final String skill;
    @Getter
    private final int level;

    public SkillLevelUpParsedGameMessage(String skill, int level) {
        this.skill = skill;
        this.level = level;
    }

    @Override
    public ParsedGameMessageType getType() {
        return ParsedGameMessageType.SkillLevelUp;
    }
}
