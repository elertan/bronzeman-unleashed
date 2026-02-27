package com.elertan.panel.components;

import com.elertan.models.GameRules;
import com.elertan.models.ISOOffsetDateTime;
import com.elertan.panel.BaseViewModel;
import com.elertan.ui.Property;
import com.google.inject.ImplementedBy;
import com.google.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.function.Consumer;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GameRulesEditorViewModel extends BaseViewModel {

    public final Property<Boolean> onlyForTradeableItemsProperty;
    public final Property<Boolean> restrictGroundItemsProperty;
    public final Property<Boolean> preventTradeOutsideGroupProperty;
    public final Property<Boolean> preventTradeLockedItemsProperty;
    public final Property<Boolean> preventGrandExchangeBuyOffersProperty;
    public final Property<Boolean> preventGrandExchangeGearBuyOffersProperty;
    public final Property<Boolean> preventPlayedOwnedHouseProperty;
    public final Property<Boolean> restrictPlayerVersusPlayerLootProperty;
    public final Property<Boolean> restrictFaladorPartyRoomBalloonsProperty;
    public final Property<Boolean> shareAchievementNotificationsProperty;
    public final Property<Integer> valuableLootNotificationThresholdProperty;
    public final Property<String> partyPasswordProperty;
    public final Property<Boolean> isViewOnlyModeProperty;
    private Props props;

    private GameRulesEditorViewModel(Props initialProps) {
        this.props = initialProps;
        GameRules gr = resolveOrDefault(initialProps);

        onlyForTradeableItemsProperty = tracked(gr.isOnlyForTradeableItems());
        restrictGroundItemsProperty = tracked(gr.isRestrictGroundItems());
        preventTradeOutsideGroupProperty = tracked(gr.isPreventTradeOutsideGroup());
        preventTradeLockedItemsProperty = tracked(gr.isPreventTradeLockedItems());
        preventGrandExchangeBuyOffersProperty = tracked(gr.isPreventGrandExchangeBuyOffers());
        preventGrandExchangeGearBuyOffersProperty = tracked(gr.isPreventGrandExchangeGearBuyOffers());
        preventPlayedOwnedHouseProperty = tracked(gr.isPreventPlayerOwnedHouse());
        restrictPlayerVersusPlayerLootProperty = tracked(gr.isRestrictPlayerVersusPlayerLoot());
        restrictFaladorPartyRoomBalloonsProperty = tracked(gr.isRestrictFaladorPartyRoomBalloons());
        shareAchievementNotificationsProperty = tracked(gr.isShareAchievementNotifications());
        valuableLootNotificationThresholdProperty = tracked(gr.getValuableLootNotificationThreshold());
        partyPasswordProperty = tracked(gr.getPartyPassword());
        isViewOnlyModeProperty = new Property<>(initialProps.isViewOnlyMode());

        if (initialProps.getGameRules() == null) {
            initialProps.onGameRulesChanged.accept(gr);
        }
    }

    private <T> Property<T> tracked(T initialValue) {
        Property<T> p = new Property<>(initialValue);
        addListener(p, evt -> {
            log.debug("{} changed to: {}", evt.getPropertyName(), evt.getNewValue());
            tryUpdateGameRules();
        });
        return p;
    }

    private static GameRules resolveOrDefault(Props props) {
        GameRules gr = props.getGameRules();
        if (gr != null) return gr;
        return GameRules.createWithDefaults(
            props.getAccountHash(), new ISOOffsetDateTime(OffsetDateTime.now()));
    }

    public void setProps(Props props) {
        this.props = props;
        GameRules gr = resolveOrDefault(props);
        onlyForTradeableItemsProperty.set(gr.isOnlyForTradeableItems());
        restrictGroundItemsProperty.set(gr.isRestrictGroundItems());
        preventTradeOutsideGroupProperty.set(gr.isPreventTradeOutsideGroup());
        preventTradeLockedItemsProperty.set(gr.isPreventTradeLockedItems());
        preventGrandExchangeBuyOffersProperty.set(gr.isPreventGrandExchangeBuyOffers());
        preventGrandExchangeGearBuyOffersProperty.set(gr.isPreventGrandExchangeGearBuyOffers());
        preventPlayedOwnedHouseProperty.set(gr.isPreventPlayerOwnedHouse());
        restrictPlayerVersusPlayerLootProperty.set(gr.isRestrictPlayerVersusPlayerLoot());
        restrictFaladorPartyRoomBalloonsProperty.set(gr.isRestrictFaladorPartyRoomBalloons());
        shareAchievementNotificationsProperty.set(gr.isShareAchievementNotifications());
        valuableLootNotificationThresholdProperty.set(gr.getValuableLootNotificationThreshold());
        partyPasswordProperty.set(gr.getPartyPassword());
        isViewOnlyModeProperty.set(props.isViewOnlyMode());
    }

    private boolean isValid() {
        Integer threshold = valuableLootNotificationThresholdProperty.get();
        String pw = partyPasswordProperty.get();
        return (threshold == null || threshold >= 0) && (pw == null || pw.length() <= 20);
    }

    private void tryUpdateGameRules() {
        if (!isValid()) {
            props.onGameRulesChanged.accept(null);
            return;
        }
        props.onGameRulesChanged.accept(GameRules.builder()
            .lastUpdatedByAccountHash(props.getAccountHash())
            .lastUpdatedAt(new ISOOffsetDateTime(OffsetDateTime.now()))
            .onlyForTradeableItems(onlyForTradeableItemsProperty.get())
            .restrictGroundItems(restrictGroundItemsProperty.get())
            .preventTradeOutsideGroup(preventTradeOutsideGroupProperty.get())
            .preventTradeLockedItems(preventTradeLockedItemsProperty.get())
            .preventGrandExchangeBuyOffers(preventGrandExchangeBuyOffersProperty.get())
            .preventGrandExchangeGearBuyOffers(preventGrandExchangeGearBuyOffersProperty.get())
            .preventPlayerOwnedHouse(preventPlayedOwnedHouseProperty.get())
            .restrictPlayerVersusPlayerLoot(restrictPlayerVersusPlayerLootProperty.get())
            .restrictFaladorPartyRoomBalloons(restrictFaladorPartyRoomBalloonsProperty.get())
            .shareAchievementNotifications(shareAchievementNotificationsProperty.get())
            .valuableLootNotificationThreshold(valuableLootNotificationThresholdProperty.get())
            .partyPassword(partyPasswordProperty.get())
            .build());
    }

    @ImplementedBy(FactoryImpl.class)
    public interface Factory {

        GameRulesEditorViewModel create(Props initialProps);
    }

    @Value
    public static class Props {

        long accountHash;
        GameRules gameRules;
        Consumer<GameRules> onGameRulesChanged;
        boolean isViewOnlyMode;
    }

    @Singleton
    private static final class FactoryImpl implements Factory {

        @Override
        public GameRulesEditorViewModel create(Props initialProps) {
            return new GameRulesEditorViewModel(initialProps);
        }
    }
}
