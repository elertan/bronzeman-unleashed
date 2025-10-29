package com.elertan.panel.components;

import com.elertan.models.GameRules;
import com.elertan.models.ISOOffsetDateTime;
import com.elertan.ui.Property;
import com.google.inject.ImplementedBy;
import com.google.inject.Singleton;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.time.OffsetDateTime;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GameRulesEditorViewModel implements AutoCloseable {

    public final Property<Boolean> onlyForTradeableItemsProperty;
    public final Property<Boolean> preventTradeOutsideGroupProperty;
    public final Property<Boolean> preventTradeLockedItemsProperty;
    public final Property<Boolean> preventGrandExchangeBuyOffersProperty;
    public final Property<Boolean> shareAchievementNotificationsProperty;
    public final Property<Integer> valuableLootNotificationThresholdProperty;
    public final Property<String> partyPasswordProperty;
    public final Property<Boolean> isViewOnlyModeProperty;
    private Props props;
    private final PropertyChangeListener onlyForTradeableItemsListener = this::onlyForTradeableItemsListener;
    private final PropertyChangeListener preventTradeOutsideGroupListener = this::preventTradeOutsideGroupListener;
    private final PropertyChangeListener preventTradeLockedItemsListener = this::preventTradeLockedItemsListener;
    //    public final Property<Boolean> isValid;
    private final PropertyChangeListener preventGrandExchangeBuyOffersListener = this::preventGrandExchangeBuyOffersListener;
    private final PropertyChangeListener shareAchievementNotificationsListener = this::shareAchievementNotificationsListener;
    private final PropertyChangeListener valuableLootNotificationThresholdListener = this::valuableLootNotificationThresholdListener;
    private final PropertyChangeListener partyPasswordListener = this::partyPasswordListener;

    private GameRulesEditorViewModel(Props initialProps) {
        this.props = initialProps;

        GameRules gameRules = initialProps.getGameRules();
        if (gameRules == null) {
            ISOOffsetDateTime now = new ISOOffsetDateTime(OffsetDateTime.now());
            gameRules = GameRules.createWithDefaults(initialProps.getAccountHash(), now);
        }

        onlyForTradeableItemsProperty = new Property<>(gameRules.isOnlyForTradeableItems());
        preventTradeOutsideGroupProperty = new Property<>(gameRules.isPreventTradeOutsideGroup());
        preventTradeLockedItemsProperty = new Property<>(gameRules.isPreventTradeLockedItems());
        preventGrandExchangeBuyOffersProperty = new Property<>(gameRules.isPreventGrandExchangeBuyOffers());
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

        onlyForTradeableItemsProperty.addListener(onlyForTradeableItemsListener);
        preventTradeOutsideGroupProperty.addListener(preventTradeOutsideGroupListener);
        preventTradeLockedItemsProperty.addListener(preventTradeLockedItemsListener);
        preventGrandExchangeBuyOffersProperty.addListener(preventGrandExchangeBuyOffersListener);
        shareAchievementNotificationsProperty.addListener(shareAchievementNotificationsListener);
        valuableLootNotificationThresholdProperty.addListener(
            valuableLootNotificationThresholdListener);
        partyPasswordProperty.addListener(partyPasswordListener);
    }

    @Override
    public void close() throws Exception {
        partyPasswordProperty.removeListener(partyPasswordListener);
        valuableLootNotificationThresholdProperty.removeListener(
            valuableLootNotificationThresholdListener);
        shareAchievementNotificationsProperty.removeListener(shareAchievementNotificationsListener);
        preventGrandExchangeBuyOffersProperty.removeListener(preventGrandExchangeBuyOffersListener);
        preventTradeLockedItemsProperty.removeListener(preventTradeLockedItemsListener);
        preventTradeOutsideGroupProperty.removeListener(preventTradeOutsideGroupListener);
        onlyForTradeableItemsProperty.removeListener(onlyForTradeableItemsListener);
    }

    public void setProps(Props props) {
        this.props = props;

        GameRules gameRules = props.getGameRules();
        if (gameRules == null) {
            ISOOffsetDateTime now = new ISOOffsetDateTime(OffsetDateTime.now());
            gameRules = GameRules.createWithDefaults(props.getAccountHash(), now);
        }

        onlyForTradeableItemsProperty.set(gameRules.isOnlyForTradeableItems());
        preventTradeOutsideGroupProperty.set(gameRules.isPreventTradeOutsideGroup());
        preventTradeLockedItemsProperty.set(gameRules.isPreventTradeLockedItems());
        preventGrandExchangeBuyOffersProperty.set(gameRules.isPreventGrandExchangeBuyOffers());
        shareAchievementNotificationsProperty.set(gameRules.isShareAchievementNotifications());
        partyPasswordProperty.set(gameRules.getPartyPassword());
        valuableLootNotificationThresholdProperty.set(gameRules.getValuableLootNotificationThreshold());

        isViewOnlyModeProperty.set(props.isViewOnlyMode());
    }

    private void onlyForTradeableItemsListener(PropertyChangeEvent event) {
        log.debug("onlyForTradeableItems changed to: {}", event.getNewValue());
        tryUpdateGameRules();
    }

    private void preventTradeOutsideGroupListener(PropertyChangeEvent event) {
        log.debug("preventTradeOutsideGroup changed to: {}", event.getNewValue());
        tryUpdateGameRules();
    }

    private void preventTradeLockedItemsListener(PropertyChangeEvent event) {
        log.debug("preventTradeLockedItems changed to: {}", event.getNewValue());
        tryUpdateGameRules();
    }

    private void preventGrandExchangeBuyOffersListener(PropertyChangeEvent event) {
        log.debug("preventGrandExchangeBuyOffers changed to: {}", event.getNewValue());
        tryUpdateGameRules();
    }

    private void shareAchievementNotificationsListener(PropertyChangeEvent event) {
        log.debug("shareAchievementNotifications changed to: {}", event.getNewValue());
        tryUpdateGameRules();
    }

    private void valuableLootNotificationThresholdListener(PropertyChangeEvent event) {
        log.debug("valuableLootNotificationThreshold changed to: {}", event.getNewValue());
        tryUpdateGameRules();
    }

    private void partyPasswordListener(PropertyChangeEvent event) {
        log.debug("partyPassword changed to: {}", event.getNewValue());
        tryUpdateGameRules();
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
            preventTradeOutsideGroupProperty.get(),
            preventTradeLockedItemsProperty.get(),
            preventGrandExchangeBuyOffersProperty.get(),
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
