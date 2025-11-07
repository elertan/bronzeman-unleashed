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

    // Ground items
    @Getter
    @Setter
    private boolean restrictGroundItems;

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

    // Played Owner House
    @Getter
    @Setter
    private boolean preventPlayerOwnedHouse;

    // Player Versus Player
    @Getter
    @Setter
    private boolean restrictPlayerVersusPlayerLoot;

    // Falador Party Room
    @Getter
    @Setter
    private boolean restrictFaladorPartyRoomBalloons;

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
        boolean restrictGroundItems,
        boolean preventTradeOutsideGroup,
        boolean preventTradeLockedItems,
        boolean preventGrandExchangeBuyOffers,
        boolean preventPlayerOwnedHouse,
        boolean restrictPlayerVersusPlayerLoot,
        boolean restrictFaladorPartyRoomBalloons,
        boolean shareAchievementNotifications,
        Integer valuableLootNotificationThreshold, String partyPassword) {
        this.lastUpdatedByAccountHash = lastUpdatedByAccountHash;
        this.lastUpdatedAt = lastUpdatedAt;
        this.onlyForTradeableItems = onlyForTradeableItems;
        this.restrictGroundItems = restrictGroundItems;
        this.preventTradeOutsideGroup = preventTradeOutsideGroup;
        this.preventTradeLockedItems = preventTradeLockedItems;
        this.preventGrandExchangeBuyOffers = preventGrandExchangeBuyOffers;
        this.preventPlayerOwnedHouse = preventPlayerOwnedHouse;
        this.restrictPlayerVersusPlayerLoot = restrictPlayerVersusPlayerLoot;
        this.restrictFaladorPartyRoomBalloons = restrictFaladorPartyRoomBalloons;
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
            true,
            false,
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
            + ", restrictGroundItems=" + restrictGroundItems
            + ", preventTradeOutsideGroup=" + preventTradeOutsideGroup +
            ", preventTradeLockedItems=" + preventTradeLockedItems
            + ", preventGrandExchangeBuyOffers=" + preventGrandExchangeBuyOffers
            + ", preventPlayedOwnedHouse=" + preventPlayerOwnedHouse
            + ", restrictPlayerVersusPlayerLoot=" + restrictPlayerVersusPlayerLoot
            + ", restrictFaladorPartyRoomBalloons=" + restrictFaladorPartyRoomBalloons
            + ", shareAchievementNotifications=" + shareAchievementNotifications
            + ", valuableLootNotificationThreshold=" + valuableLootNotificationThreshold
            + ", partyPassword='" + partyPassword + '\'' + '}';
    }
}
