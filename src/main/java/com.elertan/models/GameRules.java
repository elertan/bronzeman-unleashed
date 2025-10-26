package com.elertan.models;

import lombok.Getter;
import lombok.Setter;

public class GameRules {
    // Trade
    @Getter
    @Setter
    private boolean preventTradeOutsideGroup;
    @Getter
    @Setter
    private boolean preventTradeLockedItems;

    // Grand Exchange
    @Getter
    @Setter
    private boolean preventGrandExchangeBuyOffers;

    // Notifications
    @Getter
    @Setter
    private boolean shareAchievementNotifications;

    // Party
    @Getter
    @Setter
    private String partyPassword;

    public static GameRules getDefault() {
        GameRules rules = new GameRules();
        rules.setPreventTradeOutsideGroup(true);
        rules.setPreventTradeLockedItems(true);
        rules.setPreventGrandExchangeBuyOffers(true);
        rules.setShareAchievementNotifications(true);
        rules.setPartyPassword(null);
        return rules;
    }
}
