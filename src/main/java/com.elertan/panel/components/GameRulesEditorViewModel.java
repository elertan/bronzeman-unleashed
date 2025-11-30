package com.elertan.panel.components;

import com.elertan.models.GameRules;
import com.elertan.models.ISOOffsetDateTime;
import com.elertan.ui.Property;
import com.google.inject.ImplementedBy;
import com.google.inject.Singleton;
import java.beans.PropertyChangeListener;
import java.time.OffsetDateTime;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GameRulesEditorViewModel implements AutoCloseable {

    public final Property<Boolean> onlyForTradeableItemsProperty;
    public final Property<Boolean> restrictGroundItemsProperty;
    public final Property<Boolean> preventTradeOutsideGroupProperty;
    public final Property<Boolean> preventTradeLockedItemsProperty;
    public final Property<Boolean> preventGrandExchangeBuyOffersProperty;
    public final Property<Boolean> preventPlayedOwnedHouseProperty;
    public final Property<Boolean> restrictPlayerVersusPlayerLootProperty;
    public final Property<Boolean> restrictFaladorPartyRoomBalloonsProperty;
    public final Property<Boolean> shareAchievementNotificationsProperty;
    public final Property<Integer> valuableLootNotificationThresholdProperty;
    public final Property<String> partyPasswordProperty;
    public final Property<Boolean> isViewOnlyModeProperty;
    private Props props;
    private final PropertyChangeListener updateListener = evt -> {
        log.debug("{} changed to: {}", evt.getPropertyName(), evt.getNewValue());
        tryUpdateGameRules();
    };

    private GameRulesEditorViewModel(Props initialProps) {
        this.props = initialProps;

        boolean setGameRules = false;
        GameRules gameRules = initialProps.getGameRules();
        if (gameRules == null) {
            ISOOffsetDateTime now = new ISOOffsetDateTime(OffsetDateTime.now());
            gameRules = GameRules.createWithDefaults(initialProps.getAccountHash(), now);
            setGameRules = true;
        }

        onlyForTradeableItemsProperty = new Property<>(gameRules.isOnlyForTradeableItems());
        restrictGroundItemsProperty = new Property<>(gameRules.isRestrictGroundItems());
        preventTradeOutsideGroupProperty = new Property<>(gameRules.isPreventTradeOutsideGroup());
        preventTradeLockedItemsProperty = new Property<>(gameRules.isPreventTradeLockedItems());
        preventGrandExchangeBuyOffersProperty = new Property<>(gameRules.isPreventGrandExchangeBuyOffers());
        preventPlayedOwnedHouseProperty = new Property<>(gameRules.isPreventPlayerOwnedHouse());
        restrictPlayerVersusPlayerLootProperty = new Property<>(gameRules.isRestrictPlayerVersusPlayerLoot());
        restrictFaladorPartyRoomBalloonsProperty = new Property<>(gameRules.isRestrictFaladorPartyRoomBalloons());
        shareAchievementNotificationsProperty = new Property<>(gameRules.isShareAchievementNotifications());
        valuableLootNotificationThresholdProperty = new Property<>(gameRules.getValuableLootNotificationThreshold());
        partyPasswordProperty = new Property<>(gameRules.getPartyPassword());

        isViewOnlyModeProperty = new Property<>(initialProps.isViewOnlyMode());
//        isValid = Property.deriveMany(
//                Arrays.asList(
//                        preventTradeOutsideGroup,
//                        preventTradeLockedItems,
//                        preventGrandExchangeBuyOffers,
//                        shareAchievementNotifications,
//                        partyPassword
//                ),
//                (list) -> {
//                    Boolean preventTradeOutsideGroupValue = (Boolean) list.get(0);
//                    Boolean preventTradeLockedItemsValue = (Boolean) list.get(1);
//                    Boolean preventGrandExchangeBuyOffersValue = (Boolean) list.get(2);
//                    Boolean shareAchievementNotificationsValue = (Boolean) list.get(3);
//                    String partyPasswordValue = (String) list.get(4);
//
//                    return partyPasswordValue == null || partyPasswordValue.length() <= 20;
//                }
//        );
//        isValid = partyPassword.derive((partyPasswordValue) -> partyPasswordValue == null || partyPasswordValue.length() <= 20);

        onlyForTradeableItemsProperty.addListener(updateListener);
        restrictGroundItemsProperty.addListener(updateListener);
        preventTradeOutsideGroupProperty.addListener(updateListener);
        preventTradeLockedItemsProperty.addListener(updateListener);
        preventGrandExchangeBuyOffersProperty.addListener(updateListener);
        preventPlayedOwnedHouseProperty.addListener(updateListener);
        restrictPlayerVersusPlayerLootProperty.addListener(updateListener);
        restrictFaladorPartyRoomBalloonsProperty.addListener(updateListener);
        shareAchievementNotificationsProperty.addListener(updateListener);
        valuableLootNotificationThresholdProperty.addListener(updateListener);
        partyPasswordProperty.addListener(updateListener);

        if (setGameRules) {
            initialProps.onGameRulesChanged.accept(gameRules);
        }
    }

    @Override
    public void close() throws Exception {
        partyPasswordProperty.removeListener(updateListener);
        valuableLootNotificationThresholdProperty.removeListener(updateListener);
        shareAchievementNotificationsProperty.removeListener(updateListener);
        restrictFaladorPartyRoomBalloonsProperty.removeListener(updateListener);
        restrictPlayerVersusPlayerLootProperty.removeListener(updateListener);
        preventPlayedOwnedHouseProperty.removeListener(updateListener);
        preventGrandExchangeBuyOffersProperty.removeListener(updateListener);
        preventTradeLockedItemsProperty.removeListener(updateListener);
        preventTradeOutsideGroupProperty.removeListener(updateListener);
        restrictGroundItemsProperty.removeListener(updateListener);
        onlyForTradeableItemsProperty.removeListener(updateListener);
    }

    public void setProps(Props props) {
        this.props = props;

        GameRules gameRules = props.getGameRules();
        if (gameRules == null) {
            ISOOffsetDateTime now = new ISOOffsetDateTime(OffsetDateTime.now());
            gameRules = GameRules.createWithDefaults(props.getAccountHash(), now);
        }

        onlyForTradeableItemsProperty.set(gameRules.isOnlyForTradeableItems());
        restrictGroundItemsProperty.set(gameRules.isRestrictGroundItems());
        preventTradeOutsideGroupProperty.set(gameRules.isPreventTradeOutsideGroup());
        preventTradeLockedItemsProperty.set(gameRules.isPreventTradeLockedItems());
        preventGrandExchangeBuyOffersProperty.set(gameRules.isPreventGrandExchangeBuyOffers());
        preventPlayedOwnedHouseProperty.set(gameRules.isPreventPlayerOwnedHouse());
        restrictPlayerVersusPlayerLootProperty.set(gameRules.isRestrictPlayerVersusPlayerLoot());
        restrictFaladorPartyRoomBalloonsProperty.set(gameRules.isRestrictFaladorPartyRoomBalloons());
        shareAchievementNotificationsProperty.set(gameRules.isShareAchievementNotifications());
        partyPasswordProperty.set(gameRules.getPartyPassword());
        valuableLootNotificationThresholdProperty.set(gameRules.getValuableLootNotificationThreshold());

        isViewOnlyModeProperty.set(props.isViewOnlyMode());
    }

    private boolean isValid() {
        String partyPassword = partyPasswordProperty.get();
        Integer valuableLootNotificationThreshold = valuableLootNotificationThresholdProperty.get();
        if (valuableLootNotificationThreshold != null && valuableLootNotificationThreshold < 0) {
            return false;
        }
        return partyPassword == null || partyPassword.length() <= 20;
    }

    private void tryUpdateGameRules() {
        if (!isValid()) {
            props.onGameRulesChanged.accept(null);
            return;
        }

        GameRules newGameRules = new GameRules(
            props.getAccountHash(),
            new ISOOffsetDateTime(OffsetDateTime.now()),
            onlyForTradeableItemsProperty.get(),
            restrictGroundItemsProperty.get(),
            preventTradeOutsideGroupProperty.get(),
            preventTradeLockedItemsProperty.get(),
            preventGrandExchangeBuyOffersProperty.get(),
            preventPlayedOwnedHouseProperty.get(),
            restrictPlayerVersusPlayerLootProperty.get(),
            restrictFaladorPartyRoomBalloonsProperty.get(),
            shareAchievementNotificationsProperty.get(),
            valuableLootNotificationThresholdProperty.get(),
            partyPasswordProperty.get()
        );
        props.onGameRulesChanged.accept(newGameRules);
    }

    @ImplementedBy(FactoryImpl.class)
    public interface Factory {

        GameRulesEditorViewModel create(Props initialProps);
    }

    public static class Props {

        @Getter
        private final long accountHash;
        @Getter
        private final GameRules gameRules;
        @Getter
        private final Consumer<GameRules> onGameRulesChanged;
        @Getter
        private final boolean isViewOnlyMode;

        public Props(
            long accountHash,
            GameRules gameRules,
            Consumer<GameRules> onGameRulesChanged,
            boolean isViewOnlyMode
        ) {
            this.accountHash = accountHash;
            this.gameRules = gameRules;
            this.onGameRulesChanged = onGameRulesChanged;
            this.isViewOnlyMode = isViewOnlyMode;
        }
    }

    @Singleton
    private static final class FactoryImpl implements Factory {

        @Override
        public GameRulesEditorViewModel create(Props initialProps) {
            return new GameRulesEditorViewModel(initialProps);
        }
    }
}
