package com.elertan.models;

import com.elertan.gson.AccountHashJsonAdapter;
import com.google.gson.annotations.JsonAdapter;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GameRules {

    @JsonAdapter(AccountHashJsonAdapter.class)
    Long lastUpdatedByAccountHash;
    ISOOffsetDateTime lastUpdatedAt;
    boolean onlyForTradeableItems;
    boolean restrictGroundItems;
    boolean preventTradeOutsideGroup;
    boolean preventTradeLockedItems;
    boolean preventGrandExchangeBuyOffers;
    boolean preventPlayerOwnedHouse;
    boolean restrictPlayerVersusPlayerLoot;
    boolean restrictFaladorPartyRoomBalloons;
    boolean shareAchievementNotifications;
    // New members must choose "Existing account", so their items are locked. Current members do not change.
    boolean requireItemLock;
    Integer valuableLootNotificationThreshold;
    String partyPassword;

    public static GameRules createWithDefaults(Long lastUpdatedByAccountHash,
        ISOOffsetDateTime lastUpdatedAt) {
        return GameRules.builder()
            .lastUpdatedByAccountHash(lastUpdatedByAccountHash)
            .lastUpdatedAt(lastUpdatedAt)
            .onlyForTradeableItems(true)
            .restrictGroundItems(true)
            .preventTradeOutsideGroup(true)
            .preventTradeLockedItems(true)
            .preventGrandExchangeBuyOffers(true)
            .preventPlayerOwnedHouse(true)
            .restrictPlayerVersusPlayerLoot(false)
            .restrictFaladorPartyRoomBalloons(true)
            .shareAchievementNotifications(true)
            .requireItemLock(false)
            .valuableLootNotificationThreshold(100_000)
            .partyPassword(null)
            .build();
    }
}
