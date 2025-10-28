package com.elertan.panel2.screens.setup;

import com.elertan.models.GameRules;
import com.elertan.ui.Property;
import com.google.inject.ImplementedBy;
import com.google.inject.Singleton;

public class GameRulesStepViewViewModel {
    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        GameRulesStepViewViewModel create(Property<GameRules> gameRules, Listener listener);
    }

    @Singleton
    private static final class FactoryImpl implements Factory {
        @Override
        public GameRulesStepViewViewModel create(Property<GameRules> gameRules, Listener listener) {
            return new GameRulesStepViewViewModel(gameRules, listener);
        }
    }

    public interface Listener {
        void onBack();
        void onFinish();
    }

    public final Property<GameRules> gameRules;

    private final Listener listener;


    private GameRulesStepViewViewModel(Property<GameRules> gameRules, Listener listener) {
        this.gameRules = gameRules;
        this.listener = listener;
    }

    public void onBackButtonClicked() {
        listener.onBack();
    }

    public void onFinishButtonClicked() {
        listener.onFinish();
    }
}
