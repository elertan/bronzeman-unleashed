package com.elertan.panel.screens.setup.remoteStep;

import com.elertan.remote.firebase.FirebaseRealtimeDatabaseURL;
import com.elertan.ui.Property;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.concurrent.CompletableFuture;

public final class EntryViewViewModel implements AutoCloseable {
    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        EntryViewViewModel create(Listener listener);
    }

    @Singleton
    static final class FactoryImpl implements Factory {
        @Inject
        public FactoryImpl() {
        }

        @Override
        public EntryViewViewModel create(Listener listener) {
            return new EntryViewViewModel(listener);
        }
    }

    public interface Listener {
        CompletableFuture<String> trySubmit(FirebaseRealtimeDatabaseURL url);
    }

    public final Property<Boolean> isLoading = new Property<>(false);
    public final Property<Boolean> isValid;
    public final Property<String> firebaseRealtimeDatabaseURL = new Property<>("");
    public final Property<String> errorMessage = new Property<>(null);

    private final Listener listener;

    private EntryViewViewModel(Listener listener) {
        this.listener = listener;

        isValid = firebaseRealtimeDatabaseURL.derive(this::isValidFirebaseRealtimeDatabaseURL);
    }

    @Override
    public void close() throws Exception {
    }

    public void onContinueClick() {
        isLoading.set(true);

        FirebaseRealtimeDatabaseURL url;
        try {
            url = new FirebaseRealtimeDatabaseURL(firebaseRealtimeDatabaseURL.get());
        } catch (Exception ex) {
            errorMessage.set("The given URL is not a valid Firebase URL");
            return;
        }

        listener.trySubmit(url)
                .whenComplete((error, throwable) -> {
                    try {
                        if (throwable != null) {
                            errorMessage.set("An error occurred while trying to connect to the database.");
                            return;
                        }

                        if (error != null) {
                            errorMessage.set(error);
                            return;
                        }

                        errorMessage.set(null);
                    } finally {
                        isLoading.set(false);
                    }
                });
    }

    private boolean isValidFirebaseRealtimeDatabaseURL(String value) {
        try {
            new FirebaseRealtimeDatabaseURL(value);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
