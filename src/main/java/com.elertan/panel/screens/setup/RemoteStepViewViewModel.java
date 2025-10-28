package com.elertan.panel.screens.setup;

import com.elertan.remote.firebase.FirebaseRealtimeDatabase;
import com.elertan.remote.firebase.FirebaseRealtimeDatabaseURL;
import com.elertan.ui.Property;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.Setter;
import okhttp3.OkHttpClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class RemoteStepViewViewModel implements AutoCloseable {
    @ImplementedBy(FactoryImpl.class)
    public interface Factory {
        RemoteStepViewViewModel create(Listener listener);
    }

    @Singleton
    private static class FactoryImpl implements Factory {
        private final OkHttpClient httpClient;

        @Inject
        public FactoryImpl(OkHttpClient httpClient) {
            this.httpClient = httpClient;
        }

        @Override
        public RemoteStepViewViewModel create(Listener listener) {
            return new RemoteStepViewViewModel(httpClient, listener);
        }
    }

    public interface Listener {
        CompletableFuture<Void> onRemoteStepFinished(FirebaseRealtimeDatabaseURL url);
    }

    private static final class TrySubmitAttempt {
        @Setter
        private CompletableFuture<String> future;
        @Setter
        private CompletableFuture<Boolean> firebaseRealtimeDatabaseCanConnectToFuture;
        @Setter
        private CompletableFuture<Void> onRemoteStepFinishedFuture;

        public void cancel() {
            if (future != null) {
                future.cancel(true);
            }
            if (firebaseRealtimeDatabaseCanConnectToFuture != null) {
                firebaseRealtimeDatabaseCanConnectToFuture.cancel(true);
            }
            if (onRemoteStepFinishedFuture != null) {
                onRemoteStepFinishedFuture.cancel(true);
            }
        }
    }
    private final AtomicReference<TrySubmitAttempt> trySubmitAttempt = new AtomicReference<>(null);

    public enum StateView {
        ENTRY,
        CHECKING
    }

    private final OkHttpClient httpClient;
    private final Listener listener;

    public final Property<StateView> stateView = new Property<>(StateView.ENTRY);

    private RemoteStepViewViewModel(OkHttpClient httpClient, Listener listener) {
        this.httpClient = httpClient;
        this.listener = listener;
    }

    @Override
    public void close() throws Exception {
        TrySubmitAttempt attempt = trySubmitAttempt.getAndSet(null);
        if (attempt != null) {
            attempt.cancel();
        }
    }

    public CompletableFuture<String> onEntryViewTrySubmit(FirebaseRealtimeDatabaseURL url) {
        stateView.set(StateView.CHECKING);
        CompletableFuture<String> future = new CompletableFuture<>();

        TrySubmitAttempt attempt = trySubmitAttempt.get();
        if (attempt != null) {
            attempt.cancel();
            trySubmitAttempt.set(null);
        }

        attempt = new TrySubmitAttempt();
        attempt.setFuture(future);
        trySubmitAttempt.set(attempt);

        // Reset state when this attempt finishes
        future.whenComplete((__, throwable) -> {
            trySubmitAttempt.set(null);
            stateView.set(StateView.ENTRY);
        });

        CompletableFuture<Boolean> firebaseRealtimeDatabaseCanConnectToFuture = FirebaseRealtimeDatabase.canConnectTo(httpClient, url);
        attempt.setFirebaseRealtimeDatabaseCanConnectToFuture(firebaseRealtimeDatabaseCanConnectToFuture);

        TrySubmitAttempt finalAttempt = attempt;
        firebaseRealtimeDatabaseCanConnectToFuture.whenComplete((canConnect, throwable) -> {
            if (throwable != null) {
                future.completeExceptionally(throwable);
                return;
            }
            if (!canConnect) {
                future.complete("Could not connect to the Firebase Realtime database, please check the URL or try again later.");
                return;
            }

            CompletableFuture<Void> onRemoteStepFinishedFuture = listener.onRemoteStepFinished(url);
            finalAttempt.setOnRemoteStepFinishedFuture(onRemoteStepFinishedFuture);

            onRemoteStepFinishedFuture.whenComplete((__, throwable2) -> {
                if (throwable2 != null) {
                    future.completeExceptionally(throwable2);
                    return;
                }
                future.complete(null);
            });
        });

        return future;
    }


    public void onCancelChecking() {
        TrySubmitAttempt attempt = trySubmitAttempt.getAndSet(null);
        if (attempt != null) {
            attempt.cancel();
        }
        stateView.set(StateView.ENTRY);
    }
}
