package com.elertan.panel2.screens;

import com.elertan.ui.Property;

public final class SetupScreenViewModel implements AutoCloseable {
    public enum Step {
        REMOTE,
        GAME_RULES,
    }

    public final Property<Step> step = new Property<>(Step.REMOTE);

    @Override
    public void close() throws Exception {

    }
}
