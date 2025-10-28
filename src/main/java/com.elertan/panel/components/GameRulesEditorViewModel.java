package com.elertan.panel.components;

import com.elertan.models.GameRules;
import com.elertan.models.ISOOffsetDateTime;
import com.elertan.ui.Property;
import com.google.inject.ImplementedBy;
import com.google.inject.Singleton;
import lombok.Getter;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.time.OffsetDateTime;
import java.util.function.Consumer;

public class GameRulesEditorViewModel implements AutoCloseable {
    public static class Props {
        @Getter
        private final long accountHash;
        @Getter
        private final GameRules gameRules;
        @Getter
        private final Consumer<GameRules> onGameRulesChanged;
        @Getter
        private final boolean isViewOnlyMode;

        public Props(long accountHash, GameRules gameRules, Consumer<GameRules> onGameRulesChanged, boolean isViewOnlyMode) {
            this.accountHash = accountHash;
            this.gameRules = gameRules;
            this.onGameRulesChanged = onGameRulesChanged;
            this.isViewOnlyMode = isViewOnlyMode;
        }
    }

    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        GameRulesEditorViewModel create(Props initialProps);
    }

    @Singleton
    private static final class FactoryImpl implements Factory {
        @Override
        public GameRulesEditorViewModel create(Props initialProps) {
            return new GameRulesEditorViewModel(initialProps);
        }
    }

    public final Property<Boolean> preventTradeOutsideGroup;
    public final Property<Boolean> preventTradeLockedItems;
    public final Property<Boolean> preventGrandExchangeBuyOffers;
    public final Property<Boolean> shareAchievementNotifications;
    public final Property<String> partyPassword;

    public final Property<Boolean> isViewOnlyMode;
//    public final Property<Boolean> isValid;


    private Props props;

    private final PropertyChangeListener preventTradeOutsideGroupListener = this::preventTradeOutsideGroupListener;
    private final PropertyChangeListener preventTradeLockedItemsListener = this::preventTradeLockedItemsListener;
    private final PropertyChangeListener preventGrandExchangeBuyOffersListener = this::preventGrandExchangeBuyOffersListener;
    private final PropertyChangeListener shareAchievementNotificationsListener = this::shareAchievementNotificationsListener;
    private final PropertyChangeListener partyPasswordListener = this::partyPasswordListener;

    private GameRulesEditorViewModel(Props initialProps) {
        this.props = initialProps;

        GameRules gameRules = initialProps.getGameRules();
        if (gameRules == null) {
            ISOOffsetDateTime now = new ISOOffsetDateTime(OffsetDateTime.now());
            gameRules = GameRules.createWithDefaults(initialProps.getAccountHash(), now);
        }

        preventTradeOutsideGroup = new Property<>(gameRules.isPreventTradeOutsideGroup());
        preventTradeLockedItems = new Property<>(gameRules.isPreventTradeLockedItems());
        preventGrandExchangeBuyOffers = new Property<>(gameRules.isPreventGrandExchangeBuyOffers());
        shareAchievementNotifications = new Property<>(gameRules.isShareAchievementNotifications());
        partyPassword = new Property<>(gameRules.getPartyPassword());

        isViewOnlyMode = new Property<>(initialProps.isViewOnlyMode());
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

        preventTradeOutsideGroup.addListener(preventTradeOutsideGroupListener);
        preventTradeLockedItems.addListener(preventTradeLockedItemsListener);
        preventGrandExchangeBuyOffers.addListener(preventGrandExchangeBuyOffersListener);
        shareAchievementNotifications.addListener(shareAchievementNotificationsListener);
        partyPassword.addListener(partyPasswordListener);
    }

    @Override
    public void close() throws Exception {
        partyPassword.removeListener(partyPasswordListener);
        shareAchievementNotifications.removeListener(shareAchievementNotificationsListener);
        preventGrandExchangeBuyOffers.removeListener(preventGrandExchangeBuyOffersListener);
        preventTradeLockedItems.removeListener(preventTradeLockedItemsListener);
        preventTradeOutsideGroup.removeListener(preventTradeOutsideGroupListener);
    }

    public void setProps(Props props) {
        this.props = props;

        GameRules gameRules = props.getGameRules();
        if (gameRules == null) {
            ISOOffsetDateTime now = new ISOOffsetDateTime(OffsetDateTime.now());
            gameRules = GameRules.createWithDefaults(props.getAccountHash(), now);
        }

        preventTradeOutsideGroup.set(gameRules.isPreventTradeOutsideGroup());
        preventTradeLockedItems.set(gameRules.isPreventTradeLockedItems());
        preventGrandExchangeBuyOffers.set(gameRules.isPreventGrandExchangeBuyOffers());
        shareAchievementNotifications.set(gameRules.isShareAchievementNotifications());
        partyPassword.set(gameRules.getPartyPassword());

        isViewOnlyMode.set(props.isViewOnlyMode());
    }

    private void preventTradeOutsideGroupListener(PropertyChangeEvent event) {
        tryUpdateGameRules();
    }

    private void preventTradeLockedItemsListener(PropertyChangeEvent event) {
        tryUpdateGameRules();
    }

    private void preventGrandExchangeBuyOffersListener(PropertyChangeEvent event) {
        tryUpdateGameRules();
    }

    private void shareAchievementNotificationsListener(PropertyChangeEvent event) {
        tryUpdateGameRules();
    }

    private void partyPasswordListener(PropertyChangeEvent event) {
        tryUpdateGameRules();
    }

    private boolean isValid() {
        String partyPasswordValue = partyPassword.get();
        return partyPasswordValue == null || partyPasswordValue.length() <= 20;
    }

    private void tryUpdateGameRules() {
        if (!isValid()) {
            props.onGameRulesChanged.accept(null);
            return;
        }

        GameRules newGameRules = new GameRules(
                props.getAccountHash(),
                new ISOOffsetDateTime(OffsetDateTime.now()),
                preventTradeOutsideGroup.get(),
                preventTradeLockedItems.get(),
                preventGrandExchangeBuyOffers.get(),
                shareAchievementNotifications.get(),
                partyPassword.get()
        );
        props.onGameRulesChanged.accept(newGameRules);
    }
}
