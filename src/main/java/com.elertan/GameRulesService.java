package com.elertan;

import com.elertan.data.GameRulesDataProvider;
import com.elertan.models.GameRules;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class GameRulesService implements BUPluginLifecycle {
    public enum State {
        NotReady,
        Ready,
    }

    @Inject
    private GameRulesDataProvider gameRulesDataProvider;

    @Getter
    private State state = State.NotReady;

    @Getter
    private GameRules gameRules;

    @Override
    public void startUp() throws Exception {
        gameRulesDataProvider.addGameRulesListener(this::gameRulesListener);
    }

    @Override
    public void shutDown() throws Exception {
        gameRulesDataProvider.removeGameRulesListener(this::gameRulesListener);

        gameRules = null;
        state = State.NotReady;
    }

    private void gameRulesListener(GameRules gameRules) {
        this.gameRules = gameRules;
        if (gameRules == null) {
            setState(State.NotReady);
        } else {
            setState(State.Ready);
        }
    }

    private void setState(State state) {
        if (this.state == state) {
            return;
        }
        this.state = state;
    }
}
