package com.elertan.panel2.screens.setup;

import com.elertan.ui.Property;
import com.google.inject.Inject;

public final class RemoteStepScreenViewModel implements AutoCloseable {
    public enum StateView {
        ENTRY,
        CHECKING
    }
    public final Property<StateView> stateView = new Property<>(StateView.ENTRY);

    @Inject
    public RemoteStepScreenViewModel() {
    }

    @Override
    public void close() throws Exception {

    }
}
