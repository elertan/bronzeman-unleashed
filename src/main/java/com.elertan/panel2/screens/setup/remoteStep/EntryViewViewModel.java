package com.elertan.panel2.screens.setup.remoteStep;

import com.elertan.ui.Property;
import com.google.inject.Inject;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public final class EntryViewViewModel implements AutoCloseable {
    public final Property<Boolean> isLoading = new Property<>(false);
    public final Property<String> firebaseRealtimeDatabaseURL = new Property<>("");
    public final Property<String> errorMessage = new Property<>(null);

    private final PropertyChangeListener firebaseRealtimeDatabaseURLListener = this::firebaseRealtimeDatabaseURLListener;

    @Inject
    public EntryViewViewModel() {
        firebaseRealtimeDatabaseURL.addListener(firebaseRealtimeDatabaseURLListener);
    }

    @Override
    public void close() throws Exception {
        firebaseRealtimeDatabaseURL.removeListener(firebaseRealtimeDatabaseURLListener);
    }

    private void firebaseRealtimeDatabaseURLListener(PropertyChangeEvent event) {
        String value = (String)event.getNewValue();
        errorMessage.set(value);
    }
}
