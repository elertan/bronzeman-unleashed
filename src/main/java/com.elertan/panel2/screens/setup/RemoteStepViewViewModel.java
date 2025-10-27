package com.elertan.panel2.screens.setup;

import com.elertan.ui.Property;
import com.google.inject.Inject;

public final class RemoteStepViewViewModel implements AutoCloseable {
    public enum StateView {
        ENTRY,
        CHECKING
    }
    public final Property<StateView> stateView = new Property<>(StateView.ENTRY);

    @Inject
    public RemoteStepViewViewModel() {
    }

    @Override
    public void close() throws Exception {

    }
}
