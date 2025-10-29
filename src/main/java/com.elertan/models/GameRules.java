package com.elertan.models;

import com.elertan.gson.AccountHashJsonAdapter;
import com.google.gson.annotations.JsonAdapter;
import lombok.Getter;
import lombok.Setter;

public class GameRules {

    // General
    @JsonAdapter(AccountHashJsonAdapter.class)
    @Getter
    @Setter
    private Long lastUpdatedByAccountHash;

    @Getter
    @Setter
    private ISOOffsetDateTime lastUpdatedAt;

    @Getter
    @Setter
    private boolean onlyForTradeableItems;

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

    @Getter
    @Setter
    private Integer valuableLootNotificationThreshold;

    // Party
    @Getter
    @Setter
    private String partyPassword;

    public GameRules(Long lastUpdatedByAccountHash, ISOOffsetDateTime lastUpdatedAt,
        boolean onlyForTradeableItems,
        boolean preventTradeOutsideGroup, boolean preventTradeLockedItems,
        boolean preventGrandExchangeBuyOffers, boolean shareAchievementNotifications,
        Integer valuableLootNotificationThreshold, String partyPassword) {
        this.lastUpdatedByAccountHash = lastUpdatedByAccountHash;
        this.lastUpdatedAt = lastUpdatedAt;
        this.onlyForTradeableItems = onlyForTradeableItems;
        this.preventTradeOutsideGroup = preventTradeOutsideGroup;
        this.preventTradeLockedItems = preventTradeLockedItems;
        this.preventGrandExchangeBuyOffers = preventGrandExchangeBuyOffers;
        this.shareAchievementNotifications = shareAchievementNotifications;
        this.valuableLootNotificationThreshold = valuableLootNotificationThreshold;
        this.partyPassword = partyPassword;
    }

    public static GameRules createWithDefaults(Long lastUpdatedByAccountHash,
        ISOOffsetDateTime lastUpdatedAt) {
        return new GameRules(
            lastUpdatedByAccountHash,
            lastUpdatedAt,
            true,
            true,
            true,
            true,
            true,
            100_000,
            null
        );
    }

    @Override
    public String toString() {
        return "GameRules{" + "lastUpdatedByAccountHash=" + lastUpdatedByAccountHash
            + ", lastUpdatedAt=" + lastUpdatedAt
            + ", onlyForTradeableItems=" + onlyForTradeableItems
            + ", preventTradeOutsideGroup=" + preventTradeOutsideGroup +
            ", preventTradeLockedItems=" + preventTradeLockedItems
            + ", preventGrandExchangeBuyOffers=" + preventGrandExchangeBuyOffers
            + ", shareAchievementNotifications=" + shareAchievementNotifications
            + ", valuableLootNotificationThreshold=" + valuableLootNotificationThreshold
            + ", partyPassword='" + partyPassword + '\'' + '}';
    }
}
