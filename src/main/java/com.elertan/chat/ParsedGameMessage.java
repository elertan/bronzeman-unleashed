package com.elertan.chat;

import lombok.Getter;

public interface ParsedGameMessage {

    ParsedGameMessageType getType();

    @Getter
    class SkillLevelUpParsedGameMessage implements ParsedGameMessage {

        private final String skill;
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

    @Getter
    class TotalLevelParsedGameMessage implements ParsedGameMessage {

        private final int totalLevel;

        public TotalLevelParsedGameMessage(int totalLevel) {
            this.totalLevel = totalLevel;
        }

        @Override
        public ParsedGameMessageType getType() {
            return ParsedGameMessageType.TotalLevel;
        }
    }

    @Getter
    class CombatLevelUpParsedGameMessage implements ParsedGameMessage {

        private final int level;

        public CombatLevelUpParsedGameMessage(int level) {
            this.level = level;
        }

        @Override
        public ParsedGameMessageType getType() {
            return ParsedGameMessageType.CombatLevelUp;
        }
    }

    @Getter
    class CombatTaskParsedGameMessage implements ParsedGameMessage {

        private final String tier;
        private final String name;

        public CombatTaskParsedGameMessage(String tier, String name) {
            this.tier = tier;
            this.name = name;
        }

        @Override
        public ParsedGameMessageType getType() {
            return ParsedGameMessageType.CombatTask;
        }
    }

    @Getter
    class QuestCompletionParsedGameMessage implements ParsedGameMessage {

        private final String name;

        public QuestCompletionParsedGameMessage(String name) {
            this.name = name;
        }

        @Override
        public ParsedGameMessageType getType() {
            return ParsedGameMessageType.QuestCompletion;
        }
    }

    @Getter
    class CollectionLogUnlockParsedGameMessage implements ParsedGameMessage {

        private final String itemName;

        public CollectionLogUnlockParsedGameMessage(String itemName) {
            this.itemName = itemName;
        }

        @Override
        public ParsedGameMessageType getType() {
            return ParsedGameMessageType.CollectionLogUnlock;
        }
    }
}
